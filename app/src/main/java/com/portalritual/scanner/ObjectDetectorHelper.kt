package com.portalritual.scanner

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
// --- MediaPipe imports (requires com.google.mediapipe:tasks-vision) ---
// import com.google.mediapipe.framework.image.MediaImageBuilder
// import com.google.mediapipe.framework.image.MPImage
// import com.google.mediapipe.tasks.core.BaseOptions
// import com.google.mediapipe.tasks.core.Delegate
// import com.google.mediapipe.tasks.vision.core.RunningMode
// import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
// import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

/**
 * MediaPipe ObjectDetector helper — Phase F spike.
 *
 * Wraps EfficientDet-Lite0 (float16) for real-time object detection via CameraX
 * ImageAnalysis. Runs inference on a dedicated background HandlerThread to avoid
 * blocking the UI or Filament render thread.
 *
 * CRITICAL: Must NOT share EGL context with SceneView/Filament.
 * The HandlerThread approach avoids this — no OpenGL calls happen here.
 * GPU delegate (if used) creates its own EGL context internally.
 *
 * ## Dependencies needed (Manager adds to build.gradle.kts in Phase F):
 *   implementation("com.google.mediapipe:tasks-vision:0.10.32")
 *
 * ## Model file:
 *   app/src/main/assets/efficientdet_lite0.tflite
 *   Download: https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float16/latest/efficientdet_lite0.tflite
 *
 * ## GPU delegate notes (Dimensity 7300 / Mali-G615 MC2):
 *   - Mali-G615 supports OpenCL 2.0 and Vulkan 1.1
 *   - MediaPipe GPU delegate uses OpenGL ES on Android
 *   - Mali-G615 MC2 is mid-range (2 shader cores) — expect ~30-50ms per frame
 *     with EfficientDet-Lite0 float16 on GPU, ~60-100ms on CPU
 *   - GPU delegate may fail on some MediaTek devices — always fall back to CPU
 *   - Warm-up: first inference is 2-5x slower (shader compilation). Pre-warm
 *     with a dummy frame after initialization.
 */
class ObjectDetectorHelper(
    private val context: Context,
    private val listener: DetectionListener,
    private val modelPath: String = MODEL_PATH,
    private val maxResults: Int = 5,
    private val scoreThreshold: Float = 0.5f,
    private val useGpuDelegate: Boolean = false
) {

    companion object {
        private const val TAG = "ObjectDetectorHelper"
        const val MODEL_PATH = "efficientdet_lite0.tflite"
    }

    // Dedicated thread for inference — keeps UI and Filament render threads free
    private var inferenceThread: HandlerThread? = null
    private var inferenceHandler: Handler? = null

    // private var objectDetector: ObjectDetector? = null

    // Latency tracking
    private var lastInferenceTimeMs: Long = 0L
    private var warmUpDone: Boolean = false

    /**
     * Initialize the detector. Call from a lifecycle-aware component.
     * Creates a background HandlerThread and builds the ObjectDetector.
     */
    fun initialize() {
        inferenceThread = HandlerThread("mediapipe-inference").also { it.start() }
        inferenceHandler = Handler(inferenceThread!!.looper)

        inferenceHandler?.post {
            try {
                setupDetector()
                Log.i(TAG, "ObjectDetector initialized (gpu=$useGpuDelegate)")
            } catch (e: Exception) {
                Log.e(TAG, "ObjectDetector init failed: ${e.message}", e)
                if (useGpuDelegate) {
                    Log.w(TAG, "GPU delegate failed — falling back to CPU")
                    setupDetector(forceCpu = true)
                } else {
                    listener.onError("ObjectDetector init failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Build the ObjectDetector with options.
     * When uncommented, this creates the detector with LIVE_STREAM mode.
     */
    private fun setupDetector(forceCpu: Boolean = false) {
        // val baseOptions = BaseOptions.builder()
        //     .setModelAssetPath(modelPath)
        //     .apply {
        //         if (useGpuDelegate && !forceCpu) {
        //             setDelegate(Delegate.GPU)
        //         } else {
        //             setDelegate(Delegate.CPU)
        //         }
        //     }
        //     .build()
        //
        // val options = ObjectDetector.ObjectDetectorOptions.builder()
        //     .setBaseOptions(baseOptions)
        //     .setRunningMode(RunningMode.LIVE_STREAM)
        //     .setMaxResults(maxResults)
        //     .setScoreThreshold(scoreThreshold)
        //     .setResultListener { result, inputImage ->
        //         val inferenceTime = SystemClock.uptimeMillis() - lastInferenceTimeMs
        //         listener.onResults(
        //             results = result,
        //             inferenceTimeMs = inferenceTime,
        //             imageWidth = inputImage.width,
        //             imageHeight = inputImage.height
        //         )
        //     }
        //     .setErrorListener { e ->
        //         Log.e(TAG, "Detection error: ${e.message}", e)
        //         listener.onError(e.message ?: "Unknown detection error")
        //     }
        //     .build()
        //
        // objectDetector = ObjectDetector.createFromOptions(context, options)
        //
        // // Pre-warm: run a dummy inference to trigger shader compilation (GPU)
        // // This avoids a latency spike on the first real frame.
        // if (useGpuDelegate && !forceCpu) {
        //     Log.i(TAG, "Pre-warming GPU delegate...")
        //     // Create a small dummy Bitmap, convert to MPImage, run detectAsync
        //     // warmUpDone = true after first result callback
        // }
    }

    /**
     * Process a CameraX ImageProxy frame.
     * Call this from ImageAnalysis.Analyzer.analyze() on the inference thread.
     *
     * Pipeline: ImageProxy → android.media.Image → MPImage → detectAsync()
     */
    fun detectFromImageProxy(imageProxy: ImageProxy) {
        // if (objectDetector == null) {
        //     imageProxy.close()
        //     return
        // }
        //
        // val mediaImage = imageProxy.image
        // if (mediaImage == null) {
        //     imageProxy.close()
        //     return
        // }
        //
        // lastInferenceTimeMs = SystemClock.uptimeMillis()
        //
        // val mpImage = MediaImageBuilder(mediaImage).build()
        // val timestampMs = imageProxy.imageInfo.timestamp / 1_000 // ns → µs...
        //     // Actually MediaPipe expects ms:
        //     // timestampMs = SystemClock.uptimeMillis()
        //
        // try {
        //     objectDetector?.detectAsync(mpImage, SystemClock.uptimeMillis())
        // } catch (e: Exception) {
        //     Log.e(TAG, "detectAsync failed: ${e.message}", e)
        // } finally {
        //     imageProxy.close()  // MUST close to free the buffer
        // }
    }

    /**
     * Create an ImageAnalysis.Analyzer that routes frames to this helper.
     * Bind this to CameraX alongside the Preview use case.
     *
     * IMPORTANT: Set ImageAnalysis executor to the inference HandlerThread,
     * not the main thread. Example:
     *
     *   val imageAnalysis = ImageAnalysis.Builder()
     *       .setTargetResolution(Size(640, 480))  // lower res = faster inference
     *       .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
     *       .build()
     *   imageAnalysis.setAnalyzer(inferenceExecutor) { proxy ->
     *       detectFromImageProxy(proxy)
     *   }
     *   cameraProvider.bindToLifecycle(
     *       lifecycleOwner, selector, preview, imageAnalysis
     *   )
     */
    fun createAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            detectFromImageProxy(imageProxy)
        }
    }

    /**
     * Get the inference thread executor for CameraX ImageAnalysis binding.
     * Returns null if not initialized.
     */
    fun getInferenceExecutor(): java.util.concurrent.Executor? {
        val handler = inferenceHandler ?: return null
        return java.util.concurrent.Executor { command -> handler.post(command) }
    }

    /**
     * Release resources. Call from onDestroy or DisposableEffect.
     */
    fun close() {
        // objectDetector?.close()
        // objectDetector = null
        inferenceThread?.quitSafely()
        inferenceThread = null
        inferenceHandler = null
    }

    /**
     * Callback interface for detection results.
     */
    interface DetectionListener {
        /**
         * Called on the inference thread with detection results.
         * Marshal to main thread if updating UI.
         */
        fun onResults(
            results: Any, // ObjectDetectorResult when uncommented
            inferenceTimeMs: Long,
            imageWidth: Int,
            imageHeight: Int
        )

        fun onError(error: String)
    }
}
