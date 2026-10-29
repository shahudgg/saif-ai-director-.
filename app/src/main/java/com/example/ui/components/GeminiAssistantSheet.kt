package com.example.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AIApiUtility
import com.example.data.remote.GeminiService
import com.example.ui.theme.PrimaryAccent
import com.example.util.DeviceActionManager
import com.example.util.DeviceActionParser
import com.example.util.WakeWordManager
import com.example.utils.TTSManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AssistantSheetState {
    LISTENING,
    THINKING,
    RESPONDED,
    TYPING
}

/**
 * Gemini-style Floating Assistant Chatbox that slides up from the bottom of the screen.
 * - Starts in listening mode with live waveform animation.
 * - Tapping chatbox brings up soft keyboard and positions it comfortably above the keyboard.
 * - Shows AI response in a sleek Gemini popup card with voice speech and device action execution.
 * - Tapping anywhere on the unwanted screen area dismisses the chatbox.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeminiAssistantSheet(
    initialPrompt: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }

    var mode by remember { mutableStateOf(AssistantSheetState.LISTENING) }
    var textInput by remember { mutableStateOf("") }
    var spokenTranscription by remember { mutableStateOf("") }
    var assistantResponse by remember { mutableStateOf<String?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableFloatStateOf(0f) }

    val speechEngine = remember { DirectSpeechInputEngine(context) }

    // Multi-action system prompt to execute device commands (YouTube, WhatsApp, Instagram, Camera, Flashlight, Calls, SMS, etc.)
    val liveActionSystemPrompt = """
You are SAIF AI, an advanced native Android AI assistant with full device automation capabilities.
When the user asks to perform device actions, you execute them directly. If multiple tasks are chained together, execute all of them in sequence!
Include an action block at the beginning of your response in this exact format:
<<<ACTIONS
[
  {"type": "YOUTUBE", "query": "Free Fire gameplay"},
  {"type": "FLASHLIGHT", "enable": true}
]
ACTIONS>>>
Supported action types:
- OPEN_APP: {"type": "OPEN_APP", "app": "<app_name>"}
- YOUTUBE: {"type": "YOUTUBE", "query": "<search_query>"}
- YOUTUBE_CONTROL: {"type": "YOUTUBE_CONTROL", "command": "play|pause|first|next"}
- WHATSAPP: {"type": "WHATSAPP", "recipient": "<name_or_number>", "message": "<msg>"}
- WHATSAPP_CALL: {"type": "WHATSAPP_CALL", "recipient": "<name>"}
- INSTAGRAM: {"type": "INSTAGRAM", "subAction": "profile|messages|camera|story|open"}
- CALL: {"type": "CALL", "target": "<name_or_number>", "direct": true}
- SMS: {"type": "SMS", "recipient": "<name_or_number>", "message": "<msg>"}
- CAMERA: {"type": "CAMERA", "mode": "photo|video"}
- FLASHLIGHT: {"type": "FLASHLIGHT", "enable": true|false}
- WIFI: {"type": "WIFI"}
- BLUETOOTH: {"type": "BLUETOOTH"}
- SETTINGS: {"type": "SETTINGS"}
- NOTES: {"type": "NOTES", "text": "<note_content>"}
- GALLERY: {"type": "GALLERY"}
- FILES: {"type": "FILES"}
- NOTIFICATIONS: {"type": "NOTIFICATIONS", "filter": "<optional_app_filter>"}
- VOLUME: {"type": "VOLUME", "direction": "up|down|mute"}
- ACCESSIBILITY_CLICK: {"type": "ACCESSIBILITY_CLICK", "target": "<text_or_button_to_click>"}
- ACCESSIBILITY_SCROLL: {"type": "ACCESSIBILITY_SCROLL", "direction": "down|up"}
- ACCESSIBILITY_NAV: {"type": "ACCESSIBILITY_NAV", "navType": "back|home|recents"}
- WEB_SEARCH: {"type": "WEB_SEARCH", "query": "<search_query>"}
- ALARM: {"type": "ALARM", "hour": <0-23>, "minute": <0-59>, "message": "<label>"}

After the action block (or if no action needed), give a short, natural, friendly 1-2 sentence spoken reply in Hindi/Hinglish/English confirming the answer or action. Do NOT output code or JSON in the spoken reply.
    """.trimIndent()

    fun handleSend(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        
        mode = AssistantSheetState.THINKING
        spokenTranscription = trimmed
        keyboardController?.hide()
        speechEngine.stopListening()

        scope.launch {
            val responseResult = AIApiUtility.executeWithFallback(
                prompt = trimmed,
                mode = "general",
                history = emptyList(),
                customSystemPrompt = liveActionSystemPrompt,
                base64Images = emptyList(),
                onChunk = { /* streaming */ }
            )
            val hasError = !responseResult.isSuccess
            val rawReply = if (hasError) {
                "⚠️ " + (responseResult.exceptionOrNull()?.message ?: "Response nahi mil saka. Kripya Settings me apna API key check karein.")
            } else {
                responseResult.getOrNull().orEmpty()
            }
            val parsed = if (hasError) null else DeviceActionParser.parse(rawReply, trimmed)
            val cleanReply = if (hasError) {
                rawReply
            } else {
                parsed?.cleanSpokenText?.ifBlank {
                    if (parsed.actions.isNotEmpty()) "Theek hai, sabhi actions execute kar diye hain." else rawReply
                } ?: rawReply
            }

            assistantResponse = cleanReply
            mode = AssistantSheetState.RESPONDED

            // Execute device actions sequentially if any
            if (parsed != null && parsed.actions.isNotEmpty()) {
                launch {
                    DeviceActionManager.executeActionsSequentially(context, parsed.actions)
                }
            }

            // Speak the reply out loud
            isSpeaking = true
            TTSManager.speak(
                text = cleanReply,
                context = context,
                onStart = { isSpeaking = true },
                onDone = { isSpeaking = false }
            )
        }
    }

    fun startListening() {
        mode = AssistantSheetState.LISTENING
        spokenTranscription = ""
        speechEngine.startListening(
            onRmsChanged = { rms -> voiceRms = rms },
            onInterimResult = { interim -> spokenTranscription = interim },
            onFinalResult = { finalResult ->
                if (finalResult.isNotBlank()) {
                    handleSend(finalResult)
                } else {
                    if (mode == AssistantSheetState.LISTENING) {
                        mode = AssistantSheetState.TYPING
                    }
                }
            },
            onStateChanged = { listening ->
                if (!listening && mode == AssistantSheetState.LISTENING && spokenTranscription.isBlank()) {
                    mode = AssistantSheetState.TYPING
                }
            }
        )
    }

    // Auto-start listening on mount or execute initialPrompt if passed
    LaunchedEffect(Unit) {
        if (!initialPrompt.isNullOrBlank()) {
            spokenTranscription = initialPrompt
            handleSend(initialPrompt)
        } else {
            startListening()
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val dismissWithAnimation: () -> Unit = {
        scope.launch {
            speechEngine.stopListening()
            TTSManager.stop()
            keyboardController?.hide()
            isVisible = false
            delay(220)
            onDismiss()
        }
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.52f else 0f,
        animationSpec = tween(220),
        label = "bgAlpha"
    )

    DisposableEffect(Unit) {
        onDispose {
            speechEngine.stopListening()
            TTSManager.stop()
        }
    }

    // Unwanted area outside the card: Dismisses the assistant on tap
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    dismissWithAnimation()
                })
            }
    ) {
        // Bottom Assistant Container: animated from bottom, positioned above keyboard via imePadding()
        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    // Consume taps inside the chatbox so clicking the chatbox doesn't dismiss it
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume */ }
                    }
            ) {
            // 1. POPUP ANSWER CARD (If AI has responded or is thinking)
            AnimatedVisibility(
                visible = mode == AssistantSheetState.THINKING || mode == AssistantSheetState.RESPONDED,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xEB141824),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF00E5FF))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✦", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "SAIF AI",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            // TTS Speaker control
                            if (mode == AssistantSheetState.RESPONDED && !assistantResponse.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        if (isSpeaking) {
                                            TTSManager.stop()
                                            isSpeaking = false
                                        } else {
                                            isSpeaking = true
                                            TTSManager.speak(
                                                text = assistantResponse ?: "",
                                                context = context,
                                                onStart = { isSpeaking = true },
                                                onDone = { isSpeaking = false }
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = "Voice Speech",
                                        tint = if (isSpeaking) Color(0xFFEC4899) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(assistantResponse ?: ""))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy text",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (mode == AssistantSheetState.THINKING) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (spokenTranscription.isNotBlank()) "\"$spokenTranscription\" soch raha hoon..." else "Thinking...",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp
                                )
                            }
                        } else if (!assistantResponse.isNullOrBlank()) {
                            Text(
                                text = assistantResponse ?: "",
                                color = Color(0xFFF1F5F9),
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }

            // 2. MAIN BOTTOM CHATBOX (Gemini floating input bar)
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xF00F1420),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Header Status Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Gemini glowing star
                        Text(
                            text = "✨",
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                AssistantSheetState.LISTENING -> "Listening... (Bolna shuru karein)"
                                AssistantSheetState.THINKING -> "Thinking..."
                                AssistantSheetState.TYPING -> "Type your question..."
                                AssistantSheetState.RESPONDED -> "Ready for next question"
                            },
                            color = when (mode) {
                                AssistantSheetState.LISTENING -> Color(0xFF38BDF8)
                                AssistantSheetState.THINKING -> Color(0xFFA78BFA)
                                else -> Color(0xFF94A3B8)
                            },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        // Switch mode button (Keyboard <-> Mic)
                        IconButton(
                            onClick = {
                                if (mode == AssistantSheetState.LISTENING) {
                                    speechEngine.stopListening()
                                    mode = AssistantSheetState.TYPING
                                    scope.launch {
                                        delay(100)
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                } else {
                                    keyboardController?.hide()
                                    startListening()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (mode == AssistantSheetState.LISTENING) Icons.Default.Keyboard else Icons.Default.Mic,
                                contentDescription = "Toggle Keyboard / Voice",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close button
                        IconButton(
                            onClick = {
                                dismissWithAnimation()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 3. LIVE VOICE WAVE OR TEXT INPUT
                    if (mode == AssistantSheetState.LISTENING) {
                        // Animated sound wave bars reacting to RMS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clickable {
                                    // Clicking chatbox when listening switches to keyboard!
                                    speechEngine.stopListening()
                                    mode = AssistantSheetState.TYPING
                                    scope.launch {
                                        delay(100)
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val heights = listOf(14.dp, 26.dp, 38.dp, 48.dp, 34.dp, 20.dp, 12.dp)
                            val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
                            
                            heights.forEachIndexed { index, baseHeight ->
                                val waveAnim by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400 + index * 60, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "bar_$index"
                                )
                                val dynamicHeight = (baseHeight * (0.4f + (voiceRms / 10f) * 0.6f) * waveAnim).coerceIn(8.dp, 50.dp)
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .width(5.dp)
                                        .height(dynamicHeight)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF00E5FF), Color(0xFF8B5CF6))
                                            )
                                        )
                                )
                            }
                        }

                        if (spokenTranscription.isNotBlank()) {
                            Text(
                                text = "\"$spokenTranscription\"",
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        } else {
                            Text(
                                text = "Bolna shuru karein ya likhne ke liye yaha tap karein...",
                                color = Color(0xFF64748B),
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else {
                        // TYPING / TEXT FIELD INPUT
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1E2538))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (textInput.isNotBlank()) {
                                        val q = textInput
                                        textInput = ""
                                        handleSend(q)
                                    }
                                }),
                                decorationBox = { innerTextField ->
                                    if (textInput.isEmpty()) {
                                        Text(
                                            text = "Ask SAIF anything...",
                                            color = Color(0xFF64748B),
                                            fontSize = 14.5.sp
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                            )

                            if (textInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val q = textInput
                                        textInput = ""
                                        handleSend(q)
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        startListening()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Switch to Mic",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
