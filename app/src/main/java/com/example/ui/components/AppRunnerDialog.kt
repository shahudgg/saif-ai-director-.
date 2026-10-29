package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.compiler.AppCompilerEngine
import kotlinx.coroutines.delay
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppRunnerDialog(
    project: ProjectData,
    isDarkTheme: Boolean = true,
    onBuildApk: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sanitizedAppName = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
    val projectDir = remember(project.appName) { File(context.filesDir, "projects/$sanitizedAppName") }

    var reloadCount by remember { mutableStateOf(1) }

    LaunchedEffect(projectDir) {
        reloadCount++
    }

    // Detect if the project contains an Android XML layout file
    val hasXmlLayout = remember(projectDir, reloadCount) {
        val layoutDir = File(projectDir, "app/src/main/res/layout")
        layoutDir.exists() && layoutDir.listFiles()?.any { it.name.endsWith(".xml") } == true
    }

    val hasHtml = remember(projectDir, reloadCount) {
        val html1 = File(projectDir, "index.html")
        val html2 = File(projectDir, "app/src/main/assets/index.html")
        (html1.exists() && html1.length() > 50) || (html2.exists() && html2.length() > 50)
    }

    var activeMode by remember(project.appName) {
        mutableStateOf("engine")
    }

    var currentTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    var activeWebView by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            // Realistic Smartphone Bezel Outer Container with Hardware Side Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.96f)
            ) {
                // Left Hardware Volume Rocker Buttons on Device Frame
                Column(
                    modifier = Modifier.padding(end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF334155))))
                    )
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF334155))))
                    )
                }

                // Phone Titanium Chassis Surface
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(36.dp, RoundedCornerShape(42.dp)),
                    shape = RoundedCornerShape(42.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(
                        3.5.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF94A3B8),
                                Color(0xFF38BDF8).copy(alpha = 0.8f),
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(39.dp))
                            .background(Color(0xFF050811))
                    ) {
                        // 1. Realistic Android Flagship Status Bar
                        PhoneStatusBar(
                            currentTime = currentTime,
                            appName = project.appName
                        )

                        // 2. Google AI Studio Companion Control Bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0D1424),
                            border = BorderStroke(0.5.dp, Color(0xFF1E293B))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // App identity & Internet ready badge
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = project.appName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Column {
                                        Text(
                                            text = project.appName,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Online • Internet Live",
                                                color = Color(0xFF10B981),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Quick Studio Actions: Reload, Build APK, Export ZIP, Close
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Refresh / Restart button
                                    IconButton(
                                        onClick = {
                                            reloadCount++
                                            activeWebView?.reload()
                                            Toast.makeText(context, "Preview reloaded", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reload Preview",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    // Build standalone APK
                                    if (onBuildApk != null) {
                                        IconButton(
                                            onClick = onBuildApk,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Build,
                                                contentDescription = "Build APK",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Export ZIP
                                    IconButton(
                                        onClick = {
                                            val zipFile = AppCompilerEngine.exportProjectAsZip(context, project)
                                            if (zipFile != null && zipFile.exists()) {
                                                try {
                                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/zip"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        putExtra(Intent.EXTRA_SUBJECT, "${project.appName} Android Studio Project")
                                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Export Android Project (.zip)"))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Saved to ${zipFile.name}", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Could not create zip export", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Export ZIP",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Close
                                    IconButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Mode Switcher Pill (if project has XML layout)
                        if (hasXmlLayout) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF0F172A)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (activeMode == "engine") Color(0xFF10B981) else Color(0xFF1E293B),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { activeMode = "engine" }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 5.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (activeMode == "engine") Color.White else Color(0xFF94A3B8),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Live App Simulator",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeMode == "engine") Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (activeMode == "layout") Color(0xFF38BDF8) else Color(0xFF1E293B),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { activeMode = "layout" }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 5.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Dashboard,
                                                contentDescription = null,
                                                tint = if (activeMode == "layout") Color(0xFF0B0F19) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "XML Layout",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeMode == "layout") Color(0xFF0B0F19) else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Active Running App Screen Viewport
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF111827))
                        ) {
                            if (activeMode == "layout") {
                                LiveInteractiveLayoutView(projectDir = projectDir, project = project)
                            } else {
                                val indexHtml = File(projectDir, "index.html")
                                val appIndexHtml = File(projectDir, "app/src/main/assets/index.html")
                                val realHtmlExists = (indexHtml.exists() && indexHtml.length() > 50) || (appIndexHtml.exists() && appIndexHtml.length() > 50)
                                if (realHtmlExists) {
                                    LiveWebViewApp(
                                        projectDir = projectDir,
                                        reloadKey = reloadCount,
                                        onWebViewCreated = { activeWebView = it }
                                    )
                                } else {
                                    NativeAndroidReadyView(
                                        project = project,
                                        projectDir = projectDir,
                                        onBuildApk = onBuildApk,
                                        onSwitchToLayout = { activeMode = "layout" }
                                    )
                                }
                            }
                        }

                        // 4. Modern Android 3-Button & Gesture Navigation Bar
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            color = Color(0xFF070B14)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back Button ◀
                                IconButton(
                                    onClick = {
                                        if (activeWebView?.canGoBack() == true) {
                                            activeWebView?.goBack()
                                        } else {
                                            Toast.makeText(context, "Home screen", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBackIosNew,
                                        contentDescription = "Back",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Center Home Gesture Pill Bar
                                Box(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(4.5.dp)
                                        .clip(RoundedCornerShape(2.5.dp))
                                        .background(Color(0xFFCBD5E1).copy(alpha = 0.7f))
                                        .clickable {
                                            activeWebView?.reload()
                                        }
                                )

                                // Recents / Apps Button ▢
                                IconButton(
                                    onClick = {
                                        reloadCount++
                                        activeWebView?.reload()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Square,
                                        contentDescription = "Recents / Refresh",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Hardware Power Button on Device Frame
                Column(
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF334155))))
                    )
                }
            }
        }
    }
}

/**
 * Top Status Bar resembling a modern flagship smartphone with camera punch-hole,
 * 5G signal, WiFi, and battery percentage.
 */
@Composable
private fun PhoneStatusBar(
    currentTime: String,
    appName: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF070B14))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Time & 5G Carrier Badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currentTime,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                border = BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
            ) {
                Text(
                    text = "5G",
                    color = Color(0xFF38BDF8),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // Center: Dynamic Island / Punch-hole with camera reflection
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF030712))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Front Camera Lens with circular glass reflection
                Box(
                    modifier = Modifier
                        .size(7.5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.6f))
                    )
                }
                // Ambient Sensor dot
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.7f))
                )
            }
        }

        // Right: Signal bars, WiFi, Battery percent & icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SignalCellularAlt,
                contentDescription = "Cellular Signal",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "WiFi Online",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "99%",
                color = Color(0xFFE2E8F0),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = "Battery Full",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Live interactive Web View renderer for projects containing index.html with full internet support.
 */
@Composable
private fun LiveWebViewApp(
    projectDir: File,
    reloadKey: Int = 0,
    onWebViewCreated: ((WebView) -> Unit)? = null
) {
    val indexHtml = File(projectDir, "index.html")
    val appIndexHtml = File(projectDir, "app/src/main/assets/index.html")
    val targetFile = if (indexHtml.exists()) indexHtml else appIndexHtml

    if (!targetFile.exists()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("index.html not found in project", color = Color(0xFF94A3B8))
        }
        return
    }

    // Ensure no residual auth overlay blocks the user's interactive preview
    try {
        val rawHtml = targetFile.readText()
        if (rawHtml.contains("saif-auth-overlay") || rawHtml.contains("saif_app_auth_session")) {
            val cleanedHtml = com.example.compiler.AppHtmlSynthesizer.stripAuthGate(rawHtml)
            targetFile.writeText(cleanedHtml)
        }
    } catch (ignored: Exception) {}

    var internalWebView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(reloadKey) {
        if (reloadKey > 0) {
            internalWebView?.reload()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setGeolocationEnabled(true)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                        result?.confirm()
                        return true
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        android.util.Log.d("AppRunnerWebView", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                        return true
                    }
                }
                loadUrl("file://${targetFile.absolutePath}")
                internalWebView = this
                onWebViewCreated?.invoke(this)
            }
        },
        update = { wv ->
            internalWebView = wv
            onWebViewCreated?.invoke(wv)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun NativeAndroidReadyView(
    project: ProjectData,
    projectDir: File,
    onBuildApk: (() -> Unit)? = null,
    onSwitchToLayout: () -> Unit
) {
    val activityXml = remember(projectDir) {
        val xml1 = File(projectDir, "app/src/main/res/layout/activity_main.xml")
        val xml2 = File(projectDir, "app/src/main/res/layout/main.xml")
        if (xml1.exists()) xml1 else if (xml2.exists()) xml2 else null
    }
    val javaMain = remember(projectDir) {
        projectDir.walkTopDown().firstOrNull {
            it.isFile && (it.name.endsWith(".java") || it.name.endsWith(".kt")) && !it.name.contains("R.java")
        }
    }
    val manifestFile = remember(projectDir) {
        File(projectDir, "app/src/main/AndroidManifest.xml")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Futuristic Status Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF10B981).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Native Android Code Ready",
                    color = Color(0xFF10B981),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // App Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = project.appName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = project.packageName,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "Target API ${project.targetSdk} • ${project.language}",
                            fontSize = 10.5.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Files Breakdown Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0B101D),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACTIVE NATIVE FILES ON DISK",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF94A3B8)
                )

                // Layout File
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Widgets, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activityXml?.name ?: "activity_main.xml",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    val lines = activityXml?.readLines()?.size ?: 0
                    Text(
                        text = if (activityXml?.exists() == true) "$lines lines" else "Pending",
                        fontSize = 11.sp,
                        color = if (activityXml?.exists() == true) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }

                // Java / Kotlin File
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = javaMain?.name ?: "MainActivity.java",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    val lines = javaMain?.readLines()?.size ?: 0
                    Text(
                        text = if (javaMain?.exists() == true) "$lines lines" else "Pending",
                        fontSize = 11.sp,
                        color = if (javaMain?.exists() == true) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }

                // AndroidManifest.xml
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AndroidManifest.xml",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = if (manifestFile.exists()) "Configured" else "Pending",
                        fontSize = 11.sp,
                        color = if (manifestFile.exists()) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }
        }

        // Informational Notice
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dynamic Android code generation is decoupled from live preview. Tap below to compile the genuine standalone debug APK and test live on Android.",
                    fontSize = 11.5.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 16.sp
                )
            }
        }

        // Big Green Run APK button
        Button(
            onClick = { onBuildApk?.invoke() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "⚡ Compile & Run Debug APK",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Inspect Layout XML button
        OutlinedButton(
            onClick = onSwitchToLayout,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dashboard,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Inspect XML Layout",
                color = Color(0xFF38BDF8),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatStopwatchTime(ms: Long): String {
    val secs = ms / 1000
    val mins = secs / 60
    val remSecs = secs % 60
    val tenths = (ms % 1000) / 100
    return String.format(Locale.getDefault(), "%02d:%02d.%d", mins, remSecs, tenths)
}

data class ParsedViewNode(
    val tag: String,
    val id: String = "",
    val text: String = "",
    val hint: String = "",
    val textColor: String = "",
    val textSize: String = "",
    val background: String = "",
    val orientation: String = "vertical",
    val layoutWeight: Float = 0f,
    val children: List<ParsedViewNode> = emptyList()
)

private fun parseLayoutXml(file: File, stringsMap: Map<String, String>): ParsedViewNode {
    if (!file.exists()) return ParsedViewNode(tag = "LinearLayout")
    return try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(file.reader())
        parseXmlNodeRecursive(parser, stringsMap) ?: ParsedViewNode(tag = "LinearLayout")
    } catch (e: Exception) {
        ParsedViewNode(tag = "LinearLayout")
    }
}

private fun parseXmlNodeRecursive(parser: XmlPullParser, stringsMap: Map<String, String>): ParsedViewNode? {
    var eventType = parser.eventType
    while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
        eventType = parser.next()
    }
    if (eventType == XmlPullParser.END_DOCUMENT) return null

    val rawTag = parser.name ?: "View"
    val tag = rawTag.substringAfterLast('.')

    var id = ""
    var text = ""
    var hint = ""
    var textColor = ""
    var textSize = ""
    var background = ""
    var orientation = "vertical"
    var layoutWeight = 0f

    for (i in 0 until parser.attributeCount) {
        val attrName = parser.getAttributeName(i) ?: ""
        val attrVal = parser.getAttributeValue(i) ?: ""
        when (attrName.lowercase()) {
            "id" -> id = attrVal.substringAfterLast('/')
            "text" -> {
                text = if (attrVal.startsWith("@string/")) {
                    val key = attrVal.removePrefix("@string/")
                    stringsMap[key] ?: key
                } else attrVal
            }
            "hint" -> {
                hint = if (attrVal.startsWith("@string/")) {
                    val key = attrVal.removePrefix("@string/")
                    stringsMap[key] ?: key
                } else attrVal
            }
            "textcolor" -> textColor = attrVal
            "textsize" -> textSize = attrVal
            "background" -> background = attrVal
            "orientation" -> orientation = attrVal
            "layout_weight" -> layoutWeight = attrVal.toFloatOrNull() ?: 0f
        }
    }

    val children = mutableListOf<ParsedViewNode>()
    eventType = parser.next()
    while (eventType != XmlPullParser.END_TAG && eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            val child = parseXmlNodeRecursive(parser, stringsMap)
            if (child != null) children.add(child)
        } else {
            eventType = parser.next()
        }
    }

    return ParsedViewNode(
        tag = tag,
        id = id,
        text = text,
        hint = hint,
        textColor = textColor,
        textSize = textSize,
        background = background,
        orientation = orientation,
        layoutWeight = layoutWeight,
        children = children
    )
}

private fun evaluateArithmeticExpression(expr: String): String {
    val clean = expr.trim()
        .replace("×", "*")
        .replace("÷", "/")
        .replace(" ", "")
    if (clean.isBlank()) return "0"

    return try {
        val tokens = mutableListOf<String>()
        var currentNum = StringBuilder()
        for (i in clean.indices) {
            val c = clean[i]
            if (c.isDigit() || c == '.') {
                currentNum.append(c)
            } else if (c in listOf('+', '-', '*', '/', '%')) {
                if (currentNum.isNotEmpty()) {
                    tokens.add(currentNum.toString())
                    currentNum = StringBuilder()
                } else if (c == '-' && (tokens.isEmpty() || tokens.last() in listOf("+", "-", "*", "/", "%"))) {
                    currentNum.append(c)
                    continue
                }
                tokens.add(c.toString())
            }
        }
        if (currentNum.isNotEmpty()) tokens.add(currentNum.toString())

        if (tokens.isEmpty()) return "0"

        val pass1 = mutableListOf<String>()
        var idx = 0
        while (idx < tokens.size) {
            val token = tokens[idx]
            if (token in listOf("*", "/", "%") && pass1.isNotEmpty() && idx + 1 < tokens.size) {
                val left = pass1.removeAt(pass1.size - 1).toDoubleOrNull() ?: 0.0
                val right = tokens[idx + 1].toDoubleOrNull() ?: 1.0
                val res = when (token) {
                    "*" -> left * right
                    "/" -> if (right != 0.0) left / right else 0.0
                    else -> left % right
                }
                pass1.add(res.toString())
                idx += 2
            } else {
                pass1.add(token)
                idx++
            }
        }

        var result = pass1.firstOrNull()?.toDoubleOrNull() ?: 0.0
        var pIdx = 1
        while (pIdx < pass1.size) {
            val op = pass1[pIdx]
            val nextVal = pass1.getOrNull(pIdx + 1)?.toDoubleOrNull() ?: 0.0
            if (op == "+") result += nextVal
            if (op == "-") result -= nextVal
            pIdx += 2
        }

        if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            DecimalFormat("#.######").format(result)
        }
    } catch (e: Exception) {
        "Error"
    }
}

/**
 * Fully interactive layout visualizer that parses Android XML layout, renders responsive
 * UI components, and supports real typing, dynamic lists, counter tallies, and toasts.
 */
@Composable
private fun LiveInteractiveLayoutView(projectDir: File, project: ProjectData) {
    val mainXml = File(projectDir, "app/src/main/res/layout/main.xml")
    val activityMainXml = File(projectDir, "app/src/main/res/layout/activity_main.xml")
    val targetXml = remember(projectDir) {
        val javaMain = projectDir.walkTopDown().firstOrNull { it.name == "MainActivity.java" }
        val javaText = javaMain?.readText() ?: ""
        when {
            javaText.contains("R.layout.main") && mainXml.exists() -> mainXml
            javaText.contains("R.layout.activity_main") && activityMainXml.exists() -> activityMainXml
            mainXml.exists() -> mainXml
            activityMainXml.exists() -> activityMainXml
            else -> projectDir.walkTopDown().firstOrNull { it.isFile && it.extension == "xml" && it.parentFile?.name == "layout" } ?: mainXml
        }
    }

    val stringsFile = File(projectDir, "app/src/main/res/values/strings.xml")
    val stringsMap = remember(projectDir) {
        if (stringsFile.exists()) {
            val strRegex = Regex("""<string\s+name="([^"]+)">([^<]*)</string>""")
            strRegex.findAll(stringsFile.readText()).associate { it.groupValues[1] to it.groupValues[2] }
        } else emptyMap()
    }

    val context = LocalContext.current
    val rootNode = remember(targetXml, targetXml.lastModified(), targetXml.length()) {
        parseLayoutXml(targetXml, stringsMap)
    }

    // Interactive States
    val viewStates = remember { mutableStateMapOf<String, String>() }
    val checkStates = remember { mutableStateMapOf<String, Boolean>() }
    var counterState by remember { mutableStateOf(0) }
    var calcExpression by remember { mutableStateOf("") }
    var calcResult by remember { mutableStateOf("0") }
    var stopwatchMs by remember { mutableStateOf(0L) }
    var isStopwatchRunning by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var quizScore by remember { mutableStateOf(0) }
    var runningTotal by remember { mutableStateOf(0.0) }
    val dynamicList = remember { mutableStateListOf<String>() }
    var activeToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isStopwatchRunning) {
        if (isStopwatchRunning) {
            val startSystem = System.currentTimeMillis() - stopwatchMs
            while (isStopwatchRunning) {
                stopwatchMs = System.currentTimeMillis() - startSystem
                kotlinx.coroutines.delay(50)
            }
        }
    }

    val cameraManager = remember {
        try {
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isFlashOn) {
                try {
                    val id = cameraManager?.cameraIdList?.firstOrNull()
                    if (id != null) cameraManager.setTorchMode(id, false)
                } catch (e: Exception) {}
            }
        }
    }

    val toggleFlash = {
        isFlashOn = !isFlashOn
        try {
            val id = cameraManager?.cameraIdList?.firstOrNull()
            if (id != null) {
                cameraManager.setTorchMode(id, isFlashOn)
            }
        } catch (e: Exception) {}
        activeToast = if (isFlashOn) "⚡ Flashlight ON" else "Flashlight OFF"
    }

    LaunchedEffect(activeToast) {
        if (activeToast != null) {
            kotlinx.coroutines.delay(2400)
            activeToast = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Bar / Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Text(
                            text = "LIVE XML",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (rootNode != null) {
                RenderLayoutNode(
                    node = rootNode,
                    viewStates = viewStates,
                    checkStates = checkStates,
                    counterState = counterState,
                    onCounterChange = { counterState = it },
                    calcExpression = calcExpression,
                    onCalcExpressionChange = { calcExpression = it },
                    calcResult = calcResult,
                    onCalcResultChange = { calcResult = it },
                    stopwatchMs = stopwatchMs,
                    isStopwatchRunning = isStopwatchRunning,
                    onToggleStopwatch = { isStopwatchRunning = it },
                    onResetStopwatch = {
                        isStopwatchRunning = false
                        stopwatchMs = 0L
                    },
                    onToggleFlash = toggleFlash,
                    dynamicList = dynamicList,
                    onShowToast = { activeToast = it }
                )
            } else {
                Text(
                    text = "Welcome to ${project.appName}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // If user added items via interactive Add button, render the live dynamic list below
            if (dynamicList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "ITEMS LIST (${dynamicList.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
                dynamicList.forEachIndexed { index, item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. $item",
                                color = Color(0xFFF1F5F9),
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { dynamicList.remove(item); activeToast = "Removed: $item" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Floating On-Screen Toast Pill
        AnimatedVisibility(
            visible = activeToast != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeToast ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF8FAFC)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderLayoutNode(
    node: ParsedViewNode,
    viewStates: SnapshotStateMap<String, String>,
    checkStates: SnapshotStateMap<String, Boolean>,
    counterState: Int,
    onCounterChange: (Int) -> Unit,
    calcExpression: String,
    onCalcExpressionChange: (String) -> Unit,
    calcResult: String,
    onCalcResultChange: (String) -> Unit,
    stopwatchMs: Long,
    isStopwatchRunning: Boolean,
    onToggleStopwatch: (Boolean) -> Unit,
    onResetStopwatch: () -> Unit,
    onToggleFlash: () -> Unit,
    dynamicList: SnapshotStateList<String>,
    onShowToast: (String) -> Unit
) {
    when (node.tag) {
        "LinearLayout", "RelativeLayout", "FrameLayout", "ScrollView", "ConstraintLayout", "ViewGroup" -> {
            if (node.orientation.equals("horizontal", ignoreCase = true)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    node.children.forEach { child ->
                        val childModifier = if (child.layoutWeight > 0f) Modifier.weight(child.layoutWeight) else Modifier
                        Box(modifier = childModifier) {
                            RenderLayoutNode(
                                node = child,
                                viewStates = viewStates,
                                checkStates = checkStates,
                                counterState = counterState,
                                onCounterChange = onCounterChange,
                                calcExpression = calcExpression,
                                onCalcExpressionChange = onCalcExpressionChange,
                                calcResult = calcResult,
                                onCalcResultChange = onCalcResultChange,
                                stopwatchMs = stopwatchMs,
                                isStopwatchRunning = isStopwatchRunning,
                                onToggleStopwatch = onToggleStopwatch,
                                onResetStopwatch = onResetStopwatch,
                                onToggleFlash = onToggleFlash,
                                dynamicList = dynamicList,
                                onShowToast = onShowToast
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    node.children.forEach { child ->
                        RenderLayoutNode(
                            node = child,
                            viewStates = viewStates,
                            checkStates = checkStates,
                            counterState = counterState,
                            onCounterChange = onCounterChange,
                            calcExpression = calcExpression,
                            onCalcExpressionChange = onCalcExpressionChange,
                            calcResult = calcResult,
                            onCalcResultChange = onCalcResultChange,
                            stopwatchMs = stopwatchMs,
                            isStopwatchRunning = isStopwatchRunning,
                            onToggleStopwatch = onToggleStopwatch,
                            onResetStopwatch = onResetStopwatch,
                            onToggleFlash = onToggleFlash,
                            dynamicList = dynamicList,
                            onShowToast = onShowToast
                        )
                    }
                }
            }
        }

        "TextView" -> {
            val idLower = node.id.lowercase()
            val textLower = node.text.lowercase()

            val displayText = when {
                idLower.contains("calc") || idLower.contains("display") || idLower.contains("screen") || idLower.contains("tvresult") || (idLower.contains("result") && !idLower.contains("test")) -> {
                    if (calcExpression.isNotBlank()) calcExpression else if (calcResult != "0") calcResult else node.text.ifBlank { "0" }
                }
                idLower.contains("stopwatch") || idLower.contains("chronometer") || idLower.contains("timer") || textLower.contains("00:00") -> {
                    formatStopwatchTime(stopwatchMs)
                }
                idLower.contains("count") || textLower.contains("count") -> {
                    if (node.text.any { it.isDigit() }) {
                        "${node.text.takeWhile { !it.isDigit() }}$counterState"
                    } else {
                        "Count: $counterState"
                    }
                }
                viewStates[node.id] != null -> viewStates[node.id]!!
                else -> node.text.ifBlank { node.id }
            }

            val sizeSp = node.textSize.filter { it.isDigit() }.toFloatOrNull() ?: 16f
            val fontSize = sizeSp.coerceIn(12f, 32f).sp
            val textColor = if (node.textColor.isNotBlank()) parseXmlColor(node.textColor) else Color(0xFFF8FAFC)
            val isBold = fontSize >= 18.sp || node.text.length < 25

            Text(
                text = displayText,
                fontSize = fontSize,
                color = textColor,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        "EditText" -> {
            val currentVal = viewStates[node.id] ?: ""
            OutlinedTextField(
                value = currentVal,
                onValueChange = { viewStates[node.id] = it },
                placeholder = {
                    Text(
                        text = node.hint.ifBlank { "Enter value..." },
                        color = Color(0xFF64748B),
                        fontSize = 13.5.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFF8FAFC),
                    unfocusedTextColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    cursorColor = Color(0xFF38BDF8)
                )
            )
        }

        "Button", "MaterialButton", "AppCompatButton", "ImageButton" -> {
            val btnText = node.text.ifBlank { node.id.ifBlank { "Action" } }
            val btnColor = parseXmlColor(node.background).let { if (it == Color.Transparent) Color(0xFF3B82F6) else it }
            val lower = btnText.lowercase().trim()
            val lowerId = node.id.lowercase().trim()

            Button(
                onClick = {
                    when {
                        // Calculator Digits & Decimal
                        lower in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".") ||
                        lowerId in listOf("btn0", "btn1", "btn2", "btn3", "btn4", "btn5", "btn6", "btn7", "btn8", "btn9", "btndot") -> {
                            val digit = if (lower.length == 1) lower else lowerId.removePrefix("btn")
                            val updated = if (calcExpression == "0" && digit != ".") digit else calcExpression + digit
                            onCalcExpressionChange(updated)
                            onCalcResultChange(evaluateArithmeticExpression(updated))
                            onShowToast(updated)
                        }
                        // Calculator Operators
                        lower in listOf("+", "-", "×", "*", "÷", "/", "%") ||
                        lowerId in listOf("btnadd", "btnsub", "btnmul", "btndiv", "btnpercent") -> {
                            val op = when {
                                lower in listOf("+", "-", "×", "*", "÷", "/", "%") -> lower
                                lowerId == "btnadd" -> "+"
                                lowerId == "btnsub" -> "-"
                                lowerId == "btnmul" -> "×"
                                lowerId == "btndiv" -> "÷"
                                else -> "%"
                            }
                            val updated = "$calcExpression $op "
                            onCalcExpressionChange(updated)
                            onShowToast(updated)
                        }
                        // Calculator Equals
                        lower == "=" || lower == "enter" || lower == "eval" || lower == "calculate" || lowerId.contains("equal") -> {
                            val eval = evaluateArithmeticExpression(calcExpression)
                            onCalcResultChange(eval)
                            onCalcExpressionChange(eval)
                            onShowToast("= $eval")
                        }
                        // Calculator Clear
                        lower in listOf("c", "ac", "clear") || lowerId.contains("clear") || lowerId.contains("allclear") -> {
                            onCalcExpressionChange("")
                            onCalcResultChange("0")
                            onShowToast("Cleared")
                        }
                        // Calculator Delete / Backspace
                        lower in listOf("del", "back", "⌫") || lowerId.contains("delete") || lowerId.contains("back") -> {
                            val trimmed = calcExpression.trimEnd()
                            val updated = if (trimmed.isNotEmpty()) trimmed.dropLast(1).trimEnd() else ""
                            onCalcExpressionChange(updated)
                            onCalcResultChange(if (updated.isNotBlank()) evaluateArithmeticExpression(updated) else "0")
                            onShowToast(updated.ifBlank { "0" })
                        }
                        // Flashlight / Torch
                        lower.contains("torch") || lower.contains("flash") || lower.contains("light") ||
                        lowerId.contains("flash") || lowerId.contains("torch") -> {
                            onToggleFlash()
                        }
                        // Stopwatch Controls
                        lower == "start" || lower.contains("start timer") || lowerId.contains("start") -> {
                            onToggleStopwatch(true)
                            onShowToast("⏱️ Timer Started")
                        }
                        lower in listOf("stop", "pause") || lowerId.contains("stop") || lowerId.contains("pause") -> {
                            onToggleStopwatch(false)
                            onShowToast("⏱️ Timer Paused")
                        }
                        (lower == "reset" && (lowerId.contains("stopwatch") || lowerId.contains("timer"))) || lowerId.contains("timerreset") -> {
                            onResetStopwatch()
                            onShowToast("⏱️ Timer Reset")
                        }
                        // Dynamic List (Todo / Notes / Items)
                        lower.contains("add") || lower.contains("insert") || lower.contains("save") || lower.contains("submit") || lowerId.contains("add") -> {
                            val candidateText = viewStates.values.firstOrNull { it.isNotBlank() } ?: "New Item #${dynamicList.size + 1}"
                            dynamicList.add(candidateText)
                            viewStates.keys.forEach { viewStates[it] = "" }
                            onShowToast("Added: $candidateText")
                        }
                        // Counter Controls
                        lower.contains("+") || lower.contains("inc") || lower.contains("count") || lower.contains("click") || lower.contains("tap") || lowerId.contains("inc") -> {
                            val next = counterState + 1
                            onCounterChange(next)
                            onShowToast("Count: $next")
                        }
                        lower.contains("-") || lower.contains("dec") || lowerId.contains("dec") -> {
                            val next = maxOf(0, counterState - 1)
                            onCounterChange(next)
                            onShowToast("Count: $next")
                        }
                        lower.contains("reset") || lower.contains("clear") || lowerId.contains("clear") || lowerId.contains("reset") -> {
                            onCounterChange(0)
                            dynamicList.clear()
                            viewStates.clear()
                            onCalcExpressionChange("")
                            onCalcResultChange("0")
                            onResetStopwatch()
                            onShowToast("Reset complete")
                        }
                        else -> {
                            onShowToast("Tapped: $btnText")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = btnText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        "CheckBox" -> {
            val isChecked = checkStates[node.id] ?: false
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        checkStates[node.id] = !isChecked
                        onShowToast(if (!isChecked) "Checked: ${node.text}" else "Unchecked")
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = {
                        checkStates[node.id] = it
                        onShowToast(if (it) "Checked: ${node.text}" else "Unchecked")
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = node.text.ifBlank { "Option" },
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp
                )
            }
        }

        "Switch", "SwitchCompat" -> {
            val isChecked = checkStates[node.id] ?: false
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = node.text.ifBlank { "Toggle Option" },
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = {
                        checkStates[node.id] = it
                        onShowToast(if (it) "Enabled: ${node.text}" else "Disabled")
                    }
                )
            }
        }

        "ProgressBar" -> {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(0xFF38BDF8),
                trackColor = Color(0xFF1E293B)
            )
        }

        "ImageView" -> {
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .padding(vertical = 4.dp),
                shape = CircleShape,
                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                border = BorderStroke(2.dp, Color(0xFF38BDF8))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        else -> {
            if (node.children.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    node.children.forEach { child ->
                        RenderLayoutNode(
                            node = child,
                            viewStates = viewStates,
                            checkStates = checkStates,
                            counterState = counterState,
                            onCounterChange = onCounterChange,
                            calcExpression = calcExpression,
                            onCalcExpressionChange = onCalcExpressionChange,
                            calcResult = calcResult,
                            onCalcResultChange = onCalcResultChange,
                            stopwatchMs = stopwatchMs,
                            isStopwatchRunning = isStopwatchRunning,
                            onToggleStopwatch = onToggleStopwatch,
                            onResetStopwatch = onResetStopwatch,
                            onToggleFlash = onToggleFlash,
                            dynamicList = dynamicList,
                            onShowToast = onShowToast
                        )
                    }
                }
            } else if (node.text.isNotBlank()) {
                Text(
                    text = node.text,
                    color = Color(0xFFF8FAFC),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

private fun parseXmlColor(colorStr: String): Color {
    val trimmed = colorStr.trim()
    return try {
        when {
            trimmed.startsWith("#") -> {
                val hex = trimmed.removePrefix("#")
                when (hex.length) {
                    6 -> Color(android.graphics.Color.parseColor("#FF$hex"))
                    8 -> Color(android.graphics.Color.parseColor("#$hex"))
                    3 -> {
                        val expanded = hex.map { "$it$it" }.joinToString("")
                        Color(android.graphics.Color.parseColor("#FF$expanded"))
                    }
                    else -> Color(0xFF3B82F6)
                }
            }
            trimmed.contains("white", true) -> Color.White
            trimmed.contains("black", true) -> Color.Black
            trimmed.contains("green", true) -> Color(0xFF10B981)
            trimmed.contains("red", true) -> Color(0xFFEF4444)
            trimmed.contains("purple", true) -> Color(0xFF8B5CF6)
            trimmed.contains("blue", true) -> Color(0xFF3B82F6)
            else -> Color.Transparent
        }
    } catch (e: Exception) {
        Color.Transparent
    }
}

