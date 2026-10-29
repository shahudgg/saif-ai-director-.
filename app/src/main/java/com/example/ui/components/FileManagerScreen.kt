package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Represents an entry inside the File Manager list.
 */
data class ManagerItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L
)

/**
 * Creation type options matching the studio's file creation modal.
 */
enum class CreationType(val label: String) {
    FOLDER("Folder"),
    JAVA_CLASS("Java Class"),
    JAVA_ACTIVITY("Java Activity")
}

/**
 * File Manager Screen in SAIF AI Signature Liquid Glass Dark Theme.
 * Supports: Java File, Resources File, Assets File, Lib File, Jni File.
 * - Liquid glass top bar with path breadcrumbs & category icon
 * - Liquid glass cards for folders and files with luminous badges
 * - Java Coffee icon with authentic red/blue steam for Java source files
 * - Animated Cyan/Violet FAB for creating new items
 * - Fully working on-device file management synced with CodeEditorViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    category: FileManagerCategory,
    initialRelativePath: String,
    title: String,
    project: ProjectData,
    editorViewModel: CodeEditorViewModel,
    onBack: () -> Unit,
    onSelectFile: (EditorFileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Base directory of the project
    val sanitizedApp = remember(project.appName) {
        project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "DefaultProject" }
    }
    val projectBaseDir = remember(sanitizedApp) {
        File(context.filesDir, "projects/$sanitizedApp")
    }

    // Current relative path inside project
    var currentRelativePath by remember {
        mutableStateOf(
            if (initialRelativePath.isNotBlank()) initialRelativePath
            else when (category) {
                FileManagerCategory.JAVA -> {
                    val pkgPath = project.packageName.replace('.', '/').ifBlank { "com/example" }
                    "app/src/main/java/$pkgPath"
                }
                FileManagerCategory.RESOURCES -> "app/src/main/res"
                FileManagerCategory.ASSETS -> "app/src/main/assets"
                FileManagerCategory.LIB -> "app/libs"
                FileManagerCategory.JNI -> "app/src/main/jni"
            }
        )
    }

    var itemsList by remember { mutableStateOf<List<ManagerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ManagerItem?>(null) }

    // Helper to reload directory contents from storage
    fun reloadFiles() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val targetDir = File(projectBaseDir, currentRelativePath)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val rawFiles = targetDir.listFiles()?.toList().orEmpty()
            val mapped = rawFiles.map { file ->
                val rel = file.relativeTo(projectBaseDir).path
                ManagerItem(
                    name = file.name,
                    relativePath = rel,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified()
                )
            }.sortedWith(
                compareBy<ManagerItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
            )

            withContext(Dispatchers.Main) {
                itemsList = mapped
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentRelativePath) {
        reloadFiles()
    }

    val defaultPath = remember(category, project.packageName) {
        when (category) {
            FileManagerCategory.JAVA -> {
                val pkgPath = project.packageName.replace('.', '/').ifBlank { "com/example" }
                "app/src/main/java/$pkgPath"
            }
            FileManagerCategory.RESOURCES -> "app/src/main/res"
            FileManagerCategory.ASSETS -> "app/src/main/assets"
            FileManagerCategory.LIB -> "app/libs"
            FileManagerCategory.JNI -> "app/src/main/jni"
        }
    }

    androidx.activity.compose.BackHandler {
        if (currentRelativePath != defaultPath && currentRelativePath.contains("/")) {
            val parent = currentRelativePath.substringBeforeLast('/', "")
            if (parent.isNotBlank()) {
                currentRelativePath = parent
            } else {
                onBack()
            }
        } else {
            onBack()
        }
    }

    // SAIF AI Liquid Glass Theme Colors
    val bgDark = Color(0xFF070B14)
    val cardBg = Color(0xFF0F172A)
    val glassBorder = Color(0x3338BDF8)
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val purpleAccent = Color(0xFFA855F7)
    val cyanAccent = Color(0xFF00BCD4)
    val electricBlue = Color(0xFF3B82F6)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = bgDark,
        topBar = {
            // Liquid Glass Frosted Top Bar
            Surface(
                color = cardBg.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val defaultPath = when (category) {
                                FileManagerCategory.JAVA -> "app/src/main/java/${project.packageName.replace('.', '/')}"
                                FileManagerCategory.RESOURCES -> "app/src/main/res"
                                FileManagerCategory.ASSETS -> "app/src/main/assets"
                                FileManagerCategory.LIB -> "app/libs"
                                FileManagerCategory.JNI -> "app/src/main/jni"
                            }

                            if (currentRelativePath != defaultPath && currentRelativePath.contains("/")) {
                                val parent = currentRelativePath.substringBeforeLast('/', "")
                                if (parent.isNotBlank()) {
                                    currentRelativePath = parent
                                } else {
                                    onBack()
                                }
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Category Badge Icon
                        Surface(
                            shape = CircleShape,
                            color = electricBlue.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, electricBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when (category) {
                                    FileManagerCategory.JAVA -> JavaCoffeeIcon(size = 20.dp)
                                    FileManagerCategory.RESOURCES -> Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = Color(0xFFF97316),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    FileManagerCategory.ASSETS -> Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = null,
                                        tint = cyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    FileManagerCategory.LIB -> Icon(
                                        imageVector = Icons.Default.CollectionsBookmark,
                                        contentDescription = null,
                                        tint = purpleAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    FileManagerCategory.JNI -> Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifBlank { category.defaultTitle },
                                color = textLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentRelativePath,
                                color = textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Animated Liquid Glass Cyan/Blue FAB with glowing aura
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = cyanAccent,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .padding(16.dp)
                    .size(56.dp)
                    .shadow(elevation = 12.dp, shape = CircleShape, spotColor = cyanAccent)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create New File or Folder",
                    modifier = Modifier.size(28.dp),
                    tint = Color.Black
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = cyanAccent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (itemsList.isEmpty()) {
                // Liquid glass empty state card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cardBg,
                        border = BorderStroke(1.dp, glassBorder),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = cyanAccent.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Folder is empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = textLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the + button below to create files or folders.",
                        fontSize = 13.sp,
                        color = textMuted
                    )
                }
            } else {
                // List of files in Liquid Glass Cards
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(itemsList, key = { it.relativePath }) { item ->
                        FileManagerItemCard(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    currentRelativePath = item.relativePath
                                } else {
                                    onSelectFile(
                                        EditorFileItem(
                                            name = item.name,
                                            path = item.relativePath,
                                            isMain = item.name.startsWith("MainActivity")
                                        )
                                    )
                                }
                            },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }

            // ============================================================
            // LIQUID GLASS "CREATE NEW FILE AND FOLDER" MODAL DIALOG
            // ============================================================
            if (showCreateDialog) {
                CreateFileFolderModalDialog(
                    packageName = project.packageName,
                    onDismiss = { showCreateDialog = false },
                    onConfirm = { type, fileName ->
                        showCreateDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val targetDir = File(projectBaseDir, currentRelativePath)
                                if (!targetDir.exists()) targetDir.mkdirs()

                                when (type) {
                                    CreationType.FOLDER -> {
                                        val newFolder = File(targetDir, fileName.trim())
                                        newFolder.mkdirs()
                                    }
                                    CreationType.JAVA_CLASS -> {
                                        val finalName = if (fileName.endsWith(".java", ignoreCase = true)) {
                                            fileName
                                        } else {
                                            "$fileName.java"
                                        }
                                        val className = finalName.removeSuffix(".java")
                                        val cleanPkg = project.packageName.ifBlank { "com.example" }
                                        val classCode = """
                                            package $cleanPkg;

                                            public class $className {

                                            }
                                        """.trimIndent()

                                        val newFile = File(targetDir, finalName)
                                        newFile.writeText(classCode)
                                    }
                                    CreationType.JAVA_ACTIVITY -> {
                                        val finalName = if (fileName.endsWith(".java", ignoreCase = true)) {
                                            fileName
                                        } else {
                                            "$fileName.java"
                                        }
                                        val className = finalName.removeSuffix(".java")
                                        val cleanPkg = project.packageName.ifBlank { "com.example" }
                                        val activityCode = """
                                            package $cleanPkg;

                                            import android.app.Activity;
                                            import android.os.Bundle;

                                            public class $className extends Activity {

                                                @Override
                                                protected void onCreate(Bundle savedInstanceState) {
                                                    super.onCreate(savedInstanceState);
                                                }
                                            }
                                        """.trimIndent()

                                        val newFile = File(targetDir, finalName)
                                        newFile.writeText(activityCode)
                                    }
                                }

                                editorViewModel.refreshTree(context)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Saved $fileName", Toast.LENGTH_SHORT).show()
                                    reloadFiles()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }

            // Liquid Glass Delete Confirmation Dialog
            itemToDelete?.let { item ->
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    containerColor = cardBg,
                    titleContentColor = textLight,
                    textContentColor = textMuted,
                    title = { Text("Delete ${if (item.isDirectory) "Folder" else "File"}?") },
                    text = { Text("Are you sure you want to delete '${item.name}'? This cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val toDel = item
                                itemToDelete = null
                                coroutineScope.launch(Dispatchers.IO) {
                                    val target = File(projectBaseDir, toDel.relativePath)
                                    if (target.isDirectory) {
                                        target.deleteRecursively()
                                    } else {
                                        target.delete()
                                    }
                                    editorViewModel.refreshTree(context)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Deleted ${toDel.name}", Toast.LENGTH_SHORT).show()
                                        reloadFiles()
                                    }
                                }
                            }
                        ) {
                            Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToDelete = null }) {
                            Text("Cancel", color = textMuted)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Liquid Glass Card for File / Folder in File Manager list.
 */
@Composable
private fun FileManagerItemCard(
    item: ManagerItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = Color(0xFF0F172A)
    val glassBorder = Color(0x2238BDF8)
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with glowing rounded container
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (item.isDirectory) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Folder",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (item.name.endsWith(".java", ignoreCase = true) || item.name.endsWith(".kt", ignoreCase = true)) {
                        JavaCoffeeIcon(size = 24.dp)
                    } else if (item.name.endsWith(".xml", ignoreCase = true)) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "XML File",
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "File",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File/Folder Name and metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isDirectory) "Directory" else "${item.sizeBytes / 1024} KB",
                    fontSize = 11.5.sp,
                    color = textMuted
                )
            }

            // Delete Action Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Liquid Glass implementation of "Create New File and Folder" Dialog:
 * - Liquid Glass Frosted Card in Dark Theme
 * - Radio options: Folder, Java Class, Java Activity
 * - Glowing Input text field
 * - Cyan action buttons
 */
@Composable
private fun CreateFileFolderModalDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onConfirm: (CreationType, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(CreationType.FOLDER) }
    var inputName by remember { mutableStateOf("") }

    val cardBg = Color(0xFF0F172A)
    val glassBorder = Color(0x3338BDF8)
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val cyanAccent = Color(0xFF00BCD4)
    val electricBlue = Color(0xFF3B82F6)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, glassBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp), spotColor = cyanAccent)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cyanAccent.copy(alpha = 0.16f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = cyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Create New File & Folder",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textLight
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type Options Chips
                Column(modifier = Modifier.fillMaxWidth()) {
                    CreationType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) electricBlue.copy(alpha = 0.2f) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) electricBlue else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedType = type }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedType = type },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = cyanAccent,
                                        unselectedColor = textMuted
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = type.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) textLight else textMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name Input
                Text(
                    text = "Name:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    placeholder = {
                        Text(
                            text = when (selectedType) {
                                CreationType.FOLDER -> "e.g. models, utils, ui"
                                CreationType.JAVA_CLASS -> "e.g. MyHelper or MyHelper.java"
                                CreationType.JAVA_ACTIVITY -> "e.g. DetailActivity"
                            },
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textLight,
                        unfocusedTextColor = textLight,
                        focusedBorderColor = cyanAccent,
                        unfocusedBorderColor = Color(0xFF334155),
                        cursorColor = cyanAccent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = textMuted, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            if (inputName.isNotBlank()) {
                                onConfirm(selectedType, inputName.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                        shape = RoundedCornerShape(10.dp),
                        enabled = inputName.isNotBlank()
                    ) {
                        Text(
                            text = "Create",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
