package com.example.agent.skills

import android.util.Log
import org.json.JSONObject

object SkillRegistry {
    private const val TAG = "SAIF_AGENT"

    private val skills = mutableMapOf<String, Skill>()

    init {
        register(AppSkill)
        register(CallSkill)
        register(WhatsAppSkill)
        register(YouTubeSkill)
        register(SystemSkill)
        register(InstagramSkill)
        register(PlayStoreSkill)
        register(NotificationsSkill)
        register(MediaCapcutSkill)
        register(DescribeScreenSkill)
    }

    fun register(skill: Skill) {
        skills[skill.name.lowercase()] = skill
    }

    fun get(name: String): Skill? {
        val clean = name.trim().lowercase()
        return skills[clean] ?: when {
            clean.contains("whatsapp") -> WhatsAppSkill
            clean.contains("call") || clean.contains("dial") -> CallSkill
            clean.contains("youtube") -> YouTubeSkill
            clean.contains("app") -> AppSkill
            clean.contains("torch") || clean.contains("volume") || clean.contains("alarm") || clean.contains("system") -> SystemSkill
            clean.contains("instagram") || clean.contains("insta") -> InstagramSkill
            clean.contains("play") || clean.contains("update") -> PlayStoreSkill
            clean.contains("notif") -> NotificationsSkill
            clean.contains("capcut") || clean.contains("gallery") -> MediaCapcutSkill
            clean.contains("describe") || clean.contains("screen") -> DescribeScreenSkill
            else -> null
        }
    }

    suspend fun execute(name: String, args: JSONObject, env: AgentEnv): SkillResult {
        val skill = get(name)
        if (skill == null) {
            Log.w(TAG, "Skill not found: $name")
            return SkillResult(false, "No registered skill matching '$name'")
        }
        return try {
            skill.run(args, env)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing skill $name: ${e.message}")
            SkillResult(false, "Skill '$name' execution failed: ${e.message}")
        }
    }
}
