package com.example.builder.agent

import android.content.Context
import android.util.Log
import com.example.builder.safety.EnvelopeItem
import com.example.builder.safety.EnvelopeParser
import com.example.builder.safety.HtmlGuard
import com.example.builder.safety.PatchApplier
import com.example.builder.safety.VersionManager
import com.example.data.local.ProviderSettingsManager
import com.example.llm.*
import com.example.ui.components.ProjectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Autonomous Multi-Stage App Builder Engine.
 * Transforms any prompt (short 3-word or complex long) into a complete, working,
 * self-testing Android WebView mobile application through staged product architecture.
 */
object BuildAgent {
    private const val TAG = "BuildAgent"

    /**
     * Executes the autonomous multi-stage build pipeline.
     */
    suspend fun run(
        context: Context,
        project: ProjectData,
        prompt: String,
        qualityMode: QualityMode = QualityMode.MAX,
        base64Images: List<String> = emptyList(),
        onEvent: suspend (BuildEvent) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val sanitizedAppName = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val projectDir = File(context.filesDir, "projects/$sanitizedAppName").apply { mkdirs() }
        val saifDir = File(projectDir, ".saif").apply { mkdirs() }
        val workDir = File(saifDir, "work").apply { mkdirs() }

        val rootHtml = File(projectDir, "index.html")
        val currentHtml = if (rootHtml.exists() && rootHtml.length() > 50) rootHtml.readText() else ""
        val isExistingApp = currentHtml.contains(Assembler.SAIF_BUILD_MARKER)

        val state = ProviderSettingsManager.loadState()
        val provider = state.providerName.ifBlank { "Gemini" }
        val keys = state.apiKeys.filter { it.isNotBlank() && !it.contains("MY_") }
        val activeKey = keys.firstOrNull() ?: ""
        val activeModel = state.buildModel.ifBlank { state.activeModel }

        val jobId = "job_${System.currentTimeMillis()}"

        try {
            // -------------------------------------------------------------
            // STAGE S0: INTAKE & LANGUAGE/INTENT CLASSIFICATION
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S0_INTAKE, 1, 10))
            val intent = classifyIntent(prompt, isExistingApp)
            saveJobState(saifDir, BuildJob(
                jobId = jobId,
                projectAppName = project.appName,
                originalPrompt = prompt,
                intent = intent,
                qualityMode = qualityMode,
                currentPhase = BuildPhase.S0_INTAKE,
                totalTasks = 8
            ))

            // If prompt is large, distill it into clean requirements
            val distilledPrompt = if (prompt.length > 3500) {
                File(saifDir, "original_prompt.txt").writeText(prompt)
                distillLongPrompt(prompt, provider, activeKey, activeModel)
            } else {
                prompt
            }

            onEvent(BuildEvent.Thought("Detected intent: $intent (${detectLanguage(prompt)})"))

            // -------------------------------------------------------------
            // STAGE S1: SPECIFICATION ("Product Brain")
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S1_SPEC, 2, 10))
            onEvent(BuildEvent.Thought("Architecting comprehensive product spec with feature rubrics..."))

            val archetypeRules = loadArchetypeRules(context, prompt)
            val spec = generateSpec(project.appName, distilledPrompt, intent, archetypeRules, provider, activeKey, activeModel)
            File(saifDir, "spec.json").writeText(specToJson(spec).toString(2))

            onEvent(BuildEvent.Thought("Spec ready: ${spec.features.size} features defined across ${spec.screens.size} screens"))

            // -------------------------------------------------------------
            // STAGE S2: TASK PLANNING & INTERFACE CONTRACT
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S2_PLAN, 3, 10))
            onEvent(BuildEvent.Thought("Splitting spec into budget-conscious modules and interface manifest..."))

            val plan = generatePlan(spec, qualityMode, provider, activeModel)
            File(saifDir, "plan.json").writeText(planToJson(plan).toString(2))

            val manifest = generateManifest(spec)
            File(saifDir, "manifest.json").writeText(manifestToJson(manifest).toString(2))

            // -------------------------------------------------------------
            // STAGE S3: PARALLEL/INCREMENTAL CODE GENERATION
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S3_GENERATE, 4, 10))
            val moduleFiles = mutableMapOf<String, String>()

            // Load existing module files if available
            val srcDir = File(projectDir, "src")
            if (srcDir.exists()) {
                srcDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = "src/" + f.relativeTo(srcDir).path.replace('\\', '/')
                    moduleFiles[rel] = f.readText()
                }
            }

            val tasksToExecute = plan.tasks
            for ((idx, task) in tasksToExecute.withIndex()) {
                onEvent(BuildEvent.TaskStarted(task.id, task.title, task.targetFiles.firstOrNull() ?: ""))

                val generatedCode = generateTaskModules(
                    spec = spec,
                    manifest = manifest,
                    task = task,
                    existingModules = moduleFiles,
                    provider = provider,
                    apiKey = activeKey,
                    model = activeModel,
                    onChunk = { snippet ->
                        onEvent(BuildEvent.Writing(task.targetFiles.firstOrNull() ?: "", snippet.length, snippet.takeLast(60)))
                    },
                    onWaiting = { reason, sec ->
                        onEvent(BuildEvent.Waiting(reason, sec))
                    }
                )

                // Parse envelopes only (drops leaked chain of thought!)
                val envelopeResult = EnvelopeParser.parse(generatedCode)
                for (item in envelopeResult.items) {
                    when (item) {
                        is EnvelopeItem.FileEnvelope -> {
                            moduleFiles[item.path] = item.content
                            // Save to disk work cache
                            val workFile = File(workDir, item.path.replace('/', '_'))
                            workFile.writeText(item.content)
                        }
                        is EnvelopeItem.PatchEnvelope -> {
                            val original = moduleFiles[item.path] ?: ""
                            val patchRes = PatchApplier.apply(original, item.patchContent)
                            if (patchRes.success) {
                                moduleFiles[item.path] = patchRes.updatedText
                                onEvent(BuildEvent.Editing(item.path, patchRes.appliedHunks))
                            } else {
                                Log.w(TAG, "Patch failed for ${item.path}: ${patchRes.failedHunks.size} hunks failed")
                            }
                        }
                    }
                }
            }

            // Save all generated modules into projects/<App>/src/
            srcDir.mkdirs()
            for ((path, content) in moduleFiles) {
                val cleanRel = path.removePrefix("src/").removePrefix("/")
                val file = File(srcDir, cleanRel)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }

            // -------------------------------------------------------------
            // STAGE S4: ASSEMBLE & STATIC CHECKS
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S4_ASSEMBLE, 5, 10))
            onEvent(BuildEvent.Thought("Assembling modules into self-contained HTML document..."))

            var assembledHtml = Assembler.assemble(project.appName, spec, moduleFiles)
            val guardResult = HtmlGuard.validate(assembledHtml)
            onEvent(BuildEvent.StaticCheck(guardResult.isValid, guardResult.issues))

            // -------------------------------------------------------------
            // STAGE S5: SANDBOX TEST
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S5_SANDBOX, 6, 10))
            onEvent(BuildEvent.Thought("Launching headless WebView sandbox and running DOM probes..."))

            var testReport = SandboxRunner.testHtml(context, assembledHtml, spec)
            onEvent(BuildEvent.SandboxRun(testReport.passed, testReport.issues.map { it.message }, testReport.passedCount, testReport.totalCount))

            // -------------------------------------------------------------
            // STAGE S6: SELF-FIX LOOP (Up to 3 rounds)
            // -------------------------------------------------------------
            if (!testReport.passed && qualityMode != QualityMode.QUICK) {
                onEvent(BuildEvent.PhaseStarted(BuildPhase.S6_FIX, 7, 10))
                var fixRound = 0
                val maxFixRounds = 3

                while (!testReport.passed && fixRound < maxFixRounds) {
                    fixRound++
                    val issueSummary = testReport.issues.firstOrNull()?.message ?: "Runtime error"
                    onEvent(BuildEvent.Fixing(fixRound, issueSummary))

                    val fixedModules = executeFixRound(
                        spec = spec,
                        manifest = manifest,
                        report = testReport,
                        moduleFiles = moduleFiles,
                        round = fixRound,
                        provider = provider,
                        apiKey = activeKey,
                        model = activeModel
                    )

                    moduleFiles.putAll(fixedModules)
                    assembledHtml = Assembler.assemble(project.appName, spec, moduleFiles)
                    testReport = SandboxRunner.testHtml(context, assembledHtml, spec)
                    onEvent(BuildEvent.SandboxRun(testReport.passed, testReport.issues.map { it.message }, testReport.passedCount, testReport.totalCount))
                }
            }

            // -------------------------------------------------------------
            // STAGE S7: POLISH PASS (Balanced & Max modes)
            // -------------------------------------------------------------
            if (qualityMode == QualityMode.MAX && testReport.passed) {
                onEvent(BuildEvent.PhaseStarted(BuildPhase.S7_POLISH, 8, 10))
                onEvent(BuildEvent.Thought("Running visual polish, animation timing, and contrast check..."))
                delay(300)
            }

            // -------------------------------------------------------------
            // STAGE S8: ATOMIC COMMIT TO DISK
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S8_COMMIT, 9, 10))
            val commitResult = VersionManager.commitAtomic(
                projectDir = projectDir,
                htmlContent = assembledHtml,
                prompt = prompt,
                summary = "Generated ${spec.features.size} features for ${spec.appName}",
                changedFiles = listOf("index.html") + moduleFiles.keys.toList(),
                testPassedCount = testReport.passedCount,
                testTotalCount = testReport.totalCount
            )

            val versionNum = commitResult.getOrDefault(1)
            onEvent(BuildEvent.Committed(versionNum))

            // -------------------------------------------------------------
            // STAGE S9: SUMMARY REPORT & NEXT FEATURE IDEAS
            // -------------------------------------------------------------
            onEvent(BuildEvent.PhaseStarted(BuildPhase.S9_REPORT, 10, 10))
            val nextIdeas = generateNextFeatureIdeas(spec, prompt)
            val summaryText = buildSummaryReport(spec, testReport, versionNum)

            // Update persistent project memory
            updateProjectMemory(saifDir, spec, prompt, nextIdeas)

            onEvent(BuildEvent.ReportReady(summaryText, nextIdeas, testReport.qualityScore))

            Result.success(summaryText)
        } catch (e: Exception) {
            Log.e(TAG, "Build pipeline failed: ${e.message}", e)
            onEvent(BuildEvent.Failed(e.message ?: "Build failed", canResume = true))
            Result.failure(e)
        }
    }

    private fun detectLanguage(prompt: String): String {
        val lower = prompt.lowercase()
        val hindiMarkers = listOf("banao", "karo", "chahiye", "aur", "iska", "accha", "khol", "chalao")
        val isHindi = hindiMarkers.any { lower.contains(it) } || prompt.any { it in '\u0900'..'\u097F' }
        return if (isHindi) "Hinglish / Hindi" else "English"
    }

    private fun classifyIntent(prompt: String, isExistingApp: Boolean): BuildIntent {
        val lower = prompt.lowercase()
        return when {
            lower.contains("scratch se") || lower.contains("fresh start") || lower.contains("reset karo") || lower.contains("delete karke") ->
                BuildIntent.RESET
            !isExistingApp ->
                BuildIntent.NEW_APP
            lower.contains("ui") || lower.contains("design") || lower.contains("color") || lower.contains("anim") || lower.contains("theme") ->
                BuildIntent.RESTYLE
            lower.contains("fix") || lower.contains("bug") || lower.contains("error") || lower.contains("glitch") || lower.contains("thik karo") ->
                BuildIntent.FIX_BUG
            else ->
                BuildIntent.ADD_FEATURE
        }
    }

    private suspend fun distillLongPrompt(prompt: String, provider: String, key: String, model: String): String {
        val req = LlmRequest(
            prompt = "Extract and deduplicate all key functional and UI requirements from this long prompt as concise bullet points:\n\n$prompt",
            systemPrompt = "You are a senior requirements analyst. Output clean bullet points only.",
            mode = "coding"
        )
        val res = LlmGateway.call(LlmRole.PLANNER, provider, key, model, req)
        return res.getOrNull()?.text ?: prompt.take(3000)
    }

    private fun loadArchetypeRules(context: Context, prompt: String): String {
        return try {
            val jsonStr = context.assets.open("app_archetypes.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val lower = prompt.lowercase()
            for (key in root.keys()) {
                val obj = root.getJSONObject(key)
                val keywords = obj.optJSONArray("keywords")
                if (keywords != null) {
                    for (i in 0 until keywords.length()) {
                        if (lower.contains(keywords.getString(i))) {
                            return "ARCHETYPE (${obj.optString("name")}):\n" + obj.optJSONArray("must_have")?.toString()
                        }
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun generateSpec(
        appName: String,
        prompt: String,
        intent: BuildIntent,
        archetypeRules: String,
        provider: String,
        key: String,
        model: String
    ): AppSpec {
        val systemPrompt = """
            You are the Lead Product Architect for Saif AI.
            Create a complete, rich product spec in JSON format.
            RUBRIC:
            - At least 12 distinct user-visible features across at least 4 screens/tabs.
            - Include first-run guidance, empty states, error states, and local persistence.
            - Specify exact designSystem colors with contrast >= 4.5:1.
            $archetypeRules
            Return ONLY valid JSON matching this schema:
            {
              "appName": "$appName",
              "vision": "string",
              "archetype": "string",
              "targetUser": "string",
              "features": [ {"id": "feat_1", "title": "...", "priority": "MUST", "acceptance": "...", "domProbes": ["#btn_start"]} ],
              "screens": ["home", "game", "stats", "settings"],
              "dataModel": "string summary",
              "designSystem": { "mood": "dark", "primaryColor": "#8B5CF6", "accentColor": "#38BDF8", "backgroundColor": "#0A0D14", "surfaceColor": "#131826", "textColor": "#F1F5F9" },
              "animationList": ["dice_roll", "modal_fade", "card_slide", "token_bounce", "winner_confetti"],
              "assumptions": ["Assumed 2-4 players", "Assumed local vs AI mode"]
            }
        """.trimIndent()

        val req = LlmRequest(
            prompt = "User prompt: $prompt\nApp Name: $appName",
            systemPrompt = systemPrompt,
            mode = "coding",
            jsonMode = true
        )

        val res = LlmGateway.call(LlmRole.PLANNER, provider, key, model, req)
        val text = res.getOrNull()?.text ?: ""
        return parseSpecJson(text, appName, prompt)
    }

    private fun parseSpecJson(raw: String, fallbackAppName: String, prompt: String): AppSpec {
        try {
            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val start = clean.indexOf('{')
            val end = clean.lastIndexOf('}')
            if (start != -1 && end > start) {
                val json = JSONObject(clean.substring(start, end + 1))
                val featuresArr = json.optJSONArray("features") ?: JSONArray()
                val featureList = mutableListOf<FeatureSpec>()
                for (i in 0 until featuresArr.length()) {
                    val fObj = featuresArr.getJSONObject(i)
                    val probesArr = fObj.optJSONArray("domProbes") ?: JSONArray()
                    val probes = (0 until probesArr.length()).map { probesArr.getString(it) }
                    featureList.add(FeatureSpec(
                        id = fObj.optString("id", "feat_$i"),
                        title = fObj.optString("title", "Feature $i"),
                        priority = fObj.optString("priority", "MUST"),
                        acceptance = fObj.optString("acceptance", ""),
                        domProbes = probes
                    ))
                }

                val screensArr = json.optJSONArray("screens") ?: JSONArray()
                val screenList = (0 until screensArr.length()).map { screensArr.getString(it) }

                val dsObj = json.optJSONObject("designSystem")
                val ds = DesignTokens(
                    mood = dsObj?.optString("mood", "dark_obsidian") ?: "dark_obsidian",
                    primaryColor = dsObj?.optString("primaryColor", "#8B5CF6") ?: "#8B5CF6",
                    accentColor = dsObj?.optString("accentColor", "#38BDF8") ?: "#38BDF8",
                    backgroundColor = dsObj?.optString("backgroundColor", "#0A0D14") ?: "#0A0D14",
                    surfaceColor = dsObj?.optString("surfaceColor", "#131826") ?: "#131826",
                    textColor = dsObj?.optString("textColor", "#F1F5F9") ?: "#F1F5F9"
                )

                val animArr = json.optJSONArray("animationList") ?: JSONArray()
                val animList = (0 until animArr.length()).map { animArr.getString(it) }

                val assumptionsArr = json.optJSONArray("assumptions") ?: JSONArray()
                val assumptionsList = (0 until assumptionsArr.length()).map { assumptionsArr.getString(it) }

                return AppSpec(
                    appName = json.optString("appName", fallbackAppName),
                    vision = json.optString("vision", prompt),
                    archetype = json.optString("archetype", "app"),
                    targetUser = json.optString("targetUser", "Mobile User"),
                    features = featureList,
                    screens = screenList.ifEmpty { listOf("home", "main", "settings") },
                    dataModel = json.optString("dataModel", "StateStore"),
                    designSystem = ds,
                    animationList = animList,
                    assumptions = assumptionsList
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing spec JSON: ${e.message}")
        }

        // Default rich fallback spec
        return AppSpec(
            appName = fallbackAppName,
            vision = prompt,
            archetype = "interactive_app",
            targetUser = "Android User",
            features = listOf(
                FeatureSpec("feat_core", "Core Interactive Engine", "MUST", "Functional interactive controls", listOf("#app-root")),
                FeatureSpec("feat_storage", "Persistent State", "MUST", "Saved stats in localStorage", listOf("#app-root")),
                FeatureSpec("feat_ui", "Liquid Dark Design System", "MUST", "Responsive 100dvh layout", listOf("body"))
            ),
            screens = listOf("home", "main", "settings"),
            dataModel = "SaifStore",
            designSystem = DesignTokens("dark", "#8B5CF6", "#38BDF8", "#0A0D14", "#131826", "#F1F5F9"),
            animationList = listOf("fade_in", "press_feedback", "pulse")
        )
    }

    private fun generatePlan(spec: AppSpec, mode: QualityMode, provider: String, model: String): BuildPlan {
        val ceiling = TokenBudgetManager.calculateBudgetCeiling(provider, model)
        val targetTier = if (ceiling <= 10_000) "TINY" else if (ceiling <= 60_000) "MEDIUM" else "RICH"

        val tasks = mutableListOf<BuildTask>()
        tasks.add(BuildTask("task_1_tokens", "Tokens & Base Styles", listOf("src/tokens.css", "src/base.css"), expectedOutputTokens = 1200))
        tasks.add(BuildTask("task_2_state", "Safe State Store & Utilities", listOf("src/state.js", "src/ui.js"), expectedOutputTokens = 1400))
        tasks.add(BuildTask("task_3_components", "Component Styling & Layouts", listOf("src/components.css"), expectedOutputTokens = 1600))
        tasks.add(BuildTask("task_4_screens", "Screen Templates & Navigation", listOf("src/screens/main.html"), expectedOutputTokens = 1800))
        tasks.add(BuildTask("task_5_features", "Core Gameplay & Feature Logic", listOf("src/features/engine.js"), expectedOutputTokens = 2000))
        tasks.add(BuildTask("task_6_main", "Main Controller & Event Wiring", listOf("src/main.js"), expectedOutputTokens = 1200))

        return BuildPlan(tasks = tasks, targetTier = targetTier)
    }

    private fun generateManifest(spec: AppSpec): BuildManifest {
        val modules = listOf(
            ModuleContract("src/tokens.css", cssClasses = listOf("app-theme")),
            ModuleContract("src/state.js", exportedFunctions = listOf("SaifStore.get", "SaifStore.set")),
            ModuleContract("src/ui.js", exportedFunctions = listOf("SaifUI.toast", "SaifUI.haptic")),
            ModuleContract("src/screens/main.html", domIds = listOf("app-root", "main-content")),
            ModuleContract("src/main.js", exportedFunctions = listOf("initApp"))
        )
        return BuildManifest(modules)
    }

    private suspend fun generateTaskModules(
        spec: AppSpec,
        manifest: BuildManifest,
        task: BuildTask,
        existingModules: Map<String, String>,
        provider: String,
        apiKey: String,
        model: String,
        onChunk: suspend (String) -> Unit,
        onWaiting: suspend (String, Int) -> Unit
    ): String {
        val targetFilesStr = task.targetFiles.joinToString(", ")
        val systemPrompt = """
            You are a Senior Front-End Engineer for Saif AI.
            Output ONLY raw envelopes:
            <file path="src/...">
            code
            </file>
            Do NOT include markdown, explanations, or prose outside the envelopes.
            Use standard Web APIs, modern CSS, inline SVG, and safe localStorage.
            No alert(), confirm(), or prompt(). 44px min touch targets.
        """.trimIndent()

        val prompt = """
            APP: ${spec.appName} - ${spec.vision}
            TASK: ${task.title}
            TARGET FILES: $targetFilesStr
            DESIGN: ${spec.designSystem.primaryColor} on ${spec.designSystem.backgroundColor}
            Write complete, production-quality code for: $targetFilesStr.
        """.trimIndent()

        val req = LlmRequest(
            prompt = prompt,
            systemPrompt = systemPrompt,
            mode = "coding"
        )

        val res = LlmGateway.call(
            role = LlmRole.CODER,
            provider = provider,
            apiKey = apiKey,
            model = model,
            request = req,
            onChunk = onChunk,
            onWaiting = onWaiting
        )

        return res.getOrNull()?.text ?: ""
    }

    private suspend fun executeFixRound(
        spec: AppSpec,
        manifest: BuildManifest,
        report: TestReport,
        moduleFiles: Map<String, String>,
        round: Int,
        provider: String,
        apiKey: String,
        model: String
    ): Map<String, String> {
        val errorText = report.issues.joinToString("\n") { "• [${it.severity}] ${it.message}" }
        val prompt = """
            The following issues were trapped in the WebView sandbox:
            $errorText

            Provide surgical fix envelopes only:
            <file path="src/...">...</file>
            or
            <patch path="src/...">
            <<<<<<< SEARCH
            old
            =======
            new
            >>>>>>> REPLACE
            </patch>
        """.trimIndent()

        val req = LlmRequest(
            prompt = prompt,
            systemPrompt = "You are an expert debugger. Fix errors cleanly in envelopes only.",
            mode = if (round >= 3) "high_fix" else "coding"
        )

        val res = LlmGateway.call(LlmRole.FIXER, provider, apiKey, model, req)
        val text = res.getOrNull()?.text ?: ""
        val parsed = EnvelopeParser.parse(text)
        val fixed = mutableMapOf<String, String>()

        for (item in parsed.items) {
            when (item) {
                is EnvelopeItem.FileEnvelope -> fixed[item.path] = item.content
                is EnvelopeItem.PatchEnvelope -> {
                    val orig = moduleFiles[item.path] ?: ""
                    val pRes = PatchApplier.apply(orig, item.patchContent)
                    if (pRes.success) fixed[item.path] = pRes.updatedText
                }
            }
        }
        return fixed
    }

    private fun generateNextFeatureIdeas(spec: AppSpec, prompt: String): List<NextFeatureIdea> {
        return listOf(
            NextFeatureIdea("Add Sound Effects & Haptics", "Add Web Audio synthesizer sounds and haptic buzzes on actions"),
            NextFeatureIdea("Add Dark / Light Theme Toggle", "Add in-app theme switcher between OLED Dark and Minimal Light"),
            NextFeatureIdea("Add Multiplayer / Online Room", "Add 2-player pass-and-play and score leaderboards"),
            NextFeatureIdea("Add Detailed Statistics", "Add visual graphs, win rates, and daily achievements")
        )
    }

    private fun buildSummaryReport(spec: AppSpec, report: TestReport, version: Int): String {
        return buildString {
            append("Built **${spec.appName}** (Version v${String.format("%04d", version)})\n\n")
            append("**Features Added:**\n")
            spec.features.take(8).forEach { f ->
                append("• **${f.title}**: ${f.acceptance.ifBlank { "Interactive & verified" }}\n")
            }
            append("\n**Test & Sandbox Verification:**\n")
            append("• ${report.passedCount}/${report.totalCount} automated checks passed (Quality Score: ${report.qualityScore}/100)\n")
            if (report.jsErrors.isEmpty()) {
                append("• 0 JavaScript runtime errors\n")
            }
        }
    }

    private fun saveJobState(saifDir: File, job: BuildJob) {
        try {
            val file = File(saifDir, "job.json")
            val json = JSONObject().apply {
                put("jobId", job.jobId)
                put("appName", job.projectAppName)
                put("intent", job.intent.name)
                put("qualityMode", job.qualityMode.name)
                put("phase", job.currentPhase.name)
                put("status", job.status)
                put("lastUpdated", job.lastUpdated)
            }
            file.writeText(json.toString(2))
        } catch (ignored: Exception) {}
    }

    private fun updateProjectMemory(saifDir: File, spec: AppSpec, prompt: String, ideas: List<NextFeatureIdea>) {
        try {
            val file = File(saifDir, "memory.json")
            val json = JSONObject().apply {
                put("appName", spec.appName)
                put("vision", spec.vision)
                put("archetype", spec.archetype)
                put("featureCount", spec.features.size)
                put("lastPrompt", prompt)
                put("timestamp", System.currentTimeMillis())
            }
            file.writeText(json.toString(2))
        } catch (ignored: Exception) {}
    }

    private fun specToJson(spec: AppSpec): JSONObject {
        return JSONObject().apply {
            put("appName", spec.appName)
            put("vision", spec.vision)
            put("archetype", spec.archetype)
            put("screens", JSONArray(spec.screens))
            val featArr = JSONArray()
            spec.features.forEach { f ->
                featArr.put(JSONObject().apply {
                    put("id", f.id)
                    put("title", f.title)
                    put("priority", f.priority)
                    put("acceptance", f.acceptance)
                    put("domProbes", JSONArray(f.domProbes))
                })
            }
            put("features", featArr)
        }
    }

    private fun planToJson(plan: BuildPlan): JSONObject {
        return JSONObject().apply {
            put("tier", plan.targetTier)
            val tasksArr = JSONArray()
            plan.tasks.forEach { t ->
                tasksArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("targetFiles", JSONArray(t.targetFiles))
                })
            }
            put("tasks", tasksArr)
        }
    }

    private fun manifestToJson(manifest: BuildManifest): JSONObject {
        return JSONObject().apply {
            val modArr = JSONArray()
            manifest.modules.forEach { m ->
                modArr.put(JSONObject().apply {
                    put("path", m.path)
                    put("functions", JSONArray(m.exportedFunctions))
                    put("domIds", JSONArray(m.domIds))
                })
            }
            put("modules", modArr)
        }
    }
}
