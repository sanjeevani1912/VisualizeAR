package com.example.dhruvar.ui.ar.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import com.example.dhruvar.ui.ar.render.ARDistanceLineLabelProjection
import com.example.dhruvar.ui.ar.render.ARObjectLabelProjection
import com.example.dhruvar.ui.ar.render.AROriginLabelProjection
import com.example.dhruvar.ui.ar.render.ARRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Metadata associated with an AR visualization capture.
 */
data class CaptureMetadata(
    val layoutName: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val objectCount: Int = 0,
    val isCalibrated: Boolean = true
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestampMillis))
        }

    val sanitizedLayoutName: String
        get() {
            val trimmed = layoutName.trim()
            if (trimmed.isEmpty()) return "Plan"
            val sanitized = trimmed.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return if (sanitized.all { it == '_' }) "Plan" else sanitized
        }
}

/**
 * Service managing AR visualization capture, tactical overlay composition,
 * and scoped MediaStore image persistence.
 */
object ARCaptureManager {

    /**
     * Generates a non-colliding, timestamped image file name for the given [metadata].
     */
    fun buildImageFileName(metadata: CaptureMetadata): String {
        return "DHRUV_${metadata.sanitizedLayoutName}_${metadata.timestampMillis}.jpg"
    }

    /**
     * Captures the live AR surface view (camera feed + OpenGL 3D assets & lines)
     * using PixelCopy on Android 8.0+ (API 26+) with fallback to GL read pixels.
     */
    fun captureARView(
        surfaceView: GLSurfaceView,
        renderer: ARRenderer?,
        onComplete: (Result<Bitmap>) -> Unit
    ) {
        val width = surfaceView.width
        val height = surfaceView.height

        if (width <= 0 || height <= 0) {
            onComplete(Result.failure(IllegalStateException("AR SurfaceView dimensions are zero or invalid ($width x $height)")))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val mainHandler = Handler(Looper.getMainLooper())

                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            onComplete(Result.success(bitmap))
                        } else {
                            // Attempt fallback via GL renderer if PixelCopy returns failure
                            fallbackGLCapture(renderer, onComplete, Exception("PixelCopy failed with code $copyResult"))
                        }
                    },
                    mainHandler
                )
            } catch (e: Exception) {
                fallbackGLCapture(renderer, onComplete, e)
            }
        } else {
            fallbackGLCapture(renderer, onComplete, null)
        }
    }

    private fun fallbackGLCapture(
        renderer: ARRenderer?,
        onComplete: (Result<Bitmap>) -> Unit,
        previousError: Throwable?
    ) {
        if (renderer != null) {
            renderer.captureNextFrame { glBitmap ->
                if (glBitmap != null) {
                    onComplete(Result.success(glBitmap))
                } else {
                    onComplete(
                        Result.failure(
                            previousError ?: IllegalStateException("GL frame capture returned null bitmap")
                        )
                    )
                }
            }
        } else {
            onComplete(
                Result.failure(
                    previousError ?: IllegalStateException("Renderer unavailable for AR frame capture")
                )
            )
        }
    }

    /**
     * Composites tactical billboard labels, distance measurement badges,
     * and authoritative metadata header onto the base AR camera bitmap.
     */
    fun compositeLabelsAndMetadata(
        baseBitmap: Bitmap,
        objectLabels: List<ARObjectLabelProjection>,
        originLabel: AROriginLabelProjection?,
        distanceBadge: ARDistanceLineLabelProjection?,
        metadata: CaptureMetadata
    ): Bitmap {
        val composite = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(composite)
        val width = composite.width.toFloat()
        val height = composite.height.toFloat()

        // 1. Draw top tactical metadata banner
        val bannerHeight = 72f
        val bannerPaint = Paint().apply {
            color = Color.argb(200, 15, 23, 42) // Dark tactical navy slate
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width, bannerHeight, bannerPaint)

        val borderPaint = Paint().apply {
            color = Color.argb(230, 74, 222, 128) // Tactical Green
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, bannerHeight, width, bannerHeight, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("DhruvAR  •  PLAN: ${metadata.layoutName.uppercase()}", 24f, 44f, titlePaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(240, 203, 213, 225)
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val originStatus = if (metadata.isCalibrated) "ORIGIN: CALIBRATED" else "ORIGIN: UNCALIBRATED"
        canvas.drawText("${metadata.formattedDate}  |  $originStatus", width - 24f, 44f, metaPaint)

        // 2. Draw 3D Distance Line Midpoint Badge if visible
        distanceBadge?.let { badge ->
            if (badge.isVisible && badge.screenX in 0f..width && badge.screenY in 0f..height) {
                drawTacticalBadge(
                    canvas = canvas,
                    x = badge.screenX,
                    y = badge.screenY,
                    text = "≈ ${String.format(Locale.US, "%.1f", badge.distanceMeters)} m",
                    bgColor = Color.argb(220, 2, 132, 199), // Precision Blue
                    borderColor = Color.argb(255, 56, 189, 248),
                    textColor = Color.WHITE
                )
            }
        }

        // 3. Draw Planning Origin Marker Badge if visible
        originLabel?.let { origin ->
            if (origin.isVisible && origin.screenX in 0f..width && origin.screenY in 0f..height) {
                drawTacticalBadge(
                    canvas = canvas,
                    x = origin.screenX,
                    y = origin.screenY,
                    text = "ORIGIN (0.0m, 0.0m)",
                    bgColor = Color.argb(220, 194, 65, 12), // Anchor Orange
                    borderColor = Color.argb(255, 251, 146, 60),
                    textColor = Color.WHITE
                )
            }
        }

        // 4. Draw Billboard Labels for placed objects
        for (label in objectLabels) {
            if (label.isVisible && label.screenX in 0f..width && label.screenY in 0f..height) {
                val labelText = "${label.name} • ${label.assetType.displayName.uppercase()} (d=${String.format(Locale.US, "%.1f", label.distanceToCameraMeters)}m)"
                val bgColor = if (label.isSelected) Color.argb(230, 194, 65, 12) else Color.argb(210, 15, 23, 42)
                val borderColor = if (label.isSelected) Color.argb(255, 251, 146, 60) else Color.argb(220, 74, 222, 128)

                drawTacticalBadge(
                    canvas = canvas,
                    x = label.screenX,
                    y = label.screenY,
                    text = labelText,
                    bgColor = bgColor,
                    borderColor = borderColor,
                    textColor = Color.WHITE
                )
            }
        }

        return composite
    }

    private fun drawTacticalBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        text: String,
        bgColor: Int,
        borderColor: Int,
        textColor: Int
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)

        val paddingH = 16f
        val paddingV = 10f
        val rectW = bounds.width() + paddingH * 2
        val rectH = bounds.height() + paddingV * 2

        val rect = RectF(
            x - rectW / 2,
            y - rectH / 2,
            x + rectW / 2,
            y + rectH / 2
        )

        val bgPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }

        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)
        canvas.drawText(
            text,
            rect.left + paddingH,
            rect.bottom - paddingV + 1f,
            textPaint
        )
    }

    /**
     * Saves the captured visualization image locally using modern Android MediaStore Scoped Storage.
     * Requires ZERO broad storage permissions on Android 10+ (API 29+).
     *
     * @return [Result] containing the persistent image [Uri] or an exception on error.
     */
    fun saveImageToGallery(
        context: Context,
        bitmap: Bitmap,
        metadata: CaptureMetadata
    ): Result<Uri> {
        return try {
            val fileName = buildImageFileName(metadata)
            val resolver = context.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, metadata.timestampMillis / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, metadata.timestampMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DhruvAR")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val imageUri = resolver.insert(collectionUri, values)
                ?: return Result.failure(IllegalStateException("Failed to create MediaStore entry for $fileName"))

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                if (!compressed) {
                    return Result.failure(IllegalStateException("Failed to compress bitmap to JPEG format"))
                }
            } ?: return Result.failure(IllegalStateException("Failed to open output stream for $imageUri"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, values, null, null)
            }

            Result.success(imageUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
