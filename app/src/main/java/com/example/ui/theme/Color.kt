package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Hardcoded core colors (used for gradients/accents)
val PrimaryAccent = Color(0xFF3B82F6)     // Vibrant Electric Blue
val PrimaryAccentDark = Color(0xFF2563EB) // Electric Blue Dark
val SecondaryViolet = Color(0xFF8B5CF6)   // Violet Glow
val SecondaryIndigo = Color(0xFF6366F1)   // Indigo Glow

// Bubble Colors
val UserBubbleStart = Color(0xFF2563EB)
val UserBubbleEnd = Color(0xFF1D4ED8)
val UserBubbleText = Color(0xFFFFFFFF)

val StatusSuccess = Color(0xFF10B981)
val StatusWarning = Color(0xFFF59E0B)
val StatusError = Color(0xFFEF4444)

// Gradient Brushes
val SaifAuraGradient = Brush.horizontalGradient(
    listOf(Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6))
)

val SaifUserBubbleBrush = Brush.linearGradient(
    listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
)

val SaifCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF161E30), Color(0xFF101522))
)
val SubtextMuted = Color(0xFF64748B) // Subtext & Timestamps
