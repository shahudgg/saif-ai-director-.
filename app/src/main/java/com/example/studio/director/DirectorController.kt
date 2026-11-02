package com.example.studio.director

import android.content.Context
import com.example.studio.composer.LocalBeatAnalyzer
import com.example.studio.composer.Beat
import com.example.studio.composer.SceneVarietyComposer
import com.example.studio.composer.SceneUniqueness
import com.example.studio.composer.StyleDNA
import android.net.Uri
import com.example.studio.model.StudioProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DirectorUi(
    val workflow: DirectorWorkflow,
    val brief: DirectorBrief,
    val turns: List<ChatTurn> = emptyList(),
    val status: JobStatus? = null,
    val working: Boolean = false,
    val paused: Boolean = false,
    val script: String = "",
    val scriptApproved: Boolean = false,
    val manifest: SceneManifest? = null,
    val sources: List<ResearchSource> = emptyList(),
    val problems: List<String> = emptyList(),
    val sourceName: String? = null,
    val awaitingBriefConfirm: Boolean = false,
    val readyToApply: Boolean = false,
    val applied: Boolean = false,
    /** Validated scene drafts streamed during planning; never persisted or applied as a partial project. */
    val liveScenes: List<Scene> = emptyList(),
    /** Round 14: the scene-by-scene Flow image plan (prompts + generated pictures + verifications). */
    val flowPlan: com.example.flow.FlowPlan? = null
)

/** Persists one conversation per (project, workflow) so users can leave and come back. */
class DirectorStore(context: Context) {
    private val dir = File(context.filesDir, "director_state").apply { mkdirs() }
    private fun key(projectId: String) = projectId.filter { it.isLetterOrDigit() || it == '-' }
    private fun file(projectId: String, wf: DirectorWorkflow) = File(dir, "${key(projectId)}_${wf.name}.json")
    private fun artifactFile(projectId: String, wf: DirectorWorkflow) = File(dir, "${key(projectId)}_${wf.name}_artifacts.json")

    /** Moves every saved conversation of [oldId] to [newId] (used once when a legacy project gets its permanent id). */
    fun moveProject(oldId: String, newId: String) {
        if (key(oldId) == key(newId)) return
        DirectorWorkflow.values().forEach { wf ->
            val from = file(oldId, wf); val to = file(newId, wf)
            if (from.exists() && !to.exists()) from.renameTo(to)
            val fa = artifactFile(oldId, wf); val ta = artifactFile(newId, wf)
            if (fa.exists() && !ta.exists()) fa.renameTo(ta)
        }
    }

    /** Deletes every saved conversation and artifact of a project (used when the project is deleted). */
    fun deleteAll(projectId: String) { DirectorWorkflow.values().forEach { clear(projectId, it) } }

    /** Narration clips and verified assets are kept so scene-level revision still works after the app restarts. */
    fun saveArtifacts(projectId: String, wf: DirectorWorkflow, clips: List<NarrationClip>, assets: List<ResolvedAsset>) {
        val o = JSONObject().apply {
            put("clips", JSONArray().apply { clips.forEach { c -> put(JSONObject().put("s", c.sceneId).put("u", c.uri).put("d", c.durationMs).put("t", c.text).put("g", c.groupIds.joinToString(","))) } })
            put("assets", JSONArray().apply { assets.forEach { a -> put(JSONObject().put("s", a.sceneId).put("r", a.requestId).put("k", a.kind)
                .put("p", a.localPath).put("src", a.source).put("v", a.verified).put("n", a.note).put("at", a.atMs).put("du", a.durMs)) } })
        }
        runCatching { artifactFile(projectId, wf).writeText(o.toString()) }
    }

    fun loadArtifacts(projectId: String, wf: DirectorWorkflow): Pair<List<NarrationClip>, List<ResolvedAsset>> = runCatching {
        val f = artifactFile(projectId, wf)
        if (!f.exists()) return Pair(emptyList(), emptyList())
        val o = JSONObject(f.readText())
        val ca = o.optJSONArray("clips") ?: JSONArray()
        val aa = o.optJSONArray("assets") ?: JSONArray()
        val clips = (0 until ca.length()).map { i -> val x = ca.getJSONObject(i); NarrationClip(x.optString("s"), x.optString("u"), x.optLong("d"), x.optString("t"), x.optString("g").split(',').filter { it.isNotBlank() }) }
        val assets = (0 until aa.length()).map { i -> val x = aa.getJSONObject(i)
            ResolvedAsset(x.optString("s"), x.optString("r"), x.optString("k"), x.optString("p"), x.optString("src"), x.optBoolean("v"), x.optString("n"), x.optLong("at"), x.optLong("du")) }
        Pair(clips, assets)
    }.getOrElse { Pair(emptyList(), emptyList()) }

    fun save(projectId: String, ui: DirectorUi) {
        val b = ui.brief
        val o = JSONObject().apply {
            put("v", 1); put("script", ui.script); put("scriptApproved", ui.scriptApproved); put("sourceName", ui.sourceName ?: "")
            put("applied", ui.applied); put("awaitingBriefConfirm", ui.awaitingBriefConfirm)
            put("brief", JSONObject().apply {
                put("prompt", b.prompt); put("scriptMode", b.scriptMode?.name ?: ""); put("userScript", b.userScript)
                b.durationSec?.let { put("durationSec", it) }; put("narrationLanguage", b.narrationLanguage ?: "")
                put("aspect", b.aspect ?: ""); put("sourceUri", b.sourceUri ?: ""); put("pace", b.pace ?: "")
                put("answers", JSONObject(b.answers)); put("skipped", JSONArray(b.skipped.toList()))
            })
            put("turns", JSONArray().apply { ui.turns.takeLast(80).forEach { t -> put(JSONObject().apply {
                put("u", t.fromUser); put("t", t.text); put("err", t.isError)
                t.card?.let { c -> put("ck", c.key); put("ct", c.title); put("co", JSONArray(c.options)); put("ca", t.cardAnswered ?: "")
                    put("copt", c.optional); put("ccustom", c.allowCustom); put("chint", c.customHint) } }) } })
            ui.manifest?.let { put("manifest", SceneJson.manifestToJson(it)) }
            put("sources", JSONArray().apply { ui.sources.forEach { put(JSONObject().put("t", it.title).put("u", it.url).put("s", it.snippet).put("d", it.publishedDate ?: "")) } })
        }
        runCatching { file(projectId, ui.workflow).writeText(o.toString()) }
    }

    fun load(projectId: String, wf: DirectorWorkflow): DirectorUi? = runCatching {
        val f = file(projectId, wf); if (!f.exists()) return null
        val o = JSONObject(f.readText()); val bo = o.getJSONObject("brief")
        fun s(k: String) = bo.optString(k, "").ifBlank { null }
        val answers = bo.optJSONObject("answers")?.let { a -> a.keys().asSequence().associateWith { a.getString(it) } } ?: emptyMap()
        val brief = DirectorBrief(wf, bo.optString("prompt"), s("scriptMode")?.let { ScriptMode.valueOf(it) }, bo.optString("userScript"),
            if (bo.has("durationSec")) bo.getInt("durationSec") else null, s("narrationLanguage"), aspect = s("aspect"), pace = s("pace"),
            sourceUri = s("sourceUri"), skipped = bo.optJSONArray("skipped")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(), answers = answers)
        var rebuilt = brief
        answers.forEach { (k, v) -> rebuilt = DirectorQuestions.apply(rebuilt, k, v) }
        rebuilt = rebuilt.copy(prompt = brief.prompt, scriptMode = brief.scriptMode, userScript = brief.userScript, sourceUri = brief.sourceUri, skipped = brief.skipped)
        val turns = o.optJSONArray("turns")?.let { a -> (0 until a.length()).map { i -> val t = a.getJSONObject(i)
            ChatTurn(fromUser = t.optBoolean("u"), text = t.optString("t"), isError = t.optBoolean("err"),
                card = if (t.has("ck")) {
                    val cardKey = t.getString("ck")
                    val requiredLegacy = cardKey == "language" || (cardKey == "duration" && wf == DirectorWorkflow.ZERO_TO_VIDEO) ||
                        (cardKey == "narrationMode" && wf == DirectorWorkflow.VIDEO_EDIT_WITH_VOICE)
                    QuestionCard(cardKey, t.optString("ct"), t.getJSONArray("co").let { c -> (0 until c.length()).map { c.getString(it) } },
                        optional = t.optBoolean("copt", !requiredLegacy), allowCustom = t.optBoolean("ccustom", true),
                        customHint = t.optString("chint", "Type your own answer"))
                } else null,
                cardAnswered = t.optString("ca").ifBlank { null }) } } ?: emptyList()
        DirectorUi(wf, rebuilt, turns, script = o.optString("script"), scriptApproved = o.optBoolean("scriptApproved"),
            manifest = o.optJSONObject("manifest")?.let { SceneJson.parseManifest(it.toString()).getOrNull() },
            sources = o.optJSONArray("sources")?.let { a -> (0 until a.length()).map { i -> val x = a.getJSONObject(i); ResearchSource(x.optString("t"), x.optString("u"), x.optString("s"), x.optString("d").ifBlank { null }) } } ?: emptyList(),
            sourceName = o.optString("sourceName").ifBlank { null }, applied = o.optBoolean("applied"),
            awaitingBriefConfirm = o.optBoolean("awaitingBriefConfirm", false))
    }.getOrNull()

    fun clear(projectId: String, wf: DirectorWorkflow) { file(projectId, wf).delete(); artifactFile(projectId, wf).delete() }
}

/**
 * Drives one workflow. Lives in [DirectorRegistry] so jobs survive closing the modal.
 * [getProject]/[applyProject] connect to the real editor timeline (StudioViewModel).
 */
class DirectorController(
    private val appContext: Context, val projectId: String, val workflow: DirectorWorkflow,
    var getProject: () -> StudioProject, var applyProject: (StudioProject) -> Unit
) {
    private val activityBus = AgentActivity()
    val activity: StateFlow<List<ActivityEvent>> = activityBus.events
    private var activeStage: Pair<DirectorStage, Long>? = null
    private var storyboardEpoch = 0L
    private val services = DirectorServices(appContext, activityBus)
    private val store = DirectorStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _ui = MutableStateFlow(store.load(projectId, workflow) ?: DirectorUi(workflow, DirectorBrief(workflow)))
    val ui: StateFlow<DirectorUi> = _ui.asStateFlow()
    private val pausedFlow = MutableStateFlow(false)
    private var analysis: VideoAnalysis? = null
    private var transcript: Transcript? = null
    private var narrationClips: List<NarrationClip> = emptyList()

    /** Licensed SFX found in the Telegram library, by sound kind. Anything missing here is synthesized on the device. */
    private var sfxLib: Map<String, String> = emptyMap()
    private fun sfxLibrary(kind: String): String? = sfxLib[kind]
    private var assets: List<ResolvedAsset> = emptyList()
    private var footage: FootagePlan? = null
    init {
        store.loadArtifacts(projectId, workflow).let { (c, a) -> narrationClips = c; assets = a }
    }
    private var footageNarration: List<Triple<Long, Long, String>> = emptyList()

    init {
        if (_ui.value.turns.isEmpty()) greet() else if (_ui.value.working) update { copy(working = false, status = null) }
        // Round 14: the full-screen Flow browser hands the finished plan back here.
        runCatching { com.example.flow.FlowBridgeBus.onPlanReady = { plan, applyNow -> onFlowPlanReady(plan, applyNow) } }
        runCatching {
            val saved = com.example.flow.FlowPlanStore.load(appContext, projectId)
            if (saved != null) update { copy(flowPlan = saved) }
        }
    }

    @Synchronized
    private fun update(f: DirectorUi.() -> DirectorUi) {
        val previous = _ui.value
        val next = previous.f()
        _ui.value = next
        if (previous.working && !next.working) {
            finishStage(if (next.status?.failed == true) ActivityState.FAILED else ActivityState.SUCCEEDED)
        }
        store.save(projectId, next)
    }

    private fun stageKind(stage: DirectorStage): ActivityKind = when (stage) {
        DirectorStage.RESEARCHING -> ActivityKind.RESEARCH
        DirectorStage.WRITING_SCRIPT -> ActivityKind.SCRIPT
        DirectorStage.PLANNING_SCENES -> ActivityKind.PLANNING
        DirectorStage.GENERATING_ANIMATIONS -> ActivityKind.SCENE_JSON
        DirectorStage.FINDING_ASSETS, DirectorStage.CHECKING_ASSETS -> ActivityKind.ASSET_SEARCH
        DirectorStage.GENERATING_NARRATION -> ActivityKind.NARRATION
        DirectorStage.BUILDING_TIMELINE -> ActivityKind.TIMELINE
        DirectorStage.ANALYZING_SOURCE -> ActivityKind.SOURCE
        else -> ActivityKind.MODEL
    }

    private fun finishStage(state: ActivityState) {
        activeStage?.let { activityBus.finish(it.second, state) }
        activeStage = null
    }

    @Synchronized
    private fun beginStoryboard(): Long {
        storyboardEpoch++
        _ui.value = _ui.value.copy(liveScenes = emptyList())
        return storyboardEpoch
    }

    @Synchronized
    private fun publishStoryboard(epoch: Long, scene: Scene) {
        if (epoch != storyboardEpoch || !_ui.value.working) return
        val drafts = (_ui.value.liveScenes.filterNot { it.id == scene.id } + scene).sortedBy { it.startMs }
        _ui.value = _ui.value.copy(liveScenes = drafts) // transient UI only: never write project storage per scene
    }
    /** Long-lived facts about the owner's channel. Everything else (a one-off topic) stays out of memory. */
    private fun rememberAnswer(key: String, value: String) {
        val v = value.trim()
        if (v.isBlank() || v.length > 160) return
        val factKey = when (key) {
            "channelName" -> "youtube channel name"
            "niche" -> "content niche"
            "audience" -> "target audience"
            "style" -> "preferred video style"
            "voiceStyle" -> "preferred narration style"
            "language" -> "preferred language"
            else -> null
        } ?: return
        com.example.util.SaifMemoryCore.remember(
            com.example.util.SaifMemoryCore.Features.VIDEO, factKey, v
        )
    }

    private fun say(text: String, error: Boolean = false, card: QuestionCard? = null) =
        update { copy(turns = turns + ChatTurn(fromUser = false, text = text, card = card, isError = error)) }
    @Synchronized
    private fun status(stage: DirectorStage, label: String, fraction: Float? = null, detail: String = "") {
        if (activeStage?.first != stage) {
            finishStage(ActivityState.SUCCEEDED)
            activeStage = stage to activityBus.begin(stageKind(stage), label, detail)
        } else activeStage?.let { activityBus.detail(it.second, detail, label) }
        update { copy(status = JobStatus(stage, label, fraction, detail), working = true) }
    }

    private fun greet() {
        val msg = when (workflow) {
            DirectorWorkflow.ZERO_TO_VIDEO -> "Tell me the topic, or paste your full script. I'll ask only what I still need."
            DirectorWorkflow.VIDEO_TO_EDITING -> "Pick the video you want edited. I'll edit it — I won't add new narration."
            DirectorWorkflow.VIDEO_EDIT_WITH_VOICE -> "Pick your video. I'll edit it and write AI narration based on what is really in it."
            DirectorWorkflow.VOICE_OVER_TO_VIDEO -> "Pick your voice-over audio. It stays untouched; I'll build visuals around it."
        }
        say(msg)
    }

    // ---------------- conversation ----------------

    fun send(text: String) {
        val t = text.trim(); if (t.isEmpty() || _ui.value.working) return
        update { copy(turns = turns + ChatTurn(fromUser = true, text = t)) }
        val s = _ui.value
        when {
            s.script.isNotBlank() && !s.scriptApproved && s.manifest == null -> reviseScript(t)
            s.manifest != null && (s.readyToApply || s.applied) -> reviseScene(t)
            s.brief.prompt.isBlank() && !workflow.needsSourceVideo && !workflow.needsSourceAudio -> {
                update { copy(brief = DirectorQuestions.seed(brief, t)) }; nextStep()
            }
            s.brief.prompt.isBlank() -> { update { copy(brief = DirectorQuestions.seed(brief, t)) }; nextStep() }
            else -> { update { copy(brief = brief.copy(answers = brief.answers + ("note${brief.answers.size}" to t))) }; nextStep() }
        }
    }

    fun onSourcePicked(uri: Uri, name: String) {
        update { copy(brief = brief.copy(sourceUri = uri.toString()), sourceName = name, turns = turns + ChatTurn(fromUser = true, text = "📎 $name")) }
        nextStep()
    }

    fun answer(key: String, value: String) {
        val ui = _ui.value
        val current = ui.turns.lastOrNull()
        if (ui.working || (workflow == DirectorWorkflow.ZERO_TO_VIDEO && ui.manifest != null) || value.isBlank() || current?.card?.key != key || current.cardAnswered != null) return
        // The owner's answers become app-wide memories: the chat and the live conversation know the channel,
        // the niche and the tone too, and the next video starts from them instead of asking again.
        rememberAnswer(key, value)
        update {
            val idx = turns.indexOfLast { it.card?.key == key }
            val newTurns = if (idx >= 0) turns.toMutableList().also { it[idx] = it[idx].copy(cardAnswered = value) } else turns
            copy(turns = newTurns, brief = DirectorQuestions.apply(brief, key, value))
        }
        nextStep()
    }

    fun skip(key: String) {
        val ui = _ui.value
        val current = ui.turns.lastOrNull()
        if (ui.working || (workflow == DirectorWorkflow.ZERO_TO_VIDEO && ui.manifest != null) || current?.card?.key != key || current.cardAnswered != null || current.card?.optional != true) return
        update {
            val idx = turns.indexOfLast { it.card?.key == key && it.cardAnswered == null }
            val marked = if (idx >= 0) turns.toMutableList().also { it[idx] = it[idx].copy(cardAnswered = "Skipped") } else turns
            copy(brief = DirectorQuestions.skip(brief, key), turns = marked)
        }
        nextStep()
    }

    /** Quick brief cards share the same model as the conversational questions. */
    @Synchronized
    fun selectBriefOption(key: String, value: String) {
        val ui = _ui.value
        if (workflow != DirectorWorkflow.ZERO_TO_VIDEO || ui.working || ui.manifest != null || value.isBlank()) return
        if (key !in setOf("duration", "aspect", "style", "audience", "language")) return
        if (key == "duration" && SpeechTiming.parseDuration(value) == null) return
        val current = ui.turns.lastOrNull()
        if (current?.card?.key == key && current.cardAnswered == null) {
            answer(key, value) // advance the already-visible question exactly once
            return
        }
        rememberAnswer(key, value)
        update {
            val idx = turns.indexOfLast { it.card?.key == key && it.cardAnswered != null }
            val marked = if (idx >= 0) turns.toMutableList().also { it[idx] = it[idx].copy(cardAnswered = value) } else turns
            copy(turns = marked).withBrief(DirectorQuestions.apply(brief, key, value))
        }
    }

    /** Per-video override; null follows the app's selected voice without changing global settings. */
    fun selectNarrationVoice(id: String?) {
        if (workflow != DirectorWorkflow.ZERO_TO_VIDEO || _ui.value.working || _ui.value.manifest != null) return
        if (id != null && com.example.ui.components.VOICE_PROFILES.none { it.id == id }) return
        update { withBrief(GuidedBriefOptions.withVoice(brief, id)) }
    }

    /** The picker probes and persists permission before calling this. Existing projects change only on Apply. */
    fun selectBackgroundMusic(music: BackgroundMusic?) {
        if (workflow != DirectorWorkflow.ZERO_TO_VIDEO || _ui.value.working) return
        if (music != null && (music.uri.isBlank() || music.durationMs <= 0)) return
        update { withBrief(GuidedBriefOptions.withMusic(brief, music)) }
        val state = _ui.value
        val manifest = state.manifest ?: return
        if (!state.readyToApply && !state.applied) return
        val (project, notes) = TimelineAssembler.withBackgroundMusic(pendingProject ?: getProject(), manifest.totalMs, music)
        pendingProject = project
        update { copy(readyToApply = true, problems = problems.filterNot { it.startsWith("Music ") } + notes,
            status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Music updated — tap Add to timeline")) }
    }

    fun editAnswer(key: String) {
        if (_ui.value.working || (workflow == DirectorWorkflow.ZERO_TO_VIDEO && _ui.value.manifest != null)) return
        update { copy(brief = brief.copy(answers = brief.answers - key, skipped = brief.skipped - key,
            durationSec = if (key == "duration") null else brief.durationSec, narrationLanguage = if (key == "language") null else brief.narrationLanguage,
            aspect = if (key == "aspect") null else brief.aspect, style = if (key == "style") null else brief.style,
            audience = if (key == "audience") null else brief.audience, subtitles = if (key == "subtitles") null else brief.subtitles), awaitingBriefConfirm = false) }
        val card = DirectorQuestions.next(_ui.value.brief)
        if (card != null) say("Let's redo that one.", card = card)
    }

    @Synchronized
    fun cancel() {
        job?.cancel(); job = null
        storyboardEpoch++
        finishStage(ActivityState.CANCELLED)
        activityBus.cancelOpen()
        update { copy(working = false, status = null, paused = false, liveScenes = emptyList()) }
        pausedFlow.value = false
        say("Cancelled. Everything saved so far is kept — nothing was added to your timeline.")
    }
    fun setPaused(p: Boolean) { pausedFlow.value = p; update { copy(paused = p) } }
    private suspend fun gate() {
        currentCoroutineContext().ensureActive()
        if (pausedFlow.value) pausedFlow.first { !it }
        currentCoroutineContext().ensureActive()
    }

    /** Clean spoken narration only (no citations, labels, markdown) — exactly what a voice artist or TTS tool should read. */
    fun voiceoverText(): String = VoiceoverScript.clean(_ui.value.script)

    @Synchronized
    fun resetConversation() {
        job?.cancel(); job = null
        activeStage = null; storyboardEpoch++; activityBus.clear(); pausedFlow.value = false
        store.clear(projectId, workflow); narrationClips = emptyList(); assets = emptyList(); pendingProject = null
        _ui.value = DirectorUi(workflow, DirectorBrief(workflow)); greet()
    }

    // ---------------- stage machine ----------------

    private fun confirmationText(b: DirectorBrief): String {
        val warn = if (b.scriptMode == ScriptMode.FULL_SCRIPT_PRESERVE && b.durationSec != null)
            SpeechTiming.feasibilityWarning(b.durationSec, SpeechTiming.countWords(b.userScript), b.narrationLanguage, b.pace) else null
        return DirectorQuestions.summary(b) + (warn?.let { "\n\n⚠️ $it" } ?: "") + "\n\nTap “Confirm & start” to begin, or edit any answer."
    }

    private fun DirectorUi.withBrief(b: DirectorBrief): DirectorUi {
        if (!awaitingBriefConfirm) return copy(brief = b)
        val idx = turns.indexOfLast { !it.fromUser && it.card == null && it.text.startsWith("Brief — ") }
        val refreshed = if (idx >= 0) turns.toMutableList().also { it[idx] = it[idx].copy(text = confirmationText(b)) } else turns
        return copy(brief = b, turns = refreshed)
    }

    private fun nextStep() {
        val s = _ui.value
        if ((workflow.needsSourceVideo || workflow.needsSourceAudio) && s.brief.sourceUri == null) {
            say(if (workflow.needsSourceAudio) "Please attach the voice-over audio file first." else "Please attach the video first."); return
        }
        if (s.brief.prompt.isBlank() && workflow == DirectorWorkflow.ZERO_TO_VIDEO) return
        val card = DirectorQuestions.next(s.brief)
        if (card != null) { say(card.title, card = card); return }
        // Brief complete → warn about unrealistic length, then ask for confirmation.
        say(confirmationText(s.brief))
        update { copy(awaitingBriefConfirm = true) }
    }

    fun confirmAndStart() {
        if (_ui.value.working) return
        update { copy(awaitingBriefConfirm = false, problems = emptyList(), working = true) }
        job = scope.launch { guarded { when (workflow) {
            DirectorWorkflow.ZERO_TO_VIDEO -> runZero()
            DirectorWorkflow.VIDEO_TO_EDITING, DirectorWorkflow.VIDEO_EDIT_WITH_VOICE -> runFootage()
            DirectorWorkflow.VOICE_OVER_TO_VIDEO -> runVoiceOver()
        } } }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { fail(e.message ?: "Unexpected error") }
    }

    private fun fail(msg: String) { update { copy(working = false, liveScenes = emptyList(), status = JobStatus(DirectorStage.FAILED, "Failed", null, msg, failed = true, retryable = true)) }; say("❌ $msg", error = true) }
    fun retry() { if (!_ui.value.working) confirmAndStart() }

    private suspend fun runZero() {
        val b = _ui.value.brief
        if (!services.can(Capability.CHAT)) { fail(ProviderRegistry.missingMessage(Capability.CHAT)); return }
        ensureSeed()
        var script = _ui.value.script
        if (script.isBlank()) {
            status(DirectorStage.RESEARCHING, "Researching the topic")
            val r = services.research(_ui.value.brief) { step -> status(DirectorStage.RESEARCHING, "Researching the topic", detail = step) }; gate()
            update { copy(sources = r.sources) }
            say(r.note)
            status(DirectorStage.WRITING_SCRIPT, "Writing the script")
            script = services.writeScript(_ui.value.brief, r.digest).getOrElse { fail("Script writing failed: ${it.message}"); return }
            gate()
            val words = SpeechTiming.countWords(script); val target = b.durationSec?.let { SpeechTiming.targetWords(it, b.narrationLanguage, b.pace) }
            update { copy(script = script, scriptApproved = false, working = false, status = JobStatus(DirectorStage.AWAITING_SCRIPT_APPROVAL, "Script ready for your review")) }
            say("Here is the script ($words words${target?.let { ", target ≈ $it" } ?: ""}). Reply with changes, or tap Approve.${services.lastScriptNote.takeIf { it.isNotBlank() }?.let { "\n($it)" } ?: ""}\n\n$script")
            return
        }
        produceFromScript(script)
    }

    fun approveScript() {
        if (_ui.value.working || _ui.value.script.isBlank()) return
        update { copy(scriptApproved = true, working = true) }
        job = scope.launch { guarded { produceFromScript(_ui.value.script) } }
    }

    /**
     * Round 13 — SAIF AGENT CREW (multi-agent, supervised). One goal in, a finished video out:
     * PLANNER decides the brief + researches, SCRIPT writes the narration, ASSEMBLER plans unique scenes +
     * generates AI key visuals + narrates + builds the timeline, PLACER fixes Flow-browser images onto the
     * scenes in order (with vision checks). The WATCHDOG retries any failed step once and then takes the
     * declared fallback path — every handoff is logged live in the chat panel.
     */
    fun agentRun(goal: String) {
        if (_ui.value.working || goal.isBlank()) return
        if (workflow != DirectorWorkflow.ZERO_TO_VIDEO) { say("The SAIF Agent runs the Zero-to-video workflow — open that first.", error = true); return }
        update { copy(working = true, problems = emptyList(), awaitingBriefConfirm = false) }
        job = scope.launch { guarded {
            val crew = AgentOrchestrator(activityBus)
            say("🤖 SAIF AGENT crew start: PLANNER → SCRIPT → ASSEMBLER → PLACER, WATCHDOG har step pe nazar rakhega. Goal: " + goal.take(120))
            status(DirectorStage.RESEARCHING, "SAIF Agent crew deciding the brief", detail = goal.take(80))
            val dec = crew.supervised(AgentRole.PLANNER, "brief + research decide kar raha hai", goal.take(120),
                fallbackNote = "Model brief nahi de paya — default brief (60s, 9:16) use hoga.") {
                services.agentDecide(goal, _ui.value.brief)
            }.getOrElse {
                AgentDecision(_ui.value.brief.copy(prompt = goal, durationSec = _ui.value.brief.durationSec ?: 60, aspect = _ui.value.brief.aspect ?: "9:16"), "")
            }
            update { withBrief(dec.brief) }
            gate()
            val b = _ui.value.brief
            status(DirectorStage.RESEARCHING, "SAIF Agent researching the topic")
            val r = crew.supervised(AgentRole.PLANNER, "topic research kar raha hai", "",
                fallbackNote = "Research skip — model ke apne knowledge se script likha jayega.") {
                Result.success(services.research(b) { step -> status(DirectorStage.RESEARCHING, "SAIF Agent researching the topic", detail = step) })
            }.getOrNull() ?: ResearchOutcome(null, emptyList(), "Research skipped (watchdog fallback).")
            gate()
            update { copy(sources = r.sources) }
            var script = dec.script
            if (script.isBlank()) {
                status(DirectorStage.WRITING_SCRIPT, "SAIF Agent script likh raha hai")
                script = crew.supervised(AgentRole.SCRIPT, "narration script likh raha hai", "",
                    fallbackNote = "Model script nahi likh paya — simple local script use hoga.") {
                    services.writeScript(b, r.digest)
                }.getOrElse {
                    "Hook: " + goal.take(90) + ".\n\n" + goal.take(220) +
                        "\n\nTeen points mein samjhata hoon: pehla, doosra, teesra. Ant mein ek clear call to action."
                }
            }
            gate()
            update { copy(script = script, scriptApproved = true) }
            say("🤖 Brief + script ready (" + SpeechTiming.countWords(script) + " words, " + (b.durationSec ?: 60) + "s, " + (b.aspect ?: "9:16") + "). ASSEMBLER ab plan + AI key visuals + narration + timeline banayega…")
            produceFromScript(script)
            gate()
            // Round 14: if the owner already ran the Flow pipeline, its verified pictures + transitions win.
            val storedPlan = com.example.flow.FlowPlanStore.load(appContext, projectId)
            val flowReady = storedPlan != null && storedPlan.slots.any { it.imagePath.isNotBlank() && File(it.imagePath).exists() }
            if (flowReady && storedPlan != null && _ui.value.manifest != null) {
                crew.supervised(AgentRole.PLACER, "Flow ki " + storedPlan.slots.count { it.imagePath.isNotBlank() } +
                    " pictures ko scenes + transitions ke saath lagaya ja raha hai", "",
                    fallbackNote = "Flow placement skip — AI key visuals hi use honge.") {
                    try {
                        applyFlowPlanInternal(storedPlan, applyNow = false)
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure<Unit>(e)
                    }
                }
                gate()
            } else if (_ui.value.manifest != null) {
                say("ℹ️ Flow pictures nahi mili, isliye AI key visuals use hui. Scene-by-scene Flow images chahiye to '🖼 Flow images (auto)' dabao — " +
                    "prompts script se banenge, browser khulega, aur 8s gap pe images generate + verify hokar lag jayengi.")
            }
            if (_ui.value.readyToApply) {
                applyToTimeline()
                say("✅ SAIF AGENT crew ne kaam khatam kiya — video timeline pe aa gaya. Editor mein Export dabao (MP4).")
            }
        } }
    }

    private fun reviseScript(instruction: String) {
        job = scope.launch { guarded {
            status(DirectorStage.WRITING_SCRIPT, "Revising the script")
            val b = _ui.value.brief
            val out = services.reviseScript(b, _ui.value.script, instruction).getOrElse { fail("Revision failed: ${it.message}"); return@guarded }
            update { copy(script = out, scriptApproved = false, working = false, status = JobStatus(DirectorStage.AWAITING_SCRIPT_APPROVAL, "Revised script ready")) }
            say("Updated script:\n\n$out")
        } }
    }

    /** StyleDNA v2 is derived from the normalized creative brief, not wall time or a random seed. */
    private fun ensureSeed() {
        val dna = com.example.studio.composer.StyleDNA.fromBrief(_ui.value.brief)
        if (_ui.value.brief.answers["styleSignature"] != dna.signature || _ui.value.brief.answers["styleSeed"] != dna.seed.toString()) {
            update { copy(brief = brief.copy(answers = brief.answers + mapOf("styleSeed" to dna.seed.toString(), "styleSignature" to dna.signature))) }
        }
    }

    private fun attachCheckedMusic(project: StudioProject, manifest: SceneManifest, brief: DirectorBrief): Pair<StudioProject, List<String>> {
        val choice = GuidedBriefOptions.music(brief) ?: return TimelineAssembler.withBackgroundMusic(project, manifest.totalMs, null)
        val checked = BackgroundMusicProbe.inspect(appContext, Uri.parse(choice.uri))
        if (checked.isFailure) {
            val clean = TimelineAssembler.withBackgroundMusic(project, manifest.totalMs, null).first
            return clean to listOf("Music was not added: ${checked.exceptionOrNull()?.message ?: "source unavailable"}")
        }
        return TimelineAssembler.withBackgroundMusic(project, manifest.totalMs, choice.copy(durationMs = checked.getOrThrow()))
    }

    private suspend fun produceFromScript(script: String) {
        ensureSeed()
        val b = _ui.value.brief
        status(DirectorStage.PLANNING_SCENES, "Planning scenes and animations")
        val epoch = beginStoryboard()
        var manifest = services.planManifest(b, script,
            onProgress = { i, n -> status(DirectorStage.PLANNING_SCENES, "Planning scenes and animations", (i - 1f) / n, "Batch $i of $n") },
            onSceneAccepted = { _, scene -> publishStoryboard(epoch, scene) }
        ).getOrElse { fail("Scene planning failed: ${it.message}"); return }
        gate()
        services.lastPlanNote.takeIf { it.isNotBlank() }?.let { say(it) }
        update { copy(manifest = manifest, liveScenes = emptyList()) }
        // Round 10 — IMAGE-FIRST: one AI key visual per scene is generated from the plan BEFORE narration, so the
        // code-animated graphics play over a real generated backdrop (needs any image-capable key in Settings;
        // without one this step is skipped with a note and the video is pure code-animation as before).
        status(DirectorStage.GENERATING_ANIMATIONS, "Generating AI key visuals", detail = "0/${manifest.scenes.size}")
        val (genAssets, genNote) = services.generateSceneImages(b, manifest) { i, n, t ->
            status(DirectorStage.GENERATING_ANIMATIONS, "Generating AI key visuals", i.toFloat() / n, t)
        }
        gate()
        genNote?.let { say(it) }
        val genSceneIds = genAssets.map { it.sceneId }.toSet()
        status(DirectorStage.GENERATING_NARRATION, "Generating narration", detail = "0/${manifest.scenes.size}")
        val nar = services.synthNarration(b, manifest) { i, n -> status(DirectorStage.GENERATING_NARRATION, "Generating narration", i.toFloat() / n, "$i/$n") }
        gate()
        if (nar.clips.isEmpty()) { fail("Narration could not be generated. " + nar.failures.firstOrNull().orEmpty()); return }
        manifest = nar.manifest; narrationClips = nar.clips; gate()
        // The asset hunter fills the timeline ONE ASSET AT A TIME: it looks at one place, asks for that one file
        // (format + 2-3 keywords), searches Telegram, downloads it, LOOKS at it, retries with changed keywords when
        // it is wrong, and only then places it and moves on to the next place.
        // Round 13: Telegram asset hunting REMOVED — visuals come from the Flow AI browser + AI image generation only.
        val useLibrary = false
        status(DirectorStage.FINDING_ASSETS, "Preparing Flow AI / generated visuals")
        val ao = AssetOutcome(emptyList(), listOf("Visuals: Flow AI images + one AI-generated key visual per scene (Telegram library disabled)."), emptyList())
        gate(); assets = genAssets + ao.assets
        val requestNote = if (useLibrary) requestNeeds(b, manifest, ao) else null
        // Scenes that got a real library sound do not also get a synthetic one on top.
        manifest = manifest.copy(scenes = manifest.scenes.map { sc -> if (assets.any { it.sceneId == sc.id && it.kind == "audio" }) sc.copy(cues = emptyList()) else sc })
        sfxLib = cancellableResult { services.resolveSfxLibrary(manifest, "default") }.getOrDefault(emptyMap())
        gate()
        store.saveArtifacts(projectId, workflow, narrationClips, assets)
        status(DirectorStage.BUILDING_TIMELINE, "Building the timeline")
        val (assembled, probs) = TimelineAssembler.assemble(getProject(), manifest, narrationClips, assets, captions = b.subtitles?.startsWith("No", true) != true, aspect = b.aspect,
            sfx = SfxFiles.render(appContext, manifest, library = { kind -> sfxLibrary(kind) }), captionStyle = BrandKit.captionStyle(appContext))
        val (p, musicNotes) = attachCheckedMusic(assembled, manifest, b)
        val all = nar.failures + ao.notes + listOfNotNull(requestNote) + ao.unresolved + probs + musicNotes
        gate()
        pendingProject = p
        update { copy(manifest = manifest, problems = all, working = false, readyToApply = true,
            status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Ready — ${manifest.scenes.size} scenes, ${narrationClips.size} narration clips, ${assets.size} verified assets")) }
        // Show the one-at-a-time hunt so the owner can see which file was accepted for which place, and which
        // places were rejected and retried with different keywords.
        val hunt = services.lastHuntLog.filter { it.contains("ACCEPTED") || it.contains("asked") || it.contains("rejected") }
        val huntBlock = if (useLibrary && hunt.isNotEmpty()) "\n\nAsset hunt (one at a time):\n" + hunt.take(14).joinToString("\n") { "• $it" } else ""
        say("Your video is built: ${manifest.scenes.size} scenes, total ${manifest.totalMs / 1000}s." +
            (if (all.isNotEmpty()) "\n\nNotes:\n" + all.take(8).joinToString("\n") { "• $it" } else "") + huntBlock +
            "\n\nTap “Add to timeline” to preview it, or tell me what to change.")
    }

    private suspend fun runVoiceOver() {
        val b = _ui.value.brief; val uri = Uri.parse(b.sourceUri)
        status(DirectorStage.ANALYZING_SOURCE, "Transcribing your voice-over")
        val tr = Transcriber.transcribe(appContext, uri, b.narrationLanguage).getOrElse { fail("${it.message}\nI will not invent a transcript. Add a Groq/OpenAI key for transcription, then retry."); return }
        transcript = tr; gate()
        val total = AudioDurations.durationMs(appContext, uri).takeIf { it > 0 } ?: tr.segments.maxOf { it.endMs }
        say("Transcribed ${tr.segments.size} segments (${tr.provider}). Total ${total / 1000}s.")
        ensureSeed()
        val b2 = _ui.value.brief
        status(DirectorStage.PLANNING_SCENES, "Planning scenes from your narration")
        val epoch = beginStoryboard()
        var m = services.planManifest(b2, tr.fullText, tr, total,
            onProgress = { i, n -> status(DirectorStage.PLANNING_SCENES, "Planning scenes from your narration", (i - 1f) / n, "Batch $i of $n") },
            researchDigest = null, onSceneAccepted = { _, scene -> publishStoryboard(epoch, scene) }
        ).getOrElse { fail("Scene planning failed: ${it.message}"); return }
        gate()
        services.lastPlanNote.takeIf { it.isNotBlank() }?.let { say(it) }
        m = m.copy(scenes = m.scenes.mapIndexed { i, s -> if (i == m.scenes.lastIndex) s.copy(endMs = maxOf(s.endMs, total)) else s })
        update { copy(manifest = m, liveScenes = emptyList()) }
        // Round 10 — IMAGE-FIRST key visuals per scene (skipped automatically without an image-capable key).
        status(DirectorStage.GENERATING_ANIMATIONS, "Generating AI key visuals", detail = "0/${m.scenes.size}")
        val (genAssets2, genNote2) = services.generateSceneImages(b2, m) { i, n, t ->
            status(DirectorStage.GENERATING_ANIMATIONS, "Generating AI key visuals", i.toFloat() / n, t)
        }
        gate()
        genNote2?.let { say(it) }
        status(DirectorStage.FINDING_ASSETS, "Hunting assets one at a time from your Telegram library")
        // Round 13: Telegram hunting removed — visuals come from Flow AI / AI image generation.
        val ao = AssetOutcome(emptyList(), emptyList(), listOf("Visuals: Flow AI images + AI-generated key visuals (Telegram library disabled)."), emptyList())
        gate(); assets = genAssets2 + ao.assets
        val reqNote = requestNeeds(b, m, ao)
        store.saveArtifacts(projectId, workflow, narrationClips, assets)
        status(DirectorStage.BUILDING_TIMELINE, "Building the timeline")
        val (p, probs) = TimelineAssembler.assemble(getProject(), m, emptyList(), assets, captions = b.subtitles?.startsWith("No", true) != true, voiceOverUri = b.sourceUri, aspect = b.aspect)
        val all = ao.notes + ao.unresolved + listOfNotNull(reqNote) + probs
        gate()
        pendingProject = p
        update { copy(problems = all, working = false, readyToApply = true, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Ready — voice-over preserved, ${m.scenes.size} scenes")) }
        say("Built ${m.scenes.size} scenes around your original voice-over (unchanged)." + (if (all.isNotEmpty()) "\n\nNotes:\n" + all.take(8).joinToString("\n") { "• $it" } else "") + "\n\nTap “Add to timeline”.")
    }

    private suspend fun runFootage() {
        val b = _ui.value.brief; val uri = Uri.parse(b.sourceUri)
        status(DirectorStage.ANALYZING_SOURCE, "Analyzing your video")
        val a = VideoAnalyzer.analyze(appContext, uri) { st -> status(DirectorStage.ANALYZING_SOURCE, st) }.getOrElse { fail(it.message ?: "Video analysis failed"); return }
        analysis = a; gate()
        val probsLocal = mutableListOf<String>()
        if (a.hasAudio && services.can(Capability.AUDIO_TRANSCRIPTION)) {
            status(DirectorStage.ANALYZING_SOURCE, "Transcribing speech for analysis")
            Transcriber.transcribe(appContext, uri, b.narrationLanguage).fold({ transcript = it }, { probsLocal += "Transcription unavailable: ${it.message}" })
        } else if (a.hasAudio) probsLocal += ProviderRegistry.missingMessage(Capability.AUDIO_TRANSCRIPTION)
        status(DirectorStage.PLANNING_SCENES, "Creating the edit plan")
        val plan = services.planFootageEdit(b, a, transcript); footage = plan; gate()
        val kept = plan.keep.sumOf { it.sourceOutMs - it.sourceInMs }
        say("Analysis: ${a.durationMs / 1000}s, ${a.width}x${a.height}, ${a.silences.size} silent gaps, ${a.sceneChangesMs.size} visual cuts detected.\nPlan: ${plan.summary}\nKeeps ${kept / 1000}s of ${a.durationMs / 1000}s across ${plan.keep.size} segments. Your original file is untouched.")

        if (workflow == DirectorWorkflow.VIDEO_EDIT_WITH_VOICE) {
            status(DirectorStage.WRITING_SCRIPT, "Writing narration from the footage")
            val segs = services.narrationForFootage(b, a, transcript, plan.keep).getOrElse { fail("Narration writing failed: ${it.message}"); return }
            footageNarration = emptyList()
            val made = mutableListOf<Triple<Long, String, Pair<Long, String>>>()
            val dir = File(appContext.filesDir, "director_audio/footage")
            try { segs.forEachIndexed { i, (at, maxMs, text) ->
                currentCoroutineContext().ensureActive()
                status(DirectorStage.GENERATING_NARRATION, "Generating narration", i.toFloat() / segs.size, "${i + 1}/${segs.size}")
                val tl = TimelineAssembler.sourceToTimelineMs(plan.keep, at) ?: return@forEachIndexed
                DeviceSpeech.synthesize(appContext, text, b.narrationLanguage, b.pace, dir, "n$i").fold({ c ->
                    if (c.durationMs > maxMs + 400) probsLocal += "Narration at ${at / 1000}s is ${c.durationMs - maxMs} ms longer than its window; shorten that line or allow a longer cut."
                    made += Triple(tl, "file://${c.file.absolutePath}", c.durationMs to text)
                }, { probsLocal += "Narration at ${at / 1000}s failed: ${it.message}" })
            } } finally { DeviceSpeech.release() }
            if (made.isEmpty()) { fail("No narration audio could be generated. " + probsLocal.lastOrNull().orEmpty()); return }
            footageNarration = made.map { Triple(it.first, it.third.first, it.third.second + "\u0000" + it.second) }
        }

        status(DirectorStage.BUILDING_TIMELINE, "Building the timeline")
        val manifest = SceneManifest(title = "Edit", scenes = emptyList())
        var (p, probs) = TimelineAssembler.assemble(getProject(), manifest.copy(scenes = listOf(Scene("footage", 0, kept.coerceAtLeast(1000), visualObjective = "footage"))), emptyList(), emptyList(), footage = plan.keep, captions = false, aspect = b.aspect ?: a.aspectLabel)
        transcript?.takeIf { plan.captions }?.segments?.forEach { s ->
            val st = TimelineAssembler.sourceToTimelineMs(plan.keep, s.startMs) ?: return@forEach
            p = TimelineAssembler.addTextAt(p, st, (s.endMs - s.startMs).coerceAtLeast(600), s.text, "caption")
        }
        plan.overlays.forEach { (at, d, text) -> TimelineAssembler.sourceToTimelineMs(plan.keep, at)?.let { p = TimelineAssembler.addTextAt(p, it, d, text, "overlay") } }
        footageNarration.forEach { (tl, dur, packed) -> val (text, uriStr) = packed.split("\u0000"); p = TimelineAssembler.addVoiceAt(p, tl, uriStr, dur, text) }
        if (workflow == DirectorWorkflow.VIDEO_EDIT_WITH_VOICE) {
            val mode = b.narrationMode ?: ""
            p = TimelineAssembler.setFootageVolume(p, when { mode.startsWith("Replace") -> 0f; mode.startsWith("Mix") || mode.startsWith("Narrate") -> 0.6f; else -> 0.45f })
        }
        if (workflow == DirectorWorkflow.VIDEO_TO_EDITING && p.items.any { it.trackType == com.example.studio.model.TrackType.VOICE && it.origin == "ai" })
            error("Internal guard: VIDEO TO EDITING must not contain generated narration")
        gate()
        pendingProject = p
        val all = probsLocal + probs.filterNot { it.startsWith("Scene footage") }
        update { copy(problems = all, working = false, readyToApply = true, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Edit ready — ${plan.keep.size} segments")) }
        say("Edit ready." + (if (all.isNotEmpty()) "\n\nNotes:\n" + all.take(8).joinToString("\n") { "• $it" } else "") + "\n\nTap “Add to timeline”. The change is one undo step.")
    }

    /** Asks on Telegram for what the AI asset editor could not find in the library (once per need per day). */
    private suspend fun requestNeeds(b: DirectorBrief, m: SceneManifest, ao: AssetOutcome): String? {
        if (ao.needs.isEmpty()) return null
        val token = WebResearch.telegramToken(appContext)
        if (token.isBlank()) return null
        val lines = AssetCurator.needLines(ao.needs, m.scenes)
        val title = m.title.ifBlank { b.prompt.take(40) }
        return AssetRequester.request(appContext, token, com.example.ui.components.TelegramAssetManager.DEFAULT_CHANNEL_ID, title, lines).fold(
            { n -> if (n > 0) "Asked you on Telegram for $n missing asset(s). Upload them, then tap “Check Telegram again”." else "Missing assets were already requested on Telegram recently." },
            { e -> "Could not ask for missing assets on Telegram: ${e.message}" })
    }

    /** Asks for missing library assets in the user's Telegram chat (once per need per day). Returns a note for the report. */
    private suspend fun requestMissingAssets(b: DirectorBrief, m: SceneManifest, ao: AssetOutcome): String? {
        val missing = ao.unresolved.filter { it.contains("no suitable asset") }
        if (missing.isEmpty()) return null
        val token = WebResearch.telegramToken(appContext)
        if (token.isBlank()) return null
        val needs = missing.mapNotNull { line ->
            val sid = line.substringBefore('/')
            val scene = m.scenes.firstOrNull { it.id == sid } ?: return@mapNotNull null
            val req = scene.assets.firstOrNull { line.contains("${it.id}:") || line.startsWith("$sid/${it.id}") } ?: scene.assets.firstOrNull() ?: return@mapNotNull null
            "${req.kind}: ${req.description} (scene ${m.scenes.indexOf(scene) + 1})"
        }.distinct()
        if (needs.isEmpty()) return null
        val title = m.title.ifBlank { b.prompt.take(40) }
        return AssetRequester.request(appContext, token, com.example.ui.components.TelegramAssetManager.DEFAULT_CHANNEL_ID, title, needs).fold(
            { n -> if (n > 0) "Asked you on Telegram for $n missing asset(s). Upload them, then tap “Check Telegram again”." else "Missing assets were already requested on Telegram recently." },
            { e -> "Could not ask for missing assets on Telegram: ${e.message}" })
    }

    /** Re-reads the Telegram channel and retries every asset request of the current plan; rebuilds the timeline if anything new was found. */
    fun refreshAssets() {
        val m = _ui.value.manifest ?: run { say("There is no plan to refresh yet.", error = true); return }
        if (_ui.value.working) return
        job = scope.launch { guarded {
            val b = _ui.value.brief
            status(DirectorStage.FINDING_ASSETS, "Hunting the missing assets on Telegram, one at a time")
            // Only the places that are still empty are hunted again, so assets already placed are never re-downloaded.
            val empty = HuntSlots.unfilled(HuntSlots.build(m, b.aspect), assets).map { it.sceneId }.toSet()
            val alreadyVisual = assets.filter { it.kind != "audio" }.map { it.sceneId }.toSet()
            val ao = services.huntAssets(b, m, "default", onStep = { step ->
                status(DirectorStage.CHECKING_ASSETS, "Hunting assets from Telegram", step.fraction, step.text)
            }, only = empty.ifEmpty { null }, skipVisualFor = alreadyVisual)
            val before = assets.size
            assets = ao.assets
            val reqNote = requestNeeds(b, m, ao)
            sfxLib = cancellableResult { services.resolveSfxLibrary(m, "default") }.getOrDefault(emptyMap())
            store.saveArtifacts(projectId, workflow, narrationClips, assets)
            val (assembled, probs) = TimelineAssembler.assemble(getProject(), m, narrationClips, assets, captions = b.subtitles?.startsWith("No", true) != true, aspect = b.aspect,
                sfx = SfxFiles.render(appContext, m, library = { kind -> sfxLibrary(kind) }), captionStyle = BrandKit.captionStyle(appContext))
            val (p, musicNotes) = if (workflow == DirectorWorkflow.ZERO_TO_VIDEO) attachCheckedMusic(assembled, m, b) else assembled to emptyList()
            gate()
            pendingProject = p
            val all = ao.notes + ao.unresolved + listOfNotNull(reqNote) + probs + musicNotes
            update { copy(problems = all, working = false, readyToApply = true, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Assets refreshed")) }
            say(if (assets.size > before) "Found ${assets.size - before} new asset(s) on Telegram. Tap “Add to timeline” to apply." else "No new matching assets on Telegram yet (${assets.size} in use).")
        } }
    }

    /**
     * Round 12 — Flow autopilot placement: Flow se generate ki gayi images ko current plan ke scenes pe
     * IN ORDER fix karta hai (scene 1 → pehli image, …) as full-frame backdrops; code animations unke upar
     * play hongi. Agar plan abhi nahi bana to images library mein rehte hain aur agle run mein hunter unhe
     * keyword match karke khud place kar dega.
     */
    /**
     * Round 14 — "Flow images (auto)": builds ONE prompt per scene from the approved script (plus key visuals),
     * saves the plan, and opens the full-screen Flow browser which generates them one by one with an 8 s gap,
     * downloads + verifies every picture, and hands the plan back to [onFlowPlanReady].
     */
    fun prepareFlowPlan(autoStart: Boolean = true) {
        val m = _ui.value.manifest ?: run {
            say("Pehle scene plan banao (🤖 SAIF Agent ya Confirm & start), phir Flow prompts usi script se banenge.", error = true)
            return
        }
        if (_ui.value.working) return
        update { copy(working = true, problems = emptyList()) }
        job = scope.launch { guarded {
            status(DirectorStage.FINDING_ASSETS, "Script ke scenes se image prompts ban rahe hain", detail = "scene by scene")
            // FLOW agent, supervised: one prompt per scene from the narration; the local builder is the declared fallback.
            val r = AgentOrchestrator(activityBus).supervised(
                AgentRole.FLOW, "scene-by-scene image prompts",
                detail = "${m.scenes.size} scenes · narration se, topic se nahi",
                fallbackNote = "local prompt builder (scene ki narration) use hoga"
            ) { services.buildFlowPlan(projectId, _ui.value.brief, m, _ui.value.script) }
            val plan = r.getOrElse { fail("Flow prompts nahi bane: ${it.message}"); return@guarded }
            gate()
            com.example.flow.FlowPlanStore.save(appContext, plan)
            update { copy(working = false, flowPlan = plan,
                status = JobStatus(DirectorStage.FINDING_ASSETS, plan.slots.size.toString() + " Flow prompts ready")) }
            say("🖼 ${plan.slots.size} image prompts tayyar — har scene ke narration se, topic se nahi.\n" +
                plan.notes.joinToString("\n") + "\n\nFlow browser khul raha hai. Login karo (jab chaho — koi one-time lock nahi), " +
                "phir Start dabao: SAIF khud ek-ek prompt generate karega (8 s gap), download karega aur verify karega.")
            com.example.flow.web.FlowBrowserActivity.start(appContext, projectId, autoStart, _ui.value.brief.prompt)
        } }
    }

    /**
     * Flow browser se aaya hua plan: transitions + placement ko manifest mein daalta hai, assets ko scene-wise
     * resolve karta hai aur (agar bole) seedha timeline pe apply kar deta hai.
     */
    fun onFlowPlanReady(plan: com.example.flow.FlowPlan, applyNow: Boolean = false) {
        if (_ui.value.working) { say("Abhi ek kaam chal raha hai — Flow plan save ho gaya, thodi der baad lag jayega."); return }
        if (_ui.value.manifest == null) {
            update { copy(flowPlan = plan) }
            say("Flow plan save ho gaya (${plan.slots.size} prompts) — scene plan banne ke baad ye pictures khud lag jayengi.", error = true)
            return
        }
        job = scope.launch { guarded { applyFlowPlanInternal(plan, applyNow) } }
    }

    /** Shared by the browser hand-off and the SAIF Agent crew (so the crew waits for it). */
    suspend fun applyFlowPlanInternal(plan: com.example.flow.FlowPlan, applyNow: Boolean = false) {
        val m = _ui.value.manifest ?: run { update { copy(flowPlan = plan) }; return }
        run {
            update { copy(working = true, flowPlan = plan) }
            status(DirectorStage.CHECKING_ASSETS, "Flow pictures ko scene-wise lagaya ja raha hai")
            val usable = plan.slots.filter { it.imagePath.isNotBlank() && File(it.imagePath).exists() }
            if (usable.isEmpty()) {
                update { copy(working = false) }
                say("Flow plan mein abhi koi downloaded picture nahi hai — browser mein Start dabao. Kuch bhi delete nahi hua.", error = true)
                return@run
            }
            // Transition plan (measured from the real pixels) → manifest scenes.
            val orderedSceneIds = plan.slots.filter { it.review != null }.sortedBy { it.index }.map { it.sceneId }.distinct()
            val transitionsByScene = HashMap<String, com.example.flow.FlowTransition>()
            plan.transitions.forEach { t ->
                val sceneId = orderedSceneIds.getOrNull(t.toIndex) ?: return@forEach
                transitionsByScene[sceneId] = t
            }
            val newScenes = m.scenes.map { sc ->
                val t = transitionsByScene[sc.id] ?: return@map sc
                val type = if (t.type in SUPPORTED_TRANSITIONS) t.type else "fade"
                sc.copy(transitionIn = type, transitionMs = t.ms.coerceIn(200L, 900L))
            }
            val newManifest = m.copy(scenes = newScenes)
            // Resolve every picture with its measured placement.
            val add = usable.map { slot ->
                val transition = transitionsByScene[slot.sceneId]?.type ?: "fade"
                val placement = com.example.flow.FlowPlacement.forSlot(slot, slot.sceneMs, transition)
                ResolvedAsset(
                    sceneId = slot.sceneId, requestId = "flow-" + slot.index, kind = "image",
                    localPath = slot.imagePath, source = "flow-ai:" + (slot.review?.paletteHex ?: ""),
                    verified = true,
                    note = slot.role + " · " + (slot.review?.verdict ?: "verified") + (slot.review?.aiVerdict?.takeIf { it.isNotBlank() }?.let { " · AI: " + it } ?: ""),
                    ext = if (slot.imagePath.endsWith(".jpg")) "jpg" else "png",
                    placement = placement
                )
            }
            // KEYFRAME/STICKER slots become floating cut-outs on the same scene; backgrounds replace old Flow assets.
            assets = assets.filterNot { it.requestId.startsWith("flow-") } + add
            store.saveArtifacts(projectId, workflow, narrationClips, assets)
            val b = _ui.value.brief
            val (assembled, probs) = TimelineAssembler.assemble(getProject(), newManifest, narrationClips, assets,
                captions = b.subtitles?.startsWith("No", true) != true, aspect = b.aspect,
                sfx = SfxFiles.render(appContext, newManifest, library = { kind -> sfxLibrary(kind) }),
                captionStyle = BrandKit.captionStyle(appContext))
            val (p, musicNotes) = if (workflow == DirectorWorkflow.ZERO_TO_VIDEO) attachCheckedMusic(assembled, newManifest, b) else assembled to emptyList()
            gate()
            pendingProject = p
            update { copy(manifest = newManifest, problems = probs + musicNotes, working = false, readyToApply = true,
                status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "${usable.size} Flow pictures ready")) }
            say(flowReport(plan, usable.size, newManifest.scenes.size))
            if (applyNow) applyToTimeline()
        }
    }

    /** Honest report of what was really measured, not a generic success line. */
    private fun flowReport(plan: com.example.flow.FlowPlan, used: Int, sceneCount: Int): String {
        val reviews = plan.slots.mapNotNull { it.review }
        val lowCrop = reviews.count { it.cropLoss <= 0.15f }
        val glow = reviews.count { it.needsGlow }
        val bg = reviews.count { it.needsBgRemove }
        val soft = reviews.count { it.sharpness < 0.12f }
        val ai = plan.slots.mapNotNull { it.review?.aiVerdict?.takeIf { v -> v.isNotBlank() } }
        val trans = plan.transitions.joinToString(" · ") { it.type }
        return buildString {
            append("✅ Flow pipeline: ").append(used).append("/").append(plan.slots.size).append(" pictures scenes pe lagi (")
            append(sceneCount).append(" scenes).\n")
            append("• Crop: ").append(lowCrop).append("/").append(reviews.size).append(" pictures 9:16 mein safe (baaki Ken-Burns ke saath adjust).\n")
            if (glow > 0) append("• Glow: ").append(glow).append(" dark/flat picture pe neon glow laga.\n")
            if (bg > 0) append("• Background remove-able: ").append(bg).append(" cut-out key visual chroma-key ke saath lagi.\n")
            if (soft > 0) append("• ").append(soft).append(" picture(s) soft hain — 1080p pe thodi blur dikh sakti hain.\n")
            if (ai.isNotEmpty()) append("• AI vision check: ").append(ai.first().take(100)).append("\n")
            if (trans.isNotEmpty()) append("• Transitions (pixels se): ").append(trans.take(180)).append("\n")
            append("Sab kuch timeline pe editable hai — 'Add to timeline' dabao (ya already apply ho gaya).")
        }
    }

    fun attachFlowImages() {
        val stored = com.example.flow.FlowPlanStore.load(appContext, projectId) ?: _ui.value.flowPlan
        if (stored != null && stored.slots.any { it.imagePath.isNotBlank() }) {
            onFlowPlanReady(stored, applyNow = false)
            return
        }
        val m = _ui.value.manifest ?: run {
            say("Pehle ek plan banao (🤖 SAIF Agent ya Confirm & start). Flow images abhi sirf library mein add hui hain — agle run mein khud aa jayengi.", error = true)
            return
        }
        if (_ui.value.working) return
        job = scope.launch { guarded { placeFlowImages(m) } }
    }

    /** Round 13 — shared by the UI button and the PLACER agent inside the agent crew. */
    private suspend fun placeFlowImages(m: SceneManifest) {
        run {
            status(DirectorStage.CHECKING_ASSETS, "Flow images ko sahi scene pe fix kar raha hoon")
            val imgs = com.example.ui.components.FlowAssetStore.items(appContext).filter { it.type == "image" }.sortedBy { it.messageId }
            if (imgs.isEmpty()) { say("Flow library khali hai — pehle 🌐 browser se images generate karo.", error = true); return@run }
            val entrances = listOf("fadeIn", "slideUp", "zoom", "popIn", "wipe")
            val add = m.scenes.mapIndexedNotNull { i, sc ->
                val a = imgs.getOrNull(i) ?: return@mapIndexedNotNull null
                ResolvedAsset(sceneId = sc.id, requestId = "flow-" + a.assetId, kind = "image",
                    localPath = a.fileId.removePrefix("local:"),
                    source = "flow-ai:" + a.title,
                    verified = true, note = "Flow AI image: " + a.title.take(90),
                    ext = a.extension.ifBlank { "png" },
                    placement = AssetPlacements.clamp(AssetPlacement(role = "backdrop", width = 1f, height = 1f, fit = "cover",
                        drift = if (i % 2 == 0) 0.25f else -0.25f, animIn = entrances[i % entrances.size], animInMs = 350L,
                        glow = 0.08f, glowColor = "#8B5CF6")))
            }
            val replaced = add.map { it.sceneId }.toSet()
            assets = assets.filterNot { it.sceneId in replaced && it.kind != "audio" } + add
            val b = _ui.value.brief
            store.saveArtifacts(projectId, workflow, narrationClips, assets)
            val (assembled, probs) = TimelineAssembler.assemble(getProject(), m, narrationClips, assets, captions = b.subtitles?.startsWith("No", true) != true, aspect = b.aspect,
                sfx = SfxFiles.render(appContext, m, library = { kind -> sfxLibrary(kind) }), captionStyle = BrandKit.captionStyle(appContext))
            val (p, musicNotes) = if (workflow == DirectorWorkflow.ZERO_TO_VIDEO) attachCheckedMusic(assembled, m, b) else assembled to emptyList()
            gate()
            pendingProject = p
            update { copy(problems = probs + musicNotes, working = false, readyToApply = true,
                status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Flow images lag gaye — " + add.size + "/" + m.scenes.size + " scenes")) }
            // Round 13 — ASSEMBLER agent analyses each placed image with the vision model (FIT / NOT FIT).
            if (services.can(Capability.IMAGE_UNDERSTANDING)) {
                val verdicts = add.take(5).map { a ->
                    val sc = m.scenes.firstOrNull { it.id == a.sceneId }
                    val v = if (sc != null) services.visionVerdict(a.localPath, sc.visualObjective, sc.narration).getOrNull() else null
                    "• scene ${m.scenes.indexOf(sc) + 1}: " + (v ?: "unverified (no vision)")
                }
                if (verdicts.isNotEmpty()) say("🔍 ASSEMBLER agent ne Flow images analyse ki (vision):\n" + verdicts.joinToString("\n").take(700))
            }
            say("✅ " + add.size + " Flow image(s) ko scenes pe fix kar diya (order mein, scene 1 se). Code animations inke upar play hongi. “Add to timeline” dabao.")
        }
    }

    // ---------------- apply / revise ----------------

    private var pendingProject: StudioProject? = null

    fun applyToTimeline() {
        val p = pendingProject ?: run { say("Nothing to apply yet.", error = true); return }
        val event = activityBus.begin(ActivityKind.TIMELINE, "Adding to timeline")
        try {
            applyProject(p); pendingProject = null
            activityBus.finish(event)
        } catch (e: Exception) {
            activityBus.finish(event, ActivityState.FAILED, "Could not apply the project")
            throw e
        }
        update { copy(applied = true, readyToApply = false) }
        runCatching { com.example.power.SaifToolkit.Counters.bump(appContext, "videos_applied") }
        say("Added to your timeline. Press Play in the editor to preview. You can reopen this chat any time to revise scenes.")
    }

    private val variantOffsets = HashMap<String, Int>()

    private fun aspectOf(): String = _ui.value.brief.aspect ?: "9:16"

    /** True when this scene is one beat of a longer spoken sentence (its voice clip spans several scenes). */
    private fun inVoiceGroup(sceneId: String): Boolean = narrationClips.any { it.groupIds.size > 1 && sceneId in it.groupIds }

    /** Locked scenes are skipped by every regenerate/revise action. */
    fun toggleLock(index: Int) {
        val m = _ui.value.manifest ?: return
        if (m.scenes.getOrNull(index) == null) return
        val nm = m.copy(scenes = m.scenes.mapIndexed { i, x -> if (i == index) x.copy(locked = !x.locked) else x })
        update { copy(manifest = nm) }
    }

    /** Replan an edited scene against every current scene, including the old version being replaced. */
    private fun uniqueRedesign(beat: Beat, scenes: List<Scene>, variant: Int, accent: String?): SceneGraph? {
        val base = StyleDNA.fromBrief(_ui.value.brief)
        val dna = base.copy(variantShift = (base.variantShift + variant).mod(3))
        return SceneVarietyComposer.unique(beat, aspectOf(), dna, accent, scenes.mapNotNull { it.graph })
    }

    /** Replaces one scene's layout with the next local design variant. Narration, timing and all other scenes stay untouched. */
    fun regenerateVariant(index: Int) {
        val m = _ui.value.manifest ?: return
        val target = m.scenes.getOrNull(index) ?: return
        if (_ui.value.working) return
        if (target.locked) { say("Scene ${index + 1} is locked. Unlock it first.", error = true); return }
        job = scope.launch { guarded {
            status(DirectorStage.GENERATING_ANIMATIONS, "Designing another layout for scene ${index + 1}")
            val v = (variantOffsets[target.id] ?: 0) + 1; variantOffsets[target.id] = v
            val base = LocalBeatAnalyzer.analyze(target.narration, index, m.scenes.size, target.durationMs)
            val beat = com.example.studio.composer.Intent.parse(target.visualObjective.substringBefore(':'))?.let { base.copy(intent = it) } ?: base
            val graph = uniqueRedesign(beat, m.scenes, v, BrandKit.accent(appContext))
                ?: run { fail("No unique layout remains for scene ${index + 1}; nothing was changed."); return@guarded }
            val ns = target.copy(graph = graph, cues = SfxPlanner.cuesFor(beat, target.transitionIn))
            reassembleOne(m, ns, "Scene ${index + 1} now uses a different layout.")
        } }
    }

    private fun reassembleOne(m: SceneManifest, ns: Scene, message: String) {
        val nm = m.copy(scenes = m.scenes.map { if (it.id == ns.id) ns else it })
        val replaced = TimelineAssembler.clearAi(pendingProject ?: getProject(), setOf(ns.id))
        val one = nm.copy(scenes = listOf(ns))
        val (p, probs) = TimelineAssembler.assemble(replaced, one, narrationClips.filter { it.sceneId == ns.id }, assets.filter { it.sceneId == ns.id }, replaceExisting = false,
            sfx = SfxFiles.render(appContext, one, library = { kind -> sfxLibrary(kind) }), captionStyle = BrandKit.captionStyle(appContext))
        pendingProject = p
        update { copy(manifest = nm, working = false, readyToApply = true, problems = probs, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Scene ${ns.id} updated")) }
        say("$message Tap “Add to timeline” to apply.")
    }

    /** One tap: only the first beat is rewritten, re-voiced and redesigned; later scenes are shifted, not redone. */
    fun strengthenHook() {
        val m = _ui.value.manifest ?: return
        val first = m.scenes.firstOrNull() ?: return
        if (_ui.value.working) return
        if (first.locked) { say("Scene 1 is locked. Unlock it first.", error = true); return }
        if (inVoiceGroup(first.id)) { say("The hook sentence is spoken over more than one scene, so it can't be swapped alone without cutting the voice. Edit the script and tap Approve again.", error = true); return }
        job = scope.launch { guarded {
            val b = _ui.value.brief
            status(DirectorStage.WRITING_SCRIPT, "Writing stronger hook options")
            val currentHook = com.example.studio.composer.BeatPlanner.sentences(first.narration).firstOrNull() ?: first.narration
            val hook = services.betterHook(b, currentHook).getOrElse { update { copy(working = false, status = null) }; say(it.message ?: "No stronger hook found."); return@guarded }
            val sents = com.example.studio.composer.BeatPlanner.sentences(first.narration)
            val newNarration = (listOf(hook) + sents.drop(1)).joinToString(" ")
            status(DirectorStage.GENERATING_NARRATION, "Voicing the new hook")
            val voiced = services.synthNarration(b, m.copy(scenes = listOf(first.copy(narration = newNarration))))
            val clip = voiced.clips.firstOrNull() ?: run { fail("The new hook could not be voiced. ${voiced.failures.firstOrNull().orEmpty()}"); return@guarded }
            val clips = narrationClips.filter { it.sceneId != first.id } + clip
            val renamed = m.copy(scenes = m.scenes.map { if (it.id == first.id) it.copy(narration = newNarration, caption = newNarration) else it })
            val retimed = SceneTiming.retimeGrouped(renamed, clips, padMs = 170L)
            val beat = LocalBeatAnalyzer.analyze(hook, 0, m.scenes.size, retimed.scenes.first().durationMs).copy(intent = com.example.studio.composer.Intent.HOOK)
            val v = (variantOffsets[first.id] ?: 0) + 1; variantOffsets[first.id] = v
            val graph = uniqueRedesign(beat, m.scenes, v, BrandKit.accent(appContext))
                ?: run { fail("No unique layout remains for the hook; nothing was changed."); return@guarded }
            val ns = retimed.scenes.first().copy(graph = graph, cues = SfxPlanner.cuesFor(beat, "none"))
            val nm = retimed.copy(scenes = listOf(ns) + retimed.scenes.drop(1))
            narrationClips = clips
            val replaced = TimelineAssembler.clearAi(pendingProject ?: getProject(), setOf(first.id))
            val one = nm.copy(scenes = listOf(ns))
            val (p, probs) = TimelineAssembler.assemble(replaced, one, listOf(clip), assets.filter { it.sceneId == first.id }, replaceExisting = false,
                sfx = SfxFiles.render(appContext, one, library = { kind -> sfxLibrary(kind) }), captionStyle = BrandKit.captionStyle(appContext))
            val retimedProject = TimelineAssembler.retimeAfterChange(p, nm)
            val (withMusic, musicNotes) = if (workflow == DirectorWorkflow.ZERO_TO_VIDEO)
                TimelineAssembler.withBackgroundMusic(retimedProject, nm.totalMs, GuidedBriefOptions.music(_ui.value.brief))
            else retimedProject to emptyList()
            pendingProject = withMusic
            val newScript = _ui.value.script.let { sc -> if (sc.contains(currentHook)) sc.replaceFirst(currentHook, hook) else sc }
            store.saveArtifacts(projectId, workflow, narrationClips, assets)
            update { copy(script = newScript, manifest = nm, working = false, readyToApply = true, problems = probs + musicNotes, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Hook updated")) }
            say("New hook: “$hook”. Only scene 1 was redone. Tap “Add to timeline” to apply.")
        } }
    }

    /** Route chat revisions through the same validated uniqueness gate as scene cards. */
    private fun reviseScene(instruction: String) {
        val m = _ui.value.manifest ?: return
        val num = Regex("(?i)scene\\s*(\\d+)").find(instruction)?.groupValues?.get(1)?.toIntOrNull()
        if (num == null || m.scenes.getOrNull(num - 1) == null) {
            say("Tell me which scene to change, for example “Scene 2: make it a bar chart”. The plan has ${m.scenes.size} scenes.")
            return
        }
        reviseScenes(listOf(num - 1), instruction)
    }

    // ---------------- multi-scene actions used by the scene cards ----------------

    /** Re-assembles several changed scenes into the pending project, one at a time, without touching any other scene. */
    private fun reassembleMany(m: SceneManifest, changed: List<Scene>): Pair<SceneManifest, List<String>> {
        var cur = m
        var base = pendingProject ?: getProject()
        val probs = ArrayList<String>()
        for (ns in changed) {
            cur = cur.copy(scenes = cur.scenes.map { if (it.id == ns.id) ns else it })
            val replaced = TimelineAssembler.clearAi(base, setOf(ns.id))
            val one = cur.copy(scenes = listOf(ns))
            val (p, pr) = TimelineAssembler.assemble(replaced, one, narrationClips.filter { it.sceneId == ns.id }, assets.filter { it.sceneId == ns.id }, replaceExisting = false,
                sfx = SfxFiles.render(appContext, one, library = { kind -> sfxLibrary(kind) }), captionStyle = BrandKit.captionStyle(appContext))
            base = p; probs += pr
        }
        pendingProject = base
        return cur to probs
    }

    /** Applies one written instruction to every selected scene (locked scenes are skipped). Other scenes are never touched. */
    fun reviseScenes(indices: List<Int>, instruction: String) {
        val m0 = _ui.value.manifest ?: return
        if (_ui.value.working) return
        val targets = indices.distinct().sorted().mapNotNull { m0.scenes.getOrNull(it) }.filter { !it.locked }
        if (targets.isEmpty()) { say("Select at least one unlocked scene first.", error = true); return }
        if (instruction.isBlank()) { say("Describe the change you want for the selected scenes.", error = true); return }
        update { copy(working = true) }
        job = scope.launch { guarded {
            val (aw, ah) = com.example.studio.composer.LayoutEngine.parseAspect(aspectOf())
            var m = m0
            val done = ArrayList<Scene>(); val failed = ArrayList<String>()
            targets.forEachIndexed { k, target ->
                val num = m0.scenes.indexOfFirst { it.id == target.id } + 1
                status(DirectorStage.GENERATING_ANIMATIONS, "Revising scene $num", k.toFloat() / targets.size, "${k + 1}/${targets.size}")
                val prompt = "Rewrite ONLY the 'graph' of this scene per the instruction, keeping narration and timing. Reply with ONE JSON object {\"graph\":{...}} using the same primitives. Every rectangle must hold a short label inside it; no empty blocks.\nInstruction: $instruction\nScene narration: ${target.narration}\nCurrent graph: ${target.graph?.let { SceneJson.graphToJson(it) } ?: "none"}"
                var feedback = ""
                var chosen: SceneGraph? = null
                for (attempt in 1..3) {
                    val raw = services.revisionCall(prompt + feedback).getOrNull() ?: break
                    val g = runCatching { SceneJson.parseGraph(JSONObject(SceneJson.extractJson(raw)).getJSONObject("graph")) }.getOrNull()
                    val fixed = g?.let { com.example.studio.composer.LayoutEngine.repair(com.example.studio.composer.SceneLint.adoptCards(it, aw, ah), aw, ah, target.durationMs).graph }
                    val fatal = fixed?.let { SceneValidator.fatal(SceneValidator.validate(m.copy(scenes = listOf(target.copy(graph = it))))) }
                    val lint = fixed?.let { com.example.studio.composer.SceneLint.problems(it, aw, ah) }
                    val layout = fixed?.let { com.example.studio.composer.LayoutEngine.issues(it, aw, ah, target.durationMs) }
                    val others = m.scenes.filter { it.id != target.id }.mapNotNull { it.graph }
                    val collision = fixed?.let { SceneUniqueness.reason(it, others, num - 1) }
                    if (fixed != null && fatal.isNullOrEmpty() && lint.isNullOrEmpty() && layout.isNullOrEmpty() && collision == null) {
                        chosen = fixed
                        activityBus.instant(ActivityKind.SCENE_JSON, "Scene $num revision accepted", "Passed validation and uniqueness checks.")
                        break
                    }
                    val reason = collision ?: fatal?.firstOrNull()?.message ?: lint?.firstOrNull() ?: layout?.firstOrNull() ?: "invalid JSON"
                    activityBus.instant(ActivityKind.SCENE_JSON, "Scene $num revision rejected", reason)
                    feedback = "\nYour previous design was rejected: $reason. Replan a unique, valid layout."
                }
                if (chosen == null) { failed += "scene $num"; return@forEachIndexed }
                val revised = target.copy(graph = chosen)
                done += revised
                m = m.copy(scenes = m.scenes.map { if (it.id == target.id) revised else it })
            }
            if (done.isEmpty()) { fail("The model did not return a usable design for ${failed.joinToString()}. Nothing was changed."); return@guarded }
            val (nm, probs) = reassembleMany(m0, done)
            m = nm
            update { copy(manifest = m, working = false, readyToApply = true, problems = probs, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "${done.size} scene(s) revised")) }
            say("Changed ${done.size} scene(s)" + (if (failed.isNotEmpty()) "; ${failed.joinToString()} could not be redesigned and stayed as they were" else "") + ". Tap “Add to timeline” to apply.")
        } }
    }

    /** Gives each selected scene a completely new look (palette, backdrop, layout) while keeping its narration and timing. */
    fun changeStyle(indices: List<Int>) {
        val m0 = _ui.value.manifest ?: return
        if (_ui.value.working) return
        val targets = indices.distinct().sorted().mapNotNull { i -> m0.scenes.getOrNull(i)?.let { i to it } }.filter { !it.second.locked }
        if (targets.isEmpty()) { say("Select at least one unlocked scene first.", error = true); return }
        job = scope.launch { guarded {
            status(DirectorStage.GENERATING_ANIMATIONS, "Restyling ${targets.size} scene(s)")
            val current = m0.scenes.toMutableList()
            val done = ArrayList<Scene>()
            val failed = ArrayList<Int>()
            for ((i, target) in targets) {
                val v = (variantOffsets[target.id] ?: 0) + 1; variantOffsets[target.id] = v
                val seed = StyleDNA.fromBrief(_ui.value.brief).seed xor (v * 977 + i * 31)
                val dna = StyleDNA.from(seed)
                val base = LocalBeatAnalyzer.analyze(target.narration, i, m0.scenes.size, target.durationMs)
                val beat = com.example.studio.composer.Intent.parse(target.visualObjective.substringBefore(':'))?.let { base.copy(intent = it) } ?: base
                val graph = SceneVarietyComposer.unique(beat, aspectOf(), dna, null, current.mapNotNull { it.graph })
                if (graph == null) { failed += i + 1; continue }
                val revised = target.copy(graph = graph, cues = SfxPlanner.cuesFor(beat, target.transitionIn))
                current[i] = revised; done += revised
            }
            if (done.isEmpty()) { fail("No unique layouts remained for the selected scenes; nothing was changed."); return@guarded }
            val (nm, probs) = reassembleMany(m0, done)
            update { copy(manifest = nm, working = false, readyToApply = true, problems = probs, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "${done.size} scene(s) restyled")) }
            say("Restyled ${done.size} scene(s)." + (if (failed.isNotEmpty()) " Could not find a unique layout for ${failed.joinToString()}; those scenes are unchanged." else "") + " Tap “Add to timeline” to apply.")
        } }
    }

    /** Replaces the narration text of ONE scene: re-voices it, redesigns it and shifts later scenes, nothing else is redone. */
    fun editSceneText(index: Int, newText: String) {
        val m = _ui.value.manifest ?: return
        val sc = m.scenes.getOrNull(index) ?: return
        if (_ui.value.working) return
        if (sc.locked) { say("Scene ${index + 1} is locked. Unlock it first.", error = true); return }
        if (inVoiceGroup(sc.id)) { say("Scene ${index + 1} is one beat of a longer sentence that is voiced in one piece. Edit the script text instead and approve it again.", error = true); return }
        val text = newText.trim()
        if (text.length < 6) { say("Write at least a short sentence for scene ${index + 1}.", error = true); return }
        job = scope.launch { guarded {
            val b = _ui.value.brief
            status(DirectorStage.GENERATING_NARRATION, "Voicing the new text for scene ${index + 1}")
            val voiced = services.synthNarration(b, m.copy(scenes = listOf(sc.copy(narration = text))))
            val clip = voiced.clips.firstOrNull() ?: run { fail("The new text could not be voiced. ${voiced.failures.firstOrNull().orEmpty()}"); return@guarded }
            val clips = narrationClips.filter { it.sceneId != sc.id } + clip
            val renamed = m.copy(scenes = m.scenes.map { if (it.id == sc.id) it.copy(narration = text, caption = text) else it })
            val retimed = SceneTiming.retimeGrouped(renamed, clips, padMs = 170L)
            val cur = retimed.scenes[index]
            val base = LocalBeatAnalyzer.analyze(text, index, m.scenes.size, cur.durationMs)
            val beat = com.example.studio.composer.Intent.parse(sc.visualObjective.substringBefore(':'))?.let { base.copy(intent = it) } ?: base
            val v = (variantOffsets[sc.id] ?: 0) + 1; variantOffsets[sc.id] = v
            val graph = uniqueRedesign(beat, m.scenes, v, BrandKit.accent(appContext))
                ?: run { fail("No unique layout remains for scene ${index + 1}; nothing was changed."); return@guarded }
            val ns = cur.copy(graph = graph, cues = SfxPlanner.cuesFor(beat, cur.transitionIn))
            val nm = retimed.copy(scenes = retimed.scenes.map { if (it.id == ns.id) ns else it })
            narrationClips = clips
            val replaced = TimelineAssembler.clearAi(pendingProject ?: getProject(), setOf(sc.id))
            val one = nm.copy(scenes = listOf(ns))
            val (p, probs) = TimelineAssembler.assemble(replaced, one, listOf(clip), assets.filter { it.sceneId == sc.id }, replaceExisting = false,
                sfx = SfxFiles.render(appContext, one, library = { kind -> sfxLibrary(kind) }), captionStyle = BrandKit.captionStyle(appContext))
            val retimedProject = TimelineAssembler.retimeAfterChange(p, nm)
            val (withMusic, musicNotes) = if (workflow == DirectorWorkflow.ZERO_TO_VIDEO)
                TimelineAssembler.withBackgroundMusic(retimedProject, nm.totalMs, GuidedBriefOptions.music(_ui.value.brief))
            else retimedProject to emptyList()
            pendingProject = withMusic
            store.saveArtifacts(projectId, workflow, narrationClips, assets)
            update { copy(manifest = nm, working = false, readyToApply = true, problems = probs + musicNotes, status = JobStatus(DirectorStage.READY_FOR_PREVIEW, "Scene ${index + 1} text updated")) }
            say("Scene ${index + 1} was re-voiced and redesigned; later scenes moved to fit. Tap “Add to timeline” to apply.")
        } }
    }

    /** Looks for a DIFFERENT asset for one scene in the Telegram library; asks for one on Telegram when nothing else fits. */
    fun replaceAsset(index: Int) {
        val m = _ui.value.manifest ?: return
        val target = m.scenes.getOrNull(index) ?: return
        if (_ui.value.working) return
        if (target.locked) { say("Scene ${index + 1} is locked. Unlock it first.", error = true); return }
        job = scope.launch { guarded {
            val b = _ui.value.brief
            status(DirectorStage.FINDING_ASSETS, "Looking for a different asset for scene ${index + 1}")
            val exclude = assets.filter { it.sceneId == target.id }.map { it.source.removePrefix("telegram:").substringBefore(" (").trim() }.toSet()
            val withReq = if (target.assets.isNotEmpty()) target
                else target.copy(assets = listOf(AssetRequest("lib$index", AssetPlanner.kindFor(target.narration), AssetPlanner.describe(target.narration))))
            val one = m.copy(scenes = listOf(withReq))
            val ao = services.resolveAssets(b, one, "default", exclude = exclude)
            if (ao.assets.isEmpty()) {
                val note = requestMissingAssets(b, one, ao)
                update { copy(working = false, status = null) }
                say("No other matching asset in your Telegram library for scene ${index + 1}. ${note ?: ao.unresolved.firstOrNull().orEmpty()}".trim(), error = true)
                return@guarded
            }
            assets = assets.filter { it.sceneId != target.id } + ao.assets
            store.saveArtifacts(projectId, workflow, narrationClips, assets)
            val nm = m.copy(scenes = m.scenes.map { if (it.id == target.id) withReq else it })
            reassembleOne(nm, withReq, "Scene ${index + 1} now uses a different asset from Telegram.")
        } }
    }
}

object DirectorRegistry {
    private val map = HashMap<String, DirectorController>()
    @Synchronized fun get(ctx: Context, projectId: String, wf: DirectorWorkflow, getProject: () -> StudioProject, apply: (StudioProject) -> Unit): DirectorController =
        map.getOrPut("$projectId|${wf.name}") { DirectorController(ctx.applicationContext, projectId, wf, getProject, apply) }
            .also { it.getProject = getProject; it.applyProject = apply }
    @Synchronized fun drop(projectId: String, wf: DirectorWorkflow) { map.remove("$projectId|${wf.name}") }
}
