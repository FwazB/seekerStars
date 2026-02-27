package com.epochdefenders

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.delay

import com.epochdefenders.ar.CameraPreview
import com.epochdefenders.ar.SensorRuntime
import com.epochdefenders.engine.DefaultPath
import com.epochdefenders.engine.FinalScore
import com.epochdefenders.engine.GameConstants
import com.epochdefenders.engine.GameEvent
import com.epochdefenders.engine.GameStateManager
import com.epochdefenders.engine.ProjectileData
import com.epochdefenders.engine.TDPhase
import com.epochdefenders.engine.TowerData
import com.epochdefenders.engine.TowerType
import com.epochdefenders.engine.Upgrade
import com.epochdefenders.input.TowerPlacementOverlay
import com.epochdefenders.solana.EpochInfo
import com.epochdefenders.solana.EpochService
import com.epochdefenders.solana.LeaderboardService
import com.epochdefenders.solana.WalletManager
import com.epochdefenders.ui.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EpochDefendersApp() }
    }
}

@Composable
private fun EpochDefendersApp() {
    var cameraGranted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }

    var gameState by remember { mutableStateOf(GameStateManager.newGame()) }
    var selectedTower by remember { mutableStateOf<TowerType?>(null) }
    var epochInfo by remember { mutableStateOf<EpochInfo?>(null) }
    var effects by remember { mutableStateOf(listOf<TimedEffect>()) }
    var parallax by remember { mutableStateOf(Offset.Zero) }
    val epochService = remember { EpochService.getInstance() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val sensorRuntime = remember { SensorRuntime(context) }
    val scope = rememberCoroutineScope()

    // Leaderboard + Wallet state
    var showLeaderboard by remember { mutableStateOf(false) }
    var lastFinalScore by remember { mutableStateOf<FinalScore?>(null) }
    val activity = context as? androidx.activity.ComponentActivity
    val walletManager = remember(activity) { activity?.let { WalletManager(it) } }
    val leaderboardService = remember { LeaderboardService.getInstance() }
    val walletAddressFlow = remember(walletManager) {
        walletManager?.walletAddress ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }
    val walletAddress by walletAddressFlow.collectAsState()
    val walletErrorFlow = remember(walletManager) {
        walletManager?.walletError ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }
    val walletError by walletErrorFlow.collectAsState()

    // Show wallet errors as Toast
    LaunchedEffect(walletError) {
        walletError?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    var leaderboardEntries by remember { mutableStateOf(listOf<com.epochdefenders.solana.LeaderboardEntry>()) }
    var leaderboardLoading by remember { mutableStateOf(false) }
    var leaderboardError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        sensorRuntime.start()
        onDispose { sensorRuntime.stop() }
    }

    // Game loop — vsync-aligned via withFrameNanos
    var lastFrameNanos by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val dt = if (lastFrameNanos == 0L) 0.016f
                         else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                lastFrameNanos = frameTimeNanos

                // Read parallax offset from sensor each frame
                val (dx, dy) = sensorRuntime.parallaxOffset()
                parallax = Offset(dx, dy)

                if (!gameState.isGameOver) {
                    // Epoch boss check (fire-once per threshold)
                    epochInfo?.let { info ->
                        val progress = epochService.getEpochProgress(info)
                        gameState = GameStateManager.checkEpochBoss(gameState, progress)
                    }
                    val updated = GameStateManager.update(gameState, dt)
                    effects = processEvents(updated.events, effects)
                    // Capture FinalScore from GameOver event
                    updated.events.filterIsInstance<GameEvent.GameOver>().firstOrNull()?.let {
                        lastFinalScore = it.finalScore
                    }
                    gameState = updated
                }
                effects = tickEffects(effects, dt)
            }
        }
    }

    // Epoch polling every 30s
    LaunchedEffect(Unit) {
        while (true) {
            epochInfo = epochService.getEpochInfo()
            delay(30_000L)
        }
    }

    // Derived UI state
    val buildableGrid = remember(gameState.occupiedCells) {
        Array(GameConstants.GRID_ROWS) { row ->
            BooleanArray(GameConstants.GRID_COLS) { col ->
                GameStateManager.isBuildable(gameState, col, row)
            }
        }
    }
    val gridState = remember(gameState.occupiedCells) { buildGridState(buildableGrid) }
    val pathATiers = remember(gameState.towers) {
        gameState.towers.map { it.upgrades.pathA }
    }
    val pathBTiers = remember(gameState.towers) {
        gameState.towers.map { it.upgrades.pathB }
    }
    val invested = remember(gameState.towers) {
        gameState.towers.map { Upgrade.totalInvested(it.type, it.upgrades) }
    }

    // Wave timing state for HUD
    val isWaiting = gameState.phase == TDPhase.WAITING
    val waveTimer = gameState.waveTimer
    val waveTimerMax = gameState.waveTimerMax

    val uiTowers = remember(gameState.towers) {
        gameState.towers.map { Tower(it.x, it.y, it.type, pathALevel = it.upgrades.pathA, pathBLevel = it.upgrades.pathB) }
    }
    val uiEnemies = remember(gameState.enemies) {
        gameState.enemies.filter { it.active }.map {
            Enemy(it.x, it.y, it.type, it.health, it.maxHealth, it.slowed, it.shield, it.shieldMax)
        }
    }
    val uiProjectiles = remember(gameState.projectiles) {
        gameState.projectiles.filter { it.active }.map {
            Projectile(it.x, it.y, projectileColor(it))
        }
    }

    val epochNum = (epochInfo?.epoch ?: 0L).toInt()
    val epochProg = epochInfo?.let { epochService.getEpochProgress(it) } ?: 0f
    val bossTime = epochInfo?.let {
        epochService.formatTimeRemaining(epochService.getTimeUntilNextEpoch(it))
    } ?: "--"

    if (showLeaderboard) {
        LeaderboardScreen(
            entries = leaderboardEntries,
            isLoading = leaderboardLoading,
            error = leaderboardError,
            walletAddress = walletAddress,
            playerScore = lastFinalScore?.score,
            playerWave = lastFinalScore?.waveReached,
            isSubmitting = isSubmitting,
            onConnect = {
                walletManager?.let { wm ->
                    scope.launch { wm.connect() }
                }
            },
            onDisconnect = { walletManager?.disconnect() },
            onSubmitScore = {
                val fs = lastFinalScore ?: return@LeaderboardScreen
                val playerName = walletAddress ?: "Player"
                scope.launch {
                    isSubmitting = true
                    leaderboardService.saveScore(context, playerName, fs.score, fs.waveReached)
                    isSubmitting = false
                    lastFinalScore = null // prevent duplicate submissions
                    // Refresh leaderboard after submission
                    leaderboardLoading = true
                    leaderboardEntries = leaderboardService.getLeaderboard(context)
                    leaderboardLoading = false
                }
            },
            onRefresh = {
                scope.launch {
                    leaderboardLoading = true
                    leaderboardError = null
                    try {
                        leaderboardEntries = leaderboardService.getLeaderboard(context)
                    } catch (e: Exception) {
                        leaderboardError = e.message ?: "Failed to load"
                    }
                    leaderboardLoading = false
                }
            },
            onBack = { showLeaderboard = false }
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            if (cameraGranted) {
                CameraPreview(lifecycleOwner, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0A1A)))
            }

            TDCanvasOverlay(
                uiTowers, uiEnemies, uiProjectiles, gridState,
                selectedTower, null, effects.map { it.visual() },
                parallaxOffset = parallax,
                modifier = Modifier.fillMaxSize()
            )
            TowerPlacementOverlay(
                gold = gameState.gold,
                selectedTowerType = selectedTower,
                onSelectTower = { selectedTower = it },
                onPlaceTower = { gx, gy, type ->
                    GameStateManager.placeTower(gameState, type, gx, gy)?.let {
                        gameState = it
                        selectedTower = null
                    }
                },
                onCancelPlacement = { selectedTower = null },
                onUpgradeTower = { idx, path ->
                    GameStateManager.upgradeTower(gameState, idx, path)?.let { gameState = it }
                },
                onSellTower = { idx ->
                    GameStateManager.sellTower(gameState, idx)?.let {
                        gameState = it; selectedTower = null
                    }
                },
                buildableGrid = buildableGrid,
                towers = gameState.towers,
                pathATiers = pathATiers,
                pathBTiers = pathBTiers,
                totalInvested = invested
            )

            TDHUD(
                gameState.gold, gameState.lives, gameState.waveNumber, gameState.score,
                epochNum, epochProg, bossTime, gameState.isBossWave, gameState.isGameOver,
                onRestart = {
                    gameState = GameStateManager.newGame()
                    selectedTower = null
                    effects = emptyList()
                    lastFinalScore = null
                },
                onLeaderboard = {
                    showLeaderboard = true
                    scope.launch {
                        leaderboardLoading = true
                        try {
                            leaderboardEntries = leaderboardService.getLeaderboard(context)
                        } catch (e: Exception) {
                            leaderboardError = e.message ?: "Failed to load"
                        }
                        leaderboardLoading = false
                    }
                },
                isWaiting = isWaiting,
                waveTimer = waveTimer,
                waveTimerMax = waveTimerMax,
                onSendWave = { gameState = GameStateManager.sendWave(gameState) },
                currentWave = gameState.waveNumber + 1
            )
        }
    }
}

// ── Visual Effects ──

private data class TimedEffect(
    val x: Float, val y: Float,
    val kind: Kind, val color: Color,
    val elapsed: Float = 0f, val duration: Float,
    val radius: Float = 60f,
    val amount: Int = 0,
    val comboSize: Int = 0
) {
    enum class Kind { SPLASH, HIT, DEATH, GOLD_POPUP, COMBO_FLASH }

    fun visual(): VisualEffect {
        val p = (elapsed / duration).coerceIn(0f, 1f)
        return when (kind) {
            Kind.SPLASH -> VisualEffect.Splash(x, y, p, radius, color)
            Kind.HIT -> VisualEffect.Hit(x, y, p, color)
            Kind.DEATH -> VisualEffect.DeathParticles(x, y, p, color)
            Kind.GOLD_POPUP -> VisualEffect.GoldPopup(x, y, p, amount)
            Kind.COMBO_FLASH -> VisualEffect.ComboFlash(x, y, p, amount, comboSize)
        }
    }
}

private fun processEvents(events: List<GameEvent>, current: List<TimedEffect>): List<TimedEffect> =
    current + events.flatMap { event ->
        when (event) {
            is GameEvent.ProjectileHit -> listOf(TimedEffect(
                event.x, event.y,
                if (event.isSplash) TimedEffect.Kind.SPLASH else TimedEffect.Kind.HIT,
                if (event.isSplash) Color(0xFFFFA500) else Color(0xFF4A9EFF),
                duration = if (event.isSplash) 0.2f else 0.15f
            ))
            is GameEvent.EnemyKilled -> listOf(
                TimedEffect(
                    event.x, event.y, TimedEffect.Kind.DEATH, Color(0xFFFF4444), duration = 0.3f
                ),
                TimedEffect(
                    event.x, event.y, TimedEffect.Kind.GOLD_POPUP, Color(0xFF00FF44),
                    duration = 1.0f, amount = event.reward
                )
            )
            is GameEvent.ComboBonus -> listOf(TimedEffect(
                event.x, event.y, TimedEffect.Kind.COMBO_FLASH, Color.White,
                duration = 1.5f, amount = event.bonus, comboSize = event.comboSize
            ))
            else -> emptyList()
        }
    }

private fun tickEffects(effects: List<TimedEffect>, dt: Float): List<TimedEffect> =
    effects.mapNotNull { e -> e.copy(elapsed = e.elapsed + dt).takeIf { it.elapsed < it.duration } }

private fun buildGridState(buildable: Array<BooleanArray>): GridState = GridState(
    cols = GameConstants.GRID_COLS,
    rows = GameConstants.GRID_ROWS,
    cellSize = GameConstants.GRID_SIZE,
    buildable = buildable.map { it.toList() },
    pathPoints = DefaultPath.WAYPOINTS.map { Offset(it.x, it.y) }
)

private fun projectileColor(proj: ProjectileData): Color = when {
    proj.isSlowing -> Color(0xFF00FFFF)
    proj.isSplash -> Color(0xFFFFA500)
    else -> Color(0xFF4A9EFF)
}
