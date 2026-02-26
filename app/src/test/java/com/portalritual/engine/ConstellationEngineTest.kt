package com.portalritual.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationEngineTest {

    private val triangle = ConstellationPatterns.TRIANGLE
    private val noop = ConstellationInput(deltaTime = 1f / 60f)
    private val tap = noop.copy(tapEvent = true)

    private fun activeState(
        connections: Set<Connection> = emptySet(),
        stability: Float = 100f,
        streak: Int = 0,
        streakTimer: Float = 0f,
        score: Int = 0,
        timeRemaining: Float = 20f
    ): ConstellationState {
        return ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.CONSTELLATION_ACTIVE,
            completedConnections = connections,
            stability = stability,
            streak = streak,
            streakTimer = streakTimer,
            score = score,
            timeRemaining = timeRemaining
        )
    }

    // ========================
    // ORIGINAL TESTS (updated)
    // ========================

    @Test
    fun `initial state is IDLE with no completed connections`() {
        val state = ConstellationEngine.initialState(triangle)
        assertEquals(ConstellationPhase.IDLE, state.phase)
        assertTrue(state.completedConnections.isEmpty())
        assertEquals(0L, state.frameCount)
        assertEquals(100f, state.stability, 0.001f)
        assertEquals(0, state.score)
        assertEquals(20f, state.timeRemaining, 0.001f)
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
    fun `valid connection is accepted`() {
        val state = activeState()
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.completedConnections.size)
        assertTrue(result.completedConnections.contains(Connection(0, 1).normalized()))
    }

    @Test
    fun `reversed connection is treated as same edge`() {
        val state = activeState()
        val input = noop.copy(connectionMade = Connection(1, 0))
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.completedConnections.size)
        assertTrue(result.completedConnections.contains(Connection(0, 1)))
    }

    @Test
    fun `duplicate connection is ignored`() {
        val state = activeState(connections = setOf(Connection(0, 1).normalized()))
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.completedConnections.size)
    }

    @Test
    fun `all connections completes constellation`() {
        val state = activeState(
            connections = setOf(
                Connection(0, 1).normalized(),
                Connection(1, 2).normalized()
            )
        )
        val input = noop.copy(connectionMade = Connection(0, 2))
        val result = ConstellationEngine.step(state, input)
        assertEquals(ConstellationPhase.CONSTELLATION_COMPLETE, result.phase)
        assertEquals(3, result.completedConnections.size)
    }

    @Test
    fun `CONSTELLATION_COMPLETE transitions to TRACE_RUNE after 1_5s`() {
        var state = ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.CONSTELLATION_COMPLETE
        )
        repeat(91) {
            state = ConstellationEngine.step(state, noop)
        }
        assertEquals(ConstellationPhase.TRACE_RUNE, state.phase)
    }

    @Test
    fun `TRACE_RUNE transitions to RESULTS on accepted trace`() {
        val state = ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.TRACE_RUNE
        )
        val input = noop.copy(traceAccepted = true)
        val result = ConstellationEngine.step(state, input)
        assertEquals(ConstellationPhase.RESULTS, result.phase)
    }

    @Test
    fun `RESULTS restarts on tap`() {
        val state = ConstellationEngine.initialState(triangle).copy(
            phase = ConstellationPhase.RESULTS,
            score = 500,
            stability = 60f
        )
        val result = ConstellationEngine.step(state, tap)
        assertEquals(ConstellationPhase.IDLE, result.phase)
        assertTrue(result.completedConnections.isEmpty())
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

    @Test
    fun `frame counter increments every step`() {
        var state = ConstellationEngine.initialState(triangle)
        assertEquals(0L, state.frameCount)
        state = ConstellationEngine.step(state, noop)
        assertEquals(1L, state.frameCount)
        state = ConstellationEngine.step(state, noop)
        assertEquals(2L, state.frameCount)
    }

    @Test
    fun `determinism - same inputs produce same outputs`() {
        val state = activeState()
        val input = noop.copy(connectionMade = Connection(0, 1))
        val r1 = ConstellationEngine.step(state, input)
        val r2 = ConstellationEngine.step(state, input)
        assertEquals(r1, r2)
    }

    // ========================
    // STREAK + SCORE TESTS
    // ========================

    @Test
    fun `first connection starts streak at 1 and scores 100`() {
        val state = activeState()
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.streak)
        assertEquals(100, result.score)
        assertEquals(EngineEvent.CORRECT_CONNECTION, result.lastEvent)
        assertTrue(result.streakTimer > 0f)
    }

    @Test
    fun `consecutive connections within window multiply score`() {
        // First connection: streak=1, score=100
        val state = activeState(
            connections = setOf(Connection(0, 1).normalized()),
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
        // Streak active but timer about to expire
        val state = activeState(
            streak = 3,
            streakTimer = 0.005f // will drain to 0 this frame
        )
        val result = ConstellationEngine.step(state, noop)
        assertEquals(0, result.streak)
        assertEquals(EngineEvent.STREAK_BROKEN, result.lastEvent)
    }

    @Test
    fun `connection after streak break starts fresh streak at 1`() {
        val state = activeState(streak = 0, streakTimer = 0f, score = 300)
        val input = noop.copy(connectionMade = Connection(0, 1))
        val result = ConstellationEngine.step(state, input)
        assertEquals(1, result.streak)
        assertEquals(400, result.score) // 300 + 100*1
    }

    // ========================
    // COUNTDOWN TESTS
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
        assertEquals(0f, result.timeRemaining, 0.001f)
    }

    @Test
    fun `timeRemainingPct returns correct percentage`() {
        val state = activeState(timeRemaining = 10f).copy(timeLimitSec = 20f)
        assertEquals(0.5f, ConstellationEngine.timeRemainingPct(state), 0.001f)
    }

    @Test
    fun `timeRemainingPct clamps to 0 and 1`() {
        val full = activeState(timeRemaining = 20f).copy(timeLimitSec = 20f)
        assertEquals(1f, ConstellationEngine.timeRemainingPct(full), 0.001f)
        val empty = activeState(timeRemaining = 0f).copy(timeLimitSec = 20f)
        assertEquals(0f, ConstellationEngine.timeRemainingPct(empty), 0.001f)
    }

    // ========================
    // WRONG CONNECTION / STABILITY TESTS
    // ========================

    @Test
    fun `wrong connection drains stability by 20`() {
        val state = activeState(stability = 100f)
        val input = noop.copy(connectionMade = Connection(0, 5)) // not in triangle
        val result = ConstellationEngine.step(state, input)
        assertEquals(80f, result.stability, 0.001f)
        assertEquals(EngineEvent.WRONG_CONNECTION, result.lastEvent)
        assertTrue(result.completedConnections.isEmpty())
    }

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
    fun `stability collapse triggers COLLAPSED phase`() {
        val state = activeState(stability = 15f)
        // 15 - 20 = -5 → clamped to 0 → collapse
        val input = noop.copy(connectionMade = Connection(0, 5))
        val result = ConstellationEngine.step(state, input)
        assertEquals(ConstellationPhase.COLLAPSED, result.phase)
        assertEquals(0f, result.stability, 0.001f)
        assertEquals(EngineEvent.STABILITY_COLLAPSE, result.lastEvent)
    }

    @Test
    fun `five wrong connections from full stability causes collapse`() {
        var state = activeState(stability = 100f)
        // 5 × -20 = -100 → 0
        repeat(4) {
            state = ConstellationEngine.step(state, noop.copy(connectionMade = Connection(0, 5)))
            assertEquals(ConstellationPhase.CONSTELLATION_ACTIVE, state.phase)
        }
        state = ConstellationEngine.step(state, noop.copy(connectionMade = Connection(0, 5)))
        assertEquals(ConstellationPhase.COLLAPSED, state.phase)
    }

    @Test
    fun `glitch intensity increases as stability drops`() {
        val full = activeState(stability = 100f)
        assertEquals(0f, ConstellationEngine.glitchIntensity(full), 0.001f)
        val half = activeState(stability = 50f)
        assertEquals(0.5f, ConstellationEngine.glitchIntensity(half), 0.001f)
        val zero = activeState(stability = 0f)
        assertEquals(1f, ConstellationEngine.glitchIntensity(zero), 0.001f)
    }

    // ========================
    // LASTÉVENT CLEARING
    // ========================

    @Test
    fun `lastEvent is cleared on frames with no event`() {
        val state = activeState().copy(lastEvent = EngineEvent.CORRECT_CONNECTION)
        val result = ConstellationEngine.step(state, noop)
        assertNull(result.lastEvent)
    }
}
