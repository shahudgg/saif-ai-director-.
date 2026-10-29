package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PermissionItemInfo(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isGranted: Boolean,
    val isSpecialSettings: Boolean = false,
    val manifestPermission: String? = null
)

data class PermissionPromptData(
    val id: String,
    val title: String,
    val message: String,
    val manifestPermission: String? = null,
    val isSpecial: Boolean = false,
    val onGranted: (() -> Unit)? = null
)

object PermissionManager {

    private val _permissionPrompt = MutableStateFlow<PermissionPromptData?>(null)
    val permissionPrompt: StateFlow<PermissionPromptData?> = _permissionPrompt

    fun showPermissionPrompt(prompt: PermissionPromptData) {
        _permissionPrompt.value = prompt
    }

    fun dismissPermissionPrompt() {
        _permissionPrompt.value = null
    }

    fun hasRecordAudio(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasAccessibility(context: Context): Boolean {
        return SaifAccessibilityService.isServiceEnabled(context)
    }

    fun hasCamera(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPhone(context: Context): Boolean {
        return true // Uses ACTION_DIAL + Accessibility automation (zero dangerous permissions, safe for Play Protect)
    }

    fun hasReadContacts(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSendSms(context: Context): Boolean {
        return true // Uses ACTION_SENDTO SMS composer intent (zero dangerous permissions, safe for Play Protect)
    }

    fun hasPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasNotificationListener(context: Context): Boolean {
        return SaifNotificationListenerService.isNotificationAccessGranted(context)
    }

    fun getAllPermissionItems(context: Context): List<PermissionItemInfo> {
        val list = mutableListOf<PermissionItemInfo>()

        list.add(
            PermissionItemInfo(
                id = "mic",
                title = "Microphone (माइक्रोफ़ोन)",
                description = "Live Baatcheet aur Hey Saif voice commands sunne ke liye",
                iconName = "mic",
                isGranted = hasRecordAudio(context),
                manifestPermission = Manifest.permission.RECORD_AUDIO
            )
        )

        list.add(
            PermissionItemInfo(
                id = "overlay",
                title = "Display Over Other Apps (फ़्लोटिंग ओवरले)",
                description = "Doosre apps ke upar live assistant orb aur controls dikhane ke liye",
                iconName = "layers",
                isGranted = hasOverlayPermission(context),
                isSpecialSettings = true
            )
        )

        list.add(
            PermissionItemInfo(
                id = "accessibility",
                title = "Accessibility Automation (स्क्रीन ऑटोमेशन)",
                description = "YouTube, WhatsApp, Instagram me auto click, scroll aur text type karne ke liye",
                iconName = "touch_app",
                isGranted = hasAccessibility(context),
                isSpecialSettings = true
            )
        )

        list.add(
            PermissionItemInfo(
                id = "camera",
                title = "Camera (कैमरा)",
                description = "Photo analysis, scan aur photo khinchne ke liye",
                iconName = "camera",
                isGranted = hasCamera(context),
                manifestPermission = Manifest.permission.CAMERA
            )
        )

        list.add(
            PermissionItemInfo(
                id = "contacts",
                title = "Contacts (संपर्क)",
                description = "Mom, Papa ya doston ka number dhoondh kar call/message karne ke liye",
                iconName = "contacts",
                isGranted = hasReadContacts(context),
                manifestPermission = Manifest.permission.READ_CONTACTS
            )
        )

        list.add(
            PermissionItemInfo(
                id = "call",
                title = "Phone Calls (फ़ोन कॉल)",
                description = "Voice command bol kar direct call milane ke liye",
                iconName = "phone",
                isGranted = hasCallPhone(context),
                manifestPermission = null
            )
        )

        list.add(
            PermissionItemInfo(
                id = "sms",
                title = "SMS Messages (संदेश)",
                description = "Direct SMS message bhejne ke liye",
                iconName = "message",
                isGranted = hasSendSms(context),
                manifestPermission = null
            )
        )

        list.add(
            PermissionItemInfo(
                id = "notification_listener",
                title = "Notification Access (सूचना पढ़ना)",
                description = "WhatsApp aur phone ke notifications bol kar sunane ke liye",
                iconName = "notifications",
                isGranted = hasNotificationListener(context),
                isSpecialSettings = true
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionItemInfo(
                    id = "post_notifications",
                    title = "System Notifications (सूचना भेजना)",
                    description = "Live Voice service aur task updates alert notification dikhane ke liye",
                    iconName = "notifications_active",
                    isGranted = hasPostNotifications(context),
                    manifestPermission = Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }

        return list
    }

    fun openSpecialPermissionSetting(context: Context, id: String) {
        try {
            when (id) {
                "overlay" -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
                "accessibility" -> {
                    SaifAccessibilityService.openAccessibilitySettings(context)
                }
                "notification_listener" -> {
                    SaifNotificationListenerService.openNotificationAccessSettings(context)
                }
                else -> {
                    openAppSettings(context)
                }
            }
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // fallback
        }
    }
}
