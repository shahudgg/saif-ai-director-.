package com.example.stt

import java.util.Locale

/**
 * Result from evaluating an audio transcript for hallucinations, noise loops, or ghosts.
 */
data class GuardDecision(
    val isAccepted: Boolean,
    val sanitizedText: String,
    val rejectionReason: String? = null
)

/**
 * Pure-Kotlin guard that filters Whisper / Gemini audio transcription hallucinations,
 * ghost phrases on background noise ("Thank you.", "Thanks for watching", "Welcome"), repetitive loops,
 * and low-SNR acoustic artifacts.
 */
object TranscriptGuard {

    private val GHOST_PHRASES = setOf(
        "thank you",
        "thanks",
        "thank you for watching",
        "thanks for watching",
        "thank you very much",
        "you're welcome",
        "welcome",
        "you",
        "bye",
        "bye bye",
        "ok",
        "okay",
        "hmm",
        "mm",
        "uh",
        "um",
        "please subscribe",
        "like and subscribe",
        "subtitles by",
        "amara.org",
        "subtitles by the amara.org community",
        "english subtitles",
        "hindi subtitles",
        "धन्यवाद",
        "बहुत धन्यवाद",
        "शुक्रिया",
        "अलविदा",
        "स्वागत है",
        "आपका स्वागत है"
    )

    /**
     * Evaluates whether a raw transcript is genuine human speech or an acoustic artifact.
     *
     * @param rawText Transcribed text
     * @param durationMs Approximate duration of voiced speech in milliseconds
     * @param avgLogProb Whisper average logprob (null if unavailable)
     * @param noSpeechProb Whisper no_speech_prob (null if unavailable)
     * @param snrDb Measured acoustic signal-to-noise ratio in dB (null if unavailable)
     * @param voicedRatio Ratio of voiced frames during the turn (null if unavailable)
     */
    fun evaluate(
        rawText: String,
        durationMs: Long = 2000L,
        avgLogProb: Double? = null,
        noSpeechProb: Double? = null,
        snrDb: Double? = null,
        voicedRatio: Float? = null
    ): GuardDecision {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return GuardDecision(false, "", "Empty transcript")
        }

        // 1. Check Whisper confidence thresholds
        if (noSpeechProb != null && noSpeechProb > 0.60) {
            return GuardDecision(false, "", "High no_speech_prob: $noSpeechProb")
        }
        if (avgLogProb != null && avgLogProb < -1.1) {
            return GuardDecision(false, "", "Low average logprob: $avgLogProb")
        }

        // 2. Reject if audio energy is low or SNR indicates non-speech ambient sound
        if (snrDb != null && snrDb < 2.5 && durationMs < 1200L) {
            return GuardDecision(false, "", "Low SNR acoustic artifact ($snrDb dB)")
        }
        if (voicedRatio != null && voicedRatio < 0.20 && durationMs < 1500L) {
            return GuardDecision(false, "", "Low voiced ratio ($voicedRatio)")
        }

        // Clean punctuation and normalize
        val normalized = trimmed.lowercase(Locale.ROOT)
            .replace(Regex("""[.,!?;:"'—\-_]"""), "")
            .trim()

        // 3. Reject known hallucinated ghost phrases when audio is brief (< 1.6s) or isolated low energy
        if (GHOST_PHRASES.contains(normalized)) {
            val isShortOrLowEnergy = durationMs < 1600L ||
                (noSpeechProb != null && noSpeechProb > 0.25) ||
                (snrDb != null && snrDb < 6.0)

            if (isShortOrLowEnergy) {
                return GuardDecision(false, "", "Ghost phrase detected on low-energy audio: '$normalized'")
            }
        }

        // Also check if transcript is solely subtitle boilerplates
        if (normalized.contains("subtitles by") || normalized.contains("amara.org") || normalized.contains("thank you for watching")) {
            return GuardDecision(false, "", "Boilerplate subtitle hallucination: '$normalized'")
        }

        // 4. Reject n-gram repetition loops (e.g., "you you you you" or "thank you thank you thank you")
        val words = normalized.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size >= 4) {
            val distinctWords = words.toSet()
            if (distinctWords.size == 1) {
                return GuardDecision(false, "", "Repetitive word loop: '${words.first()}'")
            }
            if (words.size >= 6 && distinctWords.size <= 2) {
                return GuardDecision(false, "", "Repetitive 2-gram loop")
            }
        }

        // Clean trailing punctuation and normalize spaces
        val sanitized = trimmed.replace(Regex("""\s+"""), " ")
        return GuardDecision(true, sanitized, null)
    }
}
