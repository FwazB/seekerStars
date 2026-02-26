package com.portalritual.engine

import kotlin.math.abs

object RitualEngine {

    fun initialState(config: RitualConfig, ringTargets: List<Float>): RitualState {
        val rings = ringTargets.map { RingState(targetAngleDeg = it) }
        return RitualState(
            phase = RitualPhase.IDLE,
            rings = rings,
            stability = config.initialStability,
            glitchIntensity = computeGlitch(config.initialStability),
            currentCombinedAngleDeg = 0f
        )
    }

    fun step(state: RitualState, input: RitualInput, config: RitualConfig): RitualState {
        val nextFrame = state.frameCount + 1

        return when (state.phase) {
            RitualPhase.IDLE -> {
                if (input.tapEvent) {
                    state.copy(phase = RitualPhase.MANIFESTING, frameCount = nextFrame)
                } else {
                    state.copy(frameCount = nextFrame)
                }
            }

            RitualPhase.MANIFESTING -> {
                if (input.tapEvent) {
                    state.copy(phase = RitualPhase.ALIGN_1, frameCount = nextFrame)
                } else {
                    state.copy(frameCount = nextFrame)
                }
            }

            RitualPhase.ALIGN_1, RitualPhase.ALIGN_2, RitualPhase.ALIGN_3 -> {
                stepAlignment(state, input, config, nextFrame)
            }

            RitualPhase.TRACE_RUNE -> {
                when (input.traceAccepted) {
                    true -> state.copy(
                        phase = RitualPhase.STABILIZED,
                        stabilizationTimer = 0f,
                        frameCount = nextFrame
                    )
                    else -> state.copy(frameCount = nextFrame)
                }
            }

            RitualPhase.STABILIZED -> {
                val newTimer = state.stabilizationTimer + input.deltaTime
                if (newTimer >= config.stabilizationDurationSec) {
                    state.copy(
                        phase = RitualPhase.RESULTS,
                        stabilizationTimer = newTimer,
                        frameCount = nextFrame
                    )
                } else {
                    state.copy(stabilizationTimer = newTimer, frameCount = nextFrame)
                }
            }

            RitualPhase.RESULTS, RitualPhase.COLLAPSED -> {
                if (input.tapEvent) {
                    initialState(config, state.rings.map { it.targetAngleDeg })
                } else {
                    state.copy(frameCount = nextFrame)
                }
            }
        }
    }

    fun computeGlitch(stability: Float): Float {
        return 1.0f - (stability.coerceIn(0f, 100f) / 100.0f)
    }

    internal fun updateLockTimer(
        ring: RingState,
        currentAngle: Float,
        deltaTime: Float,
        config: RitualConfig
    ): RingState {
        if (ring.locked) return ring

        val distance = abs(shortestAngleDistance(currentAngle, ring.targetAngleDeg))
        return if (distance <= config.lockToleranceDeg) {
            val newTimer = ring.lockTimer + deltaTime
            if (newTimer >= config.lockDurationSec) {
                ring.copy(lockTimer = newTimer, locked = true)
            } else {
                ring.copy(lockTimer = newTimer)
            }
        } else {
            ring.copy(lockTimer = 0f)
        }
    }

    internal fun stabilityUpdate(
        currentStability: Float,
        withinTolerance: Boolean,
        deltaTime: Float,
        config: RitualConfig
    ): Float {
        val delta = if (withinTolerance) {
            config.stabilityBoostPerSec * deltaTime
        } else {
            -config.stabilityDrainPerSec * deltaTime
        }
        return (currentStability + delta).coerceIn(0f, 100f)
    }

    private fun stepAlignment(
        state: RitualState,
        input: RitualInput,
        config: RitualConfig,
        nextFrame: Long
    ): RitualState {
        val ringIndex = state.phase.activeRingIndex
        val currentAngle = combineRotation(input.yawBaselineDeg, input.swipeOffsetDeg)
        val ring = state.rings[ringIndex]

        val distance = abs(shortestAngleDistance(currentAngle, ring.targetAngleDeg))
        val withinTolerance = distance <= config.lockToleranceDeg

        val newStability = stabilityUpdate(state.stability, withinTolerance, input.deltaTime, config)
        val newGlitch = computeGlitch(newStability)

        // Collapse check first — collapse wins over lock on same frame
        if (newStability <= 0f) {
            return state.copy(
                phase = RitualPhase.COLLAPSED,
                stability = 0f,
                glitchIntensity = 1f,
                currentCombinedAngleDeg = currentAngle,
                frameCount = nextFrame
            )
        }

        val updatedRing = updateLockTimer(ring, currentAngle, input.deltaTime, config)
        val newRings = state.rings.toMutableList()
        newRings[ringIndex] = updatedRing

        val newPhase = if (updatedRing.locked) state.phase.nextAfterLock() else state.phase

        return state.copy(
            phase = newPhase,
            rings = newRings,
            stability = newStability,
            glitchIntensity = newGlitch,
            currentCombinedAngleDeg = currentAngle,
            frameCount = nextFrame
        )
    }
}
