package com.example.studio.director

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ResearchSource(val title: String, val url: String, val snippet: String, val publishedDate: String?)

/**
 * Real web research through a user-configured search API (Tavily or Brave Search).
 * The key is stored encrypted via the Android Keystore and is never logged or shown.
 * When no key is configured, [isConfigured] is false and callers must say research was NOT performed.
 */
object WebResearch {
    private const val PREFS = "saif_director_secure"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    /** Returns false (and stores nothing) when secure storage is unavailable; secrets are never written in plaintext. */
    fun saveKey(context: Context, provider: String, key: String): Boolean {
        val sealed = SecretBox.seal(key.trim()) ?: return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("search_provider", provider).putString("search_key", sealed).apply()
        return true
    }

    fun saveTelegramToken(context: Context, token: String): Boolean {
        val sealed = SecretBox.seal(token.trim()) ?: return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tg_token", sealed).apply()
        return true
    }
    /** A token the user saved wins; otherwise the one injected at build time (CI secret) is used, so users need no setup. */
    fun telegramToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tg_token", "")?.takeIf { it.isNotBlank() }?.let { SecretBox.open(it) }
            ?.takeIf { it.isNotBlank() } ?: com.example.ui.components.TelegramAssetManager.DEFAULT_BOT_TOKEN

    fun provider(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("search_provider", "Tavily") ?: "Tavily"
    fun isConfigured(context: Context): Boolean = !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("search_key", "").isNullOrBlank()

    private fun key(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("search_key", "")?.takeIf { it.isNotBlank() }?.let { SecretBox.open(it) }

    suspend fun search(context: Context, query: String, max: Int = 6): Result<List<ResearchSource>> = withContext(Dispatchers.IO) {
        val k = key(context) ?: return@withContext Result.failure(IllegalStateException(ProviderRegistry.missingMessage(Capability.WEB_SEARCH)))
        try {
            val req = if (provider(context).equals("Brave", true)) {
                Request.Builder().url("https://api.search.brave.com/res/v1/web/search?count=$max&q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                    .header("X-Subscription-Token", k).header("Accept", "application/json").build()
            } else {
                val body = JSONObject().put("query", query).put("max_results", max).put("search_depth", "advanced").toString()
                Request.Builder().url("https://api.tavily.com/search").header("Authorization", "Bearer $k")
                    .post(body.toRequestBody("application/json".toMediaType())).build()
            }
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext Result.failure(IllegalStateException("Search provider returned HTTP ${r.code}"))
                Result.success(parse(provider(context), txt))
            }
        } catch (e: Exception) { Result.failure(IllegalStateException("Search failed: ${e.javaClass.simpleName}")) }
    }

    fun parse(provider: String, json: String): List<ResearchSource> {
        val o = JSONObject(json)
        return if (provider.equals("Brave", true)) {
            val arr = o.optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                ResearchSource(it.optString("title"), it.optString("url"), it.optString("description"), it.optString("age").ifBlank { null }) }
        } else {
            val arr = o.optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                ResearchSource(it.optString("title"), it.optString("url"), it.optString("content"), it.optString("published_date").ifBlank { null }) }
        }.filter { it.url.startsWith("https://") }
    }

    // ---- keyless research (Wikipedia + Google News RSS): works with no API key, so research is never silently skipped

    private fun stripTags(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ").trim()

    fun parseWikipedia(json: String): List<ResearchSource> = try {
        val arr = JSONObject(json).getJSONObject("query").getJSONArray("search")
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            val title = it.optString("title")
            val url = "https://en.wikipedia.org/wiki/" + java.net.URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
            ResearchSource(title, url, stripTags(it.optString("snippet")), it.optString("timestamp").take(10).ifBlank { null })
        }.filter { it.title.isNotBlank() && it.snippet.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    fun parseNewsRss(xml: String, max: Int = 5): List<ResearchSource> {
        val item = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        fun tag(block: String, name: String): String =
            Regex("<$name>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</$name>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1)?.trim().orEmpty()
        return item.findAll(xml).take(max).mapNotNull { m ->
            val b = m.groupValues[1]
            val title = stripTags(tag(b, "title")); val link = tag(b, "link")
            if (title.isBlank() || !link.startsWith("https://")) null
            else ResearchSource(title, link, stripTags(tag(b, "description")).ifBlank { title }, tag(b, "pubDate").ifBlank { null })
        }.toList()
    }

    private fun fetchText(url: String): String? =
        http.newCall(Request.Builder().url(url).header("User-Agent", "SaifAI/1.0 (video research)").build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }

    /** Encyclopedia + latest news for [query]. Fails only when both sources return nothing. */
    suspend fun searchFree(query: String, max: Int = 4): Result<List<ResearchSource>> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val out = ArrayList<ResearchSource>()
        try { fetchText("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&format=json&utf8=1&srlimit=$max")?.let { out += parseWikipedia(it) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
        try { fetchText("https://news.google.com/rss/search?q=$q&hl=en-IN&gl=IN&ceid=IN:en")?.let { out += parseNewsRss(it, max) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}
        if (out.isEmpty()) Result.failure(IllegalStateException("Free research sources returned nothing for this query.")) else Result.success(out)
    }

    /** Numbered digest passed to the script prompt so claims can cite [n]. */
    fun digest(sources: List<ResearchSource>): String =
        sources.mapIndexed { i, s -> "[${i + 1}] ${s.title} (${s.url}${s.publishedDate?.let { ", $it" } ?: ""}): ${s.snippet.take(380)}" }.joinToString("\n")
}
