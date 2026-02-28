package com.epochdefenders.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TowerDefenseTest {

    private val simplePath = listOf(
        PathPoint(0f, 0f),
        PathPoint(100f, 0f),
        PathPoint(100f, 100f)
    )

    // ========================
    // ENEMY — SPAWN + HEALTH SCALING
    // ========================

    @Test
    fun `enemy spawns with correct base stats`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        assertEquals(25f, e.health, 0.001f)
        assertEquals(25f, e.maxHealth, 0.001f)
        assertEquals(65f, e.speed, 0.001f)
        assertTrue(e.active)
    }

    @Test
    fun `enemy health scales with difficulty multiplier`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f, difficultyMultiplier = 2f)
        assertEquals(50f, e.health, 0.001f)
        assertEquals(50f, e.maxHealth, 0.001f)
        assertEquals(65f, e.speed, 0.001f) // speed not scaled by difficulty
    }

    @Test
    fun `enemy speed scales with speed multiplier`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f, speedMultiplier = 1.5f)
        assertEquals(25f, e.health, 0.001f)
        assertEquals(97.5f, e.speed, 0.001f) // 65 * 1.5
        assertEquals(97.5f, e.baseSpeed, 0.001f)
    }

    @Test
    fun `boss has correct stats`() {
        val e = Enemy.spawn(1, EnemyType.BOSS, 0f, 0f)
        assertEquals(800f, e.health, 0.001f)
        assertEquals(35f, e.speed, 0.001f)
    }

    // ========================
    // ENEMY — PATH FOLLOWING
    // ========================

    @Test
    fun `enemy moves toward first waypoint`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        val updated = Enemy.update(e, 1f, simplePath)
        // Start at (0,0), first waypoint is (0,0) → dist < 5 → advance
        assertEquals(1, updated.pathIndex)
    }

    @Test
    fun `enemy advances path index when reaching waypoint`() {
        // Start at (98, 0), target is (100, 0) → dist = 2 < 5 → advance
        val e = Enemy.spawn(1, EnemyType.GRUNT, 98f, 0f).copy(pathIndex = 1)
        val updated = Enemy.update(e, 0.016f, simplePath)
        assertEquals(2, updated.pathIndex)
    }

    @Test
    fun `enemy deactivates and flags reachedEnd at path end`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 100f, 100f)
            .copy(pathIndex = 3) // past last index
        val updated = Enemy.update(e, 0.016f, simplePath)
        assertFalse(updated.active)
        assertTrue(updated.reachedEnd)
    }

    @Test
    fun `enemy moves at correct speed`() {
        // Start far from waypoint at index 1 (100, 0)
        val e = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f).copy(pathIndex = 1)
        val updated = Enemy.update(e, 0.5f, simplePath)
        // Speed 65, dt 0.5s → move 32.5px toward (100, 0)
        assertEquals(42.5f, updated.x, 1f) // 10 + 32.5
        assertEquals(0f, updated.y, 0.1f)
    }

    // ========================
    // ENEMY — DAMAGE
    // ========================

    @Test
    fun `takeDamage reduces health`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        val damaged = Enemy.takeDamage(e, 10f)
        assertEquals(15f, damaged.health, 0.001f) // 25 - 10
        assertTrue(damaged.active)
    }

    @Test
    fun `enemy dies when health reaches 0`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        val dead = Enemy.takeDamage(e, 25f)
        assertEquals(0f, dead.health, 0.001f)
        assertFalse(dead.active)
    }

    @Test
    fun `overkill damage still deactivates`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        val dead = Enemy.takeDamage(e, 100f)
        assertFalse(dead.active)
    }

    // ========================
    // ENEMY — SHIELD ABSORPTION
    // ========================

    @Test
    fun `shield absorbs damage before health`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f).copy(shield = 10f)
        val hit = Enemy.takeDamage(e, 8f)
        assertEquals(25f, hit.health, 0.001f) // health unchanged
        assertEquals(2f, hit.shield, 0.001f) // 10 - 8
    }

    @Test
    fun `damage overflows from shield to health`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f).copy(shield = 5f)
        val hit = Enemy.takeDamage(e, 12f)
        assertEquals(18f, hit.health, 0.001f) // 25 - (12 - 5)
        assertEquals(0f, hit.shield, 0.001f)
    }

    // ========================
    // ENEMY — SLOW EFFECT
    // ========================

    @Test
    fun `applySlow reduces speed`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f)
        val slowed = Enemy.applySlow(e)
        assertTrue(slowed.slowed)
        assertEquals(65f * 0.65f, slowed.speed, 0.001f)
        assertEquals(65f, slowed.baseSpeed, 0.001f)
    }

    @Test
    fun `slow wears off after duration`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 50f, 0f).copy(pathIndex = 1)
        val slowed = Enemy.applySlow(e) // 2.5s duration
        // Update for 2.6 seconds — slow should wear off
        val updated = Enemy.update(slowed, 2.6f, simplePath)
        assertFalse(updated.slowed)
    }

    @Test
    fun `slow timer drains each frame`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 50f, 0f).copy(pathIndex = 1)
        val slowed = Enemy.applySlow(e) // 2.5s
        val updated = Enemy.update(slowed, 0.5f, simplePath)
        assertTrue(updated.slowed)
        assertEquals(2.0f, updated.slowTimer, 0.01f) // 2.5 - 0.5
    }

    @Test
    fun `immuneToSlow prevents slow application`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f).copy(immuneToSlow = true)
        val result = Enemy.applySlow(e)
        assertFalse(result.slowed)
        assertEquals(65f, result.speed, 0.001f)
    }

    // ========================
    // ENEMY — BOSS ABILITIES
    // ========================

    // ========================
    // TOWER — TARGETING
    // ========================

    @Test
    fun `tower finds nearest enemy in range`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3)
        val near = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val far = Enemy.spawn(2, EnemyType.GRUNT, tower.x + 130f, tower.y)
        val outOfRange = Enemy.spawn(3, EnemyType.GRUNT, tower.x + 200f, tower.y)

        val target = Tower.findTarget(tower, listOf(near, far, outOfRange))
        assertNotNull(target)
        assertEquals(1, target!!.id)
    }

    @Test
    fun `tower ignores inactive enemies`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3)
        val dead = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y).copy(active = false)
        val alive = Enemy.spawn(2, EnemyType.GRUNT, tower.x + 100f, tower.y)

        val target = Tower.findTarget(tower, listOf(dead, alive))
        assertNotNull(target)
        assertEquals(2, target!!.id)
    }

    @Test
    fun `tower returns null when no enemies in range`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3) // range 140
        val far = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 200f, tower.y)

        val target = Tower.findTarget(tower, listOf(far))
        assertNull(target)
    }

    // ========================
    // TOWER — FIRE RATE
    // ========================

    @Test
    fun `canFire respects fire rate interval`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3) // 1 shot/sec
        assertTrue(Tower.canFire(tower, 1.0f)) // 1.0 - 0.0 >= 1.0
        assertFalse(Tower.canFire(tower.copy(lastFireTimeSec = 0.5f), 1.0f)) // 0.5 < 1.0
    }

    @Test
    fun `cryo does not fire projectiles — uses aura instead`() {
        val tower = Tower.place(1, TowerType.CRYO, 3, 3) // aura-based, fireRate = 0
        assertFalse(Tower.canFire(tower, 1.0f))
        assertFalse(Tower.canFire(tower, 100.0f))
    }

    @Test
    fun `nova cannon fires every 2 seconds`() {
        val tower = Tower.place(1, TowerType.NOVA_CANNON, 3, 3) // 0.5 shots/sec → 2.0s interval
        assertFalse(Tower.canFire(tower, 1.5f)) // 1.5 < 2.0
        assertTrue(Tower.canFire(tower, 2.0f))  // 2.0 >= 2.0
    }

    @Test
    fun `tower update produces projectile when target in range and ready to fire`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3)
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val result = Tower.update(tower, listOf(enemy), 1.0f)
        assertNotNull(result.projectile)
        assertEquals(8f, result.projectile!!.damage, 0.001f)
    }

    @Test
    fun `tower update returns no projectile when on cooldown`() {
        val tower = Tower.place(1, TowerType.PULSE, 3, 3).copy(lastFireTimeSec = 0.5f)
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, tower.x + 50f, tower.y)
        val result = Tower.update(tower, listOf(enemy), 1.0f)
        assertNull(result.projectile)
    }

    // ========================
    // PROJECTILE — MOVEMENT + HIT
    // ========================

    @Test
    fun `projectile moves toward target`() {
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, 200f, 0f)
        val proj = ProjectileData(1, 0f, 0f, targetId = 1, damage = 8f, speed = 400f)
        val result = Projectile.update(proj, 0.1f, listOf(enemy))
        assertTrue(result.projectile.x > 0f)
        assertTrue(result.projectile.active)
        assertTrue(result.damageEvents.isEmpty())
    }

    @Test
    fun `projectile hits target within 15px`() {
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f)
        val proj = ProjectileData(1, 5f, 0f, targetId = 1, damage = 8f, speed = 400f)
        val result = Projectile.update(proj, 0.016f, listOf(enemy))
        assertFalse(result.projectile.active)
        assertEquals(1, result.damageEvents.size)
        assertEquals(8f, result.damageEvents[0].damage, 0.001f)
        assertEquals(1, result.damageEvents[0].enemyId)
    }

    @Test
    fun `projectile deactivates when target is dead`() {
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, 100f, 0f).copy(active = false)
        val proj = ProjectileData(1, 0f, 0f, targetId = 1, damage = 8f, speed = 400f)
        val result = Projectile.update(proj, 0.016f, listOf(enemy))
        assertFalse(result.projectile.active)
    }

    // ========================
    // PROJECTILE — SPLASH
    // ========================

    @Test
    fun `splash projectile damages all enemies in radius`() {
        val e1 = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f)
        val e2 = Enemy.spawn(2, EnemyType.GRUNT, 30f, 0f)
        val e3 = Enemy.spawn(3, EnemyType.GRUNT, 200f, 0f) // out of 80px splash

        val proj = ProjectileData(
            1, 5f, 0f, targetId = 1, damage = 20f, speed = 300f,
            isSplash = true, splashRadius = 80f
        )
        val result = Projectile.update(proj, 0.016f, listOf(e1, e2, e3))
        assertFalse(result.projectile.active)
        assertEquals(2, result.damageEvents.size) // e1 and e2 hit, e3 too far
    }

    // ========================
    // PROJECTILE — CRYO SLOW
    // ========================

    @Test
    fun `cryo projectile applies slow on hit`() {
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f)
        val proj = ProjectileData(
            1, 5f, 0f, targetId = 1, damage = 3f, speed = 350f,
            isSlowing = true
        )
        val result = Projectile.update(proj, 0.016f, listOf(enemy))
        assertEquals(1, result.damageEvents.size)
        assertTrue(result.damageEvents[0].applySlow)
    }

    @Test
    fun `non-cryo projectile does not apply slow`() {
        val enemy = Enemy.spawn(1, EnemyType.GRUNT, 10f, 0f)
        val proj = ProjectileData(1, 5f, 0f, targetId = 1, damage = 8f, speed = 400f)
        val result = Projectile.update(proj, 0.016f, listOf(enemy))
        assertFalse(result.damageEvents[0].applySlow)
    }

    // ========================
    // DIFFICULTY SCALING
    // ========================

    @Test
    fun `grunt with difficulty 2 has 50 health`() {
        val e = Enemy.spawn(1, EnemyType.GRUNT, 0f, 0f, difficultyMultiplier = 2f)
        assertEquals(50f, e.health, 0.001f) // 25 * 2
    }

    @Test
    fun `runner with difficulty 3 has 60 health`() {
        val e = Enemy.spawn(1, EnemyType.RUNNER, 0f, 0f, difficultyMultiplier = 3f)
        assertEquals(60f, e.health, 0.001f) // 20 * 3
    }
}
