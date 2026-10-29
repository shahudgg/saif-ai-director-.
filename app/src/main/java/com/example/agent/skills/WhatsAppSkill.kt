package com.example.agent.skills

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.agent.AgentConfig
import com.example.agent.UserDialogBridge
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder

object WhatsAppSkill : Skill {
    override val name: String = "whatsapp_send"
    private const val TAG = "SAIF_AGENT"
    const val WA_PACKAGE = "com.whatsapp"

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val skillSubtype = args.optString("type", name)
        return when (skillSubtype) {
            "whatsapp_send_many" -> handleSendMany(args, env)
            "whatsapp_live_location" -> handleLiveLocation(args, env)
            else -> handleSingleSend(args, env)
        }
    }

    private suspend fun handleSingleSend(args: JSONObject, env: AgentEnv): SkillResult {
        val contactQuery = args.optString("contact", "").ifBlank { args.optString("recipient", "") }
        val message = args.optString("message", "").ifBlank { args.optString("text", "") }

        if (contactQuery.isBlank()) return SkillResult(false, "Recipient contact not specified")
        if (message.isBlank()) return SkillResult(false, "Message body not specified")

        // 1. Resolve phone number
        var targetDigits = ContactAliasStore.normalizePhoneNumber(contactQuery)
        var contactName = contactQuery

        if (!contactQuery.startsWith("+") && contactQuery.replace(Regex("[^0-9]"), "").length < 7) {
            val matches = ContactAliasStore.resolveContact(env.context, contactQuery)
            if (matches.isNotEmpty()) {
                val resolved = matches.first()
                contactName = resolved.name
                targetDigits = ContactAliasStore.normalizePhoneNumber(resolved.phoneNumber)
            }
        }

        // Clean to pure digits
        val cleanDigits = targetDigits.replace(Regex("[^0-9]"), "")

        // 2. Deep-link launch via wa.me intent
        val encodedMsg = try { URLEncoder.encode(message, "UTF-8") } catch (e: Exception) { message }
        val waUri = if (cleanDigits.length >= 10) {
            Uri.parse("https://wa.me/$cleanDigits?text=$encodedMsg")
        } else {
            Uri.parse("whatsapp://send?text=$encodedMsg")
        }

        val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
            setPackage(WA_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            env.context.startActivity(intent)
        } catch (e: Exception) {
            // WhatsApp not installed
            return SkillResult(false, "WhatsApp is not installed on this device.")
        }

        // 3. Await WhatsApp in foreground
        val service = env.service
        if (service != null) {
            service.waitForPackage(WA_PACKAGE, 4000)
            service.waitForIdle(2000, 300)

            // 4. Tap the Send button (contentDescription "Send" or viewId "send")
            delay(600)
            val sendClicked = service.clickElementByQuery("send") || service.clickElementByQuery("Send") || service.pressEnterOrSearch()

            service.waitForIdle(1500, 300)
            return SkillResult(true, "Message sent to $contactName on WhatsApp.", data = mapOf("contact" to contactName, "delivered" to sendClicked))
        }

        return SkillResult(true, "WhatsApp chat opened for $contactName.")
    }

    private suspend fun handleSendMany(args: JSONObject, env: AgentEnv): SkillResult {
        val contactsArray = args.optJSONArray("contacts")
        val baseMessage = args.optString("message", "Good morning!")
        if (contactsArray == null || contactsArray.length() == 0) {
            return SkillResult(false, "No contacts provided for bulk send.")
        }

        val total = contactsArray.length()
        val successList = mutableListOf<String>()
        val failList = mutableListOf<String>()

        env.listener?.onMilestone("Sending WhatsApp message to $total contacts...")

        for (i in 0 until total) {
            val contactName = contactsArray.optString(i, "")
            if (contactName.isBlank()) continue

            val singleArgs = JSONObject().apply {
                put("contact", contactName)
                put("message", baseMessage)
            }

            val res = handleSingleSend(singleArgs, env)
            if (res.success) {
                successList.add(contactName)
            } else {
                failList.add(contactName)
            }

            // Human pacing delay between 3000ms and 8000ms
            if (i < total - 1) {
                val delayTime = (3000L..5000L).random()
                delay(delayTime)
            }
        }

        val summary = "WhatsApp broadcast complete: ${successList.size} sent successfully, ${failList.size} failed."
        return SkillResult(true, summary, data = mapOf("succeeded" to successList, "failed" to failList))
    }

    private suspend fun handleLiveLocation(args: JSONObject, env: AgentEnv): SkillResult {
        val contact = args.optString("contact", "").ifBlank { args.optString("recipient", "") }
        val durationStr = args.optString("duration", "1 hour")

        // 1. Open WhatsApp chat with contact
        val openArgs = JSONObject().apply {
            put("contact", contact)
            put("message", "")
        }
        handleSingleSend(openArgs, env)

        val service = env.service ?: return SkillResult(false, "Accessibility service unavailable for live location flow.")
        service.waitForPackage(WA_PACKAGE, 3500)
        service.waitForIdle(2000, 300)

        // 2. Tap Attach icon
        val attachClicked = service.clickElementByQuery("Attach") || service.clickElementByQuery("attach")
        if (!attachClicked) {
            return SkillResult(false, "Could not find Attach icon in WhatsApp chat.")
        }

        delay(800)
        service.waitForIdle(1500, 300)

        // 3. Tap Location
        val locationClicked = service.clickElementByQuery("Location") || service.clickElementByQuery("स्थान")
        if (!locationClicked) {
            return SkillResult(false, "Could not find Location option in WhatsApp attach menu.")
        }

        delay(1200)
        service.waitForIdle(2500, 300)

        // 4. Tap "Share live location"
        val shareLiveClicked = service.clickElementByQuery("Share live location") || service.clickElementByQuery("लाइव लोकेशन")
        if (!shareLiveClicked) {
            return SkillResult(false, "Could not tap 'Share live location' button.")
        }

        delay(800)
        service.waitForIdle(1500, 300)

        // 5. Select duration (15 min, 1 hour, 8 hours)
        val targetDuration = when {
            durationStr.contains("15") -> "15"
            durationStr.contains("8") -> "8"
            else -> "1"
        }
        service.clickElementByQuery(targetDuration)
        delay(400)

        // 6. Tap Send
        val sendClicked = service.clickElementByQuery("Send") || service.clickElementByQuery("send")
        return SkillResult(true, "Live location shared with $contact for $durationStr.", data = mapOf("contact" to contact, "duration" to durationStr))
    }
}
