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
    fun isBackdrop(aiWhy: String?): Boolean = aiWhy?.contains(":asset:") == true

    /** Source rectangle that centre-crops a [bw]x[bh] bitmap to the aspect ratio of [w]x[h]. */
    fun coverSource(bw: Int, bh: Int, w: Int, h: Int): Rect {
        val scale = maxOf(w.toFloat() / bw, h.toFloat() / bh)
        val sw = w / scale; val sh = h / scale
        val l = ((bw - sw) / 2f).toInt().coerceAtLeast(0); val t = ((bh - sh) / 2f).toInt().coerceAtLeast(0)
        return Rect(l, t, (l + sw).toInt().coerceAtMost(bw), (t + sh).toInt().coerceAtMost(bh))
    }

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
                    if (isBackdrop(item.aiWhy)) {
                        // Library b-roll fills the frame (centre-crop), never stretched.
                        canvas.drawBitmap(bmp, coverSource(bmp.width, bmp.height, width, height), Rect(0, 0, width, height), paint)
                    } else {
                        // Footage keeps its own proportions (black bars) instead of being stretched to the frame.
                        val s = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                        val dw = bmp.width * s; val dh = bmp.height * s
                        canvas.drawBitmap(bmp, null, RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f), paint)
                    }
                    bmp.recycle()
                }
                is ImageItem -> media?.image(item, width, height)?.let { bmp ->
                    if (isBackdrop(item.aiWhy)) {
                        canvas.drawBitmap(bmp, coverSource(bmp.width, bmp.height, width, height), Rect(0, 0, width, height), paint)
                    } else {
                        val s = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                        val dw = bmp.width * s; val dh = bmp.height * s
                        canvas.drawBitmap(bmp, null, RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f), paint)
                    }
                }
                is CodeSceneItem -> {
                    val graph = SceneJson.decodeFromTimeline(item.htmlCode)
                        ?: if (strict) throw IllegalStateException("Scene '${item.label}' uses a legacy HTML scene that cannot be rendered. Regenerate it with SAIF AI or remove it.") else null
                    if (graph != null) {
                        val tr = item.animIn
                        val active = tr != null && TransitionFx.isGeometric(tr.type) && tr.durationUs > 0 && layer.localTimeUs < tr.durationUs
                        val prog = if (active) layer.localTimeUs.toFloat() / tr!!.durationUs else 1f
                        canvas.save()
                        if (active) TransitionFx.begin(canvas, width.toFloat(), height.toFloat(), tr!!.type, prog)
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
}
