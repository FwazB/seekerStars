package com.portalritual.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RitualEngineAlignmentTest {

    private val config = RitualConfig()
    private val targets = listOf(90f, 180f, 270f)

    private fun alignState(phase: RitualPhase, stability: Float = 70f): RitualState {
        return RitualEngine.initialState(config, targets).copy(
            phase = phase,
            stability = stability,
            glitchIntensity = RitualEngine.computeGlitch(stability)
        )
    }

    private fun inputAt(yaw: Float, dt: Float = 1f / 60f): RitualInput {
        return RitualInput(deltaTime = dt, yawBaselineDeg = yaw, swipeOffsetDeg = 0f)
    }

    // --- Lock timer accumulation ---

    @Test
    fun `lock timer accumulates when within tolerance`() {
        val state = alignState(RitualPhase.ALIGN_1)
        // Target is 90, input at 92 (within 4 deg)
        val result = RitualEngine.step(state, inputAt(yaw = 92f, dt = 0.5f), config)
        assertEquals(0.5f, result.rings[0].lockTimer, 0.001f)
    }

    @Test
    fun `lock timer resets when outside tolerance`() {
        val state = alignState(RitualPhase.ALIGN_1).let {
            val rings = it.rings.toMutableList()
            rings[0] = rings[0].copy(lockTimer = 0.8f)
            it.copy(rings = rings)
        }
        // Target is 90, input at 100 (outside 4 deg)
        val result = RitualEngine.step(state, inputAt(yaw = 100f), config)
        assertEquals(0f, result.rings[0].lockTimer, 0.001f)
    }

    @Test
    fun `ring locks after 1s of continuous alignment`() {
        var state = alignState(RitualPhase.ALIGN_1)
        val dt = 1f / 60f
        // 61 frames at ~16.67ms each > 1.0s total
        repeat(61) {
            state = RitualEngine.step(state, inputAt(yaw = 91f, dt = dt), config)
        }
        assertTrue(state.rings[0].locked)
    }

    // --- Phase advancement ---

    @Test
    fun `ALIGN_1 advances to ALIGN_2 on lock`() {
        var state = alignState(RitualPhase.ALIGN_1)
        // Use a single large dt to trigger lock immediately
        state = RitualEngine.step(state, inputAt(yaw = 90f, dt = 1.1f), config)
        assertEquals(RitualPhase.ALIGN_2, state.phase)
    }

    @Test
    fun `ALIGN_2 advances to ALIGN_3 on lock`() {
        var state = alignState(RitualPhase.ALIGN_2)
        state = RitualEngine.step(state, inputAt(yaw = 180f, dt = 1.1f), config)
        assertEquals(RitualPhase.ALIGN_3, state.phase)
    }

    @Test
    fun `ALIGN_3 advances to TRACE_RUNE on lock`() {
        var state = alignState(RitualPhase.ALIGN_3)
        state = RitualEngine.step(state, inputAt(yaw = 270f, dt = 1.1f), config)
        assertEquals(RitualPhase.TRACE_RUNE, state.phase)
    }

    // --- Boundary tolerance ---

    @Test
    fun `exact tolerance boundary is within tolerance (inclusive)`() {
        // Target = 90, angle = 94 -> distance = 4.0 exactly
        val ring = RingState(targetAngleDeg = 90f)
        val updated = RitualEngine.updateLockTimer(ring, 94f, 0.5f, config)
        assertEquals(0.5f, updated.lockTimer, 0.001f)
    }

    @Test
    fun `just outside tolerance resets timer`() {
        // Target = 90, angle = 94.01 -> distance = 4.01 > 4.0
        val ring = RingState(targetAngleDeg = 90f, lockTimer = 0.8f)
        val updated = RitualEngine.updateLockTimer(ring, 94.01f, 0.5f, config)
        assertEquals(0f, updated.lockTimer, 0.001f)
    }

    // --- Locked rings persist ---

    @Test
    fun `previously locked rings remain locked when advancing`() {
        // Lock ring 0, then step in ALIGN_2
        var state = alignState(RitualPhase.ALIGN_2).let {
            val rings = it.rings.toMutableList()
            rings[0] = rings[0].copy(locked = true)
            it.copy(rings = rings)
        }
        state = RitualEngine.step(state, inputAt(yaw = 0f), config)
        assertTrue(state.rings[0].locked)
    }
}
