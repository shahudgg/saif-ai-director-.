

package com.example.ui.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LiquidGlassDefaults
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.liquidBounceClickable
import kotlinx.coroutines.launch

@OptIn(ExperimentalTextApi::class)
@Composable
fun SaifHeader(
    currentMode: String,
    isLightMode: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenModeSelector: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlusClick: () -> Unit,
    onNewVideoProjectClick: () -> Unit = {},
    /** Kept for compatibility: the red "+" is gone, this now opens SAIF AI's live-conversation mode sheet. */
    onAutomationClick: () -> Unit = {},
    isFilterBookmarked: Boolean,
    onToggleBookmarkFilter: () -> Unit,
    onUpgradeClick: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    /** Round 15: 🧰 opens SAIF POWER TOOLS (self test, crash log, 15 utilities). */
    onOpenPowerTools: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Phones are narrow: below 480 dp the header switches to a compact layout.
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 480
    val iconBtn = if (compact) 32.dp else 36.dp

    // Flowing liquid iridescent text gradient
    val infiniteTransition = rememberInfiniteTransition(label = "saif_title_liquid")
    val titlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "titlePhase"
    )

    val saifGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF8B5CF6), // Purple
            Color(0xFF3B82F6), // Blue
            Color(0xFF06B6D4), // Cyan
            Color(0xFFEC4899), // Pink
            Color(0xFF8B5CF6)  // Wrap
        ),
        startX = titlePhase,
        endX = titlePhase + 600f
    )

    // Bouncy "water drop" animation state for the menu icon
    val interactionSourceMenu = remember { MutableInteractionSource() }
    val isMenuPressed by interactionSourceMenu.collectIsPressedAsState()
    val scaleMenu by animateFloatAsState(
        targetValue = if (isMenuPressed) 0.72f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WaterDropBounce"
    )

    // Liquid glide animation states for Upgrade button
    val upgradeScaleX = remember { Animatable(1f) }
    val upgradeScaleY = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0f) }
    val glowScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Status bar safe area padding + floating margin so header NEVER touches time/battery/cutout
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
    ) {
        // Floating Header Capsule Bar
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF131826),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
            ),
            shadowElevation = if (isLightMode) 2.dp else 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Drawer Menu & Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Water drop bouncing icon wrapper
                    Box(
                        modifier = Modifier
                            .size(if (compact) 38.dp else 42.dp)
                            .scale(scaleMenu)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSourceMenu,
                                indication = androidx.compose.foundation.LocalIndication.current,
                                onClick = onOpenDrawer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = SaifTheme.colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(if (compact) 2.dp else 6.dp))

                    Text(
                        text = "SAIF AI",
                        style = TextStyle(
                            brush = saifGradient,
                            fontSize = if (compact) 17.sp else 19.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Right side: Admin Crown, Upgrade, Theme, Settings, Purple Video Plus, Orange App Plus, Red Automation Plus
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)
                ) {
                    // Admin Crown Button (Shows only for Admin role)
                    val currentAuthUser by com.example.data.local.AuthManager.currentUser.collectAsState()
                    if (currentAuthUser?.isAdmin == true) {
                        Surface(
                            onClick = onOpenAdminDashboard,
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0x33F59E0B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.8f)),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = "👑", fontSize = 11.sp)
                                if (!compact) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Admin",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Upgrade Pill with Liquid Glide Animation
                    Box(contentAlignment = Alignment.Center) {
                        // Explosion Glow
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .scale(glowScale.value)
                                .background(Color(0xFFA855F7).copy(alpha = glowAlpha.value), RoundedCornerShape(18.dp))
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isLightMode) Color(0x20A855F7) else Color(0x22A855F7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66A855F7)),
                            modifier = Modifier
                                .scale(upgradeScaleX.value, upgradeScaleY.value)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        // Glow blast
                                        launch {
                                            glowAlpha.snapTo(0.6f)
                                            glowScale.snapTo(1f)
                                            launch { glowScale.animateTo(1.6f, animationSpec = tween(400, easing = FastOutSlowInEasing)) }
                                            glowAlpha.animateTo(0f, animationSpec = tween(400))
                                        }

                                        // Liquid glide (Squish horizontally, then bounce back)
                                        launch { upgradeScaleX.animateTo(1.12f, animationSpec = tween(150, easing = FastOutSlowInEasing)) }
                                        upgradeScaleY.animateTo(0.85f, animationSpec = tween(150, easing = FastOutSlowInEasing))

                                        launch { upgradeScaleX.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                        upgradeScaleY.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))

                                        onUpgradeClick()
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "Upgrade",
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (!compact) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Upgrade",
                                        color = Color(0xFFA855F7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Theme Icon Animation
                    val themeInteractionSource = remember { MutableInteractionSource() }
                    val isThemePressed by themeInteractionSource.collectIsPressedAsState()

                    val themeScale by animateFloatAsState(
                        targetValue = if (isThemePressed) 0.65f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "ThemeScale"
                    )

                    val themeRotation by animateFloatAsState(
                        targetValue = if (isLightMode) 180f else 0f,
                        animationSpec = tween(700, easing = FastOutSlowInEasing),
                        label = "ThemeRotation"
                    )

                    IconButton(
                        onClick = onToggleTheme,
                        interactionSource = themeInteractionSource,
                        modifier = Modifier
                            .size(iconBtn)
                            .graphicsLayer {
                                scaleX = themeScale
                                scaleY = themeScale
                                rotationZ = themeRotation
                            }
                    ) {
                        AnimatedContent(
                            targetState = isLightMode,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "ThemeIcon"
                        ) { light ->
                            Icon(
                                imageVector = if (light) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Theme",
                                tint = SaifTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Power tools (Round 15): every subsystem's status in one place.
                    IconButton(onClick = onOpenPowerTools, modifier = Modifier.size(iconBtn)) {
                        Text("🧰", fontSize = 15.sp)
                    }

                    // Settings Button
                    val settingsInteraction = remember { MutableInteractionSource() }
                    val isSettingsPressed by settingsInteraction.collectIsPressedAsState()

                    val settingsScale by animateFloatAsState(
                        targetValue = if (isSettingsPressed) 0.72f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
                        label = "SettingsScale"
                    )
                    val settingsRotation by animateFloatAsState(
                        targetValue = if (isSettingsPressed) 90f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "SettingsRotation"
                    )

                    IconButton(
                        onClick = onOpenSettings,
                        interactionSource = settingsInteraction,
                        modifier = Modifier
                            .size(iconBtn)
                            .scale(settingsScale)
                            .graphicsLayer {
                                rotationZ = settingsRotation
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = SaifTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 1. Purple Video Studio Plus Button (AI Video Director & Video Project Studio)
                    val videoPlusInteraction = remember { MutableInteractionSource() }
                    val isVideoPlusPressed by videoPlusInteraction.collectIsPressedAsState()
                    val videoPlusScale by animateFloatAsState(
                        targetValue = if (isVideoPlusPressed) 0.80f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
                        label = "VideoPlusScale"
                    )
                    IconButton(
                        onClick = onNewVideoProjectClick,
                        interactionSource = videoPlusInteraction,
                        modifier = Modifier
                            .size(iconBtn)
                            .scale(videoPlusScale)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x33A855F7),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC084FC)),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF7C3AED), Color(0xFFA855F7), Color(0xFF06B6D4))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Video Project Studio (+ Video)",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // 2. Original Orange App Creator Plus Button (Apk & App Creation Project)
                    val appPlusInteraction = remember { MutableInteractionSource() }
                    val isAppPlusPressed by appPlusInteraction.collectIsPressedAsState()
                    val appPlusScale by animateFloatAsState(
                        targetValue = if (isAppPlusPressed) 0.80f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
                        label = "AppPlusScale"
                    )
                    IconButton(
                        onClick = onPlusClick,
                        interactionSource = appPlusInteraction,
                        modifier = Modifier
                            .size(iconBtn)
                            .scale(appPlusScale)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x33F97316),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFB923C).copy(alpha = 0.9f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFEA580C), Color(0xFFF97316))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New App Project (+ App)",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // The red SAIF AI AUTOMATION "+" button was REMOVED in Round 9: the whole automation engine
                    // now lives inside SAIF AI's own live-conversation feature (the purple mic next to the input
                    // bar), so there is no second front door and no separate credit. `onAutomationClick` is kept
                    // only so older callers still compile; it opens the same live mode sheet.
                }
            }
        }
    }
}

