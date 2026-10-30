package com.example.studio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.studio.director.SceneJson
import com.example.studio.director.SceneRenderer
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.ItemEffects
import com.example.studio.model.TextItem
import com.example.studio.model.VideoItem

/** Supplies decoded media to the shared renderer. The preview passes null (it shows video/images with its own players). */
interface FrameMediaProvider {
    fun videoFrame(item: VideoItem, localTimeUs: Long): Bitmap?
    fun image(item: ImageItem, width: Int, height: Int): Bitmap?
}

    /**
     * ONE renderer for preview and export: render(plan, t) -> Canvas. The canvas is cleared by the caller once per frame
     * and each layer in the [RenderPlan] is drawn exactly once, in z-order. Everything here is a pure function of time.
     */
    object StudioFrameRenderer {

    /** True for pictures/clips the director placed behind a scene (they fill the frame). */
    fun isBackdrop(aiWhy: String?): Boolean = com.example.studio.director.AssetPlacements.isBackdropTag(aiWhy)

    /** Source rectangle that centre-crops a [bw]x[bh] bitmap to the aspect ratio of [w]x[h]. */
    fun coverSource(bw: Int, bh: Int, w: Int, h: Int): Rect = LayerEffects.coverSource(bw, bh, w, h)

    /** Draws every visible layer of [plan]. [layerFilter] lets the preview skip layer kinds it renders natively. */
    fun render(
        canvas: Canvas, width: Int, height: Int, plan: RenderPlan,
        media: FrameMediaProvider? = null, unitScale: Float = 1f,
        strict: Boolean = true,
        layerFilter: (RenderLayer) -> Boolean = { true }
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        plan.visibleLayers.forEach { layer ->
            if (!layerFilter(layer)) return@forEach
            val t = layer.resolvedTransform
            canvas.save()
            val cx = width / 2f + t.x * unitScale
            val cy = height / 2f + t.y * unitScale
            canvas.translate(cx, cy); canvas.rotate(t.rotation); canvas.scale(t.scale, t.scale); canvas.translate(-cx, -cy)
            paint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            when (val item = layer.item) {
                is VideoItem -> media?.videoFrame(item, layer.localTimeUs)?.let { bmp ->
                    drawMedia(canvas, bmp, item.effects, item.aiWhy, layer.progress, layer.localTimeUs, item.durationUs,
                        item.animIn?.type, item.animIn?.durationUs, item.animOut?.type, item.animOut?.durationUs,
                        width, height, paint, "v:${item.id}")
                    bmp.recycle()
                }
                is ImageItem -> media?.image(item, width, height)?.let { bmp ->
                    drawMedia(canvas, bmp, item.effects, item.aiWhy, layer.progress, layer.localTimeUs, item.durationUs,
                        item.animIn?.type, item.animIn?.durationUs, item.animOut?.type, item.animOut?.durationUs,
                        width, height, paint, "i:${item.id}")
                }
                is CodeSceneItem -> {
                    val graph = SceneJson.decodeFromTimeline(item.htmlCode)
                        ?: if (strict) throw IllegalStateException("Scene '${item.label}' uses a legacy HTML scene that cannot be rendered. Regenerate it with SAIF AI or remove it.") else null
                    if (graph != null) {
                        val tr = item.animIn
                        val active = tr != null && TransitionFx.isGeometric(tr.type) && tr.durationUs > 0 && layer.localTimeUs < tr.durationUs
                        val prog = if (active) layer.localTimeUs.toFloat() / tr!!.durationUs else 1f
                        canvas.save()
                        if (active) TransitionFx.begin(canvas, width.toFloat(), height.toFloat(), tr!!.type, prog, graph.elements.firstOrNull()?.seed ?: item.id.hashCode())
                        SceneRenderer.draw(canvas, width, height, graph, layer.localTimeUs / 1000L, item.durationUs / 1000L, layer.opacity)
                        canvas.restore()
                        if (active) TransitionFx.overlay(canvas, width.toFloat(), height.toFloat(), tr!!.type, prog)
                    }
                }
                is TextItem -> CaptionRenderer.draw(canvas, width, height, item, layer.localTimeUs, unitScale)
                else -> {}
            }
            canvas.restore()
        }
    }

    /**
     * Draws one picture/clip layer with its FULL placement: the box (position + size + fit), the slow drift of a
     * backdrop, and every effect of [LayerEffects] (glow, shadow, blur, grade, blend, rounded corner, chroma key).
     * The keyframed transform is already resolved by [RenderPlan] and applied by the caller.
     * Items with no placement tag keep exactly the old behaviour, so projects saved before this change look the same.
     */
    private fun drawMedia(
        canvas: Canvas, bmp: Bitmap, effects: ItemEffects, aiWhy: String?,
        progress: Float, localTimeUs: Long, durationUs: Long,
        animInType: String?, animInUs: Long?, animOutType: String?, animOutUs: Long?,
        width: Int, height: Int, paint: Paint, tag: String
    ) {
        val placement = com.example.studio.director.AssetPlacements.of(aiWhy)
        if (placement == null) {
            if (isBackdrop(aiWhy)) {
                // Library b-roll fills the frame (centre-crop), never stretched.
                canvas.drawBitmap(bmp, LayerEffects.coverSource(bmp.width, bmp.height, width, height), Rect(0, 0, width, height), paint)
            } else {
                // Footage keeps its own proportions (black bars) instead of being stretched to the frame.
                val s = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                val dw = bmp.width * s; val dh = bmp.height * s
                canvas.drawBitmap(bmp, null, RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f), paint)
            }
            return
        }
        // Extra motion: the slow backdrop drift, plus the geometric part of the entrance / exit animation.
        val drift = if (placement.isBackdrop && placement.keyframes.isEmpty() && placement.drift > 0.001f)
            LayerEffects.driftTransform(placement, progress, width, height) else null
        val inUs = animInUs ?: 0L
        val inPose = if (animInType != null && inUs > 0L && localTimeUs < inUs)
            LayerEffects.entrance(animInType, localTimeUs.toFloat() / inUs) else LayerEffects.AnimPose()
        val outUs = animOutUs ?: 0L
        val remaining = durationUs - localTimeUs
        val outPose = if (animOutType != null && outUs > 0L && remaining in 0 until outUs)
            LayerEffects.exit(animOutType, 1f - remaining.toFloat() / outUs) else LayerEffects.AnimPose()

        var opened = false
        if (drift != null || !inPose.isIdentity || !outPose.isIdentity) {
            val dcx = width / 2f + (drift?.x ?: 0f) + (inPose.dx + outPose.dx) * width
            val dcy = height / 2f + (drift?.y ?: 0f) + (inPose.dy + outPose.dy) * height
            val sc = (drift?.scale ?: 1f) * inPose.scale * outPose.scale
            val rot = inPose.rotation + outPose.rotation
            canvas.save(); opened = true
            canvas.translate(dcx, dcy)
            if (rot != 0f) canvas.rotate(rot)
            if (sc != 1f) canvas.scale(sc, sc)
            canvas.translate(-dcx, -dcy)
        }
        val dst = LayerEffects.destRect(placement, width, height, bmp.width, bmp.height)
        val src = LayerEffects.srcRect(placement, bmp.width, bmp.height, dst)
        LayerEffects.drawLayer(canvas, bmp, src, dst, paint, effects, placement, tag, width, height)
        if (opened) canvas.restore()
    }
}
