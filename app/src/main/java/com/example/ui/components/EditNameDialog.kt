package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AuthManager
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.StatusError
import com.example.util.SmartConversationMemory
import kotlinx.coroutines.launch

@Composable
fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onNameUpdated: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var nameInput by remember { mutableStateOf(currentName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = PrimaryAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Change Display Name",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaifTheme.colors.textPrimary
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter your preferred name. SAIF AI will address you with this name in all conversations.",
                    fontSize = 13.sp,
                    color = SaifTheme.colors.textSecondary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        errorMessage = null
                    },
                    label = { Text("Your Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = SaifTheme.colors.cardBorder,
                        focusedContainerColor = SaifTheme.colors.surfaceCard,
                        unfocusedContainerColor = SaifTheme.colors.surfaceCard,
                        focusedTextColor = SaifTheme.colors.textPrimary,
                        unfocusedTextColor = SaifTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage!!,
                        color = StatusError,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = nameInput.trim()
                    if (clean.isBlank()) {
                        errorMessage = "Name cannot be empty"
                        return@Button
                    }
                    isSaving = true
                    coroutineScope.launch {
                        val result = AuthManager.updateUserName(clean)
                        isSaving = false
                        if (result.isSuccess) {
                            SmartConversationMemory.saveFact("userName", clean)
                            Toast.makeText(context, "Name updated successfully!", Toast.LENGTH_SHORT).show()
                            onNameUpdated(clean)
                            onDismiss()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to update name"
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SaifTheme.colors.textSecondary)
            }
        },
        containerColor = SaifTheme.colors.surfaceCard,
        shape = RoundedCornerShape(18.dp)
    )
}
