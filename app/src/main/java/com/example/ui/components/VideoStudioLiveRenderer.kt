package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.utils.TTSManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live Autonomous Video Viewport:
 * Renders the full video timeline with real-time animations, smooth Ken Burns motion,
 * cinematic scene transitions (Whip Pan, Zoom Punch, Chromatic Flash, Light Leak),
 * interactive scene motion graphics, and clean, lower-third kinetic word-by-word captions.
 * Completely free of cluttering debug text or unwanted placeholder graphs.
 */
@Composable
fun LiveAutonomousVideoViewport(
    project: VideoProjectData,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onGenerateFromPrompt: ((String) -> Unit)? = null,
    isGenerating: Boolean = false,
    generatingStatus: String = "",
    modifier: Modifier = Modifier
) {
    // If this is a blank project without generated script or clips, show the AI Director Studio Canvas
    if (project.script == null && project.clips.isEmpty()) {
        BlankProjectStudioView(
            projectName = project.projectName,
            isGenerating = isGenerating,
            generatingStatus = generatingStatus,
            onGenerateFromPrompt = onGenerateFromPrompt,
            modifier = modifier
        )
        return
    }

    val pos = project.currentPositionSeconds
    val totalSec = project.totalDurationSeconds.coerceAtLeast(15.0f)
    val sections: List<VideoScriptSection> = project.script?.sections ?: emptyList()
    if (sections.isEmpty() && project.clips.isEmpty()) {
        BlankProjectStudioView(
            projectName = project.projectName,
            isGenerating = isGenerating,
            generatingStatus = generatingStatus,
            onGenerateFromPrompt = onGenerateFromPrompt,
            modifier = modifier
        )
        return
    }

    // Calculate dynamic scene bounds from sections
    var accumulatedTime = 0f
    var activeSectionIndex = 0
    var activeSectionStart = 0f
    var activeSectionEnd = totalSec
    for (i in sections.indices) {
        val secDur = sections[i].estSeconds.coerceAtLeast(2.0f)
        val secEnd = accumulatedTime + secDur
        if (pos >= accumulatedTime && (pos < secEnd || i == sections.lastIndex)) {
            activeSectionIndex = i
            activeSectionStart = accumulatedTime
            activeSectionEnd = secEnd
            break
        }
        accumulatedTime = secEnd
    }
    val currentSec = sections.getOrElse(activeSectionIndex) { sections.first() }
    val sceneDuration = (activeSectionEnd - activeSectionStart).coerceAtLeast(0.5f)
    val progressInScene = ((pos - activeSectionStart) / sceneDuration).coerceIn(0f, 1f)

    // Transition detection: active during the first 0.35s and last 0.35s of each scene cut
    val isNearTransition = (pos - activeSectionStart < 0.35f && activeSectionIndex > 0) || (activeSectionEnd - pos < 0.35f)
    val transitionProgress = if (pos - activeSectionStart < 0.35f) {
        (pos - activeSectionStart) / 0.35f
    } else {
        (activeSectionEnd - pos) / 0.35f
    }

    // Smooth continuous Ken Burns camera zoom and subtle panning
    val cameraZoom = 1.0f + (progressInScene * 0.12f)
    val panX = kotlin.math.sin(pos * 1.5f) * 8f
    val panY = kotlin.math.cos(pos * 1.2f) * 6f

    val context = LocalContext.current

    // Real audio Sound Effects player
    val toneGen = remember {
        try {
            android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 65)
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { toneGen?.release() } catch (_: Exception) {}
        }
    }

    // Trigger Sound Effects on scene transition cuts
    LaunchedEffect(activeSectionIndex, isPlaying) {
        if (isPlaying && toneGen != null) {
            try {
                when (activeSectionIndex) {
                    0 -> toneGen.startTone(android.media.ToneGenerator.TONE_PROP_PROMPT, 140) // Whoosh / Hook impact
                    1 -> toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 120)    // Tension radar
                    2 -> toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 160)  // Bell Ding / Insight
                    3 -> toneGen.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 130)    // Pop / Checkmark
                    else -> toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150) // CTA bell
                }
            } catch (_: Exception) {}
        }
    }

    // Voiceover Narration via TTS
    val activeNarration = currentSec.narration
    LaunchedEffect(activeSectionIndex, isPlaying) {
        if (isPlaying && activeNarration.isNotBlank()) {
            TTSManager.init(context)
            TTSManager.speak(activeNarration, context)
        } else if (!isPlaying) {
            TTSManager.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            TTSManager.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onTogglePlay() }
    ) {
        // --- 1. Dynamic Atmosphere & Particle Canvas ---
        val bgGradients = listOf(
            listOf(Color(0xFF0F0C20), Color(0xFF1E1035), Color(0xFF070B18)), // Dark Studio
            listOf(Color(0xFF06141D), Color(0xFF0A2540), Color(0xFF05101A)), // Deep Cyber Cyan
            listOf(Color(0xFF150A21), Color(0xFF26103F), Color(0xFF0A0413)), // Neon Violet
            listOf(Color(0xFF0D1E18), Color(0xFF0F382A), Color(0xFF061510)), // Emerald Growth
            listOf(Color(0xFF1C0A1E), Color(0xFF330B3C), Color(0xFF100518))  // Magenta Energy
        )
        val activeBg = bgGradients[activeSectionIndex % bgGradients.size]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(cameraZoom)
                .offset(x = panX.dp, y = panY.dp)
                .background(Brush.verticalGradient(activeBg))
        )

        // --- 1B. Active User Media Clip (Video or Photo from Gallery) ---
        var clipStartAccum = 0f
        var activeClipAtPos: TimelineMediaClip? = null
        for (c in project.clips) {
            val clipEnd = clipStartAccum + c.durationSeconds
            if (pos in clipStartAccum..clipEnd) {
                activeClipAtPos = c
                break
            }
            clipStartAccum = clipEnd
        }

        if (activeClipAtPos?.uri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = activeClipAtPos.posX.dp, y = activeClipAtPos.posY.dp)
                    .scale(activeClipAtPos.scale)
                    .rotate(activeClipAtPos.rotation)
                    .alpha(activeClipAtPos.opacity)
            ) {
                AsyncImage(
                    model = activeClipAtPos.uri,
                    contentDescription = activeClipAtPos.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- 1C. Active Visual Asset / Sticker from Track 3 ---
        val activeVisualAsset = project.getResolvedVisualAssets().firstOrNull {
            pos in it.startSeconds..(it.startSeconds + it.durationSeconds)
        }
        if (activeVisualAsset?.imageUri != null) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.Center)
                    .offset(x = activeVisualAsset.posX.dp, y = activeVisualAsset.posY.dp)
                    .scale(activeVisualAsset.scale)
                    .rotate(activeVisualAsset.rotation)
                    .alpha(activeVisualAsset.opacity)
            ) {
                AsyncImage(
                    model = activeVisualAsset.imageUri,
                    contentDescription = activeVisualAsset.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Canvas for floating dynamic particles & ambient beat pulses
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Floating Bokeh Orbs
            val orb1X = width * 0.3f + kotlin.math.sin(pos * 0.8f) * (width * 0.2f)
            val orb1Y = height * 0.4f + kotlin.math.cos(pos * 0.6f) * (height * 0.15f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3500DAF3), Color.Transparent),
                    center = Offset(orb1X, orb1Y),
                    radius = width * 0.45f
                ),
                radius = width * 0.45f,
                center = Offset(orb1X, orb1Y)
            )

            val orb2X = width * 0.7f + kotlin.math.cos(pos * 0.7f) * (width * 0.2f)
            val orb2Y = height * 0.65f + kotlin.math.sin(pos * 0.9f) * (height * 0.15f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x309333EA), Color.Transparent),
                    center = Offset(orb2X, orb2Y),
                    radius = width * 0.4f
                ),
                radius = width * 0.4f,
                center = Offset(orb2X, orb2Y)
            )

            // Dynamic floating ambient particles
            for (i in 0 until 18) {
                val seed = i * 47.19f
                val px = (seed * 31f + pos * (15f + (i % 5) * 8f)) % width
                val py = (seed * 53f - pos * (20f + (i % 4) * 10f)) % height
                val normalizedY = if (py < 0) py + height else py
                val alpha = (0.2f + 0.5f * kotlin.math.abs(kotlin.math.sin(pos * 2f + i))).coerceIn(0.1f, 0.7f)
                val pRadius = (2.5f + (i % 4) * 1.5f).dp.toPx()
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFF00DAF3).copy(alpha = alpha) else Color(0xFFFFC44D).copy(alpha = alpha),
                    radius = pRadius,
                    center = Offset(px, normalizedY)
                )
            }
        }

        // --- 2. Dynamic High-Animation Center Motion Graphics (Clean & Elegant, No Giant Screen-Covering Text) ---
        val kind = currentSec.kind.lowercase()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                // Hook Scene: Kinetic Exploding Hook Badge with neon ring
                kind.contains("hook") -> {
                    val hookScale by animateFloatAsState(
                        targetValue = if (progressInScene < 0.85f) 1.0f else 0.95f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "hookScale"
                    )
                    val rotationDeg = kotlin.math.sin(pos * 3f) * 6f
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEF4444).copy(alpha = 0.25f),
                        border = BorderStroke(2.5.dp, Color(0xFFFF4D6D)),
                        shadowElevation = 16.dp,
                        modifier = Modifier
                            .size(76.dp)
                            .scale(hookScale)
                            .rotate(rotationDeg)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(42.dp))
                        }
                    }
                }

                // Problem / Tension Scene: Pulsing Radar Core
                kind.contains("problem") || kind.contains("story") -> {
                    val pulseScale = 1.0f + kotlin.math.sin(pos * 5f) * 0.08f
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFB703).copy(alpha = 0.2f),
                        border = BorderStroke(2.5.dp, Color(0xFFFFB703)),
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(38.dp))
                        }
                    }
                }

                // Value / Point / Climax Scene: Glossy 3D Emerald Checkmark Pop-in
                kind.contains("point") || kind.contains("value") || kind.contains("climax") || kind.contains("insight") -> {
                    val checkBounce by animateFloatAsState(
                        targetValue = 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "checkBounce"
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981),
                        shadowElevation = 22.dp,
                        border = BorderStroke(3.dp, Color(0xFF6EE7B7)),
                        modifier = Modifier
                            .size(76.dp)
                            .scale(checkBounce)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                        }
                    }
                }

                // CTA / Outro Scene: Subscribe & Bell Pop-up
                else -> {
                    val bellRing = kotlin.math.sin(pos * 10f) * 12f
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFEF4444),
                        shadowElevation = 18.dp,
                        border = BorderStroke(1.5.dp, Color.White),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp).rotate(bellRing)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SUBSCRIBE & FOLLOW",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Seamless Scene Transitions Overlay ---
        if (isNearTransition) {
            val transAlpha = ((1.0f - transitionProgress).coerceIn(0f, 1f) * 0.45f)
            // Whip Pan streak + Subtle Chromatic light beam
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = transAlpha),
                                Color(0xFF00DAF3).copy(alpha = transAlpha * 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // --- 4A. Telegram Catalog Asset Badge (Top Left) ---
        val activeClip = project.clips.getOrNull(activeSectionIndex) ?: project.clips.firstOrNull()
        val assetLabel = activeClip?.name?.ifBlank { "Telegram Catalog Scene" } ?: "Telegram Catalog Asset"
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.6f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📡 Telegram: ",
                    color = Color(0xFFC084FC),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = assetLabel.take(20),
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- 4B. Discreet Video Header (Sleek Timecode & Ratio) ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val curSecInt = pos.toInt()
                    val totSecInt = totalSec.toInt()
                    val timeStr = "%02d:%02d / %02d:%02d".format(curSecInt / 60, curSecInt % 60, totSecInt / 60, totSecInt % 60)
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (project.isProxyModeEnabled) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color(0xFF35E6A0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF35E6A0))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "PROXY 720P",
                            color = Color(0xFF35E6A0),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color(0x4000DAF3))
            ) {
                Text(
                    text = project.aspectRatio,
                    color = Color(0xFF00DAF3),
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // --- 5. Clean, Professional Lower-Third Captions (Shorts / Hormozi Style) ---
        val customTextItem = project.getResolvedTextSubtitles().firstOrNull {
            pos in it.startSeconds..(it.startSeconds + it.durationSeconds)
        }
        val captionTextToDisplay = customTextItem?.text ?: activeNarration
        val words = captionTextToDisplay.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            val wordsPerSec = (words.size.toFloat() / sceneDuration).coerceIn(1.5f, 4.0f)
            val currentWordIdx = ((pos - activeSectionStart) * wordsPerSec).toInt().coerceIn(0, words.lastIndex)
            
            // Window of 3-4 words centered on active word
            val windowSize = 4
            val startW = (currentWordIdx - 1).coerceAtLeast(0)
            val endW = (startW + windowSize).coerceAtMost(words.size)
            val displayWords = words.subList(startW, endW)

            val parsedTextColor = try {
                if (customTextItem != null) Color(android.graphics.Color.parseColor(customTextItem.colorHex)) else Color(0xFFFFE600)
            } catch (_: Exception) {
                Color(0xFFFFE600)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(x = (customTextItem?.posX ?: 0f).dp, y = (customTextItem?.posY ?: 0f).dp)
                    .scale(customTextItem?.scale ?: 1.0f)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (customTextItem?.hasBackgroundBox == true) Color.Black.copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.5.dp, if (customTextItem?.hasOutline == true) parsedTextColor else Color(0x6000DAF3)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayWords.forEachIndexed { idx, w ->
                            val absoluteWordIdx = startW + idx
                            val isActive = absoluteWordIdx == currentWordIdx
                            Text(
                                text = w,
                                color = if (isActive) parsedTextColor else Color.White,
                                fontSize = if (isActive) ((customTextItem?.fontSize ?: 22f) * 0.65f).sp else ((customTextItem?.fontSize ?: 22f) * 0.58f).sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                fontFamily = if (customTextItem?.fontFamily == "Mono") FontFamily.Monospace else FontFamily.SansSerif,
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .scale(if (isActive) 1.08f else 1.0f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Viewport Mode Segmented Selector Bar:
 * Allows user to switch between Live Video Viewport, Executable Python Code,
 * Structured Video Script, and Telegram Master Catalog.
 */
@Composable
fun VideoStudioViewportModeBar(
    currentMode: String,
    onSelectMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF14131A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0x30FFFFFF)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modes = listOf(
                Triple("VIDEO", "📺 Video", Color(0xFF00DAF3)),
                Triple("CODE", "💻 Code", Color(0xFF10B981)),
                Triple("SCRIPT", "📝 Script", Color(0xFFFFC44D)),
                Triple("CATALOG", "📦 Assets", Color(0xFFD1BCFF))
            )
            modes.forEach { (id, label, accent) ->
                val isSelected = currentMode == id
                Surface(
                    onClick = { onSelectMode(id) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) accent.copy(alpha = 0.25f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, accent) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFCCC3D9),
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live Python Code & Render Engine Component:
 * Displays standalone MoviePy/FFmpeg rendering code and allows user to run
 * a simulated video rendering process with real-time logs and output completion.
 */
@Composable
fun LivePythonCodeRenderer(
    project: VideoProjectData,
    isSimulatingRender: Boolean,
    renderProgress: Float,
    renderLogText: String,
    onStartRender: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pythonCode = remember(project) {
        if (!project.activeEditPlan?.why.isNullOrBlank()) {
            project.activeEditPlan!!.why!!
        } else {
            SaifAiEditingEngine.generatePythonCodeForProject(project)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(8.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("render_timeline.py", color = Color.White, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Copy Code Button
                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("render_timeline.py", pythonCode)
                        clipboard?.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "📋 Code copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Copy", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Run Code Engine Button (Level 5)
                Surface(
                    onClick = onStartRender,
                    enabled = !isSimulatingRender,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSimulatingRender) Color(0xFF065F46) else Color(0xFF10B981),
                    border = BorderStroke(1.dp, Color(0xFF34D399))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (isSimulatingRender) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                color = Color.White,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Rendering ${(renderProgress * 100).toInt()}%...",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Run Code Engine (Level 5)",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Render Logs Console when active
        if (renderLogText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black,
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text("⚙️ LIVE RENDER ENGINE LOGS:", color = Color(0xFF10B981), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(renderLogText, color = Color(0xFFE2E8F0), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp)
                }
            }
        }

        // Code Viewer Surface
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF030712),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    text = pythonCode,
                    color = Color(0xFF38BDF8),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * Live Structured Video Script View:
 * Displays the 4-phase High-Retention Script (Hook, Problem, Value, CTA).
 */
@Composable
fun LiveScriptView(
    script: VideoScript?,
    modifier: Modifier = Modifier
) {
    val sections = script?.sections ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0C20))
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📝 HIGH-RETENTION SCRIPT", color = Color(0xFFFFC44D), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("${script?.targetDurationSeconds?.toInt() ?: 15}s • 4 Phases", color = Color.White.copy(alpha = 0.6f), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
        }

        if (sections.isEmpty()) {
            Text("No script sections available.", color = Color.Gray, fontSize = 11.sp)
        } else {
            sections.forEachIndexed { idx, sec ->
                val badgeColor = when (sec.kind.lowercase()) {
                    "hook" -> Color(0xFFEF4444)
                    "story", "problem" -> Color(0xFF00DAF3)
                    "point", "value" -> Color(0xFF10B981)
                    else -> Color(0xFFA855F7)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF181528),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = badgeColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, badgeColor)
                            ) {
                                Text(
                                    text = "${sec.kind.uppercase()} (${sec.estSeconds}s)",
                                    color = badgeColor,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Mood: ${sec.emotion}",
                                color = Color(0xFFCCC3D9),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${sec.narration}\"",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🎬 Visual Hint: ${sec.visualHint}",
                            color = Color(0xFF00DAF3),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live Telegram Master Catalog View:
 * Displays exact items retrieved from `assets_catalog.json` for this video timeline.
 */
@Composable
fun LiveTelegramCatalogView(
    edlWhy: String?,
    clips: List<TimelineMediaClip>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val catalog = remember { TelegramAssetManager.loadMasterCatalog(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📦 MASTER TELEGRAM ASSETS (${catalog.size})", color = Color(0xFFD1BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("Cloud Sync: Active", color = Color(0xFF38BDF8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        catalog.take(15).forEach { item ->
            val typeColor = when (item.type.lowercase()) {
                "video" -> Color(0xFF00DAF3)
                "audio" -> Color(0xFF35E6A0)
                "image" -> Color(0xFFFFC44D)
                else -> Color(0xFFFF7AD9)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF131B2E),
                border = BorderStroke(1.dp, typeColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.assetId, color = typeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.2f)) {
                            Text(
                                text = "${item.type.uppercase()} • ${item.category}",
                                color = typeColor,
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(item.title, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                    if (item.subject.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(item.subject, color = Color(0xFFCCC3D9), fontSize = 8.5.sp)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("File ID: ${item.fileId}", color = Color.White.copy(alpha = 0.45f), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/**
 * Interactive Empty State Canvas for a New Project:
 * Allows the user to research any topic, generate a 5-scene viral script,
 * code animations, select Telegram assets, and assemble a complete video directly from the studio.
 */
@Composable
fun BlankProjectStudioView(
    projectName: String,
    isGenerating: Boolean,
    generatingStatus: String,
    onGenerateFromPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var promptInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F0C20), Color(0xFF131124), Color(0xFF0A0815))
                )
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Icon
            Surface(
                shape = CircleShape,
                color = Color(0xFF7B2BFF).copy(alpha = 0.25f),
                border = BorderStroke(1.5.dp, Color(0xFF00DAF3)),
                shadowElevation = 12.dp,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF00DAF3),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Autonomous AI Director",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Enter any topic or prompt in Hindi or English.\nAI will research the topic, write a 5-scene viral script, code animations, and render your video.",
                color = Color(0xFFCCC3D9),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Text input
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                placeholder = {
                    Text(
                        "e.g. 5 AI tools jo 2026 me sab badal denge / YouTube growth secrets",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                },
                enabled = !isGenerating,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00DAF3),
                    unfocusedBorderColor = Color(0x40FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF181528),
                    unfocusedContainerColor = Color(0xFF181528)
                ),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick suggestion chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val suggestions = listOf(
                    "🔥 5 AI Tools in 2026 (Hindi)",
                    "📈 YouTube Channel Growth Secret",
                    "🧠 Quantum Computing Explained",
                    "💰 Crypto vs Stocks for Beginners"
                )
                suggestions.forEach { suggestion ->
                    Surface(
                        onClick = { promptInput = suggestion },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF231D38),
                        border = BorderStroke(0.5.dp, Color(0x5000DAF3))
                    ) {
                        Text(
                            text = suggestion,
                            color = Color(0xFFE2E8F0),
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isGenerating) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F1B33),
                    border = BorderStroke(1.dp, Color(0xFF00DAF3).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF00DAF3),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = generatingStatus.ifBlank { "Analyzing prompt & researching..." },
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        val p = promptInput.trim().ifBlank { projectName.ifBlank { "Viral AI Video" } }
                        if (p.isNotBlank()) {
                            onGenerateFromPrompt?.invoke(p)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Research & Generate Video",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
