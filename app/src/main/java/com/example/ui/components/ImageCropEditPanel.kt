package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ImageCropEditPanel(
    bitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var userZoom by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var isFlippedHorizontal by remember { mutableStateOf(false) }

    val cropBoxSizeDp = 270.dp
    val density = LocalDensity.current
    val cropBoxSizePx = with(density) { cropBoxSizeDp.toPx() }

    val baseScale = remember(bitmap, cropBoxSizePx) {
        maxOf(
            cropBoxSizePx / bitmap.width.toFloat(),
            cropBoxSizePx / bitmap.height.toFloat()
        )
    }

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
                .background(Color(0xF00A0D14))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close / Cancel Button (Cut)
                    Surface(
                        shape = CircleShape,
                        color = Color(0x33FFFFFF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Crop & Edit Icon",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Square 1:1 Aspect Ratio",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    // Tick (Checkmark) Button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF97316),
                        modifier = Modifier.size(44.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val cropped = cropSquareBitmap(
                                    sourceBitmap = bitmap,
                                    cropSizePx = cropBoxSizePx,
                                    panOffset = panOffset,
                                    userZoom = userZoom,
                                    rotationDegrees = rotationDegrees,
                                    isFlipped = isFlippedHorizontal,
                                    outputSize = 512
                                )
                                onCropConfirmed(cropped)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Apply Crop",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Square Crop Viewport Container
                Box(
                    modifier = Modifier
                        .size(cropBoxSizeDp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF000000))
                        .border(1.5.dp, Color(0xFFF97316), RoundedCornerShape(8.dp))
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                userZoom = (userZoom * zoom).coerceIn(0.5f, 5.0f)
                                panOffset = Offset(
                                    x = panOffset.x + pan.x,
                                    y = panOffset.y + pan.y
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val currentTotalScale = baseScale * userZoom

                    // Image Display Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        drawIntoCanvas { composeCanvas ->
                            val nativeCanvas = composeCanvas.nativeCanvas
                            nativeCanvas.save()

                            nativeCanvas.translate(
                                canvasWidth / 2f + panOffset.x,
                                canvasHeight / 2f + panOffset.y
                            )
                            nativeCanvas.rotate(rotationDegrees)

                            val sx = if (isFlippedHorizontal) -currentTotalScale else currentTotalScale
                            val sy = currentTotalScale
                            nativeCanvas.scale(sx, sy)

                            val paint = AndroidPaint().apply {
                                isAntiAlias = true
                                isFilterBitmap = true
                            }

                            nativeCanvas.drawBitmap(
                                bitmap,
                                -bitmap.width / 2f,
                                -bitmap.height / 2f,
                                paint
                            )

                            nativeCanvas.restore()
                        }

                        // Rule of Thirds Grid Guidelines
                        val thirdW = size.width / 3f
                        val thirdH = size.height / 3f
                        val gridColor = Color(0x33FFFFFF)

                        drawLine(gridColor, Offset(thirdW, 0f), Offset(thirdW, size.height), 1f)
                        drawLine(gridColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), 1f)
                        drawLine(gridColor, Offset(0f, thirdH), Offset(size.width, thirdH), 1f)
                        drawLine(gridColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), 1f)

                        // 4 Corner Brackets (Stylized crop indicators)
                        val bracketLen = 22.dp.toPx()
                        val bracketStroke = 3.dp.toPx()
                        val bracketColor = Color(0xFFF97316)

                        // Top-Left
                        drawLine(bracketColor, Offset(0f, 0f), Offset(bracketLen, 0f), bracketStroke)
                        drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bracketLen), bracketStroke)
                        // Top-Right
                        drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width - bracketLen, 0f), bracketStroke)
                        drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width, bracketLen), bracketStroke)
                        // Bottom-Left
                        drawLine(bracketColor, Offset(0f, size.height), Offset(bracketLen, size.height), bracketStroke)
                        drawLine(bracketColor, Offset(0f, size.height), Offset(0f, size.height - bracketLen), bracketStroke)
                        // Bottom-Right
                        drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width - bracketLen, size.height), bracketStroke)
                        drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), bracketStroke)
                    }
                }

                // Interactive Edit Controls & Tools (Liquid Glass Card)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0x2BFFFFFF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2BFFFFFF))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Zoom Slider Row with +/- buttons (crucial for streaming emulator mouse support)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { userZoom = (userZoom - 0.2f).coerceAtLeast(0.5f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
                            }

                            Slider(
                                value = userZoom,
                                onValueChange = { userZoom = it },
                                valueRange = 0.5f..4.0f,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF97316),
                                    activeTrackColor = Color(0xFFF97316),
                                    inactiveTrackColor = Color(0x33FFFFFF)
                                )
                            )

                            IconButton(
                                onClick = { userZoom = (userZoom + 0.2f).coerceAtMost(4.0f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tool Action Buttons: Rotate, Mirror, Reset/Cut, Apply
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rotate 90° Clockwise
                            ToolButton(
                                icon = Icons.AutoMirrored.Filled.RotateRight,
                                label = "Rotate",
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90f) % 360f
                                }
                            )

                            // Mirror / Flip Horizontal
                            ToolButton(
                                icon = Icons.Default.Flip,
                                label = if (isFlippedHorizontal) "Flipped" else "Mirror",
                                isHighlight = isFlippedHorizontal,
                                onClick = {
                                    isFlippedHorizontal = !isFlippedHorizontal
                                }
                            )

                            // Reset / Cut transforms
                            ToolButton(
                                icon = Icons.Default.Refresh,
                                label = "Reset",
                                onClick = {
                                    userZoom = 1.0f
                                    panOffset = Offset.Zero
                                    rotationDegrees = 0f
                                    isFlippedHorizontal = false
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Full-width Confirm Button
                        Button(
                            onClick = {
                                val cropped = cropSquareBitmap(
                                    sourceBitmap = bitmap,
                                    cropSizePx = cropBoxSizePx,
                                    panOffset = panOffset,
                                    userZoom = userZoom,
                                    rotationDegrees = rotationDegrees,
                                    isFlipped = isFlippedHorizontal,
                                    outputSize = 512
                                )
                                onCropConfirmed(cropped)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF97316)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Set as App Icon",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isHighlight) Color(0xFFF97316) else Color(0x22FFFFFF),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isHighlight) Color(0xFFF97316) else Color(0x33FFFFFF)
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isHighlight) Color(0xFFF97316) else Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Mathematically identical projection of the viewport transforms onto a 512x512 square Bitmap.
 */
fun cropSquareBitmap(
    sourceBitmap: Bitmap,
    cropSizePx: Float,
    panOffset: Offset,
    userZoom: Float,
    rotationDegrees: Float,
    isFlipped: Boolean,
    outputSize: Int = 512
): Bitmap {
    val baseScale = maxOf(
        cropSizePx / sourceBitmap.width.toFloat(),
        cropSizePx / sourceBitmap.height.toFloat()
    )
    val totalScale = baseScale * userZoom
    val ratio = outputSize.toFloat() / cropSizePx

    val outputBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(outputBitmap)

    canvas.save()
    canvas.translate(
        outputSize / 2f + panOffset.x * ratio,
        outputSize / 2f + panOffset.y * ratio
    )
    canvas.rotate(rotationDegrees)

    val scaleX = (if (isFlipped) -totalScale else totalScale) * ratio
    val scaleY = totalScale * ratio
    canvas.scale(scaleX, scaleY)

    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        sourceBitmap,
        -sourceBitmap.width / 2f,
        -sourceBitmap.height / 2f,
        paint
    )
    canvas.restore()

    return outputBitmap
}
