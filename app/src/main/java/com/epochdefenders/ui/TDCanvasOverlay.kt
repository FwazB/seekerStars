package com.epochdefenders.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import com.epochdefenders.engine.EnemyType
import com.epochdefenders.engine.TowerType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Game coordinate system: 800x600 logical pixels
private const val GAME_W = 800f
private const val GAME_H = 600f

// ── TRON Neon Palette ─────────────────────────────────────────────────
private val NeonCyan = Color(0xFF00FFFF)
private val ElectricBlue = Color(0xFF0080FF)
private val NeonOrange = Color(0xFFFF6600)
private val NeonRed = Color(0xFFFF0044)
private val NeonGreen = Color(0xFF00FF44)
private val NeonYellow = Color(0xFFFFFF00)
private val GridBg = Color(0xFF0A0A2A)

/**
 * Full-screen Compose Canvas overlay rendering the tower defense battlefield.
 * TRON neon aesthetic — all glow effects are extra drawCircle calls at low alpha.
 */
@Composable
fun TDCanvasOverlay(
    towers: List<Tower>,
    enemies: List<Enemy>,
    projectiles: List<Projectile>,
    gridState: GridState,
    selectedTower: TowerType?,
    placementPos: Offset?,
    effects: List<VisualEffect>,
    parallaxOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier
) {
    // Tier 3 pulse animation (alpha oscillates 0.3 → 1.0)
    val pulseTransition = rememberInfiniteTransition(label = "tierPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tierPulseAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val sx = size.width / GAME_W
        val sy = size.height / GAME_H

        // TRON dark overlay — darkens camera feed so neon elements pop
        drawRect(color = GridBg.copy(alpha = 0.8f), size = size)

        // Apply parallax offset — shifts entire game grid for AR depth illusion
        translate(left = parallaxOffset.x, top = parallaxOffset.y) {
            // 1. Grid
            drawGrid(gridState, sx, sy)

            // 2. Path
            drawPath(gridState.pathPoints, sx, sy)

            // 3. Tower range circles (behind towers)
            for (tower in towers) {
                if (tower.showRange) {
                    drawRangeCircle(tower, sx, sy)
                }
            }

            // 4. Placement preview
            if (selectedTower != null && placementPos != null) {
                drawPlacementPreview(placementPos, selectedTower, sx, sy)
            }

            // 5. Towers
            for (tower in towers) {
                drawTower(tower, sx, sy, pulseAlpha)
            }

            // 6. Enemies
            for (enemy in enemies) {
                drawEnemy(enemy, sx, sy)
            }

            // 7. Projectiles
            for (proj in projectiles) {
                drawProjectile(proj, sx, sy)
            }

            // 8. Visual effects (on top)
            for (effect in effects) {
                drawEffect(effect, sx, sy)
            }
        }
    }
}

// ── Grid ───────────────────────────────────────────────────────────────

private fun DrawScope.drawGrid(grid: GridState, sx: Float, sy: Float) {
    val cellW = grid.cellSize * sx
    val cellH = grid.cellSize * sy
    val ox = grid.offsetX * sx
    val oy = grid.offsetY * sy

    for (row in 0 until grid.rows) {
        for (col in 0 until grid.cols) {
            val x = ox + col * cellW
            val y = oy + row * cellH
            val cellSize = Size(cellW, cellH)

            if (grid.buildable.getOrNull(row)?.getOrNull(col) == true) {
                // Buildable cell: faint cyan fill + neon cyan border glow
                drawRect(
                    color = NeonCyan.copy(alpha = 0.03f),
                    topLeft = Offset(x, y),
                    size = cellSize
                )
                drawRect(
                    color = NeonCyan.copy(alpha = 0.15f),
                    topLeft = Offset(x, y),
                    size = cellSize,
                    style = Stroke(width = 1f)
                )
            } else {
                // Non-buildable: very faint grid outline
                drawRect(
                    color = NeonCyan.copy(alpha = 0.04f),
                    topLeft = Offset(x, y),
                    size = cellSize,
                    style = Stroke(width = 0.5f)
                )
            }
        }
    }
}

// ── Path ───────────────────────────────────────────────────────────────

private fun DrawScope.drawPath(points: List<Offset>, sx: Float, sy: Float) {
    if (points.size < 2) return

    // Dark base layer
    for (i in 0 until points.size - 1) {
        val from = Offset(points[i].x * sx, points[i].y * sy)
        val to = Offset(points[i + 1].x * sx, points[i + 1].y * sy)
        drawLine(
            color = GridBg,
            start = from,
            end = to,
            strokeWidth = 40f * sx
        )
    }

    // Neon orange edge glow (outer)
    for (i in 0 until points.size - 1) {
        val from = Offset(points[i].x * sx, points[i].y * sy)
        val to = Offset(points[i + 1].x * sx, points[i + 1].y * sy)
        drawLine(
            color = NeonOrange.copy(alpha = 0.15f),
            start = from,
            end = to,
            strokeWidth = 44f * sx
        )
    }

    // Neon orange edge lines (TRON light trail)
    for (i in 0 until points.size - 1) {
        val from = Offset(points[i].x * sx, points[i].y * sy)
        val to = Offset(points[i + 1].x * sx, points[i + 1].y * sy)
        // Left edge
        drawLine(
            color = NeonOrange.copy(alpha = 0.6f),
            start = from,
            end = to,
            strokeWidth = 2f * sx
        )
    }
}

// ── Towers ─────────────────────────────────────────────────────────────

private fun DrawScope.drawTower(tower: Tower, sx: Float, sy: Float, pulseAlpha: Float = 0.7f) {
    val cx = tower.x * sx
    val cy = tower.y * sy
    val color = tower.type.color()
    val glowColor = tower.type.glowColor()
    val tier = tower.tier

    // ── Cryo aura: semi-transparent slow zone (drawn behind tower body) ──
    if (tower.type == TowerType.CRYO) {
        val auraRadius = tower.type.range * sx
        // Soft fill — faint cyan disc showing the aura zone
        drawCircle(
            color = NeonCyan.copy(alpha = 0.1f),
            radius = auraRadius,
            center = Offset(cx, cy)
        )
        // Neon edge ring
        drawCircle(
            color = NeonCyan.copy(alpha = 0.2f),
            radius = auraRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
    }

    // Tier scaling: base size grows with tier
    val sizeBoost = when (tier) {
        2 -> 2f * sx
        3 -> 4f * sx
        else -> 0f
    }

    // ── Base glow (always present — TRON neon look) ──
    val baseGlowAlpha = when (tier) {
        0 -> 0.15f
        1 -> 0.25f
        2 -> 0.35f
        3 -> pulseAlpha * 0.4f  // oscillates 0.12–0.40
        else -> 0.15f
    }
    drawCircle(
        color = glowColor.copy(alpha = baseGlowAlpha),
        radius = 28f * sx + sizeBoost,
        center = Offset(cx, cy)
    )

    // ── Upgrade outer ring (tier 1+) ──
    if (tier >= 1) {
        val ringAlpha = when (tier) {
            1 -> 0.3f
            2 -> 0.5f
            3 -> pulseAlpha  // oscillates 0.3–1.0
            else -> 0f
        }
        drawCircle(
            color = glowColor.copy(alpha = ringAlpha),
            radius = 24f * sx + sizeBoost + 4f * sx,
            center = Offset(cx, cy),
            style = Stroke(width = 2f * sx)
        )
    }

    // ── Base: dark rounded rect ──
    val halfBase = 20f * sx + sizeBoost / 2f
    drawRoundRect(
        color = Color(0xFF111122),
        topLeft = Offset(cx - halfBase, cy - halfBase),
        size = Size(halfBase * 2f, halfBase * 2f),
        cornerRadius = CornerRadius(4f * sx)
    )

    // ── Colored inner ──
    val halfInner = 16f * sx + sizeBoost / 2f
    drawRoundRect(
        color = color.copy(alpha = 0.85f),
        topLeft = Offset(cx - halfInner, cy - halfInner),
        size = Size(halfInner * 2f, halfInner * 2f),
        cornerRadius = CornerRadius(4f * sx)
    )

    // ── Path tint overlay (A = warm orange, B = cool cyan) ──
    tower.activePath?.let { path ->
        val tintColor = when (path) {
            UpgradePath.A -> NeonOrange
            UpgradePath.B -> NeonCyan
        }
        drawRoundRect(
            color = tintColor.copy(alpha = 0.15f),
            topLeft = Offset(cx - halfInner, cy - halfInner),
            size = Size(halfInner * 2f, halfInner * 2f),
            cornerRadius = CornerRadius(4f * sx)
        )
    }

    // ── Turret: dark center circle ──
    drawCircle(
        color = Color(0xFF0A0A1A),
        radius = 10f * sx,
        center = Offset(cx, cy)
    )

    // ── Turret barrel (rotated line) — special cases for new tower types ──
    when (tower.type) {
        TowerType.BEACON -> {
            // Gold pulsing orb instead of barrel
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = pulseAlpha * 0.8f),
                radius = 8f * sx,
                center = Offset(cx, cy)
            )
            // Always-visible aura circle
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = 0.1f),
                radius = tower.type.range * sx,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = 0.25f),
                radius = tower.type.range * sx,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
        }
        TowerType.RAILGUN -> {
            // Longer barrel for sniper feel
            val barrelLen = 30f * sx  // 1.5× normal
            val bx = cx + cos(tower.turretAngle) * barrelLen
            val by = cy + sin(tower.turretAngle) * barrelLen
            drawLine(
                color = color,
                start = Offset(cx, cy),
                end = Offset(bx, by),
                strokeWidth = 4f * sx  // slightly thinner, sniper-like
            )
        }
        else -> {
            // Normal barrel (existing code)
            val barrelLen = 20f * sx
            val bx = cx + cos(tower.turretAngle) * barrelLen
            val by = cy + sin(tower.turretAngle) * barrelLen
            drawLine(
                color = color,
                start = Offset(cx, cy),
                end = Offset(bx, by),
                strokeWidth = 6f * sx
            )
        }
    }
}

private fun DrawScope.drawRangeCircle(tower: Tower, sx: Float, sy: Float) {
    val cx = tower.x * sx
    val cy = tower.y * sy
    val range = tower.type.range * sx
    val color = tower.type.glowColor()

    // Fill
    drawCircle(
        color = color.copy(alpha = 0.04f),
        radius = range,
        center = Offset(cx, cy)
    )
    // Neon dashed border
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = range,
        center = Offset(cx, cy),
        style = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )
    )
}

// ── Placement Preview ──────────────────────────────────────────────────

private fun DrawScope.drawPlacementPreview(pos: Offset, type: TowerType, sx: Float, sy: Float) {
    val cx = pos.x * sx
    val cy = pos.y * sy
    val color = type.color()
    val glowColor = type.glowColor()

    // Glow
    drawCircle(
        color = glowColor.copy(alpha = 0.2f),
        radius = 28f * sx,
        center = Offset(cx, cy)
    )
    // Preview circle
    drawCircle(
        color = color.copy(alpha = 0.6f),
        radius = 20f * sx,
        center = Offset(cx, cy)
    )
    // Range circle
    drawCircle(
        color = glowColor.copy(alpha = 0.3f),
        radius = type.range * sx,
        center = Offset(cx, cy),
        style = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )
    )
}

// ── Enemies ────────────────────────────────────────────────────────────

private fun DrawScope.drawEnemy(enemy: Enemy, sx: Float, sy: Float) {
    val cx = enemy.x * sx
    val cy = enemy.y * sy
    val size = enemy.type.size * sx
    val color = if (enemy.slowed) NeonCyan else enemy.type.color()
    val glowColor = if (enemy.slowed) NeonCyan else enemy.type.glowColor()

    // Outer neon glow
    drawCircle(
        color = glowColor.copy(alpha = 0.25f),
        radius = size + 6f * sx,
        center = Offset(cx, cy)
    )

    // Body
    drawCircle(
        color = color,
        radius = size,
        center = Offset(cx, cy)
    )

    // Inner highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.25f),
        radius = size / 3f,
        center = Offset(cx - size / 4f, cy - size / 4f)
    )

    // Boss: gold crown ring at 1.2× size
    if (enemy.type == EnemyType.BOSS) {
        drawCircle(
            color = NeonYellow,
            radius = size * 1.2f,
            center = Offset(cx, cy),
            style = Stroke(width = 3f * sx)
        )
        // Gold glow behind crown
        drawCircle(
            color = NeonYellow.copy(alpha = 0.15f),
            radius = size * 1.3f,
            center = Offset(cx, cy)
        )
    }

    // Shield ring (cyan/white, alpha proportional to remaining shield)
    if (enemy.shieldMax > 0f && enemy.shield > 0f) {
        val shieldPct = (enemy.shield / enemy.shieldMax).coerceIn(0f, 1f)
        val shieldRadius = size + 12f * sx
        // Outer glow
        drawCircle(
            color = NeonCyan.copy(alpha = 0.2f * shieldPct),
            radius = shieldRadius + 3f * sx,
            center = Offset(cx, cy)
        )
        // Shield ring
        drawCircle(
            color = Color.White.copy(alpha = 0.6f * shieldPct),
            radius = shieldRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f * sx)
        )
    }

    // Neon health bar
    drawHealthBar(cx, cy, size, enemy.health, enemy.maxHealth, sx, sy)
}

private fun DrawScope.drawHealthBar(
    cx: Float,
    cy: Float,
    enemySize: Float,
    health: Float,
    maxHealth: Float,
    sx: Float,
    sy: Float
) {
    val barWidth = enemySize * 2f
    val barHeight = 4f * sy
    val barY = cy - enemySize - 10f * sy
    val barX = cx - barWidth / 2f
    val healthPct = (health / maxHealth).coerceIn(0f, 1f)

    // Background
    drawRect(
        color = Color(0xFF0A0A2A).copy(alpha = 0.9f),
        topLeft = Offset(barX, barY),
        size = Size(barWidth, barHeight)
    )
    // Border
    drawRect(
        color = NeonCyan.copy(alpha = 0.2f),
        topLeft = Offset(barX, barY),
        size = Size(barWidth, barHeight),
        style = Stroke(width = 0.5f)
    )

    // Neon fill: green → yellow → red
    val fillColor = when {
        healthPct > 0.5f -> NeonGreen
        healthPct > 0.25f -> NeonYellow
        else -> NeonRed
    }
    if (healthPct > 0f) {
        drawRect(
            color = fillColor,
            topLeft = Offset(barX, barY),
            size = Size(barWidth * healthPct, barHeight)
        )
    }
}

// ── Projectiles ────────────────────────────────────────────────────────

private fun DrawScope.drawProjectile(proj: Projectile, sx: Float, sy: Float) {
    val cx = proj.x * sx
    val cy = proj.y * sy

    // Outer glow
    drawCircle(
        color = proj.color.copy(alpha = 0.2f),
        radius = 10f * sx,
        center = Offset(cx, cy)
    )

    // Glow ring
    drawCircle(
        color = proj.color.copy(alpha = 0.35f),
        radius = 8f * sx,
        center = Offset(cx, cy)
    )

    // Bright core
    drawCircle(
        color = proj.color,
        radius = 4f * sx,
        center = Offset(cx, cy)
    )

    // Hot center
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = 2f * sx,
        center = Offset(cx, cy)
    )
}

// ── Visual Effects ─────────────────────────────────────────────────────

private fun DrawScope.drawEffect(effect: VisualEffect, sx: Float, sy: Float) {
    val cx = effect.x * sx
    val cy = effect.y * sy
    val alpha = (1f - effect.progress).coerceIn(0f, 1f)

    when (effect) {
        is VisualEffect.Splash -> {
            // Neon expanding ring
            val radius = effect.radius * effect.progress * sx
            drawCircle(
                color = NeonOrange.copy(alpha = alpha * 0.3f),
                radius = radius + 4f * sx,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = NeonOrange.copy(alpha = alpha * 0.8f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )
        }

        is VisualEffect.Hit -> {
            // Cyan/white flash burst
            val scale = 1f + effect.progress
            drawCircle(
                color = NeonCyan.copy(alpha = alpha * 0.3f),
                radius = 14f * scale * sx,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.8f),
                radius = 8f * scale * sx,
                center = Offset(cx, cy)
            )
        }

        is VisualEffect.DeathParticles -> {
            // Neon particle burst — red/magenta
            val spread = 30f * effect.progress * sx
            val particleScale = 1f + effect.progress
            for (i in 0 until 8) {
                val angle = (i.toFloat() / 8f) * 2f * PI.toFloat()
                val px = cx + cos(angle) * spread
                val py = cy + sin(angle) * spread
                // Glow behind particle
                drawCircle(
                    color = NeonRed.copy(alpha = alpha * 0.3f),
                    radius = 6f * particleScale * sx,
                    center = Offset(px, py)
                )
                // Bright core
                drawCircle(
                    color = NeonRed.copy(alpha = alpha),
                    radius = 3f * particleScale * sx,
                    center = Offset(px, py)
                )
            }
        }

        is VisualEffect.GoldPopup -> {
            // Floating "+Xg" text rising upward, fading out over 1s
            val riseOffset = 40f * effect.progress * sy  // rise ~40px over lifetime
            val textSize = 16f * sx
            val paint = Paint().apply {
                color = android.graphics.Color.argb(
                    (alpha * 255).toInt(),
                    0x00, 0xFF, 0x44  // NeonGreen
                )
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            // Glow layer (slightly larger, lower alpha)
            val glowPaint = Paint().apply {
                color = android.graphics.Color.argb(
                    (alpha * 100).toInt(),
                    0x00, 0xFF, 0x44
                )
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(6f * sx, 0f, 0f, android.graphics.Color.argb(
                    (alpha * 180).toInt(), 0x00, 0xFF, 0x44
                ))
            }
            val text = "+${effect.amount}g"
            val drawY = cy - riseOffset
            drawContext.canvas.nativeCanvas.drawText(text, cx, drawY, glowPaint)
            drawContext.canvas.nativeCanvas.drawText(text, cx, drawY, paint)
        }

        is VisualEffect.ComboFlash -> {
            // Center-screen combo banner: "Nx COMBO +Xg!"
            val comboColor = when {
                effect.comboSize >= 10 -> NeonCyan
                effect.comboSize >= 5 -> NeonOrange
                else -> NeonYellow
            }
            val textSize = 28f * sx
            val argbColor = android.graphics.Color.argb(
                (alpha * 255).toInt(),
                (comboColor.red * 255).toInt(),
                (comboColor.green * 255).toInt(),
                (comboColor.blue * 255).toInt()
            )
            val paint = Paint().apply {
                color = argbColor
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            // Neon glow shadow behind text
            val glowPaint = Paint().apply {
                color = argbColor
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                setShadowLayer(10f * sx, 0f, 0f, argbColor)
            }
            val text = "${effect.comboSize}x COMBO +${effect.bonus}g!"
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            drawContext.canvas.nativeCanvas.drawText(text, centerX, centerY, glowPaint)
            drawContext.canvas.nativeCanvas.drawText(text, centerX, centerY, paint)
        }
    }
}
