package com.example.studio.export

import com.example.studio.director.SceneJson
import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.TimelineItem
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem

enum class Severity { ERROR, WARNING }

data class QualityIssue(val severity: Severity, val message: String, val atUs: Long? = null)

/**
 * Pre-export checks on the real timeline. ERRORs would break or ruin the export (missing files, scenes the exporter
 * cannot render); WARNINGs are things a viewer would notice (static stretches, overlapping text, silence).
 */
object QualityChecker {
    private const val STATIC_LIMIT_US = 6_000_000L
    private const val SILENCE_LIMIT_US = 5_000_000L
    private const val GAP_LIMIT_US = 300_000L

    private fun mediaMissing(uri: String?, exists: (String) -> Boolean) = uri.isNullOrBlank() || !exists(uri)
    private fun fmt(us: Long): String { val s = us / 1_000_000; return "%d:%02d".format(s / 60, s % 60) }

    fun check(p: StudioProject, exists: (String) -> Boolean = { true }): List<QualityIssue> {
        val out = ArrayList<QualityIssue>()
        if (p.items.isEmpty()) return listOf(QualityIssue(Severity.ERROR, "The timeline is empty — add a clip or generate a video first."))
        val total = p.totalDurationUs

        // --- missing / unreadable media and unrenderable scenes
        p.items.forEach { i ->
            when (i) {
                is VideoItem -> if (mediaMissing(i.mediaUri, exists)) out += QualityIssue(Severity.ERROR, "Missing video file: ${i.label}", i.startUs)
                is ImageItem -> if (mediaMissing(i.mediaUri, exists)) out += QualityIssue(Severity.ERROR, "Missing image file: ${i.label}", i.startUs)
                is VoiceItem -> if (mediaMissing(i.audioUri, exists)) out += QualityIssue(Severity.ERROR, "Missing voice-over audio at ${fmt(i.startUs)}", i.startUs)
                is AudioItem -> if (mediaMissing(i.audioUri, exists)) out += QualityIssue(Severity.ERROR, "Missing audio file: ${i.label}", i.startUs)
                is CodeSceneItem -> if (SceneJson.decodeFromTimeline(i.htmlCode) == null)
                    out += QualityIssue(Severity.ERROR, "Scene '${i.label}' is an old HTML scene the exporter cannot render. Regenerate or remove it.", i.startUs)
                else -> {}
            }
        }

        // --- visual coverage: black gaps and static stretches
        val visual = p.items.filter { it is CodeSceneItem || it is VideoItem || it is ImageItem }.sortedBy { it.startUs }
        var cursor = 0L
        visual.forEach { v ->
            if (v.startUs - cursor > GAP_LIMIT_US) out += QualityIssue(Severity.WARNING, "Black gap of ${(v.startUs - cursor) / 1000} ms at ${fmt(cursor)}", cursor)
            cursor = maxOf(cursor, v.startUs + v.durationUs)
        }
        if (total - cursor > GAP_LIMIT_US && visual.isNotEmpty()) out += QualityIssue(Severity.WARNING, "No visuals for the last ${(total - cursor) / 1000} ms", cursor)
        visual.filter { (it is CodeSceneItem || it is ImageItem) && it.durationUs > STATIC_LIMIT_US }.forEach {
            out += QualityIssue(Severity.WARNING, "'${it.label}' stays on screen ${it.durationUs / 1_000_000} s — viewers may drop off. Split it or add a cutaway.", it.startUs)
        }

        // --- overlapping captions / text
        val texts = p.items.filterIsInstance<TextItem>().sortedBy { it.startUs }
        for (a in texts.indices) for (b in a + 1 until texts.size) {
            if (texts[b].startUs >= texts[a].startUs + texts[a].durationUs - 100_000L) break
            out += QualityIssue(Severity.WARNING, "Text overlaps at ${fmt(texts[b].startUs)}: “${texts[a].text.take(18)}” and “${texts[b].text.take(18)}”", texts[b].startUs)
        }

        // --- audio presence
        val voices = p.items.filter { it is VoiceItem || (it is AudioItem && !it.isSfx) }
        val hasAnyAudio = p.items.any { it is VoiceItem || it is AudioItem || (it is VideoItem && it.volume > 0f) }
        if (!hasAnyAudio) out += QualityIssue(Severity.WARNING, "The video has no audio at all.")
        else if (voices.isNotEmpty()) {
            var c = 0L
            voices.sortedBy { it.startUs }.forEach { v ->
                if (v.startUs - c > SILENCE_LIMIT_US) out += QualityIssue(Severity.WARNING, "Silent stretch of ${(v.startUs - c) / 1_000_000} s at ${fmt(c)}", c)
                c = maxOf(c, v.startUs + v.durationUs)
            }
            if (total - c > SILENCE_LIMIT_US) out += QualityIssue(Severity.WARNING, "Silent for the last ${(total - c) / 1_000_000} s", c)
        }
        return out.sortedWith(compareBy({ it.severity }, { it.atUs ?: 0L }))
    }
}
