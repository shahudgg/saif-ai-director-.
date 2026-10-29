package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AdminConfigManager
import com.example.data.local.AdminGlobalConfig
import com.example.data.local.AuthManager
import com.example.data.local.ManagedUser
import kotlinx.coroutines.launch

enum class AdminTab(val title: String, val icon: ImageVector) {
    ANALYTICS("Analytics", Icons.Outlined.Analytics),
    USERS("Users", Icons.Outlined.People),
    API_GATEWAY("API Fleet", Icons.Outlined.VpnKey),
    BROADCAST("Broadcast", Icons.Outlined.Campaign)
}

/**
 * Ultra-Modern Liquid Glass Admin Control Center
 * Features dynamic fluid aurora refraction, iridescent rotating specular borders,
 * spring-physics touch feedback, and high-fidelity real-time telemetry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val globalConfig by AdminConfigManager.globalConfig.collectAsState()
    val managedUsers by AdminConfigManager.managedUsers.collectAsState()
    val isLoadingUsers by AdminConfigManager.isLoadingUsers.collectAsState()
    val currentUser by AuthManager.currentUser.collectAsState()

    var selectedTab by remember { mutableStateOf(AdminTab.ANALYTICS) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Fetch latest user list & config on mount
    LaunchedEffect(Unit) {
        AdminConfigManager.fetchAllUsers()
    }

    // High Animation Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "admin_liquid_motion")

    // Iridescent chromatic sweep angle
    val liquidAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chromatic_angle"
    )

    // Breathing float for Master Badge
    val badgeFloat by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_float"
    )

    // Pulsing glow for live radar beacon
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_pulse"
    )

    // Refresh spin animation
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    fun refreshAll() {
        scope.launch {
            isRefreshing = true
            AdminConfigManager.fetchAllUsers()
            AuthManager.refreshCurrentUserData()
            isRefreshing = false
            Toast.makeText(context, "Data synced from cloud & local DB", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // --- 1. Dynamic Liquid Aurora Background with Morphing Fluid Orbs ---
        LiquidGlassBackground(modifier = Modifier.fillMaxSize())

        // Ambient dark frosted overlay for crisp text contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xD0060913),
                            Color(0xB8090D1A),
                            Color(0xDC060913)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // --- 2. Liquid Glass Header Bar with Specular Highlights ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                        ambientColor = Color(0x607C3AED),
                        spotColor = Color(0x8006B6D4)
                    )
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xE60E1528),
                                Color(0xD8121B33),
                                Color(0xEE0A0F1E)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0x40A78BFA),
                                    Color(0x6038BDF8),
                                    Color(0x30EC4899),
                                    Color(0x508B5CF6)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                    )
            ) {
                // Top Specular Highlight Line
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .align(Alignment.TopCenter)
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.5f),
                                Color(0xFF67E8F9).copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button with Spring Bounce
                    LiquidGlassIconButton(
                        onClick = onClose,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        containerColor = Color(0x28FFFFFF),
                        borderColor = Color(0x40FFFFFF)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Admin Control Center",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Shimmering Master Crown Badge
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { translationY = badgeFloat }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF8B5CF6),
                                                Color(0xFF6366F1),
                                                Color(0xFFEC4899)
                                            )
                                        )
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "👑 MASTER",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Text(
                            text = "Connected: ${currentUser?.email ?: "Admin"}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Refresh Button with Spin Animation
                    LiquidGlassIconButton(
                        onClick = { refreshAll() },
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFFA78BFA),
                        containerColor = Color(0x288B5CF6),
                        borderColor = Color(0x60A78BFA),
                        iconModifier = if (isRefreshing || isLoadingUsers) Modifier.rotate(spinAngle) else Modifier
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sign Out Button
                    LiquidGlassIconButton(
                        onClick = { AuthManager.signOut() },
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = "Sign Out",
                        tint = Color(0xFFFCA5A5),
                        containerColor = Color(0x28EF4444),
                        borderColor = Color(0x60EF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 3. Floating Glassmorphic Tab Navigation Pill Bar ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x40000000),
                        spotColor = Color(0x607C3AED)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xCC0C1222),
                                Color(0xBA10182E),
                                Color(0xCC0A0E1A)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0x35A78BFA),
                                    Color(0x2038BDF8),
                                    Color(0x35A78BFA)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AdminTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.94f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "tab_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(scale)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF8B5CF6),
                                                Color(0xFF6366F1),
                                                Color(0xFF06B6D4)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(Color.Transparent, Color.Transparent)
                                        )
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0x90FFFFFF) else Color(0x15FFFFFF)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    selectedTab = tab
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- 4. Animated Tab Content with Fluid Glass Transition ---
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInHorizontally(
                        spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    ) { it / 3 }).togetherWith(
                        fadeOut(tween(180)) + slideOutHorizontally { -it / 3 }
                    )
                },
                label = "admin_tab_content",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    AdminTab.ANALYTICS -> AdminAnalyticsTab(
                        config = globalConfig,
                        users = managedUsers,
                        radarPulse = radarPulse,
                        liquidAngle = liquidAngle,
                        onNavigateToUsers = { selectedTab = AdminTab.USERS },
                        onNavigateToApi = { selectedTab = AdminTab.API_GATEWAY },
                        onNavigateToBroadcast = { selectedTab = AdminTab.BROADCAST }
                    )
                    AdminTab.USERS -> AdminUsersTab(
                        users = managedUsers,
                        isLoading = isLoadingUsers,
                        quotaLimit = globalConfig.testingQuotaPerUser
                    )
                    AdminTab.API_GATEWAY -> AdminApiGatewayTab(
                        currentConfig = globalConfig
                    )
                    AdminTab.BROADCAST -> AdminBroadcastTab(
                        currentConfig = globalConfig
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// REUSABLE LIQUID GLASS CARD CONTAINER WITH SPECULAR HIGHLIGHT
// ----------------------------------------------------------------------------
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    accentColor: Color = Color(0xFF8B5CF6),
    borderColor: Color = Color(0x35A78BFA),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.20f),
                spotColor = accentColor.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xD80F172A),
                        Color(0xC4131D34),
                        Color(0xE00B1120)
                    )
                )
            )
            .border(
                BorderStroke(1.2.dp, borderColor),
                shape = shape
            )
    ) {
        // Specular Top-Edge Reflection Line
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.8.dp)
                .align(Alignment.TopCenter)
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.45f),
                        accentColor.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                )
            )
        }

        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

// ----------------------------------------------------------------------------
// LIQUID GLASS ICON BUTTON WITH ELASTIC BOUNCE
// ----------------------------------------------------------------------------
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp,
    iconModifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "btn_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize).then(iconModifier)
        )
    }
}

// ----------------------------------------------------------------------------
// TAB 1: OVERVIEW & REAL-TIME ANALYTICS
// ----------------------------------------------------------------------------
@Composable
private fun AdminAnalyticsTab(
    config: AdminGlobalConfig,
    users: List<ManagedUser>,
    radarPulse: Float,
    liquidAngle: Float,
    onNavigateToUsers: () -> Unit,
    onNavigateToApi: () -> Unit,
    onNavigateToBroadcast: () -> Unit
) {
    val totalUsers = users.size
    val activeUsers = users.count { it.isActive }
    val bannedUsers = users.count { !it.isActive }
    val adminUsers = users.count { it.isAdmin }
    val totalApiCalls = config.totalGlobalApiCalls
    val totalUserPrompts = users.sumOf { it.apiUsageCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Dynamic System Health Liquid Glass Card with Pulsing Radar ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFF10B981),
            borderColor = Color(0x6010B981)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Live Radar Beacon
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SYSTEM STATUS: OPERATIONAL ⚡",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Global AI Fleet Overview",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Glowing Radar Beacon Circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x2210B981))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Color(0xFF10B981).copy(alpha = radarPulse)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Online",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Aapke SAIF AI studio me Admin Control active hai. Default API se har naye user ko testing access milta hai, aur users apne personal keys se unlimited use kar sakte hain.",
                color = Color(0xFFCBD5E1),
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
        }

        // --- 2. Interactive KPI Stat Cards Grid (2x2) with Liquid Glass Depth ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidKpiStatCard(
                title = "Total Users",
                value = "$totalUsers",
                subtitle = "$activeUsers Active • $bannedUsers Banned",
                icon = Icons.Outlined.People,
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToUsers
            )

            LiquidKpiStatCard(
                title = "Global API Calls",
                value = "$totalApiCalls",
                subtitle = "$totalUserPrompts Fleet Prompts",
                icon = Icons.Outlined.Analytics,
                accentColor = Color(0xFFA78BFA),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToApi
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidKpiStatCard(
                title = "Testing Quota / User",
                value = "${config.testingQuotaPerUser} calls",
                subtitle = "Shared Fleet Limit",
                icon = Icons.Outlined.Speed,
                accentColor = Color(0xFFF472B6),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToApi
            )

            LiquidKpiStatCard(
                title = "Fleet Provider",
                value = config.defaultProvider,
                subtitle = config.defaultModel.take(16),
                icon = Icons.Outlined.Cloud,
                accentColor = Color(0xFF34D399),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToApi
            )
        }

        // --- 3. Quick Control Shortcuts with Fluid Motion ---
        Text(
            text = "⚡ Quick Control Center Shortcuts",
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
        )

        LiquidShortcutCard(
            title = "Manage Registered Users",
            description = "Inspect user accounts, ban/suspend misbehaving accounts, promote admins, or tune individual VIP limits.",
            icon = Icons.Default.ManageAccounts,
            accentColor = Color(0xFF38BDF8),
            buttonLabel = "Open Users ($totalUsers)",
            onClick = onNavigateToUsers
        )

        LiquidShortcutCard(
            title = "Admin Master API & User Quotas",
            description = "Configure system default API key, choose inceptionlabs.ai / api.atria-asi.ai / Gemini, and set multi-tier free limits.",
            icon = Icons.Default.VpnKey,
            accentColor = Color(0xFFA78BFA),
            buttonLabel = "Configure API Gateway",
            onClick = onNavigateToApi
        )

        LiquidShortcutCard(
            title = "Global Announcement Banner",
            description = "Broadcast an instant alert or update notice to all active users on the app home screen.",
            icon = Icons.Default.Campaign,
            accentColor = Color(0xFFFBBF24),
            buttonLabel = "Send Broadcast",
            onClick = onNavigateToBroadcast
        )
    }
}

@Composable
private fun LiquidKpiStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "kpi_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = accentColor.copy(alpha = 0.20f),
                spotColor = accentColor.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xD80E162A),
                        Color(0xC4121C34),
                        Color(0xE00A1020)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, accentColor.copy(alpha = 0.40f)),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Specular Reflection Line
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.TopCenter)
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.4f),
                        accentColor.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
        }

        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f))
                        .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiquidShortcutCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    buttonLabel: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "shortcut_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = accentColor.copy(alpha = 0.15f),
                spotColor = accentColor.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xD00F172A),
                        Color(0xC0131E34),
                        Color(0xD80B1120)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, Color(0x28A78BFA)),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Specular Line
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.TopCenter)
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.35f),
                        accentColor.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
        }

        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.22f))
                    .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)), shape = RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(23.dp)
                )
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buttonLabel,
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 2: USER MANAGEMENT WITH LIQUID GLASS CARDS & DIALOGS
// ----------------------------------------------------------------------------
enum class UserFilter { ALL, ACTIVE, BANNED, ADMINS }

@Composable
private fun AdminUsersTab(
    users: List<ManagedUser>,
    isLoading: Boolean,
    quotaLimit: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(UserFilter.ALL) }
    var actionInProgressUid by remember { mutableStateOf<String?>(null) }
    var userToDelete by remember { mutableStateOf<ManagedUser?>(null) }
    var userToEditLimits by remember { mutableStateOf<ManagedUser?>(null) }

    val filteredUsers = remember(users, searchQuery, selectedFilter) {
        users.filter { user ->
            val matchesSearch = searchQuery.isBlank() ||
                    user.name.contains(searchQuery, ignoreCase = true) ||
                    user.email.contains(searchQuery, ignoreCase = true) ||
                    user.uid.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                UserFilter.ALL -> true
                UserFilter.ACTIVE -> user.isActive
                UserFilter.BANNED -> !user.isActive
                UserFilter.ADMINS -> user.isAdmin
            }
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // --- 1. Frosted Search Bar ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search users by name, email, or UID...", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFA78BFA)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0x350F1626),
                unfocusedContainerColor = Color(0x220F1626),
                focusedBorderColor = Color(0xFF8B5CF6),
                unfocusedBorderColor = Color(0x30334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. Frosted Glass Filter Chips ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidFilterChip(
                label = "All (${users.size})",
                selected = selectedFilter == UserFilter.ALL,
                onClick = { selectedFilter = UserFilter.ALL },
                accentColor = Color(0xFF8B5CF6)
            )
            LiquidFilterChip(
                label = "Active (${users.count { it.isActive }})",
                selected = selectedFilter == UserFilter.ACTIVE,
                onClick = { selectedFilter = UserFilter.ACTIVE },
                accentColor = Color(0xFF10B981)
            )
            LiquidFilterChip(
                label = "Suspended (${users.count { !it.isActive }})",
                selected = selectedFilter == UserFilter.BANNED,
                onClick = { selectedFilter = UserFilter.BANNED },
                accentColor = Color(0xFFEF4444)
            )
            LiquidFilterChip(
                label = "Admins (${users.count { it.isAdmin }})",
                selected = selectedFilter == UserFilter.ADMINS,
                onClick = { selectedFilter = UserFilter.ADMINS },
                accentColor = Color(0xFFF59E0B)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading && users.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF8B5CF6))
            }
        } else if (filteredUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.PersonSearch,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No users found", color = Color(0xFF94A3B8), fontSize = 13.5.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredUsers, key = { it.uid.ifBlank { it.email } }) { user ->
                    LiquidUserManagementCard(
                        user = user,
                        quotaLimit = quotaLimit,
                        isProcessing = actionInProgressUid == user.uid,
                        onToggleStatus = { newActive ->
                            scope.launch {
                                actionInProgressUid = user.uid
                                val res = AdminConfigManager.updateUserStatus(user, newActive)
                                actionInProgressUid = null
                                if (res.isSuccess) {
                                    val actText = if (newActive) "activated / unbanned" else "suspended / banned"
                                    Toast.makeText(context, "${user.name} has been $actText", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onToggleRole = { newRole ->
                            scope.launch {
                                actionInProgressUid = user.uid
                                val res = AdminConfigManager.updateUserRole(user, newRole)
                                actionInProgressUid = null
                                if (res.isSuccess) {
                                    Toast.makeText(context, "${user.name} role changed to $newRole", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onResetQuota = {
                            scope.launch {
                                actionInProgressUid = user.uid
                                val res = AdminConfigManager.resetUserQuota(user)
                                actionInProgressUid = null
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Quota reset to 0 for ${user.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteClick = {
                            userToDelete = user
                        },
                        onEditLimits = {
                            userToEditLimits = user
                        },
                        onCopyEmail = {
                            clipboardManager.setText(AnnotatedString(user.email))
                            Toast.makeText(context, "Email copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // --- Custom Limits Dialog with Liquid Glass Styling ---
    if (userToEditLimits != null) {
        val target = userToEditLimits!!
        var customChat by remember(target) { mutableIntStateOf(target.customChatLimit ?: 20) }
        var customImage by remember(target) { mutableIntStateOf(target.customImageLimit ?: 5) }
        var customBuild by remember(target) { mutableIntStateOf(target.customAppBuildLimit ?: 10) }
        var customTotal by remember(target) { mutableIntStateOf(target.customTotalLimit ?: quotaLimit) }
        var isVipUnlimited by remember(target) { mutableStateOf(target.isUnlimited) }

        AlertDialog(
            onDismissRequest = { userToEditLimits = null },
            containerColor = Color(0xF20F1626),
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VIP Quota Tuning: ${target.name}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Aap is specific user (${target.email}) ke liye alag limits set kar sakte hain ya VIP Unlimited access de sakte hain.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    // VIP Unlimited Switch Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isVipUnlimited) Color(0x3310B981) else Color(0x221E2638))
                            .border(BorderStroke(1.dp, if (isVipUnlimited) Color(0xFF10B981) else Color(0x33FFFFFF)), shape = RoundedCornerShape(12.dp))
                            .clickable { isVipUnlimited = !isVipUnlimited }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("👑 VIP Unlimited Access", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Sabhi limits hat jayengi (No restrictions)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            Switch(
                                checked = isVipUnlimited,
                                onCheckedChange = { isVipUnlimited = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF10B981))
                            )
                        }
                    }

                    if (!isVipUnlimited) {
                        // Custom Chat Limit
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("💬 Custom Chat Limit: $customChat", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(10, 20, 50, 100, 200).forEach { p ->
                                    LiquidFilterChip(
                                        label = "$p",
                                        selected = customChat == p,
                                        onClick = { customChat = p },
                                        accentColor = Color(0xFF8B5CF6)
                                    )
                                }
                            }
                        }

                        // Custom Image Limit
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🎨 Custom Image Limit: $customImage", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(3, 5, 10, 25, 50).forEach { p ->
                                    LiquidFilterChip(
                                        label = "$p",
                                        selected = customImage == p,
                                        onClick = { customImage = p },
                                        accentColor = Color(0xFFEC4899)
                                    )
                                }
                            }
                        }

                        // Custom Build Limit
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("⚡ Custom App Build Limit: $customBuild", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5, 10, 20, 50).forEach { p ->
                                    LiquidFilterChip(
                                        label = "$p",
                                        selected = customBuild == p,
                                        onClick = { customBuild = p },
                                        accentColor = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }

                        // Custom Total Limit
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🌐 Custom Total Limit: $customTotal", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(15, 35, 75, 150, 300).forEach { p ->
                                    LiquidFilterChip(
                                        label = "$p",
                                        selected = customTotal == p,
                                        onClick = { customTotal = p },
                                        accentColor = Color(0xFF10B981)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminConfigManager.updateCustomUserLimits(
                                targetUser = target,
                                chatLimit = if (isVipUnlimited) null else customChat,
                                imageLimit = if (isVipUnlimited) null else customImage,
                                appBuildLimit = if (isVipUnlimited) null else customBuild,
                                totalLimit = if (isVipUnlimited) null else customTotal,
                                isUnlimited = isVipUnlimited
                            )
                            userToEditLimits = null
                            Toast.makeText(context, "Custom limits updated for ${target.name}!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Custom Limits", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToEditLimits = null }) {
                    Text("Cancel", color = Color(0xFFCBD5E1))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (userToDelete != null) {
        val target = userToDelete!!
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            containerColor = Color(0xF21A0F14),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete User Account?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("Are you sure you want to delete ${target.name} (${target.email})? This action cannot be undone.", color = Color(0xFFE2E8F0)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            AdminConfigManager.deleteUser(target)
                            userToDelete = null
                            Toast.makeText(context, "User deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Cancel", color = Color(0xFFCBD5E1))
                }
            }
        )
    }
}

@Composable
private fun LiquidFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chip_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) accentColor.copy(alpha = 0.28f) else Color(0x24151E30)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) accentColor else Color(0x28FFFFFF)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun LiquidUserManagementCard(
    user: ManagedUser,
    quotaLimit: Int,
    isProcessing: Boolean,
    onToggleStatus: (Boolean) -> Unit,
    onToggleRole: (String) -> Unit,
    onResetQuota: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditLimits: () -> Unit,
    onCopyEmail: () -> Unit
) {
    val initials = user.name.trim().take(2).uppercase().ifBlank { "U" }
    val isBanned = !user.isActive
    val usage = user.apiUsageCount
    val progress = if (quotaLimit > 0) (usage.toFloat() / quotaLimit).coerceIn(0f, 1f) else 0f

    val accentBorder = when {
        isBanned -> Color(0xFFEF4444).copy(alpha = 0.6f)
        user.isAdmin -> Color(0xFFF59E0B).copy(alpha = 0.6f)
        else -> Color(0x35A78BFA)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color(0x20000000),
                spotColor = accentBorder
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xD00E162A),
                        Color(0xC0121C34),
                        Color(0xD80A1020)
                    )
                )
            )
            .border(BorderStroke(1.dp, accentBorder), shape = RoundedCornerShape(18.dp))
    ) {
        // Specular highlight
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.TopCenter)
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.35f),
                        accentBorder,
                        Color.Transparent
                    )
                )
            )
        }

        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Avatar + Info + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (user.isAdmin) Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                            else Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF38BDF8)))
                        )
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (user.isAdmin) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x33F59E0B))
                                    .border(BorderStroke(1.dp, Color(0xFFF59E0B)), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "ADMIN",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = user.email,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onCopyEmail() }
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (user.isActive) Color(0x2210B981) else Color(0x22EF4444))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (user.isActive) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFFEF4444).copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (user.isActive) "ACTIVE" else "BANNED",
                        color = if (user.isActive) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quota Usage Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fleet Quota: $usage / $quotaLimit calls used",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
                if (usage >= quotaLimit) {
                    Text(
                        text = "Limit Reached",
                        color = Color(0xFFF59E0B),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progress >= 1f) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                trackColor = Color(0xFF1E2638)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row with Liquid Glass Feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Ban / Unban
                Button(
                    onClick = { onToggleStatus(!user.isActive) },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isActive) Color(0xFFDC2626) else Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Icon(
                        imageVector = if (user.isActive) Icons.Default.Block else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (user.isActive) "Ban" else "Unban",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Role Toggle
                OutlinedButton(
                    onClick = { onToggleRole(if (user.isAdmin) "user" else "admin") },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0x35A78BFA)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.1f).height(34.dp)
                ) {
                    Icon(
                        imageVector = if (user.isAdmin) Icons.Default.Person else Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (user.isAdmin) Color(0xFF94A3B8) else Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (user.isAdmin) "Demote" else "Admin",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Edit Custom Limits
                IconButton(
                    onClick = onEditLimits,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x3038BDF8))
                        .border(BorderStroke(1.dp, Color(0x6038BDF8)), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Edit Limits",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Reset Quota
                IconButton(
                    onClick = onResetQuota,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x308B5CF6))
                        .border(BorderStroke(1.dp, Color(0x608B5CF6)), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Quota",
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete
                IconButton(
                    onClick = onDeleteClick,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x30EF4444))
                        .border(BorderStroke(1.dp, Color(0x60EF4444)), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 3: API GATEWAY & FLEET QUOTA MANAGEMENT
// ----------------------------------------------------------------------------
@Composable
private fun AdminApiGatewayTab(
    currentConfig: AdminGlobalConfig
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val googleDefaultKey = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }.trim()
    val cleanGoogleDefault = if (googleDefaultKey.isNotBlank() && !googleDefaultKey.contains("MY_")) googleDefaultKey else ""

    var selectedProvider by remember { 
        mutableStateOf(
            if (currentConfig.defaultProvider.isBlank()) "Gemini" 
            else currentConfig.defaultProvider
        ) 
    }
    var apiKeyInput by remember { 
        mutableStateOf(
            if (currentConfig.defaultApiKey.isNotBlank() && !currentConfig.defaultApiKey.contains("MY_")) {
                currentConfig.defaultApiKey
            } else if (currentConfig.defaultProvider.equals("Gemini", ignoreCase = true)) {
                cleanGoogleDefault
            } else ""
        ) 
    }
    var modelInput by remember { 
        mutableStateOf(
            if (currentConfig.defaultModel.isBlank() || currentConfig.defaultModel.contains("2.5-flash")) {
                when (currentConfig.defaultProvider) {
                    "Groq" -> "llama-3.3-70b-versatile"
                    "OpenRouter" -> "google/gemini-2.0-flash-001"
                    "OpenAI" -> "gpt-4o-mini"
                    "DeepSeek" -> "deepseek-chat"
                    "Anthropic" -> "claude-3-5-sonnet-20241022"
                    "Mistral" -> "mistral-small-latest"
                    "Together" -> "meta-llama/Llama-3.3-70B-Instruct-Turbo"
                    "Cerebras" -> "llama-3.3-70b"
                    "xAI" -> "grok-2-latest"
                    "Perplexity" -> "sonar"
                    "inceptionlabs.ai" -> "mercury-2.5"
                    "api.atria-asi.ai" -> "Atria-Dawn-Preview"
                    "OpenCode.ai" -> "glm-5.1"
                    else -> "gemini-flash-latest"
                }
            } else currentConfig.defaultModel
        ) 
    }
    var chatLimitInput by remember { mutableIntStateOf(currentConfig.chatLimitPerUser) }
    var imageLimitInput by remember { mutableIntStateOf(currentConfig.imageLimitPerUser) }
    var appBuildLimitInput by remember { mutableIntStateOf(currentConfig.appBuildLimitPerUser) }
    var totalLimitInput by remember { mutableIntStateOf(currentConfig.totalApiLimitPerUser) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(currentConfig) {
        if (currentConfig.defaultProvider.isNotBlank()) {
            selectedProvider = currentConfig.defaultProvider
        }
        if (currentConfig.defaultApiKey.isNotBlank() && !currentConfig.defaultApiKey.contains("MY_")) {
            apiKeyInput = currentConfig.defaultApiKey
        } else if (currentConfig.defaultProvider.equals("Gemini", ignoreCase = true)) {
            apiKeyInput = cleanGoogleDefault
        }
        if (currentConfig.defaultModel.isNotBlank()) {
            modelInput = currentConfig.defaultModel
        }
        chatLimitInput = currentConfig.chatLimitPerUser
        imageLimitInput = currentConfig.imageLimitPerUser
        appBuildLimitInput = currentConfig.appBuildLimitPerUser
        totalLimitInput = currentConfig.totalApiLimitPerUser
    }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultBanner by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val providersList = listOf(
        "Gemini", "Ashna AI", "Groq", "OpenRouter", "OpenAI", "DeepSeek", "Anthropic", 
        "Mistral", "Together", "Cerebras", "xAI", "Perplexity", "inceptionlabs.ai", "api.atria-asi.ai", "OpenCode.ai"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Liquid Glass Header Banner ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFF38BDF8),
            borderColor = Color(0x6038BDF8)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8B5CF6).copy(alpha = 0.4f), Color(0xFF38BDF8).copy(alpha = 0.4f))
                            )
                        )
                        .border(BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(13.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Fleet API & Quota Engine",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x3310B981))
                                .border(BorderStroke(1.dp, Color(0x6610B981)), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIQUID GLASS",
                                color = Color(0xFF6EE7B7),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Admin dwara set ki gayi API naye users ko secretly free me milti hai. User ko ye key dikhai nahi degi. Limit khatam hone par user ko apni API dalni hogi.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // --- 2. Provider Selector ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFF8B5CF6),
            borderColor = Color(0x40A78BFA)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select System Provider",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Click to configure",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providersList.forEach { provider ->
                    val isSelected = selectedProvider == provider
                    val isNew = provider == "inceptionlabs.ai" || provider == "api.atria-asi.ai" || provider == "Ashna AI"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color(0x35141D30), Color(0x35141D30))
                                    )
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFFC4B5FD) else if (isNew) Color(0x6638BDF8) else Color(0x25FFFFFF)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedProvider = provider
                                testResultBanner = null
                                modelInput = when (provider) {
                                    "Ashna AI" -> "ashna-x1"
                                    "Gemini" -> "gemini-flash-latest"
                                    "Groq" -> "llama-3.3-70b-versatile"
                                    "OpenRouter" -> "google/gemini-2.0-flash-001"
                                    "OpenAI" -> "gpt-4o-mini"
                                    "DeepSeek" -> "deepseek-chat"
                                    "Anthropic" -> "claude-3-5-sonnet-20241022"
                                    "Mistral" -> "mistral-small-latest"
                                    "Together" -> "meta-llama/Llama-3.3-70B-Instruct-Turbo"
                                    "Cerebras" -> "llama-3.3-70b"
                                    "xAI" -> "grok-2-latest"
                                    "Perplexity" -> "sonar"
                                    "inceptionlabs.ai" -> "mercury-2.5"
                                    "api.atria-asi.ai" -> "Atria-Dawn-Preview"
                                    "OpenCode.ai" -> "glm-5.1"
                                    else -> "gemini-flash-latest"
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                            if (isNew) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) Color.White.copy(alpha = 0.25f) else Color(0xFF0284C7).copy(alpha = 0.35f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "FAST ⚡",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.White else Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Endpoint Indicator Box
            val endpointDisplay = when (selectedProvider) {
                "Ashna AI" -> "🌐 Endpoint: https://api.ashna.ai/v1/api/chat/completions (Ashna AI)"
                "Gemini" -> "🌐 Endpoint: generativelanguage.googleapis.com/v1beta (Official Google)"
                "Groq" -> "🌐 Endpoint: https://api.groq.com/openai/v1/chat/completions (Ultra-Fast LPU)"
                "OpenRouter" -> "🌐 Endpoint: https://openrouter.ai/api/v1/chat/completions (Universal Gateway)"
                "OpenAI" -> "🌐 Endpoint: https://api.openai.com/v1/chat/completions (GPT-4o Mini/4o)"
                "DeepSeek" -> "🌐 Endpoint: https://api.deepseek.com/chat/completions (DeepSeek-V3/R1)"
                "Anthropic" -> "🌐 Endpoint: https://api.anthropic.com/v1/messages (Claude 3.5 Sonnet)"
                "Mistral" -> "🌐 Endpoint: https://api.mistral.ai/v1/chat/completions (Mistral AI)"
                "Together" -> "🌐 Endpoint: https://api.together.xyz/v1/chat/completions (Together AI)"
                "Cerebras" -> "🌐 Endpoint: https://api.cerebras.ai/v1/chat/completions (Wafer Scale AI)"
                "xAI" -> "🌐 Endpoint: https://api.x.ai/v1/chat/completions (Grok Models)"
                "Perplexity" -> "🌐 Endpoint: https://api.perplexity.ai/chat/completions (Sonar Search)"
                "inceptionlabs.ai" -> "🌐 Endpoint: https://api.inceptionlabs.ai/v1/chat/completions (Inception Labs)"
                "api.atria-asi.ai" -> "🌐 Endpoint: https://api.atria-asi.ai/v1/chat/completions (Atria ASI)"
                "OpenCode.ai" -> "🌐 Endpoint: https://opencode.ai/inference/openai/v1/chat/completions"
                else -> "🌐 Endpoint: OpenAI-compatible custom gateway"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x350D1525))
                    .border(BorderStroke(1.dp, Color(0x3038BDF8)), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = endpointDisplay,
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- 3. Master API Key & Model Input ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFF8B5CF6),
            borderColor = Color(0x40A78BFA)
        ) {
            // API Key Header & Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Master $selectedProvider API Key",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val getApiKeyUrl = when {
                        selectedProvider.contains("Ashna", ignoreCase = true) -> "https://app.ashna.ai/integrations?tab=api"
                        selectedProvider.contains("Gemini", ignoreCase = true) -> "https://aistudio.google.com/app/apikey"
                        selectedProvider.contains("OpenCode", ignoreCase = true) -> "https://opencode.ai"
                        selectedProvider.contains("OpenRouter", ignoreCase = true) -> "https://openrouter.ai/keys"
                        selectedProvider.contains("Groq", ignoreCase = true) -> "https://console.groq.com/keys"
                        selectedProvider.contains("OpenAI", ignoreCase = true) -> "https://platform.openai.com/api-keys"
                        selectedProvider.contains("DeepSeek", ignoreCase = true) -> "https://platform.deepseek.com/api_keys"
                        selectedProvider.contains("Anthropic", ignoreCase = true) -> "https://console.anthropic.com/settings/keys"
                        selectedProvider.contains("inception", ignoreCase = true) -> "https://inceptionlabs.ai"
                        selectedProvider.contains("atria", ignoreCase = true) -> "https://api.atria-asi.ai"
                        else -> null
                    }
                    if (getApiKeyUrl != null) {
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getApiKeyUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Get Key", color = Color(0xFF38BDF8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (apiKeyInput.isNotBlank()) {
                        TextButton(
                            onClick = { apiKeyInput = "" },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", color = Color(0xFFEF4444), fontSize = 11.5.sp)
                        }
                    }
                    TextButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                apiKeyInput = clip.trim()
                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", color = Color(0xFFA78BFA), fontSize = 11.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it; testResultBanner = null },
                placeholder = { Text("Paste $selectedProvider API Key here...", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Visibility",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x350F1626),
                    unfocusedContainerColor = Color(0x220F1626),
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0x30334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Model Identifier
            Text(
                text = "Model Identifier",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it; testResultBanner = null },
                placeholder = { Text("e.g. gemini-flash-latest, llama-3.3-70b-versatile, gpt-4o-mini", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x350F1626),
                    unfocusedContainerColor = Color(0x220F1626),
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0x30334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Preset Model Recommendation Chips
            val modelPresets = when (selectedProvider) {
                "Ashna AI" -> listOf("ashna-x1", "ashna-x1-flash", "gpt-4o-mini", "gemini-flash-latest", "deepseek-chat", "claude-3-5-sonnet")
                "Gemini" -> listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-lite-latest")
                "Groq" -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
                "OpenRouter" -> listOf("google/gemini-2.0-flash-001", "meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-chat")
                "OpenAI" -> listOf("gpt-4o-mini", "gpt-4o", "o3-mini")
                "DeepSeek" -> listOf("deepseek-chat", "deepseek-reasoner")
                "Anthropic" -> listOf("claude-3-5-sonnet-20241022", "claude-3-haiku-20240307")
                "Mistral" -> listOf("mistral-small-latest", "mistral-large-latest")
                "Together" -> listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3")
                "Cerebras" -> listOf("llama-3.3-70b", "llama3.1-8b")
                "xAI" -> listOf("grok-2-latest")
                "Perplexity" -> listOf("sonar", "sonar-pro")
                "inceptionlabs.ai" -> listOf("mercury-2.5", "mercury-2", "mercury-voice", "mercury-coder-small", "mercury-edit-2", "mercury-router")
                "api.atria-asi.ai" -> listOf("Atria-Dawn-Preview", "Atria-Dawn-Preview-FP8")
                "OpenCode.ai" -> listOf("glm-5.1", "deepseek-v4-flash")
                else -> listOf("gemini-flash-latest", "gpt-4o-mini", "llama-3.3-70b-versatile")
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modelPresets.forEach { preset ->
                    val isModelSelected = modelInput.trim() == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isModelSelected) Color(0x358B5CF6) else Color(0x221E293B))
                            .border(
                                BorderStroke(1.dp, if (isModelSelected) Color(0xFFA78BFA) else Color(0x25FFFFFF)),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                modelInput = preset
                                testResultBanner = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset,
                            color = if (isModelSelected) Color(0xFFC4B5FD) else Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Connection Button & Result
            Button(
                onClick = {
                    scope.launch {
                        isTestingConnection = true
                        testResultBanner = null
                        val res = AdminConfigManager.testApiKey(apiKeyInput, selectedProvider, modelInput)
                        isTestingConnection = false
                        if (res.isSuccess) {
                            testResultBanner = Pair(true, res.getOrNull() ?: "Connection OK")
                        } else {
                            testResultBanner = Pair(false, res.exceptionOrNull()?.message ?: "Test failed")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x381E2638)),
                border = BorderStroke(1.dp, Color(0x60A78BFA)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(color = Color(0xFFF59E0B), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying $selectedProvider...", color = Color.White, fontSize = 12.5.sp)
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test API Key Connection", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Test Result Banner
            if (testResultBanner != null) {
                val (isOk, msg) = testResultBanner!!
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isOk) Color(0x2610B981) else Color(0x26EF4444))
                        .border(BorderStroke(1.dp, if (isOk) Color(0xFF10B981) else Color(0xFFEF4444)), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (isOk) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = if (isOk) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- 4. Advance Multi-Tier User Quotas & Limits ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFF10B981),
            borderColor = Color(0x5010B981)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x3310B981))
                        .border(BorderStroke(1.dp, Color(0x6610B981)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Advance User Free Tier Limits",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Har feature ke liye alag limit set karein",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Aap yaha jo limit set karenge, har naya user bina apni key dale utna free me access kar sakega. Jab koi user kisi feature ki limit touch kar jayega, use warning popup milega aur Settings me jakar apni personal key lagane ko bola jayega. User apni key lagate hi sari limit hat jayegi.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Chat Limit Card
            LiquidQuotaSelectorCard(
                title = "💬 Chat Messages Limit",
                description = "User kitne chat messages bina API key ke bhej sakta hai",
                currentValue = chatLimitInput,
                unit = "chats",
                presets = listOf(5, 10, 20, 50, 100, 200),
                onValueChange = { chatLimitInput = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Image Generation Limit Card
            LiquidQuotaSelectorCard(
                title = "🎨 Image Generation Limit",
                description = "Kitni images user bina personal API key ke generate kar sakta hai",
                currentValue = imageLimitInput,
                unit = "images",
                presets = listOf(2, 5, 10, 25, 50, 100),
                onValueChange = { imageLimitInput = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. App Building & Studio Limit Card
            LiquidQuotaSelectorCard(
                title = "⚡ App Building & Code Studio Limit",
                description = "Kitne native apps/projects user AI se auto-build karwa sakta hai",
                currentValue = appBuildLimitInput,
                unit = "builds",
                presets = listOf(3, 5, 10, 20, 50, 100),
                onValueChange = { appBuildLimitInput = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Overall Combined Total Limit Card
            LiquidQuotaSelectorCard(
                title = "🌐 Overall Total API Limit",
                description = "User ke kul mila kar total free calls ki maximum limit",
                currentValue = totalLimitInput,
                unit = "prompts",
                presets = listOf(10, 25, 35, 75, 150, 300),
                onValueChange = { totalLimitInput = it }
            )
        }

        // --- 5. Save & Deploy Fleet API Button ---
        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    val updatedConfig = currentConfig.copy(
                        defaultApiKey = apiKeyInput.trim(),
                        defaultProvider = selectedProvider,
                        defaultModel = modelInput.trim().ifBlank {
                            if (selectedProvider == "inceptionlabs.ai") "mercury-2.5"
                            else if (selectedProvider == "api.atria-asi.ai") "Atria-Dawn-Preview"
                            else "gemini-flash-latest"
                        },
                        testingQuotaPerUser = totalLimitInput,
                        chatLimitPerUser = chatLimitInput,
                        imageLimitPerUser = imageLimitInput,
                        appBuildLimitPerUser = appBuildLimitInput,
                        totalApiLimitPerUser = totalLimitInput,
                        updatedAt = System.currentTimeMillis(),
                        updatedBy = AuthManager.currentUser.value?.email ?: "admin"
                    )
                    val res = AdminConfigManager.saveGlobalConfig(updatedConfig)
                    isSaving = false
                    if (res.isSuccess) {
                        Toast.makeText(context, "✅ Master API & Multi-Tier Limits deployed to all app users!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deploying to Fleet...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Deploy Fleet API to All Users", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiquidQuotaSelectorCard(
    title: String,
    description: String,
    currentValue: Int,
    unit: String,
    presets: List<Int>,
    onValueChange: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x35141E32))
            .border(BorderStroke(1.dp, Color(0x30A78BFA)), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Value Stepper Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (currentValue > 1) onValueChange(currentValue - 1) },
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(13.dp))
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x338B5CF6))
                            .border(BorderStroke(1.dp, Color(0x668B5CF6)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$currentValue $unit",
                            color = Color(0xFFC4B5FD),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { onValueChange(currentValue + 1) },
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(text = description, color = Color(0xFF94A3B8), fontSize = 10.5.sp)

            Spacer(modifier = Modifier.height(8.dp))

            // Presets row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = currentValue == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF8B5CF6) else Color(0x30161E30))
                            .border(
                                BorderStroke(1.dp, if (isSelected) Color(0xFFC4B5FD) else Color(0x20FFFFFF)),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onValueChange(preset) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$preset",
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 4: BROADCAST ANNOUNCEMENTS & SYSTEM CONTROLS
// ----------------------------------------------------------------------------
@Composable
private fun AdminBroadcastTab(
    currentConfig: AdminGlobalConfig
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    var pushTitle by remember { mutableStateOf("📢 Important Update from SAIF AI") }
    var pushMessage by remember { mutableStateOf("") }
    var isSendingPush by remember { mutableStateOf(false) }
    var pushSentSuccess by remember { mutableStateOf(false) }

    var announcementText by remember { mutableStateOf(currentConfig.globalAnnouncement) }
    var isAnnouncementActive by remember { mutableStateOf(currentConfig.isAnnouncementActive) }
    var isMaintenanceMode by remember { mutableStateOf(currentConfig.isMaintenanceMode) }
    var maintenanceMsg by remember { mutableStateOf(currentConfig.maintenanceMessage) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Push Notification Dispatcher Card ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFF38BDF8),
            borderColor = Color(0x6038BDF8)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x3338BDF8))
                        .border(BorderStroke(1.dp, Color(0x6638BDF8)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Instant Push Notification",
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Direct Android system notification sabhi users ke phone par bhejein",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Template Chips
            Text(text = "Quick Presets:", color = Color(0xFFCBD5E1), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    "🚀 Update Live" to ("App Update Ready" to "SAIF AI ka naya update live ho chuka hai. Naye features explore karein!"),
                    "🔥 New AI Models" to ("New Models Available" to "Ultra-fast Llama 3.3 aur DeepSeek models enable ho gaye hain."),
                    "🎁 Bonus Quota" to ("Special Bonus Quota" to "Aapke account me extra free generations add kar diye gaye hain!"),
                    "⚠️ Maintenance" to ("Server Maintenance" to "SAIF AI me kuch samay ke liye maintenance chal raha hai.")
                )
                presets.forEach { (label, content) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x2538BDF8))
                            .border(BorderStroke(1.dp, Color(0x5038BDF8)), RoundedCornerShape(8.dp))
                            .clickable {
                                pushTitle = content.first
                                pushMessage = content.second
                                pushSentSuccess = false
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(text = label, color = Color(0xFF7DD3FC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notification Title Input
            Text(text = "Notification Title", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = pushTitle,
                onValueChange = { pushTitle = it; pushSentSuccess = false },
                placeholder = { Text("Notification Title...", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x350C101A),
                    unfocusedContainerColor = Color(0x220C101A),
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0x30334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Notification Message Input
            Text(text = "Notification Message", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = pushMessage,
                onValueChange = { pushMessage = it; pushSentSuccess = false },
                placeholder = { Text("Type notification message here...", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x350C101A),
                    unfocusedContainerColor = Color(0x220C101A),
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0x30334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Android Notification Preview
            Text(text = "Android System Preview:", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE01E293B))
                    .border(BorderStroke(1.dp, Color(0x40FFFFFF)), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8B5CF6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "SAIF AI", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "• now", color = Color(0xFF64748B), fontSize = 10.sp)
                        }
                        Text(
                            text = pushTitle.ifBlank { "Notification Title" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pushMessage.ifBlank { "Notification body message will appear here on user screen..." },
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Send Notification Button
            Button(
                onClick = {
                    if (pushMessage.isBlank()) {
                        Toast.makeText(context, "Please enter a message to send", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    scope.launch {
                        isSendingPush = true
                        val success = AdminConfigManager.sendBroadcastNotification(
                            context = context,
                            title = pushTitle.ifBlank { "📢 SAIF AI Announcement" },
                            message = pushMessage.trim()
                        )
                        isSendingPush = false
                        pushSentSuccess = success
                        if (success) {
                            Toast.makeText(context, "✅ Push notification dispatched to all users!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "✅ Notification broadcasted to system & users!", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isSendingPush) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcasting...", color = Color.White, fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcast Notification Now", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (pushSentSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Notification sent and synced across active devices", color = Color(0xFF6EE7B7), fontSize = 11.sp)
                }
            }
        }

        // --- 2. In-App Notice Banner Card ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFFFBBF24),
            borderColor = Color(0x50FBBF24)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Global In-App Top Banner",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Displays top banner on all user screens",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }

                Switch(
                    checked = isAnnouncementActive,
                    onCheckedChange = { isAnnouncementActive = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF8B5CF6)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = announcementText,
                onValueChange = { announcementText = it },
                placeholder = { Text("e.g. 🚀 Welcome to SAIF AI! Build Android apps instantly...", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x350C101A),
                    unfocusedContainerColor = Color(0x220C101A),
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0x30334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            if (isAnnouncementActive && announcementText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Live Banner Preview:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x308B5CF6))
                        .border(BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = announcementText, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 3. Maintenance Mode Card ---
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = Color(0xFFEF4444),
            borderColor = Color(0x50EF4444)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Maintenance Mode",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Temporarily block standard users for upgrades",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }

                Switch(
                    checked = isMaintenanceMode,
                    onCheckedChange = { isMaintenanceMode = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFEF4444)
                    )
                )
            }

            if (isMaintenanceMode) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = maintenanceMsg,
                    onValueChange = { maintenanceMsg = it },
                    placeholder = { Text("Maintenance message for users...", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x350C101A),
                        unfocusedContainerColor = Color(0x220C101A),
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0x30334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 4. Save Broadcast Settings Button ---
        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    val updated = currentConfig.copy(
                        globalAnnouncement = announcementText.trim(),
                        isAnnouncementActive = isAnnouncementActive,
                        isMaintenanceMode = isMaintenanceMode,
                        maintenanceMessage = maintenanceMsg.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                    val res = AdminConfigManager.saveGlobalConfig(updated)
                    isSaving = false
                    if (res.isSuccess) {
                        Toast.makeText(context, "Broadcast & banner settings saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving...", color = Color.White, fontSize = 14.sp)
            } else {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Broadcast & Notice", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
