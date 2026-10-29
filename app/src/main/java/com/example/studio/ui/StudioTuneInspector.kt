package com.example.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studio.model.AudioItem
import com.example.studio.model.ItemEffects
import com.example.studio.model.ItemTransform
import com.example.studio.model.TextItem
import com.example.studio.model.TimelineItem
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import com.example.studio.state.StudioAction
import com.example.studio.state.StudioViewModel

@Composable
fun StudioTuneInspector(
    item: TimelineItem,
    viewModel: StudioViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val playheadUs = viewModel.clock.currentTimeUs.value

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF161322))
            .border(1.dp, Color(0xFF262335), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(14.dp)
            .verticalScroll(scrollState)
    ) {
        // Inspector Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tune: ${item.label}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (item) {
            is TextItem -> {
                TextTuneControls(item = item, viewModel = viewModel)
            }
            is AudioItem -> {
                AudioTuneControls(item = item, viewModel = viewModel)
            }
            is VoiceItem -> {
                VoiceTuneControls(item = item, viewModel = viewModel)
            }
            else -> {
                VisualTuneControls(item = item, viewModel = viewModel, playheadUs = playheadUs)
            }
        }
    }
}

@Composable
private fun VisualTuneControls(
    item: TimelineItem,
    viewModel: StudioViewModel,
    playheadUs: Long
) {
    var transform by remember(item.id, item.transform) { mutableStateOf(item.transform) }
    var effects by remember(item.id, item.effects) { mutableStateOf(item.effects) }

    val hasKeyframeAtPlayhead = remember(item.keyframes, playheadUs) {
        item.keyframes.any { kotlin.math.abs(it.timeUs - playheadUs) < 100_000L }
    }

    // Keyframe Toggle Button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF231F33))
            .clickable {
                viewModel.commit(StudioAction.ToggleKeyframeAtTime(item.id, playheadUs, transform))
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Diamond,
                contentDescription = null,
                tint = if (hasKeyframeAtPlayhead) Color(0xFF00E5FF) else Color(0xFF9E95AC),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasKeyframeAtPlayhead) "Keyframe Active at Playhead" else "Add Keyframe at Playhead",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = if (hasKeyframeAtPlayhead) "REMOVE ◆" else "ADD ◆",
            color = if (hasKeyframeAtPlayhead) Color(0xFF00E5FF) else Color(0xFF8B5CF6),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Scale Slider
    TuneSliderRow(
        label = "Scale",
        value = transform.scale,
        valueRange = 0.2f..3.0f,
        displayValue = "%.2fx".format(transform.scale),
        onValueChange = {
            transform = transform.copy(scale = it)
            viewModel.commit(StudioAction.UpdateItemTransform(item.id, transform))
        }
    )

    // Position X
    TuneSliderRow(
        label = "Position X",
        value = transform.x,
        valueRange = -300f..300f,
        displayValue = "${transform.x.toInt()}px",
        onValueChange = {
            transform = transform.copy(x = it)
            viewModel.commit(StudioAction.UpdateItemTransform(item.id, transform))
        }
    )

    // Position Y
    TuneSliderRow(
        label = "Position Y",
        value = transform.y,
        valueRange = -400f..400f,
        displayValue = "${transform.y.toInt()}px",
        onValueChange = {
            transform = transform.copy(y = it)
            viewModel.commit(StudioAction.UpdateItemTransform(item.id, transform))
        }
    )

    // Rotation Slider
    TuneSliderRow(
        label = "Rotation",
        value = transform.rotation,
        valueRange = -180f..180f,
        displayValue = "${transform.rotation.toInt()}°",
        onValueChange = {
            transform = transform.copy(rotation = it)
            viewModel.commit(StudioAction.UpdateItemTransform(item.id, transform))
        }
    )

    // Opacity Slider
    TuneSliderRow(
        label = "Opacity",
        value = transform.opacity,
        valueRange = 0f..1f,
        displayValue = "${(transform.opacity * 100).toInt()}%",
        onValueChange = {
            transform = transform.copy(opacity = it)
            viewModel.commit(StudioAction.UpdateItemTransform(item.id, transform))
        }
    )

    // Neon Glow Effect Slider
    TuneSliderRow(
        label = "Neon Glow",
        value = effects.neonGlow,
        valueRange = 0f..1f,
        displayValue = "${(effects.neonGlow * 100).toInt()}%",
        onValueChange = {
            effects = effects.copy(neonGlow = it)
            viewModel.commit(StudioAction.UpdateItemEffects(item.id, effects))
        }
    )

    // Drop Shadow Slider
    TuneSliderRow(
        label = "Drop Shadow",
        value = effects.dropShadow,
        valueRange = 0f..1f,
        displayValue = "${(effects.dropShadow * 100).toInt()}%",
        onValueChange = {
            effects = effects.copy(dropShadow = it)
            viewModel.commit(StudioAction.UpdateItemEffects(item.id, effects))
        }
    )

    // Blend Mode Selector
    Text("Blend Mode", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Normal", "Screen", "Multiply", "Overlay", "Add", "Lighten", "Darken").forEach { mode ->
            val isSelected = transform.blendMode.equals(mode, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF262335))
                    .clickable {
                        transform = transform.copy(blendMode = mode)
                        viewModel.commit(StudioAction.UpdateItemTransform(item.id, transform))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(mode, color = Color.White, fontSize = 11.sp)
            }
        }
    }

    if (item is VideoItem) {
        Spacer(modifier = Modifier.height(14.dp))
        Text("⚡ Speed Ramp Curve Studio (Level 4)", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            com.example.ui.components.VideoEditorPresets.ALL_SPEED_RAMP_PRESETS.forEach { preset ->
                val isSelected = item.speedRampCurve.equals(preset.name, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF262335))
                        .border(1.dp, if (isSelected) Color(0xFF00E5FF) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.commit(StudioAction.UpdateSpeedRamp(item.id, preset.name))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(preset.name, color = if (isSelected) Color(0xFF00E5FF) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(preset.description.take(24) + "...", color = Color(0xFF9E95AC), fontSize = 9.sp)
                    }
                }
            }
        }
    }

    if (item is VideoItem || item is com.example.studio.model.ImageItem) {
        val currentTransition = when (item) {
            is VideoItem -> item.transitionIn
            is com.example.studio.model.ImageItem -> item.transitionIn
            else -> "None"
        }
        val currentTransitionDurSec = when (item) {
            is VideoItem -> item.transitionDurationUs / 1_000_000f
            is com.example.studio.model.ImageItem -> item.transitionDurationUs / 1_000_000f
            else -> 0.3f
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("🎬 Cinematic Transition In (Level 4)", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val transitionsList = listOf("None") + com.example.ui.components.VideoEditorPresets.ALL_CINEMATIC_TRANSITIONS.map { it.name }
            transitionsList.forEach { transName ->
                val isSelected = currentTransition.equals(transName, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF7B2BFF) else Color(0xFF262335))
                        .clickable {
                            viewModel.commit(StudioAction.UpdateTransition(item.id, transName, (currentTransitionDurSec * 1_000_000L).toLong()))
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(transName, color = Color.White, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (!currentTransition.equals("None", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(6.dp))
            TuneSliderRow(
                label = "Transition Duration",
                value = currentTransitionDurSec,
                valueRange = 0.2f..2.0f,
                displayValue = "%.1fs".format(currentTransitionDurSec),
                onValueChange = {
                    viewModel.commit(StudioAction.UpdateTransition(item.id, currentTransition, (it * 1_000_000L).toLong()))
                }
            )
        }
    }
}

@Composable
private fun TextTuneControls(
    item: TextItem,
    viewModel: StudioViewModel
) {
    var textInput by remember(item.id, item.text) { mutableStateOf(item.text) }
    var fontSizeSp by remember(item.id, item.fontSizeSp) { mutableStateOf(item.fontSizeSp) }
    var textColorHex by remember(item.id, item.textColorHex) { mutableStateOf(item.textColorHex) }
    var outlineWidth by remember(item.id, item.outlineWidth) { mutableStateOf(item.outlineWidth) }
    var stylePreset by remember(item.id, item.stylePreset) { mutableStateOf(item.stylePreset) }

    // Multi-line Text Input
    OutlinedTextField(
        value = textInput,
        onValueChange = {
            textInput = it
            viewModel.commit(
                StudioAction.UpdateTextItem(
                    itemId = item.id,
                    text = it,
                    fontSizeSp = fontSizeSp,
                    textColorHex = textColorHex,
                    outlineColorHex = item.outlineColorHex,
                    outlineWidth = outlineWidth,
                    stylePreset = stylePreset
                )
            )
        },
        label = { Text("Subtitle Text (Devanagari / Hinglish)", color = Color(0xFFAEC6FF)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF1E1A2D),
            unfocusedContainerColor = Color(0xFF1E1A2D)
        )
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Font Size Slider
    TuneSliderRow(
        label = "Font Size",
        value = fontSizeSp,
        valueRange = 14f..48f,
        displayValue = "${fontSizeSp.toInt()}sp",
        onValueChange = {
            fontSizeSp = it
            viewModel.commit(
                StudioAction.UpdateTextItem(
                    itemId = item.id,
                    text = textInput,
                    fontSizeSp = it,
                    textColorHex = textColorHex,
                    outlineColorHex = item.outlineColorHex,
                    outlineWidth = outlineWidth,
                    stylePreset = stylePreset
                )
            )
        }
    )

    // Outline / Stroke Slider
    TuneSliderRow(
        label = "Outline Stroke",
        value = outlineWidth,
        valueRange = 0f..6f,
        displayValue = "${outlineWidth.toInt()}px",
        onValueChange = {
            outlineWidth = it
            viewModel.commit(
                StudioAction.UpdateTextItem(
                    itemId = item.id,
                    text = textInput,
                    fontSizeSp = fontSizeSp,
                    textColorHex = textColorHex,
                    outlineColorHex = item.outlineColorHex,
                    outlineWidth = it,
                    stylePreset = stylePreset
                )
            )
        }
    )

    // Color Swatches
    Text("Text Color", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            0xFFFFFFFF to "White",
            0xFFFFFF00 to "Yellow",
            0xFF00E5FF to "Cyan",
            0xFF10B981 to "Green",
            0xFFF43F5E to "Rose",
            0xFFA855F7 to "Purple"
        ).forEach { (colorHex, _) ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(colorHex))
                    .border(
                        width = if (textColorHex == colorHex) 2.dp else 0.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .clickable {
                        textColorHex = colorHex
                        viewModel.commit(
                            StudioAction.UpdateTextItem(
                                itemId = item.id,
                                text = textInput,
                                fontSizeSp = fontSizeSp,
                                textColorHex = colorHex,
                                outlineColorHex = item.outlineColorHex,
                                outlineWidth = outlineWidth,
                                stylePreset = stylePreset
                            )
                        )
                    }
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Caption Style Presets + "Apply style to all captions"
    Text("Caption Style Preset", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Hormozi Neon", "MrBeast Bold", "Minimal Clean", "Cyberpunk Glow").forEach { preset ->
            val isSelected = stylePreset == preset
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF262335))
                    .clickable {
                        stylePreset = preset
                        viewModel.commit(
                            StudioAction.UpdateTextItem(
                                itemId = item.id,
                                text = textInput,
                                fontSizeSp = fontSizeSp,
                                textColorHex = textColorHex,
                                outlineColorHex = item.outlineColorHex,
                                outlineWidth = outlineWidth,
                                stylePreset = preset
                            )
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(preset, color = Color.White, fontSize = 11.sp)
            }
        }
    }

    // Apply to All Captions Button
    Button(
        onClick = {
            viewModel.commit(
                StudioAction.ApplyStyleToAllCaptions(
                    stylePreset = stylePreset,
                    fontSizeSp = fontSizeSp,
                    textColorHex = textColorHex,
                    outlineColorHex = item.outlineColorHex,
                    outlineWidth = outlineWidth
                )
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C1D95)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Icon(Icons.Default.FormatPaint, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Apply Style to All Captions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AudioTuneControls(
    item: AudioItem,
    viewModel: StudioViewModel
) {
    var volume by remember(item.id, item.volume) { mutableStateOf(item.volume) }
    var speed by remember(item.id, item.speed) { mutableStateOf(item.speed) }
    var fadeInSec by remember(item.id, item.fadeInUs) { mutableStateOf(item.fadeInUs / 1_000_000f) }
    var fadeOutSec by remember(item.id, item.fadeOutUs) { mutableStateOf(item.fadeOutUs / 1_000_000f) }
    var ducking by remember(item.id, item.audioDucking) { mutableStateOf(item.audioDucking) }

    TuneSliderRow(
        label = "Volume",
        value = volume,
        valueRange = 0f..2.0f,
        displayValue = "${(volume * 100).toInt()}%",
        onValueChange = {
            volume = it
            viewModel.commit(StudioAction.UpdateAudioItem(item.id, it, speed, (fadeInSec * 1e6).toLong(), (fadeOutSec * 1e6).toLong(), ducking))
        }
    )

    TuneSliderRow(
        label = "Speed",
        value = speed,
        valueRange = 0.5f..2.0f,
        displayValue = "%.2fx".format(speed),
        onValueChange = {
            speed = it
            viewModel.commit(StudioAction.UpdateAudioItem(item.id, volume, it, (fadeInSec * 1e6).toLong(), (fadeOutSec * 1e6).toLong(), ducking))
        }
    )

    TuneSliderRow(
        label = "Fade In",
        value = fadeInSec,
        valueRange = 0f..3.0f,
        displayValue = "%.1fs".format(fadeInSec),
        onValueChange = {
            fadeInSec = it
            viewModel.commit(StudioAction.UpdateAudioItem(item.id, volume, speed, (it * 1e6).toLong(), (fadeOutSec * 1e6).toLong(), ducking))
        }
    )

    TuneSliderRow(
        label = "Fade Out",
        value = fadeOutSec,
        valueRange = 0f..3.0f,
        displayValue = "%.1fs".format(fadeOutSec),
        onValueChange = {
            fadeOutSec = it
            viewModel.commit(StudioAction.UpdateAudioItem(item.id, volume, speed, (fadeInSec * 1e6).toLong(), (it * 1e6).toLong(), ducking))
        }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Audio Ducking", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Lowers volume when voice is active", color = Color(0xFF9E95AC), fontSize = 10.sp)
        }
        Switch(
            checked = ducking,
            onCheckedChange = {
                ducking = it
                viewModel.commit(StudioAction.UpdateAudioItem(item.id, volume, speed, (fadeInSec * 1e6).toLong(), (fadeOutSec * 1e6).toLong(), it))
            },
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
        )
    }
}

@Composable
private fun VoiceTuneControls(
    item: VoiceItem,
    viewModel: StudioViewModel
) {
    var volume by remember(item.id, item.volume) { mutableStateOf(item.volume) }
    var speed by remember(item.id, item.speed) { mutableStateOf(item.speed) }

    TuneSliderRow(
        label = "Voice Volume",
        value = volume,
        valueRange = 0f..2.0f,
        displayValue = "${(volume * 100).toInt()}%",
        onValueChange = {
            volume = it
            viewModel.commit(StudioAction.UpdateAudioItem(item.id, it, speed, 0L, 0L, item.audioDucking))
        }
    )

    TuneSliderRow(
        label = "Speaking Pace",
        value = speed,
        valueRange = 0.75f..1.5f,
        displayValue = "%.2fx".format(speed),
        onValueChange = {
            speed = it
            viewModel.commit(StudioAction.UpdateAudioItem(item.id, volume, it, 0L, 0L, item.audioDucking))
        }
    )
}

@Composable
private fun TuneSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(displayValue, color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF8B5CF6),
                activeTrackColor = Color(0xFF8B5CF6),
                inactiveTrackColor = Color(0xFF2E2A3E)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
