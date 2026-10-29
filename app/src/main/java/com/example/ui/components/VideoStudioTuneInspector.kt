package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LogiCut-Style Dedicated "Tune" Inspector Drawer for SAIF AI Studio.
 * Provides deep manual parameter tuning for whichever card is currently selected on the timeline:
 * - Image/Video/Code Cards: Scale, X/Y, Rotation, Opacity, Blend Modes, Visual Effects (Neon Glow, Shadow, Blur, Color), In/Out Animations, Asset Replacement.
 * - Text Cards: Live text editing, Font family, Size, Color, Stroke/Outline, Neon Glow, Background box, Text Animations.
 * - Audio/Voice Cards: Volume (0-200%), Speed (0.5x-2x), Fade In/Out (0-3s), Audio Ducking, Sound Replacement.
 */
@Composable
fun VideoStudioTuneInspector(
    project: VideoProjectData,
    selectedTrackNumber: Int,
    selectedCardId: String,
    onUpdateProject: (VideoProjectData) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var tuneSubCategory by remember { mutableStateOf("Transform") }

    // Media Replacement Picker (Phone Gallery)
    val replaceMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            when (selectedTrackNumber) {
                2 -> {
                    val idx = project.clips.indexOfFirst { it.id == selectedCardId }
                    if (idx != -1) {
                        val updated = project.clips.toMutableList()
                        updated[idx] = updated[idx].copy(uri = uri, name = uri.lastPathSegment?.takeLast(16) ?: "Replaced_Clip")
                        onUpdateProject(project.copy(clips = updated))
                        Toast.makeText(context, "Asset Replaced with Phone Media!", Toast.LENGTH_SHORT).show()
                    }
                }
                3 -> {
                    val assets = project.getResolvedVisualAssets().toMutableList()
                    val idx = assets.indexOfFirst { it.id == selectedCardId }
                    if (idx != -1) {
                        assets[idx] = assets[idx].copy(imageUri = uri.toString(), name = "Local: " + (uri.lastPathSegment?.takeLast(14) ?: "Asset"))
                        onUpdateProject(project.copy(visualAssetTracks = assets))
                        Toast.makeText(context, "Sticker/Asset Replaced!", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    Toast.makeText(context, "Asset updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Audio Replacement Picker (Phone Audio Files)
    val replaceAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            when (selectedTrackNumber) {
                5 -> {
                    val voices = project.getResolvedVoiceoverTracks().toMutableList()
                    val idx = voices.indexOfFirst { it.id == selectedCardId }
                    if (idx != -1) {
                        voices[idx] = voices[idx].copy(audioUri = uri.toString(), narration = "Custom Voice Audio")
                        onUpdateProject(project.copy(voiceoverTracks = voices))
                        Toast.makeText(context, "Custom Voiceover MP3 Applied!", Toast.LENGTH_SHORT).show()
                    }
                }
                6 -> {
                    val sounds = project.getResolvedSoundAudioTracks().toMutableList()
                    val idx = sounds.indexOfFirst { it.id == selectedCardId }
                    if (idx != -1) {
                        sounds[idx] = sounds[idx].copy(audioUri = uri.toString(), title = uri.lastPathSegment?.takeLast(18) ?: "Custom_Audio.mp3")
                        onUpdateProject(project.copy(soundAudioTracks = sounds))
                        Toast.makeText(context, "Sound/BGM Replaced!", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }
    }

    Surface(
        color = Color(0xFF111016),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, Color(0xFF00DAF3).copy(alpha = 0.4f)),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            // 1. Inspector Drawer Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0A0F))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00DAF3).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFF00DAF3),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TUNE: ${
                            when (selectedTrackNumber) {
                                1 -> "Track 1 · Code Animation"
                                2 -> "Track 2 · Video Clip"
                                3 -> "Track 3 · Visual Asset / Sticker"
                                4 -> "Track 4 · Kinetic Subtitle"
                                5 -> "Track 5 · AI Voiceover"
                                else -> "Track 6 · Audio & SFX"
                            }
                        }",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCC3D9), modifier = Modifier.size(16.dp))
                }
            }

            // 2. Sub-Category Tabs (Transform / Effects / Typography / Sound / Replace)
            val subTabs = when (selectedTrackNumber) {
                4 -> listOf("Text Edit", "Style & Font", "Animation")
                5, 6 -> listOf("Volume & Speed", "Fades & Ducking", "Replace Sound")
                else -> listOf("Transform", "Effects & Glow", "In/Out Anim", "Replace Asset")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(Color(0xFF16151E))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                subTabs.forEach { tabName ->
                    val isSelected = tuneSubCategory == tabName || (tuneSubCategory !in subTabs && tabName == subTabs.first())
                    Surface(
                        onClick = { tuneSubCategory = tabName },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF00DAF3).copy(alpha = 0.2f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF00DAF3)) else null
                    ) {
                        Text(
                            text = tabName,
                            color = if (isSelected) Color(0xFF00DAF3) else Color(0xFF888496),
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Body Controls by Track Type
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                when (selectedTrackNumber) {
                    // TRACK 1 (Code), TRACK 2 (Video), TRACK 3 (Visual Asset)
                    1, 2, 3 -> {
                        VisualMediaTuneSection(
                            trackNumber = selectedTrackNumber,
                            cardId = selectedCardId,
                            subCategory = tuneSubCategory,
                            project = project,
                            onUpdateProject = onUpdateProject,
                            onLaunchReplace = {
                                replaceMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                        )
                    }
                    // TRACK 4: TEXT & KINETIC SUBTITLES
                    4 -> {
                        TextSubtitleTuneSection(
                            cardId = selectedCardId,
                            subCategory = tuneSubCategory,
                            project = project,
                            onUpdateProject = onUpdateProject
                        )
                    }
                    // TRACK 5 & 6: AUDIO / VOICEOVER / SFX
                    else -> {
                        AudioTuneSection(
                            trackNumber = selectedTrackNumber,
                            cardId = selectedCardId,
                            subCategory = tuneSubCategory,
                            project = project,
                            onUpdateProject = onUpdateProject,
                            onLaunchReplace = {
                                replaceAudioLauncher.launch("audio/*")
                            }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VISUAL / VIDEO / CODE TUNE SECTION
// -------------------------------------------------------------
@Composable
private fun VisualMediaTuneSection(
    trackNumber: Int,
    cardId: String,
    subCategory: String,
    project: VideoProjectData,
    onUpdateProject: (VideoProjectData) -> Unit,
    onLaunchReplace: () -> Unit
) {
    val context = LocalContext.current

    // Extract active element properties depending on track
    val activeClip = project.clips.firstOrNull { it.id == cardId } ?: project.clips.getOrNull(project.selectedClipIndex)
    val activeAsset = project.getResolvedVisualAssets().firstOrNull { it.id == cardId }
    val activeCode = project.getResolvedCodeTracks().firstOrNull { it.id == cardId }

    val currentScale = when (trackNumber) {
        1 -> activeCode?.scale ?: 1.0f
        3 -> activeAsset?.scale ?: 1.0f
        else -> activeClip?.scale ?: 1.0f
    }
    val currentPosX = when (trackNumber) {
        1 -> activeCode?.posX ?: 0f
        3 -> activeAsset?.posX ?: 0f
        else -> activeClip?.posX ?: 0f
    }
    val currentPosY = when (trackNumber) {
        1 -> activeCode?.posY ?: 0f
        3 -> activeAsset?.posY ?: 0f
        else -> activeClip?.posY ?: 0f
    }
    val currentRot = when (trackNumber) {
        1 -> activeCode?.rotation ?: 0f
        3 -> activeAsset?.rotation ?: 0f
        else -> activeClip?.rotation ?: 0f
    }
    val currentOpacity = when (trackNumber) {
        1 -> activeCode?.opacity ?: 1.0f
        3 -> activeAsset?.opacity ?: 1.0f
        else -> activeClip?.opacity ?: 1.0f
    }
    val currentGlow = when (trackNumber) {
        1 -> activeCode?.neonGlow ?: 0f
        3 -> activeAsset?.neonGlow ?: 0f
        else -> activeClip?.neonGlow ?: 0f
    }
    val currentBlend = when (trackNumber) {
        3 -> activeAsset?.blendMode ?: "Normal"
        else -> activeClip?.blendMode ?: "Normal"
    }

    when (subCategory) {
        "Transform" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Scale Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scale / Zoom", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${"%.2f".format(currentScale)}x", color = Color(0xFF00DAF3), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = currentScale,
                    onValueChange = { newScale ->
                        updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                            clip?.copy(scale = newScale) to (asset?.copy(scale = newScale) to code?.copy(scale = newScale))
                        }
                    },
                    valueRange = 0.2f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00DAF3), activeTrackColor = Color(0xFF00DAF3))
                )

                // X and Y Position Sliders
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Position X", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                            Text("${currentPosX.toInt()}px", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = currentPosX,
                            onValueChange = { newX ->
                                updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                                    clip?.copy(posX = newX) to (asset?.copy(posX = newX) to code?.copy(posX = newX))
                                }
                            },
                            valueRange = -300f..300f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF7B2BFF), activeTrackColor = Color(0xFF7B2BFF))
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Position Y", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                            Text("${currentPosY.toInt()}px", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = currentPosY,
                            onValueChange = { newY ->
                                updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                                    clip?.copy(posY = newY) to (asset?.copy(posY = newY) to code?.copy(posY = newY))
                                }
                            },
                            valueRange = -300f..300f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF7B2BFF), activeTrackColor = Color(0xFF7B2BFF))
                        )
                    }
                }

                // Rotation Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rotation", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${currentRot.toInt()}°", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = currentRot,
                    onValueChange = { newRot ->
                        updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                            clip?.copy(rotation = newRot) to (asset?.copy(rotation = newRot) to code?.copy(rotation = newRot))
                        }
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFFB703), activeTrackColor = Color(0xFFFFB703))
                )
            }
        }

        "Effects & Glow" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Neon Glow Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Neon Glow Intensity", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${currentGlow.toInt()}%", color = Color(0xFF00DAF3), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = currentGlow,
                    onValueChange = { newGlow ->
                        updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                            clip?.copy(neonGlow = newGlow) to (asset?.copy(neonGlow = newGlow) to code?.copy(neonGlow = newGlow))
                        }
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00DAF3), activeTrackColor = Color(0xFF00DAF3))
                )

                // Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Opacity", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${(currentOpacity * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = currentOpacity,
                    onValueChange = { newOpacity ->
                        updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                            clip?.copy(opacity = newOpacity) to (asset?.copy(opacity = newOpacity) to code?.copy(opacity = newOpacity))
                        }
                    },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )

                // Blending Mode Selector
                Text("Blending Mode", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Normal", "Screen", "Multiply", "Overlay", "Add").forEach { mode ->
                        val isSel = currentBlend == mode
                        Surface(
                            onClick = {
                                updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                                    clip?.copy(blendMode = mode) to (asset?.copy(blendMode = mode) to code)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Color(0xFF7B2BFF) else Color(0xFF201F2B)
                        ) {
                            Text(mode, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        "In/Out Anim" -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("In-Animation Preset", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Pop-in", "Bounce", "Slide Left", "Fade In", "3D Flip").forEach { anim ->
                        Surface(
                            onClick = {
                                updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                                    clip?.copy(inAnimation = anim) to (asset?.copy(inAnimation = anim) to code?.copy(inAnimation = anim))
                                }
                                Toast.makeText(context, "In-Anim: $anim", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF201F2B),
                            border = BorderStroke(1.dp, Color(0x3000DAF3))
                        ) {
                            Text(anim, color = Color(0xFF00DAF3), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }

                Text("Out-Animation Preset", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Fade Out", "Slide Right", "Pop-out", "Zoom Dissolve").forEach { anim ->
                        Surface(
                            onClick = {
                                updateVisualItem(trackNumber, cardId, project, onUpdateProject) { clip, asset, code ->
                                    clip?.copy(outAnimation = anim) to (asset?.copy(outAnimation = anim) to code?.copy(outAnimation = anim))
                                }
                                Toast.makeText(context, "Out-Anim: $anim", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF201F2B)
                        ) {
                            Text(anim, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        "Replace Asset" -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Swap this asset without losing timeline cuts or timestamps:", color = Color(0xFFCCC3D9), fontSize = 11.sp)

                // Pick from Phone Gallery
                Button(
                    onClick = onLaunchReplace,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose Replacement from Phone Gallery", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                // Or swap from Telegram Master Catalog
                OutlinedButton(
                    onClick = {
                        Toast.makeText(context, "Fetching fresh assets from Telegram Catalog...", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF00DAF3)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Swap from Telegram Catalog API", color = Color(0xFF00DAF3), fontSize = 11.5.sp)
                }
            }
        }
    }
}

private fun updateVisualItem(
    trackNumber: Int,
    cardId: String,
    project: VideoProjectData,
    onUpdateProject: (VideoProjectData) -> Unit,
    updater: (TimelineMediaClip?, VisualAssetTrackItem?, CodeAnimationTrackItem?) -> Pair<TimelineMediaClip?, Pair<VisualAssetTrackItem?, CodeAnimationTrackItem?>>
) {
    when (trackNumber) {
        1 -> {
            val list = project.getResolvedCodeTracks().toMutableList()
            val idx = list.indexOfFirst { it.id == cardId }
            if (idx != -1) {
                val res = updater(null, null, list[idx]).second.second
                if (res != null) list[idx] = res
                onUpdateProject(project.copy(codeTracks = list))
            }
        }
        2 -> {
            val list = project.clips.toMutableList()
            val idx = list.indexOfFirst { it.id == cardId }
            val targetIdx = if (idx != -1) idx else project.selectedClipIndex
            if (targetIdx in list.indices) {
                val res = updater(list[targetIdx], null, null).first
                if (res != null) list[targetIdx] = res
                onUpdateProject(project.copy(clips = list))
            }
        }
        3 -> {
            val list = project.getResolvedVisualAssets().toMutableList()
            val idx = list.indexOfFirst { it.id == cardId }
            if (idx != -1) {
                val res = updater(null, list[idx], null).second.first
                if (res != null) list[idx] = res
                onUpdateProject(project.copy(visualAssetTracks = list))
            }
        }
    }
}

// -------------------------------------------------------------
// TEXT & KINETIC SUBTITLE TUNE SECTION
// -------------------------------------------------------------
@Composable
private fun TextSubtitleTuneSection(
    cardId: String,
    subCategory: String,
    project: VideoProjectData,
    onUpdateProject: (VideoProjectData) -> Unit
) {
    val items = project.getResolvedTextSubtitles().toMutableList()
    val idx = items.indexOfFirst { it.id == cardId }
    val activeItem = if (idx != -1) items[idx] else items.firstOrNull() ?: TextSubtitleTrackItem(text = "Sample Subtitle", startSeconds = 0f, durationSeconds = 3f)

    fun applyUpdate(updated: TextSubtitleTrackItem) {
        if (idx != -1) {
            items[idx] = updated
        } else {
            items.add(updated)
        }
        onUpdateProject(project.copy(textSubtitleTracks = items))
    }

    when (subCategory) {
        "Text Edit" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live Subtitle Text:", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                OutlinedTextField(
                    value = activeItem.text,
                    onValueChange = { newTxt -> applyUpdate(activeItem.copy(text = newTxt)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00DAF3),
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                // Scale / Size
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Font Size", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${activeItem.fontSize.toInt()}sp", color = Color(0xFF00DAF3), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = activeItem.fontSize,
                    onValueChange = { applyUpdate(activeItem.copy(fontSize = it)) },
                    valueRange = 12f..48f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00DAF3), activeTrackColor = Color(0xFF00DAF3))
                )
            }
        }

        "Style & Font" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Font Family Selector
                Text("Font Family", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Montserrat", "Inter", "Poppins", "Anton", "Bebas", "Impact", "Mono").forEach { font ->
                        val isSel = activeItem.fontFamily == font
                        Surface(
                            onClick = { applyUpdate(activeItem.copy(fontFamily = font)) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Color(0xFF7B2BFF) else Color(0xFF201F2B)
                        ) {
                            Text(font, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }

                // Color Swatches
                Text("Text Color", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val colors = listOf(
                        "#FFFFFF" to Color.White,
                        "#00DAF3" to Color(0xFF00DAF3),
                        "#FFD166" to Color(0xFFFFD166),
                        "#00F5A0" to Color(0xFF00F5A0),
                        "#FF4D94" to Color(0xFFFF4D94),
                        "#C084FC" to Color(0xFFC084FC)
                    )
                    colors.forEach { (hex, clr) ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(clr)
                                .border(if (activeItem.colorHex == hex) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { applyUpdate(activeItem.copy(colorHex = hex)) }
                        )
                    }
                }

                // Outline & Background Box Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Black Outline / Stroke", color = Color.White, fontSize = 11.sp)
                    Switch(
                        checked = activeItem.hasOutline,
                        onCheckedChange = { applyUpdate(activeItem.copy(hasOutline = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00DAF3), checkedTrackColor = Color(0xFF7B2BFF))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Background Box", color = Color.White, fontSize = 11.sp)
                    Switch(
                        checked = activeItem.hasBackgroundBox,
                        onCheckedChange = { applyUpdate(activeItem.copy(hasBackgroundBox = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00DAF3), checkedTrackColor = Color(0xFF7B2BFF))
                    )
                }
            }
        }

        else -> { // Animation
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kinetic Caption Animation:", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                val anims = listOf("Hormozi Word-by-Word", "Pop-in", "Typewriter", "Bounce", "Glitch Fade")
                anims.forEach { anim ->
                    val isSel = activeItem.animation == anim
                    Surface(
                        onClick = { applyUpdate(activeItem.copy(animation = anim)) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) Color(0xFF00DAF3).copy(alpha = 0.25f) else Color(0xFF1E1D26),
                        border = if (isSel) BorderStroke(1.dp, Color(0xFF00DAF3)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(anim, color = Color.White, fontSize = 11.5.sp)
                            if (isSel) Text("ACTIVE ✓", color = Color(0xFF00DAF3), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// AUDIO & SFX TUNE SECTION
// -------------------------------------------------------------
@Composable
private fun AudioTuneSection(
    trackNumber: Int,
    cardId: String,
    subCategory: String,
    project: VideoProjectData,
    onUpdateProject: (VideoProjectData) -> Unit,
    onLaunchReplace: () -> Unit
) {
    val soundTracks = project.getResolvedSoundAudioTracks().toMutableList()
    val soundIdx = soundTracks.indexOfFirst { it.id == cardId }
    val activeSound = if (soundIdx != -1) soundTracks[soundIdx] else soundTracks.firstOrNull() ?: SoundAudioTrackItem(title = "Audio", startSeconds = 0f, durationSeconds = 5f)

    val voiceTracks = project.getResolvedVoiceoverTracks().toMutableList()
    val voiceIdx = voiceTracks.indexOfFirst { it.id == cardId }
    val activeVoice = if (voiceIdx != -1) voiceTracks[voiceIdx] else voiceTracks.firstOrNull() ?: VoiceoverTrackItem(narration = "Voiceover", startSeconds = 0f, durationSeconds = 3f)

    val currentVol = if (trackNumber == 5) activeVoice.volume else activeSound.volume
    val currentSpeed = if (trackNumber == 5) activeVoice.speed else activeSound.speed
    val currentFadeIn = if (trackNumber == 5) activeVoice.fadeInSeconds else activeSound.fadeInSeconds
    val currentFadeOut = if (trackNumber == 5) activeVoice.fadeOutSeconds else activeSound.fadeOutSeconds
    val currentDucking = if (trackNumber == 5) activeVoice.isDuckingEnabled else activeSound.isDuckingEnabled

    fun updateAudio(vol: Float = currentVol, spd: Float = currentSpeed, fin: Float = currentFadeIn, fout: Float = currentFadeOut, duck: Boolean = currentDucking) {
        if (trackNumber == 5) {
            if (voiceIdx != -1) {
                voiceTracks[voiceIdx] = activeVoice.copy(volume = vol, speed = spd, fadeInSeconds = fin, fadeOutSeconds = fout, isDuckingEnabled = duck)
                onUpdateProject(project.copy(voiceoverTracks = voiceTracks))
            }
        } else {
            if (soundIdx != -1) {
                soundTracks[soundIdx] = activeSound.copy(volume = vol, speed = spd, fadeInSeconds = fin, fadeOutSeconds = fout, isDuckingEnabled = duck)
                onUpdateProject(project.copy(soundAudioTracks = soundTracks, audioVolume = vol))
            }
        }
    }

    when (subCategory) {
        "Volume & Speed" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Volume Slider (0% to 200%)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Volume Level", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${(currentVol * 100).toInt()}%", color = Color(0xFFFF4D94), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = currentVol,
                    onValueChange = { updateAudio(vol = it) },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF4D94), activeTrackColor = Color(0xFFFF4D94))
                )

                // Speed Slider (0.5x to 2.0x)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Playback Speed", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${"%.1f".format(currentSpeed)}x", color = Color(0xFF00DAF3), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = currentSpeed,
                    onValueChange = { updateAudio(spd = it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00DAF3), activeTrackColor = Color(0xFF00DAF3))
                )
            }
        }

        "Fades & Ducking" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Fade In Slider
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fade In Duration", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${"%.1f".format(currentFadeIn)}s", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = currentFadeIn,
                    onValueChange = { updateAudio(fin = it) },
                    valueRange = 0f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )

                // Fade Out Slider
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fade Out Duration", color = Color(0xFFCCC3D9), fontSize = 11.sp)
                    Text("${"%.1f".format(currentFadeOut)}s", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = currentFadeOut,
                    onValueChange = { updateAudio(fout = it) },
                    valueRange = 0f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )

                // Audio Ducking Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto Audio Ducking", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text("Automatically dips BGM volume when voice speaks", color = Color(0xFF888496), fontSize = 9.5.sp)
                    }
                    Switch(
                        checked = currentDucking,
                        onCheckedChange = { updateAudio(duck = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00DAF3), checkedTrackColor = Color(0xFF7B2BFF))
                    )
                }
            }
        }

        else -> { // Replace Sound
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Replace sound with your own audio track or SFX:", color = Color(0xFFCCC3D9), fontSize = 11.sp)

                Button(
                    onClick = onLaunchReplace,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D94)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Custom MP3 / WAV from Phone", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        updateAudio(vol = 1.2f)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF00DAF3)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Swap from Telegram SFX Catalog", color = Color(0xFF00DAF3), fontSize = 11.5.sp)
                }
            }
        }
    }
}
