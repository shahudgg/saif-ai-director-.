package com.jarvis.assistant.security

import android.content.Context

/**
 * SAIF AI AUTOMATION runs inside the SAIF AI app (its own package + signing key), so the original
 * standalone-JARVIS release-certificate pinning no longer applies and never blocks the app.
 */
object AppIdentityVerifier {
    @Suppress("UNUSED_PARAMETER")
    fun isTrustedRelease(context: Context): Boolean = true

    fun report(context: Context): String = buildString {
        appendLine("SAIF AI AUTOMATION")
        appendLine("Part of the SAIF AI app (${context.packageName}).")
        appendLine()
        appendLine("Based on the open-source JARVIS Android Assistant")
        appendLine("Original developer: ${DeveloperInfo.DEVELOPER_NAME}")
        appendLine(DeveloperInfo.COPYRIGHT_NOTICE)
        appendLine("Source: ${DeveloperInfo.GITHUB_URL}")
        append("License: ${DeveloperInfo.LICENSE}")
    }
}
