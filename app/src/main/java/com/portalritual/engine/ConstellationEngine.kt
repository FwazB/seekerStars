package com.portalritual.engine

object ConstellationEngine {

    private const val COMPLETION_BEAT_SEC = 1.5f
    private const val STREAK_WINDOW_SEC = 2.5f
    private const val POINTS_PER_CONNECTION = 100
    private const val STABILITY_WRONG_PENALTY = 20f
    private const val STABILITY_CORRECT_BONUS = 5f
    private const val INITIAL_STABILITY = 100f

    fun initialState(pattern: ConstellationPattern): ConstellationState {
        return ConstellationState(
            phase = ConstellationPhase.IDLE,
            pattern = pattern,
            completedConnections = emptySet(),
            timeRemaining = pattern.timeLimitSec,
            timeLimitSec = pattern.timeLimitSec,
            stability = INITIAL_STABILITY
        )
    }

    fun step(state: ConstellationState, input: ConstellationInput): ConstellationState {
        val nextFrame = state.frameCount + 1

        return when (state.phase) {
            ConstellationPhase.IDLE -> {
                if (input.tapEvent) {
                    state.copy(
                        phase = ConstellationPhase.CONSTELLATION_ACTIVE,
                        lastEvent = null,
                        frameCount = nextFrame
                    )
                } else {
                    state.copy(lastEvent = null, frameCount = nextFrame)
                }
            }

            ConstellationPhase.CONSTELLATION_ACTIVE -> {
                stepActive(state, input, nextFrame)
            }

            ConstellationPhase.CONSTELLATION_COMPLETE -> {
                val newTimer = state.completionTimer + input.deltaTime
                if (newTimer >= COMPLETION_BEAT_SEC) {
                    state.copy(
                        phase = ConstellationPhase.TRACE_RUNE,
                        completionTimer = newTimer,
                        lastEvent = null,
                        frameCount = nextFrame
                    )
                } else {
                    state.copy(
                        completionTimer = newTimer,
                        lastEvent = null,
                        frameCount = nextFrame
                    )
                }
            }

            ConstellationPhase.TRACE_RUNE -> {
                when (input.traceAccepted) {
                    true -> state.copy(
                        phase = ConstellationPhase.RESULTS,
                        lastEvent = null,
                        frameCount = nextFrame
                    )
                    else -> state.copy(lastEvent = null, frameCount = nextFrame)
                }
            }

            ConstellationPhase.RESULTS, ConstellationPhase.COLLAPSED -> {
                if (input.tapEvent) {
                    initialState(state.pattern)
                } else {
                    state.copy(lastEvent = null, frameCount = nextFrame)
                }
            }
        }
    }

    fun timeRemainingPct(state: ConstellationState): Float {
        if (state.timeLimitSec <= 0f) return 1f
        return (state.timeRemaining / state.timeLimitSec).coerceIn(0f, 1f)
    }

    fun glitchIntensity(state: ConstellationState): Float {
        return 1f - (state.stability / INITIAL_STABILITY).coerceIn(0f, 1f)
    }

    private fun stepActive(
        state: ConstellationState,
        input: ConstellationInput,
        nextFrame: Long
    ): ConstellationState {
        var event: EngineEvent? = null
        var stability = state.stability
        var streak = state.streak
        var streakTimer = state.streakTimer
        var score = state.score

        // 1. Drain countdown
        val newTimeRemaining = (state.timeRemaining - input.deltaTime).coerceAtLeast(0f)
        if (newTimeRemaining <= 0f) {
            return state.copy(
                phase = ConstellationPhase.COLLAPSED,
                timeRemaining = 0f,
                lastEvent = EngineEvent.TIMEOUT,
                frameCount = nextFrame
            )
        }

        // 2. Drain streak timer
        if (streakTimer > 0f) {
            streakTimer = (streakTimer - input.deltaTime).coerceAtLeast(0f)
            if (streakTimer <= 0f && streak > 0) {
                streak = 0
                event = EngineEvent.STREAK_BROKEN
            }
        }

        // 3. Process connection
        val conn = input.connectionMade?.normalized()
        var completedConnections = state.completedConnections

        if (conn != null) {
            val required = state.pattern.requiredConnections.map { it.normalized() }.toSet()

            when {
                conn !in required -> {
                    // Wrong connection — penalty
                    stability = (stability - STABILITY_WRONG_PENALTY).coerceAtLeast(0f)
                    event = if (stability <= 0f) {
                        EngineEvent.STABILITY_COLLAPSE
                    } else {
                        EngineEvent.WRONG_CONNECTION
                    }

                    if (stability <= 0f) {
                        return state.copy(
                            phase = ConstellationPhase.COLLAPSED,
                            stability = 0f,
                            streak = streak,
                            streakTimer = streakTimer,
                            score = score,
                            timeRemaining = newTimeRemaining,
                            lastEvent = EngineEvent.STABILITY_COLLAPSE,
                            frameCount = nextFrame
                        )
                    }
                }
                conn in completedConnections -> {
                    // Duplicate — ignore, keep whatever event was set (streak broken)
                }
                else -> {
                    // Valid new connection
                    completedConnections = completedConnections + conn
                    stability = (stability + STABILITY_CORRECT_BONUS).coerceAtMost(INITIAL_STABILITY)

                    streak = if (streakTimer > 0f || streak > 0) streak + 1 else 1
                    score += POINTS_PER_CONNECTION * streak
                    streakTimer = STREAK_WINDOW_SEC
                    event = EngineEvent.CORRECT_CONNECTION
                }
            }
        }

        // 4. Check completion
        val required = state.pattern.requiredConnections.map { it.normalized() }.toSet()
        return if (completedConnections.size == required.size) {
            state.copy(
                phase = ConstellationPhase.CONSTELLATION_COMPLETE,
                completedConnections = completedConnections,
                completionTimer = 0f,
                score = score,
                streak = streak,
                streakTimer = streakTimer,
                stability = stability,
                timeRemaining = newTimeRemaining,
                lastEvent = event,
                frameCount = nextFrame
            )
        } else {
            state.copy(
                completedConnections = completedConnections,
                score = score,
                streak = streak,
                streakTimer = streakTimer,
                stability = stability,
                timeRemaining = newTimeRemaining,
                lastEvent = event,
                frameCount = nextFrame
            )
        }
    }
}
