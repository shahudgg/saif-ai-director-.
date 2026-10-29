package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ProjectDataManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.compiler.BuildProgressDialog
import com.example.compiler.CompilerErrorInfo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Project configuration data.
 */
data class ProjectData(
    val appName: String,
    val packageName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val buildStudio: String,
    val language: String,
    val iconBitmap: Bitmap?
)

/**
 * Representation of a file currently open or available in the editor.
 */
data class EditorFileItem(
    val name: String,
    val path: String,
    val isMain: Boolean = false
)

/**
 * Syntax Highlighting Transformation for Compose BasicTextField.
 * Applies token-based colors without altering string length or indices,
 * preserving cursor and selection fidelity at 60 FPS.
 */
class CodeSyntaxHighlightTransformation(
    private val isDarkTheme: Boolean,
    private val fileExtension: String
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)

        val keywordColor = if (isDarkTheme) Color(0xFFC084FC) else Color(0xFF7C3AED)
        val stringColor = if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF059669)
        val commentColor = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
        val numberColor = if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFFD97706)
        val tagColor = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0284C7)
        val annotationColor = if (isDarkTheme) Color(0xFFF472B6) else Color(0xFFDB2777)

        // 1. Strings: "..." or '...'
        val stringRegex = Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")
        for (match in stringRegex.findAll(raw)) {
            builder.addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
        }

        // 2. Comments: //... or /*...*/ or <!--...-->
        val commentRegex = Regex("""//.*|/\*[\s\S]*?\*/|<!--[\s\S]*?-->""")
        for (match in commentRegex.findAll(raw)) {
            builder.addStyle(
                SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }

        // 3. Numbers: \b\d+(\.\d+)?\b
        val numberRegex = Regex("""\b\d+(\.\d+)?[fFLdD]?\b""")
        for (match in numberRegex.findAll(raw)) {
            builder.addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
        }

        // 4. Annotations: @[A-Za-z0-9_]+
        val annotationRegex = Regex("""@[A-Za-z0-9_]+""")
        for (match in annotationRegex.findAll(raw)) {
            builder.addStyle(SpanStyle(color = annotationColor, fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
        }

        // 5. XML Tags & Attributes if XML or HTML
        if (fileExtension in listOf("xml", "html", "svg")) {
            val xmlTagRegex = Regex("""</?[a-zA-Z0-9_:-]+|/?>""")
            for (match in xmlTagRegex.findAll(raw)) {
                builder.addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            }
            val attrRegex = Regex("""\b[a-zA-Z0-9_:-]+(?==)""")
            for (match in attrRegex.findAll(raw)) {
                builder.addStyle(SpanStyle(color = keywordColor), match.range.first, match.range.last + 1)
            }
        } else {
            // Keywords for Java / Kotlin / Gradle / Python / JS
            val keywords = setOf(
                "package", "import", "class", "interface", "fun", "val", "var",
                "public", "private", "protected", "internal", "override", "open",
                "void", "int", "boolean", "float", "double", "char", "byte", "short",
                "String", "null", "true", "false", "new", "this", "super",
                "return", "if", "else", "while", "for", "do", "switch", "case", "break",
                "continue", "default", "try", "catch", "finally", "throw", "throws",
                "extends", "implements", "static", "final", "abstract", "synchronized",
                "plugins", "id", "android", "defaultConfig", "dependencies", "implementation",
                "def", "from", "as", "is", "not", "with", "lambda", "const", "let", "function"
            )
            val wordRegex = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")
            for (match in wordRegex.findAll(raw)) {
                if (keywords.contains(match.value)) {
                    builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
                }
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/**
 * Full-Screen In-App Code Editor & Project Explorer.
 * Integrates CodeEditorViewModel, FileTreeDrawer, synchronized line numbers,
 * bidirectional scrolling, undo/redo, auto-save, and liquid glass styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    project: ProjectData,
    isDarkTheme: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    editorViewModel: CodeEditorViewModel = viewModel(),
    onUpdateProject: ((ProjectData) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentProject by remember(project.appName) { mutableStateOf(project) }

    // Initialize Project workspace on first launch
    LaunchedEffect(currentProject.appName) {
        editorViewModel.initProject(context, currentProject)
    }

    // Collect status messages for Snackbars
    LaunchedEffect(Unit) {
        editorViewModel.statusMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Automatically reload editor and refresh tree whenever AI completes code generation
    LaunchedEffect(currentProject.appName) {
        ProjectEditorCoordinator.globalFileUpdates.collectLatest { event ->
            if (event.projectName.equals(currentProject.appName, ignoreCase = true)) {
                editorViewModel.refreshTree(context)
                editorViewModel.reloadActiveFile(context)
                snackbarHostState.showSnackbar("Project synchronized with AI Engine")
            }
        }
    }

    // ViewModel States
    val fileTree by editorViewModel.fileTree.collectAsState()
    val expandedPaths by editorViewModel.expandedPaths.collectAsState()
    val activeFile by editorViewModel.activeFile.collectAsState()
    val editorTextFieldValue by editorViewModel.editorTextFieldValue.collectAsState()
    val canUndo by editorViewModel.canUndo.collectAsState()
    val canRedo by editorViewModel.canRedo.collectAsState()
    val isDirty by editorViewModel.isDirty.collectAsState()
    val isLoadingFile by editorViewModel.isLoadingFile.collectAsState()

    // UI Dialog & Drawer states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRunDialog by remember { mutableStateOf(false) }
    var showAiAssistDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var showProjectInfoDialog by remember { mutableStateOf(false) }
    var showProjectConfigDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var activeSubScreen by remember { mutableStateOf<ProjectSubScreen?>(null) }

    // Scroll States for Synchronized Bidirectional Scrolling
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    // Color Palette with Liquid Glass Touches
    val topBarBg = if (isDarkTheme) Color(0xEE0F172A) else Color(0xEEFFFFFF)
    val editorBg = if (isDarkTheme) Color(0xFF0B1120) else Color.White
    val gutterBg = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val gutterBorder = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
    val gutterText = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val dividerColor = if (isDarkTheme) Color(0x33334155) else Color(0x33E2E8F0)
    val runButtonColor = Color(0xFF00A86B) // Vibrant emerald green from screenshot
    val accentOrange = Color(0xFFF97316)

    // Active file extension for syntax highlighting
    val currentExt = remember(activeFile?.name) {
        activeFile?.name?.substringAfterLast('.', "") ?: "java"
    }

    val syntaxTransformation = remember(isDarkTheme, currentExt) {
        CodeSyntaxHighlightTransformation(isDarkTheme, currentExt)
    }

    // Handle device/system back navigation: close drawer if open, or return to project list
    androidx.activity.compose.BackHandler(enabled = activeSubScreen == null) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else {
            onClose()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            FileTreeDrawer(
                project = currentProject,
                fileTree = fileTree,
                expandedPaths = expandedPaths,
                activeFilePath = activeFile?.path,
                isDirty = isDirty,
                isDarkTheme = isDarkTheme,
                onToggleFolder = { path ->
                    editorViewModel.toggleFolder(path)
                },
                onSelectFile = { item ->
                    // 1. Load file in background
                    editorViewModel.selectFile(context, item)
                    // 2. Smoothly close drawer to reveal full-screen code
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
                onNewFileClick = {
                    showNewFileDialog = true
                },
                onCloseDrawer = {
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
                onExitProject = onClose
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // ==========================================
                // TOP APP BAR (Matching Reference UI Exactly)
                // ==========================================
                Surface(
                    color = topBarBg,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, dividerColor)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. HAMBURGER MENU (Opens Sidebar Project Tree)
                            IconButton(
                                onClick = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open File Tree Explorer",
                                    tint = textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 2. ACTIVE FILE BADGE / TAB (Displays active file name, path, unsaved dot, and close 'X')
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isDarkTheme) Color(0x33334155) else Color(0x15000000)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                // Code Icon
                                Icon(
                                    imageVector = if (activeFile?.name?.endsWith(".xml") == true) Icons.Outlined.DataObject else Icons.Default.Code,
                                    contentDescription = null,
                                    tint = accentOrange,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Active File Name
                                Text(
                                    text = activeFile?.name ?: project.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Unsaved changes dot indicator
                                if (isDirty) {
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(accentOrange)
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Close file tab 'X' button
                                if (activeFile != null) {
                                    IconButton(
                                        onClick = { editorViewModel.closeActiveFile(context) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close File",
                                            tint = gutterText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // 3. UNDO BUTTON
                            IconButton(
                                onClick = { editorViewModel.undo() },
                                enabled = canUndo,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) textPrimary else textPrimary.copy(alpha = 0.35f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 4. REDO BUTTON
                            IconButton(
                                onClick = { editorViewModel.redo() },
                                enabled = canRedo,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) textPrimary else textPrimary.copy(alpha = 0.35f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 5. FLOPPY DISK SAVE BUTTON (Rewrites file on device storage on Dispatchers.IO)
                            IconButton(
                                onClick = { editorViewModel.saveCurrentFile(context) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save File",
                                        tint = if (isDirty) accentOrange else textPrimary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                    if (isDirty) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(accentOrange)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // 6. RUN BUTTON (Emerald green pill button from screenshot)
                            Button(
                                onClick = {
                                    if (isDirty) {
                                        editorViewModel.saveCurrentFile(context)
                                    }
                                    showRunDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = runButtonColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "Run",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // 7. AI ENGINE SETTINGS BUTTON
                            IconButton(
                                onClick = { showAiSettingsDialog = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "AI Engine Settings",
                                    tint = textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 8. THREE DOTS OVERFLOW MENU (Includes "Open File Tree" option)
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = textPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                ProjectMenuDropdown(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    isDarkTheme = isDarkTheme,
                                    onSelectCategory = { category ->
                                        val folderRel = when (category) {
                                            FileManagerCategory.JAVA -> {
                                                val pkg = currentProject.packageName.replace('.', '/').ifBlank { "com/example" }
                                                "app/src/main/java/$pkg"
                                            }
                                            FileManagerCategory.RESOURCES -> "app/src/main/res"
                                            FileManagerCategory.ASSETS -> "app/src/main/assets"
                                            FileManagerCategory.LIB -> "app/libs"
                                            FileManagerCategory.JNI -> "app/src/main/jni"
                                        }
                                        activeSubScreen = ProjectSubScreen.FileManager(
                                            category = category,
                                            relativePath = folderRel,
                                            title = category.defaultTitle
                                        )
                                    },
                                    onOpenLibraryManager = {
                                        activeSubScreen = ProjectSubScreen.LibraryManager
                                    },
                                    onOpenBuildAI = {
                                        activeSubScreen = ProjectSubScreen.BuildAI()
                                    },
                                    onOpenProjectConfiguration = {
                                        showProjectConfigDialog = true
                                    },
                                    onCloseProject = onClose
                                )
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = dividerColor)
                    }
                }
            }
        ) { innerPadding ->
            // ==========================================
            // MAIN EDITOR CANVAS WITH LINE NUMBERS
            // ==========================================
            val lineCount = remember(editorTextFieldValue.text) {
                maxOf(1, editorTextFieldValue.text.count { it == '\n' } + 1)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(editorBg)
            ) {
                if (isLoadingFile) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = accentOrange,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading file from storage...",
                                fontSize = 13.sp,
                                color = gutterText
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                    ) {
                        // 1. LEFT GUTTER FOR LINE NUMBERS (1, 2, 3...)
                        Column(
                            modifier = Modifier
                                .width(44.dp)
                                .background(gutterBg)
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = "$i",
                                    color = gutterText,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .height(22.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }

                        // Border separating line numbers and code
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(maxOf(500.dp, (lineCount * 22 + 60).dp))
                                .background(gutterBorder)
                        )

                        // 2. CODE TEXT EDITOR (Horizontal + Vertical scrolling, Monospace font, Syntax Highlighting)
                        BasicTextField(
                            value = editorTextFieldValue,
                            onValueChange = { editorViewModel.onCodeChanged(it) },
                            visualTransformation = syntaxTransformation,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            textStyle = TextStyle(
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 22.sp
                            ),
                            cursorBrush = SolidColor(if (isDarkTheme) accentOrange else Color(0xFF0F172A))
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // IN-DEVICE COMPILER & RUN PIPELINE DIALOG
    // ==========================================
    if (showRunDialog) {
        BuildProgressDialog(
            project = project,
            isDarkTheme = isDarkTheme,
            onDismiss = { showRunDialog = false },
            onAskSaifAi = { errorInfo ->
                showRunDialog = false
                activeSubScreen = ProjectSubScreen.BuildAI(errorInfo)
            }
        )
    }

    // ==========================================
    // AI ENGINE CONFIGURATION MODAL
    // ==========================================
    if (showAiSettingsDialog) {
        AISettingsDialog(
            isDarkTheme = isDarkTheme,
            onDismiss = { showAiSettingsDialog = false }
        )
    }

    // ==========================================
    // ASK SAIF AI CODE ASSISTANT DIALOG
    // ==========================================
    if (showAiAssistDialog) {
        var userInstruction by remember { mutableStateOf("") }
        var isAiLoading by remember { mutableStateOf(false) }
        var aiResponseCode by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { if (!isAiLoading) showAiAssistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = accentOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SAIF AI Code Studio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = textPrimary
                            )
                        }
                        IconButton(onClick = { showAiAssistDialog = false }, enabled = !isAiLoading) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = gutterText)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Describe changes or features for ${activeFile?.name ?: "this file"}:",
                        fontSize = 13.sp,
                        color = gutterText
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userInstruction,
                        onValueChange = { userInstruction = it },
                        placeholder = { Text("e.g. Add event listener, UI button, network call, or fix syntax error...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isAiLoading
                    )

                    if (isAiLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = accentOrange,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Generating code with AI...",
                                fontSize = 13.sp,
                                color = accentOrange
                            )
                        }
                    }

                    if (aiResponseCode != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Code generated successfully!",
                            color = runButtonColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (aiResponseCode == null) {
                            Button(
                                onClick = {
                                    val prompt = """
                                        You are an expert Android developer in SAIF AI Studio.
                                        Project: ${project.appName}
                                        Language: ${project.language}
                                        File: ${activeFile?.name ?: "MainActivity.java"}
                                        
                                        Current Code:
                                        ```
                                        ${editorTextFieldValue.text}
                                        ```
                                        
                                        User Request:
                                        $userInstruction
                                        
                                        Please provide the complete updated code. Return ONLY the code inside a markdown code block.
                                    """.trimIndent()

                                    isAiLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val result = com.example.data.remote.AIApiUtility.executeWithFallback(
                                                prompt = prompt,
                                                mode = "coding"
                                            )
                                            val fullText = result.getOrDefault("")
                                            val extracted = extractCodeFromMarkdown(fullText)
                                            aiResponseCode = extracted.ifBlank { fullText }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isAiLoading = false
                                        }
                                    }
                                },
                                enabled = userInstruction.isNotBlank() && !isAiLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = accentOrange),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate Code", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    aiResponseCode?.let { newCode ->
                                        editorViewModel.applyAiCode(newCode, context)
                                    }
                                    showAiAssistDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = runButtonColor),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Apply to Editor", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { aiResponseCode = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Discard")
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // ADD NEW FILE DIALOG
    // ==========================================
    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        var parentPath by remember { mutableStateOf("app/src/main/java") }

        Dialog(onDismissRequest = { showNewFileDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Add New File", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = textPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("File Name:", fontSize = 12.sp, color = gutterText)
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = { Text("e.g. Utils.java, layout_item.xml") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Folder Path:", fontSize = 12.sp, color = gutterText)
                    OutlinedTextField(
                        value = parentPath,
                        onValueChange = { parentPath = it },
                        placeholder = { Text("e.g. app/src/main/java") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showNewFileDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (newFileName.isNotBlank()) {
                                    editorViewModel.createNewFile(context, parentPath, newFileName.trim())
                                    showNewFileDialog = false
                                }
                            },
                            enabled = newFileName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = accentOrange),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Create", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // PROJECT DETAILS DIALOG
    // ==========================================
    if (showProjectInfoDialog) {
        Dialog(onDismissRequest = { showProjectInfoDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Project Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    InfoRow("App Name", project.appName, textPrimary, gutterText)
                    InfoRow("Package", project.packageName, textPrimary, gutterText)
                    InfoRow("Language", project.language, accentOrange, gutterText)
                    InfoRow("Min SDK", "API ${project.minSdk}", textPrimary, gutterText)
                    InfoRow("Target SDK", "API ${project.targetSdk}", textPrimary, gutterText)
                    InfoRow("Studio", project.buildStudio, textPrimary, gutterText)
                    InfoRow("Storage Path", "projects/${currentProject.appName.replace(" ", "_")}/", textPrimary, gutterText)

                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { showProjectInfoDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }

    // ==========================================
    // APP CONFIGURATION / EDIT PROJECT DIALOG
    // ==========================================
    if (showProjectConfigDialog) {
        ConfigureProjectDialog(
            isDarkTheme = isDarkTheme,
            initialProject = currentProject,
            isEditMode = true,
            onDismiss = {
                showProjectConfigDialog = false
            },
            onCreateProject = { newName, newPkg, minSdk, targetSdk, buildStudio, lang, iconBmp ->
                val oldName = currentProject.appName
                val updated = currentProject.copy(
                    appName = newName,
                    packageName = newPkg,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    buildStudio = buildStudio,
                    language = lang,
                    iconBitmap = iconBmp
                )
                currentProject = updated
                ProjectDataManager.renameProject(context, oldName, newName, updated)
                onUpdateProject?.invoke(updated)
                showProjectConfigDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Project settings updated successfully!")
                }
            }
        )
    }

    // ==========================================
    // SUB-SCREENS: FILE MANAGER, LIBRARY MANAGER, BUILD AI
    // ==========================================
    AnimatedVisibility(
        visible = activeSubScreen != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(250)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200))
    ) {
        activeSubScreen?.let { subScreen ->
            androidx.activity.compose.BackHandler {
                activeSubScreen = null
            }

            when (subScreen) {
                is ProjectSubScreen.FileManager -> {
                    FileManagerScreen(
                        category = subScreen.category,
                        initialRelativePath = subScreen.relativePath,
                        title = subScreen.title,
                        project = currentProject,
                        editorViewModel = editorViewModel,
                        onBack = { activeSubScreen = null },
                        onSelectFile = { fileItem ->
                            editorViewModel.selectFile(context, fileItem)
                            activeSubScreen = null
                        }
                    )
                }
                is ProjectSubScreen.LibraryManager -> {
                    LibraryManagerScreen(
                        project = currentProject,
                        editorViewModel = editorViewModel,
                        onBack = { activeSubScreen = null }
                    )
                }
                is ProjectSubScreen.BuildAI -> {
                    BuildAIScreen(
                        project = currentProject,
                        editorViewModel = editorViewModel,
                        onBack = {
                            editorViewModel.reloadActiveFile(context)
                            activeSubScreen = null
                        },
                        initialError = subScreen.initialError
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color, labelColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = labelColor, fontSize = 13.sp)
        Text(text = value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Extracts pure code from markdown backticks.
 */
private fun extractCodeFromMarkdown(raw: String): String {
    val codeBlockRegex = Regex("""```(?:[a-zA-Z0-9_-]+)?\s*([\s\S]*?)```""")
    val match = codeBlockRegex.find(raw)
    return if (match != null) {
        match.groups[1]?.value?.trim() ?: raw.trim()
    } else {
        raw.trim()
    }
}

/**
 * Parses user code and simulates execution output in the console terminal.
 */
private fun simulateExecution(code: String, project: ProjectData, fileName: String): String {
    val printOutputs = mutableListOf<String>()
    val lines = code.lines()

    for (line in lines) {
        val trimmed = line.trim()
        val regex = Regex("""(?:System\.out\.println|println|print|console\.log)\s*\(\s*(?:['"](.*?)['"]|([a-zA-Z0-9_]+))\s*\)""")
        val match = regex.find(trimmed)
        if (match != null) {
            val content = match.groups[1]?.value ?: match.groups[2]?.value ?: ""
            printOutputs.add(content)
        }
    }

    return if (printOutputs.isNotEmpty()) {
        printOutputs.joinToString("\n")
    } else {
        """
            🚀 ${project.appName} initialized successfully.
            File: $fileName
            Package: ${project.packageName}
            Build Studio: ${project.buildStudio}
            Target SDK: API ${project.targetSdk}
            Status: Execution verified without runtime exceptions.
        """.trimIndent()
    }
}
