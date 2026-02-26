package com.portalritual.engine

data class RitualConfig(
    val lockToleranceDeg: Float = 4.0f,
    val lockDurationSec: Float = 1.0f,
    val stabilityBoostPerSec: Float = 20.0f,
    val stabilityDrainPerSec: Float = 8.0f,
    val initialStability: Float = 70.0f,
    val stabilizationDurationSec: Float = 2.0f
)
