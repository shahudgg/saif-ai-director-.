package com.example.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FileUpdateEvent(val projectName: String, val filePath: String? = null)

object ProjectEditorCoordinator {
    private val _globalFileUpdates = MutableSharedFlow<FileUpdateEvent>(extraBufferCapacity = 64)
    val globalFileUpdates = _globalFileUpdates.asSharedFlow()

    fun notifyFileUpdated(projectName: String, filePath: String? = null) {
        _globalFileUpdates.tryEmit(FileUpdateEvent(projectName, filePath))
    }
}
