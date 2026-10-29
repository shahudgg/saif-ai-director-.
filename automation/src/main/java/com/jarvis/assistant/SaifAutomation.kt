package com.jarvis.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.background.JarvisLiveError
import com.jarvis.assistant.ui.home.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** API settings of the host app (SAIF AI Settings panel) as seen by SAIF AI AUTOMATION. */
data class SaifApiConfig(
    val provider: String,
    val apiKey: String,
    val model: String
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()

    /** The voice engine speaks the Gemini Live protocol, so it needs a Gemini key. */
    val isGemini: Boolean get() = provider.isBlank() || provider.contains("gemini", ignoreCase = true)
}

/**
 * Link between the host app (SAIF AI) and the SAIF AI AUTOMATION module.
 * The host registers [link] once (Application.onCreate). After that the automation engine always
 * reads the key / provider / model from the host's Settings panel - no separate API screen.
 */
object SaifAutomation {

    private const val PREFS = "saif_automation_link"
    private const val KEY_HOST_LIGHT = "host_light_mode"

    @Volatile private var apiResolver: (() -> SaifApiConfig)? = null
    @Volatile private var settingsOpener: (() -> Unit)? = null

    fun link(apiResolver: () -> SaifApiConfig, openHostSettings: () -> Unit) {
        this.apiResolver = apiResolver
        this.settingsOpener = openHostSettings
    }

    fun currentApi(): SaifApiConfig {
        val cfg = try { apiResolver?.invoke() } catch (_: Throwable) { null }
        return cfg ?: SaifApiConfig(provider = "", apiKey = "", model = "")
    }

    /** True while the host has linked its settings (always the case inside SAIF AI). */
    val isLinked: Boolean get() = apiResolver != null

    var hostLightMode: Boolean
        get() = prefs().getBoolean(KEY_HOST_LIGHT, false)
        set(value) { prefs().edit().putBoolean(KEY_HOST_LIGHT, value).apply() }

    private fun prefs() = AutomationContext.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Opens the automation module's own screens. The red "+" button that used to call this is gone
     * (Round 9), but the entry point stays for the automation module's internal navigation. Returns
     * false - and tells the user why - when the host Settings panel has no API key yet.
     */
    fun launch(context: Context, hostLightMode: Boolean): Boolean {
        AutomationContext.set(context)
        this.hostLightMode = hostLightMode
        // Create the engine first so lifecycle callbacks are registered before the first automation screen opens.
        JarvisApp.instance.ensureSpeechModels()
        val cfg = currentApi()
        if (!cfg.hasKey) {
            Toast.makeText(
                context,
                "SAIF AI Settings (⚙) me pehle API key daalo, phir red + dabao.",
                Toast.LENGTH_LONG
            ).show()
            openHostSettings()
            return false
        }
        val intent = Intent(context, MainActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Closes every automation screen and asks the host to show its Settings panel. */
    fun openHostSettings() {
        try { JarvisApp.instance.finishAutomationScreens() } catch (_: Throwable) {}
        try { settingsOpener?.invoke() } catch (_: Throwable) {}
    }

    // ---- Round 9: the HOST owns the voice, the memory and the language policy -----------------------------------
    // SAIF AI's Application class sets these. The automation module asks the host instead of keeping a second copy
    // of each setting - that is what makes the merged feature behave like one app.

    /** Voice name to speak with, taken from SAIF AI Settings (default: SAIF ADVANCE VOICE). */
    var hostVoiceResolver: (() -> String)? = null

    /**
     * Memory recall for a feature ("live", "chat", "video", ...). The host answers from the app-wide
     * SAIF MEMORY CORE, so the live engine remembers what was learned anywhere in the app.
     */
    var hostMemoryResolver: ((feature: String) -> String)? = null

    /** AUTO LANGUAGE (moved into the host's Settings): answer in the language the user uses. */
    var hostAutoLanguageResolver: (() -> Boolean)? = null

    /** The voice the host wants, or null when the host did not register one. */
    fun hostVoiceName(): String? = try { hostVoiceResolver?.invoke() } catch (_: Throwable) { null }

    /** What the host remembers about [feature]; empty when the host did not register a memory. */
    fun hostMemory(feature: String): String =
        try { hostMemoryResolver?.invoke(feature)?.trim().orEmpty() } catch (_: Throwable) { "" }

    /** Whether the host wants automatic language detection. */
    fun hostAutoLanguage(): Boolean = try { hostAutoLanguageResolver?.invoke() ?: true } catch (_: Throwable) { true }

    /** Host saved new API settings: drop old live errors and reconnect the live session. */
    fun onHostApiChanged() {
        try {
            JarvisConversationService.clearLiveError()
            JarvisConversationService.restartSession()
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------
    // Round 9: the automation engine now runs INSIDE SAIF AI's own live-conversation
    // feature (the purple mic), not behind a separate red "+" button. The host picks a
    // mode and gets the engine's state through the flows below; the host draws the orb.
    // ---------------------------------------------------------------------------

    /** How the live conversation should run. */
    enum class LiveMode(val label: String, val description: String) {
        /** Talk only: microphone in, SAIF AI voice out. */
        NORMAL("Normal voice", "Sirf baat karein — mic se boliye, SAIF AI jawab dega"),
        /** Talk + look at the screen: the engine can see what is on screen and help with it. */
        SCREEN_SHARE("Share screen", "SAIF AI aapki screen dekh kar madad karega")
    }

    /** The mode the running session was started in. */
    val liveMode = MutableStateFlow(LiveMode.NORMAL)

    /** Which engine is serving the live conversation right now. */
    enum class EngineKind { NONE, GEMINI_LIVE, HOST_TURN_BASED }

    private val _activeEngine = MutableStateFlow(EngineKind.NONE)
    /**
     * The engine behind the live session. `GEMINI_LIVE` is the full-duplex Gemini WebSocket;
     * `HOST_TURN_BASED` is SAIF AI's own turn-based stack (VAD -> STT -> the provider-agnostic LLM
     * gateway -> on-device TTS), which is what makes live baatcheet work on EVERY provider.
     */
    val activeEngine: StateFlow<EngineKind> = _activeEngine.asStateFlow()

    /** True when the host's turn-based engine is running instead of the Gemini Live socket. */
    val isHostEngine: Boolean get() = _activeEngine.value == EngineKind.HOST_TURN_BASED

    // ---- SAIF AI's own live state -------------------------------------------------------
    // These flows belong to this object, not to an engine. Whichever engine is active feeds them,
    // so the orb, the captions and the buttons behave exactly the same on every API provider.

    /** True while an engine owns the live conversation. */
    private val _isLiveRunning = MutableStateFlow(false)
    val isLiveRunning: StateFlow<Boolean> = _isLiveRunning.asStateFlow()

    /** Orb state for the host's own orb view: idle | listening | thinking | speaking. */
    private val _orbState = MutableStateFlow(com.jarvis.assistant.data.model.ConversationState.IDLE)
    val orbState: StateFlow<com.jarvis.assistant.data.model.ConversationState> = _orbState.asStateFlow()

    /** Connection text for the host UI (READY / CONNECTING / LIVE / error). */
    private val _connectionStatus = MutableStateFlow("READY")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    /** Mic energy 0..1, so the orb reacts to the voice. */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    /** Bass/mid/high split of the mic, used by the orb's audio-reactive mode. */
    private val _audioFrequency = MutableStateFlow(com.jarvis.assistant.audio.AudioFrequencyData())
    val audioFrequency: StateFlow<com.jarvis.assistant.audio.AudioFrequencyData> = _audioFrequency.asStateFlow()

    /** What the user just said, and what SAIF AI is answering, for the host's captions. */
    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()
    private val _assistantTranscript = MutableStateFlow("")
    val assistantTranscript: StateFlow<String> = _assistantTranscript.asStateFlow()

    /** True while the screen is being shared with the engine. */
    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    /** A live error the host should show (missing key, socket failure, no STT route...). */
    private val _liveError = MutableStateFlow<JarvisLiveError?>(null)
    val liveError: StateFlow<JarvisLiveError?> = _liveError.asStateFlow()

    // ---- host-provided engine -----------------------------------------------------------

    /** Where a host engine pushes its state; this object copies it into the flows above. */
    interface HostLiveSink {
        fun running(value: Boolean)
        /** The orb state, so the host orb animates exactly like it does for the Gemini Live engine. */
        fun state(value: com.jarvis.assistant.data.model.ConversationState)
        fun level(value: Float)
        fun userText(value: String)
        fun aiText(value: String)
        fun status(value: String)
        fun errorText(value: String?)
        fun screenSharing(value: Boolean)
    }

    /**
     * A live conversation engine the HOST provides for providers the Gemini Live WebSocket cannot
     * serve (OpenAI, Anthropic, Groq, OpenRouter, custom endpoints...). Registered by SAIF AI's
     * Application class. The automation module never depends on the app module, so it only sees
     * this interface.
     */
    interface HostLiveEngine {
        fun start(context: Context, screenShare: Boolean, sink: HostLiveSink): Boolean
        fun stop(context: Context)
        fun setMicMuted(muted: Boolean)
        fun interrupt()
        fun sendText(text: String)
        fun startScreenShare(
            projection: android.media.projection.MediaProjection,
            metrics: android.util.DisplayMetrics
        ): Boolean

        fun stopScreenShare()
    }

    var hostLiveEngine: HostLiveEngine? = null

    private val hostSink = object : HostLiveSink {
        override fun running(value: Boolean) { _isLiveRunning.value = value }
        override fun state(value: com.jarvis.assistant.data.model.ConversationState) { _orbState.value = value }
        override fun level(value: Float) {
            _audioLevel.value = value
            // A turn-based host engine only reports mic energy, but the orb also animates bass/high
            // rings, so derive a proportional split. (The Gemini Live engine pushes the real FFT
            // through the Gemini bridge, and that wins while the socket engine is the active one.)
            val v = value.coerceIn(0f, 1f)
            _audioFrequency.value = com.jarvis.assistant.audio.AudioFrequencyData(
                amplitude = v,
                bass = (v * 0.85f).coerceIn(0f, 1f),
                mid = (v * 0.6f).coerceIn(0f, 1f),
                high = (v * 0.35f).coerceIn(0f, 1f)
            )
        }
        override fun userText(value: String) { _userTranscript.value = value }
        override fun aiText(value: String) { _assistantTranscript.value = value }
        override fun status(value: String) { _connectionStatus.value = value }
        override fun errorText(value: String?) {
            _liveError.value = value?.takeIf { it.isNotBlank() }?.let {
                JarvisLiveError("Live baatcheet", it, "HOST_ENGINE")
            }
        }

        override fun screenSharing(value: Boolean) { _isScreenSharing.value = value }
    }

    // ---- Gemini Live screen-share hand-off ---------------------------------------------
    // The Gemini socket refuses a projection until its WebSocket is actually LIVE, and it stops the
    // projection it rejects - so when the user picks "Screen share" before the session is up, the
    // projection is kept here and handed over the moment the socket reports LIVE.
    @Volatile private var pendingProjection: android.media.projection.MediaProjection? = null
    @Volatile private var pendingMetrics: android.util.DisplayMetrics? = null

    /** True when a screen share is waiting for the Gemini Live socket to become ready. */
    val isScreenSharePending: Boolean get() = pendingProjection != null

    private fun geminiSocketReady(): Boolean = try {
        JarvisConversationService.isSessionOn.value &&
            JarvisConversationService.connectionStatus.value.equals("LIVE", ignoreCase = true)
    } catch (_: Throwable) {
        false
    }

    /** Hands a queued projection to the Gemini Live engine once its socket is ready. */
    private fun flushPendingScreenShare() {
        val projection = pendingProjection ?: return
        val metrics = pendingMetrics
        if (metrics == null) {
            pendingProjection = null
            return
        }
        if (!geminiSocketReady()) return
        pendingProjection = null
        pendingMetrics = null
        val ok = try {
            JarvisConversationService.startScreenCapture(projection, metrics)
        } catch (t: Throwable) {
            false
        }
        _isScreenSharing.value = ok
        if (!ok) {
            _liveError.value = JarvisLiveError(
                "Screen share",
                "Screen share start nahi ho paya. Live panel me dobara Screen share dabayein.",
                "SCREEN_CAPTURE_FAILED"
            )
        }
    }

    private fun dropPendingScreenShare() {
        val projection = pendingProjection
        pendingProjection = null
        pendingMetrics = null
        if (projection != null) {
            try { projection.stop() } catch (_: Throwable) {}
        }
    }

    // ---- Gemini Live bridge -------------------------------------------------------------

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    @Volatile private var geminiBridged = false

    /**
     * Copies the Gemini Live engine's own flows into SAIF AI's flows, but only while that engine is
     * the active one - so the two engines can never fight over the orb.
     */
    private fun ensureGeminiBridge() {
        if (geminiBridged) return
        geminiBridged = true
        scope.launch {
            JarvisConversationService.isSessionOn.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _isLiveRunning.value = it
            }
        }
        scope.launch {
            JarvisConversationService.conversationState.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _orbState.value = it
            }
        }
        scope.launch {
            JarvisConversationService.connectionStatus.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) {
                    _connectionStatus.value = it
                    flushPendingScreenShare()
                }
            }
        }
        scope.launch {
            JarvisConversationService.audioLevel.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _audioLevel.value = it
            }
        }
        scope.launch {
            JarvisConversationService.audioFrequency.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _audioFrequency.value = it
            }
        }
        scope.launch {
            JarvisConversationService.liveUserTranscript.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _userTranscript.value = it
            }
        }
        scope.launch {
            JarvisConversationService.liveAssistantResponse.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _assistantTranscript.value = it
            }
        }
        scope.launch {
            JarvisConversationService.isScreenSharing.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _isScreenSharing.value = it
            }
        }
        scope.launch {
            JarvisConversationService.liveError.collect {
                if (_activeEngine.value == EngineKind.GEMINI_LIVE) _liveError.value = it
            }
        }
    }

    private fun resetFlowsForStart() {
        _isLiveRunning.value = false
        _orbState.value = com.jarvis.assistant.data.model.ConversationState.IDLE
        _connectionStatus.value = "CONNECTING"
        _audioLevel.value = 0f
        _audioFrequency.value = com.jarvis.assistant.audio.AudioFrequencyData()
        _userTranscript.value = ""
        _assistantTranscript.value = ""
        _isScreenSharing.value = false
        _liveError.value = null
    }

    /**
     * Starts the live conversation WITHOUT opening any automation screen: the host draws its own orb.
     *
     * Provider routing - this is what makes the feature work on every API:
     *  • a Gemini key            -> the full-duplex Gemini Live WebSocket (`JarvisConversationService`)
     *  • any other provider      -> SAIF AI's own turn-based engine (`hostLiveEngine`), which uses
     *    `SttRouter` for speech-to-text (Groq / OpenAI Whisper / Gemini, with fallbacks), the app's
     *    provider-agnostic LLM gateway for the reply, and on-device TTS for the voice.
     *
     * Returns false (with a toast) when the host Settings panel has no API key yet.
     */
    fun startLive(context: Context, mode: LiveMode, hostLightMode: Boolean): Boolean {
        AutomationContext.set(context)
        this.hostLightMode = hostLightMode
        liveMode.value = mode
        _liveError.value = null
        val cfg = currentApi()
        if (!cfg.hasKey) {
            Toast.makeText(
                context,
                "SAIF AI Settings (\u2699) me pehle API key daalo, phir live baatcheet shuru karo.",
                Toast.LENGTH_LONG
            ).show()
            openHostSettings()
            return false
        }
        resetFlowsForStart()

        if (cfg.isGemini) {
            _activeEngine.value = EngineKind.GEMINI_LIVE
            ensureGeminiBridge()
            JarvisApp.instance.ensureSpeechModels()
            val intent = Intent(context, JarvisConversationService::class.java)
                .setAction(JarvisConversationService.ACTION_START_CONVERSATION)
            return try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Throwable) {
                _activeEngine.value = EngineKind.NONE
                Toast.makeText(
                    context,
                    "Live session start nahi ho payi: ${e.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }

        val engine = hostLiveEngine
        if (engine == null) {
            _activeEngine.value = EngineKind.NONE
            Toast.makeText(
                context,
                "Is provider ke liye live engine register nahi hua. App dobara khol kar try karein.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        _activeEngine.value = EngineKind.HOST_TURN_BASED
        _connectionStatus.value = "CONNECTING"
        val ok = try {
            engine.start(context, mode == LiveMode.SCREEN_SHARE, hostSink)
        } catch (t: Throwable) {
            hostSink.errorText("Live engine start nahi hua: ${t.javaClass.simpleName}")
            false
        }
        if (!ok) {
            _activeEngine.value = EngineKind.NONE
            Toast.makeText(
                context,
                "Live baatcheet shuru nahi ho payi. Mic permission aur Settings me API key check karein.",
                Toast.LENGTH_LONG
            ).show()
        }
        return ok
    }

    /** Stops whichever engine is running, plus any screen capture. */
    fun stopLive(context: Context) {
        AutomationContext.set(context)
        dropPendingScreenShare()
        if (_activeEngine.value == EngineKind.HOST_TURN_BASED) {
            try { hostLiveEngine?.stopScreenShare() } catch (_: Throwable) {}
            try { hostLiveEngine?.stop(context) } catch (_: Throwable) {}
        } else {
            try { stopScreenShare() } catch (_: Throwable) {}
            try {
                val intent = Intent(context, JarvisConversationService::class.java)
                    .setAction(JarvisConversationService.ACTION_STOP_ALL)
                context.startService(intent)
            } catch (_: Throwable) {}
        }
        _activeEngine.value = EngineKind.NONE
        _isLiveRunning.value = false
        _orbState.value = com.jarvis.assistant.data.model.ConversationState.IDLE
        _isScreenSharing.value = false
        _audioLevel.value = 0f
        _connectionStatus.value = "READY"
    }

    /** Mutes / unmutes the microphone of whichever engine is running. */
    fun setMicMuted(muted: Boolean) {
        if (isHostEngine) {
            try { hostLiveEngine?.setMicMuted(muted) } catch (_: Throwable) {}
        } else {
            try { JarvisConversationService.setMicMuted(muted) } catch (_: Throwable) {}
        }
    }

    /** Interrupts whatever SAIF AI is saying (barge-in). */
    fun interrupt() {
        if (isHostEngine) {
            try { hostLiveEngine?.interrupt() } catch (_: Throwable) {}
        } else {
            try { JarvisConversationService.interruptPlayback() } catch (_: Throwable) {}
        }
    }

    /** Sends typed text into the running live session. */
    fun sendText(text: String) {
        if (isHostEngine) {
            try { hostLiveEngine?.sendText(text) } catch (_: Throwable) {}
        } else {
            try { JarvisConversationService.sendTextMessage(text) } catch (_: Throwable) {}
        }
    }

    /**
     * Begins screen sharing with a MediaProjection the HOST already obtained (the host asks for the
     * permission, because the projection dialog must come from a visible activity). Works on both
     * engines: Gemini Live streams frames over the socket, the turn-based engine attaches the newest
     * frame to the next prompt.
     */
    fun startScreenShare(
        projection: android.media.projection.MediaProjection,
        metrics: android.util.DisplayMetrics
    ): Boolean {
        if (!isHostEngine && !geminiSocketReady()) {
            // The Gemini Live socket is still connecting: queue it instead of losing the projection.
            dropPendingScreenShare()
            pendingProjection = projection
            pendingMetrics = metrics
            scope.launch {
                kotlinx.coroutines.delay(20_000L)
                if (pendingProjection === projection && !geminiSocketReady()) {
                    dropPendingScreenShare()
                    _liveError.value = JarvisLiveError(
                        "Screen share",
                        "Live socket ready nahi hua, isliye screen share start nahi ho paya.",
                        "SCREEN_CAPTURE_TIMEOUT"
                    )
                }
            }
            return false
        }
        val ok = if (isHostEngine) {
            try { hostLiveEngine?.startScreenShare(projection, metrics) ?: false } catch (_: Throwable) { false }
        } else {
            try { JarvisConversationService.startScreenCapture(projection, metrics) } catch (_: Throwable) { false }
        }
        if (ok) _isScreenSharing.value = true
        return ok
    }

    fun stopScreenShare() {
        dropPendingScreenShare()
        if (isHostEngine) {
            try { hostLiveEngine?.stopScreenShare() } catch (_: Throwable) {}
        } else {
            try { JarvisConversationService.stopScreenCapture() } catch (_: Throwable) {}
        }
        _isScreenSharing.value = false
    }

    /** The engine can only see the screen after the host hands it a MediaProjection. */
    fun wantsScreenShare(): Boolean = liveMode.value == LiveMode.SCREEN_SHARE

    /** Clears a shown live error. */
    fun clearLiveError() {
        _liveError.value = null
        if (!isHostEngine) {
            try { JarvisConversationService.clearLiveError() } catch (_: Throwable) {}
        }
    }

    /** True when a live engine is running (either one). */
    fun isServiceRunning(): Boolean = _isLiveRunning.value ||
        (try { JarvisConversationService.isServiceRunning() } catch (_: Throwable) { false })
}
