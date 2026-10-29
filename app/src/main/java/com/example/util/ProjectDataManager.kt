package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.components.ProjectData
import org.json.JSONObject
import java.io.File

/**
 * Manages persistence and retrieval of ProjectData for SAIF AI Studio projects.
 */
object ProjectDataManager {

    private const val PREFS_NAME = "saif_ai_projects"
    private const val KEY_PREFIX_PROJECT = "project_data_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves ProjectData to persistent storage (both SharedPreferences and project app_config.json).
     */
    fun saveProject(context: Context, project: ProjectData) {
        val sanitized = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val json = JSONObject().apply {
            put("appName", project.appName)
            put("packageName", project.packageName)
            put("minSdk", project.minSdk)
            put("targetSdk", project.targetSdk)
            put("buildStudio", project.buildStudio)
            put("language", project.language)
        }

        // Save in SharedPreferences
        getPrefs(context).edit().putString(KEY_PREFIX_PROJECT + sanitized, json.toString()).apply()
        getPrefs(context).edit().putString(KEY_PREFIX_PROJECT + project.appName, json.toString()).apply()

        // Also save in project physical directory
        try {
            val projectDir = File(context.filesDir, "projects/$sanitized").apply { mkdirs() }
            val configFile = File(projectDir, "app_config.json")
            configFile.writeText(json.toString(2))

            // Save app icon bitmap if available
            if (project.iconBitmap != null) {
                val iconFile = File(projectDir, "app_icon.png")
                java.io.FileOutputStream(iconFile).use { out ->
                    project.iconBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            // Ensure physical starter layout, activity, and manifest exist immediately
            ensureProjectScaffolded(context, project)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Ensures that essential Android project files (main.xml layout, MainActivity, AndroidManifest)
     * exist on disk so that live layout previews and code compilers can function immediately.
     */
    fun ensureProjectScaffolded(context: Context, project: ProjectData) {
        try {
            val sanitized = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
            val projectDir = File(context.filesDir, "projects/$sanitized").apply { mkdirs() }
            val layoutFile = File(projectDir, "app/src/main/res/layout/main.xml")
            if (!layoutFile.exists()) {
                layoutFile.parentFile?.mkdirs()
                layoutFile.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="24dp"
    android:background="#0F172A">

    <TextView
        android:id="@+id/title_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="${project.appName}"
        android:textColor="#38BDF8"
        android:textSize="26sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tv_counter"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="Ready to build. Tell SAIF AI your prompt!"
        android:textColor="#94A3B8"
        android:textSize="15sp"
        android:gravity="center" />

    <Button
        android:id="@+id/action_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="Tap to Test"
        android:background="#6D28D9"
        android:textColor="#FFFFFF" />

</LinearLayout>""".trimIndent()
                )
            }

            val pkgPath = project.packageName.replace('.', '/')
            val javaFile = File(projectDir, "app/src/main/java/$pkgPath/MainActivity.java")
            if (!javaFile.exists()) {
                javaFile.parentFile?.mkdirs()
                javaFile.writeText(
                    """package ${project.packageName};

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private int clickCount = 0;
    private TextView tvCounter;
    private Button actionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        tvCounter = findViewById(R.id.tv_counter);
        actionButton = findViewById(R.id.action_button);

        if (actionButton != null) {
            actionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickCount++;
                    if (tvCounter != null) {
                        tvCounter.setText("Interactions: " + clickCount);
                    }
                    Toast.makeText(MainActivity.this, "Welcome to ${project.appName}!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}""".trimIndent()
                )
            }

            val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
            if (!manifestFile.exists()) {
                manifestFile.parentFile?.mkdirs()
                manifestFile.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${project.packageName}">

    <application
        android:allowBackup="true"
        android:icon="@drawable/app_icon"
        android:label="${project.appName}"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>""".trimIndent()
                )
            }

            val stringsFile = File(projectDir, "app/src/main/res/values/strings.xml")
            if (!stringsFile.exists()) {
                stringsFile.parentFile?.mkdirs()
                stringsFile.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.appName}</string>
    <string name="welcome_message">Welcome to your new Android application built with SAIF AI Studio.</string>
</resources>""".trimIndent()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads custom app icon bitmap for project if available.
     */
    fun loadProjectIcon(context: Context, appName: String): android.graphics.Bitmap? {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val file = File(context.filesDir, "projects/$sanitized/app_icon.png")
        return if (file.exists()) {
            try {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * Retrieves ProjectData by app name.
     */
    fun getProject(context: Context, appName: String): ProjectData? {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val icon = loadProjectIcon(context, appName)
        val prefs = getPrefs(context)
        val rawJson = prefs.getString(KEY_PREFIX_PROJECT + sanitized, null)
            ?: prefs.getString(KEY_PREFIX_PROJECT + appName, null)

        if (!rawJson.isNullOrBlank()) {
            val parsed = parseProjectJson(rawJson)
            if (parsed != null) return parsed.copy(iconBitmap = icon ?: parsed.iconBitmap)
        }

        // Try reading from disk app_config.json
        try {
            val configFile = File(context.filesDir, "projects/$sanitized/app_config.json")
            if (configFile.exists()) {
                val content = configFile.readText()
                val parsed = parseProjectJson(content)
                if (parsed != null) return parsed.copy(iconBitmap = icon ?: parsed.iconBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback default ProjectData if project directory or pref exists
        if (hasProject(context, appName)) {
            return ProjectData(
                appName = appName,
                packageName = "com.example.${sanitized.lowercase()}",
                minSdk = 21,
                targetSdk = 34,
                buildStudio = "SAIF AI Studio",
                language = "Java",
                iconBitmap = icon
            )
        }
        return null
    }

    /**
     * Renames an existing project across prefs and directory storage.
     */
    fun renameProject(context: Context, oldName: String, newName: String, updatedProject: ProjectData) {
        if (oldName == newName) {
            saveProject(context, updatedProject)
            return
        }
        val oldSanitized = oldName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val newSanitized = newName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }

        getPrefs(context).edit()
            .remove(KEY_PREFIX_PROJECT + oldSanitized)
            .remove(KEY_PREFIX_PROJECT + oldName)
            .apply()

        try {
            val oldDir = File(context.filesDir, "projects/$oldSanitized")
            val newDir = File(context.filesDir, "projects/$newSanitized")
            if (oldDir.exists() && oldDir.absolutePath != newDir.absolutePath) {
                oldDir.renameTo(newDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        saveProject(context, updatedProject)
    }

    /**
     * Deletes a project from preferences and disk.
     */
    fun deleteProject(context: Context, appName: String) {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        getPrefs(context).edit()
            .remove(KEY_PREFIX_PROJECT + sanitized)
            .remove(KEY_PREFIX_PROJECT + appName)
            .apply()
        try {
            val projectDir = File(context.filesDir, "projects/$sanitized")
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Checks if a project exists by app name in storage or preferences.
     */
    fun hasProject(context: Context, appName: String): Boolean {
        if (appName.isBlank()) return false
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_PREFIX_PROJECT + sanitized) || prefs.contains(KEY_PREFIX_PROJECT + appName)) {
            return true
        }
        val projectDir = File(context.filesDir, "projects/$sanitized")
        return projectDir.exists() && projectDir.isDirectory
    }

    private fun parseProjectJson(rawJson: String): ProjectData? {
        return try {
            val obj = JSONObject(rawJson)
            ProjectData(
                appName = obj.optString("appName", "App"),
                packageName = obj.optString("packageName", "com.example.app"),
                minSdk = obj.optInt("minSdk", 21),
                targetSdk = obj.optInt("targetSdk", 34),
                buildStudio = obj.optString("buildStudio", "Android Studio"),
                language = obj.optString("language", "Java"),
                iconBitmap = null
            )
        } catch (e: Exception) {
            null
        }
    }

    private const val KEY_PREFIX_VIDEO = "video_proj_"
    private const val KEY_ALL_VIDEO_NAMES = "all_video_project_names"

    fun saveVideoProject(context: Context, videoProject: com.example.ui.components.VideoProjectData) {
        val prefs = getPrefs(context)
        val name = videoProject.projectName.trim()
        val json = JSONObject().apply {
            put("projectId", videoProject.projectId)
            put("projectName", videoProject.projectName)
            put("resolution", videoProject.resolution)
            put("fps", videoProject.fps)
            put("totalDurationSeconds", videoProject.totalDurationSeconds.toDouble())
            put("currentPositionSeconds", videoProject.currentPositionSeconds.toDouble())
            put("audioTrackName", videoProject.audioTrackName)
            put("textOverlay", videoProject.textOverlay)
            put("effectOverlay", videoProject.effectOverlay)
            put("selectedClipIndex", videoProject.selectedClipIndex)
            put("aspectRatio", videoProject.aspectRatio)
            put("activeFilter", videoProject.activeFilter)
            put("filterIntensity", videoProject.filterIntensity.toDouble())
            put("brightness", videoProject.brightness.toDouble())
            put("contrast", videoProject.contrast.toDouble())
            put("saturation", videoProject.saturation.toDouble())
            put("audioVolume", videoProject.audioVolume.toDouble())
            put("isStabilized", videoProject.isStabilized)
            put("isChromaKeyEnabled", videoProject.isChromaKeyEnabled)
            put("activeWatermark", videoProject.activeWatermark)
            put("activeTransition", videoProject.activeTransition)
            put("textColorHex", videoProject.textColorHex)
            put("textFontFamily", videoProject.textFontFamily)
            put("textAnimation", videoProject.textAnimation)
            put("overlayMediaUri", videoProject.overlayMediaUri ?: "")
            put("overlayOpacity", videoProject.overlayOpacity.toDouble())
            put("activePipPosition", videoProject.activePipPosition)
            val clipsArray = org.json.JSONArray()
            videoProject.clips.forEach { clip ->
                val clipObj = JSONObject().apply {
                    put("id", clip.id)
                    put("name", clip.name)
                    put("uri", clip.uri?.toString() ?: "")
                    put("durationSeconds", clip.durationSeconds.toDouble())
                    put("isAiEnhanced", clip.isAiEnhanced)
                    put("isPhoto", clip.isPhoto)
                    put("trackIndex", clip.trackIndex)
                    put("speed", clip.speed.toDouble())
                    put("volume", clip.volume.toDouble())
                    put("rotation", clip.rotation.toDouble())
                    put("isFlippedHorizontally", clip.isFlippedHorizontally)
                    put("transitionIn", clip.transitionIn)
                    put("origin", clip.origin)
                    put("aiWhy", clip.aiWhy)
                    put("inSeconds", clip.inSeconds.toDouble())
                    put("outSeconds", clip.outSeconds.toDouble())
                    put("scale", clip.scale.toDouble())
                    put("opacity", clip.opacity.toDouble())
                    put("lastModified", clip.lastModified)
                }
                clipsArray.put(clipObj)
            }
            put("clips", clipsArray)
        }
        prefs.edit().putString(KEY_PREFIX_VIDEO + name, json.toString()).apply()

        // Track list of video project names
        val existingNames = prefs.getStringSet(KEY_ALL_VIDEO_NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        existingNames.add(name)
        prefs.edit().putStringSet(KEY_ALL_VIDEO_NAMES, existingNames).apply()
    }

    fun getVideoProject(context: Context, projectName: String): com.example.ui.components.VideoProjectData? {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_PREFIX_VIDEO + projectName.trim(), null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val clipsArray = obj.optJSONArray("clips")
            val clipsList = mutableListOf<com.example.ui.components.TimelineMediaClip>()
            if (clipsArray != null) {
                for (i in 0 until clipsArray.length()) {
                    val c = clipsArray.getJSONObject(i)
                    val uriStr = c.optString("uri", "")
                    val uri = if (uriStr.isNotBlank()) android.net.Uri.parse(uriStr) else null
                    val dur = c.optDouble("durationSeconds", 6.0).toFloat()
                    clipsList.add(
                        com.example.ui.components.TimelineMediaClip(
                            id = c.optString("id", java.util.UUID.randomUUID().toString()),
                            name = c.optString("name", "Clip"),
                            uri = uri,
                            durationSeconds = dur,
                            isAiEnhanced = c.optBoolean("isAiEnhanced", false),
                            isPhoto = c.optBoolean("isPhoto", false),
                            trackIndex = c.optInt("trackIndex", 0),
                            speed = c.optDouble("speed", 1.0).toFloat(),
                            volume = c.optDouble("volume", 1.0).toFloat(),
                            rotation = c.optDouble("rotation", 0.0).toFloat(),
                            isFlippedHorizontally = c.optBoolean("isFlippedHorizontally", false),
                            transitionIn = c.optString("transitionIn", "None"),
                            origin = c.optString("origin", "manual"),
                            aiWhy = c.optString("aiWhy", ""),
                            inSeconds = c.optDouble("inSeconds", 0.0).toFloat(),
                            outSeconds = c.optDouble("outSeconds", dur.toDouble()).toFloat(),
                            scale = c.optDouble("scale", 1.0).toFloat(),
                            opacity = c.optDouble("opacity", 1.0).toFloat(),
                            lastModified = c.optLong("lastModified", System.currentTimeMillis())
                        )
                    )
                }
            }
            val overlayUriStr = obj.optString("overlayMediaUri", "")
            val storedId = obj.optString("projectId", "")
            val resolvedId = storedId.ifBlank { com.example.studio.model.ProjectIdentity.newId() }
            val loaded = com.example.ui.components.VideoProjectData(
                projectId = resolvedId,
                projectName = obj.optString("projectName", projectName),
                resolution = obj.optString("resolution", "1080P"),
                fps = obj.optString("fps", "60FPS"),
                totalDurationSeconds = obj.optDouble("totalDurationSeconds", 42.0).toFloat(),
                currentPositionSeconds = obj.optDouble("currentPositionSeconds", 12.0).toFloat(),
                clips = clipsList,
                audioTrackName = obj.optString("audioTrackName", "Cyber_Synthwave.wav"),
                textOverlay = obj.optString("textOverlay", "NEON NIGHTS"),
                effectOverlay = obj.optString("effectOverlay", "Anamorphic Flare"),
                selectedClipIndex = obj.optInt("selectedClipIndex", 0),
                aspectRatio = obj.optString("aspectRatio", "9:16"),
                activeFilter = obj.optString("activeFilter", "Original"),
                filterIntensity = obj.optDouble("filterIntensity", 0.8).toFloat(),
                brightness = obj.optDouble("brightness", 0.0).toFloat(),
                contrast = obj.optDouble("contrast", 0.0).toFloat(),
                saturation = obj.optDouble("saturation", 0.0).toFloat(),
                audioVolume = obj.optDouble("audioVolume", 1.0).toFloat(),
                isStabilized = obj.optBoolean("isStabilized", false),
                isChromaKeyEnabled = obj.optBoolean("isChromaKeyEnabled", false),
                activeWatermark = obj.optString("activeWatermark", ""),
                activeTransition = obj.optString("activeTransition", "Dissolve"),
                textColorHex = obj.optString("textColorHex", "#00DAF3"),
                textFontFamily = obj.optString("textFontFamily", "Monospace"),
                textAnimation = obj.optString("textAnimation", "Glitch Fade"),
                overlayMediaUri = if (overlayUriStr.isNotBlank()) overlayUriStr else null,
                overlayOpacity = obj.optDouble("overlayOpacity", 1.0).toFloat(),
                activePipPosition = obj.optString("activePipPosition", "None")
            )
            if (storedId.isBlank()) {
                // Pre-UUID project: move its old name-hash studio data and chat to the new permanent id, then persist the id.
                com.example.studio.model.ProjectIdentity.adoptLegacyIdentity(context, loaded.projectName, resolvedId)
                saveVideoProject(context, loaded)
            }
            loaded
        } catch (e: Exception) {
            null
        }
    }

    /** Names of all saved video projects (used to generate unique names). */
    fun videoProjectNames(context: Context): List<String> =
        getPrefs(context).getStringSet(KEY_ALL_VIDEO_NAMES, emptySet())?.toList() ?: emptyList()

    /** "Untitled 1", "Untitled 2", ... */
    fun nextUntitledVideoName(context: Context): String =
        com.example.studio.model.ProjectIdentity.nextUntitled(videoProjectNames(context))

    /** Returns [base], or "base 2", "base 3"... if a project with that name already exists. */
    fun uniqueVideoProjectName(context: Context, base: String): String =
        com.example.studio.model.ProjectIdentity.uniqueName(base, videoProjectNames(context))

    fun hasVideoProject(context: Context, projectName: String): Boolean {
        if (projectName.isBlank()) return false
        val prefs = getPrefs(context)
        return prefs.contains(KEY_PREFIX_VIDEO + projectName.trim())
    }

    fun getAllVideoProjects(context: Context): List<com.example.ui.components.VideoProjectData> {
        val prefs = getPrefs(context)
        val names = prefs.getStringSet(KEY_ALL_VIDEO_NAMES, emptySet()) ?: emptySet()
        return names.mapNotNull { getVideoProject(context, it) }
    }

    /** Copies a project (timeline included) under a new permanent id and a unique name. Returns the new name. */
    fun duplicateVideoProject(context: Context, projectName: String): String? {
        val src = getVideoProject(context, projectName) ?: return null
        val newName = uniqueVideoProjectName(context, "${src.projectName} copy")
        val newId = com.example.studio.model.ProjectIdentity.newId()
        saveVideoProject(context, src.copy(projectName = newName, projectId = newId))
        runCatching {
            val st = com.example.studio.model.StudioProjectStorage(context)
            val now = System.currentTimeMillis()
            st.loadProject(src.projectId)?.let { st.saveProject(it.copy(id = newId, name = newName, createdAt = now, updatedAt = now)) }
        }
        return newName
    }

    fun deleteVideoProject(context: Context, projectName: String) {
        val prefs = getPrefs(context)
        val name = projectName.trim()
        // Remove the studio timeline and the AI chat that belong to this project too, so nothing is orphaned.
        getVideoProject(context, name)?.projectId?.let { id ->
            runCatching { com.example.studio.model.StudioProjectStorage(context).deleteProject(id) }
            runCatching { com.example.studio.director.DirectorStore(context).deleteAll(id) }
        }
        prefs.edit().remove(KEY_PREFIX_VIDEO + name).apply()
        val existingNames = prefs.getStringSet(KEY_ALL_VIDEO_NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        existingNames.remove(name)
        prefs.edit().putStringSet(KEY_ALL_VIDEO_NAMES, existingNames).apply()
    }
}
