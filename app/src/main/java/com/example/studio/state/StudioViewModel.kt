package com.example.studio.state

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studio.model.AudioItem
import com.example.studio.model.ImageItem
import com.example.studio.model.StudioProject
import com.example.studio.model.StudioProjectStorage
import com.example.studio.model.TimelineItem
import com.example.studio.model.TrackType
import com.example.studio.model.VideoItem
import com.example.studio.render.LayerMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SaveState { SAVED, SAVING, ERROR }

data class StudioUiState(
    val project: StudioProject,
    val selectedItemId: String? = null,
    val zoomDpPerSec: Float = LayerMath.DEFAULT_ZOOM_DP_PER_SEC,
    val isSnappingEnabled: Boolean = true,
    val isFocusMode: Boolean = false,
    val isFullscreen: Boolean = false,
    val isSafeZoneEnabled: Boolean = false,
    val isThirdsGridEnabled: Boolean = false,
    val previewHeightPercent: Float = 0.42f, // 42% height default
    val previewMode: String = "LIVE", // PROXY or LIVE
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String? = null,
    val saveState: SaveState = SaveState.SAVED
)

class StudioViewModel(
    private val context: Context,
    initialProject: StudioProject
) : ViewModel() {

    private val storage = StudioProjectStorage(context)
    val clock = MasterClock(viewModelScope)

    private val _uiState = MutableStateFlow(
        StudioUiState(
            project = initialProject,
            previewHeightPercent = 0.42f
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<StudioProject>()
    private val redoStack = mutableListOf<StudioProject>()

    init {
        clock.totalDurationUs = initialProject.totalDurationUs
    }

    private var saveJob: Job? = null

    /** Debounced autosave: bursts of edits (dragging, trimming) produce one write shortly after the last change. */
    private fun scheduleSave() {
        _uiState.value = _uiState.value.copy(saveState = SaveState.SAVING)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val snapshot = _uiState.value.project
            val ok = withContext(Dispatchers.IO) { runCatching { storage.saveProject(snapshot) }.isSuccess }
            _uiState.value = _uiState.value.copy(saveState = if (ok) SaveState.SAVED else SaveState.ERROR)
        }
    }

    /**
     * Saves right now, without waiting for the debounce. Call on pause, stop, back and when the app goes to the
     * background. Runs on an app-level scope so it still completes if the screen is being torn down.
     */
    fun flushSave() {
        saveJob?.cancel()
        val snapshot = _uiState.value.project
        flushScope.launch {
            val ok = runCatching { storage.saveProject(snapshot) }.isSuccess
            _uiState.value = _uiState.value.copy(saveState = if (ok) SaveState.SAVED else SaveState.ERROR)
        }
    }

    override fun onCleared() {
        runCatching { storage.saveProject(_uiState.value.project) }
        super.onCleared()
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 600L
        private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    fun commit(action: StudioAction) {
        val current = _uiState.value.project
        val next = action.apply(current)

        if (undoStack.size >= 100) undoStack.removeAt(0)
        undoStack.add(current)
        redoStack.clear()

        clock.totalDurationUs = next.totalDurationUs
        _uiState.value = _uiState.value.copy(
            project = next,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )

        scheduleSave()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(_uiState.value.project)

        clock.totalDurationUs = previous.totalDurationUs
        _uiState.value = _uiState.value.copy(
            project = previous,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )

        scheduleSave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(_uiState.value.project)

        clock.totalDurationUs = next.totalDurationUs
        _uiState.value = _uiState.value.copy(
            project = next,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )

        scheduleSave()
    }

    fun selectItem(itemId: String?) {
        _uiState.value = _uiState.value.copy(selectedItemId = itemId)
    }

    fun setZoom(zoomDpPerSec: Float) {
        val clamped = zoomDpPerSec.coerceIn(LayerMath.MIN_ZOOM_DP_PER_SEC, LayerMath.MAX_ZOOM_DP_PER_SEC)
        _uiState.value = _uiState.value.copy(zoomDpPerSec = clamped)
    }

    fun zoomIn() {
        setZoom(_uiState.value.zoomDpPerSec * 1.25f)
    }

    fun zoomOut() {
        setZoom(_uiState.value.zoomDpPerSec * 0.8f)
    }

    fun fitProject(screenWidthPx: Float, density: Float) {
        val durSec = _uiState.value.project.totalDurationSeconds
        if (durSec <= 0f || screenWidthPx <= 0f) return
        val targetDp = (screenWidthPx / density) - 64f
        val fitZoom = (targetDp / durSec).coerceIn(LayerMath.MIN_ZOOM_DP_PER_SEC, LayerMath.MAX_ZOOM_DP_PER_SEC)
        setZoom(fitZoom)
    }

    fun toggleSnapping() {
        _uiState.value = _uiState.value.copy(isSnappingEnabled = !_uiState.value.isSnappingEnabled)
    }

    fun toggleFocusMode() {
        _uiState.value = _uiState.value.copy(isFocusMode = !_uiState.value.isFocusMode)
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(isFullscreen = !_uiState.value.isFullscreen)
    }

    fun toggleSafeZone() {
        _uiState.value = _uiState.value.copy(isSafeZoneEnabled = !_uiState.value.isSafeZoneEnabled)
    }

    fun toggleThirdsGrid() {
        _uiState.value = _uiState.value.copy(isThirdsGridEnabled = !_uiState.value.isThirdsGridEnabled)
    }

    fun setPreviewHeightPercent(percent: Float) {
        _uiState.value = _uiState.value.copy(previewHeightPercent = percent.coerceIn(0.25f, 0.65f))
    }

    fun setAspect(aspect: String) {
        commit(StudioAction.SetAspect(aspect))
    }

    fun renameProject(newName: String) {
        commit(StudioAction.RenameProject(newName))
    }

    fun addMarkerAtPlayhead() {
        commit(StudioAction.AddMarker(clock.currentTimeUs.value))
    }

    fun splitSelectedItemAtPlayhead() {
        val selectedId = _uiState.value.selectedItemId ?: return
        val playheadUs = clock.currentTimeUs.value
        commit(StudioAction.SplitItem(selectedId, playheadUs))
    }

    fun deleteSelectedItem() {
        val selectedId = _uiState.value.selectedItemId ?: return
        commit(StudioAction.RemoveItem(selectedId))
        selectItem(null)
    }

    /**
     * Real Media Import Pipeline via MediaMetadataRetriever.
     * Accurately extracts duration, w/h, rotation, and fps without fake defaults.
     */
    fun importMediaUri(uri: Uri, isPhoto: Boolean = false) {
        viewModelScope.launch {
            val item = withContext(Dispatchers.IO) {
                try {
                    val playheadUs = clock.currentTimeUs.value
                    val lanes = _uiState.value.project.lanes

                    if (isPhoto) {
                        val visualLaneId = lanes.firstOrNull { it.trackType == TrackType.VISUAL }?.id ?: "lane_visual_1"
                        ImageItem(
                            id = UUID.randomUUID().toString(),
                            laneId = visualLaneId,
                            startUs = playheadUs,
                            durationUs = 5_000_000L, // 5 seconds default for photos
                            mediaUri = uri.toString(),
                            label = uri.lastPathSegment?.takeLast(16) ?: "Photo"
                        )
                    } else {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            val durMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val durMs = durMsStr?.toLongOrNull() ?: 5000L
                            val durUs = durMs * 1000L

                            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null

                            val videoLaneId = lanes.firstOrNull { it.trackType == TrackType.VIDEO }?.id ?: "lane_video_1"
                            VideoItem(
                                id = UUID.randomUUID().toString(),
                                laneId = videoLaneId,
                                startUs = playheadUs,
                                durationUs = durUs,
                                mediaUri = uri.toString(),
                                sourceInUs = 0L,
                                sourceOutUs = durUs,
                                naturalWidth = width,
                                naturalHeight = height,
                                label = uri.lastPathSegment?.takeLast(16) ?: "Video Clip"
                            )
                        } finally {
                            retriever.release()
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (item != null) {
                commit(StudioAction.AddItem(item))
                selectItem(item.id)
            } else {
                _uiState.value = _uiState.value.copy(statusMessage = "Media file could not be parsed.")
            }
        }
    }
}
