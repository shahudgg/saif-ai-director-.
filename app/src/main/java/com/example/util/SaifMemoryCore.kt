package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * SAIF MEMORY CORE — the memory recall of SAIF AI AUTOMATION, moved into the app's backend so that EVERY
 * feature remembers, not only the live conversation.
 *
 * Before this, memory lived in three disconnected places:
 *  • `SmartConversationMemory` — facts learned from the chat, used only by the chat and the live voice session,
 *  • the automation engine's Room memory bank (`MemoryManager`) — used only while the automation UI was open,
 *  • nothing at all for the video director, the app builder or the phone agent.
 *
 * [SaifMemoryCore] is now the single recall layer. It keeps [SmartConversationMemory] as its fact store (so the
 * existing AI MEMORY screen in Settings and its PIN lock keep working unchanged), tags every fact with the
 * FEATURE that learned it, mirrors everything into the automation engine's own memory bank, and answers
 * `recall(feature)` for any caller. Chat, live baatcheet, the video director, the builder and the agent all ask
 * here, so a fact learned while making a video is remembered in the chat — and the other way round.
 */
object SaifMemoryCore {

    /** Features that can learn and recall. Adding one is enough; nothing else has to change. */
    object Features {
        const val CHAT = "chat"
        const val LIVE = "live"
        const val VIDEO = "video"
        const val BUILDER = "builder"
        const val AGENT = "agent"
        const val AUTOMATION = "automation"
        const val GLOBAL = "global"

        val ALL = listOf(CHAT, LIVE, VIDEO, BUILDER, AGENT, AUTOMATION, GLOBAL)
    }

    private const val PREFS = "saif_memory_core"
    private const val KEY_TAGS = "fact_feature_tags"
    private const val KEY_ENABLED = "memory_recall_enabled"
    private const val KEY_AUTO_LANGUAGE = "auto_language_enabled"
    private const val KEY_LAST_USED = "feature_last_used_ms"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** feature -> keys learned by that feature. Kept in prefs so it survives a restart. */
    private val tags = ConcurrentHashMap<String, MutableSet<String>>()

    private val _enabled = MutableStateFlow(true)
    /** Master switch for memory recall (Settings > AI MEMORY). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _autoLanguage = MutableStateFlow(true)
    /**
     * AUTO LANGUAGE (moved here from SAIF AI AUTOMATION's settings): detect the language the user speaks or types
     * and answer in the same one, instead of forcing one language.
     */
    val autoLanguage: StateFlow<Boolean> = _autoLanguage.asStateFlow()

    private val _lastFactCount = MutableStateFlow(0)
    val lastFactCount: StateFlow<Int> = _lastFactCount.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val c = context.applicationContext
        appContext = c
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        SmartConversationMemory.init(c)
        _enabled.value = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        _autoLanguage.value = prefs?.getBoolean(KEY_AUTO_LANGUAGE, true) ?: true
        loadTags()
        _lastFactCount.value = SmartConversationMemory.getAllMemoriesList().size
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }

    fun setAutoLanguage(on: Boolean) {
        _autoLanguage.value = on
        prefs?.edit()?.putBoolean(KEY_AUTO_LANGUAGE, on)?.apply()
    }

    // ------------------------------------------------------------------ learn

    /**
     * Learns from one exchange in [feature]. The heavy lifting (finding "my name is", "I like", numbers, dates)
     * stays in [SmartConversationMemory]; this adds the feature tag and mirrors the new facts into the automation
     * engine's memory bank so the live orb remembers them too.
     */
    fun learn(feature: String, userText: String, aiReply: String = "") {
        val c = appContext ?: return
        if (!_enabled.value) return
        if (userText.isBlank()) return
        val before = SmartConversationMemory.getAllMemoriesList().map { it.key }.toSet()
        SmartConversationMemory.extractAndSaveFacts(userText, aiReply)
        val after = SmartConversationMemory.getAllMemoriesList()
        _lastFactCount.value = after.size
        val fresh = after.map { it.key }.filter { it !in before }
        if (fresh.isNotEmpty()) {
            tag(feature, fresh)
            mirrorToAutomation(userText)
        }
        noteUsed(c, feature)
    }

    /** Remembers one fact explicitly (used by the video director for the owner's channel, niche, tone...). */
    fun remember(feature: String, key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        SmartConversationMemory.saveFact(key, value)
        tag(feature, listOf(key))
        _lastFactCount.value = SmartConversationMemory.getAllMemoriesList().size
        appContext?.let { noteUsed(it, feature) }
    }

    private fun tag(feature: String, keys: List<String>) {
        val f = feature.ifBlank { Features.GLOBAL }
        val set = tags.getOrPut(f) { LinkedHashSet() }
        set.addAll(keys)
        val all = tags.getOrPut(Features.GLOBAL) { LinkedHashSet() }
        all.addAll(keys)
        saveTags()
    }

    private fun saveTags() {
        val o = JSONObject()
        tags.forEach { (f, keys) -> o.put(f, JSONArray(keys.toList())) }
        prefs?.edit()?.putString(KEY_TAGS, o.toString())?.apply()
    }

    private fun loadTags() {
        val raw = prefs?.getString(KEY_TAGS, null) ?: return
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        tags.clear()
        o.keys().forEach { k ->
            val arr = o.optJSONArray(k) ?: return@forEach
            val set = LinkedHashSet<String>()
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { set.add(it) }
            tags[k] = set
        }
    }

    /** Mirrors a fact into the automation engine's own Room memory bank (best effort, never throws). */
    private fun mirrorToAutomation(userText: String) {
        scope.launch {
            try {
                com.jarvis.assistant.JarvisApp.instance.memoryManager.rememberFact(userText)
            } catch (_: Throwable) {
            }
        }
    }

    private fun noteUsed(c: Context, feature: String) {
        prefs?.edit()?.putLong("$KEY_LAST_USED:$feature", System.currentTimeMillis())?.apply()
    }

    // ------------------------------------------------------------------ recall

    /**
     * The facts [feature] should know about: everything that feature learned, plus everything learned globally.
     * Most recent first, capped so the prompt never grows without limit.
     */
    fun recall(feature: String, limit: Int = 18): List<MemoryFactItem> {
        if (!_enabled.value) return emptyList()
        val all = SmartConversationMemory.getAllMemoriesList()
        if (all.isEmpty()) return emptyList()
        val wanted = (tags[feature] ?: emptySet()) + (tags[Features.GLOBAL] ?: emptySet())
        val mine = all.filter { it.key in wanted }
        val others = all.filter { it.key !in wanted }
        return (mine + others).take(limit)
    }

    /**
     * Memory block for a prompt. [feature] decides which facts come first, so a video project sees the channel and
     * niche facts first while the chat sees personal facts first. Empty string when recall is off or nothing is known.
     */
    fun promptBlock(feature: String, limit: Int = 18): String {
        val facts = recall(feature, limit)
        if (facts.isEmpty()) return ""
        return buildString {
            appendLine("WHAT SAIF AI ALREADY KNOWS (learned across the whole app, feature: $feature). Use it naturally, never announce it:")
            facts.forEach { appendLine("- ${it.key}: ${it.value}") }
        }.trim()
    }

    /** Everything the automation engine remembers, merged with the app's own facts (for the AI MEMORY screen). */
    suspend fun fullRecallText(): String {
        val mine = SmartConversationMemory.getAllMemoriesList()
        val theirs = try { com.jarvis.assistant.JarvisApp.instance.memoryManager.getWhatDoYouRemember() } catch (_: Throwable) { "" }
        return buildString {
            if (mine.isNotEmpty()) {
                appendLine("SAIF AI memory (${mine.size} facts):")
                mine.forEach { appendLine("- ${it.key}: ${it.value}") }
            }
            if (theirs.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                appendLine("Live conversation memory bank:")
                append(theirs.trim())
            }
        }.trim()
    }

    /** How many facts each feature contributed, for the Settings screen. */
    fun perFeatureCounts(): List<Pair<String, Int>> {
        val all = SmartConversationMemory.getAllMemoriesList().map { it.key }.toSet()
        return Features.ALL.map { f -> f to (tags[f]?.count { it in all } ?: 0) }.filter { it.second > 0 }
    }

    /** Detected language of [text] when AUTO LANGUAGE is on, else null (the caller keeps its own language). */
    fun detectLanguage(text: String): String? {
        if (!_autoLanguage.value || text.isBlank()) return null
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return "Hindi"
        val lower = text.lowercase()
        val hinglish = HINGLISH_MARKERS.count { lower.contains(it) }
        val words = lower.split(Regex("[^a-z]+")).filter { it.length > 1 }
        if (words.isEmpty()) return null
        return if (hinglish >= 2 || (hinglish >= 1 && words.size <= 6)) "Hinglish" else "English"
    }

    private val HINGLISH_MARKERS = listOf(
        "kya", "hai", "kaise", "karo", "karna", "mera", "tum", "aap", "batao", "chahiye", "nahi", "haan",
        "acha", "theek", "bahut", "thoda", "kab", "kahan", "kaun", "kyun", "bhi", "aur", "lekin", "toh", "ji"
    )

    fun clearAll() {
        SmartConversationMemory.clearAll()
        tags.clear()
        saveTags()
        _lastFactCount.value = 0
    }
}
