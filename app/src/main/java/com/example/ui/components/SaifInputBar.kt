package com.example.ui.components

import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.focus.onFocusChanged
import com.example.ui.theme.LiquidGlassDefaults
import com.example.ui.theme.liquidBounceClickable


@Composable
fun SaifInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    onStop: () -> Unit,
    onVoiceRecorded: (java.io.File) -> Unit,
    onStartLiveVoice: () -> Unit,
    onGalleryClick: () -> Unit,
    onFileClick: () -> Unit,
    onVideoClick: () -> Unit,
    onCameraClick: () -> Unit,
    onVoiceListeningStateChange: (Boolean, Float) -> Unit = { _, _ -> },
    stopVoiceListeningTrigger: Int = 0,
    hasAttachments: Boolean = false,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val context = androidx.compose.ui.platform.LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var rmsValue by remember { mutableStateOf(0f) }
    var textBeforeListening by remember { mutableStateOf("") }

    val directSpeechEngine = remember { DirectSpeechInputEngine(context) }

    val stopListeningNow = {
        if (isListening) {
            directSpeechEngine.stopListening()
            isListening = false
            rmsValue = 0f
            onVoiceListeningStateChange(false, 0f)
        }
    }

    LaunchedEffect(stopVoiceListeningTrigger) {
        if (stopVoiceListeningTrigger > 0 && isListening) {
            stopListeningNow()
        }
    }

    val startListening = {
        textBeforeListening = input
        directSpeechEngine.startListening(
            onRmsChanged = { rms ->
                rmsValue = rms
                onVoiceListeningStateChange(true, rms)
            },
            onInterimResult = { partialText ->
                val newText = if (textBeforeListening.isBlank()) partialText else "$textBeforeListening $partialText"
                onInputChange(newText)
            },
            onFinalResult = { finalText ->
                val newText = if (textBeforeListening.isBlank()) finalText else "$textBeforeListening $finalText"
                onInputChange(newText)
            },
            onStateChanged = { listening ->
                isListening = listening
                if (!listening) rmsValue = 0f
                onVoiceListeningStateChange(listening, if (listening) rmsValue else 0f)
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            directSpeechEngine.release()
        }
    }

    val hasText = input.trim().isNotEmpty()
    val canSend = hasText || hasAttachments
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Attachment Menu using Popup to allow dismissal by clicking outside
        if (showAttachmentMenu) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = androidx.compose.ui.unit.IntOffset(16, -180),
                onDismissRequest = { showAttachmentMenu = false },
                properties = PopupProperties(focusable = true)
            ) {
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }
                val scale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.8f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "popupScale"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(200),
                    label = "popupAlpha"
                )
                val isLight = com.example.ui.theme.SaifTheme.colors.isLight
                Box(
                    modifier = Modifier
                        .width(168.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isLight) Color(0xFFFFFFFF) else Color(0xFF131826)
                        )
                        .border(1.dp, if (isLight) Color(0xFFE2E8F0) else Color(0xFF1E293B), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AttachmentOption(icon = Icons.Outlined.Image, color = Color(0xFF3B82F6), label = "Gallery", onClick = { showAttachmentMenu = false; onGalleryClick() })
                        AttachmentOption(icon = Icons.AutoMirrored.Outlined.InsertDriveFile, color = Color(0xFFA855F7), label = "File", onClick = { showAttachmentMenu = false; onFileClick() })
                        AttachmentOption(icon = Icons.Outlined.Videocam, color = Color(0xFFEF4444), label = "Video", onClick = { showAttachmentMenu = false; onVideoClick() })
                    }
                }
            }
        }
        
        val isLightMode = com.example.ui.theme.SaifTheme.colors.isLight
        val hasText = input.isNotEmpty()

        // Aura colorful effect alpha: fades in smoothly over 1.5 seconds when typing any text (e.g. "hii"),
        // and fades out smoothly over 1.5 seconds when text is cleared!
        val auraAlpha by animateFloatAsState(
            targetValue = if (hasText) 1f else 0f,
            animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
            label = "chatbox_aura_alpha"
        )

        // Shifting aura color cycle animation
        val infiniteTransition = rememberInfiniteTransition(label = "chatbox_aura_transition")
        val auraOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1200f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "chatbox_aura_offset"
        )

        val auraColors = listOf(
            Color(0xFF00E5FF), // Electric Cyan
            Color(0xFF3B82F6), // Vibrant Blue
            Color(0xFF8B5CF6), // Neon Purple
            Color(0xFFEC4899), // Magenta Pink
            Color(0xFFF59E0B), // Glowing Amber
            Color(0xFF10B981), // Emerald Mint
            Color(0xFF00E5FF)  // Wrap Cyan
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Ambient outer aura glow around the chatbox borders (when text is present / typing)
            if (auraAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            val brush = Brush.linearGradient(
                                colors = auraColors,
                                start = Offset(auraOffset, 0f),
                                end = Offset(size.width + auraOffset, size.height)
                            )
                            // Outer diffused aura glow
                            drawRoundRect(
                                brush = brush,
                                alpha = (if (isLightMode) 0.28f else 0.42f) * auraAlpha,
                                topLeft = Offset(-3.5.dp.toPx(), -3.5.dp.toPx()),
                                size = Size(size.width + 7.dp.toPx(), size.height + 7.dp.toPx()),
                                cornerRadius = CornerRadius(31.5.dp.toPx(), 31.5.dp.toPx()),
                                style = Stroke(width = 4.dp.toPx())
                            )
                            // Secondary soft dispersion aura
                            drawRoundRect(
                                brush = brush,
                                alpha = (if (isLightMode) 0.12f else 0.20f) * auraAlpha,
                                topLeft = Offset(-7.dp.toPx(), -7.dp.toPx()),
                                size = Size(size.width + 14.dp.toPx(), size.height + 14.dp.toPx()),
                                cornerRadius = CornerRadius(35.dp.toPx(), 35.dp.toPx()),
                                style = Stroke(width = 7.dp.toPx())
                            )
                        }
                )
            }

            // Main Chatbox Capsule Surface with dynamic border
            val normalBorderColor = if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF131826),
                shadowElevation = if (isLightMode) 3.dp else 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val strokePx = 1.dp.toPx()
                        val cornerPx = 28.dp.toPx()
                        // Base theme border
                        drawRoundRect(
                            color = normalBorderColor,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = strokePx)
                        )
                        // Dynamic shifting aura border drawn on top with 1.5s smooth alpha transition
                        if (auraAlpha > 0.001f) {
                            val auraBorderBrush = Brush.linearGradient(
                                colors = auraColors,
                                start = Offset(auraOffset, 0f),
                                end = Offset(size.width + auraOffset, size.height)
                            )
                            drawRoundRect(
                                brush = auraBorderBrush,
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                alpha = auraAlpha,
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Left Icons
                    val clipInteraction = remember { MutableInteractionSource() }
                    val isClipPressed by clipInteraction.collectIsPressedAsState()
                    val clipScale by animateFloatAsState(
                        targetValue = if (isClipPressed) 0.7f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
                        label = "ClipScale"
                    )
                    val clipGlow by animateFloatAsState(if (isClipPressed) 0.3f else 0f, label = "ClipGlow")

                    Box(modifier = Modifier.padding(bottom = 4.dp).scale(clipScale).clickable(interactionSource = clipInteraction, indication = null) {
                        stopListeningNow()
                        showAttachmentMenu = !showAttachmentMenu
                    }) {
                        Box(modifier = Modifier.matchParentSize().background(Color(0xFF8B5CF6).copy(alpha = clipGlow), CircleShape))
                        IconButton(onClick = {
                            stopListeningNow()
                            showAttachmentMenu = !showAttachmentMenu
                        }, interactionSource = clipInteraction) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach File",
                                tint = com.example.ui.theme.SaifTheme.colors.textSecondary
                            )
                        }
                    }
                    
                    val camInteraction = remember { MutableInteractionSource() }
                    val isCamPressed by camInteraction.collectIsPressedAsState()
                    val camScale by animateFloatAsState(
                        targetValue = if (isCamPressed) 0.6f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "CamScale"
                    )
                    
                    Box(modifier = Modifier.padding(bottom = 4.dp).scale(camScale).clickable(interactionSource = camInteraction, indication = null) {
                        stopListeningNow()
                        onCameraClick()
                    }) {
                        IconButton(onClick = {
                            stopListeningNow()
                            onCameraClick()
                        }, interactionSource = camInteraction) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "Camera",
                                tint = com.example.ui.theme.SaifTheme.colors.textSecondary
                            )
                        }
                    }

                    // Text Input Full Width in the middle
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                            .heightIn(min = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) {
                            if (isListening) {
                                val listeningRmsNorm = (rmsValue / 10f).coerceIn(0f, 1f)
                                val pulseDotScale by animateFloatAsState(
                                    targetValue = 1f + (listeningRmsNorm * 0.5f),
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    label = "pulseDotScale"
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .scale(pulseDotScale)
                                            .background(Color(0xFFD946EF), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Listening...",
                                        color = Color(0xFFA855F7),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Text(
                                    text = "Message SAIF AI...",
                                    color = com.example.ui.theme.SaifTheme.colors.textSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { newVal ->
                                if (isListening) {
                                    stopListeningNow()
                                }
                                onInputChange(newVal)
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (canSend) {
                                    stopListeningNow()
                                    focusManager.clearFocus()
                                    onSend()
                                }
                            }),
                            textStyle = TextStyle(
                                color = com.example.ui.theme.SaifTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            ),
                            maxLines = 6,
                            cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && isListening) {
                                        stopListeningNow()
                                    }
                                }
                        )
                    }

                    // Right Icons
                    if (!isGenerating) {
                        val micInteraction = remember { MutableInteractionSource() }
                        val isMicPressed by micInteraction.collectIsPressedAsState()
                        val micScale by animateFloatAsState(
                            targetValue = if (isMicPressed) 0.92f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                            label = "MicScale"
                        )
                        val micTint by animateColorAsState(
                            targetValue = if (isListening) Color(0xFFD946EF) else Color(0xFFA855F7),
                            animationSpec = tween(durationMillis = 200),
                            label = "micTint"
                        )

                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 4.dp)
                                .scale(micScale),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    if (isListening) {
                                        stopListeningNow()
                                    } else {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            startListening()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                interactionSource = micInteraction
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = "Microphone",
                                    tint = micTint
                                )
                            }
                        }
                    }

                    if (isGenerating) {

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .clickable { onStop() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        val actionInteraction = remember { MutableInteractionSource() }
                        val isActionPressed by actionInteraction.collectIsPressedAsState()
                        val actionScale by animateFloatAsState(
                            targetValue = if (isActionPressed) 0.82f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
                            label = "ActionScale"
                        )

                        val liveBtnColor by animateColorAsState(
                            targetValue = if (isListening) Color(0xFFD946EF) else Color(0xFFA855F7),
                            animationSpec = tween(250),
                            label = "liveBtnColor"
                        )

                        // Fluid breathing pulse animation for Live Voice conversation button
                        val liveBreathTransition = rememberInfiniteTransition(label = "liveBreath")
                        val liveBreathScale by liveBreathTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (isListening) 1.12f else 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(if (isListening) 650 else 1100, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "liveBreathScale"
                        )
                        val effectiveScale = if (!canSend) actionScale * liveBreathScale else actionScale
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 8.dp)
                                .size(40.dp)
                                .scale(effectiveScale)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) {
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9), Color(0xFF3B82F6))
                                        )
                                    } else {
                                        // Vibrant gradient matching the microphone's purple/magenta color theme
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(
                                                liveBtnColor,
                                                Color(0xFF9333EA),
                                                Color(0xFFA855F7)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        width = if (isListening) 1.8.dp else 1.2.dp,
                                        color = if (!canSend) {
                                            if (isListening) Color(0xFFF472B6) else Color(0xFFC084FC).copy(alpha = 0.8f)
                                        } else Color.Transparent
                                    ),
                                    shape = CircleShape
                                )
                                .clickable(interactionSource = actionInteraction, indication = null) {
                                    if (canSend) {
                                        if (isListening) stopListeningNow()
                                        focusManager.clearFocus()
                                        onSend()
                                    } else {
                                        if (isListening) {
                                            stopListeningNow()
                                        } else {
                                            onStartLiveVoice()
                                        }
                                    }
                                }
                        ) {
                            // Light glow around the active button
                            val btnGlowAlpha by animateFloatAsState(
                                targetValue = if (isActionPressed || isListening) 0.45f else 0.1f,
                                animationSpec = tween(200),
                                label = "ActionGlow"
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.White.copy(alpha = btnGlowAlpha), CircleShape)
                            )
                            
                            AnimatedContent(
                                targetState = canSend,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.85f))
                                        .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.85f))
                                },
                                label = "SendVoiceTransition"
                            ) { isSendReady ->
                                if (isSendReady) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    SoundWaveIcon(isAnimated = isListening, rms = rmsValue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentOption(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "AttachmentOptionScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(end = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (com.example.ui.theme.SaifTheme.colors.isLight) Color(0x203B82F6) else Color(0x333B82F6)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = com.example.ui.theme.SaifTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SoundWaveIcon(isAnimated: Boolean = false, rms: Float = 0f) {
    val waveBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color.White, Color(0xFFE0E7FF))
    )

    if (isAnimated) {
        val transition = rememberInfiniteTransition(label = "soundbars")
        val h1Base by transition.animateFloat(6f, 15f, infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h1")
        val h2Base by transition.animateFloat(10f, 20f, infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h2")
        val h3Base by transition.animateFloat(8f, 17f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h3")
        val h4Base by transition.animateFloat(5f, 13f, infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h4")
        
        val rmsFactor = (rms / 10f).coerceIn(0f, 1f)
        val h1 = h1Base + (10f * rmsFactor)
        val h2 = h2Base + (14f * rmsFactor)
        val h3 = h3Base + (12f * rmsFactor)
        val h4 = h4Base + (8f * rmsFactor)

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(h1.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(h2.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(h3.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(h4.dp).clip(CircleShape).background(waveBrush))
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(10.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(14.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(8.dp).clip(CircleShape).background(waveBrush))
        }
    }
}

