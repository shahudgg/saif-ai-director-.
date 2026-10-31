package com.example.studio.director

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One fact pulled out of a real page, with where it came from. */
data class ResearchFact(val text: String, val sourceIndex: Int, val score: Int)

/**
 * Pure helpers (no network, no Android) so the research logic is unit-testable:
 * query planning fallbacks, readable-text extraction from HTML and fact scoring.
 */
object ResearchText {
    private val dropBlocks = Regex("<(script|style|nav|header|footer|aside|form|noscript|svg|iframe)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val paragraph = Regex("<(p|li)[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)

    fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&ndash;", "-").replace("&mdash;", "-").replace("&rsquo;", "'").replace("&lsquo;", "'")
        .replace("&ldquo;", "\"").replace("&rdquo;", "\"").replace("&hellip;", "...")
        .replace(Regex("&#(\\d{2,5});")) { m -> m.groupValues[1].toIntOrNull()?.let { c -> if (c in 32..0xFFFF) c.toChar().toString() else " " } ?: " " }

    /** Visible paragraph text of an HTML page, in reading order. */
    fun paragraphs(html: String, minChars: Int = 60): List<String> {
        val body = html.replace(dropBlocks, " ")
        return paragraph.findAll(body).map { m ->
            decodeEntities(m.groupValues[2].replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim()
        }.filter { it.length >= minChars }.toList()
    }

    fun sentences(text: String): List<String> =
        text.replace(Regex("\\s+"), " ").split(Regex("(?<=[.!?])\\s+(?=[A-Z0-9\"'\\u201C])")).map { it.trim() }.filter { it.isNotEmpty() }

    private val numberRx = Regex("\\d[\\d,.]*\\s?(%|percent|million|billion|trillion|crore|lakh|thousand|x|times|years?|months?|days?|hours?|km|kg|\\$|usd|inr|rs)?", RegexOption.IGNORE_CASE)
    private val yearRx = Regex("\\b(19[5-9]\\d|20[0-4]\\d)\\b")
    private val junk = Regex("cookie|subscribe|sign up|newsletter|advertisement|all rights reserved|privacy policy|click here|read more|follow us|terms of use", RegexOption.IGNORE_CASE)

    /** Higher = more concrete and on-topic. [topicWords] are lower-case keywords of the user's topic. */
    fun scoreSentence(s: String, topicWords: Set<String>, currentYear: Int): Int {
        if (s.length < 45 || s.length > 280 || junk.containsMatchIn(s)) return -1
        var sc = 0
        if (numberRx.containsMatchIn(s)) sc += 3
        yearRx.findAll(s).forEach { m -> val y = m.value.toInt(); sc += if (y >= currentYear - 1) 3 else if (y >= currentYear - 5) 2 else 1 }
        val lower = s.lowercase()
        topicWords.forEach { w -> if (w.length > 3 && lower.contains(w)) sc += 2 }
        if (Regex("\\b[A-Z][a-z]{2,}\\b.*\\b[A-Z][a-z]{2,}\\b").containsMatchIn(s.drop(1))) sc += 1       // names / places / products
        if (s.endsWith("?")) sc -= 2
        return sc
    }

    fun topFacts(sourceIndex: Int, texts: List<String>, topicWords: Set<String>, currentYear: Int, max: Int): List<ResearchFact> =
        texts.flatMap { sentences(it) }.map { it to scoreSentence(it, topicWords, currentYear) }
            .filter { it.second >= 3 }.distinctBy { it.first.lowercase().take(60) }
            .sortedByDescending { it.second }.take(max).map { ResearchFact(it.first, sourceIndex, it.second) }

    /** Queries used when no model can plan them: different angles of the same topic. */
    fun fallbackQueries(topic: String, year: Int): List<String> {
        val t = topic.trim().take(90)
        return listOf("$t $year", "$t latest news", "$t statistics numbers", "$t how it works explained", "$t controversy criticism")
    }

    /** Extracts a JSON array of strings from a model reply; tolerant of fences and numbering. */
    fun parseQueries(raw: String, max: Int = 5): List<String> {
        runCatching {
            val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1) ?: raw
            val a = JSONArray(fenced.substring(fenced.indexOf('['), fenced.lastIndexOf(']') + 1))
            return (0 until a.length()).map { a.optString(it).trim() }.filter { it.length in 4..120 }.distinct().take(max)
        }
        return raw.lines().map { it.replace(Regex("^\\s*(?:\\d+[.)]|[-*\u2022])\\s*"), "").trim().trim('"') }.filter { it.length in 4..120 }.take(max)
    }

    /** Unwraps DuckDuckGo's redirect links (…/l/?uddg=<encoded url>). */
    fun ddgTarget(href: String): String? {
        val raw = if (href.contains("uddg=")) java.net.URLDecoder.decode(href.substringAfter("uddg=").substringBefore('&'), "UTF-8") else href
        val u = if (raw.startsWith("//")) "https:$raw" else raw
        return u.takeIf { it.startsWith("https://") && !it.contains("duckduckgo.com") }
    }

    fun parseDuckDuckGo(html: String, max: Int = 6): List<ResearchSource> {
        val link = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
        val snip = Regex("<a[^>]*class=\"result__snippet\"[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
        val snippets = snip.findAll(html).map { decodeEntities(it.groupValues[1].replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim() }.toList()
        return link.findAll(html).mapIndexedNotNull { i, m ->
            val url = ddgTarget(decodeEntities(m.groupValues[1])) ?: return@mapIndexedNotNull null
            val title = decodeEntities(m.groupValues[2].replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim()
            if (title.isBlank()) null else ResearchSource(title, url, snippets.getOrNull(i).orEmpty().ifBlank { title }, null)
        }.take(max).toList()
    }
}

/**
 * Real research for a video topic: the model plans several search angles, the app searches (your key, or keyless Bing /
 * DuckDuckGo / Wikipedia / Google News), then READS the top pages and pulls out concrete, dated, numbered facts.
 * The script writer only gets facts that came from pages that were really fetched.
 */
object DeepResearch {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).followRedirects(true).build()
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val MAX_PAGE_BYTES = 450_000

    private fun fetch(url: String, accept: String = "text/html,application/xhtml+xml,application/xml,application/json"): String? = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Accept", accept).header("Accept-Language", "en-US,en;q=0.9,hi;q=0.6").build()).execute().use { r ->
            if (!r.isSuccessful) null else {
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                r.body?.byteStream()?.use { inp ->
                    while (out.size() < MAX_PAGE_BYTES) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                }
                String(out.toByteArray(), Charsets.UTF_8)
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }

    /** Keyless web search: Bing's RSS feed first, DuckDuckGo's HTML page as a second opinion. */
    suspend fun searchKeyless(query: String, max: Int = 6): List<ResearchSource> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val out = LinkedHashMap<String, ResearchSource>()
        fetch("https://www.bing.com/search?q=$q&format=rss&count=$max&setlang=en")?.let { xml -> WebResearch.parseNewsRss(xml, max).forEach { out.putIfAbsent(it.url, it) } }
        if (out.size < 3) fetch("https://html.duckduckgo.com/html/?q=$q")?.let { html -> ResearchText.parseDuckDuckGo(html, max).forEach { out.putIfAbsent(it.url, it) } }
        out.values.toList()
    }

    /** Plain-text intro + body of a Wikipedia article (much better than a search snippet). */
    private suspend fun wikipediaExtract(title: String): String? = withContext(Dispatchers.IO) {
        val t = java.net.URLEncoder.encode(title, "UTF-8")
        val json = fetch("https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=plain&redirects=1&exchars=4500&titles=$t&format=json&utf8=1") ?: return@withContext null
        runCatching {
            val pages = JSONObject(json).getJSONObject("query").getJSONObject("pages")
            val k = pages.keys().next()
            pages.getJSONObject(k).optString("extract").takeIf { it.length > 120 }
        }.getOrNull()
    }

    private suspend fun readPage(src: ResearchSource): List<String> = withContext(Dispatchers.IO) {
        if (src.url.startsWith("https://en.wikipedia.org/wiki/")) {
            val title = java.net.URLDecoder.decode(src.url.substringAfter("/wiki/"), "UTF-8").replace('_', ' ')
            return@withContext wikipediaExtract(title)?.split(Regex("\\n+"))?.filter { it.length > 60 } ?: emptyList()
        }
        fetch(src.url)?.let { ResearchText.paragraphs(it) } ?: emptyList()
    }

    private suspend fun planQueries(topic: String, year: Int, ask: suspend (String) -> Result<String>): List<String> {
        val today = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
        val prompt = "Today is $today. List 5 different web search queries (English, max 8 words each) that would find the NEWEST, most specific information " +
            "about this video topic: \"$topic\". Cover: latest news/updates, hard numbers and statistics, how it really works, a surprising real example or case, and criticism or risks. " +
            "Reply with ONLY a JSON array of 5 strings."
        val raw = ask(prompt).getOrNull() ?: return emptyList()
        return ResearchText.parseQueries(raw)
    }

    /**
     * Runs the whole research. [ask] returns raw model text (null model => deterministic query angles).
     * Never throws: returns an outcome whose note says exactly what was and was not possible.
     */
    suspend fun run(
        context: Context, topic: String, keyed: Boolean, ask: (suspend (String) -> Result<String>)?,
        onStep: (String) -> Unit = {}
    ): ResearchOutcome = coroutineScope {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        onStep("Planning search angles")
        val planned = if (ask != null) cancellableResult { planQueries(topic, year, ask) }.getOrDefault(emptyList()) else emptyList()
        val queries = planned.ifEmpty { ResearchText.fallbackQueries(topic, year) }.take(5)
        // Topic words come from the user's topic AND the (English) planned queries, so Hinglish topics still match English pages.
        val topicWords = (com.example.studio.composer.LocalBeatAnalyzer.keywords(topic, 8) + queries.flatMap { com.example.studio.composer.LocalBeatAnalyzer.keywords(it, 4) })
            .map { it.lowercase() }.toSet()

        onStep("Searching the web (${queries.size} angles)")
        val found = LinkedHashMap<String, ResearchSource>()
        val results = queries.map { q ->
            async {
                val r = ArrayList<ResearchSource>()
                if (keyed) WebResearch.search(context, q, 5).onSuccess { r += it }
                if (r.isEmpty()) r += searchKeyless(q, 5)
                r
            }
        }.awaitAll()
        results.forEach { rs -> rs.forEach { found.putIfAbsent(it.url, it) } }
        // Encyclopedia + news add a stable base even when web search is thin.
        WebResearch.searchFree(topic.take(90), 3).onSuccess { rs -> rs.forEach { found.putIfAbsent(it.url, it) } }
        if (found.isEmpty()) return@coroutineScope ResearchOutcome(null, emptyList(), "Web search returned nothing usable (offline or blocked), so the script uses general knowledge only.")

        // Prefer one page per site, news/encyclopedia/organisation pages before random blogs.
        val perHost = HashMap<String, Int>()
        val ordered = found.values.filter { s ->
            val host = runCatching { java.net.URI(s.url).host.orEmpty() }.getOrDefault("")
            val n = perHost.getOrDefault(host, 0); perHost[host] = n + 1; n < 2
        }.take(14)

        onStep("Reading ${minOf(6, ordered.size)} pages")
        val toRead = ordered.take(6)
        val pageTexts = toRead.map { s -> async { withTimeoutOrNull(14_000) { readPage(s) } ?: emptyList() } }.awaitAll()

        val facts = ArrayList<ResearchFact>()
        toRead.forEachIndexed { i, _ -> facts += ResearchText.topFacts(i + 1, pageTexts[i], topicWords, year, 4) }
        // Snippets of the remaining results still count as (weaker) facts.
        ordered.forEachIndexed { i, s -> if (i >= toRead.size) facts += ResearchText.topFacts(i + 1, listOf(s.snippet), topicWords, year, 1) }
        val readCount = pageTexts.count { it.isNotEmpty() }
        val best = facts.distinctBy { it.text.lowercase().take(60) }.sortedByDescending { it.score }.take(16)

        val digest = buildString {
            if (best.isNotEmpty()) {
                appendLine("CONCRETE FACTS (from pages that were actually read; use the numbers, names and dates exactly as written):")
                best.forEachIndexed { i, f -> appendLine("F${i + 1}. ${f.text.take(260)} [${f.sourceIndex}]") }
                appendLine()
            }
            appendLine("SOURCES:")
            ordered.forEachIndexed { i, s -> appendLine("[${i + 1}] ${s.title.take(100)} (${s.url}${s.publishedDate?.let { ", $it" } ?: ""}): ${s.snippet.take(260)}") }
        }.take(7500)
        val how = if (keyed) "your search key + free sources" else "keyless Bing/DuckDuckGo, Wikipedia and Google News"
        ResearchOutcome(digest, ordered, "Researched ${queries.size} angles via $how, opened $readCount pages in full and extracted ${best.size} concrete facts from ${ordered.size} sources.")
    }
}

/** Hands each scene the researched facts that belong to it, so the graphics show real numbers and names. */
object SceneFacts {
    /** Lines "F1. text [n]" of a research digest, without the label and source number. */
    fun parse(digest: String?): List<String> = digest?.lines()?.map { it.trim() }?.filter { Regex("^F\\d+\\.").containsMatchIn(it) }
        ?.map { it.replace(Regex("^F\\d+\\.\\s*"), "").replace(Regex("\\s*\\[\\d+\\]\\s*$"), "").trim() } ?: emptyList()

    fun forScene(facts: List<String>, narration: String, max: Int = 2): List<String> {
        if (facts.isEmpty()) return emptyList()
        val words = com.example.studio.composer.LocalBeatAnalyzer.keywords(narration, 10).map { it.lowercase() }
        val nums = Regex("\\d[\\d.,]*").findAll(narration).map { it.value.trim('.', ',') }.filter { it.isNotEmpty() }.toList()
        return facts.map { f ->
            val l = f.lowercase()
            f to (words.count { w -> w.length > 3 && l.contains(w) } + nums.count { n -> f.contains(n) } * 2)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(max).map { it.first.take(170) }
    }
}
