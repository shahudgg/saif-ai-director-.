package com.example

import android.content.Context
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import com.example.util.SaifLiveScreen
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.SaifAutomation
import com.jarvis.assistant.vision.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * SAIF AI's provider-agnostic live conversation engine.
 *
 * Why this exists: the automation module's own live voice speaks the **Gemini Live WebSocket**, so
 * with an OpenAI / Anthropic / Groq / OpenRouter / custom key the live feature simply had no engine.
 * The app already contains a complete turn-based live stack that is *not* tied to any provider:
 *
 *   • speech-to-text  -> `SttRouter` picks Groq Whisper, OpenAI Whisper or Gemini from the keys in
 *                        Settings and falls back across them, so it works with whatever is configured;
 *   • the reply       -> `AIApiUtility.executeWithFallback`, the same gateway the chat uses;
 *   • the voice       -> `TTSManager`, fully on-device (no API needed);
 *   • screen share    -> `ScreenCaptureManager` + `SaifLiveScreen`, attached as `base64Images` to the
 *                        next turn instead of being streamed to a socket.
 *
 * This object wires that stack into `SaifAutomation`'s live flows, so one orb, one caption bar and
 * one set of buttons behave identically on EVERY API provider. It never opens an automation screen
 * and it never assumes a WebSocket exists.
 *
 * Registered by [SaifApplication] as `SaifAutomation.hostLiveEngine`.
 */
object SaifHostLiveEngine : SaifAutomation.HostLiveEngine {

    private const val TAG = "SaifHostLiveEngine"

    private var scope: CoroutineScope? = null
    private var engineJob: Job? = null

    @Volatile private var sink: SaifAutomation.HostLiveSink? = null

    @Volatile private var started = false

    /** Set once the session really turned active, so the initial `false` emission is ignored. */
    @Volatile private var sawActive = false

    val isRunning: Boolean get() = started

    // ---------------------------------------------------------------- start / stop

    override fun start(
        context: Context,
        screenShare: Boolean,
        sink: SaifAutomation.HostLiveSink
    ): Boolean {
        val appContext = context.applicationContext

        // Without the microphone there is no turn to capture, so fail loudly instead of showing an
        // orb that never listens.
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            started = false
            sink.errorText("Live baatcheet ke liye mic permission chahiye. Permission dekar dobara koshish karein.")
            return false
        }

        // A previous run may still be collecting; never stack two sets of collectors.
        stopCollecting()
        this.sink = sink
        sawActive = false

        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = sc

        sink.state(ConversationState.IDLE)
        sink.status("CONNECTING")
        sink.level(0f)
        sink.userText("")
        sink.aiText("")
        sink.errorText(null)
        sink.screenSharing(false)
        SaifLiveScreen.clear()

        // The session may already be running (started from the in-app orb); otherwise start it.
        try {
            if (LiveVoiceManager.isLiveModeActive.value) {
                LiveVoiceManager.engine?.let { publishEngine(it, sc, sink) }
            } else {
                LiveVoiceManager.startLiveVoice(appContext)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not start the live voice session", t)
            started = false
            stopCollecting()
            sink.errorText("Live session start nahi ho payi: ${t.javaClass.simpleName}")
            return false
        }

        // Forward the session's state into the automation flows for as long as this run lives.
        sc.launch {
            LiveVoiceManager.engineFlow.collect { engine ->
                if (engine == null) return@collect
                publishEngine(engine, sc, sink)
            }
        }
        sc.launch {
            LiveVoiceManager.isLiveModeActive.collect { active ->
                if (active) {
                    sawActive = true
                    sink.running(true)
                } else if (sawActive) {
                    // Closed from the notification / overlay / in-app button.
                    sawActive = false
                    started = false
                    sink.running(false)
                    sink.state(ConversationState.IDLE)
                    sink.level(0f)
                    sink.status("READY")
                }
            }
        }

        started = true
        sink.running(true)
        if (screenShare) {
            // The host owns the MediaProjection permission dialog, so it asks right after this.
            sink.status("SCREEN SHARE KE LIYE PERMISSION CHAHIYE")
        }
        return true
    }

    override fun stop(context: Context) {
        started = false
        sawActive = false
        stopScreenShare()
        try {
            LiveVoiceManager.stopLiveVoice(context.applicationContext)
        } catch (t: Throwable) {
            Log.w(TAG, "stopLiveVoice failed: ${t.javaClass.simpleName}")
        }
        stopCollecting()
        val s = sink
        sink = null
        s?.running(false)
        s?.state(ConversationState.IDLE)
        s?.level(0f)
        s?.status("READY")
        s?.screenSharing(false)
        s?.errorText(null)
    }

    private fun stopCollecting() {
        engineJob?.cancel()
        engineJob = null
        scope?.cancel()
        scope = null
    }

    // ---------------------------------------------------------------- state forwarding

    private fun publishEngine(
        engine: LiveVoiceSessionManager,
        parent: CoroutineScope,
        sink: SaifAutomation.HostLiveSink
    ) {
        engineJob?.cancel()
        engineJob = parent.launch {
            launch {
                engine.sessionState.collect { state ->
                    sink.state(orbStateOf(state))
                    sink.status(statusOf(state, engine.isMicMuted))
                }
            }
            launch { engine.amplitude.collect { sink.level(it) } }
            launch {
                engine.userCaption.collect { text ->
                    if (text.isNotBlank()) sink.userText(text)
                }
            }
            launch {
                engine.aiCaption.collect { text ->
                    if (text.isNotBlank()) sink.aiText(text)
                }
            }
            launch { engine.turnError.collect { sink.errorText(it) } }
        }
    }

    private fun currentState(): VoiceSessionState =
        LiveVoiceManager.engine?.sessionState?.value ?: VoiceSessionState.IDLE

    /** The turn-based session's state, expressed in the orb states the automation module already knows. */
    private fun orbStateOf(state: VoiceSessionState): ConversationState = when (state) {
        VoiceSessionState.IDLE -> ConversationState.IDLE
        VoiceSessionState.LISTENING -> ConversationState.LISTENING
        VoiceSessionState.PROCESSING -> ConversationState.THINKING
        VoiceSessionState.SPEAKING -> ConversationState.SPEAKING
    }

    private fun statusOf(state: VoiceSessionState, muted: Boolean): String {
        if (muted) return "MIC MUTED"
        return when (state) {
            VoiceSessionState.IDLE -> "READY"
            VoiceSessionState.LISTENING -> if (SaifLiveScreen.isSharing) "LISTENING + SCREEN" else "LISTENING"
            VoiceSessionState.PROCESSING -> "THINKING"
            VoiceSessionState.SPEAKING -> "SPEAKING"
        }
    }

    // ---------------------------------------------------------------- controls

    override fun setMicMuted(muted: Boolean) {
        val engine = LiveVoiceManager.engine
        if (engine == null) {
            Log.w(TAG, "setMicMuted ignored: the live session is not ready yet")
            return
        }
        engine.isMicMuted = muted
        sink?.status(statusOf(engine.sessionState.value, muted))
    }

    /** Barge-in: stop the current TTS sentence and go back to listening. */
    override fun interrupt() {
        try {
            LiveVoiceManager.engine?.stopSpeaking(causedByUserInterruption = true)
        } catch (t: Throwable) {
            Log.w(TAG, "interrupt failed: ${t.javaClass.simpleName}")
        }
        sink?.state(ConversationState.LISTENING)
        sink?.status("LISTENING")
    }

    /** Typed text joins the session through the very same turn pipeline as speech. */
    override fun sendText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = LiveVoiceManager.engine
        if (engine == null) {
            sink?.errorText("Live session abhi ready nahi hai, ek second baad dobara bhejein.")
            return
        }
        sink?.userText(clean)
        sink?.state(ConversationState.THINKING)
        sink?.status("THINKING")
        engine.submitText(clean)
    }

    // ---------------------------------------------------------------- screen share

    /**
     * Shares the screen with a projection the host already obtained. Unlike the Gemini path, the
     * frames are not streamed to a socket - the newest one is buffered in [SaifLiveScreen] and
     * attached as an image to the next turn, which any vision-capable provider can read.
     */
    override fun startScreenShare(projection: MediaProjection, metrics: DisplayMetrics): Boolean {
        val manager = ScreenCaptureManager.getInstance()
        if (manager.isSharing.value) {
            sink?.screenSharing(true)
            sink?.status(statusOf(currentState(), LiveVoiceManager.engine?.isMicMuted == true))
            return true
        }
        manager.onStoppedListener = {
            SaifLiveScreen.clear()
            sink?.screenSharing(false)
        }
        val ok = try {
            manager.startCapture(projection, metrics) { jpeg -> SaifLiveScreen.onFrame(jpeg) }
        } catch (t: Throwable) {
            Log.e(TAG, "Screen capture could not start", t)
            false
        }
        if (ok) {
            sink?.screenSharing(true)
            sink?.status(statusOf(currentState(), LiveVoiceManager.engine?.isMicMuted == true))
        } else {
            try { projection.stop() } catch (_: Throwable) {}
            sink?.screenSharing(false)
            sink?.errorText("Screen share start nahi ho paya. Permission dobara dein.")
        }
        return ok
    }

    override fun stopScreenShare() {
        try {
            val manager = ScreenCaptureManager.getInstance()
            if (manager.isSharing.value) manager.stopCapture()
        } catch (t: Throwable) {
            Log.w(TAG, "stopCapture failed: ${t.javaClass.simpleName}")
        }
        SaifLiveScreen.clear()
        sink?.screenSharing(false)
    }
}
