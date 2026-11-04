package com.example.flow

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Persistent Flow library + plan storage.
 *
 * The library is the ONLY way a Flow picture can reach a video, and every record keeps its real path,
 * pixel size, content hash and the prompt index it was generated from. That is what lets the pipeline
 * (a) skip the owner's Google profile photo, (b) never import the same picture twice, and
 * (c) know exactly which scene a picture belongs to.
 */
object FlowLibrary {
    private const val FILE = "flow_library.json"
    private const val LEGACY_FILE = "flow_assets.json"
    private const val MAX_RECORDS = 500

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun records(ctx: Context): List<FlowRecord> = runCatching {
        val f = file(ctx)
        if (!f.exists() || f.length() == 0L) return migrateLegacy(ctx)
        val arr = JSONArray(f.readText())
        val out = ArrayList<FlowRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val r = FlowJson.recordFromJson(o)
            if (r.path.isNotBlank() && File(r.path).exists()) out.add(r)
        }
        out.sortedBy { it.createdAt }
    }.getOrElse { emptyList() }

    /** Old builds stored a Telegram-shaped list with `local:<path>` file ids. Import those once. */
    private fun migrateLegacy(ctx: Context): List<FlowRecord> {
        val legacy = File(ctx.filesDir, LEGACY_FILE)
        if (!legacy.exists()) return emptyList()
        val out = ArrayList<FlowRecord>()
        runCatching {
            val arr = JSONArray(legacy.readText())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("fileId")
                if (!id.startsWith("local:")) continue
                val path = id.removePrefix("local:")
                if (!File(path).exists()) continue
                out.add(FlowRecord(
                    id = o.optString("assetId").ifBlank { "FLOW_" + i },
                    title = o.optString("title"), path = path, kind = o.optString("type", "image"),
                    ext = o.optString("ext", "png"), bytes = o.optLong("size"),
                    createdAt = o.optLong("messageId", 900_000_000L + i) - 900_000_000L
                ))
            }
        }
        if (out.isNotEmpty()) write(ctx, out)
        return out
    }

    @Synchronized
    private fun write(ctx: Context, list: List<FlowRecord>) {
        runCatching {
            val arr = JSONArray()
            list.takeLast(MAX_RECORDS).forEach { arr.put(FlowJson.recordToJson(it)) }
            file(ctx).writeText(arr.toString())
        }
    }

    fun count(ctx: Context): Int = records(ctx).size

    fun images(ctx: Context): List<FlowRecord> = records(ctx).filter { it.kind == "image" }

    fun byHash(ctx: Context, hash: String): FlowRecord? =
        if (hash.isBlank()) null else records(ctx).firstOrNull { it.hash == hash }

    fun byPath(ctx: Context, path: String): FlowRecord? = records(ctx).firstOrNull { it.path == path }

    fun byScene(ctx: Context, sceneId: String): List<FlowRecord> = records(ctx).filter { it.sceneId == sceneId }

    @Synchronized
    fun add(ctx: Context, record: FlowRecord): FlowRecord {
        val list = records(ctx).toMutableList()
        val existingIndex = list.indexOfFirst {
            (record.hash.isNotBlank() && it.hash == record.hash) || it.path == record.path
        }
        val stored = if (existingIndex >= 0) record.copy(id = list[existingIndex].id) else record
        if (existingIndex >= 0) list[existingIndex] = stored else list.add(stored)
        write(ctx, list)
        return stored
    }

    @Synchronized
    fun update(ctx: Context, id: String, change: (FlowRecord) -> FlowRecord) {
        val list = records(ctx).map { if (it.id == id) change(it) else it }
        write(ctx, list)
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        val target = records(ctx).firstOrNull { it.id == id }
        write(ctx, records(ctx).filterNot { it.id == id })
        target?.let { r -> if (r.path.startsWith(ctx.filesDir.absolutePath)) runCatching { File(r.path).delete() } }
    }

    @Synchronized
    fun clear(ctx: Context) { write(ctx, emptyList()) }

    /** Newest-first list for the tools screen. */
    fun recent(ctx: Context, n: Int = 60): List<FlowRecord> = records(ctx).sortedByDescending { it.createdAt }.take(n)

    /** SHA-256 of a file, used for de-duplication (same picture, different CDN URL). */
    fun hashOf(file: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = ins.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        md.digest().joinToString("") { b -> "%02x".format(b) }
    }.getOrElse { "" }

    fun hashOf(bytes: ByteArray): String = runCatching {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> "%02x".format(b) }
    }.getOrElse { "" }
}

/** One plan per project, so the browser can be closed and reopened without losing the prompt queue. */
object FlowPlanStore {
    private const val PREFIX = "flow_plan_"

    private fun file(ctx: Context, projectId: String) =
        File(ctx.filesDir, "$PREFIX${projectId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}.json")

    @Synchronized
    fun save(ctx: Context, plan: FlowPlan) {
        runCatching { file(ctx, plan.projectId).writeText(FlowJson.planToJson(plan).toString()) }
    }

    @Synchronized
    fun load(ctx: Context, projectId: String): FlowPlan? = runCatching {
        val f = file(ctx, projectId)
        if (!f.exists()) null else FlowJson.planFromJson(f.readText())
    }.getOrNull()

    @Synchronized
    fun latest(ctx: Context): FlowPlan? = runCatching {
        val f = ctx.filesDir.listFiles { x -> x.isFile && x.name.startsWith(PREFIX) }
            ?.maxByOrNull { it.lastModified() } ?: return null
        FlowJson.planFromJson(f.readText())
    }.getOrNull()

    fun clear(ctx: Context, projectId: String) { runCatching { file(ctx, projectId).delete() } }
}

/**
 * Browser/autopilot settings. The 8 second gap is the owner's requirement and can only be raised,
 * never lowered below 8 s, so Flow is not hammered and images are not lost.
 */
object FlowSettings {
    private const val PREFS = "flow_browser_prefs"
    private const val KEY_DELAY = "delay_ms"
    private const val KEY_DESKTOP = "desktop_mode"
    private const val KEY_AI_VERIFY = "ai_verify"
    private const val KEY_LAST_URL = "last_url"
    const val MIN_DELAY_MS = 8000L

    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun delayMs(ctx: Context): Long = prefs(ctx).getLong(KEY_DELAY, MIN_DELAY_MS).coerceAtLeast(MIN_DELAY_MS)
    fun setDelayMs(ctx: Context, ms: Long) { prefs(ctx).edit().putLong(KEY_DELAY, ms.coerceAtLeast(MIN_DELAY_MS)).apply() }

    fun desktopMode(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DESKTOP, false)
    fun setDesktopMode(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_DESKTOP, on).apply() }

    /** When true and a vision provider is configured, every picture also gets an AI FIT / NOT FIT check. */
    fun aiVerify(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AI_VERIFY, true)
    fun setAiVerify(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_AI_VERIFY, on).apply() }

    fun lastUrl(ctx: Context): String = prefs(ctx).getString(KEY_LAST_URL, FLOW_HOME) ?: FLOW_HOME
    fun setLastUrl(ctx: Context, url: String) { prefs(ctx).edit().putString(KEY_LAST_URL, url).apply() }
}

const val FLOW_HOME = "https://flow.google.com/"
const val FX_HOME = "https://labs.google/fx/tools/flow"
const val GEMINI_HOME = "https://gemini.google.com/app"
const val IMAGE_FX_HOME = "https://labs.google/fx/tools/image-fx"
const val WHISK_HOME = "https://labs.google/fx/tools/whisk"
