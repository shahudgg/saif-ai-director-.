package com.example.builder.agent

enum class BuildPhase(val displayName: String) {
    S0_INTAKE("Understanding Request"),
    S1_SPEC("Architecting Product Spec"),
    S2_PLAN("Splitting into Planned Tasks"),
    S3_GENERATE("Generating Modules"),
    S4_ASSEMBLE("Assembling & Static Checks"),
    S5_SANDBOX("Testing in Sandbox"),
    S6_FIX("Self-Fixing Glitches"),
    S7_POLISH("Review & Polish Pass"),
    S8_COMMIT("Atomic Commit to Disk"),
    S9_REPORT("Complete Report & Ideas")
}

enum class BuildIntent {
    NEW_APP,
    ADD_FEATURE,
    FIX_BUG,
    RESTYLE,
    DATA_CHANGE,
    RESET
}

enum class QualityMode(val title: String, val description: String, val estTime: String) {
    QUICK("Quick", "Fast prototype, minimal stages", "~1-2 min"),
    BALANCED("Balanced", "Default for edits, thorough testing", "~3-5 min"),
    MAX("Max", "Full multi-task architecture & polish", "~5-10 min")
}

data class NextFeatureIdea(
    val title: String,
    val prompt: String,
    val description: String = ""
)

sealed interface BuildEvent {
    data class PhaseStarted(val phase: BuildPhase, val index: Int, val total: Int) : BuildEvent
    data class Thought(val summary: String) : BuildEvent
    data class TaskStarted(val id: String, val title: String, val file: String) : BuildEvent
    data class Writing(val file: String, val approxBytes: Int, val snippet: String = "") : BuildEvent
    data class Editing(val file: String, val hunks: Int) : BuildEvent
    data class StaticCheck(val passed: Boolean, val issues: List<String>) : BuildEvent
    data class SandboxRun(val passed: Boolean, val issues: List<String>, val passedCount: Int, val totalCount: Int) : BuildEvent
    data class Fixing(val round: Int, val issueSummary: String) : BuildEvent
    data class Waiting(val reason: String, val seconds: Int) : BuildEvent
    data class ModelSwitch(val from: String, val to: String, val reason: String) : BuildEvent
    data class Retrying(val reason: String) : BuildEvent
    data class Committed(val version: Int) : BuildEvent
    data class ReportReady(val summary: String, val nextIdeas: List<NextFeatureIdea>, val qualityScore: Int) : BuildEvent
    data class Failed(val userMessage: String, val canResume: Boolean) : BuildEvent
}

// Product Brain Spec models
data class FeatureSpec(
    val id: String,
    val title: String,
    val priority: String, // "MUST", "SHOULD", "NICE"
    val acceptance: String,
    val domProbes: List<String> = emptyList() // CSS selectors / element IDs expected in DOM
)

data class DesignTokens(
    val mood: String, // e.g. "dark_obsidian", "vibrant_neon", "minimal_glass"
    val primaryColor: String,
    val accentColor: String,
    val backgroundColor: String,
    val surfaceColor: String,
    val textColor: String,
    val fontPair: String = "'Inter', system-ui, -apple-system, sans-serif"
)

data class AppSpec(
    val appName: String,
    val vision: String,
    val archetype: String,
    val targetUser: String,
    val features: List<FeatureSpec>,
    val screens: List<String>,
    val dataModel: String,
    val designSystem: DesignTokens,
    val animationList: List<String>,
    val assumptions: List<String> = emptyList(),
    val outOfScope: List<String> = emptyList()
)

data class BuildTask(
    val id: String,
    val title: String,
    val targetFiles: List<String>,
    val dependsOn: List<String> = emptyList(),
    val expectedOutputTokens: Int = 1500,
    val contextNeeded: String = ""
)

data class BuildPlan(
    val tasks: List<BuildTask>,
    val targetTier: String = "TINY" // TINY (<=10K TPM), MEDIUM (<=60K), RICH
)

data class ModuleContract(
    val path: String,
    val exportedFunctions: List<String> = emptyList(),
    val domIds: List<String> = emptyList(),
    val cssClasses: List<String> = emptyList(),
    val events: List<String> = emptyList()
)

data class BuildManifest(
    val modules: List<ModuleContract> = emptyList()
)

data class BuildJob(
    val jobId: String,
    val projectAppName: String,
    val originalPrompt: String,
    val intent: BuildIntent,
    val qualityMode: QualityMode,
    val currentPhase: BuildPhase,
    val currentTaskIndex: Int = 0,
    val totalTasks: Int = 0,
    val completedTaskIds: List<String> = emptyList(),
    val status: String = "RUNNING", // "RUNNING", "WAITING", "COMPLETED", "FAILED"
    val lastError: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class TestIssue(
    val severity: String, // "ERROR", "WARNING"
    val message: String,
    val source: String? = null,
    val lineNumber: Int? = null
)

data class TestReport(
    val passed: Boolean,
    val passedCount: Int,
    val totalCount: Int,
    val issues: List<TestIssue>,
    val jsErrors: List<String> = emptyList(),
    val failedDomProbes: List<String> = emptyList(),
    val hasHorizontalOverflow: Boolean = false,
    val smallTapTargetsCount: Int = 0,
    val isBlankScreen: Boolean = false,
    val qualityScore: Int = 85
)
