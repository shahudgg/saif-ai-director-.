package com.example.studio.director

import com.example.studio.composer.LocalBeatAnalyzer
import com.example.ui.components.TelegramAssetItem
import org.json.JSONObject

/** A library asset the AI editor may choose, under a short key (A1, A2...) so the model cannot mis-copy a long id. */
data class CatalogEntry(val key: String, val item: TelegramAssetItem)

/** One choice of the AI editor: [key] of a library asset for a scene (visual) or a sound at [atMs] inside it. */
data class AssetPick(val sceneId: String, val key: String, val reason: String, val atMs: Long = 0L, val durMs: Long = 0L)

/** Something the video needs that the library does not have: asked for on Telegram. */
data class AssetNeed(val sceneId: String, val type: String, val description: String)

data class CuratorPlan(val picks: List<AssetPick>, val sfx: List<AssetPick>, val needs: List<AssetNeed>)

/**
 * The asset editor. Instead of matching a few keywords, the model reads the real library listing (title, subject,
 * keywords) together with what every scene says, and chooses the exact b-roll / meme / picture / sound effect that fits.
 * Everything here is pure (prompt building and tolerant parsing), so it is unit-tested without a device.
 */
object AssetCurator {

    /** Assets that can really be downloaded and used: real file id, a media type, within Telegram's 20 MB bot limit. */
    fun usable(items: List<TelegramAssetItem>, userId: String = "default"): List<TelegramAssetItem> = items.filter { a ->
        val own = a.userId.isBlank() || a.userId.equals("all", true) || a.userId.equals("default", true) || a.userId.equals(userId, true)
        own && a.fileId.isNotBlank() && !a.fileId.startsWith("FILE_ID_") && a.type in setOf("image", "video", "audio") &&
            (a.fileSize <= 0L || a.fileSize <= 20L * 1024 * 1024)
    }

    private fun words(s: String): Set<String> = LocalBeatAnalyzer.words(s).map { it.lowercase() }.filter { it.length > 2 }.toSet()

    /** At most [max] entries: everything when the library is small, otherwise the best lexical matches plus sound effects. */
    fun shortlist(items: List<TelegramAssetItem>, scenes: List<Scene>, max: Int = 170): List<CatalogEntry> {
        val chosen = if (items.size <= max) items else {
            val want = words(scenes.joinToString(" ") { it.narration + " " + it.visualObjective })
            fun score(a: TelegramAssetItem): Int {
                val bag = words("${a.title} ${a.subject} ${a.searchKeywords.joinToString(" ")} ${a.tags.joinToString(" ")} ${a.category}")
                return bag.count { it in want }
            }
            val sfx = items.filter { it.type == "audio" }.sortedByDescending { score(it) }.take(max / 4)
            val visual = items.filter { it.type != "audio" }.sortedByDescending { score(it) }.take(max - sfx.size)
            sfx + visual
        }
        return chosen.mapIndexed { i, a -> CatalogEntry("A${i + 1}", a) }
    }

    fun catalogLine(e: CatalogEntry): String {
        val a = e.item
        val kw = (a.searchKeywords + a.tags.map { it.removePrefix("#") }).distinct().take(10).joinToString(", ")
        return "${e.key} | ${a.type} | ${a.category} | ${a.title.take(60)} | ${a.subject.take(70)} | $kw"
    }

    fun prompt(b: DirectorBrief, scenes: List<Scene>, entries: List<CatalogEntry>): String = buildString {
        appendLine("You are the asset editor of a video called \"${b.prompt.take(80)}\". Pick assets from the user's Telegram library so they EXACTLY fit what each scene says and shows.")
        appendLine("LIBRARY (key | type | category | title | subject | keywords):")
        entries.forEach { appendLine(catalogLine(it)) }
        appendLine()
        appendLine("SCENES (id | duration ms | narration | what the graphic shows):")
        scenes.forEach { appendLine("${it.id} | ${it.durationMs} | ${it.narration.take(150)} | ${it.visualObjective.take(60)}") }
        appendLine()
        appendLine("RULES:")
        appendLine("- Only use keys that exist in the LIBRARY. Never invent a key.")
        appendLine("- A visual (type video or image) may be used only when its title, subject or keywords clearly match the subject, emotion or joke of that scene. A wrong or loosely related asset is worse than none: then leave the scene without a visual.")
        appendLine("- At most one visual per scene and each visual only once in the whole video. Use real footage (b_roll) for real-world subjects, memes/reactions for funny or shocking beats, png/jpg pictures for objects, logos, people and products. Aim for a visual in about 40-60% of the scenes where the library has a genuinely good match.")
        appendLine("- Sound effects (type audio, category sfx): add them where a sound really belongs (transition whoosh, impact when a big number lands, pop for list items, ding for a correct answer, laugh/reaction on a joke). atMs is the moment inside the scene (0 .. duration), durationMs at most 1500. At most 2 per scene and not in every scene. Do not pick music (bgm) here.")
        appendLine("- For every scene that needs an asset the library does NOT have, add it to \"missing\" with type (video, image or sfx) and a precise description of what to upload (subject, action, mood, e.g. \"slow motion red stock chart falling, 5-8 s, portrait\").")
        appendLine("""Reply with ONLY this JSON: {"picks":[{"scene":"s3","asset":"A12","reason":"why it fits"}],"sfx":[{"scene":"s3","asset":"A40","atMs":200,"durationMs":900,"reason":"why"}],"missing":[{"scene":"s5","type":"video","need":"precise description"}]}""")
    }

    /** Tolerant parser: unknown keys/scenes are dropped, duplicates removed, one visual per scene, each asset once. */
    fun parse(raw: String, entries: List<CatalogEntry>, scenes: List<Scene>): CuratorPlan {
        val byKey = entries.associateBy { it.key.uppercase() }
        val sceneIds = scenes.map { it.id }.toSet()
        val dur = scenes.associate { it.id to it.durationMs }
        val o = runCatching { JSONObject(SceneJson.extractJson(raw)) }.getOrNull() ?: return CuratorPlan(emptyList(), emptyList(), emptyList())
        val usedVisual = HashSet<String>(); val sceneHasVisual = HashSet<String>()
        val picks = ArrayList<AssetPick>(); val sfx = ArrayList<AssetPick>(); val needs = ArrayList<AssetNeed>()
        o.optJSONArray("picks")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val sid = x.optString("scene"); val key = x.optString("asset").trim().uppercase()
                val e = byKey[key] ?: continue
                if (sid !in sceneIds || e.item.type == "audio" || key in usedVisual || sid in sceneHasVisual) continue
                usedVisual += key; sceneHasVisual += sid
                picks += AssetPick(sid, key, x.optString("reason").take(120))
            }
        }
        o.optJSONArray("sfx")?.let { a ->
            val perScene = HashMap<String, Int>()
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val sid = x.optString("scene"); val key = x.optString("asset").trim().uppercase()
                val e = byKey[key] ?: continue
                if (sid !in sceneIds || e.item.type != "audio") continue
                val n = perScene.getOrDefault(sid, 0); if (n >= 2) continue
                perScene[sid] = n + 1
                val sceneMs = dur[sid] ?: 3000L
                val at = x.optLong("atMs", 0L).coerceIn(0L, (sceneMs - 150L).coerceAtLeast(0L))
                val d = x.optLong("durationMs", 900L).coerceIn(120L, 1500L)
                sfx += AssetPick(sid, key, x.optString("reason").take(100), at, d)
            }
        }
        o.optJSONArray("missing")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val sid = x.optString("scene"); val need = x.optString("need").trim()
                if (sid in sceneIds && need.length > 5) needs += AssetNeed(sid, x.optString("type", "video").lowercase().ifBlank { "video" }, need.take(160))
            }
        }
        return CuratorPlan(picks, sfx, needs.distinctBy { it.description.lowercase() })
    }

    /** Readable lines for the Telegram message, e.g. "video: slow motion red chart (scene 4)". */
    fun needLines(needs: List<AssetNeed>, scenes: List<Scene>): List<String> =
        needs.map { n -> "${n.type}: ${n.description} (scene ${scenes.indexOfFirst { it.id == n.sceneId } + 1})" }.distinct()
}
