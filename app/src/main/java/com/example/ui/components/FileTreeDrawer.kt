package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Interactive Expandable File Explorer Drawer for SAIF AI Studio.
 * Faithfully mirrors the project hierarchy shown in user screenshots
 * (Root -> app -> src -> main -> java -> com.example... -> MainActivity.java, etc.).
 * Includes smooth expand/collapse animations and subtle liquid glass styling.
 */
@Composable
fun FileTreeDrawer(
    project: ProjectData,
    fileTree: List<FileTreeNode>,
    expandedPaths: Set<String>,
    activeFilePath: String?,
    isDirty: Boolean,
    isDarkTheme: Boolean,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (EditorFileItem) -> Unit,
    onNewFileClick: () -> Unit,
    onCloseDrawer: () -> Unit,
    onExitProject: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Theme colors with subtle liquid glass effect
    val bgGradient = if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                Color(0xFA0F172A),
                Color(0xF51E293B)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFAFFFFFF),
                Color(0xF5F8FAFC)
            )
        )
    }

    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val dividerColor = if (isDarkTheme) Color(0x33334155) else Color(0x33E2E8F0)
    val accentOrange = Color(0xFFF97316)
    val folderColor = Color(0xFFF59E0B)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(bgGradient)
            .border(
                width = 1.dp,
                color = if (isDarkTheme) Color(0x3364748B) else Color(0x22000000),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
    ) {
        // ==========================================
        // 1. DRAWER HEADER (Matching Project Config)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (project.iconBitmap != null) {
                        Image(
                            bitmap = project.iconBitmap.asImageBitmap(),
                            contentDescription = "Project Icon",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, accentOrange.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accentOrange,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = project.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accentOrange.copy(alpha = 0.15f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = project.language,
                                    fontSize = 10.5.sp,
                                    color = accentOrange,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "API ${project.targetSdk}",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }
                }

                // Action Buttons: Exit Project & Close Drawer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onExitProject != null) {
                        IconButton(
                            onClick = {
                                onCloseDrawer()
                                onExitProject()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Return to Main Screen",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Close File Tree",
                            tint = textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = project.packageName,
                fontSize = 11.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        // ==========================================
        // 2. PROJECT FILES EXPLORER TITLE & ACTIONS
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "PROJECT EXPLORER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = textSecondary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNewFileClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New File",
                        tint = accentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ==========================================
        // 3. EXPANDABLE TREE VIEW (LAZY COLUMN)
        // ==========================================
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            for (node in fileTree) {
                renderNode(
                    node = node,
                    expandedPaths = expandedPaths,
                    activeFilePath = activeFilePath,
                    isDirty = isDirty,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accentOrange = accentOrange,
                    folderColor = folderColor,
                    onToggleFolder = onToggleFolder,
                    onSelectFile = onSelectFile
                )
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        // ==========================================
        // 4. DRAWER BOTTOM ACTION BUTTONS
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNewFileClick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, accentOrange.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = accentOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "New File",
                    fontSize = 12.sp,
                    color = accentOrange,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onCloseDrawer,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentOrange),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Editor",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (onExitProject != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onCloseDrawer()
                    onExitProject()
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Return to Main Screen",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}

/**
 * Recursive DSL function to render tree nodes with proper nesting and expand/collapse states.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.renderNode(
    node: FileTreeNode,
    expandedPaths: Set<String>,
    activeFilePath: String?,
    isDirty: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentOrange: Color,
    folderColor: Color,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (EditorFileItem) -> Unit
) {
    if (node.isDirectory) {
        val isExpanded = expandedPaths.contains(node.path)
        item(key = "dir_${node.path}_${node.name}") {
            FolderTreeRow(
                node = node,
                isExpanded = isExpanded,
                textPrimary = textPrimary,
                folderColor = folderColor,
                onClick = { onToggleFolder(node.path) }
            )
        }

        if (isExpanded) {
            for (child in node.children) {
                renderNode(
                    node = child,
                    expandedPaths = expandedPaths,
                    activeFilePath = activeFilePath,
                    isDirty = isDirty,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accentOrange = accentOrange,
                    folderColor = folderColor,
                    onToggleFolder = onToggleFolder,
                    onSelectFile = onSelectFile
                )
            }
        }
    } else {
        val isSelected = activeFilePath == node.path || (activeFilePath == null && node.isMain)
        item(key = "file_${node.path}_${node.name}") {
            FileTreeRow(
                node = node,
                isSelected = isSelected,
                isDirty = isSelected && isDirty,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentOrange = accentOrange,
                onClick = {
                    onSelectFile(
                        EditorFileItem(
                            name = node.name,
                            path = node.path,
                            isMain = node.isMain
                        )
                    )
                }
            )
        }
    }
}

/**
 * Composable row representing a directory in the tree.
 */
@Composable
private fun FolderTreeRow(
    node: FileTreeNode,
    isExpanded: Boolean,
    textPrimary: Color,
    folderColor: Color,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "folder_arrow"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (node.depth * 14).dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // Expand/Collapse arrow
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = textPrimary.copy(alpha = 0.5f),
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Folder Icon
        Icon(
            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = folderColor,
            modifier = Modifier.size(19.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Folder Name
        Text(
            text = node.name,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Composable row representing a file in the tree.
 */
@Composable
private fun FileTreeRow(
    node: FileTreeNode,
    isSelected: Boolean,
    isDirty: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentOrange: Color,
    onClick: () -> Unit
) {
    val fileIcon = getFileIcon(node.name)
    val iconTint = getFileIconTint(node.name)

    val rowBg = if (isSelected) {
        accentOrange.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (node.depth * 14 + 18).dp, top = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // File Icon
        Icon(
            imageVector = fileIcon,
            contentDescription = null,
            tint = if (isSelected) accentOrange else iconTint,
            modifier = Modifier.size(17.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // File Name
        Text(
            text = node.name,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) accentOrange else textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Unsaved dirty dot indicator
        if (isDirty) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentOrange)
            )
        }
    }
}

/**
 * Returns suitable icon for a given file name based on extension.
 */
private fun getFileIcon(fileName: String): ImageVector {
    return when {
        fileName.endsWith(".java") || fileName.endsWith(".kt") -> Icons.Default.Code
        fileName.endsWith(".xml") -> Icons.Outlined.DataObject
        fileName.contains("gradle") -> Icons.Default.Build
        fileName.endsWith(".json") -> Icons.Default.DataObject
        fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".webp") -> Icons.Default.Image
        fileName.endsWith(".html") || fileName.endsWith(".js") || fileName.endsWith(".css") -> Icons.Default.Language
        fileName.endsWith(".py") -> Icons.Default.Terminal
        else -> Icons.Outlined.Description
    }
}

/**
 * Returns appropriate tint color for file icon.
 */
private fun getFileIconTint(fileName: String): Color {
    return when {
        fileName.endsWith(".java") || fileName.endsWith(".kt") -> Color(0xFFF97316) // Orange
        fileName.endsWith(".xml") -> Color(0xFF38BDF8) // Sky Blue
        fileName.contains("gradle") -> Color(0xFF4ADE80) // Emerald
        fileName.endsWith(".json") -> Color(0xFFFBBF24) // Amber
        fileName.endsWith(".png") || fileName.endsWith(".jpg") -> Color(0xFFA855F7) // Purple
        else -> Color(0xFF94A3B8) // Slate Gray
    }
}
