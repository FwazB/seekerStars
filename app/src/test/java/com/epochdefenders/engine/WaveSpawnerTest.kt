package com.epochdefenders.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class WaveSpawnerTest {

    private fun List<EnemyType>.countOf(type: EnemyType) = count { it == type }

    // ========================
    // WAVE SIZE
    // ========================

    @Test
    fun `wave 1 size is 4`() {
        assertEquals(4, WaveSpawner.waveSize(1)) // 3 + 1
    }

    @Test
    fun `wave 10 size is 13`() {
        assertEquals(13, WaveSpawner.waveSize(10)) // 3 + 10
    }

    @Test
    fun `wave 15 size is 26`() {
        assertEquals(26, WaveSpawner.waveSize(15)) // 8 + 15 + 3
    }

    @Test
    fun `wave 30 size is 48`() {
        assertEquals(48, WaveSpawner.waveSize(30)) // 8 + 30 + 10
    }

    // ========================
    // WAVE COMPOSITION — TIER 1 (waves 1-10)
    // ========================

    @Test
    fun `wave 1 — all grunts`() {
        val wave = WaveSpawner.generateWave(1)
        assertEquals(4, wave.size)
        assertEquals(0, wave.countOf(EnemyType.RUNNER))
        assertEquals(0, wave.countOf(EnemyType.TANK))
    }

    @Test
    fun `wave 3 — introduces runners`() {
        val wave = WaveSpawner.generateWave(3)
        assertEquals(6, wave.size)
        assertTrue(wave.countOf(EnemyType.RUNNER) > 0)
        assertEquals(0, wave.countOf(EnemyType.TANK))
    }

    @Test
    fun `wave 6 — introduces tanks`() {
        val wave = WaveSpawner.generateWave(6)
        assertTrue(wave.countOf(EnemyType.TANK) > 0)
        assertTrue(wave.countOf(EnemyType.RUNNER) > 0)
    }

    // ========================
    // WAVE COMPOSITION — TIER 2 (waves 11-25)
    // ========================

    @Test
    fun `wave 15 — mini-boss included`() {
        val wave = WaveSpawner.generateWave(15)
        assertTrue(wave.contains(EnemyType.BOSS))
    }

    @Test
    fun `wave 12 — no mini-boss`() {
        val wave = WaveSpawner.generateWave(12)
        assertEquals(0, wave.countOf(EnemyType.BOSS))
    }

    // ========================
    // WAVE COMPOSITION — TIER 3 (waves 26+)
    // ========================

    @Test
    fun `wave 30 — runner rush`() {
        val wave = WaveSpawner.generateWave(30)
        assertEquals(wave.size, wave.countOf(EnemyType.RUNNER))
    }

    @Test
    fun `wave 28 — tank swarm`() {
        val wave = WaveSpawner.generateWave(28)
        assertEquals(wave.size, wave.countOf(EnemyType.TANK))
    }

    // ========================
    // DIFFICULTY MULTIPLIER (exponential)
    // ========================

    @Test
    fun `wave 1 difficulty is 1x`() {
        assertEquals(1.0f, WaveSpawner.difficultyMultiplier(1), 0.001f)
    }

    @Test
    fun `wave 5 difficulty is 1_08 pow 4`() {
        assertEquals(1.08f.pow(4), WaveSpawner.difficultyMultiplier(5), 0.001f)
    }

    @Test
    fun `wave 10 difficulty is 1_08 pow 9`() {
        assertEquals(1.08f.pow(9), WaveSpawner.difficultyMultiplier(10), 0.001f)
    }

    // ========================
    // SPEED MULTIPLIER
    // ========================

    @Test
    fun `wave 1 speed multiplier is 1x`() {
        assertEquals(1.0f, WaveSpawner.speedMultiplier(1), 0.001f)
    }

    @Test
    fun `wave 10 speed multiplier is 1_045`() {
        assertEquals(1.045f, WaveSpawner.speedMultiplier(10), 0.001f)
    }

    // ========================
    // BOSS DIFFICULTY
    // ========================

    @Test
    fun `first boss difficulty equals wave multiplier`() {
        val waveMult = WaveSpawner.difficultyMultiplier(10)
        assertEquals(waveMult, WaveSpawner.bossDifficultyMultiplier(1, 10), 0.001f)
    }

    @Test
    fun `second boss is 15 pct harder`() {
        val first = WaveSpawner.bossDifficultyMultiplier(1, 20)
        val second = WaveSpawner.bossDifficultyMultiplier(2, 20)
        assertEquals(first * 1.15f, second, 0.01f)
    }

    // ========================
    // WAVE BONUS
    // ========================

    @Test
    fun `wave 1 bonus is 25`() {
        assertEquals(25, WaveSpawner.waveBonus(1))
    }

    @Test
    fun `wave 10 bonus is 70`() {
        assertEquals(70, WaveSpawner.waveBonus(10))
    }
}
