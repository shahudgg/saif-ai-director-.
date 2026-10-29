package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import com.example.ui.theme.SaifTheme

@Composable
fun QuickPromptChips(
    currentMode: String,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val promptPurple = Color(0xFFA855F7) // Light Purple for text and icons
    val isLightMode = com.example.ui.theme.SaifTheme.colors.isLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        val infiniteTransition = rememberInfiniteTransition(label = "hero")
        val rotationZ by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hero_rotation"
        )
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hero_scale"
        )
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hero_glow"
        )

        // Hero Icon with Floating Animation & Liquid Atmospheric Glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(scale)
                .rotate(rotationZ)
        ) {
            // Background Liquid Atmospheric Glow
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = glowAlpha * 0.5f),
                                Color(0xFFA855F7).copy(alpha = glowAlpha * 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Hero AI Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF131826)
                    )
                    .border(
                        1.dp,
                        if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                        RoundedCornerShape(26.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "AI Core",
                    tint = promptPurple,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Titles
        Text(
            text = "How can I help you, Saif?",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = SaifTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Chat, generate images, analyze photos, or just\ntalk. SAIF AI works in English and Hinglish.",
            fontSize = 14.5.sp,
            color = com.example.ui.theme.SaifTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Cards list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PromptCard(
                icon = Icons.Outlined.AutoFixHigh,
                title = "Generate an image",
                subtitle = "A futuristic city in neon lights",
                iconTint = promptPurple,
                onClick = { onSelectPrompt("Generate an image of a futuristic city in neon lights") }
            )
            PromptCard(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Plan something",
                subtitle = "3-day Tokyo itinerary",
                iconTint = promptPurple,
                onClick = { onSelectPrompt("Help me plan a 3-day Tokyo itinerary") }
            )
            PromptCard(
                icon = Icons.Default.Code,
                title = "Explain code",
                subtitle = "Walk me through React hooks",
                iconTint = promptPurple,
                onClick = { onSelectPrompt("Walk me through React hooks") }
            )
            PromptCard(
                icon = Icons.Outlined.Lightbulb,
                title = "Hinglish chat",
                subtitle = "Roman-script Hindi support",
                iconTint = promptPurple,
                onClick = { onSelectPrompt("Let's chat in Hinglish") }
            )
        }
    }
}

@Composable
fun PromptCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    val isLightMode = com.example.ui.theme.SaifTheme.colors.isLight
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PromptCardScale"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF131826),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
        ),
        shadowElevation = if (isLightMode) 2.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = iconTint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = com.example.ui.theme.SaifTheme.colors.textSecondary,
                fontSize = 13.5.sp
            )
        }
    }
}

