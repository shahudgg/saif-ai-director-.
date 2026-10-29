package com.example.agent.skills

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

object SystemSkill : Skill {
    override val name: String = "system"
    private const val TAG = "SAIF_AGENT"

    private var isTorchOn = false

    override suspend fun run(args: JSONObject, env: AgentEnv): SkillResult {
        val action = args.optString("action", "").lowercase()
        val context = env.context
        val service = env.service

        return when (action) {
            "flashlight_on", "torch_on" -> {
                setFlashlight(context, true)
            }
            "flashlight_off", "torch_off" -> {
                setFlashlight(context, false)
            }
            "toggle_flashlight", "toggle_torch" -> {
                setFlashlight(context, !isTorchOn)
            }
            "volume_up" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                SkillResult(true, "Volume increased")
            }
            "volume_down" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                SkillResult(true, "Volume decreased")
            }
            "mute" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                SkillResult(true, "Muted volume")
            }
            "set_alarm" -> {
                val hour = args.optInt("hour", 7)
                val minute = args.optInt("minute", 0)
                val message = args.optString("message", "Alarm")
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    SkillResult(true, "Alarm set for %02d:%02d".format(hour, minute))
                } catch (e: Exception) {
                    SkillResult(false, "Could not set alarm: ${e.message}")
                }
            }
            "set_timer" -> {
                val seconds = args.optInt("seconds", 60)
                val message = args.optString("message", "Timer")
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    SkillResult(true, "Timer set for $seconds seconds")
                } catch (e: Exception) {
                    SkillResult(false, "Could not set timer: ${e.message}")
                }
            }
            "open_wifi" -> {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
                SkillResult(true, "Opened Wi-Fi settings")
            }
            "open_bluetooth" -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
                SkillResult(true, "Opened Bluetooth settings")
            }
            "lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val ok = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
                    SkillResult(ok, if (ok) "Screen locked" else "Failed to lock screen")
                } else {
                    SkillResult(false, "Lock screen requires Android 9+")
                }
            }
            "screenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val ok = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
                    SkillResult(ok, if (ok) "Screenshot captured" else "Failed to trigger screenshot")
                } else {
                    SkillResult(false, "System screenshot action requires Android 9+")
                }
            }
            else -> SkillResult(false, "Unknown system action: $action")
        }
    }

    private fun setFlashlight(context: Context, enable: Boolean): SkillResult {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cm?.cameraIdList?.firstOrNull() ?: return SkillResult(false, "No camera found for flashlight")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.setTorchMode(cameraId, enable)
                isTorchOn = enable
                SkillResult(true, if (enable) "Flashlight turned on" else "Flashlight turned off")
            } else {
                SkillResult(false, "Flashlight control requires Android M+")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight: ${e.message}")
            SkillResult(false, "Failed to control flashlight: ${e.message}")
        }
    }
}
