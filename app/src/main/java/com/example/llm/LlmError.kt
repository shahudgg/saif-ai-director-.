package com.example.llm

/**
 * Categorization of LLM failures for actionable diagnostics and self-healing.
 */
enum class LlmErrorCategory {
    AUTH,             // 401, 403 Invalid API key or unauthorized
    RATE_LIMIT,       // 429 TPM/RPM quota exceeded
    TOO_LARGE,        // 413 or context window / max output overflow
    MODEL_NOT_FOUND,  // 404 Model does not exist or deprecated
    BAD_REQUEST,      // 400 Parameter or role incompatibility
    NETWORK,          // DNS, connection drop, SSL failure
    TIMEOUT,          // Coroutine or socket read timeout
    SERVER,           // 500, 502, 503, 504 upstream outages
    UNKNOWN
}

/**
 * Structured LLM error containing technical cause, HTTP status, and human-friendly explanation.
 */
data class LlmError(
    val provider: String,
    val model: String,
    val httpCode: Int?,
    val rawMessage: String,
    val category: LlmErrorCategory,
    val retryAfterSeconds: Long? = null,
    val scope: String? = null,
    val limitTokens: Long? = null,
    val requestedTokens: Long? = null,
    val retryAfterMs: Long? = null
) : Exception("[$provider · $model${httpCode?.let { " · HTTP $it" } ?: ""}] $rawMessage") {

    fun formatHumanMessage(): String {
        val provName = provider.ifBlank { "AI Provider" }
        val modelName = model.ifBlank { "model" }
        return when (category) {
            LlmErrorCategory.AUTH ->
                "⚠️ $provName ($modelName): Invalid API Key (HTTP ${httpCode ?: 401}). Please check your key in Settings ⚙️ ➔ AI Provider."
            LlmErrorCategory.TOO_LARGE -> {
                val scopeStr = scope ?: "per-minute"
                if (limitTokens != null && requestedTokens != null) {
                    "📏 $provName ($modelName): This single request ($requestedTokens tokens) exceeds the $scopeStr limit ($limitTokens tokens) (HTTP ${httpCode ?: 413}). Splitting it automatically into smaller tasks."
                } else {
                    "📏 $provName ($modelName): This single request is bigger than the $scopeStr limit (HTTP ${httpCode ?: 413}), splitting it automatically."
                }
            }
            LlmErrorCategory.RATE_LIMIT -> {
                val isDaily = scope == "TPD" || scope == "RPD" || rawMessage.contains("per day", true)
                val isQuota = rawMessage.contains("quota", true) || rawMessage.contains("credit", true) || rawMessage.contains("balance", true) || rawMessage.contains("insufficient", true)
                if (isDaily) {
                    val waitHint = retryAfterSeconds?.let { " Resets in ${it}s." } ?: ""
                    "⏳ $provName ($modelName): Daily limit ($scope) reached.$waitHint Please switch to another model or key in Settings ⚙️."
                } else if (provName.contains("Ashna", ignoreCase = true)) {
                    if (modelName.contains("astra", true) || modelName.contains("gpt-6", true) || modelName.contains("claude", true)) {
                        "⏳ Ashna AI ($modelName): Quota / Credits exceeded (HTTP ${httpCode ?: 429}). Frontier models require paid credits on Ashna AI. Please select free/trial models like 'ashna-x1' or 'ashna-x1-flash' in Settings ⚙️ ➔ AI Provider, or check your balance at app.ashna.ai."
                    } else {
                        "⏳ Ashna AI ($modelName): Rate limit / Quota exceeded (HTTP ${httpCode ?: 429}). Please check your account credits at app.ashna.ai or try 'ashna-x1' / 'ashna-x1-flash'."
                    }
                } else if (isQuota) {
                    "⏳ $provName ($modelName): Account quota or credits exceeded (HTTP ${httpCode ?: 429}). Please check your API credits/billing or select a free/lighter model in Settings ⚙️."
                } else {
                    val waitText = retryAfterSeconds?.let { " Retry in ${it}s." } ?: " Please wait a moment."
                    "⏳ $provName ($modelName): Rate limit (${scope ?: "TPM/RPM"}) exceeded (HTTP ${httpCode ?: 429}).$waitText"
                }
            }
            LlmErrorCategory.MODEL_NOT_FOUND ->
                "🔍 $provName ($modelName): Model '$model' was not found or is deprecated (HTTP ${httpCode ?: 404}). Please select another model in settings."
            LlmErrorCategory.BAD_REQUEST ->
                "⚠️ $provName ($modelName): Request rejected by provider (HTTP ${httpCode ?: 400}): ${KeyStoreCrypto.redact(rawMessage).take(120)}"
            LlmErrorCategory.TIMEOUT ->
                "⏱️ $provName ($modelName): Request timed out after waiting for upstream response. Please check your network connection."
            LlmErrorCategory.SERVER ->
                "☁️ $provName ($modelName): Upstream server error (HTTP ${httpCode ?: 500}). The service is temporarily unavailable."
            LlmErrorCategory.NETWORK ->
                "📡 Network error connecting to $provName. Please verify your internet connection."
            LlmErrorCategory.UNKNOWN ->
                "⚠️ $provName ($modelName): ${KeyStoreCrypto.redact(rawMessage)}"
        }
    }
}

/**
 * Record of an individual model attempt within an execution chain.
 */
data class AttemptRecord(
    val provider: String,
    val model: String,
    val isUserConfiguredKey: Boolean,
    val success: Boolean,
    val error: LlmError? = null,
    val latencyMs: Long = 0L
)

/**
 * Comprehensive audit trail of all attempts made to fulfill a request.
 */
data class AttemptReport(
    val attempts: MutableList<AttemptRecord> = mutableListOf()
) {
    fun addAttempt(record: AttemptRecord) {
        attempts.add(record)
    }

    fun primaryFailure(preferredModel: String = ""): LlmError? {
        if (preferredModel.isNotBlank()) {
            val matching = attempts.firstOrNull { it.isUserConfiguredKey && it.model.equals(preferredModel, ignoreCase = true) && it.error != null }?.error
            if (matching != null) return matching
        }
        // Return first error from user-configured key if present, otherwise last error
        return attempts.firstOrNull { it.isUserConfiguredKey && it.error != null }?.error
            ?: attempts.lastOrNull { it.error != null }?.error
    }

    fun toDiagnosticSummary(): String {
        if (attempts.isEmpty()) return "No attempts executed."
        val sb = StringBuilder("Execution Report (${attempts.size} attempt(s)):\n")
        attempts.forEachIndexed { i, a ->
            val status = if (a.success) "SUCCESS (${a.latencyMs}ms)" else "FAILED: ${a.error?.category} (HTTP ${a.error?.httpCode})"
            sb.append("${i + 1}. ${a.provider} [${a.model}] ${if (a.isUserConfiguredKey) "(User Key)" else "(Fallback)"} ➔ $status\n")
        }
        return sb.toString().trimEnd()
    }
}
