package com.example.llm

/**
 * Authentication mechanism used by the upstream AI provider.
 */
enum class AuthStyle {
    BEARER_TOKEN,
    QUERY_PARAM_KEY,
    X_API_KEY_HEADER,
    ANTHROPIC_HEADER
}

/**
 * Reasoning policy for providers that support explicit reasoning configuration.
 */
data class ReasoningPolicy(
    val supportsReasoningEffort: Boolean = false,
    val parameterName: String = "reasoning_effort",
    val allowedEfforts: List<String> = listOf("low", "medium", "high")
)

/**
 * Dialect constraints and supported protocol features for an LLM endpoint.
 */
data class ApiDialect(
    val tokenParamName: String = "max_tokens", // "max_tokens" or "max_completion_tokens"
    val supportsTemperature: Boolean = true,
    val supportsSystemRole: Boolean = true,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = true,
    val supportsJsonMode: Boolean = true,
    val supportsStreamingUsage: Boolean = false,
    val requiresThinkingConfigForReasoning: Boolean = false,
    val reasoningPolicy: ReasoningPolicy = ReasoningPolicy()
)

/**
 * Metadata profile for an AI service provider.
 */
data class ProviderProfile(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val authStyle: AuthStyle,
    val chatPath: String = "/chat/completions",
    val modelsPath: String = "/models",
    val sttPath: String? = "/audio/transcriptions",
    val keyPrefixes: List<String> = emptyList(),
    val dialect: ApiDialect = ApiDialect(),
    val defaultFallbackModels: List<String> = emptyList()
) {
    companion object {
        val GEMINI = ProviderProfile(
            id = "Gemini",
            displayName = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            authStyle = AuthStyle.QUERY_PARAM_KEY,
            chatPath = "", // uses /models/{model}:streamGenerateContent
            modelsPath = "/models",
            sttPath = null, // uses Gemini multimodal audio input
            keyPrefixes = listOf("AIza", "AQ."),
            dialect = ApiDialect(
                tokenParamName = "maxOutputTokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true
            ),
            defaultFallbackModels = GeminiModelCatalog.STATIC_FALLBACKS
        )

        val GROQ = ProviderProfile(
            id = "Groq",
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = "/audio/transcriptions",
            keyPrefixes = listOf("gsk_"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true,
                reasoningPolicy = ReasoningPolicy(
                    supportsReasoningEffort = true,
                    parameterName = "reasoning_effort",
                    allowedEfforts = listOf("low", "medium", "high")
                )
            ),
            defaultFallbackModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "openai/gpt-oss-20b")
        )

        val OPENROUTER = ProviderProfile(
            id = "OpenRouter",
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("sk-or-"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf(
                "meta-llama/llama-3.3-70b-instruct:free",
                "google/gemini-2.0-flash-001",
                "qwen/qwen-2.5-coder-32b-instruct:free",
                "meta-llama/llama-3.1-8b-instruct:free",
                "deepseek/deepseek-chat"
            )
        )

        val OPENAI = ProviderProfile(
            id = "OpenAI",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = "/audio/transcriptions",
            keyPrefixes = listOf("sk-proj-", "sk-admin-"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf("gpt-4o-mini", "gpt-4o", "o3-mini", "gpt-4-turbo")
        )

        val ANTHROPIC = ProviderProfile(
            id = "Anthropic",
            displayName = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1",
            authStyle = AuthStyle.ANTHROPIC_HEADER,
            chatPath = "/messages",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("sk-ant-"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = false, // Anthropic puts system in top-level 'system' field
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = false
            ),
            defaultFallbackModels = listOf("claude-3-7-sonnet-20250219", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
        )

        val DEEPSEEK = ProviderProfile(
            id = "DeepSeek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = emptyList(),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = false,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
        )

        val MISTRAL = ProviderProfile(
            id = "Mistral",
            displayName = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = "/audio/transcriptions",
            keyPrefixes = listOf("mistral-"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf("mistral-large-latest", "mistral-small-latest", "codestral-latest")
        )

        val XAI = ProviderProfile(
            id = "xAI",
            displayName = "xAI (Grok)",
            baseUrl = "https://api.x.ai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("xai-"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf("grok-2-1212", "grok-2-vision-1212", "grok-beta")
        )

        val TOGETHER = ProviderProfile(
            id = "Together",
            displayName = "Together AI",
            baseUrl = "https://api.together.xyz/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("together-"),
            defaultFallbackModels = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3")
        )

        val PERPLEXITY = ProviderProfile(
            id = "Perplexity",
            displayName = "Perplexity",
            baseUrl = "https://api.perplexity.ai",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("pplx-"),
            defaultFallbackModels = listOf("sonar-pro", "sonar")
        )

        val CEREBRAS = ProviderProfile(
            id = "Cerebras",
            displayName = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("csk-"),
            defaultFallbackModels = listOf("llama-3.3-70b", "llama3.1-8b")
        )

        val FIREWORKS = ProviderProfile(
            id = "Fireworks",
            displayName = "Fireworks AI",
            baseUrl = "https://api.fireworks.ai/inference/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("fw_", "fwi_"),
            defaultFallbackModels = listOf("accounts/fireworks/models/llama-v3p3-70b-instruct", "accounts/fireworks/models/deepseek-v3")
        )

        val SAMBANOVA = ProviderProfile(
            id = "Sambanova",
            displayName = "SambaNova",
            baseUrl = "https://api.sambanova.ai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            defaultFallbackModels = listOf("Qwen2.5-Coder-32B-Instruct", "Meta-Llama-3.3-70B-Instruct")
        )

        val SILICONFLOW = ProviderProfile(
            id = "SiliconFlow",
            displayName = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            defaultFallbackModels = listOf("Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3")
        )

        val OPENCODE = ProviderProfile(
            id = "OpenCode.ai",
            displayName = "OpenCode.ai",
            baseUrl = "https://opencode.ai/inference/openai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("opencode", "oc-"),
            defaultFallbackModels = listOf("glm-5.1", "deepseek-v4-flash", "kimi-k2.6", "minimax-m2.7", "ling-3.0-flash-fin-free", "claude-sonnet-4-5")
        )

        val INCEPTION_LABS = ProviderProfile(
            id = "inceptionlabs.ai",
            displayName = "inceptionlabs.ai",
            baseUrl = "https://api.inceptionlabs.ai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("inception", "il_", "sk-il-"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = false,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf(
                "mercury-2.5",
                "mercury-2",
                "mercury-voice",
                "mercury-coder-small",
                "mercury-edit-2",
                "mercury-router"
            )
        )

        val ATRIA_ASI = ProviderProfile(
            id = "api.atria-asi.ai",
            displayName = "api.atria-asi.ai",
            baseUrl = "https://api.atria-asi.ai/v1",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("atria", "asi-", "sk-atria-", "atria_sk_"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf(
                "Atria-Dawn-Preview",
                "Atria-Dawn-Preview-FP8"
            )
        )

        val ASHNA_AI = ProviderProfile(
            id = "Ashna AI",
            displayName = "Ashna AI",
            baseUrl = "https://api.ashna.ai/v1/api",
            authStyle = AuthStyle.BEARER_TOKEN,
            chatPath = "/chat/completions",
            modelsPath = "/models",
            sttPath = null,
            keyPrefixes = listOf("ashna_", "ash_", "sk-ashna-", "ashna-", "asana_", "asana-", "sk-asana-", "asa_"),
            dialect = ApiDialect(
                tokenParamName = "max_tokens",
                supportsTemperature = true,
                supportsSystemRole = true,
                supportsTools = true,
                supportsVision = true,
                supportsJsonMode = true
            ),
            defaultFallbackModels = listOf(
                "ashna-x1",
                "deepseek-v4.1-flash",
                "deepseek-chat",
                "gpt-4o-mini",
                "ashna-coder",
                "claude-3-5-sonnet"
            )
        )

        val ALL_PROFILES = listOf(
            GEMINI,
            GROQ,
            OPENROUTER,
            OPENAI,
            ANTHROPIC,
            DEEPSEEK,
            MISTRAL,
            XAI,
            TOGETHER,
            PERPLEXITY,
            CEREBRAS,
            FIREWORKS,
            SAMBANOVA,
            SILICONFLOW,
            OPENCODE,
            INCEPTION_LABS,
            ATRIA_ASI,
            ASHNA_AI
        )

        /**
         * Resolves a ProviderProfile by configured id / name or creates a dynamic custom OpenAI-compatible profile.
         */
        fun getById(idOrName: String, customBaseUrl: String = ""): ProviderProfile {
            val matched = ALL_PROFILES.firstOrNull { 
                it.id.equals(idOrName, ignoreCase = true) || 
                it.displayName.equals(idOrName, ignoreCase = true) ||
                (it.id.contains("OpenCode", ignoreCase = true) && idOrName.contains("OpenCode", ignoreCase = true)) ||
                (it.id.contains("Ashna", ignoreCase = true) && (idOrName.contains("Ashna", ignoreCase = true) || idOrName.contains("Asana", ignoreCase = true))) ||
                (it.id.contains("atria", ignoreCase = true) && idOrName.contains("atria", ignoreCase = true)) ||
                (it.id.contains("inception", ignoreCase = true) && idOrName.contains("inception", ignoreCase = true))
            }
            if (matched != null) return matched

            if (customBaseUrl.isNotBlank() || idOrName.startsWith("http://") || idOrName.startsWith("https://")) {
                val url = if (customBaseUrl.isNotBlank()) customBaseUrl else idOrName
                return ProviderProfile(
                    id = "Custom",
                    displayName = "Custom Endpoint",
                    baseUrl = url.trimEnd('/'),
                    authStyle = AuthStyle.BEARER_TOKEN,
                    chatPath = "/chat/completions",
                    modelsPath = "/models",
                    sttPath = null,
                    defaultFallbackModels = listOf("default")
                )
            }

            // Fallback to Gemini if unknown
            return GEMINI
        }

        /**
         * Guess provider from key prefix if user hasn't explicitly chosen one or if prefix unmistakably matches.
         */
        fun detectProviderFromKey(key: String, selectedProviderHint: String = ""): ProviderProfile {
            val trimmed = key.trim().removeSurrounding("\"").removeSurrounding("'").removePrefix("Bearer ").removePrefix("bearer ").trim()

            // An explicitly configured endpoint must not be replaced just because its
            // OpenAI-compatible key happens to have another vendor's prefix.
            if (selectedProviderHint.startsWith("https://") || selectedProviderHint.startsWith("http://")) {
                return getById(selectedProviderHint)
            }

            // 1. Unmistakable vendor-specific prefixes always identify their provider
            if (trimmed.startsWith("AIza") || trimmed.startsWith("AQ.")) return GEMINI
            if (trimmed.startsWith("gsk_")) return GROQ
            if (trimmed.startsWith("sk-or-")) return OPENROUTER
            if (trimmed.startsWith("sk-ant-")) return ANTHROPIC
            if (trimmed.startsWith("xai-")) return XAI
            if (trimmed.startsWith("mistral-")) return MISTRAL
            if (trimmed.startsWith("together-")) return TOGETHER
            if (trimmed.startsWith("pplx-")) return PERPLEXITY
            if (trimmed.startsWith("csk-")) return CEREBRAS
            if (trimmed.startsWith("fw_") || trimmed.startsWith("fwi_")) return FIREWORKS
            if (trimmed.startsWith("opencode", ignoreCase = true) || trimmed.startsWith("oc-")) return OPENCODE
            if (trimmed.startsWith("ashna", ignoreCase = true) || trimmed.startsWith("ash_", ignoreCase = true) || trimmed.startsWith("sk-ashna-", ignoreCase = true) || trimmed.startsWith("asana", ignoreCase = true) || trimmed.startsWith("sk-asana-", ignoreCase = true) || trimmed.startsWith("asa_", ignoreCase = true)) return ASHNA_AI
            if (trimmed.startsWith("atria", ignoreCase = true) || trimmed.startsWith("asi-", ignoreCase = true) || trimmed.startsWith("sk-atria-", ignoreCase = true) || trimmed.startsWith("atria_sk_", ignoreCase = true)) return ATRIA_ASI
            if (trimmed.startsWith("inception", ignoreCase = true) || trimmed.startsWith("il_", ignoreCase = true) || trimmed.startsWith("sk-il-", ignoreCase = true)) return INCEPTION_LABS
            if (trimmed.startsWith("sk-proj-") || trimmed.startsWith("sk-admin-")) return OPENAI
            if (trimmed.startsWith("samba_", ignoreCase = true) || trimmed.startsWith("sn_", ignoreCase = true)) return SAMBANOVA

            // 2. If user explicitly chose a non-Gemini provider (e.g., Ashna AI, DeepSeek, Mistral, Together, OpenAI, OpenRouter), honor it!
            if (selectedProviderHint.isNotBlank() && !selectedProviderHint.equals("Gemini", ignoreCase = true)) {
                val explicit = ALL_PROFILES.firstOrNull { 
                    it.id.equals(selectedProviderHint, ignoreCase = true) || 
                    it.displayName.equals(selectedProviderHint, ignoreCase = true) ||
                    (it.id.contains("OpenCode", ignoreCase = true) && selectedProviderHint.contains("OpenCode", ignoreCase = true)) ||
                    (it.id.contains("Ashna", ignoreCase = true) && (selectedProviderHint.contains("Ashna", ignoreCase = true) || selectedProviderHint.contains("Asana", ignoreCase = true))) ||
                    (it.id.contains("atria", ignoreCase = true) && selectedProviderHint.contains("atria", ignoreCase = true)) ||
                    (it.id.contains("inception", ignoreCase = true) && selectedProviderHint.contains("inception", ignoreCase = true))
                }
                if (explicit != null) return explicit
            }

            // 3. Generic "sk-" prefix disambiguation when user is on default or unspecified provider
            if (trimmed.startsWith("sk-")) {
                if (selectedProviderHint.contains("Ashna", ignoreCase = true) || selectedProviderHint.contains("Asana", ignoreCase = true)) return ASHNA_AI
                if (selectedProviderHint.contains("DeepSeek", ignoreCase = true)) return DEEPSEEK
                if (selectedProviderHint.contains("OpenRouter", ignoreCase = true)) return OPENROUTER
                return OPENAI
            }

            // 4. If selectedProviderHint is set (including Gemini), return it
            if (selectedProviderHint.isNotBlank()) {
                val explicit = ALL_PROFILES.firstOrNull { 
                    it.id.equals(selectedProviderHint, ignoreCase = true) || 
                    it.displayName.equals(selectedProviderHint, ignoreCase = true)
                }
                if (explicit != null) return explicit
            }

            if (trimmed.length in 38..40 && !trimmed.contains(" ") && !trimmed.contains("-")) return GEMINI
            return GEMINI
        }
    }
}
