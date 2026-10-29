package com.example.agent

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.util.SaifAccessibilityService
import kotlinx.coroutines.delay

/**
 * 4-Step verified application launch ladder with foreground verification and SAIF self-window protection.
 */
object AppLauncher {
    private const val TAG = "SAIF_AGENT"
    private const val SAIF_PACKAGE = "com.saifai.assistant"

    data class LaunchResult(
        val success: Boolean,
        val targetPackage: String,
        val message: String
    )

    /**
     * Executes the launch ladder until the target package is verifiably in the foreground.
     */
    suspend fun launchVerified(context: Context, appNameOrPackage: String): LaunchResult {
        val targetPackage = AppResolver.resolvePackage(context, appNameOrPackage)
            ?: appNameOrPackage.trim()

        val service = SaifAccessibilityService.instance

        // Check if already in foreground
        if (service != null && service.isAppInForeground(targetPackage)) {
            Log.i(TAG, "Target package '$targetPackage' is already in foreground.")
            return LaunchResult(true, targetPackage, "App '$targetPackage' is already active")
        }

        // HARD GUARD: If SAIF itself is currently foreground and we want to open another app,
        // send SAIF to background first so it doesn't block intent transitions.
        val currentForeground = service?.getCurrentPackage() ?: ""
        if (currentForeground.contains("saifai") || currentForeground == context.packageName) {
            Log.d(TAG, "SAIF is in foreground. Pressing HOME to yield screen before launch.")
            service?.pressHome()
            delay(350)
        }

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)

        // RUNG 1: AccessibilityService Context Launch
        if (service != null && launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                service.startActivity(launchIntent)
                if (waitForPackageForeground(service, targetPackage, 3000L)) {
                    Log.i(TAG, "Rung 1 (Accessibility Context) launched '$targetPackage' successfully.")
                    return LaunchResult(true, targetPackage, "App opened successfully: $targetPackage")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rung 1 launch failed: ${e.message}")
            }
        }

        // RUNG 2: Application Context Launch with FLAG_ACTIVITY_NEW_TASK
        if (launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                if (waitForPackageForeground(service, targetPackage, 3000L)) {
                    Log.i(TAG, "Rung 2 (Application Context) launched '$targetPackage' successfully.")
                    return LaunchResult(true, targetPackage, "App opened successfully: $targetPackage")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rung 2 launch failed: ${e.message}")
            }
        }

        // RUNG 3: PendingIntent with background activity start options
        if (launchIntent != null) {
            try {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, flags)

                if (Build.VERSION.SDK_INT >= 34) {
                    val options = ActivityOptions.makeBasic()
                    val setModeMethod = ActivityOptions::class.java.getMethod("setPendingIntentBackgroundActivityStartMode", Int::class.javaPrimitiveType)
                    setModeMethod.invoke(options, 1) // MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    pendingIntent.send()
                }

                if (waitForPackageForeground(service, targetPackage, 3500L)) {
                    Log.i(TAG, "Rung 3 (PendingIntent) launched '$targetPackage' successfully.")
                    return LaunchResult(true, targetPackage, "App opened successfully: $targetPackage")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rung 3 launch failed: ${e.message}")
            }
        }

        // RUNG 4: Accessibility UI Route (Home -> search / app drawer)
        if (service != null) {
            try {
                Log.d(TAG, "Attempting Rung 4 (UI navigation fallback) for '$targetPackage'...")
                service.pressHome()
                delay(600)
                // Search for app icon on home screen
                val appLabel = AppResolver.getInstalledApps(context)
                    .firstOrNull { it.packageName == targetPackage }?.label ?: appNameOrPackage
                if (service.findAndClick(appLabel)) {
                    if (waitForPackageForeground(service, targetPackage, 3500L)) {
                        Log.i(TAG, "Rung 4 (UI tap) launched '$targetPackage' successfully.")
                        return LaunchResult(true, targetPackage, "App opened successfully via Home: $targetPackage")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rung 4 UI fallback failed: ${e.message}")
            }
        }

        return LaunchResult(false, targetPackage, "Could not bring '$targetPackage' to foreground after 4 launch attempts.")
    }

    private suspend fun waitForPackageForeground(service: SaifAccessibilityService?, targetPackage: String, timeoutMs: Long): Boolean {
        if (service == null) return false
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (service.isAppInForeground(targetPackage)) {
                return true
            }
            delay(250)
        }
        return service.isAppInForeground(targetPackage)
    }
}
