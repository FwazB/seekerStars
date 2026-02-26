package com.portalritual.input

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class DollarOneRecognizerTest {

    private lateinit var recognizer: DollarOneRecognizer

    @Before
    fun setUp() {
        recognizer = DollarOneRecognizer(threshold = 0.7f, numPoints = 64)
    }

    // --- Template self-match ---

    @Test
    fun `triangle template matches itself`() {
        val result = recognizer.recognize(RuneTemplates.triangle)
        assertTrue("Should be accepted", result.accepted)
        assertEquals("triangle", result.templateName)
        assertTrue("Score should be high, was ${result.score}", result.score >= 0.90f)
    }

    @Test
    fun `zigzag template matches itself`() {
        val result = recognizer.recognize(RuneTemplates.zigzag)
        assertTrue("Should be accepted", result.accepted)
        assertEquals("zigzag", result.templateName)
        assertTrue("Score should be high, was ${result.score}", result.score >= 0.90f)
    }

    @Test
    fun `spiral template matches itself`() {
        val result = recognizer.recognize(RuneTemplates.spiral)
        assertTrue("Should be accepted", result.accepted)
        assertEquals("spiral", result.templateName)
        assertTrue("Score should be high, was ${result.score}", result.score >= 0.90f)
    }

    // --- Cross-template discrimination ---

    @Test
    fun `triangle does not match as zigzag or spiral`() {
        val result = recognizer.recognize(RuneTemplates.triangle)
        assertEquals("triangle", result.templateName)
    }

    @Test
    fun `zigzag does not match as triangle or spiral`() {
        val result = recognizer.recognize(RuneTemplates.zigzag)
        assertEquals("zigzag", result.templateName)
    }

    // --- Jittered input still matches ---

    @Test
    fun `triangle with small jitter still matches`() {
        val rng = Random(42)
        val jittered = RuneTemplates.triangle.map { (x, y) ->
            Pair(x + rng.nextFloat() * 0.02f - 0.01f, y + rng.nextFloat() * 0.02f - 0.01f)
        }
        val result = recognizer.recognize(jittered)
        assertTrue("Should be accepted with jitter", result.accepted)
        assertEquals("triangle", result.templateName)
    }

    // --- Scribble rejection ---

    @Test
    fun `random scribble is rejected`() {
        val rng = Random(42)
        val scribble = (0 until 40).map {
            Pair(rng.nextFloat(), rng.nextFloat())
        }
        val result = recognizer.recognize(scribble)
        assertFalse("Random scribble should be rejected", result.accepted)
        assertNull("templateName should be null", result.templateName)
    }

    @Test
    fun `straight horizontal line is rejected`() {
        val line = (0 until 64).map { Pair(it.toFloat() / 63f, 0.5f) }
        val result = recognizer.recognize(line)
        assertFalse("Straight line should be rejected", result.accepted)
    }

    // --- Short / empty input safety ---

    @Test
    fun `empty list returns no match`() {
        val result = recognizer.recognize(emptyList())
        assertFalse(result.accepted)
        assertNull(result.templateName)
        assertEquals(0f, result.score, 0.001f)
    }

    @Test
    fun `single point returns no match`() {
        val result = recognizer.recognize(listOf(Pair(5f, 5f)))
        assertFalse(result.accepted)
        assertNull(result.templateName)
        assertEquals(0f, result.score, 0.001f)
    }

    @Test
    fun `two identical points returns no match`() {
        val result = recognizer.recognize(listOf(Pair(1f, 1f), Pair(1f, 1f)))
        assertFalse(result.accepted)
        assertNull(result.templateName)
    }

    // --- Resample correctness ---

    @Test
    fun `resample produces exactly N points`() {
        val input = listOf(Pair(0f, 0f), Pair(10f, 0f), Pair(10f, 10f))
        val resampled = recognizer.resample(input, 64)
        assertEquals(64, resampled.size)
    }

    @Test
    fun `resample first point matches input first point`() {
        val input = listOf(Pair(3f, 7f), Pair(10f, 0f), Pair(20f, 5f))
        val resampled = recognizer.resample(input, 64)
        assertEquals(3f, resampled[0].first, 0.001f)
        assertEquals(7f, resampled[0].second, 0.001f)
    }

    // --- Centroid correctness ---

    @Test
    fun `centroid of square corners is center`() {
        val square = listOf(Pair(0f, 0f), Pair(1f, 0f), Pair(1f, 1f), Pair(0f, 1f))
        val c = recognizer.centroid(square)
        assertEquals(0.5f, c.first, 0.001f)
        assertEquals(0.5f, c.second, 0.001f)
    }

    // --- Score is clamped [0, 1] ---

    @Test
    fun `score is always between 0 and 1`() {
        val rng = Random(99)
        val randomInput = (0 until 30).map { Pair(rng.nextFloat(), rng.nextFloat()) }
        val result = recognizer.recognize(randomInput)
        assertTrue("Score should be >= 0, was ${result.score}", result.score >= 0f)
        assertTrue("Score should be <= 1, was ${result.score}", result.score <= 1f)
    }
}
