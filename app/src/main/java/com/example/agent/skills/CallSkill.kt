package com.example.agent.skills

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.agent.UserDialogBridge
import org.json.JSONObject

object CallSkill : Skill {
    override val name: String = "call"
    private const val TAG = "SAIF_AGENT"

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val query = args.optString("contact", "").ifBlank { args.optString("name", "") }
        val speaker = args.optBoolean("speaker", false)

        if (query.isBlank()) {
            return SkillResult(false, "Contact name or number not specified")
        }

        // Direct number check
        val cleanNumber = query.replace(Regex("[^0-9+]"), "")
        val finalNumber: String
        val displayName: String

        if (cleanNumber.length >= 7) {
            finalNumber = cleanNumber
            displayName = query
        } else {
            // Resolve contact
            val matches = ContactAliasStore.resolveContact(env.context, query)
            if (matches.isEmpty()) {
                return SkillResult(false, "No contact found matching '$query'.")
            }

            val chosen = if (matches.size > 1) {
                // Ask user which contact
                val optionsText = matches.take(3).joinToString(", ") { it.name }
                val question = "Multiple contacts found: $optionsText. Which one would you like to call?"
                val answer = UserDialogBridge.askUser(question, matches.take(3).map { it.name }, env.listener)
                matches.firstOrNull { it.name.contains(answer, ignoreCase = true) || answer.contains(it.name, ignoreCase = true) }
                    ?: matches.first()
            } else {
                matches.first()
            }

            finalNumber = chosen.phoneNumber
            displayName = chosen.name
        }

        val hasCallPerm = ContextCompat.checkSelfPermission(
            env.context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPerm) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$finalNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$finalNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        try {
            env.context.startActivity(intent)

            if (speaker) {
                try {
                    val audioManager = env.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.isSpeakerphoneOn = true
                } catch (ignored: Exception) {}
            }

            return SkillResult(true, "Calling $displayName ($finalNumber)", data = mapOf("contact" to displayName, "number" to finalNumber))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call intent: ${e.message}")
            return SkillResult(false, "Failed to place call: ${e.message}")
        }
    }
}
