package com.portalritual.render

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.node.CylinderNode
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Factory for connection-line nodes. Each line is a thin cylinder
 * oriented between two 3D points.
 */
object LineGeometry {

    fun createLineNode(
        engine: Engine,
        materialInstance: MaterialInstance,
        from: Float3,
        to: Float3,
        radius: Float = RenderConstants.LINE_RADIUS
    ): CylinderNode {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val height = sqrt(dx * dx + dy * dy + dz * dz)

        val mid = Float3(
            (from.x + to.x) / 2f,
            (from.y + to.y) / 2f,
            (from.z + to.z) / 2f
        )

        return CylinderNode(
            engine = engine,
            radius = radius,
            height = height,
            materialInstance = materialInstance
        ).also {
            it.position = mid
            if (height > 0.0001f) {
                it.quaternion = rotationFromYTo(
                    Float3(dx / height, dy / height, dz / height)
                )
            }
        }
    }

    /**
     * Quaternion rotating the Y-axis (0,1,0) to the given unit direction.
     */
    private fun rotationFromYTo(direction: Float3): Quaternion {
        val d = direction.y // dot(Y, direction) = direction.y
        if (d > 0.9999f) return Quaternion()                        // identity
        if (d < -0.9999f) return Quaternion(1f, 0f, 0f, 0f)        // 180° around X

        // cross(Y, direction) = (-direction.z, 0, direction.x)
        val ax = -direction.z
        val az = direction.x
        val axisLen = sqrt(ax * ax + az * az)

        val angle = acos(d.coerceIn(-1f, 1f))
        val halfAngle = angle / 2f
        val s = sin(halfAngle) / axisLen

        return Quaternion(ax * s, 0f, az * s, cos(halfAngle))
    }
}
