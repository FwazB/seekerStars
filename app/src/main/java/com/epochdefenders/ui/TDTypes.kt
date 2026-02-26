package com.epochdefenders.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.epochdefenders.engine.EnemyType
import com.epochdefenders.engine.TowerType

/** Render colors for engine tower types — TRON neon palette. */
fun TowerType.color(): Color = when (this) {
    TowerType.PULSE -> Color(0xFF0080FF)       // Electric Blue
    TowerType.NOVA_CANNON -> Color(0xFFFF6600) // Neon Orange
    TowerType.CRYO -> Color(0xFFAAFFFF)        // Cyan/Ice-White
    TowerType.RAILGUN -> Color(0xFFFF4444)     // Neon Red / danger
    TowerType.BEACON -> Color(0xFFFFD700)      // Neon Gold
}

/** Glow color per tower — used for outer glow rings. */
fun TowerType.glowColor(): Color = when (this) {
    TowerType.PULSE -> Color(0xFF00FFFF)       // Cyan glow
    TowerType.NOVA_CANNON -> Color(0xFFFF6600) // Warm orange glow
    TowerType.CRYO -> Color(0xFF00FFFF)        // Cold cyan glow
    TowerType.RAILGUN -> Color(0xFFCC2222)     // Dark red glow
    TowerType.BEACON -> Color(0xFFCCAA00)      // Warm gold glow
}

/** Render colors for engine enemy types — TRON neon palette. */
fun EnemyType.color(): Color = when (this) {
    EnemyType.GRUNT -> Color(0xFFFF0044)       // Neon Red
    EnemyType.RUNNER -> Color(0xFFFF00FF)      // Hot Magenta
    EnemyType.TANK -> Color(0xFF8800FF)        // Deep Purple
    EnemyType.BOSS -> Color(0xFFFF0000)        // Bright Red
}

/** Glow color per enemy — darker version for outer glow. */
fun EnemyType.glowColor(): Color = when (this) {
    EnemyType.GRUNT -> Color(0xFFAA0022)       // Dark neon red
    EnemyType.RUNNER -> Color(0xFFCC00CC)      // Dark magenta
    EnemyType.TANK -> Color(0xFF6600CC)        // Dark purple
    EnemyType.BOSS -> Color(0xFFCC0000)        // Dark red
}

data class Tower(
    val x: Float,
    val y: Float,
    val type: TowerType,
    val turretAngle: Float = 0f,
    val showRange: Boolean = false,
    val pathALevel: Int = 0,
    val pathBLevel: Int = 0
) {
    /** Visual tier = max of both upgrade paths (0-3). */
    val tier: Int get() = maxOf(pathALevel, pathBLevel)

    /** Which path is dominant for color tinting. Null if no upgrades. */
    val activePath: UpgradePath? get() = when {
        pathALevel == 0 && pathBLevel == 0 -> null
        pathALevel >= pathBLevel -> UpgradePath.A
        else -> UpgradePath.B
    }
}

enum class UpgradePath { A, B }

data class Enemy(
    val x: Float,
    val y: Float,
    val type: EnemyType,
    val health: Float,
    val maxHealth: Float,
    val slowed: Boolean = false,
    val shield: Float = 0f,
    val shieldMax: Float = 0f
)

data class Projectile(
    val x: Float,
    val y: Float,
    val color: Color
)

data class GridState(
    val cols: Int = 12,
    val rows: Int = 9,
    val cellSize: Int = 64,
    val offsetX: Float = 16f,
    val offsetY: Float = 48f,
    val buildable: List<List<Boolean>>,
    val pathPoints: List<Offset>
)

/** Visual effects with progress 0..1 (caller manages timing). */
sealed class VisualEffect {
    abstract val x: Float
    abstract val y: Float
    abstract val progress: Float

    /** Expanding ring at projectile impact. 200ms duration. */
    data class Splash(
        override val x: Float,
        override val y: Float,
        override val progress: Float,
        val radius: Float = 60f,
        val color: Color
    ) : VisualEffect()

    /** Small burst at projectile hit. 150ms duration. */
    data class Hit(
        override val x: Float,
        override val y: Float,
        override val progress: Float,
        val color: Color
    ) : VisualEffect()

    /** 8 particles radiating from kill position. 300ms duration. */
    data class DeathParticles(
        override val x: Float,
        override val y: Float,
        override val progress: Float,
        val color: Color
    ) : VisualEffect()

    /** Floating "+Xg" gold text rising from kill position. 1s duration. */
    data class GoldPopup(
        override val x: Float,
        override val y: Float,
        override val progress: Float,
        val amount: Int
    ) : VisualEffect()

    /** Center-screen combo bonus flash. 1.5s duration. */
    data class ComboFlash(
        override val x: Float,
        override val y: Float,
        override val progress: Float,
        val bonus: Int,
        val comboSize: Int
    ) : VisualEffect()
}
