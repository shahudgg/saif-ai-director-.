package com.example.studio.director

import android.content.Context
import com.example.studio.audio.SfxSynth
import com.example.studio.composer.Beat
import com.example.studio.composer.Intent
import java.io.File

/** Picks a transition per beat (never the same twice in a row) and plans a few well-placed sound cues. */
object TransitionPlanner {
    private val pools = mapOf(
        Intent.HOOK to listOf("flash", "zoomBlur", "pageflip"),
        Intent.BIG_STAT to listOf("zoomBlur", "depthWipe", "flash"),
        Intent.LIST to listOf("slideLeft", "pageflip", "slideUp"),
        Intent.COMPARISON to listOf("cubeSpin", "slideLeft", "glitchDissolve"),
        Intent.PROCESS_STEPS to listOf("slideLeft", "depthWipe", "pageflip"),
        Intent.TIMELINE to listOf("slideLeft", "wipe", "push"),
        Intent.QUOTE to listOf("fade", "iris", "dissolve"),
        Intent.CHART_BAR to listOf("slideUp", "wipe", "zoomBlur"),
        Intent.CHART_LINE to listOf("wipe", "slideUp", "push"),
        Intent.NETWORK_DIAGRAM to listOf("zoomBlur", "iris", "glitch"),
        Intent.MAP_LIKE_ZOOM to listOf("iris", "zoomBlur", "spin"),
        Intent.MYTH_VS_FACT to listOf("glitchDissolve", "whip", "flash"),
        Intent.QUESTION to listOf("iris", "zoomBlur", "slideUp"),
        Intent.CTA to listOf("flash", "zoomBlur", "slideUp"),
        Intent.STATEMENT to listOf("push", "cubeSpin", "depthWipe", "pageflip", "glitchDissolve")
    )

    fun pick(beat: Beat, previous: String?): String {
        if (beat.index == 0) return "none"
        val pool = pools[beat.intent] ?: listOf("fade")
        val options = pool.filter { it != previous }.ifEmpty { pool }
        return options[beat.index % options.size]
    }
}

object SfxPlanner {
    private const val MAX_CUES_PER_SCENE = 4

    fun cuesFor(beat: Beat, transition: String): List<AudioCue> {
        val d = beat.durationMs
        val cues = ArrayList<AudioCue>()
        if (transition != "none" && transition != "fade" && transition != "dissolve")
            cues += AudioCue(if (transition.startsWith("glitch")) "glitch" else "whoosh", "$transition transition", 0L, if (transition.startsWith("glitch")) 280L else 450L)
        when (beat.intent) {
            Intent.HOOK -> cues += AudioCue("hit", "hook impact", 60L, 600L)
            Intent.BIG_STAT -> { cues += AudioCue("riser", "build-up to number", 0L, 800L); cues += AudioCue("hit", "number lands", 780L.coerceAtMost(d - 300), 600L) }
            Intent.LIST, Intent.PROCESS_STEPS -> beat.items.take(3).forEachIndexed { i, _ -> cues += AudioCue("pop", "item ${i + 1}", 240L + i * 220L, 160L) }
            Intent.COMPARISON -> { cues += AudioCue("pop", "left side", 300L, 160L); cues += AudioCue("ding", "versus", 620L, 700L) }
            Intent.QUESTION -> cues += AudioCue("ding", "question", 120L, 700L)
            Intent.CTA -> cues += AudioCue("ding", "call to action", 240L, 700L)
            Intent.MYTH_VS_FACT -> { cues += AudioCue("glitch", "myth struck", (d * 0.2).toLong(), 280L); cues += AudioCue("ding", "fact", (d * 0.45).toLong(), 700L) }
            Intent.CHART_BAR, Intent.CHART_LINE -> cues += AudioCue("tick", "data draws", 200L, 90L)
            Intent.NETWORK_DIAGRAM -> cues += AudioCue("riser", "signal flow", 100L, 900L)
            Intent.TIMELINE -> beat.numberTokens.take(3).forEachIndexed { i, _ -> cues += AudioCue("tick", "milestone ${i + 1}", 300L + i * 280L, 90L) }
            else -> cues += AudioCue("pop", "key phrase", 180L, 160L)
        }
        return cues.filter { it.atMs in 0 until d }.take(MAX_CUES_PER_SCENE)
    }
}

/** Writes procedural SFX to cache and returns, per "sceneId:cueIndex", the file and its length. Library sounds win when provided. */
object SfxFiles {
    fun render(context: Context, manifest: SceneManifest, library: (kind: String) -> String? = { null }): Map<String, Pair<String, Long>> {
        val dir = File(context.cacheDir, "sfx").also { it.mkdirs() }
        val out = LinkedHashMap<String, Pair<String, Long>>()
        manifest.scenes.forEach { s ->
            s.cues.forEachIndexed { i, c ->
                if (c.kind !in SfxSynth.KINDS) return@forEachIndexed
                val lib = library(c.kind)
                if (lib != null) { out["${s.id}:$i"] = lib to c.durationMs; return@forEachIndexed }
                val ms = c.durationMs.coerceIn(60L, 1500L)
                val f = File(dir, "${c.kind}_${ms}.wav")
                if (!f.exists()) runCatching { f.writeBytes(SfxSynth.toWav(SfxSynth.generate(c.kind, ms, 1))) }
                if (f.exists()) out["${s.id}:$i"] = "file://${f.absolutePath}" to ms
            }
        }
        return out
    }
}
