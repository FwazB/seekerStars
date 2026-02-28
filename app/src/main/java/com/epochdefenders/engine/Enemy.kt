package com.epochdefenders.engine

import kotlin.math.sqrt

data class EnemyData(
    val id: Int,
    val type: EnemyType,
    val health: Float,
    val maxHealth: Float,
    val speed: Float,
    val baseSpeed: Float,
    val x: Float,
    val y: Float,
    val pathIndex: Int = 0,
    val slowed: Boolean = false,
    val slowTimer: Float = 0f,
    val slowMultiplier: Float = GameConstants.CRYO_SLOW_MULTIPLIER,
    val active: Boolean = true,
    val reachedEnd: Boolean = false,
    val shield: Float = 0f,
    val shieldMax: Float = 0f,
    val immuneToSlow: Boolean = false
)

object Enemy {

    fun spawn(
        id: Int,
        type: EnemyType,
        startX: Float,
        startY: Float,
        difficultyMultiplier: Float = 1f,
        speedMultiplier: Float = 1f
    ): EnemyData {
        val scaledHealth = type.baseHealth * difficultyMultiplier
        val scaledSpeed = type.baseSpeed * speedMultiplier
        return EnemyData(
            id = id,
            type = type,
            health = scaledHealth,
            maxHealth = scaledHealth,
            speed = scaledSpeed,
            baseSpeed = scaledSpeed,
            x = startX,
            y = startY
        )
    }

    fun update(enemy: EnemyData, dtSec: Float, path: List<PathPoint>): EnemyData {
        if (!enemy.active) return enemy

        // 1. Handle slow timer
        var slowed = enemy.slowed
        var slowTimer = enemy.slowTimer
        if (slowed) {
            slowTimer -= dtSec
            if (slowTimer <= 0f) {
                slowed = false
                slowTimer = 0f
            }
        }

        // 2. Calculate effective speed
        var speed = enemy.baseSpeed
        if (slowed) speed *= enemy.slowMultiplier

        // 3. Move along path
        return moveAlongPath(
            enemy.copy(slowed = slowed, slowTimer = slowTimer, speed = speed),
            dtSec,
            path
        )
    }

    fun takeDamage(enemy: EnemyData, amount: Float, pierceShield: Boolean = false): EnemyData {
        if (!enemy.active) return enemy

        if (pierceShield) {
            // Skip shield, damage goes directly to HP
            val newHealth = enemy.health - amount
            return if (newHealth <= 0f) {
                enemy.copy(health = 0f, active = false)
            } else {
                enemy.copy(health = newHealth)
            }
        }

        var remaining = amount
        var shield = enemy.shield

        // Shield absorbs damage first
        if (shield > 0f) {
            if (remaining <= shield) {
                return enemy.copy(shield = shield - remaining)
            }
            remaining -= shield
            shield = 0f
        }

        val newHealth = enemy.health - remaining
        return if (newHealth <= 0f) {
            enemy.copy(health = 0f, shield = 0f, active = false)
        } else {
            enemy.copy(health = newHealth, shield = shield)
        }
    }

    fun applySlow(
        enemy: EnemyData,
        durationSec: Float = GameConstants.CRYO_SLOW_DURATION_SEC,
        multiplier: Float = GameConstants.CRYO_SLOW_MULTIPLIER
    ): EnemyData {
        if (!enemy.active || enemy.immuneToSlow) return enemy
        // Strongest slow wins (lower multiplier = stronger slow)
        val bestMult = if (enemy.slowed) minOf(enemy.slowMultiplier, multiplier) else multiplier
        val bestTimer = if (enemy.slowed) maxOf(enemy.slowTimer, durationSec) else durationSec
        return enemy.copy(
            slowed = true,
            slowTimer = bestTimer,
            slowMultiplier = bestMult,
            speed = enemy.baseSpeed * bestMult
        )
    }

    private fun moveAlongPath(
        enemy: EnemyData,
        dtSec: Float,
        path: List<PathPoint>
    ): EnemyData {
        if (enemy.pathIndex >= path.size) {
            return enemy.copy(active = false, reachedEnd = true)
        }

        val target = path[enemy.pathIndex]
        val dx = target.x - enemy.x
        val dy = target.y - enemy.y
        val dist = sqrt(dx * dx + dy * dy)

        if (dist < GameConstants.WAYPOINT_REACH_DIST) {
            return enemy.copy(pathIndex = enemy.pathIndex + 1)
        }

        val moveSpeed = enemy.speed * dtSec
        val moveX = (dx / dist) * moveSpeed
        val moveY = (dy / dist) * moveSpeed

        return enemy.copy(x = enemy.x + moveX, y = enemy.y + moveY)
    }
}
