package com.epochdefenders.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.epochdefenders.solana.LeaderboardEntry

// ── TRON Neon Palette ────────────────────────────────────────────────────

private val NeonCyan = Color(0xFF00FFFF)
private val NeonGreen = Color(0xFF00FF44)
private val NeonRed = Color(0xFFFF0044)
private val NeonYellow = Color(0xFFFFFF00)
private val HudBg = Color(0xFF0A0A2A)
private val HudBorder = NeonCyan
private val CardBg = Color(0xFF12123A)
private val GoldColor = Color(0xFFFFD700)
private val SilverColor = Color(0xFFC0C0C0)
private val BronzeColor = Color(0xFFCD7F32)

// ── Main Screen ──────────────────────────────────────────────────────────

@Composable
fun LeaderboardScreen(
    entries: List<LeaderboardEntry>,
    isLoading: Boolean,
    error: String?,
    playerScore: Int?,
    playerWave: Int?,
    isSubmitting: Boolean,
    onSubmitScore: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
    ) {
        // 1 ── Header bar ─────────────────────────────────────────────
        HeaderBar(onBack = onBack, onRefresh = onRefresh)

        // 2 ── Player score card ──────────────────────────────────────
        if (playerScore != null) {
            PlayerScoreCard(
                score = playerScore,
                wave = playerWave ?: 0,
                isSubmitting = isSubmitting,
                onSubmitScore = onSubmitScore
            )
        }

        // 3 ── Leaderboard list ───────────────────────────────────────
        LeaderboardList(
            entries = entries,
            isLoading = isLoading,
            error = error,
            onRetry = onRefresh,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Header Bar ───────────────────────────────────────────────────────────

@Composable
private fun HeaderBar(onBack: () -> Unit, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        // Back arrow — left
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = NeonCyan
            )
        }

        // Title — center
        Text(
            text = "LOCAL HIGH SCORES",
            color = NeonCyan,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        // Refresh — right
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            // Simple text-based refresh icon to avoid extra icon dependency
            Text(
                text = "\u21BB", // ↻ unicode refresh arrow
                color = NeonCyan,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Neon border line beneath header
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(HudBorder)
    )
}

// ── Player Score Card ────────────────────────────────────────────────────

@Composable
private fun PlayerScoreCard(
    score: Int,
    wave: Int,
    isSubmitting: Boolean,
    onSubmitScore: () -> Unit
) {
    // Pulsing animation for "Submitting..." text
    val pulse = rememberInfiniteTransition(label = "submitPulse")
    val submitAlpha by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "submitGlow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(
                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp)
            )
            .background(CardBg, shape = RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YOUR SCORE: $score  |  WAVE $wave",
            color = NeonGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isSubmitting) {
            Text(
                text = "SAVING...",
                color = NeonYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(submitAlpha)
            )
        } else {
            OutlinedButton(
                onClick = onSubmitScore,
                border = BorderStroke(1.dp, NeonGreen),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardBg,
                    contentColor = NeonGreen
                )
            ) {
                Text(
                    text = "SAVE SCORE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
}

// ── Leaderboard List ─────────────────────────────────────────────────────

@Composable
private fun LeaderboardList(
    entries: List<LeaderboardEntry>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            // Loading state — skeleton placeholders
            isLoading -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(5) {
                        SkeletonRow()
                    }
                }
            }

            // Error state
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error.uppercase(),
                        color = NeonRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        border = BorderStroke(1.dp, NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardBg,
                            contentColor = NeonCyan
                        )
                    ) {
                        Text(
                            text = "RETRY",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // Empty state
            entries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO ENTRIES YET",
                        color = NeonCyan.copy(alpha = 0.5f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Entries list
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Column headers
                    LeaderboardHeaderRow()

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(HudBorder.copy(alpha = 0.3f))
                    )

                    // Scrollable rows (cap at 20)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val displayEntries = entries.take(20)
                        itemsIndexed(displayEntries) { _, entry ->
                            LeaderboardRow(entry = entry)
                        }

                        // Bottom spacer so the last row isn't clipped
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ── Column Headers ───────────────────────────────────────────────────────

@Composable
private fun LeaderboardHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            text = "RANK",
            color = NeonCyan.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.width(48.dp)
        )
        // Player
        Text(
            text = "PLAYER",
            color = NeonCyan.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        // Score
        Text(
            text = "SCORE",
            color = NeonCyan.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
        // Wave
        Text(
            text = "WAVE",
            color = NeonCyan.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}

// ── Leaderboard Row ──────────────────────────────────────────────────────

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val rankColor = when (entry.rank) {
        1 -> GoldColor
        2 -> SilverColor
        3 -> BronzeColor
        else -> Color.White.copy(alpha = 0.8f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            text = "#${entry.rank}",
            color = rankColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.width(48.dp)
        )

        // Player name
        Text(
            text = entry.walletAddress,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Score
        Text(
            text = formatScore(entry.score),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )

        // Wave
        Text(
            text = "${entry.waveReached}",
            color = NeonCyan.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}

// ── Skeleton Row ─────────────────────────────────────────────────────────

@Composable
private fun SkeletonRow() {
    val shimmer = rememberInfiniteTransition(label = "skeleton")
    val shimmerAlpha by shimmer.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CardBg)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank placeholder
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(14.dp)
                .background(
                    NeonCyan.copy(alpha = shimmerAlpha),
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(modifier = Modifier.width(16.dp))
        // Player placeholder
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .background(
                    NeonCyan.copy(alpha = shimmerAlpha),
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(modifier = Modifier.width(16.dp))
        // Score placeholder
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(14.dp)
                .background(
                    NeonCyan.copy(alpha = shimmerAlpha),
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Wave placeholder
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(14.dp)
                .background(
                    NeonCyan.copy(alpha = shimmerAlpha),
                    RoundedCornerShape(3.dp)
                )
        )
    }
}

// ── Utilities ────────────────────────────────────────────────────────────

/**
 * Formats a score integer with comma separators for readability.
 * Example: 12345 -> "12,345"
 */
private fun formatScore(score: Int): String {
    return "%,d".format(score)
}
