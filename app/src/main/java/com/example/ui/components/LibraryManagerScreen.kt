package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Library Manager screen in SAIF AI Signature Liquid Glass Dark Theme.
 * Manages Built-in Libraries, External Libraries, and Advanced options,
 * with real synchronization to the project's app/build.gradle file on disk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryManagerScreen(
    project: ProjectData,
    editorViewModel: CodeEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sanitizedApp = remember(project.appName) {
        project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "DefaultProject" }
    }
    val projectDir = remember(sanitizedApp) {
        File(context.filesDir, "projects/$sanitizedApp")
    }

    androidx.activity.compose.BackHandler {
        onBack()
    }
    val gradleFile = remember(projectDir) {
        File(projectDir, "app/build.gradle")
    }

    val prefs = remember(sanitizedApp) {
        context.getSharedPreferences("ProjectLibs_$sanitizedApp", Context.MODE_PRIVATE)
    }

    // Switch States backed by SharedPreferences and build.gradle
    var appCompatEnabled by remember { mutableStateOf(prefs.getBoolean("app_compat", true)) }
    var material3Enabled by remember { mutableStateOf(prefs.getBoolean("material3", true)) }
    var firebaseEnabled by remember { mutableStateOf(prefs.getBoolean("firebase", false)) }
    var admobEnabled by remember { mutableStateOf(prefs.getBoolean("admob", false)) }
    var googleMapEnabled by remember { mutableStateOf(prefs.getBoolean("google_map", false)) }
    var excludeBuiltInEnabled by remember { mutableStateOf(prefs.getBoolean("exclude_builtin", false)) }

    // Dialogs for Local & Native library managers
    var showLocalLibDialog by remember { mutableStateOf(false) }
    var showNativeLibDialog by remember { mutableStateOf(false) }

    // Real synchronization with app/build.gradle on storage
    fun updateGradleDependencies(libKey: String, isEnabled: Boolean) {
        prefs.edit().putBoolean(libKey, isEnabled).apply()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (!gradleFile.exists()) {
                    gradleFile.parentFile?.mkdirs()
                    gradleFile.writeText(
                        """
                        plugins {
                            id 'com.android.application'
                        }

                        android {
                            namespace '${project.packageName}'
                            compileSdk ${project.targetSdk}

                            defaultConfig {
                                applicationId '${project.packageName}'
                                minSdk ${project.minSdk}
                                targetSdk ${project.targetSdk}
                                versionCode 1
                                versionName "1.0"
                            }
                        }

                        dependencies {
                            implementation 'androidx.appcompat:appcompat:1.6.1'
                            implementation 'com.google.android.material:material:1.11.0'
                        }
                        """.trimIndent()
                    )
                }

                var gradleContent = gradleFile.readText()

                val depMap = mapOf(
                    "app_compat" to listOf(
                        "implementation 'androidx.appcompat:appcompat:1.6.1'",
                        "implementation 'com.google.android.material:material:1.11.0'"
                    ),
                    "material3" to listOf(
                        "implementation 'androidx.compose.material3:material3:1.2.1'"
                    ),
                    "firebase" to listOf(
                        "implementation platform('com.google.firebase:firebase-bom:32.7.0')",
                        "implementation 'com.google.firebase:firebase-auth-ktx:22.3.1'",
                        "implementation 'com.google.firebase:firebase-firestore-ktx:24.10.1'"
                    ),
                    "admob" to listOf(
                        "implementation 'com.google.android.gms:play-services-ads:22.6.0'"
                    ),
                    "google_map" to listOf(
                        "implementation 'com.google.android.gms:play-services-maps:18.2.0'"
                    )
                )

                val targetLines = depMap[libKey] ?: emptyList()

                if (isEnabled) {
                    // Add dependencies if not already present
                    for (line in targetLines) {
                        if (!gradleContent.contains(line)) {
                            if (gradleContent.contains("dependencies {")) {
                                gradleContent = gradleContent.replace(
                                    "dependencies {",
                                    "dependencies {\n    $line"
                                )
                            } else {
                                gradleContent += "\n\ndependencies {\n    $line\n}"
                            }
                        }
                    }
                } else {
                    // Remove dependencies
                    for (line in targetLines) {
                        gradleContent = gradleContent.replace("    $line\n", "")
                        gradleContent = gradleContent.replace("$line\n", "")
                        gradleContent = gradleContent.replace(line, "")
                    }
                }

                gradleFile.writeText(gradleContent)
                withContext(Dispatchers.Main) {
                    editorViewModel.refreshTree(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // SAIF AI Liquid Glass Theme Colors
    val bgDark = Color(0xFF070B14)
    val cardBg = Color(0xFF0F172A)
    val glassBorder = Color(0x3338BDF8)
    val cardBorder = Color(0x2238BDF8)
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
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = CircleShape,
                            color = purpleAccent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, purpleAccent.copy(alpha = 0.35f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CollectionsBookmark,
                                    contentDescription = null,
                                    tint = purpleAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Library Manager",
                                color = textLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Project Dependencies • app/build.gradle",
                                color = textMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ============================================================
            // 1. BUILT-IN LIBRARIES SECTION
            // ============================================================
            item {
                Text(
                    text = "BUILT-IN LIBRARIES",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = purpleAccent,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LibraryToggleRow(
                            title = "AppCompat & Design",
                            description = "Standard Android UI components, ActionBars, and Material Widgets",
                            icon = Icons.Outlined.Layers,
                            iconTint = electricBlue,
                            checked = appCompatEnabled,
                            onCheckedChange = { checked ->
                                appCompatEnabled = checked
                                updateGradleDependencies("app_compat", checked)
                                Toast.makeText(
                                    context,
                                    if (checked) "AppCompat added to build.gradle" else "AppCompat removed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                        LibraryToggleRow(
                            title = "Material 3 (Compose)",
                            description = "Latest Jetpack Compose Material Design components",
                            icon = Icons.Outlined.Palette,
                            iconTint = purpleAccent,
                            checked = material3Enabled,
                            onCheckedChange = { checked ->
                                material3Enabled = checked
                                updateGradleDependencies("material3", checked)
                                Toast.makeText(
                                    context,
                                    if (checked) "Material 3 added to build.gradle" else "Material 3 removed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            // ============================================================
            // 2. EXTERNAL LIBRARIES SECTION
            // ============================================================
            item {
                Text(
                    text = "EXTERNAL LIBRARIES & SERVICES",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = cyanAccent,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LibraryToggleRow(
                            title = "Firebase Services",
                            description = "Authentication, Cloud Firestore, and real-time database SDK",
                            icon = Icons.Outlined.LocalFireDepartment,
                            iconTint = Color(0xFFF59E0B),
                            checked = firebaseEnabled,
                            onCheckedChange = { checked ->
                                firebaseEnabled = checked
                                updateGradleDependencies("firebase", checked)
                                Toast.makeText(
                                    context,
                                    if (checked) "Firebase SDK enabled" else "Firebase disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                        LibraryToggleRow(
                            title = "Google AdMob",
                            description = "Monetize your app with Google Mobile Ads (Banner, Interstitial)",
                            icon = Icons.Outlined.MonetizationOn,
                            iconTint = Color(0xFF10B981),
                            checked = admobEnabled,
                            onCheckedChange = { checked ->
                                admobEnabled = checked
                                updateGradleDependencies("admob", checked)
                                Toast.makeText(
                                    context,
                                    if (checked) "AdMob SDK enabled" else "AdMob disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                        LibraryToggleRow(
                            title = "Google Maps",
                            description = "Display interactive maps, markers, and location routes",
                            icon = Icons.Outlined.Map,
                            iconTint = Color(0xFF38BDF8),
                            checked = googleMapEnabled,
                            onCheckedChange = { checked ->
                                googleMapEnabled = checked
                                updateGradleDependencies("google_map", checked)
                                Toast.makeText(
                                    context,
                                    if (checked) "Google Maps SDK enabled" else "Google Maps disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            // ============================================================
            // 3. ADVANCED ARCHITECTURE SETTINGS
            // ============================================================
            item {
                Text(
                    text = "ADVANCED COMPILER & LOCAL LIBS",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LibraryToggleRow(
                            title = "Exclude Built-in Libraries",
                            description = "Allows using custom support libraries instead of studio defaults",
                            icon = Icons.Outlined.SettingsBackupRestore,
                            iconTint = Color(0xFFE11D48),
                            checked = excludeBuiltInEnabled,
                            onCheckedChange = { checked ->
                                excludeBuiltInEnabled = checked
                                prefs.edit().putBoolean("exclude_builtin", checked).apply()
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                        LibraryActionRow(
                            title = "Local Library (JAR / AAR)",
                            description = "Manage local .jar and .aar files placed inside app/libs",
                            icon = Icons.Outlined.FolderZip,
                            iconTint = Color(0xFF818CF8),
                            onClick = { showLocalLibDialog = true }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                        LibraryActionRow(
                            title = "Native Library (.SO)",
                            description = "Manage C/C++ native shared object libraries inside app/src/main/jni",
                            icon = Icons.Outlined.Memory,
                            iconTint = Color(0xFF10B981),
                            onClick = { showNativeLibDialog = true }
                        )
                    }
                }
            }
        }

        // ============================================================
        // DIALOG: LOCAL LIBRARY (.JAR / .AAR)
        // ============================================================
        if (showLocalLibDialog) {
            LocalLibsManagementModalDialog(
                projectDir = projectDir,
                onDismiss = { showLocalLibDialog = false }
            )
        }

        // ============================================================
        // DIALOG: NATIVE LIBRARY (.SO)
        // ============================================================
        if (showNativeLibDialog) {
            NativeLibsManagementModalDialog(
                projectDir = projectDir,
                onDismiss = { showNativeLibDialog = false }
            )
        }
    }
}

/**
 * Toggle Row for Libraries inside liquid glass card.
 */
@Composable
private fun LibraryToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val cyanAccent = Color(0xFF00BCD4)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = textLight
            )
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = textMuted,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = cyanAccent,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

/**
 * Action Row for Navigation items inside Library Manager.
 */
@Composable
private fun LibraryActionRow(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = textLight
            )
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = textMuted,
                lineHeight = 16.sp
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Liquid Glass Dialog for Local JAR / AAR management.
 */
@Composable
private fun LocalLibsManagementModalDialog(
    projectDir: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val libsDir = remember(projectDir) { File(projectDir, "app/libs").apply { mkdirs() } }
    var libFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshLibs() {
        libFiles = libsDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".aar")) }?.toList().orEmpty()
    }

    LaunchedEffect(Unit) {
        refreshLibs()
    }

    val cardBg = Color(0xFF0F172A)
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val cyanAccent = Color(0xFF00BCD4)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, Color(0x3338BDF8)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Local Libraries (app/libs)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Place compiled .jar or .aar dependencies here.",
                    fontSize = 12.sp,
                    color = textMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (libFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No JAR / AAR files found in app/libs",
                            fontSize = 12.5.sp,
                            color = textMuted
                        )
                    }
                } else {
                    libFiles.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderZip,
                                contentDescription = null,
                                tint = cyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = file.name,
                                fontSize = 13.5.sp,
                                color = textLight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Liquid Glass Dialog for Native (.SO) library management.
 */
@Composable
private fun NativeLibsManagementModalDialog(
    projectDir: File,
    onDismiss: () -> Unit
) {
    val jniDir = remember(projectDir) { File(projectDir, "app/src/main/jni").apply { mkdirs() } }
    var jniFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshJni() {
        jniFiles = jniDir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") }?.toList().orEmpty()
    }

    LaunchedEffect(Unit) {
        refreshJni()
    }

    val cardBg = Color(0xFF0F172A)
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val greenAccent = Color(0xFF10B981)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, Color(0x3310B981)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Native Libraries (.so)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "C/C++ shared object binaries inside app/src/main/jni.",
                    fontSize = 12.sp,
                    color = textMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (jniFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No .so files found in app/src/main/jni",
                            fontSize = 12.5.sp,
                            color = textMuted
                        )
                    }
                } else {
                    jniFiles.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = file.name,
                                fontSize = 13.5.sp,
                                color = textLight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = greenAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
