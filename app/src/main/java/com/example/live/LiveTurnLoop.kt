package com.example.live

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.agent.AgentListener
import com.example.agent.AgentOutcome
import com.example.agent.AgentStatusState
import com.example.agent.IntentRouter
import com.example.agent.PhoneAgentRuntime
import com.example.agent.RouteDestination
import com.example.audio.AudioPipeline
import com.example.audio.AudioSourceMode
import com.example.audio.VadGate
import com.example.data.local.ProviderSettingsManager
import com.example.llm.LlmGateway
import com.example.llm.LlmRequest
import com.example.llm.LlmRole
import com.example.llm.ProviderProfile
import com.example.stt.AudioPrep
import com.example.stt.SttProfile
import com.example.stt.SttRouter
import com.example.utils.TTSManager
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisLiveError
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.live.HostLiveTurnListener
import com.jarvis.assistant.live.HostLiveTurnLoop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/** A turn-based live transport. Its service owner, not the UI, owns the microphone and lifetime. */
enum class LiveTurnState { IDLE, LISTENING, TRANSCRIBING, THINKING, SPEAKING }

class LiveTurnLoop(
    context: Context,
    private val systemPrompt: String,
    private val listener: HostLiveTurnListener
) : HostLiveTurnLoop {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = VadGate(sampleRate = 16000, minimumBargeInRms = 700.0)
    private val gateLock = Any()
    private val jobLock = Any()
    private val generation = AtomicLong(0)
    private var work: Job? = null
    private var microphone: AudioPipeline? = null
    private val history = mutableListOf<Pair<String, String>>()
    private val taskUpdates = ConcurrentLinkedQueue<String>()
    @Volatile private var muted = false
    @Volatile private var lastSpeechFinishedAt = 0L
    @Volatile private var screenFrame: ByteArray? = null

    private val _isSessionOn = MutableStateFlow(false)
    val isSessionOn: StateFlow<Boolean> = _isSessionOn.asStateFlow()
    private val _conversationState = MutableStateFlow(LiveTurnState.IDLE)
    val conversationState: StateFlow<LiveTurnState> = _conversationState.asStateFlow()
    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()
    private val _assistantTranscript = MutableStateFlow("")
    val assistantTranscript: StateFlow<String> = _assistantTranscript.asStateFlow()
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    private val _liveError = MutableStateFlow<JarvisLiveError?>(null)
    val liveError: StateFlow<JarvisLiveError?> = _liveError.asStateFlow()

    override fun start(): Boolean {
        if (_isSessionOn.value) return true
        TTSManager.init(context)
        PhoneAgentRuntime.init(context)
        synchronized(gateLock) { gate.reset() }
        _isSessionOn.value = true
        setState(LiveTurnState.LISTENING)
        val pipeline = AudioPipeline(
            context = context,
            sampleRate = 16000,
            sourceMode = AudioSourceMode.COMMUNICATION, // use hardware echo cancellation when available
            onFrameCaptured = { frame, _ -> onFrame(frame) }
        )
        microphone = pipeline
        if (!pipeline.start()) {
            report("MICROPHONE ERROR", "Could not start the microphone. Check microphone permission and try again.", "MIC_ERROR")
            _isSessionOn.value = false
            setState(LiveTurnState.IDLE)
            return false
        }
        return true
    }

    override fun stop() {
        _isSessionOn.value = false
        generation.incrementAndGet()
        synchronized(jobLock) { work?.cancel(); work = null }
        TTSManager.stop()
        microphone?.stop()
        microphone = null
        screenFrame = null
        taskUpdates.clear()
        PhoneAgentRuntime.cancelAll()
        scope.cancel()
        setState(LiveTurnState.IDLE)
        setLevel(0f)
    }

    override fun setMicMuted(muted: Boolean) {
        this.muted = muted
        synchronized(gateLock) { gate.reset() }
        if (muted) setLevel(0f)
    }

    override fun interrupt() {
        if (!_isSessionOn.value) return
        cancelCurrentTurn()
        synchronized(gateLock) { gate.reset() }
        lastSpeechFinishedAt = 0L
        setState(LiveTurnState.LISTENING)
    }

    override fun sendText(text: String) {
        val clean = text.trim()
        if (!_isSessionOn.value || clean.isEmpty()) return
        cancelCurrentTurn()
        synchronized(gateLock) { gate.reset() }
        launchWork { token -> respond(clean, token) }
    }

    override fun onScreenFrame(jpeg: ByteArray?) {
        // Only the latest opt-in screen frame is retained. A null clears it immediately on stop.
        screenFrame = if (jpeg != null && _isSessionOn.value &&
            com.jarvis.assistant.vision.ScreenCaptureManager.getInstance().isSharing.value) jpeg.copyOf() else null
    }

    private fun onFrame(frame: ByteArray) {
        if (!_isSessionOn.value || muted) return
        val state = _conversationState.value
        if (state == LiveTurnState.SPEAKING) {
            val decision = synchronized(gateLock) {
                gate.processFrame(frame, isTtsSpeaking = true, preRollSupplier = {
                    // Never feed the preceding 600ms of speaker echo into a new user turn.
                    microphone?.snapshotPreRoll()?.takeLast(5) ?: emptyList()
                })
            }
            if (decision.isInterruption) {
                // Four 20ms voiced frames are sufficient; stop on the capture thread, not
                // after a network request or UI dispatch. Keep the gate's new utterance.
                cancelCurrentTurn()
                lastSpeechFinishedAt = 0L
                setState(LiveTurnState.LISTENING)
                setLevel(decision.normalizedAmplitude)
                listener.onActivity()
            }
            return
        }
        if (state != LiveTurnState.LISTENING) return
        if (System.currentTimeMillis() - lastSpeechFinishedAt < 350L) return
        val decision = synchronized(gateLock) {
            gate.processFrame(frame, isTtsSpeaking = false, preRollSupplier = {
                microphone?.snapshotPreRoll() ?: emptyList()
            })
        }
        setLevel(decision.normalizedAmplitude)
        if (decision.isSpeechActive) listener.onActivity()
        val audio = decision.completedAudio
        if (decision.isEndOfSpeech && audio != null) {
            launchWork { token -> transcribeAndRespond(audio, decision.audioDurationMs, decision.snrDb, decision.voicedRatio, token) }
        }
    }

    private fun launchWork(block: suspend (Long) -> Unit) {
        if (!_isSessionOn.value) return
        synchronized(jobLock) {
            if (work?.isActive == true) return
            val token = generation.incrementAndGet()
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block(token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LiveTurnLoop", "Turn failed", e)
                    if (isCurrent(token)) report("LIVE VOICE ERROR", e.message ?: "The turn could not be completed.", "TURN_FAILED")
                } finally {
                    if (isCurrent(token)) {
                        setState(LiveTurnState.LISTENING)
                        dispatchTaskUpdate()
                    }
                }
            }
            work = job
            job.start()
        }
    }

    private fun cancelCurrentTurn() {
        generation.incrementAndGet()
        // Stop synchronously for barge-in. Cancellation also prevents a late LLM chunk
        // or TTS callback from reviving the interrupted turn.
        TTSManager.stop()
        synchronized(jobLock) { work?.cancel(); work = null }
    }

    private suspend fun transcribeAndRespond(pcm: ByteArray, duration: Long, snr: Double, ratio: Float, token: Long) {
        setState(LiveTurnState.TRANSCRIBING)
        val prefs = JarvisApp.instance.preferences
        if (prefs.isOwnerVoiceEnabled && prefs.hasOwnerVoiceProfile()) {
            val match = com.jarvis.assistant.audio.OwnerVoiceManager.verifySpeakerAudio(pcm, prefs)
            if (!match.isMatch) {
                report("VOICE LOCK", if (match.hasExtractedVector) "Voice did not match the enrolled owner." else "Voice lock could not verify this recording; wait for the offline voice model or speak again.", "VOICE_NOT_VERIFIED")
                return
            }
        }
        val wav = AudioPrep.pcmToWav(AudioPrep.processPcm(pcm, 16000), 16000)
        val result = SttRouter.transcribe(
            wavBytes = wav, profile = SttProfile.LIVE_COMMAND, context = context,
            durationMs = duration, snrDb = snr, voicedRatio = ratio
        )
        currentCoroutineContext().ensureActive()
        if (!isCurrent(token)) return
        if (!result.isSuccess) {
            val reason = result.rejectedReason.orEmpty()
            // Noise and empty utterances are expected; configuration/network failures are actionable.
            if (reason.contains("model", true) || reason.contains("key", true) || reason.contains("failed", true) || reason.contains("network", true)) {
                report("SPEECH RECOGNITION", reason, "STT_ERROR")
            }
            return
        }
        respond(result.text, token)
    }

    private suspend fun respond(text: String, token: Long) {
        if (!isCurrent(token)) return
        _liveError.value = null
        _userTranscript.value = text
        _assistantTranscript.value = ""
        listener.onUserTranscript(text)
        listener.onAssistantTranscript("")
        listener.onActivity()
        setState(LiveTurnState.THINKING)

        val isTaskRunning = PhoneAgentRuntime.status.value.state == AgentStatusState.RUNNING
        val route = IntentRouter.route(text, isTaskRunning)
        currentCoroutineContext().ensureActive()
        if (!isCurrent(token)) return
        if (route.route == RouteDestination.CONTROL) {
            val reply = PhoneAgentRuntime.control(route.controlCmd ?: text)
            publishReply(text, reply, token)
            return
        }
        if (route.route == RouteDestination.TASK) {
            PhoneAgentRuntime.submit(route.goal, listener = object : AgentListener {
                override fun onMilestone(text: String) = Unit
                override fun onAskUser(question: String, options: List<String>?) { queueTaskUpdate(question) }
                override fun onFinished(outcome: AgentOutcome) { queueTaskUpdate(outcome.summary) }
            })
            publishReply(text, "Task shuru kar raha hoon.", token)
            return
        }

        val settings = ProviderSettingsManager.loadState()
        val key = settings.apiKeys.getOrNull(settings.currentKeyIndex)?.takeIf { it.isNotBlank() }
            ?: settings.apiKeys.firstOrNull { it.isNotBlank() }.orEmpty()
        if (key.isBlank()) {
            report("API KEY MISSING", "Add an API key in AI Provider settings.", "API_KEY_MISSING")
            return
        }
        val frame = if (com.jarvis.assistant.vision.ScreenCaptureManager.getInstance().isSharing.value) screenFrame else null
        val profile = ProviderProfile.detectProviderFromKey(key, settings.providerName)
        if (frame != null && !profile.dialect.supportsVision) {
            report("SCREEN VISION UNAVAILABLE", "The selected provider does not support images. Choose a vision-capable model to discuss the shared screen.", "VISION_UNAVAILABLE")
            return
        }
        val request = LlmRequest(
            prompt = text,
            systemPrompt = "$systemPrompt\n\nReply naturally and concisely for speech in the user's language. " +
                "Do not claim to have performed a device action unless it actually happened. " +
                "Do not claim to see the screen unless an image is attached.",
            history = synchronized(history) { history.toList() },
            imagesBase64 = frame?.let { listOf(Base64.encodeToString(it, Base64.NO_WRAP)) } ?: emptyList(),
            maxTokens = 400
        )
        val streamed = StringBuilder()
        val result = LlmGateway.call(
            role = LlmRole.SUMMARIZER, provider = settings.providerName, apiKey = key,
            model = settings.activeModel, request = request,
            onChunk = { chunk ->
                if (isCurrent(token)) {
                    streamed.append(chunk)
                    _assistantTranscript.value = streamed.toString()
                    listener.onAssistantTranscript(_assistantTranscript.value)
                }
            }
        )
        currentCoroutineContext().ensureActive()
        if (!isCurrent(token)) return
        val response = result.getOrElse {
            report("AI CONNECTION ERROR", it.message ?: "The provider did not respond.", "LLM_ERROR")
            return
        }.text.trim()
        if (response.isEmpty()) {
            report("EMPTY RESPONSE", "The provider returned an empty reply. Please try again.", "LLM_EMPTY")
            return
        }
        publishReply(text, response, token)
    }

    private suspend fun publishReply(user: String, reply: String, token: Long) {
        if (!isCurrent(token)) return
        _assistantTranscript.value = reply
        listener.onAssistantTranscript(reply)
        synchronized(history) {
            history.add("user" to user)
            history.add("assistant" to reply)
            while (history.size > 12) history.removeAt(0)
        }
        listener.onTurnCompleted(user, reply)
        speak(reply, token)
    }

    private suspend fun speak(text: String, token: Long) {
        if (!isCurrent(token)) return
        synchronized(gateLock) { gate.reset() }
        setState(LiveTurnState.SPEAKING)
        // TTSManager resolves SaifVoice.selectedId on every utterance; it defaults to
        // SAIF ADVANCE VOICE and respects changes made in Settings.
        val completed = withTimeoutOrNull(90_000L) {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { TTSManager.stop() }
                TTSManager.speak(text, context, onDone = {
                    if (continuation.isActive) continuation.resume(Unit)
                })
            }
            true
        } ?: false
        if (isCurrent(token)) {
            if (!completed) TTSManager.stop()
            lastSpeechFinishedAt = System.currentTimeMillis()
            synchronized(gateLock) { gate.reset() }
        }
    }

    private fun queueTaskUpdate(text: String) {
        if (!_isSessionOn.value || text.isBlank()) return
        taskUpdates.add(text)
        dispatchTaskUpdate()
    }

    private fun dispatchTaskUpdate() {
        if (!_isSessionOn.value || _conversationState.value != LiveTurnState.LISTENING) return
        // The just-finished job may still be unwinding its finally block; dispatch later.
        scope.launch {
            kotlinx.coroutines.delay(30)
            if (!_isSessionOn.value || _conversationState.value != LiveTurnState.LISTENING) return@launch
            val update = taskUpdates.poll() ?: return@launch
            launchWork { token ->
                _assistantTranscript.value = update
                listener.onAssistantTranscript(update)
                speak(update, token)
            }
        }
    }

    private fun isCurrent(token: Long) = _isSessionOn.value && generation.get() == token

    private fun setState(state: LiveTurnState) {
        _conversationState.value = state
        listener.onState(when (state) {
            LiveTurnState.IDLE -> ConversationState.IDLE
            LiveTurnState.LISTENING -> ConversationState.LISTENING
            LiveTurnState.TRANSCRIBING, LiveTurnState.THINKING -> ConversationState.THINKING
            LiveTurnState.SPEAKING -> ConversationState.SPEAKING
        })
    }

    private fun setLevel(level: Float) {
        _audioLevel.value = level
        listener.onAudioLevel(level)
    }

    private fun report(title: String, message: String, code: String) {
        val error = JarvisLiveError(title, message, code, "RETRY")
        _liveError.value = error
        listener.onError(error)
    }
}
