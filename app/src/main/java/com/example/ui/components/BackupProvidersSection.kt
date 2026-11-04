package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llm.BackupProviders
import com.example.llm.ProviderProfile

/**
 * Round 14 — Settings section: "agar kisi API ka limit lag jaye to model automatic dusre par switch ho".
 *
 * The primary provider is set above. Here the owner stores extra providers (each with its own key and
 * model). The gateway uses them automatically when the primary is rate-limited, out of daily quota,
 * unauthorized or the model is retired — so a long video job keeps going.
 */
@Composable
fun BackupProvidersSection() {
    val ctx = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    var list by remember(refreshTick) { mutableStateOf(BackupProviders.list()) }
    var auto by remember { mutableStateOf(BackupProviders.autoSwitchEnabled()) }
    var provider by remember { mutableStateOf("Groq") }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141826),
        border = BorderStroke(1.dp, Color(0xFF243049)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔁 AUTO-SWITCH ON LIMIT", color = Color(0xFF5EEAD4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Limit / quota / key error aane pe SAIF khud dusre provider + model par chala jayega.",
                        color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 13.sp)
                }
                Switch(checked = auto, onCheckedChange = {
                    auto = it; BackupProviders.setAutoSwitchEnabled(it); note = if (it) "Auto-switch ON" else "Auto-switch OFF"
                })
            }
            Text(BackupProviders.summary(), color = Color(0xFFCBD5E1), fontSize = 10.sp)
            list.forEachIndexed { index, b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = b.enabled, onCheckedChange = {
                        BackupProviders.toggle(index, it); refreshTick++
                    })
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.provider + " · " + b.model.ifBlank { "default model" }, color = Color.White, fontSize = 11.sp)
                        Text("key saved (encrypted) · tap 🗑 to remove", color = Color(0xFF64748B), fontSize = 9.sp)
                    }
                    TextButton(onClick = { BackupProviders.removeAt(index); refreshTick++; note = "Removed" }) { Text("🗑", fontSize = 11.sp) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(provider, { provider = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Provider", fontSize = 9.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White))
                OutlinedTextField(model, { model = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Model (optional)", fontSize = 9.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White))
            }
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Backup API key", fontSize = 9.sp) },
                visualTransformation = PasswordVisualTransformation(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White))
            Text("Providers: " + ProviderProfile.ALL_PROFILES.take(10).joinToString(", ") { it.id } + " …",
                color = Color(0xFF64748B), fontSize = 8.sp, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = provider.isNotBlank() && key.isNotBlank(), onClick = {
                    BackupProviders.add(provider.trim(), key.trim(), model.trim())
                    key = ""; model = ""; note = "Backup provider saved"; refreshTick++
                }) { Text("+ Add backup", fontSize = 10.sp) }
                TextButton(onClick = { BackupProviders.clear(); refreshTick++; note = "All backups removed" }) { Text("Clear all", fontSize = 10.sp) }
                if (note.isNotBlank()) Text(note, color = Color(0xFF5EEAD4), fontSize = 9.sp)
            }
            Text("Keys Android Keystore se encrypt hokar save hote hain — kabhi log ya chat mein nahi jaate.",
                color = Color(0xFF475569), fontSize = 8.sp)
        }
    }
}
