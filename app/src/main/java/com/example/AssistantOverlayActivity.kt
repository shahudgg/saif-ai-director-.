package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.components.GeminiAssistantSheet
import com.example.ui.components.GeminiCornerGlow
import com.example.ui.theme.SaifAiTheme
import com.example.util.WakeWordManager

/**
 * Translucent Activity providing the Gemini-like Home Screen popup assistant interface.
 * Shows the glowing screen corners and bottom chatbox with voice listening and typing modes.
 */
class AssistantOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WakeWordManager.init(this)
        val spokenPrompt = intent.getStringExtra("INITIAL_PROMPT")

        setContent {
            SaifAiTheme {
                val cornerGlowTrigger by WakeWordManager.cornerGlowTrigger.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    // Screen 4-Corner & Perimeter Aura Glow
                    GeminiCornerGlow(triggerTimestamp = cornerGlowTrigger)

                    // Floating Bottom Assistant Sheet
                    GeminiAssistantSheet(
                        initialPrompt = spokenPrompt,
                        onDismiss = {
                            WakeWordManager.dismissAssistant()
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WakeWordManager.dismissAssistant()
    }
}
