package com.example.studio.export

import java.nio.ByteBuffer

/**
 * ARGB -> YUV 4:2:0 conversion that honours the real plane layout of the codec's input image
 * (row stride + pixel stride per plane), so planar (I420), semi-planar (NV12/NV21) and padded
 * buffers are all filled correctly. Colour math is BT.709, limited (studio) range, which is what
 * the exporter tags in the output (see [StudioExportEngine]).
 *
 * Pure JVM code: it only needs [ByteBuffer]s, so it is unit-tested without a device.
 */
object YuvConverter {

    // BT.709 limited range, 16.16 fixed point.  Y = 16 + 219*Y',  Cb/Cr = 128 + 224*(...)
    private const val YR = 11966; private const val YG = 40254; private const val YB = 4064
    private const val UR = -6596; private const val UG = -22188; private const val UB = 28784
    private const val VR = 28784; private const val VG = -26145; private const val VB = -2639
    private const val ROUND = 1 shl 15

    class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

    /** [argb] holds width*height pixels (Bitmap.getPixels layout). [width] and [height] must be even. */
    fun argbToYuv420(argb: IntArray, width: Int, height: Int, y: Plane, u: Plane, v: Plane) {
        require(width % 2 == 0 && height % 2 == 0) { "Frame size must be even ($width x $height)" }
        require(argb.size >= width * height) { "Pixel array too small" }

        // ---- luma ----
        val yRow = ByteArray(width)
        for (j in 0 until height) {
            val base = j * y.rowStride
            val rowOff = j * width
            if (y.pixelStride == 1) {
                for (i in 0 until width) {
                    val c = argb[rowOff + i]
                    yRow[i] = lumaOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).toByte()
                }
                y.buffer.position(base)
                y.buffer.put(yRow, 0, width)
            } else {
                for (i in 0 until width) {
                    val c = argb[rowOff + i]
                    y.buffer.put(base + i * y.pixelStride, lumaOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF).toByte())
                }
            }
        }

        // ---- chroma: average each 2x2 block, then convert ----
        val cw = width / 2; val ch = height / 2
        for (cj in 0 until ch) {
            val r0 = (cj * 2) * width; val r1 = r0 + width
            val uBase = cj * u.rowStride; val vBase = cj * v.rowStride
            for (ci in 0 until cw) {
                val p0 = argb[r0 + ci * 2]; val p1 = argb[r0 + ci * 2 + 1]
                val p2 = argb[r1 + ci * 2]; val p3 = argb[r1 + ci * 2 + 1]
                val r = (((p0 shr 16) and 0xFF) + ((p1 shr 16) and 0xFF) + ((p2 shr 16) and 0xFF) + ((p3 shr 16) and 0xFF) + 2) shr 2
                val g = (((p0 shr 8) and 0xFF) + ((p1 shr 8) and 0xFF) + ((p2 shr 8) and 0xFF) + ((p3 shr 8) and 0xFF) + 2) shr 2
                val b = ((p0 and 0xFF) + (p1 and 0xFF) + (p2 and 0xFF) + (p3 and 0xFF) + 2) shr 2
                val cb = 128 + ((UR * r + UG * g + UB * b + ROUND) shr 16)
                val cr = 128 + ((VR * r + VG * g + VB * b + ROUND) shr 16)
                u.buffer.put(uBase + ci * u.pixelStride, clamp(cb).toByte())
                v.buffer.put(vBase + ci * v.pixelStride, clamp(cr).toByte())
            }
        }
    }

    private fun lumaOf(r: Int, g: Int, b: Int): Int = clamp(16 + ((YR * r + YG * g + YB * b + ROUND) shr 16))
    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

    /** Inverse (BT.709 limited) used by tests and diagnostics. Returns [r, g, b] in 0..255. */
    fun yuvToRgb(y: Int, u: Int, v: Int): IntArray {
        val yy = 1.16438f * (y - 16)
        val r = yy + 1.79274f * (v - 128)
        val g = yy - 0.21325f * (u - 128) - 0.53291f * (v - 128)
        val b = yy + 2.11240f * (u - 128)
        return intArrayOf(clamp(Math.round(r)), clamp(Math.round(g)), clamp(Math.round(b)))
    }
}
