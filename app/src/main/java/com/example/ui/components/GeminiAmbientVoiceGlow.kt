package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gemini-style ambient atmospheric glow and multi-color shifting waves
 * that radiate upwards from the bottom half of the screen behind the chat input bar.
 */
@Composable
fun GeminiAmbientVoiceGlow(
    isActive: Boolean,
    rmsValue: Float,
    modifier: Modifier = Modifier
) {
    val alphaAnim by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ambientGlowAlpha"
    )

    if (alphaAnim <= 0.001f && !isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "geminiAurora")

    // Slow, beautiful color sweep phase
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "colorPhase"
    )

    // Primary wave movement
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase1"
    )

    // Secondary wave movement (counter direction)
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase2"
    )

    // Dynamic amplitude based on voice volume with snappy bouncy spring
    val animatedRms by animateFloatAsState(
        targetValue = (rmsValue / 10f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "voiceRms"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val glowHeight = height * 0.52f // Takes up bottom ~50% of the screen
        val startY = height - glowHeight

        // 1. VERTICAL ATMOSPHERIC FADE (Dark purple/violet/magenta fading smoothly up to 0 alpha)
        val verticalFade = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.25f to Color(0xFF4C1D95).copy(alpha = 0.08f * alphaAnim),
                0.45f to Color(0xFF6D28D9).copy(alpha = (0.28f + animatedRms * 0.12f) * alphaAnim),
                0.70f to Color(0xFF7C3AED).copy(alpha = (0.50f + animatedRms * 0.18f) * alphaAnim),
                0.90f to Color(0xFF4338CA).copy(alpha = (0.75f + animatedRms * 0.15f) * alphaAnim),
                1.00f to Color(0xFF312E81).copy(alpha = 0.88f * alphaAnim)
            ),
            startY = startY,
            endY = height
        )
        drawRect(
            brush = verticalFade,
            topLeft = Offset(0f, startY),
            size = Size(width, glowHeight)
        )

        // 2. WIDE RADIAL AMBIENT GLOW (Pulsing behind the chat bar & suggestion cards)
        val radialCenter = Offset(width / 2f, height - 60.dp.toPx())
        val radialRadius = width * 0.95f
        val radialBrush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFA855F7).copy(alpha = (0.55f + animatedRms * 0.35f) * alphaAnim),
                Color(0xFF7C3AED).copy(alpha = (0.35f + animatedRms * 0.25f) * alphaAnim),
                Color(0xFF38BDF8).copy(alpha = (0.20f + animatedRms * 0.15f) * alphaAnim),
                Color.Transparent
            ),
            center = radialCenter,
            radius = radialRadius
        )
        drawCircle(
            brush = radialBrush,
            radius = radialRadius,
            center = radialCenter
        )

        // 3. AMBIENT SHIFTING WAVE 1 (Primary energetic ribbon)
        val centerY1 = height - 95.dp.toPx()
        val path1 = Path()
        path1.moveTo(0f, centerY1)

        val stepPx = 4
        for (x in 0..width.toInt() step stepPx) {
            val xNorm = x / width
            // Sinusoidal bell envelope so ends attach nicely at borders and center is high
            val envelope = sin(xNorm * Math.PI).toFloat()
            val w1 = sin((xNorm * 2.8 * Math.PI) + (wavePhase1 * 2 * Math.PI))
            val w2 = sin((xNorm * 4.6 * Math.PI) - (wavePhase1 * 2.5 * Math.PI))
            val voiceRipple = sin((xNorm * 6.5 * Math.PI) + (wavePhase1 * 4.5 * Math.PI)) * (animatedRms * 12.dp.toPx())
            val amp = (16.dp.toPx() + animatedRms * 72.dp.toPx()) * envelope
            val y = centerY1 + ((w1 + w2) * 0.5f * amp + voiceRipple * envelope).toFloat()
            path1.lineTo(x.toFloat(), y)
        }

        val shiftingGradient1 = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF3B82F6), // Royal Blue
                Color(0xFF8B5CF6), // Purple
                Color(0xFFA855F7), // Vivid Violet
                Color(0xFF06B6D4), // Cyan
                Color(0xFF7C3AED), // Deep Violet
                Color(0xFF3B82F6)  // Wrap back
            ),
            startX = colorPhase * width,
            endX = (colorPhase + 1f) * width,
            tileMode = TileMode.Repeated
        )

        drawPath(
            path = path1,
            brush = shiftingGradient1,
            alpha = (0.80f + animatedRms * 0.20f) * alphaAnim,
            style = Stroke(
                width = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 4. HARMONIC SECONDARY WAVE (Gentle floating shimmer for depth)
        val centerY2 = centerY1 - 18.dp.toPx()
        val path2 = Path()
        path2.moveTo(0f, centerY2)

        for (x in 0..width.toInt() step stepPx) {
            val xNorm = x / width
            val envelope = sin(xNorm * Math.PI).toFloat()
            val w1 = cos((xNorm * 2.2 * Math.PI) + (wavePhase2 * 2 * Math.PI))
            val w2 = sin((xNorm * 3.5 * Math.PI) + (wavePhase2 * 1.8 * Math.PI))
            val voiceRipple2 = cos((xNorm * 5.5 * Math.PI) - (wavePhase2 * 4.0 * Math.PI)) * (animatedRms * 10.dp.toPx())
            val amp = (12.dp.toPx() + animatedRms * 52.dp.toPx()) * envelope
            val y = centerY2 + ((w1 + w2) * 0.5f * amp + voiceRipple2 * envelope).toFloat()
            path2.lineTo(x.toFloat(), y)
        }

        val shiftingGradient2 = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFA855F7), // Vivid Violet
                Color(0xFF06B6D4), // Cyan
                Color(0xFF8B5CF6), // Purple
                Color(0xFF3B82F6), // Blue
                Color(0xFFA855F7)  // Wrap back
            ),
            startX = (1f - colorPhase) * width,
            endX = (2f - colorPhase) * width,
            tileMode = TileMode.Repeated
        )

        drawPath(
            path = path2,
            brush = shiftingGradient2,
            alpha = (0.50f + animatedRms * 0.25f) * alphaAnim,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
