package com.portalritual.ar

import kotlin.math.abs

/**
 * Exponential moving average smoother for camera yaw (degrees).
 * Handles angle wraparound at ±180° and suppresses sensor jitter via dead zone.
 */
class YawSmoother(
    private val alpha: Float = 0.2f,
    private val deadZoneDeg: Float = 0.3f
) {
    private var smoothed: Float = Float.NaN

    /** Current smoothed value, or 0 if no readings yet. */
    val current: Float get() = if (smoothed.isNaN()) 0f else smoothed

    /**
     * Feed a raw yaw reading and return the smoothed value.
     * First call initializes without smoothing.
     */
    fun smooth(rawDeg: Float): Float {
        if (smoothed.isNaN()) {
            smoothed = rawDeg
            return rawDeg
        }

        // Shortest angular distance (handles ±180° wraparound)
        var delta = rawDeg - smoothed
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        // Dead zone: ignore jitter below threshold
        if (abs(delta) < deadZoneDeg) return smoothed

        // Exponential moving average
        smoothed += alpha * delta

        // Normalize to [-180, 180]
        if (smoothed > 180f) smoothed -= 360f
        if (smoothed < -180f) smoothed += 360f

        return smoothed
    }

    /** Reset state. Call when tracking is regained after a loss. */
    fun reset() {
        smoothed = Float.NaN
    }
}
