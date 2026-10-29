package com.example.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.AuthManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MemoryFactItem(
    val key: String,
    val title: String,
    val value: String,
    val category: String,
    val icon: String = "brain"
)

/**
 * Advanced Saif AI Long-Term Smart Memory & Privacy Vault.
 * Intelligently captures user personal facts (Name, Favorite Color, Cars, Games, Hobbies, Devices)
 * and seamlessly provides them to LLMs across turns and future sessions.
 * Features customizable PIN lock and account password recovery.
 */
object SmartConversationMemory {
    private const val TAG = "SmartConversationMemory"
    private const val PREFS_NAME = "SmartConversationMemoryPrefs"
    private const val PIN_PREF_KEY = "memory_custom_pin_hash"
    private const val PIN_ENABLED_KEY = "memory_pin_enabled"

    private var prefs: SharedPreferences? = null
    private val memoryMap = ConcurrentHashMap<String, String>()
    private var activeTopicOrEntity: String = ""

    private val _isPinProtected = MutableStateFlow<Boolean>(false)
    val isPinProtected: StateFlow<Boolean> = _isPinProtected.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromPrefs()
        }
        _isPinProtected.value = isPinLockEnabled()
    }

    private fun loadFromPrefs() {
        val all = prefs?.all ?: return
        for ((key, value) in all) {
            if (key != PIN_PREF_KEY && key != PIN_ENABLED_KEY && value is String && value.isNotBlank()) {
                memoryMap[key] = value
            }
        }
    }

    fun saveFact(key: String, value: String) {
        val trimmedVal = value.trim()
        if (trimmedVal.isBlank()) return
        memoryMap[key] = trimmedVal
        prefs?.edit()?.putString(key, trimmedVal)?.apply()
        Log.d(TAG, "Saved memory fact: $key = $trimmedVal")
    }

    fun deleteFact(key: String) {
        memoryMap.remove(key)
        prefs?.edit()?.remove(key)?.apply()
        Log.d(TAG, "Deleted memory fact: $key")
    }

    fun clearAll() {
        val pin = getPinHash()
        val pinEnabled = isPinLockEnabled()
        memoryMap.clear()
        prefs?.edit()?.clear()?.apply()
        if (pinEnabled && pin != null) {
            prefs?.edit()?.putBoolean(PIN_ENABLED_KEY, true)?.putString(PIN_PREF_KEY, pin)?.commit()
            _isPinProtected.value = true
        } else {
            _isPinProtected.value = false
        }
        Log.d(TAG, "Cleared all memories")
    }

    fun getFact(key: String): String? = memoryMap[key]

    fun updateActiveEntity(entity: String) {
        if (entity.isNotBlank()) {
            activeTopicOrEntity = entity.trim()
        }
    }

    // --- PIN LOCK & SECURITY ---

    fun isPinLockEnabled(): Boolean {
        val enabled = prefs?.getBoolean(PIN_ENABLED_KEY, false) == true && !getPinHash().isNullOrBlank()
        if (_isPinProtected.value != enabled) {
            _isPinProtected.value = enabled
        }
        return enabled
    }

    private fun getPinHash(): String? {
        return prefs?.getString(PIN_PREF_KEY, null)
    }

    fun setPinLock(pin: String, context: Context? = null): Boolean {
        if (prefs == null && context != null) {
            init(context)
        }
        val clean = pin.trim()
        if (clean.length < 4) return false
        val hash = hashString(clean)
        val editor = prefs?.edit() ?: return false
        val ok = editor
            .putBoolean(PIN_ENABLED_KEY, true)
            .putString(PIN_PREF_KEY, hash)
            .commit()
        if (ok) {
            _isPinProtected.value = true
            Log.d(TAG, "PIN lock successfully set and verified in preferences.")
        }
        return ok
    }

    fun disablePinLock(context: Context? = null): Boolean {
        if (prefs == null && context != null) {
            init(context)
        }
        val editor = prefs?.edit() ?: return false
        val ok = editor
            .putBoolean(PIN_ENABLED_KEY, false)
            .remove(PIN_PREF_KEY)
            .commit()
        if (ok) {
            _isPinProtected.value = false
            Log.d(TAG, "PIN lock successfully disabled.")
        }
        return ok
    }

    fun verifyPin(enteredPin: String, context: Context? = null): Boolean {
        if (prefs == null && context != null) {
            init(context)
        }
        val storedHash = getPinHash() ?: return false
        val enteredHash = hashString(enteredPin.trim())
        return storedHash.equals(enteredHash, ignoreCase = true)
    }

    suspend fun resetPinUsingAccountPassword(accountPassword: String): Boolean {
        val isVerified = AuthManager.verifyAccountPassword(accountPassword.trim())
        if (isVerified) {
            disablePinLock()
            return true
        }
        return false
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- MEMORY ITEMS LIST FOR UI ---

    fun getAllMemoriesList(): List<MemoryFactItem> {
        val list = mutableListOf<MemoryFactItem>()
        for ((key, value) in memoryMap) {
            val (title, category, icon) = when (key) {
                "userName" -> Triple("User Name", "Identity", "person")
                "userDevice" -> Triple("Phone & Device", "Hardware", "phone")
                "userLocation" -> Triple("Living Location", "Personal", "location")
                "favColor" -> Triple("Favorite Color", "Favorites", "palette")
                "favCar" -> Triple("Favorite Car / Vehicle", "Favorites", "directions_car")
                "favGame" -> Triple("Favorite Game", "Favorites", "sports_esports")
                "favFood" -> Triple("Favorite Food", "Favorites", "restaurant")
                "profession" -> Triple("Profession / Work", "Career", "work")
                "birthday" -> Triple("Birthday / Age", "Personal", "cake")
                else -> {
                    val cleanKey = key.replace(Regex("([a-z])([A-Z])"), "$1 $2")
                        .replaceFirstChar { it.uppercase() }
                    Triple(cleanKey, "Custom Memory", "psychology")
                }
            }
            list.add(MemoryFactItem(key, title, value, category, icon))
        }
        return list.sortedBy { it.title }
    }

    // --- AUTOMATIC INTELLIGENT FACT EXTRACTION ---

    fun extractAndSaveFacts(userText: String, aiReply: String = "") {
        val text = userText.trim()
        if (text.isBlank()) return

        // 1. User Name
        val nameRegexes = listOf(
            Regex("""(?:my name is|i am|i'm|call me|mera naam|mujhe)\s+([A-Za-z0-9_]{2,25})""", RegexOption.IGNORE_CASE),
            Regex("""(?:mera naam|naam mera)\s+([A-Za-z0-9_]{2,25})\s*(?:hai)?""", RegexOption.IGNORE_CASE)
        )
        for (r in nameRegexes) {
            val match = r.find(text)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 2..25 && !isCommonFillerWord(candidate)) {
                    saveFact("userName", candidate.replaceFirstChar { it.uppercase() })
                    break
                }
            }
        }

        // 2. Favorite Color
        val colorRegexes = listOf(
            Regex("""(?:my fav(?:ourite)? col(?:or|our) is|fav(?:ourite)? col(?:or|our) is|i love the col(?:or|our))\s+([A-Za-z]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:mera fav(?:ourite)? (?:color|rang)\s+([A-Za-z]+)\s*(?:hai)?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:mujhe|mera)\s+([A-Za-z]+)\s+(?:color|rang)\s+pasand\s+hai""", RegexOption.IGNORE_CASE)
        )
        for (r in colorRegexes) {
            val match = r.find(text)
            if (match != null) {
                val color = match.groupValues[1].trim()
                if (color.length in 3..20 && !isCommonFillerWord(color)) {
                    saveFact("favColor", color.replaceFirstChar { it.uppercase() })
                    break
                }
            }
        }

        // 3. Favorite Car / Vehicle
        val carRegexes = listOf(
            Regex("""(?:my fav(?:ourite)? car is|fav(?:ourite)? car is|i love the car|my dream car is)\s+([A-Za-z0-9\s]{3,28}?)(?:\.|$|,|!|and)""", RegexOption.IGNORE_CASE),
            Regex("""(?:meri fav(?:ourite)? car\s+([A-Za-z0-9\s]{3,28}?)\s*(?:hai)?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:i drive a|mere paas car hai)\s+([A-Za-z0-9\s]{3,28}?)(?:\.|$|,|!)""", RegexOption.IGNORE_CASE)
        )
        for (r in carRegexes) {
            val match = r.find(text)
            if (match != null) {
                val car = match.groupValues[1].trim()
                if (car.length in 3..30 && !isCommonFillerWord(car)) {
                    saveFact("favCar", car)
                    break
                }
            }
        }

        // 4. Favorite Game
        val gameRegexes = listOf(
            Regex("""(?:my fav(?:ourite)? game is|i love playing|fav(?:ourite)? game is|my fav sport is)\s+([A-Za-z0-9\s]{3,30}?)(?:\.|$|,|!|and)""", RegexOption.IGNORE_CASE),
            Regex("""(?:mera fav(?:ourite)? game\s+([A-Za-z0-9\s]{3,30}?)\s*(?:hai)?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:mujhe game khelna pasand hai)\s+([A-Za-z0-9\s]{3,30})""", RegexOption.IGNORE_CASE)
        )
        for (r in gameRegexes) {
            val match = r.find(text)
            if (match != null) {
                val game = match.groupValues[1].trim()
                if (game.length in 2..30 && !isCommonFillerWord(game)) {
                    saveFact("favGame", game)
                    break
                }
            }
        }

        // 5. Favorite Food
        val foodRegexes = listOf(
            Regex("""(?:my fav(?:ourite)? food is|my fav(?:ourite)? dish is|i love eating)\s+([A-Za-z0-9\s]{3,30}?)(?:\.|$|,|!)""", RegexOption.IGNORE_CASE),
            Regex("""(?:mera fav(?:ourite)? khana\s+([A-Za-z0-9\s]{3,30}?)\s*(?:hai)?)""", RegexOption.IGNORE_CASE)
        )
        for (r in foodRegexes) {
            val match = r.find(text)
            if (match != null) {
                val food = match.groupValues[1].trim()
                if (food.length in 3..30 && !isCommonFillerWord(food)) {
                    saveFact("favFood", food)
                    break
                }
            }
        }

        // 6. Phone / Device Extraction
        val phoneRegexes = listOf(
            Regex("""(?:my phone is|i have a|i use a|using|my mobile is|mera phone|mere paas)\s+([A-Za-z0-9\s\+\-]{3,30}?)(?:\s+hai|\s+phone|\.|$|,|!)""", RegexOption.IGNORE_CASE),
            Regex("""(?:device|phone model|mobile):\s*([A-Za-z0-9\s\+\-]{3,30})""", RegexOption.IGNORE_CASE)
        )
        for (r in phoneRegexes) {
            val match = r.find(text)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 3..35 && !isCommonFillerWord(candidate)) {
                    saveFact("userDevice", candidate)
                    break
                }
            }
        }

        // 7. City / Location Extraction
        val cityRegexes = listOf(
            Regex("""(?:i live in|i am from|my city is|main\s+([A-Za-z0-9]+)\s+me\s+rehta\s+hu)""", RegexOption.IGNORE_CASE),
            Regex("""(?:from)\s+([A-Za-z]+)\s*(?:city)?""", RegexOption.IGNORE_CASE)
        )
        for (r in cityRegexes) {
            val match = r.find(text)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length in 3..25 && !isCommonFillerWord(candidate)) {
                    saveFact("userLocation", candidate.replaceFirstChar { it.uppercase() })
                    break
                }
            }
        }

        // 8. General explicit memory note: "remember that ...", "yaad rakhna ki ..."
        val explicitRememberRegex = Regex("""(?:remember that|yaad rakhna ki|note that|keep in mind that)\s+([^.!?\n]{4,80})""", RegexOption.IGNORE_CASE)
        val expMatch = explicitRememberRegex.find(text)
        if (expMatch != null) {
            val statement = expMatch.groupValues[1].trim()
            if (statement.isNotBlank()) {
                val safeKey = "note_" + System.currentTimeMillis() % 100000
                saveFact(safeKey, statement)
            }
        }

        // 9. Track prominent topic entities
        val topicQuestionRegex = Regex("""(?:who is|who was|what is|tell me about|kaun hai|kya hai)\s+(?:the\s+)?([A-Za-z0-9\s]{3,35}?)(?:\?|\s+hai|\.$|$)""", RegexOption.IGNORE_CASE)
        val topicMatch = topicQuestionRegex.find(text)
        if (topicMatch != null) {
            val entity = topicMatch.groupValues[1].trim()
            if (entity.isNotBlank() && !isCommonFillerWord(entity)) {
                updateActiveEntity(entity)
            }
        }
    }

    private fun isCommonFillerWord(word: String): Boolean {
        val lower = word.lowercase()
        return lower in listOf(
            "a", "an", "the", "not", "yes", "no", "what", "how", "why", "who", "kya", "kaun",
            "ek", "yeh", "woh", "hai", "tha", "thi", "hoon", "good", "fine", "ok", "okay",
            "phone", "mobile", "name", "naam", "nothing", "something", "color", "car", "game",
            "this", "that", "which"
        )
    }

    /**
     * Formats memory context block to inject into AI prompt.
     * Teaches LLM to personalize future conversations, compliment user, and formulate answers.
     */
    fun buildMemoryPromptBlock(): String {
        val sb = StringBuilder()
        sb.append("=== SAIF AI LONG-TERM USER MEMORY & PERSONAL PROFILE ===\n")
        
        val name = memoryMap["userName"] ?: AuthManager.currentUser.value?.name ?: "User"
        sb.append("- User Name: $name\n")

        for ((k, v) in memoryMap) {
            if (k != "userName") {
                val readableLabel = when (k) {
                    "favColor" -> "Favorite Color"
                    "favCar" -> "Favorite Car"
                    "favGame" -> "Favorite Game / Sport"
                    "favFood" -> "Favorite Food"
                    "userDevice" -> "User Device / Phone"
                    "userLocation" -> "Location / City"
                    "profession" -> "Profession / Student"
                    else -> k.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
                }
                sb.append("- $readableLabel: $v\n")
            }
        }

        if (activeTopicOrEntity.isNotBlank()) {
            sb.append("- Recent Subject/Entity: $activeTopicOrEntity\n")
        }
        sb.append("========================================================\n")
        sb.append("""
PERSONALIZATION & MEMORY INSTRUCTIONS (LIKE CHATGPT & GEMINI):
1. SEAMLESS MEMORY UTILIZATION: You possess the user's permanent memories and profile facts shown above. Whenever the user asks questions about themselves (e.g. "What is my favorite color?", "What car do I like?", "Do you remember my name?", "Suggest a game for me"), recall and use these facts immediately and accurately.
2. PERSONALIZED EXAMPLES & COMPLIMENTS: Seamlessly weave the user's known favorites into your examples, compliments, and explanations (e.g. referencing their favorite game, favorite car, or favorite color when fitting).
3. NATURAL HUMAN ASSISTANT: Speak with warmth, respect, and natural conversational flow in Hindi, Hinglish, or English.
""".trimIndent())

        return sb.toString()
    }

    // --- DATA PRIVACY: DOWNLOAD / EXPORT DATA ---

    fun exportAllUserData(context: Context): File? {
        return try {
            val user = AuthManager.currentUser.value
            val rootObj = JSONObject()
            rootObj.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            rootObj.put("appName", "SAIF AI")
            rootObj.put("version", "1.0.0")

            // User Info
            val userObj = JSONObject()
            userObj.put("name", user?.name ?: memoryMap["userName"] ?: "User")
            userObj.put("email", user?.email ?: "local_user")
            userObj.put("role", user?.role ?: "user")
            rootObj.put("userProfile", userObj)

            // Saif AI Learned Memories
            val memoriesArray = JSONArray()
            for ((k, v) in memoryMap) {
                val itemObj = JSONObject()
                itemObj.put("key", k)
                itemObj.put("fact", v)
                memoriesArray.put(itemObj)
            }
            rootObj.put("saifAiMemories", memoriesArray)
            rootObj.put("totalMemoriesLearned", memoryMap.size)

            val jsonString = rootObj.toString(4)

            // Save to Downloads or external cache
            val fileName = "SAIF_AI_Personal_Data_${System.currentTimeMillis()}.json"
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val exportFile = File(downloadsDir, fileName)
            FileOutputStream(exportFile).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data: ${e.message}", e)
            null
        }
    }

    fun shareExportedFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SAIF AI Data Export")
                putExtra(Intent.EXTRA_TEXT, "Here is your exported SAIF AI personal data and learned memories.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Download / Share SAIF AI Data"))
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch share chooser: ${e.message}")
        }
    }
}
