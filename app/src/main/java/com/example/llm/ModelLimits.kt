package com.example.llm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * Thrown when an LLM rejects a request due to context window, max tokens, or token-per-minute limits.
 * Callers (e.g., AutonomousAppBuilder) catch this to partition prompts across multiple phases.
 */
class RequestTooLargeException(
    val provider: String,
    val model: String,
    val limitTokens: Long,
    val requestedTokens: Long,
    val detail: String
) : Exception("Request to $provider ($model) exceeded limit ($requestedTokens > $limitTokens tokens): $detail")

/**
 * Runtime snapshot of provider rate-limit telemetry.
 */
data class ModelRateLimitInfo(
    val limitTokens: Long? = null,
    val remainingTokens: Long? = null,
    val resetTokensSeconds: Long? = null,
    val limitRequests: Long? = null,
    val remainingRequests: Long? = null,
    val resetRequestsSeconds: Long? = null
)

/**
 * Learned and cached capabilities / constraints for a specific model.
 */
data class ModelLimits(
    val provider: String,
    val model: String,
    val contextWindowTokens: Int = 128_000,
    val maxOutputTokens: Int = 8192,
    val tokensPerMinute: Int? = null,
    val requestsPerMinute: Int? = null,
    val tokensPerDay: Long? = null,
    val requestsPerDay: Long? = null,
    val remainingTokens: Long? = null,
    val resetTokensSeconds: Long? = null,
    val requiresCompletionTokensParam: Boolean = false,
    val supportsTemperature: Boolean = true,
    val supportsSystemRole: Boolean = true
)

/**
 * Persistent store for learned model limits across app sessions.
 */
object ModelLimitsStore {
    private const val TAG = "ModelLimitsStore"
    private const val PREFS_NAME = "saif_model_limits_v1"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun keyFor(provider: String, model: String) = "${provider.lowercase()}:::${model.lowercase()}"

    fun get(provider: String, model: String): ModelLimits {
        val raw = prefs?.getString(keyFor(provider, model), null)
        if (raw != null) {
            try {
                val json = JSONObject(raw)
                return ModelLimits(
                    provider = provider,
                    model = model,
                    contextWindowTokens = json.optInt("contextWindow", 128_000),
                    maxOutputTokens = json.optInt("maxOutput", 8192),
                    tokensPerMinute = if (json.has("tpm")) json.getInt("tpm") else null,
                    requestsPerMinute = if (json.has("rpm")) json.getInt("rpm") else null,
                    tokensPerDay = if (json.has("tpd")) json.getLong("tpd") else null,
                    requestsPerDay = if (json.has("rpd")) json.getLong("rpd") else null,
                    remainingTokens = if (json.has("remTokens")) json.getLong("remTokens") else null,
                    resetTokensSeconds = if (json.has("resetTokensSec")) json.getLong("resetTokensSec") else null,
                    requiresCompletionTokensParam = json.optBoolean("requiresCompletionTokens", false),
                    supportsTemperature = json.optBoolean("supportsTemperature", true),
                    supportsSystemRole = json.optBoolean("supportsSystemRole", true)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing saved limits: ${e.message}")
            }
        }

        // Conservative defaults by provider / model name patterns
        val requiresCompletion = model.contains("o1", true) || model.contains("o3", true) || model.contains("gpt-5", true) || model.contains("gpt-6", true) || model.contains("astra", true)
        val supportsTemp = !(model.contains("o1", true) || model.contains("o3", true))
        val isGroq = provider.equals("Groq", true)
        val defaultTpm = if (isGroq) {
            if (model.contains("70b", true)) 6000 else 8000
        } else null

        val maxOut = when {
            isGroq -> if (model.contains("70b", true)) 8192 else 4096
            provider.equals("OpenRouter", true) -> 8192
            provider.equals("Anthropic", true) -> 8192
            provider.equals("Gemini", true) -> 8192
            else -> 4096
        }

        return ModelLimits(
            provider = provider,
            model = model,
            contextWindowTokens = 128_000,
            maxOutputTokens = maxOut,
            tokensPerMinute = defaultTpm,
            requiresCompletionTokensParam = requiresCompletion,
            supportsTemperature = supportsTemp,
            supportsSystemRole = true
        )
    }

    fun update(limits: ModelLimits) {
        try {
            val json = JSONObject().apply {
                put("contextWindow", limits.contextWindowTokens)
                put("maxOutput", limits.maxOutputTokens)
                limits.tokensPerMinute?.let { put("tpm", it) }
                limits.requestsPerMinute?.let { put("rpm", it) }
                limits.tokensPerDay?.let { put("tpd", it) }
                limits.requestsPerDay?.let { put("rpd", it) }
                limits.remainingTokens?.let { put("remTokens", it) }
                limits.resetTokensSeconds?.let { put("resetTokensSec", it) }
                put("requiresCompletionTokens", limits.requiresCompletionTokensParam)
                put("supportsTemperature", limits.supportsTemperature)
                put("supportsSystemRole", limits.supportsSystemRole)
            }
            prefs?.edit()?.putString(keyFor(limits.provider, limits.model), json.toString())?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving limits: ${e.message}")
        }
    }

    fun recordRateLimitHeaders(provider: String, model: String, headers: okhttp3.Headers) {
        try {
            val limitTokens = headers["x-ratelimit-limit-tokens"]?.toLongOrNull()
            val remTokens = headers["x-ratelimit-remaining-tokens"]?.toLongOrNull()
            val resetTokensSec = parseResetSeconds(headers["x-ratelimit-reset-tokens"])
            val limitReq = headers["x-ratelimit-limit-requests"]?.toIntOrNull()
            val remReq = headers["x-ratelimit-remaining-requests"]?.toIntOrNull()

            if (limitTokens != null || remTokens != null || limitReq != null) {
                val current = get(provider, model)
                val updated = current.copy(
                    tokensPerMinute = (limitTokens?.toInt() ?: current.tokensPerMinute),
                    requestsPerMinute = (limitReq ?: current.requestsPerMinute),
                    remainingTokens = remTokens ?: current.remainingTokens,
                    resetTokensSeconds = resetTokensSec ?: current.resetTokensSeconds
                )
                update(updated)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error recording rate limit headers: ${e.message}")
        }
    }

    fun recordLimitFromError(provider: String, model: String, scope: String?, limit: Long?, requested: Long?) {
        if (limit == null || limit <= 0) return
        try {
            val current = get(provider, model)
            val updated = when (scope?.uppercase()) {
                "TPD", "TP_DAY" -> current.copy(tokensPerDay = limit)
                "RPD", "RP_DAY" -> current.copy(requestsPerDay = limit)
                "RPM" -> current.copy(requestsPerMinute = limit.toInt())
                else -> current.copy(tokensPerMinute = limit.toInt())
            }
            update(updated)
        } catch (e: Exception) {
            Log.w(TAG, "Error recording limit from error: ${e.message}")
        }
    }

    private fun parseResetSeconds(headerVal: String?): Long? {
        if (headerVal.isNullOrBlank()) return null
        headerVal.toLongOrNull()?.let { return it }
        val secondsMatch = Regex("""([0-9.]+)\s*s""", RegexOption.IGNORE_CASE).find(headerVal)
        if (secondsMatch != null) return secondsMatch.groupValues[1].toDoubleOrNull()?.toLong()
        val msMatch = Regex("""([0-9]+)\s*ms""", RegexOption.IGNORE_CASE).find(headerVal)
        if (msMatch != null) return (msMatch.groupValues[1].toLongOrNull() ?: 0L) / 1000L
        return null
    }
}
