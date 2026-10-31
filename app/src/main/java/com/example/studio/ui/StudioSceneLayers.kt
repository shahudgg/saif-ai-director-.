package com.example.studio.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.studio.director.AssetPlacement
import com.example.studio.director.AssetPlacements
import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.ItemEffects
import com.example.studio.model.TextItem
import com.example.studio.model.VoiceItem
import com.example.studio.render.LayerEffects
import com.example.studio.render.RenderLayer
import com.example.studio.render.RenderPlan
import com.example.studio.render.StudioFrameRenderer
import kotlin.math.abs
import kotlin.math.roundToInt

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
    // Stickers sit ABOVE the graphics, backdrops below them — the same z-order the exporter uses.
    val backdrops = plan.visibleLayers.filter { it.item is ImageItem && AssetPlacements.of(it.item.aiWhy)?.isBackdrop != false }
    val stickers = plan.visibleLayers.filter { it.item is ImageItem && AssetPlacements.of(it.item.aiWhy)?.isBackdrop == false }
    if (overlayScene) backdrops.forEach { layer -> PlacedImage(layer.item as ImageItem, layer) }
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
    if (!overlayScene) backdrops.forEach { layer -> PlacedImage(layer.item as ImageItem, layer) }
    stickers.forEach { layer -> PlacedImage(layer.item as ImageItem, layer) }
}

/**
 * One picture layer in the preview, drawn with the same placement maths as the exporter: box (x, y, w, h, fit),
 * the slow backdrop drift, the keyframed transform from [RenderPlan] and the colour grade. Items without a
 * placement tag keep the old full-frame behaviour.
 */
@Composable
private fun PlacedImage(item: ImageItem, layer: RenderLayer) {
    val p = AssetPlacements.of(item.aiWhy)
    if (p == null) {
        AsyncImage(
            model = item.mediaUri, contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = if (StudioFrameRenderer.isBackdrop(item.aiWhy)) ContentScale.Crop else ContentScale.Fit,
            alpha = layer.opacity, colorFilter = gradeFilter(item.effects)
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fw = constraints.maxWidth.toFloat()
        val fh = constraints.maxHeight.toFloat()
        if (fw <= 1f || fh <= 1f) return@BoxWithConstraints
        val t = layer.resolvedTransform
        val drift = if (p.isBackdrop && p.keyframes.isEmpty() && p.drift > 0.001f)
            LayerEffects.driftTransform(p, layer.progress, fw.toInt(), fh.toInt()) else null
        val full = p.isBackdrop || p.fit == "fill"
        val boxW = if (full) fw else p.width * fw
        val boxH = if (full) fh else p.height * fh
        val scale = (drift?.scale ?: 1f) * t.scale
        val cx = p.x * fw + t.x + (drift?.x ?: 0f)
        val cy = p.y * fh + t.y + (drift?.y ?: 0f)
        val wDp = with(LocalDensity.current) { (boxW * scale).coerceAtLeast(1f).toDp() }
        val hDp = with(LocalDensity.current) { (boxH * scale).coerceAtLeast(1f).toDp() }
        val glow = p.glow
        Box(Modifier.fillMaxSize()) {
            // Bloom approximation: a blurred copy of the picture behind the sharp one.
            if (glow > 0.02f) {
                AsyncImage(
                    model = item.mediaUri, contentDescription = null,
                    modifier = Modifier
                        .size(wDp + (glow * 46).dp, hDp + (glow * 46).dp)
                        .offset { IntOffset((cx - (boxW * scale) / 2f - glow * 23f * density).roundToInt(), (cy - (boxH * scale) / 2f - glow * 23f * density).roundToInt()) }
                        .graphicsLayer { rotationZ = t.rotation; alpha = (layer.opacity * glow * 0.85f).coerceIn(0f, 1f) },
                    contentScale = ContentScale.Crop
                )
            }
            AsyncImage(
                model = item.mediaUri, contentDescription = null,
                modifier = Modifier
                    .size(wDp, hDp)
                    .offset { IntOffset((cx - (boxW * scale) / 2f).roundToInt(), (cy - (boxH * scale) / 2f).roundToInt()) }
                    // t.rotation already contains the placement rotation (PlacementApplier.transform copies it),
                    // so it is used alone - adding p.rotation again would spin the asset twice as far as the export.
                    .graphicsLayer { rotationZ = t.rotation; alpha = layer.opacity },
                contentScale = when (p.fit) {
                    "contain" -> ContentScale.Fit
                    "stretch" -> ContentScale.FillBounds
                    else -> ContentScale.Crop
                },
                colorFilter = gradeFilter(item.effects)
            )
        }
    }
}

/** Brightness / contrast / saturation / hue of [LayerEffects] as a Compose colour filter (preview only). */
private fun gradeFilter(e: ItemEffects): ColorFilter? {
    val neutral = abs(e.brightness - 1f) < 0.005f && abs(e.contrast - 1f) < 0.005f &&
        abs(e.saturation - 1f) < 0.005f && abs(e.hue) < 0.5f
    if (neutral) return null
    val b = e.brightness.coerceIn(0.2f, 2.5f)
    val c = e.contrast.coerceIn(0.2f, 2.5f)
    val s = e.saturation.coerceIn(0f, 2.5f)
    val t = (1f - c) * 127.5f + (b - 1f) * 90f
    val sat = arrayOf(
        floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f),
        floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f),
        floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f),
        floatArrayOf(0f, 0f, 0f, 1f, 0f)
    )
    val m = ColorMatrix()
    for (row in 0..2) for (col in 0..2) m.set(row, col, (sat[row][col] * s) * c + (if (row == col) (1f - s) * c else 0f))
    for (row in 0..2) m.set(row, 4, t)
    m.set(3, 3, 1f)
    return ColorFilter.colorMatrix(m)
}

/** Unused-placement helper kept so the preview can log what it drew when debugging. */
internal fun placementOf(aiWhy: String?): AssetPlacement? = AssetPlacements.of(aiWhy)

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
            val voiceActive = active.any { it is VoiceItem }
            val volume = com.example.studio.audio.AudioMixer.calculateEffectiveGain(item, plan.timeUs, voiceActive).coerceIn(0f, 1f)
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
                // Match export's fades and voice ducking as the playhead moves.
                p.setVolume(volume, volume)
                if (kotlin.math.abs(p.currentPosition - localMs) > 250) p.seekTo(localMs)
                if (isPlaying && !p.isPlaying) p.start() else if (!isPlaying && p.isPlaying) p.pause()
            } catch (_: IllegalStateException) { players.remove(item.id) }
        }
    }
}
