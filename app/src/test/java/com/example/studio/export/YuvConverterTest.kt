package com.example.studio.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class YuvConverterTest {

    private val w = 32
    private val h = 16

    private class Frame(val y: YuvConverter.Plane, val u: YuvConverter.Plane, val v: YuvConverter.Plane)

    private fun planar(yStride: Int = w, cStride: Int = w / 2): Frame = Frame(
        YuvConverter.Plane(ByteBuffer.allocate(yStride * h), yStride, 1),
        YuvConverter.Plane(ByteBuffer.allocate(cStride * (h / 2)), cStride, 1),
        YuvConverter.Plane(ByteBuffer.allocate(cStride * (h / 2)), cStride, 1)
    )

    /** NV12-style: U and V share one interleaved buffer, V starts one byte after U (what Image planes expose). */
    private fun semiPlanar(yStride: Int = w, cStride: Int = w): Frame {
        val chroma = ByteBuffer.allocate(cStride * (h / 2))
        val uBuf = chroma.duplicate()
        val vView = chroma.duplicate().apply { position(1) }.slice()
        return Frame(YuvConverter.Plane(ByteBuffer.allocate(yStride * h), yStride, 1), YuvConverter.Plane(uBuf, cStride, 2), YuvConverter.Plane(vView, cStride, 2))
    }

    private fun solid(rgb: Int) = IntArray(w * h) { (0xFF shl 24) or rgb }

    private fun decode(f: Frame, x: Int, yy: Int): IntArray {
        val y = f.y.buffer.get(yy * f.y.rowStride + x * f.y.pixelStride).toInt() and 0xFF
        val u = f.u.buffer.get((yy / 2) * f.u.rowStride + (x / 2) * f.u.pixelStride).toInt() and 0xFF
        val v = f.v.buffer.get((yy / 2) * f.v.rowStride + (x / 2) * f.v.pixelStride).toInt() and 0xFF
        return YuvConverter.yuvToRgb(y, u, v)
    }

    private val colors = listOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0x000000, 0x8040C8, 0x1EC85A, 0x1B1330, 0x0B0A14)

    private fun assertWholeFrame(make: () -> Frame) {
        for (rgb in colors) {
            val f = make()
            YuvConverter.argbToYuv420(solid(rgb), w, h, f.y, f.u, f.v)
            val er = (rgb shr 16) and 0xFF; val eg = (rgb shr 8) and 0xFF; val eb = rgb and 0xFF
            // Check top-left, top-right, bottom-left and bottom-right: the old bug made the top purple and the bottom green.
            for ((px, py) in listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1, w / 2 to h / 2)) {
                val d = decode(f, px, py)
                assertTrue("R at ($px,$py) for #${rgb.toString(16)}: ${d[0]} vs $er", Math.abs(d[0] - er) <= 3)
                assertTrue("G at ($px,$py) for #${rgb.toString(16)}: ${d[1]} vs $eg", Math.abs(d[1] - eg) <= 3)
                assertTrue("B at ($px,$py) for #${rgb.toString(16)}: ${d[2]} vs $eb", Math.abs(d[2] - eb) <= 3)
            }
        }
    }

    @Test fun `planar layout decodes to the original colours`() = assertWholeFrame { planar() }
    @Test fun `semi planar NV12 layout decodes to the original colours`() = assertWholeFrame { semiPlanar() }
    @Test fun `padded row strides are honoured`() {
        assertWholeFrame { planar(yStride = w + 16, cStride = w / 2 + 8) }
        assertWholeFrame { semiPlanar(yStride = w + 16, cStride = w + 16) }
    }

    @Test fun `white and black map to studio range limits`() {
        val f = planar()
        YuvConverter.argbToYuv420(solid(0xFFFFFF), w, h, f.y, f.u, f.v)
        assertEquals(235, f.y.buffer.get(0).toInt() and 0xFF)
        assertEquals(128, f.u.buffer.get(0).toInt() and 0xFF)
        val b = planar()
        YuvConverter.argbToYuv420(solid(0x000000), w, h, b.y, b.u, b.v)
        assertEquals(16, b.y.buffer.get(0).toInt() and 0xFF)
    }

    @Test fun `two halves with different colours stay separate`() {
        val top = 0xFF0000; val bottom = 0x0000FF
        val px = IntArray(w * h) { i -> (0xFF shl 24) or (if (i / w < h / 2) top else bottom) }
        val f = semiPlanar()
        YuvConverter.argbToYuv420(px, w, h, f.y, f.u, f.v)
        val t = decode(f, 4, 2); val b = decode(f, 4, h - 3)
        assertTrue(t[0] > 240 && t[2] < 15)
        assertTrue(b[2] > 240 && b[0] < 15)
    }
}
