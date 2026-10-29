package com.example.studio.export

import com.example.studio.director.SCENE_GRAPH_MARKER
import com.example.studio.director.SceneGraph
import com.example.studio.director.SceneJson
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.VoiceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QualityCheckerTest {

    private val graph = SceneJson.encodeForTimeline(SceneGraph())
    private fun scene(id: String, startS: Long, durS: Long, code: String = graph) =
        CodeSceneItem(id, "lane_code_1", startS * 1_000_000, durS * 1_000_000, title = id, htmlCode = code, animationType = SCENE_GRAPH_MARKER)
    private fun voice(startS: Long, durS: Long, uri: String? = "file:///a.wav") = VoiceItem("v$startS", "lane_voice_1", startS * 1_000_000, durS * 1_000_000, audioUri = uri)
    private fun text(id: String, startS: Double, durS: Double) = TextItem(id, "lane_text_1", (startS * 1e6).toLong(), (durS * 1e6).toLong(), text = id)
    private fun project(vararg items: com.example.studio.model.TimelineItem) = StudioProject.createEmpty("q").copy(items = items.toList())

    @Test fun `empty project is an error`() {
        assertEquals(Severity.ERROR, QualityChecker.check(StudioProject.createEmpty("e")).single().severity)
    }

    @Test fun `a healthy project has no issues`() {
        val p = project(scene("a", 0, 3), scene("b", 3, 3), voice(0, 3), voice(3, 3))
        assertTrue(QualityChecker.check(p).toString(), QualityChecker.check(p).isEmpty())
    }

    @Test fun `static stretch, black gap, missing audio file and overlapping text are all flagged`() {
        val p = project(scene("long", 0, 9), scene("after", 10, 3), voice(0, 3, uri = "file:///gone.wav"), text("one", 0.0, 2.0), text("two", 1.0, 2.0))
        val issues = QualityChecker.check(p) { uri -> !uri.contains("gone") }
        val all = issues.joinToString(" | ") { "${it.severity}: ${it.message}" }
        assertTrue(all, all.contains("stays on screen 9 s"))
        assertTrue(all, all.contains("Black gap"))
        assertTrue(all, issues.any { it.severity == Severity.ERROR && it.message.contains("Missing voice-over") })
        assertTrue(all, all.contains("Text overlaps"))
        assertTrue(all, all.contains("Silent stretch") || all.contains("Silent for the last"))
    }

    @Test fun `legacy html scenes are blocked because the exporter cannot draw them`() {
        val p = project(scene("old", 0, 3, code = "<html><body>old</body></html>"), voice(0, 3))
        assertTrue(QualityChecker.check(p).any { it.severity == Severity.ERROR && it.message.contains("cannot render") })
    }

    @Test fun `errors are listed before warnings`() {
        val p = project(scene("long", 0, 9), voice(0, 3, uri = null))
        val sev = QualityChecker.check(p).map { it.severity }
        assertEquals(sev.sortedBy { it }, sev)
    }
}
