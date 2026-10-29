package com.example.data.remote

import android.util.Log
import com.example.data.local.ProviderSettingsManager
import com.example.llm.AttemptRecord
import com.example.llm.AttemptReport
import com.example.llm.KeyStoreCrypto
import com.example.llm.LlmClient
import com.example.llm.LlmError
import com.example.llm.LlmErrorCategory
import com.example.llm.LlmRequest
import com.example.llm.ProviderProfile
import com.example.llm.RequestTooLargeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * High-level orchestration utility for AI operations.
 * Serves as a backward-compatible facade over the com.example.llm core engine.
 */
object AIApiUtility {
    private const val TAG = "AIApiUtility"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun sanitizeKey(key: String): String {
        return KeyStoreCrypto.decrypt(key).trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
    }

    fun buildMasterSystemPrompt(mode: String, customPrompt: String): String {
        val modeInstruction = when (mode) {
            "coding", "build" -> "You are SAIF AI, an elite autonomous Senior Android Architect and Full-Stack Mobile Engineer integrated into a mobile IDE. You provide production-grade, highly optimized code without placeholders, fake logic, or incomplete snippets that compile and run on Android API 24+."
            "summary" -> "You are SAIF AI in 'Quick Summary' mode. You synthesize complex information into concise bullet points, executive takeaways, and high-impact conclusions."
            "creative" -> "You are SAIF AI in 'Creative Studio' mode. You are an imaginative writer, brainstorm partner, and storyteller craftsperson with vivid phrasing and engaging ideas."
            "research" -> "You are SAIF AI in 'Deep Research' mode. You provide thorough, structured analytical reports with pros/cons, architectural tradeoffs, and nuanced technical breakdowns."
            else -> "You are SAIF AI, an advanced native Android AI companion."
        }

        val coreDirective = """
CORE GUIDELINES:
1. Identity: You are SAIF AI, an advanced native Android AI companion created by SAIF.
2. Tone & Style: Always respond directly, warmly, and politely with contextual emojis and clean markdown styling.
3. Adaptive Length:
   - For simple greetings ("hi", "hello", "kya haal hai", etc.): Reply with a warm, crisp 1-2 sentence response.
   - For detailed questions or coding: Provide complete, structured explanations with proper code blocks.
4. Language: Natural, fluent Hindi, Hinglish, or English based on the user's input.
5. Direct Conversation: Speak directly to the user as a helpful, friendly AI assistant.
6. Clean Output: NEVER output thinking tags (<think>), internal reasoning, scratchpads, analysis notes, or guideline repetitions. Start directly with the conversational message.
7. Native Android Device Automation:
   You are running on the user's Android phone with active native Accessibility and OS control permissions.
   You CAN and DO directly open apps, send WhatsApp messages, search & play YouTube videos, dial calls, click, type, scroll, navigate home/back, and inspect the screen.
   CRITICAL: NEVER claim "I cannot directly open apps or control your device" or "मैं सीधे आपके डिवाइस पर ऐप नहीं खोल सकता".
   When the user commands an action on their phone, confirm confidently that it is being done ("Ji sir, WhatsApp khol raha hoon...", "YouTube par video chala raha hoon...").
""".trimIndent()

        val finalPrompt = StringBuilder()
        finalPrompt.append(modeInstruction).append("\n\n")
        finalPrompt.append(coreDirective).append("\n\n")

        try {
            // App-wide memory: the chat sees everything SAIF AI learned in any feature.
            val memoryBlock = com.example.util.SaifMemoryCore.promptBlock(
                com.example.util.SaifMemoryCore.Features.CHAT
            )
            if (memoryBlock.isNotBlank()) {
                finalPrompt.append(memoryBlock).append("\n\n")
            }
        } catch (ignored: Exception) {}

        if (customPrompt.isNotBlank()) {
            finalPrompt.append("User Custom Directives:\n").append(customPrompt)
        }
        return finalPrompt.toString()
    }

    private data class ProviderAttempt(
        val provider: String,
        val apiKey: String,
        val models: List<String>,
        val isUserKey: Boolean
    )

    /**
     * Executes an AI request across candidate models with strict BYOK prioritization.
     * The user's configured provider and key always run first and are never silently overwritten.
     */
    suspend fun executeWithFallback(
        prompt: String,
        mode: String = "general",
        history: List<Pair<String, String>> = emptyList(),
        customSystemPrompt: String = "",
        base64Images: List<String> = emptyList(),
        sanitize: Boolean = (mode != "build" && mode != "coding"),
        onChunk: suspend (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val state = ProviderSettingsManager.loadState()
        val effectiveSystemPrompt = if (customSystemPrompt.isNotBlank() && (mode == "coding" || mode == "build")) {
            customSystemPrompt
        } else {
            buildMasterSystemPrompt(mode, customSystemPrompt)
        }

        val effectiveHistory = if (mode == "build") emptyList() else history

        val request = LlmRequest(
            prompt = prompt,
            systemPrompt = effectiveSystemPrompt,
            history = effectiveHistory,
            imagesBase64 = base64Images,
            mode = mode
        )

        val userKeys = state.apiKeys.map { sanitizeKey(it) }.filter { it.isNotBlank() && !it.contains("MY_") }
        val hasCustomUserKey = userKeys.isNotEmpty()

        val usageType = when {
            mode == "image" || prompt.contains("generate an image", ignoreCase = true) -> com.example.data.local.ApiUsageType.IMAGE
            mode == "coding" || mode == "build" -> com.example.data.local.ApiUsageType.APP_BUILD
            else -> com.example.data.local.ApiUsageType.CHAT
        }

        // Check Admin quota if user is relying on Admin free tier
        if (!hasCustomUserKey) {
            val limitCheck = com.example.data.local.AdminConfigManager.canUseApi(usageType, hasCustomUserKey = false)
            if (!limitCheck.isAllowed) {
                return@withContext Result.failure(IllegalStateException(limitCheck.message))
            }
        }

        // Build attempts chain following strict BYOK policy
        val attemptsList = mutableListOf<ProviderAttempt>()

        // Helper to resolve models for a provider with smart provider-model alignment
        fun resolveCandidateModels(prov: String, primaryModel: String): List<String> {
            val list = mutableListOf<String>()
            val trimmedPrimary = primaryModel.trim()
            val profile = ProviderProfile.getById(prov)
            val isProvGemini = prov.equals("Gemini", ignoreCase = true)
            val isModelGemini = trimmedPrimary.contains("gemini", ignoreCase = true)
            val isProvAshna = prov.contains("Ashna", ignoreCase = true) || prov.contains("Asana", ignoreCase = true)

            if (trimmedPrimary.isNotBlank()) {
                // Only add primaryModel if it is compatible with the provider
                if (isProvGemini == isModelGemini || trimmedPrimary.contains("/") || !isModelGemini) {
                    if (isProvAshna) {
                        // Do not pass unrelated vendor model names (like Groq llama or Gemini) to Ashna AI
                        if (!trimmedPrimary.contains("llama", true) && !trimmedPrimary.contains("gemini", true) && !trimmedPrimary.contains("groq", true)) {
                            list.add(trimmedPrimary)
                        }
                    } else {
                        list.add(trimmedPrimary)
                    }
                }
            }
            if (isProvAshna) {
                // Ensure Ashna AI free native models are always tried first
                list.add("ashna-x1")
                list.add("ashna-x1-flash")
                list.add("ashna-coder")
            }
            list.addAll(profile.defaultFallbackModels)
            return list.distinct()
        }

        val primaryModel = if (mode == "coding" || mode == "build") {
            state.buildModel.ifBlank { state.activeModel }
        } else {
            state.activeModel
        }

        if (hasCustomUserKey) {
            val chosenProvider = state.providerName.ifBlank { "Gemini" }
            for (key in userKeys) {
                // Auto-detect the actual provider matching this key's signature
                val detectedProfile = ProviderProfile.detectProviderFromKey(key, chosenProvider)
                val targetProvider = detectedProfile.displayName
                val models = resolveCandidateModels(targetProvider, primaryModel)
                attemptsList.add(ProviderAttempt(targetProvider, key, models, isUserKey = true))

                // If detectedProvider differs from chosenProvider, also add chosenProvider as alternative attempt
                if (!targetProvider.equals(chosenProvider, ignoreCase = true) && !key.startsWith("AIza") && !key.startsWith("gsk_")) {
                    val altModels = resolveCandidateModels(chosenProvider, primaryModel)
                    attemptsList.add(ProviderAttempt(chosenProvider, key, altModels, isUserKey = true))
                }
            }

            // Strict BYOK Isolation: When a user has configured their own API key in Settings,
            // they are completely disconnected from the Admin's fleet key and quota.
            // All calls run 100% on their own key with UNLIMITED quota.
            // Do NOT append adminDefaultKey, poolKeys, or platform fallback keys so the user's connection never touches the Admin key.
        } else {
            // Free Tier / Admin Default Key for users who haven't added their own key
            val adminConfig = com.example.data.local.AdminConfigManager.globalConfig.value
            val adminDefaultKey = sanitizeKey(adminConfig.defaultApiKey)
            if (adminDefaultKey.isNotBlank()) {
                val adminProv = adminConfig.defaultProvider.ifBlank { "Gemini" }
                attemptsList.add(ProviderAttempt(adminProv, adminDefaultKey, resolveCandidateModels(adminProv, adminConfig.defaultModel), isUserKey = false))
            }

            // Platform BuildConfig keys as last resort ONLY when user has no key
            val geminiBuildConfigKey = try { com.example.BuildConfig.GEMINI_API_KEY.trim() } catch (e: Exception) { "" }
            if (geminiBuildConfigKey.isNotBlank() && !geminiBuildConfigKey.contains("MY_")) {
                attemptsList.add(ProviderAttempt("Gemini", geminiBuildConfigKey, ProviderProfile.GEMINI.defaultFallbackModels, isUserKey = false))
            }

            val openRouterBuildConfigKey = try { com.example.BuildConfig.OPENROUTER_API_KEY.trim() } catch (e: Exception) { "" }
            if (openRouterBuildConfigKey.isNotBlank() && !openRouterBuildConfigKey.contains("MY_")) {
                attemptsList.add(ProviderAttempt("OpenRouter", openRouterBuildConfigKey, ProviderProfile.OPENROUTER.defaultFallbackModels, isUserKey = false))
            }
        }

        if (attemptsList.isEmpty()) {
            val errorMsg = "⚠️ API Key Missing: Please open Settings (⚙️) ➔ 'AI Provider' and add your API key (Gemini, Groq, OpenRouter, OpenAI, etc.)."
            Log.e(TAG, errorMsg)
            return@withContext Result.failure(IllegalStateException(errorMsg))
        }

        val report = AttemptReport()

        for (attempt in attemptsList) {
            var consecutiveQuotaFailures = 0
            for (modelName in attempt.models) {
                val start = System.currentTimeMillis()
                Log.d(TAG, "Executing attempt ➔ Provider: ${attempt.provider}, Model: $modelName, UserKey: ${attempt.isUserKey}")

                try {
                    val result = LlmClient.stream(
                        providerName = attempt.provider,
                        apiKey = attempt.apiKey,
                        modelName = modelName,
                        request = request,
                        onChunk = onChunk
                    )
                    val latency = System.currentTimeMillis() - start

                    if (result.isSuccess) {
                        val reply = result.getOrNull().orEmpty()
                        if (reply.isNotBlank()) {
                            val sanitized = if (sanitize) sanitizeAiReply(reply) else reply
                            report.addAttempt(AttemptRecord(attempt.provider, modelName, attempt.isUserKey, success = true, latencyMs = latency))
                            Log.i(TAG, "API success with ${attempt.provider} ($modelName) in ${latency}ms")

                            if (!hasCustomUserKey) {
                                com.example.data.local.AdminConfigManager.recordApiUsage(usageType)
                            }
                            return@withContext Result.success(sanitized)
                        }
                    }

                    val exception = result.exceptionOrNull()
                    val llmError = exception as? LlmError
                        ?: LlmError(attempt.provider, modelName, null, exception?.message ?: "Unknown failure", LlmErrorCategory.UNKNOWN)

                    report.addAttempt(AttemptRecord(attempt.provider, modelName, attempt.isUserKey, success = false, error = llmError, latencyMs = latency))
                    Log.w(TAG, "Attempt with ${attempt.provider} ($modelName) failed: ${llmError.rawMessage}")

                    // Stop cycling further models on this provider if auth or quota is definitively rejected
                    if (llmError.category == LlmErrorCategory.AUTH) {
                        Log.w(TAG, "Authentication failed for ${attempt.provider}. Skipping remaining models for this key.")
                        break
                    }

                    // If account has quota/credit exhausted (429) across models, check if free fallback models remain
                    if (llmError.category == LlmErrorCategory.RATE_LIMIT &&
                        (llmError.rawMessage.contains("quota", true) || llmError.rawMessage.contains("credit", true) || llmError.rawMessage.contains("balance", true) || llmError.rawMessage.contains("exceeded", true))) {
                        consecutiveQuotaFailures++
                        val remainingModels = attempt.models.dropWhile { it != modelName }.drop(1)
                        val hasRemainingFreeModel = remainingModels.any { m ->
                            m.contains("x1", ignoreCase = true) || m.contains("flash", ignoreCase = true) || m.contains("free", ignoreCase = true) || m.contains("mini", ignoreCase = true)
                        }
                        if (consecutiveQuotaFailures >= 2 && !hasRemainingFreeModel) {
                            Log.w(TAG, "Quota exhausted for ${attempt.provider}. Skipping remaining models for this key to prevent app freeze.")
                            break
                        }
                    }

                } catch (e: RequestTooLargeException) {
                    throw e // Propagate RequestTooLargeException so AutonomousAppBuilder can split phases
                } catch (e: Exception) {
                    val latency = System.currentTimeMillis() - start
                    val llmError = LlmError(attempt.provider, modelName, null, e.message ?: "Execution error", LlmErrorCategory.UNKNOWN)
                    report.addAttempt(AttemptRecord(attempt.provider, modelName, attempt.isUserKey, success = false, error = llmError, latencyMs = latency))
                }
            }
        }

        // Surface the honest primary failure from the user's provider, prioritizing the user's selected model
        val primaryError = report.primaryFailure(primaryModel)
        val userMessage = primaryError?.formatHumanMessage()
            ?: "⚠️ API Error: All configured attempts failed.\n${report.toDiagnosticSummary()}"

        Log.e(TAG, "All attempts failed. Returning honest report: $userMessage")
        return@withContext Result.failure(IllegalStateException(userMessage))
    }

    fun sanitizeAiReply(raw: String, isStreaming: Boolean = false): String {
        var text = raw.trim()

        // 1. Strip all closed reasoning XML tags (case-insensitive)
        text = text.replace(Regex("(?i)<(?:think|thought|thought_process|reasoning|reflection|details)>[\\s\\S]*?</(?:think|thought|thought_process|reasoning|reflection|details)>"), "").trim()
        text = text.replace(Regex("(?i)\\[THOUGHT\\][\\s\\S]*?\\[/THOUGHT\\]"), "").trim()

        // 2. Strip anything preceding an orphaned closing tag (e.g. ... </think> Real Response)
        val closeTags = listOf("</think>", "</thought>", "</reasoning>", "</thought_process>", "</reflection>", "[/THOUGHT]")
        for (ctag in closeTags) {
            val lastIdx = text.lastIndexOf(ctag, ignoreCase = true)
            if (lastIdx != -1) {
                text = text.substring(lastIdx + ctag.length).trim()
            }
        }

        // 3. Handle active unclosed reasoning tag
        val unclosedTags = listOf("<think>", "<thought>", "<reasoning>", "<thought_process>", "<reflection>", "[THOUGHT]")
        for (tag in unclosedTags) {
            val idx = text.indexOf(tag, ignoreCase = true)
            if (idx != -1) {
                if (isStreaming) {
                    val preContent = text.substring(0, idx).trim()
                    text = if (preContent.isNotBlank()) preContent else "Thinking..."
                } else {
                    val afterTag = text.substring(idx + tag.length).trim()
                    val paragraphs = afterTag.split(Regex("\\n\\s*\\n+"))
                    val candidate = paragraphs.dropWhile { p ->
                        val tp = p.trim()
                        tp.startsWith("1.") || tp.startsWith("2.") || tp.startsWith("*") ||
                        tp.contains("user is asking", ignoreCase = true) ||
                        tp.contains("I will reply", ignoreCase = true) ||
                        tp.contains("directive", ignoreCase = true)
                    }.joinToString("\n\n").trim()
                    text = if (candidate.isNotBlank()) candidate else afterTag
                }
            }
        }

        // 4. Strip explicit thought/reasoning sections when followed by a clear response marker
        val responseMarkerRegex = Regex("(?i)(?:^|\\n+)[#*\\s>_]*(?:Final Answer|Final Response|Response|Answer|Output|Conversational Reply)[#*\\s>_:]*\\s*\\n*")
        val markerMatch = responseMarkerRegex.find(text)
        if (markerMatch != null) {
            val candidate = text.substring(markerMatch.range.last + 1).trim()
            if (candidate.isNotBlank()) {
                text = candidate
            }
        }

        // 5. Strip leading thinking/reasoning headers (e.g. "**Thinking Process:**\n...") and subsequent deliberation bullets
        val thinkingHeaderRegex = Regex("(?i)^[#*\\s>_]*(?:Thinking Process|Thought Process|Chain of Thought|Internal Thoughts|Internal Reasoning|Thinking|Thoughts|Reasoning|My Thought Process|Analysis|Scratchpad|Plan)[#*\\s>_:]*\\s*\\n+")
        if (thinkingHeaderRegex.containsMatchIn(text)) {
            val afterHeader = text.replaceFirst(thinkingHeaderRegex, "").trim()
            val paragraphs = afterHeader.split(Regex("\\n\\s*\\n+"))
            val candidate = paragraphs.dropWhile { p ->
                val tp = p.trim()
                tp.startsWith("1.") || tp.startsWith("2.") || tp.startsWith("3.") ||
                tp.startsWith("*") || tp.startsWith("-") ||
                tp.contains("user is asking", ignoreCase = true) ||
                tp.contains("user said", ignoreCase = true) ||
                tp.contains("I will reply", ignoreCase = true) ||
                tp.contains("I should", ignoreCase = true) ||
                tp.contains("Let me", ignoreCase = true) ||
                tp.contains("directive", ignoreCase = true)
            }.joinToString("\n\n").trim()
            if (candidate.isNotBlank()) {
                text = candidate
            } else if (paragraphs.size > 1) {
                text = paragraphs.last().trim()
            }
        }

        // 6. Strip leading prompt leakage lines: "System Instruction:", "User Request:", "User Prompt:", "Core Guidelines:"
        val promptLeakRegex = Regex("(?i)^[#*\\s>_]*(?:System Instruction|System Prompt|User Request|User Prompt|\\[Instruction:.*?\\]|Core Guidelines|CORE GUIDELINES|User Custom Directives)[#*\\s>_:]*[\\s\\S]*?(?:\\n\\s*\\n+|(?=\\n*[\\p{L}]))")
        val leakMatch = promptLeakRegex.find(text)
        if (leakMatch != null && leakMatch.range.first == 0) {
            val candidate = text.substring(leakMatch.range.last + 1).trim()
            if (candidate.isNotBlank()) {
                text = candidate
            }
        }

        // 7. Strip lines discussing following directives / guidelines / instructions
        val directivesDelibRegex = Regex("(?i)^[#*\\s>_]*(?:We need to follow(?: the)? (?:directives|guidelines|instructions)|Let's follow(?: the)? (?:directives|guidelines|instructions)|According to(?: the)? (?:directives|guidelines|instructions)|The user wants(?: to)?|I must follow(?: the)? (?:directives|guidelines))[\\s\\S]*?(?:\\n\\s*\\n+|(?=\\n*[\\p{L}]))")
        val delibMatch = directivesDelibRegex.find(text)
        if (delibMatch != null && delibMatch.range.first == 0) {
            val candidate = text.substring(delibMatch.range.last + 1).trim()
            if (candidate.isNotBlank()) {
                text = candidate
            }
        }

        // 8. Strip lines echoing "Core Guidelines" or system identity instructions
        val lines = text.lines()
        if (lines.isNotEmpty() && (lines.first().contains("directives", ignoreCase = true) || lines.first().contains("guidelines", ignoreCase = true) || lines.first().contains("CORE GUIDELINES", ignoreCase = true))) {
            val nonDelib = lines.dropWhile { it.contains("directive", ignoreCase = true) || it.contains("guideline", ignoreCase = true) || it.isBlank() }
            if (nonDelib.isNotEmpty()) {
                text = nonDelib.joinToString("\n").trim()
            }
        }

        return text
            .removePrefix("```markdown")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Discovers live models from provider endpoint and identifies free tier models.
     * Throws an exception on invalid API keys / auth rejection to prevent fake model fallbacks.
     */
    suspend fun fetchModels(provider: String, apiKey: String): List<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeKey(apiKey)
        if (cleanKey.isBlank()) return@withContext emptyList()

        // Auto-detect matching provider profile for this key if mismatched with UI selection
        val detected = ProviderProfile.detectProviderFromKey(cleanKey, provider)
        val profile = if (detected.id != ProviderProfile.GEMINI.id || cleanKey.startsWith("AIza") || cleanKey.startsWith("AQ.")) {
            detected
        } else {
            ProviderProfile.getById(provider)
        }
        val targetProvider = profile.displayName

        // 1. Google Gemini models endpoint
        if (profile.id.equals("Gemini", ignoreCase = true)) {
            val url = "${profile.baseUrl}/models?key=$cleanKey"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = try {
                    val json = JSONObject(body)
                    json.optJSONObject("error")?.optString("message") ?: json.optString("message")
                } catch (e: Exception) { "" }
                val detail = if (errMsg.isNotBlank()) errMsg else "HTTP ${response.code} (${response.message})"
                throw IllegalStateException("Invalid Gemini API Key: $detail")
            }
            val json = JSONObject(body)
            val modelsArray = json.optJSONArray("models") ?: JSONArray()
            val resultList = mutableListOf<Pair<String, Boolean>>()
            for (i in 0 until modelsArray.length()) {
                val m = modelsArray.getJSONObject(i)
                val name = m.optString("name", "").replace("models/", "")
                if (name.isBlank() || name.contains("embedding") || name.contains("imagen") || name.contains("aqa") || name.contains("bison") || name.contains("whisper")) continue
                if (com.example.llm.GeminiModelCatalog.isRetired(name)) continue   // 1.5 / 2.0 are shut down: never offer them
                val methods = m.optJSONArray("supportedGenerationMethods")
                if (methods != null) {
                    var canGen = false
                    for (j in 0 until methods.length()) {
                        if (methods.optString(j) == "generateContent") { canGen = true; break }
                    }
                    if (!canGen) continue
                }
                val isFree = name.contains("flash") || name.contains("lite") || !name.contains("pro")
                resultList.add(Pair(name, isFree))
            }

            // Always ensure primary stable Gemini models are present
            profile.defaultFallbackModels.forEach { m ->
                val isFree = m.contains("flash") || m.contains("lite")
                if (resultList.none { it.first.equals(m, ignoreCase = true) }) {
                    resultList.add(Pair(m, isFree))
                }
            }

            if (resultList.isNotEmpty()) {
                // Newest stable Flash first (see GeminiModelCatalog.rank), then the rest.
                return@withContext resultList.distinctBy { it.first }.sortedWith(
                    compareByDescending<Pair<String, Boolean>> { com.example.llm.GeminiModelCatalog.rank(it.first) }.thenBy { it.first }
                )
            }
            return@withContext profile.defaultFallbackModels.map { Pair(it, true) }
        }

        // 2. OpenAI-compatible /models endpoint
        val modelsUrl = "${profile.baseUrl.trimEnd('/')}${profile.modelsPath}"
        val requestBuilder = Request.Builder().url(modelsUrl).get()
        when (profile.authStyle) {
            com.example.llm.AuthStyle.BEARER_TOKEN -> requestBuilder.addHeader("Authorization", "Bearer $cleanKey")
            com.example.llm.AuthStyle.X_API_KEY_HEADER -> requestBuilder.addHeader("x-api-key", cleanKey)
            com.example.llm.AuthStyle.ANTHROPIC_HEADER -> {
                requestBuilder.addHeader("x-api-key", cleanKey)
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            else -> requestBuilder.addHeader("Authorization", "Bearer $cleanKey")
        }
        if (profile.id.contains("Ashna", ignoreCase = true) || profile.id.contains("Asana", ignoreCase = true)) {
            requestBuilder.addHeader("X-Api-Key", cleanKey)
            requestBuilder.addHeader("Api-Key", cleanKey)
        }
        if (profile.id.contains("atria", ignoreCase = true)) {
            requestBuilder.addHeader("X-Api-Key", cleanKey)
        }
        if (profile.id.contains("OpenCode", ignoreCase = true)) {
            requestBuilder.addHeader("X-Api-Key", cleanKey)
            requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
        }

        var response = httpClient.newCall(requestBuilder.build()).execute()
        var body = response.body?.string().orEmpty()

        // Fallback endpoint check for Ashna AI if first returns 404
        if (response.code == 404 && profile.id.contains("Ashna", ignoreCase = true)) {
            try {
                val altReq = Request.Builder()
                    .url("https://api.ashna.ai/v1/models")
                    .addHeader("Authorization", "Bearer $cleanKey")
                    .addHeader("X-Api-Key", cleanKey)
                    .addHeader("Api-Key", cleanKey)
                    .get()
                    .build()
                val altResp = httpClient.newCall(altReq).execute()
                val altBody = altResp.body?.string().orEmpty()
                if (altResp.isSuccessful || altResp.code in listOf(401, 403)) {
                    response = altResp
                    body = altBody
                }
            } catch (ignored: Exception) {}
        }

        // Strict rejection on authentication failures (e.g. 401, 403, or invalid API key payload)
        if (response.code in listOf(401, 403) || (!response.isSuccessful && (body.contains("invalid_api_key", true) || body.contains("unauthorized", true) || body.contains("invalid api key", true)))) {
            val errMsg = try {
                val json = JSONObject(body)
                json.optJSONObject("error")?.optString("message") 
                    ?: json.optString("message") 
                    ?: json.optString("detail")
            } catch (e: Exception) { "" }
            val detail = if (errMsg.isNotBlank()) errMsg else "HTTP ${response.code} Unauthorized"
            throw IllegalStateException("Invalid API Key for $targetProvider: $detail")
        }

        if (response.isSuccessful) {
            // Live probe ONLY for providers that host public unauthenticated /models (like inceptionlabs.ai)
            if (profile.id.contains("inception", ignoreCase = true)) {
                val testProbe = com.example.llm.LlmClient.testConnection(profile.id, cleanKey, "mercury-2.5")
                if (!testProbe.success && testProbe.error?.category == LlmErrorCategory.AUTH) {
                    val errDetail = testProbe.error.formatHumanMessage()
                    throw IllegalStateException("Invalid API Key for $targetProvider: $errDetail")
                }
            }

            val list = mutableListOf<Pair<String, Boolean>>()
            try {
                val trimmedBody = body.trim()
                if (trimmedBody.startsWith("[")) {
                    val array = JSONArray(trimmedBody)
                    for (i in 0 until array.length()) {
                        val item = array.opt(i)
                        val id = if (item is JSONObject) item.optString("id", item.optString("name", "")) else item.toString()
                        if (id.isNotBlank() && !id.contains("whisper") && !id.contains("embed")) {
                            val isFree = id.contains(":free", true) || id.contains("flash", true) || id.contains("free", true) || id.contains("mini", true) || id.contains("ashna-x1", true) || (targetProvider.contains("Ashna", true) && !id.contains("astra", true) && !id.contains("gpt-6", true) && !id.contains("claude", true))
                            list.add(Pair(id, isFree))
                        }
                    }
                } else {
                    val json = JSONObject(trimmedBody)
                    val data = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        val item = data.opt(i)
                        val id = if (item is JSONObject) item.optString("id", item.optString("name", "")) else item.toString()
                        if (id.isBlank() || id.contains("whisper") || id.contains("tts") || id.contains("embed")) continue
                        val isFree = id.contains(":free", true) || id.contains("flash", true) || id.contains("free", true) || id.contains("mini", true) || id.contains("ashna-x1", true) || (targetProvider.contains("Ashna", true) && !id.contains("astra", true) && !id.contains("gpt-6", true) && !id.contains("claude", true))
                        list.add(Pair(id, isFree))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing models json for $targetProvider: ${e.message}")
            }

            // Always merge known profile models for full catalog visibility
            profile.defaultFallbackModels.forEach { m ->
                val isFree = m.contains("free", true) || m.contains("flash", true) || m.contains("mini", true)
                list.add(Pair(m, isFree))
            }

            if (list.isNotEmpty()) {
                return@withContext list.distinctBy { it.first }.sortedWith(
                    compareByDescending<Pair<String, Boolean>> { it.second }.thenBy {
                        if (it.first.equals("ashna-x1", true)) 0
                        else if (it.first.contains("ashna-x1-flash", true)) 1
                        else 2
                    }.thenBy { it.first }
                )
            }
        }

        // If models endpoint returned 404 or empty (e.g. DeepSeek or Anthropic without a /models endpoint),
        // verify key validity with a live probe test before returning fallback models
        val probeModel = profile.defaultFallbackModels.firstOrNull() ?: "default"
        val testRes = com.example.llm.LlmClient.testConnection(profile.id, cleanKey, probeModel)
        if (testRes.success || testRes.error?.category != LlmErrorCategory.AUTH) {
            return@withContext profile.defaultFallbackModels.map { model ->
                val isFree = model.contains("free", true) || model.contains("flash", true) || model.contains("lite", true) || model.contains("mini", true)
                Pair(model, isFree)
            }
        } else {
            val errDetail = testRes.error?.formatHumanMessage() ?: "Authentication rejected by provider"
            throw IllegalStateException("API Key verification failed for $targetProvider: $errDetail")
        }
    }
}
