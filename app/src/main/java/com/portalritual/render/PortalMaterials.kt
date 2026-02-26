package com.portalritual.render

import com.google.android.filament.MaterialInstance
import dev.romainguy.kotlin.math.Float4
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor

class PortalMaterials(materialLoader: MaterialLoader) {

    val frameMaterial: MaterialInstance = materialLoader.createColorInstance(
        RenderConstants.FRAME_COLOR,
        metallic = 0.3f,
        roughness = 0.5f
    )

    val ringMaterials: List<MaterialInstance> = RenderConstants.RING_COLORS.map { color ->
        materialLoader.createColorInstance(color, metallic = 0.4f, roughness = 0.4f)
    }

    val glowMaterial: MaterialInstance = materialLoader.createColorInstance(
        RenderConstants.GLOW_COLOR,
        metallic = 0f,
        roughness = 1f
    )

    val targetMarkerMaterials: List<MaterialInstance> = List(3) {
        materialLoader.createColorInstance(RenderConstants.TARGET_MARKER_COLOR)
    }

    val currentMarkerMaterial: MaterialInstance = materialLoader.createColorInstance(
        RenderConstants.CURRENT_MARKER_COLOR
    )

    fun updateRingColor(index: Int, color: Float4) {
        ringMaterials[index].setColor(color)
    }

    fun updateGlowAlpha(alpha: Float) {
        val c = RenderConstants.GLOW_COLOR
        glowMaterial.setColor(Float4(c.x, c.y, c.z, alpha))
    }

    fun updateFrameColor(color: Float4) {
        frameMaterial.setColor(color)
    }
}
