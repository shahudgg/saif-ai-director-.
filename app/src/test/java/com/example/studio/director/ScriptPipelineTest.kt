package com.example.studio.director

import com.example.studio.composer.Strategy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptPipelineTest {

    @Test fun `hook scorer prefers a curiosity hook over a greeting`() {
        val good = "Aapko pata hai ChatGPT ne sirf 5 din me 1 million users kaise banaye?"
        val bad = "Hello dosto aaj hum baat karenge artificial intelligence ke baare me"
        assertTrue(HookScorer.score(good) > HookScorer.score(bad))
        assertEquals(good, HookScorer.best(listOf(bad, good, "")))
    }

    @Test fun `review flags long sentences, missing cta, weak hook and unsourced statistics`() {
        val script = "Welcome to this video about AI. " + "This is a very long sentence that keeps going and going without any real pause so that nobody can say it in one breath at all and it just continues for far too long now. " + "AI market will reach 80% by 2030."
        val r = ScriptCritic.review(script, targetWords = 140, hasResearch = false)
        val all = r.issues.joinToString(" | ")
        assertTrue(all, all.contains("too long to say"))
        assertTrue(all, all.contains("follow and comment"))
        assertTrue(all, all.contains("Too short"))
        assertTrue(all, all.contains("Statistics"))
    }

    @Test fun `a clean script with hook and cta has no issues`() {
        val sentences = listOf(
            "Model pehle text ko chhote tukdon me todta hai.",
            "Har tukde ko ek number mil jata hai.",
            "Phir attention batati hai ki kaun sa shabd zyada zaroori hai.",
            "Isse model poora context samajh pata hai.",
            "Layers ek ke upar ek judti jaati hain.",
            "Har layer pichli layer ki samajh ko gehra karti hai.",
            "Aakhir me model agla shabd predict karta hai.",
            "Yahi process baar baar dohraya jata hai."
        ).joinToString(" ")
        val script = "Aapko pata hai ye 1 cheez sab badal deti hai? $sentences Follow karo aur comment me batao ki agla topic kya ho."
        val r = ScriptCritic.review(script, targetWords = SpeechTiming.countWords(script), hasResearch = true, researchDigest = "")
        assertTrue(r.issues.toString(), r.issues.isEmpty())
    }

    @Test fun `auto fix appends a call to action and splits long sentences`() {
        val long = "Pehle hum dekhenge ki model kaise kaam karta hai, phir hum dekhenge ki attention kya hai, uske baad hum samjhenge ki tokens kaise bante hain, aur aakhir me hum dekhenge ki output kaise nikalta hai."
        val fixed = ScriptCritic.autoFix("Kya aap jaante hain? $long", 0, "Hinglish")
        assertTrue(fixed.contains("follow karo", ignoreCase = true))
        assertTrue(com.example.studio.composer.BeatPlanner.sentences(fixed).all { SpeechTiming.countWords(it) <= 26 })
    }

    @Test fun `auto fix trims an overlong script but keeps hook and ending`() {
        val body = (1..30).joinToString(" ") { "Ye middle sentence number $it ke baare me kuch detail batata hai." }
        val script = "Pehli line hook hai. $body Follow karo aur comment karo."
        val fixed = ScriptCritic.autoFix(script, targetWords = 120, language = "Hinglish")
        assertTrue(fixed.startsWith("Pehli line hook hai."))
        assertTrue(fixed.endsWith("comment karo."))
        assertTrue(SpeechTiming.countWords(fixed) <= 120 * 1.2)
    }

    @Test fun `voiceover file contains only spoken text`() {
        val raw = """
            ## Scene 1: Hook
            **Kya aapko pata hai?** [1] (pause) Ye sach hai [EDIT: reworded].
            - Hook: AI duniya badal raha hai 🚀
            Visit https://example.com for more.
            [SFX whoosh]
        """.trimIndent()
        val clean = VoiceoverScript.clean(raw)
        listOf("#", "*", "[1]", "[EDIT", "(pause)", "http", "🚀", "SFX", "Scene 1", "Hook:").forEach { assertFalse("'$it' left in: $clean", clean.contains(it)) }
        assertTrue(clean.contains("Kya aapko pata hai?"))
        assertTrue(clean.contains("AI duniya badal raha hai"))
    }

    @Test fun `pipeline picks the best hook, writes, and fixes the ending when the model forgets the cta`() = runBlocking {
        val brief = DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO, prompt = "How transformers work", narrationLanguage = "English", durationSec = 20)
        val replies = ArrayDeque(listOf(
            "1. Welcome to this video about transformers\n2. Did you know transformers read every word at once, not one by one?\n3. Hello guys today we learn AI",
            "Did you know transformers read every word at once, not one by one? " + (1..12).joinToString(" ") { "Sentence $it explains one more simple idea about attention clearly." }
        ))
        val pipeline = ScriptPipeline(ask = { Result.success(replies.removeFirstOrNull() ?: "") }, guard = { null })
        val out = pipeline.run(brief, null, SpeechTiming.targetWords(20, "English", null), Strategy.LIGHT).getOrThrow()
        assertTrue(out.hook!!.startsWith("Did you know"))
        assertTrue(out.script.startsWith("Did you know"))
        assertTrue(out.script.contains("follow", ignoreCase = true))
    }

    @Test fun `pipeline fails cleanly when the model only returns corrupted text`() = runBlocking {
        val brief = DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO, prompt = "x", narrationLanguage = "English")
        val pipeline = ScriptPipeline(ask = { Result.success("<unk> <unk> broken output that is long enough to pass the length check ok") }, guard = { ModelOutputGuard.reason(it) })
        assertTrue(pipeline.run(brief, null, 100, null).isFailure)
    }
}
