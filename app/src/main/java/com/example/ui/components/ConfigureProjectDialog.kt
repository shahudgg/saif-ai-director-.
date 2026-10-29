package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.ProjectDataManager
import java.util.Locale

data class LanguageOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val tag: String,
    val accentColor: Color
)

val availableLanguages = listOf(
    LanguageOption("java", "Java", "Standard Android, Core OOP & JVM", "DEFAULT", Color(0xFFF97316)),
    LanguageOption("kotlin", "Kotlin", "Modern Jetpack Compose & Coroutines", "POPULAR", Color(0xFF8B5CF6)),
    LanguageOption("python", "Python", "AI, Machine Learning & Scripts", "AI READY", Color(0xFF3B82F6)),
    LanguageOption("html", "HTML / Web", "HTML5, CSS3, JavaScript & Web Apps", "FRONTEND", Color(0xFFEAB308)),
    LanguageOption("cpp", "C / C++", "NDK High Performance & Native Libraries", "NATIVE", Color(0xFF06B6D4)),
    LanguageOption("javascript", "JavaScript", "Node.js, Express & Web Services", "DYNAMIC", Color(0xFFF59E0B)),
    LanguageOption("typescript", "TypeScript", "Type-Safe Full-Stack & Frontend", "TYPE-SAFE", Color(0xFF3B82F6)),
    LanguageOption("flutter", "Flutter / Dart", "Cross-Platform Mobile Applications", "CROSS-PLATFORM", Color(0xFF0284C7)),
    LanguageOption("rust", "Rust", "Memory-Safe Systems & High Performance", "SYSTEMS", Color(0xFFEF4444)),
    LanguageOption("go", "Go (Golang)", "Cloud Microservices & High Concurrency", "BACKEND", Color(0xFF10B981))
)

data class SdkOption(val apiLevel: Int, val name: String, val isRecommended: Boolean = false)

val minSdkOptions = listOf(
    SdkOption(21, "Android 5.0 (Lollipop)"),
    SdkOption(23, "Android 6.0 (Marshmallow)"),
    SdkOption(24, "Android 7.0 (Nougat) - Default", isRecommended = true),
    SdkOption(26, "Android 8.0 (Oreo)"),
    SdkOption(28, "Android 9.0 (Pie)"),
    SdkOption(30, "Android 11"),
    SdkOption(33, "Android 13"),
    SdkOption(34, "Android 14")
)

val targetSdkOptions = listOf(
    SdkOption(31, "Android 12"),
    SdkOption(33, "Android 13"),
    SdkOption(34, "Android 14 - Default", isRecommended = true),
    SdkOption(35, "Android 15 (Vanilla Ice Cream)"),
    SdkOption(36, "Android 16 (Preview)")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureProjectDialog(
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    initialProject: ProjectData? = null,
    isEditMode: Boolean = false,
    onCreateProject: (
        appName: String,
        packageName: String,
        minSdk: Int,
        targetSdk: Int,
        buildStudio: String,
        language: String,
        iconBitmap: Bitmap?
    ) -> Unit
) {
    val context = LocalContext.current

    // Dialog state
    var appName by remember(initialProject) { mutableStateOf(initialProject?.appName ?: "New Project") }
    var packageName by remember(initialProject) { mutableStateOf(initialProject?.packageName ?: "com.saifai.newproject") }
    var isPackageCustom by remember(initialProject) { mutableStateOf(initialProject != null) }
    var minSdk by remember(initialProject) { mutableIntStateOf(initialProject?.minSdk ?: 24) }
    var targetSdk by remember(initialProject) { mutableIntStateOf(initialProject?.targetSdk ?: 34) }
    var buildStudio by remember(initialProject) { mutableStateOf(initialProject?.buildStudio ?: "SAIF AI Studio") }
    var selectedLanguage by remember(initialProject) {
        mutableStateOf(
            availableLanguages.firstOrNull { it.name.equals(initialProject?.language, ignoreCase = true) || it.id.equals(initialProject?.language, ignoreCase = true) }
                ?: availableLanguages.first()
        )
    }
    var finalIconBitmap by remember(initialProject) {
        mutableStateOf<Bitmap?>(initialProject?.iconBitmap ?: initialProject?.appName?.let { ProjectDataManager.loadProjectIcon(context, it) })
    }

    // Navigation sub-sheets/dialogs
    var showIconSourceSheet by remember { mutableStateOf(false) }
    var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showMinSdkSheet by remember { mutableStateOf(false) }
    var showTargetSdkSheet by remember { mutableStateOf(false) }

    // Activity launchers for Image picking
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val decoded = decodeUriToBitmap(context, it)
            if (decoded != null) {
                editingBitmap = decoded
            }
        }
    }

    val fileManagerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val decoded = decodeUriToBitmap(context, it)
            if (decoded != null) {
                editingBitmap = decoded
            }
        }
    }

    // Colors & Glass Styling
    val dialogBg = if (isDarkTheme) Color(0xED0D121F) else Color(0xF4F8FAFC)
    val cardBg = if (isDarkTheme) Color(0x401E293B) else Color(0x80FFFFFF)
    val cardBorder = if (isDarkTheme) Color(0x33FFFFFF) else Color(0x1F000000)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val orangeAccent = Color(0xFFF97316)

    // Main Dialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Liquid Glass Surface Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.90f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { },
                shape = RoundedCornerShape(28.dp),
                color = dialogBg,
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (isDarkTheme) Color(0x44FFFFFF) else Color(0x44000000),
                            if (isDarkTheme) Color(0x11FFFFFF) else Color(0x11000000)
                        )
                    )
                ),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(orangeAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isEditMode) Icons.Outlined.Tune else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = orangeAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEditMode) "App Configuration" else "New Project",
                                    color = textPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isEditMode) "Update app name & custom icon" else "Configure project parameters",
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) Color(0x22FFFFFF) else Color(0x15000000))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable Options List
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. APP ICON PICKER OPTION
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showIconSourceSheet = true },
                            shape = RoundedCornerShape(20.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Icon Display Box
                                    Box(
                                        modifier = Modifier
                                            .size(62.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (finalIconBitmap != null) orangeAccent else cardBorder,
                                                RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (finalIconBitmap != null) {
                                            Image(
                                                bitmap = finalIconBitmap!!.asImageBitmap(),
                                                contentDescription = "Selected App Icon",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Android,
                                                contentDescription = "Default Icon",
                                                tint = orangeAccent,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }

                                        // Edit Badge Icon
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(orangeAccent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Change Icon",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = "App Icon",
                                            color = textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (finalIconBitmap != null) "Custom square icon set" else "Tap to choose from Gallery or Files",
                                            color = if (finalIconBitmap != null) orangeAccent else textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = orangeAccent.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, orangeAccent.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = if (finalIconBitmap != null) "Change" else "Choose",
                                        color = orangeAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // 2. APP NAME OPTION
                        GlassTextField(
                            label = "App Name",
                            value = appName,
                            onValueChange = { newName ->
                                appName = newName
                                if (!isPackageCustom) {
                                    val slug = newName.lowercase(Locale.ROOT)
                                        .replace(Regex("[^a-z0-9]"), "")
                                    packageName = "com.saifai.${if (slug.isEmpty()) "newproject" else slug}"
                                }
                            },
                            placeholder = "e.g. New Project",
                            leadingIcon = Icons.Outlined.Edit,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentColor = orangeAccent
                        )

                        // 3. PACKAGE NAME OPTION
                        Column {
                            GlassTextField(
                                label = "Package Name",
                                value = packageName,
                                onValueChange = {
                                    packageName = it
                                    isPackageCustom = true
                                },
                                placeholder = "e.g. com.saifai.app",
                                leadingIcon = Icons.Outlined.Code,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accentColor = orangeAccent,
                                trailingContent = {
                                    if (isPackageCustom) {
                                        TextButton(
                                            onClick = {
                                                isPackageCustom = false
                                                val slug = appName.lowercase(Locale.ROOT)
                                                    .replace(Regex("[^a-z0-9]"), "")
                                                packageName = "com.saifai.${if (slug.isEmpty()) "newproject" else slug}"
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Auto-sync",
                                                color = orangeAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            )
                            if (!isPackageCustom) {
                                Text(
                                    text = "Auto-derived from App Name",
                                    color = textSecondary.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp, top = 3.dp)
                                )
                            }
                        }

                        // 4. MIN SDK & TARGET SDK ROW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Min SDK Selector
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showMinSdkSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Minimum SDK",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "API $minSdk",
                                            color = textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Target SDK Selector
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showTargetSdkSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Target SDK",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "API $targetSdk",
                                            color = textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 5. BUILD STUDIO OPTION
                        GlassTextField(
                            label = "Build Studio",
                            value = buildStudio,
                            onValueChange = { buildStudio = it },
                            placeholder = "SAIF AI Studio",
                            leadingIcon = Icons.Outlined.Build,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentColor = orangeAccent
                        )

                        // 6. PROGRAMMING LANGUAGE SELECTOR OPTION
                        val canChangeLanguage = !isEditMode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (canChangeLanguage) Modifier.clickable { showLanguageSheet = true }
                                    else Modifier
                                ),
                            shape = RoundedCornerShape(16.dp),
                            color = if (canChangeLanguage) cardBg else cardBg.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(selectedLanguage.accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Terminal,
                                            contentDescription = null,
                                            tint = selectedLanguage.accentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Language",
                                            color = textSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = selectedLanguage.name,
                                            color = textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (canChangeLanguage) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = selectedLanguage.accentColor.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = selectedLanguage.tag,
                                                color = selectedLanguage.accentColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Select Language",
                                            tint = textSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0x22EF4444)
                                        ) {
                                            Text(
                                                text = "LOCKED",
                                                color = Color(0xFFEF4444),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!canChangeLanguage) {
                            Text(
                                text = "Coding language cannot be changed after project creation",
                                color = textSecondary.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ACTION BUTTON: CREATE OR SAVE PROJECT
                    Button(
                        onClick = {
                            onCreateProject(
                                appName,
                                packageName,
                                minSdk,
                                targetSdk,
                                buildStudio,
                                selectedLanguage.name,
                                finalIconBitmap
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = orangeAccent
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Save else Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isEditMode) "Save Changes" else "Create Project",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. APP ICON PICKER SOURCE BOTTOM SHEET (Gallery vs File Manager)
    if (showIconSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showIconSourceSheet = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select App Icon Source",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose an image to crop and set as your app icon",
                    color = textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Option A: Gallery
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showIconSourceSheet = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Photo Gallery",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Pick high quality image from photo library",
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option B: File Manager
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showIconSourceSheet = false
                            fileManagerLauncher.launch("image/*")
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(orangeAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "File Manager",
                                tint = orangeAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "File Manager",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Browse storage, downloads & device folders",
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (finalIconBitmap != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            finalIconBitmap = null
                            showIconSourceSheet = false
                        }
                    ) {
                        Text(
                            text = "Reset to Default Android Icon",
                            color = Color(0xFFEF4444),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // 2. EDIT PANEL: SQUARE CROP, ZOOM, PAN, ROTATE, MIRROR, CUT & TICK
    if (editingBitmap != null) {
        ImageCropEditPanel(
            bitmap = editingBitmap!!,
            onCropConfirmed = { cropped ->
                finalIconBitmap = cropped
                editingBitmap = null
            },
            onDismiss = {
                editingBitmap = null
            }
        )
    }

    // 3. PROGRAMMING LANGUAGE SELECTOR SHEET
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Language",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose the primary language for this project",
                    color = textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableLanguages.forEach { lang ->
                        val isSelected = lang.id == selectedLanguage.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedLanguage = lang
                                    showLanguageSheet = false
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) lang.accentColor.copy(alpha = 0.15f) else cardBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) lang.accentColor else cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(lang.accentColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang.name.take(2).uppercase(),
                                            color = lang.accentColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = lang.name,
                                            color = textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = lang.subtitle,
                                            color = textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = lang.accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 4. MIN SDK PICKER SHEET
    if (showMinSdkSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMinSdkSheet = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Minimum SDK",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    minSdkOptions.forEach { opt ->
                        val isSelected = opt.apiLevel == minSdk
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    minSdk = opt.apiLevel
                                    showMinSdkSheet = false
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) orangeAccent.copy(alpha = 0.15f) else cardBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) orangeAccent else cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "API ${opt.apiLevel}",
                                        color = textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = opt.name,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = orangeAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 5. TARGET SDK PICKER SHEET
    if (showTargetSdkSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTargetSdkSheet = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Target SDK",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    targetSdkOptions.forEach { opt ->
                        val isSelected = opt.apiLevel == targetSdk
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    targetSdk = opt.apiLevel
                                    showTargetSdkSheet = false
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) orangeAccent.copy(alpha = 0.15f) else cardBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) orangeAccent else cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "API ${opt.apiLevel}",
                                        color = textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = opt.name,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = orangeAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GlassTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    trailingContent: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = textSecondary.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = trailingContent,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = cardBg,
            unfocusedContainerColor = cardBg,
            focusedBorderColor = accentColor,
            unfocusedBorderColor = cardBorder,
            focusedLabelColor = accentColor,
            unfocusedLabelColor = textSecondary,
            focusedTextColor = textPrimary,
            unfocusedTextColor = textPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Safe URI to Bitmap decoder with downsampling to avoid out-of-memory errors on large camera images.
 */
fun decodeUriToBitmap(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        var w = origW
        var h = origH
        while (w > maxDim || h > maxDim) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
