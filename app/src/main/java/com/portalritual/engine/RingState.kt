package com.portalritual.engine

data class RingState(
    val targetAngleDeg: Float,
    val lockTimer: Float = 0.0f,
    val locked: Boolean = false
)
