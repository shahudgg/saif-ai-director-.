package com.example.agent

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.agent.brain.AgentBrainFactory
import com.example.agent.brain.ComputerUseBrain
import com.example.agent.safety.GateResult
import com.example.agent.safety.RiskGuard
import com.example.data.local.ProviderSettingsManager
import com.example.data.remote.GeminiKeyPool
import com.example.util.SaifAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

data class PhoneAgentTask(
    val id: String,
    val goal: String,
    val listener: AgentListener? = null,
    val priority: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object PhoneAgent {
    private const val TAG = "SAIF_AGENT"

    suspend fun run(task: PhoneAgentTask, context: Context): AgentOutcome {
        val startTime = System.currentTimeMillis()
        AgentLogger.log("Starting agent execution for goal: '${task.goal}'")

        // 1. Preflight checks
        var service = SaifAccessibilityService.instance
        if (service == null && SaifAccessibilityService.isServiceEnabled(context)) {
            for (i in 1..4) {
                delay(250)
                service = SaifAccessibilityService.instance
                if (service != null) break
            }
        }
        if (service == null || !SaifAccessibilityService.isServiceEnabled(context)) {
            val msg = "Accessibility permission band hai. Kripya phone settings me Accessibility on kijiye."
            AgentLogger.log("Preflight failed: Accessibility service not running")
            task.listener?.onFinished(AgentOutcome(false, msg, 0, 0, "ACCESSIBILITY_DISABLED"))
            SaifAccessibilityService.openAccessibilitySettings(context)
            return AgentOutcome(false, msg, 0, 0, "ACCESSIBILITY_DISABLED")
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && !pm.isInteractive) {
            val msg = "Phone screen locked ya off hai. Kripya screen unlock kijiye."
            task.listener?.onFinished(AgentOutcome(false, msg, 0, 0, "SCREEN_LOCKED"))
            return AgentOutcome(false, msg, 0, 0, "SCREEN_LOCKED")
        }

        val state = ProviderSettingsManager.loadState()
        val hasAnyKey = state.apiKeys.any { it.isNotBlank() && !it.contains("MY_") } ||
            GeminiKeyPool.getAllValidKeys().isNotEmpty()
        if (!hasAnyKey) {
            val msg = "AI key missing hai. Kripya Settings ⚙️ ➔ AI Provider me jakar apni API key add karein."
            task.listener?.onFinished(AgentOutcome(false, msg, 0, 0, "NO_API_KEY"))
            return AgentOutcome(false, msg, 0, 0, "NO_API_KEY")
        }

        ComputerUseBrain.resetSession()

        val history = mutableListOf<StepRecord>()
        var stepCount = 0
        var notes = ""
        var consecutiveNoChangeCount = 0
        var lastFingerprint = ""
        var verificationRetries = 0

        val maxSteps = AgentConfig.MAX_STEPS
        val maxDurationMs = AgentConfig.MAX_MILLIS

        // Initial idle wait
        service.waitForIdle(1500, 300)

        while (coroutineContext.isActive && stepCount < maxSteps && (System.currentTimeMillis() - startTime) < maxDurationMs) {
            stepCount++
            val stepStart = System.currentTimeMillis()

            // 1. OBSERVE & DISMISS POPUPS
            if (PopupHandler.dismissIfPresent(service)) {
                AgentLogger.log("PopupHandler automatically dismissed on-screen blocker.")
                service.waitForIdle(AgentConfig.IDLE_TIMEOUT_MS, AgentConfig.IDLE_QUIET_MS)
            }
            val snapshot = ScreenObserver.capture(withScreenshot = true)

            // 2. STUCK DETECTION
            if (snapshot.fingerprint == lastFingerprint) {
                consecutiveNoChangeCount++
            } else {
                consecutiveNoChangeCount = 0
                lastFingerprint = snapshot.fingerprint
            }

            val thinkingLevel = if (consecutiveNoChangeCount >= 2) "high" else "low"
            if (consecutiveNoChangeCount >= 4) {
                AgentLogger.log("Stuck detection: Screen has not changed for 4 steps. Yielding to user.")
                val yieldOutcome = AgentOutcome(false, "Screen respond nahi kar rahi hai. Kripya ek baar manually check kijiye.", stepCount, System.currentTimeMillis() - startTime)
                task.listener?.onFinished(yieldOutcome)
                return yieldOutcome
            }

            // 3. DECIDE
            val brain = AgentBrainFactory.create()
            val decision = brain.decide(
                task = task.goal,
                sessionCtx = MacroCache.getHint(snapshot.packageName, task.goal) ?: "",
                notes = notes,
                history = history,
                snapshot = snapshot,
                thinkingLevel = thinkingLevel
            )

            if (decision.calls.isEmpty()) {
                AgentLogger.log("Brain returned no action calls. Waiting briefly.")
                delay(800)
                continue
            }

            var shouldBreakLoop = false

            // 4. GATE & ACT
            for (call in decision.calls) {
                if (!coroutineContext.isActive) break

                // Check finish/yield calls
                if (call.name == "finish") {
                    val requestedSuccess = call.arguments.optBoolean("success", true)
                    val rawSummary = call.arguments.optString("summary", "Task complete")

                    if (requestedSuccess) {
                        // 5. VERIFY
                        val verifyResult = GoalVerifier.verify(task.goal, history.map { "${it.call.name}: ${it.result.detail}" }, snapshot)
                        if (!verifyResult.achieved && verificationRetries < 2) {
                            verificationRetries++
                            AgentLogger.log("GoalVerifier reported not achieved: ${verifyResult.missing}. Continuing loop.")
                            notes += " NOT_VERIFIED: ${verifyResult.missing}"
                            break
                        }
                    }

                    // Save successful macro sequence
                    if (requestedSuccess) {
                        MacroCache.put(snapshot.packageName, task.goal, history.map { step ->
                            val target = step.call.arguments.optString("text").ifBlank {
                                step.call.arguments.optString("app_name").ifBlank {
                                    step.call.arguments.optString("id")
                                }
                            }
                            if (target.isNotBlank()) "${step.call.name}[$target]" else step.call.name
                        })
                    }

                    val finalOutcome = AgentOutcome(
                        success = requestedSuccess,
                        summary = rawSummary,
                        stepsTaken = stepCount,
                        durationMs = System.currentTimeMillis() - startTime,
                        data = call.arguments.opt("data")
                    )
                    task.listener?.onFinished(finalOutcome)
                    return finalOutcome
                }

                if (call.name == "yield_to_user") {
                    val reason = call.arguments.optString("reason", "Manual user action needed")
                    val yieldOutcome = AgentOutcome(false, reason, stepCount, System.currentTimeMillis() - startTime)
                    task.listener?.onFinished(yieldOutcome)
                    return yieldOutcome
                }

                if (call.name == "note") {
                    notes += " " + call.arguments.optString("text", "")
                    continue
                }

                // Risk Guard check
                val gate = RiskGuard.gate(call, snapshot)
                when (gate.result) {
                    GateResult.BLOCK -> {
                        AgentLogger.log("Action blocked by RiskGuard: ${gate.reason}")
                        continue
                    }
                    GateResult.CONFIRM -> {
                        val confirmed = UserDialogBridge.askUser(gate.reason, listOf("Haan", "Nahi"), task.listener)
                        if (!confirmed.contains("haan", ignoreCase = true) && !confirmed.contains("yes", ignoreCase = true)) {
                            AgentLogger.log("User declined confirmation for: ${call.name}")
                            continue
                        }
                    }
                    GateResult.YIELD -> {
                        val yieldOutcome = AgentOutcome(false, gate.reason, stepCount, System.currentTimeMillis() - startTime)
                        task.listener?.onFinished(yieldOutcome)
                        return yieldOutcome
                    }
                    GateResult.ALLOW -> { /* Proceed */ }
                }

                val beforeFp = snapshot.fingerprint
                val actionResult = ActionExecutor.execute(call, context, task.listener)
                service.waitForIdle(AgentConfig.IDLE_TIMEOUT_MS, AgentConfig.IDLE_QUIET_MS)
                val afterSnap = ScreenObserver.capture(withScreenshot = false)
                val afterFp = afterSnap.fingerprint

                val stepRecord = StepRecord(
                    step = stepCount,
                    call = call,
                    result = actionResult,
                    fingerprintBefore = beforeFp,
                    fingerprintAfter = afterFp
                )
                history.add(stepRecord)

                val stepDuration = System.currentTimeMillis() - stepStart
                AgentLogger.logStep(task.goal, stepCount, call.intent, call.name, actionResult, stepDuration, afterFp)
            }
        }

        val timeoutMsg = if (stepCount >= maxSteps) "Max steps ($maxSteps) exceeded" else "Task timeout (6 min)"
        val timeoutOutcome = AgentOutcome(false, "Task pura nahi ho paya: $timeoutMsg", stepCount, System.currentTimeMillis() - startTime, "TIMEOUT")
        task.listener?.onFinished(timeoutOutcome)
        return timeoutOutcome
    }
}
