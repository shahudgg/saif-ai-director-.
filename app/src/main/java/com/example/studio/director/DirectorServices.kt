package com.example.studio.director

import android.content.Context
import com.example.studio.composer.BeatPlanner
import com.example.studio.composer.CapabilityProbe
import com.example.studio.composer.CapabilityProfile
import com.example.studio.composer.LayoutEngine
import com.example.studio.composer.LocalBeatAnalyzer
import com.example.studio.composer.Strategy
import com.example.studio.composer.SceneLint
import com.example.studio.composer.SceneUniqueness
import com.example.studio.composer.SceneVarietyComposer
import com.example.studio.composer.StyleDNA
import android.net.Uri
import com.example.llm.ImageGen
import com.example.llm.LlmRole
import com.example.llm.ProviderProfile
import com.example.studio.ai.AiClient
import com.example.studio.ai.AiProfile
import com.example.ui.components.TelegramAssetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ResearchOutcome(val digest: String?, val sources: List<ResearchSource>, val note: String)
data class NarrationOutcome(val clips: List<NarrationClip>, val manifest: SceneManifest, val failures: List<String>)
data class AssetOutcome(val assets: List<ResolvedAsset>, val unresolved: List<String>, val notes: List<String>, val needs: List<AssetNeed> = emptyList())

/** Round 11 — SAIF AGENT autopilot decision: a complete brief plus an optional draft script. */
data class AgentDecision(val brief: DirectorBrief, val script: String)
data class FootagePlan(val summary: String, val keep: List<FootageRange>, val overlays: List<Triple<Long, Long, String>>, val captions: Boolean, val usedAi: Boolean)

/** All real work behind the four workflows. Every method reports failure honestly instead of faking output. */
class DirectorServices(private val context: Context, private val activity: AgentActivity? = null) {

    fun providers(): List<ProviderEntry> {
        val prof = AiProfile.fromSettings(context)
        val profile = ProviderProfile.getById(prof.provider)
        // Round 11: one chain entry per saved key (so quota/auth on one key rotates to the next),
        // plus the provider's own fallback models on the first key.
        val keys = runCatching {
            com.example.data.local.ProviderSettingsManager.init(context)
            com.example.data.local.ProviderSettingsManager.loadState().apiKeys.filter { it.isNotBlank() }
        }.getOrDefault(listOf(prof.apiKey).filter { it.isNotBlank() })
        val models = (listOf(prof.activeModel) + profile.defaultFallbackModels).distinct()
        val chatEntries = when {
            keys.isEmpty() -> listOf(ProviderRegistry.fromProfile(profile, false, prof.activeModel))
            else -> keys.flatMapIndexed { ki, k ->
                val ms = if (ki == 0) models else listOf(prof.activeModel)
                ms.map { m -> ProviderRegistry.fromProfile(profile, true, m).copy(apiKey = k) }
            }
        }
        return chatEntries + listOf(ProviderRegistry.deviceSpeech(), ProviderRegistry.webSearch(WebResearch.isConfigured(context), WebResearch.provider(context)))
    }

    fun can(c: Capability) = ProviderRegistry.route(providers(), c) is RouteResult.Routed

    /** Calls the configured model. An HTTP 200 with an empty body is treated as a failure and retried with backoff and a smaller token budget. */
    private suspend fun llm(
        prompt: String, role: LlmRole = LlmRole.PLANNER, system: String = DirectorPrompts.SYSTEM_DIRECTOR,
        maxTokens: Int = 8000, traceKind: ActivityKind = ActivityKind.MODEL, traceTitle: String = "Director model response"
    ): Result<String> {
        val chain = ProviderRegistry.chain(providers(), Capability.CHAT)
        var requestNumber = 0
        return ProviderRegistry.runWithFallback(chain) {
            var last: Throwable? = null
            var result: Result<String>? = null
            for (attempt in 0..2) {
                currentCoroutineContext().ensureActive()
                requestNumber++
                val budget = if (attempt == 0) maxTokens else minOf(maxTokens, 3000)
                var traceId: Long? = null
                val r = try {
                    val base = AiProfile.fromSettings(context)
                    val callProfile = if (it.apiKey.isNotBlank()) AiProfile(provider = base.provider, apiKey = it.apiKey, activeModel = it.model.ifBlank { base.activeModel }, customBaseUrl = base.customBaseUrl) else base
                    val response = AiClient(callProfile).call(
                        role, prompt, system, maxTokens = budget,
                        onChunk = { chunk -> traceId?.let { id -> activity?.append(id, chunk) } },
                        onAttemptStart = { transportAttempt, model ->
                            traceId?.let { id -> activity?.finish(id, ActivityState.RETRIED, "Retrying after an incomplete attempt") }
                            traceId = activity?.begin(traceKind, "$traceTitle · request $requestNumber · attempt $transportAttempt", "Model: $model")
                        }
                    )
                    currentCoroutineContext().ensureActive()
                    response
                } catch (e: CancellationException) {
                    traceId?.let { id -> activity?.finish(id, ActivityState.CANCELLED) }
                    throw e
                }
                val usable = r.isSuccess && r.getOrThrow().isNotBlank()
                traceId?.let { id ->
                    activity?.finish(id, if (usable) ActivityState.SUCCEEDED else ActivityState.FAILED,
                        if (usable) null else "The request did not return usable output")
                }
                if (usable) { result = r; break }
                last = r.exceptionOrNull() ?: IllegalStateException("Empty response received from the model.")
                val msg = last.message.orEmpty()
                val emptyReply = msg.contains("Empty response", true) || r.isSuccess
                if (!emptyReply) break
                kotlinx.coroutines.delay(1500L * (attempt + 1))
            }
            result ?: Result.failure(last ?: IllegalStateException("The model returned no content."))
        }
    }

    /** Research digest of the last run (facts + sources), used again when scenes are designed so graphics show real facts. */
    var lastDigest: String? = null

    suspend fun research(b: DirectorBrief, onStep: (String) -> Unit = {}): ResearchOutcome {
        if (b.scriptMode == ScriptMode.FULL_SCRIPT_PRESERVE) return ResearchOutcome(null, emptyList(), "Complete script supplied — research skipped.")
        val keyed = WebResearch.isConfigured(context)
        val topic = b.prompt.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(140).ifBlank { b.prompt.take(80) }
        val askModel: suspend (String) -> Result<String> = { p -> llm(p, maxTokens = 500, traceKind = ActivityKind.RESEARCH, traceTitle = "Research planning") }
        val ask = if (can(Capability.CHAT)) askModel else null
        val deep = cancellableResult { DeepResearch.run(context, topic, keyed, ask, onStep) }.getOrNull()
        if (deep != null && deep.digest != null) { lastDigest = deep.digest; return deep }
        // Fallback: the simple snippet research (Wikipedia + news) when the deep run found nothing.
        val kw = LocalBeatAnalyzer.keywords(b.prompt, 6).joinToString(" ").ifBlank { b.prompt.take(80) }
        val all = LinkedHashMap<String, ResearchSource>()
        WebResearch.searchFree(kw).onSuccess { rs -> rs.forEach { all.putIfAbsent(it.url, it) } }
        if (all.isEmpty()) return deep ?: ResearchOutcome(null, emptyList(), "Search returned no usable HTTPS sources.")
        val list = all.values.take(8)
        lastDigest = WebResearch.digest(list)
        return ResearchOutcome(lastDigest, list, "Researched ${list.size} sources via Wikipedia and Google News.")
    }

    /** Last script-writing details (hook choice, rewrites, remaining review issues), shown to the user. */
    var lastScriptNote: String = ""

    suspend fun writeScript(b: DirectorBrief, digest: String?): Result<String> {
        val words = SpeechTiming.targetWords(b.durationSec ?: 60, b.narrationLanguage, b.pace)
        if (b.scriptMode == ScriptMode.FULL_SCRIPT_PRESERVE && b.userScript.isNotBlank()) return Result.success(b.userScript.trim()) // preserved verbatim
        val strategy = if (can(Capability.CHAT)) cancellableResult { capabilityProfile().strategy }.getOrNull() else null
        val pipeline = ScriptPipeline(ask = { p -> llm(p, traceKind = ActivityKind.SCRIPT, traceTitle = "Script model response") }, guard = { ModelOutputGuard.reason(it, b.narrationLanguage) })
        return pipeline.run(b, digest, words, strategy).map { o ->
            lastScriptNote = buildString {
                o.hook?.let { append("Hook: \"").append(it).append("\". ") }
                o.notes.forEach { append(it).append(' ') }
                if (o.issuesLeft.isNotEmpty()) append("Still to improve: ").append(o.issuesLeft.take(3).joinToString(" "))
            }.trim()
            o.script
        }
    }

    /**
     * Round 11 — SAIF AGENT autopilot. One goal in; the model decides the whole creative brief
     * (topic framing, language, duration, aspect, style, voice, pace, subtitles, asset policy) and
     * drafts the narration script. Parsed defensively; any model junk becomes an honest failure.
     */
    suspend fun agentDecide(goal: String, current: DirectorBrief): Result<AgentDecision> {
        val prompt = buildString {
            appendLine("You are the SAIF AGENT autopilot inside SAIF AI. The owner gave ONE goal: \"${goal.take(300)}\".")
            appendLine("Decide EVERYTHING needed for a short social video and reply with STRICT JSON only — no markdown fences, no commentary:")
            appendLine("""{"script":"full spoken narration script as plain text (no scene directions, no emojis)","audience":"who this is for","narrationLanguage":"ISO 639-1 code, e.g. en or hi - use hi when the goal is Hinglish","onScreenLanguage":"language for on-screen text","durationSec":60,"aspect":"9:16","style":"cinematic explainer|bold meme|minimal documentary|neon cyber|hand-drawn doodle|crazy suspense","subtitles":"Yes - big bold captions","assetPolicy":"Use my Telegram library","voiceStyle":"warm|energetic|calm|documentary","pace":"calm|normal|fast"}""")
            appendLine("Rules: a hook in the first sentence; script length must match durationSec (about 2.4 words per second); durationSec between 30 and 120; make it original — never copy a known script; if the goal names a style, aspect, duration or language, follow it exactly; aspect must be one of 9:16, 16:9, 1:1.")
        }
        val r = llm(prompt, role = LlmRole.PLANNER, system = "You are the SAIF AGENT. Reply with JSON only.",
            maxTokens = 8000, traceKind = ActivityKind.SCRIPT, traceTitle = "SAIF Agent decision")
        return r.fold({ raw -> parseAgentDecision(goal, current, raw) }, { Result.failure(it) })
    }

    private fun parseAgentDecision(goal: String, current: DirectorBrief, raw: String): Result<AgentDecision> = runCatching {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val o = JSONObject(cleaned)
        val script = o.optString("script", "").trim()
        val dur = o.optInt("durationSec", 60).coerceIn(30, 120)
        val aspect = o.optString("aspect", "9:16").ifBlank { "9:16" }
        val lang = o.optString("narrationLanguage", "en").ifBlank { "en" }
        val brief = current.copy(
            prompt = goal,
            scriptMode = ScriptMode.FULL_SCRIPT_PRESERVE,
            durationSec = dur, aspect = aspect,
            audience = o.optString("audience", "").ifBlank { null },
            narrationLanguage = lang,
            onScreenLanguage = o.optString("onScreenLanguage", lang).ifBlank { lang },
            subtitleLanguage = o.optString("onScreenLanguage", lang).ifBlank { lang },
            researchLanguage = lang,
            style = o.optString("style", "").ifBlank { null },
            subtitles = o.optString("subtitles", "Yes - big bold captions").ifBlank { null },
            assetPolicy = o.optString("assetPolicy", "Use my Telegram library").ifBlank { null },
            voiceStyle = o.optString("voiceStyle", "").ifBlank { null },
            pace = o.optString("pace", "normal").ifBlank { null },
            narrationMode = o.optString("narrationMode", "").ifBlank { null }
        )
        AgentDecision(brief, script)
    }

    /** Round 12 — Flow autopilot: N distinct English image prompts for a topic (one per scene), strict JSON array. */
    suspend fun flowImagePrompts(topic: String, n: Int, aspect: String?, style: String?): Result<List<String>> {
        val prompt = buildString {
            appendLine("You are the SAIF AGENT inside SAIF AI. Topic for a short video: " + topic.take(200) + ".")
            appendLine("Write " + n + " DISTINCT, detailed ENGLISH image-generation prompts (one per scene), aspect " + (aspect ?: "9:16") + ", style " + (style ?: "cinematic explainer") + ".")
            appendLine("Each prompt must name: subject, action, setting, lighting, camera angle and lens, colour mood. Every image must look DIFFERENT from the others. No text, watermarks or logos in the images.")
            appendLine("""{"prompts":["prompt 1","prompt 2","prompt 3"]}""")
            appendLine("Reply with STRICT JSON only — no markdown fences, no commentary.")
        }
        return llm(prompt, role = LlmRole.PLANNER, system = "You are the SAIF AGENT. Reply with JSON only.", maxTokens = 4000,
            traceKind = ActivityKind.SCRIPT, traceTitle = "Flow image prompts").map { raw ->
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONObject(cleaned).optJSONArray("prompts") ?: throw IllegalStateException("The agent returned no prompt list: " + raw.take(100))
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.length > 10 }.take(n)
        }
    }

    /** Round 13 — ASSEMBLER agent's eyes: does this image fit this scene? One-line FIT / NOT FIT verdict. */
    suspend fun visionVerdict(imagePath: String, sceneObjective: String, narration: String): Result<String> {
        val prof = AiProfile.fromSettings(context)
        if (!can(Capability.IMAGE_UNDERSTANDING) || prof.apiKey.isBlank()) return Result.failure(IllegalStateException("no vision provider configured"))
        val b64 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val src = android.graphics.BitmapFactory.decodeFile(imagePath) ?: return@runCatching null
                val maxE = maxOf(src.width, src.height).coerceAtLeast(1)
                val scale = 640f / maxE
                val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
                val bos = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, bos)
                android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
            }.getOrNull()
        } ?: return Result.failure(IllegalStateException("unreadable image"))
        return AiClient(prof).callWithImages(LlmRole.REVIEWER,
            "Does this image fit a video scene with objective: \" + sceneObjective.take(140) + \" and narration: \" + narration.take(140) + \"? Reply in ONE line: FIT or NOT FIT — short reason.",
            b64)
    }

    /** A stronger opening line than [current], or a failure explaining why nothing changed. */
    suspend fun betterHook(b: DirectorBrief, current: String): Result<String> {
        val pipeline = ScriptPipeline(ask = { p -> llm(p, maxTokens = 600, traceKind = ActivityKind.SCRIPT, traceTitle = "Hook options") }, guard = { ModelOutputGuard.reason(it, b.narrationLanguage) })
        val opts = pipeline.hookOptions(b, null).filter { !it.equals(current.trim(), true) }
        if (opts.isEmpty()) return Result.failure(IllegalStateException("The model did not return usable hook options."))
        val best = HookScorer.best(opts)!!
        return if (HookScorer.score(best) > HookScorer.score(current)) Result.success(best)
        else Result.failure(IllegalStateException("Your current hook already scores at least as high as the ${opts.size} new options, so I kept it."))
    }

    /** Applies ONE requested change to an existing script and keeps everything else (no hook rewrite, no new structure). */
    suspend fun reviseScript(b: DirectorBrief, current: String, instruction: String): Result<String> {
        val prompt = "Revise this video narration script. Apply ONLY the requested change and keep everything else exactly as is.\n" +
            "Change: $instruction\nLanguage: ${b.narrationLanguage ?: "same as the script"}\nOutput the full narration only, no labels, brackets or commentary.\n---\n$current"
        val r = llm(prompt, traceKind = ActivityKind.SCRIPT, traceTitle = "Script revision")
        if (r.isFailure) return Result.failure(r.exceptionOrNull()!!)
        val text = VoiceoverScript.clean(r.getOrThrow())
        if (text.length <= 40) return Result.failure(IllegalStateException("Model returned an empty script"))
        ModelOutputGuard.reason(text, b.narrationLanguage, requireScript = true)?.let { return Result.failure(IllegalStateException("the revised script $it")) }
        return Result.success(text)
    }

    /** Measured once per provider+model and cached; decides how much visual-design work the model is given. */
    suspend fun capabilityProfile(): CapabilityProfile {
        val prof = AiProfile.fromSettings(context)
        return CapabilityProbe.probe(context, prof.provider, prof.activeModel,
            ask = { p -> llm(p, maxTokens = 200) }, guard = { ModelOutputGuard.reason(it) })
    }

    private suspend fun askDirectives(b: DirectorBrief, outs: List<SceneOutline>, total: Int, strategy: Strategy): Result<List<BeatPlanner.Directive>> {
        var problem = ""
        repeat(2) {
            val prompt = BeatPlanner.directivePrompt(b.narrationLanguage ?: "the narration language", b.style ?: "clean", outs, total) + problem
            val r = llm(prompt, maxTokens = if (strategy == Strategy.LIGHT) 600 else 1300,
                traceKind = ActivityKind.PLANNING, traceTitle = "Scene planning directives")
            if (r.isFailure) return Result.failure(r.exceptionOrNull()!!)
            val raw = r.getOrThrow()
            ModelOutputGuard.reason(raw, b.narrationLanguage)?.let { problem = "\nYour previous reply $it. Reply with clean JSON only."; return@repeat }
            val ds = BeatPlanner.parseDirectives(raw)
            if (ds.isNotEmpty()) return Result.success(ds)
            problem = "\nYour previous reply was not valid JSON. Reply with ONLY the JSON object."
        }
        return Result.success(emptyList())   // a weak model's silence is fine: beats are designed locally
    }

    /** Human-readable note about the last planning run (e.g. scenes that used fallback visuals). */
    var lastPlanNote: String = ""
    private val sceneCache = HashMap<String, Scene>()

    private fun cacheKey(b: DirectorBrief, o: SceneOutline) =
        "${b.workflow.name}|${StyleDNA.fromBrief(b).signature}|${o.id}|${o.startMs}|${o.endMs}|${o.narration.hashCode()}"

    /**
     * Scene planning in small batches. Narration and timing come from the approved script; the model only designs visuals.
     * Each batch is parsed, checked for corruption and validated on its own; only an invalid batch is retried, and accepted
     * batches are cached so a retry after a provider failure resumes instead of starting over.
     */
    suspend fun planManifest(
        b: DirectorBrief, script: String, transcript: Transcript? = null, totalMsOverride: Long? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }, researchDigest: String? = lastDigest,
        onSceneAccepted: (Int, Scene) -> Unit = { _, _ -> }
    ): Result<SceneManifest> {
        val fps = 30
        lastPlanNote = ""
        val estMs = totalMsOverride ?: transcript?.segments?.maxOfOrNull { it.endMs }
            ?: ((b.durationSec ?: (SpeechTiming.estimateSeconds(script, b.narrationLanguage, b.pace).toInt().coerceAtLeast(10))) * 1000L)
        if (transcript == null && b.scriptMode != ScriptMode.FULL_SCRIPT_PRESERVE) {
            val bad = ModelOutputGuard.reason(script, b.narrationLanguage, requireScript = true)
            if (bad != null) return Result.failure(IllegalStateException("The script $bad. Please regenerate or edit it."))
        }
        // Round 10: ~15 scenes for a 1-minute video — beat count scales with the target length, each beat 2.2–9 s.
        val targetBeats = (estMs / 4000L).toInt().coerceIn(6, 24)
        val targetBeatMs = (estMs / targetBeats).coerceIn(2200L, 9000L)
        val outlines = BeatPlanner.outline(script, estMs, b.narrationLanguage, b.pace, targetBeatMs = targetBeatMs, maxBeats = 30)
        if (outlines.isEmpty()) return Result.failure(IllegalStateException("The script is empty, so no scenes can be planned."))
        val total = outlines.size
        val (aw, ah) = LayoutEngine.parseAspect(b.aspect ?: "9:16")
        val guard: (String) -> String? = { ModelOutputGuard.reason(it, b.narrationLanguage) }

        // How much to trust the configured model is measured once per provider+model (see CapabilityProbe).
        val chatReady = can(Capability.CHAT)
        val prof = if (chatReady) cancellableResult { capabilityProfile() }.getOrNull() else null
        val strategy = prof?.strategy
        val directives = HashMap<Int, BeatPlanner.Directive>()   // 0-based beat index
        val modelScenes = HashMap<Int, Scene>()                  // STRONG strategy: validated model-drafted scenes
        var modelDown: Throwable? = null

        // The model designs EVERY scene unless it cannot return JSON at all. The local composer is only a per-scene safety net:
        // relying on it for "standard" models (e.g. gemini-flash-latest) was what made every video look like a template.
        val designByModel = prof != null && prof.jsonOk
        val attemptsPerBatch = if (strategy == Strategy.LIGHT) 1 else 3
        var failedBatches = 0
        val batchSize = when (strategy) { Strategy.STRONG -> 3; Strategy.STANDARD -> 3; Strategy.LIGHT -> 2; null -> 0 }
        val digestFacts = SceneFacts.parse(researchDigest)
        // Every scene accepted so far, so no later scene may repeat an earlier one anywhere in the video
        // (the old gate only compared a scene with its immediate neighbour, which is how templates crept in).
        val acceptedGraphs = ArrayList<SceneGraph>()
        if (batchSize > 0) {
            val batches = outlines.withIndex().toList().chunked(batchSize)
            for ((bi, batch) in batches.withIndex()) {
                currentCoroutineContext().ensureActive()
                onProgress(bi + 1, batches.size)
                if (modelDown != null) break
                val outs = batch.map { it.value }
                if (designByModel && failedBatches >= 2 && modelScenes.isEmpty()) break          // this model cannot design scenes: stop wasting calls
                if (designByModel) {
                    val cached = outs.map { sceneCache[cacheKey(b, it)] }
                    val cacheGraphs = ArrayList<SceneGraph>(acceptedGraphs)
                    var cacheValid = true
                    for (sc in cached) {
                        val gr = sc?.graph
                        if (sc == null || gr == null) { cacheValid = false; break }
                        val fixed = LayoutEngine.repair(SceneLint.adoptCards(gr, aw, ah), aw, ah, sc.durationMs).graph
                        if (LayoutEngine.issues(fixed, aw, ah, sc.durationMs).isNotEmpty() ||
                            SceneLint.problems(fixed, aw, ah).isNotEmpty() || SceneUniqueness.repeatsOf(fixed, cacheGraphs).isNotEmpty()) {
                            cacheValid = false; break
                        }
                        cacheGraphs.add(fixed)
                    }
                    if (cacheValid) {
                        activity?.instant(ActivityKind.SCENE_JSON, "Cached scene batch accepted", "${outs.size} scenes passed validation and uniqueness checks.")
                        batch.forEachIndexed { k, iv ->
                            val scene = cached[k]!!
                            modelScenes[iv.index] = scene
                            val graph = scene.graph?.let { LayoutEngine.repair(SceneLint.adoptCards(it, aw, ah), aw, ah, scene.durationMs).graph }
                            onSceneAccepted(iv.index, scene.copy(graph = graph))
                        }
                        acceptedGraphs.clear(); acceptedGraphs.addAll(cacheGraphs)
                        continue
                    }
                    var accepted: List<Scene>? = null
                    var problem = ""
                    fun reject(reason: String): String {
                        activity?.instant(ActivityKind.SCENE_JSON, "Scene batch rejected", reason)
                        return reason
                    }
                    for (attempt in 1..attemptsPerBatch) {
                        val call = llm(DirectorPrompts.sceneBatchV2(b, batch.map { it.index to it.value }, total, fps, problem) { o -> SceneFacts.forScene(digestFacts, o.narration) }, maxTokens = 6000, traceKind = ActivityKind.SCENE_JSON, traceTitle = "Scene definitions · declarative JSON")
                        if (call.isFailure) { modelDown = call.exceptionOrNull(); break }
                        val raw = call.getOrThrow()
                        val rawBad = ModelOutputGuard.reason(raw, b.narrationLanguage)
                        if (rawBad != null) { problem = reject("Your previous reply $rawBad. Reply with clean JSON only."); continue }
                        val parsed = SceneJson.parseManifest(raw)
                        if (parsed.isFailure) { problem = reject("Your previous reply was not complete valid JSON. Keep each scene compact and reply with ONLY the JSON object."); continue }
                        val got = parsed.getOrThrow().scenes
                        if (got.size < outs.size) { problem = reject("You returned ${got.size} scenes but exactly ${outs.size} are required."); continue }
                        val merged = outs.mapIndexed { i, o -> got[i].copy(id = o.id, startMs = o.startMs, endMs = o.endMs, narration = o.narration) }
                        val textBad = merged.firstNotNullOfOrNull { ModelOutputGuard.reason(ScenePlanner.sceneText(it), b.narrationLanguage) }
                        if (textBad != null) { problem = reject("Your previous reply $textBad. Reply with clean text only."); continue }
                        val fatal = SceneValidator.fatal(SceneValidator.validate(SceneManifest(fps = fps, scenes = merged)))
                        if (fatal.isNotEmpty()) { problem = reject("Fix these problems: " + fatal.take(5).joinToString("; ") { it.message }); continue }
                        val lintBad = merged.firstNotNullOfOrNull { sc -> sc.graph?.let { gr -> SceneLint.problems(SceneLint.adoptCards(gr, aw, ah), aw, ah).firstOrNull() } }
                        if (lintBad != null) { problem = reject("Design problem: $lintBad. Every scene needs a clear visual that explains its narration, labels inside their shapes, and no empty blocks."); continue }
                        // Same gate the final build uses (after automatic repair), so a scene that would be thrown away later is fixed NOW by the model.
                        val layoutBad = merged.firstNotNullOfOrNull { sc -> sc.graph?.let { gr ->
                            val fx = LayoutEngine.repair(SceneLint.adoptCards(gr, aw, ah), aw, ah, sc.durationMs).graph
                            LayoutEngine.issues(fx, aw, ah, sc.durationMs).firstOrNull()?.toString()
                        } }
                        if (layoutBad != null) { problem = reject("Layout problem: $layoutBad. Keep every element fully inside the frame, text inside its shape, and elements apart from each other."); continue }
                        // ANTI-TEMPLATE GATE: no scene may repeat one already accepted, in this batch or earlier.
                        var uniqBad: String? = null
                        val running = ArrayList<SceneGraph>(acceptedGraphs)
                        for (mi in merged.indices) {
                            val gr = merged[mi].graph ?: continue
                            val fixed = LayoutEngine.repair(SceneLint.adoptCards(gr, aw, ah), aw, ah, merged[mi].durationMs).graph
                            val why = SceneUniqueness.reason(fixed, running, batch[mi].index)
                            if (why != null) { uniqBad = why; break }
                            running.add(fixed)
                        }
                        if (uniqBad != null) { problem = reject("Originality problem: $uniqBad"); continue }
                        accepted = merged
                        activity?.instant(ActivityKind.SCENE_JSON, "Scene batch accepted", "${merged.size} scenes passed validation and uniqueness checks.")
                        break
                    }
                    if (accepted == null) failedBatches++
                    accepted?.let { ok ->
                        batch.forEachIndexed { k, iv ->
                            val scene = ok[k]
                            modelScenes[iv.index] = scene
                            sceneCache[cacheKey(b, outs[k])] = scene
                            val graph = scene.graph?.let { LayoutEngine.repair(SceneLint.adoptCards(it, aw, ah), aw, ah, scene.durationMs).graph }
                            onSceneAccepted(iv.index, scene.copy(graph = graph))
                        }
                        ok.forEach { sc -> sc.graph?.let { gr ->
                            acceptedGraphs.add(LayoutEngine.repair(SceneLint.adoptCards(gr, aw, ah), aw, ah, sc.durationMs).graph)
                        } }
                    }
                } else {
                    askDirectives(b, outs, total, strategy!!).fold(
                        { ds -> ds.forEach { d -> directives[d.index - 1] = d } },
                        { modelDown = it }
                    )
                }
            }
        } else onProgress(1, 1)

        var beats = outlines.mapIndexed { i, o -> BeatPlanner.toBeat(o, i, total, directives[i], guard) }
        beats = LocalBeatAnalyzer.diversify(beats)

        var localCount = 0
        var prevTransition: String? = null
        // Start empty: acceptedGraphs includes the current model scene, so seeding this list with it
        // makes every model candidate collide with itself. Only actually built earlier scenes belong here.
        val builtGraphs = ArrayList<SceneGraph>()
        val dna = StyleDNA.fromBrief(b)
        val scenes = ArrayList<Scene>()
        outlines.forEachIndexed { i, o ->
            currentCoroutineContext().ensureActive()
            val beat = beats[i]
            val tr = TransitionPlanner.pick(beat, prevTransition).also { prevTransition = it }
            val dur = o.endMs - o.startMs
            val modelScene = modelScenes[i]
            var graph: SceneGraph? = modelScene?.graph?.let { g ->
                val fixed = LayoutEngine.repair(SceneLint.adoptCards(g, aw, ah), aw, ah, dur).graph
                val ok = LayoutEngine.issues(fixed, aw, ah, dur).isEmpty() && SceneLint.problems(fixed, aw, ah).isEmpty() &&
                    SceneUniqueness.repeatsOf(fixed, builtGraphs).isEmpty()
                if (ok) fixed else null
            }
            if (graph == null) {
                // Deterministic exhaustive replan; never accept the last candidate merely because retries ran out.
                graph = SceneVarietyComposer.unique(beat, b.aspect ?: "9:16", dna, BrandKit.accent(context), builtGraphs)
                    ?: return Result.failure(IllegalStateException("Could not find a unique valid design for scene ${i + 1}; no repeated scene was accepted."))
                localCount++
            }
            val scene = Scene(
                id = o.id, startMs = o.startMs, endMs = o.endMs, narration = o.narration,
                visualObjective = "${beat.intent.name}: ${beat.headline}".take(80), graph = graph,
                assets = modelScene?.assets ?: emptyList(),
                transitionIn = tr, transitionMs = if (tr == "none") 0L else 400L,
                cues = if (BrandKit.autoSfx(context)) SfxPlanner.cuesFor(beat, tr) else emptyList(), caption = o.narration
            )
            val partialFatal = SceneValidator.fatal(SceneValidator.validate(SceneManifest(fps = fps, scenes = scenes + scene)))
            if (partialFatal.isNotEmpty()) return Result.failure(IllegalStateException(partialFatal.first().message))
            builtGraphs.add(graph)
            scenes += scene
            onSceneAccepted(i, scene)
        }
        val note = StringBuilder("Planned ${scenes.size} beats (about ${"%.1f".format(scenes.sumOf { it.durationMs } / 1000.0 / scenes.size)} s each)")
        note.append(when (strategy) { null -> " — no AI model configured, so visuals were designed locally from your script."; else -> " — model strategy: ${strategy.name}." })
        if (modelDown != null) note.append(" The model became unavailable, so the remaining beats were designed locally.")
        else if (strategy != null && localCount > 0 && designByModel) note.append(" $localCount beat(s) were redesigned by the local composer because the model's design repeated an earlier scene or failed validation.")
        val collisions = SceneUniqueness.collisions(scenes.map { it.graph })
        if (collisions.isNotEmpty()) return Result.failure(IllegalStateException("Scene uniqueness gate failed: " +
            collisions.take(3).joinToString(", ") { "scene ${it.first + 1} and ${it.second + 1}" }))
        note.append(" Every scene passed the uniqueness and layout gates.")
        lastPlanNote = note.toString()

        var m = SceneManifest(fps = fps, title = b.prompt.lineSequence().firstOrNull().orEmpty().take(60), scenes = scenes)
        if (transcript != null) m = alignToTranscript(m, transcript)
        val fatal = SceneValidator.fatal(SceneValidator.validate(m, estMs))
        if (fatal.isNotEmpty()) return Result.failure(IllegalStateException(fatal.take(4).joinToString("; ") { it.message }))
        activity?.instant(ActivityKind.PLANNING, "Scene plan accepted", "${scenes.size} validated scenes; $localCount designed locally.")
        return Result.success(m.copy(fps = fps))
    }

    /** Voice-over workflow: scene boundaries come from the real transcript, not from the model. */
    fun alignToTranscript(m: SceneManifest, t: Transcript): SceneManifest {
        val total = t.segments.maxOf { it.endMs }
        val bounds = SceneTiming.fromSegments(t.segments.map { it.startMs to it.endMs }, total)
        if (m.scenes.isEmpty()) return m
        // Map each scene to a contiguous group of transcript segments proportionally by order.
        val per = (bounds.size.toDouble() / m.scenes.size).coerceAtLeast(1.0)
        var prevEnd = 0L
        val scenes = m.scenes.mapIndexed { i, s ->
            val from = (i * per).toInt().coerceAtMost(bounds.lastIndex)
            val to = if (i == m.scenes.lastIndex) bounds.lastIndex else (((i + 1) * per).toInt() - 1).coerceIn(from, bounds.lastIndex)
            val start = if (i == 0) 0L else prevEnd; val end = bounds[to].second.coerceAtLeast(start + 500)
            prevEnd = end
            s.copy(startMs = start, endMs = end, narration = t.segments.subList(from.coerceAtMost(t.segments.lastIndex), (to + 1).coerceAtMost(t.segments.size)).joinToString(" ") { it.text })
        }
        return m.copy(scenes = scenes)
    }

    /** Generates one real audio file per spoken sentence, measures it, then retimes the manifest from the measured lengths. */
    suspend fun synthNarration(b: DirectorBrief, m: SceneManifest, onScene: (Int, Int) -> Unit = { _, _ -> }): NarrationOutcome {
        check(!b.workflow.forbidsGeneratedNarration) { "${b.workflow.title} must never generate replacement narration" }
        val dir = File(context.filesDir, "director_audio/${b.workflow.name.lowercase()}")
        val clips = mutableListOf<NarrationClip>(); val fails = mutableListOf<String>()
        val withText = m.scenes.filter { it.narration.isNotBlank() }
        val groups = VoiceGroups.build(withText)
        try {
            groups.forEachIndexed { i, g ->
                currentCoroutineContext().ensureActive()
                val text = g.joinToString(" ") { it.narration.trim() }
                val event = activity?.begin(ActivityKind.NARRATION, "Narrating ${g.joinToString { it.id }}")
                try {
                    DeviceSpeech.synthesize(context, text, b.narrationLanguage, b.pace, dir, g.first().id, b.answers[GuidedBriefOptions.VOICE]).fold(
                        { c ->
                            clips += NarrationClip(g.first().id, "file://${c.file.absolutePath}", c.durationMs, text, if (g.size > 1) g.map { it.id } else emptyList())
                            event?.let { activity?.finish(it, detail = "Audio created · ${c.durationMs} ms") }
                        },
                        { e ->
                            fails += "Scene ${g.first().id}: ${e.message}"
                            event?.let { activity?.finish(it, ActivityState.FAILED, e.message ?: "Speech synthesis failed") }
                        })
                } catch (e: CancellationException) {
                    event?.let { activity?.finish(it, ActivityState.CANCELLED) }
                    throw e
                }
                onScene(i + 1, groups.size) // count finished attempts, not work that has merely started
            }
        } finally { DeviceSpeech.release() }
        // Clips are silence-trimmed, so a short natural pause between sentences is enough.
        val retimed = SceneTiming.retimeGrouped(m, clips, padMs = 170L)
        return NarrationOutcome(clips, retimed, fails)
    }

    // ------------------------------------------------------------------ Round 10: image-first scenes

    /**
     * IMAGE-FIRST SCENES: after the manifest is planned, ONE AI key visual is generated per scene (using the
     * model's own `imagePrompt`, or a locally built prompt from the scene's objective + narration + the
     * video's StyleDNA palette). Each image is saved to app storage and returned as a full-frame BACKDROP
     * placement, so the animated code scene is drawn on top of it (overlay mode) — plan → image → animate.
     * Scenes whose image fails keep pure procedural graphics (reported, never faked).
     */
    suspend fun generateSceneImages(
        b: DirectorBrief, m: SceneManifest,
        onStep: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Pair<List<ResolvedAsset>, String?> {
        val out = ArrayList<ResolvedAsset>()
        val prof = AiProfile.fromSettings(context)
        if (!ImageGen.canGenerate(prof.provider, prof.apiKey)) {
            activity?.instant(ActivityKind.IMAGE_GEN, "AI image generation not available",
                ProviderRegistry.missingMessage(Capability.IMAGE_GENERATION))
            return out to null
        }
        val dir = File(context.filesDir, "director_assets/gen/${b.workflow.name.lowercase()}").apply { mkdirs() }
        val dna = StyleDNA.fromBrief(b)
        val aspect = b.aspect ?: "9:16"
        val accent = dna.palettes.firstOrNull()?.accent?.ifBlank { null } ?: "#8B5CF6"
        var ok = 0; var failed = 0
        m.scenes.forEachIndexed { i, sc ->
            currentCoroutineContext().ensureActive()
            val prompt = sc.imagePrompt.ifBlank { imagePromptFor(sc, b, dna, aspect) }
            onStep(i + 1, m.scenes.size, "AI key visual for scene ${i + 1}/${m.scenes.size}")
            val ev = activity?.begin(ActivityKind.IMAGE_GEN, "Key visual · scene ${i + 1}/${m.scenes.size}", prompt.take(220))
            val r = cancellableResult { ImageGen.generate(prompt, aspect, prof.provider, prof.apiKey, prof.activeModel, prof.customBaseUrl) }
            val bytes = r.getOrNull()?.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val f = File(dir, "scene_${sc.id}.png")
                f.writeBytes(bytes)
                val placement = AssetPlacements.clamp(AssetPlacement(
                    role = "backdrop", width = 1f, height = 1f, fit = "cover",
                    drift = 0.25f, animIn = "fadeIn", animInMs = 350L,
                    glow = 0.10f, glowColor = accent))
                out += ResolvedAsset(sceneId = sc.id, requestId = "gen-${sc.id}", kind = "image",
                    localPath = f.absolutePath,
                    source = "ai-image:${ImageGen.imageModel(prof.provider, prof.activeModel)} (generated in-app with your key)",
                    verified = true, note = "AI key visual for: ${sc.visualObjective.take(90)}",
                    ext = "png", placement = placement)
                ok++
                ev?.let { activity?.finish(it, detail = "${f.name} · ${bytes.size / 1024} KB · ${ImageGen.imageModel(prof.provider, prof.activeModel)}") }
            } else {
                failed++
                val reason = r.exceptionOrNull()?.message ?: "empty image"
                ev?.let { activity?.finish(it, ActivityState.FAILED, reason.take(140)) }
                activity?.instant(ActivityKind.IMAGE_GEN, "Scene ${i + 1} key visual skipped — pure graphics used", reason.take(140))
            }
        }
        val note = if (ok > 0) {
            "Generated $ok AI key visual(s) with your ${prof.provider} key" +
                (if (failed > 0) " — $failed scene(s) kept pure animated graphics (the image model could not draw them)" else "") +
                ". Each scene = AI image backdrop + unique animated graphics on top."
        } else null
        return out to note
    }

    /** Local fallback image prompt when the model did not write one for the scene. */
    private fun imagePromptFor(sc: Scene, b: DirectorBrief, dna: StyleDNA, aspect: String): String = buildString {
        append("Cinematic key visual for a $aspect video scene. ")
        append("Scene idea: ${sc.visualObjective.ifBlank { "illustrate the narration" }}. ")
        append("Narration of this beat: \"${sc.narration.take(180)}\". ")
        append("Video topic: ${b.prompt.take(120)}. Style: ${b.style ?: "premium motion graphics"}. ")
        val pal = dna.palettes.firstOrNull()
        if (pal != null) append("Colour mood: background ${pal.bgTop} to ${pal.bgBottom}, accents ${pal.accent} and ${pal.accent2}. ")
        append("One strong subject with a clear focal point, dramatic cinematic lighting, depth of field, subtle film grain, 8k, highly detailed. ")
        append("Do NOT include any text, watermark or logo. ")
        append("Leave the upper-middle area relatively clean for animated overlay graphics.")
    }

    suspend fun resolveAssets(b: DirectorBrief, m: SceneManifest, userId: String, exclude: Set<String> = emptySet(), onItem: (Int, Int) -> Unit = { _, _ -> }): AssetOutcome {
        val resolved = mutableListOf<ResolvedAsset>(); val unresolved = mutableListOf<String>(); val notes = mutableListOf<String>()
        val token = WebResearch.telegramToken(context)
        val vision = can(Capability.IMAGE_UNDERSTANDING)
        if (!vision) notes += ProviderRegistry.missingMessage(Capability.IMAGE_UNDERSTANDING)
        if (token.isBlank()) notes += "Telegram asset library is not configured (no bot token) — using generated graphics for scenes that need assets."
        val reqs = m.scenes.flatMap { s -> s.assets.map { s to it } }
        reqs.forEachIndexed { idx, (scene, req) ->
            onItem(idx + 1, reqs.size)
            if (b.assetPolicy?.startsWith("Generate", true) == true || token.isBlank()) { unresolved += "${scene.id}/${req.id}: no library source, procedural graphics used"; return@forEachIndexed }
            val kw = req.description.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
            val wantType = if (req.kind == "overlay") "image" else req.kind
            var cands = TelegramAssetManager.rankCandidates(context, userId, wantType, kw, limit = 3 + exclude.size, botToken = token)
            // Nothing as video? A matching picture or meme is still better than a plain scene.
            if (cands.isEmpty() && wantType == "video") cands = TelegramAssetManager.rankCandidates(context, userId, "image", kw, limit = 3, botToken = token)
            if (exclude.isNotEmpty()) cands = cands.filter { it.assetId.ifBlank { it.title } !in exclude }
            var accepted = false
            for (c in cands) {
                val dl = TelegramAssetManager.downloadCandidate(context, userId, c, token) ?: continue
                val f = File(dl.localCachedPath ?: continue)
                val v = AssetVerifier.verify(context, f, if (c.type == "image" && req.kind == "video") req.copy(kind = "image") else req, b.aspect, scene.visualObjective, vision)
                if (v.accepted) {
                    resolved += ResolvedAsset(scene.id, req.id, if (c.type == "image" && req.kind == "video") "image" else req.kind, f.absolutePath, "telegram:${c.assetId.ifBlank { c.title }} (licence unverified)", v.visuallyVerified, v.reason)
                    accepted = true; break
                } else notes += "Rejected '${c.title}' for ${scene.id}: ${v.reason}"
            }
            if (!accepted) unresolved += "${scene.id}/${req.id}: no suitable asset for \"${req.description.take(50)}\" — procedural fallback"
        }
        return AssetOutcome(resolved, unresolved, notes)
    }

    /**
     * Telegram problem the user can act on. When the library really is empty the app now runs a live
     * diagnose (getMe / getChat / membership / webhook) and shows the actual reason instead of just
     * "your telegram is empty" — usually the bot is not admin, a webhook is set, or the posts are older
     * than the 24 h the Bot API keeps. The APK also ships a build-time catalog (assets_catalog.json,
     * committed in the repo), so the library is only empty when no asset was ever indexed anywhere.
     */
    private suspend fun libraryProblem(token: String, count: Int): String? = when {
        token.isBlank() -> {
            val baked = AssetLibrary.usable(TelegramAssetManager.loadMasterCatalog(context), "default").size
            "No Telegram bot token in this build, so the $baked baked catalog item(s) cannot be downloaded. " +
                "Add TG_BOT_TOKEN + TG_CHAT_ID as GitHub secrets and rebuild, or paste a bot token in the library screen."
        }
        count == 0 -> {
            val diag = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                cancellableResult {
                    com.example.ui.components.TelegramCatalogStore(context)
                        .diagnose(token, TelegramAssetManager.DEFAULT_CHANNEL_ID)
                }.getOrNull()
            }?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(7)?.joinToString("\n")
            buildString {
                append("Your Telegram library is empty for this app: no posts were ever indexed (the APK's build-time catalog is empty too). ")
                append("Upload assets to the channel (the bot must be ADMIN and have no webhook), open the library screen and tap Sync, ")
                append("or forward your existing assets to the bot once in a private chat — file ids never expire.")
                if (diag != null) append("\nDiagnostics:\n").append(diag)
            }
        }
        else -> null
    }

    /**
     * The AI asset editor. Reads the real Telegram library listing together with every scene, picks the exact b-roll / meme /
     * picture / sound effect for each scene, downloads and verifies them, and returns what is missing so it can be requested
     * on Telegram. Falls back to keyword matching only when the model cannot answer.
     */
    suspend fun curateAssets(b: DirectorBrief, m: SceneManifest, userId: String, onItem: (Int, Int) -> Unit = { _, _ -> }): AssetOutcome {
        val resolved = mutableListOf<ResolvedAsset>(); val unresolved = mutableListOf<String>(); val notes = mutableListOf<String>()
        val token = WebResearch.telegramToken(context)
        val all = if (token.isBlank()) emptyList() else cancellableResult { TelegramAssetManager.syncChannelAssets(botToken = token, context = context) }.getOrDefault(emptyList())
        val usable = AssetCurator.usable(all, userId)
        libraryProblem(token, usable.size)?.let { notes += it; return AssetOutcome(emptyList(), emptyList(), notes) }
        if (!can(Capability.CHAT)) {
            notes += "No AI model is configured, so assets were matched by keywords only."
            val withReq = m.copy(scenes = AssetPlanner.plan(m.scenes))
            val ao = resolveAssets(b, withReq, userId, onItem = onItem)
            return ao.copy(notes = notes + ao.notes)
        }
        val entries = AssetCurator.shortlist(usable, m.scenes)
        val keyMap = entries.associateBy { it.key }
        val call = llm(AssetCurator.prompt(b, m.scenes, entries), maxTokens = 3500,
            traceKind = ActivityKind.ASSET_SEARCH, traceTitle = "Asset curation")
        val plan = call.getOrNull()?.takeIf { ModelOutputGuard.reason(it) == null }?.let { AssetCurator.parse(it, entries, m.scenes) }
        if (plan == null) {
            notes += "The AI asset editor could not answer (${call.exceptionOrNull()?.message ?: "unusable reply"}); matched by keywords instead."
            val ao = resolveAssets(b, m.copy(scenes = AssetPlanner.plan(m.scenes)), userId, onItem = onItem)
            return ao.copy(notes = notes + ao.notes)
        }
        val vision = can(Capability.IMAGE_UNDERSTANDING)
        val sceneById = m.scenes.associateBy { it.id }
        val total = plan.picks.size + plan.sfx.size
        var n = 0
        for (p in plan.picks) {
            onItem(++n, total)
            val item = keyMap[p.key]?.item
            val scene = sceneById[p.sceneId]
            if (item == null || scene == null) continue
            val dl = TelegramAssetManager.downloadCandidate(context, userId, item, token)
            val f = dl?.localCachedPath?.let { File(it) }
            if (f == null) { unresolved += "${scene.id}: '${item.title}' could not be downloaded (${TelegramAssetManager.lastDownloadError.ifBlank { "unknown error" }})"; continue }
            val kind = if (item.type == "video") "video" else "image"
            val req = AssetRequest("lib-${scene.id}", kind, "${item.title}. ${p.reason}".take(160))
            val v = AssetVerifier.verify(context, f, req, b.aspect, scene.visualObjective, vision)
            if (v.accepted) resolved += ResolvedAsset(scene.id, req.id, kind, f.absolutePath, "telegram:${item.assetId.ifBlank { item.title }} (licence unverified)", v.visuallyVerified, "AI editor: ${p.reason.ifBlank { v.reason }}".take(120))
            else notes += "Rejected '${item.title}' for ${scene.id}: ${v.reason}"
        }
        for ((i, p) in plan.sfx.withIndex()) {
            onItem(++n, total)
            val item = keyMap[p.key]?.item
            val scene = sceneById[p.sceneId]
            if (item == null || scene == null) continue
            val dl = TelegramAssetManager.downloadCandidate(context, userId, item, token)
            val f = dl?.localCachedPath?.let { File(it) }
            if (f == null || !MediaInspector.probe(context, f).let { it.valid && it.kind == "audio" }) { notes += "Sound '${item.title}' could not be used for ${scene.id}."; continue }
            val len = AudioDurations.durationMs(f).takeIf { it > 0 } ?: continue
            resolved += ResolvedAsset(scene.id, "sfx-$i", "audio", f.absolutePath, "telegram:${item.assetId.ifBlank { item.title }} (licence unverified)", true, "AI editor: ${p.reason}".take(100), atMs = p.atMs, durMs = minOf(len, p.durMs.coerceAtLeast(150L)))
        }
        // What the model asked for but the library does not have (plus its own unmet stock-footage ideas).
        val needs = ArrayList<AssetNeed>(plan.needs)
        m.scenes.forEach { sc -> if (resolved.none { it.sceneId == sc.id && it.kind != "audio" }) sc.assets.firstOrNull()?.let { r -> if (r.description.length > 5) needs += AssetNeed(sc.id, r.kind, r.description.take(160)) } }
        val picked = resolved.count { it.kind != "audio" }; val sounds = resolved.count { it.kind == "audio" }
        notes += "AI asset editor chose $picked picture/clip(s) and $sounds sound effect(s) from ${usable.size} library items; ${needs.distinctBy { it.description.lowercase() }.size} need(s) not in the library."
        return AssetOutcome(resolved, unresolved, notes, needs.distinctBy { it.description.lowercase() })
    }

    // ------------------------------------------------------------------ sequential asset hunt

    /** Readable trace of the last hunt, one line per place on the timeline (shown in the director chat). */
    var lastHuntLog: List<String> = emptyList()
        private set

    /**
     * SEQUENTIAL ASSET HUNT — the replacement for "ask the model for the whole asset list in one call".
     *
     * The Telegram library is synced ONCE, then the video is walked place by place, in playing order. For each
     * place the model is asked for exactly ONE asset: what it must show, in which FILE FORMAT (png / jpg / mp4 /
     * mp3 ...), and with only TWO OR THREE search keywords. Those keywords search the local catalog, the best hit
     * is downloaded and LOOKED AT (technical probe + a vision check against that exact moment of the video). A
     * rejected file makes the model change the keywords a little and try the same place again, up to
     * [AssetHunter.MAX_TRIES] times. Only a verified file is placed on the timeline, and it is placed with full
     * control (position, size, fit, keyframes, glow, blend, grade, corner, entrance/exit) — then the hunt moves
     * on to the next place. Nothing is ever faked: a place that stays empty is reported.
     *
     * @param onStep one progress line per search / download / verify, for the director chat.
     * @param only   scene ids to re-hunt (null = the whole video).
     */
    suspend fun huntAssets(
        b: DirectorBrief, m: SceneManifest, userId: String,
        onStep: (HuntStep) -> Unit = {},
        only: Set<String>? = null,
        maxSlots: Int = 48,
        /** Round 10: scene ids that already have an AI-generated key visual — their picture slot is skipped (sounds are still hunted). */
        skipVisualFor: Set<String> = emptySet()
    ): AssetOutcome {
        val resolved = ArrayList<ResolvedAsset>()
        val unresolved = ArrayList<String>()
        val notes = ArrayList<String>()
        val needs = ArrayList<AssetNeed>()
        val log = object : ArrayList<String>() {
            override fun add(element: String): Boolean {
                val kind = when {
                    element.contains("ACCEPTED", ignoreCase = true) -> ActivityKind.ASSET_ACCEPTED
                    element.contains("rejected", ignoreCase = true) || element.contains("could not be downloaded", ignoreCase = true) -> ActivityKind.ASSET_REJECTED
                    else -> ActivityKind.ASSET_SEARCH
                }
                val title = when (kind) {
                    ActivityKind.ASSET_ACCEPTED -> "Asset accepted"
                    ActivityKind.ASSET_REJECTED -> "Asset rejected"
                    else -> "Asset hunt"
                }
                activity?.instant(kind, title, element)
                return super.add(element)
            }
        }
        lastHuntLog = log
        fun report(step: HuntStep) {
            activity?.instant(ActivityKind.ASSET_SEARCH, "Asset search", step.text)
            onStep(step)
        }

        val token = WebResearch.telegramToken(context)
        if (token.isBlank()) {
            notes += "Telegram asset library is not configured (no bot token in this build). Add TG_BOT_TOKEN and TG_CHAT_ID as GitHub secrets and rebuild."
            return AssetOutcome(emptyList(), emptyList(), notes)
        }
        val all = cancellableResult { TelegramAssetManager.syncChannelAssets(botToken = token, context = context) }.getOrDefault(emptyList())
        val library = AssetLibrary.usable(all, userId)
        libraryProblem(token, library.size)?.let { notes += it; return AssetOutcome(emptyList(), emptyList(), notes) }

        val chat = can(Capability.CHAT)
        val vision = can(Capability.IMAGE_UNDERSTANDING)
        if (!vision) notes += "No vision-capable provider is configured, so downloaded files are accepted on technical checks and keyword match only (the hunter cannot LOOK at them)."
        if (!chat) notes += "No AI model is configured, so assets were hunted with keywords taken from the narration instead of an AI asset editor."

        val slots = HuntSlots.build(m, b.aspect).filter { sl -> (only == null || sl.sceneId in only) && !(!sl.isSfx && sl.sceneId in skipVisualFor) }.take(maxSlots)
        if (slots.isEmpty()) {
            notes += "No place in this video asked for a library asset."
            return AssetOutcome(emptyList(), emptyList(), notes)
        }

        val title = m.title.ifBlank { b.prompt.lineSequence().firstOrNull().orEmpty() }
        val sampleAll = AssetLibrary.sampleTitles(library, "all", 20)
        val used = HashSet<String>()
        val sceneById = m.scenes.associateBy { it.id }
        var done = 0

        for (sl in slots) {
            currentCoroutineContext().ensureActive()
            done++
            var current: HuntNeed? = null
            var accepted: ResolvedAsset? = null
            val attempts = ArrayList<HuntAttempt>()
            val rejectedTitles = ArrayList<String>()
            var uploadRequest: String? = null
            var tryNo = 0

            hunt@ while (tryNo < AssetHunter.MAX_TRIES && accepted == null) {
                currentCoroutineContext().ensureActive()
                tryNo++
                // 1. ONE need for this place (the model, or a deterministic seed when no model answers).
                var need = current
                if (need == null) {
                    report(HuntStep(done, slots.size, "Scene ${sl.sceneIndex + 1}/${sl.sceneCount} · deciding what this moment needs", (done - 1f) / slots.size))
                    val asked = askNeed(title, sl, library.size, sampleAll, chat)
                    need = asked ?: AssetHunter.seedNeed(sl)
                    if (asked == null) log += "Scene ${sl.sceneIndex + 1}: the model did not answer, so the keywords were taken from the narration."
                    if (!need.want) {
                        uploadRequest = need.what.takeIf { it.length > 5 }
                        log += "Scene ${sl.sceneIndex + 1}: no library asset needed here (${need.reason.ifBlank { "the graphics already explain it" }})."
                        break@hunt
                    }
                    current = need
                }
                report(HuntStep(done, slots.size, AssetHunter.stepText(sl, need, need.keywords, tryNo, resolved.size), (done - 1f) / slots.size))

                // 2. search the local catalog with those two or three keywords, filtered by the wanted format.
                var hits = AssetLibrary.search(library, need.keywords, need.ext, limit = 3, avoidIds = used, strictFormat = true)
                if (hits.isEmpty()) hits = AssetLibrary.search(library, need.keywords, need.ext, limit = 3, avoidIds = used, strictFormat = false)
                if (hits.isEmpty()) {
                    attempts += HuntAttempt(need.keywords, need.ext, "", "no file in the library matched “${need.keywords.joinToString(" ")} ${need.ext}”", AssetLibrary.sampleTitles(library, need.ext, 8))
                    log += "Scene ${sl.sceneIndex + 1} · “${need.keywords.joinToString(" ")} ${need.ext}” → nothing in the library."
                    current = reviseNeed(sl, need, attempts, library.size, sampleAll, chat)
                    if (current == null) { uploadRequest = need.what; break@hunt }
                    continue@hunt
                }

                // 3. download and LOOK AT the best hits, one by one.
                for (c in hits) {
                    currentCoroutineContext().ensureActive()
                    val dl = TelegramAssetManager.downloadCandidate(context, userId, c, token)
                    val path = dl?.localCachedPath
                    if (path == null) {
                        val err = TelegramAssetManager.lastDownloadError.ifBlank { "unknown error" }
                        attempts += HuntAttempt(need.keywords, need.ext, c.title, "download failed: $err")
                        log += "Scene ${sl.sceneIndex + 1} · '${c.title}' could not be downloaded ($err)."
                        continue
                    }
                    val f = File(path)
                    val situation = buildString {
                        append("Scene ").append(sl.sceneIndex + 1).append(" of ").append(sl.sceneCount).append(": \"").append(sl.narration.take(200)).append('"')
                        if (sl.isSfx) append(" — sound at ").append(sl.atMs).append(" ms (").append(sl.cueKind).append(')')
                        val vo = sceneById[sl.sceneId]?.visualObjective
                        if (!vo.isNullOrBlank()) append("\nThis beat is about: ").append(vo.take(120))
                    }
                    val v = AssetVerifier.verifyForHunt(context, f, need.what, need.kind, situation, b.aspect, vision, rejectedTitles)
                    if (!v.verdict.accepted) {
                        rejectedTitles += "${c.title}: ${v.verdict.reason}"
                        attempts += HuntAttempt(need.keywords, need.ext, c.title, v.verdict.reason, hits.map { it.title })
                        log += "Scene ${sl.sceneIndex + 1} · rejected '${c.title}' — ${v.verdict.reason}"
                        // The verifier's own keyword suggestion is the cheapest good retry.
                        val suggested = v.betterKeywords.take(3)
                        if (suggested.size >= 2 && suggested.toSet() != need.keywords.toSet() && attempts.none { it.keywords == suggested }) {
                            current = need.copy(keywords = suggested)
                            continue@hunt
                        }
                        continue
                    }

                    // 4. verified: place it with full control, then stop hunting for this place.
                    used += AssetLibrary.idOf(c)
                    val placement: AssetPlacement? = if (sl.isSfx) null else {
                        val base = need.placement ?: AssetPlacements.defaultFor(need.kind, v.ext, v.transparent, b.aspect)
                        // The file's REAL transparency decides sticker vs backdrop, whatever the model guessed.
                        val role = when {
                            v.transparent -> "sticker"
                            base.role == "sticker" && v.ext != "png" && v.ext != "webp" -> "backdrop"
                            else -> base.role
                        }
                        AssetPlacements.clamp(base.copy(role = role))
                    }
                    val probeKind = v.verdict.probe?.kind
                    accepted = ResolvedAsset(
                        sceneId = sl.sceneId,
                        requestId = if (sl.isSfx) "sfx-${sl.slotId}" else "lib-${sl.slotId}",
                        kind = if (sl.isSfx) "audio" else if (probeKind == "video") "video" else "image",
                        localPath = f.absolutePath,
                        source = "telegram:${AssetLibrary.idOf(c)} (licence unverified)",
                        verified = true,
                        note = "hunter: ${need.reason.ifBlank { need.what }}".take(110) +
                            (placement?.let { " · ${PlacementApplier.describe(it)}" } ?: ""),
                        atMs = if (sl.isSfx) need.atMs else 0L,
                        durMs = if (sl.isSfx) minOf(AudioDurations.durationMs(f).takeIf { it > 0 } ?: need.durMs, need.durMs.coerceAtLeast(150L)) else 0L,
                        ext = v.ext,
                        transparent = v.transparent,
                        placement = placement
                    )
                    log += "Scene ${sl.sceneIndex + 1} · ACCEPTED '${c.title}' (${v.ext}) after $tryNo try/tries" +
                        (if (v.verdict.visuallyVerified) " — visually verified ${v.verdict.score}/10" else " — technical checks only") +
                        (placement?.let { " · ${PlacementApplier.describe(it)}" } ?: "")
                    break
                }
                if (accepted != null) break@hunt

                // 5. every downloaded file was wrong: change the keywords a little and try this place again.
                attempts += HuntAttempt(need.keywords, need.ext, "", "every downloaded file was rejected")
                current = reviseNeed(sl, need, attempts, library.size, sampleAll, chat)
                if (current == null) { uploadRequest = need.what; break@hunt }
            }

            if (accepted != null) {
                resolved += accepted
                report(HuntStep(done, slots.size, "Scene ${sl.sceneIndex + 1}/${sl.sceneCount} · placed · ${resolved.size} asset(s) so far", done.toFloat() / slots.size))
            } else if (uploadRequest != null) {
                needs += AssetNeed(sl.sceneId, if (sl.isSfx) "sfx" else (current?.kind ?: "video"), uploadRequest.take(160))
                unresolved += "${sl.sceneId}/${sl.slotId}: nothing suitable in the library for \"${(current?.what ?: uploadRequest).take(50)}\" — asked on Telegram"
            } else {
                unresolved += "${sl.sceneId}/${sl.slotId}: no suitable asset after $tryNo tries — procedural graphics used"
            }
        }

        val pictures = resolved.count { it.kind != "audio" }
        val sounds = resolved.count { it.kind == "audio" }
        notes += "Hunted one asset at a time: $pictures picture/clip(s) and $sounds sound(s) placed from ${library.size} library file(s); " +
            "${needs.distinctBy { it.description.lowercase() }.size} need(s) are not in the library and were requested on Telegram."
        lastHuntLog = log
        return AssetOutcome(resolved, unresolved, notes, needs.distinctBy { it.description.lowercase() })
    }

    /** Asks the model for exactly ONE asset need for ONE place on the timeline. */
    private suspend fun askNeed(title: String, sl: HuntSlot, librarySize: Int, sample: List<String>, chat: Boolean): HuntNeed? {
        if (!chat) return null
        val raw = llm(AssetHunter.needPrompt(title, sl, librarySize, sample), maxTokens = 900,
            traceKind = ActivityKind.ASSET_SEARCH, traceTitle = "Asset need").getOrNull() ?: return null
        if (ModelOutputGuard.reason(raw) != null) return null
        return AssetHunter.parseNeed(raw, sl)
    }

    /** After a failure: a small keyword change for the SAME asset, or null when this place should be given up. */
    private suspend fun reviseNeed(
        sl: HuntSlot, need: HuntNeed, attempts: List<HuntAttempt>, librarySize: Int, sample: List<String>, chat: Boolean
    ): HuntNeed? {
        if (!chat) return fallbackRevise(sl, need, attempts)
        val raw = llm(AssetHunter.revisePrompt(sl, need, attempts, sample, librarySize), maxTokens = 500,
            traceKind = ActivityKind.ASSET_SEARCH, traceTitle = "Asset search revision").getOrNull()
            ?: return fallbackRevise(sl, need, attempts)
        if (ModelOutputGuard.reason(raw) != null) return fallbackRevise(sl, need, attempts)
        return AssetHunter.parseRevision(raw, sl, need) ?: fallbackRevise(sl, need, attempts)
    }

    /** Deterministic keyword widening when the model cannot repair its own search. */
    private fun fallbackRevise(sl: HuntSlot, need: HuntNeed, attempts: List<HuntAttempt>): HuntNeed? {
        val tried = attempts.map { it.keywords.joinToString(" ") }.toSet()
        val next = AssetHunter.keywordLadder(need, sl).firstOrNull { it.joinToString(" ") !in tried } ?: return null
        return need.copy(keywords = next)
    }
    /**
     * Finds licensed SFX in the Telegram library for each sound kind the manifest asks for (whoosh, pop, ding...).
     * Returns kind -> local file URI. Kinds with no match are simply absent, so procedural SFX is used for them.
     */
    suspend fun resolveSfxLibrary(m: SceneManifest, userId: String): Map<String, String> {
        val token = WebResearch.telegramToken(context)
        if (token.isBlank()) return emptyMap()
        val kinds = m.scenes.flatMap { s -> s.cues.map { it.kind } }.toSet()
        val out = HashMap<String, String>()
        for (kind in kinds) {
            val cands = cancellableResult { TelegramAssetManager.rankCandidates(context, userId, "sfx", listOf(kind, "sfx"), limit = 2, botToken = token) }.getOrDefault(emptyList())
            for (c in cands) {
                if (c.type != "audio") continue
                val dl = TelegramAssetManager.downloadCandidate(context, userId, c, token) ?: continue
                val f = File(dl.localCachedPath ?: continue)
                if (f.length() > 0) { out[kind] = "file://${f.absolutePath}"; break }
            }
        }
        return out
    }

    /** Edit plan from real analysis. Falls back to deterministic silence trimming when no model is available. */
    suspend fun planFootageEdit(b: DirectorBrief, a: VideoAnalysis, transcript: Transcript?): FootagePlan {
        val uri = a.uri
        fun fr(r: TimeRange, why: String) = FootageRange(uri, r.startMs, r.endMs, a.width, a.height, why)
        val fallback = VideoAnalyzer.silenceTrimPlan(a)
        val tx = transcript?.let { t -> JSONArray().also { arr -> t.segments.forEach { arr.put(JSONObject().put("s", it.startMs).put("e", it.endMs).put("t", it.text)) } }.toString() }
        val raw = if (can(Capability.CHAT)) llm(DirectorPrompts.footageAnalysisPlan(b, a.toJson().toString(), tx),
            traceKind = ActivityKind.PLANNING, traceTitle = "Footage edit plan").getOrNull() else null
        val parsed = raw?.let { runCatching { JSONObject(SceneJson.extractJson(it)) }.getOrNull() }
        val keeps = parsed?.optJSONArray("keep")?.let { arr -> (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i); val s = o.optLong("startMs"); val e = o.optLong("endMs")
            if (s >= 0 && e > s && e <= a.durationMs + 100) fr(TimeRange(s, minOf(e, a.durationMs)), o.optString("why", "kept by AI plan")) else null } }.orEmpty()
        if (keeps.isEmpty()) return FootagePlan(
            if (raw == null) "Deterministic edit: removed ${a.silences.size} silent gaps (no AI model response)." else "AI plan could not be parsed; silence trimming applied instead.",
            fallback.map { fr(it, "silence trimmed") }.ifEmpty { listOf(fr(TimeRange(0, a.durationMs), "kept whole clip")) }, emptyList(), b.subtitles?.startsWith("No", true) != true, false)
        val ov = parsed?.optJSONArray("overlays")?.let { arr -> (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Triple(o.optLong("atMs"), o.optLong("durationMs", 2500L), o.optString("text")) } }.orEmpty().filter { it.third.isNotBlank() }
        return FootagePlan(parsed?.optString("summary").orEmpty().ifBlank { "AI edit plan" }, keeps, ov, parsed?.optBoolean("captions", true) ?: true, true)
    }

    /** Narration text for the footage workflow: windows come from kept ranges so audio can fit. */
    suspend fun narrationForFootage(b: DirectorBrief, a: VideoAnalysis, tr: Transcript?, keep: List<FootageRange>): Result<List<Triple<Long, Long, String>>> {
        val keepJson = JSONArray().also { arr -> keep.forEach { arr.put(JSONObject().put("startMs", it.sourceInMs).put("endMs", it.sourceOutMs)) } }.toString()
        val tx = tr?.let { t -> JSONArray().also { arr -> t.segments.forEach { arr.put(JSONObject().put("s", it.startMs).put("e", it.endMs).put("t", it.text)) } }.toString() }
        return llm(DirectorPrompts.narrationFromFootage(b, a.toJson().toString(), tx, keepJson),
            traceKind = ActivityKind.SCRIPT, traceTitle = "Footage narration").mapCatching { raw ->
            val arr = JSONObject(SceneJson.extractJson(raw)).getJSONArray("segments")
            (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Triple(o.getLong("atMs"), o.optLong("maxMs", 6000L), o.getString("text")) }.filter { it.third.isNotBlank() }
                .also { require(it.isNotEmpty()) { "Model returned no narration segments" } }
        }
    }

    suspend fun revisionCall(prompt: String): Result<String> = llm(prompt,
        traceKind = ActivityKind.SCENE_JSON, traceTitle = "Scene revision · declarative JSON")

    fun uriOf(s: String): Uri = Uri.parse(s)
}
