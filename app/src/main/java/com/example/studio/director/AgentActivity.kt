package com.example.studio.director

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A bounded, per-director in-memory trace. It does not write streamed model text to the project store. */
enum class ActivityKind { RESEARCH, SCRIPT, PLANNING, SCENE_JSON, ASSET_SEARCH, ASSET_REJECTED, ASSET_ACCEPTED, NARRATION, TIMELINE, SOURCE, MODEL, IMAGE_GEN }
enum class ActivityState { RUNNING, SUCCEEDED, FAILED, CANCELLED, RETRIED, INFO }

data class ActivityEvent(
    val id: Long,
    val kind: ActivityKind,
    val title: String,
    val state: ActivityState,
    val startedAtMs: Long,
    val durationMs: Long? = null,
    val detail: String = "",
    val output: String = "",
    val truncated: Boolean = false
)

/** Thread-safe event bus. Chunks are buffered immediately but UI snapshots are throttled. */
class AgentActivity(
    private val maxEvents: Int = 120,
    private val maxOutputChars: Int = 16_000,
    private val throttleMs: Long = 100L,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    init { require(maxEvents > 0 && maxOutputChars > 0 && throttleMs >= 0) }
    private val buffer = ArrayList<ActivityEvent>()
    private var nextId = 1L
    private var lastPublishedAt = Long.MIN_VALUE
    private val _events = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val events: StateFlow<List<ActivityEvent>> = _events.asStateFlow()

    private fun publish(now: Long) {
        _events.value = buffer.toList()
        lastPublishedAt = now
    }
    private fun trim() {
        while (buffer.size > maxEvents) {
            // Keep running spans when a busy asset hunt floods the trace with instantaneous events.
            val completed = buffer.indexOfFirst { it.state != ActivityState.RUNNING }
            buffer.removeAt(if (completed >= 0) completed else 0)
        }
    }

    @Synchronized
    fun begin(kind: ActivityKind, title: String, detail: String = ""): Long {
        val now = clockMs()
        val id = nextId++
        buffer.add(ActivityEvent(id, kind, title, ActivityState.RUNNING, now, detail = detail))
        trim(); publish(now)
        return id
    }

    @Synchronized
    fun instant(kind: ActivityKind, title: String, detail: String = "") {
        val now = clockMs()
        buffer.add(ActivityEvent(nextId++, kind, title, ActivityState.INFO, now, detail = detail))
        trim(); publish(now)
    }

    @Synchronized
    fun detail(id: Long, text: String, title: String? = null) {
        val index = buffer.indexOfFirst { it.id == id && it.state == ActivityState.RUNNING }
        if (index < 0) return
        val event = buffer[index]
        val currentTitle = title ?: event.title
        if (event.detail == text && event.title == currentTitle) return
        buffer[index] = event.copy(title = currentTitle, detail = text)
        publish(clockMs())
    }

    @Synchronized
    fun append(id: Long, chunk: String) {
        if (chunk.isEmpty()) return
        val index = buffer.indexOfFirst { it.id == id && it.state == ActivityState.RUNNING }
        if (index < 0) return
        val event = buffer[index]
        val remaining = (maxOutputChars - event.output.length).coerceAtLeast(0)
        val accepted = chunk.take(remaining)
        val truncated = event.truncated || chunk.length > remaining
        if (accepted.isEmpty() && truncated == event.truncated) return
        buffer[index] = event.copy(output = event.output + accepted, truncated = truncated)
        val now = clockMs()
        // First content and truncation are immediately visible, even if the stream then stalls.
        if (event.output.isEmpty() || truncated != event.truncated || now - lastPublishedAt >= throttleMs) publish(now)
    }

    @Synchronized
    fun finish(id: Long, state: ActivityState = ActivityState.SUCCEEDED, detail: String? = null) {
        require(state != ActivityState.RUNNING && state != ActivityState.INFO)
        val index = buffer.indexOfFirst { it.id == id && it.state == ActivityState.RUNNING }
        if (index < 0) return
        val now = clockMs()
        val event = buffer[index]
        buffer[index] = event.copy(state = state, durationMs = (now - event.startedAtMs).coerceAtLeast(0), detail = detail ?: event.detail)
        publish(now) // flush buffered final chunks even when they arrived within the throttle window
    }

    @Synchronized
    fun cancelOpen() {
        val now = clockMs()
        for (i in buffer.indices) {
            val event = buffer[i]
            if (event.state == ActivityState.RUNNING) buffer[i] = event.copy(
                state = ActivityState.CANCELLED, durationMs = (now - event.startedAtMs).coerceAtLeast(0)
            )
        }
        publish(now)
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        // IDs never repeat, so a late callback from a cancelled job cannot mutate a new event.
        publish(clockMs())
    }
}

/** Result helper for suspend work: unlike runCatching, it never converts cancellation into a fallback. */
internal suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
