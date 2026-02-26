package com.portalritual.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.portalritual.engine.ConstellationPhase
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Cyan = Color(0xFF00FFFF)
private val Gold = Color(0xFFFFD700)
private val FailRed = Color(0xFFFF3333)
private val SuccessBg = Color(0xD9000000)   // 85% black
private val FailureBg = Color(0xE6000000)   // 90% black

@Composable
fun ResultsScreen(
    phase: ConstellationPhase,
    score: Int,
    streak: Int,
    patternName: String,
    onTapRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSuccess = phase == ConstellationPhase.RESULTS

    // ── Score count-up animation ───────────────────────────────────────

    var displayScore by remember { mutableIntStateOf(0) }
    LaunchedEffect(score, isSuccess) {
        displayScore = 0
        if (score > 0) {
            val stepDelay = (800L / score).coerceIn(15, 60)
            for (i in 1..score) {
                displayScore = i
                delay(stepDelay)
            }
        }
    }

    // Score pop when count finishes
    val scoreScale by animateFloatAsState(
        targetValue = if (displayScore == score && score > 0) 1f else 1.2f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "scorePop"
    )

    // ── Blinking prompt ────────────────────────────────────────────────

    val blink = rememberInfiniteTransition(label = "blink")
    val promptAlpha by blink.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "promptAlpha"
    )

    // ── Sparkle animation (success only) ───────────────────────────────

    val sparkles = remember { generateSparkles(30) }
    var animTime by remember { mutableFloatStateOf(0f) }
    if (isSuccess) {
        LaunchedEffect(Unit) {
            var lastNs = 0L
            while (true) {
                withFrameNanos { frameNs ->
                    if (lastNs > 0L) {
                        animTime += (frameNs - lastNs) / 1_000_000_000f
                    }
                    lastNs = frameNs
                }
            }
        }
    }

    // ── Layout ─────────────────────────────────────────────────────────

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onTapRestart() } },
        contentAlignment = Alignment.Center
    ) {
        // Background + sparkles
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = if (isSuccess) SuccessBg else FailureBg)

            if (isSuccess) {
                drawSparkles(sparkles, animTime)
            }
        }

        // Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Title
            Text(
                text = if (isSuccess) "CONSTELLATION COMPLETE" else "CONSTELLATION COLLAPSED",
                color = if (isSuccess) Gold else FailRed,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(32.dp))

            // Score
            Text(
                text = "$displayScore",
                color = if (isSuccess) Gold else Color.White.copy(alpha = 0.7f),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.scale(scoreScale)
            )
            Text(
                text = "SCORE",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(24.dp))

            // Streak
            if (streak > 0) {
                Text(
                    text = "Best Streak: ${streak}x",
                    color = Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
            }

            // Pattern name
            Text(
                text = patternName,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                letterSpacing = 2.sp
            )

            if (!isSuccess) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The constellation destabilized",
                    color = FailRed.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(48.dp))

            // Tap prompt
            Text(
                text = if (isSuccess) "Tap to continue" else "Tap to retry",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.alpha(promptAlpha)
            )
        }
    }
}

// ── Sparkle particle system ────────────────────────────────────────────

private data class Sparkle(
    val angle: Float,
    val speed: Float,
    val phaseOffset: Float,
    val size: Float,
    val color: Color
)

private const val SPARKLE_CYCLE_SEC = 2.5f

private fun generateSparkles(count: Int): List<Sparkle> {
    val rng = Random(42)
    return List(count) {
        Sparkle(
            angle = rng.nextFloat() * 2f * PI.toFloat(),
            speed = 40f + rng.nextFloat() * 80f,
            phaseOffset = rng.nextFloat(),
            size = 2f + rng.nextFloat() * 4f,
            color = if (rng.nextFloat() > 0.4f) Cyan else Gold
        )
    }
}

private fun DrawScope.drawSparkles(sparkles: List<Sparkle>, time: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    for (sparkle in sparkles) {
        val t = ((time / SPARKLE_CYCLE_SEC + sparkle.phaseOffset) % 1f)
        val r = t * sparkle.speed * SPARKLE_CYCLE_SEC
        val alpha = (1f - t).coerceIn(0f, 0.8f)

        val x = cx + r * cos(sparkle.angle)
        val y = cy + r * sin(sparkle.angle)

        // Glow
        drawCircle(
            color = sparkle.color.copy(alpha = alpha * 0.3f),
            radius = sparkle.size * 2.5f,
            center = Offset(x, y),
            blendMode = BlendMode.Screen
        )
        // Core
        drawCircle(
            color = sparkle.color.copy(alpha = alpha),
            radius = sparkle.size,
            center = Offset(x, y)
        )
    }
}
