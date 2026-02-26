package com.epochdefenders.scanner

/**
 * Maps MediaPipe object detection bounding boxes to a tower defense grid.
 *
 * The camera frame is divided into a grid (default 12 columns x 9 rows).
 * Detected objects mark overlapping grid cells as "enhanced" buildable zones.
 *
 * Usage:
 *   val mapper = DetectionZoneMapper()
 *   // In ObjectDetectorHelper.DetectionListener.onResults:
 *   val zones = mapper.mapDetections(results, imageWidth, imageHeight)
 *   // zones is Set<GridCell> of enhanced cells
 */
class DetectionZoneMapper(
    val gridCols: Int = 12,
    val gridRows: Int = 9
) {
    /**
     * Map detection bounding boxes to grid cells.
     *
     * @param detections List of normalized bounding boxes (x, y, width, height in 0..1)
     * @param minScore Minimum confidence score to accept a detection
     * @return Set of grid cells that overlap with detected objects
     */
    fun mapDetections(
        detections: List<NormalizedBox>,
        minScore: Float = 0.5f
    ): Set<GridCell> {
        val cells = mutableSetOf<GridCell>()

        for (det in detections) {
            if (det.score < minScore) continue

            // Convert normalized coords to grid cell range
            val colStart = (det.x * gridCols).toInt().coerceIn(0, gridCols - 1)
            val colEnd = ((det.x + det.width) * gridCols).toInt().coerceIn(0, gridCols - 1)
            val rowStart = (det.y * gridRows).toInt().coerceIn(0, gridRows - 1)
            val rowEnd = ((det.y + det.height) * gridRows).toInt().coerceIn(0, gridRows - 1)

            for (col in colStart..colEnd) {
                for (row in rowStart..rowEnd) {
                    cells.add(GridCell(col, row))
                }
            }
        }

        return cells
    }

    /**
     * Convert from MediaPipe ObjectDetectorResult bounding boxes to our NormalizedBox format.
     * Call this in the DetectionListener callback.
     *
     * MediaPipe returns RectF with (left, top, right, bottom) in pixel coords.
     * We normalize to 0..1 using image dimensions.
     *
     * Example (uncomment when MediaPipe dep is added):
     *
     *   fun fromMediaPipeResult(
     *       result: ObjectDetectorResult,
     *       imageWidth: Int,
     *       imageHeight: Int
     *   ): List<NormalizedBox> {
     *       return result.detections().map { detection ->
     *           val box = detection.boundingBox()
     *           val category = detection.categories().firstOrNull()
     *           NormalizedBox(
     *               x = box.left / imageWidth,
     *               y = box.top / imageHeight,
     *               width = (box.right - box.left) / imageWidth,
     *               height = (box.bottom - box.top) / imageHeight,
     *               score = category?.score() ?: 0f,
     *               label = category?.categoryName() ?: "unknown"
     *           )
     *       }
     *   }
     */
}

/** Bounding box in normalized coordinates (0..1). */
data class NormalizedBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val score: Float = 1f,
    val label: String = ""
)

/** Grid cell coordinate. */
data class GridCell(val col: Int, val row: Int)
