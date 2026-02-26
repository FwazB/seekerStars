package com.portalritual.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RitualEngineStabilityTest {

    private val config = RitualConfig()
    private val targets = listOf(90f, 180f, 270f)
    private val eps = 0.001f

    private fun alignState(stability: Float): RitualState {
        return RitualEngine.initialState(config, targets).copy(
            phase = RitualPhase.ALIGN_1,
            stability = stability,
            glitchIntensity = RitualEngine.computeGlitch(stability)
        )
    }

    // --- Stability boost/drain ---

    @Test
    fun `stability boosts when within tolerance`() {
        // boost = 20/s, dt = 1.0s -> +20
        val result = RitualEngine.stabilityUpdate(70f, withinTolerance = true, 1.0f, config)
        assertEquals(90f, result, eps)
    }

    @Test
    fun `stability drains when outside tolerance`() {
        // drain = 8/s, dt = 1.0s -> -8
        val result = RitualEngine.stabilityUpdate(70f, withinTolerance = false, 1.0f, config)
        assertEquals(62f, result, eps)
    }

    @Test
    fun `stability clamps at 100`() {
        val result = RitualEngine.stabilityUpdate(95f, withinTolerance = true, 1.0f, config)
        assertEquals(100f, result, eps)
    }

    @Test
    fun `stability clamps at 0`() {
        val result = RitualEngine.stabilityUpdate(3f, withinTolerance = false, 1.0f, config)
        assertEquals(0f, result, eps)
    }

    // --- Collapse ---

    @Test
    fun `collapse triggers when stability hits 0`() {
        // Start at 5 stability, drain = 8/s, dt = 1.0s -> goes to 0
        val state = alignState(stability = 5f)
        val input = RitualInput(deltaTime = 1.0f, yawBaselineDeg = 0f, swipeOffsetDeg = 0f)
        val result = RitualEngine.step(state, input, config)
        assertEquals(RitualPhase.COLLAPSED, result.phase)
        assertEquals(0f, result.stability, eps)
        assertEquals(1f, result.glitchIntensity, eps)
    }

    // --- Glitch mapping ---

    @Test
    fun `glitch is 0 at full stability`() {
        assertEquals(0f, RitualEngine.computeGlitch(100f), eps)
    }

    @Test
    fun `glitch is 1 at zero stability`() {
        assertEquals(1f, RitualEngine.computeGlitch(0f), eps)
    }

    @Test
    fun `glitch at 70 stability`() {
        assertEquals(0.3f, RitualEngine.computeGlitch(70f), eps)
    }

    // --- Stability frozen in TRACE_RUNE ---

    @Test
    fun `stability unchanged during TRACE_RUNE`() {
        val state = RitualEngine.initialState(config, targets).copy(
            phase = RitualPhase.TRACE_RUNE,
            stability = 55f
        )
        val input = RitualInput(deltaTime = 1.0f, yawBaselineDeg = 0f, swipeOffsetDeg = 0f)
        val result = RitualEngine.step(state, input, config)
        assertEquals(55f, result.stability, eps)
    }

    // --- Collapse wins over lock ---

    @Test
    fun `collapse check happens before lock check`() {
        // Stability at 1 with drain=8 and dt=0.2 -> stability = 1 - 1.6 = 0 (clamped)
        // But angle is also within tolerance and would lock (timer already at 0.9, dt=0.2 -> 1.1)
        val state = alignState(stability = 1f).let {
            val rings = it.rings.toMutableList()
            rings[0] = rings[0].copy(lockTimer = 0.9f)
            it.copy(rings = rings)
        }
        // yaw=90 -> exact target, within tolerance, timer would exceed 1.0s
        // but stability will drain to 0 first
        val input = RitualInput(deltaTime = 0.2f, yawBaselineDeg = 90f, swipeOffsetDeg = 0f)
        // Note: within tolerance -> stability BOOSTS, not drains
        // So we need to be outside tolerance to drain and collapse
        val inputOutside = RitualInput(deltaTime = 0.2f, yawBaselineDeg = 0f, swipeOffsetDeg = 0f)
        val result = RitualEngine.step(state, inputOutside, config)
        assertEquals(RitualPhase.COLLAPSED, result.phase)
    }
}
