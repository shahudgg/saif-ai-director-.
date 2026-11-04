package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProviderSettingsManager
import com.example.data.local.ProviderState
import com.example.data.remote.AIApiUtility
import com.example.llm.ProviderProfile
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import kotlinx.coroutines.launch

val PROVIDERS = listOf(
    "Gemini",
    "Ashna AI",
    "OpenCode.ai",
    "OpenRouter",
    "Groq",
    "OpenAI",
    "DeepSeek",
    "Anthropic",
    "Mistral",
    "Together",
    "xAI",
    "Cerebras",
    "Perplexity",
    "inceptionlabs.ai",
    "api.atria-asi.ai"
)

fun getProviderApiKeyUrl(provider: String): String {
    return when {
        provider.contains("Ashna", ignoreCase = true) -> "https://app.ashna.ai/integrations?tab=api"
        provider.contains("Gemini", ignoreCase = true) -> "https://aistudio.google.com/app/apikey"
        provider.contains("OpenCode", ignoreCase = true) -> "https://opencode.ai"
        provider.contains("OpenRouter", ignoreCase = true) -> "https://openrouter.ai/keys"
        provider.contains("Groq", ignoreCase = true) -> "https://console.groq.com/keys"
        provider.contains("OpenAI", ignoreCase = true) -> "https://platform.openai.com/api-keys"
        provider.contains("DeepSeek", ignoreCase = true) -> "https://platform.deepseek.com/api_keys"
        provider.contains("Anthropic", ignoreCase = true) -> "https://console.anthropic.com/settings/keys"
        provider.contains("Mistral", ignoreCase = true) -> "https://console.mistral.ai/api-keys"
        provider.contains("Together", ignoreCase = true) -> "https://api.together.xyz/settings/api-keys"
        provider.contains("xAI", ignoreCase = true) -> "https://console.x.ai"
        provider.contains("Cerebras", ignoreCase = true) -> "https://cloud.cerebras.ai"
        provider.contains("Perplexity", ignoreCase = true) -> "https://www.perplexity.ai/settings/api"
        provider.contains("inception", ignoreCase = true) -> "https://inceptionlabs.ai"
        provider.contains("atria", ignoreCase = true) -> "https://api.atria-asi.ai"
        else -> "https://aistudio.google.com/app/apikey"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiProviderSettingsInline() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ProviderSettingsManager.loadState()) }
    val globalConfig by com.example.data.local.AdminConfigManager.globalConfig.collectAsState()
    val chatUsage by com.example.data.local.AdminConfigManager.userChatUsage.collectAsState()
    val imageUsage by com.example.data.local.AdminConfigManager.userImageUsage.collectAsState()
    val appBuildUsage by com.example.data.local.AdminConfigManager.userAppBuildUsage.collectAsState()

    var providerName by remember {
        mutableStateOf(
            when {
                state.providerName.equals("OpenCode Zen", ignoreCase = true) -> "OpenCode.ai"
                state.providerName.contains("OpenCode", ignoreCase = true) -> "OpenCode.ai"
                state.providerName.contains("Asana", ignoreCase = true) || state.providerName.contains("Ashna", ignoreCase = true) -> "Ashna AI"
                PROVIDERS.contains(state.providerName) -> state.providerName
                else -> "Gemini"
            }
        )
    }
    
    var apiKeys by remember { mutableStateOf(if (state.apiKeys.isEmpty()) listOf("") else state.apiKeys) }
    var activeModel by remember { mutableStateOf(state.activeModel.ifBlank { "gemini-flash-latest" }) }
    var buildModel by remember { mutableStateOf(state.buildModel.ifBlank { "gemini-flash-latest" }) }
    var allowFallbackToOtherProviders by remember { mutableStateOf(state.allowFallbackToOtherProviders) }
    var connectionDiagnostics by remember { mutableStateOf<com.example.llm.ConnectionTestResult?>(null) }
    
    var fetchedModels by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var fetchSuccessMessage by remember { mutableStateOf<String?>(null) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showAllModelsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val hasPersonalKey = apiKeys.any { it.isNotBlank() && !it.contains("MY_") }

    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Banner (Admin Fleet Free Tier vs Personal API Key)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (hasPersonalKey) Color(0x1A10B981) else Color(0x1F8B5CF6),
            border = BorderStroke(1.dp, if (hasPersonalKey) Color(0x6610B981) else Color(0x668B5CF6)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasPersonalKey) Icons.Default.CheckCircle else Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (hasPersonalKey) Color(0xFF10B981) else Color(0xFFA78BFA),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (hasPersonalKey) "🚀 Personal API Key Active (Unlimited)" else "🎁 Free Tier Active (Admin Fleet)",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasPersonalKey) {
                            "All limits removed! App is executing directly via your personal API key."
                        } else {
                            "Free usage: $chatUsage/${globalConfig.chatLimitPerUser} chats • $imageUsage/${globalConfig.imageLimitPerUser} images • $appBuildUsage/${globalConfig.appBuildLimitPerUser} builds. Enter your key below for unlimited access."
                        },
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // API Provider Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("API Provider", fontSize = 13.sp, color = SaifTheme.colors.textSecondary)
            var expanded by remember { mutableStateOf(false) }
            
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = SaifTheme.colors.surfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(providerName, color = SaifTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (providerName == "OpenCode.ai") {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF0F766E),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("PRO & FREE", color = Color(0xFF5EEAD4), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
                    }
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(SaifTheme.colors.surfaceCard).fillMaxWidth(0.85f)
                ) {
                    PROVIDERS.forEach { pName ->
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(pName, color = SaifTheme.colors.textPrimary, fontWeight = if (pName == providerName) FontWeight.Bold else FontWeight.Normal)
                                    if (pName == "OpenCode.ai") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(Official)", color = Color(0xFF10B981), fontSize = 11.sp)
                                    }
                                }
                            },
                            onClick = { 
                                providerName = pName
                                fetchedModels = emptyList()
                                fetchError = null
                                fetchSuccessMessage = null
                                saveSuccessMessage = null
                                expanded = false
                                
                                // Auto-update activeModel to a sensible default for the new provider
                                val newDefault = when {
                                    pName.contains("Ashna", ignoreCase = true) -> "ashna-x1"
                                    pName.contains("DeepSeek", ignoreCase = true) -> "deepseek-chat"
                                    pName.contains("Anthropic", ignoreCase = true) -> "claude-3-5-sonnet-20241022"
                                    pName.contains("Mistral", ignoreCase = true) -> "mistral-large-latest"
                                    pName.contains("Together", ignoreCase = true) -> "meta-llama/Llama-3.3-70B-Instruct-Turbo"
                                    pName.contains("xAI", ignoreCase = true) -> "grok-2-latest"
                                    pName.contains("Cerebras", ignoreCase = true) -> "llama-3.3-70b"
                                    pName.contains("Perplexity", ignoreCase = true) -> "sonar"
                                    pName.contains("inception", ignoreCase = true) -> "mercury-2.5"
                                    pName.contains("atria", ignoreCase = true) -> "Atria-Dawn-Preview"
                                    pName.contains("OpenCode", ignoreCase = true) -> "glm-5.1"
                                    pName.contains("OpenRouter", ignoreCase = true) -> "meta-llama/llama-3.1-8b-instruct:free"
                                    pName.contains("Groq", ignoreCase = true) -> "llama-3.3-70b-versatile"
                                    pName.contains("OpenAI", ignoreCase = true) -> "gpt-4o-mini"
                                    pName.contains("Gemini", ignoreCase = true) -> "gemini-flash-latest"
                                    else -> "mercury-2.5"
                                }
                                activeModel = newDefault
                                buildModel = newDefault
                                val cleanedList = apiKeys.map { AIApiUtility.sanitizeKey(it) }.filter { it.isNotBlank() }
                                ProviderSettingsManager.saveState(ProviderState(
                                    providerName = pName,
                                    apiKeys = cleanedList,
                                    activeModel = newDefault,
                                    buildModel = newDefault,
                                    currentKeyIndex = 0,
                                    allowFallbackToOtherProviders = allowFallbackToOtherProviders
                                ))
                            }
                        )
                    }
                }
            }
        }

        // Multiple API Keys
        val targetApiUrl = getProviderApiKeyUrl(providerName)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$providerName API Keys", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SaifTheme.colors.textSecondary)
                
                Surface(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetApiUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x2638BDF8),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Get API Key",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Get $providerName Key",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            apiKeys.forEachIndexed { index, key ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var isPasswordVisible by remember { mutableStateOf(false) }
                    
                    TextField(
                        value = key,
                        onValueChange = { newKey ->
                            val cleanKey = AIApiUtility.sanitizeKey(newKey)
                            val updated = apiKeys.toMutableList()
                            updated[index] = cleanKey
                            apiKeys = updated
                            fetchError = null
                            saveSuccessMessage = null

                            // Auto-detect provider from key prefix if unmistakable vendor prefix
                            val detectedProfile = ProviderProfile.detectProviderFromKey(cleanKey, providerName)
                            val autoProv = if (detectedProfile.displayName.equals("Google Gemini", true)) "Gemini" else detectedProfile.displayName
                            val shouldAutoSwitch = autoProv.isNotBlank() && !autoProv.equals(providerName, ignoreCase = true) &&
                                (cleanKey.startsWith("AIza") || cleanKey.startsWith("AQ.") || cleanKey.startsWith("gsk_") ||
                                 cleanKey.startsWith("sk-or-") || cleanKey.startsWith("sk-ant-") || cleanKey.startsWith("sk-proj-") ||
                                 cleanKey.startsWith("xai-") || cleanKey.startsWith("csk-") || cleanKey.startsWith("pplx-") ||
                                 cleanKey.startsWith("together-") || cleanKey.startsWith("mistral-") || cleanKey.startsWith("opencode") ||
                                 cleanKey.startsWith("oc-") || cleanKey.startsWith("ashna") || cleanKey.startsWith("atria") ||
                                 cleanKey.startsWith("inception") || cleanKey.startsWith("fw_"))

                            if (shouldAutoSwitch) {
                                providerName = autoProv
                                activeModel = detectedProfile.defaultFallbackModels.firstOrNull() ?: activeModel
                            }

                            // Align activeModel to provider default if currently holding unrelated Gemini default
                            if (!providerName.equals("Gemini", ignoreCase = true) && activeModel.contains("gemini", ignoreCase = true)) {
                                activeModel = detectedProfile.defaultFallbackModels.firstOrNull() ?: when {
                                    providerName.contains("Ashna", ignoreCase = true) -> "ashna-x1"
                                    providerName.contains("Groq", ignoreCase = true) -> "llama-3.3-70b-versatile"
                                    providerName.contains("DeepSeek", ignoreCase = true) -> "deepseek-chat"
                                    providerName.contains("OpenAI", ignoreCase = true) -> "gpt-4o-mini"
                                    else -> "ashna-x1"
                                }
                            }

                            // Instantly auto-save so user never loses their key upon leaving settings
                            val cleanedList = updated.map { AIApiUtility.sanitizeKey(it) }.filter { it.isNotBlank() }
                            ProviderSettingsManager.saveState(ProviderState(
                                providerName = providerName,
                                apiKeys = cleanedList,
                                activeModel = activeModel.ifBlank { "gemini-flash-latest" },
                                buildModel = buildModel.ifBlank { activeModel.ifBlank { "gemini-flash-latest" } },
                                currentKeyIndex = 0,
                                allowFallbackToOtherProviders = allowFallbackToOtherProviders
                            ))
                        },
                        placeholder = { 
                            Text(
                                if (providerName == "OpenCode.ai") "Enter OpenCode.ai API Key" else "Enter $providerName API Key",
                                color = SaifTheme.colors.textSecondary,
                                fontSize = 13.sp
                            ) 
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SaifTheme.colors.surfaceCard,
                            unfocusedContainerColor = SaifTheme.colors.surfaceCard,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = SaifTheme.colors.textPrimary,
                            unfocusedTextColor = SaifTheme.colors.textPrimary
                        ),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility",
                                    tint = Color.Gray
                                )
                            }
                        },
                        singleLine = true
                    )
                    
                    if (apiKeys.size > 1) {
                        IconButton(onClick = {
                            val updated = apiKeys.toMutableList()
                            updated.removeAt(index)
                            apiKeys = updated
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                        }
                    }
                }
            }

            Surface(
                onClick = { apiKeys = apiKeys + "" },
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, SaifTheme.colors.textSecondary.copy(alpha=0.3f))
            ) {
                Text("+ Add Another Key (Fallback)", color = SaifTheme.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        // Test Connection / Fetch Models
        Surface(
            onClick = { 
                val rawKey = apiKeys.firstOrNull { it.isNotBlank() }
                if (rawKey != null) {
                    val keyToUse = AIApiUtility.sanitizeKey(rawKey)
                    isFetching = true
                    fetchError = null
                    fetchSuccessMessage = null
                    saveSuccessMessage = null
                    scope.launch {
                        try {
                            val result = AIApiUtility.fetchModels(providerName, keyToUse)
                            fetchedModels = result

                            // Pick a verified, current model for latency & capability probe
                            val probeModel = when {
                                providerName.equals("Gemini", true) -> "gemini-flash-latest"
                                providerName.equals("Groq", true) -> "llama-3.3-70b-versatile"
                                providerName.equals("OpenAI", true) -> "gpt-4o-mini"
                                providerName.equals("DeepSeek", true) -> "deepseek-chat"
                                providerName.equals("Anthropic", true) -> "claude-3-5-sonnet-20241022"
                                providerName.contains("Ashna", true) || providerName.contains("Asana", true) -> "ashna-x1"
                                else -> if (result.isNotEmpty()) {
                                    result.firstOrNull { it.first == activeModel }?.first
                                        ?: result.firstOrNull { it.second }?.first
                                        ?: result.first().first
                                } else activeModel.ifBlank { "default" }
                            }
                            val testRes = com.example.llm.LlmClient.testConnection(providerName, keyToUse, probeModel)
                            connectionDiagnostics = testRes

                            if (result.isEmpty()) {
                                val errDetail = if (!testRes.success && testRes.error != null) {
                                    testRes.error.formatHumanMessage()
                                } else {
                                    "No models found. Please check your API key."
                                }
                                fetchError = "❌ Invalid API Key for $providerName: $errDetail"
                                fetchedModels = emptyList()
                                fetchSuccessMessage = null
                            } else {
                                fetchSuccessMessage = if (testRes.success) {
                                    "✓ Connected to $providerName! (${testRes.latencyMs}ms latency • ${result.size} models loaded)"
                                } else {
                                    "✓ Connected to $providerName! (${result.size} models loaded)"
                                }
                                fetchError = null
                                
                                // Auto-select activeModel if current is empty or not in fetched list
                                if (activeModel.isBlank() || fetchedModels.none { it.first.equals(activeModel, ignoreCase = true) }) {
                                    val defaultModel = if (providerName.contains("OpenCode", ignoreCase = true)) {
                                        fetchedModels.firstOrNull { it.first == "glm-5.1" }?.first
                                            ?: fetchedModels.firstOrNull { it.first == "deepseek-v4-flash" }?.first
                                            ?: fetchedModels.firstOrNull { it.second }?.first
                                            ?: fetchedModels.first().first
                                    } else if (providerName.contains("Ashna", ignoreCase = true)) {
                                        fetchedModels.firstOrNull { it.first.contains(activeModel, ignoreCase = true) || (activeModel.isNotBlank() && activeModel.contains(it.first, ignoreCase = true)) }?.first
                                            ?: (if (activeModel.contains("deepseek", ignoreCase = true)) fetchedModels.firstOrNull { it.first.contains("deepseek", ignoreCase = true) }?.first else null)
                                            ?: fetchedModels.firstOrNull { it.first.equals("deepseek-v4.1-flash", ignoreCase = true) }?.first
                                            ?: fetchedModels.firstOrNull { it.first.equals("ashna-x1", ignoreCase = true) }?.first
                                            ?: fetchedModels.firstOrNull { it.first.contains("ashna-x1", ignoreCase = true) }?.first
                                            ?: fetchedModels.firstOrNull { it.second }?.first
                                            ?: fetchedModels.first().first
                                    } else if (providerName.contains("inception", ignoreCase = true)) {
                                        fetchedModels.firstOrNull { it.first.equals("mercury-2.5", ignoreCase = true) }?.first
                                            ?: fetchedModels.firstOrNull { it.first.contains("mercury", ignoreCase = true) }?.first
                                            ?: fetchedModels.firstOrNull { it.second }?.first
                                            ?: fetchedModels.first().first
                                    } else if (providerName.contains("atria", ignoreCase = true)) {
                                        fetchedModels.firstOrNull { it.first.equals("Atria-Dawn-Preview", ignoreCase = true) }?.first
                                            ?: fetchedModels.firstOrNull { it.first.contains("dawn", ignoreCase = true) }?.first
                                            ?: fetchedModels.firstOrNull { it.second }?.first
                                            ?: fetchedModels.first().first
                                    } else {
                                        fetchedModels.firstOrNull { it.second }?.first ?: fetchedModels.first().first
                                    }
                                    activeModel = defaultModel
                                }
                                
                                // Auto-save the valid keys, model, and fallback switch
                                val cleanedKeys = apiKeys.map { AIApiUtility.sanitizeKey(it) }.filter { it.isNotBlank() }
                                ProviderSettingsManager.saveState(ProviderState(
                                    providerName = providerName,
                                    apiKeys = cleanedKeys,
                                    activeModel = activeModel,
                                    buildModel = activeModel,
                                    currentKeyIndex = 0,
                                    allowFallbackToOtherProviders = allowFallbackToOtherProviders
                                ))
                            }
                        } catch (e: Exception) {
                            fetchError = "❌ ${e.message ?: "Connection failed"}"
                            fetchSuccessMessage = null
                            val fallbackList = ProviderProfile.getById(providerName).defaultFallbackModels.map { Pair(it, true) }
                            if (fallbackList.isNotEmpty()) {
                                fetchedModels = fallbackList
                                if (activeModel.isBlank() || fetchedModels.none { it.first.equals(activeModel, ignoreCase = true) }) {
                                    activeModel = fallbackList.first().first
                                }
                            }
                            val cleanedKeys = apiKeys.map { AIApiUtility.sanitizeKey(it) }.filter { it.isNotBlank() }
                            ProviderSettingsManager.saveState(ProviderState(
                                providerName = providerName,
                                apiKeys = cleanedKeys,
                                activeModel = activeModel,
                                buildModel = activeModel,
                                currentKeyIndex = 0,
                                allowFallbackToOtherProviders = allowFallbackToOtherProviders
                            ))
                        } finally {
                            isFetching = false
                        }
                    }
                } else {
                    fetchError = "Please enter an API Key first."
                }
            },
            shape = RoundedCornerShape(8.dp),
            color = SaifTheme.colors.surfaceCard,
            border = BorderStroke(1.dp, PrimaryAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFA855F7), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting & fetching models from $providerName...", color = Color(0xFFA855F7), fontSize = 14.sp)
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection & Fetch Models", color = Color(0xFFA855F7), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        
        if (fetchSuccessMessage != null) {
            Text(fetchSuccessMessage!!, color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (fetchError != null) {
            Text(fetchError!!, color = Color.Red, fontSize = 12.sp)
        }

        if (connectionDiagnostics != null && connectionDiagnostics!!.success) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("⚡ Latency: ${connectionDiagnostics!!.latencyMs}ms", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Target: ${connectionDiagnostics!!.model}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    val limits = connectionDiagnostics!!.learnedLimits
                    if (limits != null) {
                        Text("Context: ${limits.contextWindowTokens / 1000}k tokens • Max Output: ${limits.maxOutputTokens} tokens", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }
            }
        }

        // Display fetched models ONLY after user enters key and fetches models
        if (fetchedModels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val freeCount = fetchedModels.count { it.second }
                    Text(
                        text = "Available Models ($freeCount FREE)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textPrimary
                    )
                    Text(
                        text = "Tap to select",
                        fontSize = 11.5.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Show free models first, then other models
                val freeModels = fetchedModels.filter { it.second }
                val paidModels = fetchedModels.filter { !it.second }
                val displayList = (freeModels + paidModels).take(20)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    displayList.forEach { (name, isFree) ->
                        val isSelected = activeModel.equals(name, ignoreCase = true)
                        Surface(
                            onClick = {
                                activeModel = name
                                buildModel = name
                                fetchError = null
                                val cleanedKeys = apiKeys.map { it.trim() }.filter { it.isNotBlank() }
                                ProviderSettingsManager.saveState(ProviderState(
                                    providerName = providerName,
                                    apiKeys = cleanedKeys,
                                    activeModel = name,
                                    buildModel = name,
                                    currentKeyIndex = 0,
                                    allowFallbackToOtherProviders = allowFallbackToOtherProviders
                                ))
                                saveSuccessMessage = if (providerName.contains("Ashna", true) && (name.contains("astra", true) || name.contains("gpt-6", true) || name.contains("claude", true))) {
                                    "✓ Model: '$name' (Tip: Requires paid credits on Ashna. Use 'ashna-x1' for free trial)"
                                } else {
                                    "✓ Model selected: '$name'"
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.22f) else SaifTheme.colors.surfaceCard,
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) Color(0xFF10B981) else if (isFree) Color(0xFF10B981).copy(alpha = 0.5f) else SaifTheme.colors.textSecondary.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.White else SaifTheme.colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isFree) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (isSelected) Color(0xFF10B981) else Color(0x2610B981),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "FREE",
                                            color = if (isSelected) Color.Black else Color(0xFF10B981),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (fetchedModels.size > 20) {
                    Surface(
                        onClick = { showAllModelsDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Browse All Models (${fetchedModels.size} found)", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Selected Model Name Input
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Selected Model", fontSize = 12.sp, color = SaifTheme.colors.textSecondary)
            TextField(
                value = activeModel,
                onValueChange = { 
                    activeModel = it
                    buildModel = it
                    saveSuccessMessage = null
                },
                placeholder = { Text("Model name", color = SaifTheme.colors.textSecondary, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SaifTheme.colors.surfaceCard,
                    unfocusedContainerColor = SaifTheme.colors.surfaceCard,
                    focusedIndicatorColor = Color(0xFF10B981),
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = SaifTheme.colors.textPrimary,
                    unfocusedTextColor = SaifTheme.colors.textPrimary
                ),
                singleLine = true
            )
        }

        if (saveSuccessMessage != null) {
            Text(saveSuccessMessage!!, color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        // Fallback policy switch
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SaifTheme.colors.surfaceCard,
            border = BorderStroke(1.dp, SaifTheme.colors.textSecondary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Allow fallback to other providers",
                        color = SaifTheme.colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "When OFF (default), SAIF AI strictly uses your selected provider and never silently switches to platform keys.",
                        color = SaifTheme.colors.textSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = allowFallbackToOtherProviders,
                    onCheckedChange = { allowFallbackToOtherProviders = it }
                )
            }
        }

        // Round 14 — backup providers + automatic switch when a limit is hit.
        BackupProvidersSection()

        // Save Button
        Button(
            onClick = {
                val cleanedKeys = apiKeys.map { AIApiUtility.sanitizeKey(it) }.filter { it.isNotBlank() }
                val firstKey = cleanedKeys.firstOrNull() ?: ""
                val detected = if (firstKey.isNotBlank()) ProviderProfile.detectProviderFromKey(firstKey, providerName) else null
                val autoName = detected?.let { if (it.displayName.equals("Google Gemini", true)) "Gemini" else it.displayName }
                val finalProv = if (autoName != null && (firstKey.startsWith("AIza") || firstKey.startsWith("AQ.") || firstKey.startsWith("gsk_") || firstKey.startsWith("sk-or-") || firstKey.startsWith("sk-ant-") || firstKey.startsWith("sk-proj-"))) {
                    autoName
                } else {
                    providerName
                }
                val finalModel = if (activeModel.isBlank() || (!finalProv.equals("Gemini", true) && activeModel.startsWith("gemini-") && !finalProv.contains("Ashna", true))) {
                    ProviderProfile.getById(finalProv).defaultFallbackModels.firstOrNull() ?: "ashna-x1"
                } else activeModel

                providerName = finalProv
                activeModel = finalModel
                ProviderSettingsManager.saveState(ProviderState(
                    providerName = finalProv,
                    apiKeys = cleanedKeys,
                    activeModel = finalModel.trim(),
                    buildModel = finalModel.trim(),
                    currentKeyIndex = 0,
                    allowFallbackToOtherProviders = allowFallbackToOtherProviders
                ))
                saveSuccessMessage = "✓ Saved: $finalProv ($finalModel) is now active!"
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Provider & Apply API", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showAllModelsDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredModels = remember(searchQuery, fetchedModels) {
            if (searchQuery.isBlank()) fetchedModels
            else fetchedModels.filter { it.first.contains(searchQuery.trim(), ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showAllModelsDialog = false },
            containerColor = SaifTheme.colors.surfaceCard,
            title = { 
                Column {
                    Text("All $providerName Models", color = SaifTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Tap any model to select and use in the app", color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search model name...", color = SaifTheme.colors.textSecondary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryAccent,
                            unfocusedBorderColor = SaifTheme.colors.cardBorder,
                            focusedTextColor = SaifTheme.colors.textPrimary,
                            unfocusedTextColor = SaifTheme.colors.textPrimary
                        )
                    )

                    Text(
                        text = "Showing ${filteredModels.size} of ${fetchedModels.size} models",
                        color = SaifTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filteredModels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No models match '$searchQuery'", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            filteredModels.forEach { (name, isFree) ->
                                val isSelected = activeModel == name
                                Surface(
                                    onClick = { 
                                        activeModel = name
                                        buildModel = name
                                        fetchError = null
                                        showAllModelsDialog = false
                                        
                                        // Auto-save model selection
                                        val cleanedKeys = apiKeys.map { it.trim() }.filter { it.isNotBlank() }
                                        ProviderSettingsManager.saveState(ProviderState(
                                            providerName = providerName,
                                            apiKeys = cleanedKeys,
                                            activeModel = name,
                                            buildModel = name,
                                            currentKeyIndex = 0,
                                            allowFallbackToOtherProviders = allowFallbackToOtherProviders
                                        ))
                                        saveSuccessMessage = "✓ Selected '$name' as active model"
                                    },
                                    color = if (isSelected) PrimaryAccent.copy(alpha = 0.25f) else SaifTheme.colors.primaryBackground,
                                    border = BorderStroke(1.dp, if (isSelected) PrimaryAccent else Color.Transparent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            if (isSelected) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = PrimaryAccent, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                text = name,
                                                color = if (isSelected) Color.White else SaifTheme.colors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        if (isFree) {
                                            Text(
                                                text = "FREE",
                                                color = Color(0xFF10B981),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.background(Color(0xFF062A1F), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllModelsDialog = false }) {
                    Text("Close", color = Color(0xFFA855F7), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
