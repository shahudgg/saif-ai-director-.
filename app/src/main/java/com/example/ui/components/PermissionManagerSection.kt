package com.example.ui.components

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.util.PermissionItemInfo
import com.example.util.PermissionManager

@Composable
fun PermissionManagerSection(
    glassBorderBrush: Brush,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionItems by remember { mutableStateOf(PermissionManager.getAllPermissionItems(context)) }

    // Refresh status on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionItems = PermissionManager.getAllPermissionItems(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var pendingPermissionId by remember { mutableStateOf<String?>(null) }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionItems = PermissionManager.getAllPermissionItems(context)
    }

    var showGrantedList by remember { mutableStateOf(false) }
    val pendingPermissions = remember(permissionItems) { permissionItems.filter { !it.isGranted } }
    val grantedPermissions = remember(permissionItems) { permissionItems.filter { it.isGranted } }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimaryAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security",
                    tint = PrimaryAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Permission Manager (अनुमति प्रबंधक)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SaifTheme.colors.textPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = if (pendingPermissions.isEmpty()) "Sabhi permissions active hain ✨" else "${pendingPermissions.size} permissions pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaifTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        LiquidSurface(
            color = SaifTheme.colors.surfaceCard,
            glassBorderBrush = glassBorderBrush,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pendingPermissions.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1A10B981))
                            .border(BorderStroke(1.dp, Color(0x4D10B981)), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x3310B981)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "All Granted",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "All Permissions Allowed! ✨",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF34D399)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Saif AI has all required permissions and is operating at peak performance.",
                                fontSize = 11.5.sp,
                                color = SaifTheme.colors.textSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                } else {
                    pendingPermissions.forEach { item ->
                        PermissionRow(
                            item = item,
                            onRequestPermission = {
                                if (item.isSpecialSettings) {
                                    PermissionManager.openSpecialPermissionSetting(context, item.id)
                                } else if (item.manifestPermission != null) {
                                    singlePermissionLauncher.launch(item.manifestPermission)
                                } else {
                                    PermissionManager.openAppSettings(context)
                                }
                            }
                        )
                    }
                }

                if (grantedPermissions.isNotEmpty()) {
                    HorizontalDivider(
                        color = SaifTheme.colors.cardBorder.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showGrantedList = !showGrantedList }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Allowed Permissions (${grantedPermissions.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = if (showGrantedList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = SaifTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showGrantedList) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            grantedPermissions.forEach { item ->
                                PermissionRow(
                                    item = item,
                                    onRequestPermission = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    item: PermissionItemInfo,
    onRequestPermission: () -> Unit
) {
    val icon = when (item.iconName) {
        "mic" -> Icons.Default.Mic
        "layers" -> Icons.Default.Layers
        "touch_app" -> Icons.Default.TouchApp
        "camera" -> Icons.Default.PhotoCamera
        "contacts" -> Icons.Default.Contacts
        "phone" -> Icons.Default.Phone
        "message" -> Icons.AutoMirrored.Filled.Message
        "notifications" -> Icons.Default.NotificationsActive
        else -> Icons.Default.Security
    }

    val badgeBgColor by animateColorAsState(
        targetValue = if (item.isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else PrimaryAccent.copy(alpha = 0.12f),
        label = "badgeBg"
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (item.isGranted) Color(0xFF10B981) else PrimaryAccent,
        label = "badgeText"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SaifTheme.colors.primaryBackground.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                        else SaifTheme.colors.cardBorder.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.title,
                    tint = if (item.isGranted) Color(0xFF10B981) else SaifTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = SaifTheme.colors.textPrimary
                )
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = SaifTheme.colors.textSecondary,
                    lineHeight = 14.sp
                )
            }
        }

        if (item.isGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(badgeBgColor)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Allowed",
                    tint = badgeTextColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Allowed",
                    color = badgeTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = "Allow",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
