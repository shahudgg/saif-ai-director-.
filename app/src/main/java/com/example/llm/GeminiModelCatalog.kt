package com.example.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Knows which Gemini text models are alive RIGHT NOW for the user's API key.
 *
 * Google retires models on a schedule (1.5 and 2.0 families are already gone). Hard-coding names caused
 * "models/gemini-1.5-flash is not found for API version v1beta" because the fallback list only contained retired
 * models. This catalog asks the API itself (ListModels), ranks the answer, remembers models that returned 404 for
 * this session and only ever falls back to a static list as the very last resort.
 */
object GeminiModelCatalog {
    /** Google keeps these aliases pointed at the current stable Flash / Flash-Lite model. */
    const val DEFAULT_MODEL = "gemini-flash-latest"
    const val LITE_MODEL = "gemini-flash-lite-latest"

    /** Last-resort order, used only when ListModels itself cannot be reached. */
    val STATIC_FALLBACKS: List<String> = listOf(
        "gemini-flash-latest", "gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-2.5-flash",
        "gemini-flash-lite-latest", "gemini-3.5-flash-lite", "gemini-2.5-flash-lite"
    )

    private val RETIRED = Regex("^gemini-(1\\.0|1\\.5|2\\.0)(-|\$)|^gemini-pro(-vision)?\$")
    private val NOT_TEXT = listOf(
        "tts", "image", "live", "embedding", "aqa", "audio", "robotics", "omni", "veo", "imagen",
        "native", "computer-use", "transcribe", "translate", "learnlm", "gemma", "vision"
    )
    private const val TTL_MS = 6L * 60L * 60L * 1000L

    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val dead = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var cached: List<String> = emptyList()
    @Volatile private var cachedAt = 0L
    @Volatile private var cachedFor = 0

    private fun clean(model: String) = model.trim().removePrefix("models/").lowercase()

    /** True for model families Google has shut down (1.0, 1.5, 2.0, gemini-pro). */
    fun isRetired(model: String): Boolean = RETIRED.containsMatchIn(clean(model))

    /** True for chat/text models (no TTS, image, live, embedding...) that are not retired. */
    fun isTextModel(model: String): Boolean {
        val n = clean(model)
        return n.startsWith("gemini-") && !isRetired(n) && NOT_TEXT.none { n.contains(it) }
    }

    /** Higher is better: the "-latest" alias first, then newer versions, Flash before Flash-Lite before Pro, stable before preview. */
    fun rank(model: String): Int {
        val n = clean(model)
        if (n == DEFAULT_MODEL) return 10_000
        if (n == LITE_MODEL) return 9_000
        val v = Regex("gemini-(\\d+)(?:\\.(\\d+))?").find(n)
        val major = v?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minor = v?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        var score = major * 100 + minor
        score += when {
            n.contains("flash-lite") -> 10
            n.contains("flash") -> 40
            n.contains("pro") -> 5
            else -> 0
        }
        if (n.contains("preview") || n.contains("exp")) score -= 3
        return score
    }

    /** Parses a ListModels response into usable text models, best first. */
    fun parseModels(body: String): List<String> {
        val root = try { JSONObject(body) } catch (_: Exception) { return emptyList() }
        val arr = root.optJSONArray("models") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").removePrefix("models/")
            if (name.isBlank()) continue
            val methods = o.optJSONArray("supportedGenerationMethods")
            var ok = methods == null
            if (methods != null) for (j in 0 until methods.length()) if (methods.optString(j) == "generateContent") ok = true
            if (ok && isTextModel(name)) out += name
        }
        return out.distinct().sortedByDescending { rank(it) }
    }

    /** Best model from [live] that is neither in [avoid] nor known dead; null when nothing is left. */
    fun chooseFrom(live: List<String>, avoid: Set<String>, deadSet: Set<String> = emptySet()): String? {
        val skip = (avoid + deadSet).map { clean(it) }.toSet()
        return live.firstOrNull { clean(it) !in skip }
    }

    fun markDead(model: String) { val c = clean(model); if (c.isNotBlank()) dead.add(c) }
    fun isDead(model: String): Boolean = dead.contains(clean(model))
    fun resetForTests() { dead.clear(); cached = emptyList(); cachedAt = 0L; cachedFor = 0 }

    /** Models this key can really call, best first. Cached for six hours; empty when ListModels is unreachable. */
    suspend fun live(apiKey: String): List<String> {
        if (apiKey.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        if (cached.isNotEmpty() && cachedFor == apiKey.hashCode() && now - cachedAt < TTL_MS) return cached
        val fetched = withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=$apiKey").get().build()
                http.newCall(req).execute().use { r -> if (r.isSuccessful) parseModels(r.body?.string().orEmpty()) else emptyList() }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) { emptyList<String>() }
        }
        if (fetched.isNotEmpty()) { cached = fetched; cachedAt = now; cachedFor = apiKey.hashCode() }
        return fetched
    }

    /** The model to use now: best live model, else the first static fallback not known to be dead. */
    suspend fun pick(apiKey: String, avoid: Set<String> = emptySet()): String {
        chooseFrom(live(apiKey), avoid, dead)?.let { return it }
        return chooseFrom(STATIC_FALLBACKS, avoid, dead) ?: DEFAULT_MODEL
    }

    /** Another model to try after [failed] returned 404; null once every candidate has been tried. */
    suspend fun alternative(apiKey: String, failed: String, tried: Set<String>): String? {
        markDead(failed)
        val avoid = tried + failed
        chooseFrom(live(apiKey), avoid, dead)?.let { return it }
        return chooseFrom(STATIC_FALLBACKS, avoid, dead)
    }
}
