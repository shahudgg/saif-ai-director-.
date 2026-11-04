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

    /**
     * Same gateway and retries as [call], with an explicit callback for every HTTP/model attempt.
     *
     * Round 14: when the ACTIVE provider runs out (daily quota, auth, retired model, or too many
     * per-minute waits) it automatically switches to a saved backup provider — provider, key and model —
     * so a finished script never dies because one free tier hit its limit. Every switch is honest:
     * the original error is only returned when every backup has also been tried.
     */
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

        var activeProfile = ProviderProfile.detectProviderFromKey(cleanKey, provider)
        var activeKey = cleanKey
        var activeProviderName = provider
        var activeModel = model.trim().removePrefix("models/")
        if (activeModel.isBlank()) {
            activeModel = activeProfile.defaultFallbackModels.firstOrNull() ?: "default"
        }
        if (activeProfile.id.equals("Gemini", ignoreCase = true) &&
            (GeminiModelCatalog.isRetired(activeModel) || GeminiModelCatalog.isDead(activeModel))) {
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
            reasoningEffort = if (activeProfile.dialect.reasoningPolicy.supportsReasoningEffort) effort else null
        )

        var shrinkAttempts = 0
        val maxShrinkAttempts = 2
        var modelSwitchCount = 0
        val maxModelSwitches = 2
        var rateLimitWaits = 0
        var vendorSwitchCount = 0
        val maxVendorSwitches = 3
        val triedProviders = LinkedHashSet<String>()
        val triedModels = LinkedHashSet<String>()
        triedProviders.add(activeProfile.id.lowercase())
        triedModels.add(activeModel.lowercase())
        var lastError: LlmError? = null

        /** Moves to the next saved backup provider. False = there is nothing left to try. */
        fun switchProvider(): Boolean {
            if (vendorSwitchCount >= maxVendorSwitches) return false
            val next = BackupProviders.next(triedProviders, triedModels) ?: return false
            val profile = ProviderProfile.getById(next.first)
            activeProfile = profile
            activeKey = next.second
            activeModel = next.third
            activeProviderName = if (profile.id.equals("Custom", true)) next.first else profile.id
            triedProviders.add(profile.id.lowercase())
            triedModels.add(activeModel.lowercase())
            vendorSwitchCount++
            modelSwitchCount = 0
            shrinkAttempts = 0
            rateLimitWaits = 0
            currentRequest = currentRequest.copy(
                reasoningEffort = if (profile.dialect.reasoningPolicy.supportsReasoningEffort) effort else null
            )
            Log.i(TAG, "Auto-switched provider to ${profile.id} / $activeModel (limit reached on the previous one)")
            return true
        }

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
                provider = activeProfile.id,
                model = activeModel,
                inputTokens = estInput,
                maxOutputTokens = desiredOutput,
                onWaiting = onWaiting
            )

            val callReq = currentRequest.copy(maxTokens = allocatedOutput)

            val result = LlmClient.streamDetailedObserved(
                providerName = activeProviderName,
                apiKey = activeKey,
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
                if (resp.promptTokens != null && resp.completionTokens != null) {
                    TokenBudgetManager.recordActualUsage(
                        activeProfile.id,
                        activeModel,
                        resp.promptTokens,
                        resp.completionTokens
                    )
                }
                return Result.success(resp)
            }

            val error = result.exceptionOrNull() as? LlmError
                ?: LlmError(activeProfile.displayName, activeModel, null, result.exceptionOrNull()?.message ?: "Error", LlmErrorCategory.UNKNOWN)
            lastError = error

            // Case 1: TOO_LARGE (413 / TPM / Context overflow) -> Never resend the same payload!
            if (error.category == LlmErrorCategory.TOO_LARGE) {
                Log.w(TAG, "TOO_LARGE from $activeModel: ${error.rawMessage}")
                ModelLimitsStore.recordLimitFromError(activeProfile.id, activeModel, error.scope, error.limitTokens, error.requestedTokens)

                if (shrinkAttempts < maxShrinkAttempts) {
                    shrinkAttempts++
                    Log.i(TAG, "Shrinking payload (attempt $shrinkAttempts of $maxShrinkAttempts)...")
                    onWaiting?.invoke("Request exceeded per-minute limit. Shrinking context (attempt $shrinkAttempts)...", 1)
                    currentRequest = shrinkPayload(currentRequest, shrinkAttempts)
                    continue
                } else if (modelSwitchCount < maxModelSwitches) {
                    modelSwitchCount++
                    val alt = findAlternativeModel(activeProfile, activeModel)
                    if (alt != null) {
                        Log.i(TAG, "Switching model from $activeModel to $alt after shrink limit")
                        activeModel = alt
                        triedModels.add(alt.lowercase())
                        shrinkAttempts = 0
                        continue
                    }
                }
                if (switchProvider()) continue
                return Result.failure(error)
            }

            // Case 2: Daily quota exhausted (TPD / RPD) or provider says the key has no credit
            if (error.category == LlmErrorCategory.RATE_LIMIT && (error.scope == "TPD" || error.scope == "RPD")) {
                Log.w(TAG, "Daily quota exhausted for $activeModel: ${error.rawMessage}")
                markModelDead(activeProfile.id, activeModel)
                if (modelSwitchCount < maxModelSwitches) {
                    modelSwitchCount++
                    val alt = findAlternativeModel(activeProfile, activeModel)
                    if (alt != null) {
                        activeModel = alt
                        triedModels.add(alt.lowercase())
                        continue
                    }
                }
                if (switchProvider()) continue
                return Result.failure(error)
            }

            // Case 3: Rate limit (TPM/RPM) with retry hint — wait a few times, then move provider
            if (error.category == LlmErrorCategory.RATE_LIMIT) {
                val waitSeconds = (error.retryAfterSeconds ?: 3L).coerceIn(1L, 90L)
                Log.w(TAG, "Rate limited on $activeModel. Waiting ${waitSeconds}s...")
                rateLimitWaits++
                if (rateLimitWaits > 4) {
                    if (switchProvider()) continue
                    return Result.failure(error)
                }
                onWaiting?.invoke("Rate limit cooldown (${waitSeconds}s)", waitSeconds.toInt())
                delay(waitSeconds * 1000L)
                continue
            }

            // Case 4: Model not found or deprecated
            if (error.category == LlmErrorCategory.MODEL_NOT_FOUND) {
                markModelDead(activeProfile.id, activeModel)
                val alt = if (activeProfile.id.equals("Gemini", true))
                    GeminiModelCatalog.alternative(activeKey, activeModel, triedModels)
                else findAlternativeModel(activeProfile, activeModel)
                if (alt != null && modelSwitchCount < (if (activeProfile.id.equals("Gemini", true)) 6 else maxModelSwitches)) {
                    modelSwitchCount++
                    activeModel = alt
                    triedModels.add(alt.lowercase())
                    continue
                }
                if (switchProvider()) continue
                return Result.failure(error)
            }

            // Case 5: Auth / permission / unknown provider problem -> another provider may still work
            if (error.category == LlmErrorCategory.AUTH || error.category == LlmErrorCategory.UNKNOWN) {
                if (switchProvider()) continue
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
