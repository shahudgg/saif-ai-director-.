package com.example.studio.director

import com.example.studio.model.CodeSceneItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TrackType
import com.example.studio.model.VoiceItem
import com.example.studio.render.RenderPlan
import com.example.ui.components.TelegramAssetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 2: continuous voice, assets drawn together with scenes, deep research and the AI asset editor. */
class Round2Test {
    private fun sc(i: Int, text: String, start: Long, end: Long) = Scene(id = "s$i", startMs = start, endMs = end, narration = text, visualObjective = "STATEMENT: x",
        graph = SceneGraph(elements = listOf(Element("t$i", "text", text = "hi"))))

    // ---- voice never cut inside a sentence ----
    @Test fun halfSentencesAreVoicedTogetherAndSentenceEndsStartNewGroups() {
        val scenes = listOf(sc(1, "Bitcoin ne 2009 me, jab kisi ko pata nahi tha,", 0, 3000), sc(2, "ek anjaan coder ne ise banaya.", 3000, 6000), sc(3, "Phir 2010 aaya.", 6000, 8000))
        val g = VoiceGroups.build(scenes)
        assertEquals(listOf(listOf("s1", "s2"), listOf("s3")), g.map { grp -> grp.map { it.id } })
        assertTrue(VoiceGroups.endsSentence("Kya baat hai?")); assertTrue(VoiceGroups.endsSentence("यह क्या है।")); assertFalse(VoiceGroups.endsSentence("aur phir,"))
    }

    @Test fun groupedRetimingSharesTheRealClipLengthByWordsAndKeepsLaterScenesInOrder() {
        val m = SceneManifest(scenes = listOf(sc(1, "one two three four", 0, 3000), sc(2, "five six seven eight nine ten.", 3000, 6000), sc(3, "Next scene here.", 6000, 9000)))
        val clips = listOf(NarrationClip("s1", "file:///a.wav", 6000, "x", listOf("s1", "s2")), NarrationClip("s3", "file:///b.wav", 2500, "y"))
        val r = SceneTiming.retimeGrouped(m, clips, padMs = 170L)
        val s = r.scenes
        assertEquals(0L, s[0].startMs); assertEquals(s[0].endMs, s[1].startMs); assertEquals(s[1].endMs, s[2].startMs)
        assertEquals(6170L, s[1].endMs)                                   // clip + pad is shared, nothing is cut
        assertTrue(s[1].durationMs > s[0].durationMs)                      // more words => more time
        assertEquals(2670L, s[2].durationMs)
    }

    @Test fun oneClipPlaysAcrossAllScenesOfItsSentence() {
        val m = SceneManifest(scenes = listOf(sc(1, "alpha beta gamma,", 0, 2500), sc(2, "delta epsilon zeta.", 2500, 5000)))
        val clips = listOf(NarrationClip("s1", "file:///a.wav", 4800, "alpha beta gamma, delta epsilon zeta.", listOf("s1", "s2")))
        val (p, problems) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), m, clips, emptyList())
        val voices = p.items.filterIsInstance<VoiceItem>()
        assertEquals(1, voices.size)
        assertEquals(4_800_000L, voices[0].durationUs)                    // not capped to the first scene (2.5 s)
        assertTrue(problems.none { it.contains("longer than the scene") })
        val moved = TimelineAssembler.retimeAfterChange(p, m)
        assertEquals(4_800_000L, moved.items.filterIsInstance<VoiceItem>()[0].durationUs)
    }

    // ---- assets and scenes visible together ----
    @Test fun sceneWithAssetBecomesATransparentOverlayDrawnAboveTheMedia() {
        val m = SceneManifest(scenes = listOf(sc(1, "Stock market crash today.", 0, 3000)))
        val asset = ResolvedAsset("s1", "lib-s1", "video", "/data/clip.mp4", "telegram:A1", true, "AI editor: fits")
        val (p, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), m, emptyList(), listOf(asset))
        val scene = p.items.filterIsInstance<CodeSceneItem>().single()
        assertEquals(SCENE_OVERLAY_MARKER, scene.animationType)
        val g = SceneJson.decodeFromTimeline(scene.htmlCode)!!
        assertEquals("#00000000", g.bgTop); assertEquals("asset_scrim", g.elements.first().id)
        val plan = RenderPlan.at(p, 500_000L)
        val zScene = plan.visibleLayers.first { it.item is CodeSceneItem }.zIndex
        val zMedia = plan.visibleLayers.first { it.item.trackType == TrackType.VIDEO }.zIndex
        assertTrue("scene $zScene must be above media $zMedia", zScene > zMedia)
    }

    @Test fun sceneWithoutAssetKeepsItsOpaqueBackdropAndLowLayer() {
        val m = SceneManifest(scenes = listOf(sc(1, "Plain scene.", 0, 3000)))
        val (p, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), m, emptyList(), emptyList())
        val scene = p.items.filterIsInstance<CodeSceneItem>().single()
        assertEquals(SCENE_GRAPH_MARKER, scene.animationType)
        assertNotEquals("#00000000", SceneJson.decodeFromTimeline(scene.htmlCode)!!.bgTop)
        assertEquals(10, RenderPlan.at(p, 100_000L).visibleLayers.first { it.item is CodeSceneItem }.zIndex)
    }

    @Test fun overlayIsIdempotentAndLibrarySoundIsPlacedInsideItsScene() {
        val g = SceneGraph(elements = listOf(Element("a", "text")))
        assertEquals(SceneOverlay.apply(g), SceneOverlay.apply(SceneOverlay.apply(g)))
        val m = SceneManifest(scenes = listOf(sc(1, "Big number lands now.", 0, 4000)))
        val sfx = ResolvedAsset("s1", "sfx-0", "audio", "/data/hit.mp3", "telegram:S1", true, "AI editor: impact", atMs = 1200, durMs = 700)
        val (p, _) = TimelineAssembler.assemble(StudioProject.createEmpty("t"), m, emptyList(), listOf(sfx))
        val a = p.items.first { it.trackType == TrackType.AUDIO }
        assertEquals(1_200_000L, a.startUs); assertEquals(700_000L, a.durationUs)
    }

    // ---- AI asset editor ----
    private fun item(id: String, type: String, title: String, cat: String = "b_roll", kw: List<String> = emptyList()) =
        TelegramAssetItem(assetId = id, title = title, type = type, extension = "mp4", category = cat, subject = title, searchKeywords = kw, tags = emptyList(), userId = "all", fileId = "F_$id", messageId = 1, fileSize = 1000)

    @Test fun curatorKeepsOnlyRealKnownChoicesAndOneVisualPerScene() {
        val items = listOf(item("v1", "video", "Red stock chart falling"), item("v2", "video", "Money rain"), item("a1", "audio", "Whoosh fast", "sfx"))
        val entries = AssetCurator.shortlist(AssetCurator.usable(items), emptyList())
        assertEquals(listOf("A1", "A2", "A3"), entries.map { it.key })
        val scenes = listOf(sc(1, "Market crash", 0, 3000), sc(2, "Money printing", 3000, 6000))
        val raw = """```json
            {"picks":[{"scene":"s1","asset":"A1","reason":"crash"},{"scene":"s1","asset":"A2","reason":"dup scene"},{"scene":"s2","asset":"A1","reason":"dup asset"},{"scene":"s9","asset":"A2","reason":"no scene"},{"scene":"s2","asset":"A99","reason":"unknown"}],
             "sfx":[{"scene":"s1","asset":"A3","atMs":99999,"durationMs":9000,"reason":"whoosh"},{"scene":"s1","asset":"A1","atMs":0,"reason":"not audio"}],
             "missing":[{"scene":"s2","type":"video","need":"slow motion printing press with cash"}]}
            ```"""
        val plan = AssetCurator.parse(raw, entries, scenes)
        assertEquals(listOf("s1" to "A1"), plan.picks.map { it.sceneId to it.key })
        assertEquals(1, plan.sfx.size); assertTrue(plan.sfx[0].atMs <= 2850L); assertEquals(1500L, plan.sfx[0].durMs)
        assertEquals("video: slow motion printing press with cash (scene 2)", AssetCurator.needLines(plan.needs, scenes).single())
    }

    @Test fun curatorGivesGarbageRepliesNoPicksAndUnusableLibraryItemsAreFiltered() {
        assertTrue(AssetCurator.parse("sorry I cannot", emptyList(), emptyList()).picks.isEmpty())
        val bad = item("x", "video", "no file").copy(fileId = "FILE_ID_1")
        val big = item("y", "video", "too big").copy(fileSize = 50L * 1024 * 1024)
        val other = item("z", "video", "someone else").copy(userId = "u2")
        assertTrue(AssetCurator.usable(listOf(bad, big, other), "default").isEmpty())
    }

    // ---- deep research ----
    @Test fun pageParagraphsSkipScriptsAndNavigationAndFactsPreferNumbersAndTopic() {
        val html = "<html><nav><p>Home About Contact us now please click here for more information</p></nav><script><p>var a=1</p></script>" +
            "<p>In 2026 India's UPI handled 18.5 billion transactions in a single month, a record for digital payments worldwide.</p><p>Short.</p>" +
            "<p>UPI is very popular and people like it quite a lot these days because it is easy to use.</p></html>"
        val ps = ResearchText.paragraphs(html)
        assertEquals(2, ps.size)
        val facts = ResearchText.topFacts(1, ps, setOf("upi", "payments"), 2026, 3)
        assertEquals(1, facts.size); assertTrue(facts[0].text.contains("18.5 billion"))
    }

    @Test fun queriesAndDuckDuckGoResultsAreParsedTolerantly() {
        assertEquals(listOf("upi growth 2026", "upi fraud cases"), ResearchText.parseQueries("```json\n[\"upi growth 2026\", \"upi fraud cases\"]\n```"))
        assertEquals(listOf("one query here", "second query here"), ResearchText.parseQueries("1. one query here\n2) second query here"))
        assertEquals(5, ResearchText.fallbackQueries("UPI", 2026).size)
        val html = """<a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa&rut=1">UPI &amp; you</a><a class="result__snippet" href="x">Fast <b>payments</b></a>"""
        val r = ResearchText.parseDuckDuckGo(html)
        assertEquals(1, r.size); assertEquals("https://example.com/a", r[0].url); assertEquals("UPI & you", r[0].title)
    }

    @Test fun sceneFactsPickTheMatchingFactsFromTheDigest() {
        val digest = "CONCRETE FACTS (x):\nF1. UPI handled 18.5 billion transactions in one month. [1]\nF2. Bitcoin was created in 2009 by Satoshi. [2]\n\nSOURCES:\n[1] a"
        val facts = SceneFacts.parse(digest)
        assertEquals(2, facts.size); assertFalse(facts[0].contains("[1]"))
        assertEquals(listOf("Bitcoin was created in 2009 by Satoshi."), SceneFacts.forScene(facts, "Bitcoin ne 2009 me sab badal diya", 1))
        assertTrue(SceneFacts.forScene(facts, "totally unrelated words", 2).isEmpty())
    }

    // ---- every video different ----
    @Test fun storyShapeAndSceneLayoutsDependOnTheVideoSeedButAreStable() {
        assertEquals(StoryStyles.forSeed("42"), StoryStyles.forSeed("42"))
        assertTrue((0 until 10).map { StoryStyles.forSeed("$it") }.distinct().size >= 8)
        val a = (0 until 8).map { SceneArchetypes.forScene("11", it) }; val b = (0 until 8).map { SceneArchetypes.forScene("97", it) }
        assertEquals(a, (0 until 8).map { SceneArchetypes.forScene("11", it) })
        assertNotEquals(a, b)
        assertEquals(8, a.distinct().size)                                // no layout repeats inside a video
    }

    @Test fun sceneBatchPromptCarriesFactsLayoutsAndTheQualityBar() {
        val b = DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO, prompt = "UPI payments", answers = mapOf("styleSeed" to "5"))
        val o = SceneOutline("s1", 0, 3000, "UPI handled 18.5 billion transactions.")
        val p = DirectorPrompts.sceneBatchV2(b, listOf(0 to o), 6, 30, "") { listOf("UPI handled 18.5 billion transactions in one month.") }
        assertTrue(p.contains("suggested layout:") && p.contains("facts you may show:") && p.contains("QUALITY BAR") && p.contains("exactly these ids"))
    }
}
