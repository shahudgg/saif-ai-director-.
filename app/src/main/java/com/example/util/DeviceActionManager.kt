package com.example.util

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

sealed class DeviceAction {
    data class OpenApp(val appQuery: String) : DeviceAction()
    data class SetAlarm(val hour: Int, val minute: Int, val message: String = "Alarm") : DeviceAction()
    data class WhatsAppMessage(val recipient: String, val message: String) : DeviceAction()
    data class WhatsAppCall(val recipient: String) : DeviceAction()
    data class YouTubeSearch(val query: String) : DeviceAction()
    data class YouTubeControl(val command: String) : DeviceAction() // "play", "pause", "first", "next"
    data class InstagramAction(val subAction: String) : DeviceAction() // "profile", "messages", "camera", "story", "open"
    data class DialPhone(val numberOrName: String, val directCall: Boolean = false) : DeviceAction()
    data class SendSms(val recipient: String, val message: String) : DeviceAction()
    data class ToggleFlashlight(val enable: Boolean?) : DeviceAction() // null = toggle
    object OpenWifi : DeviceAction()
    object OpenBluetooth : DeviceAction()
    object OpenSettings : DeviceAction()
    data class OpenNotes(val noteText: String? = null) : DeviceAction()
    object OpenGallery : DeviceAction()
    object OpenFiles : DeviceAction()
    data class WebSearch(val query: String) : DeviceAction()
    data class CameraAction(val mode: String = "photo") : DeviceAction() // "photo", "video"
    data class AccessibilityClick(val targetText: String) : DeviceAction()
    data class AccessibilityType(val text: String) : DeviceAction()
    data class AccessibilityScroll(val direction: String) : DeviceAction() // "down", "up"
    data class AccessibilityNav(val navType: String) : DeviceAction() // "back", "home", "recents"
    data class ReadNotifications(val appFilter: String? = null) : DeviceAction()
    data class VolumeControl(val direction: String) : DeviceAction() // "up", "down", "mute"

    // Autonomous Jarvis Multi-step Power Actions
    data class CapCutProject(val mediaType: String = "video") : DeviceAction()
    data class WhatsAppLiveLocation(val recipient: String, val duration: String = "1 hour") : DeviceAction()
    data class YouTubePlayAndSubscribe(val query: String, val autoSubscribe: Boolean = true, val autoLike: Boolean = false) : DeviceAction()
    data class AutoScrollFeed(val platform: String = "shorts", val times: Int = 1) : DeviceAction()
    data class HumanTap(val x: Float, val y: Float) : DeviceAction()
    data class HumanSwipe(val direction: String = "up", val distance: Float = 0.5f) : DeviceAction()
    data class HumanDoubleTap(val x: Float? = null, val y: Float? = null) : DeviceAction()
    object InspectScreen : DeviceAction()
}

object DeviceActionManager {
    private const val TAG = "DeviceActionManager"
    private var isTorchOn: Boolean = false

    /**
     * Executes a list of actions sequentially with a short interval.
     */
    suspend fun executeActionsSequentially(context: Context, actions: List<DeviceAction>, delayMillis: Long = 1200L): List<String> {
        val results = mutableListOf<String>()
        withContext(Dispatchers.Main) {
            for (action in actions) {
                try {
                    val status = executeAction(context, action)
                    results.add(status)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing action $action: ${e.message}", e)
                    results.add("Error: ${e.message}")
                }
                if (actions.size > 1) {
                    delay(delayMillis)
                }
            }
        }
        return results
    }

    /**
     * Executes a single device action and returns status description.
     */
    suspend fun executeAction(context: Context, action: DeviceAction): String {
        Log.d(TAG, "Executing device action: $action")
        return when (action) {
            is DeviceAction.OpenApp -> {
                val ok = launchApp(context, action.appQuery)
                if (ok) "${action.appQuery} khol diya hai." else "${action.appQuery} phone me nahi mila."
            }
            is DeviceAction.SetAlarm -> {
                val ok = setAlarm(context, action.hour, action.minute, action.message)
                if (ok) "%02d:%02d ka alarm set kar diya hai.".format(action.hour, action.minute) else "Alarm set nahi ho saka."
            }
            is DeviceAction.WhatsAppMessage -> {
                automateWhatsAppMessage(context, action.recipient, action.message)
            }
            is DeviceAction.WhatsAppCall -> {
                val ok = startWhatsAppCall(context, action.recipient)
                if (ok) "WhatsApp par call shuru kar rahe hain." else "WhatsApp call open nahi ho saka."
            }
            is DeviceAction.YouTubeSearch -> {
                if (action.query.contains("song", ignoreCase = true) || action.query.contains("play", ignoreCase = true) || action.query.contains("gana", ignoreCase = true)) {
                    automateYouTubePlayAndSubscribe(context, action.query, autoSubscribe = false)
                } else {
                    val ok = searchYouTube(context, action.query)
                    if (ok) "YouTube par '${action.query}' search kar diya hai sir." else "YouTube kholne me dikkat aayi."
                }
            }
            is DeviceAction.YouTubeControl -> {
                val status = controlYouTube(context, action.command)
                status
            }
            is DeviceAction.InstagramAction -> {
                val ok = launchInstagramAction(context, action.subAction)
                if (ok) "Instagram ${action.subAction} khol diya hai." else "Instagram open nahi ho saka."
            }
            is DeviceAction.DialPhone -> {
                val status = callOrDialPhone(context, action.numberOrName, action.directCall)
                status
            }
            is DeviceAction.SendSms -> {
                val status = sendSmsMessage(context, action.recipient, action.message)
                status
            }
            is DeviceAction.ToggleFlashlight -> {
                val ok = toggleTorch(context, action.enable)
                val stateStr = if (isTorchOn) "ON" else "OFF"
                if (ok) "Flashlight $stateStr kar diya hai." else "Flashlight toggle nahi ho saka."
            }
            is DeviceAction.OpenWifi -> {
                openWifi(context)
                "Wi-Fi settings khol diya hai."
            }
            is DeviceAction.OpenBluetooth -> {
                openBluetooth(context)
                "Bluetooth settings khol diya hai."
            }
            is DeviceAction.OpenSettings -> {
                openSettings(context)
                "Phone Settings khol diya hai."
            }
            is DeviceAction.OpenNotes -> {
                openNotesApp(context, action.noteText)
                "Notes app khol diya hai."
            }
            is DeviceAction.OpenGallery -> {
                openGalleryApp(context)
                "Gallery khol diya hai."
            }
            is DeviceAction.OpenFiles -> {
                openFilesApp(context)
                "Files manager khol diya hai."
            }
            is DeviceAction.WebSearch -> {
                searchWeb(context, action.query)
                "Google par search kar rahe hain: ${action.query}"
            }
            is DeviceAction.CameraAction -> {
                openCamera(context, action.mode)
                if (action.mode == "video") "Video camera khol diya hai." else "Camera khol diya hai."
            }
            is DeviceAction.AccessibilityClick -> {
                val ok = clickOnScreen(context, action.targetText)
                if (ok) "'${action.targetText}' par click kar diya hai." else "'${action.targetText}' screen par nahi mila."
            }
            is DeviceAction.AccessibilityType -> {
                val ok = typeOnScreen(context, action.text)
                if (ok) "Screen par text likh diya hai." else "Type karne ke liye editable field nahi mila."
            }
            is DeviceAction.AccessibilityScroll -> {
                val ok = scrollScreen(context, action.direction == "down")
                if (ok) "Screen scroll kar diya hai." else "Scroll karne me dikkat aayi."
            }
            is DeviceAction.AccessibilityNav -> {
                val ok = performNav(context, action.navType)
                if (ok) "${action.navType} kar diya hai." else "Navigation perform nahi ho saka."
            }
            is DeviceAction.ReadNotifications -> {
                val summary = readNotificationsSummary(context, action.appFilter)
                summary
            }
            is DeviceAction.VolumeControl -> {
                adjustVolume(context, action.direction)
                "Volume ${action.direction} kar diya hai."
            }
            is DeviceAction.CapCutProject -> {
                automateCapCutProject(context, action.mediaType)
            }
            is DeviceAction.WhatsAppLiveLocation -> {
                automateWhatsAppLiveLocation(context, action.recipient, action.duration)
            }
            is DeviceAction.YouTubePlayAndSubscribe -> {
                automateYouTubePlayAndSubscribe(context, action.query, action.autoSubscribe, action.autoLike)
            }
            is DeviceAction.AutoScrollFeed -> {
                automateFeedScroll(context, action.platform, action.times)
            }
            is DeviceAction.HumanTap -> {
                val acc = SaifAccessibilityService.instance
                if (acc != null) {
                    val ok = acc.performHumanTap(action.x, action.y)
                    if (ok) "Screen tap kar diya hai (${action.x.toInt()}, ${action.y.toInt()})." else "Tap dispatch nahi ho saka."
                } else {
                    checkOrPromptAccessibility(context)
                    "Screen touches ke liye Accessibility Service chalu kijiye."
                }
            }
            is DeviceAction.HumanSwipe -> {
                val acc = SaifAccessibilityService.instance
                if (acc != null) {
                    val ok = acc.performHumanScrollDirection(action.direction, action.distance)
                    if (ok) "Screen ${action.direction} swipe kar diya hai." else "Swipe dispatch nahi ho saka."
                } else {
                    checkOrPromptAccessibility(context)
                    "Screen gestures ke liye Accessibility Service chalu kijiye."
                }
            }
            is DeviceAction.HumanDoubleTap -> {
                val acc = SaifAccessibilityService.instance
                if (acc != null) {
                    val dm = context.resources.displayMetrics
                    val x = action.x ?: (dm.widthPixels / 2f)
                    val y = action.y ?: (dm.heightPixels / 2f)
                    val ok = acc.performHumanDoubleTap(x, y)
                    if (ok) "Screen par double tap kar diya hai." else "Double tap nahi ho saka."
                } else {
                    checkOrPromptAccessibility(context)
                    "Screen gestures ke liye Accessibility Service chalu kijiye."
                }
            }
            is DeviceAction.InspectScreen -> {
                inspectCurrentScreen(context)
            }
        }
    }

    // 1. Launch any installed app by name or package
    fun launchApp(context: Context, appQuery: String): Boolean {
        val pm = context.packageManager
        val query = appQuery.trim().lowercase()
        if (query.isBlank()) return false

        val aliases = mapOf(
            "instagram" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "watsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "yt" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "settings" to "com.android.settings",
            "setting" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "calc" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "maps" to "com.google.android.apps.maps",
            "map" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "gmail" to "com.google.android.gm",
            "mail" to "com.google.android.gm",
            "play store" to "com.android.vending",
            "playstore" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "facebook" to "com.facebook.katana",
            "fb" to "com.facebook.katana",
            "snapchat" to "com.snapchat.android",
            "snap" to "com.snapchat.android",
            "capcut" to "com.lemon.lvoverseas",
            "cap cut" to "com.lemon.lvoverseas",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "camera" to "com.google.android.GoogleCamera",
            "files" to "com.google.android.documentsui",
            "notes" to "com.google.android.keep",
            "keep" to "com.google.android.keep",
            "messages" to "com.google.android.apps.messaging",
            "contacts" to "com.google.android.contacts",
            "dialer" to "com.google.android.dialer",
            "phone" to "com.google.android.dialer"
        )

        for ((alias, pkg) in aliases) {
            if (query.contains(alias) || alias.contains(query)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }

        // Camera generic fallback
        if (query.contains("camera") || query.contains("photo") || query.contains("tasveer")) {
            return openCamera(context, "photo")
        }

        // Settings generic fallback
        if (query.contains("setting")) {
            return openSettings(context)
        }

        // Search through all installed launchable apps
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (app in apps) {
                val label = app.loadLabel(pm).toString().lowercase()
                val packageName = app.activityInfo.packageName.lowercase()
                if (label.contains(query) || query.contains(label) || packageName.contains(query)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching installed apps: ${e.message}")
        }

        Toast.makeText(context, "App not found: $appQuery", Toast.LENGTH_SHORT).show()
        return false
    }

    // 2. Set Alarm
    fun setAlarm(context: Context, hour: Int, minute: Int, message: String): Boolean {
        return try {
            val validHour = hour.coerceIn(0, 23)
            val validMinute = minute.coerceIn(0, 59)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, validHour)
                putExtra(AlarmClock.EXTRA_MINUTES, validMinute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message.ifBlank { "Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Alarm set for %02d:%02d".format(validHour, validMinute), Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm: ${e.message}")
            launchApp(context, "clock")
        }
    }

    // 3. WhatsApp Message
    fun sendWhatsAppMessage(context: Context, recipient: String, message: String): Boolean {
        try {
            var phone = recipient.filter { it.isDigit() || it == '+' }
            val cleanMsg = message.trim().ifBlank { "Hii" }

            // If recipient is a name (e.g. "Mom", "Rahul"), resolve phone number from contacts
            if (phone.isBlank() && recipient.isNotBlank()) {
                val resolvedNumber = resolveContactNumber(context, recipient)
                if (!resolvedNumber.isNullOrBlank()) {
                    phone = resolvedNumber.filter { it.isDigit() }
                }
            }

            if (phone.length >= 7) {
                val cleanDigits = phone.filter { it.isDigit() }
                val url = "https://api.whatsapp.com/send?phone=$cleanDigits&text=${Uri.encode(cleanMsg)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } else {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, cleanMsg)
                    setPackage("com.whatsapp")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(sendIntent)
                Toast.makeText(context, "Select $recipient in WhatsApp to send: $cleanMsg", Toast.LENGTH_LONG).show()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WhatsApp message: ${e.message}")
            return try {
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Send via").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun startWhatsAppCall(context: Context, recipient: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // 4. YouTube Search & In-App Control
    fun searchYouTube(context: Context, query: String): Boolean {
        val cleanQuery = query.trim()
        val acc = SaifAccessibilityService.instance

        // If accessibility is active and YouTube is open, try searching inside open YouTube WITHOUT clicking the mic!
        if (acc != null && acc.isYouTubeInForeground() && cleanQuery.isNotBlank()) {
            val clickedSearch = acc.findAndClickSearchButton()
            if (clickedSearch) {
                acc.findAndType(cleanQuery)
                acc.pressEnterOrSearch()
                return true
            }
        }

        return try {
            if (cleanQuery.isNotBlank()) {
                val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.youtube")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                launchApp(context, "youtube")
            }
            true
        } catch (e: Exception) {
            try {
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}")
                val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun searchPlayStore(context: Context, query: String): Boolean {
        val cleanQuery = query.trim()
        val success = try {
            val marketUri = if (cleanQuery.isNotBlank()) {
                Uri.parse("market://search?q=${Uri.encode(cleanQuery)}&c=apps")
            } else {
                Uri.parse("market://search?q=")
            }
            val intent = Intent(Intent.ACTION_VIEW, marketUri).apply {
                setPackage("com.android.vending")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val webUri = Uri.parse("https://play.google.com/store/search?q=${Uri.encode(cleanQuery)}&c=apps")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                true
            } catch (ex: Exception) {
                launchApp(context, "playstore")
            }
        }

        // Accessibility typing assist if user is already on the search screen
        val acc = SaifAccessibilityService.instance
        if (acc != null && acc.isAppInForeground("com.android.vending") && cleanQuery.isNotBlank()) {
            val clickedSearch = acc.findAndClickFuzzy("Search") || acc.findAndClickByViewId("search_box") || acc.findAndClickByViewId("search_bar")
            if (clickedSearch) {
                acc.findAndType(cleanQuery)
                acc.pressEnterOrSearch()
            }
        }
        return success
    }

    fun controlYouTube(context: Context, command: String): String {
        val acc = SaifAccessibilityService.instance
        return when (command.lowercase()) {
            "first", "play first", "pehla", "pehli video" -> {
                if (acc != null) {
                    val clicked = acc.clickYouTubeFirstVideo()
                    if (clicked) "Pehli video play kar rahe hain sir." else "Pehla result nahi mil saka."
                } else {
                    checkOrPromptAccessibility(context)
                    "Screen par click karne ke liye Accessibility Service chalu kijiye."
                }
            }
            "pause", "rok do", "ruko" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
                "Playback pause kar diya hai."
            }
            "play", "resume", "chalao" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY)
                "Playback resume kar diya hai."
            }
            "next", "agla", "agli video" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                "Agli video par jaa rahe hain."
            }
            else -> "YouTube action execute kar diya hai."
        }
    }

    private fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching media key: ${e.message}")
        }
    }

    // 5. Instagram Actions
    fun launchInstagramAction(context: Context, subAction: String): Boolean {
        val pm = context.packageManager
        val action = subAction.lowercase()
        return try {
            when {
                action.contains("story") || action.contains("camera") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/create/story")).apply {
                        setPackage("com.instagram.android")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    true
                }
                action.contains("message") || action.contains("dm") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/inbox")).apply {
                        setPackage("com.instagram.android")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    true
                }
                action.contains("profile") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/_")).apply {
                        setPackage("com.instagram.android")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    true
                }
                else -> launchApp(context, "instagram")
            }
        } catch (e: Exception) {
            launchApp(context, "instagram")
        }
    }

    // 6. Phone Call & Contact Resolution
    suspend fun callOrDialPhone(context: Context, numberOrName: String, directCall: Boolean): String {
        var digits = numberOrName.filter { it.isDigit() || it == '+' }
        var resolvedName = numberOrName

        if (digits.isBlank() && numberOrName.isNotBlank()) {
            val resolved = resolveContactNumber(context, numberOrName)
            if (!resolved.isNullOrBlank()) {
                digits = resolved
                resolvedName = "$numberOrName ($digits)"
            }
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val acc = SaifAccessibilityService.instance

        return try {
            if (digits.isNotBlank()) {
                if (directCall && hasCallPermission) {
                    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$digits")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(callIntent)
                    delay(600L)
                    // Ensure call button is pressed if a SIM selection or confirmation pops up
                    for (attempt in 1..4) {
                        if (acc?.clickDialerCallButton() == true) break
                        delay(400L)
                    }
                    "$resolvedName ko call laga rahe hain sir..."
                } else {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(dialIntent)
                    // Auto-press the green Call button via Accessibility with retry loop so the dialer UI is ready!
                    var dialed = false
                    for (attempt in 1..5) {
                        delay(450L)
                        if (acc?.clickDialerCallButton() == true) {
                            dialed = true
                            break
                        }
                    }
                    if (dialed) "$resolvedName ko call mila diya hai sir!" else "$resolvedName ke liye dialer open kar diya hai."
                }
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                // If contact name was specified, try searching it in the dialer
                if (numberOrName.isNotBlank() && acc != null) {
                    delay(800L)
                    acc.findAndType(numberOrName)
                    delay(500L)
                    acc.clickFirstClickableInList()
                } else {
                    delay(600L)
                    acc?.clickDialerCallButton()
                }
                if (numberOrName.isNotBlank()) {
                    "$numberOrName ke liye dialer open kar diya hai."
                } else {
                    "Phone dialer open kar diya hai sir."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dialing phone: ${e.message}")
            "Phone call karne me samasya: ${e.message}"
        }
    }

    fun resolveContactNumber(context: Context, nameQuery: String): String? {
        val hasContactPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactPerm) {
            PermissionManager.showPermissionPrompt(
                PermissionPromptData(
                    id = "contacts",
                    title = "Contacts Permission Required",
                    message = "'$nameQuery' ka number dhoondhne ke liye Contacts permission allow kijiye.",
                    manifestPermission = Manifest.permission.READ_CONTACTS
                )
            )
            return null
        }

        val cleanQuery = nameQuery.trim().lowercase()
        val queryCandidates = mutableListOf(cleanQuery)

        // Expand common familial / relationship terms in Hindi & English
        if (cleanQuery.contains("papa") || cleanQuery.contains("dad") || cleanQuery.contains("pitaji") ||
            cleanQuery.contains("father") || cleanQuery.contains("daddy") || cleanQuery.contains("abba") || cleanQuery.contains("bapu")) {
            queryCandidates.addAll(listOf("papa", "dad", "father", "daddy", "pitaji", "abba", "bapu", "pappa"))
        }
        if (cleanQuery.contains("mummy") || cleanQuery.contains("mom") || cleanQuery.contains("mother") ||
            cleanQuery.contains("maa") || cleanQuery.contains("ammi") || cleanQuery.contains("mataji")) {
            queryCandidates.addAll(listOf("mummy", "mom", "mother", "maa", "ammi", "mataji", "mami"))
        }
        if (cleanQuery.contains("bhai") || cleanQuery.contains("brother") || cleanQuery.contains("bro") || cleanQuery.contains("bhaiya")) {
            queryCandidates.addAll(listOf("bhai", "brother", "bro", "bhaiya"))
        }
        if (cleanQuery.contains("behan") || cleanQuery.contains("sister") || cleanQuery.contains("didi")) {
            queryCandidates.addAll(listOf("behan", "sister", "didi"))
        }

        // Add individual words
        queryCandidates.addAll(cleanQuery.split(" ").filter { it.length > 1 })

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
            if (cursor != null) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIdx)?.lowercase() ?: ""
                    val contactNumber = cursor.getString(numIdx) ?: ""

                    if (queryCandidates.any { cand -> contactName.contains(cand) || cand.contains(contactName) }) {
                        return contactNumber
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact: ${e.message}")
        } finally {
            cursor?.close()
        }
        return null
    }

    // 7. SMS Messaging
    fun sendSmsMessage(context: Context, recipient: String, message: String): String {
        var digits = recipient.filter { it.isDigit() || it == '+' }
        if (digits.isBlank() && recipient.isNotBlank()) {
            val resolved = resolveContactNumber(context, recipient)
            if (!resolved.isNullOrBlank()) {
                digits = resolved
            }
        }

        val hasSmsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            if (digits.isNotBlank() && hasSmsPerm) {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(digits, null, message, null, null)
                Toast.makeText(context, "SMS sent to $recipient: $message", Toast.LENGTH_SHORT).show()
                "$recipient ko SMS bhej diya hai."
            } else {
                val uri = if (digits.isNotBlank()) Uri.parse("smsto:$digits") else Uri.parse("smsto:")
                val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra("sms_body", message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
                "$recipient ke liye SMS app open kar diya hai."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS: ${e.message}")
            "SMS bhejne me dikkat aayi: ${e.message}"
        }
    }

    // 8. Flashlight / Torch
    fun toggleTorch(context: Context, enable: Boolean?): Boolean {
        return try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camManager.cameraIdList.firstOrNull() ?: return false
            val targetState = enable ?: !isTorchOn
            camManager.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            val stateText = if (targetState) "ON" else "OFF"
            Toast.makeText(context, "Flashlight $stateText", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Torch toggle error: ${e.message}")
            false
        }
    }

    // 9. Wi-Fi Settings
    fun openWifi(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    // 10. Bluetooth Settings
    fun openBluetooth(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // 11. General Settings
    fun openSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // 12. Notes App
    fun openNotesApp(context: Context, noteText: String? = null): Boolean {
        val success = launchApp(context, "notes") || launchApp(context, "keep")
        if (!success && !noteText.isNullOrBlank()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, noteText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Create note in"))
            return true
        }
        return success
    }

    // 13. Gallery & Files
    fun openGalleryApp(context: Context): Boolean {
        return launchApp(context, "photos") || launchApp(context, "gallery")
    }

    fun openFilesApp(context: Context): Boolean {
        return launchApp(context, "files")
    }

    // 14. Web Search
    fun searchWeb(context: Context, query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    // 15. Camera
    fun openCamera(context: Context, mode: String): Boolean {
        return try {
            val intent = if (mode == "video") {
                Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            } else {
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            launchApp(context, "camera")
        }
    }

    // 16. Accessibility Screen Automation
    fun clickOnScreen(context: Context, targetText: String): Boolean {
        val acc = SaifAccessibilityService.instance
        if (acc == null) {
            checkOrPromptAccessibility(context)
            return false
        }
        return acc.findAndClick(targetText)
    }

    fun typeOnScreen(context: Context, text: String): Boolean {
        val acc = SaifAccessibilityService.instance
        if (acc == null) {
            checkOrPromptAccessibility(context)
            return false
        }
        return acc.findAndType(text)
    }

    fun scrollScreen(context: Context, forward: Boolean): Boolean {
        val acc = SaifAccessibilityService.instance
        if (acc == null) {
            checkOrPromptAccessibility(context)
            return false
        }
        return acc.scroll(forward)
    }

    fun performNav(context: Context, navType: String): Boolean {
        val acc = SaifAccessibilityService.instance
        val clean = navType.lowercase().trim()
        return when {
            clean.contains("home") || clean.contains("main") -> {
                val pressed = acc?.pressHome() ?: false
                // Always also dispatch native Android Home Intent for 100% reliable return to home screen
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                    true
                } catch (e: Exception) {
                    pressed
                }
            }
            clean.contains("back") || clean.contains("piche") || clean.contains("wapas") -> {
                acc?.pressBack() ?: false
            }
            clean.contains("recent") || clean.contains("task") -> {
                acc?.pressRecents() ?: false
            }
            clean.contains("notif") || clean.contains("shade") -> {
                acc?.openNotificationsShade() ?: false
            }
            clean.contains("quick") || clean.contains("setting") -> {
                acc?.openQuickSettings() ?: false
            }
            else -> {
                acc?.pressHome() ?: false
            }
        }
    }

    fun checkOrPromptAccessibility(context: Context) {
        if (!SaifAccessibilityService.isServiceEnabled(context)) {
            PermissionManager.showPermissionPrompt(
                PermissionPromptData(
                    id = "accessibility",
                    title = "Accessibility Service Required",
                    message = "Screen par auto-click aur navigation ke liye SAIF AI Accessibility Service allow kijiye.",
                    isSpecial = true
                )
            )
        }
    }

    // 17. Notification & Message Reading
    fun readLatestSms(context: Context, limit: Int = 4): List<String> {
        val smsList = mutableListOf<String>()
        val hasReadSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasReadSms) {
            return smsList // Sideload-safe: SMS reading via content provider omitted to prevent Play Protect block
        }
        var cursor: Cursor? = null
        try {
            val uri = Uri.parse("content://sms/inbox")
            cursor = context.contentResolver.query(
                uri,
                arrayOf("address", "body"),
                null,
                null,
                "date DESC LIMIT $limit"
            )
            if (cursor != null) {
                val addrIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                while (cursor.moveToNext()) {
                    val addr = cursor.getString(addrIdx) ?: "SMS"
                    val body = cursor.getString(bodyIdx) ?: ""
                    if (body.isNotBlank()) {
                        smsList.add("$addr se SMS: $body")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading SMS inbox: ${e.message}")
        } finally {
            cursor?.close()
        }
        return smsList
    }

    fun readNotificationsSummary(context: Context, appFilter: String?): String {
        val sb = StringBuilder()
        val notifGranted = SaifNotificationListenerService.isNotificationAccessGranted(context)
        val notifs = if (notifGranted) SaifNotificationListenerService.getRecentNotifications(appFilter) else emptyList()
        val smsMessages = readLatestSms(context, 4)

        if (notifs.isNotEmpty()) {
            sb.append("Aapke naye notifications:\n")
            notifs.take(4).forEachIndexed { i, n ->
                sb.append("${i + 1}. [${n.appName}] ${n.title}: ${n.text}\n")
            }
        }

        if (smsMessages.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\nAur ")
            sb.append("Aaye huye SMS:\n")
            smsMessages.forEachIndexed { i, s ->
                sb.append("${i + 1}. $s\n")
            }
        }

        if (sb.isNotBlank()) {
            return sb.toString().trim()
        }

        // Open notification shade so user can inspect visible messages immediately
        SaifAccessibilityService.instance?.openNotificationsShade()

        if (!notifGranted) {
            PermissionManager.showPermissionPrompt(
                PermissionPromptData(
                    id = "notification_listener",
                    title = "Notification Access Required",
                    message = "Aapke notifications sunne ke liye Notification Access allow kijiye.",
                    isSpecial = true
                )
            )
            return "Notification shade open kar diya hai sir. Messages bol kar sunne ke liye Notification Access aur SMS permission allow kijiye."
        }

        return "Aapke phone par abhi koi naye unread messages nahi hain sir. Notification shade open kar diya hai."
    }

    // 18. Volume Control
    fun adjustVolume(context: Context, direction: String) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val adjust = when (direction.lowercase()) {
                "up", "increase", "badhao" -> AudioManager.ADJUST_RAISE
                "down", "decrease", "ghatao", "kam karo" -> AudioManager.ADJUST_LOWER
                "mute", "silent" -> AudioManager.ADJUST_MUTE
                else -> AudioManager.ADJUST_SAME
            }
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.e(TAG, "Volume adjust error: ${e.message}")
        }
    }

    // 19. CapCut Autonomous Video Project Creator
    suspend fun automateCapCutProject(context: Context, mediaType: String = "video"): String {
        val pm = context.packageManager
        val capcutPackages = listOf("com.lemon.lvoverseas", "com.lemon.lv")
        var installedPkg = capcutPackages.firstOrNull { isPackageInstalled(pm, it) }

        if (installedPkg == null) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            val matched = apps.firstOrNull {
                val label = it.loadLabel(pm).toString().lowercase()
                val pkg = it.activityInfo.packageName.lowercase()
                label.contains("capcut") || pkg.contains("capcut") || pkg.contains("lemon.lv")
            }
            installedPkg = matched?.activityInfo?.packageName
        }

        if (installedPkg == null) {
            Toast.makeText(context, "CapCut app phone me nahi mila. Video gallery open kar rahe hain.", Toast.LENGTH_LONG).show()
            openGalleryApp(context)
            return "CapCut install nahi mila sir, maine Video Gallery open kar diya hai."
        }

        val launchIntent = pm.getLaunchIntentForPackage(installedPkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(launchIntent)
        }

        val acc = SaifAccessibilityService.instance
        if (acc == null) {
            checkOrPromptAccessibility(context)
            return "CapCut open kar diya hai. Video auto-load karne ke liye Accessibility allow kijiye."
        }

        val dm = context.resources.displayMetrics

        // Step 1: Wait for CapCut home screen
        delay(2200L)

        // Step 2: Click "New project"
        val newProjQueries = listOf("new project", "new video", "create", "start creating", "+", "नया प्रोजेक्ट")
        var clickedNewProj = acc.findAndClickAny(newProjQueries)
        if (!clickedNewProj) {
            clickedNewProj = acc.findAndClickByViewId("new_project") || acc.findAndClickByViewId("create")
        }
        if (!clickedNewProj) {
            clickedNewProj = acc.performHumanTap(dm.widthPixels * 0.28f, dm.heightPixels * 0.22f)
        }

        // Step 3: Wait for CapCut media picker to open
        delay(1600L)

        // Ensure "Videos" tab is selected if visible
        acc.findAndClick("videos") || acc.findAndClick("video") || acc.findAndClick("वीडियो")
        delay(600L)

        // Step 4: Click the first video in the gallery grid
        val clickedItem = acc.clickFirstGridItem() || acc.performHumanTap(dm.widthPixels * 0.22f, dm.heightPixels * 0.26f)

        delay(900L)

        // Step 5: Click "Add" or "Add (1)" button at bottom right
        val addQueries = listOf("add", "add (1)", "add(1)", "जोड़ें", "done", "next")
        var clickedAdd = acc.findAndClickAny(addQueries)
        if (!clickedAdd) {
            clickedAdd = acc.findAndClickByViewId("add") || acc.findAndClickByViewId("btn_add")
        }
        if (!clickedAdd) {
            clickedAdd = acc.performHumanTap(dm.widthPixels * 0.86f, dm.heightPixels * 0.94f)
        }

        delay(1200L)
        return "CapCut me gallery ka latest video new project me load kar diya hai sir, aap edit kar sakte hain!"
    }

    // 20. WhatsApp Live Location Sender
    suspend fun automateWhatsAppLiveLocation(context: Context, recipient: String, duration: String = "1 hour"): String {
        val pm = context.packageManager
        val whatsappPkg = "com.whatsapp"
        val launchIntent = pm.getLaunchIntentForPackage(whatsappPkg)
        if (launchIntent == null) {
            return "WhatsApp phone me nahi mila."
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(launchIntent)

        val acc = SaifAccessibilityService.instance
        if (acc == null) {
            checkOrPromptAccessibility(context)
            return "WhatsApp open kar diya hai. Automatic location send karne ke liye Accessibility allow kijiye."
        }

        val dm = context.resources.displayMetrics

        // Step 1: Wait for WhatsApp chat list to render
        delay(1600L)

        val cleanRecipient = recipient.trim()
        if (cleanRecipient.isNotBlank()) {
            var chatFound = acc.findAndClick(cleanRecipient)
            if (!chatFound) {
                val searchClicked = acc.findAndClickByViewId("menuitem_search") 
                    || acc.findAndClick("search") 
                    || acc.findAndClick("खोजें")
                    || acc.performHumanTap(dm.widthPixels * 0.72f, dm.heightPixels * 0.06f)
                
                if (searchClicked) {
                    delay(700L)
                    acc.findAndType(cleanRecipient)
                    delay(900L)
                    acc.clickFirstClickableInList() || acc.performHumanTap(dm.widthPixels * 0.5f, dm.heightPixels * 0.22f)
                }
            }
        }

        // Step 2: Inside the conversation screen
        delay(1200L)

        // Step 3: Click Attachment (paperclip) button
        val attachQueries = listOf("attach", "attach document", "अटैच", "attachment")
        var clickedAttach = acc.findAndClickAny(attachQueries)
        if (!clickedAttach) {
            clickedAttach = acc.findAndClickByViewId("conversation_attach_btn") || acc.findAndClickByViewId("attach")
        }
        if (!clickedAttach) {
            clickedAttach = acc.performHumanTap(dm.widthPixels * 0.72f, dm.heightPixels * 0.95f)
        }

        // Step 4: In attachment popup, click "Location"
        delay(1000L)
        val locQueries = listOf("location", "स्थान", "लोकेशन", "live location")
        var clickedLoc = acc.findAndClickAny(locQueries)
        if (!clickedLoc) {
            clickedLoc = acc.performHumanTap(dm.widthPixels * 0.35f, dm.heightPixels * 0.82f)
        }

        // Step 5: Wait for WhatsApp map screen to load
        delay(2000L)

        // Step 6: Click "Share live location"
        val shareLiveQueries = listOf("share live location", "लाइव लोकेशन", "share live", "शेयर लाइव")
        var clickedShareLive = acc.findAndClickAny(shareLiveQueries)
        if (!clickedShareLive) {
            clickedShareLive = acc.performHumanTap(dm.widthPixels * 0.38f, dm.heightPixels * 0.42f)
        }

        // Step 7: Select duration ("15 minutes", "1 hour", "8 hours")
        delay(1000L)
        val targetDuration = when {
            duration.contains("8") -> "8 hours"
            duration.contains("15") -> "15 minutes"
            else -> "1 hour"
        }
        acc.findAndClick(targetDuration) || acc.findAndClick("1 hour") || acc.findAndClick("1 घंटा")

        delay(600L)

        // Step 8: Click the green Send arrow button
        var clickedSend = acc.findAndClick("send") || acc.findAndClick("भेजें") || acc.findAndClickByViewId("send")
        if (!clickedSend) {
            clickedSend = acc.performHumanTap(dm.widthPixels * 0.88f, dm.heightPixels * 0.92f)
        }

        delay(800L)
        val targetName = if (cleanRecipient.isNotBlank()) cleanRecipient else "contact"
        return "WhatsApp par $targetName ko live location bhej di gayi hai sir!"
    }

    // 20b. Autonomous WhatsApp Message Sender with Guaranteed Send Click and Real-Time Voice Narration
    suspend fun automateWhatsAppMessage(context: Context, recipient: String, message: String): String {
        val cleanMsg = message.trim().ifBlank { "Hii" }
        var phone = recipient.filter { it.isDigit() || it == '+' }
        val cleanRec = recipient.trim()

        if (phone.isBlank() && cleanRec.isNotBlank() && !cleanRec.equals("kisi", ignoreCase = true) && !cleanRec.equals("kisi ko", ignoreCase = true)) {
            val resolved = resolveContactNumber(context, cleanRec)
            if (!resolved.isNullOrBlank()) {
                phone = resolved.filter { it.isDigit() }
            }
        }

        val acc = SaifAccessibilityService.instance

        if (phone.length >= 7) {
            com.example.utils.TTSManager.speak("Main WhatsApp khol raha hoon sir...", context)
            val cleanDigits = phone.filter { it.isDigit() }
            val url = "https://api.whatsapp.com/send?phone=$cleanDigits&text=${Uri.encode(cleanMsg)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                return "WhatsApp open nahi ho saka: ${e.message}"
            }

            if (acc == null) {
                checkOrPromptAccessibility(context)
                return "WhatsApp par message likh diya hai. Auto-send ke liye Accessibility allow kijiye."
            }

            delay(1500L)
            com.example.utils.TTSManager.speak("Chat khul gayi, ab '$cleanMsg' bhej raha hoon...", context)

            // Automate clicking the WhatsApp SEND button with retries!
            var sent = false
            for (attempt in 1..6) {
                if (acc.clickWhatsAppSendButton()) {
                    sent = true
                    break
                }
                delay(400L)
            }

            com.example.utils.TTSManager.speak("WhatsApp par message send kar diya hai sir, ho gaya send!", context)
            return "WhatsApp par message send kar diya hai sir!"
        } else {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
            if (launchIntent != null) {
                val isAlreadyOpen = acc?.isWhatsAppInForeground() ?: false
                if (!isAlreadyOpen) {
                    com.example.utils.TTSManager.speak("Main WhatsApp khol raha hoon sir...", context)
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    delay(1800L)
                } else {
                    com.example.utils.TTSManager.speak("WhatsApp pehle se open hai sir...", context)
                    delay(400L)
                }
            } else {
                return "WhatsApp phone me nahi mila."
            }

            if (acc == null) {
                checkOrPromptAccessibility(context)
                return "WhatsApp khol diya hai. Contact select karke message bhejne ke liye Accessibility allow kijiye."
            }

            val dm = context.resources.displayMetrics
            val targetLabel = if (cleanRec.isNotBlank() && cleanRec != "kisi" && cleanRec != "kisi ko") cleanRec else "contact"

            if (cleanRec.isNotBlank() && !cleanRec.equals("kisi", ignoreCase = true) && !cleanRec.equals("kisi ko", ignoreCase = true)) {
                com.example.utils.TTSManager.speak("WhatsApp khul gaya, ab $targetLabel ki profile dhoondh raha hoon...", context)
                // Search for contact
                val searchClicked = acc.findAndClickByViewId("menuitem_search") ||
                        acc.findAndClick("search") || acc.findAndClick("खोजें") ||
                        acc.performHumanTap(dm.widthPixels * 0.82f, dm.heightPixels * 0.08f)
                delay(700L)
                acc.findAndType(cleanRec)
                delay(1000L)
                
                // Click on the matching contact card or first search result
                val contactClicked = acc.findAndClickFuzzy(cleanRec) || acc.clickFirstClickableInList() || acc.performHumanTap(dm.widthPixels * 0.5f, dm.heightPixels * 0.20f)
                delay(1200L)
            } else {
                com.example.utils.TTSManager.speak("WhatsApp khul gaya, chat open kar raha hoon...", context)
                acc.clickFirstClickableInList() || acc.performHumanTap(dm.widthPixels * 0.5f, dm.heightPixels * 0.22f)
                delay(1000L)
            }

            com.example.utils.TTSManager.speak("Chat open ho gayi, ab '$cleanMsg' likh raha hoon...", context)

            // Focus and Type into message entry field
            acc.findAndClickByViewId("entry") || acc.findAndClick("Message") || acc.findAndClick("संदेश")
            delay(400L)
            acc.findAndType(cleanMsg, "Message")
            delay(600L)

            // Click the Send button with retries!
            var sent = false
            for (attempt in 1..6) {
                if (acc.clickWhatsAppSendButton()) {
                    sent = true
                    break
                }
                delay(400L)
            }

            com.example.utils.TTSManager.speak("$targetLabel ko '$cleanMsg' bhej diya hai sir, ho gaya send!", context)
            return "WhatsApp par $targetLabel ko '$cleanMsg' bhej diya hai sir!"
        }
    }

    // 21. YouTube Play & Auto-Subscribe with Step-by-Step Voice Guidance
    suspend fun automateYouTubePlayAndSubscribe(context: Context, query: String, autoSubscribe: Boolean = true, autoLike: Boolean = false): String {
        val cleanQuery = query.trim().ifBlank { "trending song" }
        val acc = SaifAccessibilityService.instance

        val isYtOpen = acc?.isYouTubeInForeground() ?: false
        if (isYtOpen) {
            com.example.utils.TTSManager.speak("YouTube pehle se open hai sir. Ab '$cleanQuery' search kar raha hoon...", context)
            acc.findAndClickSearchButton()
            delay(500L)
            acc.findAndType(cleanQuery)
            delay(400L)
            acc.pressEnterOrSearch()
        } else {
            com.example.utils.TTSManager.speak("Main YouTube khol raha hoon sir...", context)
            val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                searchYouTube(context, cleanQuery)
            }
            delay(2000L)
            com.example.utils.TTSManager.speak("YouTube par '$cleanQuery' search kar diya hai...", context)
        }

        if (acc == null) {
            return "YouTube par '$cleanQuery' search kar diya hai. Video play karne ke liye Accessibility allow kijiye."
        }

        delay(1600L) // Wait for video results to populate

        com.example.utils.TTSManager.speak("Search results aa gaye hain, ab pehli video play kar raha hoon sir...", context)

        // Click first real video result, strictly skipping mic, voice search, and filter chips!
        var videoClicked = false
        for (attempt in 1..6) {
            if (acc.clickYouTubeFirstVideo()) {
                videoClicked = true
                break
            }
            delay(600L)
        }

        if (videoClicked) {
            com.example.utils.TTSManager.speak("Pehli video play kar di hai sir!", context)
        }

        var subMsg = ""
        if (autoSubscribe) {
            delay(2200L) // Wait for player to start playing
            com.example.utils.TTSManager.speak("Channel subscribe kar raha hoon sir...", context)
            acc.scroll(forward = true) // Scroll down slightly to reveal subscribe button below player
            delay(500L)
            for (attempt in 1..4) {
                if (acc.clickYouTubeSubscribe()) {
                    subMsg = " aur channel subscribe kar diya hai"
                    break
                }
                delay(600L)
            }
        }

        if (autoLike) {
            delay(500L)
            acc.findAndClickAny(listOf("like", "पसंद", "thumbs up"))
        }

        val videoLabel = if (cleanQuery.isNotBlank() && cleanQuery != "latest trending song") "'$cleanQuery'" else "pehla video"
        val statusMsg = if (videoClicked) "play kar diya hai" else "search kar diya hai"
        return "YouTube par $videoLabel $statusMsg$subMsg sir!"
    }

    // 22. Autonomous Shorts / Reels Feed Scroll
    suspend fun automateFeedScroll(context: Context, platform: String, times: Int = 1): String {
        val acc = SaifAccessibilityService.instance
        if (acc == null) {
            checkOrPromptAccessibility(context)
            return "Feed scroll karne ke liye Accessibility Service allow kijiye."
        }
        val repeatTimes = times.coerceIn(1, 10)
        for (i in 0 until repeatTimes) {
            acc.performHumanScrollDirection("down", distanceRatio = 0.55f)
            if (i < repeatTimes - 1) {
                delay(1200L)
            }
        }
        return "Feed scroll kar diya hai sir."
    }

    // 23. Active Screen Inspection
    fun inspectCurrentScreen(context: Context): String {
        val acc = SaifAccessibilityService.instance
        if (acc == null) {
            checkOrPromptAccessibility(context)
            return "Screen check karne ke liye Accessibility Service allow kijiye."
        }
        val summary = acc.inspectScreenHierarchy()
        return if (summary.isNotBlank()) {
            "Active screen par yeh elements maujood hain:\n$summary"
        } else {
            "Screen par koi text ya action items nahi mile."
        }
    }

    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
