# Renderer MVP Plan — Portal Frame + 3 Rings

## 1. Architecture Overview

The renderer is **dumb** — it receives a `RitualState` snapshot each frame and draws accordingly. It owns zero game logic.

```
Engine (mimas)                Renderer (this worker)
─────────────                 ─────────────────────
RitualState {                 consume ──►  show/hide portal group based on phase
  phase: RitualPhase          consume ──►  set ring rotation from targetAngleDeg / currentCombinedAngleDeg
  rings: List<RingState>      consume ──►  modulate alpha/emissive/jitter from glitchIntensity
  stability: Float            consume ──►  derive glow strength (1.0 - glitchIntensity)
  glitchIntensity: Float
  currentCombinedAngleDeg
  stabilizationTimer
  frameCount
}
```

---

## 2. Primitive Geometry

All MVP geometry is **generated at init**, not loaded from asset files.

| Element | Shape | Generation | Count |
|---------|-------|------------|-------|
| **Portal frame** | Torus (low-poly, 24 segments) | Procedural mesh or `ShapeFactory.makeCylinder` ring | 1 |
| **Ring 0 (outer)** | Torus, radius 0.40m | Same generator, thinner cross-section | 1 |
| **Ring 1 (mid)** | Torus, radius 0.32m | Same | 1 |
| **Ring 2 (inner)** | Torus, radius 0.24m | Same | 1 |
| **Glow quad** | Flat billboard quad behind rings | Simple quad mesh, additive-blend material | 1 |

~384 tris per torus (24 major × 8 minor segments). Total scene: ~2000 tris, 5 draw calls.

**Fallback:** Flat annulus (ring-shaped disc) if torus generation is painful. Acceptable for MVP.

---

## 3. Scene Graph / Node Hierarchy

```
ARScene root
  └─ portalAnchor (Anchor node, placed once via hit-test)
       └─ portalGroup (Node)
            ├─ frameNode        ← portal frame torus, static
            ├─ ring0Node        ← outer ring
            ├─ ring1Node        ← mid ring
            ├─ ring2Node        ← inner ring
            └─ glowNode         ← billboard quad behind rings
```

### Anchoring to portalPose

1. **ARCore worker** (hyperion) performs a hit-test → returns an `Anchor` on detected plane.
2. **Wiring layer** (Manager/Activity) passes the `Anchor` to renderer via `attachPortal(anchor)`.
3. **Renderer** attaches `portalAnchor` to that Anchor. Everything parented under it inherits AR-space tracking.
4. Renderer **never repositions** the portal — the Anchor handles world-tracking. Renderer only updates ring rotations and materials.

**Key:** `portalPose` is NOT in `RitualState`. The engine is pure Kotlin with no spatial types. Pose is an AR-layer concern handled via `attachPortal()`.

---

## 4. Per-Frame Update Logic

### Ring Rotation (corrected from engine source)

```kotlin
for (i in 0..2) {
    val ring = state.rings[i]
    val rotationDeg = when {
        ring.locked -> ring.targetAngleDeg              // locked: stay at target
        state.phase.activeRingIndex == i ->             // active: follow player
            state.currentCombinedAngleDeg
        else -> ring.targetAngleDeg                     // not yet active: show target
    }
    ringNodes[i].localRotation = Quaternion.axisAngle(Vector3.forward(), rotationDeg)
}
```

### Visual Parameter Mapping

| Engine field | Visual effect | Range | Implementation |
|---|---|---|---|
| `glitchIntensity` (0–1) | Position jitter + UV offset | 0 = stable, 1 = chaotic | Random offset × glitchIntensity × 0.005m |
| derived `1.0 - glitchIntensity` | Glow quad alpha + frame emissive | 0 = dim, 1 = full glow | Material `setFloat` |
| `currentCombinedAngleDeg` | Active ring Z-rotation | 0–360 continuous | `Quaternion.axisAngle` |
| `rings[i].targetAngleDeg` | Locked/inactive ring Z-rotation | 0–360 | `Quaternion.axisAngle` |
| `rings[i].locked` | Ring visual state (color/pulse) | bool | Locked rings get brighter emissive |
| `phase` | Show/hide + visual mode | enum | See phase table below |

### Phase → Visual State

| Phase | Portal visible | Rings visible | Special effect |
|---|---|---|---|
| IDLE | No | No | — |
| MANIFESTING | Yes (ghost, ~30% alpha) | No | Pulse glow = "tap to confirm" |
| ALIGN_1/2/3 | Yes (full) | Yes | Active ring follows player, glitch active |
| TRACE_RUNE | Yes (full) | Yes (all locked) | Glitch frozen at current level |
| STABILIZED | Yes (full) | Yes (all locked) | Glitch fades to 0 over 2s, glow ramps to full |
| RESULTS | Yes (full, bright) | Yes (all locked) | Max glow, no glitch |
| COLLAPSED | Yes (flickering) | Yes (scattered) | Max glitch, rapid jitter, fade out |

---

## 5. File List

```
repo/app/src/main/java/com/portalritual/render/
├── RitualRenderer.kt           — interface (4 methods)
├── SceneformRitualRenderer.kt  — Sceneform implementation
├── PortalMeshFactory.kt        — procedural torus/annulus generation
├── PortalMaterials.kt          — material factory + caching
└── RenderConstants.kt          — radii, segments, colors, phase-visual mappings
```

| File | Responsibility |
|------|---------------|
| `RitualRenderer.kt` | Interface: `init(scene)`, `attachPortal(anchor)`, `update(state)`, `detach()` |
| `SceneformRitualRenderer.kt` | Builds node hierarchy, manages materials, applies per-frame updates |
| `PortalMeshFactory.kt` | `createTorus(majorR, minorR, segments): Renderable` or annulus fallback |
| `PortalMaterials.kt` | Creates/caches materials (frame, ring, glow) with adjustable params |
| `RenderConstants.kt` | Radii, segment counts, jitter scale, color values, material param names |

Optional/deferred:
- `FilamentRitualRenderer.kt` — only if Sceneform fails
- `PortalParticles.kt` — post-MVP ambient dust

---

## 6. Dependency Options

### Option A: Sceneform Community Fork (recommended first)

```gradle
implementation 'com.gorisse.thomas.sceneform:sceneform:1.21.0'
```

**Pros:** Built-in node hierarchy, ShapeFactory, Material API, ArSceneView integration.
**Cons:** Community-maintained, custom torus needs RenderableDefinition builder.

**Need from Manager:** Add dependency in Phase B, wire ArSceneView in Activity (Phase C).

### Option B: Raw Filament (fallback)

```gradle
implementation 'com.google.android.filament:filament-android:1.51.0'
implementation 'com.google.android.filament:filament-utils-android:1.51.0'
```

**Pros:** Full PBR material control, Google-maintained.
**Cons:** No scene graph (build own), manual ARCore→Filament bridge, more boilerplate.

**Need from Manager:** Filament deps, SurfaceView instead of ArSceneView, approval before rewrite.

**Rule:** Start Sceneform. If blocked >30 min, escalate to Manager for Filament pivot.

---

## 7. Minimal Assets Strategy

| Asset | Strategy |
|-------|----------|
| Torus mesh | Procedural: `PortalMeshFactory.createTorus()` — ~400 tris per ring |
| Glow quad | Procedural: 4-vertex quad, built inline |
| Materials | Code-defined: `MaterialFactory.makeOpaqueWithColor()` + custom glow material |
| Textures | **None for MVP.** Emissive color only. |
| Models (.glb/.gltf) | **None.** All geometry generated. Zero asset pipeline. |

**Post-MVP upgrade path:** Swap procedural torus for .glb with baked detail, add noise textures, add particles.

---

## 8. Integration Contract

```kotlin
// In MainActivity or AR Fragment — called by manager's wiring code:

// Once, after AR session starts:
renderer.init(arSceneView.scene)

// Once, after user taps to place portal:
renderer.attachPortal(anchor)

// Every frame (in onUpdate callback):
renderer.update(engine.currentState)

// On reset/retry:
renderer.detach()
```

Four methods. Renderer is fully passive.

---

## 9. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Sceneform RenderableDefinition broken for custom meshes | Medium | Fallback to flat annulus via ShapeFactory.makeCylinder, or pivot Filament |
| Custom additive-blend material unsupported in Sceneform | Medium | Use semi-transparent OpaqueWithColor as approximation |
| Torus vertex math wrong on first pass | Low | Unit test geometry output before rendering |
| Performance (4 tori + glow) | Very Low | ~2000 tris, 5 draw calls — trivial |
| Anchor drift | Low | ARCore's problem (hyperion), not renderer's |

---

## 10. Cross-Reference: Engine Alignment (verified against source)

### Corrections from original assumptions

| Original Assumption | Correction |
|---|---|
| `state.portalPose` for positioning | Pose comes via `attachPortal(anchor)`, not engine state |
| `state.portalIntensity` for glow | Derive as `1.0 - state.glitchIntensity` |
| `state.ringAngles[i]` for rotation | Use `ring.targetAngleDeg` (locked/inactive) or `state.currentCombinedAngleDeg` (active) |
| `state.glitchAmount` naming | Actual field: `state.glitchIntensity` |
| No MANIFESTING visual | Added ghost portal preview state |
| No STABILIZED visual | Added 2s calm-down animation |

### Agent compatibility

- **Gameplay (mimas):** DONE. RitualState/RingState/RitualPhase verified from source. No conflicts.
- **ARcore (hyperion):** Plan done, impl blocked on Phase C. Renderer touches ARCore only via Anchor in attachPortal(). No conflicts.
- **Runeinput (deimos):** Plan done. Renderer never touches input — reads phase transitions only. No conflicts.
- **Manager:** Phase B in progress (dependency decision). Renderer blocked until Phases B+C complete.

---

## 11. Blockers

1. Phase B (Manager adds Sceneform/Filament) — must complete first
2. Phase C (Manager creates RitualRenderer interface stub) — must complete before impl
3. Sceneform vs Filament decision — waiting on Manager
