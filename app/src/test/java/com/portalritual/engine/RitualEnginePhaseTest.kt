package com.portalritual.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RitualEnginePhaseTest {

    private val config = RitualConfig()
    private val targets = listOf(90f, 180f, 270f)
    private val noopInput = RitualInput(deltaTime = 1f / 60f, yawBaselineDeg = 0f, swipeOffsetDeg = 0f)
    private val tapInput = noopInput.copy(tapEvent = true)

    // --- Initial state ---

    @Test
    fun `initial state is IDLE`() {
        val state = RitualEngine.initialState(config, targets)
        assertEquals(RitualPhase.IDLE, state.phase)
    }

    @Test
    fun `initial stability matches config`() {
        val state = RitualEngine.initialState(config, targets)
        assertEquals(config.initialStability, state.stability, 0.001f)
    }

    @Test
    fun `initial state has 3 rings`() {
        val state = RitualEngine.initialState(config, targets)
        assertEquals(3, state.rings.size)
        assertEquals(90f, state.rings[0].targetAngleDeg, 0.001f)
        assertEquals(180f, state.rings[1].targetAngleDeg, 0.001f)
        assertEquals(270f, state.rings[2].targetAngleDeg, 0.001f)
    }

    // --- IDLE ---

    @Test
    fun `IDLE transitions to MANIFESTING on tap`() {
        val state = RitualEngine.initialState(config, targets)
        val result = RitualEngine.step(state, tapInput, config)
        assertEquals(RitualPhase.MANIFESTING, result.phase)
    }

    @Test
    fun `IDLE stays without tap`() {
        val state = RitualEngine.initialState(config, targets)
        val result = RitualEngine.step(state, noopInput, config)
        assertEquals(RitualPhase.IDLE, result.phase)
    }

    // --- MANIFESTING ---

    @Test
    fun `MANIFESTING transitions to ALIGN_1 on tap`() {
        val state = RitualEngine.initialState(config, targets).copy(phase = RitualPhase.MANIFESTING)
        val result = RitualEngine.step(state, tapInput, config)
        assertEquals(RitualPhase.ALIGN_1, result.phase)
    }

    // --- TRACE_RUNE ---

    @Test
    fun `TRACE_RUNE transitions to STABILIZED on accepted trace`() {
        val state = RitualEngine.initialState(config, targets).copy(phase = RitualPhase.TRACE_RUNE)
        val input = noopInput.copy(traceAccepted = true)
        val result = RitualEngine.step(state, input, config)
        assertEquals(RitualPhase.STABILIZED, result.phase)
    }

    @Test
    fun `TRACE_RUNE stays on rejected trace`() {
        val state = RitualEngine.initialState(config, targets).copy(phase = RitualPhase.TRACE_RUNE)
        val input = noopInput.copy(traceAccepted = false)
        val result = RitualEngine.step(state, input, config)
        assertEquals(RitualPhase.TRACE_RUNE, result.phase)
    }

    @Test
    fun `TRACE_RUNE stays when no trace submitted`() {
        val state = RitualEngine.initialState(config, targets).copy(phase = RitualPhase.TRACE_RUNE)
        val result = RitualEngine.step(state, noopInput, config)
        assertEquals(RitualPhase.TRACE_RUNE, result.phase)
    }

    // --- STABILIZED ---

    @Test
    fun `STABILIZED transitions to RESULTS after duration`() {
        var state = RitualEngine.initialState(config, targets).copy(phase = RitualPhase.STABILIZED)
        // 2.0s stabilization at 60fps -> 120 frames
        repeat(121) {
            state = RitualEngine.step(state, noopInput, config)
        }
        assertEquals(RitualPhase.RESULTS, state.phase)
    }

    // --- Terminal states + retry ---

    @Test
    fun `RESULTS allows retry via tap`() {
        val state = RitualEngine.initialState(config, targets).copy(phase = RitualPhase.RESULTS)
        val result = RitualEngine.step(state, tapInput, config)
        assertEquals(RitualPhase.IDLE, result.phase)
        assertEquals(config.initialStability, result.stability, 0.001f)
    }

    @Test
    fun `COLLAPSED allows retry via tap`() {
        val state = RitualEngine.initialState(config, targets).copy(
            phase = RitualPhase.COLLAPSED,
            stability = 0f
        )
        val result = RitualEngine.step(state, tapInput, config)
        assertEquals(RitualPhase.IDLE, result.phase)
        assertEquals(config.initialStability, result.stability, 0.001f)
    }

    // --- Frame counter ---

    @Test
    fun `frame counter increments every step`() {
        var state = RitualEngine.initialState(config, targets)
        assertEquals(0L, state.frameCount)
        state = RitualEngine.step(state, noopInput, config)
        assertEquals(1L, state.frameCount)
        state = RitualEngine.step(state, noopInput, config)
        assertEquals(2L, state.frameCount)
        state = RitualEngine.step(state, noopInput, config)
        assertEquals(3L, state.frameCount)
    }

    // --- Determinism ---

    @Test
    fun `determinism - same inputs produce same outputs`() {
        val state = RitualEngine.initialState(config, targets).copy(phase = RitualPhase.ALIGN_1)
        val input = RitualInput(deltaTime = 0.016f, yawBaselineDeg = 88f, swipeOffsetDeg = 3f)
        val result1 = RitualEngine.step(state, input, config)
        val result2 = RitualEngine.step(state, input, config)
        assertEquals(result1, result2)
    }
}
