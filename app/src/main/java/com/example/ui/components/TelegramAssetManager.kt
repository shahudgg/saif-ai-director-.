package com.example.ui.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Data structure representing an indexed media asset from the Telegram Channel.
 * Parsed strictly from the [ASSET_INDEX] caption specification and Master Catalog.
 */
data class TelegramAssetItem(
    val assetId: String = "",       // e.g. AID_A1B2C3D4 or AID_YT_STUDIO_DARK_01
    val title: String,
    val type: String,               // image | audio | video | font
    val extension: String,          // e.g. mp4, mp3, png, jpg, ttf
    val category: String,           // e.g. background_video, b_roll, sfx, bgm, overlay_graphic, transition, font
    val subject: String,
    val searchKeywords: List<String>,
    val tags: List<String>,
    val userId: String,             // Tenant isolation key
    val fileId: String,             // Telegram file_id
    val messageId: Long,
    val fileSize: Long = 0L,
    val localCachedPath: String? = null
)

/**
 * Master Video EDL (Edit Decision List) Models for Autonomous AI Video Director
 */
data class VideoEdlMeta(
    val generatedTitle: String,
    val totalDurationSeconds: Float,
    val totalAssetsUsed: Int
)

data class VideoEdlEditingParameters(
    val scale: Float = 1.0f,
    val position: String = "center",
    val animation: String = "fade_in",
    val opacity: Float = 1.0f,
    val filters: String = "none",
    val volume: Float = 1.0f,
    val fadeIn: Float = 0.0f,
    val fadeOut: Float = 0.0f,
    val ducking: Boolean = false
)

data class VideoEdlTimelineItem(
    val sceneId: Int,
    val layerType: String, // background_video | b_roll | sfx | bgm | overlay_graphic | transition | font
    val startTime: Float,
    val endTime: Float,
    val assetId: String,
    val telegramFileId: String,
    val validationLogic: String,
    val editingParameters: VideoEdlEditingParameters
)

data class VideoEdlPlan(
    val projectMeta: VideoEdlMeta,
    val timeline: List<VideoEdlTimelineItem>,
    val script: VideoScript? = null
)

/**
 * Telegram Asset Manager:
 * Handles querying the configured Telegram Channel for indexed media assets,
 * loading the Master Telegram Catalog (assets_catalog.json), parsing [ASSET_INDEX] captions,
 * enforcing multi-tenant isolation, running the Phase 3 Self-Validation & Rejection loop,
 * downloading files via Telegram Bot API `getFile` endpoint to an isolated workspace,
 * and caching them locally for real-time video rendering and editing.
 */
object TelegramAssetManager {
    private const val TAG = "TelegramAssetManager"

    // Bot token and Channel ID are loaded securely from BuildConfig / Settings in Level 6
    // Injected at build time from CI secrets (TG_BOT_TOKEN / TG_CHAT_ID). Never stored in source.
    val DEFAULT_BOT_TOKEN: String get() = try { com.example.BuildConfig.TG_BOT_TOKEN } catch (e: Throwable) { "" }
    val DEFAULT_CHANNEL_ID: String get() = try { com.example.BuildConfig.TG_CHAT_ID } catch (e: Throwable) { "" }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // In-memory catalog cache to avoid duplicate network hammering
    private val memoryAssetCatalog = mutableListOf<TelegramAssetItem>()
    private var lastSyncTimestamp = 0L

    /** Result of the most recent channel sync and the most recent download problem, for the diagnostics panel. */
    @Volatile var lastReport: TelegramSyncReport? = null
    @Volatile var lastDownloadError: String = ""

    /** Bot API limit: getFile cannot serve files above 20 MB. */
    const val TELEGRAM_MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024

    /**
     * Parse raw caption text following the spec:
     * [ASSET_INDEX]
     * Title: <Text>
     * Type: <image|audio|video|font>
     * Extension: <Extension>
     * Category: <Text>
     * Subject: <Text>
     * Search Keywords: <Keywords>
     * Tags: <Tags>
     * User_ID: <User_ID>
     * [/ASSET_INDEX]
     */
    fun parseAssetCaption(caption: String, fileId: String, messageId: Long, fileSize: Long = 0L): TelegramAssetItem? {
        if (!caption.contains("[ASSET_INDEX]", ignoreCase = true) || !caption.contains("[/ASSET_INDEX]", ignoreCase = true)) {
            return null
        }

        try {
            val startIndex = caption.indexOf("[ASSET_INDEX]", ignoreCase = true) + "[ASSET_INDEX]".length
            val endIndex = caption.indexOf("[/ASSET_INDEX]", ignoreCase = true)
            if (endIndex <= startIndex) return null

            val block = caption.substring(startIndex, endIndex).trim()
            val lines = block.lines()

            var assetId = "AID_${messageId.toString().takeLast(6)}"
            var title = "Asset_$messageId"
            var type = "video"
            var extension = "mp4"
            var category = "General"
            var subject = ""
            var keywords = mutableListOf<String>()
            var tags = mutableListOf<String>()
            var userId = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val colonIndex = trimmed.indexOf(':')
                if (colonIndex == -1) continue

                val key = trimmed.substring(0, colonIndex).trim().lowercase()
                val value = trimmed.substring(colonIndex + 1).trim()

                when (key) {
                    "asset_id", "assetid", "id", "aid" -> assetId = value
                    "title" -> title = value
                    "type" -> type = value.lowercase()
                    "extension", "ext" -> extension = value.lowercase().removePrefix(".")
                    "category" -> category = value
                    "subject" -> subject = value
                    "search keywords", "keywords" -> {
                        keywords.addAll(value.split(",", ";", " ").map { it.trim().lowercase() }.filter { it.isNotEmpty() })
                    }
                    "tags", "tag" -> {
                        tags.addAll(value.split(",", ";", "#", " ").map { it.trim().lowercase() }.filter { it.isNotEmpty() })
                    }
                    "user_id", "userid", "user" -> userId = value
                }
            }

            return TelegramAssetItem(
                assetId = assetId,
                title = title,
                type = type,
                extension = extension,
                category = category,
                subject = subject,
                searchKeywords = keywords.distinct(),
                tags = tags.distinct(),
                userId = userId,
                fileId = fileId,
                messageId = messageId,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse caption: $caption", e)
            return null
        }
    }

    /**
     * Loads the Master Telegram Catalog from assets_catalog.json (Android assets or workspace).
     */
    fun loadMasterCatalog(context: Context? = null): List<TelegramAssetItem> {
        val list = mutableListOf<TelegramAssetItem>()
        var jsonText = ""

        // 1. Try reading from Android AssetManager
        if (context != null) {
            try {
                context.assets.open("assets_catalog.json").bufferedReader().use {
                    jsonText = it.readText()
                }
            } catch (e: Exception) {
                Log.w(TAG, "assets_catalog.json not in android assets: ${e.message}")
            }
        }

        // 2. Try filesystem paths
        if (jsonText.isBlank()) {
            val possiblePaths = listOf(
                "app/src/main/assets/assets_catalog.json",
                "assets_catalog.json",
                "/app/assets_catalog.json"
            )
            for (p in possiblePaths) {
                val f = File(p)
                if (f.exists() && f.length() > 0) {
                    try {
                        jsonText = f.readText()
                        break
                    } catch (ignored: Exception) {}
                }
            }
        }

        if (jsonText.isNotBlank()) {
            try {
                val arr = JSONArray(jsonText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val kList = mutableListOf<String>()
                    val kwArr = obj.optJSONArray("keywords")
                    if (kwArr != null) {
                        for (k in 0 until kwArr.length()) kList.add(kwArr.getString(k).lowercase())
                    }
                    val tList = mutableListOf<String>()
                    val tagArr = obj.optJSONArray("tags")
                    if (tagArr != null) {
                        for (t in 0 until tagArr.length()) tList.add(tagArr.getString(t).lowercase())
                    }

                    list.add(
                        TelegramAssetItem(
                            assetId = obj.optString("asset_id", "AID_${i + 1}"),
                            title = obj.optString("title", "Asset ${i + 1}"),
                            type = obj.optString("type", "video").lowercase(),
                            extension = obj.optString("extension", "mp4").lowercase(),
                            category = obj.optString("category", "b_roll"),
                            subject = obj.optString("subject", ""),
                            searchKeywords = kList,
                            tags = tList,
                            userId = obj.optString("user_id", "all"),
                            fileId = obj.optString("telegram_file_id", "FILE_ID_$i"),
                            messageId = obj.optLong("message_id", 1000L + i),
                            fileSize = obj.optLong("file_size", 0L)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse assets_catalog.json", e)
            }
        }

        // Persisted channel catalog (survives restarts). Built-in seed items with invented file ids were removed on purpose:
        // they could never be downloaded and made the director believe assets existed.
        if (context != null) runCatching { TelegramCatalogStore(context).items() }.getOrNull()?.forEach { item ->
            if (list.none { it.fileId == item.fileId && item.fileId.isNotBlank() }) list.add(item)
        }
        for (item in memoryAssetCatalog) {
            if (list.none { it.assetId == item.assetId || it.title == item.title }) {
                list.add(item)
            }
        }

        return list
    }

    /**
     * Returns serialized JSON catalog text for prompt injection to LLMs.
     */
    fun getMasterCatalogSummary(context: Context? = null): String {
        val catalog = loadMasterCatalog(context)
        return buildString {
            appendLine("=== MASTER TELEGRAM CATALOG (assets_catalog.json) ===")
            catalog.forEach { a ->
                appendLine("- Asset_ID: ${a.assetId} | Title: ${a.title} | Type: ${a.type} | Cat: ${a.category} | File_ID: ${a.fileId}")
                appendLine("  Subject: ${a.subject}")
                appendLine("  Keywords: ${a.searchKeywords.joinToString(", ")} | Tags: ${a.tags.joinToString(" ")}")
            }
            appendLine("=====================================================")
        }
    }

    /**
     * Syncs asset catalog from Telegram channel updates and posts.
     */
    suspend fun syncChannelAssets(
        botToken: String = DEFAULT_BOT_TOKEN,
        channelId: String = DEFAULT_CHANNEL_ID,
        context: Context? = null
    ): List<TelegramAssetItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastSyncTimestamp < 30_000 && memoryAssetCatalog.isNotEmpty()) {
            return@withContext memoryAssetCatalog
        }

        // 1. First ensure master catalog is loaded into memory
        val master = loadMasterCatalog(context)
        for (item in master) {
            if (memoryAssetCatalog.none { it.assetId == item.assetId && it.assetId.isNotEmpty() }) {
                memoryAssetCatalog.add(item)
            }
        }

        // 2. Pull new channel posts into the persistent catalog (offset-based, so nothing is lost or re-read)
        if (context != null) {
            val rep = TelegramCatalogStore(context).sync(botToken, channelId)
            lastReport = rep
            for (item in TelegramCatalogStore(context).items()) {
                val idx = memoryAssetCatalog.indexOfFirst { it.messageId == item.messageId && it.fileId == item.fileId }
                if (idx >= 0) memoryAssetCatalog[idx] = item else if (memoryAssetCatalog.none { it.fileId == item.fileId }) memoryAssetCatalog.add(item)
            }
            lastSyncTimestamp = now
        }

        memoryAssetCatalog
    }

    /**
     * Seeds initial verified items matching the channel schema to ensure zero-blank state.
     */
    private fun seedBuiltInCatalog() {
        val seedItems = listOf(
            TelegramAssetItem(
                assetId = "AID_OVERLAY_GLITCH_STROBE_02",
                title = "Cyberpunk Neon Glitch Overlay",
                type = "video",
                extension = "mp4",
                category = "overlay_graphic",
                subject = "Cyberpunk HUD & Neon Glitch",
                searchKeywords = listOf("cyberpunk", "glitch", "neon", "hud", "sci-fi", "overlay"),
                tags = listOf("#neon", "#glitch", "#overlay", "#trending"),
                userId = "all",
                fileId = "BAACAgIAAxkBAAE123cyberpunk_neon_overlay",
                messageId = 101L
            ),
            TelegramAssetItem(
                assetId = "AID_BGM_SYNTHWAVE_PULSE_02",
                title = "Synthwave Midnight Pulse BGM",
                type = "audio",
                extension = "mp3",
                category = "bgm",
                subject = "Electronic Retro Synth BGM 128BPM",
                searchKeywords = listOf("synthwave", "bgm", "electronic", "retro", "music", "beat"),
                tags = listOf("#synth", "#fast", "#hype", "#lofi"),
                userId = "all",
                fileId = "CQACAgIAAxkBAAE124synthwave_pulse_bgm",
                messageId = 102L
            ),
            TelegramAssetItem(
                assetId = "AID_SFX_SWOOSH_TRANS_01",
                title = "Cinematic Whoosh Transition SFX",
                type = "audio",
                extension = "mp3",
                category = "sfx",
                subject = "Fast deep whoosh dynamic cut sound",
                searchKeywords = listOf("whoosh", "sfx", "transition", "fast", "cut", "riser"),
                tags = listOf("#whoosh", "#impact", "#sfx"),
                userId = "all",
                fileId = "CQACAgIAAxkBAAE125whoosh_sfx",
                messageId = 103L
            ),
            TelegramAssetItem(
                assetId = "AID_GRAPHIC_CHECK_3D_01",
                title = "3D Glossy Green Check Mark",
                type = "image",
                extension = "png",
                category = "overlay_graphic",
                subject = "High quality 3D green tick badge with subtle reflection",
                searchKeywords = listOf("check", "tick", "verified", "correct", "success", "approved", "green"),
                tags = listOf("#check", "#success", "#verified", "#green", "#3d"),
                userId = "all",
                fileId = "AgACAgIAAxkBAAE_GRAPHIC_CHECK_3D_01_IMG",
                messageId = 104L
            ),
            TelegramAssetItem(
                assetId = "AID_FONT_BEBAS_NEUE_01",
                title = "Bebas Neue Headline Font",
                type = "font",
                extension = "ttf",
                category = "font",
                subject = "Clean sci-fi title typeface",
                searchKeywords = listOf("bebas", "font", "typeface", "bold", "clean"),
                tags = listOf("#font", "#title", "#sans"),
                userId = "all",
                fileId = "BQACAgIAAxkBAAE127orbitron_font",
                messageId = 105L
            )
        )

        for (item in seedItems) {
            if (memoryAssetCatalog.none { it.assetId == item.assetId || it.title == item.title }) {
                memoryAssetCatalog.add(item)
            }
        }
    }

    /**
     * PHASE 3: SELF-VALIDATION & REJECTION LOOP
     * Performs a rigorous logical check before committing an asset to the timeline:
     * 1. Type Check: Ensures requested layerType matches media type.
     * 2. Context Check: Ensures emotional/thematic congruency.
     */
    fun validateAssetForScene(
        asset: TelegramAssetItem,
        layerType: String,
        sceneContext: String
    ): Pair<Boolean, String> {
        val normLayer = layerType.lowercase().trim()
        val normType = asset.type.lowercase().trim()

        // 1. Type Check
        when (normLayer) {
            "background_video", "b_roll", "video" -> {
                if (normType != "video" && normType != "image") {
                    return Pair(false, "Type Mismatch: Layer '$layerType' requires video/image, but asset '${asset.title}' is '$normType'.")
                }
            }
            "sfx", "bgm", "audio" -> {
                if (normType != "audio") {
                    return Pair(false, "Type Mismatch: Layer '$layerType' requires audio, but asset '${asset.title}' is '$normType'.")
                }
            }
            "overlay_graphic", "sticker", "graphic" -> {
                if (normType != "image" && normType != "video") {
                    return Pair(false, "Type Mismatch: Layer '$layerType' requires graphic/video overlay, but asset '${asset.title}' is '$normType'.")
                }
            }
            "font" -> {
                if (normType != "font") {
                    return Pair(false, "Type Mismatch: Layer '$layerType' requires font, but asset '${asset.title}' is '$normType'.")
                }
            }
        }

        // 2. Emotional / Context Check
        val contextLower = sceneContext.lowercase()
        if (contextLower.contains("success") || contextLower.contains("growth") || contextLower.contains("viral") || contextLower.contains("win")) {
            if (asset.title.contains("sad", ignoreCase = true) || asset.subject.contains("melancholy", ignoreCase = true)) {
                return Pair(false, "Context Failure: Sad/melancholy asset rejected for high-energy growth/success scene.")
            }
        }

        return Pair(true, "Passed: Asset '${asset.title}' matches layer '$layerType' and context '$sceneContext'.")
    }

    /**
     * Tool specification method:
     * search_and_download_asset(user_id, asset_type, keywords, save_dir)
     * Filters strictly by requesting user_id (or public/default assets), asset_type, and matching keywords.
     */
    suspend fun searchAndDownloadAsset(
        context: Context,
        userId: String,
        assetType: String,
        keywords: List<String>,
        botToken: String = DEFAULT_BOT_TOKEN
    ): TelegramAssetItem? = withContext(Dispatchers.IO) {
        val allAssets = syncChannelAssets(botToken, context = context)

        val typeNormalized = assetType.trim().lowercase()
        val queryKeywords = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val candidates = allAssets.filter { asset ->
            val userMatch = asset.userId.isBlank() || asset.userId.equals("all", ignoreCase = true) || asset.userId.equals("default", ignoreCase = true) || asset.userId.equals(userId, ignoreCase = true)
            val typeMatch = typeNormalized == "all" || asset.type.equals(typeNormalized, ignoreCase = true) || asset.category.equals(typeNormalized, ignoreCase = true)
            userMatch && typeMatch
        }

        if (candidates.isEmpty()) return@withContext null

        val scored = candidates.map { asset ->
            var score = 0
            for (qk in queryKeywords) {
                if (asset.assetId.contains(qk, ignoreCase = true)) score += 6
                if (asset.title.contains(qk, ignoreCase = true)) score += 5
                if (asset.subject.contains(qk, ignoreCase = true)) score += 4
                if (asset.category.contains(qk, ignoreCase = true)) score += 3
                if (asset.searchKeywords.any { it.contains(qk, ignoreCase = true) }) score += 4
                if (asset.tags.any { it.contains(qk, ignoreCase = true) }) score += 2
            }
            Pair(asset, score)
        }.sortedByDescending { it.second }

        val bestMatch = scored.firstOrNull()?.first ?: candidates.first()

        // Multi-Tenant Isolation Directory: context.filesDir/telegram_workspace/<user_id>/
        val userDir = File(context.filesDir, "telegram_workspace/${sanitizeUserId(userId)}")
        if (!userDir.exists()) userDir.mkdirs()

        val targetFile = File(userDir, "${bestMatch.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.${bestMatch.extension}")

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext bestMatch.copy(localCachedPath = targetFile.absolutePath)
        }

        // Fetch via Telegram getFile endpoint
        try {
            val fileUrl = getTelegramFileDownloadUrl(botToken, bestMatch.fileId)
            if (fileUrl != null) {
                val dlRequest = Request.Builder().url(fileUrl).get().build()
                val dlResponse = httpClient.newCall(dlRequest).execute()
                if (dlResponse.isSuccessful) {
                    val stream = dlResponse.body?.byteStream()
                    if (stream != null) {
                        FileOutputStream(targetFile).use { out ->
                            stream.copyTo(out)
                        }
                        return@withContext bestMatch.copy(localCachedPath = targetFile.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download Telegram asset ${bestMatch.title}: ${redact(e.message, botToken)}")
        }

        // No fake placeholder: a failed download is a failure, reported honestly to the caller.
        if (targetFile.exists() && targetFile.length() == 0L) targetFile.delete()
        return@withContext null
    }

    /**
     * Download or retrieve an asset by exact asset_id or telegram_file_id.
     */
    suspend fun downloadAssetById(
        context: Context,
        userId: String,
        assetId: String,
        telegramFileId: String,
        botToken: String = DEFAULT_BOT_TOKEN
    ): TelegramAssetItem? = withContext(Dispatchers.IO) {
        val allAssets = syncChannelAssets(botToken, context = context)
        val matched = allAssets.firstOrNull { it.assetId.equals(assetId, ignoreCase = true) || it.fileId == telegramFileId }
            ?: allAssets.firstOrNull { it.assetId.contains(assetId, ignoreCase = true) }
            ?: return@withContext null

        val userDir = File(context.filesDir, "telegram_workspace/${sanitizeUserId(userId)}")
        if (!userDir.exists()) userDir.mkdirs()

        val targetFile = File(userDir, "${matched.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.${matched.extension}")
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext matched.copy(localCachedPath = targetFile.absolutePath)
        }

        try {
            val fileUrl = getTelegramFileDownloadUrl(botToken, matched.fileId)
            if (fileUrl != null) {
                val dlRequest = Request.Builder().url(fileUrl).get().build()
                val dlResponse = httpClient.newCall(dlRequest).execute()
                if (dlResponse.isSuccessful) {
                    val stream = dlResponse.body?.byteStream()
                    if (stream != null) {
                        FileOutputStream(targetFile).use { out ->
                            stream.copyTo(out)
                        }
                        return@withContext matched.copy(localCachedPath = targetFile.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error for ${matched.title}: ${redact(e.message, botToken)}")
        }

        if (targetFile.exists() && targetFile.length() == 0L) targetFile.delete()
        return@withContext null
    }

    /** Removes bot tokens (and any api.telegram.org/bot<token> URL fragment) from text before logging. */
    fun redact(message: String?, botToken: String): String {
        var out = message ?: ""
        if (botToken.isNotBlank()) out = out.replace(botToken, "***")
        return out.replace(Regex("bot\\d{5,}:[A-Za-z0-9_-]{20,}"), "bot***")
    }

    /** Ranked candidates for a scene request (no download). Empty when the catalog has nothing relevant. */
    suspend fun rankCandidates(
        context: Context, userId: String, assetType: String, keywords: List<String>,
        limit: Int = 4, botToken: String = DEFAULT_BOT_TOKEN
    ): List<TelegramAssetItem> = withContext(Dispatchers.IO) {
        val all = syncChannelAssets(botToken, context = context)
        val type = assetType.trim().lowercase()
        val q = keywords.map { it.trim().lowercase() }.filter { it.length > 1 }
        all.asSequence().filter { a ->
            val own = a.userId.isBlank() || a.userId.equals("all", true) || a.userId.equals("default", true) || a.userId.equals(userId, true)
            own && (type == "all" || a.type.equals(type, true) || a.category.equals(type, true))
        }.map { a ->
            var sc = 0
            for (k in q) {
                if (a.title.contains(k, true)) sc += 5
                if (a.subject.contains(k, true)) sc += 4
                if (a.searchKeywords.any { it.contains(k, true) }) sc += 4
                if (a.tags.any { it.contains(k, true) }) sc += 2
                if (a.category.contains(k, true)) sc += 3
            }
            a to sc
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }.toList()
    }

    /**
     * Downloads one candidate into the per-user workspace. Returns null on any failure (never a fake file).
     * The result is only a *downloaded* file: callers must still verify its content before using it.
     */
    suspend fun downloadCandidate(
        context: Context, userId: String, asset: TelegramAssetItem,
        botToken: String = DEFAULT_BOT_TOKEN, maxBytes: Long = 80L * 1024 * 1024
    ): TelegramAssetItem? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "telegram_workspace/${sanitizeUserId(userId)}").apply { mkdirs() }
        val safe = asset.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val target = File(dir, "${asset.assetId.ifBlank { safe }}_$safe.${asset.extension}")
        if (target.exists() && target.length() > 0) return@withContext asset.copy(localCachedPath = target.absolutePath)
        if (botToken.isBlank()) { lastDownloadError = "No bot token configured"; return@withContext null }
        if (asset.fileSize > TELEGRAM_MAX_DOWNLOAD_BYTES) { lastDownloadError = "'${asset.title}' is ${asset.fileSize / (1024 * 1024)} MB; Telegram bots can only download files up to 20 MB"; return@withContext null }
        val tmp = File(dir, target.name + ".part")
        try {
            val url = getTelegramFileDownloadUrl(botToken, asset.fileId) ?: return@withContext null
            httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                if (body.contentLength() > maxBytes) return@withContext null
                var total = 0L
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(16 * 1024)
                    body.byteStream().use { inp ->
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            total += n
                            if (total > maxBytes) { tmp.delete(); return@withContext null }
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            if (tmp.length() == 0L || !tmp.renameTo(target)) { tmp.delete(); return@withContext null }
            asset.copy(localCachedPath = target.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Candidate download failed: ${redact(e.message, botToken)}")
            tmp.delete()
            null
        }
    }

    /**
     * Resolves file_path via Telegram getFile API
     */
    private fun getTelegramFileDownloadUrl(botToken: String, fileId: String): String? {
        try {
            val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
            val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
            val body = response.body?.string() ?: ""
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (response.isSuccessful && json?.optBoolean("ok", false) == true) {
                val filePath = json.optJSONObject("result")?.optString("file_path", "")
                if (!filePath.isNullOrBlank()) return "https://api.telegram.org/file/bot$botToken/$filePath"
                lastDownloadError = "Telegram returned no file path"
            } else {
                lastDownloadError = json?.optString("description", "")?.takeIf { it.isNotBlank() } ?: "getFile failed (HTTP ${response.code})"
            }
        } catch (e: Exception) {
            lastDownloadError = "Network error: ${redact(e.message, botToken)}"
            Log.e(TAG, "getFile error: ${redact(e.message, botToken)}")
        }
        return null
    }

    /**
     * Parses the strict JSON EDL produced by the Elite AI Video Director.
     */
    fun parseEdlJson(rawJson: String): VideoEdlPlan? {
        return try {
            val firstBrace = rawJson.indexOf('{')
            val lastBrace = rawJson.lastIndexOf('}')
            if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) return null
            val clean = rawJson.substring(firstBrace, lastBrace + 1)
            val obj = JSONObject(clean)

            val metaObj = obj.optJSONObject("project_meta") ?: JSONObject()
            val meta = VideoEdlMeta(
                generatedTitle = metaObj.optString("generated_title", "Viral Video Master"),
                totalDurationSeconds = metaObj.optDouble("total_duration_seconds", 15.0).toFloat(),
                totalAssetsUsed = metaObj.optInt("total_assets_used", 0)
            )

            val timelineArray = obj.optJSONArray("timeline") ?: JSONArray()
            val items = mutableListOf<VideoEdlTimelineItem>()

            for (i in 0 until timelineArray.length()) {
                val t = timelineArray.getJSONObject(i)
                val editObj = t.optJSONObject("editing_parameters") ?: JSONObject()

                val editParams = VideoEdlEditingParameters(
                    scale = editObj.optDouble("scale", 1.0).toFloat(),
                    position = editObj.optString("position", "center"),
                    animation = editObj.optString("animation", "fade_in"),
                    opacity = editObj.optDouble("opacity", 1.0).toFloat(),
                    filters = editObj.optString("filters", "none"),
                    volume = editObj.optDouble("volume", 1.0).toFloat(),
                    fadeIn = editObj.optDouble("fade_in", 0.0).toFloat(),
                    fadeOut = editObj.optDouble("fade_out", 0.0).toFloat(),
                    ducking = editObj.optBoolean("ducking", false)
                )

                items.add(
                    VideoEdlTimelineItem(
                        sceneId = t.optInt("scene_id", 1),
                        layerType = t.optString("layer_type", "background_video"),
                        startTime = t.optDouble("start_time", 0.0).toFloat(),
                        endTime = t.optDouble("end_time", 3.0).toFloat(),
                        assetId = t.optString("asset_id", ""),
                        telegramFileId = t.optString("telegram_file_id", ""),
                        validationLogic = t.optString("validation_logic", "Passed: Context check verified"),
                        editingParameters = editParams
                    )
                )
            }

            val scriptObj = obj.optJSONObject("script")
            val script = if (scriptObj != null) {
                val sectionsArray = scriptObj.optJSONArray("sections") ?: JSONArray()
                val secList = mutableListOf<VideoScriptSection>()
                for (s in 0 until sectionsArray.length()) {
                    val so = sectionsArray.getJSONObject(s)
                    secList.add(
                        VideoScriptSection(
                            kind = so.optString("kind", "point"),
                            narration = so.optString("narration", ""),
                            emotion = so.optString("emotion", "excited"),
                            estSeconds = so.optDouble("estSeconds", 3.5).toFloat(),
                            visualHint = so.optString("visualHint", "")
                        )
                    )
                }
                if (secList.isNotEmpty()) {
                    VideoScript(
                        title = scriptObj.optString("title", meta.generatedTitle),
                        targetDurationSeconds = meta.totalDurationSeconds,
                        tone = scriptObj.optString("tone", "High Retention"),
                        language = scriptObj.optString("language", "Hindi & English"),
                        sections = secList
                    )
                } else null
            } else null

            VideoEdlPlan(projectMeta = meta, timeline = items, script = script)
        } catch (e: Exception) {
            Log.e(TAG, "parseEdlJson error", e)
            null
        }
    }

    private fun sanitizeUserId(userId: String): String {
        val clean = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return clean.ifBlank { "anonymous_user" }
    }
}
