package com.epochdefenders.engine

import kotlin.math.sqrt

data class ProjectileData(
    val id: Int,
    val x: Float,
    val y: Float,
    val targetId: Int,
    val damage: Float,
    val speed: Float,
    val isSplash: Boolean = false,
    val isSlowing: Boolean = false,
    val splashRadius: Float = GameConstants.SPLASH_RADIUS,
    val slowMultiplier: Float = GameConstants.CRYO_SLOW_MULTIPLIER,
    val extraSlowTargets: Int = 0,
    val pierceShield: Boolean = false,
    val active: Boolean = true
)

data class DamageEvent(
    val enemyId: Int,
    val damage: Float,
    val applySlow: Boolean = false,
    val slowMultiplier: Float = GameConstants.CRYO_SLOW_MULTIPLIER,
    val pierceShield: Boolean = false
)

data class ProjectileUpdate(
    val projectile: ProjectileData,
    val damageEvents: List<DamageEvent> = emptyList()
)

object Projectile {

    fun update(
        proj: ProjectileData,
        dtSec: Float,
        enemies: List<EnemyData>
    ): ProjectileUpdate {
        if (!proj.active) return ProjectileUpdate(proj)

        val target = enemies.find { it.id == proj.targetId && it.active }
        if (target == null) {
            return ProjectileUpdate(proj.copy(active = false))
        }

        val dx = target.x - proj.x
        val dy = target.y - proj.y
        val dist = sqrt(dx * dx + dy * dy)

        // Hit detection
        if (dist < GameConstants.PROJECTILE_HIT_DIST) {
            return onHit(proj, enemies)
        }

        // Move towards target (homing)
        val moveSpeed = proj.speed * dtSec
        val moveX = (dx / dist) * moveSpeed
        val moveY = (dy / dist) * moveSpeed

        return ProjectileUpdate(
            proj.copy(x = proj.x + moveX, y = proj.y + moveY)
        )
    }

    private fun onHit(
        proj: ProjectileData,
        enemies: List<EnemyData>
    ): ProjectileUpdate {
        val events = mutableListOf<DamageEvent>()

        if (proj.isSplash) {
            // Splash damage: hit all enemies within radius
            for (enemy in enemies) {
                if (!enemy.active) continue
                val dx = enemy.x - proj.x
                val dy = enemy.y - proj.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= proj.splashRadius) {
                    events.add(DamageEvent(enemyId = enemy.id, damage = proj.damage, pierceShield = proj.pierceShield))
                }
            }
        } else {
            // Single target damage
            events.add(
                DamageEvent(
                    enemyId = proj.targetId,
                    damage = proj.damage,
                    applySlow = proj.isSlowing,
                    slowMultiplier = proj.slowMultiplier,
                    pierceShield = proj.pierceShield
                )
            )
            // Chain Frost: hit extra nearby targets
            if (proj.isSlowing && proj.extraSlowTargets > 0) {
                enemies.filter { it.active && it.id != proj.targetId }
                    .map { e ->
                        val ddx = e.x - proj.x; val ddy = e.y - proj.y
                        Pair(e, sqrt(ddx * ddx + ddy * ddy))
                    }
                    .filter { it.second <= proj.splashRadius }
                    .sortedBy { it.second }
                    .take(proj.extraSlowTargets)
                    .forEach { (e, _) ->
                        events.add(DamageEvent(e.id, proj.damage, true, proj.slowMultiplier, proj.pierceShield))
                    }
            }
        }

        return ProjectileUpdate(
            projectile = proj.copy(active = false),
            damageEvents = events
        )
    }
}
