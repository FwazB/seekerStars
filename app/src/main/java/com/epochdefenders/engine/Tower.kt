package com.epochdefenders.engine

import kotlin.math.sqrt

data class TowerData(
    val id: Int,
    val type: TowerType,
    val gridX: Int,
    val gridY: Int,
    val x: Float,
    val y: Float,
    val lastFireTimeSec: Float = 0f,
    val targetId: Int? = null,
    val upgrades: UpgradeData = UpgradeData(),
    val fireRateMultiplier: Float = 1f
)

data class FireResult(
    val tower: TowerData,
    val projectile: ProjectileData?
)

object Tower {

    fun place(id: Int, type: TowerType, gridX: Int, gridY: Int): TowerData {
        val x = gridX * GameConstants.GRID_SIZE.toFloat() + GameConstants.GRID_SIZE / 2f
        val y = gridY * GameConstants.GRID_SIZE.toFloat() + GameConstants.GRID_SIZE / 2f
        return TowerData(id = id, type = type, gridX = gridX, gridY = gridY, x = x, y = y)
    }

    fun update(
        tower: TowerData,
        enemies: List<EnemyData>,
        currentTimeSec: Float,
        rangeMultiplier: Float = 1f
    ): FireResult {
        // BEACON and CRYO do not fire projectiles (CRYO uses aura slow instead)
        if (tower.type == TowerType.BEACON || tower.type == TowerType.CRYO) {
            return FireResult(tower = tower, projectile = null)
        }

        val effectiveRange = tower.type.range * rangeMultiplier
        val targeted = acquireTarget(tower, enemies, effectiveRange)
        val target = targeted.targetId?.let { tid -> enemies.find { it.id == tid && it.active } }

        if (target == null || !canFire(targeted, currentTimeSec)) {
            return FireResult(tower = targeted, projectile = null)
        }

        val u = targeted.upgrades
        val projectile = ProjectileData(
            id = -1, // caller assigns real ID
            x = targeted.x,
            y = targeted.y,
            targetId = target.id,
            damage = Upgrade.effectiveDamage(targeted.type, u),
            speed = targeted.type.projectileSpeed,
            isSplash = targeted.type.isSplash,
            isSlowing = targeted.type.isSlowing,
            splashRadius = Upgrade.effectiveSplashRadius(targeted.type, u),
            slowMultiplier = Upgrade.effectiveSlowMultiplier(targeted.type, u),
            extraSlowTargets = Upgrade.extraTargets(targeted.type, u),
            pierceShield = targeted.type == TowerType.RAILGUN
        )

        return FireResult(
            tower = targeted.copy(lastFireTimeSec = currentTimeSec),
            projectile = projectile
        )
    }

    fun findTarget(tower: TowerData, enemies: List<EnemyData>, effectiveRange: Float = tower.type.range): EnemyData? {
        var closestDist = effectiveRange
        var closest: EnemyData? = null

        for (enemy in enemies) {
            if (!enemy.active) continue
            val dist = distanceTo(tower, enemy)
            if (dist <= closestDist) {
                closestDist = dist
                closest = enemy
            }
        }
        return closest
    }

    fun canFire(tower: TowerData, currentTimeSec: Float): Boolean {
        val baseRate = Upgrade.effectiveFireRate(tower.type, tower.upgrades)
        val effectiveRate = baseRate * tower.fireRateMultiplier
        if (effectiveRate <= 0f) return false  // BEACON or zero-rate: never fires
        val fireInterval = 1f / effectiveRate
        return currentTimeSec - tower.lastFireTimeSec >= fireInterval
    }

    private fun acquireTarget(tower: TowerData, enemies: List<EnemyData>, effectiveRange: Float = tower.type.range): TowerData {
        // Check if current target is still valid
        if (tower.targetId != null) {
            val current = enemies.find { it.id == tower.targetId && it.active }
            if (current != null && distanceTo(tower, current) <= effectiveRange) {
                return tower
            }
        }
        // Find new closest target
        val newTarget = findTarget(tower, enemies, effectiveRange)
        return tower.copy(targetId = newTarget?.id)
    }

    private fun distanceTo(tower: TowerData, enemy: EnemyData): Float {
        val dx = tower.x - enemy.x
        val dy = tower.y - enemy.y
        return sqrt(dx * dx + dy * dy)
    }
}
