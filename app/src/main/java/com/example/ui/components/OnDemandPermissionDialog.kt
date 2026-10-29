package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.util.PermissionManager

@Composable
fun OnDemandPermissionDialog() {
    val context = LocalContext.current
    val promptData by PermissionManager.permissionPrompt.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            promptData?.onGranted?.invoke()
        }
        PermissionManager.dismissPermissionPrompt()
    }

    val currentPrompt = promptData ?: return

    AlertDialog(
        onDismissRequest = { PermissionManager.dismissPermissionPrompt() },
        containerColor = SaifTheme.colors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Permission",
                    tint = PrimaryAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = currentPrompt.title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = SaifTheme.colors.textPrimary
            )
        },
        text = {
            Text(
                text = currentPrompt.message,
                fontSize = 13.sp,
                color = SaifTheme.colors.textSecondary,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPrompt.isSpecial) {
                        PermissionManager.openSpecialPermissionSetting(context, currentPrompt.id)
                        PermissionManager.dismissPermissionPrompt()
                    } else if (currentPrompt.manifestPermission != null) {
                        permissionLauncher.launch(currentPrompt.manifestPermission)
                    } else {
                        PermissionManager.openAppSettings(context)
                        PermissionManager.dismissPermissionPrompt()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Allow (अनुमति दें)", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { PermissionManager.dismissPermissionPrompt() }
            ) {
                Text("Cancel", color = SaifTheme.colors.textSecondary)
            }
        }
    )
}
