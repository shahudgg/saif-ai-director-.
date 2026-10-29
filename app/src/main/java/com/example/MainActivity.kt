

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.data.local.AuthManager
import com.example.data.local.ChatRepository
import com.example.data.local.SaifDatabase
import com.example.data.local.ThemeManager
import com.example.data.local.ThemeMode
import com.example.ui.ChatViewModel
import com.example.ui.SaifAiApp
import com.example.ui.components.AuthScreen
import com.example.ui.theme.SaifAiTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        val db = SaifDatabase.getDatabase(applicationContext)
        val repository = ChatRepository(db.chatDao())
        ChatViewModel.Factory(repository)
    }

    override fun onResume() {
        super.onResume()
        LiveVoiceManager.isAppInForeground.value = true
        com.example.util.WakeWordManager.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        LiveVoiceManager.isAppInForeground.value = false
        com.example.util.WakeWordManager.isAppInForeground = false
        com.example.util.WakeWordAudioDetector.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthManager.init(applicationContext)
        com.example.data.local.AdminConfigManager.init(applicationContext)
        com.example.data.local.ProviderSettingsManager.init(applicationContext)
        ThemeManager.init(applicationContext)
        com.example.utils.TTSManager.init(applicationContext)
        com.example.util.WakeWordManager.init(applicationContext)
        com.example.util.SmartConversationMemory.init(applicationContext)
        com.example.util.SaifBroadcastNotificationHelper.initNotificationChannel(applicationContext)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Request runtime notification permission on Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted ->
                    android.util.Log.d("MainActivity", "Notification permission: $granted")
                }
                LaunchedEffect(Unit) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val themeMode by ThemeManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isLightMode = when (themeMode) {
                ThemeMode.LIGHT -> true
                ThemeMode.DARK -> false
                ThemeMode.SYSTEM -> !isSystemDark
            }
            
            val currentUser by AuthManager.currentUser.collectAsState()
            val globalConfig by com.example.data.local.AdminConfigManager.globalConfig.collectAsState()
            var showWelcomeToast by remember { mutableStateOf(false) }
            var showAdminDashboard by remember(currentUser?.email, currentUser?.role) { mutableStateOf(currentUser?.isAdmin == true) }
            var isAnnouncementDismissed by remember { mutableStateOf(false) }

            // Start Firestore sync for all logged in users so they receive notifications and fleet config
            LaunchedEffect(currentUser?.uid) {
                if (currentUser != null) {
                    com.example.data.local.AdminConfigManager.startFirestoreSync()
                }
            }

            // Automatic Direct Admin Login: If user is admin in Firebase, immediately open Admin Control Panel
            LaunchedEffect(currentUser?.email, currentUser?.role) {
                if (currentUser?.isAdmin == true) {
                    showAdminDashboard = true
                }
            }

            // Reset announcement dismissal whenever a new broadcast is dispatched
            LaunchedEffect(globalConfig.lastBroadcastTime) {
                if (globalConfig.lastBroadcastTime > 0L) {
                    isAnnouncementDismissed = false
                }
            }

            SaifAiTheme(isLightMode = isLightMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentUser != null,
                        transitionSpec = {
                            (fadeIn(tween(400)) + scaleIn(initialScale = 0.98f, animationSpec = tween(400)))
                                .togetherWith(fadeOut(tween(300)) + scaleOut(targetScale = 1.03f, animationSpec = tween(300)))
                        },
                        label = "auth_screen_transition"
                    ) { isLoggedIn ->
                        if (!isLoggedIn) {
                            AuthScreen(
                                onLoginSuccess = { user ->
                                    if (user.isAdmin) {
                                        showAdminDashboard = true
                                    } else {
                                        showWelcomeToast = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val user = currentUser
                            if (user != null && (!user.isActive || user.isBanned)) {
                                // Account Suspended / Banned Screen
                                com.example.ui.components.BannedUserOverlay(
                                    onSignOut = { AuthManager.signOut() }
                                )
                            } else {
                                SaifAiApp(
                                    viewModel = viewModel,
                                    isLightMode = isLightMode,
                                    onToggleTheme = { 
                                        val newMode = if (isLightMode) ThemeMode.DARK else ThemeMode.LIGHT
                                        ThemeManager.setTheme(newMode)
                                    },
                                    onOpenAdminDashboard = { showAdminDashboard = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Global Admin Broadcast Banner (When active and not dismissed)
                    val activeAnnouncement = globalConfig.globalAnnouncement.trim()
                    if (currentUser != null && globalConfig.isAnnouncementActive && activeAnnouncement.isNotBlank() && !isAnnouncementDismissed && !showAdminDashboard) {
                        Surface(
                            color = Color(0xF01E1B4B),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.7f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "📢", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeAnnouncement,
                                    color = Color(0xFFEDE9FE),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { isAnnouncementDismissed = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFFC4B5FD),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Floating Welcome Toast Banner
                    AnimatedVisibility(
                        visible = showWelcomeToast && currentUser != null && !showAdminDashboard,
                        enter = slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(280)
                        ) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 16.dp)
                    ) {
                        LaunchedEffect(Unit) {
                            delay(3500L)
                            showWelcomeToast = false
                        }
                        val isAdminUser = currentUser?.isAdmin == true
                        Surface(
                            color = if (isAdminUser) Color(0xF01E1B4B) else Color(0xEB111827),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (isAdminUser) Color(0xFFF59E0B) else Color(0x668B5CF6)),
                            shadowElevation = 8.dp,
                            modifier = Modifier.clickable { showWelcomeToast = false }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAdminUser) "👑 Logged in as Admin (${currentUser?.name})" else "👋 Welcome back, ${currentUser?.name}!",
                                    color = if (isAdminUser) Color(0xFFFDE68A) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Full-screen Admin Dashboard Screen
                    if (showAdminDashboard && currentUser?.isAdmin == true) {
                        com.example.ui.components.AdminDashboardScreen(
                            onClose = { showAdminDashboard = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
