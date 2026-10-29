package com.example.agent.skills

import android.content.Context
import com.example.agent.AgentListener
import com.example.util.SaifAccessibilityService
import org.json.JSONObject

data class SkillResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

data class AgentEnv(
    val context: Context,
    val service: SaifAccessibilityService?,
    val listener: AgentListener?
)

interface Skill {
    val name: String
    suspend fun run(args: JSONObject, env: AgentEnv): SkillResult
}
