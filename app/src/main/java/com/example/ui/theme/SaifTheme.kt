package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

data class SaifColors(
    val primaryBackground: Color,
    val surfaceCard: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val aiBubbleBackground: Color,
    val aiBubbleBorder: Color,
    val aiBubbleText: Color,
    val codeBackground: Color,
    val codeBorder: Color,
    val codeText: Color,
    val isLight: Boolean
)

val DarkColors = SaifColors(
    primaryBackground = Color(0xFF0A0D14),
    surfaceCard = Color(0xFF131826),
    cardBorder = Color(0xFF1E293B),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    aiBubbleBackground = Color(0xFF131826),
    aiBubbleBorder = Color(0xFF1E293B),
    aiBubbleText = Color(0xFFF1F5F9),
    codeBackground = Color(0xFF0D1117),
    codeBorder = Color(0xFF1E293B),
    codeText = Color(0xFFE2E8F0),
    isLight = false
)

val LightColors = SaifColors(
    primaryBackground = Color(0xFFFFFFFF),
    surfaceCard = Color(0xFFF8FAFC),
    cardBorder = Color(0xFF000000), // Black outline for visibility
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    aiBubbleBackground = Color(0xFFF1F5F9),
    aiBubbleBorder = Color(0xFF000000), // Black outline
    aiBubbleText = Color(0xFF0F172A),
    codeBackground = Color(0xFFF8FAFC),
    codeBorder = Color(0xFF000000), // Black outline
    codeText = Color(0xFF334155),
    isLight = true
)

val LocalSaifColors = compositionLocalOf { DarkColors }

object SaifTheme {
    val colors: SaifColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSaifColors.current
}

@Composable
fun AnimatedSaifTheme(
    isLightMode: Boolean,
    content: @Composable () -> Unit
) {
    val targetColors = if (isLightMode) LightColors else DarkColors
    val animSpec = tween<Color>(300)
    
    val primaryBackground by animateColorAsState(targetColors.primaryBackground, animSpec, label = "bg")
    val surfaceCard by animateColorAsState(targetColors.surfaceCard, animSpec, label = "card")
    val cardBorder by animateColorAsState(targetColors.cardBorder, animSpec, label = "border")
    val textPrimary by animateColorAsState(targetColors.textPrimary, animSpec, label = "textP")
    val textSecondary by animateColorAsState(targetColors.textSecondary, animSpec, label = "textS")
    val aiBubbleBackground by animateColorAsState(targetColors.aiBubbleBackground, animSpec, label = "aiBg")
    val aiBubbleBorder by animateColorAsState(targetColors.aiBubbleBorder, animSpec, label = "aiBorder")
    val aiBubbleText by animateColorAsState(targetColors.aiBubbleText, animSpec, label = "aiText")
    val codeBackground by animateColorAsState(targetColors.codeBackground, animSpec, label = "codeBg")
    val codeBorder by animateColorAsState(targetColors.codeBorder, animSpec, label = "codeBorder")
    val codeText by animateColorAsState(targetColors.codeText, animSpec, label = "codeText")

    val animatedColors = SaifColors(
        primaryBackground = primaryBackground,
        surfaceCard = surfaceCard,
        cardBorder = cardBorder,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        aiBubbleBackground = aiBubbleBackground,
        aiBubbleBorder = aiBubbleBorder,
        aiBubbleText = aiBubbleText,
        codeBackground = codeBackground,
        codeBorder = codeBorder,
        codeText = codeText,
        isLight = isLightMode
    )

    CompositionLocalProvider(LocalSaifColors provides animatedColors) {
        content()
    }
}
