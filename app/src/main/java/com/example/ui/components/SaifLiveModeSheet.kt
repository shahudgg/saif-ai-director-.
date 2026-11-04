package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.SaifAutomation

/**
 * The two options the purple mic now offers. This is the entry point that replaced the red "+" button: the
 * SAIF AI AUTOMATION engine starts from inside SAIF AI's own live-conversation feature, with SAIF AI's UI.
 *
 *  • NORMAL       — talk only, exactly like SAIF AI Automation's voice conversation.
 *  • SCREEN SHARE — the engine also looks at the screen and helps with what it sees.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaifLiveModeSheet(
    isLightMode: Boolean,
    onDismiss: () -> Unit,
    onNeedScreenCapturePermission: (SaifAutomation.LiveMode) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Round 15: the microphone is a runtime permission. Before this, tapping "Normal voice" started the
    // engine with no mic grant, AudioRecord failed and the session died silently.
    var startAfterGrant by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && startAfterGrant) {
            startAfterGrant = false
            startNormal(context, isLightMode, onDismiss)
        } else if (!granted) {
            startAfterGrant = false
            Toast.makeText(context, "Microphone permission ke bina live baatcheet nahi chalegi.", Toast.LENGTH_LONG).show()
        }
    }
    fun startWithMic() {
        val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startNormal(context, isLightMode, onDismiss)
        else { startAfterGrant = true; micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF0D0B18)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp)) {
            Text("SAIF AI LIVE BAATCHEET", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "3D orb ke saath live baat karein. Awaaz SAIF AI ki apni hai aur API aapki Settings se aati hai.",
                color = Color(0xFF9CA3AF), fontSize = 12.sp
            )
            // Round 10: show which provider/key the live engine will use — it works with ANY provider
            // (cloud STT for Groq/OpenAI/Gemini keys, on-device Vosk STT for everyone else).
            val api = SaifAutomation.currentApi()
            val apiLine = if (api.hasKey) {
                "Engine: ${api.provider.ifBlank { "auto" }} • ${api.model.ifBlank { "default model" }} • koi bhi provider chalega"
            } else {
                "⚠️ Settings (⚙) me pehle koi bhi API key daalo — Gemini ho ya koi aur, dono kaam karenge."
            }
            Spacer(Modifier.height(6.dp))
            Text(apiLine, color = if (api.hasKey) Color(0xFF34D399) else Color(0xFFFBBF24), fontSize = 11.5.sp)
            Spacer(Modifier.height(18.dp))

            ModeCard(
                title = SaifAutomation.LiveMode.NORMAL.label,
                subtitle = SaifAutomation.LiveMode.NORMAL.description,
                icon = Icons.Default.GraphicEq,
                gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
            ) { startWithMic() }

            Spacer(Modifier.height(12.dp))

            ModeCard(
                title = SaifAutomation.LiveMode.SCREEN_SHARE.label,
                subtitle = SaifAutomation.LiveMode.SCREEN_SHARE.description,
                icon = Icons.Default.ScreenShare,
                gradient = listOf(Color(0xFF06B6D4), Color(0xFF2563EB))
            ) { onNeedScreenCapturePermission(SaifAutomation.LiveMode.SCREEN_SHARE) }
        }
    }
}

/** Starts the engine in normal (voice only) mode. Returns true when the session really started. */
fun startNormal(context: Context, isLightMode: Boolean, onDismiss: () -> Unit): Boolean {
    val ok = SaifAutomation.startLive(context, SaifAutomation.LiveMode.NORMAL, isLightMode)
    if (ok) {
        SaifLiveSession.isHostPanelOpen = true
        com.example.power.SaifToolkit.Counters.bump(context, "live_sessions")
    }
    onDismiss()
    return ok
}

@Composable
private fun ModeCard(
    title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, title, tint = Color.White, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
    }
}

/**
 * Tiny piece of shared state between the host UI and the live engine, so the app knows whether the orb panel is
 * open in-app (full-screen) or should be shown as the floating bubble.
 */
object SaifLiveSession {
    @Volatile
    var isHostPanelOpen: Boolean = false

    /** The mode the host asked for but has not got a MediaProjection for yet. */
    @Volatile
    var pendingScreenShare: Boolean = false
}
