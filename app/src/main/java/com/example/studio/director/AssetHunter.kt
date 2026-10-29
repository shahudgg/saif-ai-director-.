package com.example.studio.director

import com.example.ui.components.TelegramAssetItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * SEQUENTIAL ASSET HUNTER
 * ----------------------
 * The old asset editor asked the model for the WHOLE asset list in one call. The library then had to be
 * dumped into the prompt and the model answered once for every scene, so a wrong pick was never corrected.
 *
 * This hunter works like a real editor, ONE ASSET AT A TIME:
 *
 *   1. look at the situation of one place on the timeline (a scene, or a moment inside a scene),
 *   2. ask the model for exactly ONE need: what must be shown/heard, in which FILE FORMAT (png, jpg, mp4, mp3...),
 *      and with only TWO OR THREE search keywords,
 *   3. search the Telegram library with those keywords, filtered by that format,
 *   4. download the best hit and LOOK AT IT (technical probe + vision check against the exact moment),
 *   5. if it is the wrong asset, ask the model to change the keywords a little and try again (max 3 tries),
 *   6. when verification passes, place the asset at the right spot on the timeline with full control
 *      (position, size, fit, keyframes, glow, blend, grade) — see [AssetPlacement],
 *   7. then move on and request the NEXT asset for the next situation.
 *
 * Everything in this file is pure (prompt building, parsing, ranking), so it is unit-tested without a device.
 * The network work lives in `DirectorServices.huntAssets`.
 */

/** File extensions the hunter can ask for, grouped by the media kind they produce. */
object AssetFormats {
    val IMAGE = listOf("png", "jpg", "jpeg", "webp")
    val VIDEO = listOf("mp4", "mov", "webm", "mkv")
    val AUDIO = listOf("mp3", "wav", "ogg", "m4a", "aac")

    val ALL = IMAGE + VIDEO + AUDIO

    fun kindOf(ext: String): String = when (ext.lowercase().removePrefix(".")) {
        in IMAGE -> "image"
        in VIDEO -> "video"
        in AUDIO -> "audio"
        else -> "unknown"
    }

    /** Telegram `type` field used by the catalog for this extension. */
    fun telegramType(ext: String): String = when (kindOf(ext)) {
        "image" -> "image"
        "video" -> "video"
        "audio" -> "audio"
        else -> "all"
    }

    /** True when a catalog item can satisfy a request for this extension (same extension, or same kind as a fallback). */
    fun matches(itemExt: String, wantExt: String, allowKindFallback: Boolean = true): Boolean {
        val a = itemExt.lowercase().removePrefix(".")
        val b = wantExt.lowercase().removePrefix(".")
        if (a == b) return true
        if (a == "jpeg" && b == "jpg") return true
        if (a == "jpg" && b == "jpeg") return true
        if (!allowKindFallback) return false
        return kindOf(a) == kindOf(b) && kindOf(a) != "unknown"
    }

    fun normalize(ext: String): String {
        val e = ext.lowercase().removePrefix(".").trim()
        return when (e) {
            "jpeg" -> "jpg"
            "", "unknown" -> "png"
            else -> if (e in ALL) e else "png"
        }
    }
}

/**
 * ONE place on the timeline that may need an asset. Slots are built from the real manifest, in timeline order,
 * so the hunter always knows the exact situation (scene narration + what the graphics already show + the moment).
 */
data class HuntSlot(
    val slotId: String,
    val sceneId: String,
    /** "visual" = a picture/clip for the scene, "sfx" = a sound at [atMs] inside the scene. */
    val purpose: String,
    val sceneIndex: Int,
    val sceneCount: Int,
    val startMs: Long,
    val durationMs: Long,
    val narration: String,
    val visualObjective: String,
    /** What the generated graphics of this scene already show, so the asset does not repeat them. */
    val graphicsSummary: String,
    /** Moment inside the scene for a sound (0 for a visual). */
    val atMs: Long = 0L,
    /** Sound kind when [purpose] == "sfx" (whoosh, hit, pop, ding...). */
    val cueKind: String = "",
    /** Aspect of the project, so the model can ask for portrait or landscape media. */
    val aspect: String = "9:16"
) {
    val isSfx: Boolean get() = purpose == "sfx"
}

/** ONE need the model asked for, for ONE slot. */
data class HuntNeed(
    val slotId: String,
    val want: Boolean,
    val ext: String,                  // png | jpg | mp4 | mp3 | ...
    val what: String,                 // what the asset must show (used by the vision check)
    val keywords: List<String>,       // 2-3 search keywords ONLY
    val atMs: Long = 0L,
    val durMs: Long = 0L,
    val placement: AssetPlacement? = null,
    val reason: String = ""
) {
    val kind: String get() = AssetFormats.kindOf(ext)
}

/** One attempt of the search-verify loop, kept so the model can be told what already failed and why. */
data class HuntAttempt(
    val keywords: List<String>,
    val ext: String,
    val title: String = "",
    val outcome: String,             // "no match in library" | "download failed" | rejection reason | "accepted"
    val sawTitles: List<String> = emptyList()
)

/** The result for one slot. */
sealed class HuntOutcome {
    data class Found(val asset: ResolvedAsset, val need: HuntNeed, val attempts: List<HuntAttempt>) : HuntOutcome()
    /** Nothing suitable in the library: the description is sent to Telegram as an upload request. */
    data class Missing(val slotId: String, val ext: String, val what: String, val attempts: List<HuntAttempt>) : HuntOutcome()
    /** The model decided this place needs no asset at all. */
    data class Skipped(val slotId: String, val why: String) : HuntOutcome()
}

/** Progress line for the chat, e.g. "Scene 3/8 · searching Telegram for 'cash money png'". */
data class HuntStep(val slotIndex: Int, val slotCount: Int, val text: String, val fraction: Float)

/**
 * Pure search over an already-synced library. One sync, then every slot searches locally — fast and offline.
 */
object AssetLibrary {

    private fun norm(s: String) = s.lowercase().trim()

    private fun bagOf(a: TelegramAssetItem): String =
        norm("${a.title} ${a.subject} ${a.category} ${a.searchKeywords.joinToString(" ")} ${a.tags.joinToString(" ")}")

    /** Assets this build can really download and use. */
    fun usable(items: List<TelegramAssetItem>, userId: String = "default"): List<TelegramAssetItem> = items.filter { a ->
        val own = a.userId.isBlank() || a.userId.equals("all", true) ||
            a.userId.equals("default", true) || a.userId.equals(userId, true)
        own && a.fileId.isNotBlank() && !a.fileId.startsWith("FILE_ID_") &&
            a.type in setOf("image", "video", "audio") &&
            (a.fileSize <= 0L || a.fileSize <= 20L * 1024 * 1024)
    }

    /**
     * Ranks the library for ONE need: 2-3 keywords + a file format.
     *
     * Scoring is deliberately strict — an asset must hit at least one keyword in its title/subject/keywords/tags,
     * and hitting the exact format is worth a lot, because the editor asked for a PNG on purpose (a transparent
     * sticker) and an MP4 in its place would be wrong.
     *
     * @param avoidIds titles/ids already used in this video, so one asset is never used twice.
     */
    fun search(
        items: List<TelegramAssetItem>, keywords: List<String>, ext: String,
        limit: Int = 4, avoidIds: Set<String> = emptySet(), strictFormat: Boolean = true
    ): List<TelegramAssetItem> {
        val wantKind = AssetFormats.kindOf(ext)
        val wantExt = AssetFormats.normalize(ext)
        val q = keywords.map { norm(it) }.filter { it.length > 1 }.distinct()
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<TelegramAssetItem, Int>>()
        for (a in items) {
            val id = a.assetId.ifBlank { a.title }
            if (id in avoidIds) continue
            if (wantKind != "unknown" && !a.type.equals(wantKind, true) && !a.category.equals(wantKind, true)) continue
            val formatOk = AssetFormats.matches(a.extension, wantExt, allowKindFallback = !strictFormat)
            if (strictFormat && !formatOk) continue
            val bag = bagOf(a)
            val title = norm(a.title); val subject = norm(a.subject)
            var sc = 0
            for (k in q) {
                if (title.contains(k)) sc += 6
                if (subject.contains(k)) sc += 5
                if (a.searchKeywords.any { norm(it).contains(k) }) sc += 4
                if (a.tags.any { norm(it).contains(k) }) sc += 3
                if (norm(a.category).contains(k)) sc += 2
                if (sc == 0 && bag.contains(k)) sc += 1
            }
            if (sc <= 0) continue
            // All keywords present beats one keyword repeated in many fields.
            val hits = q.count { bag.contains(it) }
            sc += hits * 3
            if (formatOk) sc += 4
            // Sound effects: category match matters more than the file name.
            if (wantKind == "audio" && (norm(a.category).contains("sfx") || norm(a.category).contains("effect"))) sc += 3
            scored += a to sc
        }
        val exact = scored.filter { AssetFormats.matches(it.first.extension, wantExt, allowKindFallback = false) }
        val pool = if (exact.size >= limit) exact else scored
        return pool.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /** Readable listing of a few items, shown to the model when its keywords found nothing. */
    fun sampleTitles(items: List<TelegramAssetItem>, ext: String, limit: Int = 14): List<String> {
        val wantKind = AssetFormats.kindOf(ext)
        return items.filter { wantKind == "unknown" || it.type.equals(wantKind, true) || it.category.equals(wantKind, true) }
            .take(limit).map { "${it.title.take(40)}.${it.extension}" }
    }

    fun idOf(a: TelegramAssetItem): String = a.assetId.ifBlank { a.title }
}

/** Prompts + tolerant parsers for the one-at-a-time hunt. */
object AssetHunter {

    /** The largest number of search-verify tries per asset before the slot is reported as missing. */
    const val MAX_TRIES = 3

    private fun situation(sl: HuntSlot): String = buildString {
        append("Scene ${sl.sceneIndex + 1} of ${sl.sceneCount} (")
        append(sl.durationMs / 1000).append(" s, aspect ").append(sl.aspect).append(")")
        if (sl.isSfx) {
            append(" — SOUND at ").append(sl.atMs).append(" ms inside the scene (cue: ").append(sl.cueKind).append(")")
        }
        append("\n  narration: \"").append(sl.narration.take(220)).append('"')
        if (sl.graphicsSummary.isNotBlank()) append("\n  already on screen (generated graphics): ").append(sl.graphicsSummary.take(160))
        if (sl.visualObjective.isNotBlank()) append("\n  this beat is about: ").append(sl.visualObjective.take(120))
    }

    /**
     * Asks for exactly ONE asset for ONE place on the timeline. The model must answer with a file format and
     * only two or three keywords — never a list of everything the video needs.
     */
    fun needPrompt(videoTitle: String, slot: HuntSlot, librarySize: Int, sample: List<String>): String = buildString {
        appendLine("You are the asset editor of the SAIF AI video studio. You are filling the timeline ONE ASSET AT A TIME.")
        appendLine("Video: \"${videoTitle.take(70)}\". The Telegram library has $librarySize usable file(s).")
        appendLine()
        appendLine("THE PLACE YOU ARE FILLING NOW:")
        appendLine(situation(slot))
        appendLine()
        if (slot.isSfx) {
            appendLine("Decide whether this moment needs a SOUND EFFECT from the library, and which file format it must be (mp3, wav or ogg).")
            appendLine("A sound is worth adding only when it supports the beat (whoosh on a transition, hit/impact when a big number or claim lands, pop for a list item, ding for a correct answer, crowd/laugh on a joke, tick for a countdown, glitch on a twist).")
        } else {
            appendLine("Decide whether this scene needs a PICTURE or a CLIP from the library that makes the beat stronger than graphics alone.")
            appendLine("Ask for a picture (png/jpg/webp) for objects, logos, products, people, screenshots, memes and reactions.")
            appendLine("Ask for a clip (mp4/mov/webm) for real-world action, places, crowds, machines, money, weather, sport.")
            appendLine("A wrong or only-loosely-related asset is WORSE than none: if the library cannot have what this beat really shows, answer want=false.")
        }
        appendLine()
        appendLine("SOME FILE NAMES IN THE LIBRARY (for format only, not a menu to pick from): ${sample.joinToString("; ").ifBlank { "none" }}")
        appendLine()
        appendLine("KEYWORD RULES (this is the important part):")
        appendLine("- keywords: exactly 2 or 3 short search words, lowercase, no punctuation, no sentence.")
        appendLine("- they must be the SUBJECT of the file itself, not the topic of the video. For a rupee-note sticker: \"cash money png\". For a crowd cheer: \"crowd cheer\". For a whoosh: \"whoosh transition\".")
        appendLine("- never put the video title or abstract ideas (\"success\", \"motivation\") in the keywords.")
        appendLine("- ext: the file format you want (png, jpg, webp, mp4, mov, mp3, wav, ogg).")
        if (!slot.isSfx) {
            appendLine("- placement: where and how the asset sits in the frame, normalized 0..1 (x,y = centre):")
            appendLine("  role \"backdrop\" = full-frame behind the graphics (x 0.5, y 0.5, w 1, h 1, fit cover).")
            appendLine("  role \"sticker\" = a small element above the graphics (w 0.3..0.7) placed where it does not cover the labels.")
            appendLine("  Optional: glow 0..1, blend Normal|Screen|Multiply|Overlay|Add|Lighten|Darken, rotation, opacity, corner 0..0.5,")
            appendLine("  animIn fadeIn|zoomIn|popIn|slideLeft|slideRight|slideUp|slideDown|spinIn|blurIn|flipIn|none, animOut fadeOut|zoomOut|popOut|spinOut|none,")
            appendLine("  drift 0..1 (slow automatic zoom for a backdrop), and keyframes [{tMs,x,y,scale,rotation,opacity,easing}] for real motion (2..6 of them).")
        } else {
            appendLine("- atMs: the moment inside the scene when the sound starts; durationMs at most 1500.")
        }
        appendLine()
        appendLine("""Reply with ONLY this JSON: {"want":true,"ext":"png","what":"exactly what the file must show, one sentence","keywords":["cash","money"],""" +
            if (slot.isSfx) "\"atMs\":200,\"durationMs\":900,\"reason\":\"why this sound belongs here\"}"
            else "\"placement\":{\"role\":\"sticker\",\"x\":0.5,\"y\":0.34,\"w\":0.46,\"h\":0.3,\"fit\":\"contain\",\"animIn\":\"popIn\",\"glow\":0.35},\"reason\":\"why this asset belongs here\"}")
        appendLine("""If nothing is needed: {"want":false,"reason":"why not"}""")
    }

    /** Parses the one-need reply. Returns null when the reply is unusable (caller falls back to keywords). */
    fun parseNeed(raw: String, slot: HuntSlot): HuntNeed? {
        val o = runCatching { JSONObject(SceneJson.extractJson(raw)) }.getOrNull() ?: return null
        val want = o.optBoolean("want", false)
        if (!want) return HuntNeed(slot.slotId, false, "png", "", emptyList(), reason = o.optString("reason", "").take(120))
        val extRaw = o.optString("ext", "").ifBlank { o.optString("format", "") }
        val ext = AssetFormats.normalize(extRaw)
        val what = o.optString("what", "").ifBlank { o.optString("description", "") }.trim().take(200)
        val keywords = keywordsOf(o)
        if (keywords.isEmpty() || what.length < 4) return null
        val fallback = if (slot.isSfx) AssetPlacement.BACKDROP else AssetPlacements.defaultFor(AssetFormats.kindOf(ext), ext, transparent = ext == "png", aspect = slot.aspect)
        val placement = if (slot.isSfx) null else AssetPlacements.parse(o.optJSONObject("placement"), fallback)
        return HuntNeed(
            slotId = slot.slotId, want = true, ext = ext, what = what, keywords = keywords,
            atMs = if (slot.isSfx) o.optLong("atMs", slot.atMs).coerceIn(0L, (slot.durationMs - 120L).coerceAtLeast(0L)) else slot.atMs,
            durMs = if (slot.isSfx) o.optLong("durationMs", 900L).coerceIn(120L, 1500L) else 0L,
            placement = placement, reason = o.optString("reason", "").take(120)
        )
    }

    private fun keywordsOf(o: JSONObject): List<String> {
        val arr = o.optJSONArray("keywords") ?: o.optJSONArray("search") ?: o.optJSONArray("q")
        val out = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim().lowercase().replace(Regex("[^\\p{L}\\p{N} ]+"), " ").trim()
                s.split(' ').filter { it.length > 1 }.take(2).forEach { if (it !in out) out += it }
            }
        } else {
            o.optString("keywords", "").split(Regex("[,;|]+")).map { it.trim().lowercase() }.filter { it.length > 1 }
                .forEach { if (it !in out) out += it }
        }
        return out.take(3)
    }

    /**
     * After a failed try: the model sees WHY it failed and what the library actually contains, and must change
     * the keywords a little (not invent a new asset). This is the retry loop the owner asked for.
     */
    fun revisePrompt(slot: HuntSlot, need: HuntNeed, attempts: List<HuntAttempt>, sample: List<String>, librarySize: Int): String = buildString {
        appendLine("The asset you asked for was NOT accepted. Fix the SEARCH, one asset at a time.")
        appendLine("Needed: \"${need.what}\" as ${need.ext} for:")
        appendLine(situation(slot))
        appendLine()
        appendLine("Tries so far:")
        attempts.forEachIndexed { i, a ->
            appendLine(" ${i + 1}. keywords ${a.keywords.joinToString(" ")} (${a.ext})" + if (a.title.isNotBlank()) " -> '${a.title.take(40)}'" else "")
            appendLine("    result: ${a.outcome.take(180)}")
            if (a.sawTitles.isNotEmpty()) appendLine("    the library offered: ${a.sawTitles.take(6).joinToString("; ")}")
        }
        appendLine()
        appendLine("Library size: $librarySize file(s). Examples of what is really in it: ${sample.joinToString("; ").ifBlank { "none" }}")
        appendLine()
        appendLine("Now answer ONE of these two things:")
        appendLine("A) a SMALL change to the search — 2 or 3 different/broader keywords for the SAME asset, same format or a close one (png<->jpg, mp4<->mov). Use words that appear in the examples when they fit.")
        appendLine("B) give up on this place: want=false, and say in \"need\" exactly what the owner should upload to Telegram (subject, action, mood, format, portrait/landscape).")
        appendLine()
        appendLine("""Reply with ONLY JSON: {"want":true,"ext":"png","what":"...","keywords":["...","..."],"reason":"what you changed"}""")
        appendLine("""or {"want":false,"need":"precise upload request for Telegram","reason":"why the library cannot serve this beat"}""")
    }

    /** Parses a revise reply: either new keywords for the same need, or an upload request. */
    fun parseRevision(raw: String, slot: HuntSlot, need: HuntNeed): HuntNeed? {
        val o = runCatching { JSONObject(SceneJson.extractJson(raw)) }.getOrNull() ?: return null
        if (!o.optBoolean("want", true)) {
            val upload = o.optString("need", "").trim()
            return HuntNeed(slot.slotId, false, need.ext, upload.ifBlank { need.what }, emptyList(), reason = o.optString("reason", "").take(140))
        }
        val ext = AssetFormats.normalize(o.optString("ext", need.ext).ifBlank { need.ext })
        val what = o.optString("what", need.what).ifBlank { need.what }.take(200)
        val kws = keywordsOf(o)
        if (kws.isEmpty()) return null
        // Never re-ask with identical keywords: the caller treats that as a give-up.
        if (kws.toSet() == need.keywords.toSet() && ext == need.ext) return null
        return need.copy(ext = ext, what = what, keywords = kws, reason = o.optString("reason", need.reason).take(120))
    }

    /**
     * Deterministic keyword ladder used when the model cannot answer (no key, empty reply). It widens the search
     * step by step from the need's own words to the subject nouns of the scene, so the hunt still runs.
     */
    fun keywordLadder(need: HuntNeed, slot: HuntSlot): List<List<String>> {
        val out = ArrayList<List<String>>()
        val base = need.keywords.filter { it.isNotBlank() }
        if (base.isNotEmpty()) out += base.take(3)
        val fromWhat = need.what.split(Regex("[^\\p{L}\\p{N}]+")).map { it.lowercase() }
            .filter { it.length > 3 && it !in STOP }.distinct().take(4)
        if (fromWhat.size >= 2) out += fromWhat.take(3)
        if (fromWhat.isNotEmpty()) out += fromWhat.take(2)
        val fromScene = slot.narration.split(Regex("[^\\p{L}\\p{N}]+")).map { it.lowercase() }
            .filter { it.length > 3 && it !in STOP }.distinct().take(4)
        if (fromScene.size >= 2) out += fromScene.take(3)
        if (slot.isSfx && slot.cueKind.isNotBlank()) {
            out += listOf(slot.cueKind.lowercase(), "sfx")
            out += listOf(slot.cueKind.lowercase(), "sound")
        }
        if (fromScene.isNotEmpty()) out += fromScene.take(2)
        out += base.map { listOf(it) }
        return out.distinctBy { it.joinToString(" ") }.filter { it.isNotEmpty() }.take(MAX_TRIES + 2)
    }

    private val STOP = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "your", "you", "are", "was", "were", "have",
        "has", "will", "would", "can", "could", "should", "about", "when", "where", "what", "which", "there",
        "their", "they", "them", "than", "then", "also", "more", "most", "some", "such", "only", "just", "very",
        "really", "because", "after", "before", "while", "over", "under", "between", "during", "every", "each",
        "other", "another", "need", "want", "make", "made", "show", "shows", "shown", "scene", "video", "asset",
        "picture", "image", "photo", "clip", "footage", "sound", "effect", "background", "transparent", "sticker",
        "high", "quality", "best", "good", "new", "old", "big", "small", "one", "two", "three"
    )

    /** Human line for the chat while hunting, so the owner can follow the one-at-a-time search. */
    fun stepText(slot: HuntSlot, need: HuntNeed?, keywords: List<String>, tryNo: Int, total: Int): String {
        val where = if (slot.isSfx) "sound @${(slot.atMs / 1000.0)}s" else "picture/clip"
        val head = "Scene ${slot.sceneIndex + 1}/${slot.sceneCount} · $where"
        val kw = if (keywords.isNotEmpty()) " “${keywords.joinToString(" ")}”" else ""
        val fmt = need?.let { " ${it.ext}" } ?: ""
        val t = if (tryNo > 1) " · try $tryNo" else ""
        return "$head · searching Telegram for$kw$fmt$t ($total asset(s) so far)"
    }

    /**
     * Seed need used when no AI model can answer: the format comes from the slot's purpose and the keywords from the
     * concrete nouns of the narration, so the one-at-a-time hunt still runs (and still verifies what it downloads).
     */
    fun seedNeed(slot: HuntSlot): HuntNeed {
        val words = slot.narration.split(Regex("[^\\p{L}\\p{N}]+")).map { it.lowercase() }
            .filter { it.length > 3 && it !in STOP }.distinct().take(3)
        val ext = if (slot.isSfx) "mp3" else "mp4"
        val what = if (slot.isSfx) "${slot.cueKind.ifBlank { "sound" }} effect for this beat"
        else slot.visualObjective.ifBlank { slot.narration }.take(160)
        val kws = if (slot.isSfx && slot.cueKind.isNotBlank()) listOf(slot.cueKind.lowercase(), "sfx") else words
        val fallback = if (slot.isSfx) null else AssetPlacements.defaultFor(AssetFormats.kindOf(ext), ext, aspect = slot.aspect)
        return HuntNeed(slot.slotId, kws.isNotEmpty(), ext, what, kws.ifEmpty { listOf("video") },
            atMs = slot.atMs, durMs = if (slot.isSfx) 900L else 0L, placement = fallback, reason = "no AI model: keywords from the narration")
    }
}

/**
 * Builds the ordered list of slots to fill from a real manifest. Visual slots come first inside each scene
 * (the picture decides the look), then that scene's sound cues, so the timeline is filled in playing order.
 */
object HuntSlots {

    fun build(m: SceneManifest, aspect: String?, maxSfxPerScene: Int = 2): List<HuntSlot> {
        val out = ArrayList<HuntSlot>()
        val scenes = m.scenes.sortedBy { it.startMs }
        scenes.forEachIndexed { i, s ->
            val gfx = graphicsSummary(s)
            out += HuntSlot(
                slotId = "${s.id}:visual", sceneId = s.id, purpose = "visual", sceneIndex = i, sceneCount = scenes.size,
                startMs = s.startMs, durationMs = s.durationMs, narration = s.narration,
                visualObjective = s.visualObjective, graphicsSummary = gfx, aspect = aspect ?: "9:16"
            )
            s.cues.take(maxSfxPerScene).forEachIndexed { ci, c ->
                out += HuntSlot(
                    slotId = "${s.id}:sfx$ci", sceneId = s.id, purpose = "sfx", sceneIndex = i, sceneCount = scenes.size,
                    startMs = s.startMs, durationMs = s.durationMs, narration = s.narration,
                    visualObjective = s.visualObjective, graphicsSummary = gfx,
                    atMs = c.atMs.coerceIn(0L, (s.durationMs - 120L).coerceAtLeast(0L)), cueKind = c.kind,
                    aspect = aspect ?: "9:16"
                )
            }
        }
        return out
    }

    /** What the generated graphics already show, so the hunter does not fetch a picture of the same thing. */
    fun graphicsSummary(s: Scene): String {
        val g = s.graph ?: return s.visualObjective
        val texts = g.elements.filter { it.type == "text" && it.text.isNotBlank() }.map { it.text.trim() }.take(6)
        val kinds = g.elements.map { it.type }.distinct().filter { it != "text" }
        return buildString {
            if (texts.isNotEmpty()) append(texts.joinToString(" / ") { it.take(24) })
            if (kinds.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(kinds.joinToString(",")) }
        }
    }

    /** Slots that still need filling when a re-hunt is asked for (scenes that got no asset). */
    fun unfilled(slots: List<HuntSlot>, assets: List<ResolvedAsset>): List<HuntSlot> = slots.filter { sl ->
        assets.none { a -> a.sceneId == sl.sceneId && (if (sl.isSfx) a.kind == "audio" else a.kind != "audio") }
    }
}
