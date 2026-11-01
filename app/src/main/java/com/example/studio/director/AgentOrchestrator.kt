package com.example.studio.director

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Round 13 — the SAIF AGENT CREW (multi-agent, like the reference video's agent setup, but fully in-app).
 *
 * Named agents hand work to each other — PLANNER (brief+research), SCRIPT (narration), ASSET (Flow/AI
 * images), ASSEMBLER (plan + timeline), PLACER (Flow images onto scenes) — and a WATCHDOG supervises
 * every step: on failure it retries once automatically, then announces the fallback path it took, so a
 * stuck/glitched step never silently kills the video. Every handoff is logged to the shared
 * [AgentActivity] bus, so the user can watch the agents talk to each other live in the chat panel.
 */
enum class AgentRole { PLANNER, SCRIPT, ASSET, ASSEMBLER, PLACER, WATCHDOG }

class AgentOrchestrator(private val activity: AgentActivity?) {

    /**
     * Runs one agent step under the watchdog. Returns the step result; the watchdog only decides
     * retry/fallback and logs — it never fakes success.
     */
    suspend fun <T> supervised(
        role: AgentRole,
        title: String,
        detail: String = "",
        fallbackNote: String = "",
        block: suspend () -> Result<T>
    ): Result<T> {
        activity?.instant(ActivityKind.MODEL, "🤖 ${role.name} agent: $title", detail.take(180))
        var r = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure<T>(e)
        }
        if (r.isSuccess) {
            activity?.instant(ActivityKind.MODEL, "✅ ${role.name} agent done: $title")
            return r
        }
        // WATCHDOG: something glitched — retry once before giving up.
        activity?.instant(ActivityKind.MODEL, "🛟 WATCHDOG: ${role.name} step failed (${(r.exceptionOrNull()?.message ?: "unknown").take(120)}) — automatic retry",
            "retry 1/1")
        currentCoroutineContext().ensureActive()
        r = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure<T>(e)
        }
        if (r.isSuccess) {
            activity?.instant(ActivityKind.MODEL, "✅ WATCHDOG retry worked — ${role.name} agent recovered: $title")
            return r
        }
        activity?.instant(ActivityKind.MODEL, "🔄 WATCHDOG: ${role.name} fallback path le raha hai", fallbackNote.take(200))
        return r
    }
}
