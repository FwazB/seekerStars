package com.portalritual.input

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

import com.portalritual.engine.Connection
import com.portalritual.engine.Star

/**
 * Full-screen Compose overlay for sequential connect-the-dots constellation linking.
 *
 * Stars are numbered 1-N. Player connects them in order by dragging from star to star.
 * Visual effects: star fade, wrong-connection flash, correct-connection pulse,
 * streak counter, low-stability glitch, next-star pulsing highlight, dotted hint line.
 */
@Composable
fun StarDragOverlay(
    isActive: Boolean,
    stars: List<Star>,
    completedConnections: Set<Connection>,
    onConnectionMade: (Connection) -> Unit,
    timeRemainingPct: Float = 1f,
    stability: Float = 100f,
    streak: Int = 0,
    lastEvent: String? = null,
    currentStepIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hitRadiusPx = with(density) { 40.dp.toPx() }
    val starRadiusPx = with(density) { 12.dp.toPx() }
    val glitchPx = with(density) { 2.dp.toPx() }
    val labelTextSize = with(density) { 14.sp.toPx() }

    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        var grabbedStarId by remember { mutableIntStateOf(-1) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }
        var canvasWidth by remember { mutableFloatStateOf(1f) }
        var canvasHeight by remember { mutableFloatStateOf(1f) }

        // Wrong connection red flash
        var redFlashAlpha by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(lastEvent) {
            if (lastEvent == "WRONG_CONNECTION") {
                redFlashAlpha = 0.4f
                delay(300)
                redFlashAlpha = 0f
            }
        }

        // Correct connection gold pulse
        var goldPulseAlpha by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(lastEvent) {
            if (lastEvent == "CORRECT_CONNECTION") {
                goldPulseAlpha = 0.3f
                delay(400)
                goldPulseAlpha = 0f
            }
        }

        // Streak scale bounce
        var streakScale by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(streak) {
            if (streak > 1) {
                streakScale = 1.4f
                delay(150)
                streakScale = 1f
            }
        }

        // Low-stability glitch
        var glitchSeed by remember { mutableIntStateOf(0) }
        val isGlitching = stability < 30f
        LaunchedEffect(isGlitching) {
            while (isGlitching) {
                glitchSeed++
                delay(16)
            }
        }

        // Next-star pulse animation (continuous)
        var pulseFrame by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                pulseFrame++
                delay(16)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = modifier
                    .fillMaxSize()
                    .pointerInput(stars) {
                        canvasWidth = size.width.toFloat()
                        canvasHeight = size.height.toFloat()

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w = canvasWidth.coerceAtLeast(1f)
                            val h = canvasHeight.coerceAtLeast(1f)

                            val hitStar = stars.firstOrNull { star ->
                                val sx = star.x * w
                                val sy = star.y * h
                                val dx = down.position.x - sx
                                val dy = down.position.y - sy
                                dx * dx + dy * dy <= hitRadiusPx * hitRadiusPx
                            }

                            if (hitStar != null) {
                                grabbedStarId = hitStar.id
                                dragPos = down.position

                                do {
                                    val event = awaitPointerEvent()
                                    val pos = event.changes.firstOrNull()?.position ?: break
                                    dragPos = pos
                                    event.changes.forEach { it.consume() }
                                } while (event.type != PointerEventType.Release)

                                val targetStar = stars.firstOrNull { star ->
                                    if (star.id == hitStar.id) return@firstOrNull false
                                    val sx = star.x * w
                                    val sy = star.y * h
                                    val dx = dragPos.x - sx
                                    val dy = dragPos.y - sy
                                    dx * dx + dy * dy <= hitRadiusPx * hitRadiusPx
                                }

                                if (targetStar != null) {
                                    onConnectionMade(Connection(hitStar.id, targetStar.id))
                                }

                                grabbedStarId = -1
                            }
                        }
                    }
            ) {
                canvasWidth = size.width
                canvasHeight = size.height

                val w = size.width
                val h = size.height
                val rng = Random(glitchSeed)
                val starAlpha = timeRemainingPct.coerceIn(0.15f, 1f)
                val pulseAlpha = 0.3f + 0.5f * ((sin(pulseFrame * 0.1f) + 1f) / 2f) // 0.3..0.8

                // Completed connections (gold)
                for (conn in completedConnections) {
                    val from = stars.firstOrNull { it.id == conn.fromId } ?: continue
                    val to = stars.firstOrNull { it.id == conn.toId } ?: continue
                    drawConnectionLine(
                        from = starOffset(from, w, h, isGlitching, glitchPx, rng),
                        to = starOffset(to, w, h, isGlitching, glitchPx, rng)
                    )
                }

                // Dotted hint line: current → next star
                val nextIdx = currentStepIndex + 1
                if (currentStepIndex in stars.indices && nextIdx in stars.indices) {
                    val curStar = stars[currentStepIndex]
                    val nxtStar = stars[nextIdx]
                    drawLine(
                        color = Color(0x3300FFFF),
                        start = starOffset(curStar, w, h, isGlitching, glitchPx, rng),
                        end = starOffset(nxtStar, w, h, isGlitching, glitchPx, rng),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // Active drag line
                if (grabbedStarId >= 0) {
                    val fromStar = stars.firstOrNull { it.id == grabbedStarId }
                    if (fromStar != null) {
                        drawDragLine(
                            from = starOffset(fromStar, w, h, isGlitching, glitchPx, rng),
                            to = dragPos
                        )
                    }
                }

                // Draw stars with sequential state
                for ((index, star) in stars.withIndex()) {
                    val center = starOffset(star, w, h, isGlitching, glitchPx, rng)

                    when {
                        // Completed stars: green, dimmed
                        index < currentStepIndex -> {
                            drawStarDot(center, starRadiusPx, alpha = 0.5f * starAlpha, tint = Color(0xFF00CC66))
                        }
                        // Current star (drag source): bright cyan with ring indicator
                        index == currentStepIndex -> {
                            // Indicator ring
                            drawCircle(
                                color = Color(0xFF00FFFF).copy(alpha = 0.6f),
                                radius = starRadiusPx * 2.5f,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )
                            drawStarDot(center, starRadiusPx, alpha = starAlpha)
                        }
                        // Next target star: pulsing glow
                        index == nextIdx -> {
                            drawCircle(
                                color = Color(0xFF00FFFF).copy(alpha = pulseAlpha),
                                radius = starRadiusPx * 2.2f,
                                center = center,
                                blendMode = BlendMode.Screen
                            )
                            drawStarDot(center, starRadiusPx * 1.15f, alpha = starAlpha)
                        }
                        // Future stars: normal
                        else -> {
                            drawStarDot(center, starRadiusPx, alpha = starAlpha)
                        }
                    }

                    // Number label
                    drawContext.canvas.nativeCanvas.drawText(
                        "${star.id + 1}",
                        center.x,
                        center.y + labelTextSize / 3f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = labelTextSize
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                    )
                }

                // Red flash overlay
                if (redFlashAlpha > 0f) {
                    drawRect(color = Color.Red.copy(alpha = redFlashAlpha))
                }

                // Gold pulse overlay
                if (goldPulseAlpha > 0f) {
                    drawRect(color = Color(0xFFFFD700).copy(alpha = goldPulseAlpha))
                }
            }

            // Streak counter
            if (streak > 1) {
                Text(
                    text = "${streak}x",
                    color = Color(0xFFFFD700),
                    fontSize = (24f * streakScale).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

// --- Helpers ---

private fun starOffset(
    star: Star,
    w: Float,
    h: Float,
    glitch: Boolean,
    glitchPx: Float,
    rng: Random
): Offset {
    val baseX = star.x * w
    val baseY = star.y * h
    if (!glitch) return Offset(baseX, baseY)
    val jx = (rng.nextFloat() * 2f - 1f) * glitchPx
    val jy = (rng.nextFloat() * 2f - 1f) * glitchPx
    return Offset(baseX + jx, baseY + jy)
}

private fun DrawScope.drawStarDot(
    center: Offset,
    radius: Float,
    alpha: Float = 1f,
    tint: Color = Color(0xFF00FFFF)
) {
    drawCircle(
        color = tint.copy(alpha = 0.27f * alpha),
        radius = radius * 2f,
        center = center,
        blendMode = BlendMode.Screen
    )
    drawCircle(
        color = tint.copy(alpha = alpha),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawConnectionLine(from: Offset, to: Offset) {
    drawLine(
        color = Color(0x44FFD700),
        start = from,
        end = to,
        strokeWidth = 12f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Screen
    )
    drawLine(
        color = Color(0xFFFFD700),
        start = from,
        end = to,
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawDragLine(from: Offset, to: Offset) {
    drawLine(
        color = Color(0x8800FFFF),
        start = from,
        end = to,
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}
