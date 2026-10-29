package com.example.agent.skills

import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder

object YouTubeSkill : Skill {
    override val name: String = "youtube_search"
    private const val TAG = "SAIF_AGENT"
    const val YT_PACKAGE = "com.google.android.youtube"

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val query = args.optString("query", "").ifBlank { args.optString("search_query", "") }
        val playFirst = args.optBoolean("play_first", true)
        val like = args.optBoolean("like", false)
        val subscribe = args.optBoolean("subscribe", false)

        val encodedQuery = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = "https://www.youtube.com/results?search_query=$encodedQuery"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(YT_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            env.context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to web browser if app not installed
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            env.context.startActivity(browserIntent)
        }

        val service = env.service
        if (service != null && playFirst) {
            service.waitForPackage(YT_PACKAGE, 3500)
            service.waitForIdle(2000, 350)

            // Click the first video in search results
            delay(800)
            val clicked = service.clickFirstYouTubeVideo()

            if (clicked) {
                service.waitForIdle(2500, 350)
                delay(1200)

                // Optional like
                if (like) {
                    service.clickElementByQuery("like")
                }
                // Optional subscribe
                if (subscribe) {
                    service.clickYouTubeSubscribe()
                }

                return SkillResult(true, "Playing '$query' on YouTube", data = mapOf("query" to query, "playing" to true))
            }
        }

        return SkillResult(true, "Searched for '$query' on YouTube", data = mapOf("query" to query))
    }
}
