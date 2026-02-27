package com.epochdefenders.engine

object GameConstants {
    const val GRID_SIZE = 64
    const val GRID_COLS = 12
    const val GRID_ROWS = 9
    const val GAME_WIDTH = 800
    const val GAME_HEIGHT = 600

    const val STARTING_GOLD = 150
    const val STARTING_LIVES = 15

    const val WAVE_DELAY_SEC = 3.0f
    const val SPAWN_INTERVAL_SEC = 1.0f

    const val WAVE_AUTO_START_SEC = 30f
    const val EARLY_SEND_BONUS = 10
    const val EARLY_SEND_WINDOW_SEC = 5f

    const val WAYPOINT_REACH_DIST = 5f
    const val PROJECTILE_HIT_DIST = 15f
    const val SPLASH_RADIUS = 80f
    const val CRYO_SLOW_DURATION_SEC = 2.5f
    const val CRYO_SLOW_MULTIPLIER = 0.65f

    const val SELL_REFUND_RATE = 0.7f

    // Cryo aura
    const val CRYO_BASE_LINGER_SEC = 0.5f

    // Passive wave income
    const val PASSIVE_WAVE_INCOME_BASE = 10
    const val PASSIVE_WAVE_INCOME_STEP = 5
    const val PASSIVE_WAVE_INCOME_INTERVAL = 5

    // Kill combo
    const val COMBO_3_WINDOW_SEC = 2.0f   // 3-kill combo window
    const val COMBO_5_WINDOW_SEC = 3.0f   // 5-kill combo window
    const val COMBO_10_WINDOW_SEC = 5.0f  // 10-kill combo window
    const val COMBO_3_BONUS = 5
    const val COMBO_5_BONUS = 15
    const val COMBO_10_BONUS = 30
    const val SPEED_SCALE_PER_WAVE = 0.005f
    const val BOSS_HP_SCALE_BASE = 1.15f

    // Boss abilities
    const val SHIELD_PULSE_COOLDOWN_SEC = 8f
    const val SHIELD_PULSE_PERCENT = 0.2f
    const val RALLY_CRY_SPEED_BOOST = 0.25f
    const val RALLY_CRY_DURATION_SEC = 5f
    const val DISRUPTION_RANGE_REDUCTION = 0.15f
}

enum class EnemyType(
    val baseHealth: Float,
    val baseSpeed: Float,
    val reward: Int,
    val size: Float
) {
    GRUNT(25f, 80f, 8, 16f),
    RUNNER(20f, 160f, 12, 12f),
    TANK(120f, 50f, 20, 24f),
    BOSS(800f, 35f, 250, 48f)
}

enum class TowerType(
    val cost: Int,
    val damage: Float,
    val range: Float,
    val fireRate: Float,
    val projectileSpeed: Float,
    val isSplash: Boolean = false,
    val isSlowing: Boolean = false,
    val displayName: String
) {
    PULSE(75, 8f, 140f, 1f, 400f, displayName = "Pulse Tower"),
    NOVA_CANNON(125, 20f, 130f, 0.5f, 300f, isSplash = true, displayName = "Nova Cannon"),
    CRYO(60, 0f, 140f, 0f, 0f, isSlowing = true, displayName = "Cryo Tower"),
    RAILGUN(100, 30f, 200f, 0.25f, 500f, displayName = "Railgun"),
    BEACON(125, 0f, 100f, 0f, 0f, displayName = "Beacon")
}

enum class BossAbility { SHIELD_PULSE, RALLY_CRY, DISRUPTION_FIELD, SLOW_IMMUNE }
