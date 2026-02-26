# Portal Ritual

An AR constellation-linking game for Android. Point your camera at the sky, connect the stars, trace the rune.

Built for the **Solana Seeker** (Dimensity 7300, Android 15) — no ARCore required.

## Gameplay

1. **Tap to begin** — a numbered constellation pattern appears over the live camera feed
2. **Connect the dots** — drag from star 1 to star 2, then 2 to 3, and so on in sequence. Each star shows its number
3. **Build streaks** — chain connections within 2.5s for multiplier scoring (2x, 3x, 4x...)
4. **Stay stable** — out-of-order connections drain stability. Five mistakes and the constellation collapses
5. **Beat the clock** — each pattern has a time limit. Stars fade as time runs out
6. **Trace the rune** — complete all connections to unlock a rune-tracing challenge ($1 gesture recognition)
7. **Score** — results screen with score count-up, best streak, and sparkle effects

Seven patterns in ascending difficulty:

| Pattern | Stars | Time | Shape |
|---------|-------|------|-------|
| Triangle | 3 | 15s | Classic triangle |
| Diamond | 4 | 18s | Rhombus perimeter |
| House | 5 | 20s | Roof peak + walls |
| Zigzag | 5 | 22s | Alternating descent |
| Crown | 6 | 25s | Three rising peaks |
| Lightning | 6 | 25s | Jagged bolt |
| Spiral | 7 | 30s | Expanding outward |

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

- **ConstellationEngineTest** (28) — phase transitions, sequential validation, streak multiplier, countdown, stability, collapse triggers
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
