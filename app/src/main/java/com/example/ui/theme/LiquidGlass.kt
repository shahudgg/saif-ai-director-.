package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid Glass Material & Fluid Animation System.
 * Implements physical liquid glassmorphism:
 * - Translucent frosted glass layers with realistic refraction
 * - Specular reflection highlights along top/left edges
 * - Continuous iridescent liquid light waves flowing along borders
 * - Responsive fluid breathing and spring-physics interactions
 */
object LiquidGlassDefaults {

    // Iridescent Liquid Color Palette
    val CyanSheen = Color(0xFF38BDF8)
    val VioletSheen = Color(0xFFA855F7)
    val ElectricBlue = Color(0xFF3B82F6)
    val MagentaGlow = Color(0xFFEC4899)
    val IndigoLuster = Color(0xFF818CF8)
    val DeepPurple = Color(0xFF6D28D9)

    // Dark Mode Glass Surface (Deep translucent slate with crystal depth)
    val DarkGlassTop = Color(0x381E293B)
    val DarkGlassBottom = Color(0x220F172A)

    // Light Mode Glass Surface (Milky frosted crystal)
    val LightGlassTop = Color(0xEEFFFFFF)
    val LightGlassBottom = Color(0xD8F8FAFC)

    /**
     * Builds a clean static background brush
     */
    fun glassBackgroundBrush(isLightMode: Boolean): Brush {
        return if (isLightMode) {
            Brush.verticalGradient(
                listOf(LightGlassTop, LightGlassBottom)
            )
        } else {
            Brush.verticalGradient(
                listOf(DarkGlassTop, DarkGlassBottom)
            )
        }
    }

    /**
     * Creates a clean, elegant static border brush (liquid animation removed)
     */
    fun specularBorderBrush(
        phase: Float = 0f,
        isLightMode: Boolean = false,
        alpha: Float = 1f
    ): Brush {
        return if (isLightMode) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE2E8F0).copy(alpha = 0.8f * alpha),
                    Color(0xFFCBD5E1).copy(alpha = 0.6f * alpha)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF334155).copy(alpha = 0.7f * alpha),
                    Color(0xFF1E293B).copy(alpha = 0.5f * alpha)
                )
            )
        }
    }

    /**
     * Atmospheric ambient aura brush
     */
    fun liquidAuraBrush(phase: Float = 0f, alpha: Float = 0.15f): Brush {
        return Brush.linearGradient(
            colors = listOf(
                ElectricBlue.copy(alpha = alpha),
                VioletSheen.copy(alpha = alpha)
            )
        )
    }
}

/**
 * Modifier that applies a clean, subtle border without liquid glass shimmer
 */
fun Modifier.liquidGlassBorder(
    cornerRadius: Dp = 24.dp,
    strokeWidth: Dp = 1.dp,
    isLightMode: Boolean = false,
    alpha: Float = 1f
): Modifier = this.drawBehind {
    val radiusPx = cornerRadius.toPx()
    val strokePx = strokeWidth.toPx()
    val borderBrush = LiquidGlassDefaults.specularBorderBrush(0f, isLightMode, alpha)
    drawRoundRect(
        brush = borderBrush,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(width = strokePx)
    )
}

/**
 * Modifier providing soft, fluid elastic bouncing physics when tapped (water-drop feel)
 */
fun Modifier.liquidBounceClickable(
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "liquid_bounce_scale"
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = source,
            indication = null,
            onClick = onClick
        )
}
