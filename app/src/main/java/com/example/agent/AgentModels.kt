package com.example.agent

import org.json.JSONObject

data class ActionResult(
    val ok: Boolean,
    val detail: String,
    val fingerprintChanged: Boolean = false,
    val data: Any? = null
)

data class AgentCall(
    val id: String,
    val name: String,
    val arguments: JSONObject,
    val intent: String = ""
)

data class StepRecord(
    val step: Int,
    val call: AgentCall,
    val result: ActionResult,
    val fingerprintBefore: String,
    val fingerprintAfter: String
)

data class AgentOutcome(
    val success: Boolean,
    val summary: String,
    val stepsTaken: Int,
    val durationMs: Long,
    val error: String? = null,
    val data: Any? = null
)

enum class AgentStatusState {
    IDLE,
    RUNNING,
    PAUSED,
    AWAITING_USER
}

data class AgentStatus(
    val state: AgentStatusState = AgentStatusState.IDLE,
    val currentGoal: String = "",
    val step: Int = 0,
    val maxSteps: Int = AgentConfig.MAX_STEPS,
    val lastMilestone: String = "",
    val queueSize: Int = 0
)

interface AgentListener {
    fun onMilestone(text: String)
    fun onAskUser(question: String, options: List<String>?)
    fun onFinished(outcome: AgentOutcome)
}
