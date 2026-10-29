package com.example.studio.composer

import android.content.Context
import org.json.JSONObject

/** How much visual design work the configured model is trusted with. Layout is always local; this only changes what we ask. */
enum class Strategy {
    /** One tiny question per beat (intent + headline). Local composer does everything else. */
    LIGHT,
    /** Several beats per call: intent, headline, support, items. Local composer designs. */
    STANDARD,
    /** Model may draft full scene graphs; they are validated and repaired locally, with the composer as safety net. */
    STRONG
}

data class CapabilityProfile(
    val provider: String, val model: String, val strategy: Strategy,
    val jsonOk: Boolean, val hindiOk: Boolean, val latencyMs: Long, val checkedAt: Long, val notes: String
)

object CapabilityProbe {
    private const val PREFS = "saif_capability_profiles"
    private val strongHints = Regex("70b|72b|405b|gpt-?4|gpt-?5|o[1-9]\\b|claude|gemini[- ]?(1\\.5|2|3)[- ]?(pro|flash)?|sonnet|opus|deepseek|mixtral-8x22|command-r-plus|qwen.*(72|110)|llama-?3\\.[1-9].*(70|405)", RegexOption.IGNORE_CASE)
    private val weakHints = Regex("\\b[1-9]b\\b|\\b1[0-3]b\\b|mini|nano|lite|small|tiny|instant|haiku|gemma|phi-?[1-4]|flash-lite", RegexOption.IGNORE_CASE)

    /** Name-based guess used when no probe result is cached yet. */
    fun heuristic(provider: String, model: String): Strategy {
        val m = "$provider $model"
        return when {
            weakHints.containsMatchIn(model) && !strongHints.containsMatchIn(model) -> Strategy.LIGHT
            strongHints.containsMatchIn(m) -> Strategy.STRONG
            else -> Strategy.STANDARD
        }
    }

    /** Combines probe outcomes with the name heuristic (pure, unit-tested). */
    fun decide(jsonOk: Boolean, hindiOk: Boolean, latencyMs: Long, hint: Strategy): Strategy = when {
        !jsonOk -> Strategy.LIGHT
        !hindiOk -> if (hint == Strategy.LIGHT) Strategy.LIGHT else Strategy.STANDARD
        hint == Strategy.STRONG && latencyMs in 0..25_000 -> Strategy.STRONG
        hint == Strategy.LIGHT -> if (latencyMs in 0..8_000) Strategy.STANDARD else Strategy.LIGHT
        else -> Strategy.STANDARD
    }

    fun cached(context: Context, provider: String, model: String): CapabilityProfile? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("$provider|$model", null) ?: return null
        val o = JSONObject(raw)
        CapabilityProfile(provider, model, Strategy.valueOf(o.getString("strategy")), o.optBoolean("json"), o.optBoolean("hindi"),
            o.optLong("latency"), o.optLong("at"), o.optString("notes"))
    }.getOrNull()

    private fun save(context: Context, p: CapabilityProfile) {
        val o = JSONObject().put("strategy", p.strategy.name).put("json", p.jsonOk).put("hindi", p.hindiOk)
            .put("latency", p.latencyMs).put("at", p.checkedAt).put("notes", p.notes)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("${p.provider}|${p.model}", o.toString()).apply()
    }

    fun forget(context: Context, provider: String, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("$provider|$model").apply()
    }

    /**
     * Runs two tiny checks (valid JSON with an allowed enum; clean Hinglish) and stores the result per provider+model.
     * [ask] must return the raw model text. Any failure just lowers the strategy; it never blocks the video.
     */
    suspend fun probe(context: Context, provider: String, model: String, ask: suspend (String) -> Result<String>, guard: (String) -> String?): CapabilityProfile {
        cached(context, provider, model)?.let { return it }
        val t0 = System.currentTimeMillis()
        val r1 = ask("Reply with ONLY this JSON, filled in: {\"intent\":\"<one of HOOK,BIG_STAT,LIST>\",\"headline\":\"<max 3 words>\"} for the sentence: Almost 73% of companies already use AI.")
        val latency = System.currentTimeMillis() - t0
        val jsonOk = r1.getOrNull()?.let { raw ->
            runCatching {
                val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
                val o = JSONObject(raw.substring(s, e + 1))
                Intent.parse(o.optString("intent")) != null && o.optString("headline").isNotBlank()
            }.getOrDefault(false)
        } ?: false
        val r2 = ask("Likho sirf 5-6 shabd Hinglish (Roman Hindi) me: AI ne duniya badal di. Sirf wahi line likho.")
        val hindiOk = r2.getOrNull()?.let { it.isNotBlank() && guard(it) == null && it.length in 8..160 } ?: false
        val hint = heuristic(provider, model)
        val strat = decide(jsonOk, hindiOk, latency, hint)
        val prof = CapabilityProfile(provider, model, strat, jsonOk, hindiOk, latency, System.currentTimeMillis(),
            "json=$jsonOk hindi=$hindiOk latency=${latency}ms hint=$hint")
        if (r1.isSuccess) save(context, prof)   // do not cache a profile built from a network failure
        return prof
    }
}
