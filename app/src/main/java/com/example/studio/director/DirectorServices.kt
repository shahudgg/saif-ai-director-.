package com.example.studio.director

import android.content.Context
import com.example.studio.composer.BeatPlanner
import com.example.studio.composer.CapabilityProbe
import com.example.studio.composer.CapabilityProfile
import com.example.studio.composer.LayoutEngine
import com.example.studio.composer.LocalBeatAnalyzer
import com.example.studio.composer.SceneComposer
import com.example.studio.composer.Strategy
import com.example.studio.composer.SceneLint
import com.example.studio.composer.StyleDNA
import android.net.Uri
import com.example.llm.LlmRole
import com.example.llm.ProviderProfile
import com.example.studio.ai.AiClient
import com.example.studio.ai.AiProfile
import com.example.ui.components.TelegramAssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ResearchOutcome(val digest: String?, val sources: List<ResearchSource>, val note: String)
data class NarrationOutcome(val clips: List<NarrationClip>, val manifest: SceneManifest, val failures: List<String>)
data class AssetOutcome(val assets: List<ResolvedAsset>, val unresolved: List<String>, val notes: List<String>, val needs: List<AssetNeed> = emptyList())
data class FootagePlan(val summary: String, val keep: List<FootageRange>, val overlays: List<Triple<Long, Long, String>>, val captions: Boolean, val usedAi: Boolean)

/** All real work behind the four workflows. Every method reports failure honestly instead of faking output. */
class DirectorServices(private val context: Context) {

    fun providers(): List<ProviderEntry> {
        val prof = AiProfile.fromSettings(context)
        val chat = ProviderRegistry.fromProfile(ProviderProfile.getById(prof.provider), prof.apiKey.isNotBlank(), prof.activeModel)
        return listOf(chat, ProviderRegistry.deviceSpeech(), ProviderRegistry.webSearch(WebResearch.isConfigured(context), WebResearch.provider(context)))
    }

    fun can(c: Capability) = ProviderRegistry.route(providers(), c) is RouteResult.Routed

    /** Calls the configured model. An HTTP 200 with an empty body is treated as a failure and retried with backoff and a smaller token budget. */
    private suspend fun llm(prompt: String, role: LlmRole = LlmRole.PLANNER, system: String = DirectorPrompts.SYSTEM_DIRECTOR, maxTokens: Int = 8000): Result<String> {
        val chain = ProviderRegistry.chain(providers(), Capability.CHAT)
        return ProviderRegistry.runWithFallback(chain) {
            var last: Throwable? = null
            var result: Result<String>? = null
            for (attempt in 0..2) {
                val budget = if (attempt == 0) maxTokens else minOf(maxTokens, 3000)
                val r = AiClient(AiProfile.fromSettings(context)).call(role, prompt, system, maxTokens = budget)
                if (r.isSuccess && r.getOrThrow().isNotBlank()) { result = r; break }
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
        val askModel: suspend (String) -> Result<String> = { p -> llm(p, maxTokens = 500) }
        val ask = if (can(Capability.CHAT)) askModel else null
        val deep = runCatching { DeepResearch.run(context, topic, keyed, ask, onStep) }.getOrNull()
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
        val strategy = if (can(Capability.CHAT)) runCatching { capabilityProfile().strategy }.getOrNull() else null
        val pipeline = ScriptPipeline(ask = { p -> llm(p) }, guard = { ModelOutputGuard.reason(it, b.narrationLanguage) })
        return pipeline.run(b, digest, words, strategy).map { o ->
            lastScriptNote = buildString {
                o.hook?.let { append("Hook: \"").append(it).append("\". ") }
                o.notes.forEach { append(it).append(' ') }
                if (o.issuesLeft.isNotEmpty()) append("Still to improve: ").append(o.issuesLeft.take(3).joinToString(" "))
            }.trim()
            o.script
        }
    }

    /** A stronger opening line than [current], or a failure explaining why nothing changed. */
    suspend fun betterHook(b: DirectorBrief, current: String): Result<String> {
        val pipeline = ScriptPipeline(ask = { p -> llm(p, maxTokens = 600) }, guard = { ModelOutputGuard.reason(it, b.narrationLanguage) })
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
        val r = llm(prompt)
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
            val r = llm(prompt, maxTokens = if (strategy == Strategy.LIGHT) 600 else 1300)
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
        "${b.workflow.name}|${b.aspect}|${b.style}|${b.motionLevel}|${o.id}|${o.startMs}|${o.endMs}|${o.narration.hashCode()}"

    /**
     * Scene planning in small batches. Narration and timing come from the approved script; the model only designs visuals.
     * Each batch is parsed, checked for corruption and validated on its own; only an invalid batch is retried, and accepted
     * batches are cached so a retry after a provider failure resumes instead of starting over.
     */
    suspend fun planManifest(
        b: DirectorBrief, script: String, transcript: Transcript? = null, totalMsOverride: Long? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }, researchDigest: String? = lastDigest
    ): Result<SceneManifest> {
        val fps = 30
        lastPlanNote = ""
        val estMs = totalMsOverride ?: transcript?.segments?.maxOfOrNull { it.endMs }
            ?: ((b.durationSec ?: (SpeechTiming.estimateSeconds(script, b.narrationLanguage, b.pace).toInt().coerceAtLeast(10))) * 1000L)
        if (transcript == null && b.scriptMode != ScriptMode.FULL_SCRIPT_PRESERVE) {
            val bad = ModelOutputGuard.reason(script, b.narrationLanguage, requireScript = true)
            if (bad != null) return Result.failure(IllegalStateException("The script $bad. Please regenerate or edit it."))
        }
        val outlines = BeatPlanner.outline(script, estMs, b.narrationLanguage, b.pace)
        if (outlines.isEmpty()) return Result.failure(IllegalStateException("The script is empty, so no scenes can be planned."))
        val total = outlines.size
        val (aw, ah) = LayoutEngine.parseAspect(b.aspect ?: "9:16")
        val guard: (String) -> String? = { ModelOutputGuard.reason(it, b.narrationLanguage) }

        // How much to trust the configured model is measured once per provider+model (see CapabilityProbe).
        val chatReady = can(Capability.CHAT)
        val prof = if (chatReady) runCatching { capabilityProfile() }.getOrNull() else null
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
        if (batchSize > 0) {
            val batches = outlines.withIndex().toList().chunked(batchSize)
            for ((bi, batch) in batches.withIndex()) {
                onProgress(bi + 1, batches.size)
                if (modelDown != null) break
                val outs = batch.map { it.value }
                if (designByModel && failedBatches >= 2 && modelScenes.isEmpty()) break          // this model cannot design scenes: stop wasting calls
                if (designByModel) {
                    val cached = outs.map { sceneCache[cacheKey(b, it)] }
                    if (cached.all { it != null }) { batch.forEachIndexed { k, iv -> modelScenes[iv.index] = cached[k]!! }; continue }
                    var accepted: List<Scene>? = null
                    var problem = ""
                    for (attempt in 1..attemptsPerBatch) {
                        val call = llm(DirectorPrompts.sceneBatchV2(b, batch.map { it.index to it.value }, total, fps, problem) { o -> SceneFacts.forScene(digestFacts, o.narration) }, maxTokens = 6000)
                        if (call.isFailure) { modelDown = call.exceptionOrNull(); break }
                        val raw = call.getOrThrow()
                        val rawBad = ModelOutputGuard.reason(raw, b.narrationLanguage)
                        if (rawBad != null) { problem = "Your previous reply $rawBad. Reply with clean JSON only."; continue }
                        val parsed = SceneJson.parseManifest(raw)
                        if (parsed.isFailure) { problem = "Your previous reply was not complete valid JSON. Keep each scene compact and reply with ONLY the JSON object."; continue }
                        val got = parsed.getOrThrow().scenes
                        if (got.size < outs.size) { problem = "You returned ${got.size} scenes but exactly ${outs.size} are required."; continue }
                        val merged = outs.mapIndexed { i, o -> got[i].copy(id = o.id, startMs = o.startMs, endMs = o.endMs, narration = o.narration) }
                        val textBad = merged.firstNotNullOfOrNull { ModelOutputGuard.reason(ScenePlanner.sceneText(it), b.narrationLanguage) }
                        if (textBad != null) { problem = "Your previous reply $textBad. Reply with clean text only."; continue }
                        val fatal = SceneValidator.fatal(SceneValidator.validate(SceneManifest(fps = fps, scenes = merged)))
                        if (fatal.isNotEmpty()) { problem = "Fix these problems: " + fatal.take(5).joinToString("; ") { it.message }; continue }
                        val lintBad = merged.firstNotNullOfOrNull { sc -> sc.graph?.let { gr -> SceneLint.problems(SceneLint.adoptCards(gr, aw, ah), aw, ah).firstOrNull() } }
                        if (lintBad != null) { problem = "Design problem: $lintBad. Every scene needs a clear visual that explains its narration, labels inside their shapes, and no empty blocks."; continue }
                        // Same gate the final build uses (after automatic repair), so a scene that would be thrown away later is fixed NOW by the model.
                        val layoutBad = merged.firstNotNullOfOrNull { sc -> sc.graph?.let { gr ->
                            val fx = LayoutEngine.repair(SceneLint.adoptCards(gr, aw, ah), aw, ah, sc.durationMs).graph
                            LayoutEngine.issues(fx, aw, ah, sc.durationMs).firstOrNull()?.toString()
                        } }
                        if (layoutBad != null) { problem = "Layout problem: $layoutBad. Keep every element fully inside the frame, text inside its shape, and elements apart from each other."; continue }
                        accepted = merged; break
                    }
                    if (accepted == null) failedBatches++
                    accepted?.let { ok -> batch.forEachIndexed { k, iv -> modelScenes[iv.index] = ok[k]; sceneCache[cacheKey(b, outs[k])] = ok[k] } }
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
        var prevGraph: SceneGraph? = null
        val dna = StyleDNA.fromAnswer(b.answers["styleSeed"])
        val scenes = ArrayList<Scene>()
        outlines.forEachIndexed { i, o ->
            val beat = beats[i]
            val tr = TransitionPlanner.pick(beat, prevTransition).also { prevTransition = it }
            val dur = o.endMs - o.startMs
            val modelScene = modelScenes[i]
            var graph: SceneGraph? = modelScene?.graph?.let { g ->
                val fixed = LayoutEngine.repair(SceneLint.adoptCards(g, aw, ah), aw, ah, dur).graph
                val prev = prevGraph
                val ok = LayoutEngine.issues(fixed, aw, ah, dur).isEmpty() && SceneLint.problems(fixed, aw, ah).isEmpty() &&
                    (prev == null || !SceneLint.sameLook(prev, fixed))
                if (ok) fixed else null
            }
            if (graph == null) { graph = SceneComposer.compose(beat, b.aspect ?: "9:16", accentOverride = BrandKit.accent(context), dna = dna); localCount++ }
            prevGraph = graph
            scenes += Scene(
                id = o.id, startMs = o.startMs, endMs = o.endMs, narration = o.narration,
                visualObjective = "${beat.intent.name}: ${beat.headline}".take(80), graph = graph,
                assets = modelScene?.assets ?: emptyList(),
                transitionIn = tr, transitionMs = if (tr == "none") 0L else 400L,
                cues = if (BrandKit.autoSfx(context)) SfxPlanner.cuesFor(beat, tr) else emptyList(), caption = o.narration
            )
        }
        val note = StringBuilder("Planned ${scenes.size} beats (about ${"%.1f".format(scenes.sumOf { it.durationMs } / 1000.0 / scenes.size)} s each)")
        note.append(when (strategy) { null -> " — no AI model configured, so visuals were designed locally from your script."; else -> " — model strategy: ${strategy.name}." })
        if (modelDown != null) note.append(" The model became unavailable, so the remaining beats were designed locally.")
        else if (strategy != null && localCount > 0 && designByModel) note.append(" $localCount beat(s) used the local designer because the model's layout failed validation.")
        lastPlanNote = note.toString()

        var m = SceneManifest(fps = fps, title = b.prompt.lineSequence().firstOrNull().orEmpty().take(60), scenes = scenes)
        if (transcript != null) m = alignToTranscript(m, transcript)
        val fatal = SceneValidator.fatal(SceneValidator.validate(m, estMs))
        if (fatal.isNotEmpty()) return Result.failure(IllegalStateException(fatal.take(4).joinToString("; ") { it.message }))
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
        groups.forEachIndexed { i, g ->
            onScene(i + 1, groups.size)
            val text = g.joinToString(" ") { it.narration.trim() }
            DeviceSpeech.synthesize(context, text, b.narrationLanguage, b.pace, dir, g.first().id).fold(
                { c -> clips += NarrationClip(g.first().id, "file://${c.file.absolutePath}", c.durationMs, text, if (g.size > 1) g.map { it.id } else emptyList()) },
                { e -> fails += "Scene ${g.first().id}: ${e.message}" })
        }
        DeviceSpeech.release()
        // Clips are silence-trimmed, so a short natural pause between sentences is enough.
        val retimed = SceneTiming.retimeGrouped(m, clips, padMs = 170L)
        return NarrationOutcome(clips, retimed, fails)
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

    /** Telegram problem the user can act on, in one sentence (empty library, no token...). */
    private fun libraryProblem(token: String, count: Int): String? = when {
        token.isBlank() -> "Telegram asset library is not configured (no bot token in this build). Add TG_BOT_TOKEN and TG_CHAT_ID as GitHub secrets and rebuild."
        count == 0 -> "Your Telegram library is empty for this app: no posts were indexed yet. Upload assets to the channel (the bot must be admin), open Director > Settings > Telegram and tap Sync, or rebuild so the build-time catalog is included."
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
        val all = if (token.isBlank()) emptyList() else runCatching { TelegramAssetManager.syncChannelAssets(botToken = token, context = context) }.getOrDefault(emptyList())
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
        val call = llm(AssetCurator.prompt(b, m.scenes, entries), maxTokens = 3500)
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
            val cands = runCatching { TelegramAssetManager.rankCandidates(context, userId, "sfx", listOf(kind, "sfx"), limit = 2, botToken = token) }.getOrDefault(emptyList())
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
        val raw = if (can(Capability.CHAT)) llm(DirectorPrompts.footageAnalysisPlan(b, a.toJson().toString(), tx)).getOrNull() else null
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
        return llm(DirectorPrompts.narrationFromFootage(b, a.toJson().toString(), tx, keepJson)).mapCatching { raw ->
            val arr = JSONObject(SceneJson.extractJson(raw)).getJSONArray("segments")
            (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Triple(o.getLong("atMs"), o.optLong("maxMs", 6000L), o.getString("text")) }.filter { it.third.isNotBlank() }
                .also { require(it.isNotEmpty()) { "Model returned no narration segments" } }
        }
    }

    suspend fun revisionCall(prompt: String): Result<String> = llm(prompt)

    fun uriOf(s: String): Uri = Uri.parse(s)
}
