package com.example.studio.director

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DirectorLogicTest {

    // ---- speech timing / duration -------------------------------------------------
    @Test fun wordsPerMinuteDependsOnLanguageAndPace() {
        assertTrue(SpeechTiming.wordsPerMinute("English", null) > SpeechTiming.wordsPerMinute("Hindi", null))
        assertTrue(SpeechTiming.wordsPerMinute("English", "fast") > SpeechTiming.wordsPerMinute("English", "slow"))
        assertEquals(150, SpeechTiming.wordsPerMinute("english", null))
    }
    @Test fun targetWordsScalesWithDuration() {
        assertEquals(150, SpeechTiming.targetWords(60, "English", null))
        assertEquals(135, SpeechTiming.targetWords(60, "Hindi", null))
    }
    @Test fun customDurationParsing() {
        assertEquals(150, SpeechTiming.parseDuration("2:30"))
        assertEquals(260, SpeechTiming.parseDuration("4 min 20 sec"))
        assertEquals(90, SpeechTiming.parseDuration("90 sec"))
        assertEquals(180, SpeechTiming.parseDuration("3 min"))
        assertNull(SpeechTiming.parseDuration("soon"))
    }
    @Test fun unrealisticDurationIsExplained() {
        val tooLong = SpeechTiming.feasibilityWarning(30, 600, "English", null)
        assertNotNull(tooLong); assertTrue(tooLong!!.contains("shorten"))
        val tooShort = SpeechTiming.feasibilityWarning(600, 100, "English", null)
        assertNotNull(tooShort)
        assertNull(SpeechTiming.feasibilityWarning(60, 150, "English", null))
    }

    // ---- script classification ------------------------------------------------------
    @Test fun scriptClassification() {
        assertEquals(ScriptMode.TOPIC_NEEDS_RESEARCH, DirectorQuestions.classifyScript("How to grow on YouTube"))
        assertEquals(ScriptMode.BRIEF_NEEDS_CLARIFICATION, DirectorQuestions.classifyScript("video"))
        val long = (1..200).joinToString(" ") { "word$it" }
        assertEquals(ScriptMode.FULL_SCRIPT_PRESERVE, DirectorQuestions.classifyScript(long))
        assertEquals(ScriptMode.FULL_SCRIPT_PRESERVE, DirectorQuestions.classifyScript("Scene 1: hello\nScene 2: bye"))
    }

    // ---- question cards -------------------------------------------------------------
    @Test fun zeroToVideoAsksDurationFirstAndOffersPresetsAndCustom() {
        val b = DirectorQuestions.seed(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO), "How to grow on YouTube")
        val q = DirectorQuestions.next(b)!!
        assertEquals("duration", q.key)
        assertEquals(4, q.options.size)
        assertTrue(q.allowCustom)
    }
    @Test fun shortFormPromptInfersAspectAndUsesShortDurations() {
        val b = DirectorQuestions.seed(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO), "make a reel about black holes")
        assertEquals("9:16", b.aspect)
        assertTrue(DirectorQuestions.next(b)!!.options.contains("30 seconds"))
    }
    @Test fun answersAreAccumulatedAndNeverDiscarded() {
        var b = DirectorQuestions.seed(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO), "Explain how neural networks learn")
        b = DirectorQuestions.apply(b, "duration", "2:30")
        b = DirectorQuestions.apply(b, "language", "Hinglish")
        b = DirectorQuestions.apply(b, "style", "Documentary")
        assertEquals(150, b.durationSec); assertEquals("Hinglish", b.narrationLanguage); assertEquals("Documentary", b.style)
        assertEquals("Explain how neural networks learn", b.prompt)
        assertEquals("Hinglish", b.subtitleLanguage)   // separate language settings default from narration but stay independent
    }
    @Test fun doesNotAskEverythingAndStopsWhenBriefIsSufficient() {
        var b = DirectorQuestions.seed(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO), "Explain how neural networks learn in English")
        var asked = 0
        while (true) { val q = DirectorQuestions.next(b) ?: break; asked++; b = DirectorQuestions.apply(b, q.key, q.options.first()); assertTrue(asked < 12) }
        assertTrue("asked $asked", asked <= 8)
    }
    @Test fun skippingOptionalQuestionIsRemembered() {
        var b = DirectorQuestions.seed(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO), "topic about space exploration")
        b = DirectorQuestions.apply(b, "duration", "1 minute"); b = DirectorQuestions.apply(b, "language", "English")
        val q = DirectorQuestions.next(b)!!; assertTrue(q.optional)
        b = DirectorQuestions.skip(b, q.key)
        assertNotEquals(q.key, DirectorQuestions.next(b)?.key)
    }
    @Test fun preservedScriptDoesNotAskDurationAndKeepsWording() {
        val script = (1..220).joinToString(" ") { "w$it" }
        val b = DirectorQuestions.seed(DirectorBrief(DirectorWorkflow.ZERO_TO_VIDEO), script)
        assertEquals(ScriptMode.FULL_SCRIPT_PRESERVE, b.scriptMode); assertEquals(script, b.userScript)
        assertNotEquals("duration", DirectorQuestions.next(b)?.key)
    }
    @Test fun workflowRulesAboutNarration() {
        assertTrue(DirectorWorkflow.VIDEO_TO_EDITING.forbidsGeneratedNarration)
        assertTrue(DirectorWorkflow.VOICE_OVER_TO_VIDEO.forbidsGeneratedNarration)
        assertTrue(DirectorWorkflow.VIDEO_EDIT_WITH_VOICE.generatesNarration)
        assertTrue(DirectorWorkflow.ZERO_TO_VIDEO.generatesNarration)
        // VIDEO TO EDITING never asks for voice settings
        val keys = DirectorWorkflow.values().filter { it.forbidsGeneratedNarration }.flatMap { wf ->
            var b = DirectorBrief(wf, prompt = "edit"); val ks = mutableListOf<String>()
            while (true) { val q = DirectorQuestions.next(b) ?: break; ks += q.key; b = DirectorQuestions.apply(b, q.key, q.options.first()); if (ks.size > 15) break }
            ks
        }
        assertFalse(keys.contains("voiceStyle"))
    }

    // ---- provider capabilities & fallback ---------------------------------------------
    private fun entry(id: String, vararg caps: Capability, key: Boolean = true) = ProviderEntry(id, id, key, "m", caps.toSet())

    @Test fun routingUsesRealCapabilities() {
        val ps = listOf(entry("text", Capability.CHAT), entry("vision", Capability.CHAT, Capability.IMAGE_UNDERSTANDING))
        assertEquals("vision", (ProviderRegistry.route(ps, Capability.IMAGE_UNDERSTANDING) as RouteResult.Routed).provider.id)
        assertTrue(ProviderRegistry.route(ps, Capability.WEB_SEARCH) is RouteResult.Unavailable)
        assertTrue(ProviderRegistry.route(listOf(entry("nokey", Capability.CHAT, key = false)), Capability.CHAT) is RouteResult.Unavailable)
    }
    @Test fun deviceSpeechNeedsNoCredentials() {
        assertTrue(ProviderRegistry.route(listOf(ProviderRegistry.deviceSpeech()), Capability.SPEECH_SYNTHESIS) is RouteResult.Routed)
    }
    @Test fun failureClassification() {
        assertEquals(FailureKind.QUOTA, ProviderRegistry.classifyFailure("HTTP 429 rate limit"))
        assertEquals(FailureKind.AUTH, ProviderRegistry.classifyFailure("401 Unauthorized"))
        assertEquals(FailureKind.TRANSIENT, ProviderRegistry.classifyFailure("socket timeout"))
    }
    @Test fun fallbackMovesToNextProviderOnQuotaAndRetriesTransient() = runBlocking {
        val a = entry("a", Capability.CHAT); val b = entry("b", Capability.CHAT)
        val calls = mutableListOf<String>()
        val r = ProviderRegistry.runWithFallback(listOf(a, b), sleep = {}) { p ->
            calls += p.id
            if (p.id == "a") Result.failure(Exception("429 quota exceeded")) else Result.success("ok")
        }
        assertEquals("ok", r.getOrNull()); assertEquals(listOf("a", "b"), calls)

        var n = 0
        val r2 = ProviderRegistry.runWithFallback(listOf(a), maxRetriesPerProvider = 2, sleep = {}) { n++; if (n < 3) Result.failure(Exception("timeout")) else Result.success(n) }
        assertEquals(3, r2.getOrNull())
    }
    @Test fun retriesAreBounded() = runBlocking {
        var n = 0
        val r = ProviderRegistry.runWithFallback(listOf(entry("a", Capability.CHAT)), maxRetriesPerProvider = 2, sleep = {}) { n++; Result.failure<String>(Exception("timeout")) }
        assertTrue(r.isFailure); assertEquals(3, n)
    }
    @Test fun noProviderReportsClearError() = runBlocking {
        val r = ProviderRegistry.runWithFallback<String>(emptyList()) { Result.success("x") }
        assertTrue(r.exceptionOrNull()!!.message!!.contains("No compatible provider"))
    }

    // ---- manifest validation & timing -------------------------------------------------
    private fun scene(id: String, s: Long, e: Long, graph: SceneGraph? = null) = Scene(id, s, e, narration = "n", graph = graph)

    @Test fun validManifestHasNoFatalIssues() {
        val m = SceneManifest(scenes = listOf(scene("a", 0, 3000), scene("b", 3000, 8000)))
        assertTrue(SceneValidator.fatal(SceneValidator.validate(m, 8000)).isEmpty())
    }
    @Test fun overlapsDuplicatesAndBadDurationsAreFatal() {
        val overlap = SceneManifest(scenes = listOf(scene("a", 0, 3000), scene("b", 2000, 5000)))
        assertTrue(SceneValidator.fatal(SceneValidator.validate(overlap)).any { it.message.contains("overlaps") })
        val dup = SceneManifest(scenes = listOf(scene("a", 0, 1000), scene("a", 1000, 2000)))
        assertTrue(SceneValidator.fatal(SceneValidator.validate(dup)).any { it.message.contains("Duplicate") })
        assertTrue(SceneValidator.fatal(SceneValidator.validate(SceneManifest(scenes = listOf(scene("a", 500, 500))))).isNotEmpty())
    }
    @Test fun unsupportedPrimitivesAndColorsAreRejected() {
        val g = SceneGraph(elements = listOf(Element("e1", "webview"), Element("e2", "rect", color = "red")))
        val issues = SceneValidator.fatal(SceneValidator.validate(SceneManifest(scenes = listOf(scene("a", 0, 3000, g)))))
        assertTrue(issues.any { it.message.contains("Unsupported element") }); assertTrue(issues.any { it.message.contains("Invalid color") })
    }
    @Test fun totalDurationToleranceIsReported() {
        val m = SceneManifest(scenes = listOf(scene("a", 0, 10_000)))
        assertTrue(SceneValidator.validate(m, 60_000).any { it.message.contains("differs from target") })
    }
    @Test fun scenesAreRetimedFromMeasuredNarrationNotEqualSplits() {
        val m = SceneManifest(scenes = listOf(scene("a", 0, 5000), scene("b", 5000, 10000), scene("c", 10000, 15000)))
        val r = SceneTiming.retime(m, mapOf("a" to 2000L, "b" to 9000L, "c" to 4000L))
        assertEquals(listOf(0L, 2350L, 11700L), r.scenes.map { it.startMs })
        assertEquals(2350L, r.scenes[0].durationMs); assertEquals(9350L, r.scenes[1].durationMs)
        r.scenes.zipWithNext().forEach { (x, y) -> assertEquals(x.endMs, y.startMs) }   // contiguous, never overlapping
        assertTrue(r.scenes.all { s -> s.durationMs >= (mapOf("a" to 2000L, "b" to 9000L, "c" to 4000L)[s.id]!!) })  // never cuts speech
    }
    @Test fun transcriptSegmentsDriveSceneBoundaries() {
        val b = SceneTiming.fromSegments(listOf(500L to 2000L, 2600L to 5000L, 5200L to 9000L), 9500L)
        assertEquals(listOf(0L to 2600L, 2600L to 5200L, 5200L to 9500L), b)
    }
    @Test fun frameConversionRoundTrips() {
        assertEquals(90, SceneTiming.msToFrame(3000, 30)); assertEquals(3000, SceneTiming.frameToMs(90, 30))
        assertEquals(1, SceneTiming.msToFrame(33, 30))
    }

    // ---- audio / video analysis -----------------------------------------------------------
    @Test fun silenceDetectionFindsRealGaps() {
        val loud = ShortArray(16_000) { (if (it % 2 == 0) 12000 else -12000).toShort() }   // 1 s tone-ish
        val quiet = ShortArray(16_000 * 2)                                                   // 2 s silence
        val pcm = loud + quiet + loud
        val s = SignalAnalysis.silences(pcm)
        assertEquals(1, s.size)
        assertTrue(s[0].startMs in 950..1100); assertTrue(s[0].endMs in 2950..3100)
    }
    @Test fun shortPausesAreNotCut() {
        val pcm = ShortArray(16_000) { 9000 } + ShortArray(4000) + ShortArray(16_000) { 9000 }   // 250 ms gap
        assertTrue(SignalAnalysis.silences(pcm).isEmpty())
    }
    @Test fun sceneChangeDetection() {
        val dark = IntArray(1024) { 10 }; val bright = IntArray(1024) { 240 }
        assertEquals(listOf(2000L), SignalAnalysis.detectChanges(listOf(0L, 1000L, 2000L), listOf(dark, dark, bright)))
    }
    @Test fun keepRangesRemoveCutsAndTinyFragments() {
        val keeps = SignalAnalysis.keepRanges(10_000, listOf(TimeRange(2000, 4000), TimeRange(4200, 8000)), minKeepMs = 600)
        assertEquals(listOf(TimeRange(0, 2000), TimeRange(8000, 10_000)), keeps)   // 200 ms sliver dropped
    }

    // ---- asset verification --------------------------------------------------------------
    private val imgReq = AssetRequest("a1", "image", "neural network diagram")
    private val goodImg = MediaProbe(true, "image", 1920, 1080, sizeBytes = 100_000)

    @Test fun magicByteSniffingRejectsRenamedText() {
        assertEquals("unknown", MediaInspector.sniffKind("TELEGRAM_ASSET_CACHE:x".toByteArray().copyOf(16)))
        assertEquals("image", MediaInspector.sniffKind(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals("image", MediaInspector.sniffKind(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
    }
    @Test fun invalidOrWrongTypeAssetsAreRejected() {
        assertFalse(AssetDecision.decide(MediaProbe(false, "unknown", error = "x"), imgReq, null, null).accepted)
        assertFalse(AssetDecision.decide(MediaProbe(true, "audio", durationMs = 5000), imgReq, null, null).accepted)
        assertFalse(AssetDecision.decide(MediaProbe(true, "image", 100, 80), imgReq, null, null).accepted)
    }
    @Test fun visualMismatchIsRejectedAndMatchAccepted() {
        assertFalse(AssetDecision.decide(goodImg, imgReq, null, VisualCheck(false, 2, listOf("wrong subject"))).accepted)
        assertFalse(AssetDecision.decide(goodImg, imgReq, null, VisualCheck(true, 5)).accepted)
        assertFalse(AssetDecision.decide(goodImg, imgReq, null, VisualCheck(true, 9, listOf("unsafe"), unsafe = true)).accepted)
        val ok = AssetDecision.decide(goodImg, imgReq, null, VisualCheck(true, 8))
        assertTrue(ok.accepted); assertTrue(ok.visuallyVerified)
    }
    @Test fun technicalOnlyAcceptanceIsLabelledUnverified() {
        val v = AssetDecision.decide(goodImg, imgReq, null, null)
        assertTrue(v.accepted); assertFalse(v.visuallyVerified); assertTrue(v.reason.contains("NOT verified"))
    }
    @Test fun visualCheckParsing() {
        val v = VisualCheck.parse("```json\n{\"match\":true,\"score\":7,\"issues\":[]}\n```")!!
        assertTrue(v.match); assertEquals(7, v.score)
        assertNull(VisualCheck.parse("I think it looks fine"))
    }
}
