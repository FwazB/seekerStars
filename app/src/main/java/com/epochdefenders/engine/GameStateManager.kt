package com.epochdefenders.engine

/**
 * Central game state machine for Tower Defense.
 * Orchestrates wave spawning, tower firing, projectile movement,
 * damage resolution, gold economy, lives, and scoring.
 *
 * Pure Kotlin, stateless functions, same pattern as Enemy/Tower/Projectile.
 * All coordinates in grid-local space (768×576). Renderer applies offset.
 */

// ── Score Tracking ───────────────────────────────────────────────────────

data class FinalScore(
    val score: Int,
    val waveReached: Int,
    val goldEarned: Int,
    val towersPlaced: Int,
    val timeSurvivedSec: Float,
    val bossesDefeated: Int
)

// ── State ────────────────────────────────────────────────────────────────

data class TDGameState(
    val gold: Int = GameConstants.STARTING_GOLD,
    val lives: Int = GameConstants.STARTING_LIVES,
    val score: Int = 0,
    val totalGoldEarned: Int = 0,
    val waveNumber: Int = 0,
    val phase: TDPhase = TDPhase.WAITING,
    val enemies: List<EnemyData> = emptyList(),
    val towers: List<TowerData> = emptyList(),
    val projectiles: List<ProjectileData> = emptyList(),
    val occupiedCells: Set<Long> = emptySet(), // encoded as col * 100L + row
    val spawnQueue: List<EnemyType> = emptyList(),
    val spawnTimer: Float = 0f,
    val waveDelayTimer: Float = GameConstants.WAVE_DELAY_SEC,
    val waveTimer: Float = 0f,
    val waveTimerMax: Float = Float.MAX_VALUE, // wave 1 = unlimited
    val gameTimeSec: Float = 0f,
    val nextEntityId: Int = 1,
    val isBossWave: Boolean = false,
    val bossesDefeated: Int = 0,
    val leaksThisWave: Int = 0,
    val recentKillTimes: List<Float> = emptyList(),
    val lastComboTier: Int = 0,
    val events: List<GameEvent> = emptyList()
) {
    val isGameOver: Boolean get() = phase == TDPhase.GAME_OVER
}

enum class TDPhase {
    WAITING,    // build phase — player places towers, optional countdown
    PRE_WAVE,   // short delay before spawning starts
    SPAWNING,   // enemies spawning from queue
    ACTIVE,     // enemies on field
    GAME_OVER
}

sealed class GameEvent {
    data class EnemyKilled(val x: Float, val y: Float, val reward: Int) : GameEvent()
    data class EnemyReachedEnd(val x: Float, val y: Float) : GameEvent()
    data class ProjectileHit(val x: Float, val y: Float, val isSplash: Boolean) : GameEvent()
    data class WaveComplete(val waveNumber: Int, val bonus: Int) : GameEvent()
    data class ComboBonus(val x: Float, val y: Float, val bonus: Int, val comboSize: Int) : GameEvent()
    data class GameOver(val finalScore: FinalScore) : GameEvent()
}

// ── Manager ──────────────────────────────────────────────────────────────

object GameStateManager {

    /**
     * Path cells (col, row) encoded as col * 100 + row.
     * S-curve through the 12×9 grid, derived from DefaultPath waypoints.
     */
    private val PATH_CELLS: Set<Long> = setOf(
        // Horizontal: entry → col 2, row 2
        cell(0, 2), cell(1, 2), cell(2, 2),
        // Vertical: col 3, rows 2-6
        cell(3, 2), cell(3, 3), cell(3, 4), cell(3, 5), cell(3, 6),
        // Horizontal: row 6, cols 4-6
        cell(4, 6), cell(5, 6),
        // Vertical: col 6, rows 1-6
        cell(6, 1), cell(6, 2), cell(6, 3), cell(6, 4), cell(6, 5), cell(6, 6),
        // Horizontal: row 1, cols 7-8
        cell(7, 1), cell(8, 1),
        // Vertical: col 9, rows 1-5
        cell(9, 1), cell(9, 2), cell(9, 3), cell(9, 4), cell(9, 5),
        // Horizontal: row 5, cols 10-11 (exit)
        cell(10, 5), cell(11, 5)
    )

    private fun cell(col: Int, row: Int): Long = col * 100L + row

    // ── New Game ─────────────────────────────────────────────────────

    fun newGame(): TDGameState = TDGameState()

    // ── Grid Queries ─────────────────────────────────────────────────

    fun isBuildable(state: TDGameState, gridX: Int, gridY: Int): Boolean {
        if (gridX < 0 || gridX >= GameConstants.GRID_COLS) return false
        if (gridY < 0 || gridY >= GameConstants.GRID_ROWS) return false
        val key = cell(gridX, gridY)
        return key !in PATH_CELLS && key !in state.occupiedCells
    }

    fun isPathCell(col: Int, row: Int): Boolean = cell(col, row) in PATH_CELLS

    // ── Tower Placement ──────────────────────────────────────────────

    fun placeTower(state: TDGameState, type: TowerType, gridX: Int, gridY: Int): TDGameState? {
        if (!isBuildable(state, gridX, gridY)) return null
        if (state.gold < type.cost) return null

        val tower = Tower.place(state.nextEntityId, type, gridX, gridY)
        return state.copy(
            gold = state.gold - type.cost,
            towers = state.towers + tower,
            occupiedCells = state.occupiedCells + cell(gridX, gridY),
            nextEntityId = state.nextEntityId + 1
        )
    }

    // ── Tower Selling ──────────────────────────────────────────────────

    fun sellTower(state: TDGameState, towerIndex: Int): TDGameState? {
        if (state.isGameOver) return null // A1: game-over guard
        if (towerIndex < 0 || towerIndex >= state.towers.size) return null
        val tower = state.towers[towerIndex]
        val totalCost = Upgrade.totalInvested(tower.type, tower.upgrades) // A2: includes upgrades
        val refund = (totalCost * GameConstants.SELL_REFUND_RATE).toInt()
        val cellKey = cell(tower.gridX, tower.gridY)
        return state.copy(
            gold = state.gold + refund,
            towers = state.towers.toMutableList().apply { removeAt(towerIndex) },
            occupiedCells = state.occupiedCells - cellKey
        )
    }

    // ── Tower Upgrading ──────────────────────────────────────────────

    fun upgradeTower(state: TDGameState, towerIndex: Int, path: Char): TDGameState? {
        if (state.isGameOver) return null
        if (towerIndex < 0 || towerIndex >= state.towers.size) return null
        val tower = state.towers[towerIndex]
        if (!tower.upgrades.canUpgrade(path)) return null
        val cost = Upgrade.upgradeCost(tower.type, path, tower.upgrades)
        if (cost <= 0 || state.gold < cost) return null
        val upgraded = tower.copy(upgrades = tower.upgrades.withUpgrade(path))
        return state.copy(
            gold = state.gold - cost,
            towers = state.towers.toMutableList().apply { set(towerIndex, upgraded) }
        )
    }

    // ── Wave Control ─────────────────────────────────────────────────

    fun startNextWave(state: TDGameState, forceBoss: Boolean = false): TDGameState {
        val nextWave = state.waveNumber + 1
        val isBoss = forceBoss || (nextWave > 0 && nextWave % 10 == 0)

        val types = if (isBoss) {
            listOf(EnemyType.BOSS)
        } else {
            WaveSpawner.generateWave(nextWave)
        }

        return state.copy(
            waveNumber = nextWave,
            phase = TDPhase.SPAWNING,
            spawnQueue = types,
            spawnTimer = 0f,
            isBossWave = isBoss,
            leaksThisWave = 0,
            events = emptyList()
        )
    }

    // ── Send Wave (Player-Triggered) ────────────────────────────────

    fun sendWave(state: TDGameState): TDGameState {
        if (state.phase != TDPhase.WAITING) return state
        val bonus = if (state.waveTimer <= GameConstants.EARLY_SEND_WINDOW_SEC)
            GameConstants.EARLY_SEND_BONUS else 0
        return startNextWave(state.copy(gold = state.gold + bonus))
    }

    // ── Main Update Loop ─────────────────────────────────────────────

    fun update(state: TDGameState, dtSec: Float): TDGameState {
        if (state.isGameOver) return state

        var s = state.copy(
            gameTimeSec = state.gameTimeSec + dtSec,
            events = emptyList()
        )

        s = tickPhase(s, dtSec)
        s = updateTowers(s)
        s = updateProjectiles(s, dtSec)
        s = updateEnemies(s, dtSec)
        s = applyCryoAuras(s)
        s = resolveDeadAndReached(s)
        s = checkWaveComplete(s)
        s = checkGameOver(s)

        return s
    }

    // ── Phase / Spawning ─────────────────────────────────────────────

    private fun tickPhase(state: TDGameState, dtSec: Float): TDGameState {
        return when (state.phase) {
            TDPhase.WAITING -> {
                val newTimer = state.waveTimer + dtSec
                if (state.waveTimerMax != Float.MAX_VALUE && newTimer >= state.waveTimerMax) {
                    // Auto-start: countdown expired
                    startNextWave(state)
                } else {
                    state.copy(waveTimer = newTimer)
                }
            }

            TDPhase.PRE_WAVE -> {
                val remaining = state.waveDelayTimer - dtSec
                if (remaining <= 0f) {
                    startNextWave(state)
                } else {
                    state.copy(waveDelayTimer = remaining)
                }
            }

            TDPhase.SPAWNING -> tickSpawning(state, dtSec)

            TDPhase.ACTIVE -> state // enemies updated in updateEnemies

            TDPhase.GAME_OVER -> state
        }
    }

    private fun tickSpawning(state: TDGameState, dtSec: Float): TDGameState {
        if (state.spawnQueue.isEmpty()) {
            return state.copy(phase = TDPhase.ACTIVE)
        }

        val timer = state.spawnTimer - dtSec
        if (timer > 0f) {
            return state.copy(spawnTimer = timer)
        }

        // Spawn next enemy
        val type = state.spawnQueue.first()
        val start = DefaultPath.WAYPOINTS.first()
        val difficulty = if (type == EnemyType.BOSS) {
            WaveSpawner.bossDifficultyMultiplier(state.bossesDefeated + 1, state.waveNumber)
        } else {
            WaveSpawner.difficultyMultiplier(state.waveNumber)
        }
        val speedMult = WaveSpawner.speedMultiplier(state.waveNumber)

        val enemy = Enemy.spawn(
            id = state.nextEntityId,
            type = type,
            startX = start.x,
            startY = start.y,
            difficultyMultiplier = difficulty,
            speedMultiplier = speedMult
        )

        return state.copy(
            enemies = state.enemies + enemy,
            spawnQueue = state.spawnQueue.drop(1),
            spawnTimer = GameConstants.SPAWN_INTERVAL_SEC,
            nextEntityId = state.nextEntityId + 1
        )
    }

    // ── Tower Update ─────────────────────────────────────────────────

    private fun updateTowers(state: TDGameState): TDGameState {
        val activeEnemies = state.enemies.filter { it.active }
        if (activeEnemies.isEmpty()) return state

        // Apply beacon fire rate auras before firing
        val towersWithAura = applyBeaconAuras(state.towers)

        val updatedTowers = mutableListOf<TowerData>()
        val newProjectiles = mutableListOf<ProjectileData>()
        var nextId = state.nextEntityId

        for (tower in towersWithAura) {
            val result = Tower.update(tower, activeEnemies, state.gameTimeSec)
            updatedTowers.add(result.tower)

            if (result.projectile != null) {
                newProjectiles.add(result.projectile.copy(id = nextId))
                nextId++
            }
        }

        return state.copy(
            towers = updatedTowers,
            projectiles = state.projectiles + newProjectiles,
            nextEntityId = nextId
        )
    }

    // ── Beacon Aura ───────────────────────────────────────────────────

    /**
     * Applies beacon fire-rate aura buffs each frame.
     * Uses strongest-wins semantics: each tower receives only the best
     * aura bonus from all beacons in range (no stacking).
     */
    private fun applyBeaconAuras(towers: List<TowerData>): List<TowerData> {
        val beacons = towers.filter { it.type == TowerType.BEACON }
        if (beacons.isEmpty()) {
            // Reset any lingering multipliers when no beacons exist
            return towers.map { it.copy(fireRateMultiplier = 1f) }
        }

        return towers.map { tower ->
            // Beacons don't buff themselves
            if (tower.type == TowerType.BEACON) return@map tower.copy(fireRateMultiplier = 1f)

            // Find the strongest beacon buff in range (strongest-wins, not stacking)
            val bestAura = beacons.maxOfOrNull { beacon ->
                val dx = tower.x - beacon.x
                val dy = tower.y - beacon.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val beaconRange = beacon.type.range  // beacon's aura radius
                if (dist <= beaconRange) {
                    Upgrade.effectiveAuraBonus(beacon.type, beacon.upgrades)
                } else {
                    0f
                }
            } ?: 0f

            if (bestAura > 0f) {
                // Fire rate multiplier: 1.0 + aura bonus (e.g. 0.3 → 1.3x fire rate)
                tower.copy(fireRateMultiplier = 1f + bestAura)
            } else {
                tower.copy(fireRateMultiplier = 1f)
            }
        }
    }

    // ── Cryo Aura ────────────────────────────────────────────────────

    /**
     * Applies cryo aura slow to all enemies in range of CRYO towers.
     * Uses strongest-wins semantics (same as projectile slow).
     * Linger duration determined by Permafrost (Path B) tier.
     */
    private fun applyCryoAuras(state: TDGameState): TDGameState {
        val cryos = state.towers.filter { it.type == TowerType.CRYO }
        if (cryos.isEmpty()) return state

        val enemies = state.enemies.map { enemy ->
            if (!enemy.active) return@map enemy

            // Find the best cryo affecting this enemy (strongest slow, longest linger)
            var bestMultiplier = 1f
            var bestLinger = 0f
            var inRange = false

            for (cryo in cryos) {
                val dx = enemy.x - cryo.x
                val dy = enemy.y - cryo.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist <= cryo.type.range) {
                    inRange = true
                    val mult = Upgrade.effectiveSlowMultiplier(cryo.type, cryo.upgrades)
                    val linger = Upgrade.effectiveLingerDuration(cryo.type, cryo.upgrades)
                    if (mult < bestMultiplier) bestMultiplier = mult
                    if (linger > bestLinger) bestLinger = linger
                }
            }

            if (inRange) {
                // Refresh slow with linger duration (so it persists after leaving range)
                Enemy.applySlow(enemy, durationSec = bestLinger, multiplier = bestMultiplier)
            } else {
                enemy // slow timer ticks down naturally in Enemy.update()
            }
        }

        return state.copy(enemies = enemies)
    }

    // ── Projectile Update ────────────────────────────────────────────

    private fun updateProjectiles(state: TDGameState, dtSec: Float): TDGameState {
        val activeProjectiles = state.projectiles.filter { it.active }
        if (activeProjectiles.isEmpty()) {
            return state.copy(projectiles = emptyList())
        }

        val updatedProjectiles = mutableListOf<ProjectileData>()
        val events = state.events.toMutableList()
        // Accumulate damage: enemyId → (totalDamage, applySlow, slowMultiplier)
        val damageMap = mutableMapOf<Int, Triple<Float, Boolean, Float>>()
        val pierceSet = mutableSetOf<Int>() // enemies that receive shield-piercing damage

        for (proj in activeProjectiles) {
            val result = Projectile.update(proj, dtSec, state.enemies)
            updatedProjectiles.add(result.projectile)

            if (result.damageEvents.isNotEmpty()) {
                events.add(
                    GameEvent.ProjectileHit(
                        x = result.projectile.x,
                        y = result.projectile.y,
                        isSplash = result.projectile.isSplash
                    )
                )
                for (dmg in result.damageEvents) {
                    val existing = damageMap[dmg.enemyId]
                    damageMap[dmg.enemyId] = Triple(
                        (existing?.first ?: 0f) + dmg.damage,
                        (existing?.second ?: false) || dmg.applySlow,
                        if (dmg.applySlow) minOf(existing?.third ?: 1f, dmg.slowMultiplier) else (existing?.third ?: 1f)
                    )
                    if (dmg.pierceShield) pierceSet.add(dmg.enemyId)
                }
            }
        }

        // Single pass: apply accumulated damage
        val enemies = state.enemies.map { enemy ->
            val pending = damageMap[enemy.id] ?: return@map enemy
            var updated = Enemy.takeDamage(enemy, pending.first, pierceShield = enemy.id in pierceSet)
            if (pending.second) updated = Enemy.applySlow(updated, multiplier = pending.third)
            updated
        }

        return state.copy(
            projectiles = updatedProjectiles.filter { it.active },
            enemies = enemies,
            events = events
        )
    }

    // ── Enemy Update ─────────────────────────────────────────────────

    private fun updateEnemies(state: TDGameState, dtSec: Float): TDGameState {
        val path = DefaultPath.WAYPOINTS
        val updated = state.enemies.map { enemy ->
            if (enemy.active) Enemy.update(enemy, dtSec, path) else enemy
        }
        return state.copy(enemies = updated)
    }

    // ── Resolve Dead / Reached End ───────────────────────────────────

    private fun resolveDeadAndReached(state: TDGameState): TDGameState {
        var gold = state.gold
        var goldEarned = 0
        var lives = state.lives
        var score = state.score
        var leaks = state.leaksThisWave
        var bossesDefeated = state.bossesDefeated
        val events = state.events.toMutableList()
        val surviving = mutableListOf<EnemyData>()

        // Kill combo tracking
        val killTimes = state.recentKillTimes
            .filter { state.gameTimeSec - it < GameConstants.COMBO_10_WINDOW_SEC }
            .toMutableList()
        var comboTier = state.lastComboTier
        var lastKillX = 0f
        var lastKillY = 0f

        // Prune combo tier if window has expired (no recent kills)
        if (killTimes.isEmpty()) comboTier = 0

        for (enemy in state.enemies) {
            when {
                // Dead (killed by projectile)
                !enemy.active && !enemy.reachedEnd -> {
                    gold += enemy.type.reward
                    goldEarned += enemy.type.reward
                    score += enemy.type.reward * 10
                    if (enemy.type == EnemyType.BOSS) bossesDefeated++
                    events.add(GameEvent.EnemyKilled(enemy.x, enemy.y, enemy.type.reward))

                    // Track kill for combo
                    killTimes.add(state.gameTimeSec)
                    lastKillX = enemy.x
                    lastKillY = enemy.y
                }

                // Reached end of path
                enemy.reachedEnd -> {
                    lives--
                    leaks++
                    events.add(GameEvent.EnemyReachedEnd(enemy.x, enemy.y))
                }

                // Still alive and active — keep
                else -> surviving.add(enemy)
            }
        }

        // Check combo thresholds — each tier counts only kills within its own window
        val kills10 = killTimes.count { state.gameTimeSec - it < GameConstants.COMBO_10_WINDOW_SEC }
        val kills5 = killTimes.count { state.gameTimeSec - it < GameConstants.COMBO_5_WINDOW_SEC }
        val kills3 = killTimes.count { state.gameTimeSec - it < GameConstants.COMBO_3_WINDOW_SEC }

        if (kills10 >= 10 && comboTier < 3) {
            val bonus = GameConstants.COMBO_10_BONUS
            gold += bonus
            goldEarned += bonus
            score += bonus * 10
            comboTier = 3
            events.add(GameEvent.ComboBonus(lastKillX, lastKillY, bonus, kills10))
        } else if (kills5 >= 5 && comboTier < 2) {
            val bonus = GameConstants.COMBO_5_BONUS
            gold += bonus
            goldEarned += bonus
            score += bonus * 10
            comboTier = 2
            events.add(GameEvent.ComboBonus(lastKillX, lastKillY, bonus, kills5))
        } else if (kills3 >= 3 && comboTier < 1) {
            val bonus = GameConstants.COMBO_3_BONUS
            gold += bonus
            goldEarned += bonus
            score += bonus * 10
            comboTier = 1
            events.add(GameEvent.ComboBonus(lastKillX, lastKillY, bonus, kills3))
        }

        return state.copy(
            gold = gold,
            lives = lives,
            score = score,
            totalGoldEarned = state.totalGoldEarned + goldEarned,
            leaksThisWave = leaks,
            bossesDefeated = bossesDefeated,
            enemies = surviving,
            recentKillTimes = killTimes,
            lastComboTier = comboTier,
            events = events
        )
    }

    // ── Wave Completion ──────────────────────────────────────────────

    private fun checkWaveComplete(state: TDGameState): TDGameState {
        if (state.phase != TDPhase.ACTIVE) return state
        if (state.enemies.isNotEmpty()) return state
        if (state.spawnQueue.isNotEmpty()) return state

        // Wave cleared — zero-leak bonus
        val bonus = if (state.leaksThisWave == 0) WaveSpawner.waveBonus(state.waveNumber) else 0

        // Beacon passive income: each beacon generates gold on wave clear
        val beaconIncome = state.towers
            .filter { it.type == TowerType.BEACON }
            .sumOf { Upgrade.effectiveIncome(it.type, it.upgrades) }

        // Passive wave income: unconditional 10g base + 5g every 5 waves
        val passiveIncome = GameConstants.PASSIVE_WAVE_INCOME_BASE +
            (state.waveNumber / GameConstants.PASSIVE_WAVE_INCOME_INTERVAL) * GameConstants.PASSIVE_WAVE_INCOME_STEP

        val totalBonus = bonus + beaconIncome + passiveIncome
        val events = state.events + GameEvent.WaveComplete(state.waveNumber, totalBonus)

        return state.copy(
            phase = TDPhase.WAITING,
            waveTimer = 0f,
            waveTimerMax = GameConstants.WAVE_AUTO_START_SEC,
            waveDelayTimer = GameConstants.WAVE_DELAY_SEC,
            gold = state.gold + totalBonus,
            totalGoldEarned = state.totalGoldEarned + totalBonus,
            score = state.score + bonus,  // beacon income is gold only, not score
            isBossWave = false,
            events = events
        )
    }

    // ── Game Over ────────────────────────────────────────────────────

    private fun checkGameOver(state: TDGameState): TDGameState {
        if (state.lives <= 0) {
            val finalScore = FinalScore(
                score = state.score,
                waveReached = state.waveNumber,
                goldEarned = state.totalGoldEarned,
                towersPlaced = state.towers.size,
                timeSurvivedSec = state.gameTimeSec,
                bossesDefeated = state.bossesDefeated
            )
            return state.copy(
                phase = TDPhase.GAME_OVER,
                lives = 0,
                events = state.events + GameEvent.GameOver(finalScore)
            )
        }
        return state
    }
}
