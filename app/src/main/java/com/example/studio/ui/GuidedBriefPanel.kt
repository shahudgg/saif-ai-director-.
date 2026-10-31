package com.example.studio.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.composer.LayoutEngine
import com.example.studio.composer.LocalBeatAnalyzer
import com.example.studio.composer.SceneComposer
import com.example.studio.composer.StyleDNA
import com.example.studio.director.BackgroundMusic
import com.example.studio.director.BackgroundMusicProbe
import com.example.studio.director.BrandKit
import com.example.studio.director.DirectorBrief
import com.example.studio.director.GuidedBriefOptions
import com.example.studio.director.SceneRenderer
import com.example.ui.components.VOICE_PROFILES
import com.example.utils.SaifVoice
import com.example.utils.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Panel = Color(0xFF17131F)
private val Ink = Color(0xFFCCC3D9)
private val Accent = Color(0xFF7B2BFF)
private val Aqua = Color(0xFF00E5FF)
private val styles = listOf("Educational explainer", "Documentary", "Cinematic", "Motivational")

/** Brief, visual identity and per-video audio controls for the real Director workflow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuidedBriefPanel(
    brief: DirectorBrief,
    editingBrief: Boolean,
    editingMusic: Boolean,
    onOption: (String, String) -> Unit,
    onVoice: (String?) -> Unit,
    onMusic: (BackgroundMusic?) -> Unit,
    musicScope: CoroutineScope,
    onMusicBusy: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val haptic = LocalHapticFeedback.current
    val music = GuidedBriefOptions.music(brief)
    val voice = brief.answers[GuidedBriefOptions.VOICE]?.takeIf { id -> VOICE_PROFILES.any { it.id == id } }
    var expanded by remember { mutableStateOf(true) }
    var inspecting by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playback by remember { mutableStateOf("idle") }
    fun tap() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    fun stopMusic() { val old = player; player = null; playback = "idle"; runCatching { old?.release() } }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() }; player = null } }
    LaunchedEffect(Unit) { TTSManager.init(appContext) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            stopMusic()
            val permission = runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (permission.isFailure) {
                Toast.makeText(context, "This provider cannot keep read access. Choose another audio file.", Toast.LENGTH_LONG).show()
            } else {
                inspecting = true; onMusicBusy(true)
                musicScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val name = runCatching {
                                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                    val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (c.moveToFirst() && col >= 0) c.getString(col) else null
                                }
                            }.getOrNull() ?: "Selected music"
                            BackgroundMusicProbe.inspect(context, uri).map { duration -> BackgroundMusic(uri.toString(), name, duration, music?.volume ?: 0.2f) }
                        }
                        result.fold(onMusic, { Toast.makeText(context, it.message ?: "Could not read this audio.", Toast.LENGTH_LONG).show() })
                    } finally { inspecting = false; onMusicBusy(false) }
                }
            }
        }
    }

    Surface(shape = RoundedCornerShape(16.dp), color = Panel, border = BorderStroke(1.dp, Color(0xFF34274C)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { tap(); expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR VIDEO BRIEF", color = Aqua, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(if (brief.prompt.isBlank()) "Start with a topic or paste a script below" else brief.prompt.lineSequence().first().take(70),
                        color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
                Text(if (expanded) "▴" else "▾", color = Ink, fontSize = 16.sp)
            }
            if (expanded) {
                Text("Choose a starting point. The Director will ask for anything still needed.", color = Ink, fontSize = 10.sp)
                Text("Length", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("30 seconds", "60 seconds", "3 minutes", "5 minutes").forEach { value ->
                        val seconds = com.example.studio.director.SpeechTiming.parseDuration(value)
                        BriefChoice(value, brief.durationSec == seconds, editingBrief) { tap(); onOption("duration", value) }
                    }
                }
                Text("Format", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("9:16", "16:9", "1:1", "4:5").forEach { value ->
                        BriefChoice(value, brief.aspect == value, editingBrief) { tap(); onOption("aspect", value) }
                    }
                }
                Text("Style presets", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("Illustrative Canvas previews based on your brief; final scenes can vary.", color = Ink, fontSize = 10.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.forEach { style ->
                        StyleTile(brief, style, brief.style == style, editingBrief) { tap(); onOption("style", style) }
                    }
                }
                Text("Audience", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("General public", "Students", "Professionals", "Kids / beginners").forEach { value ->
                        BriefChoice(value, brief.audience == value, editingBrief) { tap(); onOption("audience", value) }
                    }
                }
                Text("Narration voice", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("For this video only · device voice availability may vary", color = Ink, fontSize = 10.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BriefChoice("App voice", voice == null, editingBrief) { tap(); onVoice(null) }
                    VOICE_PROFILES.forEach { profile ->
                        BriefChoice(profile.name, voice == profile.id, editingBrief) { tap(); onVoice(profile.id) }
                    }
                }
                val selected = VOICE_PROFILES.firstOrNull { it.id == (voice ?: SaifVoice.narrationVoiceId(context)) } ?: VOICE_PROFILES.first()
                TextButton(enabled = editingBrief, onClick = {
                    tap(); TTSManager.previewVoice(appContext, if (brief.narrationLanguage.equals("Hinglish", true) || brief.narrationLanguage.equals("Hindi", true)) selected.previewHinglish else selected.previewEnglish, selected.id,
                        onUnavailable = { android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(appContext, "Device speech is unavailable.", Toast.LENGTH_SHORT).show() } })
                }) { Text("Play ${selected.name} sample", color = Aqua, fontSize = 11.sp) }
                Text("Background music", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(music?.let { "${it.name} · ${it.durationMs / 1000}s available" } ?: "None selected", color = Ink, fontSize = 10.sp)
                Text("Your file plays up to its length; it is not looped. Music ducks under narration.", color = Ink, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = editingMusic && !inspecting, onClick = { tap(); picker.launch(arrayOf("audio/*")) }) { Text(if (inspecting) "Checking…" else if (music == null) "Choose audio" else "Change audio", fontSize = 11.sp) }
                    if (music != null) TextButton(enabled = editingMusic && !inspecting, onClick = { tap(); stopMusic(); onMusic(null) }) { Text("Remove", color = Aqua, fontSize = 11.sp) }
                }
                if (music != null) {
                    var level by remember(music.uri, music.volume) { mutableStateOf(music.volume) }
                    Text("Music level ${(level * 100).toInt()}%", color = Ink, fontSize = 10.sp)
                    Slider(value = level, onValueChange = { level = it; runCatching { player?.setVolume(it, it) } }, onValueChangeFinished = { onMusic(music.copy(volume = level)) }, enabled = editingMusic && !inspecting)
                    TextButton(enabled = !inspecting, onClick = {
                        tap()
                        if (playback != "idle") stopMusic() else {
                            try {
                                val mp = MediaPlayer(); player = mp; playback = "loading"
                                mp.setDataSource(context, Uri.parse(music.uri))
                                mp.setOnPreparedListener { ready ->
                                    if (player === ready) runCatching { ready.setVolume(level, level); ready.start(); playback = "playing" }
                                        .onFailure { stopMusic(); Toast.makeText(context, "Music preview could not be played.", Toast.LENGTH_SHORT).show() }
                                }
                                mp.setOnCompletionListener { completed -> if (player === completed) stopMusic() }
                                mp.setOnErrorListener { failed, _, _ ->
                                    if (player === failed) { stopMusic(); Toast.makeText(context, "Music preview could not be played.", Toast.LENGTH_SHORT).show() }
                                    true
                                }
                                mp.prepareAsync()
                            } catch (_: Exception) { stopMusic(); Toast.makeText(context, "Music preview could not be played.", Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text(if (playback == "idle") "Play music preview" else if (playback == "loading") "Cancel loading" else "Stop preview", color = Aqua, fontSize = 11.sp) }
                }
                if (!editingBrief) Text("Brief and voice are locked while scenes are being built or after a plan exists.", color = Ink, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun BriefChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (selected) Accent else Color(0xFF211B2E),
        modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick)) {
        Text(label, color = if (enabled) Color.White else Ink, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

@Composable
private fun StyleTile(brief: DirectorBrief, name: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val dna = StyleDNA.fromBrief(brief.copy(style = name))
    val aspect = brief.aspect ?: "9:16"
    val accent = BrandKit.accent(context)
    val bitmap = remember(dna.signature, aspect, accent) {
        runCatching {
            val title = brief.prompt.lineSequence().firstOrNull().orEmpty().take(45).ifBlank { "Your story" }
            val beat = LocalBeatAnalyzer.analyze(title, 0, 1, 3000L)
            val graph = SceneComposer.compose(beat, aspect, variant = 0, accentOverride = accent, dna = dna)
            val (aw, ah) = LayoutEngine.parseAspect(aspect)
            val width = (180f * aw / maxOf(aw, ah)).toInt().coerceAtLeast(1)
            val height = (180f * ah / maxOf(aw, ah)).toInt().coerceAtLeast(1)
            val image = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            SceneRenderer.draw(android.graphics.Canvas(image), width, height, graph, 1800L, 3000L)
            image.asImageBitmap()
        }.getOrNull()
    }
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF211B2E), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Aqua else Color(0xFF3A3049)),
        modifier = Modifier.width(112.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.padding(5.dp)) {
            if (bitmap != null) Image(bitmap = bitmap, contentDescription = "$name illustrative style preview", modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(6.dp)))
            else Box(Modifier.fillMaxWidth().height(72.dp).background(Color(0xFF100D19), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("Preview unavailable", color = Ink, fontSize = 9.sp) }
            Text(name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp), maxLines = 2)
        }
    }
}
