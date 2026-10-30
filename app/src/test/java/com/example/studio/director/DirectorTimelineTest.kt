package com.example.studio.director

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.TrackType
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectorTimelineTest {

    private val manifestJson = """
        ```json
        {"title":"AI","fps":30,"scenes":[
         {"id":"s1","startMs":0,"endMs":4000,"narration":"Neural networks learn from data.","visualObjective":"layered network","caption":"Neural networks",
          "transition":"fade","assets":[{"id":"a1","kind":"image","description":"brain neurons"}],
          "graph":{"bgTop":"#0B0A14","bgBottom":"#1B1330","camera":{"zoomFrom":1,"zoomTo":1.1},
           "elements":[{"id":"n","type":"nodes","x":0.5,"y":0.5,"w":0.8,"h":0.4,"values":[3,4,2],"color":"#7B2BFF","color2":"#00E5FF","anims":[{"prop":"progress","from":0,"to":1,"startMs":0,"endMs":1500}]},
                       {"id":"t","type":"text","text":"Learning","x":0.5,"y":0.1,"size":0.06,"color":"#FFFFFF"}]}},
         {"id":"s2","startMs":4000,"endMs":9000,"narration":"Loss goes down over time.","visualObjective":"loss curve","caption":"Loss curve",
          "graph":{"elements":[{"id":"c","type":"linechart","values":[5,3,2,1.5,1],"color":"#00E5FF"}]}}]}
        ```""".trimIndent()

    private fun parsed() = SceneJson.parseManifest(manifestJson).getOrThrow()

    @Test fun parsesFencedJsonIntoManifest() {
        val m = parsed()
        assertEquals(2, m.scenes.size); assertEquals(9000, m.totalMs)
        assertEquals("nodes", m.scenes[0].graph!!.elements[0].type)
        assertEquals(1, m.scenes[0].assets.size)
        assertTrue(SceneValidator.fatal(SceneValidator.validate(m, 9000)).isEmpty())
    }
    @Test fun invalidJsonIsAFailureNotAFakeManifest() {
        assertTrue(SceneJson.parseManifest("sorry, I can't").isFailure)
        assertTrue(SceneJson.parseManifest("{\"title\":\"x\"}").isFailure)
    }
    @Test fun sceneGraphRoundTripsThroughTimelinePayload() {
        val g = parsed().scenes[0].graph!!
        val back = SceneJson.decodeFromTimeline(SceneJson.encodeForTimeline(g))!!
        assertEquals(g.elements.size, back.elements.size); assertEquals(g.elements[0].anims, back.elements[0].anims)
        assertEquals(g.camera.zoomTo, back.camera.zoomTo, 0.001f)
        assertNull(SceneJson.decodeFromTimeline("<html>legacy gsap scene</html>"))
    }
    @Test fun manifestSerializationIsStable() {
        val m = parsed(); val again = SceneJson.parseManifest(SceneJson.manifestToJson(m).toString()).getOrThrow()
        assertEquals(m.scenes.map { it.id to it.endMs }, again.scenes.map { it.id to it.endMs })
        assertEquals(m.scenes[0].narration, again.scenes[0].narration)
    }

    @Test fun animationResolveIsDeterministicAndEased() {
        val e = Element("x", "rect", anims = listOf(Anim("alpha", 0f, 1f, 1000, 2000, "linear")))
        assertEquals(0f, SceneRenderer.resolve(e, "alpha", 1f, 0), 0.001f)
        assertEquals(0.5f, SceneRenderer.resolve(e, "alpha", 1f, 1500), 0.001f)
        assertEquals(1f, SceneRenderer.resolve(e, "alpha", 1f, 5000), 0.001f)
        assertEquals(SceneRenderer.resolve(e, "alpha", 1f, 1234), SceneRenderer.resolve(e, "alpha", 1f, 1234), 0f)
        assertTrue(SceneRenderer.ease(0.5f, "easeOut") > 0.5f)
    }

    // ---- timeline assembly -------------------------------------------------------------------
    private val narration = listOf(NarrationClip("s1", "file:///a.wav", 3500, "n1"), NarrationClip("s2", "file:///b.wav", 4200, "n2"))

    @Test fun zeroToVideoBuildsEditableItemsOnCorrectLanes() {
        val (p, problems) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), parsed(), narration, emptyList())
        assertEquals(2, p.items.count { it is CodeSceneItem }); assertEquals(2, p.items.count { it is VoiceItem }); assertEquals(2, p.items.count { it is TextItem })
        p.items.forEach { assertTrue(p.lanes.any { l -> l.id == it.laneId && l.trackType == it.trackType }); assertTrue(it.durationUs > 0) }
        assertEquals(9_000_000L, p.totalDurationUs)
        assertTrue(problems.none { it.contains("Overlap") })
        // each generated scene carries a decodable, renderable definition
        p.items.filterIsInstance<CodeSceneItem>().forEach { assertNotNull(SceneJson.decodeFromTimeline(it.htmlCode)) }
    }
    @Test fun regenerationReplacesAiItemsWithoutTouchingUserItems() {
        val user = TextItem("u1", "lane_text_1", 0, 1_000_000, "mine", origin = "manual")
        val base = StudioProject.createEmpty("t").let { it.copy(items = listOf(user)) }
        val (p1, _) = TimelineAssembler.assemble(base, parsed(), narration, emptyList())
        val (p2, _) = TimelineAssembler.assemble(p1, parsed(), narration, emptyList())
        assertEquals(p1.items.size, p2.items.size); assertTrue(p2.items.any { it.id == "u1" })
    }
    @Test fun aSingleSceneEditKeepsItsOriginalTimestamp() {
        val m = parsed()
        val (full, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), m, narration, emptyList())
        val replaced = TimelineAssembler.clearAi(full, setOf("s2"))
        val one = m.copy(scenes = listOf(m.scenes[1]))
        val (edited, _) = TimelineAssembler.assemble(replaced, one, narration.filter { it.sceneId == "s2" }, emptyList(), replaceExisting = false)
        val scene = edited.items.filterIsInstance<CodeSceneItem>().first { it.aiWhy!!.startsWith("director:s2:") }
        assertEquals(4_000_000L, scene.startUs)
        assertEquals(9_000_000L, scene.startUs + scene.durationUs)
        assertEquals(9_000_000L, edited.totalDurationUs)
    }
    @Test fun narrationLongerThanSceneIsReported() {
        val (_, problems) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), parsed(), listOf(NarrationClip("s1", "file:///a.wav", 9000, "x")), emptyList())
        assertTrue(problems.any { it.contains("longer than the scene") })
    }
    @Test fun unusableAssetIsReportedNotAddedAndNoFakeMediaAppears() {
        val bad = ResolvedAsset("s1", "a1", "image", "", "telegram", false)
        val (p, problems) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), parsed(), narration, listOf(bad))
        assertTrue(problems.any { it.contains("no usable file") }); assertEquals(0, p.items.count { it.trackType == TrackType.VISUAL })
    }
    @Test fun validVerifiedAssetBecomesImageItemBoundToItsScene() {
        val ok = ResolvedAsset("s1", "a1", "image", "/data/x.png", "telegram:42", true, "verified")
        val (p, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), parsed(), narration, listOf(ok))
        val img = p.items.filter { it.trackType == TrackType.VISUAL }
        assertEquals(1, img.size); assertEquals(0L, img[0].startUs); assertTrue(img[0].aiWhy!!.startsWith("director:s1:"))
    }
    @Test fun voiceOverWorkflowKeepsOriginalAudioAndAddsNoGeneratedNarration() {
        val (p, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), parsed(), emptyList(), emptyList(), voiceOverUri = "content://vo/1")
        val voices = p.items.filterIsInstance<VoiceItem>()
        assertEquals(1, voices.size); assertEquals("content://vo/1", voices[0].audioUri)
        assertEquals("manual", voices[0].origin); assertEquals(9_000_000L, voices[0].durationUs)
        assertTrue(voices.none { it.origin == "ai" })
    }
    @Test fun retimeAfterSceneChangeShiftsDependentItems() {
        val (p, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), parsed(), narration, emptyList())
        val m = parsed(); val longer = m.copy(scenes = listOf(m.scenes[0].copy(endMs = 6000), m.scenes[1].copy(startMs = 6000, endMs = 11000)))
        val r = TimelineAssembler.retimeAfterChange(p, longer)
        val s2 = r.items.filterIsInstance<CodeSceneItem>().first { it.aiWhy!!.startsWith("director:s2:") }
        assertEquals(6_000_000L, s2.startUs); assertEquals(5_000_000L, s2.durationUs)
    }
    @Test fun validateFindsOverlapsAndMissingMedia() {
        val base = StudioProject.createEmpty("t")
        val a = VoiceItem("a", "lane_voice_1", 0, 2_000_000, audioUri = "file:///a")
        val b = VoiceItem("b", "lane_voice_1", 1_000_000, 2_000_000, audioUri = null)
        val issues = TimelineAssembler.validate(base.copy(items = listOf(a, b)))
        assertTrue(issues.any { it.contains("Overlap") }); assertTrue(issues.any { it.contains("missing voice audio") })
    }
    @Test fun footageEditMapsSourceTimeThroughKeptRanges() {
        val keep = listOf(FootageRange("u", 0, 2000, 1080, 1920, "a"), FootageRange("u", 5000, 9000, 1080, 1920, "b"))
        assertEquals(1000L, TimelineAssembler.sourceToTimelineMs(keep, 1000))
        assertEquals(2500L, TimelineAssembler.sourceToTimelineMs(keep, 5500))
        assertNull(TimelineAssembler.sourceToTimelineMs(keep, 3000))      // cut out
    }
    @Test fun videoEditingKeepsSourceUntouchedAndReversible() {
        val keep = listOf(FootageRange("content://v/1", 1000, 4000, 1080, 1920, "kept"), FootageRange("content://v/1", 6000, 9000, 1080, 1920, "kept"))
        val m = SceneManifest(scenes = listOf(Scene("footage", 0, 6000)))
        val (p, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), m, emptyList(), emptyList(), footage = keep, captions = false)
        val clips = p.items.filterIsInstance<VideoItem>().sortedBy { it.startUs }
        assertEquals(2, clips.size); assertEquals(1_000_000L, clips[0].sourceInUs); assertEquals(0L, clips[0].startUs)
        assertEquals(3_000_000L, clips[1].startUs); assertTrue(clips.all { it.mediaUri == "content://v/1" })
        assertTrue(p.items.none { it is VoiceItem })                       // no narration in VIDEO TO EDITING
        assertEquals(0, TimelineAssembler.clearAi(p).items.size)           // one call removes the whole AI edit
    }
    @Test fun addVoiceAtPlacesNarrationOnVoiceLane() {
        val p = TimelineAssembler.addVoiceAt(StudioProject.createEmpty("t"), 2500, "file:///n.wav", 1800, "hello")
        val v = p.items.single() as VoiceItem; assertEquals(2_500_000L, v.startUs); assertEquals(1_800_000L, v.durationUs)
    }

    // ---- research parsing, state recovery -----------------------------------------------------
    @Test fun tavilyAndBraveResultsParseAndHttpOnlyUrlsAreDropped() {
        val t = WebResearch.parse("Tavily", """{"results":[{"title":"A","url":"https://a.com","content":"x","published_date":"2026-01-02"},{"title":"B","url":"http://b.com","content":"y"}]}""")
        assertEquals(1, t.size); assertEquals("2026-01-02", t[0].publishedDate)
        val b = WebResearch.parse("Brave", """{"web":{"results":[{"title":"C","url":"https://c.com","description":"z","age":"2 days ago"}]}}""")
        assertEquals("C", b[0].title)
        assertTrue(WebResearch.digest(t).startsWith("[1] A"))
    }
    @Test fun directorStateSurvivesRestart() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = DirectorStore(ctx)
        var b = DirectorQuestions.seed(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO), "Explain black holes")
        b = DirectorQuestions.apply(b, "duration", "3 min"); b = DirectorQuestions.apply(b, "language", "Hindi")
        val ui = DirectorUi(DirectorWorkflow.ZERO_TO_VIDEO, b, listOf(ChatTurn(fromUser = true, text = "Explain black holes")),
            script = "Script text here", scriptApproved = true, manifest = parsed())
        store.save("proj-1", ui)
        val loaded = store.load("proj-1", DirectorWorkflow.ZERO_TO_VIDEO)!!
        assertEquals(180, loaded.brief.durationSec); assertEquals("Hindi", loaded.brief.narrationLanguage)
        assertEquals("Explain black holes", loaded.brief.prompt); assertEquals("Script text here", loaded.script)
        assertTrue(loaded.scriptApproved); assertEquals(2, loaded.manifest!!.scenes.size)
        assertNull(store.load("proj-2", DirectorWorkflow.ZERO_TO_VIDEO))
    }
    @Test fun serviceRefusesNarrationInForbiddenWorkflows() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val svc = DirectorServices(ctx)
        listOf(DirectorWorkflow.VIDEO_TO_EDITING, DirectorWorkflow.VOICE_OVER_TO_VIDEO).forEach { wf ->
            val r = runCatching { kotlinx.coroutines.runBlocking { svc.synthNarration(DirectorBrief(wf), parsed()) } }
            assertTrue("$wf must refuse", r.exceptionOrNull() is IllegalStateException)
        }
    }
}
