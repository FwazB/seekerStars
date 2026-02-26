package com.portalritual.engine

data class RitualInput(
    val deltaTime: Float,
    val yawBaselineDeg: Float,
    val swipeOffsetDeg: Float,
    val tapEvent: Boolean = false,
    val traceAccepted: Boolean? = null
)
