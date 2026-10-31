package com.example.llm

import android.util.Log
import com.example.data.local.ProviderSettingsManager
import com.example.data.remote.ModelResolver
import com.example.data.remote.ModelRole
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

enum class LlmRole {
    PLANNER,
    CODER,
    FIXER,
    REVIEWER,
    SUMMARIZER
}

/**
 * Resilient multi-provider gateway for LLM calls with role-based routing,
 * budget reservation, automatic payload shrinking on 413, and intelligent 429 waits.
 */
object LlmGateway {
    private const val TAG = "LlmGateway"

    // Dead model cache per provider+model
    private val deadModels = ConcurrentHashMap.newKeySet<String>()

    fun markModelDead(provider: String, model: String) {
        val key = "${provider.lowercase()}:::${model.lowercase()}"
        deadModels.add(key)
        Log.w(TAG, "Marked model $key as dead for session")
    }

    fun isModelDead(provider: String, model: String): Boolean {
        val key = "${provider.lowercase()}:::${model.lowercase()}"
        return deadModels.contains(key)
    }

    /**
     * Executes a call with full resilience, rate-limit awareness, and payload shrinking.
     */
    suspend fun call(
        role: LlmRole,
        provider: String,
        apiKey: String,
        model: String,
        request: LlmRequest,
        onChunk: suspend (String) -> Unit = {},
        onWaiting: (suspend (reason: String, secondsRemaining: Int) -> Unit)? = null,
        onThinking: (suspend (thoughtChunk: String) -> Unit)? = null
    ): Result<LlmResponse> = callObserved(role, provider, apiKey, model, request, onChunk, onWaiting, onThinking, null)

    /** Same gateway and retries as [call], with an explicit callback for every HTTP/model attempt. */
    suspend fun callObserved(
        role: LlmRole,
        provider: String,
        apiKey: String,
        model: String,
        request: LlmRequest,
        onChunk: suspend (String) -> Unit = {},
        onWaiting: (suspend (reason: String, secondsRemaining: Int) -> Unit)? = null,
        onThinking: (suspend (thoughtChunk: String) -> Unit)? = null,
        onAttemptStart: (suspend (attempt: Int, model: String) -> Unit)? = null
    ): Result<LlmResponse> {
        var attempts = 0
        val cleanKey = KeyStoreCrypto.decrypt(apiKey).trim()
            .removeSurrounding("\"").removeSurrounding("'").removePrefix("Bearer ").removePrefix("bearer ").trim()

        val profile = ProviderProfile.detectProviderFromKey(cleanKey, provider)
        var activeModel = model.trim().removePrefix("models/")
        if (activeModel.isBlank()) {
            activeModel = profile.defaultFallbackModels.firstOrNull() ?: "default"
        }
        val isGeminiProfile = profile.id.equals("Gemini", ignoreCase = true)
        if (isGeminiProfile && (GeminiModelCatalog.isRetired(activeModel) || GeminiModelCatalog.isDead(activeModel))) {
            activeModel = GeminiModelCatalog.pick(cleanKey)
        }

        // Determine appropriate reasoning effort per role
        val effort = when (role) {
            LlmRole.CODER -> "low"
            LlmRole.SUMMARIZER -> "low"
            LlmRole.PLANNER -> "medium"
            LlmRole.FIXER -> if (request.mode == "high_fix") "high" else "medium"
            LlmRole.REVIEWER -> "low"
        }

        var currentRequest = request.copy(
            reasoningEffort = if (profile.dialect.reasoningPolicy.supportsReasoningEffort) effort else null
        )

        var shrinkAttempts = 0
        val maxShrinkAttempts = 2
        var modelSwitchCount = 0
        val maxModelSwitches = 2

        while (true) {
            // Estimate tokens and reserve budget
            val estInput = TokenBudgetManager.estimateTokens(
                currentRequest.systemPrompt + currentRequest.prompt,
                isCode = (role == LlmRole.CODER || role == LlmRole.FIXER)
            )
            val desiredOutput = currentRequest.maxTokens ?: when (role) {
                LlmRole.PLANNER -> 2500
                LlmRole.CODER -> 3500
                LlmRole.FIXER -> 2500
                LlmRole.REVIEWER -> 1800
                LlmRole.SUMMARIZER -> 1000
            }

            val allocatedOutput = TokenBudgetManager.reserve(
                provider = profile.id,
                model = activeModel,
                inputTokens = estInput,
                maxOutputTokens = desiredOutput,
                onWaiting = onWaiting
            )

            val callReq = currentRequest.copy(maxTokens = allocatedOutput)

            val result = LlmClient.streamDetailedObserved(
                providerName = if (profile.id == "Custom") provider else profile.id,
                apiKey = cleanKey,
                modelName = activeModel,
                request = callReq,
                onChunk = onChunk,
                onThinking = onThinking,
                onAttemptStart = { actualModel ->
                    attempts++
                    onAttemptStart?.invoke(attempts, actualModel)
                }
            )

            if (result.isSuccess) {
                val resp = result.getOrNull()!!
                // Calibrate budget manager with real tokens
                if (resp.promptTokens != null && resp.completionTokens != null) {
                    TokenBudgetManager.recordActualUsage(
                        profile.id,
                        activeModel,
                        resp.promptTokens,
                        resp.completionTokens
                    )
                }
                return Result.success(resp)
            }

            val error = result.exceptionOrNull() as? LlmError
                ?: LlmError(profile.displayName, activeModel, null, result.exceptionOrNull()?.message ?: "Error", LlmErrorCategory.UNKNOWN)

            // Case 1: TOO_LARGE (413 / TPM / Context overflow) -> Never resend the same payload!
            if (error.category == LlmErrorCategory.TOO_LARGE) {
                Log.w(TAG, "TOO_LARGE from $activeModel: ${error.rawMessage}")
                ModelLimitsStore.recordLimitFromError(profile.id, activeModel, error.scope, error.limitTokens, error.requestedTokens)

                if (shrinkAttempts < maxShrinkAttempts) {
                    shrinkAttempts++
                    Log.i(TAG, "Shrinking payload (attempt $shrinkAttempts of $maxShrinkAttempts)...")
                    onWaiting?.invoke("Request exceeded per-minute limit. Shrinking context (attempt $shrinkAttempts)...", 1)

                    currentRequest = shrinkPayload(currentRequest, shrinkAttempts)
                    continue
                } else if (modelSwitchCount < maxModelSwitches) {
                    // Try another model with higher limits or smaller footprint
                    modelSwitchCount++
                    val alt = findAlternativeModel(profile, activeModel)
                    if (alt != null) {
                        Log.i(TAG, "Switching model from $activeModel to $alt after shrink limit")
                        activeModel = alt
                        shrinkAttempts = 0
                        continue
                    }
                }
                return Result.failure(error)
            }

            // Case 2: Daily quota exhausted (TPD / RPD)
            if (error.category == LlmErrorCategory.RATE_LIMIT && (error.scope == "TPD" || error.scope == "RPD")) {
                Log.w(TAG, "Daily quota exhausted for $activeModel: ${error.rawMessage}")
                markModelDead(profile.id, activeModel)
                if (modelSwitchCount < maxModelSwitches) {
                    modelSwitchCount++
                    val alt = findAlternativeModel(profile, activeModel)
                    if (alt != null) {
                        activeModel = alt
                        continue
                    }
                }
                return Result.failure(error)
            }

            // Case 3: Rate limit (TPM/RPM) with retry hint
            if (error.category == LlmErrorCategory.RATE_LIMIT) {
                val waitSeconds = (error.retryAfterSeconds ?: 3L).coerceIn(1L, 90L)
                Log.w(TAG, "Rate limited on $activeModel. Waiting ${waitSeconds}s...")
                onWaiting?.invoke("Rate limit cooldown (${waitSeconds}s)", waitSeconds.toInt())
                delay(waitSeconds * 1000L)
                continue
            }

            // Case 4: Model not found or deprecated
            if (error.category == LlmErrorCategory.MODEL_NOT_FOUND) {
                markModelDead(profile.id, activeModel)
                val alt = if (isGeminiProfile) GeminiModelCatalog.alternative(cleanKey, activeModel, setOf(activeModel))
                          else findAlternativeModel(profile, activeModel)
                if (alt != null && modelSwitchCount < (if (isGeminiProfile) 6 else maxModelSwitches)) {
                    modelSwitchCount++
                    activeModel = alt
                    continue
                }
                return Result.failure(error)
            }

            // Other errors
            return Result.failure(error)
        }
    }

    private fun shrinkPayload(req: LlmRequest, level: Int): LlmRequest {
        return when (level) {
            1 -> {
                // Drop optional history, compress system prompt
                val trimmedSys = req.systemPrompt.lines()
                    .filter { !it.trim().startsWith("- ") || it.length < 120 }
                    .joinToString("\n")
                val trimmedPrompt = if (req.prompt.length > 3000) req.prompt.take(3000) else req.prompt
                req.copy(
                    systemPrompt = trimmedSys,
                    prompt = trimmedPrompt,
                    history = emptyList(),
                    maxTokens = req.maxTokens?.let { (it * 0.75).toInt().coerceAtLeast(1024) }
                )
            }
            else -> {
                // Heavy shrink: short directive only, compact prompt
                val minimalSys = req.systemPrompt.lines().take(8).joinToString("\n")
                val excerptPrompt = if (req.prompt.length > 1800) req.prompt.take(1800) else req.prompt
                req.copy(
                    systemPrompt = minimalSys,
                    prompt = excerptPrompt,
                    history = emptyList(),
                    maxTokens = req.maxTokens?.let { (it * 0.50).toInt().coerceAtLeast(800) }
                )
            }
        }
    }

    private fun findAlternativeModel(profile: ProviderProfile, currentModel: String): String? {
        val candidates = profile.defaultFallbackModels
        for (cand in candidates) {
            if (!cand.equals(currentModel, ignoreCase = true) && !isModelDead(profile.id, cand)) {
                return cand
            }
        }
        return null
    }
}
