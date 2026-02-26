package com.portalritual.render

import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.geometries.Geometry
import kotlin.math.cos
import kotlin.math.sin

object PortalGeometry {

    fun buildTorus(
        engine: Engine,
        majorRadius: Float,
        minorRadius: Float,
        majorSegments: Int = RenderConstants.TORUS_MAJOR_SEGMENTS,
        minorSegments: Int = RenderConstants.TORUS_MINOR_SEGMENTS
    ): Geometry {
        val vertices = mutableListOf<Geometry.Vertex>()
        val indices = mutableListOf<Int>()

        val twoPi = 2f * Math.PI.toFloat()

        for (i in 0..majorSegments) {
            val theta = twoPi * i / majorSegments
            val ct = cos(theta)
            val st = sin(theta)

            for (j in 0..minorSegments) {
                val phi = twoPi * j / minorSegments
                val cp = cos(phi)
                val sp = sin(phi)

                vertices.add(
                    Geometry.Vertex(
                        position = Float3(
                            (majorRadius + minorRadius * cp) * ct,
                            minorRadius * sp,
                            (majorRadius + minorRadius * cp) * st
                        ),
                        normal = Float3(cp * ct, sp, cp * st)
                    )
                )
            }
        }

        for (i in 0 until majorSegments) {
            for (j in 0 until minorSegments) {
                val a = i * (minorSegments + 1) + j
                val b = a + minorSegments + 1
                val c = a + 1
                val d = b + 1
                indices.addAll(listOf(a, b, c, c, b, d))
            }
        }

        return Geometry.Builder()
            .vertices(vertices)
            .indices(indices)
            .build(engine)
    }

    fun buildQuad(engine: Engine, width: Float, height: Float): Geometry {
        val hw = width / 2f
        val hh = height / 2f
        val up = Float3(0f, 1f, 0f)

        val vertices = listOf(
            Geometry.Vertex(position = Float3(-hw, 0f, -hh), normal = up),
            Geometry.Vertex(position = Float3(hw, 0f, -hh), normal = up),
            Geometry.Vertex(position = Float3(hw, 0f, hh), normal = up),
            Geometry.Vertex(position = Float3(-hw, 0f, hh), normal = up)
        )
        val indices = listOf(0, 1, 2, 0, 2, 3)

        return Geometry.Builder()
            .vertices(vertices)
            .indices(indices)
            .build(engine)
    }
}
