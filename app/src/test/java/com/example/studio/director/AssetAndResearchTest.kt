package com.example.studio.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetAndResearchTest {
    private fun scene(i: Int, text: String, obj: String = "STATEMENT: x", ms: Long = 3000L, locked: Boolean = false) =
        Scene(id = "s$i", startMs = i * 3000L, endMs = i * 3000L + ms, narration = text, visualObjective = obj, locked = locked)

    @Test fun plannerAsksForAboutOneSceneInThreeNeverTwoInARow() {
        val scenes = (0 until 9).map { scene(it, "Stock market crash explained simply today") }
        val planned = AssetPlanner.plan(scenes)
        val idx = planned.indices.filter { planned[it].assets.isNotEmpty() }
        assertTrue("got $idx", idx.size in 1..3)
        assertTrue(idx.zipWithNext().all { (a, b) -> b - a >= 2 })
    }

    @Test fun plannerSkipsChartsLockedAndShortScenes() {
        val scenes = listOf(scene(0, "Revenue growth by quarter", "CHART_BAR: x"), scene(1, "Market crash story", locked = true), scene(2, "Quick market note", ms = 1500L))
        assertTrue(AssetPlanner.plan(scenes).all { it.assets.isEmpty() })
    }

    @Test fun plannerKeepsRequestsTheModelAlreadyMade() {
        val own = scene(0, "Something").copy(assets = listOf(AssetRequest("m1", "image", "a cat")))
        assertEquals("m1", AssetPlanner.plan(listOf(own)).first().assets.first().id)
    }

    @Test fun memeWordsAskForPicturesOtherwiseVideo() {
        assertEquals("image", AssetPlanner.kindFor("Ye meme dekh ke sab hasi nahi rok paye"))
        assertEquals("video", AssetPlanner.kindFor("The central bank raised interest rates again"))
    }

    @Test fun telegramRequestMessageListsEveryNeed() {
        val m = AssetRequester.message("My video", listOf("video: stock market (scene 2)", "image: bull and bear (scene 5)"))
        assertTrue(m.contains("1. video: stock market (scene 2)") && m.contains("2. image: bull and bear (scene 5)") && m.contains("ASSET_INDEX"))
    }

    @Test fun wikipediaResponseBecomesHttpsSources() {
        val json = """{"query":{"search":[{"title":"Stock market","snippet":"A <span>stock market</span> is the aggregation of buyers &amp; sellers","timestamp":"2026-09-01T10:00:00Z"}]}}"""
        val s = WebResearch.parseWikipedia(json)
        assertEquals(1, s.size)
        assertTrue(s[0].url.startsWith("https://en.wikipedia.org/wiki/Stock_market"))
        assertTrue(!s[0].snippet.contains("<") && s[0].snippet.contains("& sellers"))
    }

    @Test fun newsRssItemsAreParsedAndNonHttpsLinksDropped() {
        val xml = """<rss><channel>
            <item><title><![CDATA[Sensex jumps 500 points]]></title><link>https://news.example.com/a</link><pubDate>Mon, 26 Oct 2026 08:00:00 GMT</pubDate><description>&lt;a&gt;Markets rally&lt;/a&gt;</description></item>
            <item><title>Insecure</title><link>http://news.example.com/b</link></item>
        </channel></rss>"""
        val s = WebResearch.parseNewsRss(xml)
        assertEquals(1, s.size)
        assertEquals("Sensex jumps 500 points", s[0].title)
        assertEquals("Markets rally", s[0].snippet)
    }

    @Test fun garbageResearchResponsesGiveEmptyLists() {
        assertTrue(WebResearch.parseWikipedia("nope").isEmpty())
        assertTrue(WebResearch.parseNewsRss("<html/>").isEmpty())
    }

    @Test fun speechTextIsCleanedAndHinglishUsesIndianEnglish() {
        assertEquals("Risk and reward hai asli game.", DeviceSpeech.prepare("**Risk** & reward hai asli game [1]", false).replace("  ", " "))
        assertEquals("en", DeviceSpeech.localeFor("Hinglish", "Ye paisa kaise kaam karta hai").language)
        assertEquals("IN", DeviceSpeech.localeFor("Hinglish", "Ye paisa kaise kaam karta hai").country)
        assertEquals("hi", DeviceSpeech.localeFor("Hindi", "यह पैसा कैसे काम करता है").language)
    }
}
