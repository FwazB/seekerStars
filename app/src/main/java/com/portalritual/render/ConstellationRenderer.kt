package com.portalritual.render

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.portalritual.engine.ConstellationPhase
import com.portalritual.engine.ConstellationState
import com.portalritual.engine.Connection
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.sin

/**
 * Renderer interface for the constellation-linking mechanic.
 * Consumes ConstellationState from the engine and draws stars + connection lines.
 */
interface ConstellationRenderer {
    fun updateState(
        state: ConstellationState,
        parallaxOffset: Pair<Float, Float> = Pair(0f, 0f)
    )
    fun dispose() {}
}

/**
 * SceneView 2.3.3 implementation of [ConstellationRenderer].
 *
 * Integration (Manager wires this in MainActivity's PortalRitualScreen):
 *   val materialLoader = rememberMaterialLoader(engine)
 *   val renderer = remember { SceneViewConstellationRenderer(engine, materialLoader, childNodes) }
 *
 * Then call each frame:
 *   renderer.updateState(constellationState, sensorRuntime.parallaxOffset())
 */
class SceneViewConstellationRenderer(
    private val engine: Engine,
    private val materialLoader: MaterialLoader,
    private val childNodes: MutableList<Node>
) : ConstellationRenderer {

    private var root: Node? = null
    private var starNodes: Map<Int, SphereNode> = emptyMap()
    private var lineNodes: MutableMap<Connection, CylinderNode> = mutableMapOf()

    // Per-star materials for independent flicker
    private var starMaterials: Map<Int, MaterialInstance> = emptyMap()
    private var lineMaterial: MaterialInstance? = null

    private var initialized = false
    private var currentPatternStars: Int = -1

    // ── ConstellationRenderer contract ─────────────────────────────────

    override fun updateState(
        state: ConstellationState,
        parallaxOffset: Pair<Float, Float>
    ) {
        if (state.phase == ConstellationPhase.IDLE) {
            root?.isVisible = false
            return
        }

        if (!initialized || state.pattern.stars.size != currentPatternStars) {
            buildNodes(state)
            initialized = true
            currentPatternStars = state.pattern.stars.size
        }

        root?.isVisible = true
        applyParallax(parallaxOffset)
        applyPhaseVisuals(state)
    }

    override fun dispose() {
        root?.let { r ->
            childNodes.remove(r)
            r.destroy()
        }
        root = null
        starNodes = emptyMap()
        lineNodes.clear()
        starMaterials = emptyMap()
        lineMaterial = null
        initialized = false
        currentPatternStars = -1
    }

    // ── Node construction ──────────────────────────────────────────────

    private fun buildNodes(state: ConstellationState) {
        dispose()

        val r = Node(engine).apply {
            position = Float3(
                0f,
                RenderConstants.CONSTELLATION_Y,
                RenderConstants.CONSTELLATION_Z
            )
        }

        val lMat = materialLoader.createColorInstance(RenderConstants.LINE_CONNECTED_COLOR)
        lineMaterial = lMat

        // Per-star MaterialInstance so each star can flicker independently
        val nodes = mutableMapOf<Int, SphereNode>()
        val mats = mutableMapOf<Int, MaterialInstance>()
        for (star in state.pattern.stars) {
            val mat = materialLoader.createColorInstance(RenderConstants.STAR_COLOR)
            val node = StarGeometry.createStarNode(engine, mat, star)
            r.addChildNode(node)
            nodes[star.id] = node
            mats[star.id] = mat
        }
        starNodes = nodes
        starMaterials = mats

        root = r
        childNodes.add(r)
    }

    // ── Parallax ───────────────────────────────────────────────────────

    private fun applyParallax(offset: Pair<Float, Float>) {
        // Uniform shift applied to root — moves all children (stars + lines)
        // together so there's no line drift.
        val px = offset.first * RenderConstants.CONSTELLATION_SPREAD_X * 2f
        val py = offset.second * RenderConstants.CONSTELLATION_SPREAD_Y * 2f
        root?.position = Float3(
            px,
            RenderConstants.CONSTELLATION_Y + py,
            RenderConstants.CONSTELLATION_Z
        )
    }

    // ── Per-frame visual updates ───────────────────────────────────────

    private fun applyPhaseVisuals(state: ConstellationState) {
        when (state.phase) {
            ConstellationPhase.CONSTELLATION_ACTIVE -> {
                animateStarFlicker(state.frameCount)
                resetStarScale()
                syncConnectionLines(state)
                resetLineColor()
            }

            ConstellationPhase.CONSTELLATION_COMPLETE -> {
                animateCompletion(state)
                syncConnectionLines(state)
            }

            ConstellationPhase.TRACE_RUNE,
            ConstellationPhase.RESULTS -> {
                setAllStarAlpha(1.0f)
                resetStarScale()
                syncConnectionLines(state)
                resetLineColor()
            }

            else -> {}
        }
    }

    /**
     * Per-star flicker: each star pulses at a slightly different phase
     * with a small pseudo-random jitter, so they feel like real stars.
     */
    private fun animateStarFlicker(frameCount: Long) {
        val c = RenderConstants.STAR_COLOR
        for ((id, mat) in starMaterials) {
            val phaseOffset = id * 1.7f
            val t = sin(frameCount * RenderConstants.STAR_PULSE_SPEED + phaseOffset)
            // Tiny pseudo-random flicker on top of the sine
            val flicker = ((id * 31 + frameCount) % 7).toFloat() / 70f
            val alpha = (RenderConstants.STAR_PULSE_MIN_ALPHA +
                (RenderConstants.STAR_PULSE_MAX_ALPHA - RenderConstants.STAR_PULSE_MIN_ALPHA) *
                (0.5f + 0.5f * t) - flicker)
                .coerceIn(RenderConstants.STAR_PULSE_MIN_ALPHA, RenderConstants.STAR_PULSE_MAX_ALPHA)
            mat.setColor(Float4(c.x, c.y, c.z, alpha))
        }
    }

    private fun setAllStarAlpha(alpha: Float) {
        val c = RenderConstants.STAR_COLOR
        for ((_, mat) in starMaterials) {
            mat.setColor(Float4(c.x, c.y, c.z, alpha))
        }
    }

    private fun resetStarScale() {
        for ((_, node) in starNodes) {
            node.scale = Float3(1f, 1f, 1f)
        }
    }

    private fun resetLineColor() {
        lineMaterial?.setColor(RenderConstants.LINE_CONNECTED_COLOR)
    }

    private fun syncConnectionLines(state: ConstellationState) {
        val starMap = state.pattern.stars.associateBy { it.id }

        for (conn in state.completedConnections) {
            val normalized = conn.normalized()
            if (normalized in lineNodes) continue

            val fromStar = starMap[normalized.fromId] ?: continue
            val toStar = starMap[normalized.toId] ?: continue

            val from3D = StarGeometry.starToWorld(fromStar)
            val to3D = StarGeometry.starToWorld(toStar)

            val lMat = lineMaterial ?: continue
            val lineNode = LineGeometry.createLineNode(engine, lMat, from3D, to3D)
            root?.addChildNode(lineNode)
            lineNodes[normalized] = lineNode
        }
    }

    private fun animateCompletion(state: ConstellationState) {
        val t = state.completionTimer
        val beat = sin(t * RenderConstants.COMPLETION_PULSE_SPEED * 2f * Math.PI.toFloat())
        val intensity = 0.7f + 0.3f * (0.5f + 0.5f * beat)

        // Pulse all stars in sync (uniform flare during completion)
        val c = RenderConstants.STAR_COLOR
        for ((_, mat) in starMaterials) {
            mat.setColor(Float4(c.x * intensity, c.y * intensity, c.z * intensity, 1.0f))
        }

        // Pulse line brightness
        val lc = RenderConstants.LINE_CONNECTED_COLOR
        lineMaterial?.setColor(
            Float4(lc.x * intensity, lc.y * intensity, lc.z * intensity, 1.0f)
        )

        // Flare stars (scale up/down with beat)
        val scale = 1.0f + 0.5f * (0.5f + 0.5f * beat)
        for ((_, node) in starNodes) {
            node.scale = Float3(scale, scale, scale)
        }
    }
}
