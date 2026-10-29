package com.example.studio.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.TextItem
import com.example.studio.model.VoiceItem
import com.example.studio.render.RenderPlan
import com.example.studio.render.StudioFrameRenderer

/**
 * Draws director scenes and captions with the SAME [StudioFrameRenderer] the exporter uses, so the preview and the
 * exported MP4 look identical (same wrapping, same chunking, same transforms). Images are still shown through Coil and
 * video through the player, so those layer kinds are filtered out here.
 */
@Composable
fun StudioSceneLayers(plan: RenderPlan) {
    val hasNative = plan.visibleLayers.any { it.item is CodeSceneItem || it.item is TextItem }
    // Scenes built over a library picture are transparent: the picture must be drawn first, the scene on top of it.
    val overlayScene = plan.visibleLayers.any { (it.item as? CodeSceneItem)?.animationType == com.example.studio.director.SCENE_OVERLAY_MARKER }
    if (overlayScene) {
        plan.visibleLayers.forEach { layer ->
            val item = layer.item
            if (item is ImageItem) {
                AsyncImage(model = item.mediaUri, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = if (StudioFrameRenderer.isBackdrop(item.aiWhy)) ContentScale.Crop else ContentScale.Fit, alpha = layer.opacity)
            }
        }
    }
    if (hasNative) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c ->
                StudioFrameRenderer.render(
                    c.nativeCanvas, size.width.toInt(), size.height.toInt(), plan, media = null, strict = false,
                    layerFilter = { it.item is CodeSceneItem || it.item is TextItem }
                )
            }
        }
    }
    if (!overlayScene) plan.visibleLayers.forEach { layer ->
        val item = layer.item
        if (item is ImageItem) {
            AsyncImage(model = item.mediaUri, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = if (StudioFrameRenderer.isBackdrop(item.aiWhy)) ContentScale.Crop else ContentScale.Fit, alpha = layer.opacity)
        }
    }
}

/** Plays narration / voice-over / SFX / music in sync with the master clock; drift is corrected, failures are not swallowed silently. */
@Composable
fun StudioAudioLayers(plan: RenderPlan, isPlaying: Boolean, onError: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val players = remember { HashMap<String, MediaPlayer>() }
    val failed = remember { HashSet<String>() }
    DisposableEffect(Unit) { onDispose { players.values.forEach { runCatching { it.release() } }; players.clear() } }

    LaunchedEffect(plan.timeUs / 200_000L, isPlaying, plan.activeAudioItems.map { it.id }) {
        val active = plan.activeAudioItems
        val activeIds = active.map { it.id }.toSet()
        players.filterKeys { it !in activeIds }.forEach { (id, p) -> runCatching { p.release() }; players.remove(id) }
        active.forEach { item ->
            val uri = when (item) { is VoiceItem -> item.audioUri; is AudioItem -> item.audioUri; else -> null }
            val volume = when (item) { is VoiceItem -> item.volume; is AudioItem -> item.volume; else -> 1f }
            if (uri.isNullOrBlank() || item.id in failed) return@forEach
            val localMs = ((plan.timeUs - item.startUs) / 1000L).toInt().coerceAtLeast(0)
            var p = players[item.id]
            if (p == null) {
                p = try {
                    MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
                        setDataSource(ctx, Uri.parse(uri)); prepare(); setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f)); seekTo(localMs)
                    }
                } catch (e: Exception) { failed += item.id; onError("Audio layer '${item.label}' could not be played"); null }
                if (p != null) players[item.id] = p
            }
            p ?: return@forEach
            try {
                if (kotlin.math.abs(p.currentPosition - localMs) > 250) p.seekTo(localMs)
                if (isPlaying && !p.isPlaying) p.start() else if (!isPlaying && p.isPlaying) p.pause()
            } catch (_: IllegalStateException) { players.remove(item.id) }
        }
    }
}
