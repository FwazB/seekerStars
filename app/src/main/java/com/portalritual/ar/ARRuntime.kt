package com.portalritual.ar

/**
 * Bridge between device sensors and pure-Kotlin types.
 * Post-pivot: sensor-based (no ARCore dependency).
 *
 * The engine package NEVER imports this — only the wiring layer uses it.
 */
interface ARRuntime {
    /** Start listening to sensors. Call from onResume. */
    fun start()
    /** Stop listening. Call from onPause. */
    fun stop()
    /** Current smoothed device yaw in degrees [-180, 180]. */
    val currentYawDeg: Float
    /**
     * Compute a placement pose at [distanceMeters] along the camera forward vector.
     * Replaces ARCore hit-testing for sensor-based AR.
     */
    fun placementPose(distanceMeters: Float = 2f): Pose3

    /**
     * Parallax offset based on device tilt relative to a baseline.
     * Returns (dx, dy) in normalized coords, clamped to ±0.05.
     * Applied to star positions for AR depth illusion.
     */
    fun parallaxOffset(): Pair<Float, Float>

    /**
     * Capture current yaw/pitch as the parallax baseline.
     * Call when a constellation phase starts.
     */
    fun resetBaseline()
}
