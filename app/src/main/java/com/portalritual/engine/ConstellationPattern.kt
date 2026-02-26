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
    val requiredConnections: List<Connection>,
    val timeLimitSec: Float = 30f
)

object ConstellationPatterns {

    /** Triangle — 3 stars, 3 connections */
    val TRIANGLE = ConstellationPattern(
        stars = listOf(
            Star(0, 0.5f, 0.25f),
            Star(1, 0.25f, 0.75f),
            Star(2, 0.75f, 0.75f)
        ),
        requiredConnections = listOf(
            Connection(0, 1),
            Connection(1, 2),
            Connection(0, 2)
        ),
        timeLimitSec = 20f
    )

    /** Big Dipper — 7 stars, 6 connections (chain) */
    val BIG_DIPPER = ConstellationPattern(
        stars = listOf(
            Star(0, 0.15f, 0.55f),
            Star(1, 0.25f, 0.45f),
            Star(2, 0.38f, 0.40f),
            Star(3, 0.50f, 0.45f),
            Star(4, 0.55f, 0.60f),
            Star(5, 0.70f, 0.62f),
            Star(6, 0.72f, 0.48f)
        ),
        requiredConnections = listOf(
            Connection(0, 1),
            Connection(1, 2),
            Connection(2, 3),
            Connection(3, 4),
            Connection(4, 5),
            Connection(5, 6)
        ),
        timeLimitSec = 30f
    )

    /** Pentagram — 5 stars, 5 connections (star shape) */
    val PENTAGRAM: ConstellationPattern = run {
        val cx = 0.5f
        val cy = 0.5f
        val r = 0.3f
        val angleOffset = -Math.PI.toFloat() / 2f // top
        val stars = (0 until 5).map { i ->
            val angle = angleOffset + (2f * Math.PI.toFloat() * i / 5f)
            Star(i, cx + r * kotlin.math.cos(angle), cy + r * kotlin.math.sin(angle))
        }
        // Star shape: connect every other vertex (0→2, 2→4, 4→1, 1→3, 3→0)
        val connections = listOf(
            Connection(0, 2),
            Connection(2, 4),
            Connection(4, 1),
            Connection(1, 3),
            Connection(3, 0)
        )
        ConstellationPattern(stars, connections, timeLimitSec = 25f)
    }

    val ALL = listOf(TRIANGLE, BIG_DIPPER, PENTAGRAM)
}
