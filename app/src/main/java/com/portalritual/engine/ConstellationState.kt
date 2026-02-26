package com.portalritual.engine

data class ConstellationState(
    val phase: ConstellationPhase,
    val pattern: ConstellationPattern,
    val completedConnections: Set<Connection>,
    val completionTimer: Float = 0f,
    val frameCount: Long = 0,
    // Streak + score
    val score: Int = 0,
    val streak: Int = 0,
    val streakTimer: Float = 0f,
    // Countdown
    val timeRemaining: Float = 0f,
    val timeLimitSec: Float = 0f,
    // Stability
    val stability: Float = 100f,
    // Renderer event (cleared each frame)
    val lastEvent: EngineEvent? = null
)
