package com.example.agent.skills

import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject

object InstagramSkill : Skill {
    override val name: String = "instagram"
    private const val TAG = "SAIF_AGENT"
    const val IG_PACKAGE = "com.instagram.android"

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val action = args.optString("action", "").lowercase()
        val context = env.context
        val service = env.service

        return when (action) {
            "open_profile" -> {
                val username = args.optString("username", "")
                val uri = if (username.isNotBlank()) Uri.parse("instagram://user?username=$username") else Uri.parse("https://instagram.com")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(IG_PACKAGE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    SkillResult(true, "Opened Instagram profile: $username")
                } catch (e: Exception) {
                    SkillResult(false, "Instagram app not installed")
                }
            }
            "story_camera" -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://story-camera")).apply {
                    setPackage(IG_PACKAGE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    SkillResult(true, "Opened Instagram Story Camera")
                } catch (e: Exception) {
                    SkillResult(false, "Failed to open Instagram Story Camera")
                }
            }
            "story_viewers" -> {
                // Open Instagram, tap own story, swipe up to read viewer count
                val launchIntent = context.packageManager.getLaunchIntentForPackage(IG_PACKAGE)
                if (launchIntent == null) return SkillResult(false, "Instagram app is not installed")
                context.startActivity(launchIntent)

                if (service != null) {
                    service.waitForPackage(IG_PACKAGE, 3500)
                    service.waitForIdle(2000, 300)
                    delay(800)

                    // Tap "Your story" or story avatar at top left
                    service.clickElementByQuery("Your story") || service.clickElementByQuery("Story")
                    delay(1200)
                    service.waitForIdle(1500, 300)

                    // Swipe up to open seen-by sheet
                    service.scroll(forward = true)
                    delay(800)
                    service.waitForIdle(1500, 300)

                    // Read visible screen text
                    val summary = service.collectHierarchySummary()
                    return SkillResult(true, "Instagram story opened to read viewers", data = mapOf("screenText" to summary))
                }
                SkillResult(true, "Opened Instagram story")
            }
            "post_story" -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://story-camera")).apply {
                    setPackage(IG_PACKAGE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    SkillResult(true, "Instagram story camera opened for posting")
                } catch (e: Exception) {
                    SkillResult(false, "Instagram app not installed")
                }
            }
            else -> {
                // Launch Instagram default
                val launchIntent = context.packageManager.getLaunchIntentForPackage(IG_PACKAGE)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    SkillResult(true, "Instagram opened")
                } else {
                    SkillResult(false, "Instagram is not installed")
                }
            }
        }
    }
}
