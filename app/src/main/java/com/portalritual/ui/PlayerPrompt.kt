package com.portalritual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portalritual.engine.ConstellationPhase
import com.portalritual.engine.ConstellationState

@Composable
fun PlayerPrompt(state: ConstellationState, modifier: Modifier = Modifier) {
    val (title, hint) = when (state.phase) {
        ConstellationPhase.IDLE -> "TAP TO BEGIN" to "Touch the screen to reveal the stars"
        ConstellationPhase.CONSTELLATION_ACTIVE -> "CONNECT THE STARS" to "Drag from star to star in numbered order"
        ConstellationPhase.CONSTELLATION_COMPLETE -> "CONSTELLATION FORMED" to "The pattern awakens..."
        ConstellationPhase.TRACE_RUNE -> "TRACE THE RUNE" to "Draw the sigil with your finger"
        ConstellationPhase.RESULTS -> "RITUAL COMPLETE" to "Tap to begin again"
        ConstellationPhase.COLLAPSED -> "COLLAPSED" to "The constellation destabilized"
    }

    val progressText = if (state.phase == ConstellationPhase.CONSTELLATION_ACTIVE) {
        "${state.completedConnections.size}/${state.pattern.requiredConnections.size} connections"
    } else null

    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF00FFFF),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 3.sp
            )
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (progressText != null) {
                Text(
                    text = progressText,
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
