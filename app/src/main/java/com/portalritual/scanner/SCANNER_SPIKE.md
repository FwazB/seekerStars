# MediaPipe ObjectDetector Spike — Findings

## Dependency Required

```toml
# gradle/libs.versions.toml
[versions]
mediapipe = "0.10.32"

[libraries]
mediapipe-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipe" }
```

```kotlin
# app/build.gradle.kts
implementation(libs.mediapipe.vision)
```

**Compatibility note:** MediaPipe tasks-vision 0.10.x bundles its own TFLite runtime.
Should not conflict with SceneView 2.3.3 (Filament) or CameraX 1.4.1 — they don't
use TFLite. No transitive dependency overlap expected. Verify with `./gradlew dependencies`
after adding.

## Model

- **Model:** EfficientDet-Lite0 (float16)
- **File:** `app/src/main/assets/efficientdet_lite0.tflite`
- **Download:** https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float16/latest/efficientdet_lite0.tflite
- **Size:** ~4.4 MB
- **Classes:** 90 COCO categories
- **Input:** 320x320 RGB

## Pipeline Architecture

```
CameraX ImageAnalysis (640x480, STRATEGY_KEEP_ONLY_LATEST)
    │
    ▼ (on inference HandlerThread — NOT main/render thread)
ImageProxy.image → MediaImageBuilder → MPImage
    │
    ▼
ObjectDetector.detectAsync(mpImage, timestampMs)
    │
    ▼ (ResultListener callback, still on inference thread)
ObjectDetectorResult → marshal to main thread for UI
```

### Critical Threading Rules

1. **Dedicated HandlerThread for inference** — avoids blocking UI frame rate (60fps)
   and Filament render loop
2. **No shared EGL context** — MediaPipe GPU delegate creates its own OpenGL context.
   SceneView/Filament has its own. They must never share. HandlerThread isolation
   guarantees this.
3. **ImageProxy.close()** — MUST be called after detectAsync, or CameraX buffers fill
   up and preview freezes. Use try/finally.
4. **STRATEGY_KEEP_ONLY_LATEST** — drops frames if inference is slower than camera.
   Prevents backlog on slower GPU.
5. **Lower resolution (640x480)** — model input is 320x320 anyway. Higher res just
   wastes resize time.

## GPU Delegate — Dimensity 7300 / Mali-G615 MC2

### Hardware
- **GPU:** ARM Mali-G615 MC2 (2 shader cores)
- **OpenGL ES:** 3.2
- **Vulkan:** 1.1
- **OpenCL:** 2.0

### Expected Performance (estimated, needs device testing)
| Delegate | EfficientDet-Lite0 float16 | Notes |
|----------|---------------------------|-------|
| CPU (4x A78) | ~60-100ms | Reliable, no warm-up spike |
| GPU (Mali-G615) | ~30-50ms | Warm-up 2-5x slower (shader compile) |

### GPU Risks on Mali-G615
1. **Shader compilation latency** — first inference takes 200-500ms on GPU while
   OpenGL shaders compile. Pre-warm with a dummy frame after init.
2. **Mali-specific bugs** — some MediaTek devices have known issues with MediaPipe
   GPU delegate (see github.com/google-ai-edge/mediapipe/issues/5535). Always
   implement CPU fallback.
3. **Memory** — GPU delegate uses ~50-100MB additional GPU memory. With Filament
   also using GPU, total GPU memory pressure is higher. Monitor for OOM on
   low-memory variants.
4. **Thermal throttling** — sustained GPU inference + Filament rendering + camera
   may cause thermal throttling on a mid-range SoC. Consider reducing inference
   frequency (every 2nd or 3rd frame) if device heats up.

### Recommendation
Start with **CPU delegate** for reliability. Only switch to GPU after validating on
physical Seeker device with latency measurements. CPU at ~80ms is 12.5 FPS which is
sufficient for object scanning (not real-time tracking).

## CameraX Integration Notes

```kotlin
// Bind ImageAnalysis alongside existing Preview use case:
val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(android.util.Size(640, 480))
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .build()

val helper = ObjectDetectorHelper(context, listener)
helper.initialize()

imageAnalysis.setAnalyzer(helper.getInferenceExecutor()!!) { proxy ->
    helper.detectFromImageProxy(proxy)
}

// In cameraProvider.bindToLifecycle:
cameraProvider.bindToLifecycle(
    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
    preview, imageAnalysis  // ← add imageAnalysis alongside preview
)
```

**Note:** CameraX supports binding multiple use cases. Preview + ImageAnalysis
is a standard combination. No conflict expected.

## Files Created

- `scanner/ObjectDetectorHelper.kt` — skeleton with commented MediaPipe imports
  (uncomment after dependency is added). Includes HandlerThread isolation,
  GPU fallback, ImageProxy→MPImage pipeline, latency tracking.
- `scanner/SCANNER_SPIKE.md` — this document.

## Next Steps (Phase F)

1. Manager adds MediaPipe dependency to build.gradle.kts
2. Download model to assets/
3. Uncomment imports and method bodies in ObjectDetectorHelper.kt
4. Build + test on Seeker device
5. Measure actual GPU vs CPU latency
6. Wire into a scanner UI mode (separate from constellation flow)
