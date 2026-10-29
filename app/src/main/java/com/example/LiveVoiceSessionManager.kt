package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.AudioPipeline
import com.example.audio.AudioSourceMode
import com.example.audio.TurnManager
import com.example.audio.TurnState
import com.example.audio.VadGate
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRepository
import com.example.data.local.SaifDatabase
import com.example.data.remote.AIApiUtility
import com.example.util.AudioTranscriptionEngine
import com.example.util.DeviceAction
import com.example.util.DeviceActionManager
import com.example.util.DeviceActionParser
import com.example.util.SaifAccessibilityService
import com.example.util.SmartConversationMemory
import com.example.utils.TTSManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class VoiceSessionState {
    IDLE, LISTENING, PROCESSING, SPEAKING
}

/**
 * Modernized Live Voice Session Manager with:
 * - Dedicated urgent-priority AudioPipeline.
 * - Zero first-word cutoffs: 600 ms circular pre-roll buffer stitching.
 * - Adaptive noise floor VadGate with multi-feature voicing criteria.
 * - Decoupled TurnManager with an isolated TASK lane so background app audio
 *   (e.g., YouTube playback, terrace noise) never terminates running agent tasks.
 * - Clean audio session routing with proper MODE_NORMAL restoration on exit.
 */
class LiveVoiceSessionManager(
    private val context: Context,
    private val onClose: () -> Unit
) {
    companion object {
        private const val TAG = "LiveVoiceSessionManager"
        private const val SAMPLE_RATE = 16000
        private const val TTS_COOLDOWN_MS = 400L
    }

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _sessionState = MutableStateFlow(VoiceSessionState.IDLE)
    val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

    private val _liveSubtitle = MutableStateFlow("")
    val liveSubtitle: StateFlow<String> = _liveSubtitle.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioPipeline: AudioPipeline? = null
    private val vadGate = VadGate(sampleRate = SAMPLE_RATE)
    private val turnManager = TurnManager()

    @Volatile
    private var isRecording = false

    @Volatile
    private var isTtsSpeaking = false

    @Volatile
    private var lastTtsFinishTime = 0L

    @Volatile
    private var lastExecutedGoal: String = ""

    @Volatile
    private var lastTaskCompletionTime = 0L

    private val isProcessingTurn = AtomicBoolean(false)

    private var animationJob: Job? = null
    private var processingJob: Job? = null

    // Streaming TTS Queue
    private val speechQueue = ConcurrentLinkedQueue<String>()
    private val isPlayingQueueItem = AtomicBoolean(false)
    private val isStreamGenerationComplete = AtomicBoolean(false)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        TTSManager.init(context)
        SmartConversationMemory.init(context)
        com.example.agent.PhoneAgentRuntime.init(context)

        // Observe task running state to keep the task lane isolated
        scope.launch {
            com.example.agent.PhoneAgentRuntime.status.collect { status ->
                val isRunning = status.state == com.example.agent.AgentStatusState.RUNNING
                turnManager.setTaskRunning(isRunning, status.currentGoal)
            }
        }
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            onClose()
            return
        }

        vadGate.reset()
        turnManager.transitionTo(TurnState.LISTENING)
        _sessionState.value = VoiceSessionState.LISTENING
        _liveSubtitle.value = "LISTENING"

        audioPipeline = AudioPipeline(
            context = context,
            sampleRate = SAMPLE_RATE,
            frameDurationMs = 20,
            preRollMs = 600,
            sourceMode = AudioSourceMode.AUTO,
            onFrameCaptured = { frame, _ ->
                handleIncomingAudioFrame(frame)
            }
        )

        val started = audioPipeline?.start() ?: false
        if (!started) {
            Log.e(TAG, "Failed starting AudioPipeline")
            onClose()
            return
        }

        isRecording = true
        Log.i(TAG, "LiveVoiceSessionManager started with real-time AudioPipeline")
    }

    private fun handleIncomingAudioFrame(frame: ByteArray) {
        if (!isRecording) return

        val now = System.currentTimeMillis()
        val isCurrentlySpeaking = isTtsSpeaking || _sessionState.value == VoiceSessionState.SPEAKING

        // Run VAD on the 20ms frame
        val vadResult = vadGate.processFrame(
            pcmFrame = frame,
            isTtsSpeaking = isCurrentlySpeaking,
            preRollSupplier = { audioPipeline?.snapshotPreRoll() ?: emptyList() }
        )

        // 1. Handle Barge-in during TTS playback
        if (isCurrentlySpeaking) {
            if (vadResult.isInterruption) {
                Log.d(TAG, "Near-field vocal barge-in confirmed! Halting TTS immediately.")
                stopSpeaking(causedByUserInterruption = true)
                turnManager.transitionTo(TurnState.LISTENING)
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "Listening..."
                _amplitude.value = vadResult.normalizedAmplitude
            }
            return
        }

        // 2. Cooldown right after TTS finishes naturally to prevent acoustic room echo
        if (now - lastTtsFinishTime < TTS_COOLDOWN_MS) {
            _amplitude.value = 0f
            return
        }

        // 3. During model processing, update waveform visuals but don't disrupt
        if (_sessionState.value == VoiceSessionState.PROCESSING && !turnManager.isTaskRunning.value) {
            return
        }

        // 4. Update live amplitude for visual glow animations
        _amplitude.value = vadResult.normalizedAmplitude

        if (vadResult.isSpeechActive) {
            if (_sessionState.value == VoiceSessionState.LISTENING && _liveSubtitle.value != "Listening...") {
                _liveSubtitle.value = "Listening..."
            }
        }

        // 5. Speech turn completed
        if (vadResult.isEndOfSpeech && vadResult.completedAudio != null) {
            val pcmData = vadResult.completedAudio
            val durationMs = vadResult.audioDurationMs
            val snrDb = vadResult.snrDb
            val voicedRatio = vadResult.voicedRatio

            Log.d(TAG, "Completed speech turn: ${pcmData.size} bytes (${durationMs}ms, SNR=${snrDb}dB, Voiced=$voicedRatio)")
            triggerTurnProcessing(pcmData, durationMs, snrDb, voicedRatio)
        }
    }

    private fun triggerTurnProcessing(pcmData: ByteArray, durationMs: Long, snrDb: Double, voicedRatio: Float) {
        if (!isProcessingTurn.compareAndSet(false, true)) {
            Log.d(TAG, "Turn processor is already active. Enqueueing/dropping duplicate.")
            return
        }

        processingJob?.cancel()
        processingJob = scope.launch {
            try {
                processAudioTurn(pcmData, durationMs, snrDb, voicedRatio)
            } finally {
                isProcessingTurn.set(false)
            }
        }
    }

    private suspend fun processAudioTurn(pcmData: ByteArray, durationMs: Long, snrDb: Double, voicedRatio: Float) {
        if (pcmData.isEmpty()) {
            _sessionState.value = VoiceSessionState.LISTENING
            _liveSubtitle.value = "LISTENING"
            return
        }

        try {
            turnManager.transitionTo(TurnState.TRANSCRIBING)
            val wavData = wrapPcmToWav(pcmData, SAMPLE_RATE, 1, 16)
            val base64Audio = Base64.encodeToString(wavData, Base64.NO_WRAP)
            val apiKey = AudioTranscriptionEngine.resolveActiveApiKey()

            val transcriptionResult = AudioTranscriptionEngine.transcribeWav(
                apiKey = apiKey,
                base64Audio = base64Audio,
                rawWavBytes = wavData,
                context = context,
                durationMs = durationMs,
                snrDb = snrDb,
                voicedRatio = voicedRatio
            )

            if (!transcriptionResult.isSuccess) {
                val err = transcriptionResult.exceptionOrNull()?.message ?: "Transcription failed"
                Log.d(TAG, "Transcription filtered/failed: $err")
                _liveSubtitle.value = if (turnManager.isTaskRunning.value) "Executing..." else "LISTENING"
                _sessionState.value = VoiceSessionState.LISTENING
                turnManager.transitionTo(TurnState.LISTENING)
                return
            }

            val text = transcriptionResult.getOrNull()?.trim() ?: ""
            if (text.isBlank()) {
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = if (turnManager.isTaskRunning.value) "Executing..." else "LISTENING"
                turnManager.transitionTo(TurnState.LISTENING)
                return
            }

            Log.i(TAG, "Live Voice Utterance Transcribed: \"$text\"")

            // Check Task Lane Isolation
            val taskAction = turnManager.shouldProcessUtterance(text)
            when (taskAction) {
                is TurnManager.TaskLaneAction.IGNORE_BACKGROUND_AUDIO -> {
                    Log.d(TAG, "Ignored background/controlled audio while task is active: '$text'")
                    _liveSubtitle.value = "Executing..."
                    _sessionState.value = VoiceSessionState.LISTENING
                    turnManager.transitionTo(TurnState.LISTENING)
                    return
                }
                is TurnManager.TaskLaneAction.TASK_EMERGENCY_STOP -> {
                    Log.i(TAG, "Emergency stop requested by user during active task!")
                    com.example.agent.PhoneAgentRuntime.control("stop")
                    stopSpeaking()
                    enqueueSentenceForSpeech("Task rok diya gaya hai.")
                    playNextSentenceIfAvailable()
                    _sessionState.value = VoiceSessionState.LISTENING
                    _liveSubtitle.value = "LISTENING"
                    turnManager.transitionTo(TurnState.LISTENING)
                    return
                }
                is TurnManager.TaskLaneAction.ATTENTION_COMMAND -> {
                    Log.i(TAG, "Attention command during active task: '${taskAction.command}'")
                    // Will continue to route with the stripped command
                }
                TurnManager.TaskLaneAction.PROCESS_NORMAL -> {
                    // Normal route
                }
            }

            val cleanUtterance = if (taskAction is TurnManager.TaskLaneAction.ATTENTION_COMMAND) {
                taskAction.command
            } else {
                text
            }

            // Quick Voice Stop
            val lower = cleanUtterance.lowercase()
            if (lower in listOf("stop", "ruko", "cancel", "band karo")) {
                stopSpeaking()
                com.example.agent.PhoneAgentRuntime.control("stop")
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "LISTENING"
                turnManager.transitionTo(TurnState.LISTENING)
                return
            }

            // Save facts to Smart Conversation Memory
            SmartConversationMemory.extractAndSaveFacts(cleanUtterance)

            // Save user message to Room DB
            val db = SaifDatabase.getDatabase(context)
            val repo = ChatRepository(db.chatDao())
            val activeSessionId = LiveVoiceManager.currentSessionId

            if (activeSessionId != null) {
                repo.insertMessage(
                    ChatMessageEntity(
                        sessionId = activeSessionId,
                        role = "user",
                        content = cleanUtterance,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            val isAgentRunning = com.example.agent.PhoneAgentRuntime.status.value.state == com.example.agent.AgentStatusState.RUNNING
            val routeDecision = com.example.agent.IntentRouter.route(cleanUtterance, isAgentRunning)

            if (routeDecision.route == com.example.agent.RouteDestination.CONTROL) {
                val reply = com.example.agent.PhoneAgentRuntime.control(routeDecision.controlCmd ?: cleanUtterance)
                enqueueSentenceForSpeech(reply)
                playNextSentenceIfAvailable()
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "LISTENING"
                turnManager.transitionTo(TurnState.LISTENING)
                return
            }

            if (routeDecision.route == com.example.agent.RouteDestination.TASK) {
                val now = System.currentTimeMillis()
                val normalizedGoal = routeDecision.goal.trim().lowercase()
                if (normalizedGoal == lastExecutedGoal && (now - lastTaskCompletionTime) < 5000L) {
                    Log.i(TAG, "Suppressed duplicate task echo within cooldown: '$normalizedGoal'")
                    _sessionState.value = VoiceSessionState.LISTENING
                    _liveSubtitle.value = "LISTENING"
                    turnManager.transitionTo(TurnState.LISTENING)
                    return
                }

                turnManager.setTaskRunning(true, routeDecision.goal)
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "Executing..."

                scope.launch {
                    try {
                        val plan = com.example.util.AutonomousScreenAgent.planTask(context, routeDecision.goal)
                        if (plan.steps.isNotEmpty()) {
                            com.example.util.AutonomousScreenAgent.executeTaskPlan(context, plan) { step, msg ->
                                enqueueSentenceForSpeech(msg)
                                playNextSentenceIfAvailable()
                            }
                            val summary = "Ji sir, sabhi tasks execute kar diye gaye hain."
                            enqueueSentenceForSpeech(summary)
                            playNextSentenceIfAvailable()

                            if (activeSessionId != null) {
                                repo.insertMessage(
                                    ChatMessageEntity(
                                        sessionId = activeSessionId,
                                        role = "model",
                                        content = summary,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        } else {
                            // Fallback to PhoneAgentRuntime
                            com.example.agent.PhoneAgentRuntime.submit(
                                goal = routeDecision.goal,
                                listener = object : com.example.agent.AgentListener {
                                    override fun onMilestone(text: String) {
                                        enqueueSentenceForSpeech(text)
                                        playNextSentenceIfAvailable()
                                    }

                                    override fun onAskUser(question: String, options: List<String>?) {
                                        enqueueSentenceForSpeech(question)
                                        playNextSentenceIfAvailable()
                                    }

                                    override fun onFinished(outcome: com.example.agent.AgentOutcome) {
                                        enqueueSentenceForSpeech(outcome.summary)
                                        playNextSentenceIfAvailable()

                                        if (activeSessionId != null && outcome.summary.isNotBlank()) {
                                            scope.launch {
                                                repo.insertMessage(
                                                    ChatMessageEntity(
                                                        sessionId = activeSessionId,
                                                        role = "model",
                                                        content = outcome.summary,
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Task execution failed: ${e.message}", e)
                        enqueueSentenceForSpeech("Task execute karne me dikkat aayi sir.")
                        playNextSentenceIfAvailable()
                    } finally {
                        lastExecutedGoal = routeDecision.goal.trim().lowercase()
                        lastTaskCompletionTime = System.currentTimeMillis()
                        turnManager.setTaskRunning(false, "")
                        _liveSubtitle.value = "LISTENING"
                        turnManager.transitionTo(TurnState.LISTENING)
                    }
                }
                return
            }

            // Normal conversational chat
            turnManager.transitionTo(TurnState.THINKING)
            _sessionState.value = VoiceSessionState.PROCESSING
            _liveSubtitle.value = "THINKING"

            val recentEntities = if (activeSessionId != null) {
                repo.getRecentMessages(activeSessionId, 12)
            } else {
                emptyList()
            }
            val conversationHistory = recentEntities.map { entity ->
                Pair(if (entity.role == "user") "user" else "model", entity.content)
            }

            val memoryBlock = SmartConversationMemory.buildMemoryPromptBlock()
            val service = SaifAccessibilityService.instance
            val activeApp = service?.getCurrentAppLabel(context) ?: "Home Screen"
            val currentPkg = service?.getCurrentPackage() ?: ""
            val screenHierarchy = service?.inspectScreenHierarchy()?.take(1600) ?: ""

            val fullSystemPrompt = """
You are SAIF AI, an advanced real-time Android voice assistant.
$memoryBlock

CURRENT ACTIVE SCREEN: You are currently on the screen of '$activeApp' (Package: $currentPkg).
${if (screenHierarchy.isNotBlank()) "VISIBLE SCREEN CONTENT:\n$screenHierarchy\n" else ""}
AUTONOMOUS DEVICE AUTOMATION ACTIONS:
You have native access to control the Android phone, perform human-like touches, navigate apps, and execute multi-step macros.
When the user asks to perform device actions or macros, output an action block at the beginning of your response:
<<<ACTIONS
[
  {"type": "YOUTUBE_PLAY_SUBSCRIBE", "query": "trending song", "subscribe": true},
  {"type": "CALL", "target": "papa"},
  {"type": "WHATSAPP", "recipient": "Dad", "message": "Hii"},
  {"type": "ACCESSIBILITY_NAV", "action": "home"},
  {"type": "ACCESSIBILITY_SCROLL", "direction": "down"}
]
ACTIONS>>>

SPOKEN RESPONSE FORMAT:
- If actions are executed, confirm what was done briefly and confidently like Jarvis ("Yes sir, ...", "Theek hai sir, ...").
- Provide a friendly, concise 1-2 sentence spoken reply in Hindi/English/Hinglish (matching the user's spoken language).
- Speak naturally like a real human.
- STRICT RULE: NEVER output programming code, Kotlin, Java, markdown blocks, JSON, asterisks, or bullet points in your spoken reply.
""".trimIndent()

            clearStreamingQueue()
            isStreamGenerationComplete.set(false)

            val fullModelResponseBuilder = StringBuilder()
            val streamingTextBuffer = StringBuilder()
            var isInsideActionBlock = false

            val apiResult = AIApiUtility.executeWithFallback(
                prompt = cleanUtterance,
                mode = "general",
                history = conversationHistory,
                customSystemPrompt = fullSystemPrompt,
                base64Images = emptyList(),
                onChunk = { chunk ->
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) return@executeWithFallback
                    fullModelResponseBuilder.append(chunk)
                    streamingTextBuffer.append(chunk)

                    var bufferContent = streamingTextBuffer.toString()
                    if (!isInsideActionBlock && bufferContent.contains("<<<ACTIONS")) {
                        isInsideActionBlock = true
                    }
                    if (isInsideActionBlock) {
                        if (bufferContent.contains("ACTIONS>>>")) {
                            bufferContent = bufferContent.substringAfter("ACTIONS>>>").trimStart()
                            streamingTextBuffer.clear()
                            streamingTextBuffer.append(bufferContent)
                            isInsideActionBlock = false
                        } else {
                            return@executeWithFallback
                        }
                    }

                    val sentenceRegex = Regex("""(?<=[.?!।\n])\s+""")
                    val parts = bufferContent.split(sentenceRegex)

                    if (parts.size > 1) {
                        for (i in 0 until parts.size - 1) {
                            val sentence = parts[i].trim()
                            if (sentence.isNotBlank() && !sentence.contains("<<<ACTIONS")) {
                                enqueueSentenceForSpeech(sentence)
                            }
                        }
                        streamingTextBuffer.clear()
                        streamingTextBuffer.append(parts.last())
                    }
                }
            )

            isStreamGenerationComplete.set(true)

            var remainingText = streamingTextBuffer.toString().trim()
            if (remainingText.contains("ACTIONS>>>")) {
                remainingText = remainingText.substringAfter("ACTIONS>>>").trim()
            }
            if (remainingText.isNotBlank() && !remainingText.contains("<<<ACTIONS")) {
                enqueueSentenceForSpeech(remainingText)
            }

            val rawReply = apiResult.getOrNull()
                ?: fullModelResponseBuilder.toString().ifBlank {
                    val err = apiResult.exceptionOrNull()?.message ?: "Network error"
                    "Maaf kijiye, AI service se response nahi mila. Kripya apna API key ya internet connection check karein."
                }

            val parsed = DeviceActionParser.parse(rawReply, cleanUtterance)
            if (parsed.actions.isNotEmpty()) {
                val actionResults = DeviceActionManager.executeActionsSequentially(context, parsed.actions)
                if (parsed.actions.any { it is DeviceAction.ReadNotifications }) {
                    val notifResult = actionResults.firstOrNull {
                        it.contains("notification", ignoreCase = true) || it.contains("SMS", ignoreCase = true)
                    } ?: DeviceActionManager.readNotificationsSummary(context, null)
                    if (!notifResult.isNullOrBlank()) {
                        enqueueSentenceForSpeech(notifResult)
                    }
                }
            }

            val cleanCompleteReply = parsed.cleanSpokenText.ifBlank {
                if (parsed.actions.isNotEmpty()) "Theek hai sir, action execute kar diya hai." else rawReply
            }

            if (activeSessionId != null && cleanCompleteReply.isNotBlank()) {
                repo.insertMessage(
                    ChatMessageEntity(
                        sessionId = activeSessionId,
                        role = "model",
                        content = cleanCompleteReply,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            if (speechQueue.isEmpty() && !isPlayingQueueItem.get()) {
                enqueueSentenceForSpeech(cleanCompleteReply)
            }

            playNextSentenceIfAvailable()

        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.d(TAG, "Turn processing cancelled by user barge-in.")
                return
            }
            Log.e(TAG, "Error in processAudioTurn: ${e.message}", e)
            _liveSubtitle.value = "LISTENING"
            _sessionState.value = VoiceSessionState.LISTENING
            turnManager.transitionTo(TurnState.LISTENING)
        }
    }

    private fun enqueueSentenceForSpeech(rawSentence: String) {
        val clean = TTSManager.cleanTextForSpeech(rawSentence)
        if (clean.isNotBlank()) {
            speechQueue.add(clean)
            playNextSentenceIfAvailable()
        }
    }

    private fun playNextSentenceIfAvailable() {
        if (!isRecording) return

        if (isPlayingQueueItem.compareAndSet(false, true)) {
            val nextSentence = speechQueue.poll()
            if (nextSentence != null) {
                turnManager.transitionTo(TurnState.SPEAKING)
                _sessionState.value = VoiceSessionState.SPEAKING
                isTtsSpeaking = true
                _liveSubtitle.value = nextSentence

                startVoiceWaveAnimation()

                TTSManager.speak(
                    text = nextSentence,
                    context = context,
                    onStart = {
                        _sessionState.value = VoiceSessionState.SPEAKING
                        isTtsSpeaking = true
                        _liveSubtitle.value = nextSentence
                    },
                    onDone = {
                        isPlayingQueueItem.set(false)
                        if (speechQueue.isNotEmpty()) {
                            playNextSentenceIfAvailable()
                        } else if (isStreamGenerationComplete.get()) {
                            onSpeechPlaybackComplete()
                        }
                    }
                )
            } else {
                isPlayingQueueItem.set(false)
                if (isStreamGenerationComplete.get()) {
                    onSpeechPlaybackComplete()
                }
            }
        }
    }

    private fun onSpeechPlaybackComplete() {
        isTtsSpeaking = false
        lastTtsFinishTime = System.currentTimeMillis()
        animationJob?.cancel()
        _amplitude.value = 0f

        if (_sessionState.value == VoiceSessionState.SPEAKING) {
            turnManager.transitionTo(TurnState.LISTENING)
            _sessionState.value = VoiceSessionState.LISTENING
            _liveSubtitle.value = if (turnManager.isTaskRunning.value) "Executing..." else "LISTENING"
            vadGate.reset()
        }
    }

    private fun startVoiceWaveAnimation() {
        animationJob?.cancel()
        animationJob = scope.launch {
            while (_sessionState.value == VoiceSessionState.SPEAKING) {
                _amplitude.value = kotlin.random.Random.nextFloat() * 0.55f + 0.2f
                delay(90)
            }
        }
    }

    private fun clearStreamingQueue() {
        speechQueue.clear()
        isPlayingQueueItem.set(false)
    }

    suspend fun transcribeWav(apiKey: String, base64Audio: String, rawWavBytes: ByteArray): Result<String> {
        return AudioTranscriptionEngine.transcribeWav(apiKey, base64Audio, rawWavBytes, context)
    }

    fun stopSpeaking(causedByUserInterruption: Boolean = false) {
        isTtsSpeaking = false
        isStreamGenerationComplete.set(true)
        lastTtsFinishTime = if (causedByUserInterruption) 0L else System.currentTimeMillis()
        clearStreamingQueue()
        TTSManager.stop()
        animationJob?.cancel()
        if (!causedByUserInterruption) {
            processingJob?.cancel()
            isProcessingTurn.set(false)
        }
        vadGate.reset()

        turnManager.transitionTo(TurnState.LISTENING)
        _sessionState.value = VoiceSessionState.LISTENING
        _amplitude.value = 0f
        _liveSubtitle.value = if (turnManager.isTaskRunning.value) "Executing..." else "Listening..."
    }

    fun stop() {
        isRecording = false
        isTtsSpeaking = false
        clearStreamingQueue()

        processingJob?.cancel()
        animationJob?.cancel()

        try {
            audioPipeline?.stop()
            audioPipeline = null
        } catch (e: Exception) {
            Log.w(TAG, "AudioPipeline stop error: ${e.message}")
        }

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.w(TAG, "Audio manager reset error: ${e.message}")
        }

        TTSManager.stop()
        turnManager.transitionTo(TurnState.IDLE)
        _sessionState.value = VoiceSessionState.IDLE
        _amplitude.value = 0f

        scope.cancel()
    }

    private fun wrapPcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        return AudioTranscriptionEngine.wrapPcmToWav(pcmData, sampleRate, channels, bitDepth)
    }
}
