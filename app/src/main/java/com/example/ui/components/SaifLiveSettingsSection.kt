package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SaifTheme
import com.example.util.SaifMemoryCore
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.model.GeminiConstants

/**
 * The settings of SAIF AI AUTOMATION, merged into SAIF AI's own Settings panel.
 *
 * Rule followed here: **nothing that already exists in this panel is repeated.** The API key/model stays in
 * "AI PROVIDER & MODELS", the memories stay in "AI MEMORY", the export/clear stays in "DATA & PRIVACY", the theme
 * stays in "APPEARANCE" and the voice stays in "VOICE & TTS". What is left below is only what the automation
 * engine genuinely needed and the app did not have:
 *
 *  • AUTO LANGUAGE and the conversation language (all Indian languages)
 *  • AI persona for the live conversation
 *  • Silent execution mode
 *  • the 3D orb look (animation, speed, glow, size)
 *  • the floating overlay avatar and its behaviour
 *  • system permissions the engine needs
 *  • owner voice biometrics (voice-print lock)
 *
 * Every value is written straight into the engine's own [com.jarvis.assistant.data.preferences.AppPreferences],
 * so there is still exactly one copy of each setting and the live session picks it up immediately.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SaifLiveSettingsSection(glassBorderBrush: Brush) {
    val context = LocalContext.current
    val maybePrefs = remember { runCatching { JarvisApp.instance.preferences }.getOrNull() }
    if (maybePrefs == null) {
        // The live engine is not available on this build; show nothing instead of a broken section.
        return
    }
    val prefs = maybePrefs
    val autoLanguage by SaifMemoryCore.autoLanguage.collectAsState()

    // Local mirror of the engine values so the UI can render without touching Android prefs on every frame.
    var language by remember { mutableStateOf(prefs.language) }
    var persona by remember { mutableStateOf(prefs.personality) }
    var silent by remember { mutableStateOf(prefs.isSilentExecutionMode) }
    var orbAnim by remember { mutableStateOf(prefs.orbAnimation) }
    var orbSpeed by remember { mutableStateOf(prefs.orbSpeed) }
    var orbGlow by remember { mutableStateOf(prefs.orbGlow) }
    var orbScale by remember { mutableStateOf(prefs.orbScale) }
    var overlayOn by remember { mutableStateOf(prefs.isTaskOverlayEnabled) }
    var overlayAvatar by remember { mutableStateOf(prefs.taskOverlayAvatar) }
    var overlayMode by remember { mutableStateOf(prefs.taskOverlayMode) }
    var nativeGemini by remember { mutableStateOf(com.jarvis.assistant.SaifAutomation.useGeminiNativeLive) }
    var voiceLock by remember { mutableStateOf(prefs.isOwnerVoiceEnabled) }
    var hasVoicePrint by remember { mutableStateOf(prefs.hasOwnerVoiceProfile()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("SAIF AI LIVE \u2022 BAATCHEET")

        LiveCard(glassBorderBrush) {
            // ------------------------------------------------------------------ AUTO LANGUAGE
            LiveSwitchRow(
                title = "Auto Language",
                subtitle = "Aap jis bhasha me bolo, SAIF usi me jawab de (sabhi Indian languages)",
                checked = autoLanguage,
                onCheckedChange = { on ->
                    SaifMemoryCore.setAutoLanguage(on)
                    if (on) {
                        prefs.language = "Auto"
                        language = "Auto"
                    }
                }
            )

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            LiveSwitchRow(
                title = "Gemini native live audio",
                subtitle = "Optional fast path for Gemini only. Off uses your selected SAIF TTS voice for all providers.",
                checked = nativeGemini,
                onCheckedChange = { on ->
                    nativeGemini = on
                    com.jarvis.assistant.SaifAutomation.useGeminiNativeLive = on
                    com.jarvis.assistant.SaifAutomation.onHostApiChanged()
                }
            )
            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ conversation language
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Conversation Language",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaifTheme.colors.textPrimary
                )
                Text(
                    text = if (autoLanguage) "Auto detect chal raha hai \u2014 neeche se force kar sakte ho"
                    else "Live baatcheet isi bhasha me hogi",
                    fontSize = 13.sp,
                    color = SaifTheme.colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GeminiConstants.SUPPORTED_LANGUAGES.forEach { l ->
                        LiveChip(l, selected = language == l) {
                            language = l
                            prefs.language = l
                            if (l == "Auto") SaifMemoryCore.setAutoLanguage(true)
                        }
                    }
                }
            }

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ AI persona
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "AI Persona",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaifTheme.colors.textPrimary
                )
                Text(
                    text = "Live baatcheet me SAIF ka behaviour",
                    fontSize = 13.sp,
                    color = SaifTheme.colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GeminiConstants.SUPPORTED_PERSONALITIES.forEach { pName ->
                        LiveChip(pName, selected = persona == pName) {
                            persona = pName
                            prefs.personality = pName
                        }
                    }
                }
            }

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ silent execution
            LiveSwitchRow(
                title = "Silent Execution Mode",
                subtitle = "Task bina awaaz kiye chupchap complete kare",
                checked = silent,
                onCheckedChange = { on -> silent = on; prefs.isSilentExecutionMode = on }
            )

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ 3D orb
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "3D Orb Look",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaifTheme.colors.textPrimary
                )
                Text(
                    text = "Live baatcheet wala 3D orb animation",
                    fontSize = 13.sp,
                    color = SaifTheme.colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ORB_STYLES.forEach { (id, label) ->
                        LiveChip(label, selected = orbAnim == id) {
                            orbAnim = id
                            prefs.orbAnimation = id
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LiveSlider("Speed", orbSpeed, 0.3f..2.5f) { v -> orbSpeed = v; prefs.orbSpeed = v }
                LiveSlider("Glow", orbGlow, 0.2f..2.0f) { v -> orbGlow = v; prefs.orbGlow = v }
                LiveSlider("Size", orbScale, 0.5f..1.8f) { v -> orbScale = v; prefs.orbScale = v }
            }

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ floating overlay
            LiveSwitchRow(
                title = "Floating Window (Overlay)",
                subtitle = "Screen ke upar chhota SAIF orb \u2014 kisi bhi app me chale",
                checked = overlayOn,
                onCheckedChange = { on -> overlayOn = on; prefs.isTaskOverlayEnabled = on }
            )

            if (overlayOn) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Overlay Avatar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SaifTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OVERLAY_AVATARS.forEach { a ->
                            LiveChip(a, selected = overlayAvatar == a) {
                                overlayAvatar = a
                                prefs.taskOverlayAvatar = a
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiveChip("Always visible", selected = overlayMode == "always") {
                            overlayMode = "always"; prefs.taskOverlayMode = "always"
                        }
                        LiveChip("Only while working", selected = overlayMode == "working") {
                            overlayMode = "working"; prefs.taskOverlayMode = "working"
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LiveLink("Open Overlay Avatars screen") {
                        open(context, "com.jarvis.assistant.ui.settings.OverlayAvatarActivity")
                    }
                }
                HorizontalDivider(color = SaifTheme.colors.cardBorder)
            }

            // ------------------------------------------------------------------ owner voice biometrics
            LiveSwitchRow(
                title = "Owner Voice Lock",
                subtitle = if (hasVoicePrint) "Voice print saved \u2014 sirf aapki awaaz sunega"
                else "Pehle apni awaaz enroll karo",
                checked = voiceLock,
                onCheckedChange = { on ->
                    voiceLock = on
                    prefs.isOwnerVoiceEnabled = on
                    if (on && !hasVoicePrint) {
                        open(context, "com.jarvis.assistant.ui.voice.VoiceEnrollmentActivity")
                    }
                }
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                LiveLink(if (hasVoicePrint) "Re-enroll my voice" else "Enroll my voice") {
                    open(context, "com.jarvis.assistant.ui.voice.VoiceEnrollmentActivity")
                }
                if (hasVoicePrint) {
                    Spacer(Modifier.height(6.dp))
                    LiveLink("Delete voice print") {
                        prefs.clearOwnerVoiceProfile()
                        hasVoicePrint = false
                        voiceLock = false
                        prefs.isOwnerVoiceEnabled = false
                    }
                }
            }

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ permissions + hands-free
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "System Permissions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaifTheme.colors.textPrimary
                )
                Text(
                    text = "Screen share, overlay, accessibility aur notification \u2014 live baatcheet ke liye zaroori",
                    fontSize = 13.sp,
                    color = SaifTheme.colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                LiveLink("Open Permissions") {
                    open(context, "com.jarvis.assistant.ui.permission.PermissionActivity")
                }
                Spacer(Modifier.height(6.dp))
                LiveLink("App Control & Hands-Free") {
                    open(context, "com.jarvis.assistant.ui.settings.HandsFreeActivity")
                }
            }

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ credit
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "SAIF AI \u2022 LIVE AUTOMATION ENGINE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaifTheme.colors.textPrimary
                )
                Text(
                    text = "Live baatcheet, 3D orb aur screen share SAIF AI ka apna feature hai.",
                    fontSize = 12.sp,
                    color = SaifTheme.colors.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Built on JARVIS Android Assistant by Tech GPTx \u2022 TAMCL v1.0",
                    fontSize = 11.sp,
                    color = SaifTheme.colors.textSecondary
                )
            }

            HorizontalDivider(color = SaifTheme.colors.cardBorder)

            // ------------------------------------------------------------------ memory per feature
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Memory Recall",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaifTheme.colors.textPrimary
                )
                Text(
                    text = "Har feature ka memory ek hi backend me \u2014 chat, live, video, builder",
                    fontSize = 13.sp,
                    color = SaifTheme.colors.textSecondary
                )
                val counts = remember { SaifMemoryCore.perFeatureCounts() }
                if (counts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        counts.forEach { (f, n) ->
                            LiveChip("$f \u2022 $n", selected = false) {}
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private val ORB_STYLES = listOf(
    "holographic_plasma" to "Holographic Plasma",
    "cosmic_pulsar" to "Cosmic Pulsar",
    "quantum_core" to "Quantum Core",
    "cyber_ring" to "Cyber Ring",
    "siri_glow" to "Siri Glow"
)

private val OVERLAY_AVATARS = listOf(
    "ghost", "cat", "droid", "mech", "alien", "blob", "star", "flower",
    "cloud", "drop", "hexagon", "circle", "square", "triangle", "pebble", "pill", "puddle"
)

private fun open(context: Context, className: String) {
    try {
        context.startActivity(Intent().setClassName(context.packageName, className))
    } catch (t: Throwable) {
        android.widget.Toast.makeText(context, "Screen nahi khul payi: ${t.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun LiveCard(glassBorderBrush: Brush, content: @Composable () -> Unit) {
    LiquidSurface(
        color = SaifTheme.colors.surfaceCard,
        glassBorderBrush = glassBorderBrush,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun LiveSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SaifTheme.colors.textPrimary)
            Text(text = subtitle, fontSize = 13.sp, color = SaifTheme.colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = com.example.ui.theme.PrimaryAccent,
                uncheckedThumbColor = SaifTheme.colors.textSecondary,
                uncheckedTrackColor = SaifTheme.colors.primaryBackground
            )
        )
    }
}

@Composable
private fun LiveChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(10.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) com.example.ui.theme.PrimaryAccent else SaifTheme.colors.primaryBackground,
            labelColor = if (selected) Color.White else SaifTheme.colors.textSecondary
        ),
        border = null
    )
}

@Composable
private fun LiveSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = SaifTheme.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(String.format("%.1fx", value), fontSize = 13.sp, color = SaifTheme.colors.textPrimary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = com.example.ui.theme.PrimaryAccent,
                activeTrackColor = com.example.ui.theme.PrimaryAccent,
                inactiveTrackColor = SaifTheme.colors.cardBorder
            )
        )
    }
}

@Composable
private fun LiveLink(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = com.example.ui.theme.PrimaryAccent,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = com.example.ui.theme.PrimaryAccent,
            modifier = Modifier.size(18.dp)
        )
    }
}
