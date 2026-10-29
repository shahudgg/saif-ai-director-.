package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun VideoResolutionQualityDialog(
    project: VideoProjectData,
    onDismiss: () -> Unit,
    onConfirm: (resolution: String, fps: String) -> Unit
) {
    val context = LocalContext.current
    var selectedRes by remember { mutableStateOf(project.resolution) }
    var selectedFps by remember { mutableStateOf(project.fps) }

    val resolutions = listOf("720P", "1080P", "2K", "4K UHD")
    val fpsList = listOf("24FPS", "30FPS", "60FPS", "120FPS")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF16151C),
            border = BorderStroke(1.dp, Color(0x3500DAF3)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HighQuality, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Project Resolution & FPS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFCCC3D9), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resolution selector
                Text("Render Resolution", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(resolutions) { res ->
                        val isSel = res == selectedRes
                        Surface(
                            onClick = { selectedRes = res },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) Color(0xFF7B2BFF) else Color(0xFF222129),
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF00DAF3) else Color(0x22FFFFFF))
                        ) {
                            Text(
                                text = res,
                                color = if (isSel) Color.White else Color(0xFFCCC3D9),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // FPS selector
                Text("Frame Rate (FPS)", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fpsList) { fps ->
                        val isSel = fps == selectedFps
                        Surface(
                            onClick = { selectedFps = fps },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) Color(0xFF00DAF3) else Color(0xFF222129),
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF00DAF3) else Color(0x22FFFFFF))
                        ) {
                            Text(
                                text = fps,
                                color = if (isSel) Color.Black else Color(0xFFCCC3D9),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFFCCC3D9))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(selectedRes, selectedFps)
                            Toast.makeText(context, "Updated to $selectedRes at $selectedFps", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00DAF3)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
