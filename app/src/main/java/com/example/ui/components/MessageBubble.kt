package com.example.ui.components
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Download
import com.example.util.ImageDownloader
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifAuraGradient
import com.example.ui.theme.SaifUserBubbleBrush
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.SubtextMuted
import com.example.ui.theme.UserBubbleText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    isLatestAiMessage: Boolean,
    onToggleBookmark: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isUser = message.role == "user"
    var isCopied by remember { mutableStateOf(false) }

    val currentlySpeaking by com.example.utils.TTSManager.currentlySpeakingText.collectAsState()
    val isThisSpeaking = currentlySpeaking == message.content

    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appeared = true
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "bubbleAlpha"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bubbleScale"
    )
    val animatedTranslationY by animateFloatAsState(
        targetValue = if (appeared) 0f else 18f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubbleY"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
                translationY = animatedTranslationY
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User message bubble - Liquid Glass Pebble with Specular Highlight
            Surface(
                shape = RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = 22.dp,
                    bottomStart = 22.dp,
                    bottomEnd = 6.dp
                ),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.52f),
                            Color(0x3538BDF8),
                            Color.White.copy(alpha = 0.15f)
                        )
                    )
                ),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomStart = 22.dp,
                            bottomEnd = 6.dp
                        )
                    )
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF4F46E5), Color(0xFF7C3AED))
                        )
                    )
                    .testTag("user_message_bubble")
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    message.attachmentUris?.split(",")?.filter { it.isNotBlank() }?.forEach { uriString ->
                        var showPreview by remember { mutableStateOf(false) }
                        coil.compose.AsyncImage(
                            model = android.net.Uri.parse(uriString),
                            contentDescription = "Attached image",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showPreview = true }
                                .padding(bottom = 8.dp)
                        )
                        if (showPreview) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showPreview = false },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    coil.compose.AsyncImage(
                                        model = android.net.Uri.parse(uriString),
                                        contentDescription = "Preview",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { showPreview = false },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).padding(top = 24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            color = UserBubbleText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Timestamp
            Text(
                text = formattedTime,
                color = SubtextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, end = 4.dp)
            )
        } else {
            // AI Assistant Message - Liquid Frosted Glass Container
            val isLightMode = SaifTheme.colors.isLight
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Mini SAIF Emblem Avatar with Glowing Aura
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 4.dp, end = 8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.sweepGradient(
                                listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFF818CF8),
                                    Color(0xFFA855F7),
                                    Color(0xFFEC4899),
                                    Color(0xFF38BDF8)
                                )
                            )
                        )
                ) {
                    Text(
                        text = "S",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Bubble container: Sleek AI Message Card
                val isApiError = message.content.startsWith("❌") || message.content.contains("API Error", ignoreCase = true)
                val cardBorder = if (isApiError) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444))
                } else {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                    )
                }
                val cardColor = if (isApiError) {
                    if (isLightMode) Color(0xFFFEE2E2) else Color(0xFF2B1214)
                } else {
                    if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF131826)
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardColor,
                        border = cardBorder,
                        shadowElevation = if (isLightMode) 1.dp else 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp, top = 2.dp, bottom = 4.dp)
                            .testTag("ai_message_bubble")
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            MarkdownRenderer(content = message.content)
                        }
                    }

                    // Action buttons row below AI message
                    Row(
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formattedTime,
                            color = SubtextMuted,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Copy Action
                        ActionIcon(
                            icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = if (isCopied) StatusSuccess else SubtextMuted,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SAIF AI response", message.content)
                                clipboard.setPrimaryClip(clip)
                                isCopied = true
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    delay(2000)
                                    isCopied = false
                                }
                            }
                        )

                        // Bookmark / Star Action
                        ActionIcon(
                            icon = if (message.isBookmarked) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Star message",
                            tint = if (message.isBookmarked) Color(0xFFF59E0B) else SubtextMuted,
                            onClick = onToggleBookmark
                        )

                        // Share Action
                        // Share Action
                        ActionIcon(
                            icon = Icons.Default.Share,
                            contentDescription = "Share message",
                            tint = SubtextMuted,
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.content)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share SAIF AI response")
                                context.startActivity(shareIntent)
                            }
                        )
                        
                        // Download Action (if image exists)
                        val imgRegex = Regex("""!\[(.*?)\]\((.*?)\)""")
                        val match = imgRegex.find(message.content)
                        if (match != null) {
                            val imageUrl = match.groupValues.getOrNull(2)
                            if (imageUrl != null) {
                                ActionIcon(
                                    icon = Icons.Default.Download,
                                    contentDescription = "Download image",
                                    tint = SubtextMuted,
                                    onClick = {
                                        scope.launch {
                                            Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                                            ImageDownloader.downloadImageToGallery(context, imageUrl)
                                        }
                                    }
                                )
                            }
                        }

                        // Regenerate Action (only available for latest AI message)
                        if (isLatestAiMessage) {
                            ActionIcon(
                                icon = Icons.Default.Refresh,
                                contentDescription = "Regenerate response",
                                tint = PrimaryAccent,
                                onClick = onRegenerate
                            )
                        }

                        // Speak / Sound Action to speak AI answer using selected voice
                        ActionIcon(
                            icon = if (isThisSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isThisSpeaking) "Stop speaking" else "Read aloud",
                            tint = if (isThisSpeaking) PrimaryAccent else SubtextMuted,
                            onClick = {
                                if (isThisSpeaking) {
                                    com.example.utils.TTSManager.stop()
                                } else {
                                    com.example.utils.TTSManager.speak(message.content, context)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.72f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ActionIconScale"
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}

