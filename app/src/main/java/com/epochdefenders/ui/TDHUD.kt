package com.epochdefenders.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.epochdefenders.engine.GameConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// TRON Neon palette
private val NeonCyan = Color(0xFF00FFFF)
private val NeonYellow = Color(0xFFFFFF00)
private val NeonRed = Color(0xFFFF0044)
private val NeonGreen = Color(0xFF00FF44)
private val HudBg = Color(0xFF0A0A2A).copy(alpha = 0.95f)
private val HudBorder = NeonCyan

/**
 * Tower Defense HUD overlay.
 * Top bar + wave/boss announcements + game over screen.
 */
@Composable
fun TDHUD(
    gold: Int,
    lives: Int,
    wave: Int,
    score: Int,
    isBossWave: Boolean,
    isGameOver: Boolean,
    onRestart: () -> Unit,
    onLeaderboard: () -> Unit = {},
    modifier: Modifier = Modifier,
    isWaiting: Boolean = false,
    waveTimer: Float = 0f,
    waveTimerMax: Float = Float.MAX_VALUE,
    onSendWave: () -> Unit = {},
    currentWave: Int = 1
) {
    Box(modifier = modifier.fillMaxSize()) {

        // ── Top bar ────────────────────────────────────────────────

        TopBar(gold, lives, wave, score)

        // ── Send Wave / Wave Timer ─────────────────────────────────

        if (isWaiting) {
            SendWavePanel(
                currentWave = currentWave,
                waveTimer = waveTimer,
                waveTimerMax = waveTimerMax,
                onSendWave = onSendWave
            )
        }

        // ── Wave announcement ──────────────────────────────────────

        WaveAnnouncement(wave)

        // ── Boss announcement ──────────────────────────────────────

        if (isBossWave) {
            BossAnnouncement()
        }

        // ── Game over ──────────────────────────────────────────────

        if (isGameOver) {
            GameOverOverlay(score, wave, onRestart, onLeaderboard)
        }
    }
}

// ── Send Wave Panel ───────────────────────────────────────────────────

@Composable
private fun SendWavePanel(
    currentWave: Int,
    waveTimer: Float,
    waveTimerMax: Float,
    onSendWave: () -> Unit
) {
    // Pulsing glow on the Send Wave button
    val pulse = rememberInfiniteTransition(label = "sendPulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sendGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp), // offset below top bar
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // ── Countdown timer (only for waves after wave 1) ──
            if (waveTimerMax < Float.MAX_VALUE) {
                val remaining = (waveTimerMax - waveTimer).toInt().coerceAtLeast(0)
                Text(
                    text = "Auto-start in ${remaining}s",
                    color = NeonYellow,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }

            // ── Send Wave button ────────────────────────────────
            Box(
                modifier = Modifier
                    .border(
                        border = BorderStroke(2.dp, NeonCyan.copy(alpha = glowAlpha)),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(HudBg, shape = RoundedCornerShape(8.dp))
                    .clickable { onSendWave() }
                    .padding(horizontal = 48.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SEND WAVE $currentWave",
                    color = NeonCyan.copy(alpha = glowAlpha),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            // ── Early send bonus hint ───────────────────────────
            if (waveTimer < GameConstants.EARLY_SEND_WINDOW_SEC) {
                Text(
                    text = "Early send: +${GameConstants.EARLY_SEND_BONUS}g!",
                    color = NeonGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Top Bar ────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    gold: Int,
    lives: Int,
    wave: Int,
    score: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Background bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudBg)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem("GOLD", "$gold", NeonYellow)
                StatItem("LIVES", "$lives", NeonRed)
                StatItem("WAVE", "$wave", NeonGreen)
                StatItem("SCORE", "$score", Color.White)
            }
        }

        // Border line
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawRect(color = HudBorder, size = size)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            color = NeonCyan,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Wave Announcement ──────────────────────────────────────────────────

@Composable
private fun WaveAnnouncement(wave: Int) {
    var showAnnouncement by remember { mutableStateOf(false) }
    var displayWave by remember { mutableIntStateOf(0) }
    var announcementAlpha by remember { mutableFloatStateOf(0f) }
    var announcementOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(wave) {
        if (wave > 0) {
            displayWave = wave
            showAnnouncement = true
            // Fade in + slide up
            announcementAlpha = 0f
            announcementOffsetY = 50f
            val steps = 20
            for (i in 1..steps) {
                announcementAlpha = i.toFloat() / steps
                announcementOffsetY = 50f * (1f - i.toFloat() / steps)
                delay(25) // 500ms total
            }
            // Hold
            delay(1000)
            // Fade out
            for (i in steps downTo 0) {
                announcementAlpha = i.toFloat() / steps
                delay(25) // 500ms total
            }
            showAnnouncement = false
        }
    }

    if (showAnnouncement) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "WAVE $displayWave",
                color = NeonCyan,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier
                    .alpha(announcementAlpha)
                    .padding(bottom = announcementOffsetY.dp)
            )
        }
    }
}

// ── Boss Announcement ──────────────────────────────────────────────────

@Composable
private fun BossAnnouncement() {
    var showBoss by remember { mutableStateOf(true) }
    var overlayAlpha by remember { mutableFloatStateOf(0f) }
    var textAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        // Darken screen
        val steps = 20
        for (i in 1..steps) {
            overlayAlpha = 0.7f * i / steps
            delay(25)
        }
        // Fade in text
        for (i in 1..steps) {
            textAlpha = i.toFloat() / steps
            delay(25)
        }
        // Hold
        delay(1500)
        // Fade out everything
        for (i in steps downTo 0) {
            overlayAlpha = 0.7f * i / steps
            textAlpha = i.toFloat() / steps
            delay(25)
        }
        showBoss = false
    }

    if (showBoss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "EPOCH BOSS",
                    color = NeonRed,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    modifier = Modifier.alpha(textAlpha)
                )
            }
        }
    }
}

// ── Game Over ──────────────────────────────────────────────────────────

@Composable
private fun GameOverOverlay(score: Int, wave: Int, onRestart: () -> Unit, onLeaderboard: () -> Unit) {
    // Blinking restart prompt
    val blink = rememberInfiniteTransition(label = "restart")
    val promptAlpha by blink.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "restartAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "GAME OVER",
                color = NeonRed,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Score: $score",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Waves Survived: $wave",
                color = NeonCyan,
                fontSize = 24.sp
            )

            Spacer(Modifier.height(36.dp))

            // Leaderboard button
            Box(
                modifier = Modifier
                    .border(
                        border = BorderStroke(1.dp, NeonCyan),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(Color(0xFF12123A), shape = RoundedCornerShape(8.dp))
                    .clickable { onLeaderboard() }
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "LEADERBOARD",
                    color = NeonCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Tap to Restart",
                color = NeonCyan,
                fontSize = 20.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .alpha(promptAlpha)
                    .clickable { onRestart() }
            )
        }
    }
}
