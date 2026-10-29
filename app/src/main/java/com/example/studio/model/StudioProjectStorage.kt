package com.example.studio.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Robust JSON storage for StudioProject v2 under filesDir/studio/projects/<id>/project.json.
 * Ids are permanent UUIDs, so same-named projects never collide. Every timeline item field is
 * persisted (see [StudioItemCodec]) and writes are atomic (temp file + rename).
 */
class StudioProjectStorage(private val context: Context) {

    private val studioDir = File(context.filesDir, "studio")
    private val projectsDir = File(studioDir, "projects")
    private val indexFile = File(studioDir, "index.json")

    init {
        projectsDir.mkdirs()
    }

    private fun safeDirName(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "invalid" }

    @Synchronized
    fun exists(id: String): Boolean = File(File(projectsDir, safeDirName(id)), "project.json").exists()

    @Synchronized
    fun saveProject(project: StudioProject) {
        val projDir = File(projectsDir, safeDirName(project.id))
        projDir.mkdirs()
        val tempFile = File(projDir, "project.json.tmp")
        val finalFile = File(projDir, "project.json")

        val root = JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("createdAt", project.createdAt)
            put("updatedAt", project.updatedAt)
            put("aspect", project.aspect)
            put("fps", project.fps)
            put("schemaVersion", project.schemaVersion)

            val lanesArr = JSONArray()
            project.lanes.forEach { l ->
                lanesArr.put(JSONObject().apply {
                    put("id", l.id)
                    put("trackType", l.trackType.name)
                    put("name", l.name)
                    put("isMuted", l.isMuted)
                    put("isSolo", l.isSolo)
                    put("isLocked", l.isLocked)
                    put("heightDp", l.heightDp)
                })
            }
            put("lanes", lanesArr)

            val itemsArr = JSONArray()
            project.items.forEach { itemsArr.put(StudioItemCodec.toJson(it)) }
            put("items", itemsArr)

            val markersArr = JSONArray()
            project.markersUs.forEach { markersArr.put(it) }
            put("markersUs", markersArr)
        }

        tempFile.writeText(root.toString())
        if (!tempFile.renameTo(finalFile)) {
            // Some file systems refuse to rename over an existing file.
            finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                finalFile.writeText(tempFile.readText())
                tempFile.delete()
            }
        }
        updateIndex(project)
    }

    @Synchronized
    fun loadProject(id: String): StudioProject? {
        val file = File(File(projectsDir, safeDirName(id)), "project.json")
        if (!file.exists()) return null

        return try {
            val root = JSONObject(file.readText())
            val lanesArr = root.optJSONArray("lanes") ?: JSONArray()
            val lanes = mutableListOf<StudioLane>()
            for (i in 0 until lanesArr.length()) {
                val o = lanesArr.getJSONObject(i)
                val tt = TrackType.values().firstOrNull { it.name == o.optString("trackType") } ?: TrackType.VIDEO
                lanes.add(
                    StudioLane(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        trackType = tt,
                        name = o.optString("name", "Lane"),
                        isMuted = o.optBoolean("isMuted", false),
                        isSolo = o.optBoolean("isSolo", false),
                        isLocked = o.optBoolean("isLocked", false),
                        heightDp = o.optInt("heightDp", 48)
                    )
                )
            }

            val itemsArr = root.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<TimelineItem>()
            for (i in 0 until itemsArr.length()) {
                val o = itemsArr.optJSONObject(i) ?: continue
                items.add(StudioItemCodec.fromJson(o))
            }

            val markersArr = root.optJSONArray("markersUs") ?: JSONArray()
            val markers = mutableListOf<Long>()
            for (i in 0 until markersArr.length()) {
                markers.add(markersArr.getLong(i))
            }

            StudioProject(
                id = root.optString("id", id).ifBlank { id },
                name = root.optString("name", "Untitled"),
                createdAt = root.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = root.optLong("updatedAt", System.currentTimeMillis()),
                aspect = root.optString("aspect", "9:16"),
                fps = root.optInt("fps", 30),
                lanes = if (lanes.isNotEmpty()) lanes else StudioProject.defaultEmptyLanes(),
                items = items,
                markersUs = markers,
                schemaVersion = root.optInt("schemaVersion", 2)
            )
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun listProjects(): List<StudioProjectSummary> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            val list = mutableListOf<StudioProjectSummary>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    StudioProjectSummary(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        updatedAt = o.optLong("updatedAt", 0L),
                        aspect = o.optString("aspect", "9:16"),
                        durationSeconds = o.optDouble("durationSeconds", 0.0).toFloat(),
                        itemCount = o.optInt("itemCount", 0)
                    )
                )
            }
            // De-duplicate by id (keep the most recently updated entry) and sort newest first.
            list.groupBy { it.id }.map { (_, v) -> v.maxByOrNull { it.updatedAt }!! }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun deleteProject(id: String) {
        File(projectsDir, safeDirName(id)).deleteRecursively()
        writeIndex(listProjects().filterNot { it.id == id })
    }

    private fun updateIndex(project: StudioProject) {
        val current = listProjects().filterNot { it.id == project.id }
        val updatedSummary = StudioProjectSummary(
            id = project.id,
            name = project.name,
            updatedAt = project.updatedAt,
            aspect = project.aspect,
            durationSeconds = project.totalDurationSeconds,
            itemCount = project.items.size
        )
        writeIndex(listOf(updatedSummary) + current)
    }

    private fun writeIndex(list: List<StudioProjectSummary>) {
        studioDir.mkdirs()
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("updatedAt", s.updatedAt)
                put("aspect", s.aspect)
                put("durationSeconds", s.durationSeconds.toDouble())
                put("itemCount", s.itemCount)
            })
        }
        indexFile.writeText(arr.toString())
    }
}

data class StudioProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val aspect: String,
    val durationSeconds: Float,
    val itemCount: Int
)

/**
 * Opening rule: the saved studio project (with all AI work) always wins. A legacy
 * project is migrated only when no saved studio project exists yet.
 */
object StudioProjectResolver {
    fun resolve(context: Context, legacy: com.example.ui.components.VideoProjectData): StudioProject {
        val storage = StudioProjectStorage(context)
        storage.loadProject(legacy.projectId)?.let { return it }
        val migrated = StudioProjectMigrator.migrate(legacy)
        runCatching { storage.saveProject(migrated) }
        return migrated
    }
}
