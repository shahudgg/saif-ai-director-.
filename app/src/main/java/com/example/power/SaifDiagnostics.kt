package com.example.power

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.data.local.ProviderSettingsManager
import com.example.flow.FlowDownloader
import com.example.flow.FlowLibrary
import com.example.llm.ProviderProfile
import com.jarvis.assistant.SaifAutomation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Round 15 — SELF TEST.
 *
 * "Feature kaam nahi kar raha" is impossible to fix from a screenshot, so this runs the same checks the
 * code needs at runtime and reports PASS/FAIL with the exact fix. Everything is measured, nothing is
 * assumed: permissions are queried from the OS, the voice engine is asked whether it is linked, the
 * online model is asked whether it links at all, the Flow library and plan are read from disk, and the
 * last crash log (if any) is shown verbatim.
 */
object SaifDiagnostics {

    data class Check(
        val title: String,
        val ok: Boolean,
        val detail: String,
        val fix: String = ""
    )

    suspend fun run(context: Context): List<Check> = withContext(Dispatchers.IO) {
        val out = ArrayList<Check>()
        val app = context.applicationContext

        // 1 — API settings
        runCatching { ProviderSettingsManager.init(app) }
        val state = runCatching { ProviderSettingsManager.loadState() }.getOrNull()
        val keys = state?.apiKeys?.filter { it.isNotBlank() } ?: emptyList()
        out.add(Check(
            title = "API key & model",
            ok = keys.isNotEmpty(),
            detail = if (keys.isEmpty()) "Koi key save nahi hai"
            else "${keys.size} key · ${state?.providerName ?: "?"} · ${state?.activeModel ?: "?"}",
            fix = "Settings (⚙) → AI PROVIDER → key + model daal ke Save dabao"
        ))

        // 2 — microphone (the #1 reason live baatcheet stays silent)
        val mic = SaifToolkit.isGranted(app, android.Manifest.permission.RECORD_AUDIO)
        out.add(Check(
            title = "Microphone permission",
            ok = mic,
            detail = if (mic) "Granted" else "DENIED — live baatcheet mic nahi khol payegi",
            fix = "Settings (⚙) → PERMISSIONS → Microphone ON, ya app settings se allow karo"
        ))

        // 3 — notifications (foreground service needs this on Android 13+)
        val notif = Build.VERSION.SDK_INT < 33 || SaifToolkit.isGranted(app, "android.permission.POST_NOTIFICATIONS")
        out.add(Check(
            title = "Notification permission",
            ok = notif,
            detail = if (notif) "Granted" else "DENIED — live service ka notification chhup jayega",
            fix = "Notification allow karo (Android 13+)"
        ))

        // 4 — voice engine link + running state
        val linked = runCatching { SaifAutomation.isLinked && SaifAutomation.hostLiveTurnFactory != null }.getOrDefault(false)
        val running = runCatching { SaifAutomation.isServiceRunning() }.getOrDefault(false)
        out.add(Check(
            title = "Live voice engine",
            ok = linked,
            detail = if (linked) (if (running) "Linked · session RUNNING" else "Linked · session idle") else "Engine link nahi hua (Application init fail)",
            fix = "App ko ek baar force-stop karke dobara kholo; phir Power Tools → Live test"
        ))

        // 5 — TTS engine present
        val ttsOk = runCatching {
            app.packageManager.queryIntentServices(Intent("android.intent.action.TTS_SERVICE"), 0).isNotEmpty()
        }.getOrDefault(false)
        out.add(Check(
            title = "Text-to-speech engine",
            ok = ttsOk,
            detail = if (ttsOk) "Device TTS available" else "Koi TTS engine nahi — SAIF bol nahi payega",
            fix = "Play Store se 'Speech Services by Google' install/enable karo"
        ))

        // 6 — offline speech recognition model
        val voskReady = runCatching { com.jarvis.assistant.audio.VoskModelManager.isModelReady.value }.getOrDefault(false)
        out.add(Check(
            title = "Offline speech model (Vosk)",
            ok = voskReady,
            detail = if (voskReady) "Ready"
            else "Abhi download nahi hua — non-OpenAI/Groq keys ke saath sunai nahi dega",
            fix = "Internet on rakho aur ek baar live baatcheet shuru karo (pehli baar model download hota hai)"
        ))

        // 7 — Flow library
        val flowCount = runCatching { FlowLibrary.images(app).size }.getOrDefault(0)
        val lastErr = runCatching { FlowDownloader.lastError }.getOrDefault("")
        out.add(Check(
            title = "Flow picture library",
            ok = flowCount > 0,
            detail = "$flowCount image(s)" + if (lastErr.isNotBlank()) " · last download error: $lastErr" else "",
            fix = "Studio → 🌐 Flow browser → Login → ▶ Generate all"
        ))

        // 8 — Flow plan
        val plan = runCatching { SaifToolkit.Resume.latestPlan(app) }.getOrNull()
        out.add(Check(
            title = "Flow plan (scene prompts)",
            ok = plan != null,
            detail = SaifToolkit.Resume.planLine(plan),
            fix = "AI Director → script approve → 🖼 Flow images (auto)"
        ))

        // 9 — storage
        val free = SaifToolkit.Storage.freeBytes(app)
        val freeOk = free > 300L * 1024 * 1024
        out.add(Check(
            title = "Free storage",
            ok = freeOk,
            detail = SaifToolkit.Storage.fmt(free) + " free",
            fix = "Power Tools → Storage → purani files clean karo"
        ))

        // 10 — network + provider endpoint
        val provider = runCatching { ProviderProfile.getById(state?.providerName ?: "Gemini") }.getOrNull()
        val net = runCatching {
            val conn = (URL((provider?.baseUrl ?: "https://generativelanguage.googleapis.com").trimEnd('/'))
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 7000; readTimeout = 8000
            }
            val code = conn.responseCode
            conn.disconnect()
            code
        }.getOrNull()
        out.add(Check(
            title = "Network / ${provider?.displayName ?: "provider"} reachable",
            ok = net != null,
            detail = if (net == null) "DNS/connection fail" else "HTTP $net",
            fix = "Internet / VPN check karo"
        ))

        // 11 — Termux
        val termux = runCatching { com.example.termux.TermuxBridge.isInstalled(app) }.getOrDefault(false)
        out.add(Check(
            title = "Termux (optional)",
            ok = true,
            detail = if (termux) "Installed" else "Not installed — app shell fallback use hoga",
            fix = "F-Droid se Termux + allow-external-apps=true"
        ))

        // 12 — accessibility (phone agent)
        val a11y = runCatching {
            val enabled = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.contains(app.packageName)
        }.getOrDefault(false)
        out.add(Check(
            title = "Accessibility service (phone agent)",
            ok = a11y,
            detail = if (a11y) "Enabled" else "Off — apps kholna/commands limited rahenge",
            fix = "Android Settings → Accessibility → SAIF AI ON"
        ))

        // 13 — crash log
        val crash = runCatching { com.example.power.CrashGuard.logs(app) }.getOrDefault(emptyList())
        out.add(Check(
            title = "Crash log",
            ok = crash.isEmpty(),
            detail = if (crash.isEmpty()) "Koi crash record nahi" else "${crash.size} crash log — Power Tools se dekh/share karo",
            fix = if (crash.isEmpty()) "" else "'Crash report' card kholo aur trace share karo"
        ))

        out
    }

    fun report(context: Context, checks: List<Check>): String = buildString {
        appendLine("SAIF AI — SELF TEST · build 15.2")
        appendLine("device: " + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE)
        appendLine("passed: " + checks.count { it.ok } + "/" + checks.size)
        appendLine()
        checks.forEach { c ->
            appendLine((if (c.ok) "[PASS] " else "[FAIL] ") + c.title + " — " + c.detail)
            if (!c.ok && c.fix.isNotBlank()) appendLine("        fix: " + c.fix)
        }
        val crash = com.example.power.CrashGuard.lastCrashText(context)
        if (crash.isNotBlank()) {
            appendLine()
            appendLine("---- last crash ----")
            appendLine(crash.take(4000))
        }
    }
}
