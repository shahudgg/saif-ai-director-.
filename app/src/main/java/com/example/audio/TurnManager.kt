package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TurnState {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    SPEAKING
}

/**
 * Manages conversational turn state transitions and isolates the independent TASK execution lane.
 * Prevents ambient noise, audio from controlled apps (e.g. YouTube playback), or background chatter
 * from corrupting or prematurely cancelling active device automation tasks.
 */
class TurnManager {

    private val _turnState = MutableStateFlow(TurnState.IDLE)
    val turnState: StateFlow<TurnState> = _turnState.asStateFlow()

    private val _isTaskRunning = MutableStateFlow(false)
    val isTaskRunning: StateFlow<Boolean> = _isTaskRunning.asStateFlow()

    private val _currentTaskGoal = MutableStateFlow("")
    val currentTaskGoal: StateFlow<String> = _currentTaskGoal.asStateFlow()

    companion object {
        // Strict stop words with isolated word boundaries
        private val ISOLATED_STOP_REGEX = Regex("""^(stop|ruko|rukiye|cancel|band karo)$""", RegexOption.IGNORE_CASE)
        private val ATTENTION_REGEX = Regex("""^(saif|hey saif|jarvis)\b.*""", RegexOption.IGNORE_CASE)
    }

    fun transitionTo(newState: TurnState) {
        _turnState.value = newState
    }

    fun setTaskRunning(running: Boolean, goal: String = "") {
        _isTaskRunning.value = running
        _currentTaskGoal.value = if (running) goal else ""
    }

    /**
     * Determines whether a newly transcribed utterance should be processed given the current state.
     * When a task is running, strictly admits:
     * 1) Exact isolated STOP commands ("stop", "ruko", "cancel", "band karo")
     * 2) Utterances explicitly directed to the assistant ("SAIF, ...")
     * Filters out video speech, music lyrics, and casual ambient phrases.
     */
    fun shouldProcessUtterance(rawTranscript: String): TaskLaneAction {
        val trimmed = rawTranscript.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[.,!?]"""), "")
            .trim()

        if (!_isTaskRunning.value) {
            return TaskLaneAction.PROCESS_NORMAL
        }

        // Active task lane check:
        if (ISOLATED_STOP_REGEX.matches(trimmed)) {
            return TaskLaneAction.TASK_EMERGENCY_STOP
        }

        if (ATTENTION_REGEX.matches(trimmed)) {
            val cleanedCommand = trimmed.replace(Regex("""^(saif|hey saif|jarvis)\s*""", RegexOption.IGNORE_CASE), "").trim()
            return TaskLaneAction.ATTENTION_COMMAND(cleanedCommand.ifBlank { trimmed })
        }

        // Discard all other audio as background noise / controlled app output
        return TaskLaneAction.IGNORE_BACKGROUND_AUDIO
    }

    sealed class TaskLaneAction {
        object PROCESS_NORMAL : TaskLaneAction()
        object TASK_EMERGENCY_STOP : TaskLaneAction()
        data class ATTENTION_COMMAND(val command: String) : TaskLaneAction()
        object IGNORE_BACKGROUND_AUDIO : TaskLaneAction()
    }
}
