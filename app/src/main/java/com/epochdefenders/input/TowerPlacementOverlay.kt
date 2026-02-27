package com.epochdefenders.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

import com.epochdefenders.engine.GameConstants
import com.epochdefenders.engine.TowerData
import com.epochdefenders.engine.TowerType
import com.epochdefenders.engine.Upgrade

// --- Layout constants (not in engine — UI-only) ---

private const val GAME_WIDTH = 800f
private const val GAME_HEIGHT = 600f
private const val GRID_OFFSET_X = 16f
private const val GRID_OFFSET_Y = 48f
private const val FORGIVENESS_RADIUS_DP = 12f

// --- Tower display helpers ---

private fun TowerType.color(): Long = when (this) {
    TowerType.PULSE -> 0xFF0080FF
    TowerType.NOVA_CANNON -> 0xFFFF6600
    TowerType.CRYO -> 0xFF00FFFF
    TowerType.RAILGUN -> 0xFFFF4444
    TowerType.BEACON -> 0xFFFFD700
}

private fun TowerType.special(): String = when (this) {
    TowerType.PULSE -> ""
    TowerType.NOVA_CANNON -> "AoE"
    TowerType.CRYO -> "Aura Slow"
    TowerType.RAILGUN -> "Pierce"
    TowerType.BEACON -> "Aura"
}

// --- Upgrade path data (UI-only, static) ---

private data class TierInfo(val effect: String, val cost: Int)

private data class PathInfo(val name: String, val tiers: List<TierInfo>)

private fun TowerType.upgradePaths(): Pair<PathInfo, PathInfo> = when (this) {
    TowerType.PULSE -> Pair(
        PathInfo("Focused Fire", listOf(
            TierInfo("+4 dmg", 50),
            TierInfo("+6 dmg", 100),
            TierInfo("+10 dmg", 200)
        )),
        PathInfo("Rapid Fire", listOf(
            TierInfo("+30% rate", 40),
            TierInfo("+50% rate", 80),
            TierInfo("+100% rate", 160)
        ))
    )
    TowerType.NOVA_CANNON -> Pair(
        PathInfo("Megablast", listOf(
            TierInfo("+20px splash", 60),
            TierInfo("+40px splash", 125),
            TierInfo("180px AoE", 275)
        )),
        PathInfo("Heavy Ordnance", listOf(
            TierInfo("+5 dmg", 75),
            TierInfo("+10 dmg", 150),
            TierInfo("+15 dmg", 300)
        ))
    )
    TowerType.CRYO -> Pair(
        PathInfo("Deep Freeze", listOf(
            TierInfo("50% slow", 40),
            TierInfo("75% slow", 80),
            TierInfo("100% freeze", 200)
        )),
        PathInfo("Permafrost", listOf(
            TierInfo("+1s slow linger", 50),
            TierInfo("+2s slow linger", 100),
            TierInfo("+4s slow linger", 200)
        ))
    )
    TowerType.RAILGUN -> Pair(
        PathInfo("Overcharge", listOf(
            TierInfo("+15 dmg", 60),
            TierInfo("+25 dmg", 120),
            TierInfo("+30 dmg", 200)
        )),
        PathInfo("Tracking", listOf(
            TierInfo("+10% fire rate", 50),
            TierInfo("+25% fire rate", 100),
            TierInfo("+50% fire rate", 175)
        ))
    )
    TowerType.BEACON -> Pair(
        PathInfo("Vault", listOf(
            TierInfo("+6 income/wave", 75),
            TierInfo("+16 income/wave", 150),
            TierInfo("+28 income/wave", 250)
        )),
        PathInfo("Amplifier", listOf(
            TierInfo("+5% fire rate aura", 60),
            TierInfo("+15% fire rate aura", 125),
            TierInfo("+25% fire rate aura", 225)
        ))
    )
}

// --- Pending placement state ---

private data class PendingPlacement(
    val gridX: Int,
    val gridY: Int,
    val screenX: Float,
    val screenY: Float
)

// --- Upgrade panel state (separate remember per perf guidance) ---

private data class UpgradeTarget(
    val towerIndex: Int,
    val tower: TowerData,
    val screenX: Float,
    val screenY: Float,
    val pathATier: Int,
    val pathBTier: Int,
    val totalInvested: Int
)

/**
 * Tower selection panel + grid placement + upgrade panel overlay.
 *
 * Bottom panel shows 3 tower buttons with stats.
 * Tap grid to show confirmation popup, then confirm to place.
 * Tap placed tower (when no tower selected) to open upgrade panel.
 * Green/red highlights show buildable vs blocked cells.
 * Touch-down feedback, haptics, snap forgiveness for mobile UX.
 */
@Composable
fun TowerPlacementOverlay(
    gold: Int,
    selectedTowerType: TowerType?,
    onSelectTower: (TowerType) -> Unit,
    onPlaceTower: (gridX: Int, gridY: Int, TowerType) -> Unit,
    onCancelPlacement: () -> Unit,
    onUpgradeTower: (towerIndex: Int, path: Char) -> Unit,
    onSellTower: (towerIndex: Int) -> Unit,
    buildableGrid: Array<BooleanArray>,
    towers: List<TowerData>,
    pathATiers: List<Int>,
    pathBTiers: List<Int>,
    totalInvested: List<Int>,
    modifier: Modifier = Modifier
) {
    val gridSize = GameConstants.GRID_SIZE.toFloat()
    val gridCols = GameConstants.GRID_COLS
    val gridRows = GameConstants.GRID_ROWS
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val forgivenessRadiusPx = with(density) { FORGIVENESS_RADIUS_DP.dp.toPx() }

    var pending by remember { mutableStateOf<PendingPlacement?>(null) }
    var pressedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Upgrade panel state — separate from game state (perf: avoids recomposition cascade)
    var upgradeTarget by remember { mutableStateOf<UpgradeTarget?>(null) }

    // Close upgrade panel when a tower is selected for placement
    LaunchedEffect(selectedTowerType) {
        if (selectedTowerType != null) upgradeTarget = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Grid area — placement mode OR tower tap detection
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedTowerType, towers) {
                    val sx = size.width.toFloat() / GAME_WIDTH
                    val sy = size.height.toFloat() / GAME_HEIGHT

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        var gx = ((down.position.x / sx - GRID_OFFSET_X) / gridSize).toInt()
                        var gy = ((down.position.y / sy - GRID_OFFSET_Y) / gridSize).toInt()

                        // Snap forgiveness for placement mode
                        if (selectedTowerType != null && gx in -1..gridCols && gy in -1..gridRows) {
                            val snapped = snapToBuildable(
                                down.position.x, down.position.y,
                                sx, sy, gridSize, gridCols, gridRows,
                                forgivenessRadiusPx, buildableGrid
                            )
                            if (snapped != null) {
                                gx = snapped.first
                                gy = snapped.second
                            }
                        }

                        val inGrid = gx in 0 until gridCols && gy in 0 until gridRows

                        // Show press highlight (placement mode only)
                        if (selectedTowerType != null && inGrid) {
                            pressedCell = Pair(gx, gy)
                        }

                        // Wait for release
                        do {
                            val event = awaitPointerEvent()
                            if (selectedTowerType != null) {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null) {
                                    val mgx = ((pos.x / sx - GRID_OFFSET_X) / gridSize).toInt()
                                    val mgy = ((pos.y / sy - GRID_OFFSET_Y) / gridSize).toInt()
                                    pressedCell = if (mgx in 0 until gridCols && mgy in 0 until gridRows)
                                        Pair(mgx, mgy) else null
                                }
                            }
                        } while (event.type != PointerEventType.Release)

                        pressedCell = null

                        if (!inGrid) {
                            // Off-grid: clear pending/upgrade, do NOT cancel tower selection
                            pending = null
                            upgradeTarget = null
                            return@awaitEachGesture
                        }

                        if (selectedTowerType != null) {
                            // --- Placement mode ---
                            val canPlace = gy in buildableGrid.indices
                                    && gx in buildableGrid[gy].indices
                                    && buildableGrid[gy][gx]

                            if (canPlace) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val cx = (GRID_OFFSET_X + gx * gridSize + gridSize / 2f) * sx
                                val cy = (GRID_OFFSET_Y + gy * gridSize + gridSize / 2f) * sy
                                pending = PendingPlacement(gx, gy, cx, cy)
                                upgradeTarget = null
                            } else {
                                pending = null
                            }
                        } else {
                            // --- Tower tap detection (no tower selected) ---
                            val tappedIdx = towers.indexOfFirst { it.gridX == gx && it.gridY == gy }
                            if (tappedIdx >= 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val t = towers[tappedIdx]
                                val cx = (GRID_OFFSET_X + t.gridX * gridSize + gridSize / 2f) * sx
                                val cy = (GRID_OFFSET_Y + t.gridY * gridSize + gridSize / 2f) * sy
                                upgradeTarget = UpgradeTarget(
                                    towerIndex = tappedIdx,
                                    tower = t,
                                    screenX = cx,
                                    screenY = cy,
                                    pathATier = pathATiers.getOrElse(tappedIdx) { 0 },
                                    pathBTier = pathBTiers.getOrElse(tappedIdx) { 0 },
                                    totalInvested = totalInvested.getOrElse(tappedIdx) { t.type.cost }
                                )
                                pending = null
                            } else {
                                upgradeTarget = null
                            }
                        }
                    }
                }
        ) {
            val sx = size.width / GAME_WIDTH
            val sy = size.height / GAME_HEIGHT

            if (selectedTowerType != null) {
                val towerColor = Color(selectedTowerType.color())

                // Draw ALL grid cells with green/red highlights
                for (row in 0 until gridRows.coerceAtMost(buildableGrid.size)) {
                    for (col in 0 until gridCols.coerceAtMost(buildableGrid[row].size)) {
                        val px = (GRID_OFFSET_X + col * gridSize) * sx
                        val py = (GRID_OFFSET_Y + row * gridSize) * sy
                        val cellW = gridSize * sx
                        val cellH = gridSize * sy
                        val isBuildable = buildableGrid[row][col]
                        val isPressed = pressedCell?.first == col && pressedCell?.second == row

                        val bgAlpha = if (isPressed) 0.4f else 0.15f
                        drawRect(
                            color = if (isBuildable) Color(0xFF00FFFF).copy(alpha = bgAlpha)
                            else Color(0xFFFF0044).copy(alpha = 0.15f),
                            topLeft = Offset(px, py),
                            size = androidx.compose.ui.geometry.Size(cellW, cellH)
                        )

                        drawRect(
                            color = if (isBuildable) Color(0xFF00FFFF).copy(alpha = 0.5f) else Color(0xFFFF0044).copy(alpha = 0.3f),
                            topLeft = Offset(px, py),
                            size = androidx.compose.ui.geometry.Size(cellW, cellH),
                            style = Stroke(width = if (isPressed) 2f else 1f)
                        )

                        if (isBuildable) {
                            val cx = (GRID_OFFSET_X + col * gridSize + gridSize / 2f) * sx
                            val cy = (GRID_OFFSET_Y + row * gridSize + gridSize / 2f) * sy
                            drawCircle(
                                color = towerColor.copy(alpha = if (isPressed) 0.25f else 0.1f),
                                radius = gridSize / 2f * sx * 0.8f,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                // Highlight pending cell with range circle
                val p = pending
                if (p != null) {
                    val cx = (GRID_OFFSET_X + p.gridX * gridSize + gridSize / 2f) * sx
                    val cy = (GRID_OFFSET_Y + p.gridY * gridSize + gridSize / 2f) * sy

                    drawCircle(
                        color = towerColor.copy(alpha = 0.5f),
                        radius = 20f * sx,
                        center = Offset(cx, cy)
                    )

                    drawCircle(
                        color = towerColor.copy(alpha = 0.3f),
                        radius = selectedTowerType.range * sx,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f)
                    )
                }
            }

            // Draw range ring around upgrade-targeted tower
            val ut = upgradeTarget
            if (ut != null && selectedTowerType == null) {
                val tColor = Color(ut.tower.type.color())
                val cx = (GRID_OFFSET_X + ut.tower.gridX * gridSize + gridSize / 2f) * sx
                val cy = (GRID_OFFSET_Y + ut.tower.gridY * gridSize + gridSize / 2f) * sy

                drawCircle(
                    color = tColor.copy(alpha = 0.35f),
                    radius = ut.tower.type.range * sx,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = tColor.copy(alpha = 0.2f),
                    radius = 20f * sx,
                    center = Offset(cx, cy)
                )
            }
        }

        // --- Placement confirmation popup ---
        val p = pending
        if (p != null && selectedTowerType != null) {
            PlacementConfirmPopup(
                placement = p,
                towerType = selectedTowerType,
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlaceTower(p.gridX, p.gridY, selectedTowerType)
                    pending = null
                },
                onCancel = { pending = null }
            )
        }

        // --- Upgrade panel popup ---
        val ut = upgradeTarget
        if (ut != null && selectedTowerType == null) {
            UpgradePopup(
                target = ut,
                gold = gold,
                onUpgrade = { path ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUpgradeTower(ut.towerIndex, path)
                    upgradeTarget = null
                },
                onSell = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSellTower(ut.towerIndex)
                    upgradeTarget = null
                },
                onDismiss = { upgradeTarget = null }
            )
        }

        // --- Tower selection panel (bottom) ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xF00A0A2A))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            for (tower in TowerType.entries) {
                val canAfford = gold >= tower.cost
                val isSelected = selectedTowerType == tower
                val towerColor = Color(tower.color())

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) Color(0xFF1A1A3E) else Color(0xFF0A0A2A),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) towerColor else towerColor.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = canAfford) {
                            if (isSelected) {
                                pending = null
                                onCancelPlacement()
                            } else {
                                pending = null
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectTower(tower)
                            }
                        }
                        .padding(8.dp)
                        .then(if (!canAfford) Modifier.background(Color(0x44000000)) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(
                            color = if (canAfford) towerColor else towerColor.copy(alpha = 0.3f),
                            radius = size.minDimension / 2f
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tower.displayName,
                        color = if (canAfford) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "${tower.cost}g",
                        color = if (canAfford) Color(0xFFFFFF00) else Color(0xFF666600),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "DMG:${tower.damage.toInt()} RNG:${tower.range.toInt()}",
                        color = Color(0xFF00FFFF),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    val special = tower.special()
                    if (special.isNotEmpty()) {
                        Text(
                            text = special,
                            color = towerColor.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// --- Effective stat row ---

@Composable
private fun EffStatRow(label: String, value: String, baseValue: String) {
    val changed = value != baseValue
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: $value",
            color = Color.White,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        if (changed) {
            Text(
                text = "  ($baseValue\u2192$value)",
                color = Color(0xFF44FF44),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun EffStatRow(label: String, value: Int, baseValue: Int? = null, suffix: String = "") {
    val bonus = if (baseValue != null && value != baseValue) value - baseValue else null
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: $value$suffix",
            color = Color.White,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        if (bonus != null && bonus > 0) {
            Text(
                text = "  +$bonus",
                color = Color(0xFF44FF44),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun EffStatRow(label: String, value: Float, baseValue: Float, suffix: String = "") {
    val hasBonus = value != baseValue
    val display = if (suffix == "x") "%.1f".format(value) else value.toInt().toString()
    val bonusDisplay = if (suffix == "x") "+%.1f".format(value - baseValue) else "+${(value - baseValue).toInt()}"
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: $display$suffix",
            color = Color.White,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        if (hasBonus && value > baseValue) {
            Text(
                text = "  $bonusDisplay",
                color = Color(0xFF44FF44),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// --- Placement confirmation popup (extracted for clarity) ---

@Composable
private fun PlacementConfirmPopup(
    placement: PendingPlacement,
    towerType: TowerType,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val towerColor = Color(towerType.color())

    Box(
        modifier = Modifier
            .offset { IntOffset(
                (placement.screenX - 70.dp.toPx()).toInt(),
                (placement.screenY - 90.dp.toPx()).toInt()
            ) }
    ) {
        Column(
            modifier = Modifier
                .width(140.dp)
                .background(Color(0xEE0A0A2A), RoundedCornerShape(8.dp))
                .border(1.dp, towerColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = towerType.displayName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${towerType.cost}g",
                color = Color(0xFFFFFF00),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF00FF44), CircleShape)
                        .clickable { onConfirm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2713",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFFF0044), CircleShape)
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2715",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Upgrade popup ---

@Composable
private fun UpgradePopup(
    target: UpgradeTarget,
    gold: Int,
    onUpgrade: (path: Char) -> Unit,
    onSell: () -> Unit,
    onDismiss: () -> Unit
) {
    val type = target.tower.type
    val towerColor = Color(type.color())
    val (pathA, pathB) = type.upgradePaths()
    val tierA = target.pathATier
    val tierB = target.pathBTier
    val pathALocked = tierB >= 3
    val pathBLocked = tierA >= 3
    val sellRefund = (target.totalInvested * GameConstants.SELL_REFUND_RATE).toInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(
                (target.screenX - 120.dp.toPx()).toInt(),
                (target.screenY - 180.dp.toPx()).toInt()
            ) }
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .background(Color(0xEE0A0A2A), RoundedCornerShape(10.dp))
                .border(1.dp, towerColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: tower name + dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = type.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2715",
                        color = Color(0xFF446688),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Effective stats (with upgrade bonuses)
            val upgrades = target.tower.upgrades
            val effDmg = Upgrade.effectiveDamage(type, upgrades)
            val baseDmg = type.damage
            val effRate = Upgrade.effectiveFireRate(type, upgrades)
            val baseRate = type.fireRate

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // DMG + RATE + RNG (all towers)
                EffStatRow("DMG", effDmg.toInt(), baseDmg.toInt())
                EffStatRow("RATE", effRate, baseRate, suffix = "x")
                EffStatRow("RNG", type.range.toInt())

                // NOVA: splash radius
                if (type == TowerType.NOVA_CANNON) {
                    val effSplash = Upgrade.effectiveSplashRadius(type, upgrades)
                    EffStatRow("SPLASH", effSplash.toInt(), GameConstants.SPLASH_RADIUS.toInt())
                }

                // CRYO: slow % + linger duration
                if (type == TowerType.CRYO) {
                    val effSlow = Upgrade.effectiveSlowMultiplier(type, upgrades)
                    val baseSlow = GameConstants.CRYO_SLOW_MULTIPLIER
                    val effPct = ((1f - effSlow) * 100).toInt()
                    val basePct = ((1f - baseSlow) * 100).toInt()
                    EffStatRow("SLOW", effPct, basePct, suffix = "%")

                    val effLinger = Upgrade.effectiveLingerDuration(type, upgrades)
                    val baseLinger = GameConstants.CRYO_BASE_LINGER_SEC
                    if (effLinger > baseLinger) {
                        EffStatRow("LINGER", "%.1fs".format(effLinger), "%.1fs".format(baseLinger))
                    }
                }

                // BEACON: income + aura bonus
                if (type == TowerType.BEACON) {
                    val income = Upgrade.effectiveIncome(type, upgrades)
                    EffStatRow("INCOME", income, 8, suffix = "g")
                    val aura = Upgrade.effectiveAuraBonus(type, upgrades)
                    val auraPct = (aura * 100).toInt()
                    val basePct = 5
                    EffStatRow("AURA", auraPct, basePct, suffix = "%")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Two path columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Path A
                UpgradePathColumn(
                    pathInfo = pathA,
                    label = "A",
                    currentTier = tierA,
                    isLocked = pathALocked,
                    gold = gold,
                    towerColor = towerColor,
                    onUpgrade = { onUpgrade('A') },
                    modifier = Modifier.weight(1f)
                )

                // Path B
                UpgradePathColumn(
                    pathInfo = pathB,
                    label = "B",
                    currentTier = tierB,
                    isLocked = pathBLocked,
                    gold = gold,
                    towerColor = towerColor,
                    onUpgrade = { onUpgrade('B') },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sell button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFFFF0044).copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .clickable { onSell() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sell (${sellRefund}g)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun UpgradePathColumn(
    pathInfo: PathInfo,
    label: String,
    currentTier: Int,
    isLocked: Boolean,
    gold: Int,
    towerColor: Color,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxed = currentTier >= 3

    Column(
        modifier = modifier
            .background(Color(0xFF0A0A2A), RoundedCornerShape(6.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Path name
        Text(
            text = pathInfo.name,
            color = if (isLocked) Color(0xFF444466) else towerColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        // Tier pips
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(vertical = 3.dp)
        ) {
            for (i in 1..3) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (i <= currentTier) towerColor
                            else if (isLocked) Color(0xFF222244)
                            else Color(0xFF222244),
                            CircleShape
                        )
                )
            }
        }

        if (isLocked) {
            // Locked state
            Text(
                text = "\uD83D\uDD12",
                fontSize = 16.sp
            )
            Text(
                text = "LOCKED",
                color = Color(0xFF444466),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        } else if (maxed) {
            // Maxed state
            Text(
                text = "MAX",
                color = Color(0xFFFFFF00),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        } else {
            // Next upgrade info
            val nextTier = pathInfo.tiers[currentTier]
            Text(
                text = nextTier.effect,
                color = Color(0xFFCCCCCC),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            val canAfford = gold >= nextTier.cost
            // Upgrade button — 48dp min touch target
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 48.dp)
                    .background(
                        if (canAfford) Color(0xFF00FF44) else Color(0xFF222244),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(enabled = canAfford) { onUpgrade() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u2B06",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${nextTier.cost}g",
                        color = if (canAfford) Color(0xFFFFFF00) else Color(0xFF666600),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// --- Snap forgiveness: find nearest buildable cell within radius ---

private fun snapToBuildable(
    touchX: Float, touchY: Float,
    sx: Float, sy: Float, gridSize: Float,
    gridCols: Int, gridRows: Int,
    radiusPx: Float,
    buildableGrid: Array<BooleanArray>
): Pair<Int, Int>? {
    val rawGx = ((touchX / sx - GRID_OFFSET_X) / gridSize).toInt()
    val rawGy = ((touchY / sy - GRID_OFFSET_Y) / gridSize).toInt()

    // Check the tapped cell first
    if (rawGx in 0 until gridCols && rawGy in 0 until gridRows
        && rawGy in buildableGrid.indices && rawGx in buildableGrid[rawGy].indices
        && buildableGrid[rawGy][rawGx]
    ) {
        return Pair(rawGx, rawGy)
    }

    // Search adjacent cells for nearest buildable within forgiveness radius
    var bestDist = Float.MAX_VALUE
    var bestCell: Pair<Int, Int>? = null

    for (dy in -1..1) {
        for (dx in -1..1) {
            val nx = rawGx + dx
            val ny = rawGy + dy
            if (nx < 0 || nx >= gridCols || ny < 0 || ny >= gridRows) continue
            if (ny !in buildableGrid.indices || nx !in buildableGrid[ny].indices) continue
            if (!buildableGrid[ny][nx]) continue

            val cx = (GRID_OFFSET_X + nx * gridSize + gridSize / 2f) * sx
            val cy = (GRID_OFFSET_Y + ny * gridSize + gridSize / 2f) * sy
            val dist = sqrt((touchX - cx) * (touchX - cx) + (touchY - cy) * (touchY - cy))

            if (dist < bestDist && dist <= radiusPx + gridSize / 2f * sx) {
                bestDist = dist
                bestCell = Pair(nx, ny)
            }
        }
    }

    return bestCell
}
