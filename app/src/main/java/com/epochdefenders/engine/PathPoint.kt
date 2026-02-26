package com.epochdefenders.engine

data class PathPoint(val x: Float, val y: Float)

/** Default S-curve path through a 12x9 grid (64px cells). */
object DefaultPath {
    val WAYPOINTS = listOf(
        PathPoint(0f, 160f),
        PathPoint(192f, 160f),
        PathPoint(192f, 416f),
        PathPoint(416f, 416f),
        PathPoint(416f, 96f),
        PathPoint(608f, 96f),
        PathPoint(608f, 352f),
        PathPoint(736f, 352f),
        PathPoint(800f, 352f)
    )
}
