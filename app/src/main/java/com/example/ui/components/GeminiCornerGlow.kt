package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Gemini-style screen 4-corner & border edge glow animation.
 * Activates for ~3.5 seconds when the wake word ("Hey Saif") is spoken.
 */
@Composable
fun GeminiCornerGlow(
    triggerTimestamp: Long,
    modifier: Modifier = Modifier
) {
    var isGlowActive by remember { mutableStateOf(false) }

    LaunchedEffect(triggerTimestamp) {
        if (triggerTimestamp > 0) {
            isGlowActive = true
            // Glow stays vibrant for 3.5 seconds
            delay(3500)
            isGlowActive = false
        }
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (isGlowActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isGlowActive) 300 else 700,
            easing = FastOutSlowInEasing
        ),
        label = "geminiCornerGlowAlpha"
    )

    if (glowAlpha <= 0.001f && !isGlowActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "cornerGlowAurora")

    // Dynamic rotation / phase sweep for liquid color shifting
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cornerPhase"
    )

    // Pulsing expansion
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerPulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cornerRadius = 170.dp.toPx() * pulse

        // Vibrant Gemini Aurora Colors
        val cyan = Color(0xFF00E5FF).copy(alpha = 0.85f * glowAlpha)
        val blue = Color(0xFF3B82F6).copy(alpha = 0.90f * glowAlpha)
        val purple = Color(0xFF8B5CF6).copy(alpha = 0.85f * glowAlpha)
        val magenta = Color(0xFFEC4899).copy(alpha = 0.80f * glowAlpha)
        val amber = Color(0xFFF59E0B).copy(alpha = 0.70f * glowAlpha)

        // 1. TOP-LEFT CORNER GLOW
        val tlBrush = Brush.radialGradient(
            colors = listOf(cyan, blue, purple, Color.Transparent),
            center = Offset(0f, 0f),
            radius = cornerRadius
        )
        drawCircle(
            brush = tlBrush,
            radius = cornerRadius,
            center = Offset(0f, 0f)
        )

        // 2. TOP-RIGHT CORNER GLOW
        val trBrush = Brush.radialGradient(
            colors = listOf(magenta, purple, blue, Color.Transparent),
            center = Offset(w, 0f),
            radius = cornerRadius
        )
        drawCircle(
            brush = trBrush,
            radius = cornerRadius,
            center = Offset(w, 0f)
        )

        // 3. BOTTOM-LEFT CORNER GLOW
        val blBrush = Brush.radialGradient(
            colors = listOf(blue, cyan, magenta, Color.Transparent),
            center = Offset(0f, h),
            radius = cornerRadius * 1.15f
        )
        drawCircle(
            brush = blBrush,
            radius = cornerRadius * 1.15f,
            center = Offset(0f, h)
        )

        // 4. BOTTOM-RIGHT CORNER GLOW
        val brBrush = Brush.radialGradient(
            colors = listOf(purple, magenta, amber, Color.Transparent),
            center = Offset(w, h),
            radius = cornerRadius * 1.15f
        )
        drawCircle(
            brush = brBrush,
            radius = cornerRadius * 1.15f,
            center = Offset(w, h)
        )

        // 5. PERIMETER EDGE AURA (Continuous perimeter glow line)
        val borderGradient = Brush.sweepGradient(
            colors = listOf(
                cyan, blue, purple, magenta, amber, cyan
            ),
            center = Offset(w / 2f, h / 2f)
        )

        drawRect(
            brush = borderGradient,
            size = Size(w, h),
            style = Stroke(width = 4.dp.toPx() * glowAlpha)
        )
    }
}
