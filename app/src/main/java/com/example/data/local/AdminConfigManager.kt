package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

enum class ApiUsageType(val displayName: String) {
    CHAT("Chat Messages"),
    IMAGE("Image Generation"),
    APP_BUILD("App Building & Code"),
    GENERAL("Total API Calls")
}

data class LimitCheckResult(
    val isAllowed: Boolean,
    val currentUsage: Int,
    val maxLimit: Int,
    val usageType: ApiUsageType,
    val message: String
)

data class AdminGlobalConfig(
    val defaultApiKey: String = "",
    val defaultProvider: String = "Gemini",
    val defaultModel: String = "gemini-2.5-flash",
    val testingQuotaPerUser: Int = 10,
    val chatLimitPerUser: Int = 20,
    val imageLimitPerUser: Int = 5,
    val appBuildLimitPerUser: Int = 10,
    val totalApiLimitPerUser: Int = 35,
    val globalAnnouncement: String = "",
    val isAnnouncementActive: Boolean = false,
    val lastBroadcastTitle: String = "",
    val lastBroadcastMessage: String = "",
    val lastBroadcastTime: Long = 0L,
    val totalGlobalApiCalls: Long = 0L,
    val isMaintenanceMode: Boolean = false,
    val maintenanceMessage: String = "SAIF AI is currently under scheduled maintenance. Please check back shortly.",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
)

data class ManagedUser(
    val uid: String,
    val email: String,
    val name: String,
    val role: String = "user",
    val isActive: Boolean = true,
    val isBanned: Boolean = false,
    val apiUsageCount: Int = 0,
    val chatUsageCount: Int = 0,
    val imageUsageCount: Int = 0,
    val appBuildUsageCount: Int = 0,
    val customChatLimit: Int? = null,
    val customImageLimit: Int? = null,
    val customAppBuildLimit: Int? = null,
    val customTotalLimit: Int? = null,
    val isUnlimited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
}

object AdminConfigManager {
    private const val TAG = "AdminConfigManager"
    private const val PREFS_NAME = "SaifAdminConfigPrefs"
    private const val KEY_CONFIG_JSON = "admin_global_config_json"
    private const val KEY_USER_API_USAGE = "user_api_usage_count"
    private const val KEY_USER_CHAT_USAGE = "user_chat_usage_count"
    private const val KEY_USER_IMAGE_USAGE = "user_image_usage_count"
    private const val KEY_USER_APPBUILD_USAGE = "user_appbuild_usage_count"

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private var configListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null

    private val _globalConfig = MutableStateFlow(AdminGlobalConfig())
    val globalConfig: StateFlow<AdminGlobalConfig> = _globalConfig.asStateFlow()

    private val _userApiUsage = MutableStateFlow(0)
    val userApiUsage: StateFlow<Int> = _userApiUsage.asStateFlow()

    private val _userChatUsage = MutableStateFlow(0)
    val userChatUsage: StateFlow<Int> = _userChatUsage.asStateFlow()

    private val _userImageUsage = MutableStateFlow(0)
    val userImageUsage: StateFlow<Int> = _userImageUsage.asStateFlow()

    private val _userAppBuildUsage = MutableStateFlow(0)
    val userAppBuildUsage: StateFlow<Int> = _userAppBuildUsage.asStateFlow()

    private val _apiLimitReachedEvent = MutableSharedFlow<LimitCheckResult>(extraBufferCapacity = 1)
    val apiLimitReachedEvent: SharedFlow<LimitCheckResult> = _apiLimitReachedEvent.asSharedFlow()

    private val _managedUsers = MutableStateFlow<List<ManagedUser>>(emptyList())
    val managedUsers: StateFlow<List<ManagedUser>> = _managedUsers.asStateFlow()

    private val _isLoadingUsers = MutableStateFlow(false)
    val isLoadingUsers: StateFlow<Boolean> = _isLoadingUsers.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load cached config
        loadLocalCache()

        // Start listening to Firebase Firestore if available
        startFirestoreSync()
    }

    private fun loadLocalCache() {
        val googleDefaultKey = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }.trim()
        val cleanGoogleDefault = if (googleDefaultKey.isNotBlank() && !googleDefaultKey.contains("MY_")) googleDefaultKey else ""

        val jsonStr = prefs.getString(KEY_CONFIG_JSON, null)
        if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                val rawKey = json.optString("defaultApiKey", "").trim()
                val rawProv = json.optString("defaultProvider", "Gemini").trim()
                val finalProv = if (rawProv.isBlank()) "Gemini" else rawProv
                val finalKey = if (rawKey.isBlank() || rawKey.contains("MY_")) {
                    if (finalProv.equals("Gemini", ignoreCase = true)) cleanGoogleDefault else ""
                } else rawKey

                val rawModel = json.optString("defaultModel", com.example.llm.GeminiModelCatalog.DEFAULT_MODEL).trim()
                val cleanModel = if (rawModel.isBlank() || com.example.llm.GeminiModelCatalog.isRetired(rawModel)) com.example.llm.GeminiModelCatalog.DEFAULT_MODEL else rawModel

                _globalConfig.value = AdminGlobalConfig(
                    defaultApiKey = finalKey,
                    defaultProvider = finalProv,
                    defaultModel = cleanModel,
                    testingQuotaPerUser = json.optInt("testingQuotaPerUser", 10),
                    chatLimitPerUser = json.optInt("chatLimitPerUser", 20),
                    imageLimitPerUser = json.optInt("imageLimitPerUser", 5),
                    appBuildLimitPerUser = json.optInt("appBuildLimitPerUser", 10),
                    totalApiLimitPerUser = json.optInt("totalApiLimitPerUser", 35),
                    globalAnnouncement = json.optString("globalAnnouncement", ""),
                    isAnnouncementActive = json.optBoolean("isAnnouncementActive", false),
                    lastBroadcastTitle = json.optString("lastBroadcastTitle", ""),
                    lastBroadcastMessage = json.optString("lastBroadcastMessage", ""),
                    lastBroadcastTime = json.optLong("lastBroadcastTime", 0L),
                    totalGlobalApiCalls = json.optLong("totalGlobalApiCalls", 0L),
                    isMaintenanceMode = json.optBoolean("isMaintenanceMode", false),
                    maintenanceMessage = json.optString("maintenanceMessage", "SAIF AI is currently under scheduled maintenance. Please check back shortly."),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                    updatedBy = json.optString("updatedBy", "")
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error loading cached config", e)
            }
        } else {
            _globalConfig.value = AdminGlobalConfig(
                defaultApiKey = cleanGoogleDefault,
                defaultProvider = "Gemini",
                defaultModel = com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
            )
        }
        _userApiUsage.value = prefs.getInt(KEY_USER_API_USAGE, 0)
        _userChatUsage.value = prefs.getInt(KEY_USER_CHAT_USAGE, 0)
        _userImageUsage.value = prefs.getInt(KEY_USER_IMAGE_USAGE, 0)
        _userAppBuildUsage.value = prefs.getInt(KEY_USER_APPBUILD_USAGE, 0)
    }

    private fun saveLocalCache(config: AdminGlobalConfig) {
        val json = JSONObject().apply {
            put("defaultApiKey", config.defaultApiKey)
            put("defaultProvider", config.defaultProvider)
            put("defaultModel", config.defaultModel)
            put("testingQuotaPerUser", config.testingQuotaPerUser)
            put("chatLimitPerUser", config.chatLimitPerUser)
            put("imageLimitPerUser", config.imageLimitPerUser)
            put("appBuildLimitPerUser", config.appBuildLimitPerUser)
            put("totalApiLimitPerUser", config.totalApiLimitPerUser)
            put("globalAnnouncement", config.globalAnnouncement)
            put("isAnnouncementActive", config.isAnnouncementActive)
            put("lastBroadcastTitle", config.lastBroadcastTitle)
            put("lastBroadcastMessage", config.lastBroadcastMessage)
            put("lastBroadcastTime", config.lastBroadcastTime)
            put("totalGlobalApiCalls", config.totalGlobalApiCalls)
            put("isMaintenanceMode", config.isMaintenanceMode)
            put("maintenanceMessage", config.maintenanceMessage)
            put("updatedAt", config.updatedAt)
            put("updatedBy", config.updatedBy)
        }
        prefs.edit().putString(KEY_CONFIG_JSON, json.toString()).apply()
    }

    fun startFirestoreSync() {
        if (!AuthManager.isFirebaseAvailable()) return

        try {
            val firestore = FirebaseFirestore.getInstance()
            configListener?.remove()

            // Listen to system_config/api_settings
            configListener = firestore.collection("system_config").document("api_settings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        try {
                            configListener?.remove()
                        } catch (ignored: Exception) {}
                        configListener = null

                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d(TAG, "Remote Firestore config not accessible or permission denied. Using local configuration.")
                        } else {
                            Log.w(TAG, "Firestore config listen note: ${error.message}")
                        }
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val rawKey = snapshot.getString("defaultApiKey") ?: snapshot.getString("apiKey") ?: ""
                        val rawProvider = snapshot.getString("defaultProvider") ?: snapshot.getString("provider") ?: "Gemini"
                        val provider = if (rawProvider.isBlank()) "Gemini" else rawProvider

                        val googleDefaultKey = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }.trim()
                        val cleanGoogleDefault = if (googleDefaultKey.isNotBlank() && !googleDefaultKey.contains("MY_")) googleDefaultKey else ""
                        val apiKey = if (rawKey.isBlank() || rawKey.contains("MY_")) {
                            if (provider.equals("Gemini", ignoreCase = true)) cleanGoogleDefault else ""
                        } else rawKey

                        val rawModel = snapshot.getString("defaultModel") ?: snapshot.getString("model") ?: com.example.llm.GeminiModelCatalog.DEFAULT_MODEL
                        val model = if ((rawModel.contains("llama", ignoreCase = true) && provider == "Gemini") || (provider == "Gemini" && com.example.llm.GeminiModelCatalog.isRetired(rawModel))) com.example.llm.GeminiModelCatalog.DEFAULT_MODEL else rawModel

                        val quota = snapshot.getLong("testingQuotaPerUser")?.toInt() ?: snapshot.getLong("quota")?.toInt() ?: 10
                        val chatLimit = snapshot.getLong("chatLimitPerUser")?.toInt() ?: 20
                        val imageLimit = snapshot.getLong("imageLimitPerUser")?.toInt() ?: 5
                        val appBuildLimit = snapshot.getLong("appBuildLimitPerUser")?.toInt() ?: 10
                        val totalLimit = snapshot.getLong("totalApiLimitPerUser")?.toInt() ?: 35
                        val announcement = snapshot.getString("globalAnnouncement") ?: snapshot.getString("announcement") ?: ""
                        val isAnnouncement = snapshot.getBoolean("isAnnouncementActive") ?: false
                        val lastBTitle = snapshot.getString("lastBroadcastTitle") ?: ""
                        val lastBMsg = snapshot.getString("lastBroadcastMessage") ?: ""
                        val lastBTime = snapshot.getLong("lastBroadcastTime") ?: 0L
                        val totalCalls = snapshot.getLong("totalGlobalApiCalls") ?: 0L
                        val isMaint = snapshot.getBoolean("isMaintenanceMode") ?: false
                        val maintMsg = snapshot.getString("maintenanceMessage") ?: "SAIF AI is currently under scheduled maintenance."
                        val updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                        val updatedBy = snapshot.getString("updatedBy") ?: ""

                        val prevConfig = _globalConfig.value
                        if (lastBTime > prevConfig.lastBroadcastTime && lastBMsg.isNotBlank()) {
                            // Automatically fire broadcast notification on user's device!
                            if (::appContext.isInitialized) {
                                com.example.util.SaifBroadcastNotificationHelper.postBroadcastNotification(
                                    appContext,
                                    lastBTitle.ifBlank { "📢 SAIF AI Announcement" },
                                    lastBMsg
                                )
                            }
                        }

                        val newConfig = AdminGlobalConfig(
                            defaultApiKey = apiKey,
                            defaultProvider = provider,
                            defaultModel = model,
                            testingQuotaPerUser = quota,
                            chatLimitPerUser = chatLimit,
                            imageLimitPerUser = imageLimit,
                            appBuildLimitPerUser = appBuildLimit,
                            totalApiLimitPerUser = totalLimit,
                            globalAnnouncement = announcement,
                            isAnnouncementActive = isAnnouncement,
                            lastBroadcastTitle = lastBTitle,
                            lastBroadcastMessage = lastBMsg,
                            lastBroadcastTime = lastBTime,
                            totalGlobalApiCalls = totalCalls,
                            isMaintenanceMode = isMaint,
                            maintenanceMessage = maintMsg,
                            updatedAt = updatedAt,
                            updatedBy = updatedBy
                        )
                        _globalConfig.value = newConfig
                        saveLocalCache(newConfig)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore sync initialization note: ${e.message}")
        }
    }

    suspend fun saveGlobalConfig(config: AdminGlobalConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _globalConfig.value = config
            saveLocalCache(config)

            if (AuthManager.isFirebaseAvailable()) {
                val firestore = FirebaseFirestore.getInstance()
                val data = mapOf(
                    "defaultApiKey" to config.defaultApiKey,
                    "defaultProvider" to config.defaultProvider,
                    "defaultModel" to config.defaultModel,
                    "testingQuotaPerUser" to config.testingQuotaPerUser,
                    "chatLimitPerUser" to config.chatLimitPerUser,
                    "imageLimitPerUser" to config.imageLimitPerUser,
                    "appBuildLimitPerUser" to config.appBuildLimitPerUser,
                    "totalApiLimitPerUser" to config.totalApiLimitPerUser,
                    "globalAnnouncement" to config.globalAnnouncement,
                    "isAnnouncementActive" to config.isAnnouncementActive,
                    "lastBroadcastTitle" to config.lastBroadcastTitle,
                    "lastBroadcastMessage" to config.lastBroadcastMessage,
                    "lastBroadcastTime" to config.lastBroadcastTime,
                    "totalGlobalApiCalls" to config.totalGlobalApiCalls,
                    "isMaintenanceMode" to config.isMaintenanceMode,
                    "maintenanceMessage" to config.maintenanceMessage,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to config.updatedBy
                )

                // Sync to system_config/api_settings and app_config/global_settings for full coverage
                try {
                    firestore.collection("system_config").document("api_settings").set(data, SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Note: system_config save skipped: ${e.message}")
                }
                try {
                    firestore.collection("app_config").document("global_settings").set(data, SetOptions.merge()).await()
                } catch (ignored: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save global config to Firestore", e)
            Result.failure(e)
        }
    }

    fun recordApiUsage(type: ApiUsageType = ApiUsageType.GENERAL, email: String = "") {
        val newTotal = _userApiUsage.value + 1
        _userApiUsage.value = newTotal
        prefs.edit().putInt(KEY_USER_API_USAGE, newTotal).apply()

        when (type) {
            ApiUsageType.CHAT -> {
                val newC = _userChatUsage.value + 1
                _userChatUsage.value = newC
                prefs.edit().putInt(KEY_USER_CHAT_USAGE, newC).apply()
            }
            ApiUsageType.IMAGE -> {
                val newI = _userImageUsage.value + 1
                _userImageUsage.value = newI
                prefs.edit().putInt(KEY_USER_IMAGE_USAGE, newI).apply()
            }
            ApiUsageType.APP_BUILD -> {
                val newA = _userAppBuildUsage.value + 1
                _userAppBuildUsage.value = newA
                prefs.edit().putInt(KEY_USER_APPBUILD_USAGE, newA).apply()
            }
            ApiUsageType.GENERAL -> {}
        }

        // Sync to cloud and local Room DB
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SaifDatabase.getDatabase(appContext)
                val currentUser = AuthManager.currentUser.value
                val targetEmail = email.ifBlank { currentUser?.email ?: "" }
                if (targetEmail.isNotBlank()) {
                    db.userAccountDao().updateApiUsage(targetEmail, newTotal)
                }

                if (AuthManager.isFirebaseAvailable()) {
                    val firestore = FirebaseFirestore.getInstance()
                    if (currentUser != null && currentUser.uid.isNotBlank()) {
                        val usageUpdate = mapOf(
                            "apiUsageCount" to newTotal,
                            "chatUsageCount" to _userChatUsage.value,
                            "imageUsageCount" to _userImageUsage.value,
                            "appBuildUsageCount" to _userAppBuildUsage.value,
                            "lastApiUseTime" to System.currentTimeMillis()
                        )
                        firestore.collection("users").document(currentUser.uid).set(usageUpdate, SetOptions.merge()).await()
                        try {
                            firestore.collection("Users").document(currentUser.uid).set(usageUpdate, SetOptions.merge()).await()
                        } catch (ignored: Exception) {}
                    }

                    // Increment global count
                    try {
                        firestore.collection("system_config").document("api_settings")
                            .update("totalGlobalApiCalls", com.google.firebase.firestore.FieldValue.increment(1))
                    } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync api usage: ${e.message}")
            }
        }
    }

    fun canUseApi(type: ApiUsageType = ApiUsageType.GENERAL, hasCustomUserKey: Boolean): LimitCheckResult {
        val currentUser = AuthManager.currentUser.value
        // Admins always have unlimited access to their own fleet API
        if (currentUser?.isAdmin == true) {
            return LimitCheckResult(true, 0, Int.MAX_VALUE, type, "👑 Master Admin Access (Unlimited)")
        }

        // If user provided their own key, ALL limits are permanently lifted!
        if (hasCustomUserKey) {
            return LimitCheckResult(true, 0, Int.MAX_VALUE, type, "🚀 Personal API Key Active (Unlimited)")
        }

        val config = _globalConfig.value
        if (config.defaultApiKey.isBlank()) {
            return LimitCheckResult(false, 0, 0, type, "⚠️ Admin dwara koi default API set nahi hai. Kripya Settings (⚙️) me jakar apni personal API key dalein.")
        }

        // Look for targeted custom user overrides
        val managed = _managedUsers.value.firstOrNull { 
            (currentUser != null && it.email.equals(currentUser.email, ignoreCase = true)) || 
            (currentUser != null && it.uid == currentUser.uid)
        }

        if (managed?.isUnlimited == true) {
            return LimitCheckResult(true, 0, Int.MAX_VALUE, type, "🌟 VIP Unlimited Access Granted")
        }

        val (currentUsage, maxLimit) = when (type) {
            ApiUsageType.CHAT -> {
                val usage = _userChatUsage.value
                val limit = (managed?.customChatLimit?.takeIf { it > 0 }) ?: config.chatLimitPerUser
                usage to limit
            }
            ApiUsageType.IMAGE -> {
                val usage = _userImageUsage.value
                val limit = (managed?.customImageLimit?.takeIf { it > 0 }) ?: config.imageLimitPerUser
                usage to limit
            }
            ApiUsageType.APP_BUILD -> {
                val usage = _userAppBuildUsage.value
                val limit = (managed?.customAppBuildLimit?.takeIf { it > 0 }) ?: config.appBuildLimitPerUser
                usage to limit
            }
            ApiUsageType.GENERAL -> {
                val usage = _userApiUsage.value
                val limit = (managed?.customTotalLimit?.takeIf { it > 0 }) ?: config.totalApiLimitPerUser
                usage to limit
            }
        }

        val totalUsage = _userApiUsage.value
        val totalLimit = (managed?.customTotalLimit?.takeIf { it > 0 }) ?: config.totalApiLimitPerUser

        if (currentUsage >= maxLimit || totalUsage >= totalLimit) {
            val msg = "⚠️ Aapki free usage limit khatam ho chuki hai ($currentUsage/$maxLimit calls used). Kripya Settings (⚙️) -> 'AI Provider' me jakar apni personal API key dalein taaki aap unlimited use kar sakein."
            val result = LimitCheckResult(false, currentUsage, maxLimit, type, msg)
            _apiLimitReachedEvent.tryEmit(result)
            return result
        }

        return LimitCheckResult(true, currentUsage, maxLimit, type, "${type.displayName}: $currentUsage/$maxLimit used")
    }

    fun sendBroadcastNotification(context: Context, title: String, message: String): Boolean {
        val success = com.example.util.SaifBroadcastNotificationHelper.postBroadcastNotification(context, title, message)
        val cur = _globalConfig.value
        val updated = cur.copy(
            globalAnnouncement = message,
            isAnnouncementActive = true,
            lastBroadcastTitle = title,
            lastBroadcastMessage = message,
            lastBroadcastTime = System.currentTimeMillis()
        )
        _globalConfig.value = updated
        saveLocalCache(updated)

        CoroutineScope(Dispatchers.IO).launch {
            if (AuthManager.isFirebaseAvailable()) {
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val bData = mapOf(
                        "title" to title,
                        "message" to message,
                        "timestamp" to System.currentTimeMillis(),
                        "sender" to (AuthManager.currentUser.value?.email ?: "Admin")
                    )
                    firestore.collection("broadcasts").add(bData).await()
                    firestore.collection("system_config").document("api_settings").set(
                        mapOf(
                            "globalAnnouncement" to message,
                            "isAnnouncementActive" to true,
                            "lastBroadcastTitle" to title,
                            "lastBroadcastMessage" to message,
                            "lastBroadcastTime" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore broadcast sync note: ${e.message}")
                }
            }
        }
        return success
    }

    fun canUseAdminApi(hasCustomUserKey: Boolean): Pair<Boolean, String> {
        val res = canUseApi(ApiUsageType.GENERAL, hasCustomUserKey)
        return Pair(res.isAllowed, res.message)
    }

    suspend fun fetchAllUsers(): Result<List<ManagedUser>> = withContext(Dispatchers.IO) {
        _isLoadingUsers.value = true
        try {
            val userMap = mutableMapOf<String, ManagedUser>()

            // 1. Fetch from Room Database (ensures local & offline accounts appear)
            try {
                val db = SaifDatabase.getDatabase(appContext)
                val roomUsers = db.userAccountDao().getAllUsers()
                for (ru in roomUsers) {
                    val mu = ManagedUser(
                        uid = ru.email,
                        email = ru.email,
                        name = ru.name,
                        role = ru.role,
                        isActive = ru.isActive,
                        isBanned = ru.isBanned,
                        apiUsageCount = ru.apiUsageCount,
                        createdAt = ru.createdAt
                    )
                    userMap[mu.email.lowercase()] = mu
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from Room database", e)
            }

            // 2. Fetch from collection "users" in Firestore
            if (AuthManager.isFirebaseAvailable()) {
                val firestore = FirebaseFirestore.getInstance()
                try {
                    val usersSnap = firestore.collection("users").get().await()
                    for (doc in usersSnap.documents) {
                        val u = parseManagedUser(doc.id, doc.data ?: emptyMap())
                        val key = if (u.email.isNotBlank()) u.email.lowercase() else u.uid
                        val existing = userMap[key]
                        if (existing != null) {
                            userMap[key] = existing.copy(
                                uid = if (u.uid.isNotBlank()) u.uid else existing.uid,
                                role = if (u.role.equals("admin", true)) u.role else existing.role,
                                isActive = u.isActive,
                                isBanned = !u.isActive,
                                apiUsageCount = maxOf(existing.apiUsageCount, u.apiUsageCount)
                            )
                        } else {
                            userMap[key] = u
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching from 'users' collection", e)
                }

                // 3. Fetch from collection "Users" (custom collection from console)
                try {
                    val usersCapSnap = firestore.collection("Users").get().await()
                    for (doc in usersCapSnap.documents) {
                        val u = parseManagedUser(doc.id, doc.data ?: emptyMap())
                        val key = if (u.email.isNotBlank()) u.email.lowercase() else u.uid
                        val existing = userMap[key]
                        if (existing != null) {
                            userMap[key] = existing.copy(
                                uid = if (u.uid.isNotBlank()) u.uid else existing.uid,
                                role = if (u.role.equals("admin", true)) u.role else existing.role,
                                isActive = u.isActive,
                                isBanned = !u.isActive,
                                apiUsageCount = maxOf(existing.apiUsageCount, u.apiUsageCount)
                            )
                        } else {
                            userMap[key] = u
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching from 'Users' collection", e)
                }
            }

            val resultList = userMap.values.sortedByDescending { it.createdAt }
            _managedUsers.value = resultList
            _isLoadingUsers.value = false
            Result.success(resultList)
        } catch (e: Exception) {
            _isLoadingUsers.value = false
            Log.w(TAG, "Failed to fetch all users", e)
            Result.failure(e)
        }
    }

    private fun parseManagedUser(docId: String, data: Map<String, Any?>): ManagedUser {
        val email = (data["email"] as? String)
            ?: (data["E-mail"] as? String)
            ?: (data["Email"] as? String)
            ?: ""

        val name = (data["name"] as? String)
            ?: (data["Name"] as? String)
            ?: (data["Username"] as? String)
            ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

        val role = AuthManager.extractRole(data)

        val isActive = when {
            data.containsKey("Is active") -> data["Is active"] as? Boolean ?: true
            data.containsKey("isActive") -> data["isActive"] as? Boolean ?: true
            data.containsKey("status") -> (data["status"] as? String)?.lowercase() != "suspended" && (data["status"] as? String)?.lowercase() != "banned"
            else -> true
        }

        val usage = (data["apiUsageCount"] as? Number)?.toInt()
            ?: (data["api_usage_count"] as? Number)?.toInt()
            ?: 0

        val createdAt = (data["createdAt"] as? Number)?.toLong()
            ?: System.currentTimeMillis()

        return ManagedUser(
            uid = docId,
            email = email,
            name = name,
            role = role,
            isActive = isActive,
            isBanned = !isActive,
            apiUsageCount = usage,
            createdAt = createdAt
        )
    }

    suspend fun updateUserStatus(targetUser: ManagedUser, newActiveState: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Update Room database
            try {
                val db = SaifDatabase.getDatabase(appContext)
                db.userAccountDao().updateUserStatus(targetUser.email, newActiveState, !newActiveState)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update Room status", e)
            }

            // Update Firestore if available
            if (AuthManager.isFirebaseAvailable()) {
                val firestore = FirebaseFirestore.getInstance()
                val update = mapOf(
                    "isActive" to newActiveState,
                    "Is active" to newActiveState,
                    "status" to (if (newActiveState) "active" else "suspended"),
                    "updatedAt" to System.currentTimeMillis()
                )

                try { firestore.collection("users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                try { firestore.collection("Users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                if (targetUser.email.isNotBlank()) {
                    try { firestore.collection("users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                    try { firestore.collection("Users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                }
            }

            // Update local memory list
            val updatedList = _managedUsers.value.map {
                if (it.email.equals(targetUser.email, true) || it.uid == targetUser.uid) it.copy(isActive = newActiveState, isBanned = !newActiveState) else it
            }
            _managedUsers.value = updatedList

            // If the user modified is the current logged-in user, update session live
            val current = AuthManager.currentUser.value
            if (current != null && current.email.equals(targetUser.email, true)) {
                AuthManager.refreshCurrentUserData()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update user status", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserRole(targetUser: ManagedUser, newRole: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanRole = newRole.lowercase()

            // Update Room database
            try {
                val db = SaifDatabase.getDatabase(appContext)
                db.userAccountDao().updateUserRole(targetUser.email, cleanRole)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update Room role", e)
            }

            // Update Firestore if available
            if (AuthManager.isFirebaseAvailable()) {
                val firestore = FirebaseFirestore.getInstance()
                val update = mapOf(
                    "role" to cleanRole,
                    "Role" to cleanRole,
                    "updatedAt" to System.currentTimeMillis()
                )

                try { firestore.collection("users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                try { firestore.collection("Users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                if (targetUser.email.isNotBlank()) {
                    try { firestore.collection("users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                    try { firestore.collection("Users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                }
            }

            val updatedList = _managedUsers.value.map {
                if (it.email.equals(targetUser.email, true) || it.uid == targetUser.uid) it.copy(role = cleanRole) else it
            }
            _managedUsers.value = updatedList

            val current = AuthManager.currentUser.value
            if (current != null && current.email.equals(targetUser.email, true)) {
                AuthManager.refreshCurrentUserData()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update user role", e)
            Result.failure(e)
        }
    }

    suspend fun resetUserQuota(targetUser: ManagedUser): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Update Room database
            try {
                val db = SaifDatabase.getDatabase(appContext)
                db.userAccountDao().updateApiUsage(targetUser.email, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reset Room quota", e)
            }

            // Update Firestore if available
            if (AuthManager.isFirebaseAvailable()) {
                val firestore = FirebaseFirestore.getInstance()
                val update = mapOf(
                    "apiUsageCount" to 0,
                    "api_usage_count" to 0
                )

                try { firestore.collection("users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                try { firestore.collection("Users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                if (targetUser.email.isNotBlank()) {
                    try { firestore.collection("users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                    try { firestore.collection("Users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                }
            }

            val updatedList = _managedUsers.value.map {
                if (it.email.equals(targetUser.email, true) || it.uid == targetUser.uid) it.copy(apiUsageCount = 0) else it
            }
            _managedUsers.value = updatedList

            val current = AuthManager.currentUser.value
            if (current != null && current.email.equals(targetUser.email, true)) {
                _userApiUsage.value = 0
                prefs.edit().putInt(KEY_USER_API_USAGE, 0).apply()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset quota", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUser(targetUser: ManagedUser): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            try {
                val db = SaifDatabase.getDatabase(appContext)
                db.userAccountDao().deleteUser(targetUser.email)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete from Room", e)
            }

            if (AuthManager.isFirebaseAvailable()) {
                val firestore = FirebaseFirestore.getInstance()
                try { firestore.collection("users").document(targetUser.uid).delete().await() } catch (ignored: Exception) {}
                try { firestore.collection("Users").document(targetUser.uid).delete().await() } catch (ignored: Exception) {}
                if (targetUser.email.isNotBlank()) {
                    try { firestore.collection("users").document(targetUser.email).delete().await() } catch (ignored: Exception) {}
                    try { firestore.collection("Users").document(targetUser.email).delete().await() } catch (ignored: Exception) {}
                }
            }

            _managedUsers.value = _managedUsers.value.filter { it.email != targetUser.email && it.uid != targetUser.uid }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCustomUserLimits(
        targetUser: ManagedUser,
        chatLimit: Int?,
        imageLimit: Int?,
        appBuildLimit: Int?,
        totalLimit: Int?,
        isUnlimited: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (AuthManager.isFirebaseAvailable()) {
                val firestore = FirebaseFirestore.getInstance()
                val update = mutableMapOf<String, Any?>(
                    "customChatLimit" to chatLimit,
                    "customImageLimit" to imageLimit,
                    "customAppBuildLimit" to appBuildLimit,
                    "customTotalLimit" to totalLimit,
                    "isUnlimited" to isUnlimited,
                    "updatedAt" to System.currentTimeMillis()
                )
                try { firestore.collection("users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                try { firestore.collection("Users").document(targetUser.uid).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                if (targetUser.email.isNotBlank()) {
                    try { firestore.collection("users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                    try { firestore.collection("Users").document(targetUser.email).set(update, SetOptions.merge()).await() } catch (ignored: Exception) {}
                }
            }

            val updatedList = _managedUsers.value.map {
                if (it.email.equals(targetUser.email, true) || it.uid == targetUser.uid) {
                    it.copy(
                        customChatLimit = chatLimit,
                        customImageLimit = imageLimit,
                        customAppBuildLimit = appBuildLimit,
                        customTotalLimit = totalLimit,
                        isUnlimited = isUnlimited
                    )
                } else it
            }
            _managedUsers.value = updatedList
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update custom limits", e)
            Result.failure(e)
        }
    }

    fun resetLocalUserUsage() {
        _userApiUsage.value = 0
        _userChatUsage.value = 0
        _userImageUsage.value = 0
        _userAppBuildUsage.value = 0
        prefs.edit()
            .putInt(KEY_USER_API_USAGE, 0)
            .putInt(KEY_USER_CHAT_USAGE, 0)
            .putInt(KEY_USER_IMAGE_USAGE, 0)
            .putInt(KEY_USER_APPBUILD_USAGE, 0)
            .apply()
    }

    suspend fun testApiKey(apiKey: String, provider: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter an API Key to test"))
        }

        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = when {
                provider.contains("Ashna", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "ashna-x1" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.ashna.ai/v1/api/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("x-api-key", cleanKey)
                        .addHeader("api-key", cleanKey)
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("Gemini", ignoreCase = true) -> {
                    val rawM = model.trim().ifBlank { com.example.llm.GeminiModelCatalog.DEFAULT_MODEL }
                    val m = if (rawM.isBlank() || com.example.llm.GeminiModelCatalog.isRetired(rawM)) com.example.llm.GeminiModelCatalog.DEFAULT_MODEL else rawM
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$cleanKey"
                    val json = org.json.JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().put("text", "Ping"))
                                })
                            })
                        })
                    }
                    okhttp3.Request.Builder()
                        .url(url)
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("Anthropic", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "claude-3-5-sonnet-20241022" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("max_tokens", 10)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.anthropic.com/v1/messages")
                        .addHeader("x-api-key", cleanKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .addHeader("content-type", "application/json")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("OpenRouter", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "google/gemini-2.0-flash-001" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("HTTP-Referer", "https://saif.ai")
                        .addHeader("X-Title", "SAIF AI Fleet")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("OpenAI", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "gpt-4o-mini" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("Groq", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "llama-3.3-70b-versatile" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.groq.com/openai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("DeepSeek", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "deepseek-chat" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.deepseek.com/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("Mistral", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "mistral-small-latest" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.mistral.ai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("Together", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "meta-llama/Llama-3.3-70B-Instruct-Turbo" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.together.xyz/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("Cerebras", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "llama-3.3-70b" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.cerebras.ai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("xAI", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "grok-2-latest" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.x.ai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.equals("Perplexity", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "sonar" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.perplexity.ai/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.contains("inception", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "mercury-2.5" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.inceptionlabs.ai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.contains("atria", ignoreCase = true) -> {
                    val rawM = model.trim()
                    val m = if (rawM.contains("FP8", true)) "Atria-Dawn-Preview-FP8"
                    else if (rawM.isBlank() || rawM.equals("atria-1", true) || rawM.equals("atria-dawn", true) || rawM.contains("dawn", true)) "Atria-Dawn-Preview"
                    else rawM
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://api.atria-asi.ai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("X-Api-Key", cleanKey)
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                provider.contains("OpenCode", ignoreCase = true) -> {
                    val m = model.trim().ifBlank { "glm-5.1" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url("https://opencode.ai/inference/openai/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("X-Api-Key", cleanKey)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
                else -> {
                    val url = if (provider.startsWith("http://") || provider.startsWith("https://")) provider else "https://api.openai.com/v1/chat/completions"
                    val m = model.trim().ifBlank { "gpt-4o-mini" }
                    val json = org.json.JSONObject().apply {
                        put("model", m)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("role", "user").put("content", "Ping"))
                        })
                    }
                    okhttp3.Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            }

            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code in 200..299) {
                Result.success("✅ Connection Successful! Provider ($provider) verified.")
            } else if (code == 401 || code == 403) {
                Result.failure(IllegalStateException("Invalid API key or unauthorized ($code). Please check key."))
            } else if (code == 429) {
                Result.success("⚠️ API Key is valid, but currently rate-limited by $provider ($code).")
            } else {
                Result.failure(IllegalStateException("Provider returned status $code: ${body.take(120)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchEventsAndRegistrations(): Pair<List<Map<String, Any?>>, List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        if (!AuthManager.isFirebaseAvailable()) return@withContext Pair(emptyList(), emptyList())

        val events = mutableListOf<Map<String, Any?>>()
        val registrations = mutableListOf<Map<String, Any?>>()

        try {
            val firestore = FirebaseFirestore.getInstance()
            try {
                val evSnap = firestore.collection("Event001").get().await()
                for (d in evSnap.documents) {
                    val m = d.data?.toMutableMap() ?: mutableMapOf()
                    m["_id"] = d.id
                    events.add(m)
                }
            } catch (ignored: Exception) {}

            try {
                val regSnap = firestore.collection("Registration").get().await()
                for (d in regSnap.documents) {
                    val m = d.data?.toMutableMap() ?: mutableMapOf()
                    m["_id"] = d.id
                    registrations.add(m)
                }
            } catch (ignored: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch events/registrations: ${e.message}")
        }

        Pair(events, registrations)
    }
}
