package com.portalritual.render

import dev.romainguy.kotlin.math.Float4

object RenderConstants {

    // Portal frame torus
    const val FRAME_MAJOR_RADIUS = 0.45f
    const val FRAME_MINOR_RADIUS = 0.015f

    // Ring tori (outer → inner)
    val RING_MAJOR_RADII = floatArrayOf(0.40f, 0.32f, 0.24f)
    const val RING_MINOR_RADIUS = 0.008f

    // Torus tessellation
    const val TORUS_MAJOR_SEGMENTS = 24
    const val TORUS_MINOR_SEGMENTS = 8

    // Glow quad
    const val GLOW_QUAD_SIZE = 0.9f
    const val GLOW_QUAD_OFFSET_Y = -0.002f

    // Colors (RGBA)
    val FRAME_COLOR = Float4(0.0f, 0.8f, 0.9f, 1.0f)
    val RING_COLORS = arrayOf(
        Float4(0.9f, 0.3f, 0.1f, 1.0f),
        Float4(0.1f, 0.9f, 0.4f, 1.0f),
        Float4(0.3f, 0.2f, 0.9f, 1.0f)
    )
    val RING_LOCKED_COLOR = Float4(1.0f, 0.95f, 0.3f, 1.0f)
    val GLOW_COLOR = Float4(0.2f, 0.6f, 1.0f, 0.4f)

    // Glitch jitter
    const val MAX_JITTER_METERS = 0.005f

    // Phase alphas
    const val MANIFESTING_ALPHA = 0.3f
    const val GLOW_MIN_ALPHA = 0.1f
    const val GLOW_MAX_ALPHA = 0.5f

    // Alignment markers
    const val MARKER_RADIUS = 0.018f
    val TARGET_MARKER_COLOR = Float4(1.0f, 1.0f, 1.0f, 1.0f)    // White
    val CURRENT_MARKER_COLOR = Float4(1.0f, 0.0f, 0.5f, 1.0f)   // Hot pink

    // Lock progress
    const val LOCK_DURATION_SEC = 1.0f

    // ── Constellation ──────────────────────────────────────────────────

    // Star appearance
    val STAR_COLOR = Float4(0.0f, 1.0f, 1.0f, 1.0f)           // Cyan
    const val STAR_RADIUS = 0.025f
    const val STAR_PULSE_SPEED = 0.12f                          // radians per frame
    const val STAR_PULSE_MIN_ALPHA = 0.6f
    const val STAR_PULSE_MAX_ALPHA = 1.0f

    // Connection lines
    val LINE_CONNECTED_COLOR = Float4(1.0f, 0.84f, 0.0f, 1.0f) // Gold
    const val LINE_RADIUS = 0.004f

    // Constellation placement (world space)
    const val CONSTELLATION_Z = -2.0f
    const val CONSTELLATION_Y = -0.3f
    const val CONSTELLATION_SPREAD_X = 1.0f                     // half-width in meters
    const val CONSTELLATION_SPREAD_Y = 0.7f                     // half-height in meters

    // Completion effect
    const val COMPLETION_PULSE_SPEED = 4.0f                     // radians per second
}
