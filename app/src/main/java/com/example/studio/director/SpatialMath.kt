package com.example.studio.director

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure 3D geometry shared by preview and export. Coordinates use the same units on every axis. */
object SpatialMath {
    data class Vec3(val x: Float, val y: Float, val z: Float)
    data class Projection(val x: Float, val y: Float, val scale: Float)
    data class Face(val name: String, val vertices: List<Vec3>, val normal: Vec3) {
        val depth: Float get() = vertices.sumOf { it.z.toDouble() }.toFloat() / vertices.size
    }

    /** Pitch about X, then yaw about Y, in degrees. Positive Z points toward the viewer. */
    fun rotate(v: Vec3, pitch: Float, yaw: Float): Vec3 {
        val rx = (if (pitch.isFinite()) pitch.coerceIn(-360f, 360f) else 0f) * (PI.toFloat() / 180f)
        val ry = (if (yaw.isFinite()) yaw.coerceIn(-360f, 360f) else 0f) * (PI.toFloat() / 180f)
        val cy = cos(rx); val sy = sin(rx)
        val y = v.y * cy - v.z * sy
        val z = v.y * sy + v.z * cy
        val cx = cos(ry); val sx = sin(ry)
        return Vec3(v.x * cx + z * sx, y, -v.x * sx + z * cx)
    }

    /** A pinhole projection with a near-plane guard; it never divides by zero. */
    fun project(v: Vec3, focal: Float): Projection {
        val f = if (focal.isFinite()) max(0.001f, focal) else 1f
        val z = if (v.z.isFinite()) v.z else 0f
        val denominator = max(f * 0.22f, f - z)
        val scale = f / denominator
        val x = if (v.x.isFinite()) v.x else 0f
        val y = if (v.y.isFinite()) v.y else 0f
        return Projection(x * scale, y * scale, scale)
    }

    /** A depth-sorted set of front-facing cube faces, including transformed normals for lighting. */
    fun cube(width: Float, height: Float, depth: Float, pitch: Float, yaw: Float): List<Face> {
        val x = width / 2f; val y = height / 2f; val z = depth / 2f
        val points = listOf(
            Vec3(-x, -y, -z), Vec3(x, -y, -z), Vec3(x, y, -z), Vec3(-x, y, -z),
            Vec3(-x, -y, z), Vec3(x, -y, z), Vec3(x, y, z), Vec3(-x, y, z)
        ).map { rotate(it, pitch, yaw) }
        fun face(name: String, normal: Vec3, vararg indices: Int): Face =
            Face(name, indices.map { points[it] }, rotate(normal, pitch, yaw))
        return listOf(
            face("back", Vec3(0f, 0f, -1f), 0, 3, 2, 1),
            face("front", Vec3(0f, 0f, 1f), 4, 5, 6, 7),
            face("left", Vec3(-1f, 0f, 0f), 0, 4, 7, 3),
            face("right", Vec3(1f, 0f, 0f), 1, 2, 6, 5),
            face("top", Vec3(0f, -1f, 0f), 0, 1, 5, 4),
            face("bottom", Vec3(0f, 1f, 0f), 3, 7, 6, 2)
        ).filter { it.normal.z > 0.0001f }.sortedBy { it.depth }
    }

    /** Ambient plus directional diffuse light, bounded so no face disappears. */
    fun light(normal: Vec3): Float {
        val lx = -0.38f; val ly = -0.55f; val lz = 0.74f
        val length = sqrt(lx * lx + ly * ly + lz * lz)
        val dot = (normal.x * lx + normal.y * ly + normal.z * lz) / length
        return (0.38f + 0.62f * max(0f, dot)).coerceIn(0.38f, 1f)
    }

    /** A reproducible highlight position in -1..1. No mutable random state or wall clock. */
    fun specularPosition(seed: Int, timeMs: Long): Float {
        val phase = (seed.toLong() and 0xffffL) / 65535f * 2f * PI.toFloat()
        return sin(timeMs.coerceAtLeast(0L) / 1100f + phase)
    }
}
