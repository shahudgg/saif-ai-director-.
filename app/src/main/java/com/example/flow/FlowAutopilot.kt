package com.example.flow

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** What the browser reports about the page it is showing. */
data class FlowPageInfo(
    val url: String = "",
    val hasComposer: Boolean = false,
    val needsLogin: Boolean = false,
    val imageCount: Int = 0
)

/** One picture/video the page produced. [base64] is set for blob:/canvas results that only exist inside the page. */
data class FlowCaptured(
    val url: String,
    val mime: String = "image",
    val width: Int = 0,
    val height: Int = 0,
    val base64: String? = null,
    val promptIndex: Int = -1,
    val title: String = ""
)

/** Everything the autopilot needs from the WebView. Implemented by [com.example.flow.web.FlowWebSession]. */
interface FlowDriver {
    suspend fun info(): FlowPageInfo
    suspend fun arm(promptIndex: Int): Boolean
    suspend fun submit(prompt: String): Boolean
    suspend fun cookieHeader(): String
    /** Cookies for the exact media host (googleusercontent and flow.google.com differ). */
    suspend fun cookieFor(url: String): String = cookieHeader()
    suspend fun resolveBlob(url: String): String? = null
    /** True when a "new project / start" control was clicked (Flow needs a project before a composer exists). */
    suspend fun openProject(): Boolean = false
}

/**
 * Round 14 — FLOW AUTOPILOT.
 *
 * Runs the owner's exact sequence, always in this order:
 *   1. wait until Flow is usable (login can happen whenever — there is no "one-time" lock),
 *   2. for every planned prompt: arm the capture, type the prompt, press generate,
 *   3. wait for the new picture, download it with the session cookies,
 *   4. verify it (crop / glow / background / sharpness), record it against its scene,
 *   5. wait 8 seconds, then do the next prompt,
 *   6. after the last picture: plan transitions from the real pixels and finish.
 *
 * It is resumable: a slot already verified is skipped, and every step is persisted by the caller.
 */
class FlowAutopilot(
    private val context: Context,
    private val driver: FlowDriver,
    private val captures: ReceiveChannel<FlowCaptured>,
    plan: FlowPlan,
    private val onState: (FlowRunState) -> Unit,
    private val onPlan: (FlowPlan) -> Unit,
    private val aiVerify: suspend (FlowSlot, FlowReview, String) -> String = { _, _, _ -> "" },
    private val sceneDurationMs: (String) -> Long = { id ->
        plan.slots.firstOrNull { it.sceneId == id }?.sceneMs ?: 4000L
    },
    private val delayMs: Long = FlowSettings.delayMs(context),
    private val captureTimeoutMs: Long = 150_000L
) {
    @Volatile var stopped: Boolean = false
        private set

    private var current: FlowPlan = plan

    fun stop() { stopped = true }

    private fun state(phase: FlowPhase, index: Int, total: Int, message: String, loggedIn: Boolean = true, path: String = "") {
        onState(FlowRunState(phase, index, total, message, loggedIn, path))
    }

    suspend fun run(): FlowPlan {
        val slots = current.slots.sortedBy { it.index }
        val total = slots.size
        if (total == 0) {
            state(FlowPhase.FAILED, 0, 0, "No prompts in the plan.")
            return current
        }
        var done = 0
        for (slot in slots) {
            currentCoroutineContext().ensureActive()
            if (stopped) { state(FlowPhase.STOPPED, done, total, "Stopped by you."); return current }
            val existing = current.slot(slot.index) ?: slot
            if (existing.status == FlowSlotStatus.VERIFIED && existing.imagePath.isNotBlank() && File(existing.imagePath).exists()) {
                done++
                state(FlowPhase.GENERATING, done, total, "Already verified — skipped picture ${done}/${total}")
                continue
            }
            // ---- 1. wait for a usable page (login is allowed at any moment, never gated) ----
            var info = safeInfo()
            if (!info.hasComposer) {
                state(FlowPhase.WAITING_LOGIN, done, total,
                    "Flow login chahiye (ya create page kholo). Browser mein login karo — SAIF khud shuru kar dega.", false)
                val waitStart = System.currentTimeMillis()
                var lastNudge = 0L
                while (!info.hasComposer) {
                    currentCoroutineContext().ensureActive()
                    if (stopped) { state(FlowPhase.STOPPED, done, total, "Stopped by you."); return current }
                    // Every ~25 s, try to open a Flow project so a prompt box appears (dashboard pages have none).
                    if (System.currentTimeMillis() - lastNudge > 25_000L) {
                        lastNudge = System.currentTimeMillis()
                        runCatching { driver.openProject() }
                    }
                    if (System.currentTimeMillis() - waitStart > 20 * 60 * 1000L) {
                        state(FlowPhase.FAILED, done, total,
                            "Flow 20 minute tak ready nahi hua — login karke dobara Start dabao (kuch bhi delete nahi hua).", false)
                        return current
                    }
                    delay(2500L)
                    info = safeInfo()
                }
            }
            // ---- 2. run the prompt ----
            val running = existing.copy(status = FlowSlotStatus.RUNNING, tries = existing.tries + 1, error = "")
            current = current.withSlot(running)
            onPlan(current)
            driver.arm(slot.index)
            val submitted = driver.submit(running.prompt)
            state(FlowPhase.GENERATING, done, total,
                "Prompt ${done + 1}/$total generate ho raha hai" + if (submitted) "" else " (composer nahi mila — aap khud Generate dabao, SAIF picture pakad lega)")
            // ---- 3. wait for the picture ----
            var media = awaitCapture(slot.index)
            if (media == null && !submitted) {
                // Manual fallback: the page may need a moment after the owner taps generate.
                media = awaitCapture(slot.index, 90_000L)
            }
            if (media == null) {
                val failed = running.copy(status = FlowSlotStatus.FAILED,
                    error = "Flow ne is prompt ke liye picture nahi di (timeout). Prompt badal ke dobara try kar sakte ho.")
                current = current.withSlot(failed)
                onPlan(current)
                done++
                state(FlowPhase.GENERATING, done, total, "Picture nahi mili — agla prompt.", true)
                delay(3000L)
                continue
            }
            // ---- 4. download ----
            state(FlowPhase.DOWNLOADING, done, total, "Picture download ho rahi hai…")
            val record = saveCaptured(media, running)
            if (record == null) {
                val failed = running.copy(status = FlowSlotStatus.FAILED,
                    error = "Download fail: " + FlowDownloader.lastError.ifBlank { "file page se nahi mili" })
                current = current.withSlot(failed)
                onPlan(current)
                done++
                delay(2500L)
                continue
            }
            // ---- 5. verify ----
            state(FlowPhase.VERIFYING, done, total, "Verify: crop, glow, background, sharpness…")
            val local = FlowImageProbe.analyse(File(record.path), current.aspect, running.role)
            val ai = if (local != null && FlowSettings.aiVerify(context)) {
                runCatching { aiVerify(running, local, record.path) }.getOrElse { "" }
            } else ""
            val review = local?.copy(aiVerdict = ai)
            val status = if (review != null && review.ok) FlowSlotStatus.VERIFIED else FlowSlotStatus.DOWNLOADED
            current = current.withSlot(running.copy(
                status = status, imagePath = record.path, imageUrl = record.url,
                imageHash = record.hash, review = review,
                error = when {
                    review == null -> "Picture decode nahi hui"
                    !review.ok -> "Quality gate: " + review.verdict
                    else -> ""
                }
            ))
            FlowLibrary.update(context, record.id) { it.copy(sceneId = running.sceneId, promptIndex = running.index, role = running.role) }
            onPlan(current)
            done++
            state(FlowPhase.GENERATING, done, total, "Verified: " + (review?.verdict ?: "picture saved"), true, record.path)
            // ---- 6. the owner's 8 second gap between prompts ----
            if (done < total) delay(delayMs.coerceAtLeast(FlowSettings.MIN_DELAY_MS))
        }
        // ---- transitions from the real pixels ----
        state(FlowPhase.TRANSITIONS, done, total, "Downloads ke pixels se transitions plan ho rahe hain…")
        val ordered = current.slots.filter { it.review != null }.sortedBy { it.index }
        val durations = ordered.map { sceneDurationMs(it.sceneId).coerceAtLeast(1200L) }
        val transitions = FlowTransitions.plan(ordered, durations)
        current = current.withTransitions(transitions)
        onPlan(current)
        val okCount = current.slots.count { it.status == FlowSlotStatus.VERIFIED }
        state(FlowPhase.DONE, okCount, total, "$okCount/$total pictures ready · ${transitions.size} transitions planned")
        return current
    }

    private suspend fun safeInfo(): FlowPageInfo = try {
        driver.info()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FlowPageInfo()
    }

    private suspend fun awaitCapture(index: Int, timeoutMs: Long = captureTimeoutMs): FlowCaptured? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            if (stopped) return null
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
            val got = withTimeoutOrNull(remaining) { captures.receive() } ?: return null
            // A picture generated for a previous prompt is not this slot's picture.
            if (got.promptIndex >= 0 && got.promptIndex != index) continue
            if (got.width in 1..(FlowMediaRules.MIN_SIDE - 1) || got.height in 1..(FlowMediaRules.MIN_SIDE - 1)) continue
            return got
        }
        return null
    }

    private suspend fun saveCaptured(media: FlowCaptured, slot: FlowSlot): FlowRecord? {
        val b64 = media.base64
        if (!b64.isNullOrBlank()) {
            val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return FlowDownloader.saveBytes(
                    context, bytes, slot.index, slot.sceneId, slot.role,
                    "scene_${slot.index + 1}_${slot.role}", media.url,
                    if (media.mime == "video") "video" else "image"
                )
            }
        }
        val cookie = runCatching { driver.cookieFor(media.url) }.getOrElse { "" }
        if (media.url.startsWith("blob:")) {
            FlowDownloader.noteError("Blob picture could not be read from the page")
            return null
        }
        val direct = FlowDownloader.download(
            context, media.url, cookie, slot.index, slot.sceneId, slot.role,
            "scene_${slot.index + 1}_${slot.role}"
        )
        if (direct != null) return direct
        // Round 15 FALLBACK: signed Flow URLs and hotlink protection reject a bare HTTP client, so the
        // page itself is asked to fetch the picture (credentials included) and stream it back as base64.
        val grabbed = runCatching { driver.resolveBlob(media.url) }.getOrNull()
        if (!grabbed.isNullOrBlank()) {
            val bytes = runCatching { android.util.Base64.decode(grabbed, android.util.Base64.DEFAULT) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return FlowDownloader.saveBytes(
                    context, bytes, slot.index, slot.sceneId, slot.role,
                    "scene_${slot.index + 1}_${slot.role}", media.url, "image", strict = false
                )
            }
        }
        return null
    }
}
