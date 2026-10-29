package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Very subtle ("bhot halka") Liquid Glass Effect for the Main Screen.
 * Provides frosted specular depth and gentle floating light waves
 * without overpowering readability or content contrast.
 */
@Composable
fun LiquidGlassBackground(
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass_ambience")
    
    // Slow, calming breathing and drift
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquid_orb_phase_1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquid_orb_phase_2"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Subtle liquid colors: Cyan & Violet in dark, Soft Sky & Lavender in light
        val orbColor1 = if (isLightMode) Color(0xFF60A5FA).copy(alpha = 0.045f) else Color(0xFF38BDF8).copy(alpha = 0.055f)
        val orbColor2 = if (isLightMode) Color(0xFFA855F7).copy(alpha = 0.04f) else Color(0xFF818CF8).copy(alpha = 0.05f)
        val specularSheen = if (isLightMode) Color(0xFFFFFFFF).copy(alpha = 0.035f) else Color(0xFF38BDF8).copy(alpha = 0.025f)

        // Floating Top-Left Orb
        val orb1Center = Offset(
            x = width * (0.2f + 0.12f * phase1),
            y = height * (0.22f + 0.08f * phase2)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbColor1, Color.Transparent),
                center = orb1Center,
                radius = width * 0.75f
            ),
            center = orb1Center,
            radius = width * 0.75f
        )

        // Floating Bottom-Right Orb
        val orb2Center = Offset(
            x = width * (0.8f - 0.15f * phase2),
            y = height * (0.75f - 0.1f * phase1)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbColor2, Color.Transparent),
                center = orb2Center,
                radius = width * 0.85f
            ),
            center = orb2Center,
            radius = width * 0.85f
        )

        // Delicate diagonal frosted glass specular sheen
        val sheenStart = Offset(width * (0.1f * phase1), 0f)
        val sheenEnd = Offset(width, height * (0.8f + 0.2f * phase2))
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    specularSheen,
                    Color.Transparent
                ),
                start = sheenStart,
                end = sheenEnd
            )
        )
    }
}
