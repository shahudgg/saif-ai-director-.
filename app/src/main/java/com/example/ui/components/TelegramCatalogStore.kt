package com.example.ui.components

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** What the last sync did, in numbers a person can act on. Shown in the diagnostics panel. */
data class TelegramSyncReport(
    val ok: Boolean = false,
    val error: String = "",
    val updatesReceived: Int = 0,
    val indexedFromCaption: Int = 0,
    val indexedFromFileName: Int = 0,
    val skipped: Map<String, Int> = emptyMap(),
    val totalItems: Int = 0,
    val webhookUrl: String = "",
    val lastUpdateId: Long = 0L,
    val tookMs: Long = 0L,
    val at: Long = System.currentTimeMillis()
) {
    fun summary(): String = buildString {
        append(if (ok) "Sync OK" else "Sync FAILED")
        if (error.isNotBlank()) append(": ").append(error)
        append("\nUpdates received: $updatesReceived")
        append("\nIndexed from [ASSET_INDEX] caption: $indexedFromCaption")
        append("\nIndexed from file name (no caption): $indexedFromFileName")
        if (skipped.isNotEmpty()) append("\nSkipped: ").append(skipped.entries.joinToString { "${it.key}=${it.value}" })
        append("\nAssets in catalog: $totalItems")
        if (webhookUrl.isNotBlank()) append("\nWebhook is set ($webhookUrl): Telegram will not deliver getUpdates until it is removed.")
    }
}

/** Pure parsing of a getUpdates response: no network, no Android state, fully testable. */
object TelegramUpdateProcessor {
    data class Processed(
        val items: List<TelegramAssetItem>, val fromCaption: Int, val fromFileName: Int,
        val skipped: Map<String, Int>, val received: Int, val maxUpdateId: Long
    )

    private fun bump(m: MutableMap<String, Int>, k: String) { m[k] = (m[k] ?: 0) + 1 }

    /** Guesses type/category from a file name or mime when the post has no [ASSET_INDEX] caption. */
    fun indexFromFile(fileName: String, mime: String, fileId: String, messageId: Long, fileSize: Long, durationSec: Int, caption: String = ""): TelegramAssetItem? {
        val ext = fileName.substringAfterLast('.', "").lowercase().ifBlank { mime.substringAfter('/', "").lowercase() }
        val type = when {
            mime.startsWith("audio") || ext in setOf("mp3", "wav", "m4a", "ogg", "aac", "flac") -> "audio"
            mime.startsWith("video") || ext in setOf("mp4", "mov", "webm", "mkv") -> "video"
            mime.startsWith("image") || ext in setOf("png", "jpg", "jpeg", "webp", "gif") -> "image"
            ext in setOf("ttf", "otf") -> "font"
            else -> return null
        }
        // Telegram often sends videos/photos with NO file_name, which used to leave the asset with the
        // useless keyword "asset". The caption almost always describes the clip, so mine it as well.
        val cap = caption.replace(Regex("\\s+"), " ").trim()
        val rawBase = fileName.substringBeforeLast('.').ifBlank { "" }
        val base = if (rawBase.isBlank() || rawBase.matches(Regex("(?i)asset[-_]?\\d+"))) {
            cap.take(48).trim().ifBlank { "asset_$messageId" }
        } else rawBase
        val words = (fileName.lowercase() + " " + caption.lowercase())
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 && !it.all { c -> c.isDigit() } }
        val category = when (type) {
            "audio" -> if (durationSec in 1..10 || words.any { it in setOf("sfx", "whoosh", "pop", "hit", "click", "swoosh", "ding", "riser", "impact") }) "sfx" else "bgm"
            "video" -> if (words.any { it in setOf("overlay", "glitch", "transition") }) "overlay_graphic" else "b_roll"
            "image" -> "overlay_graphic"
            else -> "font"
        }
        return TelegramAssetItem(
            assetId = "AID_${messageId}", title = base, type = type, extension = ext.ifBlank { "bin" }, category = category,
            subject = "auto-indexed from file name", searchKeywords = words.distinct(), tags = words.distinct().map { "#$it" },
            userId = "all", fileId = fileId, messageId = messageId, fileSize = fileSize
        )
    }

    /**
     * @param acceptPrivateChats when true, media that the owner forwards to the bot in a private chat is indexed too.
     *   This matters because the Bot API has NO way to read a channel's history: getUpdates only ever returns the
     *   ~24h of pending updates, so older channel posts are invisible no matter what. Forwarding the existing library
     *   to the bot once makes every file permanently visible to the app (file ids never expire for a bot).
     */
    fun process(response: JSONObject, channelId: String, acceptPrivateChats: Boolean = false): Processed {
        val skipped = LinkedHashMap<String, Int>()
        val out = ArrayList<TelegramAssetItem>()
        var fromCaption = 0; var fromName = 0; var maxId = 0L
        val arr = response.optJSONArray("result") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            maxId = maxOf(maxId, u.optLong("update_id", 0L))
            val post = u.optJSONObject("channel_post") ?: u.optJSONObject("edited_channel_post") ?: u.optJSONObject("message") ?: u.optJSONObject("edited_message")
            if (post == null) { bump(skipped, "not_a_post"); continue }
            val chat = post.optJSONObject("chat")
            val chatId = chat?.optLong("id", 0L)?.toString().orEmpty()
            val uname = chat?.optString("username", "").orEmpty()
            val chatType = chat?.optString("type", "").orEmpty()
            val want = channelId.trim()
            val isWantedChannel = chatId == want || (!want.isBlank() && uname.equals(want.removePrefix("@"), true))
            val isOwnerDm = acceptPrivateChats && chatType == "private"
            if (want.isNotBlank() && !isWantedChannel && !isOwnerDm) { bump(skipped, "other_chat"); continue }
            val messageId = post.optLong("message_id", 0L)
            val text = post.optString("caption", "").ifBlank { post.optString("text", "") }

            var fileId = ""; var size = 0L; var fileName = ""; var mime = ""; var dur = 0
            fun take(o: JSONObject?) { if (o != null && fileId.isBlank()) {
                fileId = o.optString("file_id"); size = o.optLong("file_size", 0L); fileName = o.optString("file_name", ""); mime = o.optString("mime_type", ""); dur = o.optInt("duration", 0)
            } }
            take(post.optJSONObject("video")); take(post.optJSONObject("audio")); take(post.optJSONObject("voice"))
            take(post.optJSONObject("document")); take(post.optJSONObject("animation"))
            post.optJSONArray("photo")?.let { p -> if (p.length() > 0 && fileId.isBlank()) { val last = p.optJSONObject(p.length() - 1); fileId = last?.optString("file_id").orEmpty(); size = last?.optLong("file_size", 0L) ?: 0L; mime = "image/jpeg"; fileName = "photo_$messageId.jpg" } }

            if (fileId.isBlank()) { bump(skipped, if (text.contains("[ASSET_INDEX]", true)) "caption_without_file" else "no_file"); continue }
            val parsed = if (text.isNotBlank()) TelegramAssetManager.parseAssetCaption(text, fileId, messageId, size) else null
            if (parsed != null) { out += parsed; fromCaption++; continue }
            val auto = indexFromFile(fileName, mime, fileId, messageId, size, dur, caption = text)
            if (auto != null) { out += auto; fromName++ } else bump(skipped, "unrecognised_file")
        }
        return Processed(out, fromCaption, fromName, skipped, arr.length(), maxId)
    }
}

/**
 * Persistent Telegram asset catalog. Posts are merged into filesDir/telegram_catalog.json and the getUpdates offset is
 * remembered, so assets stay available across restarts even after Telegram has dropped the old updates.
 */
class TelegramCatalogStore(private val context: Context) {
    private val file = File(context.filesDir, "telegram_catalog.json")
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private data class State(val items: MutableList<TelegramAssetItem>, var lastUpdateId: Long)

    private fun itemToJson(a: TelegramAssetItem) = JSONObject().put("assetId", a.assetId).put("title", a.title).put("type", a.type).put("ext", a.extension)
        .put("category", a.category).put("subject", a.subject).put("kw", JSONArray(a.searchKeywords)).put("tags", JSONArray(a.tags))
        .put("userId", a.userId).put("fileId", a.fileId).put("messageId", a.messageId).put("size", a.fileSize)

    private fun strs(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun load(): State = runCatching {
        if (!file.exists()) return State(mutableListOf(), 0L)
        val o = JSONObject(file.readText())
        val arr = o.optJSONArray("items") ?: JSONArray()
        State((0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { x ->
            TelegramAssetItem(x.optString("assetId"), x.optString("title"), x.optString("type"), x.optString("ext"), x.optString("category"), x.optString("subject"),
                strs(x.optJSONArray("kw")), strs(x.optJSONArray("tags")), x.optString("userId", "all"), x.optString("fileId"), x.optLong("messageId"), x.optLong("size"))
        } }.toMutableList(), o.optLong("lastUpdateId", 0L))
    }.getOrElse { State(mutableListOf(), 0L) }

    private fun save(s: State) {
        val o = JSONObject().put("lastUpdateId", s.lastUpdateId).put("items", JSONArray().also { a -> s.items.forEach { a.put(itemToJson(it)) } })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString()); if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun items(): List<TelegramAssetItem> = load().items

    fun lastReport(): TelegramSyncReport? = runCatching {
        val f = File(context.filesDir, "telegram_sync_report.json"); if (!f.exists()) return null
        val o = JSONObject(f.readText())
        TelegramSyncReport(o.optBoolean("ok"), o.optString("error"), o.optInt("received"), o.optInt("cap"), o.optInt("name"),
            o.optJSONObject("skipped")?.let { s -> s.keys().asSequence().associateWith { s.optInt(it) } } ?: emptyMap(),
            o.optInt("total"), o.optString("webhook"), o.optLong("lastUpdateId"), o.optLong("took"), o.optLong("at"))
    }.getOrNull()

    private fun saveReport(r: TelegramSyncReport) = runCatching {
        File(context.filesDir, "telegram_sync_report.json").writeText(JSONObject().put("ok", r.ok).put("error", r.error).put("received", r.updatesReceived)
            .put("cap", r.indexedFromCaption).put("name", r.indexedFromFileName).put("skipped", JSONObject(r.skipped as Map<*, *>)).put("total", r.totalItems)
            .put("webhook", r.webhookUrl).put("lastUpdateId", r.lastUpdateId).put("took", r.tookMs).put("at", r.at).toString())
    }

    private fun get(url: String): JSONObject? = runCatching {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            JSONObject(body).also { if (!resp.isSuccessful && !it.has("description")) it.put("description", "HTTP ${resp.code}") }
        }
    }.getOrNull()

    /** Webhooks block getUpdates. Returns the webhook URL (blank if none) or null when Telegram could not be reached. */
    fun webhookUrl(botToken: String): String? = get("https://api.telegram.org/bot$botToken/getWebhookInfo")?.optJSONObject("result")?.optString("url", "")

    /** Explicit user action from the diagnostics panel. */
    fun removeWebhook(botToken: String): Boolean = get("https://api.telegram.org/bot$botToken/deleteWebhook?drop_pending_updates=false")?.optBoolean("ok", false) ?: false

    /**
     * Readable health check for the diagnostics panel. Bot API has no "read channel history", so most
     * "the channel is empty" reports come from one of these four things - this says which one.
     */
    fun diagnose(botToken: String, channelId: String): String = buildString {
        if (botToken.isBlank()) {
            appendLine("BOT TOKEN: missing (TG_BOT_TOKEN). Rebuild with the GitHub secret set.")
            return@buildString
        }
        appendLine("BOT TOKEN: present (" + botToken.take(8) + "\u2026)")
        val me = get("https://api.telegram.org/bot$botToken/getMe")
        val meRes = me?.optJSONObject("result")
        if (me?.optBoolean("ok", false) != true) {
            appendLine("BOT: getMe FAILED \u2014 " + (me?.optString("description", "invalid token") ?: "network error"))
            return@buildString
        }
        val botId = meRes?.optLong("id", 0L) ?: 0L
        appendLine("BOT: @" + meRes?.optString("username", "?"))
        if (channelId.isBlank()) {
            appendLine("CHANNEL: missing (TG_CHAT_ID).")
            return@buildString
        }
        appendLine("CHANNEL ID: $channelId")
        val enc = java.net.URLEncoder.encode(channelId, "UTF-8")
        val chat = get("https://api.telegram.org/bot$botToken/getChat?chat_id=$enc")
        val c = chat?.optJSONObject("result")
        if (chat?.optBoolean("ok", false) == true && c != null) {
            appendLine("CHANNEL: " + c.optString("title", "?") + " (type=" + c.optString("type", "?") + ")")
        } else {
            appendLine("CHANNEL: NOT REACHABLE \u2014 " + (chat?.optString("description", "?") ?: "network error"))
            appendLine("  \u2192 Add this bot as an ADMIN of the channel, then sync again.")
        }
        if (botId != 0L && chat?.optBoolean("ok", false) == true) {
            val mem = get("https://api.telegram.org/bot$botToken/getChatMember?chat_id=$enc&user_id=$botId")
            val st = mem?.optJSONObject("result")?.optString("status", "").orEmpty()
            if (st.isNotBlank()) {
                appendLine("BOT MEMBERSHIP: $st" + if (st == "administrator" || st == "creator") " (OK)" else " \u2014 must be 'administrator'")
            }
        }
        val hook = webhookUrl(botToken)
        appendLine(if (hook.isNullOrBlank()) "WEBHOOK: none (getUpdates works)" else "WEBHOOK: SET ($hook) \u2014 blocks getUpdates")
        appendLine("NOTE: the Bot API cannot read channel history. Only posts made while the bot is admin are ever delivered.")
        appendLine("      To import your existing library once: forward those posts to this bot in a private chat, then sync.")
    }

    @Synchronized
    fun sync(botToken: String, channelId: String, acceptPrivateChats: Boolean = true): TelegramSyncReport {
        val t0 = System.currentTimeMillis()
        if (botToken.isBlank()) return TelegramSyncReport(false, "No Telegram bot token is configured (TG_BOT_TOKEN).", totalItems = load().items.size).also { saveReport(it) }
        val state = load()
        val webhook = webhookUrl(botToken)
        if (webhook == null) return TelegramSyncReport(false, "Could not reach Telegram (network or invalid token).", totalItems = state.items.size, lastUpdateId = state.lastUpdateId).also { saveReport(it) }
        var received = 0; var cap = 0; var name = 0
        val skipped = LinkedHashMap<String, Int>()
        var error = ""
        if (webhook.isBlank()) {
            var pages = 0
            while (pages++ < 20) {
                val resp = get("https://api.telegram.org/bot$botToken/getUpdates?offset=${state.lastUpdateId + 1}&limit=100&timeout=0&allowed_updates=" +
                    java.net.URLEncoder.encode("[\"channel_post\",\"edited_channel_post\",\"message\",\"edited_message\"]", "UTF-8"))
                if (resp == null) { error = "Network error while reading updates."; break }
                if (!resp.optBoolean("ok", false)) { error = resp.optString("description", "Telegram returned an error"); break }
                val p = TelegramUpdateProcessor.process(resp, channelId, acceptPrivateChats)
                if (p.received == 0) break
                received += p.received; cap += p.fromCaption; name += p.fromFileName
                p.skipped.forEach { (k, v) -> skipped[k] = (skipped[k] ?: 0) + v }
                state.lastUpdateId = maxOf(state.lastUpdateId, p.maxUpdateId)
                p.items.forEach { a ->
                    val idx = state.items.indexOfFirst { it.messageId == a.messageId || (it.fileId == a.fileId && a.fileId.isNotBlank()) }
                    if (idx >= 0) state.items[idx] = a else state.items.add(a)
                }
                if (p.received < 100) break
            }
            runCatching { save(state) }
        } else error = "A webhook is set on this bot, so Telegram will not deliver new posts to getUpdates."
        if (error.isBlank() && received == 0 && state.items.isEmpty()) {
            error = "Telegram returned no pending updates. The Bot API cannot read channel history, so posts made " +
                "before the bot became an admin are invisible. Forward your library to the bot once, or post new assets."
        }
        val rep = TelegramSyncReport(error.isBlank(), error, received, cap, name, skipped, state.items.size, webhook, state.lastUpdateId, System.currentTimeMillis() - t0)
        saveReport(rep)
        Log.i("TelegramCatalog", rep.summary().replace(botToken, "***"))
        return rep
    }
}
