package com.jarvis.assistant

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.jarvis.assistant.audio.VoskModelManager
import com.jarvis.assistant.automation.command.CommandExecutor
import com.jarvis.assistant.automation.contacts.ContactResolver
import com.jarvis.assistant.automation.phone.PhoneControlManager
import com.jarvis.assistant.automation.whatsapp.WhatsAppAssistant
import com.jarvis.assistant.automation.youtube.YouTubeAutomationManager
import com.jarvis.assistant.data.memory.MemoryDatabase
import com.jarvis.assistant.data.memory.MemoryManager
import com.jarvis.assistant.data.memory.MemoryRepository
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.data.preferences.YouTubePreferences
import com.jarvis.assistant.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Service locator of SAIF AI AUTOMATION (originally the JARVIS Application class).
 *
 * Inside SAIF AI the real Application belongs to the host app, so this is a normal object that is
 * created lazily the first time any automation code needs it. Nothing heavy runs until the user
 * actually opens SAIF AI AUTOMATION (red "+" button) or one of its services starts.
 */
class JarvisApp private constructor(private val appContext: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val automationActivities = mutableListOf<WeakReference<Activity>>()

    val preferences: AppPreferences by lazy { AppPreferences(appContext) }
    val chatRepository: ChatRepository by lazy { ChatRepository(preferences) }
    val memoryDatabase: MemoryDatabase by lazy { MemoryDatabase.getInstance(appContext) }
    val memoryRepository: MemoryRepository by lazy { MemoryRepository(memoryDatabase.memoryDao()) }
    val memoryManager: MemoryManager by lazy { MemoryManager(memoryRepository, preferences, chatRepository, appScope) }
    val youTubePreferences: YouTubePreferences by lazy { YouTubePreferences(appContext) }
    val youTubeAutomationManager: YouTubeAutomationManager by lazy { YouTubeAutomationManager(appContext, youTubePreferences) }
    val contactResolver: ContactResolver by lazy { ContactResolver(appContext) }
    val phoneControlManager: PhoneControlManager by lazy { PhoneControlManager(appContext, contactResolver) }
    val whatsAppAssistant: WhatsAppAssistant by lazy { WhatsAppAssistant(appContext, contactResolver) }
    val commandExecutor: CommandExecutor by lazy {
        CommandExecutor(
            phoneManager = phoneControlManager,
            youtubeManager = youTubeAutomationManager,
            whatsappAssistant = whatsAppAssistant,
            memoryManager = memoryManager
        )
    }

    var currentResumedActivity: Activity? = null
        private set

    init {
        (appContext as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private val startedIds = HashSet<Int>()

            private fun isAutomation(activity: Activity) =
                activity.javaClass.name.startsWith("com.jarvis.assistant.")

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (isAutomation(activity)) {
                    synchronized(automationActivities) { automationActivities.add(WeakReference(activity)) }
                    com.jarvis.assistant.util.ThemeManager.applyTheme(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) {
                startedIds.add(System.identityHashCode(activity))
                _isAppForeground.value = true
            }

            override fun onActivityResumed(activity: Activity) {
                currentResumedActivity = activity
                if (isAutomation(activity)) com.jarvis.assistant.util.ThemeManager.applyTheme(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentResumedActivity === activity) currentResumedActivity = null
            }

            override fun onActivityStopped(activity: Activity) {
                startedIds.remove(System.identityHashCode(activity))
                if (startedIds.isEmpty()) _isAppForeground.value = false
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentResumedActivity === activity) currentResumedActivity = null
                synchronized(automationActivities) {
                    automationActivities.removeAll { it.get() == null || it.get() === activity }
                }
            }
        })
        VoskModelManager.init(appContext, CoroutineScope(Dispatchers.Default))
    }

    /** Loads (or downloads, first run) the offline Vosk speech models; safe to call repeatedly. */
    fun ensureSpeechModels() {
        VoskModelManager.init(appContext, CoroutineScope(Dispatchers.Default))
    }

    fun moveAppToBack(): Boolean {
        val act = currentResumedActivity ?: return false
        return try {
            act.moveTaskToBack(true)
        } catch (e: Exception) {
            false
        }
    }

    /** Finishes every open SAIF AI AUTOMATION screen (used when jumping to the host Settings panel). */
    fun finishAutomationScreens() {
        val snapshot = synchronized(automationActivities) { automationActivities.mapNotNull { it.get() } }
        snapshot.forEach { runCatching { it.finish() } }
    }

    companion object {
        private val _isAppForeground = MutableStateFlow(false)
        val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()

        @Volatile
        private var holder: JarvisApp? = null

        val instance: JarvisApp
            get() = holder ?: synchronized(this) {
                holder ?: JarvisApp(AutomationContext.get()).also { holder = it }
            }
    }
}
