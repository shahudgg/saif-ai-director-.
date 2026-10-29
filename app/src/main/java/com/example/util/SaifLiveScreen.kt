package com.example.util

import android.util.Base64
import android.util.Log
import com.jarvis.assistant.vision.ScreenCaptureManager

/**
 * Holds the newest screen-share frame so that EVERY live engine can "see" the screen, not only the
 * Gemini Live WebSocket.
 *
 * The Gemini socket pushes frames to Google continuously. The provider-agnostic turn-based engine
 * cannot do that, so instead it attaches the newest frame to the LLM call through
 * `AIApiUtility.executeWithFallback(base64Images = ...)`, which already routes images to whichever
 * provider is configured (OpenAI, Anthropic, Groq, OpenRouter, Gemini, custom...).
 *
 * Frames are pushed in by [com.example.SaifHostLiveEngine] through [onFrame]; [frameForPrompt] is
 * read once per turn so a spoken question about the screen always carries a picture with it.
 */
object SaifLiveScreen {

    private const val TAG = "SaifLiveScreen"

    /** A frame older than this is treated as stale and is not sent to the model. */
    private const val FRESH_MS = 15_000L

    /** JPEG quality for an on-demand snapshot (kept modest: uploads go over the user's API plan). */
    private const val SNAPSHOT_QUALITY = 72

    @Volatile private var lastJpeg: ByteArray? = null
    @Volatile private var lastAtMs: Long = 0L

    /** True while a MediaProjection capture is actually running. */
    val isSharing: Boolean
        get() = try {
            ScreenCaptureManager.getInstance().isSharing.value
        } catch (t: Throwable) {
            false
        }

    /** True when a usable frame is buffered. */
    val hasFrame: Boolean
        get() {
            val jpeg = lastJpeg ?: return false
            if (jpeg.isEmpty()) return false
            return System.currentTimeMillis() - lastAtMs <= FRESH_MS
        }

    /** Called for every captured frame; only the newest one is kept. */
    fun onFrame(jpeg: ByteArray) {
        if (jpeg.isEmpty()) return
        lastJpeg = jpeg
        lastAtMs = System.currentTimeMillis()
    }

    /** Drops the buffered frame (screen share stopped, or the session ended). */
    fun clear() {
        lastJpeg = null
        lastAtMs = 0L
    }

    /**
     * The image list for one LLM turn: the buffered frame when it is fresh, otherwise an on-demand
     * snapshot, otherwise an empty list (no screen shared -> the turn stays text-only).
     */
    fun frameForPrompt(): List<String> {
        val jpeg = refreshIfNeeded() ?: return emptyList()
        return try {
            listOf(Base64.encodeToString(jpeg, Base64.NO_WRAP))
        } catch (t: Throwable) {
            Log.w(TAG, "Could not encode the screen frame: ${t.javaClass.simpleName}")
            emptyList()
        }
    }

    /** Returns the buffered frame, taking a fresh snapshot first when it is missing or stale. */
    private fun refreshIfNeeded(): ByteArray? {
        val buffered = lastJpeg
        val fresh = buffered != null && buffered.isNotEmpty() &&
            System.currentTimeMillis() - lastAtMs <= FRESH_MS
        if (fresh) return buffered
        if (!isSharing) return null
        return try {
            val snap = ScreenCaptureManager.getInstance().captureSnapshotJpeg(SNAPSHOT_QUALITY)
            if (snap != null && snap.isNotEmpty()) {
                onFrame(snap)
                snap
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Screen snapshot failed: ${t.javaClass.simpleName}")
            null
        }
    }
}
