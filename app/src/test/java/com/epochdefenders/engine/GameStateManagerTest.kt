package com.epochdefenders.engine

import org.junit.Assert.*
import org.junit.Test

class GameStateManagerTest {

    // ── New Game ─────────────────────────────────────────────────────

    @Test
    fun `newGame returns default state`() {
        val state = GameStateManager.newGame()
        assertEquals(GameConstants.STARTING_GOLD, state.gold)
        assertEquals(GameConstants.STARTING_LIVES, state.lives)
        assertEquals(0, state.score)
        assertEquals(0, state.waveNumber)
        assertEquals(TDPhase.WAITING, state.phase)
        assertTrue(state.enemies.isEmpty())
        assertTrue(state.towers.isEmpty())
        assertTrue(state.projectiles.isEmpty())
        assertFalse(state.isGameOver)
    }

    // ── Grid / Buildability ──────────────────────────────────────────

    @Test
    fun `isBuildable returns true for empty non-path cell`() {
        val state = GameStateManager.newGame()
        // (0,0) is not on the path
        assertTrue(GameStateManager.isBuildable(state, 0, 0))
    }

    @Test
    fun `isBuildable returns false for path cell`() {
        val state = GameStateManager.newGame()
        // (0,2) is the first path cell
        assertFalse(GameStateManager.isBuildable(state, 0, 2))
    }

    @Test
    fun `isBuildable returns false for occupied cell`() {
        val state = GameStateManager.newGame()
        val placed = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)
        assertNotNull(placed)
        assertFalse(GameStateManager.isBuildable(placed!!, 0, 0))
    }

    @Test
    fun `isBuildable returns false for out of bounds`() {
        val state = GameStateManager.newGame()
        assertFalse(GameStateManager.isBuildable(state, -1, 0))
        assertFalse(GameStateManager.isBuildable(state, 12, 0))
        assertFalse(GameStateManager.isBuildable(state, 0, -1))
        assertFalse(GameStateManager.isBuildable(state, 0, 9))
    }

    @Test
    fun `isPathCell matches known path cells`() {
        assertTrue(GameStateManager.isPathCell(0, 2))
        assertTrue(GameStateManager.isPathCell(3, 4))
        assertTrue(GameStateManager.isPathCell(9, 3))
        assertTrue(GameStateManager.isPathCell(11, 5))
        assertFalse(GameStateManager.isPathCell(0, 0))
        assertFalse(GameStateManager.isPathCell(5, 5))
    }

    // ── Tower Placement ──────────────────────────────────────────────

    @Test
    fun `placeTower deducts gold and adds tower`() {
        val state = GameStateManager.newGame()
        val result = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)
        assertNotNull(result)
        assertEquals(GameConstants.STARTING_GOLD - TowerType.PULSE.cost, result!!.gold)
        assertEquals(1, result.towers.size)
        assertEquals(TowerType.PULSE, result.towers[0].type)
    }

    @Test
    fun `placeTower returns null when insufficient gold`() {
        val state = GameStateManager.newGame().copy(gold = 10)
        val result = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)
        assertNull(result)
    }

    @Test
    fun `placeTower returns null on path cell`() {
        val state = GameStateManager.newGame()
        val result = GameStateManager.placeTower(state, TowerType.PULSE, 0, 2)
        assertNull(result)
    }

    @Test
    fun `placeTower returns null on occupied cell`() {
        val state = GameStateManager.newGame()
        val placed = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)!!
        val again = GameStateManager.placeTower(placed, TowerType.PULSE, 0, 0)
        assertNull(again)
    }

    @Test
    fun `placeTower increments nextEntityId`() {
        val state = GameStateManager.newGame()
        val r1 = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)!!
        val r2 = GameStateManager.placeTower(r1, TowerType.CRYO, 1, 0)!!
        assertEquals(r1.towers[0].id + 1, r2.towers[1].id)
    }

    // ── Wave Control ─────────────────────────────────────────────────

    @Test
    fun `startNextWave increments wave number and sets SPAWNING`() {
        val state = GameStateManager.newGame()
        val waved = GameStateManager.startNextWave(state)
        assertEquals(1, waved.waveNumber)
        assertEquals(TDPhase.SPAWNING, waved.phase)
        assertFalse(waved.isBossWave)
        assertTrue(waved.spawnQueue.isNotEmpty())
    }

    @Test
    fun `wave 10 is boss wave`() {
        val state = GameStateManager.newGame().copy(waveNumber = 9)
        val waved = GameStateManager.startNextWave(state)
        assertEquals(10, waved.waveNumber)
        assertTrue(waved.isBossWave)
        assertEquals(1, waved.spawnQueue.size)
        assertEquals(EnemyType.BOSS, waved.spawnQueue[0])
    }

    @Test
    fun `forceBoss makes any wave a boss wave`() {
        val state = GameStateManager.newGame()
        val waved = GameStateManager.startNextWave(state, forceBoss = true)
        assertTrue(waved.isBossWave)
        assertEquals(listOf(EnemyType.BOSS), waved.spawnQueue)
    }

    // ── Update: Pre-Wave Timer ───────────────────────────────────────

    @Test
    fun `update counts down pre-wave timer`() {
        val state = GameStateManager.newGame().copy(phase = TDPhase.PRE_WAVE)
        val updated = GameStateManager.update(state, 1.0f)
        assertEquals(TDPhase.PRE_WAVE, updated.phase)
        assertTrue(updated.waveDelayTimer < state.waveDelayTimer)
    }

    @Test
    fun `update auto-starts wave when pre-wave timer expires`() {
        val state = GameStateManager.newGame().copy(phase = TDPhase.PRE_WAVE, waveDelayTimer = 0.1f)
        val updated = GameStateManager.update(state, 0.2f)
        assertEquals(TDPhase.SPAWNING, updated.phase)
        assertEquals(1, updated.waveNumber)
    }

    @Test
    fun `WAITING phase increments wave timer`() {
        val state = GameStateManager.newGame() // starts in WAITING
        val updated = GameStateManager.update(state, 1.0f)
        assertEquals(TDPhase.WAITING, updated.phase)
        assertEquals(1.0f, updated.waveTimer, 0.1f)
    }

    // ── Update: Spawning ─────────────────────────────────────────────

    @Test
    fun `spawning creates enemies from queue`() {
        val state = GameStateManager.startNextWave(GameStateManager.newGame())
        val queueSize = state.spawnQueue.size
        // First update spawns immediately (spawnTimer = 0)
        val updated = GameStateManager.update(state, 0.01f)
        assertEquals(1, updated.enemies.size)
        assertEquals(queueSize - 1, updated.spawnQueue.size)
    }

    @Test
    fun `spawning transitions to ACTIVE when queue empty`() {
        val state = GameStateManager.startNextWave(GameStateManager.newGame())
            .copy(spawnQueue = emptyList())
        val updated = GameStateManager.update(state, 0.01f)
        assertEquals(TDPhase.ACTIVE, updated.phase)
    }

    @Test
    fun `spawn interval enforced between enemies`() {
        val state = GameStateManager.startNextWave(GameStateManager.newGame())
        // First enemy spawns immediately
        val s1 = GameStateManager.update(state, 0.01f)
        assertEquals(1, s1.enemies.size)
        // 0.5s later: still only 1 enemy (interval is 1.0s)
        val s2 = GameStateManager.update(s1, 0.5f)
        assertEquals(1, s2.enemies.size)
        // 0.6s later (total 1.1s): second enemy spawns
        val s3 = GameStateManager.update(s2, 0.6f)
        assertEquals(2, s3.enemies.size)
    }

    // ── Update: Enemy Movement ───────────────────────────────────────

    @Test
    fun `enemies move along path`() {
        val state = GameStateManager.startNextWave(GameStateManager.newGame())
        val spawned = GameStateManager.update(state, 0.01f) // spawn first enemy
        val startX = spawned.enemies[0].x
        val moved = GameStateManager.update(spawned, 0.5f)
        // Enemy should have moved (speed > 0)
        assertNotEquals(startX, moved.enemies[0].x)
    }

    // ── Update: Tower Firing ─────────────────────────────────────────

    @Test
    fun `tower creates projectile when enemy in range`() {
        // Place a tower, spawn an enemy nearby
        var state = GameStateManager.newGame().copy(
            towers = listOf(
                Tower.place(1, TowerType.PULSE, 1, 2) // near path row 2
            ),
            enemies = listOf(
                Enemy.spawn(2, EnemyType.GRUNT, 100f, 160f) // on path
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            nextEntityId = 3,
            gameTimeSec = 2.0f // past first fire interval
        )

        val updated = GameStateManager.update(state, 0.01f)
        assertTrue(updated.projectiles.isNotEmpty())
    }

    // ── Update: Enemy Kill → Gold + Score ────────────────────────────

    @Test
    fun `killing enemy awards gold and score`() {
        val state = GameStateManager.newGame().copy(
            enemies = listOf(
                EnemyData(
                    id = 1, type = EnemyType.GRUNT,
                    health = 0f, maxHealth = 25f,
                    speed = 80f, baseSpeed = 80f,
                    x = 100f, y = 160f,
                    active = false, reachedEnd = false
                )
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1
        )

        val updated = GameStateManager.update(state, 0.01f)
        assertEquals(GameConstants.STARTING_GOLD + EnemyType.GRUNT.reward, updated.gold)
        assertEquals(EnemyType.GRUNT.reward * 10, updated.score)
        assertTrue(updated.events.any { it is GameEvent.EnemyKilled })
    }

    // ── Update: Enemy Reaches End → Lose Life ────────────────────────

    @Test
    fun `enemy reaching end subtracts a life`() {
        val state = GameStateManager.newGame().copy(
            enemies = listOf(
                EnemyData(
                    id = 1, type = EnemyType.GRUNT,
                    health = 25f, maxHealth = 25f,
                    speed = 80f, baseSpeed = 80f,
                    x = 800f, y = 352f,
                    active = false, reachedEnd = true
                )
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1
        )

        val updated = GameStateManager.update(state, 0.01f)
        assertEquals(GameConstants.STARTING_LIVES - 1, updated.lives)
        assertTrue(updated.events.any { it is GameEvent.EnemyReachedEnd })
    }

    // ── Tower Selling ──────────────────────────────────────────────────

    @Test
    fun `sellTower refunds 70 percent and frees cell`() {
        val state = GameStateManager.newGame()
        val placed = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)!!
        val sold = GameStateManager.sellTower(placed, 0)
        assertNotNull(sold)
        val refund = (TowerType.PULSE.cost * GameConstants.SELL_REFUND_RATE).toInt()
        assertEquals(placed.gold + refund, sold!!.gold)
        assertTrue(sold.towers.isEmpty())
        assertTrue(GameStateManager.isBuildable(sold, 0, 0))
    }

    @Test
    fun `sellTower returns null for invalid index`() {
        val state = GameStateManager.newGame()
        assertNull(GameStateManager.sellTower(state, 0))
        assertNull(GameStateManager.sellTower(state, -1))
    }

    // ── Wave Completion ──────────────────────────────────────────────

    @Test
    fun `wave completes with zero-leak bonus and passive income`() {
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            enemies = emptyList(),
            spawnQueue = emptyList(),
            leaksThisWave = 0
        )

        val updated = GameStateManager.update(state, 0.01f)
        assertEquals(TDPhase.WAITING, updated.phase)
        assertTrue(updated.events.any { it is GameEvent.WaveComplete })
        val bonus = WaveSpawner.waveBonus(1)
        val passiveIncome = GameConstants.PASSIVE_WAVE_INCOME_BASE +
            (1 / GameConstants.PASSIVE_WAVE_INCOME_INTERVAL) * GameConstants.PASSIVE_WAVE_INCOME_STEP
        assertEquals(GameConstants.STARTING_GOLD + bonus + passiveIncome, updated.gold)
    }

    @Test
    fun `wave completes with passive income only when leaks occurred`() {
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            enemies = emptyList(),
            spawnQueue = emptyList(),
            leaksThisWave = 2
        )

        val updated = GameStateManager.update(state, 0.01f)
        assertEquals(TDPhase.WAITING, updated.phase)
        val passiveIncome = GameConstants.PASSIVE_WAVE_INCOME_BASE +
            (1 / GameConstants.PASSIVE_WAVE_INCOME_INTERVAL) * GameConstants.PASSIVE_WAVE_INCOME_STEP
        assertEquals(GameConstants.STARTING_GOLD + passiveIncome, updated.gold)
    }

    // ── Game Over ────────────────────────────────────────────────────

    @Test
    fun `game over when lives reach zero`() {
        val state = GameStateManager.newGame().copy(
            lives = 1,
            enemies = listOf(
                EnemyData(
                    id = 1, type = EnemyType.GRUNT,
                    health = 25f, maxHealth = 25f,
                    speed = 80f, baseSpeed = 80f,
                    x = 800f, y = 352f,
                    active = false, reachedEnd = true
                )
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1
        )

        val updated = GameStateManager.update(state, 0.01f)
        assertTrue(updated.isGameOver)
        assertEquals(TDPhase.GAME_OVER, updated.phase)
        assertEquals(0, updated.lives)
    }

    @Test
    fun `update does nothing in GAME_OVER`() {
        val state = GameStateManager.newGame().copy(phase = TDPhase.GAME_OVER)
        val updated = GameStateManager.update(state, 1.0f)
        assertEquals(state, updated)
    }

    // ── Integration: Full Wave Cycle ─────────────────────────────────

    @Test
    fun `full cycle - pre-wave to spawning to active`() {
        var state = GameStateManager.newGame().copy(phase = TDPhase.PRE_WAVE, waveDelayTimer = 0.01f)

        // Tick past pre-wave
        state = GameStateManager.update(state, 0.02f)
        assertEquals(TDPhase.SPAWNING, state.phase)
        assertEquals(1, state.waveNumber)

        // Spawn all enemies (tick many times)
        repeat(20) {
            state = GameStateManager.update(state, 1.1f)
        }

        // Should have transitioned to ACTIVE at some point
        // (enemies still alive and moving, or some have reached end)
        assertTrue(state.phase == TDPhase.ACTIVE || state.enemies.isEmpty())
    }
}
