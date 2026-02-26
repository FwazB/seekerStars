package com.portalritual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portalritual.engine.ConstellationState

@Composable
fun ConstellationOverlay(state: ConstellationState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        DebugLine("Phase", state.phase.name)
        DebugLine("Stars", "${state.pattern.stars.size}")
        DebugLine("Connections", "${state.completedConnections.size}/${state.pattern.requiredConnections.size}")
        if (state.completionTimer > 0f) {
            DebugLine("Timer", "%.1fs".format(state.completionTimer))
        }
        DebugLine("Frame", "${state.frameCount}")
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = Color.Green,
        fontSize = 12.sp
    )
}
