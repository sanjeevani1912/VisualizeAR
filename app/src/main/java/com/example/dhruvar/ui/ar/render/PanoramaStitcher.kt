package com.example.dhruvar.ui.ar.render

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * One rendered AR frame (camera feed + assets) with the ARCore camera orientation it was taken at.
 *
 * [xAxis], [yAxis], [zAxis] are the display-oriented camera axes in world space (camera looks down -Z).
 * [projX] / [projY] are the projection matrix diagonal terms, i.e. 1 / tan(half field of view).
 */
class PanoramaShot(
    val bitmap: Bitmap,
    val xAxis: FloatArray,
    val yAxis: FloatArray,
    val zAxis: FloatArray,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val projX: Float,
    val projY: Float
) {
    val halfFovHDegrees: Float get() = Math.toDegrees(atan(1.0 / projX)).toFloat()
    val halfFovVDegrees: Float get() = Math.toDegrees(atan(1.0 / projY)).toFloat()
}

/**
 * Builds an equirectangular panorama from [PanoramaShot]s using ARCore's camera orientation,
 * so no feature matching is needed. Every output pixel is traced back into each shot that covers it
 * and the samples are blended with a weight that fades towards the shot edges.
 */
object PanoramaStitcher {

    private const val PIXELS_PER_DEGREE = 8f
    private const val MAX_WIDTH = 2880
    private const val MAX_HEIGHT = 1440
    private val EMPTY_PIXEL = 0xFF14181A.toInt()

    /** Total yaw span covered by [shots], in degrees (capped at 360). */
    fun sweptDegrees(shots: List<PanoramaShot>): Float {
        if (shots.isEmpty()) return 0f
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (s in shots) {
            lo = minOf(lo, s.yawDegrees - s.halfFovHDegrees)
            hi = maxOf(hi, s.yawDegrees + s.halfFovHDegrees)
        }
        return (hi - lo).coerceAtMost(360f)
    }

    fun stitch(shots: List<PanoramaShot>): Bitmap? {
        if (shots.isEmpty()) return null

        var yawMin = Float.MAX_VALUE
        var yawMax = -Float.MAX_VALUE
        var pitchTop = -90f
        var pitchBottom = 90f
        var topSeen = -Float.MAX_VALUE
        var bottomSeen = Float.MAX_VALUE
        for (s in shots) {
            yawMin = minOf(yawMin, s.yawDegrees - s.halfFovHDegrees)
            yawMax = maxOf(yawMax, s.yawDegrees + s.halfFovHDegrees)
            topSeen = maxOf(topSeen, s.pitchDegrees + s.halfFovVDegrees)
            bottomSeen = minOf(bottomSeen, s.pitchDegrees - s.halfFovVDegrees)
        }
        pitchTop = topSeen.coerceAtMost(90f)
        pitchBottom = bottomSeen.coerceAtLeast(-90f)

        val spanYaw = (yawMax - yawMin).coerceAtMost(360f)
        val spanPitch = (pitchTop - pitchBottom).coerceAtLeast(10f)
        val width = (spanYaw * PIXELS_PER_DEGREE).toInt().coerceIn(64, MAX_WIDTH)
        val height = (spanPitch * PIXELS_PER_DEGREE).toInt().coerceIn(64, MAX_HEIGHT)
        val degPerPxX = spanYaw / width
        val degPerPxY = spanPitch / height

        // Per-column / per-row trigonometry shared by every shot.
        val sinTheta = FloatArray(width)
        val cosTheta = FloatArray(width)
        for (x in 0 until width) {
            val theta = Math.toRadians((yawMin + (x + 0.5f) * degPerPxX).toDouble())
            sinTheta[x] = sin(theta).toFloat()
            cosTheta[x] = cos(theta).toFloat()
        }
        val sinPhi = FloatArray(height)
        val cosPhi = FloatArray(height)
        for (y in 0 until height) {
            val phi = Math.toRadians((pitchTop - (y + 0.5f) * degPerPxY).toDouble())
            sinPhi[y] = sin(phi).toFloat()
            cosPhi[y] = cos(phi).toFloat()
        }

        val total = width * height
        val accR = FloatArray(total)
        val accG = FloatArray(total)
        val accB = FloatArray(total)
        val accW = FloatArray(total)

        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

        for (shot in shots) {
            val sw = shot.bitmap.width
            val sh = shot.bitmap.height
            val px = IntArray(sw * sh)
            shot.bitmap.getPixels(px, 0, sw, 0, 0, sw, sh)

            val threads = Array(workers) { w ->
                Thread {
                    var y = w
                    while (y < height) {
                        blendRow(shot, px, sw, sh, y, width, sinTheta, cosTheta, sinPhi[y], cosPhi[y], accR, accG, accB, accW)
                        y += workers
                    }
                }.also { it.start() }
            }
            threads.forEach { it.join() }
        }

        val out = IntArray(total)
        for (i in 0 until total) {
            val w = accW[i]
            out[i] = if (w <= 0f) {
                EMPTY_PIXEL
            } else {
                val r = (accR[i] / w).toInt().coerceIn(0, 255)
                val g = (accG[i] / w).toInt().coerceIn(0, 255)
                val b = (accB[i] / w).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blendRow(
        shot: PanoramaShot,
        px: IntArray,
        sw: Int,
        sh: Int,
        y: Int,
        width: Int,
        sinTheta: FloatArray,
        cosTheta: FloatArray,
        sinPhi: Float,
        cosPhi: Float,
        accR: FloatArray,
        accG: FloatArray,
        accB: FloatArray,
        accW: FloatArray
    ) {
        val xa = shot.xAxis
        val ya = shot.yAxis
        val za = shot.zAxis
        val rowBase = y * width

        for (x in 0 until width) {
            // World direction: yaw 0 looks down -Z, positive yaw turns towards +X.
            val dx = cosPhi * sinTheta[x]
            val dy = sinPhi
            val dz = -cosPhi * cosTheta[x]

            val cz = dx * za[0] + dy * za[1] + dz * za[2]
            if (cz >= -1e-3f) continue // behind this camera
            val inv = 1f / -cz
            val u = (dx * xa[0] + dy * xa[1] + dz * xa[2]) * inv * shot.projX
            if (u <= -1f || u >= 1f) continue
            val v = (dx * ya[0] + dy * ya[1] + dz * ya[2]) * inv * shot.projY
            if (v <= -1f || v >= 1f) continue

            val sx = ((u + 1f) * 0.5f * (sw - 1)).toInt().coerceIn(0, sw - 1)
            val sy = ((1f - v) * 0.5f * (sh - 1)).toInt().coerceIn(0, sh - 1)
            val c = px[sy * sw + sx]

            val edge = (1f - abs(u)) * (1f - abs(v))
            val weight = edge * edge + 1e-4f

            val idx = rowBase + x
            accR[idx] += ((c shr 16) and 0xFF) * weight
            accG[idx] += ((c shr 8) and 0xFF) * weight
            accB[idx] += (c and 0xFF) * weight
            accW[idx] += weight
        }
    }
}
