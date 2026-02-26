package com.epochdefenders.engine

data class UpgradeData(
    val pathA: Int = 0, // tier 0-3
    val pathB: Int = 0  // tier 0-3
) {
    /** Can upgrade this path? Enforces tier 3 exclusion: only one path can reach 3. */
    fun canUpgrade(path: Char): Boolean {
        val next = if (path == 'A') pathA + 1 else pathB + 1
        val other = if (path == 'A') pathB else pathA
        return next <= 3 && (next < 3 || other < 3)
    }

    fun withUpgrade(path: Char): UpgradeData = when (path) {
        'A' -> copy(pathA = pathA + 1)
        'B' -> copy(pathB = pathB + 1)
        else -> this
    }
}

object Upgrade {

    // Cost per tier (index 0 = tier 1 cost, index 1 = tier 2, index 2 = tier 3)
    private val COSTS: Map<Pair<TowerType, Char>, List<Int>> = mapOf(
        Pair(TowerType.PULSE, 'A') to listOf(50, 100, 200),
        Pair(TowerType.PULSE, 'B') to listOf(40, 80, 160),
        Pair(TowerType.NOVA_CANNON, 'A') to listOf(60, 125, 275),
        Pair(TowerType.NOVA_CANNON, 'B') to listOf(75, 150, 300),
        Pair(TowerType.CRYO, 'A') to listOf(40, 80, 200),
        Pair(TowerType.CRYO, 'B') to listOf(50, 100, 200),
        Pair(TowerType.RAILGUN, 'A') to listOf(60, 120, 200),
        Pair(TowerType.RAILGUN, 'B') to listOf(50, 100, 175),
        Pair(TowerType.BEACON, 'A') to listOf(75, 150, 250),
        Pair(TowerType.BEACON, 'B') to listOf(60, 125, 225)
    )

    /** Cost to upgrade from current tier to next tier. */
    fun upgradeCost(type: TowerType, path: Char, upgrades: UpgradeData): Int {
        val current = if (path == 'A') upgrades.pathA else upgrades.pathB
        return COSTS[Pair(type, path)]?.getOrNull(current) ?: 0
    }

    /** Total gold invested: base tower cost + all applied upgrades. */
    fun totalInvested(type: TowerType, upgrades: UpgradeData): Int {
        val costA = COSTS[Pair(type, 'A')]?.take(upgrades.pathA)?.sum() ?: 0
        val costB = COSTS[Pair(type, 'B')]?.take(upgrades.pathB)?.sum() ?: 0
        return type.cost + costA + costB
    }

    // ── Effective Stats ──────────────────────────────────────────────

    /** Pulse A: +damage. Nova B: +damage. Railgun A: +damage. */
    fun effectiveDamage(type: TowerType, upgrades: UpgradeData): Float {
        var dmg = type.damage
        when (type) {
            TowerType.PULSE -> dmg += PULSE_A_DMG.getOrDefault(upgrades.pathA, 0f)
            TowerType.NOVA_CANNON -> dmg += NOVA_B_DMG.getOrDefault(upgrades.pathB, 0f)
            TowerType.RAILGUN -> dmg += RAILGUN_A_DMG.getOrDefault(upgrades.pathA, 0f)
            else -> {}
        }
        return dmg
    }

    /** Pulse B: fire rate multiplier. Railgun B: +fireRate. */
    fun effectiveFireRate(type: TowerType, upgrades: UpgradeData): Float =
        when (type) {
            TowerType.PULSE -> type.fireRate * PULSE_B_RATE.getOrDefault(upgrades.pathB, 1f)
            TowerType.RAILGUN -> type.fireRate + RAILGUN_B_RATE.getOrDefault(upgrades.pathB, 0f)
            else -> type.fireRate
        }

    /** Nova A: splash radius bonus. */
    fun effectiveSplashRadius(type: TowerType, upgrades: UpgradeData): Float =
        if (type == TowerType.NOVA_CANNON)
            NOVA_A_SPLASH.getOrDefault(upgrades.pathA, GameConstants.SPLASH_RADIUS)
        else GameConstants.SPLASH_RADIUS

    /** Cryo A: slow multiplier (lower = stronger). T3 = freeze. */
    fun effectiveSlowMultiplier(type: TowerType, upgrades: UpgradeData): Float =
        if (type == TowerType.CRYO)
            CRYO_A_SLOW.getOrDefault(upgrades.pathA, GameConstants.CRYO_SLOW_MULTIPLIER)
        else GameConstants.CRYO_SLOW_MULTIPLIER

    /** Cryo B (Permafrost): slow linger duration after leaving aura range. */
    fun effectiveLingerDuration(type: TowerType, upgrades: UpgradeData): Float =
        if (type == TowerType.CRYO)
            GameConstants.CRYO_BASE_LINGER_SEC + CRYO_B_LINGER.getOrDefault(upgrades.pathB, 0f)
        else GameConstants.CRYO_BASE_LINGER_SEC

    /** Extra targets (legacy — kept for Nova splash; Cryo no longer uses). */
    fun extraTargets(type: TowerType, upgrades: UpgradeData): Int = 0

    /** Beacon A (Vault): income per wave. Base 8 + path A bonuses. */
    fun effectiveIncome(type: TowerType, upgrades: UpgradeData): Int =
        if (type == TowerType.BEACON)
            8 + BEACON_A_INCOME.getOrDefault(upgrades.pathA, 0)
        else 0

    /** Beacon B (Amplifier): fire rate aura bonus. Base 0.05 + path B bonuses. */
    fun effectiveAuraBonus(type: TowerType, upgrades: UpgradeData): Float =
        if (type == TowerType.BEACON)
            0.05f + BEACON_B_AURA.getOrDefault(upgrades.pathB, 0f)
        else 0f

    // ── Lookup Tables ────────────────────────────────────────────────

    // Pulse Path A (Focused Fire): cumulative damage bonus at each tier
    private val PULSE_A_DMG = mapOf(1 to 4f, 2 to 10f, 3 to 20f)

    // Pulse Path B (Rapid Fire): rate multiplier at each tier
    private val PULSE_B_RATE = mapOf(1 to 1.3f, 2 to 1.5f, 3 to 2.0f)

    // Nova Path A (Megablast): total splash radius at each tier
    private val NOVA_A_SPLASH = mapOf(
        1 to GameConstants.SPLASH_RADIUS + 20f,  // 100
        2 to GameConstants.SPLASH_RADIUS + 40f,  // 120
        3 to 180f                                 // 180 total
    )

    // Nova Path B (Heavy Ordnance): cumulative damage bonus at each tier
    private val NOVA_B_DMG = mapOf(1 to 5f, 2 to 15f, 3 to 30f)

    // Cryo Path A (Deep Freeze): slow multiplier (lower = stronger)
    private val CRYO_A_SLOW = mapOf(1 to 0.55f, 2 to 0.40f, 3 to 0.0f)

    // Cryo Path B (Permafrost): cumulative linger duration bonus
    private val CRYO_B_LINGER = mapOf(1 to 0.5f, 2 to 1.5f, 3 to 3.5f)
    // Total linger: base 0.5 + bonus = T1: 1.0s, T2: 2.0s, T3: 4.0s

    // Railgun Path A (Overcharge): cumulative damage bonus at each tier
    private val RAILGUN_A_DMG = mapOf(1 to 15f, 2 to 40f, 3 to 70f)

    // Railgun Path B (Tracking): cumulative fire rate bonus at each tier
    private val RAILGUN_B_RATE = mapOf(1 to 0.10f, 2 to 0.25f, 3 to 0.50f)

    // Beacon Path A (Vault): cumulative income bonus at each tier
    private val BEACON_A_INCOME = mapOf(1 to 6, 2 to 16, 3 to 28)

    // Beacon Path B (Amplifier): cumulative aura bonus at each tier
    private val BEACON_B_AURA = mapOf(1 to 0.05f, 2 to 0.15f, 3 to 0.25f)
}
