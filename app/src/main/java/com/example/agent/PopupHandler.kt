package com.example.agent

import android.util.Log
import com.example.util.SaifAccessibilityService
import kotlinx.coroutines.delay

/**
 * Intelligent blocker and popup dismissal engine.
 * Safely dismisses ads, update dialogs, and rating prompts without clicking sensitive permissions or payment terms.
 */
object PopupHandler {
    private const val TAG = "SAIF_AGENT"

    private val HARMLESS_DISMISS_TARGETS = listOf(
        "skip ad",
        "skip ads",
        "skip",
        "not now",
        "later",
        "no thanks",
        "maybe later",
        "dismiss",
        "close",
        "cancel",
        "remind me later",
        "got it",
        "theek hai"
    )

    private val SENSITIVE_KEYWORDS = listOf(
        "pay",
        "purchase",
        "subscribe",
        "payment",
        "upi",
        "terms of service",
        "privacy policy",
        "accept all",
        "agree and continue"
    )

    /**
     * Checks if a harmless dismissible blocker is on screen and clicks it.
     */
    suspend fun dismissIfPresent(service: SaifAccessibilityService): Boolean {
        val screenText = service.getVisibleScreenText().lowercase()

        // Check if popup looks sensitive
        val hasSensitiveTerms = SENSITIVE_KEYWORDS.any { screenText.contains(it) }
        if (hasSensitiveTerms) {
            // Do not auto-dismiss or auto-accept sensitive financial/legal prompts
            return false
        }

        for (target in HARMLESS_DISMISS_TARGETS) {
            val clicked = service.findAndClick(target)
            if (clicked) {
                Log.i(TAG, "PopupHandler: Safely dismissed dialog with label '$target'")
                delay(400)
                service.waitForIdle(1000, 200)
                return true
            }
        }

        return false
    }
}
