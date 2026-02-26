package com.portalritual.ar

import org.junit.Assert.assertEquals
import org.junit.Test

class YawSmootherTest {

    private val eps = 0.01f

    @Test
    fun `first reading passes through unsmoothed`() {
        val s = YawSmoother()
        assertEquals(45f, s.smooth(45f), eps)
    }

    @Test
    fun `subsequent readings are smoothed toward raw`() {
        val s = YawSmoother(alpha = 0.5f, deadZoneDeg = 0f)
        s.smooth(0f) // initialize
        val result = s.smooth(10f)
        // EMA: 0 + 0.5 * 10 = 5
        assertEquals(5f, result, eps)
    }

    @Test
    fun `dead zone suppresses small jitter`() {
        val s = YawSmoother(alpha = 0.2f, deadZoneDeg = 1f)
        s.smooth(0f)
        // 0.5° change is below dead zone — should return previous
        assertEquals(0f, s.smooth(0.5f), eps)
    }

    @Test
    fun `dead zone allows changes above threshold`() {
        val s = YawSmoother(alpha = 1f, deadZoneDeg = 1f)
        s.smooth(0f)
        // 2° change is above dead zone with alpha=1 → raw value
        assertEquals(2f, s.smooth(2f), eps)
    }

    @Test
    fun `handles wraparound from positive to negative`() {
        val s = YawSmoother(alpha = 0.5f, deadZoneDeg = 0f)
        s.smooth(170f)
        // Jump to -170° — shortest path is +20° (through 180)
        // EMA: 170 + 0.5 * 20 = 180 → normalized to -180
        val result = s.smooth(-170f)
        assertEquals(-180f, result, eps)
    }

    @Test
    fun `handles wraparound from negative to positive`() {
        val s = YawSmoother(alpha = 0.5f, deadZoneDeg = 0f)
        s.smooth(-170f)
        // Jump to 170° — shortest path is -20° (through -180)
        // EMA: -170 + 0.5 * (-20) = -180 → normalized to -180
        val result = s.smooth(170f)
        assertEquals(-180f, result, eps)
    }

    @Test
    fun `reset clears state`() {
        val s = YawSmoother()
        s.smooth(90f)
        s.reset()
        assertEquals(0f, s.current, eps)
        // Next smooth acts as first reading
        assertEquals(45f, s.smooth(45f), eps)
    }

    @Test
    fun `current returns zero before any readings`() {
        val s = YawSmoother()
        assertEquals(0f, s.current, eps)
    }

    @Test
    fun `current returns last smoothed value`() {
        val s = YawSmoother(alpha = 1f, deadZoneDeg = 0f)
        s.smooth(30f)
        s.smooth(60f)
        assertEquals(60f, s.current, eps)
    }
}
