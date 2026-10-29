package com.example.builder

import android.content.Context
import android.util.Log
import com.example.compiler.AppCompilerEngine
import com.example.ui.components.CodeEditorViewModel
import com.example.ui.components.EditorFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Direct Filesystem Bridge for SAIF AI Studio.
 * Synchronizes LLM generated files directly to disk, manages parent directories,
 * coordinates XML/Java integrity, and informs the editor UI in real-time.
 */
data class ProjectFileEntry(
    val path: String,
    val content: String,
    val action: String = "UPDATE"
)

object ProjectDiskBridge {
    private const val TAG = "ProjectDiskBridge"

    /**
     * Strips all markdown fences (```json, ```) from the LLM output
     * and parses the JSON array in an IO-safe manner.
     */
    fun parseGeneratedFiles(rawOutput: String): List<ProjectFileEntry> {
        val trimmed = rawOutput.trim()
        val files = mutableListOf<ProjectFileEntry>()

        // 1. Strip markdown fences if wrapping the JSON
        var cleanJson = trimmed
        if (cleanJson.startsWith("```json", ignoreCase = true)) {
            cleanJson = cleanJson.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substringAfter("```").substringBeforeLast("```").trim()
        }

        // 2. Try parsing as JSON Array directly: [ { "path": "...", "content": "..." }, ... ]
        try {
            val jsonArray = when {
                cleanJson.startsWith("[") -> JSONArray(cleanJson)
                cleanJson.contains("[") && cleanJson.contains("]") -> {
                    val start = cleanJson.indexOf('[')
                    val end = cleanJson.lastIndexOf(']')
                    JSONArray(cleanJson.substring(start, end + 1))
                }
                else -> null
            }

            if (jsonArray != null) {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val p = obj.optString("path", "").trim()
                    val c = obj.optString("content", "").trim()
                    val a = obj.optString("action", "UPDATE").trim()
                    if (p.isNotBlank() && c.isNotBlank()) {
                        files.removeAll { it.path.equals(p, ignoreCase = true) }
                        files.add(ProjectFileEntry(path = p, content = c, action = a))
                    }
                }
                if (files.isNotEmpty()) return files
            }
        } catch (e: Throwable) {
            safeLogW(TAG, "Failed parsing as raw JSONArray: ${e.message}")
        }

        // 3. Try parsing as JSONObject with "files" array: { "files": [ ... ] }
        try {
            val start = cleanJson.indexOf('{')
            val end = cleanJson.lastIndexOf('}')
            if (start in 0 until end) {
                val jsonObj = JSONObject(cleanJson.substring(start, end + 1))
                val filesArr = jsonObj.optJSONArray("files")
                if (filesArr != null) {
                    for (i in 0 until filesArr.length()) {
                        val obj = filesArr.getJSONObject(i)
                        val p = obj.optString("path", "").trim()
                        val c = obj.optString("content", "").trim()
                        val a = obj.optString("action", "UPDATE").trim()
                        if (p.isNotBlank() && c.isNotBlank()) {
                            files.removeAll { it.path.equals(p, ignoreCase = true) }
                            files.add(ProjectFileEntry(path = p, content = c, action = a))
                        }
                    }
                    if (files.isNotEmpty()) return files
                }
            }
        } catch (e: Throwable) {
            safeLogW(TAG, "Failed parsing as JSONObject with files array: ${e.message}")
        }

        // 4. Fallback: Regex extraction for escaped JSON blocks
        val fileRegex = Regex(""""path"\s*:\s*"([^"]+)"[\s\S]*?"content"\s*:\s*"([\s\S]*?)"""")
        for (match in fileRegex.findAll(cleanJson)) {
            val p = match.groupValues[1].trim()
            var c = match.groupValues[2]
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\t", "\t")
                .replace("\\r", "")
            if (p.isNotBlank() && c.isNotBlank()) {
                files.removeAll { it.path.equals(p, ignoreCase = true) }
                files.add(ProjectFileEntry(path = p, content = c))
            }
        }
        if (files.isNotEmpty()) return files

        // 5. Fallback: Markdown codeblocks ```xml:path or ```java
        val blockRegex = Regex("""```([a-zA-Z0-9_.-]*):?([a-zA-Z0-9_./\\-]*)\n?([\s\S]*?)```""")
        for (match in blockRegex.findAll(rawOutput)) {
            val lang = match.groupValues[1].trim().lowercase()
            val explicitPath = match.groupValues[2].trim()
            val code = match.groupValues[3].trim()
            if (code.isNotBlank() && lang != "json") {
                val path = when {
                    explicitPath.isNotBlank() -> explicitPath
                    lang == "xml" && (code.contains("<manifest") || code.contains("<application")) -> "app/src/main/AndroidManifest.xml"
                    lang == "xml" && code.contains("<resources>") -> "app/src/main/res/values/strings.xml"
                    lang == "xml" -> "app/src/main/res/layout/activity_main.xml"
                    lang == "java" || lang == "kt" || code.contains("class ") -> "app/src/main/java/com/saifai/app/MainActivity.java"
                    lang == "html" || code.contains("<html") -> "app/src/main/assets/index.html"
                    else -> "app/src/main/java/com/saifai/app/MainActivity.java"
                }
                files.removeAll { it.path.equals(path, ignoreCase = true) }
                files.add(ProjectFileEntry(path = path, content = code))
            }
        }

        return files
    }

    /**
     * Resolves target paths against the project base directory,
     * creates parent directories, writes complete code directly to files,
     * syncs layout variants, and notifies the editor UI.
     */
    suspend fun syncFilesToDisk(
        context: Context,
        projectDir: File,
        files: List<ProjectFileEntry>,
        editorViewModel: CodeEditorViewModel? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val writtenFiles = mutableListOf<File>()

        for (entry in files) {
            try {
                val cleanPath = entry.path.trim()
                    .replace('\\', '/')
                    .removePrefix("./")
                    .removePrefix("/")

                val targetFile = File(projectDir, cleanPath)
                targetFile.parentFile?.mkdirs()

                var contentToWrite = entry.content.trim()

                // Sanitize XML
                if (targetFile.name.endsWith(".xml", ignoreCase = true)) {
                    contentToWrite = sanitizeXml(contentToWrite)
                }

                // Sanitize Java package header if needed
                if (targetFile.name.endsWith(".java", ignoreCase = true)) {
                    contentToWrite = sanitizeJava(targetFile, contentToWrite)
                }

                targetFile.writeText(contentToWrite)
                writtenFiles.add(targetFile)

                // Sync main.xml <-> activity_main.xml so build, editor and compiler always find both
                if (targetFile.name.equals("activity_main.xml", ignoreCase = true)) {
                    val mainXml = File(targetFile.parentFile, "main.xml")
                    mainXml.writeText(contentToWrite)
                    if (!writtenFiles.contains(mainXml)) writtenFiles.add(mainXml)
                } else if (targetFile.name.equals("main.xml", ignoreCase = true)) {
                    val actXml = File(targetFile.parentFile, "activity_main.xml")
                    actXml.writeText(contentToWrite)
                    if (!writtenFiles.contains(actXml)) writtenFiles.add(actXml)
                }

                // If MainActivity is generated, also sync to canonical project package path if different
                if (targetFile.name.equals("MainActivity.java", ignoreCase = true) || targetFile.name.equals("MainActivity.kt", ignoreCase = true)) {
                    val existingMains = projectDir.walkTopDown().filter {
                        it.isFile && (it.name.equals("MainActivity.java", ignoreCase = true) || it.name.equals("MainActivity.kt", ignoreCase = true)) &&
                                it.absolutePath != targetFile.absolutePath && !it.path.contains("/build/")
                    }.toList()
                    for (otherMain in existingMains) {
                        otherMain.writeText(contentToWrite)
                        if (!writtenFiles.contains(otherMain)) writtenFiles.add(otherMain)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing file ${entry.path}", e)
            }
        }

        // Post-write actions on Main thread:
        // 1. Notify the File Explorer Tree to instantly reflect new/updated files
        // 2. If the In-App Code Editor currently has that file open, refresh editor buffer from disk
        // 3. If no file was open, automatically select the updated primary file so user sees the code
        if (editorViewModel != null) {
            withContext(Dispatchers.Main) {
                try {
                    editorViewModel.refreshTree(context)
                    val activeFile = editorViewModel.activeFile.value
                    if (activeFile != null) {
                        val matchingWritten = writtenFiles.find {
                            it.name.equals(activeFile.name, ignoreCase = true) ||
                            it.name.substringBefore('.') == activeFile.name.substringBefore('.')
                        }
                        if (matchingWritten != null && matchingWritten.exists()) {
                            val rel = matchingWritten.relativeTo(projectDir).path.replace('\\', '/')
                            editorViewModel.updateFileFromExternal(
                                EditorFileItem(
                                    name = matchingWritten.name,
                                    path = rel,
                                    isMain = matchingWritten.name.startsWith("MainActivity")
                                ),
                                matchingWritten.readText()
                            )
                        } else {
                            editorViewModel.reloadActiveFile(context)
                        }
                    } else {
                        // Automatically open primary generated file into editor
                        val primary = writtenFiles.find { it.name.startsWith("MainActivity") }
                            ?: writtenFiles.find { it.name.equals("activity_main.xml", ignoreCase = true) }
                            ?: writtenFiles.firstOrNull()
                        if (primary != null && primary.exists()) {
                            val rel = primary.relativeTo(projectDir).path.replace('\\', '/')
                            editorViewModel.selectFile(
                                context,
                                EditorFileItem(
                                    name = primary.name,
                                    path = rel,
                                    isMain = primary.name.startsWith("MainActivity")
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating editor ViewModel: ${e.message}")
                }
            }
        }

        writtenFiles
    }

    private fun sanitizeXml(raw: String): String {
        var clean = raw.trim().removePrefix("\uFEFF").trim()
        if (clean.contains("<?xml")) {
            clean = clean.substring(clean.indexOf("<?xml")).trim()
        } else {
            clean = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$clean"
        }
        return clean.replace(Regex("""<!--\s*(?:File|filepath|filename)?:?.*?-->""", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun sanitizeJava(targetFile: File, raw: String): String {
        var clean = raw.trim()
        val path = targetFile.absolutePath.replace('\\', '/')
        if (path.contains("/src/main/java/")) {
            val pkgSub = path.substringAfter("/src/main/java/").substringBeforeLast('/')
            val expectedPkg = pkgSub.replace('/', '.')
            if (expectedPkg.isNotBlank() && !expectedPkg.contains(".")) {
                // simple subfolder
            }
            if (expectedPkg.isNotBlank()) {
                val pkgHeader = "package $expectedPkg;"
                if (!clean.contains("package ")) {
                    clean = "$pkgHeader\n\n$clean"
                } else {
                    clean = clean.replaceFirst(Regex("""package\s+[^;]+;"""), pkgHeader)
                }
            }
        }
        return clean
    }

    private fun safeLogW(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (ignored: Throwable) {
            // Ignored in non-Robolectric JVM unit test environments
        }
    }
}
