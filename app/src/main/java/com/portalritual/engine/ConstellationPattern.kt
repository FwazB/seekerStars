package com.portalritual.engine

data class Star(val id: Int, val x: Float, val y: Float)

data class Connection(val fromId: Int, val toId: Int) {
    /** Normalize so fromId < toId (undirected edge). */
    fun normalized(): Connection =
        if (fromId <= toId) this else Connection(toId, fromId)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Connection) return false
        val a = normalized()
        val b = other.normalized()
        return a.fromId == b.fromId && a.toId == b.toId
    }

    override fun hashCode(): Int {
        val n = normalized()
        return 31 * n.fromId + n.toId
    }
}

data class ConstellationPattern(
    val stars: List<Star>,
    val timeLimitSec: Float = 30f
) {
    /** Sequential connections derived from star order. */
    val requiredConnections: List<Connection>
        get() = stars.zipWithNext { a, b -> Connection(a.id, b.id) }
}

object ConstellationPatterns {

    /** Triangle — 3 stars, 15s. Easy warmup. */
    val TRIANGLE = ConstellationPattern(
        stars = listOf(
            Star(0, 0.50f, 0.25f),  // 1: top
            Star(1, 0.25f, 0.70f),  // 2: bottom-left
            Star(2, 0.75f, 0.70f)   // 3: bottom-right
        ),
        timeLimitSec = 15f
    )

    /** Diamond — 4 stars, 18s. Trace the perimeter. */
    val DIAMOND = ConstellationPattern(
        stars = listOf(
            Star(0, 0.50f, 0.20f),  // 1: top
            Star(1, 0.75f, 0.50f),  // 2: right
            Star(2, 0.50f, 0.80f),  // 3: bottom
            Star(3, 0.25f, 0.50f)   // 4: left
        ),
        timeLimitSec = 18f
    )

    /** House — 5 stars, 20s. Roof peak then walls. */
    val HOUSE = ConstellationPattern(
        stars = listOf(
            Star(0, 0.50f, 0.20f),  // 1: roof peak
            Star(1, 0.75f, 0.40f),  // 2: top-right
            Star(2, 0.75f, 0.75f),  // 3: bottom-right
            Star(3, 0.25f, 0.75f),  // 4: bottom-left
            Star(4, 0.25f, 0.40f)   // 5: top-left
        ),
        timeLimitSec = 20f
    )

    /** Zigzag — 5 stars, 22s. Alternating left-right descent. */
    val ZIGZAG = ConstellationPattern(
        stars = listOf(
            Star(0, 0.20f, 0.25f),  // 1: top-left
            Star(1, 0.70f, 0.35f),  // 2: mid-right
            Star(2, 0.30f, 0.45f),  // 3: mid-left
            Star(3, 0.75f, 0.55f),  // 4: lower-right
            Star(4, 0.25f, 0.70f)   // 5: bottom-left
        ),
        timeLimitSec = 22f
    )

    /** Crown — 6 stars, 25s. Three peaks rising from left. */
    val CROWN = ConstellationPattern(
        stars = listOf(
            Star(0, 0.15f, 0.70f),  // 1: base-left
            Star(1, 0.30f, 0.30f),  // 2: peak 1
            Star(2, 0.45f, 0.60f),  // 3: valley 1
            Star(3, 0.55f, 0.25f),  // 4: peak 2 (tallest)
            Star(4, 0.70f, 0.60f),  // 5: valley 2
            Star(5, 0.85f, 0.30f)   // 6: peak 3
        ),
        timeLimitSec = 25f
    )

    /** Lightning — 6 stars, 25s. Jagged bolt top to bottom. */
    val LIGHTNING = ConstellationPattern(
        stars = listOf(
            Star(0, 0.45f, 0.15f),  // 1: top
            Star(1, 0.55f, 0.35f),  // 2: right jag
            Star(2, 0.40f, 0.45f),  // 3: left jag
            Star(3, 0.60f, 0.55f),  // 4: right jag
            Star(4, 0.35f, 0.65f),  // 5: left jag
            Star(5, 0.55f, 0.85f)   // 6: bottom
        ),
        timeLimitSec = 25f
    )

    /** Spiral — 7 stars, 30s. Expanding outward from center. */
    val SPIRAL = ConstellationPattern(
        stars = listOf(
            Star(0, 0.50f, 0.50f),  // 1: center
            Star(1, 0.58f, 0.40f),  // 2: NE close
            Star(2, 0.68f, 0.55f),  // 3: E
            Star(3, 0.55f, 0.68f),  // 4: SE
            Star(4, 0.38f, 0.62f),  // 5: SW
            Star(5, 0.30f, 0.42f),  // 6: W
            Star(6, 0.42f, 0.25f)   // 7: NW outer
        ),
        timeLimitSec = 30f
    )

    /** All patterns in difficulty order (easy → hard). Star count = difficulty. */
    val ALL = listOf(TRIANGLE, DIAMOND, HOUSE, ZIGZAG, CROWN, LIGHTNING, SPIRAL)
}
