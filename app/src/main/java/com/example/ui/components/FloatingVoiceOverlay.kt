package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LiveVoiceSessionManager
import com.example.VoiceSessionState
import kotlin.math.sin

/**
 * Live Voice Overlay with the signature glowing AI Sparkle Star animation
 * (4-point star with top-right '+' and bottom-left dot), featuring:
 * - Subtle left-right swaying & organic floating tilt
 * - Continuous smooth breathing scale (chota-bada hona)
 * - Deep multi-layered radiant atmospheric background glow
 * - Dynamic high-energy audio reactivity when speaking (glow teji se hile)
 */
@Composable
fun FloatingVoiceOverlay(
    engine: LiveVoiceSessionManager,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    isInApp: Boolean = false
) {
    val amplitude by engine.amplitude.collectAsState(initial = 0f)
    val state by engine.sessionState.collectAsState(initial = VoiceSessionState.IDLE)
    val liveSubtitle by engine.liveSubtitle.collectAsState(initial = "")

    if (isInApp) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (state == VoiceSessionState.SPEAKING) {
                        engine.stopSpeaking()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Main glowing sparkle star animation in place of the old pink circle
            SaifSparkleLiveVoiceAnimation(
                state = state,
                amplitude = amplitude,
                sizeDp = 260.dp,
                onClick = {
                    if (state == VoiceSessionState.SPEAKING) {
                        engine.stopSpeaking()
                    }
                }
            )

            // Subtitles and status readout at bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                val displayText = when (state) {
                    VoiceSessionState.LISTENING -> "LISTENING"
                    VoiceSessionState.PROCESSING -> "THINKING"
                    VoiceSessionState.SPEAKING -> if (liveSubtitle.isNotBlank() &&
                        liveSubtitle != "Listening..." &&
                        liveSubtitle != "Thinking..." &&
                        liveSubtitle != "LISTENING" &&
                        liveSubtitle != "THINKING"
                    ) liveSubtitle else "SPEAKING"
                    else -> "LISTENING"
                }

                Text(
                    text = displayText,
                    color = Color.White,
                    fontWeight = if (state == VoiceSessionState.SPEAKING) FontWeight.Normal else FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(0.9f),
                    fontSize = if (state == VoiceSessionState.SPEAKING) 16.sp else 20.sp,
                    letterSpacing = if (state == VoiceSessionState.SPEAKING) 0.sp else 1.5.sp,
                    maxLines = 6,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(24.dp))

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red.copy(alpha = 0.22f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Live Voice",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    } else {
        // Floating overlay bubble outside of app
        Box(
            modifier = Modifier
                .size(76.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable { onOpenApp() }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF130E24),
                modifier = Modifier.fillMaxSize(),
                shadowElevation = 8.dp
            ) {
                SaifSparkleLiveVoiceAnimation(
                    state = state,
                    amplitude = amplitude,
                    sizeDp = 60.dp,
                    isCompact = true,
                    onClick = { onOpenApp() }
                )
            }
        }
    }
}

/**
 * Signature AI Sparkle Voice Animation (matching the user's provided purple sparkle icon):
 * - Center 4-point concave star
 * - Top-right '+' cross sparkle
 * - Bottom-left glowing dot
 * - Left/Right gentle swaying animation
 * - Continuous breathing scale (chota-bada hona)
 * - Deep radiating background glow
 * - Rapid dynamic soundwave vibration when speaking
 */
@Composable
fun SaifSparkleLiveVoiceAnimation(
    state: VoiceSessionState,
    amplitude: Float,
    sizeDp: Dp = 260.dp,
    isCompact: Boolean = false,
    onClick: () -> Unit = {}
) {
    val isVoiceActive = amplitude > 0.035f || state == VoiceSessionState.SPEAKING
    val dynAmplitude = when (state) {
        VoiceSessionState.LISTENING, VoiceSessionState.SPEAKING -> amplitude
        VoiceSessionState.PROCESSING -> 0.35f
        else -> 0.05f
    }

    // Dynamic amplitude smoothed with snappy bouncy spring
    val animatedAmp by animateFloatAsState(
        targetValue = (dynAmplitude * 2.2f).coerceIn(0f, 1.3f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "animatedAmp"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "sparkleVoiceMotion")

    // 1. "halka left right hile": Gentle horizontal swaying & organic tilt
    val swayX by infiniteTransition.animateFloat(
        initialValue = if (isCompact) -4f else -18f,
        targetValue = if (isCompact) 4f else 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swayX"
    )

    val swayY by infiniteTransition.animateFloat(
        initialValue = if (isCompact) -2.5f else -8f,
        targetValue = if (isCompact) 2.5f else 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swayY"
    )

    val swayTilt by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swayTilt"
    )

    // 2. "chota bada ho": Smooth breathing scale
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.91f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // 3. "bolne par glow teji se hile": High-speed sonic pulse/vibration cycle
    val rapidPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isVoiceActive) 320 else 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rapidPulse"
    )

    // Audio-reactive jitter when speaking loudly
    val microAudioJitter = if (isVoiceActive) {
        sin(rapidPulse * 6.28318f * 3f) * animatedAmp * (if (isCompact) 2f else 6.5f)
    } else {
        0f
    }

    // Dynamic total scale combining breathing + voice amplitude boost
    val voiceScaleBoost = 1f + (animatedAmp * 0.38f)
    val totalScale = breathingScale * voiceScaleBoost

    // Canvas container with background glow and central spark
    Box(
        modifier = Modifier
            .size(if (isCompact) sizeDp else sizeDp + 80.dp)
            .offset(x = (swayX + microAudioJitter).dp, y = swayY.dp)
            .rotate(swayTilt)
            .scale(totalScale)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseRadius = (size.minDimension / 2f) * (if (isCompact) 0.52f else 0.46f)

            // Neon Violet palette matching the user's screenshot
            val neonVioletCore = Color(0xFFC084FC)   // Bright lavender/violet highlight
            val neonVioletBody = Color(0xFFA855F7)   // Vivid electric purple
            val neonVioletAura = Color(0xFF9333EA)   // Rich violet aura
            val deepVioletGlow = Color(0xFF7C3AED)   // Ambient atmospheric glow

            // -------------------------------------------------------------
            // LAYER 1: Radiant atmospheric background glow ("background se glow nikle")
            // -------------------------------------------------------------
            val glowIntensity = if (isVoiceActive) {
                (0.55f + animatedAmp * 0.45f).coerceIn(0.4f, 1f)
            } else if (state == VoiceSessionState.PROCESSING) {
                0.60f
            } else {
                0.35f
            }

            // Outer atmospheric bloom
            val outerGlowRadius = baseRadius * (if (isCompact) 1.5f else 1.9f + animatedAmp * 0.7f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        deepVioletGlow.copy(alpha = 0.50f * glowIntensity),
                        neonVioletAura.copy(alpha = 0.28f * glowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = outerGlowRadius
                ),
                radius = outerGlowRadius,
                center = Offset(cx, cy)
            )

            // Core shimmer aura behind the star
            val coreGlowRadius = baseRadius * (if (isCompact) 1.2f else 1.35f + animatedAmp * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        neonVioletBody.copy(alpha = 0.70f * glowIntensity),
                        deepVioletGlow.copy(alpha = 0.35f * glowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = coreGlowRadius
                ),
                radius = coreGlowRadius,
                center = Offset(cx, cy)
            )

            // -------------------------------------------------------------
            // LAYER 2: Expanding Soundwave Ripples ("bolne par glow teji se hile")
            // -------------------------------------------------------------
            if (!isCompact && (isVoiceActive || state == VoiceSessionState.PROCESSING)) {
                val ripplePhase1 = rapidPulse
                val ripplePhase2 = (rapidPulse + 0.5f) % 1f

                // Ripple 1
                val r1 = baseRadius * (1.0f + ripplePhase1 * 0.85f)
                val alpha1 = (1f - ripplePhase1) * (0.45f + animatedAmp * 0.35f)
                drawCircle(
                    color = neonVioletBody.copy(alpha = alpha1.coerceIn(0f, 1f)),
                    radius = r1,
                    center = Offset(cx, cy),
                    style = Stroke(width = (2.5f - ripplePhase1 * 1.5f).dp.toPx())
                )

                // Ripple 2
                val r2 = baseRadius * (1.0f + ripplePhase2 * 0.85f)
                val alpha2 = (1f - ripplePhase2) * (0.35f + animatedAmp * 0.30f)
                drawCircle(
                    color = neonVioletAura.copy(alpha = alpha2.coerceIn(0f, 1f)),
                    radius = r2,
                    center = Offset(cx, cy),
                    style = Stroke(width = (2.0f - ripplePhase2 * 1.2f).dp.toPx())
                )
            }

            // -------------------------------------------------------------
            // LAYER 3: The 4-Point Concave Sparkle Star Path
            // -------------------------------------------------------------
            val starR = baseRadius
            val innerWaist = starR * 0.28f

            val starPath = Path().apply {
                moveTo(cx, cy - starR)
                cubicTo(cx, cy - innerWaist, cx + innerWaist, cy, cx + starR, cy)
                cubicTo(cx + innerWaist, cy, cx, cy + innerWaist, cx, cy + starR)
                cubicTo(cx, cy + innerWaist, cx - innerWaist, cy, cx - starR, cy)
                cubicTo(cx - innerWaist, cy, cx, cy - innerWaist, cx, cy - starR)
                close()
            }

            // Translucent glowing fill inside the star
            drawPath(
                path = starPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        neonVioletBody.copy(alpha = 0.25f + animatedAmp * 0.20f),
                        deepVioletGlow.copy(alpha = 0.08f)
                    ),
                    center = Offset(cx, cy),
                    radius = starR
                )
            )

            // Multi-layer neon tube stroke for center star
            val strokeMultiplier = if (isCompact) 0.5f else 1f

            // Outer blur halo stroke
            drawPath(
                path = starPath,
                color = deepVioletGlow.copy(alpha = 0.35f * glowIntensity),
                style = Stroke(
                    width = 18.dp.toPx() * strokeMultiplier,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Mid neon body stroke
            drawPath(
                path = starPath,
                color = neonVioletBody,
                style = Stroke(
                    width = 9.dp.toPx() * strokeMultiplier,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Bright core neon tube highlight
            drawPath(
                path = starPath,
                color = neonVioletCore,
                style = Stroke(
                    width = 4.5.dp.toPx() * strokeMultiplier,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // White/Lilac hot center streak
            drawPath(
                path = starPath,
                color = Color(0xFFF3E8FF).copy(alpha = 0.85f),
                style = Stroke(
                    width = 1.8.dp.toPx() * strokeMultiplier,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // -------------------------------------------------------------
            // LAYER 4: Top-Right '+' Sparkle Cross
            // -------------------------------------------------------------
            val plusCx = cx + starR * 0.68f
            val plusCy = cy - starR * 0.68f
            val arm = starR * 0.20f
            val plusStroke = 5.dp.toPx() * strokeMultiplier
            val plusGlowStroke = 12.dp.toPx() * strokeMultiplier

            // Outer glow for plus
            drawLine(
                color = deepVioletGlow.copy(alpha = 0.35f * glowIntensity),
                start = Offset(plusCx - arm, plusCy),
                end = Offset(plusCx + arm, plusCy),
                strokeWidth = plusGlowStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = deepVioletGlow.copy(alpha = 0.35f * glowIntensity),
                start = Offset(plusCx, plusCy - arm),
                end = Offset(plusCx, plusCy + arm),
                strokeWidth = plusGlowStroke,
                cap = StrokeCap.Round
            )

            // Neon body for plus
            drawLine(
                color = neonVioletBody,
                start = Offset(plusCx - arm, plusCy),
                end = Offset(plusCx + arm, plusCy),
                strokeWidth = plusStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = neonVioletBody,
                start = Offset(plusCx, plusCy - arm),
                end = Offset(plusCx, plusCy + arm),
                strokeWidth = plusStroke,
                cap = StrokeCap.Round
            )

            // Bright core for plus
            drawLine(
                color = neonVioletCore,
                start = Offset(plusCx - arm * 0.75f, plusCy),
                end = Offset(plusCx + arm * 0.75f, plusCy),
                strokeWidth = 2.dp.toPx() * strokeMultiplier,
                cap = StrokeCap.Round
            )
            drawLine(
                color = neonVioletCore,
                start = Offset(plusCx, plusCy - arm * 0.75f),
                end = Offset(plusCx, plusCy + arm * 0.75f),
                strokeWidth = 2.dp.toPx() * strokeMultiplier,
                cap = StrokeCap.Round
            )

            // -------------------------------------------------------------
            // LAYER 5: Bottom-Left Glowing Dot
            // -------------------------------------------------------------
            val dotCx = cx - starR * 0.66f
            val dotCy = cy + starR * 0.58f
            val dotRadius = starR * 0.13f

            // Outer glow for dot
            drawCircle(
                color = deepVioletGlow.copy(alpha = 0.40f * glowIntensity),
                radius = dotRadius + 7.dp.toPx() * strokeMultiplier,
                center = Offset(dotCx, dotCy)
            )

            // Filled neon dot body
            drawCircle(
                color = neonVioletBody,
                radius = dotRadius,
                center = Offset(dotCx, dotCy)
            )

            // Inner translucent highlight
            drawCircle(
                color = neonVioletCore,
                radius = dotRadius * 0.65f,
                center = Offset(dotCx - dotRadius * 0.2f, dotCy - dotRadius * 0.2f)
            )

            // Center hot spot
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = dotRadius * 0.28f,
                center = Offset(dotCx - dotRadius * 0.25f, dotCy - dotRadius * 0.25f)
            )
        }
    }
}
