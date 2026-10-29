package com.example.ui.components

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Represents a node in the project file explorer tree.
 */
data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileTreeNode> = emptyList(),
    val isMain: Boolean = false,
    val depth: Int = 0
)

/**
 * Production-grade ViewModel for the in-app Code Studio Editor & File Viewer.
 * Safely handles disk I/O on Dispatchers.IO, maintains undo/redo history,
 * manages the expandable project tree, and tracks unsaved changes.
 */
class CodeEditorViewModel : ViewModel() {

    // Current Project Data
    private val _projectData = MutableStateFlow<ProjectData?>(null)
    val projectData = _projectData.asStateFlow()

    // File Tree State
    private val _fileTree = MutableStateFlow<List<FileTreeNode>>(emptyList())
    val fileTree = _fileTree.asStateFlow()

    // Set of paths that are currently expanded in the sidebar tree
    private val _expandedPaths = MutableStateFlow<Set<String>>(emptySet())
    val expandedPaths = _expandedPaths.asStateFlow()

    // Active File State
    private val _activeFile = MutableStateFlow<EditorFileItem?>(null)
    val activeFile = _activeFile.asStateFlow()

    // Editor Text & Selection
    private val _editorTextFieldValue = MutableStateFlow(TextFieldValue(""))
    val editorTextFieldValue = _editorTextFieldValue.asStateFlow()

    // History (Undo / Redo)
    private var editorHistory = EditorHistory("")
    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    // Dirty / Unsaved Changes Indicator
    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    // Loading State
    private val _isLoadingFile = MutableStateFlow(false)
    val isLoadingFile = _isLoadingFile.asStateFlow()

    // Status / Feedback messages (Toasts & Snackbars)
    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val statusMessage = _statusMessage.asSharedFlow()

    // Timestamp of last successful save
    private val _lastSavedTimestamp = MutableStateFlow<Long?>(null)
    val lastSavedTimestamp = _lastSavedTimestamp.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes the project workspace on disk, creating actual physical directories
     * and template files matching the Android Studio structure shown in user screenshots.
     */
    fun initProject(context: Context, project: ProjectData) {
        if (isInitialized && _projectData.value?.appName == project.appName) {
            return
        }
        _projectData.value = project
        isInitialized = true
        _activeFile.value = null
        _editorTextFieldValue.value = TextFieldValue("")
        _isDirty.value = false
        editorHistory = EditorHistory("")
        _canUndo.value = false
        _canRedo.value = false
        _fileTree.value = emptyList()
        _expandedPaths.value = emptySet()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sanitizedAppName = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val projectDir = File(context.filesDir, "projects/$sanitizedAppName")
                if (!projectDir.exists()) {
                    projectDir.mkdirs()
                }

                // 1. Scaffold the physical directory structure and boilerplate files
                scaffoldProjectFiles(projectDir, project)

                // 2. Build the hierarchical FileTreeNode list
                val rootNodes = buildTreeHierarchy(projectDir, project.appName, project.packageName)
                _fileTree.value = rootNodes

                // 3. Auto-expand paths down to MainActivity so it's immediately visible
                val initialExpanded = mutableSetOf<String>()
                fun collectDefaultExpanded(nodes: List<FileTreeNode>) {
                    for (node in nodes) {
                        if (node.isDirectory) {
                            // Expand common root folders: app, src, main, java, com, etc.
                            if (node.name in listOf(project.appName, "app", "src", "main", "java", "com", "example", "res", "layout", "values")) {
                                initialExpanded.add(node.path)
                            }
                            collectDefaultExpanded(node.children)
                        }
                    }
                }
                collectDefaultExpanded(rootNodes)
                _expandedPaths.value = initialExpanded

                // 4. Determine initial starter file
                val defaultFile = CodeTemplates.getDefaultFile(project.language, project.appName, project.packageName)
                val targetFileName = defaultFile.fileName
                val mainFile = findFileInTree(rootNodes, targetFileName) ?: EditorFileItem(
                    name = targetFileName,
                    path = "app/src/main/java/${project.packageName.replace('.', '/')}/$targetFileName",
                    isMain = true
                )

                // 5. Load the initial file into the editor
                loadFileInternal(context, projectDir, mainFile, defaultFile.initialCode)

            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.emit("Error initializing project: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    /**
     * Toggles a folder's expanded or collapsed state in the file explorer.
     */
    fun toggleFolder(path: String) {
        val current = _expandedPaths.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _expandedPaths.value = current
    }

    /**
     * Selects a file from the tree, auto-saves the previous file if dirty,
     * reads the new file from disk on Dispatchers.IO, and updates the editor.
     */
    fun selectFile(context: Context, file: EditorFileItem, onComplete: () -> Unit = {}) {
        val current = _activeFile.value

        viewModelScope.launch(Dispatchers.IO) {
            // Auto-save previous file if modified and switching to a different file
            if (_isDirty.value && current != null && current.path != file.path) {
                saveFileInternal(context, current, _editorTextFieldValue.value.text)
            }

            val project = _projectData.value
            val sanitized = project?.appName?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "DefaultProject"
            val projectDir = File(context.filesDir, "projects/$sanitized")

            loadFileInternal(context, projectDir, file, null)

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Reads a file from disk into the editor state.
     */
    private suspend fun loadFileInternal(
        context: Context,
        projectDir: File,
        file: EditorFileItem,
        fallbackCode: String?
    ) {
        _isLoadingFile.value = true
        try {
            val diskFile = File(projectDir, file.path)
            val code = if (diskFile.exists()) {
                diskFile.readText()
            } else {
                fallbackCode ?: getBoilerplateForFile(file.name, _projectData.value)
            }

            // If file doesn't exist on disk, persist it now
            if (!diskFile.exists()) {
                diskFile.parentFile?.mkdirs()
                diskFile.writeText(code)
            }

            withContext(Dispatchers.Main) {
                _activeFile.value = file
                _editorTextFieldValue.value = TextFieldValue(text = code, selection = TextRange(0))
                editorHistory = EditorHistory(code)
                _canUndo.value = false
                _canRedo.value = false
                _isDirty.value = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _statusMessage.emit("Failed to load ${file.name}: ${e.message}")
        } finally {
            _isLoadingFile.value = false
        }
    }

    /**
     * Handles manual code edits from the user, updates undo/redo stacks,
     * and flags the file as dirty.
     */
    fun onCodeChanged(newValue: TextFieldValue) {
        val currentText = _editorTextFieldValue.value.text
        if (newValue.text != currentText) {
            editorHistory.recordChange(currentText, newValue.text)
            _canUndo.value = editorHistory.canUndo()
            _canRedo.value = editorHistory.canRedo()
            _isDirty.value = true
        }
        _editorTextFieldValue.value = newValue
    }

    /**
     * Reverts to previous text state in history stack.
     */
    fun undo() {
        val currentText = _editorTextFieldValue.value.text
        val previous = editorHistory.undo(currentText)
        if (previous != null) {
            _editorTextFieldValue.value = TextFieldValue(
                text = previous,
                selection = TextRange(previous.length)
            )
            _canUndo.value = editorHistory.canUndo()
            _canRedo.value = editorHistory.canRedo()
            _isDirty.value = true
        }
    }

    /**
     * Moves forward to next text state in history stack.
     */
    fun redo() {
        val currentText = _editorTextFieldValue.value.text
        val next = editorHistory.redo(currentText)
        if (next != null) {
            _editorTextFieldValue.value = TextFieldValue(
                text = next,
                selection = TextRange(next.length)
            )
            _canUndo.value = editorHistory.canUndo()
            _canRedo.value = editorHistory.canRedo()
            _isDirty.value = true
        }
    }

    /**
     * Saves the current active file's code to device storage on Dispatchers.IO.
     */
    fun saveCurrentFile(context: Context) {
        val file = _activeFile.value ?: return
        val textToSave = _editorTextFieldValue.value.text

        viewModelScope.launch(Dispatchers.IO) {
            saveFileInternal(context, file, textToSave)
            _statusMessage.emit("${file.name} saved successfully!")
        }
    }

    private fun saveFileInternal(context: Context, file: EditorFileItem, textToSave: String) {
        try {
            val project = _projectData.value
            val sanitized = project?.appName?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "DefaultProject"
            val projectDir = File(context.filesDir, "projects/$sanitized")
            val target = File(projectDir, file.path)
            target.parentFile?.mkdirs()
            target.writeText(textToSave)

            // Cache to SharedPreferences for extra fast recovery
            val prefs = context.getSharedPreferences("ProjectFiles_$sanitized", Context.MODE_PRIVATE)
            prefs.edit().putString(file.name, textToSave).putLong("${file.name}_saved_at", System.currentTimeMillis()).apply()

            _isDirty.value = false
            _lastSavedTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch {
                _statusMessage.emit("Save failed: ${e.message}")
            }
        }
    }

    /**
     * Applies AI-generated code directly to the editor, pushing a checkpoint to undo history.
     */
    fun applyAiCode(newCode: String, context: Context) {
        editorHistory.pushCheckpoint(_editorTextFieldValue.value.text)
        _editorTextFieldValue.value = TextFieldValue(text = newCode, selection = TextRange(newCode.length))
        _canUndo.value = editorHistory.canUndo()
        _isDirty.value = false
        saveCurrentFile(context)
    }

    /**
     * Reloads the currently active file from storage to ensure the editor reflects any external changes made by AI.
     */
    fun reloadActiveFile(context: Context) {
        val file = _activeFile.value ?: return
        val project = _projectData.value ?: return
        val sanitized = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "DefaultProject" }
        val projectDir = File(context.filesDir, "projects/$sanitized")
        viewModelScope.launch(Dispatchers.IO) {
            loadFileInternal(context, projectDir, file, null)
        }
    }

    /**
     * Updates in-memory editor content if the modified file matches the currently open file.
     */
    fun updateFileFromExternal(fileItem: EditorFileItem, newContent: String) {
        val current = _activeFile.value
        if (current?.path == fileItem.path || current?.name.equals(fileItem.name, ignoreCase = true)) {
            _editorTextFieldValue.value = TextFieldValue(text = newContent, selection = TextRange(0))
            _isDirty.value = false
        }
    }

    /**
     * Closes the active file tab.
     */
    fun closeActiveFile(context: Context) {
        if (_isDirty.value) {
            _activeFile.value?.let { file ->
                saveFileInternal(context, file, _editorTextFieldValue.value.text)
            }
        }
        _activeFile.value = null
        _editorTextFieldValue.value = TextFieldValue("")
        _isDirty.value = false
    }

    /**
     * Creates a new file in the project, updates disk and refreshes the tree.
     */
    fun createNewFile(context: Context, relativeParentPath: String, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val project = _projectData.value ?: return@launch
                val sanitized = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val projectDir = File(context.filesDir, "projects/$sanitized")

                val cleanParent = relativeParentPath.trim().removePrefix("/").removeSuffix("/")
                val targetDir = if (cleanParent.isBlank()) projectDir else File(projectDir, cleanParent)
                targetDir.mkdirs()

                val newFile = File(targetDir, fileName.trim())
                val initialContent = getBoilerplateForFile(fileName, project)
                newFile.writeText(initialContent)

                // Refresh tree
                val rootNodes = buildTreeHierarchy(projectDir, project.appName, project.packageName)
                _fileTree.value = rootNodes

                // Select newly created file
                val relativePath = newFile.relativeTo(projectDir).path
                val newItem = EditorFileItem(fileName.trim(), relativePath)
                loadFileInternal(context, projectDir, newItem, initialContent)
                _statusMessage.emit("Created $fileName")
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.emit("Failed to create file: ${e.message}")
            }
        }
    }

    /**
     * Scaffolds the complete Android Studio physical folder and file structure on disk.
     */
    private fun scaffoldProjectFiles(projectDir: File, project: ProjectData) {
        val cleanPkg = project.packageName.ifBlank { "com.example.newproject3" }
        val pkgPath = cleanPkg.replace('.', '/')
        val appName = project.appName

        // 1. Root files
        createFileIfNotExists(
            File(projectDir, "settings.gradle"),
            """
            rootProject.name = "$appName"
            include ':app'
            """.trimIndent()
        )

        // 2. App folder files
        createFileIfNotExists(
            File(projectDir, "app/build.gradle"),
            """
            plugins {
                id 'com.android.application'
            }

            android {
                namespace '$cleanPkg'
                compileSdk ${project.targetSdk}

                defaultConfig {
                    applicationId '$cleanPkg'
                    minSdk ${project.minSdk}
                    targetSdk ${project.targetSdk}
                    versionCode 1
                    versionName "1.0"
                }

                buildTypes {
                    release {
                        minifyEnabled false
                        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                    }
                }
            }

            dependencies {
                implementation 'androidx.appcompat:appcompat:1.6.1'
                implementation 'com.google.android.material:material:1.11.0'
            }
            """.trimIndent()
        )

        createFileIfNotExists(
            File(projectDir, "app/app_config.json"),
            """
            {
              "project_name": "$appName",
              "package_name": "$cleanPkg",
              "version": "1.0.0",
              "build_studio": "${project.buildStudio}",
              "min_sdk": ${project.minSdk},
              "target_sdk": ${project.targetSdk}
            }
            """.trimIndent()
        )

        // Empty libs directory
        File(projectDir, "app/libs").mkdirs()

        // 3. AndroidManifest.xml
        createFileIfNotExists(
            File(projectDir, "app/src/main/AndroidManifest.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$cleanPkg">

                <uses-permission android:name="android.permission.INTERNET" />

                <application
                    android:allowBackup="true"
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name"
                    android:roundIcon="@drawable/ic_launcher"
                    android:supportsRtl="true"
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

            </manifest>
            """.trimIndent()
        )

        // 4. Source Java / Kotlin File
        val isKotlin = project.language.contains("Kotlin", ignoreCase = true)
        val sourceExt = if (isKotlin) "kt" else "java"
        val sourceFile = File(projectDir, "app/src/main/java/$pkgPath/MainActivity.$sourceExt")

        val starterSource = if (isKotlin) {
            """
            package $cleanPkg

            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView
            import android.widget.Toast

            class MainActivity : Activity() {

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.main)

                    val titleText = findViewById<TextView>(R.id.title_text)
                    titleText?.text = "Welcome to $appName!"

                    Toast.makeText(this, "$appName Ready", Toast.LENGTH_SHORT).show()
                }
            }
            """.trimIndent()
        } else {
            """
            package $cleanPkg;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;
            import android.widget.Toast;

            public class MainActivity extends Activity {

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.main);

                    TextView titleText = findViewById(R.id.title_text);
                    if (titleText != null) {
                        titleText.setText("Welcome to $appName!");
                    }

                    Toast.makeText(this, "$appName Ready", Toast.LENGTH_SHORT).show();
                }
            }
            """.trimIndent()
        }

        createFileIfNotExists(sourceFile, starterSource)

        // 5. Resources: layout/main.xml
        createFileIfNotExists(
            File(projectDir, "app/src/main/res/layout/main.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
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
                    android:text="@string/app_name"
                    android:textColor="#F97316"
                    android:textSize="24sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/welcome_message"
                    android:textColor="#94A3B8"
                    android:textSize="15sp"
                    android:gravity="center" />

                <Button
                    android:id="@+id/action_button"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="24dp"
                    android:text="Run Code"
                    android:backgroundTint="#00A86B"
                    android:textColor="#FFFFFF" />

            </LinearLayout>
            """.trimIndent()
        )

        // 6. Resources: values/strings.xml, colors.xml, styles.xml
        createFileIfNotExists(
            File(projectDir, "app/src/main/res/values/strings.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$appName</string>
                <string name="welcome_message">Welcome to your new Android application built with SAIF AI Studio.</string>
            </resources>
            """.trimIndent()
        )

        createFileIfNotExists(
            File(projectDir, "app/src/main/res/values/colors.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="primary">#F97316</color>
                <color name="primary_dark">#C2410C</color>
                <color name="accent">#00A86B</color>
                <color name="background">#0F172A</color>
                <color name="card_bg">#1E293B</color>
            </resources>
            """.trimIndent()
        )

        createFileIfNotExists(
            File(projectDir, "app/src/main/res/values/styles.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar">
                    <item name="android:colorPrimary">@color/primary</item>
                    <item name="android:colorPrimaryDark">@color/primary_dark</item>
                    <item name="android:colorAccent">@color/accent</item>
                </style>
            </resources>
            """.trimIndent()
        )

        // 7. Extra folders shown in user screenshots
        File(projectDir, "app/src/main/res/drawable").mkdirs()
        File(projectDir, "app/src/main/res/drawable-xhdpi").mkdirs()
        File(projectDir, "app/src/main/assets").mkdirs()
        File(projectDir, "app/src/main/jni").mkdirs()
    }

    private fun createFileIfNotExists(file: File, content: String) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    /**
     * Recursively traverses projectDir and builds a structured tree of FileTreeNode objects.
     */
    private fun buildTreeHierarchy(projectDir: File, rootLabel: String, packageName: String): List<FileTreeNode> {
        fun scanDirectory(file: File, depth: Int): FileTreeNode {
            val relativePath = file.relativeTo(projectDir).path.ifBlank { file.name }
            if (file.isDirectory) {
                val rawChildren = file.listFiles()?.toList().orEmpty()
                // Sort folders first, then files alphabetically
                val sorted = rawChildren.sortedWith(
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                )
                val childrenNodes = sorted.map { child -> scanDirectory(child, depth + 1) }
                return FileTreeNode(
                    name = file.name,
                    path = relativePath,
                    isDirectory = true,
                    children = childrenNodes,
                    depth = depth
                )
            } else {
                val isMain = file.name.startsWith("MainActivity")
                return FileTreeNode(
                    name = file.name,
                    path = relativePath,
                    isDirectory = false,
                    isMain = isMain,
                    depth = depth
                )
            }
        }

        // Return the root node named after the project, containing all children
        val topFiles = projectDir.listFiles()?.toList().orEmpty().sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )

        val rootChildren = topFiles.map { scanDirectory(it, 1) }

        return listOf(
            FileTreeNode(
                name = rootLabel,
                path = "",
                isDirectory = true,
                children = rootChildren,
                depth = 0
            )
        )
    }

    private fun findFileInTree(nodes: List<FileTreeNode>, targetName: String): EditorFileItem? {
        for (node in nodes) {
            if (!node.isDirectory && node.name.equals(targetName, ignoreCase = true)) {
                return EditorFileItem(node.name, node.path, node.isMain)
            }
            if (node.isDirectory) {
                val found = findFileInTree(node.children, targetName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun getBoilerplateForFile(fileName: String, project: ProjectData?): String {
        val appName = project?.appName ?: "MyProject"
        val pkg = project?.packageName ?: "com.example.newproject3"

        return when {
            fileName.endsWith(".java") -> """
            package $pkg;

            public class ${fileName.removeSuffix(".java")} {
                // Code for $appName
            }
            """.trimIndent()

            fileName.endsWith(".kt") -> """
            package $pkg

            class ${fileName.removeSuffix(".kt")} {
                // Code for $appName
            }
            """.trimIndent()

            fileName.endsWith(".xml") -> """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <!-- Resource for $appName -->
            </resources>
            """.trimIndent()

            fileName.endsWith(".json") -> """
            {
              "name": "$appName",
              "file": "$fileName"
            }
            """.trimIndent()

            else -> """
            // $fileName
            // Created with SAIF AI Studio
            """.trimIndent()
        }
    }

    /**
     * Returns the physical project directory on disk.
     */
    fun getProjectDirectory(context: Context): File {
        val sanitized = _projectData.value?.appName?.replace(Regex("[^a-zA-Z0-9_]"), "_") ?: "DefaultProject"
        val dir = File(context.filesDir, "projects/$sanitized")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Refreshes the project file tree hierarchy on disk.
     */
    fun refreshTree(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = _projectData.value ?: return@launch
            val projectDir = getProjectDirectory(context)
            val rootNodes = buildTreeHierarchy(projectDir, project.appName, project.packageName)
            _fileTree.value = rootNodes
        }
    }
}
