package com.portalritual.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portalritual.engine.ConstellationPhase

private val Cyan = Color(0xFF00FFFF)
private val Gold = Color(0xFFFFD700)
private val BarYellow = Color(0xFFFFAA00)
private val BarRed = Color(0xFFFF3333)
private val BarGreen = Color(0xFF33FF66)
private val HudBg = Color.Black.copy(alpha = 0.5f)

@Composable
fun GameHUD(
    phase: ConstellationPhase,
    timeRemainingPct: Float,
    stability: Float,
    score: Int,
    streak: Int,
    connections: Int,
    totalConnections: Int,
    modifier: Modifier = Modifier
) {
    // Don't show HUD in IDLE or terminal phases
    if (phase == ConstellationPhase.IDLE) return

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Countdown bar
            CountdownBar(timeRemainingPct)

            Spacer(Modifier.height(4.dp))

            // Stability bar
            StabilityBar(stability)

            Spacer(Modifier.height(8.dp))

            // Score row: connection progress (left) — score + streak (right)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Connection progress (left)
                ConnectionProgress(connections, totalConnections)

                // Score + streak (right)
                Column(horizontalAlignment = Alignment.End) {
                    ScoreDisplay(score)
                    if (streak > 1) {
                        StreakDisplay(streak)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownBar(pct: Float) {
    val animatedPct by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 1f),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "countdown"
    )
    val barColor by animateColorAsState(
        targetValue = when {
            pct > 0.5f -> Cyan
            pct > 0.25f -> BarYellow
            else -> BarRed
        },
        animationSpec = tween(300),
        label = "countdownColor"
    )

    // Pulse when low
    val pulse = if (pct <= 0.25f) {
        val transition = rememberInfiniteTransition(label = "countdownPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "countdownAlpha"
        )
        alpha
    } else 1f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .alpha(pulse)
    ) {
        // Track background
        drawRoundRect(
            color = HudBg,
            cornerRadius = CornerRadius(4.dp.toPx()),
            size = size
        )
        // Fill
        if (animatedPct > 0f) {
            drawRoundRect(
                color = barColor,
                cornerRadius = CornerRadius(4.dp.toPx()),
                size = Size(size.width * animatedPct, size.height)
            )
        }
    }
}

@Composable
private fun StabilityBar(stability: Float) {
    val pct = (stability / 100f).coerceIn(0f, 1f)
    val animatedPct by animateFloatAsState(
        targetValue = pct,
        animationSpec = tween(200),
        label = "stability"
    )
    val barColor by animateColorAsState(
        targetValue = when {
            stability > 60f -> BarGreen
            stability > 30f -> BarYellow
            else -> BarRed
        },
        animationSpec = tween(300),
        label = "stabilityColor"
    )

    // Pulse when critically low
    val pulse = if (stability < 30f) {
        val transition = rememberInfiniteTransition(label = "stabilityPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(300),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stabilityAlpha"
        )
        alpha
    } else 1f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .alpha(pulse)
    ) {
        drawRoundRect(
            color = HudBg,
            cornerRadius = CornerRadius(3.dp.toPx()),
            size = size
        )
        if (animatedPct > 0f) {
            drawRoundRect(
                color = barColor,
                cornerRadius = CornerRadius(3.dp.toPx()),
                size = Size(size.width * animatedPct, size.height)
            )
        }
    }
}

@Composable
private fun ScoreDisplay(score: Int) {
    // Scale pop when score changes
    var prevScore by remember { mutableIntStateOf(score) }
    var popping by remember { mutableStateOf(false) }

    LaunchedEffect(score) {
        if (score != prevScore) {
            popping = true
            prevScore = score
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (popping) 1.3f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        finishedListener = { popping = false },
        label = "scorePop"
    )

    Text(
        text = "$score",
        color = Gold,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .scale(scale)
            .background(HudBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable
private fun StreakDisplay(streak: Int) {
    val transition = rememberInfiniteTransition(label = "streakPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakAlpha"
    )

    Text(
        text = "${streak}x STREAK",
        color = Cyan.copy(alpha = alpha),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun ConnectionProgress(connections: Int, total: Int) {
    Text(
        text = "$connections/$total",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(HudBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
