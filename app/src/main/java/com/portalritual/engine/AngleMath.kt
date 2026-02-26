package com.portalritual.engine

fun normalizeDeg(degrees: Float): Float {
    val result = degrees % 360f
    return if (result < 0f) result + 360f else result
}

fun shortestAngleDistance(from: Float, to: Float): Float {
    val diff = normalizeDeg(to - from)
    return if (diff > 180f) diff - 360f else diff
}

fun combineRotation(yawBaseline: Float, swipeOffset: Float): Float {
    return normalizeDeg(yawBaseline + swipeOffset)
}
