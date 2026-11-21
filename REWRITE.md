### Phase 1: Decoupled JVM Baseline & Automated State-Machine Testing

**Goal:** Separate UI rendering from game/rules/deck/lobby state behind a clean, testable single-player state/action contract while keeping the existing Java/Swing application runnable as a baseline.

* **Audit Coupling:** Map where UI/windowing/EDT calls are intertwined with game rules, match flow, deck management, and lobby/prompt state.
* **Define State & Action Contract:** Design a versioned, UI-agnostic state snapshot/delta and canonical player-action model covering single-player match phases, priority, zones, choices/prompts, and deck/Commander setup.
* **Build Engine Adapter:** Wrap the existing JVM game/deck/lobby engine behind a state/action boundary that accepts canonical actions and emits deterministic state updates.
* **Transition & Invariant Tests:** Write fast, deterministic unit tests verifying legal/illegal action handling, phase/step progression, and state invariants.
* **Golden Replay & Headless Match Tests:** Add seeded replay traces and headless single-player/AI match tests that run without UI windows to guard behavior during decoupling.
* **Serialization & Transport Tests:** Verify clean round-trip serialization of state snapshots/deltas and actions so the boundary is ready for out-of-process or network transport.

---

### Phase 2: Direct Non-JVM Backend Port & Parity Harness (Unoptimized)

**Goal:** Port the single-player game/rules/AI engine into a new standalone repository (such as **Rust** for zero-GC predictability and strong memory/concurrency safety) with 100% functional parity—no optimizations or logic changes yet—wired up to the existing Swing UI.

* **Scaffold New Backend Repo:** Create a dedicated standalone repository (e.g., Rust workspace) with clean internal boundaries for protocol/state types, card/deck data, rules state machine, AI, and server/IPC transport.
* **Build Parity Harness:** Create an automated comparison harness that runs identical seeds, decks, and action sequences against both the decoupled Java reference engine and the new backend, checking state snapshots, legal actions, and outcomes.
* **Port Core Rules & Zones:** Implement turn structure, priority, zone transitions, and stack resolution in the new backend and validate against the parity harness.
* **Port Cards, Triggers, Replacement Effects & AI:** Port card abilities/scripts, continuous/replacement layers, Commander rules, and AI decision logic in small, parity-tested increments.
* **Connect Existing Swing UI:** Implement the state/action transport on the new backend so the existing Swing UI can run against it as a drop-in single-player backend.

---

### Phase 3: Backend Performance & Memory Optimization

**Goal:** Maximize backend throughput, low latency, and tight memory usage on single-player/AI workloads while keeping parity tests passing.

* **Benchmark & Profile Baseline:** Measure match simulation speed, AI turn latency, state serialization/diffing cost, and peak memory usage across realistic scenarios (including multiplayer/Commander states).
* **Tighten Allocations & Data Layouts:** Eliminate hot-path heap allocations, reuse buffers/scratch state, and refine struct/arena layouts where profiling shows gains.
* **Optimize State Updates & Concurrency:** Favor compact state deltas/events over heavy full-state clones on minor steps, and tune async I/O and worker scheduling.

---

### Phase 4: High-FPS Desktop & Web Frontends

**Goal:** Build smooth, hardware-accelerated, Hearthstone-polished clients tailored to each target rather than forcing a single widget toolkit everywhere, sharing only non-UI state/protocol/asset/animation definitions where helpful.

* **Shared Non-UI Layer (Optional/Selective):** Share protocol types, view-state models, asset manifests, and layout/card-arc/animation math where practical without compromising per-client rendering freedom.
* **High-FPS Desktop Client (Linux & Windows):** Build a GPU-accelerated desktop client (such as **LibGDX / LWJGL** or a modern hardware-accelerated 2D/game graphics stack) with smooth card hover/fan animations, texture atlases/caching, and responsive controls.
* **Expand Desktop Gameplay & Tools:** Complete deck selection/editing/importing, Commander setup, match table/zones/targeting/prompts, and visual combat/spell feedback, profiling frame pacing and GPU memory on Linux and Windows.
* **High-FPS Web Client & Online
