package com.epochdefenders.engine

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

object WaveSpawner {

    fun generateWave(waveNumber: Int): List<EnemyType> {
        val wave = mutableListOf<EnemyType>()
        val total = waveSize(waveNumber)

        when {
            // Waves 1-10: 75% Grunt, 15% Runner (from w3), 10% Tank (from w6)
            waveNumber <= 10 -> {
                val tanks = if (waveNumber >= 6) (total * 0.10f).roundToInt() else 0
                val runners = if (waveNumber >= 3) (total * 0.15f).roundToInt() else 0
                val grunts = total - runners - tanks
                repeat(grunts) { wave.add(EnemyType.GRUNT) }
                repeat(runners) { wave.add(EnemyType.RUNNER) }
                repeat(tanks) { wave.add(EnemyType.TANK) }
            }
            // Waves 11-25: 55/25/20 split. Mini-boss every 5th
            waveNumber <= 25 -> {
                val tanks = (total * 0.20f).roundToInt()
                val runners = (total * 0.25f).roundToInt()
                val grunts = total - runners - tanks
                repeat(grunts) { wave.add(EnemyType.GRUNT) }
                repeat(runners) { wave.add(EnemyType.RUNNER) }
                repeat(tanks) { wave.add(EnemyType.TANK) }
                if (waveNumber % 5 == 0) wave.add(EnemyType.BOSS)
            }
            // Waves 26-50: 35/30/35 split. Runner Rush every 5th, Tank Swarm every 7th
            else -> {
                if (waveNumber % 5 == 0) {
                    // Runner Rush
                    repeat(total) { wave.add(EnemyType.RUNNER) }
                } else if (waveNumber % 7 == 0) {
                    // Tank Swarm
                    repeat(total) { wave.add(EnemyType.TANK) }
                } else {
                    val tanks = (total * 0.35f).roundToInt()
                    val runners = (total * 0.30f).roundToInt()
                    val grunts = total - runners - tanks
                    repeat(grunts) { wave.add(EnemyType.GRUNT) }
                    repeat(runners) { wave.add(EnemyType.RUNNER) }
                    repeat(tanks) { wave.add(EnemyType.TANK) }
                }
            }
        }

        return wave.shuffled()
    }

    fun waveSize(waveNumber: Int): Int = when {
        waveNumber <= 10 -> 8 + waveNumber
        waveNumber <= 25 -> 8 + waveNumber + floor(waveNumber / 5f).toInt()
        else -> 8 + waveNumber + floor(waveNumber / 3f).toInt()
    }

    /** Exponential HP scaling: 1.08^(wave-1) */
    fun difficultyMultiplier(waveNumber: Int): Float =
        1.08f.pow(waveNumber - 1)

    /** Speed scaling: 1 + (wave-1)*0.005 */
    fun speedMultiplier(waveNumber: Int): Float =
        1f + (waveNumber - 1) * GameConstants.SPEED_SCALE_PER_WAVE

    /** Boss HP: base × 1.15^(bossNumber-1) × waveMultiplier */
    fun bossDifficultyMultiplier(bossNumber: Int, waveNumber: Int): Float =
        GameConstants.BOSS_HP_SCALE_BASE.pow(bossNumber - 1) * difficultyMultiplier(waveNumber)

    /** Zero-leak wave bonus */
    fun waveBonus(waveNumber: Int): Int =
        15 + waveNumber * 3
}
