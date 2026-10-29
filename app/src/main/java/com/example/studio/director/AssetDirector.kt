package com.example.studio.director

import android.content.Context
import com.example.studio.composer.Intent
import com.example.studio.composer.LocalBeatAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Decides WHICH scenes should try to use a library asset (b-roll, meme, picture) and describes what is needed, so the
 * Telegram library is consulted for every video, not only when an AI model happens to ask for assets.
 */
object AssetPlanner {
    private val MEME_WORDS = setOf("funny", "lol", "meme", "memes", "reaction", "shocked", "shock", "hasi", "mazak", "expression", "crazy", "unbelievable", "wow")
    private val ASSET_INTENTS = setOf(Intent.STATEMENT, Intent.QUESTION, Intent.QUOTE, Intent.HOOK, Intent.MAP_LIKE_ZOOM)

    /** Search words for a scene: its strongest keywords. */
    fun describe(narration: String): String = LocalBeatAnalyzer.keywords(narration, 4).joinToString(" ")

    /** Kind of asset worth asking for, from the wording of the narration. */
    fun kindFor(narration: String): String {
        val ws = LocalBeatAnalyzer.words(narration).map { it.lowercase() }.toSet()
        return if (ws.any { it in MEME_WORDS }) "image" else "video"
    }

    /**
     * Adds a library request to about one scene in three (never two in a row, never a scene shorter than 2.2 s). Scenes that
     * already carry requests from the model are left alone. Charts and diagrams stay procedural: they must stay readable.
     */
    fun plan(scenes: List<Scene>, maxShare: Double = 0.34): List<Scene> {
        val budget = maxOf(1, (scenes.size * maxShare).toInt())
        var used = scenes.count { it.assets.isNotEmpty() }
        var lastIdx = -10
        return scenes.mapIndexed { i, s ->
            if (s.assets.isNotEmpty()) { lastIdx = i; return@mapIndexed s }
            val intent = Intent.parse(s.visualObjective.substringBefore(':'))
            val ok = used < budget && i - lastIdx >= 2 && s.durationMs >= 2200L && s.narration.isNotBlank() && !s.locked &&
                (intent == null || intent in ASSET_INTENTS)
            val desc = describe(s.narration)
            if (!ok || desc.isBlank()) s
            else { used++; lastIdx = i; s.copy(assets = listOf(AssetRequest("lib$i", kindFor(s.narration), desc))) }
        }
    }
}

/** Asks the user, in their own Telegram chat, for the assets the library could not provide. */
object AssetRequester {
    private const val PREFS = "saif_asset_requests"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private fun hash(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.lowercase().trim().toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    /** Needs that were not already requested in the last 24 hours (so the chat is never spammed). */
    fun freshNeeds(context: Context, needs: List<String>, nowMs: Long = System.currentTimeMillis()): List<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return needs.filter { n -> nowMs - p.getLong(hash(n), 0L) > 24L * 3600_000L }
    }

    fun message(title: String, needs: List<String>): String = buildString {
        appendLine("SAIF AI needs assets for the video \"${title.take(60)}\":")
        needs.forEachIndexed { i, n -> appendLine("${i + 1}. $n") }
        appendLine()
        append("Upload them to this chat with a descriptive file name (or an [ASSET_INDEX] caption with Title / Type / Search Keywords). Then tap \"Check Telegram again\" in the app.")
    }

    /** Sends the request. Returns the number of needs requested, or a failure with a readable reason. */
    suspend fun request(context: Context, botToken: String, chatId: String, title: String, needs: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) return@withContext Result.failure(IllegalStateException("No Telegram bot token is configured (TG_BOT_TOKEN)."))
        if (chatId.isBlank()) return@withContext Result.failure(IllegalStateException("No Telegram chat id is configured (TG_CHAT_ID)."))
        val fresh = freshNeeds(context, needs)
        if (fresh.isEmpty()) return@withContext Result.success(0)
        try {
            val body = FormBody.Builder().add("chat_id", chatId).add("text", message(title, fresh)).build()
            val req = Request.Builder().url("https://api.telegram.org/bot$botToken/sendMessage").post(body).build()
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val why = Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").find(txt)?.groupValues?.get(1) ?: "HTTP ${r.code}"
                    return@withContext Result.failure(IllegalStateException("Telegram refused the request: $why"))
                }
            }
            val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            val now = System.currentTimeMillis()
            fresh.forEach { e.putLong(hash(it), now) }
            e.apply()
            Result.success(fresh.size)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not reach Telegram (${e.javaClass.simpleName})."))
        }
    }
}
