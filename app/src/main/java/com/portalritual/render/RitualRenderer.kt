package com.portalritual.render

import com.portalritual.ar.Pose3
import com.portalritual.engine.RitualState

/**
 * Renderer interface: consumes RitualState + anchor pose, draws 3D content.
 * Contract for ariel (Renderer worker).
 */
interface RitualRenderer {
    fun updateState(state: RitualState, anchorPose: Pose3?)
    fun dispose() {}
}

class StubRitualRenderer : RitualRenderer {
    override fun updateState(state: RitualState, anchorPose: Pose3?) {
        // No-op stub — ariel will implement real 3D rendering
    }
}
