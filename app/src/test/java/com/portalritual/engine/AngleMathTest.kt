package com.portalritual.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AngleMathTest {

    private val eps = 0.001f

    // --- normalizeDeg ---

    @Test
    fun `normalizeDeg wraps positive overflow`() {
        assertEquals(10f, normalizeDeg(370f), eps)
    }

    @Test
    fun `normalizeDeg wraps negative`() {
        assertEquals(350f, normalizeDeg(-10f), eps)
    }

    @Test
    fun `normalizeDeg handles zero`() {
        assertEquals(0f, normalizeDeg(0f), eps)
    }

    @Test
    fun `normalizeDeg handles exact 360`() {
        assertEquals(0f, normalizeDeg(360f), eps)
    }

    @Test
    fun `normalizeDeg handles large negative`() {
        assertEquals(350f, normalizeDeg(-730f), eps)
    }

    // --- shortestAngleDistance ---

    @Test
    fun `shortestAngleDistance same angle returns zero`() {
        assertEquals(0f, shortestAngleDistance(45f, 45f), eps)
    }

    @Test
    fun `shortestAngleDistance small positive`() {
        assertEquals(20f, shortestAngleDistance(10f, 30f), eps)
    }

    @Test
    fun `shortestAngleDistance wraps clockwise shorter`() {
        assertEquals(20f, shortestAngleDistance(350f, 10f), eps)
    }

    @Test
    fun `shortestAngleDistance wraps counterclockwise shorter`() {
        assertEquals(-20f, shortestAngleDistance(10f, 350f), eps)
    }

    @Test
    fun `shortestAngleDistance exactly 180 apart`() {
        assertEquals(180f, kotlin.math.abs(shortestAngleDistance(0f, 180f)), eps)
    }

    // --- combineRotation ---

    @Test
    fun `combineRotation simple addition`() {
        assertEquals(95f, combineRotation(90f, 5f), eps)
    }

    @Test
    fun `combineRotation wraps around 360`() {
        assertEquals(5f, combineRotation(355f, 10f), eps)
    }

    @Test
    fun `combineRotation negative swipe`() {
        assertEquals(350f, combineRotation(10f, -20f), eps)
    }
}
