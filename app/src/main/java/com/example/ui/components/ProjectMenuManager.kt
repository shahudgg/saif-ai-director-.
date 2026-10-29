package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Category enum for file management sections.
 */
enum class FileManagerCategory(
    val label: String,
    val folderPath: String,
    val defaultTitle: String
) {
    JAVA("Java File", "app/src/main/java", "Java"),
    RESOURCES("Resources File", "app/src/main/res", "res"),
    ASSETS("Assets File", "app/src/main/assets", "assets"),
    LIB("Lib File", "app/libs", "libs"),
    JNI("Jni File", "app/src/main/jni", "jni")
}

/**
 * Navigation destination for code editor sub-screens.
 */
sealed interface ProjectSubScreen {
    data class FileManager(
        val category: FileManagerCategory,
        val relativePath: String,
        val title: String
    ) : ProjectSubScreen

    data object LibraryManager : ProjectSubScreen
    data class BuildAI(val initialError: com.example.compiler.CompilerErrorInfo? = null) : ProjectSubScreen
}

/**
 * Custom-drawn authentic Java Coffee Cup logo with red and blue steam waves,
 * faithfully matching the user's reference screenshot.
 */
@Composable
fun JavaCoffeeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Colors matching authentic Java logo
        val steamRed = Color(0xFFDC2626) // Vibrant scarlet red
        val steamBlue = Color(0xFF0284C7) // Sky blue
        val cupBlue = Color(0xFF1D4ED8) // Deep royal blue

        // 1. Saucer curve at bottom
        val saucerPath = Path().apply {
            moveTo(w * 0.15f, h * 0.85f)
            cubicTo(
                w * 0.25f, h * 0.95f,
                w * 0.75f, h * 0.95f,
                w * 0.85f, h * 0.85f
            )
        }
        drawPath(
            path = saucerPath,
            color = cupBlue,
            style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        )

        // 2. Main Cup Body Arc
        val cupPath = Path().apply {
            moveTo(w * 0.22f, h * 0.65f)
            cubicTo(
                w * 0.25f, h * 0.80f,
                w * 0.75f, h * 0.80f,
                w * 0.78f, h * 0.65f
            )
        }
        drawPath(
            path = cupPath,
            color = cupBlue,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        )

        // 3. Cup Handle on right
        drawArc(
            color = cupBlue,
            startAngle = -60f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.68f, h * 0.60f),
            size = Size(w * 0.24f, h * 0.18f),
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        )

        // 4. Red Steam Wave (Left rising plume)
        val redSteam = Path().apply {
            moveTo(w * 0.38f, h * 0.58f)
            cubicTo(
                w * 0.46f, h * 0.44f,
                w * 0.30f, h * 0.32f,
                w * 0.42f, h * 0.16f
            )
        }
        drawPath(
            path = redSteam,
            color = steamRed,
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        )

        // 5. Blue Steam Wave (Right rising plume)
        val blueSteam = Path().apply {
            moveTo(w * 0.56f, h * 0.54f)
            cubicTo(
                w * 0.64f, h * 0.40f,
                w * 0.48f, h * 0.28f,
                w * 0.60f, h * 0.14f
            )
        }
        drawPath(
            path = blueSteam,
            color = steamBlue,
            style = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Complete Top-Right 3-Dots Popup Dropdown Menu matching Screenshot 1.
 * Features:
 * 1. Java File (Coffee cup icon)
 * 2. Resources File (Folder icon)
 * 3. Assets File (Folder icon)
 * 4. Lib File (Folder icon)
 * 5. Jni File (Folder icon)
 * 6. Local Library (Book icon)
 * 7. Build AI (Purple Sparkles icon)
 */
@Composable
fun ProjectMenuDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean,
    onSelectCategory: (FileManagerCategory) -> Unit,
    onOpenLibraryManager: () -> Unit,
    onOpenBuildAI: () -> Unit,
    onOpenProjectConfiguration: () -> Unit = {},
    onCloseProject: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    // Offset popup nicely under the 3-dots icon
    Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(x = -12, y = 96),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // Liquid glass surface styling with clean shadow
        val cardBg = if (isDarkTheme) Color(0xF21E293B) else Color(0xFAFFFFFF)
        val borderStroke = if (isDarkTheme) Color(0x33FFFFFF) else Color(0x18000000)
        val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF1E293B)
        val folderBlue = Color(0xFF4F46E5) // Indigo / Vibrant Royal Blue
        val sparklePurple = Color(0xFFA855F7) // Radiant Sparkle Purple

        Surface(
            modifier = modifier
                .width(220.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color(0x22000000),
                    spotColor = Color(0x33000000)
                ),
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderStroke)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 1. Java File
                ProjectMenuItemRow(
                    title = "Java File",
                    icon = { JavaCoffeeIcon(size = 22.dp) },
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onSelectCategory(FileManagerCategory.JAVA)
                    }
                )

                // 2. Resources File
                ProjectMenuItemRow(
                    title = "Resources File",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = folderBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onSelectCategory(FileManagerCategory.RESOURCES)
                    }
                )

                // 3. Assets File
                ProjectMenuItemRow(
                    title = "Assets File",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = folderBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onSelectCategory(FileManagerCategory.ASSETS)
                    }
                )

                // 4. Lib File
                ProjectMenuItemRow(
                    title = "Lib File",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = folderBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onSelectCategory(FileManagerCategory.LIB)
                    }
                )

                // 5. Jni File
                ProjectMenuItemRow(
                    title = "Jni File",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = folderBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onSelectCategory(FileManagerCategory.JNI)
                    }
                )

                // 6. Local Library
                ProjectMenuItemRow(
                    title = "Local Library",
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = null,
                            tint = folderBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onOpenLibraryManager()
                    }
                )

                // 7. SAIF AI
                ProjectMenuItemRow(
                    title = "SAIF AI",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = sparklePurple,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    textColor = Color(0xFF9333EA), // Purple text highlight matching screenshot
                    isBold = true,
                    onClick = {
                        onDismissRequest()
                        onOpenBuildAI()
                    }
                )

                // 8. App Configuration (Change Name & Icon)
                val orangeAccent = Color(0xFFF97316)
                ProjectMenuItemRow(
                    title = "App Configuration (Name & Icon)",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Project Settings",
                            tint = orangeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    textColor = textPrimary,
                    onClick = {
                        onDismissRequest()
                        onOpenProjectConfiguration()
                    }
                )

                // 9. Close / Exit Project
                if (onCloseProject != null) {
                    HorizontalDivider(
                        color = if (isDarkTheme) Color(0x22FFFFFF) else Color(0x15000000),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    ProjectMenuItemRow(
                        title = "Exit Project",
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Exit Project",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        textColor = Color(0xFFEF4444),
                        onClick = {
                            onDismissRequest()
                            onCloseProject()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Single Row inside the Dropdown Menu with ripple and padding.
 */
@Composable
private fun ProjectMenuItemRow(
    title: String,
    icon: @Composable () -> Unit,
    textColor: Color,
    isBold: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}
