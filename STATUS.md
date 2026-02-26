# Portal Ritual — Status Log

## Phase A — Project Creation
- **Proposed:** Android Studio wizard settings (Empty Activity, Compose, Kotlin DSL, minSdk 26, package `com.portalritual`, save to `repo/`)
- **Approved:** yes
- **Completed:** Project created, Gradle sync + build + run verified. Default Compose UI launches.

## Phase B — ARCore + Renderer Dependencies
- **Proposed:** Add SceneView 2.3.3 (bundles ARCore 1.51.0 + Filament 1.66.0), camera permission, AR metadata in manifest
- **Approved:** yes
- **Completed:** 3 files edited (libs.versions.toml, app/build.gradle.kts, AndroidManifest.xml). Gradle sync + build + run verified. No AGP 9.0.1 compat issues.

## Phase C — Contracts/Stubs + App Loop Wiring
- **Proposed:** 7 new files (ar/, input/, render/, ui/ contracts + stubs) + MainActivity rewrite with ARScene frame loop + debug HUD
- **Approved:** yes
- **In progress:** Files created. Manifest merger conflict fixed (tools:replace). Build verification pending.
- **Worker comms:** Renderer + ARcore unblocked and actively implementing. Gameplay already complete (engine/).

## Worker: ARcore (hyperion) — ARCoreRuntime Implementation
- **Summary:** Replaced StubARRuntime with production ARCoreRuntime. Added YawSmoother for EMA-smoothed yaw extraction with dead zone and angle wraparound. Hit testing filtered to horizontal upward-facing planes with tracking-state guards.
- **Files touched:**
  - `app/src/main/java/com/portalritual/ar/ARCoreRuntime.kt` (new)
  - `app/src/main/java/com/portalritual/ar/YawSmoother.kt` (new)
  - `app/src/test/java/com/portalritual/ar/YawSmootherTest.kt` (new)
- **Tests added:** YawSmootherTest — 9 tests covering EMA smoothing, dead zone, wraparound at ±180°, reset behavior

## PIVOT — ARCore Not Supported on Solana Seeker
- **Discovery:** Seeker (Dimensity 7300, Android 15) not on Google ARCore supported devices list. Emulator also incompatible.
- **Decision:** Drop ARCore entirely. Use sensor-based AR (gyroscope + CameraX + SceneView Filament-only).
- **Engine impact:** ZERO — pure Kotlin, takes yaw from any source.

## Phase B2 — Dependency Pivot
- **Approved:** yes
- **Changes:** Swapped `arsceneview` → `sceneview` (Filament-only). Added CameraX (camera2 + lifecycle + view). Removed AR metadata + camera.ar feature from manifest. Kept CAMERA permission.
- **Completed:** 3 files edited (libs.versions.toml, app/build.gradle.kts, AndroidManifest.xml).

## Phase C2 — Runtime Pivot (CameraX + Scene + SensorRuntime)
- **Completed:** Build verified in Android Studio.
- **Changes:**
  - `ar/ARRuntime.kt` — Rewrote interface: sensor-based with `start()/stop()/currentYawDeg/placementPose()`.
  - `ar/SensorRuntime.kt` — NEW. `TYPE_ROTATION_VECTOR` sensor + `YawSmoother`.
  - `ar/ARCoreRuntime.kt` — DELETED.
  - `MainActivity.kt` — Full rewrite: CameraX PreviewView + SceneView Scene (`isOpaque=false`) + SensorRuntime.
- **Build fix:** Replaced broken `onViewCreated` block with `isOpaque = false` parameter (correct SceneView 2.3.3 API).

## Phase D — Worker Delegation (post-pivot, WingCommander-approved with 4 amendments)
- **Status:** in progress
- **Amendment 1 (deimos scope):** Expand to include Compose Canvas gesture overlay + $1 recognizer. Delegated to deimos.
- **Amendment 2 (swipe offset):** Added `onScroll` handler in `rememberOnGestureListener` — accumulates horizontal swipe as `swipeOffsetDeg` during ALIGN phases. DONE.
- **Amendment 3 (MANIFESTING bug):** Added `RitualPhase.MANIFESTING` to tap handler accepted phases. DONE.
- **Amendment 4 (player UI):** Scheduled for late Phase D / early Phase E.
- **Worker delegations:**
  - Runeinput (deimos): $1 recognizer + RuneTraceOverlay Canvas. Brief sent.

## Worker: Runeinput (deimos) — $1 Recognizer + RuneTraceOverlay
- **Summary:** Implemented full $1 Unistroke Recognizer (resample → rotate-to-indicative → scale → translate → golden-section-search compare) with 3 rune templates (triangle, zigzag, spiral). Created Compose Canvas overlay for touch capture during TRACE_RUNE phase with glowing trace visual feedback and 0–1 coordinate normalization. 15 unit tests.
- **Files touched:**
  - `app/src/main/java/com/portalritual/input/DollarOneRecognizer.kt` (new — implements TraceRecognizer)
  - `app/src/main/java/com/portalritual/input/RuneTemplates.kt` (new — 3 × 64-point pre-normalized templates)
  - `app/src/main/java/com/portalritual/input/RuneTraceOverlay.kt` (new — Compose Canvas + touch + glow)
  - `app/src/test/java/com/portalritual/input/DollarOneRecognizerTest.kt` (new)
- **Tests added:** DollarOneRecognizerTest — 15 tests covering template self-match (≥0.90), cross-template discrimination, jitter tolerance, scribble rejection, straight-line rejection, empty/single/zero-length input safety, resample correctness, centroid correctness, score bounds [0,1]

## Worker: ARcore (hyperion) — Camera Transparency Fix + Sensor Parallax
- **Camera fix (CRITICAL):** Added Filament `view.blendMode=TRANSLUCENT`, `scene.skybox=null`, renderer clear options, `environment=null` to Scene composable in MainActivity. `isOpaque=false` alone doesn't enable alpha compositing.
- **Sensor parallax:** Added `parallaxOffset()` and `resetBaseline()` to ARRuntime interface + SensorRuntime. Captures baseline yaw/pitch, returns normalized (dx, dy) offset clamped to ±0.05. Negated for window parallax effect. Added pitch smoothing via YawSmoother reuse.
- **Files touched:**
  - `app/src/main/java/com/portalritual/ar/ARRuntime.kt` (modified — 2 new methods)
  - `app/src/main/java/com/portalritual/ar/SensorRuntime.kt` (modified — parallax + pitch smoothing)
  - `app/src/main/java/com/portalritual/MainActivity.kt` (modified — Filament transparency settings)

## Worker: Runeinput (deimos) — StarDragOverlay (Constellation Pivot)
- **Summary:** Created Compose Canvas overlay for constellation star-linking gesture. Renders star dots as cyan glowing circles, completed connections as gold lines, and live drag line during touch. Hit-tests star positions (40dp radius), emits `Connection(fromId, toId)` on successful star-to-star drag. Defined local `Star` and `Connection` data classes (Manager to swap for engine types during integration).
- **Files touched:**
  - `app/src/main/java/com/portalritual/input/StarDragOverlay.kt` (new)
- **Tests added:** None (Compose UI overlay — requires instrumentation tests)

## Worker: Runeinput (deimos) — StarDragOverlay Gameplay Juice
- **Summary:** Added 5 visual effects to StarDragOverlay: (1) star alpha fade driven by `timeRemainingPct`, (2) red flash on wrong connection (0.3s LaunchedEffect), (3) gold pulse on correct connection (0.4s), (4) streak counter text with scale bounce, (5) low-stability glitch jitter (±2dp random offset at 60fps when stability < 30). All backward-compatible via default params.
- **Files touched:**
  - `app/src/main/java/com/portalritual/input/StarDragOverlay.kt` (modified)
- **Tests added:** None (visual effects — requires visual/instrumentation tests)

## Worker: Gameplay (mimas) — ConstellationEngine (Constellation Pivot)
- **Summary:** Built new ConstellationEngine replacing deprecated RitualEngine. Stateless `step()` pattern, pure Kotlin, no AR/Android imports. State machine: IDLE → CONSTELLATION_ACTIVE → CONSTELLATION_COMPLETE (1.5s beat) → TRACE_RUNE → RESULTS. Undirected connection normalization (fromId < toId). 3 hardcoded patterns: Triangle (3 stars/3 edges), Big Dipper (7 stars/6 edges), Pentagram (5 stars/5 edges). 16 unit tests.
- **Files touched:**
  - `app/src/main/java/com/portalritual/engine/ConstellationPhase.kt` (new)
  - `app/src/main/java/com/portalritual/engine/ConstellationInput.kt` (new)
  - `app/src/main/java/com/portalritual/engine/ConstellationState.kt` (new)
  - `app/src/main/java/com/portalritual/engine/ConstellationPattern.kt` (new — Star, Connection, ConstellationPattern, ConstellationPatterns)
  - `app/src/main/java/com/portalritual/engine/ConstellationEngine.kt` (new)
  - `app/src/test/java/com/portalritual/engine/ConstellationEngineTest.kt` (new)
- **Tests added:** ConstellationEngineTest — 16 tests covering initial state, phase transitions (IDLE→ACTIVE→COMPLETE→TRACE→RESULTS), valid/invalid/duplicate/reversed connections, completion detection, timer-based COMPLETE→TRACE transition, restart from RESULTS, frame counter, determinism
- **Deprecated (kept, not deleted):** RitualEngine.kt, RitualState.kt, RitualInput.kt, RitualPhase.kt, RitualConfig.kt, RingState.kt, AngleMath.kt + 4 test files

## Worker: Gameplay (mimas) — Engagement Mechanics (#1+#2+#3)
- **Summary:** Added three interlocking juice mechanics to ConstellationEngine: (1) Streak Timer + Score — 2.5s combo window, multiplier chain (streak×100 per connection), streak broken event on timeout. (2) Star Fade Countdown — per-pattern time limits (Triangle 20s, Big Dipper 30s, Pentagram 25s), COLLAPSED on timeout, timeRemainingPct for renderer. (3) Wrong Connection Penalty — stability system (100 initial, -20 wrong, +5 correct), COLLAPSED at 0, glitchIntensity helper. New EngineEvent enum (CORRECT_CONNECTION, WRONG_CONNECTION, STREAK_BROKEN, TIMEOUT, STABILITY_COLLAPSE) cleared each frame for renderer feedback. 28 unit tests (up from 16).
- **Files touched:**
  - `app/src/main/java/com/portalritual/engine/ConstellationPhase.kt` (modified — added COLLAPSED)
  - `app/src/main/java/com/portalritual/engine/ConstellationState.kt` (modified — added score, streak, streakTimer, timeRemaining, timeLimitSec, stability, lastEvent)
  - `app/src/main/java/com/portalritual/engine/ConstellationPattern.kt` (modified — added timeLimitSec per pattern)
  - `app/src/main/java/com/portalritual/engine/ConstellationEngine.kt` (modified — streak/countdown/stability logic + timeRemainingPct + glitchIntensity helpers)
  - `app/src/main/java/com/portalritual/engine/EngineEvent.kt` (new)
  - `app/src/test/java/com/portalritual/engine/ConstellationEngineTest.kt` (modified — 28 tests)
- **Tests added:** 12 new tests: streak start/multiply/break/restart, countdown drain/timeout/pct, wrong connection drain/collapse, stability clamp/5-hit-collapse, glitchIntensity, lastEvent clearing

## Worker: ARcore (hyperion) — MediaPipe ObjectDetector Spike (Phase F prep)
- **Summary:** Created ObjectDetectorHelper skeleton for CameraX → MediaPipe pipeline. Documented dependency requirements, model download, GPU delegate risks for Dimensity 7300, threading architecture. All code commented out (requires dep to compile). Fully isolated in `scanner/` package.
- **Files touched:**
  - `app/src/main/java/com/portalritual/scanner/ObjectDetectorHelper.kt` (new — skeleton)
  - `app/src/main/java/com/portalritual/scanner/SCANNER_SPIKE.md` (new — findings)
- **Key findings:** CPU delegate ~60-100ms, GPU ~30-50ms (estimated). Recommend CPU-first for reliability. GPU has shader compilation warm-up + Mali-specific risks. HandlerThread isolation prevents EGL context conflicts with Filament.

## Worker: ARcore (hyperion) — GameHUD Composable
- **Summary:** Created polished GameHUD replacing debug ConstellationOverlay. Features: countdown bar (cyan/yellow/red with pulse at <25%), stability bar (green/yellow/red with pulse at <30), score display (gold, spring scale-pop on change), streak counter (cyan pulse "3x STREAK"), connection progress ("2/6"). Cyber aesthetic, semi-transparent backgrounds, animated via `animateFloatAsState`/`animateColorAsState`/`infiniteRepeatable`.
- **Files touched:**
  - `app/src/main/java/com/portalritual/ui/GameHUD.kt` (new)
- **Tests added:** None (Compose UI — requires visual/instrumentation tests)

## Phase E — Integration + MVP Validation
- **Status:** upcoming

## Hackathon Deadline
- **Date:** March 9, 2026
- **Deliverables:** Signed APK, GitHub repo, demo video, pitch deck, banner 1200×600
