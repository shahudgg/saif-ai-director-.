package com.example.studio.director

import com.example.llm.ProviderProfile

enum class Capability {
    CHAT, STRUCTURED_JSON, WEB_SEARCH, IMAGE_UNDERSTANDING, VIDEO_UNDERSTANDING,
    IMAGE_GENERATION, AUDIO_TRANSCRIPTION, SPEECH_SYNTHESIS, FORCED_ALIGNMENT
}

/** A configured provider plus the capabilities it *really* declares. Nothing is assumed. */
data class ProviderEntry(
    val id: String,
    val displayName: String,
    val hasCredentials: Boolean,
    val model: String,
    val capabilities: Set<Capability>,
    val maxInlineImageBytes: Long = 4L * 1024 * 1024,
    val maxAudioBytes: Long = 24L * 1024 * 1024,
    val isLocalDevice: Boolean = false,
    /** Round 11: the concrete key this entry should use (empty = take the profile default). Enables per-key rotation. */
    val apiKey: String = ""
) {
    fun supports(c: Capability) = hasCredentials && c in capabilities || (isLocalDevice && c in capabilities)
}

sealed class RouteResult {
    data class Routed(val provider: ProviderEntry) : RouteResult()
    data class Unavailable(val capability: Capability, val message: String) : RouteResult()
}

/** Failure classification used by retry/fallback logic. */
enum class FailureKind { TRANSIENT, QUOTA, AUTH, INVALID_RESPONSE, UNSUPPORTED, UNKNOWN }

object ProviderRegistry {
    val VISION_PROVIDERS = setOf("gemini", "openai", "anthropic", "openrouter")
    val WHISPER_PROVIDERS = setOf("groq", "openai")
    /** Providers with a real image-generation endpoint (see llm/ImageGen). */
    val IMAGE_GEN_PROVIDERS = setOf("gemini", "openai", "openrouter")

    /** Builds the capability set from the app's provider profile. Vision/STT come from the declared dialect only. */
    fun fromProfile(profile: ProviderProfile, hasKey: Boolean, model: String): ProviderEntry {
        val caps = mutableSetOf(Capability.CHAT)
        if (profile.dialect.supportsJsonMode) caps += Capability.STRUCTURED_JSON
        val id = profile.id.lowercase()
        // Dialect flags default to "true" for most providers, so only providers known to expose multimodal input are trusted.
        if (profile.dialect.supportsVision && id in VISION_PROVIDERS) caps += Capability.IMAGE_UNDERSTANDING
        // Only providers with a Whisper-style /audio/transcriptions endpoint are claimed, because that is what the Transcriber implements.
        if (id in WHISPER_PROVIDERS) caps += Capability.AUDIO_TRANSCRIPTION
        // Round 10: image generation is claimed only for providers ImageGen really implements.
        if (id in IMAGE_GEN_PROVIDERS || id == "custom") caps += Capability.IMAGE_GENERATION
        return ProviderEntry(profile.id, profile.displayName, hasKey, model, caps)
    }

    /** Android's on-device TextToSpeech: real synthesizeToFile, no credentials required. */
    fun deviceSpeech(): ProviderEntry = ProviderEntry(
        id = "device-tts", displayName = "On-device voice", hasCredentials = false, model = "android.speech.tts",
        capabilities = setOf(Capability.SPEECH_SYNTHESIS), isLocalDevice = true
    )

    fun webSearch(configured: Boolean, name: String): ProviderEntry = ProviderEntry(
        id = "search-$name", displayName = name, hasCredentials = configured, model = "", capabilities = setOf(Capability.WEB_SEARCH)
    )

    fun route(providers: List<ProviderEntry>, needed: Capability, preferId: String? = null): RouteResult {
        val ok = providers.filter { it.supports(needed) }
        if (ok.isEmpty()) return RouteResult.Unavailable(needed, missingMessage(needed))
        val preferred = ok.firstOrNull { it.id.equals(preferId, true) } ?: ok.first()
        return RouteResult.Routed(preferred)
    }

    /** Ordered fallback chain: preferred first, then every other capable provider. */
    fun chain(providers: List<ProviderEntry>, needed: Capability, preferId: String? = null): List<ProviderEntry> {
        val ok = providers.filter { it.supports(needed) }
        return ok.sortedByDescending { it.id.equals(preferId, true) }
    }

    fun missingMessage(c: Capability): String = when (c) {
        Capability.CHAT -> "No chat model is configured. Add an API key in Settings → AI Provider."
        Capability.WEB_SEARCH -> "No web-search provider is configured, so live research is unavailable. The script will be written from model knowledge and marked unverified."
        Capability.IMAGE_UNDERSTANDING -> "The configured provider cannot analyze images, so asset relevance can only be checked by metadata, not visually."
        Capability.VIDEO_UNDERSTANDING -> "The configured provider cannot ingest video; frames will be sampled and analyzed as images instead."
        Capability.IMAGE_GENERATION -> "No image-generation provider is configured; procedural graphics will be used instead."
        Capability.AUDIO_TRANSCRIPTION -> "No speech-to-text provider (Groq, OpenAI or Gemini key) is configured, so audio cannot be transcribed. I will not invent a transcript."
        Capability.SPEECH_SYNTHESIS -> "No speech-synthesis engine is available on this device."
        Capability.FORCED_ALIGNMENT -> "No forced-alignment provider; sentence-level timing from measured audio durations will be used."
        Capability.STRUCTURED_JSON -> "The provider has no JSON mode; responses are parsed and validated defensively."
    }

    fun classifyFailure(message: String?): FailureKind {
        val m = (message ?: "").lowercase()
        return when {
            "429" in m || "quota" in m || "rate limit" in m || "resource_exhausted" in m -> FailureKind.QUOTA
            "401" in m || "403" in m || "api key" in m || "unauthorized" in m -> FailureKind.AUTH
            "timeout" in m || "timed out" in m || "unable to resolve" in m || "connection" in m || "503" in m || "502" in m || "500" in m -> FailureKind.TRANSIENT
            "400" in m && ("unsupported" in m || "unknown parameter" in m) -> FailureKind.UNSUPPORTED
            "json" in m || "parse" in m -> FailureKind.INVALID_RESPONSE
            else -> FailureKind.UNKNOWN
        }
    }

    fun isRetryable(k: FailureKind) = k == FailureKind.TRANSIENT || k == FailureKind.INVALID_RESPONSE

    /**
     * Runs [call] against the chain with bounded retries for transient failures and moves to the next
     * provider on quota/auth/unsupported errors. [sleep] is injectable for tests.
     */
    suspend fun <T> runWithFallback(
        chain: List<ProviderEntry>,
        maxRetriesPerProvider: Int = 2,
        sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        call: suspend (ProviderEntry) -> Result<T>
    ): Result<T> {
        if (chain.isEmpty()) return Result.failure(IllegalStateException("No compatible provider configured"))
        var last: Throwable? = null
        for (p in chain) {
            var attempt = 0
            while (true) {
                val r = call(p)
                if (r.isSuccess) return r
                val err = r.exceptionOrNull()
                last = err
                val kind = classifyFailure(err?.message)
                if (isRetryable(kind) && attempt < maxRetriesPerProvider) {
                    attempt++
                    sleep(400L * attempt * attempt)
                    continue
                }
                break
            }
        }
        return Result.failure(last ?: IllegalStateException("All providers failed"))
    }
}
