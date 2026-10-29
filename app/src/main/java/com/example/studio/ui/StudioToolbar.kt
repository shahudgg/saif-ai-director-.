package com.example.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import com.example.studio.state.StudioUiState

@Composable
fun StudioToolbar(
    state: StudioUiState,
    onSplit: () -> Unit,
    onTrimLeft: () -> Unit,
    onTrimRight: () -> Unit,
    onDuplicate: () -> Unit,
    onBringForward: () -> Unit,
    onSendBackward: () -> Unit,
    onDelete: () -> Unit,
    onToggleTune: () -> Unit,
    onAddMedia: () -> Unit,
    onAddText: () -> Unit,
    onOpenAiVideo: () -> Unit = {},
    onOpenAiVoice: () -> Unit = {},
    onOpenCodeEngine: () -> Unit = {},
    onOpenAiPro: () -> Unit = {},
    onOpenSaifAi: () -> Unit = onOpenAiPro,
    onAddMarker: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isItemSelected = state.selectedItemId != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF0F0D15))
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isItemSelected) {
            // Contextual Actions for Selected Card
            ToolbarButton(
                icon = Icons.Default.Tune,
                label = "Tune",
                accentColor = Color(0xFF00E5FF),
                onClick = onToggleTune
            )

            ToolbarButton(
                icon = Icons.Default.CallSplit,
                label = "Split",
                accentColor = Color(0xFFA855F7),
                onClick = onSplit
            )

            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Trim Left",
                onClick = onTrimLeft
            )

            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = "Trim Right",
                onClick = onTrimRight
            )

            // Center SAIF AI Feature Button
            SaifAiToolbarBadge(onClick = onOpenSaifAi)

            ToolbarButton(
                icon = Icons.Default.ContentCopy,
                label = "Duplicate",
                onClick = onDuplicate
            )

            ToolbarButton(
                icon = Icons.Default.FlipToFront,
                label = "Forward",
                onClick = onBringForward
            )

            ToolbarButton(
                icon = Icons.Default.FlipToBack,
                label = "Backward",
                onClick = onSendBackward
            )

            ToolbarButton(
                icon = Icons.Default.Delete,
                label = "Delete",
                accentColor = Color(0xFFEF4444),
                onClick = onDelete
            )

        } else {
            // Default Timeline Navigation Actions
            ToolbarButton(
                icon = Icons.Default.Add,
                label = "Import",
                accentColor = Color(0xFF8B5CF6),
                onClick = onAddMedia
            )

            ToolbarButton(
                icon = Icons.Default.TextFields,
                label = "Text",
                accentColor = Color(0xFFF59E0B),
                onClick = onAddText
            )

            ToolbarButton(
                icon = Icons.Default.BookmarkBorder,
                label = "Marker",
                onClick = onAddMarker
            )

            // --- Prominent Center SAIF AI Feature Button ---
            SaifAiToolbarBadge(onClick = onOpenSaifAi)

            ToolbarButton(
                icon = Icons.Default.FitScreen,
                label = "Fit",
                onClick = onFit
            )

            ToolbarButton(
                icon = Icons.Default.ZoomOut,
                label = "Zoom -",
                onClick = onZoomOut
            )

            ToolbarButton(
                icon = Icons.Default.ZoomIn,
                label = "Zoom +",
                onClick = onZoomIn
            )
        }
    }
}

@Composable
private fun SaifAiToolbarBadge(
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1735),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF7B2BFF),
                    Color(0xFF00E5FF),
                    Color(0xFF7B2BFF)
                )
            )
        ),
        shadowElevation = 8.dp,
        modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "SAIF AI",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SAIF AI",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    accentColor: Color = Color(0xFFE5E2E1),
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp) // Minimum 48dp touch target
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isEnabled) { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isEnabled) accentColor else Color(0xFF4A4458),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isEnabled) Color(0xFFE5E2E1) else Color(0xFF4A4458),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
