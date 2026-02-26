package com.portalritual.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Full-screen Compose overlay that captures rune traces during TRACE_RUNE phase.
 *
 * Draws a glowing trail as the user traces, then feeds the normalized points
 * to the recognizer on finger-up.
 */
@Composable
fun RuneTraceOverlay(
    isActive: Boolean,
    recognizer: TraceRecognizer,
    onResult: (TraceResult) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        var rawPoints by remember { mutableStateOf(listOf<Offset>()) }
        var canvasWidth by remember { mutableFloatStateOf(1f) }
        var canvasHeight by remember { mutableFloatStateOf(1f) }

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    canvasWidth = size.width.toFloat()
                    canvasHeight = size.height.toFloat()

                    awaitEachGesture {
                        // DOWN — start new trace
                        val down = awaitFirstDown(requireUnconsumed = false)
                        rawPoints = listOf(down.position)

                        // MOVE — accumulate points
                        do {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: break
                            rawPoints = rawPoints + pos
                            event.changes.forEach { it.consume() }
                        } while (event.type != PointerEventType.Release)

                        // UP — normalize and recognize
                        if (rawPoints.size >= 2) {
                            val w = canvasWidth.coerceAtLeast(1f)
                            val h = canvasHeight.coerceAtLeast(1f)
                            val normalized = rawPoints.map { offset ->
                                Pair(offset.x / w, offset.y / h)
                            }
                            val result = recognizer.recognize(normalized)
                            onResult(result)
                        }

                        rawPoints = emptyList()
                    }
                }
        ) {
            canvasWidth = size.width
            canvasHeight = size.height

            if (rawPoints.size >= 2) {
                // Outer glow layer
                val glowPath = Path().apply {
                    moveTo(rawPoints[0].x, rawPoints[0].y)
                    for (i in 1 until rawPoints.size) {
                        lineTo(rawPoints[i].x, rawPoints[i].y)
                    }
                }
                drawPath(
                    path = glowPath,
                    color = Color(0x4400FFFF),
                    style = Stroke(width = 24f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    blendMode = BlendMode.Screen
                )

                // Core trace line
                val corePath = Path().apply {
                    moveTo(rawPoints[0].x, rawPoints[0].y)
                    for (i in 1 until rawPoints.size) {
                        lineTo(rawPoints[i].x, rawPoints[i].y)
                    }
                }
                drawPath(
                    path = corePath,
                    color = Color(0xCC00FFFF),
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
