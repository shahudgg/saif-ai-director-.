package com.example.stt

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.example.audio.TranscriptRepair
import com.example.data.local.ProviderSettingsManager
import com.example.data.remote.AIApiUtility
import com.example.data.remote.ModelResolver
import com.example.data.remote.ModelRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SttProfile {
    DICTATION,
    LIVE_COMMAND
}

data class SttResult(
    val text: String,
    val languageGuess: String? = null,
    val provider: String = "",
    val model: String = "",
    val latencyMs: Long = 0L,
    val confidence: Double? = null,
    val rejectedReason: String? = null
) {
    val isSuccess: Boolean get() = text.isNotBlank() && rejectedReason == null
}

/**
 * Universal Speech-To-Text router.
 * Features:
 * - Dual-hypothesis Whisper (en/Hinglish Roman script vs hi) for zero mishearing on Indian English/Hinglish.
 * - Dynamic vocabulary biasing constructed from installed apps, contacts, and command verbs.
 * - Enforces temperature 0.0 and verbose_json for Whisper.
 * - Configured Gemini STT generationConfig (temperature 0.0, maxOutputTokens 120, thinking disabled).
 * - Real audio duration, SNR dB, and voiced ratio fed into TranscriptGuard to filter background noise.
 * - Transparent TranscriptRepair pipeline.
 */
object SttRouter {
    private const val TAG = "SttRouter"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Builds a rich vocabulary biasing prompt for Whisper to accurately recognize assistant commands,
     * installed apps, and contact aliases in Roman script.
     */
    fun buildVocabularyPrompt(profile: SttProfile, context: Context? = null): String {
        val baseVocabulary = "SAIF, YouTube, WhatsApp, Instagram, Google, Chrome, Spotify, Camera, Settings, " +
            "CapCut, Play Store, Telegram, Maps, " +
            "kholo, chalao, chalu karo, band karo, call karo, lagao, message bhejo, gaana sunao, " +
            "kya haal hai, kaise ho, search karo, alarm lagao, volume badhao, subscribe, like karo, status"

        val dynamicTokens = mutableListOf<String>()

        if (context != null) {
            // 1. Add contact aliases
            try {
                val contacts = com.example.agent.skills.ContactAliasStore.getSampleContactNames(context)
                dynamicTokens.addAll(contacts.take(15))
            } catch (ignored: Exception) {}

            // 2. Add prominent installed app labels
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val apps = pm.queryIntentActivities(intent, 0)
                for (app in apps.take(20)) {
                    val label = app.loadLabel(pm).toString()
                    if (label.length in 3..25 && !label.contains("com.")) {
                        dynamicTokens.add(label)
                    }
                }
            } catch (ignored: Exception) {}
        }

        return if (dynamicTokens.isNotEmpty()) {
            "$baseVocabulary, ${dynamicTokens.distinct().joinToString(", ")}"
        } else {
            baseVocabulary
        }
    }

    /**
     * Transcribes a 16 kHz mono 16-bit WAV byte array.
     */
    suspend fun transcribe(
        wavBytes: ByteArray,
        profile: SttProfile = SttProfile.LIVE_COMMAND,
        context: Context? = null,
        durationMs: Long = 2000L,
        snrDb: Double? = null,
        voicedRatio: Float? = null,
        speechLanguage: String? = null
    ): SttResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (wavBytes.isEmpty()) {
            return@withContext SttResult("", rejectedReason = "Empty audio buffer")
        }

        val state = ProviderSettingsManager.loadState()
        val userKeys = state.apiKeys.map { AIApiUtility.sanitizeKey(it) }.filter { it.isNotBlank() && !it.contains("MY_") }

        // Find candidate STT routes based on configured provider and keys
        val candidates = resolveSttCandidates(state.providerName, userKeys)

        if (candidates.isEmpty()) {
            val errorMsg = "Voice input ke liye Settings (⚙️) ➔ AI Provider me Groq, OpenAI ya Gemini API key add karein."
            Log.w(TAG, errorMsg)
            return@withContext SttResult("", rejectedReason = errorMsg)
        }

        val vocabPrompt = buildVocabularyPrompt(profile, context)
        var lastFailureReason = "All STT candidates failed"

        for (candidate in candidates) {
            val candidateStart = System.currentTimeMillis()
            try {
                val responseResult = when (candidate.service) {
                    SttServiceType.GROQ -> executeWhisperDualHypothesis(
                        endpoint = "https://api.groq.com/openai/v1/audio/transcriptions",
                        model = candidate.modelName,
                        apiKey = candidate.key,
                        audioBytes = wavBytes,
                        fileName = "audio.wav",
                        mimeType = "audio/wav",
                        prompt = vocabPrompt,
                        targetLanguage = speechLanguage
                    )

                    SttServiceType.OPENAI -> executeWhisperDualHypothesis(
                        endpoint = "https://api.openai.com/v1/audio/transcriptions",
                        model = candidate.modelName,
                        apiKey = candidate.key,
                        audioBytes = wavBytes,
                        fileName = "audio.wav",
                        mimeType = "audio/wav",
                        prompt = vocabPrompt,
                        targetLanguage = speechLanguage
                    )

                    SttServiceType.GEMINI -> executeGemini(
                        apiKey = candidate.key,
                        audioBytes = wavBytes,
                        mimeType = "audio/wav"
                    )
                }

                if (responseResult != null) {
                    val rawText = responseResult.text
                    val decision = TranscriptGuard.evaluate(
                        rawText = rawText,
                        durationMs = durationMs,
                        avgLogProb = responseResult.avgLogprob,
                        noSpeechProb = responseResult.noSpeechProb,
                        snrDb = snrDb,
                        voicedRatio = voicedRatio
                    )

                    val latency = System.currentTimeMillis() - candidateStart

                    if (decision.isAccepted && decision.sanitizedText.isNotBlank()) {
                        // Apply phonetic and alias transcript repair
                        val repairedText = TranscriptRepair.repair(decision.sanitizedText, context)
                        Log.i(TAG, "STT Success via ${candidate.service} (${latency}ms): '$repairedText' (raw='$rawText')")
                        return@withContext SttResult(
                            text = repairedText,
                            languageGuess = responseResult.language,
                            provider = candidate.service.name,
                            model = candidate.modelName,
                            latencyMs = latency,
                            confidence = responseResult.avgLogprob
                        )
                    } else {
                        Log.d(TAG, "STT Result filtered by TranscriptGuard: ${decision.rejectionReason} (raw='$rawText')")
                        return@withContext SttResult(
                            text = "",
                            provider = candidate.service.name,
                            model = candidate.modelName,
                            latencyMs = latency,
                            rejectedReason = decision.rejectionReason
                        )
                    }
                }
            } catch (e: Exception) {
                lastFailureReason = e.message ?: "Network error"
                Log.w(TAG, "Attempt with ${candidate.service} failed: $lastFailureReason")
            }
        }

        SttResult("", latencyMs = System.currentTimeMillis() - start, rejectedReason = lastFailureReason)
    }

    private enum class SttServiceType { GROQ, OPENAI, GEMINI }

    private data class SttCandidate(
        val service: SttServiceType,
        val key: String,
        val modelName: String
    )

    private fun resolveSttCandidates(activeProvider: String, userKeys: List<String>): List<SttCandidate> {
        val candidates = mutableListOf<SttCandidate>()

        // 1. First priority: Does the user's active provider natively support STT?
        val isGroqActive = activeProvider.equals("Groq", ignoreCase = true)
        val isOpenAiActive = activeProvider.equals("OpenAI", ignoreCase = true)
        val isGeminiActive = activeProvider.equals("Gemini", ignoreCase = true)

        if (isGroqActive) {
            val key = userKeys.firstOrNull { it.startsWith("gsk_") } ?: userKeys.firstOrNull()
            if (key != null) candidates.add(SttCandidate(SttServiceType.GROQ, key, "whisper-large-v3"))
        } else if (isOpenAiActive) {
            val key = userKeys.firstOrNull { it.startsWith("sk-") && !it.startsWith("sk-or-") && !it.startsWith("xai-") } ?: userKeys.firstOrNull()
            if (key != null) candidates.add(SttCandidate(SttServiceType.OPENAI, key, "whisper-1"))
        } else if (isGeminiActive) {
            val key = userKeys.firstOrNull { it.startsWith("AIza") || it.startsWith("AQ.") } ?: userKeys.firstOrNull()
            if (key != null) candidates.add(SttCandidate(SttServiceType.GEMINI, key, ModelResolver.resolve(ModelRole.TRANSCRIBE)))
        }

        // 2. Fallback: Search all user keys for other STT-capable providers
        // Note: xAI/Grok (xai-...) is deliberately excluded here because Grok does not provide an STT endpoint.
        for (k in userKeys) {
            if (k.startsWith("gsk_") && candidates.none { it.service == SttServiceType.GROQ }) {
                candidates.add(SttCandidate(SttServiceType.GROQ, k, "whisper-large-v3"))
            } else if (k.startsWith("sk-") && !k.startsWith("sk-or-") && !k.startsWith("sk-ant-") && !k.startsWith("xai-") && candidates.none { it.service == SttServiceType.OPENAI }) {
                candidates.add(SttCandidate(SttServiceType.OPENAI, k, "whisper-1"))
            } else if ((k.startsWith("AIza") || k.startsWith("AQ.")) && candidates.none { it.service == SttServiceType.GEMINI }) {
                candidates.add(SttCandidate(SttServiceType.GEMINI, k, ModelResolver.resolve(ModelRole.TRANSCRIBE)))
            }
        }

        // 3. Fallback to Admin or platform keys if candidates still empty
        if (candidates.isEmpty()) {
            val adminConfig = com.example.data.local.AdminConfigManager.globalConfig.value
            val adminKey = adminConfig.defaultApiKey.trim()
            if (adminKey.startsWith("gsk_")) {
                candidates.add(SttCandidate(SttServiceType.GROQ, adminKey, "whisper-large-v3"))
            } else if (adminKey.startsWith("sk-") && !adminKey.startsWith("sk-or-") && !adminKey.startsWith("xai-")) {
                candidates.add(SttCandidate(SttServiceType.OPENAI, adminKey, "whisper-1"))
            } else if (adminKey.startsWith("AIza")) {
                candidates.add(SttCandidate(SttServiceType.GEMINI, adminKey, ModelResolver.resolve(ModelRole.TRANSCRIBE)))
            }

            val geminiKey = try { com.example.BuildConfig.GEMINI_API_KEY.trim() } catch(e: Exception) { "" }
            if (geminiKey.isNotBlank() && !geminiKey.contains("MY_") && candidates.none { it.service == SttServiceType.GEMINI }) {
                candidates.add(SttCandidate(SttServiceType.GEMINI, geminiKey, ModelResolver.resolve(ModelRole.TRANSCRIBE)))
            }
        }

        // Always append available Gemini keys from key pool as safety net so speech input
        // never fails if primary provider is rate-limited or non-STT (e.g., DeepSeek, Grok, Anthropic)
        for (gk in com.example.data.remote.GeminiKeyPool.getAllValidKeys()) {
            if (candidates.none { it.service == SttServiceType.GEMINI && it.key == gk }) {
                candidates.add(SttCandidate(SttServiceType.GEMINI, gk, ModelResolver.resolve(ModelRole.TRANSCRIBE)))
            }
        }

        return candidates
    }

    private data class WhisperRawResponse(
        val text: String,
        val language: String? = null,
        val avgLogprob: Double? = null,
        val noSpeechProb: Double? = null
    )

    /**
     * Executes Whisper with dual-hypothesis scoring:
     * Hypothesis A: language="en" + Roman-script Hinglish biasing prompt.
     * Hypothesis B (fallback when A has low confidence or non-ASCII junk): language="hi".
     */
    private fun executeWhisperDualHypothesis(
        endpoint: String,
        model: String,
        apiKey: String,
        audioBytes: ByteArray,
        fileName: String,
        mimeType: String,
        prompt: String,
        targetLanguage: String?
    ): WhisperRawResponse? {
        val langToUse = when (targetLanguage?.lowercase()) {
            "en", "en_in" -> "en"
            "hi", "hi_in" -> "hi"
            else -> "en" // Default to English Roman script for Hinglish commands
        }

        val hypA = executeWhisper(endpoint, model, apiKey, audioBytes, fileName, mimeType, prompt, language = langToUse)
        if (hypA == null) return null

        val avgLog = hypA.avgLogprob ?: 0.0
        val isAConfident = avgLog >= -0.65 && hypA.text.isNotBlank()

        if (isAConfident || targetLanguage != null) {
            return hypA
        }

        // Run Hypothesis B with language="hi" if Hypothesis A was uncertain
        val hypB = executeWhisper(endpoint, model, apiKey, audioBytes, fileName, mimeType, prompt, language = "hi")
        if (hypB != null && (hypB.avgLogprob ?: -99.0) > avgLog) {
            Log.d(TAG, "Whisper Hypothesis B (hi) scored better: '${hypB.text}' vs '${hypA.text}'")
            return hypB
        }

        return hypA
    }

    private fun executeWhisper(
        endpoint: String,
        model: String,
        apiKey: String,
        audioBytes: ByteArray,
        fileName: String,
        mimeType: String,
        prompt: String,
        language: String? = null
    ): WhisperRawResponse? {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("file", fileName, audioBytes.toRequestBody(mimeType.toMediaType()))

        if (!language.isNullOrBlank()) {
            builder.addFormDataPart("language", language)
        }

        val reqBody = builder.build()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(reqBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()
        response.close()

        if (!response.isSuccessful) {
            Log.w(TAG, "Whisper call returned HTTP ${response.code}: $bodyString")
            return null
        }

        val json = JSONObject(bodyString)
        val text = json.optString("text", "").trim()
        val detectedLanguage = if (json.has("language")) json.optString("language") else language

        var avgLogProbSum = 0.0
        var noSpeechProbMax = 0.0
        val segments = json.optJSONArray("segments")
        if (segments != null && segments.length() > 0) {
            for (i in 0 until segments.length()) {
                val seg = segments.getJSONObject(i)
                avgLogProbSum += seg.optDouble("avg_logprob", 0.0)
                val nsp = seg.optDouble("no_speech_prob", 0.0)
                if (nsp > noSpeechProbMax) noSpeechProbMax = nsp
            }
            avgLogProbSum /= segments.length()
        }

        return WhisperRawResponse(
            text = text,
            language = detectedLanguage,
            avgLogprob = if (segments != null && segments.length() > 0) avgLogProbSum else null,
            noSpeechProb = if (segments != null && segments.length() > 0) noSpeechProbMax else null
        )
    }

    private fun executeGemini(
        apiKey: String,
        audioBytes: ByteArray,
        mimeType: String
    ): WhisperRawResponse? {
        val model = ModelResolver.resolve(ModelRole.TRANSCRIBE)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val root = JSONObject()

        // Explicit transcription system instruction
        val sysInstruction = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", "Transcribe only. Output exactly what was said. Languages: English (India) and Hindi, Hindi words in Roman script. If no clear human speech, output nothing.")))
        }
        root.put("systemInstruction", sysInstruction)

        // Contents
        val parts = JSONArray()
        parts.put(JSONObject().apply {
            put("inlineData", JSONObject().apply {
                put("mimeType", mimeType)
                put("data", base64Audio)
            })
        })
        root.put("contents", JSONArray().put(JSONObject().put("parts", parts)))

        // GenerationConfig: zero temperature, short output tokens, disable thinking for immediate transcription
        val genConfig = JSONObject().apply {
            put("temperature", 0.0)
            put("maxOutputTokens", 120)
            put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }
        root.put("generationConfig", genConfig)

        val reqBody = root.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder().url(url).post(reqBody).build()
        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()
        response.close()

        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini STT returned HTTP ${response.code}: $bodyString")
            return null
        }

        val json = JSONObject(bodyString)
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val content = candidates.getJSONObject(0).optJSONObject("content")
            val candidateParts = content?.optJSONArray("parts")
            if (candidateParts != null && candidateParts.length() > 0) {
                val text = candidateParts.getJSONObject(0).optString("text", "").trim()
                return WhisperRawResponse(text = text)
            }
        }
        return null
    }
}
