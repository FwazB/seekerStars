package com.epochdefenders.ar

import kotlin.math.abs

/**
 * Exponential moving average smoother for sensor angles (degrees).
 * Handles angle wraparound at ±180° and suppresses sensor jitter via dead zone.
 */
class YawSmoother(
    private val alpha: Float = 0.15f,
    private val deadZoneDeg: Float = 0.3f
) {
    @Volatile
    private var smoothed: Float = Float.NaN

    val current: Float get() = if (smoothed.isNaN()) 0f else smoothed

    fun smooth(rawDeg: Float): Float {
        if (smoothed.isNaN()) {
            smoothed = rawDeg
            return rawDeg
        }

        var delta = rawDeg - smoothed
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        if (abs(delta) < deadZoneDeg) return smoothed

        smoothed += alpha * delta

        if (smoothed > 180f) smoothed -= 360f
        if (smoothed < -180f) smoothed += 360f

        return smoothed
    }

    fun reset() {
        smoothed = Float.NaN
    }
}
