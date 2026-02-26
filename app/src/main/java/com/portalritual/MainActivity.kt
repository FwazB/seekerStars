package com.portalritual

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.portalritual.ar.SensorRuntime
import com.portalritual.engine.ConstellationEngine
import com.portalritual.engine.ConstellationInput
import com.portalritual.engine.ConstellationPatterns
import com.portalritual.engine.ConstellationPhase
import com.portalritual.engine.Connection
import com.portalritual.input.DollarOneRecognizer
import com.portalritual.input.RuneTraceOverlay
import com.portalritual.input.StarDragOverlay
import com.portalritual.ui.GameHUD
import com.portalritual.ui.PlayerPrompt
import com.portalritual.ui.ResultsScreen
import com.portalritual.ui.theme.PortalRitualTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var sensorRuntime: SensorRuntime
    private val cameraPermissionGranted = mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorRuntime = SensorRuntime(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionGranted.value = true
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        setContent {
            PortalRitualTheme {
                if (cameraPermissionGranted.value) {
                    ConstellationScreen(sensorRuntime)
                } else {
                    PermissionPendingScreen {
                        requestPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::sensorRuntime.isInitialized) sensorRuntime.start()
    }

    override fun onPause() {
        super.onPause()
        if (::sensorRuntime.isInitialized) sensorRuntime.stop()
    }
}

@Composable
private fun ConstellationScreen(sensorRuntime: SensorRuntime) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Engine state — start with Triangle pattern
    var gameState by remember {
        mutableStateOf(ConstellationEngine.initialState(ConstellationPatterns.TRIANGLE))
    }
    var lastFrameTimeNs by remember { mutableLongStateOf(0L) }
    val recognizer = remember { DollarOneRecognizer() }

    // Pending events from UI (consumed each tick)
    var pendingConnection by remember { mutableStateOf<Connection?>(null) }
    var pendingTraceResult by remember { mutableStateOf<Boolean?>(null) }
    var pendingTap by remember { mutableStateOf(false) }

    // Track best streak for results screen
    var bestStreak by remember { mutableStateOf(0) }

    // Game loop — ~60fps via coroutine
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.nanoTime()
            val dt = if (lastFrameTimeNs == 0L) {
                1f / 60f
            } else {
                ((now - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
            }
            lastFrameTimeNs = now

            // Reset parallax baseline when constellation starts
            if (gameState.phase == ConstellationPhase.IDLE && pendingTap) {
                sensorRuntime.resetBaseline()
                bestStreak = 0
            }

            val input = ConstellationInput(
                deltaTime = dt,
                tapEvent = pendingTap,
                connectionMade = pendingConnection,
                traceAccepted = pendingTraceResult
            )

            pendingTap = false
            pendingConnection = null
            pendingTraceResult = null

            gameState = ConstellationEngine.step(gameState, input)

            // Track best streak
            if (gameState.streak > bestStreak) {
                bestStreak = gameState.streak
            }

            delay(16L) // ~60fps
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Layer 1: Camera preview (CameraX)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Star drag overlay (active during CONSTELLATION_ACTIVE)
        StarDragOverlay(
            isActive = gameState.phase == ConstellationPhase.CONSTELLATION_ACTIVE,
            stars = gameState.pattern.stars,
            completedConnections = gameState.completedConnections,
            onConnectionMade = { conn -> pendingConnection = conn },
            timeRemainingPct = ConstellationEngine.timeRemainingPct(gameState),
            stability = gameState.stability,
            streak = gameState.streak,
            lastEvent = gameState.lastEvent?.name,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 3: Rune trace canvas (active during TRACE_RUNE phase)
        RuneTraceOverlay(
            isActive = gameState.phase == ConstellationPhase.TRACE_RUNE,
            recognizer = recognizer,
            onResult = { result ->
                if (result.accepted) {
                    pendingTraceResult = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 4: Results / Collapsed screen
        if (gameState.phase == ConstellationPhase.RESULTS ||
            gameState.phase == ConstellationPhase.COLLAPSED
        ) {
            ResultsScreen(
                phase = gameState.phase,
                score = gameState.score,
                streak = bestStreak,
                patternName = patternName(gameState.pattern),
                onTapRestart = { pendingTap = true }
            )
        }

        // Layer 5: Game HUD (replaces debug overlay)
        GameHUD(
            phase = gameState.phase,
            timeRemainingPct = ConstellationEngine.timeRemainingPct(gameState),
            stability = gameState.stability,
            score = gameState.score,
            streak = gameState.streak,
            connections = gameState.completedConnections.size,
            totalConnections = gameState.pattern.requiredConnections.size,
            modifier = Modifier.statusBarsPadding()
        )

        // Layer 6: Player prompt (bottom center) — hidden during results/collapsed
        if (gameState.phase != ConstellationPhase.RESULTS &&
            gameState.phase != ConstellationPhase.COLLAPSED
        ) {
            PlayerPrompt(
                state = gameState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }

        // Tap handler for IDLE phase only (RESULTS/COLLAPSED handled by ResultsScreen)
        if (gameState.phase == ConstellationPhase.IDLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        pendingTap = true
                    }
            )
        }
    }
}

/** Map pattern to display name for results screen. */
private fun patternName(pattern: com.portalritual.engine.ConstellationPattern): String {
    return when (pattern) {
        ConstellationPatterns.TRIANGLE -> "Triangle"
        ConstellationPatterns.BIG_DIPPER -> "Big Dipper"
        ConstellationPatterns.PENTAGRAM -> "Pentagram"
        else -> "Constellation"
    }
}

@Composable
private fun PermissionPendingScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission required")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}
