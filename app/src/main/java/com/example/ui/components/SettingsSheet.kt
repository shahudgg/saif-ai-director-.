package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PhotoCamera
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.outlined.ChevronRight
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.local.ThemeManager
import com.example.data.local.ThemeMode
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.SubtextMuted

data class VoiceProfile(
    val id: String, 
    val name: String, 
    val description: String, 
    val emotion: String = "Warm & Natural",
    val gender: String, 
    val pitch: Float, 
    val speechRate: Float, 
    val previewHinglish: String = "Bilkul! Mai SAIF AI hoon, aapse smoothly aur natural emotion ke saath baat karne ke liye taiyaar.",
    val previewEnglish: String = "Hello, I am SAIF AI, speaking with natural clarity and emotion.",
    val targetEngineNameMatches: List<String> = emptyList()
)

val VOICE_PROFILES = listOf(
    // Round 9: the voice of SAIF AI AUTOMATION, brought into the main app as the DEFAULT voice everywhere
    // (chat replies, live baatcheet and video narration all read the same setting - see utils/SaifVoice.kt).
    VoiceProfile(
        id = "SaifAdvance",
        name = "SAIF ADVANCE VOICE",
        description = "SAIF AI ki apni advance awaaz — warm, smart aur natural (Hindi & English). Live baatcheet, chat aur video narration sab isi me.",
        emotion = "\u2728 Warm & Intelligent",
        gender = "Male",
        pitch = 1.00f,
        speechRate = 1.00f,
        previewHinglish = "Namaste! Mai SAIF Advance Voice hoon. Aapke saath naturally baat karunga, screen bhi dekh sakta hoon, aur video bhi bana sakta hoon.",
        previewEnglish = "Hello, I am the SAIF Advance Voice. Natural, warm and ready to talk, watch your screen or build your video.",
        // Best natural Indian voices first; the live engine asks a Gemini-Live provider for "Puck" instead.
        targetEngineNameMatches = listOf(
            "en-in-x-end-network", "en-in-x-ene-local", "hi-in-x-hia-network", "hi-in-x-hia-local",
            "en-in-x-cxx-network", "en_in", "hi_in"
        )
    ),
    VoiceProfile(
        id = "Kabir",
        name = "Kabir",
        description = "Deep, confident, podcast baritone (Hindi & English)",
        emotion = "🎙️ Deep & Calm",
        gender = "Male",
        pitch = 0.90f,
        speechRate = 0.95f,
        previewHinglish = "Namaste! Mai Kabir hoon. Har baat ko deeply, smoothly aur bina kisi rukawat ke explain karna mera style hai.",
        previewEnglish = "Greetings. I am Kabir. Here to provide thoughtful, authoritative guidance.",
        targetEngineNameMatches = listOf("en-in-x-end-network", "en-in-x-ene-local", "hi-in-x-hia-network", "hi-in-x-hia-local", "en_in", "hi_in")
    ),
    VoiceProfile(
        id = "Priya",
        name = "Priya",
        description = "Cheerful, sweet, bubbly & joyful (Hindi & English)",
        emotion = "✨ Cheerful & Sweet",
        gender = "Female",
        pitch = 1.12f,
        speechRate = 0.98f,
        previewHinglish = "Hi there! Mai Priya hoon! Aaiye milkar kuch naya, super interesting aur mazedaar banate hain!",
        previewEnglish = "Hi there! I am Priya, ready to bring creativity and joy to every task!",
        targetEngineNameMatches = listOf("en-in-x-cxx-network", "en-in-x-cxx-local", "hi-in-x-hic-local", "hi-in-x-hid-network", "en_in", "hi_in")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    temperature: Float,
    customSystemPrompt: String,
    onSaveSettings: (Float, String) -> Unit,
    onClearCurrentChat: () -> Unit,
    onDismiss: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val generalPrefs = remember { context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE) }
    var autoSpeak by remember { mutableStateOf(generalPrefs.getBoolean("autoSpeak", false)) }
    val themeMode by ThemeManager.themeMode.collectAsState()
    
    val prefs = remember { context.getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE) }
    
    val wakeWord by com.example.util.WakeWordManager.wakeWordFlow.collectAsState()
    val isWakeWordEnabled by com.example.util.WakeWordManager.isWakeWordEnabledFlow.collectAsState()
    var customWakeWordInput by remember(wakeWord) { mutableStateOf(wakeWord) }
    var wakeWordSavedFeedback by remember { mutableStateOf(false) }
    var wakeWordPresets by remember { mutableStateOf(com.example.util.WakeWordManager.getPresets(context)) }

    var selectedVoiceId by remember {
        // One voice for the whole app: chat, live baatcheet and video narration (see utils/SaifVoice.kt).
        mutableStateOf(com.example.utils.SaifVoice.selectedId(context))
    }
    var ttsVoices by remember { mutableStateOf(VOICE_PROFILES) }
    var ttsInstance by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    
    LaunchedEffect(Unit) {
        ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
            // Just initialize TTS to test availability if needed
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { ttsInstance?.shutdown() }
    }
    
    val authUser by com.example.data.local.AuthManager.currentUser.collectAsState()
    val isPinProtected by com.example.util.SmartConversationMemory.isPinProtected.collectAsState()
    var showCropDialog by remember { mutableStateOf(false) }
    var selectedCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showMemoryVaultDialog by remember { mutableStateOf(false) }
    var showDownloadPinDialog by remember { mutableStateOf(false) }
    var downloadPinInput by remember { mutableStateOf("") }
    var downloadPinError by remember { mutableStateOf<String?>(null) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    val displayName = authUser?.name?.ifBlank { authUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } } ?: "User"

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bmp != null) {
                    selectedCropBitmap = bmp
                    showCropDialog = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showCropDialog && selectedCropBitmap != null) {
        ImageCropEditPanel(
            bitmap = selectedCropBitmap!!,
            onCropConfirmed = { cropped ->
                showCropDialog = false
                selectedCropBitmap = null
                val userEmail = authUser?.email ?: "user"
                com.example.data.local.AuthManager.saveUserAvatar(context, userEmail, cropped)
                Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showCropDialog = false
                selectedCropBitmap = null
            }
        )
    }

    val glassBorderBrush = Brush.linearGradient(
        listOf(
            SaifTheme.colors.textPrimary.copy(alpha = 0.35f),
            Color.Transparent,
            SaifTheme.colors.textPrimary.copy(alpha = 0.1f)
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SaifTheme.colors.primaryBackground,
        contentColor = SaifTheme.colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(SaifTheme.colors.cardBorder)
            )
        },
        modifier = Modifier.fillMaxSize() // Make it full screen
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textPrimary
                    )
                    Text(
                        text = "Manage your SAIF AI experience",
                        fontSize = 13.sp,
                        color = SubtextMuted
                    )
                }
                
                // Liquid glass close button
                LiquidButton(
                    onClick = onDismiss,
                    color = SaifTheme.colors.surfaceCard,
                    glassBorderBrush = glassBorderBrush,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SaifTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile Section
            val user = authUser
            val displayName = user?.name?.ifBlank { user.email.substringBefore("@").replaceFirstChar { it.uppercase() } } ?: "User"
            val displayEmail = user?.email?.ifBlank { "user@example.com" } ?: "user@example.com"
            val initialLetter = user?.initialLetter ?: displayName.take(1).uppercase().ifBlank { "U" }
            val avatarPath = user?.profilePicturePath
            val avatarFile = avatarPath?.let { java.io.File(it) }
            val avatarBitmap = remember(avatarPath) {
                if (avatarFile != null && avatarFile.exists()) {
                    try {
                        BitmapFactory.decodeFile(avatarFile.absolutePath)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9), Color(0xFF4C1D95))
                                    )
                                )
                                .border(2.dp, Color(0xFFA855F7).copy(alpha = 0.6f), CircleShape)
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = "Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = initialLetter,
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Camera Edit Badge Icon on bottom-end
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7C3AED),
                            border = BorderStroke(1.5.dp, Color(0xFF0F172A)),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(26.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Change Profile Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Display Name with Edit Name Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaifTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = { showEditNameDialog = true },
                            shape = CircleShape,
                            color = SaifTheme.colors.primaryBackground.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, SaifTheme.colors.cardBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = com.example.ui.theme.PrimaryAccent,
                                modifier = Modifier.padding(5.dp).size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = displayEmail,
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (user?.isAdmin == true) {
                            Surface(
                                color = Color(0xFF1E1B4B),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(text = "👑", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Admin",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFF4C1D95).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Free User",
                                    color = Color(0xFFA855F7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Sign Out button in Profile Card
                        Surface(
                            onClick = { showSignOutConfirmDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = StatusError.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Sign Out",
                                    tint = StatusError,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sign Out",
                                    color = StatusError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Appearance
            SectionHeader("APPEARANCE")
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(SaifTheme.colors.surfaceCard, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                val segmentWidth = maxWidth / 3
                val segmentWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { segmentWidth.toPx() }
                
                val targetOffsetX = when (themeMode) {
                    ThemeMode.LIGHT -> 0
                    ThemeMode.DARK -> segmentWidthPx.roundToInt()
                    ThemeMode.SYSTEM -> (segmentWidthPx * 2).roundToInt()
                }

                val offset by animateIntOffsetAsState(
                    targetValue = IntOffset(targetOffsetX, 0),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "SegmentOffset"
                )

                // The sliding pill
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .offset { offset }
                        .background(com.example.ui.theme.PrimaryAccent, RoundedCornerShape(12.dp))
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    val options = listOf(
                        Triple("Light", Icons.Default.LightMode, ThemeMode.LIGHT),
                        Triple("Dark", Icons.Default.DarkMode, ThemeMode.DARK),
                        Triple("System", Icons.Default.Computer, ThemeMode.SYSTEM)
                    )
                    options.forEach { (title, icon, mode) ->
                        val isSelected = themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { ThemeManager.setTheme(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) Color.White else SaifTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else SaifTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Wake Up Word (Hotword) Section
            SectionHeader("WAKE UP WORD (हे सैफ़ / HOTWORD)")
            Spacer(modifier = Modifier.height(8.dp))

            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Toggle Active
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✨", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wake Up Word Active",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaifTheme.colors.textPrimary
                            )
                            Text(
                                text = "Bolkar voice assistant chatbox popup open karein",
                                fontSize = 12.5.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = isWakeWordEnabled,
                            onCheckedChange = {
                                com.example.util.WakeWordManager.setWakeWordEnabled(context, it)
                                if (it) {
                                    com.example.util.WakeWordAudioDetector.start(context)
                                } else {
                                    com.example.util.WakeWordAudioDetector.stop()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = com.example.ui.theme.PrimaryAccent,
                                uncheckedThumbColor = SaifTheme.colors.textSecondary,
                                uncheckedTrackColor = SaifTheme.colors.primaryBackground
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Customize Wake Up Word",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SaifTheme.colors.textPrimary
                    )
                    Text(
                        text = "Default me 'Hey Saif' rahega. Aap yaha jo bhi naam ya shabd likhenge, wahi bolne par app ka chatbox popup screen ke neeche se open ho jayega.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = SaifTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    // Text input field with Save button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customWakeWordInput,
                            onValueChange = { 
                                customWakeWordInput = it 
                                wakeWordSavedFeedback = false
                            },
                            placeholder = { Text("e.g. Hey Saif, Hey Jarvis", fontSize = 13.sp, color = SaifTheme.colors.textSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = SaifTheme.colors.cardBorder,
                                focusedTextColor = SaifTheme.colors.textPrimary,
                                unfocusedTextColor = SaifTheme.colors.textPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (customWakeWordInput.isNotBlank()) {
                                    val cleanInput = customWakeWordInput.trim()
                                    com.example.util.WakeWordManager.setWakeWord(context, cleanInput)
                                    val updated = com.example.util.WakeWordManager.addPreset(context, cleanInput)
                                    wakeWordPresets = updated
                                    wakeWordSavedFeedback = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.PrimaryAccent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (wakeWordSavedFeedback) {
                        Text(
                            text = "✓ Wake up word saved: '$wakeWord'",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset chips
                    Text(
                        text = "Quick Presets:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SaifTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        wakeWordPresets.forEach { preset ->
                            val isCurrent = wakeWord.equals(preset, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isCurrent) com.example.ui.theme.PrimaryAccent else SaifTheme.colors.primaryBackground,
                                border = if (isCurrent) null else androidx.compose.foundation.BorderStroke(1.dp, SaifTheme.colors.cardBorder),
                                modifier = Modifier.clickable {
                                    customWakeWordInput = preset
                                    com.example.util.WakeWordManager.setWakeWord(context, preset)
                                    wakeWordSavedFeedback = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        color = if (isCurrent) Color.White else SaifTheme.colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrent) Color.White.copy(alpha = 0.25f) else SaifTheme.colors.cardBorder.copy(alpha = 0.6f))
                                            .clickable {
                                                val updated = com.example.util.WakeWordManager.deletePreset(context, preset)
                                                wakeWordPresets = updated
                                                if (isCurrent && updated.isNotEmpty()) {
                                                    val nextWord = updated.first()
                                                    customWakeWordInput = nextWord
                                                    com.example.util.WakeWordManager.setWakeWord(context, nextWord)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete preset $preset",
                                            tint = if (isCurrent) Color.White else SaifTheme.colors.textSecondary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Voice & TTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("VOICE & TTS (SAIF ADVANCE VOICE)", modifier = Modifier.padding(bottom = 0.dp))
                Text(
                    text = "${VOICE_PROFILES.size} AI Voices \u2022 Default: SAIF ADVANCE VOICE",
                    fontSize = 11.sp,
                    color = Color(0xFFA855F7)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = SaifTheme.colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-speak replies",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaifTheme.colors.textPrimary
                            )
                            Text(
                                text = "AI jawab ko bolkar sunaye",
                                fontSize = 13.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = autoSpeak,
                            onCheckedChange = { 
                                autoSpeak = it
                                generalPrefs.edit().putBoolean("autoSpeak", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = com.example.ui.theme.PrimaryAccent,
                                uncheckedThumbColor = SaifTheme.colors.textSecondary,
                                uncheckedTrackColor = SaifTheme.colors.primaryBackground
                            )
                        )
                    }

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)


                    var previewLanguage by remember { mutableStateOf("Hinglish") }
                    val isSpeakingGlobal by com.example.utils.TTSManager.isSpeaking.collectAsState()
                    val activeSpeakingText by com.example.utils.TTSManager.currentlySpeakingText.collectAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Choose AI Voice & Emotion",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaifTheme.colors.textPrimary
                            )
                            Text(
                                text = "Smooth natural Hinglish & English",
                                fontSize = 11.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }

                        // Preview Language Selector Toggle
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { previewLanguage = "Hinglish" },
                                shape = RoundedCornerShape(12.dp),
                                color = if (previewLanguage == "Hinglish") com.example.ui.theme.PrimaryAccent else SaifTheme.colors.primaryBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (previewLanguage == "Hinglish") Color(0xFFA855F7) else Color(0x33FFFFFF))
                            ) {
                                Text(
                                    text = "🇮🇳 Hinglish",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (previewLanguage == "Hinglish") Color.White else SaifTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                onClick = { previewLanguage = "English" },
                                shape = RoundedCornerShape(12.dp),
                                color = if (previewLanguage == "English") com.example.ui.theme.PrimaryAccent else SaifTheme.colors.primaryBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (previewLanguage == "English") Color(0xFFA855F7) else Color(0x33FFFFFF))
                            ) {
                                Text(
                                    text = "🌐 English",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (previewLanguage == "English") Color.White else SaifTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    ttsVoices.forEach { voice ->
                        val isActive = voice.id == selectedVoiceId
                        val testPhrase = if (previewLanguage == "Hinglish") voice.previewHinglish else voice.previewEnglish
                        val isThisVoicePreviewing = isSpeakingGlobal && (activeSpeakingText == testPhrase || activeSpeakingText?.contains(voice.name.take(4)) == true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedVoiceId = voice.id
                                    prefs.edit().putString("selected_voice_id", voice.id).apply()
                                    // Keep the video director and the live engine on the same voice.
                                    com.example.utils.SaifVoice.select(context, voice.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) com.example.ui.theme.PrimaryAccent else SaifTheme.colors.primaryBackground)
                            ) {
                                Text(
                                    text = voice.name.take(1),
                                    color = if (isActive) Color.White else SaifTheme.colors.textSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = voice.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SaifTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0x22A855F7),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x66A855F7))
                                    ) {
                                        Text(
                                            text = voice.emotion,
                                            color = Color(0xFFC084FC),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = com.example.ui.theme.PrimaryAccent,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Active",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${voice.gender} • ${voice.description}",
                                    fontSize = 11.5.sp,
                                    color = SaifTheme.colors.textSecondary
                                )
                            }
                            IconButton(onClick = {
                                if (isThisVoicePreviewing) {
                                    com.example.utils.TTSManager.stop()
                                } else {
                                    com.example.utils.TTSManager.previewVoice(context, testPhrase, voice.id)
                                }
                            }) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isThisVoicePreviewing) Color(0xFFEF4444).copy(alpha = 0.2f) else SaifTheme.colors.primaryBackground)
                                ) {
                                    Icon(
                                        imageVector = if (isThisVoicePreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isThisVoicePreviewing) "Stop" else "Play",
                                        tint = if (isThisVoicePreviewing) Color(0xFFEF4444) else SaifTheme.colors.textPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Memory
            SectionHeader("AI MEMORY")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { showMemoryVaultDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = com.example.ui.theme.PrimaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SAIF AI Memories",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaifTheme.colors.textPrimary
                            )
                            if (isPinProtected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "PIN Protected",
                                    tint = com.example.ui.theme.PrimaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isPinProtected) "Private & PIN Protected" else "Things AI learned about you",
                            fontSize = 13.sp,
                            color = SaifTheme.colors.textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = SaifTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Round 9: SAIF AI AUTOMATION's own settings live here now (auto language, persona, silent execution,
            // 3D orb, floating overlay, permissions, owner voice lock). Nothing below repeats an existing section:
            // the API key stays in AI PROVIDER & MODELS, the memories stay in AI MEMORY, the theme stays in
            // APPEARANCE and the voice stays in VOICE & TTS.
            SaifLiveSettingsSection(glassBorderBrush)

            // Data & Privacy
            SectionHeader("DATA & PRIVACY")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPinProtected) {
                                    downloadPinInput = ""
                                    downloadPinError = null
                                    showDownloadPinDialog = true
                                } else {
                                    val file = com.example.util.SmartConversationMemory.exportAllUserData(context)
                                    if (file != null) {
                                        com.example.util.SmartConversationMemory.shareExportedFile(context, file)
                                        Toast.makeText(context, "Data ready to download/share!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to export data", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = SaifTheme.colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Download Data",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaifTheme.colors.textPrimary
                            )
                            Text(
                                text = "Export your learned memories & account profile in 1 click",
                                fontSize = 12.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = SaifTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            SectionHeader("AI PROVIDER & MODELS")
            AiProviderSettingsInline()
            Spacer(modifier = Modifier.height(32.dp))

            // Permission Manager Section
            PermissionManagerSection(glassBorderBrush = glassBorderBrush)
            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "SAIF AI is created by SAIF. Version 1.0.0",
                fontSize = 11.sp,
                color = SubtextMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = displayName,
            onDismiss = { showEditNameDialog = false },
            onNameUpdated = { }
        )
    }

    if (showMemoryVaultDialog) {
        SaifAiMemoryDialog(
            onDismiss = { showMemoryVaultDialog = false }
        )
    }

    if (showDownloadPinDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Memory PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your Saif AI data is protected with a PIN lock. Enter your 4-digit PIN to download your personal data:",
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = downloadPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) downloadPinInput = it
                            downloadPinError = null
                        },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (downloadPinError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(downloadPinError!!, color = StatusError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (com.example.util.SmartConversationMemory.verifyPin(downloadPinInput)) {
                            showDownloadPinDialog = false
                            val file = com.example.util.SmartConversationMemory.exportAllUserData(context)
                            if (file != null) {
                                com.example.util.SmartConversationMemory.shareExportedFile(context, file)
                                Toast.makeText(context, "Data ready to download/share!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to export data", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            downloadPinError = "Incorrect PIN"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Verify & Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            title = {
                Text("Sign Out", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to sign out of SAIF AI?", fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        onDismiss()
                        com.example.data.local.AuthManager.signOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = SaifTheme.colors.surfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier.padding(bottom = 8.dp)) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = SubtextMuted,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

@Composable
fun AppearanceOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glassBorderBrush: Brush
) {
    val backgroundColor = if (isSelected) com.example.ui.theme.PrimaryAccent else SaifTheme.colors.surfaceCard
    val contentColor = if (isSelected) Color.White else SaifTheme.colors.textPrimary
    
    LiquidSurface(
        onClick = onClick,
        color = backgroundColor,
        glassBorderBrush = if (isSelected) null else glassBorderBrush,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LiquidSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color,
    glassBorderBrush: Brush?,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "LiquidScale"
    )

    Surface(
        shape = shape,
        color = color,
        border = if (glassBorderBrush != null) androidx.compose.foundation.BorderStroke(1.5.dp, glassBorderBrush) else null,
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource, 
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Box(modifier = Modifier.animateContentSize()) {
            content()
        }
    }
}

@Composable
fun LiquidButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    color: Color,
    glassBorderBrush: Brush?,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
        label = "ButtonScale"
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = color,
        border = if (glassBorderBrush != null) androidx.compose.foundation.BorderStroke(1.5.dp, glassBorderBrush) else null,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
