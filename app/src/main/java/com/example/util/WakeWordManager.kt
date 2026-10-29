package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the customizable wake-up word ("Hey Saif" by default)
 * and controls the Gemini-style Assistant Overlay and Corner Glow.
 */
object WakeWordManager {
    private const val TAG = "WakeWordManager"
    private const val PREFS_NAME = "WakeWordPrefs"
    private const val KEY_WAKE_WORD = "wake_word_phrase"
    private const val KEY_ENABLED = "wake_word_enabled"
    private const val KEY_PRESETS = "wake_word_presets_list"
    
    const val DEFAULT_WAKE_WORD = "Hey Saif"
    val DEFAULT_PRESETS = listOf("Hey Saif", "Hey Jarvis")

    private var prefs: SharedPreferences? = null

    private val _wakeWordFlow = MutableStateFlow(DEFAULT_WAKE_WORD)
    val wakeWordFlow: StateFlow<String> = _wakeWordFlow.asStateFlow()

    private val _isWakeWordEnabledFlow = MutableStateFlow(false)
    val isWakeWordEnabledFlow: StateFlow<Boolean> = _isWakeWordEnabledFlow.asStateFlow()

    // State controlling the Gemini-style Assistant Bottom Chatbox
    private val _isAssistantOverlayActive = MutableStateFlow(false)
    val isAssistantOverlayActive: StateFlow<Boolean> = _isAssistantOverlayActive.asStateFlow()

    // Timestamp trigger for screen 4-corner glow animation (active for ~3.5s)
    private val _cornerGlowTrigger = MutableStateFlow(0L)
    val cornerGlowTrigger: StateFlow<Long> = _cornerGlowTrigger.asStateFlow()

    // Any query spoken alongside the wake word (e.g. "Hey Saif weather kaisa hai")
    private val _initialSpokenQuery = MutableStateFlow<String?>(null)
    val initialSpokenQuery: StateFlow<String?> = _initialSpokenQuery.asStateFlow()

    @Volatile
    var isAppInForeground: Boolean = false

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedWord = prefs?.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
            val savedEnabled = prefs?.getBoolean(KEY_ENABLED, false) ?: false
            _wakeWordFlow.value = savedWord
            _isWakeWordEnabledFlow.value = savedEnabled
            Log.d(TAG, "Initialized with wake word: '$savedWord', enabled: $savedEnabled")
        }
    }

    fun getWakeWord(context: Context? = null): String {
        if (prefs == null && context != null) init(context)
        return _wakeWordFlow.value
    }

    fun setWakeWord(context: Context, newWord: String) {
        init(context)
        val cleanWord = newWord.trim().ifBlank { DEFAULT_WAKE_WORD }
        _wakeWordFlow.value = cleanWord
        prefs?.edit()?.putString(KEY_WAKE_WORD, cleanWord)?.apply()
        Log.d(TAG, "Wake word updated to: '$cleanWord'")
    }

    fun isWakeWordEnabled(context: Context? = null): Boolean {
        if (prefs == null && context != null) init(context)
        return _isWakeWordEnabledFlow.value
    }

    fun setWakeWordEnabled(context: Context, enabled: Boolean) {
        init(context)
        _isWakeWordEnabledFlow.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        Log.d(TAG, "Wake word enabled state: $enabled")
    }

    fun getPresets(context: Context): List<String> {
        init(context)
        val raw = prefs?.getString(KEY_PRESETS, null)
        if (raw.isNullOrBlank()) return DEFAULT_PRESETS
        val list = raw.split("|||").map { it.trim() }.filter { it.isNotBlank() }
        return list.ifEmpty { DEFAULT_PRESETS }
    }

    fun addPreset(context: Context, newPreset: String): List<String> {
        init(context)
        val clean = newPreset.trim()
        if (clean.isBlank()) return getPresets(context)
        val current = getPresets(context).toMutableList()
        if (!current.any { it.equals(clean, ignoreCase = true) }) {
            current.add(clean)
            prefs?.edit()?.putString(KEY_PRESETS, current.joinToString("|||"))?.apply()
        }
        return current
    }

    fun deletePreset(context: Context, presetToDelete: String): List<String> {
        init(context)
        val current = getPresets(context).toMutableList()
        current.removeAll { it.equals(presetToDelete.trim(), ignoreCase = true) }
        val updated = current.ifEmpty { listOf(DEFAULT_WAKE_WORD) }
        prefs?.edit()?.putString(KEY_PRESETS, updated.joinToString("|||"))?.apply()
        return updated
    }

    /**
     * Checks if a user's spoken phrase contains the wake word.
     * Matches exact word, phonetic variations, and colloquial prefixes (e.g. "hey", "suno", "hello").
     */
    fun matchesWakeWord(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = text.lowercase()
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val target = _wakeWordFlow.value.lowercase().trim()

        // 1. Direct contains check
        if (normalized.contains(target)) return true

        // 2. Target without "hey" if user just set "Saif" or vice versa
        val targetWithoutHey = target.removePrefix("hey ").removePrefix("he ").trim()
        if (targetWithoutHey.isNotEmpty() && normalized.contains(targetWithoutHey)) {
            // Check if it's standalone or prefixed with greeting
            val words = normalized.split(" ")
            if (words.contains(targetWithoutHey) || 
                normalized.contains("hey $targetWithoutHey") || 
                normalized.contains("suno $targetWithoutHey") || 
                normalized.contains("hello $targetWithoutHey") ||
                normalized.contains("oye $targetWithoutHey")) {
                return true
            }
        }

        // 3. Default "Hey Saif" phonetic variations in Hindi/Hinglish
        if (target.contains("saif", ignoreCase = true)) {
            val saifVariations = listOf(
                "hey saif", "he saif", "hay saif", "ae saif", "a saif", "aye saif",
                "suno saif", "hello saif", "oye saif", "bhai saif", "hi saif", "saif ai"
            )
            for (v in saifVariations) {
                if (normalized.contains(v)) return true
            }
            // If the sentence starts with or contains just "saif"
            if (normalized.startsWith("saif ") || normalized.endsWith(" saif") || normalized == "saif") {
                return true
            }
        }

        return false
    }

    /**
     * Fast on-device acoustic phonetic envelope matcher for "Hey Saif" / "Saif" / custom wake word.
     * Analyzes energy distribution, zero-crossing rate (sibilants), and speech frame duration.
     * Enables instant detection without relying solely on cloud transcription.
     */
    fun matchesAcousticWakeWord(pcmData: ByteArray, sampleRate: Int = 16000): Boolean {
        if (pcmData.size < (sampleRate * 0.35 * 2).toInt() || pcmData.size > (sampleRate * 3.5 * 2).toInt()) {
            return false
        }
        val target = _wakeWordFlow.value.lowercase().trim()
        val numSamples = pcmData.size / 2
        val frameSize = sampleRate / 50 // 20ms frame
        var totalEnergy = 0.0
        var sibilantEnergy = 0.0
        var activeFrames = 0
        var hasSibilantBurst = false

        for (i in 0 until numSamples - 1 step frameSize) {
            var frameEnergy = 0.0
            var frameZC = 0
            val end = kotlin.math.min(i + frameSize, numSamples - 1)
            for (j in i until end) {
                val s1 = (pcmData[j * 2].toInt() and 0xFF) or (pcmData[j * 2 + 1].toInt() shl 8)
                val s2 = (pcmData[(j + 1) * 2].toInt() and 0xFF) or (pcmData[(j + 1) * 2 + 1].toInt() shl 8)
                frameEnergy += kotlin.math.abs(s1)
                if ((s1 > 0 && s2 < 0) || (s1 < 0 && s2 > 0)) {
                    frameZC++
                }
            }
            val avgFrameEnergy = frameEnergy / (end - i)
            val zcRate = frameZC.toDouble() / (end - i)
            if (avgFrameEnergy > 450) {
                activeFrames++
                totalEnergy += avgFrameEnergy
                if (zcRate > 0.20) {
                    sibilantEnergy += avgFrameEnergy
                    hasSibilantBurst = true
                }
            }
        }

        if (activeFrames < 12) return false
        val sibilantRatio = if (totalEnergy > 0) sibilantEnergy / totalEnergy else 0.0

        // If target contains "saif" or "jarvis", require acoustic presence of fricatives
        if (target.contains("saif") || target.contains("jarvis")) {
            return activeFrames in 12..85 && (sibilantRatio > 0.12 || hasSibilantBurst)
        }

        return activeFrames in 12..90
    }

    /**
     * Extracts any query spoken immediately after the wake word.
     * E.g. "Hey Saif Instagram kholo" -> "Instagram kholo"
     */
    fun extractQueryAfterWakeWord(text: String): String? {
        val target = _wakeWordFlow.value.lowercase().trim()
        val lower = text.lowercase()

        var query: String? = null
        if (lower.contains(target)) {
            val idx = lower.indexOf(target) + target.length
            query = text.substring(idx).trim()
        } else {
            val targetWithoutHey = target.removePrefix("hey ").removePrefix("he ").trim()
            if (targetWithoutHey.isNotEmpty() && lower.contains(targetWithoutHey)) {
                val idx = lower.indexOf(targetWithoutHey) + targetWithoutHey.length
                query = text.substring(idx).trim()
            }
        }

        return query?.removePrefix(",")?.removePrefix(".")?.removePrefix("?")?.trim()?.ifBlank { null }
    }

    /**
     * Triggers the Gemini-style Assistant bottom chatbox with 4-corner glow.
     */
    fun triggerAssistant(context: Context, spokenPrompt: String? = null) {
        Log.d(TAG, "Triggering assistant popup! Prompt: $spokenPrompt")
        _cornerGlowTrigger.value = System.currentTimeMillis()
        _initialSpokenQuery.value = spokenPrompt
        _isAssistantOverlayActive.value = true

        // If app is not in the foreground, launch the transparent AssistantOverlayActivity over the home screen
        if (!isAppInForeground) {
            try {
                val intent = android.content.Intent(context, Class.forName("com.example.AssistantOverlayActivity")).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("INITIAL_PROMPT", spokenPrompt)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AssistantOverlayActivity: ${e.message}")
            }
        }

        // Haptic feedback vibration
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    /**
     * Dismisses the assistant overlay and stops speech.
     */
    fun dismissAssistant() {
        Log.d(TAG, "Dismissing assistant overlay")
        _isAssistantOverlayActive.value = false
        _initialSpokenQuery.value = null
        try {
            com.example.utils.TTSManager.stop()
        } catch (_: Exception) {}
    }
}
