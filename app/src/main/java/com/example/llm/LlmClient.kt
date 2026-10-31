package com.example.llm

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * High-level LLM request specification.
 */
data class LlmRequest(
    val prompt: String,
    val systemPrompt: String = "",
    val history: List<Pair<String, String>> = emptyList(),
    val imagesBase64: List<String> = emptyList(),
    val mode: String = "general",
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val jsonMode: Boolean = false,
    val reasoningEffort: String? = null
)

/**
 * Structured LLM response with detailed finish reason, usage, and reasoning thoughts.
 */
data class LlmResponse(
    val text: String,
    val finishReason: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val thinkingText: String? = null,
    val rateLimit: ModelRateLimitInfo? = null
)

/**
 * Diagnostic result from a live connection test.
 */
data class ConnectionTestResult(
    val success: Boolean,
    val provider: String,
    val model: String,
    val latencyMs: Long,
    val responseSnippet: String = "",
    val error: LlmError? = null,
    val learnedLimits: ModelLimits? = null
)

/**
 * Universal, resilient LLM client that drives all features across any configured provider.
 * Implements self-healing repairs on 400/413/422/429/5xx, typed error reporting, and strict BYOK.
 */
object LlmClient {
    private const val TAG = "LlmClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Executes a streaming LLM call returning string output.
     */
    suspend fun stream(
        providerName: String,
        apiKey: String,
        modelName: String,
        request: LlmRequest,
        onChunk: suspend (String) -> Unit = {}
    ): Result<String> {
        return streamDetailed(providerName, apiKey, modelName, request, onChunk).map { it.text }
    }

    /**
     * Executes a streaming LLM call returning detailed LlmResponse with telemetry,
     * token usage, finishReason, and separate thinkingText channel.
     */
    suspend fun streamDetailed(
        providerName: String,
        apiKey: String,
        modelName: String,
        request: LlmRequest,
        onChunk: suspend (String) -> Unit = {},
        onThinking: (suspend (String) -> Unit)? = null
    ): Result<LlmResponse> = streamDetailedObserved(providerName, apiKey, modelName, request, onChunk, onThinking, null)

    /** Reports a fresh attempt before any output. Retries never share a text buffer. */
    suspend fun streamDetailedObserved(
        providerName: String,
        apiKey: String,
        modelName: String,
        request: LlmRequest,
        onChunk: suspend (String) -> Unit = {},
        onThinking: (suspend (String) -> Unit)? = null,
        onAttemptStart: (suspend (String) -> Unit)? = null
    ): Result<LlmResponse> = withContext(Dispatchers.IO) {
        val decrypted = KeyStoreCrypto.decrypt(apiKey)
        val cleanKey = (if (decrypted.isNotBlank()) decrypted else apiKey).trim()
            .removeSurrounding("\"").removeSurrounding("'").removePrefix("Bearer ").removePrefix("bearer ").trim()

        val detected = ProviderProfile.detectProviderFromKey(cleanKey, providerName)
        val profile = if (providerName.startsWith("https://") || providerName.startsWith("http://")) {
            ProviderProfile.getById(providerName)
        } else if (cleanKey.startsWith("AIza") || cleanKey.startsWith("AQ.") || cleanKey.startsWith("gsk_") ||
            cleanKey.startsWith("sk-or-") || cleanKey.startsWith("sk-ant-") || cleanKey.startsWith("xai-") ||
            cleanKey.startsWith("csk-") || cleanKey.startsWith("opencode") || cleanKey.startsWith("ashna") ||
            cleanKey.startsWith("atria") || cleanKey.startsWith("inception") || cleanKey.startsWith("fw_")) {
            detected
        } else {
            ProviderProfile.getById(providerName)
        }
        var cleanModel = modelName.trim().removePrefix("models/")

        // Provider-specific model normalization
        cleanModel = when {
            profile.id.contains("atria", ignoreCase = true) -> {
                if (cleanModel.contains("FP8", ignoreCase = true)) "Atria-Dawn-Preview-FP8"
                else if (cleanModel.isBlank() || cleanModel.equals("atria-1", true) || cleanModel.equals("atria-dawn", true) || cleanModel.contains("dawn", true)) "Atria-Dawn-Preview"
                else cleanModel
            }
            profile.id.contains("inception", ignoreCase = true) -> {
                if (cleanModel.isBlank() || cleanModel.contains("merlin", true)) "mercury-2.5"
                else cleanModel
            }
            profile.id.contains("Ashna", ignoreCase = true) -> {
                if (cleanModel.isBlank()) "ashna-x1"
                else cleanModel
            }
            profile.id.equals("Gemini", ignoreCase = true) -> {
                // Retired families (1.5 / 2.0) always 404, so never send them: ask the API which models are alive.
                if (cleanModel.isBlank() || GeminiModelCatalog.isRetired(cleanModel)) GeminiModelCatalog.pick(cleanKey) else cleanModel
            }
            else -> cleanModel
        }

        if (cleanKey.isBlank() || cleanKey.contains("MY_")) {
            val err = LlmError(
                provider = profile.displayName,
                model = cleanModel,
                httpCode = 401,
                rawMessage = "API Key is empty or unconfigured.",
                category = LlmErrorCategory.AUTH
            )
            return@withContext Result.failure(err)
        }

        // Active limits for self-healing adjustments
        var currentLimits = ModelLimitsStore.get(profile.id, cleanModel)
        val isBuildOrCoding = request.mode == "coding" || request.mode == "build"
        var maxTokensToUse = request.maxTokens ?: if (isBuildOrCoding) {
            min(currentLimits.maxOutputTokens, 16384)
        } else {
            min(currentLimits.maxOutputTokens, 4096)
        }
        var useCompletionTokensParam = currentLimits.requiresCompletionTokensParam
        var sendTemperature = currentLimits.supportsTemperature && request.temperature != null
        var mergeSystemIntoUser = !currentLimits.supportsSystemRole
        var attemptCount = 0
        val maxAttempts = if (profile.id.equals("Gemini", ignoreCase = true)) 6 else 3
        val triedGeminiModels = mutableSetOf<String>()
        var lastError: LlmError? = null

        while (attemptCount < maxAttempts) {
            attemptCount++
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            onAttemptStart?.invoke(cleanModel)
            // A child job is cancelled as soon as the caller cancels, even while OkHttp is blocked
            // in execute() or reading an SSE body. Completing it in finally avoids retaining calls.
            val attemptJob = Job(currentCoroutineContext()[Job])
            try {
                val fullText = StringBuilder()
                val thinkingBuffer = StringBuilder()
                var detectedFinishReason: String? = null
                var parsedPromptTokens: Int? = null
                var parsedCompletionTokens: Int? = null

                val isGemini = profile.id.equals("Gemini", ignoreCase = true)
                val isAnthropic = profile.id.equals("Anthropic", ignoreCase = true)

                val response = when {
                    isGemini -> executeGemini(
                        profile = profile,
                        apiKey = cleanKey,
                        model = cleanModel,
                        request = request,
                        maxTokens = maxTokensToUse,
                        sendTemperature = sendTemperature,
                        mergeSystemIntoUser = mergeSystemIntoUser,
                        cancellationJob = attemptJob
                    )
                    isAnthropic -> executeAnthropic(
                        profile = profile,
                        apiKey = cleanKey,
                        model = cleanModel,
                        request = request,
                        maxTokens = maxTokensToUse,
                        sendTemperature = sendTemperature,
                        mergeSystemIntoUser = mergeSystemIntoUser,
                        cancellationJob = attemptJob
                    )
                    else -> executeOpenAiCompatible(
                        profile = profile,
                        apiKey = cleanKey,
                        model = cleanModel,
                        request = request,
                        maxTokens = maxTokensToUse,
                        useCompletionTokensParam = useCompletionTokensParam,
                        sendTemperature = sendTemperature,
                        mergeSystemIntoUser = mergeSystemIntoUser,
                        cancellationJob = attemptJob
                    )
                }

                // Record headers for limit learning
                ModelLimitsStore.recordRateLimitHeaders(profile.id, cleanModel, response.headers)
                val rateLimitInfo = ModelRateLimitInfo(
                    limitTokens = response.header("x-ratelimit-limit-tokens")?.toLongOrNull(),
                    remainingTokens = response.header("x-ratelimit-remaining-tokens")?.toLongOrNull(),
                    resetTokensSeconds = parseDurationSeconds(response.header("x-ratelimit-reset-tokens")),
                    limitRequests = response.header("x-ratelimit-limit-requests")?.toLongOrNull(),
                    remainingRequests = response.header("x-ratelimit-remaining-requests")?.toLongOrNull(),
                    resetRequestsSeconds = parseDurationSeconds(response.header("x-ratelimit-reset-requests"))
                )

                if (!response.isSuccessful) {
                    val code = response.code
                    val bodyStr = response.body?.string().orEmpty()
                    val retryAfterHdr = response.header("retry-after")
                    response.close()
                    val parsedError = parseUpstreamError(profile.displayName, cleanModel, code, bodyStr, retryAfterHdr)
                    lastError = parsedError

                    // Check for RequestTooLarge / TPM / Context length overflow
                    if (parsedError.category == LlmErrorCategory.TOO_LARGE) {
                        val limitTokens = parsedError.limitTokens ?: extractTokenLimit(bodyStr) ?: 8000L
                        val requestedTokens = parsedError.requestedTokens ?: (request.prompt.length / 3).toLong()
                        throw RequestTooLargeException(
                            provider = profile.displayName,
                            model = cleanModel,
                            limitTokens = limitTokens,
                            requestedTokens = requestedTokens,
                            detail = parsedError.rawMessage
                        )
                    }

                    // Self-healing attempt on 400 parameter mismatch
                    if (code == 400 || code == 422) {
                        var healed = false
                        if (bodyStr.contains("max_completion_tokens", true) || bodyStr.contains("completion_tokens", true)) {
                            useCompletionTokensParam = true
                            currentLimits = currentLimits.copy(requiresCompletionTokensParam = true)
                            ModelLimitsStore.update(currentLimits)
                            healed = true
                        } else if (bodyStr.contains("max_tokens", true) && useCompletionTokensParam) {
                            useCompletionTokensParam = false
                            currentLimits = currentLimits.copy(requiresCompletionTokensParam = false)
                            ModelLimitsStore.update(currentLimits)
                            healed = true
                        }
                        val clampedLimit = extractTokenLimit(bodyStr)
                        if (clampedLimit != null && clampedLimit > 0 && clampedLimit < maxTokensToUse) {
                            maxTokensToUse = clampedLimit.toInt()
                            currentLimits = currentLimits.copy(maxOutputTokens = maxTokensToUse)
                            ModelLimitsStore.update(currentLimits)
                            healed = true
                        }
                        if (bodyStr.contains("temperature", true) && sendTemperature) {
                            sendTemperature = false
                            currentLimits = currentLimits.copy(supportsTemperature = false)
                            ModelLimitsStore.update(currentLimits)
                            healed = true
                        }
                        if (bodyStr.contains("system", true) && !mergeSystemIntoUser) {
                            mergeSystemIntoUser = true
                            currentLimits = currentLimits.copy(supportsSystemRole = false)
                            ModelLimitsStore.update(currentLimits)
                            healed = true
                        }
                        if (healed && attemptCount < maxAttempts) {
                            Log.i(TAG, "Self-healing applied for $cleanModel (attempt $attemptCount). Retrying...")
                            continue
                        }
                    }

                    // Self-healing attempt on 404 Model Not Found
                    if (code == 404 || parsedError.category == LlmErrorCategory.MODEL_NOT_FOUND) {
                        if (attemptCount < maxAttempts) {
                            val altModel = when {
                                profile.id.equals("Gemini", true) -> {
                                    triedGeminiModels += cleanModel
                                    GeminiModelCatalog.alternative(cleanKey, cleanModel, triedGeminiModels)
                                }
                                profile.id.contains("Ashna", true) || profile.id.contains("Asana", true) -> {
                                    val alts = listOf("deepseek-v4.1-flash", "deepseek-chat", "gpt-4o-mini", "ashna-x1", "ashna-x1-flash", "ashna-coder")
                                    alts.firstOrNull { !it.equals(cleanModel, ignoreCase = true) }
                                }
                                profile.id.equals("OpenRouter", true) -> {
                                    profile.defaultFallbackModels.firstOrNull { !it.equals(cleanModel, ignoreCase = true) }
                                }
                                profile.id.equals("Groq", true) -> {
                                    profile.defaultFallbackModels.firstOrNull { !it.equals(cleanModel, ignoreCase = true) }
                                }
                                else -> profile.defaultFallbackModels.firstOrNull { !it.equals(cleanModel, ignoreCase = true) }
                            }
                            if (altModel != null && !altModel.equals(cleanModel, ignoreCase = true)) {
                                Log.i(TAG, "Model $cleanModel not found on ${profile.displayName} (404). Self-healing with alternative model $altModel (attempt $attemptCount)...")
                                cleanModel = altModel
                                delay(300L)
                                continue
                            }
                        }
                    }

                    // Handle 429 rate limit with Retry-After header or backoff
                    if (code == 429) {
                        val isQuotaExhausted = parsedError.rawMessage.contains("quota", true) ||
                            parsedError.rawMessage.contains("credit", true) ||
                            parsedError.rawMessage.contains("balance", true) ||
                            parsedError.rawMessage.contains("insufficient", true)

                        if (isQuotaExhausted) {
                            if (profile.id.contains("Ashna", true) && !cleanModel.equals("ashna-x1", true) && attemptCount < maxAttempts) {
                                Log.i(TAG, "Quota limit on Ashna model $cleanModel. Self-healing with ashna-x1...")
                                cleanModel = "ashna-x1"
                                maxTokensToUse = 800
                                delay(600L)
                                continue
                            }
                            Log.w(TAG, "${profile.displayName} ($cleanModel) quota/balance depleted. Failing immediately for fast fallback.")
                            return@withContext Result.failure(parsedError)
                        }

                        if (attemptCount < maxAttempts) {
                            val retryAfter = parsedError.retryAfterSeconds ?: 2L
                            val cappedWait = min(retryAfter, 10L)
                            if (!isBuildOrCoding) {
                                maxTokensToUse = min(maxTokensToUse, 1024)
                            }
                            if (profile.id.contains("Ashna", true) && !cleanModel.equals("ashna-x1", true)) {
                                cleanModel = "ashna-x1"
                                maxTokensToUse = 800
                            }
                            Log.w(TAG, "Rate-limited by ${profile.displayName}. Waiting ${cappedWait}s before retry (attempt $attemptCount)...")
                            delay(cappedWait * 1000L)
                            continue
                        } else {
                            return@withContext Result.failure(parsedError)
                        }
                    }

                    // Handle 5xx with exponential backoff and jitter
                    if (code in 500..599) {
                        val backoffMs = (1000L * (1 shl (attemptCount - 1))) + Random.nextLong(100, 500)
                        Log.w(TAG, "Upstream 5xx from ${profile.displayName}. Backing off for ${backoffMs}ms...")
                        delay(backoffMs)
                        continue
                    }

                    return@withContext Result.failure(parsedError)
                }

                // Parse streaming SSE body or standard JSON
                val rawBodyBuffer = StringBuilder()
                val bodyStream = response.body?.byteStream()
                if (bodyStream != null) {
                    val reader = BufferedReader(InputStreamReader(bodyStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val curLine = line ?: continue
                        rawBodyBuffer.append(curLine).append("\n")
                        val trimmedLine = curLine.trim()
                        if (trimmedLine.startsWith("data:")) {
                            val dataJson = trimmedLine.removePrefix("data:").trim()
                            if (dataJson.isEmpty() || dataJson == "[DONE]") continue
                            try {
                                val chunkResult = when {
                                    isGemini -> parseGeminiStreamChunkWithMetadata(dataJson)
                                    isAnthropic -> parseAnthropicStreamChunkWithMetadata(dataJson)
                                    else -> parseOpenAiStreamChunkWithMetadata(dataJson)
                                }
                                if (chunkResult.finishReason != null) {
                                    detectedFinishReason = chunkResult.finishReason
                                }
                                if (chunkResult.promptTokens != null) parsedPromptTokens = chunkResult.promptTokens
                                if (chunkResult.completionTokens != null) parsedCompletionTokens = chunkResult.completionTokens

                                if (chunkResult.reasoningText.isNotEmpty()) {
                                    thinkingBuffer.append(chunkResult.reasoningText)
                                    onThinking?.invoke(chunkResult.reasoningText)
                                }
                                if (chunkResult.text.isNotEmpty()) {
                                    val cleanedChunk = sanitizeSpecialTokens(chunkResult.text)
                                    if (cleanedChunk.isNotEmpty()) {
                                        fullText.append(cleanedChunk)
                                        onChunk(cleanedChunk)
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Ignore SSE parsing glitches, but never swallow cancellation.
                            }
                        } else if (trimmedLine.startsWith("0:\"") && trimmedLine.endsWith("\"")) {
                            val inner = trimmedLine.substring(3, trimmedLine.length - 1)
                            val chunkText = sanitizeSpecialTokens(inner.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"))
                            if (chunkText.isNotEmpty()) {
                                fullText.append(chunkText)
                                onChunk(chunkText)
                            }
                        }
                    }
                    response.close()
                }

                // If SSE parsing produced nothing (e.g. server returned standard JSON or unstreamed payload)
                if (fullText.isEmpty() && rawBodyBuffer.isNotEmpty()) {
                    val rawText = rawBodyBuffer.toString().trim()
                    val extracted = extractTextFromRawBody(rawText, isGemini, isAnthropic)
                    if (extracted.isNotBlank()) {
                        val cleanedExtracted = sanitizeSpecialTokens(extracted)
                        fullText.append(cleanedExtracted)
                        onChunk(cleanedExtracted)
                    } else {
                        try {
                            val errJson = JSONObject(rawText)
                            val errObj = errJson.optJSONObject("error")
                            if (errObj != null) {
                                val errMsg = errObj.optString("message", "API Error")
                                return@withContext Result.failure(
                                    LlmError(profile.displayName, cleanModel, 200, errMsg, LlmErrorCategory.UNKNOWN)
                                )
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                // If still empty and not Gemini/Anthropic, fallback to non-streaming POST once
                if (fullText.isEmpty() && !isGemini && !isAnthropic) {
                    try {
                        val nonStreamResp = executeOpenAiCompatible(
                            profile = profile,
                            apiKey = cleanKey,
                            model = cleanModel,
                            request = request,
                            maxTokens = maxTokensToUse,
                            useCompletionTokensParam = useCompletionTokensParam,
                            sendTemperature = sendTemperature,
                            mergeSystemIntoUser = mergeSystemIntoUser,
                            stream = false,
                            cancellationJob = attemptJob
                        )
                        if (nonStreamResp.isSuccessful) {
                            val nonStreamBody = nonStreamResp.body?.string().orEmpty()
                            nonStreamResp.close()
                            val extracted = extractTextFromRawBody(nonStreamBody, false, false)
                            if (extracted.isNotBlank()) {
                                val cleanedExtracted = sanitizeSpecialTokens(extracted)
                                fullText.append(cleanedExtracted)
                                onChunk(cleanedExtracted)
                            }
                        } else {
                            nonStreamResp.close()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        Log.w(TAG, "Non-streaming fallback failed: ${e.message}")
                    }
                }

                val resultStr = fullText.toString()
                if (resultStr.isNotBlank()) {
                    return@withContext Result.success(
                        LlmResponse(
                            text = resultStr,
                            finishReason = detectedFinishReason,
                            promptTokens = parsedPromptTokens,
                            completionTokens = parsedCompletionTokens,
                            thinkingText = thinkingBuffer.toString().ifBlank { null },
                            rateLimit = rateLimitInfo
                        )
                    )
                } else {
                    return@withContext Result.failure(
                        LlmError(profile.displayName, cleanModel, 200, "Empty response received from upstream model.", LlmErrorCategory.UNKNOWN)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RequestTooLargeException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                val err = parseException(profile.displayName, cleanModel, e)
                lastError = err
                if (attemptCount < maxAttempts && (err.category == LlmErrorCategory.NETWORK || err.category == LlmErrorCategory.TIMEOUT)) {
                    delay(1500L)
                    continue
                }
                return@withContext Result.failure(err)
            } finally {
                attemptJob.complete()
            }
        }
        Result.failure(lastError ?: LlmError(profile.displayName, cleanModel, null, "All retry attempts failed.", LlmErrorCategory.UNKNOWN))
    }

    /**
     * Executes a non-streaming complete LLM call.
     */
    suspend fun complete(
        providerName: String,
        apiKey: String,
        modelName: String,
        request: LlmRequest
    ): Result<String> {
        val resultSb = StringBuilder()
        return stream(providerName, apiKey, modelName, request) { chunk ->
            resultSb.append(chunk)
        }
    }

    /**
     * Tests connectivity with a tiny prompt and returns measured latency, detected capabilities, and learned limits.
     */
    suspend fun testConnection(
        providerName: String,
        apiKey: String,
        modelName: String
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val testRequest = LlmRequest(
            prompt = "Hi, reply with 'OK' only.",
            systemPrompt = "",
            maxTokens = 8,
            temperature = 0.7
        )
        var result = complete(providerName, apiKey, modelName, testRequest)
        if (!result.isSuccess && (providerName.contains("Ashna", true) || providerName.contains("Asana", true)) && !modelName.equals("ashna-x1", true)) {
            result = complete(providerName, apiKey, "ashna-x1", testRequest)
        }
        val latency = System.currentTimeMillis() - start

        if (result.isSuccess) {
            val reply = result.getOrNull().orEmpty().trim()
            val profile = ProviderProfile.getById(providerName)
            val limits = ModelLimitsStore.get(profile.id, modelName)
            ConnectionTestResult(
                success = true,
                provider = profile.displayName,
                model = modelName,
                latencyMs = latency,
                responseSnippet = reply.take(60),
                learnedLimits = limits
            )
        } else {
            val err = result.exceptionOrNull() as? LlmError
                ?: LlmError(providerName, modelName, null, result.exceptionOrNull()?.message ?: "Unknown error", LlmErrorCategory.UNKNOWN)
            ConnectionTestResult(
                success = false,
                provider = providerName,
                model = modelName,
                latencyMs = latency,
                error = err
            )
        }
    }

    // --- Provider Implementations ---

    private fun executeCancellable(request: Request, job: Job): Response {
        val call = httpClient.newCall(request)
        job.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        return call.execute()
    }

    private fun executeOpenAiCompatible(
        profile: ProviderProfile,
        apiKey: String,
        model: String,
        request: LlmRequest,
        maxTokens: Int,
        useCompletionTokensParam: Boolean,
        sendTemperature: Boolean,
        mergeSystemIntoUser: Boolean,
        stream: Boolean = true,
        cancellationJob: Job
    ): Response {
        val url = "${profile.baseUrl.trimEnd('/')}${profile.chatPath}"
        val normalizedModel = when {
            profile.id.contains("atria", ignoreCase = true) -> {
                if (model.contains("FP8", ignoreCase = true)) "Atria-Dawn-Preview-FP8"
                else if (model.isBlank() || model.equals("atria-1", true) || model.equals("atria-dawn", true) || model.contains("dawn", true)) "Atria-Dawn-Preview"
                else model
            }
            profile.id.contains("inception", ignoreCase = true) -> {
                if (model.isBlank() || model.contains("merlin", true)) "mercury-2.5" else model
            }
            profile.id.contains("Ashna", ignoreCase = true) -> {
                if (model.isBlank()) "ashna-x1" else model
            }
            else -> model
        }

        val isAshna = profile.id.contains("Ashna", ignoreCase = true) || profile.id.contains("Asana", ignoreCase = true)
        val effectiveMaxTokens = if (isAshna && !request.mode.equals("coding", true) && !request.mode.equals("build", true)) {
            min(maxTokens, 1500)
        } else {
            maxTokens
        }

        val root = JSONObject().apply {
            put("model", normalizedModel)
            put("stream", stream)
            val requiresCompTokens = !isAshna && (useCompletionTokensParam || normalizedModel.contains("o1", true) || normalizedModel.contains("o3", true) || normalizedModel.contains("gpt-6", true) || normalizedModel.contains("astra", true)) && !normalizedModel.contains("ashna", true)
            if (requiresCompTokens) {
                put("max_completion_tokens", effectiveMaxTokens)
            } else {
                put("max_tokens", effectiveMaxTokens)
            }
            if (sendTemperature && request.temperature != null && request.temperature > 0.0) {
                put("temperature", request.temperature)
            }
            if (request.jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }

            // Reasoning effort configuration (e.g. Groq gpt-oss or OpenAI o1/o3/gpt-5)
            if (request.reasoningEffort != null && profile.dialect.reasoningPolicy.supportsReasoningEffort) {
                val effort = request.reasoningEffort.lowercase()
                if (effort in profile.dialect.reasoningPolicy.allowedEfforts) {
                    put(profile.dialect.reasoningPolicy.parameterName, effort)
                }
            } else if (profile.id.equals("Groq", true) && normalizedModel.contains("gpt-oss", true)) {
                put("reasoning_effort", "low")
            }

            val messagesArray = JSONArray()
            val sysPrompt = request.systemPrompt.trim()

            if (sysPrompt.isNotBlank() && !mergeSystemIntoUser) {
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", sysPrompt)
                })
            }

            for ((role, text) in request.history.takeLast(8)) {
                val cleanRole = if (role.equals("user", true)) "user" else "assistant"
                messagesArray.put(JSONObject().apply {
                    put("role", cleanRole)
                    put("content", text)
                })
            }

            val userContent = if (mergeSystemIntoUser && sysPrompt.isNotBlank()) {
                "$sysPrompt\n\n[Instruction: Please respond directly to the user's message below]\n\n${request.prompt}"
            } else {
                request.prompt
            }

            if (request.imagesBase64.isNotEmpty() && profile.dialect.supportsVision) {
                val parts = JSONArray()
                parts.put(JSONObject().apply {
                    put("type", "text")
                    put("text", userContent)
                })
                for (img in request.imagesBase64) {
                    val formatted = if (img.startsWith("data:image/")) img else "data:image/jpeg;base64,$img"
                    parts.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", formatted))
                    })
                }
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", parts)
                })
            } else {
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            put("messages", messagesArray)
        }

        val httpReq = Request.Builder()
            .url(url)
            .post(root.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                addHeader("Accept", if (stream) "text/event-stream, application/json" else "application/json")
                if (isAshna) {
                    addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                } else {
                    addHeader("User-Agent", "SAIF-AI/1.0")
                }
                when (profile.authStyle) {
                    AuthStyle.BEARER_TOKEN -> addHeader("Authorization", "Bearer $apiKey")
                    AuthStyle.X_API_KEY_HEADER -> addHeader("x-api-key", apiKey)
                    else -> addHeader("Authorization", "Bearer $apiKey")
                }
                if (isAshna) {
                    addHeader("X-Api-Key", apiKey)
                    addHeader("Api-Key", apiKey)
                }
                if (profile.id.contains("atria", ignoreCase = true)) {
                    addHeader("X-Api-Key", apiKey)
                }
                if (profile.id.equals("OpenRouter", true)) {
                    addHeader("HTTP-Referer", "https://github.com/saifai/assistant")
                    addHeader("X-Title", "SAIF AI Android Assistant")
                }
                if (profile.id.contains("OpenCode", true)) {
                    addHeader("X-Api-Key", apiKey)
                    addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                }
            }
            .build()

        var initialResp = executeCancellable(httpReq, cancellationJob)
        // If Ashna AI returns 404 on /v1/api/chat/completions, try fallback to /v1/chat/completions
        if (isAshna && initialResp.code == 404 && url.contains("/v1/api/chat/completions")) {
            val altUrl = url.replace("/v1/api/chat/completions", "/v1/chat/completions")
            initialResp.close()
            val altReq = httpReq.newBuilder().url(altUrl).build()
            initialResp = executeCancellable(altReq, cancellationJob)
        }
        return initialResp
    }

    private fun executeAnthropic(
        profile: ProviderProfile,
        apiKey: String,
        model: String,
        request: LlmRequest,
        maxTokens: Int,
        sendTemperature: Boolean,
        mergeSystemIntoUser: Boolean,
        cancellationJob: Job
    ): Response {
        val url = "${profile.baseUrl.trimEnd('/')}${profile.chatPath}"
        val root = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("max_tokens", maxTokens)
            if (sendTemperature && request.temperature != null) {
                put("temperature", request.temperature)
            }
            if (request.systemPrompt.isNotBlank() && !mergeSystemIntoUser) {
                put("system", request.systemPrompt)
            }

            val messagesArray = JSONArray()
            for ((role, text) in request.history.takeLast(8)) {
                val anthropicRole = if (role.equals("user", true)) "user" else "assistant"
                messagesArray.put(JSONObject().apply {
                    put("role", anthropicRole)
                    put("content", text)
                })
            }

            val userContent = if (mergeSystemIntoUser && request.systemPrompt.isNotBlank()) {
                "${request.systemPrompt}\n\n${request.prompt}"
            } else {
                request.prompt
            }
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                if (request.imagesBase64.isNotEmpty() && profile.dialect.supportsVision) {
                    val parts = JSONArray()
                    for (image in request.imagesBase64) {
                        parts.put(JSONObject().put("type", "image").put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", image.substringAfter(",", image))
                        }))
                    }
                    parts.put(JSONObject().put("type", "text").put("text", userContent))
                    put("content", parts)
                } else {
                    put("content", userContent)
                }
            })
            put("messages", messagesArray)
        }

        val httpReq = Request.Builder()
            .url(url)
            .post(root.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .build()

        return executeCancellable(httpReq, cancellationJob)
    }

    private fun executeGemini(
        profile: ProviderProfile,
        apiKey: String,
        model: String,
        request: LlmRequest,
        maxTokens: Int,
        sendTemperature: Boolean,
        mergeSystemIntoUser: Boolean,
        cancellationJob: Job
    ): Response {
        val rawModel = model.removePrefix("models/").trim()
        val cleanModel = rawModel.ifBlank { GeminiModelCatalog.DEFAULT_MODEL }
        val url = "${profile.baseUrl.trimEnd('/')}/models/$cleanModel:streamGenerateContent?alt=sse&key=$apiKey"
        val root = JSONObject().apply {
            val genConfig = JSONObject().apply {
                if (sendTemperature && request.temperature != null) {
                    put("temperature", request.temperature)
                }
                put("maxOutputTokens", maxTokens)
                if (request.jsonMode) {
                    put("responseMimeType", "application/json")
                }
            }
            put("generationConfig", genConfig)

            if (request.systemPrompt.isNotBlank() && !mergeSystemIntoUser) {
                val sysPart = JSONObject().put("text", request.systemPrompt)
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(sysPart)))
            }

            val contentsArray = JSONArray()
            for ((role, text) in request.history.takeLast(8)) {
                val gRole = if (role.equals("user", true)) "user" else "model"
                contentsArray.put(JSONObject().apply {
                    put("role", gRole)
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                })
            }

            val userParts = JSONArray()
            val textContent = if (mergeSystemIntoUser && request.systemPrompt.isNotBlank()) {
                "System Instruction: ${request.systemPrompt}\n\n${request.prompt}"
            } else {
                request.prompt
            }
            userParts.put(JSONObject().put("text", textContent))

            for (img in request.imagesBase64) {
                val cleanB64 = if (img.contains(",")) img.substringAfter(",") else img
                userParts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", cleanB64)
                    })
                })
            }

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", userParts)
            })
            put("contents", contentsArray)
        }

        val httpReq = Request.Builder()
            .url(url)
            .post(root.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return executeCancellable(httpReq, cancellationJob)
    }

    // --- Stream Parsers & Metadata ---

    data class StreamChunkResult(
        val text: String = "",
        val reasoningText: String = "",
        val finishReason: String? = null,
        val promptTokens: Int? = null,
        val completionTokens: Int? = null
    )

    fun sanitizeSpecialTokens(raw: String): String {
        if (raw.isEmpty()) return ""
        var t = raw
        t = t.replace(Regex("(?i)<think>[\\s\\S]*?</think>"), "")
        t = t.replace("<think>", "").replace("</think>", "")
        t = t.replace("<|channel|>", "").replace("<|message|>", "").replace("<|end|>", "").replace("assistantfinal", "")
        return t
    }

    private fun parseOpenAiStreamChunkWithMetadata(dataJson: String): StreamChunkResult {
        return try {
            val clean = dataJson.trim()
            if (clean.startsWith("0:\"") && clean.endsWith("\"")) {
                val inner = clean.substring(3, clean.length - 1)
                return StreamChunkResult(text = inner.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"))
            }
            val json = JSONObject(clean)
            var finishReason: String? = null
            var promptTokens: Int? = null
            var completionTokens: Int? = null
            var text = ""
            var reasoning = ""

            val usage = json.optJSONObject("usage")
            if (usage != null) {
                promptTokens = usage.optInt("prompt_tokens")
                completionTokens = usage.optInt("completion_tokens")
            }

            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice0 = choices.getJSONObject(0)
                if (!choice0.isNull("finish_reason")) {
                    val fr = choice0.optString("finish_reason")
                    if (fr.isNotBlank() && fr != "null") finishReason = fr
                }
                val delta = choice0.optJSONObject("delta")
                if (delta != null) {
                    val rContent = delta.optString("reasoning_content", "")
                    val rText = delta.optString("reasoning", "")
                    reasoning = if (rContent.isNotBlank() && rContent != "null") rContent else if (rText.isNotBlank() && rText != "null") rText else ""

                    if (!delta.isNull("content")) {
                        val contentObj = delta.opt("content")
                        if (contentObj is JSONArray) {
                            val sb = StringBuilder()
                            for (i in 0 until contentObj.length()) {
                                val part = contentObj.opt(i)
                                if (part is JSONObject) sb.append(part.optString("text", ""))
                                else if (part is String) sb.append(part)
                            }
                            text = sb.toString()
                        } else {
                            val c = delta.optString("content", "")
                            if (c.isNotEmpty() && c != "null") text = c
                        }
                    }
                    if (text.isEmpty() && !delta.isNull("text")) {
                        val t = delta.optString("text", "")
                        if (t.isNotEmpty() && t != "null") text = t
                    }
                    if (text.isEmpty() && !delta.isNull("textDelta")) {
                        val td = delta.optString("textDelta", "")
                        if (td.isNotEmpty() && td != "null") text = td
                    }
                }
                val msg = choice0.optJSONObject("message")
                if (msg != null && text.isEmpty()) {
                    val rContent = msg.optString("reasoning_content", "")
                    val rText = msg.optString("reasoning", "")
                    if (reasoning.isEmpty()) {
                        reasoning = if (rContent.isNotBlank() && rContent != "null") rContent else if (rText.isNotBlank() && rText != "null") rText else ""
                    }
                    val c = msg.optString("content", "")
                    if (c.isNotEmpty() && c != "null") text = c
                }
                if (text.isEmpty() && !choice0.isNull("text")) {
                    val t = choice0.optString("text", "")
                    if (t.isNotEmpty() && t != "null") text = t
                }
            }
            if (text.isEmpty() && !json.isNull("text")) {
                text = json.optString("text", "")
            }
            if (text.isEmpty() && !json.isNull("content")) {
                text = json.optString("content", "")
            }
            StreamChunkResult(
                text = text,
                reasoningText = reasoning,
                finishReason = finishReason,
                promptTokens = promptTokens,
                completionTokens = completionTokens
            )
        } catch (e: Exception) {
            StreamChunkResult()
        }
    }

    private fun parseAnthropicStreamChunkWithMetadata(dataJson: String): StreamChunkResult {
        return try {
            val json = JSONObject(dataJson)
            val type = json.optString("type", "")
            var text = ""
            var reasoning = ""
            var finishReason: String? = null

            when (type) {
                "content_block_delta" -> {
                    val delta = json.optJSONObject("delta")
                    val deltaType = delta?.optString("type", "")
                    if (deltaType == "thinking_delta") {
                        reasoning = delta.optString("thinking", "")
                    } else {
                        text = delta?.optString("text", "") ?: ""
                    }
                }
                "message_delta" -> {
                    val delta = json.optJSONObject("delta")
                    finishReason = delta?.optString("stop_reason")
                }
            }
            StreamChunkResult(text = text, reasoningText = reasoning, finishReason = finishReason)
        } catch (e: Exception) {
            StreamChunkResult()
        }
    }

    private fun parseGeminiStreamChunkWithMetadata(dataJson: String): StreamChunkResult {
        return try {
            val json = JSONObject(dataJson)
            val candidates = json.optJSONArray("candidates") ?: return StreamChunkResult()
            if (candidates.length() == 0) return StreamChunkResult()
            val candidate0 = candidates.getJSONObject(0)
            val finishReason = candidate0.optString("finishReason").takeIf { it.isNotBlank() }
            val parts = candidate0.optJSONObject("content")?.optJSONArray("parts") ?: return StreamChunkResult(finishReason = finishReason)
            val sb = StringBuilder()
            val thoughtSb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.optBoolean("thought", false)) {
                    thoughtSb.append(part.optString("text", ""))
                } else {
                    sb.append(part.optString("text", ""))
                }
            }
            val usageMeta = json.optJSONObject("usageMetadata")
            val pTokens = usageMeta?.optInt("promptTokenCount")
            val cTokens = usageMeta?.optInt("candidatesTokenCount")
            StreamChunkResult(
                text = sb.toString(),
                reasoningText = thoughtSb.toString(),
                finishReason = finishReason,
                promptTokens = pTokens,
                completionTokens = cTokens
            )
        } catch (e: Exception) {
            StreamChunkResult()
        }
    }

    private fun parseOpenAiStreamChunk(dataJson: String): String {
        return parseOpenAiStreamChunkWithMetadata(dataJson).text
    }

    private fun extractTextFromRawBody(raw: String, isGemini: Boolean, isAnthropic: Boolean): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.contains("0:\"")) {
            val sb = StringBuilder()
            Regex("""0:"((?:[^"\\]|\\.)*)"""").findAll(trimmed).forEach { m ->
                val inner = m.groupValues[1]
                sb.append(inner.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"))
            }
            if (sb.isNotEmpty()) return sb.toString()
        }

        return try {
            val json = JSONObject(trimmed)
            if (isGemini) {
                val candidates = json.optJSONArray("candidates") ?: return ""
                if (candidates.length() == 0) return ""
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return ""
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.getJSONObject(i).optString("text", ""))
                }
                return sb.toString()
            }
            if (isAnthropic) {
                val contentArr = json.optJSONArray("content")
                if (contentArr != null && contentArr.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until contentArr.length()) {
                        val block = contentArr.optJSONObject(i)
                        if (block != null) {
                            sb.append(block.optString("text", ""))
                        }
                    }
                    if (sb.isNotEmpty()) return sb.toString()
                }
                val t = json.optString("text", "")
                if (t.isNotEmpty() && t != "null") return t
                return ""
            }
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice0 = choices.getJSONObject(0)
                val msg = choice0.optJSONObject("message")
                if (msg != null) {
                    val content = msg.optString("content", "")
                    if (content.isNotEmpty() && content != "null") return content
                }
                val delta = choice0.optJSONObject("delta")
                if (delta != null) {
                    val content = delta.optString("content", "")
                    if (content.isNotEmpty() && content != "null") return content
                }
                val t = choice0.optString("text", "")
                if (t.isNotEmpty() && t != "null") return t
            }
            if (!json.isNull("text")) return json.optString("text", "")
            if (!json.isNull("content")) return json.optString("content", "")
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseAnthropicStreamChunk(dataJson: String): String {
        return parseAnthropicStreamChunkWithMetadata(dataJson).text
    }

    private fun parseGeminiStreamChunk(dataJson: String): String {
        return parseGeminiStreamChunkWithMetadata(dataJson).text
    }

    // --- Error Parsers & Helpers ---

    fun parseDurationMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        trimmed.toLongOrNull()?.let { return it * 1000L }

        // Format: (\d+)m(\d+(\.\d+)?)s e.g. "7m12.5s", "14s", "850ms"
        val minSecRegex = Regex("""(?:(\d+)m)?\s*([0-9.]+)\s*s""", RegexOption.IGNORE_CASE)
        val match = minSecRegex.find(trimmed)
        if (match != null) {
            val mins = match.groupValues[1].ifBlank { null }?.toLongOrNull() ?: 0L
            val secs = match.groupValues[2].ifBlank { null }?.toDoubleOrNull() ?: 0.0
            return (mins * 60_000L) + (secs * 1000.0).toLong()
        }

        val msMatch = Regex("""([0-9]+)\s*ms""", RegexOption.IGNORE_CASE).find(trimmed)
        if (msMatch != null) {
            return msMatch.groupValues[1].toLongOrNull()
        }
        return null
    }

    fun parseDurationSeconds(raw: String?): Long? {
        val ms = parseDurationMs(raw) ?: return null
        return (ms / 1000L).coerceAtLeast(1L)
    }

    fun parseUpstreamError(
        provider: String,
        model: String,
        httpCode: Int,
        body: String,
        retryAfterHeader: String? = null
    ): LlmError {
        val sanitizedBody = KeyStoreCrypto.redact(body)
        var parsedMessage = ""

        try {
            val json = JSONObject(body)
            parsedMessage = json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
                ?: json.optString("detail")
        } catch (e: Exception) {
            parsedMessage = sanitizedBody.take(300)
        }

        if (parsedMessage.isBlank()) {
            parsedMessage = "HTTP $httpCode response without descriptive error message."
        }

        // Scope detection (TPM, TPD, RPM, RPD)
        val lowerMsg = parsedMessage.lowercase()
        val scope = when {
            lowerMsg.contains("tokens per day") || lowerMsg.contains("tpd") || lowerMsg.contains("tokens a day") -> "TPD"
            lowerMsg.contains("requests per day") || lowerMsg.contains("rpd") || lowerMsg.contains("requests a day") -> "RPD"
            lowerMsg.contains("tokens per minute") || lowerMsg.contains("tpm") || lowerMsg.contains("tokens a minute") -> "TPM"
            lowerMsg.contains("requests per minute") || lowerMsg.contains("rpm") || lowerMsg.contains("requests a minute") -> "RPM"
            else -> null
        }

        // Parse limit and requested numbers (e.g. "Limit 8000, Requested 9500")
        val limitMatch = Regex("""\b(?:limit|allowed)\s*[:=]?\s*([0-9,]+)""", RegexOption.IGNORE_CASE).find(parsedMessage)
            ?: Regex("""([0-9,]+)\s*(?:max_tokens|token limit)""", RegexOption.IGNORE_CASE).find(parsedMessage)
        val limitVal = limitMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()

        val requestedMatch = Regex("""\b(?:requested|needed)\s*[:=]?\s*([0-9,]+)""", RegexOption.IGNORE_CASE).find(parsedMessage)
            ?: Regex("""\b(?:used\s*:\s*[0-9,]+,\s*)?requested\s*([0-9,]+)""", RegexOption.IGNORE_CASE).find(parsedMessage)
        val requestedVal = requestedMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()

        // Extract retry-after hints
        var retryAfterMs: Long? = null
        if (!retryAfterHeader.isNullOrBlank()) {
            retryAfterMs = parseDurationMs(retryAfterHeader)
        }
        if (retryAfterMs == null) {
            val retryInMatch = Regex("""(?:try again in|retry after)\s+([0-9a-zA-Z.]+)""", RegexOption.IGNORE_CASE).find(parsedMessage)
            if (retryInMatch != null) {
                retryAfterMs = parseDurationMs(retryInMatch.groupValues[1])
            }
        }
        val retryAfterSec = retryAfterMs?.let { it / 1000L }

        // Category classification order: AUTH, then TOO_LARGE (including TPM size rejections), then RATE_LIMIT
        val isTooLarge = httpCode == 413 ||
                parsedMessage.contains("request too large", ignoreCase = true) ||
                parsedMessage.contains("too large for model", ignoreCase = true) ||
                parsedMessage.contains("reduce your message", ignoreCase = true) ||
                parsedMessage.contains("reduce the length of the messages", ignoreCase = true) ||
                parsedMessage.contains("context_length_exceeded", ignoreCase = true) ||
                parsedMessage.contains("maximum context length", ignoreCase = true) ||
                (httpCode != 429 && parsedMessage.contains("exceeded", ignoreCase = true) && parsedMessage.contains("limit", ignoreCase = true) && parsedMessage.contains("tokens per minute", ignoreCase = true))

        val category = when {
            httpCode == 401 || httpCode == 403 || parsedMessage.contains("api key", true) || parsedMessage.contains("unauthorized", true) ->
                LlmErrorCategory.AUTH
            isTooLarge ->
                LlmErrorCategory.TOO_LARGE
            httpCode == 429 || parsedMessage.contains("rate limit", true) || parsedMessage.contains("quota", true) || parsedMessage.contains("tokens per minute", true) ->
                LlmErrorCategory.RATE_LIMIT
            httpCode == 404 || parsedMessage.contains("model not found", true) || parsedMessage.contains("does not exist", true) ->
                LlmErrorCategory.MODEL_NOT_FOUND
            httpCode in 400..499 ->
                LlmErrorCategory.BAD_REQUEST
            httpCode in 500..599 ->
                LlmErrorCategory.SERVER
            else ->
                LlmErrorCategory.UNKNOWN
        }

        return LlmError(
            provider = provider,
            model = model,
            httpCode = httpCode,
            rawMessage = parsedMessage,
            category = category,
            retryAfterSeconds = retryAfterSec,
            scope = scope,
            limitTokens = limitVal,
            requestedTokens = requestedVal,
            retryAfterMs = retryAfterMs
        )
    }

    private fun parseException(provider: String, model: String, e: Exception): LlmError {
        val msg = e.message ?: e.javaClass.simpleName
        val cat = when {
            e is java.net.SocketTimeoutException || msg.contains("timeout", true) -> LlmErrorCategory.TIMEOUT
            e is java.net.UnknownHostException || e is java.net.ConnectException -> LlmErrorCategory.NETWORK
            else -> LlmErrorCategory.UNKNOWN
        }
        return LlmError(
            provider = provider,
            model = model,
            httpCode = null,
            rawMessage = msg,
            category = cat
        )
    }

    private fun extractTokenLimit(body: String): Long? {
        val match = Regex("""(?:limit|maximum|allowed)(?:\s+is)?\s+([0-9]+)\s+tokens?""", RegexOption.IGNORE_CASE).find(body)
            ?: Regex("""([0-9]+)\s+max_tokens""", RegexOption.IGNORE_CASE).find(body)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }
}
