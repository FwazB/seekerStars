package com.portalritual.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationEngineTest {

    // Triangle: stars [0, 1, 2] → sequential connections 0→1, 1→2
    private val triangle = ConstellationPatterns.TRIANGLE
    private val noop = ConstellationInput(deltaTime = 1f / 60f)
    private val tap = noop.copy(tapEvent = true)

    private fun activeState(
        connections: Set<Connection> = emptySet(),
        stepIndex: Int = 0,
        stability: Float = 100f,
        streak: Int = 0,
        streakTimer: Float = 0f,
        score: Int = 0,
        timeRemaining: Float = 15f
    ): ConstellationState {
        return ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.CONSTELLATION_ACTIVE,
            completedConnections = connections,
            currentStepIndex = stepIndex,
            stability = stability,
            streak = streak,
            streakTimer = streakTimer,
            score = score,
            timeRemaining = timeRemaining
        )
    }

    // ========================
    // INITIAL STATE + PHASE TRANSITIONS
    // ========================

    @Test
    fun `initial state is IDLE with step index 0`() {
        val state = ConstellationEngine.initialState(triangle)
        assertEquals(ConstellationPhase.IDLE, state.phase)
        assertTrue(state.completedConnections.isEmpty())
        assertEquals(0, state.currentStepIndex)
        assertEquals(100f, state.stability, 0.001f)
        assertEquals(0, state.score)
    }

    @Test
    fun `IDLE transitions to CONSTELLATION_ACTIVE on tap`() {
        val state = ConstellationEngine.initialState(triangle)
        val result = ConstellationEngine.step(state, tap)
        assertEquals(ConstellationPhase.CONSTELLATION_ACTIVE, result.phase)
    }

    @Test
    fun `IDLE stays without tap`() {
        val state = ConstellationEngine.initialState(triangle)
        val result = ConstellationEngine.step(state, noop)
        assertEquals(ConstellationPhase.IDLE, result.phase)
    }

    @Test
    fun `CONSTELLATION_COMPLETE transitions to TRACE_RUNE after 1_5s`() {
        var state = ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.CONSTELLATION_COMPLETE
        )
        repeat(91) { state = ConstellationEngine.step(state, noop) }
        assertEquals(ConstellationPhase.TRACE_RUNE, state.phase)
    }

    @Test
    fun `TRACE_RUNE transitions to RESULTS on accepted trace`() {
        val state = ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.TRACE_RUNE
        )
        val result = ConstellationEngine.step(state, noop.copy(traceAccepted = true))
        assertEquals(ConstellationPhase.RESULTS, result.phase)
    }

    @Test
    fun `RESULTS restarts on tap with full reset`() {
        val state = ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.RESULTS,
            score = 500,
            currentStepIndex = 2,
            stability = 60f
        )
        val result = ConstellationEngine.step(state, tap)
        assertEquals(ConstellationPhase.IDLE, result.phase)
        assertEquals(0, result.currentStepIndex)
        assertEquals(0, result.score)
        assertEquals(100f, result.stability, 0.001f)
    }

    @Test
    fun `COLLAPSED restarts on tap`() {
        val state = ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.COLLAPSED,
            stability = 0f
        )
        val result = ConstellationEngine.step(state, tap)
        assertEquals(ConstellationPhase.IDLE, result.phase)
        assertEquals(100f, result.stability, 0.001f)
    }

    // ========================
    // SEQUENTIAL CONNECTION VALIDATION
    // ========================

    @Test
    fun `correct sequential connection advances step index`() {
        // Step 0: must connect star 0 → star 1
        val state = activeState(stepIndex = 0)
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.currentStepIndex)
        assertEquals(1, result.completedConnections.size)
        assertEquals(EngineEvent.CORRECT_CONNECTION, result.lastEvent)
    }

    @Test
    fun `reversed correct connection still accepted (undirected)`() {
        val state = activeState(stepIndex = 0)
        val input = noop.copy(connectionMade = Connection(1, 0)) // reversed
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.currentStepIndex)
        assertEquals(1, result.completedConnections.size)
    }

    @Test
    fun `wrong order connection penalizes stability`() {
        // Step 0 expects 0→1, but player connects 1→2 (skipping ahead)
        val state = activeState(stepIndex = 0)
        val input = noop.copy(connectionMade = Connection(1, 2))
        val result = ConstellationEngine.step(state, input)
        assertEquals(0, result.currentStepIndex) // NOT advanced
        assertEquals(80f, result.stability, 0.001f) // -20
        assertEquals(EngineEvent.WRONG_CONNECTION, result.lastEvent)
    }

    @Test
    fun `completely invalid connection penalizes stability`() {
        val state = activeState(stepIndex = 0)
        val input = noop.copy(connectionMade = Connection(0, 5)) // star 5 doesn't exist
        val result = ConstellationEngine.step(state, input)
        assertEquals(0, result.currentStepIndex)
        assertEquals(80f, result.stability, 0.001f)
    }

    @Test
    fun `all sequential connections complete constellation`() {
        // Triangle: 3 stars, 2 connections. Step 0: 0→1, Step 1: 1→2
        var state = activeState(stepIndex = 0)

        // Step 0: connect 0→1
        state = ConstellationEngine.step(state, noop.copy(connectionMade = Connection(0, 1)))
        assertEquals(1, state.currentStepIndex)
        assertEquals(ConstellationPhase.CONSTELLATION_ACTIVE, state.phase)

        // Step 1: connect 1→2 (final)
        state = ConstellationEngine.step(state, noop.copy(connectionMade = Connection(1, 2)))
        assertEquals(2, state.currentStepIndex)
        assertEquals(ConstellationPhase.CONSTELLATION_COMPLETE, state.phase)
        assertEquals(2, state.completedConnections.size)
    }

    @Test
    fun `derived requiredConnections matches sequential star order`() {
        val conns = triangle.requiredConnections
        assertEquals(2, conns.size)
        assertEquals(Connection(0, 1), conns[0])
        assertEquals(Connection(1, 2), conns[1])
    }

    // ========================
    // STREAK + SCORE (sequential)
    // ========================

    @Test
    fun `first connection starts streak at 1 and scores 100`() {
        val state = activeState()
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.streak)
        assertEquals(100, result.score)
    }

    @Test
    fun `consecutive connections within window multiply score`() {
        val state = activeState(
            connections = setOf(Connection(0, 1).normalized()),
            stepIndex = 1,
            streak = 1,
            streakTimer = 2.0f,
            score = 100
        )
        val input = noop.copy(connectionMade = Connection(1, 2))
        val result = ConstellationEngine.step(state, input)
        assertEquals(2, result.streak)
        assertEquals(300, result.score) // 100 + 100*2
    }

    @Test
    fun `streak resets when timer expires`() {
        val state = activeState(streak = 3, streakTimer = 0.005f)
        val result = ConstellationEngine.step(state, noop)
        assertEquals(0, result.streak)
        assertEquals(EngineEvent.STREAK_BROKEN, result.lastEvent)
    }

    // ========================
    // COUNTDOWN
    // ========================

    @Test
    fun `time remaining decreases each frame`() {
        val state = activeState(timeRemaining = 20f)
        val result = ConstellationEngine.step(state, noop)
        assertTrue(result.timeRemaining < 20f)
    }

    @Test
    fun `timeout triggers COLLAPSED`() {
        val state = activeState(timeRemaining = 0.005f)
        val result = ConstellationEngine.step(state, noop)
        assertEquals(ConstellationPhase.COLLAPSED, result.phase)
        assertEquals(EngineEvent.TIMEOUT, result.lastEvent)
    }

    // ========================
    // STABILITY
    // ========================

    @Test
    fun `correct connection boosts stability by 5`() {
        val state = activeState(stability = 80f)
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(85f, result.stability, 0.001f)
    }

    @Test
    fun `stability clamps at 100`() {
        val state = activeState(stability = 98f)
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(100f, result.stability, 0.001f)
    }

    @Test
    fun `stability collapse from wrong connections`() {
        val state = activeState(stability = 15f, stepIndex = 0)
        // Wrong connection: 15 - 20 → 0 → collapse
        val input = noop.copy(connectionMade = Connection(1, 2))
        val result = ConstellationEngine.step(state, input)
        assertEquals(ConstellationPhase.COLLAPSED, result.phase)
        assertEquals(0f, result.stability, 0.001f)
        assertEquals(EngineEvent.STABILITY_COLLAPSE, result.lastEvent)
    }

    @Test
    fun `five wrong connections from full stability causes collapse`() {
        var state = activeState(stability = 100f, stepIndex = 0)
        repeat(4) {
            state = ConstellationEngine.step(state, noop.copy(connectionMade = Connection(1, 2)))
            assertEquals(ConstellationPhase.CONSTELLATION_ACTIVE, state.phase)
        }
        state = ConstellationEngine.step(state, noop.copy(connectionMade = Connection(1, 2)))
        assertEquals(ConstellationPhase.COLLAPSED, state.phase)
    }

    @Test
    fun `glitch intensity scales with stability`() {
        assertEquals(0f, ConstellationEngine.glitchIntensity(activeState(stability = 100f)), 0.001f)
        assertEquals(0.5f, ConstellationEngine.glitchIntensity(activeState(stability = 50f)), 0.001f)
        assertEquals(1f, ConstellationEngine.glitchIntensity(activeState(stability = 0f)), 0.001f)
    }

    // ========================
    // MISC
    // ========================

    @Test
    fun `frame counter increments every step`() {
        var state = ConstellationEngine.initialState(triangle)
        state = ConstellationEngine.step(state, noop)
        assertEquals(1L, state.frameCount)
        state = ConstellationEngine.step(state, noop)
        assertEquals(2L, state.frameCount)
    }

    @Test
    fun `lastEvent is cleared on frames with no event`() {
        val state = activeState().copy(lastEvent = EngineEvent.CORRECT_CONNECTION)
        val result = ConstellationEngine.step(state, noop)
        assertNull(result.lastEvent)
    }

    @Test
    fun `determinism - same inputs produce same outputs`() {
        val state = activeState()
        val input = noop.copy(connectionMade = Connection(0, 1))
        val r1 = ConstellationEngine.step(state, input)
        val r2 = ConstellationEngine.step(state, input)
        assertEquals(r1, r2)
    }
}
