package com.portalritual.render

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.portalritual.engine.Star
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.node.SphereNode

/**
 * Factory for star nodes. Each star is a small emissive sphere.
 * Normalized 2D star coords (0..1) are mapped to local 3D space
 * (the constellation root positions them at depth).
 */
object StarGeometry {

    fun createStarNode(
        engine: Engine,
        materialInstance: MaterialInstance,
        star: Star,
        radius: Float = RenderConstants.STAR_RADIUS
    ): SphereNode {
        return SphereNode(
            engine = engine,
            radius = radius,
            materialInstance = materialInstance
        ).also {
            it.position = starToWorld(star)
        }
    }

    /**
     * Map normalized (0..1) star coords to local 3D position.
     * x: [0,1] → [-SPREAD_X, +SPREAD_X]
     * y: [0,1] → [+SPREAD_Y, -SPREAD_Y]  (flip: 0 = top)
     * z: 0 (depth handled by constellation root node)
     */
    fun starToWorld(star: Star): Float3 {
        val x = (star.x - 0.5f) * 2f * RenderConstants.CONSTELLATION_SPREAD_X
        val y = (0.5f - star.y) * 2f * RenderConstants.CONSTELLATION_SPREAD_Y
        return Float3(x, y, 0f)
    }
}
