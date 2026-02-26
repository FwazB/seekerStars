MediaPipe Model Required
========================

This directory needs the EfficientDet-Lite0 model file for surface detection.

File needed: efficientdet_lite0_fp16.tflite

Download from:
  https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float16/latest/efficientdet_lite0.tflite

Rename the downloaded file to: efficientdet_lite0_fp16.tflite

Place it in this directory (app/src/main/assets/).

The model is used by SurfaceDetector.kt to detect flat surfaces (tables, desks, etc.)
in the camera feed so the game grid can be anchored to a real-world surface.
