package com.example.studio.composer

import com.example.studio.director.SpeechTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BeatPlannerTest {

    private val script = """
        Kya aapko pata hai ki ChatGPT sirf 5 din me 1 million users tak pahunch gaya tha? Ye history ka sabse tez growth tha.
        Iske peeche ek simple idea hai: transformer architecture. Ye model har word ko baaki sabhi words ke saath compare karta hai, aur attention se decide karta hai ki kaun sa word kitna zaroori hai.
        Pehle text ko tokens me todta hai, phir har token ka matlab numbers me badalta hai, phir attention lagata hai, aur aakhir me agla word predict karta hai.
        Myth: AI insaan ki tarah sochta hai. Fact: wo sirf agla token predict karta hai, bahut tezi se.
        2017 me transformers aaye, 2020 me GPT-3 aaya, aur 2023 me chatbots har ghar me pahunch gaye.
        Toh agar aapko ye samajh aaya, toh follow karo aur comment me batao ki agla topic kya ho.
    """.trimIndent()

    @Test fun `a 58 second video gets many short beats instead of a few long scenes`() {
        val outline = BeatPlanner.outline(script, 58_000L, "hinglish", "normal")
        assertTrue("only ${outline.size} beats", outline.size >= 12)
        val avg = outline.sumOf { it.endMs - it.startMs } / outline.size
        assertTrue("average beat ${avg} ms", avg in 2_000L..4_800L)
        outline.forEach { assertTrue("beat ${it.id} too short", it.endMs - it.startMs >= BeatPlanner.MIN_BEAT_MS) }
    }

    @Test fun `beats are contiguous and cover the whole script text`() {
        val outline = BeatPlanner.outline(script, 58_000L, "hinglish", "normal")
        outline.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        assertEquals(0L, outline.first().startMs)
        assertTrue(outline.last().endMs >= 58_000L)
        val rebuilt = outline.joinToString(" ") { it.narration }.replace(Regex("\\s+"), " ")
        assertEquals(SpeechTiming.countWords(script), SpeechTiming.countWords(rebuilt))
    }

    @Test fun `no beat is a single dangling word`() {
        BeatPlanner.outline(script, 58_000L, "hinglish", "normal").forEach { assertTrue("'${it.narration}'", SpeechTiming.countWords(it.narration) >= 3) }
    }

    @Test fun `beat count never exceeds the cap`() {
        val long = "Yeh ek bahut lamba sentence hai jo kai baar repeat hota hai. ".repeat(300)
        assertTrue(BeatPlanner.outline(long, 300_000L, "hinglish", "normal", maxBeats = 40).size <= 40)
    }

    @Test fun `directive parser tolerates fences, bare arrays and junk`() {
        val fenced = "```json\n{\"beats\":[{\"i\":1,\"intent\":\"hook\",\"headline\":\"AI ka raaz\",\"keywords\":[\"ai\"]},{\"i\":2,\"intent\":\"BIG STAT\",\"headline\":\"73%\"}]}\n```"
        val ds = BeatPlanner.parseDirectives(fenced)
        assertEquals(2, ds.size)
        assertEquals(Intent.HOOK, ds[0].intent)
        assertEquals(Intent.BIG_STAT, ds[1].intent)
        assertEquals(1, BeatPlanner.parseDirectives("[{\"i\":3,\"intent\":\"LIST\",\"items\":[\"a\",\"b\"]}]").size)
        assertTrue(BeatPlanner.parseDirectives("sorry I cannot do that").isEmpty())
        assertNull(BeatPlanner.parseDirectives("[{\"i\":1,\"intent\":\"DANCE\"}]")[0].intent)
    }

    @Test fun `a weak model cannot invent a chart the narration has no numbers for`() {
        val outline = BeatPlanner.outline("Ye ek simple baat hai jisme koi number nahi hai bas ek idea hai jo samajhna zaroori hai.", 6_000L)
        val d = BeatPlanner.Directive(2, Intent.CHART_BAR, "Fake chart", null, emptyList(), emptyList())
        val beat = BeatPlanner.toBeat(outline[0], 1, 5, d) { null }
        assertTrue(beat.intent != Intent.CHART_BAR)
    }

    @Test fun `model headline that is too long or corrupted is replaced by the local one`() {
        val o = BeatPlanner.outline("Transformer architecture ne sab kuch badal diya hai aaj ke daur me.", 4_000L)[0]
        val bad = BeatPlanner.Directive(2, Intent.STATEMENT, "this headline is much longer than six words allowed here", null, emptyList(), emptyList())
        val beat = BeatPlanner.toBeat(o, 1, 5, bad) { null }
        assertTrue(SpeechTiming.countWords(beat.headline) <= 6)
        val corrupt = BeatPlanner.Directive(2, Intent.STATEMENT, "ok <unk> text", null, emptyList(), emptyList())
        val beat2 = BeatPlanner.toBeat(o, 1, 5, corrupt) { if (it.contains("<unk>")) "corrupted" else null }
        assertTrue(!beat2.headline.contains("<unk>"))
    }

    @Test fun `first beat is always a hook`() {
        val o = BeatPlanner.outline(script, 58_000L, "hinglish", "normal")
        val beat = BeatPlanner.toBeat(o[0], 0, o.size, BeatPlanner.Directive(1, Intent.LIST, "x", null, emptyList(), emptyList())) { null }
        assertEquals(Intent.HOOK, beat.intent)
        assertNotNull(beat)
    }
}
