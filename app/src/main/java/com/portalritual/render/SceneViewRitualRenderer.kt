package com.portalritual.render

import com.google.android.filament.Engine
import com.portalritual.ar.Pose3
import com.portalritual.engine.RitualPhase
import com.portalritual.engine.RitualState
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * MVP renderer for Portal Ritual using SceneView 2.3.3 (Filament).
 *
 * Integration: Manager needs to create this in MainActivity's PortalRitualScreen:
 *   val materialLoader = rememberMaterialLoader(engine)
 *   val renderer = remember { SceneViewRitualRenderer(engine, materialLoader, childNodes) }
 *
 * Then the existing `renderer.updateState(ritualState, anchorPose)` call works as-is.
 */
class SceneViewRitualRenderer(
    private val engine: Engine,
    materialLoader: MaterialLoader,
    private val childNodes: MutableList<Node>
) : RitualRenderer {

    private val materials = PortalMaterials(materialLoader)

    private var portalRoot: Node? = null
    private var frameNode: GeometryNode? = null
    private var ringNodes: List<GeometryNode> = emptyList()
    private var glowNode: GeometryNode? = null
    private var targetMarkerNodes: List<SphereNode> = emptyList()
    private var currentMarkerNode: SphereNode? = null

    private var initialized = false
    private var lastAnchorPose: Pose3? = null

    // ── RitualRenderer contract ──────────────────────────────────────

    override fun updateState(state: RitualState, anchorPose: Pose3?) {
        if (anchorPose == null) {
            portalRoot?.isVisible = false
            return
        }

        if (!initialized) {
            buildNodes()
            initialized = true
        }

        if (anchorPose != lastAnchorPose) {
            lastAnchorPose = anchorPose
        }

        applyPhaseVisuals(state)
    }

    override fun dispose() {
        portalRoot?.let { root ->
            childNodes.remove(root)
            root.destroy()
        }
        portalRoot = null
        frameNode = null
        ringNodes = emptyList()
        glowNode = null
        targetMarkerNodes = emptyList()
        currentMarkerNode = null
        initialized = false
        lastAnchorPose = null
    }

    // ── Node construction ────────────────────────────────────────────

    private fun buildNodes() {
        val root = Node(engine)

        // Portal frame
        val frameGeo = PortalGeometry.buildTorus(
            engine,
            RenderConstants.FRAME_MAJOR_RADIUS,
            RenderConstants.FRAME_MINOR_RADIUS
        )
        frameNode = GeometryNode(engine, frameGeo, materials.frameMaterial).also {
            root.addChildNode(it)
        }

        // Three rings
        ringNodes = List(3) { i ->
            val geo = PortalGeometry.buildTorus(
                engine,
                RenderConstants.RING_MAJOR_RADII[i],
                RenderConstants.RING_MINOR_RADIUS
            )
            GeometryNode(engine, geo, materials.ringMaterials[i]).also {
                root.addChildNode(it)
            }
        }

        // Glow quad (lies flat in XZ, slightly below the ring plane)
        val glowGeo = PortalGeometry.buildQuad(
            engine,
            RenderConstants.GLOW_QUAD_SIZE,
            RenderConstants.GLOW_QUAD_SIZE
        )
        glowNode = GeometryNode(engine, glowGeo, materials.glowMaterial).also {
            it.position = Float3(0f, RenderConstants.GLOW_QUAD_OFFSET_Y, 0f)
            root.addChildNode(it)
        }

        // Target angle markers (one white sphere per ring, on its circumference)
        targetMarkerNodes = List(3) { i ->
            SphereNode(
                engine = engine,
                radius = RenderConstants.MARKER_RADIUS,
                materialInstance = materials.targetMarkerMaterials[i]
            ).also {
                it.isVisible = false
                root.addChildNode(it)
            }
        }

        // Current angle marker (hot pink sphere on active ring)
        currentMarkerNode = SphereNode(
            engine = engine,
            radius = RenderConstants.MARKER_RADIUS,
            materialInstance = materials.currentMarkerMaterial
        ).also {
            it.isVisible = false
            root.addChildNode(it)
        }

        portalRoot = root
        childNodes.add(root)
    }

    // ── Per-frame visual updates ─────────────────────────────────────

    private fun applyPhaseVisuals(state: RitualState) {
        val root = portalRoot ?: return
        val pose = lastAnchorPose ?: return

        when (state.phase) {
            RitualPhase.IDLE -> {
                root.isVisible = false
                return
            }

            RitualPhase.MANIFESTING -> {
                root.isVisible = true
                positionAtPose(root, pose, glitchIntensity = 0f)
                materials.updateFrameColor(alphaOf(RenderConstants.FRAME_COLOR, RenderConstants.MANIFESTING_ALPHA))
                ringNodes.forEach { it.isVisible = false }
                targetMarkerNodes.forEach { it.isVisible = false }
                currentMarkerNode?.isVisible = false
                glowNode?.isVisible = true
                val pulse = RenderConstants.GLOW_MIN_ALPHA +
                    0.1f * (1f + sin(state.frameCount * 0.15f))
                materials.updateGlowAlpha(pulse)
                return
            }

            RitualPhase.COLLAPSED -> {
                root.isVisible = true
                positionAtPose(root, pose, glitchIntensity = 1f)
                val flicker = 0.3f + 0.5f * Random.nextFloat()
                materials.updateFrameColor(alphaOf(RenderConstants.FRAME_COLOR, flicker))
                ringNodes.forEach { it.isVisible = true }
                targetMarkerNodes.forEach { it.isVisible = false }
                currentMarkerNode?.isVisible = false
                glowNode?.isVisible = true
                materials.updateGlowAlpha(RenderConstants.GLOW_MIN_ALPHA)
                updateRingRotations(state)
                updateRingColors(state)
                return
            }

            else -> {
                // ALIGN_1/2/3, TRACE_RUNE, STABILIZED, RESULTS
                root.isVisible = true
            }
        }

        // Position with glitch jitter
        positionAtPose(root, pose, state.glitchIntensity)

        // Frame: full opacity
        materials.updateFrameColor(RenderConstants.FRAME_COLOR)

        // Rings
        ringNodes.forEach { it.isVisible = true }
        updateRingRotations(state)
        updateRingColors(state)

        // Alignment markers (target dots + current angle indicator)
        updateMarkers(state)

        // Glow: brighter when stable, dimmer when glitchy
        glowNode?.isVisible = true
        val glowAlpha = RenderConstants.GLOW_MIN_ALPHA +
            (RenderConstants.GLOW_MAX_ALPHA - RenderConstants.GLOW_MIN_ALPHA) *
            (1f - state.glitchIntensity)
        materials.updateGlowAlpha(glowAlpha)
    }

    private fun updateRingRotations(state: RitualState) {
        for (i in 0..2) {
            val ring = state.rings[i]
            val angleDeg = when {
                ring.locked -> ring.targetAngleDeg
                state.phase.activeRingIndex == i -> state.currentCombinedAngleDeg
                else -> ring.targetAngleDeg
            }
            ringNodes[i].quaternion = axisAngleY(angleDeg)
        }
    }

    private fun updateRingColors(state: RitualState) {
        for (i in 0..2) {
            val ring = state.rings[i]
            val color = when {
                ring.locked -> RenderConstants.RING_LOCKED_COLOR
                state.phase.activeRingIndex == i && ring.lockTimer > 0f -> {
                    val t = (ring.lockTimer / RenderConstants.LOCK_DURATION_SEC).coerceIn(0f, 1f)
                    lerpColor(RenderConstants.RING_COLORS[i], RenderConstants.RING_LOCKED_COLOR, t)
                }
                else -> RenderConstants.RING_COLORS[i]
            }
            materials.updateRingColor(i, color)
        }
    }

    private fun updateMarkers(state: RitualState) {
        val isAligning = state.phase.isAlignment

        // Target markers: show on each ring during alignment+ phases
        for (i in 0..2) {
            val marker = targetMarkerNodes.getOrNull(i) ?: continue
            val showTarget = isAligning ||
                state.phase == RitualPhase.TRACE_RUNE ||
                state.phase == RitualPhase.STABILIZED ||
                state.phase == RitualPhase.RESULTS
            marker.isVisible = showTarget
            if (showTarget) {
                marker.position = angleToPosition(
                    state.rings[i].targetAngleDeg,
                    RenderConstants.RING_MAJOR_RADII[i]
                )
            }
        }

        // Current angle marker: only on active ring during alignment
        val curMarker = currentMarkerNode ?: return
        if (isAligning) {
            val activeIdx = state.phase.activeRingIndex
            curMarker.isVisible = true
            curMarker.position = angleToPosition(
                state.currentCombinedAngleDeg,
                RenderConstants.RING_MAJOR_RADII[activeIdx]
            )
        } else {
            curMarker.isVisible = false
        }
    }

    private fun positionAtPose(root: Node, pose: Pose3, glitchIntensity: Float) {
        var x = pose.tx
        var y = pose.ty
        var z = pose.tz

        if (glitchIntensity > 0.05f) {
            val jitter = RenderConstants.MAX_JITTER_METERS * glitchIntensity
            x += (Random.nextFloat() - 0.5f) * 2f * jitter
            y += (Random.nextFloat() - 0.5f) * 2f * jitter
            z += (Random.nextFloat() - 0.5f) * 2f * jitter
        }

        root.position = Float3(x, y, z)
        root.quaternion = Quaternion(pose.qx, pose.qy, pose.qz, pose.qw)
    }

    // ── Helpers ──────────────────────────────────────────────────────

    companion object {
        private fun axisAngleY(degrees: Float): Quaternion {
            val halfRad = Math.toRadians(degrees.toDouble()).toFloat() / 2f
            return Quaternion(0f, sin(halfRad), 0f, cos(halfRad))
        }

        private fun alphaOf(color: Float4, alpha: Float): Float4 =
            Float4(color.x, color.y, color.z, alpha)

        private fun angleToPosition(degrees: Float, radius: Float): Float3 {
            val rad = Math.toRadians(degrees.toDouble()).toFloat()
            return Float3(radius * cos(rad), 0f, radius * sin(rad))
        }

        private fun lerpColor(a: Float4, b: Float4, t: Float): Float4 {
            val ct = t.coerceIn(0f, 1f)
            return Float4(
                a.x + (b.x - a.x) * ct,
                a.y + (b.y - a.y) * ct,
                a.z + (b.z - a.z) * ct,
                a.w + (b.w - a.w) * ct
            )
        }
    }
}
