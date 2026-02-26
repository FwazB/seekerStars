[# Epoch Defenders

An AR tower defense game for Android. Place towers over the live camera feed, defend against waves of enemies, upgrade your arsenal.](Name: Epoch Defenders

  Short description: Tower defense on Solana. Defend your epoch, submit scores
  on-chain, climb the leaderboard.

  Long description: Epoch Defenders is a tower defense game built for Solana
  Seeker. Place towers, survive waves of enemies, defeat epoch bosses, and
  compete for the top spot on the on-chain leaderboard. Features 5 unique
  towers with dual-path upgrade trees, real-time Solana epoch integration,
  wallet connection via Mobile Wallet Adapter, and a TRON-inspired neon
  aesthetic with live camera background and gyroscope parallax. Built for the
  Solana Seeker hackathon.)

Built for the **Solana Seeker** (Dimensity 7300, Android 15) — no ARCore required.

## Gameplay

Place towers on a 16x12 grid overlaid on the camera feed. Enemies spawn in waves and follow an S-curve path. Destroy them before they reach the end.

**5 Towers:**

| Tower | Cost | Role |
|-------|------|------|
| Pulse | 75g | Balanced DPS — fast fire, medium range |
| Nova Cannon | 125g | AoE splash damage — slow fire, wide blast |
| Cryo | 65g | Slows enemies — 35% slow, freezes at T3 |
| Railgun | 100g | Sniper — high damage, pierces shields |
| Beacon | 125g | Support — passive income + fire rate aura |

**3-Tier Upgrade System:** Each tower has 2 upgrade paths (A and B). Both paths can reach Tier 2, but only one can reach Tier 3 — choose wisely.

**Enemies:** Grunts, Runners (fast), Tanks (heavy), and Bosses with epoch abilities:
- Shield Pulse (regenerating shield)
- Rally Cry (speed boost to nearby enemies)
- Disruption Field (reduces tower range)
- Slow Immune (ignores Cryo)

**Wave Scaling:** Exponential HP growth (`1.08^(wave-1)`). Manual "Send Wave" button with 30s auto-start — send early for a 10g bonus.

**Economy:** Starting gold 150, lives 15, wave bonus `15 + wave×3` for zero-leak clears, towers sell at 70% of total invested.

## Architecture

```
com.epochdefenders/
├── engine/          Game engine — stateless update pattern
│   ├── GameStateManager.kt  State machine, wave logic, tower/enemy updates
│   ├── GameConstants.kt     All balance numbers, tower stats, enemy stats
│   ├── Tower.kt             Tower placement, targeting, firing
│   ├── Enemy.kt             Movement, damage, slow/shield mechanics
│   ├── Projectile.kt        Projectile movement, hit detection, splash
│   ├── Upgrade.kt           3-tier dual-path upgrade system, income, aura
│   ├── WaveSpawner.kt       Wave composition and spawn timing
│   └── PathPoint.kt         S-curve path definition
├── ui/              Compose UI + Canvas rendering
│   ├── TDCanvasOverlay.kt   Full game rendering — grid, towers, enemies, effects
│   ├── TDHUD.kt             HUD — gold, lives, wave counter, send wave button
│   ├── TDTypes.kt           UI data classes, visual effect types, tower colors
│   └── theme/               TRON neon color palette (cyan/magenta/orange)
├── input/           Touch handling
│   └── TowerPlacementOverlay.kt  Tower selection, placement, upgrade popup
├── ar/              Sensor-based AR (no ARCore)
│   ├── CameraPreviewSetup.kt    CameraX preview + image analysis binding
│   ├── SensorRuntime.kt         Rotation vector → parallax offset
│   └── YawSmoother.kt           EMA smoother with dead zone
├── scanner/         Surface detection (MediaPipe)
│   ├── SurfaceDetector.kt       EfficientDet-Lite0 object detection pipeline
│   └── DetectionZoneMapper.kt   Maps detection results to game zones
├── solana/          Blockchain integration
│   ├── EpochService.kt          Solana epoch queries
│   └── EpochInfo.kt             Epoch data model
├── util/
│   └── AppLog.kt                Logging utility
└── MainActivity.kt  CameraX + Canvas overlays + game loop + Compose UI
```

**Rendering stack:** CameraX PreviewView → 2D Canvas overlay (TRON neon aesthetic) → Compose UI (HUD, tower selection, upgrades). No 3D engine.

**Engine design:** Pure state updates — `GameStateManager` processes game state each frame with no Android dependencies. Fully testable.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX (camera2 + lifecycle) |
| Sensors | `TYPE_ROTATION_VECTOR` → parallax with EMA smoothing |
| Detection | MediaPipe Tasks Vision 0.10.32 (EfficientDet-Lite0 fp16) |
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

6 test files covering engine logic, upgrades, wave spawning, and detection:

- **GameStateManagerTest** — state transitions, wave completion, beacon income/aura wiring, railgun pierce
- **TowerDefenseTest** — tower placement, targeting, firing, projectile hits
- **UpgradeTest** — dual-path upgrades, T3 lock, cost calculations, sell refunds
- **WaveSpawnerTest** — wave composition, enemy spawning, boss scheduling
- **DetectionZoneMapperTest** — surface detection to game zone mapping
- **EpochServiceTest** — Solana epoch service logic

```bash
./gradlew test
```

## Target Device

**Solana Seeker** — Dimensity 7300, Mali-G615 MC2 GPU, 8GB RAM, Android 15. The Seeker is not on Google's ARCore supported devices list, so this app uses sensor-based AR (gyroscope via `TYPE_ROTATION_VECTOR`) and MediaPipe for surface detection instead of ARCore.

Runs on any Android 8.0+ device with a camera and rotation vector sensor.

## License

MIT
