# Portal Ritual

An AR constellation-linking game for Android. Point your camera at the sky, connect the stars, trace the rune.

Built for the **Solana Seeker** (Dimensity 7300, Android 15) — no ARCore required.

## Gameplay

1. **Tap to begin** — a constellation pattern appears over the live camera feed
2. **Link the stars** — drag between glowing star points to form connections before time runs out
3. **Build streaks** — chain connections within 2.5s for multiplier scoring (2x, 3x, 4x...)
4. **Stay stable** — wrong connections drain stability. Five mistakes and the constellation collapses
5. **Trace the rune** — complete all connections to unlock a rune-tracing challenge ($1 gesture recognition)
6. **Score** — results screen with score breakdown, best streak, and sparkle effects

Three constellation patterns: **Triangle** (20s), **Big Dipper** (30s), **Pentagram** (25s).

## Architecture

```
com.portalritual/
├── engine/          Pure Kotlin game engine — stateless step() pattern
│   ├── ConstellationEngine.kt    State machine + gameplay logic
│   ├── ConstellationState.kt     Immutable game state
│   ├── ConstellationPhase.kt     IDLE → ACTIVE → COMPLETE → TRACE → RESULTS/COLLAPSED
│   ├── ConstellationPattern.kt   Star positions, connections, time limits
│   ├── ConstellationInput.kt     Per-frame input events
│   └── EngineEvent.kt            Frame events for renderer feedback
├── input/           Touch + gesture handling
│   ├── StarDragOverlay.kt        Canvas overlay — star hit-testing, drag, visual juice
│   ├── RuneTraceOverlay.kt       Canvas overlay — touch trace capture
│   ├── DollarOneRecognizer.kt    $1 Unistroke gesture recognizer
│   └── RuneTemplates.kt          Triangle, zigzag, spiral templates
├── ar/              Sensor-based AR (no ARCore)
│   ├── SensorRuntime.kt          Rotation vector → smoothed yaw/pitch + parallax
│   └── YawSmoother.kt            EMA smoother with dead zone + angle wraparound
├── ui/              Compose UI layers
│   ├── GameHUD.kt                Countdown bar, stability bar, score, streak, progress
│   ├── ResultsScreen.kt          Score count-up, sparkle particles, tap to restart
│   └── PlayerPrompt.kt           Contextual prompts per game phase
└── MainActivity.kt  CameraX + Canvas overlays + game loop (~60fps coroutine)
```

**Rendering stack:** CameraX PreviewView → 2D Compose Canvas overlays (no 3D engine in the render path). Transparent Canvas layers draw directly over the live camera feed.

**Engine design:** `ConstellationEngine.step(state, input) → state` — pure function, no side effects, fully testable. The engine knows nothing about Android, cameras, or rendering.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX (camera2 + lifecycle) |
| Sensors | `TYPE_ROTATION_VECTOR` → yaw/pitch with EMA smoothing |
| Gesture | $1 Unistroke Recognizer (custom implementation) |
| Build | AGP 9.0, Gradle Kotlin DSL, version catalog |
| Target | minSdk 26, targetSdk 36 |

## Building

Open the project in Android Studio and build normally. No special setup required.

```
repo/
├── app/
│   ├── build.gradle.kts
│   └── src/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## Tests

52 unit tests covering engine logic, gesture recognition, and sensor smoothing:

- **ConstellationEngineTest** (28) — phase transitions, streak multiplier, countdown, stability, collapse triggers, determinism
- **DollarOneRecognizerTest** (15) — template matching, cross-discrimination, jitter tolerance, edge cases
- **YawSmootherTest** (9) — EMA smoothing, dead zone, wraparound at ±180°

```bash
./gradlew test
```

## Target Device

**Solana Seeker** — Dimensity 7300, 8GB RAM, Android 15. The Seeker is not on Google's ARCore supported devices list, so this app uses pure sensor-based AR (gyroscope + accelerometer via `TYPE_ROTATION_VECTOR`) instead of ARCore plane detection.

Runs on any Android 8.0+ device with a camera and rotation vector sensor.

## License

MIT
