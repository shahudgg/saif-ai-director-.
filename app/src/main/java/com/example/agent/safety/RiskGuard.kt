package com.example.agent.safety

import android.util.Log
import com.example.agent.AgentCall
import com.example.agent.ScreenSnapshot

enum class GateResult {
    ALLOW,
    CONFIRM,
    BLOCK,
    YIELD
}

data class GateDecision(
    val result: GateResult,
    val reason: String = ""
)

object RiskGuard {
    private const val TAG = "SAIF_AGENT"

    private val SENSITIVE_PACKAGE_KEYWORDS = listOf(
        "paytm", "phonepe", "bhim", "gpay", "googlepay", "upi", "bank", "paisa", "wallet",
        "cred", "loan", "password", "vault", "authenticator", "bitwarden", "1password", "lastpass"
    )

    private val FORBIDDEN_ACTION_KEYWORDS = listOf(
        "factory reset", "erase all data", "clear all data", "uninstall", "force stop",
        "disable service", "delete account"
    )

    private val PAYMENT_KEYWORDS = listOf(
        "pay now", "make payment", "upi pin", "enter pin", "proceed to pay", "confirm payment",
        "transfer money", "send money", "card number", "cvv"
    )

    fun gate(call: AgentCall, snapshot: ScreenSnapshot): GateDecision {
        val actionName = call.name.lowercase()
        val pkg = snapshot.packageName.lowercase()
        val args = call.arguments.toString().lowercase()
        val intent = call.intent.lowercase()

        // 1. High risk package check (Bank / UPI / Wallet / Password managers)
        for (keyword in SENSITIVE_PACKAGE_KEYWORDS) {
            if (pkg.contains(keyword)) {
                // If trying to click or type in a banking/payment app, yield immediately
                if (actionName.contains("click") || actionName.contains("tap") || actionName.contains("type")) {
                    Log.w(TAG, "RiskGuard blocked interaction in sensitive app: $pkg")
                    return GateDecision(GateResult.YIELD, "Security protection: Banking and payment apps require your manual confirmation.")
                }
            }
        }

        // 2. Destructive actions check (Uninstall, factory reset, clear data)
        for (keyword in FORBIDDEN_ACTION_KEYWORDS) {
            if (args.contains(keyword) || intent.contains(keyword)) {
                Log.w(TAG, "RiskGuard blocked destructive action: $keyword")
                return GateDecision(GateResult.BLOCK, "Destructive action blocked for device safety.")
            }
        }

        // 3. Payment / Checkout / PIN entry check
        for (keyword in PAYMENT_KEYWORDS) {
            if (args.contains(keyword) || intent.contains(keyword)) {
                Log.w(TAG, "RiskGuard yielded payment step: $keyword")
                return GateDecision(GateResult.YIELD, "Payment/PIN step detected: Please complete this step manually on screen.")
            }
        }

        // 4. Secure or password field check on screen
        if (snapshot.secure) {
            Log.w(TAG, "RiskGuard detected secure FLAG_SECURE window")
            return GateDecision(GateResult.YIELD, "Secure screen detected. Please enter your credentials manually.")
        }

        // 5. Special checks for bulk messaging or location sharing
        if (call.name == "run_skill") {
            val skillName = call.arguments.optString("name", "")
            if (skillName == "whatsapp_send_many") {
                val contacts = call.arguments.optJSONObject("args")?.optJSONArray("contacts")
                if (contacts != null && contacts.length() >= 3) {
                    return GateDecision(GateResult.CONFIRM, "Confirm sending automated broadcast to ${contacts.length()} recipients?")
                }
            }
            if (skillName == "whatsapp_live_location") {
                val explicit = call.arguments.optJSONObject("args")?.optBoolean("explicit", false) ?: false
                if (!explicit) {
                    return GateDecision(GateResult.CONFIRM, "Confirm sharing your live location?")
                }
            }
        }

        return GateDecision(GateResult.ALLOW)
    }
}
