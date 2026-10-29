package com.example

import kotlinx.coroutines.flow.MutableStateFlow

/** Lets other modules (SAIF AI AUTOMATION) ask the Compose UI to open its Settings panel. */
object HostNavigation {
    val openSettingsRequest = MutableStateFlow(false)
}
