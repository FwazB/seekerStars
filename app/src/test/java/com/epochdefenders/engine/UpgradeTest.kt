package com.epochdefenders.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeTest {

    // ========================
    // UPGRADE DATA — PATH EXCLUSION
    // ========================

    @Test
    fun `canUpgrade allows tier 1 on empty upgrades`() {
        val u = UpgradeData()
        assertTrue(u.canUpgrade('A'))
        assertTrue(u.canUpgrade('B'))
    }

    @Test
    fun `canUpgrade allows both paths up to tier 2`() {
        val u = UpgradeData(pathA = 2, pathB = 2)
        assertTrue(u.canUpgrade('A')) // → tier 3
        assertTrue(u.canUpgrade('B')) // → tier 3
    }

    @Test
    fun `path exclusion — cannot both reach tier 3`() {
        val u = UpgradeData(pathA = 3, pathB = 2)
        assertFalse(u.canUpgrade('A')) // already maxed
        assertFalse(u.canUpgrade('B')) // blocked: pathA is 3
    }

    @Test
    fun `path exclusion — symmetric`() {
        val u = UpgradeData(pathA = 2, pathB = 3)
        assertFalse(u.canUpgrade('A')) // blocked: pathB is 3
        assertFalse(u.canUpgrade('B')) // already maxed
    }

    @Test
    fun `withUpgrade increments correct path`() {
        val u = UpgradeData().withUpgrade('A').withUpgrade('A').withUpgrade('B')
        assertEquals(2, u.pathA)
        assertEquals(1, u.pathB)
    }

    // ========================
    // UPGRADE COSTS
    // ========================

    @Test
    fun `pulse path A costs are 50-100-200`() {
        var u = UpgradeData()
        assertEquals(50, Upgrade.upgradeCost(TowerType.PULSE, 'A', u))
        u = u.withUpgrade('A')
        assertEquals(100, Upgrade.upgradeCost(TowerType.PULSE, 'A', u))
        u = u.withUpgrade('A')
        assertEquals(200, Upgrade.upgradeCost(TowerType.PULSE, 'A', u))
    }

    @Test
    fun `cryo path B costs are 50-100-200`() {
        var u = UpgradeData()
        assertEquals(50, Upgrade.upgradeCost(TowerType.CRYO, 'B', u))
        u = u.withUpgrade('B')
        assertEquals(100, Upgrade.upgradeCost(TowerType.CRYO, 'B', u))
        u = u.withUpgrade('B')
        assertEquals(200, Upgrade.upgradeCost(TowerType.CRYO, 'B', u))
    }

    @Test
    fun `cost is 0 for maxed path`() {
        val u = UpgradeData(pathA = 3)
        assertEquals(0, Upgrade.upgradeCost(TowerType.PULSE, 'A', u))
    }

    // ========================
    // TOTAL INVESTED
    // ========================

    @Test
    fun `totalInvested base only`() {
        assertEquals(75, Upgrade.totalInvested(TowerType.PULSE, UpgradeData()))
    }

    @Test
    fun `totalInvested with upgrades`() {
        val u = UpgradeData(pathA = 2, pathB = 1) // Pulse: 50+100 + 40 = 190
        assertEquals(75 + 190, Upgrade.totalInvested(TowerType.PULSE, u))
    }

    @Test
    fun `totalInvested fully maxed pulse`() {
        val u = UpgradeData(pathA = 3, pathB = 2) // 50+100+200 + 40+80 = 470
        assertEquals(75 + 470, Upgrade.totalInvested(TowerType.PULSE, u))
    }

    // ========================
    // EFFECTIVE STATS — PULSE
    // ========================

    @Test
    fun `pulse path A tier 1 adds 4 damage`() {
        val u = UpgradeData(pathA = 1)
        assertEquals(12f, Upgrade.effectiveDamage(TowerType.PULSE, u), 0.001f) // 8 + 4
    }

    @Test
    fun `pulse path A tier 3 adds 20 damage`() {
        val u = UpgradeData(pathA = 3)
        assertEquals(28f, Upgrade.effectiveDamage(TowerType.PULSE, u), 0.001f) // 8 + 20
    }

    @Test
    fun `pulse path B tier 2 multiplies rate by 1_5`() {
        val u = UpgradeData(pathB = 2)
        assertEquals(1.5f, Upgrade.effectiveFireRate(TowerType.PULSE, u), 0.001f)
    }

    @Test
    fun `pulse path B tier 3 doubles rate`() {
        val u = UpgradeData(pathB = 3)
        assertEquals(2.0f, Upgrade.effectiveFireRate(TowerType.PULSE, u), 0.001f)
    }

    @Test
    fun `pulse no upgrades has base damage and rate`() {
        val u = UpgradeData()
        assertEquals(8f, Upgrade.effectiveDamage(TowerType.PULSE, u), 0.001f)
        assertEquals(1f, Upgrade.effectiveFireRate(TowerType.PULSE, u), 0.001f)
    }

    // ========================
    // EFFECTIVE STATS — NOVA
    // ========================

    @Test
    fun `nova path A tier 3 splash is 180`() {
        val u = UpgradeData(pathA = 3)
        assertEquals(180f, Upgrade.effectiveSplashRadius(TowerType.NOVA_CANNON, u), 0.001f)
    }

    @Test
    fun `nova path A tier 1 splash is base plus 20`() {
        val u = UpgradeData(pathA = 1)
        assertEquals(GameConstants.SPLASH_RADIUS + 20f, Upgrade.effectiveSplashRadius(TowerType.NOVA_CANNON, u), 0.001f)
    }

    @Test
    fun `nova path B tier 3 adds 30 damage`() {
        val u = UpgradeData(pathB = 3)
        assertEquals(50f, Upgrade.effectiveDamage(TowerType.NOVA_CANNON, u), 0.001f) // 20 + 30
    }

    // ========================
    // EFFECTIVE STATS — CRYO
    // ========================

    @Test
    fun `cryo path A tier 1 slow multiplier is 0_55`() {
        val u = UpgradeData(pathA = 1)
        assertEquals(0.55f, Upgrade.effectiveSlowMultiplier(TowerType.CRYO, u), 0.001f)
    }

    @Test
    fun `cryo path A tier 3 is freeze`() {
        val u = UpgradeData(pathA = 3)
        assertEquals(0.0f, Upgrade.effectiveSlowMultiplier(TowerType.CRYO, u), 0.001f)
    }

    @Test
    fun `cryo path B tier 1 gives 1 extra target`() {
        assertEquals(1, Upgrade.extraTargets(TowerType.CRYO, UpgradeData(pathB = 1)))
    }

    @Test
    fun `cryo path B tier 3 gives 4 extra targets`() {
        assertEquals(4, Upgrade.extraTargets(TowerType.CRYO, UpgradeData(pathB = 3)))
    }

    @Test
    fun `cryo no upgrades has base slow`() {
        assertEquals(GameConstants.CRYO_SLOW_MULTIPLIER,
            Upgrade.effectiveSlowMultiplier(TowerType.CRYO, UpgradeData()), 0.001f)
    }

    // ========================
    // NON-APPLICABLE UPGRADES RETURN BASE
    // ========================

    @Test
    fun `non-pulse tower ignores pulse damage bonus`() {
        val u = UpgradeData(pathA = 3)
        assertEquals(TowerType.CRYO.damage, Upgrade.effectiveDamage(TowerType.CRYO, u), 0.001f)
    }

    @Test
    fun `non-cryo tower ignores slow upgrade`() {
        assertEquals(GameConstants.CRYO_SLOW_MULTIPLIER,
            Upgrade.effectiveSlowMultiplier(TowerType.PULSE, UpgradeData(pathA = 3)), 0.001f)
    }

    // ========================
    // TOWER INTEGRATION — UPGRADE AFFECTS PROJECTILE
    // ========================

    @Test
    fun `upgraded pulse tower produces higher damage projectile`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3)
            .copy(upgrades = UpgradeData(pathA = 2)) // +10 damage
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val result = Tower.update(tower, listOf(enemy), 1.0f)
        assertNotNull(result.projectile)
        assertEquals(18f, result.projectile!!.damage, 0.001f) // 8 + 10
    }

    @Test
    fun `upgraded pulse B fires faster`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3)
            .copy(upgrades = UpgradeData(pathB = 3)) // rate × 2.0
        // Fire interval = 1 / 2.0 = 0.5s
        assertTrue(Tower.canFire(tower, 0.5f)) // 0.5 >= 0.5
        assertFalse(Tower.canFire(tower.copy(lastFireTimeSec = 0.3f), 0.5f)) // 0.2 < 0.5
    }

    @Test
    fun `upgraded cryo projectile has stronger slow multiplier`() {
        val tower = Tower.place(1, TowerType.CRYO, 3, 3)
            .copy(upgrades = UpgradeData(pathA = 2)) // 0.40 multiplier
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val result = Tower.update(tower, listOf(enemy), 1.0f)
        assertNotNull(result.projectile)
        assertEquals(0.40f, result.projectile!!.slowMultiplier, 0.001f)
    }

    @Test
    fun `upgraded cryo projectile has extra targets`() {
        val tower = Tower.place(1, TowerType.CRYO, 3, 3)
            .copy(upgrades = UpgradeData(pathB = 2)) // +2 extra
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val result = Tower.update(tower, listOf(enemy), 1.0f)
        assertNotNull(result.projectile)
        assertEquals(2, result.projectile!!.extraSlowTargets)
    }

    // ========================
    // GSM — upgradeTower
    // ========================

    @Test
    fun `upgradeTower deducts gold and applies upgrade`() {
        val state = GameStateManager.newGame()
        val placed = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)!!
        val upgraded = GameStateManager.upgradeTower(placed, 0, 'A')
        assertNotNull(upgraded)
        assertEquals(placed.gold - 50, upgraded!!.gold)
        assertEquals(1, upgraded.towers[0].upgrades.pathA)
    }

    @Test
    fun `upgradeTower returns null when insufficient gold`() {
        val state = GameStateManager.newGame().copy(gold = 80)
        val placed = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)!! // 75g → 5g left
        val result = GameStateManager.upgradeTower(placed, 0, 'A') // 50g needed
        assertNull(result)
    }

    @Test
    fun `upgradeTower returns null for path exclusion`() {
        val state = GameStateManager.newGame().copy(gold = 9999)
        val placed = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)!!
        // Max out path A
        var s = placed
        s = GameStateManager.upgradeTower(s, 0, 'A')!! // tier 1
        s = GameStateManager.upgradeTower(s, 0, 'A')!! // tier 2
        s = GameStateManager.upgradeTower(s, 0, 'A')!! // tier 3
        // Path B should be blocked at tier 3 (but tier 2 ok)
        s = GameStateManager.upgradeTower(s, 0, 'B')!! // tier 1 ok
        s = GameStateManager.upgradeTower(s, 0, 'B')!! // tier 2 ok
        val blocked = GameStateManager.upgradeTower(s, 0, 'B') // tier 3 blocked
        assertNull(blocked)
        assertEquals(2, s.towers[0].upgrades.pathB)
    }

    @Test
    fun `upgradeTower returns null in game over`() {
        val state = GameStateManager.newGame().copy(phase = TDPhase.GAME_OVER)
        val placed = state.copy(towers = listOf(Tower.place(1, TowerType.PULSE, 0, 0)))
        assertNull(GameStateManager.upgradeTower(placed, 0, 'A'))
    }

    // ========================
    // GSM — sellTower with upgrades
    // ========================

    @Test
    fun `sellTower refunds base plus upgrade costs at 70 pct`() {
        val state = GameStateManager.newGame().copy(gold = 9999)
        val placed = GameStateManager.placeTower(state, TowerType.PULSE, 0, 0)!!
        val upgraded = GameStateManager.upgradeTower(placed, 0, 'A')!! // +50g invested
        val sold = GameStateManager.sellTower(upgraded, 0)!!
        // Total invested: 75 (base) + 50 (upgrade) = 125. Refund: 125 * 0.7 = 87
        val expectedRefund = (125 * GameConstants.SELL_REFUND_RATE).toInt()
        assertEquals(upgraded.gold + expectedRefund, sold.gold)
    }

    @Test
    fun `sellTower returns null in game over`() {
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.GAME_OVER,
            towers = listOf(Tower.place(1, TowerType.PULSE, 0, 0))
        )
        assertNull(GameStateManager.sellTower(state, 0))
    }

    // ========================
    // CHAIN FROST — MULTI-TARGET
    // ========================

    @Test
    fun `chain frost hits extra targets within splash radius`() {
        val e1 = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f)
        val e2 = Enemy.spawn(2, EnemyType.GRUNT, 30f, 0f)
        val e3 = Enemy.spawn(3, EnemyType.GRUNT, 50f, 0f)
        val e4 = Enemy.spawn(4, EnemyType.GRUNT, 500f, 0f) // far away

        val proj = ProjectileData(
            1, 5f, 0f, targetId = 1, damage = 3f, speed = 350f,
            isSlowing = true, extraSlowTargets = 2,
            splashRadius = GameConstants.SPLASH_RADIUS
        )
        val result = Projectile.update(proj, 0.016f, listOf(e1, e2, e3, e4))
        // Primary hit (e1) + 2 chain targets (e2, e3) = 3 events. e4 too far.
        assertEquals(3, result.damageEvents.size)
        assertTrue(result.damageEvents.all { it.applySlow })
    }

    @Test
    fun `chain frost respects extraSlowTargets limit`() {
        val e1 = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f)
        val e2 = Enemy.spawn(2, EnemyType.GRUNT, 20f, 0f)
        val e3 = Enemy.spawn(3, EnemyType.GRUNT, 30f, 0f)

        val proj = ProjectileData(
            1, 5f, 0f, targetId = 1, damage = 3f, speed = 350f,
            isSlowing = true, extraSlowTargets = 1
        )
        val result = Projectile.update(proj, 0.016f, listOf(e1, e2, e3))
        assertEquals(2, result.damageEvents.size) // primary + 1 chain
    }

    // ========================
    // SLOW MULTIPLIER PROPAGATION
    // ========================

    @Test
    fun `upgraded slow multiplier stored on enemy`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        val slowed = Enemy.applySlow(e, multiplier = 0.40f)
        assertEquals(0.40f, slowed.slowMultiplier, 0.001f)
        assertEquals(80f * 0.40f, slowed.speed, 0.001f)
    }

    // ========================
    // WIRING FIX 1 — BEACON INCOME
    // ========================

    @Test
    fun `beacon income added on wave completion`() {
        val beacon = Tower.place(1, TowerType.BEACON, 0, 0)
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            towers = listOf(beacon),
            enemies = emptyList(),
            spawnQueue = emptyList(),
            leaksThisWave = 0
        )
        val updated = GameStateManager.update(state, 0.01f)
        val waveBonus = WaveSpawner.waveBonus(1)
        val beaconIncome = Upgrade.effectiveIncome(TowerType.BEACON, UpgradeData())
        assertEquals(8, beaconIncome)
        assertEquals(GameConstants.STARTING_GOLD + waveBonus + beaconIncome, updated.gold)
    }

    @Test
    fun `upgraded beacon produces more income`() {
        val beacon = Tower.place(1, TowerType.BEACON, 0, 0)
            .copy(upgrades = UpgradeData(pathA = 2)) // 8 + 16 = 24
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            towers = listOf(beacon),
            enemies = emptyList(),
            spawnQueue = emptyList(),
            leaksThisWave = 0
        )
        val updated = GameStateManager.update(state, 0.01f)
        val waveBonus = WaveSpawner.waveBonus(1)
        assertEquals(GameConstants.STARTING_GOLD + waveBonus + 24, updated.gold)
    }

    @Test
    fun `beacon income not paid when leaks occurred`() {
        val beacon = Tower.place(1, TowerType.BEACON, 0, 0)
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            towers = listOf(beacon),
            enemies = emptyList(),
            spawnQueue = emptyList(),
            leaksThisWave = 1  // zero-leak bonus denied, but beacon income still paid
        )
        val updated = GameStateManager.update(state, 0.01f)
        val beaconIncome = Upgrade.effectiveIncome(TowerType.BEACON, UpgradeData())
        assertEquals(GameConstants.STARTING_GOLD + beaconIncome, updated.gold)
    }

    // ========================
    // WIRING FIX 2 — BEACON AURA
    // ========================

    @Test
    fun `beacon aura buffs nearby tower fire rate`() {
        val beacon = Tower.place(1, TowerType.BEACON, 0, 0)
        val pulse = Tower.place(2, TowerType.PULSE, 1, 0) // 64px away, within beacon range 100
        val state = GameStateManager.newGame().copy(
            towers = listOf(beacon, pulse),
            enemies = listOf(
                Enemy.spawn(3, EnemyType.GRUNT, pulse.x + 50f, pulse.y)
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            nextEntityId = 4,
            gameTimeSec = 2.0f
        )
        val updated = GameStateManager.update(state, 0.01f)
        val pulseTower = updated.towers.find { it.type == TowerType.PULSE }!!
        val expectedMultiplier = 1f + Upgrade.effectiveAuraBonus(TowerType.BEACON, UpgradeData())
        assertEquals(expectedMultiplier, pulseTower.fireRateMultiplier, 0.001f)
    }

    @Test
    fun `beacon aura uses strongest wins not stacking`() {
        val beacon1 = Tower.place(1, TowerType.BEACON, 0, 0) // base aura 0.05
        val beacon2 = Tower.place(2, TowerType.BEACON, 0, 1)
            .copy(upgrades = UpgradeData(pathB = 2)) // aura 0.05 + 0.15 = 0.20
        val pulse = Tower.place(3, TowerType.PULSE, 1, 0) // adjacent to both
        val state = GameStateManager.newGame().copy(
            towers = listOf(beacon1, beacon2, pulse),
            enemies = listOf(
                Enemy.spawn(4, EnemyType.GRUNT, pulse.x + 50f, pulse.y)
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            nextEntityId = 5,
            gameTimeSec = 2.0f
        )
        val updated = GameStateManager.update(state, 0.01f)
        val pulseTower = updated.towers.find { it.type == TowerType.PULSE }!!
        // Strongest wins (0.20), NOT sum (0.25)
        assertEquals(1f + 0.20f, pulseTower.fireRateMultiplier, 0.001f)
    }

    @Test
    fun `beacon does not buff itself`() {
        val beacon = Tower.place(1, TowerType.BEACON, 0, 0)
        val state = GameStateManager.newGame().copy(
            towers = listOf(beacon),
            enemies = listOf(
                Enemy.spawn(2, EnemyType.GRUNT, 200f, 200f)
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            nextEntityId = 3,
            gameTimeSec = 2.0f
        )
        val updated = GameStateManager.update(state, 0.01f)
        val beaconTower = updated.towers.find { it.type == TowerType.BEACON }!!
        assertEquals(1f, beaconTower.fireRateMultiplier, 0.001f)
    }

    @Test
    fun `beacon out of range does not buff tower`() {
        val beacon = Tower.place(1, TowerType.BEACON, 0, 0)
        val pulse = Tower.place(2, TowerType.PULSE, 5, 5) // far away (~320px diagonal > 100)
        val state = GameStateManager.newGame().copy(
            towers = listOf(beacon, pulse),
            enemies = listOf(
                Enemy.spawn(3, EnemyType.GRUNT, pulse.x + 50f, pulse.y)
            ),
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            nextEntityId = 4,
            gameTimeSec = 2.0f
        )
        val updated = GameStateManager.update(state, 0.01f)
        val pulseTower = updated.towers.find { it.type == TowerType.PULSE }!!
        assertEquals(1f, pulseTower.fireRateMultiplier, 0.001f)
    }

    // ========================
    // WIRING FIX 3 — RAILGUN PIERCE SHIELD
    // ========================

    @Test
    fun `railgun projectile has pierceShield set`() {
        val tower = Tower.place(1, TowerType.RAILGUN, 3, 3)
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val result = Tower.update(tower, listOf(enemy), 4.0f) // RAILGUN 0.25/sec → 4s interval
        assertNotNull(result.projectile)
        assertTrue(result.projectile!!.pierceShield)
    }

    @Test
    fun `non-railgun projectile has pierceShield false`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3)
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val result = Tower.update(tower, listOf(enemy), 1.0f)
        assertNotNull(result.projectile)
        assertFalse(result.projectile!!.pierceShield)
    }

    @Test
    fun `railgun DamageEvent carries pierceShield`() {
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f)
        val proj = ProjectileData(
            1, 5f, 0f, targetId = 1, damage = 30f, speed = 500f,
            pierceShield = true
        )
        val result = Projectile.update(proj, 0.016f, listOf(enemy))
        assertEquals(1, result.damageEvents.size)
        assertTrue(result.damageEvents[0].pierceShield)
    }

    @Test
    fun `railgun pierces boss shield in full pipeline`() {
        val boss = Enemy.spawn(1, EnemyType.BOSS, 250f, 250f).copy(
            shield = 100f, shieldMax = 100f
        )
        val proj = ProjectileData(
            id = 2, x = 245f, y = 250f, targetId = 1,
            damage = 30f, speed = 500f, pierceShield = true
        )
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            enemies = listOf(boss),
            projectiles = listOf(proj),
            nextEntityId = 3
        )
        val updated = GameStateManager.update(state, 0.016f)
        val updatedBoss = updated.enemies.find { it.id == 1 }
        assertNotNull(updatedBoss)
        // Shield UNTOUCHED (pierced), HP reduced by 30
        assertEquals(100f, updatedBoss!!.shield, 0.001f)
        assertEquals(800f - 30f, updatedBoss.health, 0.001f)
    }

    @Test
    fun `non-pierce projectile damages shield first`() {
        val boss = Enemy.spawn(1, EnemyType.BOSS, 250f, 250f).copy(
            shield = 100f, shieldMax = 100f
        )
        val proj = ProjectileData(
            id = 2, x = 245f, y = 250f, targetId = 1,
            damage = 30f, speed = 500f, pierceShield = false
        )
        val state = GameStateManager.newGame().copy(
            phase = TDPhase.ACTIVE,
            waveNumber = 1,
            enemies = listOf(boss),
            projectiles = listOf(proj),
            nextEntityId = 3
        )
        val updated = GameStateManager.update(state, 0.016f)
        val updatedBoss = updated.enemies.find { it.id == 1 }
        assertNotNull(updatedBoss)
        // Shield absorbs 30 damage, HP untouched
        assertEquals(70f, updatedBoss!!.shield, 0.001f)
        assertEquals(800f, updatedBoss.health, 0.001f)
    }

    // ========================
    // SLOW MULTIPLIER PROPAGATION
    // ========================

    @Test
    fun `strongest slow wins when already slowed`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        // First slow: base multiplier (0.65)
        val slow1 = Enemy.applySlow(e)
        assertEquals(0.65f, slow1.slowMultiplier, 0.001f)
        // Second slow: stronger (0.40) → should win
        val slow2 = Enemy.applySlow(slow1, multiplier = 0.40f)
        assertEquals(0.40f, slow2.slowMultiplier, 0.001f)
        // Third slow: weaker (0.65) → should NOT override
        val slow3 = Enemy.applySlow(slow2, multiplier = 0.65f)
        assertEquals(0.40f, slow3.slowMultiplier, 0.001f)
    }
}
