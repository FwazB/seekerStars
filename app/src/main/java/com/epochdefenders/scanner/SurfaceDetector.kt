package com.epochdefenders.scanner

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.epochdefenders.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.geometry.Rect

/**
 * Detects flat surfaces in camera feed using MediaPipe Object Detection.
 * Returns a bounding box in screen coordinates for the largest detected surface.
 *
 * The game grid renders WITHIN this bounding box instead of full-screen,
 * creating the AR effect of the game "sitting on" a real surface.
 */
class SurfaceDetector(private val context: Context) {

    companion object {
        private const val TAG = "SurfaceDetector"
        private const val MODEL_ASSET = "efficientdet_lite0_fp16.tflite"
    }

    private var detector: ObjectDetector? = null
    private val _surfaceRect = MutableStateFlow<Rect?>(null)
    val surfaceRect: StateFlow<Rect?> = _surfaceRect.asStateFlow()

    // EMA smoothing to prevent jitter
    private var smoothedRect: RectF? = null
    private val smoothingFactor = 0.3f  // lower = smoother but more laggy

    // Track time since last detection for timeout
    @Volatile private var lastDetectionTimeMs = 0L
    private val detectionTimeoutMs = 2000L  // 2 seconds

    // Surface-like object labels from COCO dataset
    private val surfaceLabels = setOf(
        "dining table", "desk", "table", "book", "laptop",
        "keyboard", "mouse", "cell phone", "tv", "monitor",
        "bench", "bed", "couch", "chair"
    )

    // Cap detection at 5fps (200ms) per MobileOpt guidance — GPU delegate shares
    // Mali-G615 shader cores with HWUI, so limit contention window.
    private var lastDetectMs = 0L
    private val detectIntervalMs = 200L  // 5fps max

    fun start() {
        // GPU delegate preferred (35-55ms on Dimensity 7300), capped at 5fps
        // to minimize contention with HWUI/Canvas rendering. Fallback: CPU.
        detector = try {
            createDetector(Delegate.GPU)
        } catch (e: Exception) {
            AppLog.e(TAG, "GPU delegate failed, falling back to CPU", e)
            try {
                createDetector(Delegate.CPU)
            } catch (e2: Exception) {
                AppLog.e(TAG, "CPU delegate also failed — surface detection disabled", e2)
                null
            }
        }
    }

    private fun createDetector(delegate: Delegate): ObjectDetector {
        val baseOptions = BaseOptions.builder()
            .setDelegate(delegate)
            .setModelAssetPath(MODEL_ASSET)
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMaxResults(5)
            .setScoreThreshold(0.4f)
            .setResultListener { result, input ->
                processResult(result, input.width, input.height)
            }
            .setErrorListener { error ->
                AppLog.e(TAG, "Detection error", error)
            }
            .build()

        return ObjectDetector.createFromOptions(context, options)
    }

    /**
     * Feed a camera frame for detection. Call from CameraX ImageAnalysis callback.
     * Converts ImageProxy to MPImage and runs detection asynchronously.
     */
    fun detect(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        // Rate-limit to 5fps to reduce GPU contention with Canvas/HWUI
        if (now - lastDetectMs < detectIntervalMs) {
            imageProxy.close()
            return
        }
        lastDetectMs = now

        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = imageProxy.imageInfo.timestamp / 1_000 // ns to ms

            detector?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            AppLog.e(TAG, "Frame detection failed", e)
        } finally {
            imageProxy.close()
        }

        // Check for detection timeout (reuse `now` from line 99)
        if (now - lastDetectionTimeMs > detectionTimeoutMs && lastDetectionTimeMs > 0L) {
            _surfaceRect.value = null
            smoothedRect = null
        }
    }

    private fun processResult(result: ObjectDetectorResult, imageWidth: Int, imageHeight: Int) {
        lastDetectionTimeMs = System.currentTimeMillis()

        // Find the largest surface-like object
        val surfaceDetection = result.detections()
            .filter { detection ->
                detection.categories().any { cat ->
                    surfaceLabels.contains(cat.categoryName().lowercase())
                }
            }
            .maxByOrNull { detection ->
                val box = detection.boundingBox()
                (box.right - box.left) * (box.bottom - box.top)  // area
            }

        if (surfaceDetection == null) {
            // No surface found -- don't immediately null out, let timeout handle it
            return
        }

        val box = surfaceDetection.boundingBox()

        // Apply EMA smoothing
        val current = smoothedRect
        val newRect = if (current == null) {
            box
        } else {
            RectF(
                lerp(current.left, box.left, smoothingFactor),
                lerp(current.top, box.top, smoothingFactor),
                lerp(current.right, box.right, smoothingFactor),
                lerp(current.bottom, box.bottom, smoothingFactor)
            )
        }
        smoothedRect = newRect

        // Convert from image coordinates to normalized 0..1
        // The Canvas overlay will convert to screen coordinates
        _surfaceRect.value = Rect(
            left = newRect.left / imageWidth,
            top = newRect.top / imageHeight,
            right = newRect.right / imageWidth,
            bottom = newRect.bottom / imageHeight
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun stop() {
        detector?.close()
        detector = null
        _surfaceRect.value = null
        smoothedRect = null
    }
}
