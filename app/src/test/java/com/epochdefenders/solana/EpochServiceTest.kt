package com.epochdefenders.solana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpochServiceTest {

    private val service = EpochService()
    private val eps = 0.001f

    private fun info(slotIndex: Long = 216_000, slotsInEpoch: Long = 432_000) =
        EpochInfo(epoch = 500, slotIndex = slotIndex, slotsInEpoch = slotsInEpoch, absoluteSlot = 0)

    // --- getEpochProgress ---

    @Test
    fun `progress at midpoint is 0_5`() {
        assertEquals(0.5f, service.getEpochProgress(info(216_000, 432_000)), eps)
    }

    @Test
    fun `progress at start is 0`() {
        assertEquals(0f, service.getEpochProgress(info(0, 432_000)), eps)
    }

    @Test
    fun `progress at end is 1`() {
        assertEquals(1f, service.getEpochProgress(info(432_000, 432_000)), eps)
    }

    @Test
    fun `progress handles zero slotsInEpoch`() {
        assertEquals(0f, service.getEpochProgress(info(0, 0)), eps)
    }

    // --- getTimeUntilNextEpoch ---

    @Test
    fun `time remaining at midpoint`() {
        val remaining = service.getTimeUntilNextEpoch(info(216_000, 432_000))
        // (432000 - 216000) * 0.4 = 86400 seconds = 1 day
        assertEquals(86400f, remaining, 1f)
    }

    @Test
    fun `time remaining at start`() {
        val remaining = service.getTimeUntilNextEpoch(info(0, 432_000))
        // 432000 * 0.4 = 172800 = 2 days
        assertEquals(172800f, remaining, 1f)
    }

    // --- formatTimeRemaining ---

    @Test
    fun `format days and hours`() {
        assertEquals("1d 0h", service.formatTimeRemaining(86400f))
    }

    @Test
    fun `format hours and minutes`() {
        assertEquals("2h 30m", service.formatTimeRemaining(9000f))
    }

    @Test
    fun `format minutes only`() {
        assertEquals("45m", service.formatTimeRemaining(2700f))
    }

    // --- shouldTriggerBoss ---

    @Test
    fun `boss triggers at 25 pct`() {
        assertTrue(service.shouldTriggerBoss(info(108_000, 432_000), 1))
    }

    @Test
    fun `boss triggers at 50 pct`() {
        assertTrue(service.shouldTriggerBoss(info(216_000, 432_000), 1))
    }

    @Test
    fun `boss triggers at 75 pct`() {
        assertTrue(service.shouldTriggerBoss(info(324_000, 432_000), 1))
    }

    @Test
    fun `boss does not trigger at 40 pct`() {
        // 40% = 172800 slots
        assertFalse(service.shouldTriggerBoss(info(172_800, 432_000), 1))
    }

    @Test
    fun `boss triggers every 10 waves`() {
        assertTrue(service.shouldTriggerBoss(info(0, 432_000), 10))
        assertTrue(service.shouldTriggerBoss(info(0, 432_000), 20))
    }

    @Test
    fun `boss does not trigger wave 5`() {
        assertFalse(service.shouldTriggerBoss(info(0, 432_000), 5))
    }

    @Test
    fun `boss does not trigger wave 0`() {
        // waveNumber 0 should not trigger (0 % 10 == 0 but waveNumber > 0 guard)
        assertFalse(service.shouldTriggerBoss(info(0, 432_000), 0))
    }

    // --- getBossDifficultyMultiplier ---

    @Test
    fun `difficulty at epoch 0 is 1`() {
        assertEquals(1f, service.getBossDifficultyMultiplier(0), eps)
    }

    @Test
    fun `difficulty at epoch 1000 is 2`() {
        assertEquals(2f, service.getBossDifficultyMultiplier(1000), eps)
    }

    @Test
    fun `difficulty at epoch 500 is 1_5`() {
        assertEquals(1.5f, service.getBossDifficultyMultiplier(500), eps)
    }
}
