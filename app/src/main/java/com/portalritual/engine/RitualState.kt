package com.portalritual.engine

data class RitualState(
    val phase: RitualPhase,
    val rings: List<RingState>,
    val stability: Float,
    val glitchIntensity: Float,
    val currentCombinedAngleDeg: Float,
    val stabilizationTimer: Float = 0.0f,
    val frameCount: Long = 0
)
