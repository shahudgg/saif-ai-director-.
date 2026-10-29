package com.example.agent

object AgentConfig {
    const val MAX_STEPS = 30
    const val MAX_STEPS_BATCH = 60
    const val MAX_MILLIS = 360_000L // 6 minutes max
    const val IDLE_QUIET_MS = 350L
    const val IDLE_TIMEOUT_MS = 2500L
    const val SHOT_LONG_SIDE = 768
    const val SHOT_JPEG_Q = 60
    const val MAX_TREE_NODES = 450
    const val TREE_CHARS = 7000
    const val HISTORY_WINDOW = 6
    const val CONTEXT_RESET_STEPS = 12
    const val SPEAK_MIN_GAP_MS = 8000L
    val BULK_SEND_GAP_RANGE_MS = 3000L..8000L
    const val DEBUG_SAVE_SCREENSHOTS = false
    const val USE_GEMINI_LIVE = true
}
