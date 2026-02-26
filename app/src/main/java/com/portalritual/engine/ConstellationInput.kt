package com.portalritual.engine

data class ConstellationInput(
    val deltaTime: Float,
    val tapEvent: Boolean = false,
    val connectionMade: Connection? = null,
    val traceAccepted: Boolean? = null
)
