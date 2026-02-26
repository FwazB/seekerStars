# RitualEngine — Pure Kotlin Gameplay Engine Plan

## Context

Worker **mimas** implements the pure Kotlin gameplay engine for Portal Ritual (Phase D deliverable). The engine manages the phase state machine, hybrid ring alignment, lock-on-hold, stability/glitch model, and progression through rune trace to results. **Zero ARCore imports.** All state is immutable; `step()` is a pure function. The renderer reads `RitualState` — the engine never touches rendering.

---

## 1. File List

### Source — `repo/app/src/main/java/com/portalritual/engine/`

| File | Purpose |
|---|---|
| `RitualPhase.kt` | Enum: IDLE, MANIFESTING, ALIGN_1/2/3, TRACE_RUNE, STABILIZED, RESULTS, COLLAPSED. Convenience properties: `isAlignment`, `isTerminal`, `activeRingIndex`, `nextAfterLock()` |
| `RitualConfig.kt` | Data class holding all tunable constants (tolerance, lock duration, drain/boost rates, initial stability, stabilization duration) |
| `RitualInput.kt` | Per-frame input: deltaTime, yawBaselineDeg, swipeOffsetDeg, tapEvent, traceAccepted |
| `RingState.kt` | Per-ring state: targetAngleDeg, lockTimer, locked |
| `RitualState.kt` | Full engine snapshot: phase, rings (3), stability, glitchIntensity, currentCombinedAngleDeg, stabilizationTimer, frameCount |
| `AngleMath.kt` | Top-level pure functions: `normalizeDeg`, `shortestAngleDistance`, `combineRotation` |
| `RitualEngine.kt` | Stateless `object` — `step()`, `initialState()`, `updateLockTimer()`, `stabilityUpdate()`, `computeGlitch()` |

**7 files total.**

### Tests — `repo/app/src/test/java/com/portalritual/engine/`

| File | Covers |
|---|---|
| `AngleMathTest.kt` | normalizeDeg, shortestAngleDistance, combineRotation |
| `RitualEngineAlignmentTest.kt` | Lock timer accumulation/reset, ring locking at 1.0s, phase advancement ALIGN_1→2→3→TRACE_RUNE |
| `RitualEngineStabilityTest.kt` | Drain/boost rates, clamping [0,100], glitch mapping, collapse trigger, collapse-before-lock priority |
| `RitualEnginePhaseTest.kt` | Full state machine: IDLE→…→RESULTS, COLLAPSED, retry, determinism |

**4 test files.**

---

## 2. Key Function Signatures

### AngleMath.kt

```kotlin
fun normalizeDeg(degrees: Float): Float
// Wraps to [0, 360). Handles negatives via (deg % 360 + 360) % 360.

fun shortestAngleDistance(from: Float, to: Float): Float
// Signed shortest path in (-180, 180].
// Examples: (10→350) = -20, (350→10) = +20, (0→180) = +180

fun combineRotation(yawBaseline: Float, swipeOffset: Float): Float
// Returns normalizeDeg(yawBaseline + swipeOffset).
```

### RitualEngine.kt

```kotlin
object RitualEngine {

    fun initialState(config: RitualConfig, ringTargets: List<Float>): RitualState
    // Factory. Phase=IDLE, stability=config.initialStability, rings from targets, all unlocked.

    fun step(state: RitualState, input: RitualInput, config: RitualConfig): RitualState
    // Main tick. Pure function. Dispatches by phase:
    //   IDLE         → MANIFESTING on tap
    //   MANIFESTING  → ALIGN_1 on tap (placement confirmed)
    //   ALIGN_1/2/3  → updateRing + stabilityUpdate + checkCollapse + checkLock
    //   TRACE_RUNE   → STABILIZED if traceAccepted=true
    //   STABILIZED   → RESULTS after stabilizationDuration
    //   RESULTS/COLLAPSED → IDLE on tap (retry)

    internal fun updateLockTimer(
        ring: RingState, currentAngle: Float, dt: Float, config: RitualConfig
    ): RingState
    // If |shortestAngleDistance(current, target)| ≤ tolerance: timer += dt → lock if ≥ 1.0s
    // Else: timer = 0

    internal fun stabilityUpdate(
        current: Float, withinTolerance: Boolean, dt: Float, config: RitualConfig
    ): Float
    // Within → +boostRate*dt; Outside → -drainRate*dt; Clamp [0, 100]

    fun computeGlitch(stability: Float): Float
    // Returns 1.0 - (stability / 100.0). Range [0, 1].
}
```

---

## 3. Data Classes

### RitualPhase.kt

```kotlin
enum class RitualPhase {
    IDLE, MANIFESTING, ALIGN_1, ALIGN_2, ALIGN_3, TRACE_RUNE, STABILIZED, RESULTS, COLLAPSED;

    val isAlignment: Boolean get() = this == ALIGN_1 || this == ALIGN_2 || this == ALIGN_3
    val isTerminal: Boolean get() = this == RESULTS || this == COLLAPSED
    val activeRingIndex: Int get() = when (this) {
        ALIGN_1 -> 0; ALIGN_2 -> 1; ALIGN_3 -> 2; else -> -1
    }
    fun nextAfterLock(): RitualPhase = when (this) {
        ALIGN_1 -> ALIGN_2; ALIGN_2 -> ALIGN_3; ALIGN_3 -> TRACE_RUNE; else -> this
    }
}
```

### RitualConfig.kt

```kotlin
data class RitualConfig(
    val lockToleranceDeg: Float = 4.0f,
    val lockDurationSec: Float = 1.0f,
    val stabilityBoostPerSec: Float = 20.0f,
    val stabilityDrainPerSec: Float = 8.0f,
    val initialStability: Float = 70.0f,
    val stabilizationDurationSec: Float = 2.0f
)
```

### RitualInput.kt

```kotlin
data class RitualInput(
    val deltaTime: Float,
    val yawBaselineDeg: Float,
    val swipeOffsetDeg: Float,
    val tapEvent: Boolean = false,
    val traceAccepted: Boolean? = null
)
```

### RingState.kt

```kotlin
data class RingState(
    val targetAngleDeg: Float,
    val lockTimer: Float = 0.0f,
    val locked: Boolean = false
)
```

### RitualState.kt

```kotlin
data class RitualState(
    val phase: RitualPhase,
    val rings: List<RingState>,
    val stability: Float,
    val glitchIntensity: Float,
    val currentCombinedAngleDeg: Float,
    val stabilizationTimer: Float = 0.0f,
    val frameCount: Long = 0
)
```

---

## 4. Config Defaults & Rationale

| Constant | Default | Rationale |
|---|---|---|
| `lockToleranceDeg` | 4.0° | Precise but not frustrating |
| `lockDurationSec` | 1.0s | Deliberate, not tedious |
| `stabilityBoostPerSec` | 20.0/s | 70→100 in 1.5s of perfect alignment |
| `stabilityDrainPerSec` | 8.0/s | 70→0 in ~8.75s — generous recovery window |
| `initialStability` | 70.0 | Comfortable buffer |
| `stabilizationDurationSec` | 2.0s | Calm payoff moment |

**Asymmetry is intentional:** boost 2.5× faster than drain → "calm cyber-mystic" feel rewards mastery over punishment.

---

## 5. Critical Design Decisions

1. **Collapse before lock** — if stability hits 0 on the same frame a ring would lock, collapse wins. Prevents last-frame exploits.
2. **Two taps for placement** — IDLE→MANIFESTING (preview), MANIFESTING→ALIGN_1 (confirm). Standard ARCore UX.
3. **Stability persists across all 3 alignment phases** — later rings are naturally harder because stability may have drained.
4. **TRACE_RUNE does not drain stability** — rune tracing is a distinct mechanic; stability freezes at ALIGN_3 exit value.
5. **Swipe offset arrives pre-accumulated** — input layer (deimos) tracks cumulative swipe; engine only combines values per frame.
6. **Ring targets are caller-provided** — engine doesn't generate randomness, preserving determinism.

---

## 6. Step Function — Phase Dispatch Pseudocode

```
step(state, input, config):
    nextFrame = state.frameCount + 1

    when state.phase:
        IDLE →
            if tap → copy(phase=MANIFESTING)
            else → copy(frameCount++)

        MANIFESTING →
            if tap → copy(phase=ALIGN_1)
            else → copy(frameCount++)

        ALIGN_1, ALIGN_2, ALIGN_3 →
            ringIdx = phase.activeRingIndex
            currentAngle = combineRotation(yaw, swipe)
            ring = state.rings[ringIdx]
            withinTolerance = |shortestAngleDistance(currentAngle, ring.target)| ≤ tolerance

            updatedRing = updateLockTimer(ring, currentAngle, dt, config)
            newStability = stabilityUpdate(stability, withinTolerance, dt, config)
            newGlitch = computeGlitch(newStability)

            if newStability ≤ 0 → copy(phase=COLLAPSED)           // collapse-first
            if updatedRing.locked → copy(phase=nextAfterLock())    // advance
            else → copy(updated ring, stability, glitch, angle)

        TRACE_RUNE →
            if traceAccepted=true → copy(phase=STABILIZED, timer=0)
            else → copy(frameCount++)

        STABILIZED →
            timer += dt
            if timer ≥ stabilizationDuration → copy(phase=RESULTS)
            else → copy(timer updated)

        RESULTS, COLLAPSED →
            if tap → initialState(config, same ring targets)   // retry
            else → copy(frameCount++)
```

---

## 7. Unit Test Plan (39 cases)

### AngleMathTest.kt (8 cases)
- `normalizeDeg`: 370→10, -10→350, 0→0, 360→0, -730→350
- `shortestAngleDistance`: same=0, 10→30=+20, 350→10=+20, 10→350=-20, 0→180=±180
- `combineRotation`: 90+5=95, 355+10=5, 10+(-20)=350

### RitualEngineAlignmentTest.kt (9 cases)
- Lock timer accumulates within tolerance (0.5s step → timer=0.5)
- Lock timer resets outside tolerance (prior 0.8 → 0)
- Ring locks after 1.0s of continuous alignment (60 frames × 1/60s)
- ALIGN_1→ALIGN_2, ALIGN_2→ALIGN_3, ALIGN_3→TRACE_RUNE on lock
- Boundary: distance=4.0° → within (inclusive)
- Boundary: distance=4.01° → outside, timer resets
- Previously locked rings remain locked when advancing

### RitualEngineStabilityTest.kt (10 cases)
- Boost: 70 + 20×1.0 = 90
- Drain: 70 - 8×1.0 = 62
- Clamp at 100 (95 + boost → 100)
- Clamp at 0 (3 - drain → 0)
- Collapse triggers phase=COLLAPSED when stability=0
- Glitch: stability=100 → 0.0, stability=0 → 1.0, stability=70 → 0.3
- Stability unchanged during TRACE_RUNE
- Collapse check wins over lock check on same frame

### RitualEnginePhaseTest.kt (12 cases)
- Initial state: phase=IDLE, stability=config.initialStability
- IDLE→MANIFESTING on tap, IDLE stays without tap
- MANIFESTING→ALIGN_1 on tap
- TRACE_RUNE→STABILIZED on accepted trace
- TRACE_RUNE stays on rejected trace
- STABILIZED→RESULTS after stabilizationDuration
- RESULTS/COLLAPSED→IDLE on tap (full reset)
- Frame counter increments every step
- Determinism: identical inputs → identical outputs

---

## 8. Implementation Order

1. `AngleMath.kt` + `AngleMathTest.kt` — pure math, zero deps
2. `RitualPhase.kt` — simple enum
3. `RitualConfig.kt` — data class with defaults
4. `RingState.kt` — data class
5. `RitualInput.kt` — data class
6. `RitualState.kt` — depends on Phase + RingState
7. `RitualEngine.kt` — implement: computeGlitch → stabilityUpdate → updateLockTimer → initialState → step
8. Remaining test files: Stability → Alignment → Phase

---

## 9. Improvement Suggestion: Per-Ring Difficulty Scaling

All 3 rings currently share the same tolerance and lock duration. A low-cost enhancement:

| Ring | Tolerance | Lock Duration | Feel |
|---|---|---|---|
| 1 | 5.0° | 0.8s | Generous — teaches mechanic |
| 2 | 4.0° | 1.0s | Normal |
| 3 | 3.0° | 1.2s | Tight — climactic tension |

Deferrable to Phase E tuning. Design `updateLockTimer` to accept per-ring config.

---

## Verification

- All 39 unit tests pass via `./gradlew :app:testDebugUnitTest --tests "com.portalritual.engine.*"`
- Zero ARCore/Android imports in any engine file
- Engine is deterministic: `step()` twice with identical inputs → assert equality
