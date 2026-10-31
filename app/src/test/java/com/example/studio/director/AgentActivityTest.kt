package com.example.studio.director

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentActivityTest {
    @Test fun retriesHaveSeparateOutputAndMeasuredDurations() {
        var now = 1_000L
        val trace = AgentActivity(clockMs = { now })
        val first = trace.begin(ActivityKind.SCENE_JSON, "attempt 1")
        trace.append(first, "partial ")
        trace.append(first, "JSON") // buffered within the throttle window
        now += 240
        trace.finish(first, ActivityState.RETRIED)
        val second = trace.begin(ActivityKind.SCENE_JSON, "attempt 2")
        trace.append(second, "complete JSON")
        now += 310
        trace.finish(second)
        assertEquals(listOf("partial JSON", "complete JSON"), trace.events.value.map { it.output })
        assertEquals(listOf(ActivityState.RETRIED, ActivityState.SUCCEEDED), trace.events.value.map { it.state })
        assertEquals(listOf(240L, 310L), trace.events.value.map { it.durationMs })
    }

    @Test fun chunksAreThrottledFlushedAndBounded() {
        var now = 0L
        val trace = AgentActivity(maxEvents = 2, maxOutputChars = 5, throttleMs = 100, clockMs = { now })
        val id = trace.begin(ActivityKind.SCRIPT, "Script")
        trace.append(id, "a")
        assertEquals("a", trace.events.value.last().output)
        trace.append(id, "b")
        assertEquals("a", trace.events.value.last().output)
        now += 110
        trace.append(id, "c")
        assertEquals("abc", trace.events.value.last().output)
        trace.append(id, "defgh")
        assertEquals("abcde", trace.events.value.last().output)
        assertTrue(trace.events.value.last().truncated)
        trace.finish(id)
        trace.instant(ActivityKind.ASSET_REJECTED, "Rejected", "Wrong format")
        trace.instant(ActivityKind.ASSET_ACCEPTED, "Accepted", "Technical checks only")
        assertEquals(2, trace.events.value.size)
        assertEquals("Technical checks only", trace.events.value.last().detail)
    }

    @Test fun retentionKeepsRunningStageDuringManyInstantEvents() {
        val trace = AgentActivity(maxEvents = 3)
        val stage = trace.begin(ActivityKind.ASSET_SEARCH, "Asset hunt")
        repeat(10) { trace.instant(ActivityKind.ASSET_SEARCH, "Search $it") }
        assertTrue(trace.events.value.any { it.id == stage && it.state == ActivityState.RUNNING })
        trace.finish(stage)
        assertTrue(trace.events.value.any { it.id == stage && it.state == ActivityState.SUCCEEDED })
    }

    @Test fun cancellationAndResetIgnoreLateCallbacks() {
        val trace = AgentActivity()
        val old = trace.begin(ActivityKind.SCENE_JSON, "Old attempt")
        trace.append(old, "partial")
        trace.cancelOpen()
        trace.finish(old)
        assertEquals(ActivityState.CANCELLED, trace.events.value.last().state)
        trace.clear()
        val fresh = trace.begin(ActivityKind.SCRIPT, "New attempt")
        assertNotEquals(old, fresh)
        trace.append(old, "late content")
        assertEquals("", trace.events.value.last().output)
    }

    @Test fun cancellableFallbackDoesNotSwallowCancellation() = runBlocking {
        var threw = false
        try { cancellableResult<Unit> { throw CancellationException("stop") } }
        catch (_: CancellationException) { threw = true }
        assertTrue(threw)
        assertTrue(cancellableResult<Unit> { throw IllegalStateException("ordinary failure") }.isFailure)
    }
}
