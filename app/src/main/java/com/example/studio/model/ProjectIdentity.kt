package com.example.studio.model

import android.content.Context
import com.example.studio.director.DirectorStore
import java.util.UUID

/**
 * Permanent project identity. Ids are random UUIDs created once at project creation and are
 * never derived from the project name, so two projects with the same name can never share
 * a timeline, a chat or a view-model.
 */
object ProjectIdentity {

    fun newId(): String = UUID.randomUUID().toString()

    /** The id the old code derived from a project name. Only used to migrate pre-UUID data. */
    fun legacyHashId(projectName: String): String = "proj_${projectName.hashCode().toUInt()}"

    /** "Untitled 1", "Untitled 2", ... — first number not used by [existing] (case-insensitive, trimmed). */
    fun nextUntitled(existing: Collection<String>): String {
        val used = existing.map { it.trim().lowercase() }.toSet()
        var n = 1
        while ("untitled $n" in used) n++
        return "Untitled $n"
    }

    /** Returns [base] when free, otherwise "base 2", "base 3", ... */
    fun uniqueName(base: String, existing: Collection<String>): String {
        val clean = base.trim().ifBlank { return nextUntitled(existing) }
        val used = existing.map { it.trim().lowercase() }.toSet()
        if (clean.lowercase() !in used) return clean
        var n = 2
        while ("${clean.lowercase()} $n" in used) n++
        return "$clean $n"
    }

    /**
     * One-time migration for a legacy project that had no id: moves the studio project and the director
     * chat that were stored under the old name-hash id to [newId]. Nothing is lost; safe to call repeatedly.
     */
    fun adoptLegacyIdentity(context: Context, projectName: String, newId: String) {
        val oldId = legacyHashId(projectName)
        if (oldId == newId) return
        runCatching {
            val storage = StudioProjectStorage(context)
            if (!storage.exists(newId)) {
                storage.loadProject(oldId)?.let { old ->
                    storage.saveProject(old.copy(id = newId))
                    storage.deleteProject(oldId)
                }
            }
        }
        runCatching { DirectorStore(context).moveProject(oldId, newId) }
    }
}
