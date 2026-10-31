package com.example.studio.ai

import com.example.llm.LlmGateway
import com.example.llm.LlmRequest
import com.example.llm.LlmRole
import kotlinx.coroutines.CancellationException

data class AiCapabilities(
    val hasText: Boolean = true,
    val hasJson: Boolean = true,
    val hasVision: Boolean = false,
    val hasAudioIn: Boolean = false,
    val hasVideoIn: Boolean = false,
    val hasTts: Boolean = false,
    val hasStt: Boolean = false
)

/**
 * Clean AI client skeleton for Video Studio that proxies requests to [LlmGateway]
 * using the user's active [AiProfile]. Never falls back to hardcoded models or developer keys.
 */
class AiClient(private val profile: AiProfile) {

    suspend fun call(
        role: LlmRole,
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int? = null,
        onChunk: suspend (String) -> Unit = {},
        onAttemptStart: suspend (Int, String) -> Unit = { _, _ -> }
    ): Result<String> {
        if (!profile.isConfigured) {
            return Result.failure(IllegalStateException("AI Provider is not configured in Settings. Please add your API key in Settings -> AI Provider."))
        }

        return try {
            val req = LlmRequest(
                prompt = prompt,
                systemPrompt = systemPrompt ?: "",
                mode = "chat",
                maxTokens = maxTokens
            )
            val res = LlmGateway.callObserved(
                role = role,
                provider = profile.provider,
                apiKey = profile.apiKey,
                model = profile.activeModel,
                request = req,
                onChunk = onChunk,
                onAttemptStart = onAttemptStart
            )
            if (res.isSuccess) {
                Result.success(res.getOrThrow().text)
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Unknown LLM error"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Vision call used for asset verification. Returns failure rather than guessing when unsupported. */
    suspend fun callWithImages(role: LlmRole, prompt: String, vararg imagesBase64: String): Result<String> {
        if (!profile.isConfigured) return Result.failure(IllegalStateException("AI Provider is not configured"))
        return try {
            val req = LlmRequest(prompt = prompt, systemPrompt = "Reply only with the requested JSON.", mode = "chat",
                imagesBase64 = imagesBase64.toList(), jsonMode = false)
            val res = LlmGateway.call(role, profile.provider, profile.apiKey, profile.activeModel, req)
            if (res.isSuccess) Result.success(res.getOrThrow().text) else Result.failure(res.exceptionOrNull() ?: Exception("Vision call failed"))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
}
