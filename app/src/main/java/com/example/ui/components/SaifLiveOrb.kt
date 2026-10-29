package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvis.assistant.SaifAutomation

/**
 * SAIF AI LIVE ORB — the 3D orb of SAIF AI AUTOMATION, now living inside SAIF AI's own live-conversation feature.
 *
 * The orb itself is still the real `orb.html` three.js scene from the automation engine (so the power and the look
 * are unchanged); only the place and the surrounding UI are SAIF AI's. It is driven by [SaifAutomation]'s state
 * flows, so the same orb works whether the engine talks to Gemini Live or to any other provider through the host.
 *
 * Replaces the old `FloatingVoiceOverlay` sparkle animation in both the in-app live panel and the floating window.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SaifLiveOrb(
    modifier: Modifier = Modifier,
    isLightMode: Boolean = false,
    compact: Boolean = false,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val state by SaifAutomation.orbState.collectAsState()
    val level by SaifAutomation.audioLevel.collectAsState()
    val freq by SaifAutomation.audioFrequency.collectAsState()
    val status by SaifAutomation.connectionStatus.collectAsState()

    var ready by remember { mutableStateOf(false) }
    val pendingState = remember { mutableStateOf<String?>(null) }

    // `orbKey` is the exact state name orb.html understands (idle/listening/thinking/speaking).
    val orbName = state.orbKey

    AndroidView(
        modifier = modifier
            .pointerInteropFilter { ev ->
                when (ev.action) {
                    MotionEvent.ACTION_UP -> {
                        if (ev.eventTime - ev.downTime > 420L) onLongPress() else onTap()
                        true
                    }
                    MotionEvent.ACTION_DOWN -> true
                    else -> false
                }
            },
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                val s: WebSettings = settings
                s.javaScriptEnabled = true
                s.domStorageEnabled = true
                s.allowFileAccess = true
                s.allowContentAccess = true
                @Suppress("DEPRECATION")
                s.allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                s.allowUniversalAccessFromFileURLs = true
                s.loadWithOverviewMode = true
                s.useWideViewPort = true
                s.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        ready = true
                        evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setTheme('${if (isLightMode) "light" else "dark"}');", null)
                        pendingState.value?.let { evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setState('$it');", null) }
                        pendingState.value = null
                    }
                }
                webChromeClient = object : WebChromeClient() {}
                // embedded=1 hides the orb page's own chrome: SAIF AI draws the buttons around it.
                loadUrl("file:///android_asset/orb.html?embedded=1")
            }
        },
        update = { wv ->
            if (!ready) { pendingState.value = orbName; return@AndroidView }
            wv.evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setState('$orbName');", null)
            wv.evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setExternalAudio(${level.coerceIn(0f, 1f)}, ${freq.bass.coerceIn(0f, 1f)}, ${freq.high.coerceIn(0f, 1f)});", null)
        }
    )

    if (!compact) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            StatusPill(status)
        }
    }
}

/** Small glass pill showing the engine's connection state, in SAIF AI's colours. */
@Composable
private fun StatusPill(status: String) {
    val live = status.equals("LIVE", true)
    val bad = status.contains("ERR", true) || status.contains("FAIL", true)
    val tint = when {
        bad -> androidx.compose.ui.graphics.Color(0xFFEF4444)
        live -> androidx.compose.ui.graphics.Color(0xFF22C55E)
        else -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
    }
    val pulse = rememberInfiniteTransition(label = "orb_status_pulse")
    val a by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(androidx.compose.ui.graphics.Color(0x33000000), androidx.compose.ui.graphics.Color(0x55000000))
                )
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tint.copy(alpha = a)))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (live) "SAIF AI LIVE" else status.uppercase(),
                color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * The full-screen live conversation panel opened from the purple mic: the automation orb in the middle, live
 * captions under it, and SAIF AI's own controls (mute, screen share, stop) around it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SaifLiveConversationPanel(
    isLightMode: Boolean,
    onClose: () -> Unit,
    onRequestScreenShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userText by SaifAutomation.userTranscript.collectAsState()
    val aiText by SaifAutomation.assistantTranscript.collectAsState()
    val state by SaifAutomation.orbState.collectAsState()
    val sharing by SaifAutomation.isScreenSharing.collectAsState()
    val mode by SaifAutomation.liveMode.collectAsState()
    var muted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { SaifAutomation.setMicMuted(false) } }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFF07060F),
                        androidx.compose.ui.graphics.Color(0xFF150C2E),
                        androidx.compose.ui.graphics.Color(0xFF07060F)
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SAIF AI LIVE", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                    Text(
                        text = if (mode == SaifAutomation.LiveMode.SCREEN_SHARE) "Screen dekh kar madad kar raha hai" else "Normal baatcheet",
                        color = androidx.compose.ui.graphics.Color(0xFFA78BFA), fontSize = 11.sp
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0x33FFFFFF))
                        .pointerInteropFilter { ev -> if (ev.action == MotionEvent.ACTION_UP) { onClose(); true } else ev.action == MotionEvent.ACTION_DOWN },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Close, "Band karein", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
            }

            // The orb: the real automation 3D orb, hosted by SAIF AI.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                SaifLiveOrb(
                    modifier = Modifier.size(if (sharing) 300.dp else 340.dp),
                    isLightMode = isLightMode,
                    onTap = {
                        if (state == com.jarvis.assistant.data.model.ConversationState.SPEAKING) SaifAutomation.interrupt()
                        else { muted = !muted; SaifAutomation.setMicMuted(muted) }
                    },
                    onLongPress = { SaifAutomation.stopLive(context); onClose() }
                )
            }

            // Live captions.
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = userText.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Text(userText, color = androidx.compose.ui.graphics.Color(0xFFCBD5E1), fontSize = 14.sp,
                        textAlign = TextAlign.Center, maxLines = 2)
                }
                Spacer(Modifier.height(6.dp))
                AnimatedVisibility(visible = aiText.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Text(aiText, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 4)
                }
                Spacer(Modifier.height(18.dp))
            }

            // Controls.
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(bottom = 34.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.Default.Mic, Icons.Default.MicOff, "Mic", !muted) {
                    muted = !muted
                    SaifAutomation.setMicMuted(muted)
                }
                OrbButton(
                    if (sharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                    if (sharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                    if (sharing) "Screen on" else "Screen", !sharing
                ) {
                    if (sharing) SaifAutomation.stopScreenShare() else onRequestScreenShare()
                }
                OrbButton(Icons.Default.Close, Icons.Default.Close, "Stop", false) {
                    SaifAutomation.stopLive(context)
                    onClose()
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun OrbButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    offIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF8B5CF6), androidx.compose.ui.graphics.Color(0xFF6D28D9)))
                    else Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0x33FFFFFF), androidx.compose.ui.graphics.Color(0x22FFFFFF)))
                )
                .pointerInteropFilter { ev -> if (ev.action == MotionEvent.ACTION_UP) { onClick(); true } else ev.action == MotionEvent.ACTION_DOWN },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (active) icon else offIcon, label, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = androidx.compose.ui.graphics.Color(0xFFCBD5E1), fontSize = 11.sp)
    }
}

/**
 * The floating window shown when the user leaves SAIF AI during a live session: the automation orb in a small
 * draggable glass bubble (it replaces the old sparkle-star overlay, so the power moved here unchanged).
 */
@SuppressLint("ClickableViewAccessibility")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SaifLiveOrbBubble(
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDrag: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by SaifAutomation.orbState.collectAsState()
    val level by SaifAutomation.audioLevel.collectAsState()
    // Drag bookkeeping: a press that moves more than a few pixels is a drag, not a tap.
    var downX by remember { mutableStateOf(0f) }
    var downY by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "orb_bubble_pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "bubbleGlow"
    )
    val ring = when (state) {
        com.jarvis.assistant.data.model.ConversationState.SPEAKING -> androidx.compose.ui.graphics.Color(0xFF22C55E)
        com.jarvis.assistant.data.model.ConversationState.LISTENING -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
        com.jarvis.assistant.data.model.ConversationState.THINKING -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
        else -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
    }
    Box(
        modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(ring.copy(alpha = 0.35f + glow * 0.3f * (0.4f + level)), androidx.compose.ui.graphics.Color(0xCC0B0A14))))
            .pointerInteropFilter { ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        if (!dragging && (kotlin.math.abs(dx) > 10f || kotlin.math.abs(dy) > 10f)) dragging = true
                        if (dragging && onDrag != null) {
                            onDrag(dx, dy)
                            downX = ev.rawX
                            downY = ev.rawY
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasDragging = dragging
                        dragging = false
                        if (!wasDragging) onOpen()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SaifLiveOrb(modifier = Modifier.size(66.dp), compact = true)
        Box(
            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color(0xFFEF4444))
                .pointerInteropFilter { ev -> if (ev.action == MotionEvent.ACTION_UP) { onClose(); true } else ev.action == MotionEvent.ACTION_DOWN },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Close, "Band karein", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(13.dp)) }
    }
}
