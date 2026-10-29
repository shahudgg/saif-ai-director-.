package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifAuraGradient
import com.example.ui.theme.SubtextMuted

data class AssistantMode(
    val id: String,
    val icon: String,
    val title: String,
    val description: String,
    val badge: String
)

val ASSISTANT_MODES = listOf(
    AssistantMode(
        id = "general",
        icon = "🧠",
        title = "General Assistant",
        description = "Balanced, versatile intelligence for daily tasks, questions, and ideas.",
        badge = "Default"
    ),
    AssistantMode(
        id = "coding",
        icon = "💻",
        title = "Coding Master",
        description = "Production Kotlin, Jetpack Compose, architecture, and debugging specialist.",
        badge = "Expert"
    ),
    AssistantMode(
        id = "summary",
        icon = "⚡",
        title = "Quick Summary",
        description = "Fast synthesis, executive takeaways, bullet points, and key metrics.",
        badge = "Fast"
    ),
    AssistantMode(
        id = "creative",
        icon = "🎨",
        title = "Creative Studio",
        description = "Storytelling, marketing copy, dynamic ideation, and creative brainstorming.",
        badge = "Creative"
    ),
    AssistantMode(
        id = "research",
        icon = "🔬",
        title = "Deep Research",
        description = "In-depth technical papers, structured trade-offs, and critical analysis.",
        badge = "Deep"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectorSheet(
    currentMode: String,
    onSelectMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SaifTheme.colors.surfaceCard,
        contentColor = SaifTheme.colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(SaifTheme.colors.cardBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Select Assistant Persona",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SaifTheme.colors.textPrimary
            )
            Text(
                text = "Switch modes to tailor SAIF AI's reasoning style and focus.",
                fontSize = 12.5.sp,
                color = SubtextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 28.dp)
            ) {
                ASSISTANT_MODES.forEach { mode ->
                    val isSelected = currentMode == mode.id

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) PrimaryAccent.copy(alpha = 0.1f) else SaifTheme.colors.primaryBackground,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isSelected) androidx.compose.ui.graphics.SolidColor(PrimaryAccent) else androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(
                                    SaifTheme.colors.textPrimary.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    SaifTheme.colors.textPrimary.copy(alpha = 0.1f)
                                )
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectMode(mode.id)
                                onDismiss()
                            }
                            .testTag("mode_option_${mode.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.icon,
                                fontSize = 24.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) PrimaryAccent else SaifTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else SaifTheme.colors.cardBorder.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = mode.badge,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) PrimaryAccent else SubtextMuted,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = mode.description,
                                    fontSize = 12.sp,
                                    color = SaifTheme.colors.textSecondary,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            if (isSelected) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryAccent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
