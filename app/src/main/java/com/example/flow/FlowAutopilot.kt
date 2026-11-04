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

/**
 * ROUND 15.1 — the honest result of trying to send a prompt.
 *
 * [clicked] = a send/arrow control was really pressed (or Enter / form-submit fired).
 * [confirmed] = the page then showed it took the prompt (composer cleared, "generating" appeared, or a new
 * image appeared). The autopilot retries while [confirmed] is false instead of silently waiting.
 */
data class FlowSubmitResult(
    val clicked: Boolean = false,
    val confirmed: Boolean = false,
    val how: String = "",
    val detail: String = ""
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
    /** ROUND 15.1: press Flow's own arrow/send button and report whether the prompt was really taken. */
    suspend fun submitSmart(prompt: String): FlowSubmitResult {
        val ok = submit(prompt)
        return FlowSubmitResult(clicked = ok, confirmed = false, how = if (ok) "legacy" else "none")
    }

    /**
     * ROUND 15.3 — tap the arrow again on an already-typed prompt (never re-types it).
     * The real driver fires a genuine MotionEvent at the arrow's own coordinates.
     */
    suspend fun pressSend(): FlowSubmitResult =
        FlowSubmitResult(clicked = false, confirmed = false, how = "nosupport")

    /**
     * ROUND 15.4 — create a brand-new Flow project (so pictures from older projects can never be picked up).
     * Returns true only when a fresh project with a usable composer is on screen.
     */
    suspend fun newProject(): Boolean = false

    /** ROUND 15.3 — pick Flow's own aspect-ratio control before generating. */
    suspend fun setAspect(aspect: String): Boolean = false

    /** ROUND 15.3 — switch Flow to one image per run ("1 image at a time"). */
    suspend fun setOneImage(): Boolean = false
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
    /** How many times one prompt is re-sent while the page has not confirmed the click. */
    private val MAX_SUBMIT_TRIES = 3

    /** ROUND 15.3 — Flow's own output settings are applied once per run (aspect + 1 image). */
    private var settingsDone = false

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
        // ---- 0. ROUND 15.4: a run with nothing finished yet ALWAYS starts in a brand-new Flow project ----
        // The owner hit exactly this: a picture was generated but the app picked up an old project's picture.
        // Working inside a fresh project removes those pictures from the page entirely, and the capture
        // layer additionally refuses anything that was not armed for the running prompt.
        if (current.slots.none { it.done }) {
            state(FlowPhase.GENERATING, 0, total, "Naya Flow project ban raha hai — purane project ki images use nahi hongi…")
            val made = runCatching { driver.newProject() }.getOrDefault(false)
            state(FlowPhase.GENERATING, 0, total,
                if (made) "Naya project ready ✓ — is project ke andar hi ek-ek image banegi."
                else "Naya project confirm nahi hua — phir bhi sirf armed prompt ke baad aayi image lega.")
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
            // ---- 1b. ROUND 15.3: Flow ke apne output settings (aspect + 1 image per run) ----
            if (!settingsDone) {
                settingsDone = true
                val aspect = current.aspect.ifBlank { "9:16" }
                state(FlowPhase.GENERATING, done, total,
                    "Flow settings set ho rahi hain (" + aspect + " · 1 image per run)…")
                val okAspect = runCatching { driver.setAspect(aspect) }.getOrDefault(false)
                val okOne = runCatching { driver.setOneImage() }.getOrDefault(false)
                state(FlowPhase.GENERATING, done, total,
                    "Flow settings: " + aspect + (if (okAspect) " ✓" else " (manual)") +
                        " · 1 image" + (if (okOne) " ✓" else " (manual)"))
            }
            // ---- 2. run the prompt ----
            val running = existing.copy(status = FlowSlotStatus.RUNNING, tries = existing.tries + 1, error = "")
            current = current.withSlot(running)
            onPlan(current)
            driver.arm(slot.index)
            // ---- ROUND 15.1: press the arrow, verify the page took it, retry while it has not ----
            var send = runCatching { driver.submitSmart(running.prompt) }.getOrElse { FlowSubmitResult() }
            var attempt = 1
            while (!send.confirmed && attempt < MAX_SUBMIT_TRIES) {
                currentCoroutineContext().ensureActive()
                if (stopped) { state(FlowPhase.STOPPED, done, total, "Stopped by you."); return current }
                attempt++
                state(FlowPhase.GENERATING, done, total,
                    "Arrow confirm nahi hua — dobara tap kar raha hoon (" + (attempt - 1) + "/" + (MAX_SUBMIT_TRIES - 1) + ")")
                delay(1500L)
                // Re-arm so the retry cannot pick up the previous attempt's frame. The prompt is NOT re-typed.
                runCatching { driver.arm(slot.index) }
                send = runCatching { driver.pressSend() }.getOrElse { FlowSubmitResult() }
            }
            val submitted = send.clicked
            state(FlowPhase.GENERATING, done, total,
                "Prompt " + (done + 1) + "/" + total + " generate ho raha hai" +
                    if (submitted) " (" + send.how.ifBlank { "arrow" } + ")" else " (Flow ka send button nahi mila — Enter/form fallback bhi fail hua)")
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
            // ---- 4b. ROUND 15.2: real background removal for cut-out layers ----
            // Key visuals / stickers get a transparent PNG made locally. If the picture has no flat
            // background, FlowCutout refuses and the original is used unchanged (no silent garbage).
            var cutPath = ""
            var borderHex = ""
            val wantsCut = running.role == FlowRoles.KEYFRAME || running.role == FlowRoles.STICKER
            if (wantsCut) {
                state(FlowPhase.VERIFYING, done, total, "Background remove ho raha hai (local cut-out)…")
                val cutResult = runCatching {
                    val srcFile = File(record.path)
                    val dstOut = File(srcFile.parentFile ?: context.filesDir, srcFile.nameWithoutExtension + "_cut.png")
                    FlowCutout.process(srcFile, dstOut)
                }.getOrNull()
                if (cutResult != null && cutResult.ok) {
                    cutPath = cutResult.path
                    borderHex = cutResult.borderHex
                }
            }
            // ---- 5. verify ----
            state(FlowPhase.VERIFYING, done, total, "Verify: crop, glow, background, sharpness…")
            val local = FlowImageProbe.analyse(File(record.path), current.aspect, running.role)
            val ai = if (local != null && FlowSettings.aiVerify(context)) {
                runCatching { aiVerify(running, local, record.path) }.getOrElse { "" }
            } else ""
            val review = local?.copy(aiVerdict = ai)
            val status = if (review != null && review.ok) FlowSlotStatus.VERIFIED else FlowSlotStatus.DOWNLOADED
            // ROUND 15.4 — the picture's LAYOUT is planned now, before it enters the timeline: role, exact
            // rectangle inside the safe area, fit, depth and motion. The assembly uses these very numbers.
            val measured = running.copy(
                status = status, imagePath = record.path, imageUrl = record.url,
                imageHash = record.hash, review = review, cutPath = cutPath, borderHex = borderHex
            )
            val planned = FlowLayoutPlanner.plan(measured, running.sceneMs, current.aspect)
            current = current.withSlot(measured.copy(
                layout = planned.toJson(),
                error = when {
                    review == null -> "Picture decode nahi hui"
                    !review.ok -> "Quality gate: " + review.verdict
                    else -> ""
                }
            ))
            FlowLibrary.update(context, record.id) { it.copy(sceneId = running.sceneId, promptIndex = running.index, role = running.role) }
            onPlan(current)
            done++
            val cutNote = if (cutPath.isNotBlank()) " · background hataya" else ""
            state(FlowPhase.GENERATING, done, total,
                "Verified: " + (review?.verdict ?: "picture saved") + cutNote +
                    " · jagah: " + FlowLayoutPlanner.describe(planned), true, record.path)
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
