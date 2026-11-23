# Baseline State & Action Contract

Part 7, items 27.1–27.3. Design doc for the versioned, UI-agnostic state/action
contract of the `forge-baseline` module: the state envelope schemas
(`BaselineSnapshot`, `BaselineDelta`, `BaselinePrompt`), the canonical action
model (`BaselineAction`), the versioning rules, the threading model, and how the
two consumer worlds — today's Swing baseline and the future Phase-2 non-JVM
backend — consume it.

Companion doc: `Coupling-Audit.md` (engine↔UI seams S1–S8, adapter ownership
A1–A8). All file:line references there are point-in-time; the class/field
references below are current as of the commit that lands this doc.

## 1. What the contract is (and is not)

- A **data contract, not a protocol**: value types plus one sealed action
  hierarchy, all `java.io.Serializable`, all Swing-free.
- It **reuses** the kept net codec (`CompatibleObjectEncoder` /
  `CompatibleObjectDecoder`, `TrackableSerializer`, `WireClassFilter`, Netty
  `EmbeddedChannel`) instead of inventing a wire format. Its key encoding is
  byte-for-byte compatible with the existing `DeltaPacket` schema (audit
  S3/A7), so the kept netplay/replay path (`ProtocolGuiGame`,
  `ReplayRecorder`, `ReplayClient`) stays wire-compatible.
- It is **additive**: the Swing UI keeps its own path
  (`FControlGameEventHandler` → one EDT hop → `IGuiGame.update*`, audit S1)
  untouched. The contract exists for (a) deterministic testing, (b) the
  headless `BaselineEngine` adapter (Phase 28), and (c) the future non-JVM
  backend (REWRITE Phase 2).

Module: `forge-baseline` (reactor after `forge-gui`; depends on
`forge-core`/`forge-game`/`forge-ai`/`forge-gui`). Package purity is
guard-tested:

- `forge.baseline.state` — no `forge.gamemodes`/`forge.gui`/`forge.download`/
  `java.awt`/`javax.swing` imports (`StatePackageIsUiFreeShould`).
- `forge.baseline.action` — Swing-free; may reference the pure protocol data
  types of `forge.gamemodes.match` (`YieldUpdate`, `DrawOfferMessage`,
  `NextGameDecision`) (`ActionPackageIsSwingFreeShould`).

## 2. State schema

### 2.1 `BaselineFormat` — version and key encoding

`forge.baseline.state.BaselineFormat`

- `VERSION = 2` — the single schema gate for the snapshot envelope, the delta
  envelope, and the action model (§4).
- Type tags, identical to `DeltaPacket`'s:
  `TYPE_CARD_VIEW=0`, `TYPE_PLAYER_VIEW=1`, `TYPE_STACK_ITEM_VIEW=2`,
  `TYPE_GAME_VIEW=3`, `TYPE_CSV=4`.
- Composite object key: `(type << 28) | (id & 0x0FFFFFFF)` — upper 4 bits the
  type tag, lower 28 bits the **signed** object id (bit 27 is the sign,
  mirrored from `DeltaPacket.getIdFromDeltaKey`). For `CardStateView` the id
  is the composite `id * 16 + CardStateName.ordinal()`; a static assertion
  caps `CardStateName` at 16 values.
- `makeKey` / `typeTagFor` / `typeFromKey` / `idFromKey` are exact mirrors of
  `DeltaPacket.makeDeltaKey` / `typeTagFor` / `getTypeFromDeltaKey` /
  `getIdFromDeltaKey` — parity asserted for all five tags and negative ids in
  `BaselineTransportShould.keyEncodingMatchesTheNetProtocol`.
- `checkVersion(int)` — rejects any envelope stamped with a version other
  than `VERSION` by throwing `BaselineVersionException`.

### 2.2 `BaselineSnapshot` — full state

| field | type | notes |
|---|---|---|
| `version` | `int` | `BaselineFormat.VERSION` |
| `sequenceNumber` | `long` | last applied delta on the producer side |
| `gameView` | `GameView` | root of the full Trackable view graph (players, zones, stack, turn/phase, mulligan, game-over) |
| `prompt` | `BaselinePrompt` or `null` | the prompt the engine is blocked on; `null` when the engine is not waiting for input (`hasPrompt()`) |

The snapshot is the reconciliation primitive: a consumer that joins late or
falls out of sync applies one full snapshot, then deltas. The legacy
2-arg constructor stamps no prompt.

### 2.3 `BaselineDelta` — incremental state

Same value shape as `DeltaPacket` (audit S3), wrapped with the baseline
version/sequence envelope:

| field | type | notes |
|---|---|---|
| `version` | `int` | |
| `sequenceNumber` | `long` | |
| `objectDeltas` | `Map<Integer key, Map<TrackableProperty, Object>>` | changed properties of already-known objects (empty, never null) |
| `newObjects` | `Map<Integer key, Map<TrackableProperty, Object>>` | full property maps of newly-introduced objects (empty, never null) |
| `checksum` / `checksumProperties` | `int` / `int[]` | sampled integrity checksum; `checksumProperties == null` is the legacy form (`hasChecksum()`) |
| `events` | `List<Object>` or `null` | reserved for engine `Event`s forwarded alongside state (the role `GameEventForwarder` plays in the kept net path, audit S1); current public constructors leave it null — the Phase 28 adapter populates it |

Property values are the wire-safe shapes `DeltaSyncManager.collectDeltas`
produces: Integer id substitution for object references, zone collections as
`TrackableCollection`. A consumer applies deltas on a **fresh consumer-side
`Tracker`** and reconstructs objects by key — exactly what
`AbstractGuiGame.applyDelta` does for netplay (audit S3).

### 2.4 `BaselinePrompt` — the pending prompt, as state

The prompt the engine is blocked on, exposed as data. It mirrors the
information the Swing UI renders on the latch-blocking prompt path (the engine
computes the legal candidates, then parks the game thread on a
`CountDownLatch` — audit S4) so a consumer can answer without that path.

- `Kind` — 16 families, classified by the shape of the expected answer:
  `SELECT_CARD`, `SELECT_ABILITY`, `SELECT_PLAYER`, `SELECT_STACK_ITEM`,
  `CONFIRM`, `ORDER`, `CHOOSE_NUMBER`, `CHOOSE_COLOR`, `CHOOSE_OPTION`,
  `CHOOSE_MODE`, `PAY_MANA`, `MULLIGAN`, `ATTACK`, `BLOCK`, `DRAW_OFFER`,
  `NEXT_GAME_DECISION`.
- Fields: `kind`; `playerId` (Trackable id of the asked `PlayerView`);
  `message` (engine prompt text, or null); candidate lists
  (`cardCandidates`, `abilityCandidates`, `playerCandidates`,
  `stackItemCandidates`, `stringCandidates`, `numberCandidates`,
  `colorCandidates`); `minCount` / `maxCount`; `optional` (may be answered
  "none"/cancel).
- Normalization, enforced by the builder and asserted in
  `BaselinePromptShould`: candidate lists are empty (never null) when the
  family does not use them and are immutable; `minCount`/`maxCount` are 0
  when the family has no count constraint; `colorCandidates` is null unless
  the family is `CHOOSE_COLOR`.
- Candidates are **engine-computed**: legality is derived on the engine side
  before the controller is asked (audit S4), so a consumer can validate any
  answer without re-deriving legality. "Playable" is the engine's own
  playability check (`Card.getAllPossibleAbilities(player, true)` — the same
  set the human flow offers): zone/timing/rules gates only. The mana part of a
  cost is checked at payment time (`CostPartMana.canPay` is a deliberate
  stub), so a commander's command-zone cast (granted by the engine's
  "Commander Effect" static ability) is a candidate even with an empty mana
  pool; selecting a candidate whose cost cannot be paid simply fails the
  payment (the cast is not made) and the next prompt is emitted.

Referenced domain types (all Swing-free `forge-game`/`forge-card` models):
`GameView`, `PlayerView`, `CardView` (+`CardStateView`), `StackItemView`,
`SpellAbilityView`, `ColorSet`, `TrackableProperty`, `TrackableCollection`.

### 2.5 Encoding

- Java `Serializable` through the kept Netty object codec; envelopes travel
  wrapped in `GuiGameEvent(ProtocolMethod.setGameView)` (snapshot) /
  `GuiGameEvent(ProtocolMethod.applyDelta)` (delta) — the same wire wrappers
  netplay uses (round-trip proven in `BaselineTransportShould`).
- Snapshot: the full view graph is serialized as-is (the `setGameView` path
  performs no id-reference replacement); candidates in a prompt come back as
  fresh instances with identical Trackable identity.
- Delta: object references serialize as Integer ids against the producer
  `Tracker`; the decoder reconstructs them against the consumer `Tracker`
  (`CompatibleObjectDecoder.setTracker`).
- The transport (Netty channel, IPC, file) is **not** part of the contract.
  Phase 31.1 proves round-trip equality through the kept codec, including a
  version-mismatch rejection case.

## 3. Action schema

### 3.1 `BaselineAction` — the canonical model

`forge.baseline.action.BaselineAction` is a `sealed` `Serializable` interface
with 15 record variants. Each variant maps 1:1 to one `IGameController`
method and one `CLIENT` `ProtocolMethod` name (audit S5):

| `BaselineAction` | `IGameController` | `ProtocolMethod` |
|---|---|---|
| `SelectCard(card, otherCards)` | `selectCard(CardView, List, ITriggerEvent)` | `selectCard` |
| `SelectAbility(ability)` | `selectAbility(SpellAbilityView)` | `selectAbility` |
| `SelectPlayer(player)` | `selectPlayer(PlayerView, ITriggerEvent)` | `selectPlayer` |
| `SelectStackItem(item)` | `selectStackItem(StackItemView)` | `selectStackItem` |
| `Ok()` | `selectButtonOk()` | `selectButtonOk` |
| `Cancel()` | `selectButtonCancel()` | `selectButtonCancel` |
| `Concede()` | `concede()` | `concede` |
| `Yield(YieldUpdate)` | `sendYieldUpdate(YieldUpdate)` | `sendYieldUpdate` |
| `NextGameDecision(NextGameDecision)` | `nextGameDecision(NextGameDecision)` | `nextGameDecision` |
| `UseMana(byte color)` | `useMana(byte)` | `useMana` |
| `AlphaStrike()` | `alphaStrike()` | `alphaStrike` |
| `ReorderHand(card, index)` | `reorderHand(CardView, int)` | `reorderHand` |
| `UndoLastAction()` | `undoLastAction()` | `undoLastAction` |
| `DrawOffer(DrawOfferMessage.Action)` | `drawOfferAction(DrawOfferMessage.Action)` | `drawOfferAction` |
| `RequestResync()` | `requestResync()` | `requestResync` |

Deliberate deltas from the raw controller surface (documented in the
Javadoc; machine-checked in `BaselineActionShould`):

- The trailing `ITriggerEvent` mouse-metadata parameter is dropped — the wire
  protocol already sends `null` for it (`NetGameController`) and it carries no
  decision-relevant information.
- `getActivateDescription` is a query (returns a description string), not a
  state transition — not an action.
- `cheat()` / `macros()` (and the `MACRO`-mode protocol methods) belong to
  the dev/macro systems — outside this contract.
- The local-only yield setters (`setShouldAutoYield`, `setTriggerDecision`,
  `setYieldPref`, `setDisableAutoYields`, `setDisableAutoTriggers`) are
  expressed as `Yield` with the matching `YieldUpdate` variant — the same
  synthesis `NetGameController` performs on the wire.
- Server→client pushes (`applyYieldUpdate`, …) are not actions.

### 3.2 Prompt-channel families

Five prompt families (`ORDER`, `CHOOSE_NUMBER`, `CHOOSE_COLOR`,
`CHOOSE_OPTION`, `CHOOSE_MODE`) are answered by the Swing UI on the
`IGuiGame` prompt channel rather than through `IGameController` (audit S4).
They are **exposed as `BaselinePrompt` state, not as actions**: a consumer
reads family + candidates from the snapshot and answers on the prompt
channel. The Phase 28 adapter runs the engine non-interactively
(`PlayerControllerAi` / scripted controller, audit A4), so it synthesizes
those answers from the AI/script decision rather than from a
`BaselineAction` — the CMatchUI path is untouched.

## 4. Versioning rules

1. **Single gate.** `BaselineFormat.VERSION` gates the snapshot envelope, the
   delta envelope, and the action model together. Currently `VERSION = 2`
   (version 2 added the `BaselinePrompt` field to the snapshot and
   introduced the `BaselineAction` model; version 1 was snapshot/delta
   only).
2. **Strict equality.** Consumers call `BaselineFormat.checkVersion(...)` on
   every received envelope; a mismatch throws `BaselineVersionException` and
   the envelope is rejected — never auto-migrated
   (`BaselineStateShould.versionMismatchIsRejected`).
3. **Bump on any incompatible change** to:
   - a snapshot/delta envelope field (add/remove/rename/type change);
   - the key encoding or the type tags;
   - the serialized view-model surface — the delta property maps are keyed by
     the `TrackableProperty` enum and its identity is part of the serialized
     bytes, so adding/removing/renaming a property or a serialized view class
     is incompatible;
   - a `BaselineAction` variant (add/remove/rename) or a record payload
     type;
   - a `BaselinePrompt` field or `Kind` name.
   Envelope `serialVersionUID` values stay pinned at `1L` — bookkeeping for
   Java serialization, not the version gate.
4. **Recovery is resync, not migration.** A consumer that rejects a version
   (or detects a sequence gap) issues `RequestResync`; the producer answers
   with a full `BaselineSnapshot` at the supported version. Sequence numbers
   are monotonic per match, and the snapshot carries the last applied delta's
   sequence number so a receiver can reconcile.
5. **Kept-protocol parity.** `DeltaPacket` itself carries **no** schema
   `VERSION` — the baseline envelopes are where versioning lives. The key
   encoding must stay byte-for-byte compatible with
   `DeltaPacket.makeDeltaKey` so the kept netplay/replay path (audit S8/A7)
   keeps working; `BaselineTransportShould.keyEncodingMatchesTheNetProtocol`
   guards this.

## 5. Threading model

| thread | role |
|---|---|
| **game thread** | The only thread that mutates the Trackable view graph. In the Swing baseline it is started by `game.getAction().invoke(...)` (audit S6/S7); in `BaselineEngine` the adapter owns it (or the test thread runs it in unit tests). Envelopes are **produced** here. |
| **consumer threads** | apply deltas, record golden traces, export ML shards, render. They never touch the producer's view graph: they apply a `BaselineDelta` on a fresh consumer-side `Tracker` (audit S3 reconstruction pattern) or consume a `BaselineSnapshot` they own. |

Rules:

- **Envelopes are immutable value objects** (final fields; unmodifiable
  maps/lists). A snapshot/delta produced on the game thread can be handed to
  any consumer thread without synchronization.
- **No EDT in the baseline path.** The contract never requires or touches an
  EDT. Swing consumers hop to the EDT themselves — exactly as
  `FControlGameEventHandler` already does (audit S1).
- **No latch blocking on the contract path.** In the Swing baseline the game
  thread parks in `IGuiGame.awaitInput(CountDownLatch)` (audit S4); the
  contract replaces that with state — the snapshot carries `BaselinePrompt`
  and the consumer feeds `BaselineAction`s, and the engine never blocks
  waiting for an action. The latch path stays in place for the untouched
  CMatchUI.
- **Determinism.** A single `MyRandom` funnel (thread-local streams, audit
  S7) plus the engine's canonical ordering (sorted zone prep,
  `Trigger.resetIDs()` — audit S2) means a fixed `(seed, decks, action
  sequence)` produces a fixed sequence of snapshots/deltas/prompt states.
  This is what makes Phase 29 (transition/invariant tests) and Phase 30
  (golden traces) possible.

## 6. Consumers

### 6.1 Swing baseline (today)

The existing app is the baseline of record (REWRITE Phase 1 goal: the Swing
app stays runnable). It runs unchanged — `GameLobby` → `HostedMatch` →
`Match`/`Game` → `FControlGameEventHandler` → EDT → `CMatchUI` (audit
S1/S6). Nothing in the contract pushes the UI off its current path; the
contract is consumed only by tests and, from Phase 28 on, by the headless
adapter.

### 6.2 Phase 2: the non-JVM backend (REWRITE Phase 2)

The contract is the parity surface for the ported engine:

- **Input:** the same `BaselineAction` stream (plus the initial
  `(seed, decks, players)` given to `BaselineEngine.start`).
- **Output:** the same per-step `BaselineSnapshot` / `BaselineDelta` +
  `BaselinePrompt` sequence.
- **Parity harness:** run identical `(seed, decks, actions)` against the JVM
  reference (via `BaselineEngine`) and the ported backend; compare per-step
  state (hash of the normalized envelope) and the match outcome (Phase 30.1
  golden traces, 30.2 headless full-match tests).
- **Schema portability:** every type in the contract is plain data — enums,
  strings, numbers, property→value maps, lists of view references —
  expressible 1:1 in the target language. The transport (IPC/serialization
  format) is the backend's choice; the schema, `VERSION`, and key encoding
  are not.
- **Out of scope:** ML tensor schemas. Phase 32.1 exports JSONL trajectory
  shards (state snapshot, legal action candidates, chosen action, match
  outcome) with a documented shard schema on top of these same types; the
  tensor layout stays outside this contract.

## 7. Test guards (existing)

| test | guards |
|---|---|
| `StatePackageIsUiFreeShould` / `ActionPackageIsSwingFreeShould` | package purity — no forge-gui/Swing imports in the state package, no Swing in the action package |
| `BaselineStateShould` | envelope version stamping, version-mismatch rejection, the five type tags, key round-trip incl. negative ids, CSV composite key, ≤16 `CardStateName` cap |
| `BaselinePromptShould` | builder normalization (empty-not-null immutable lists, 0 counts, null colors), snapshot prompt field |
| `BaselineActionShould` | machine-checked 1:1 mapping to `IGameController` + `ProtocolMethod` (names, declaring classes, arg lists), sealed closure, protocol-surface completeness |
| `BaselineTransportShould` | codec round-trips (snapshot, snapshot+prompt, delta) through the kept `CompatibleObjectEncoder`/`Decoder` + `EmbeddedChannel`; key-encoding parity vs `DeltaPacket` |
| `BaselineEngineShould` | 28.1/28.2 adapter: a seeded game runs headless over the direct Match/Game path, fed actions drive the scripted player, and the same seed + same actions emit an identical envelope stream (prompt snapshots included) |
| `BaselinePromptBoundaryShould` | 28.2 prompt boundary: the pending `SELECT_ABILITY` prompt with its engine-computed legal candidates on the snapshot, a choice applied as an action and visible in the next prompt, an out-of-candidates action rejected as a pass |
| `EnginePackageIsSwingFreeShould` | the engine package's main sources stay headless (no Swing/GUI/download/screens/latch-input imports) |

Planned (Phases 29–31): transition/invariant tests over `BaselineEngine`
(29.1), seeded golden-trace replay (30.1), headless full-match tests (30.2),
round-trip + version-mismatch rejection through the kept codec (31.1).

## 8. Phase 28–32 hooks (what lands on top of this contract)

- **28.1 `BaselineEngine`** — `start(seed, decks, players)` builds
  `GameRules` / `RegisteredPlayer`s / `Match` / `Game` directly (audit A1;
  recipe `FlatMonteCarloCommanderTest` + `Game.setNoGUIUser()`);
  `feed(BaselineAction)` emits the next snapshot/delta deterministically; no
  UI, no `GuiBase`.
- **28.2 prompt boundary** — the pass-priority prompt is the adapter's
  interactive boundary: before the game thread blocks on the next fed action,
  `BaselineEngine.emitPromptSnapshot` emits a `BaselineSnapshot` carrying a
  `SELECT_ABILITY` prompt whose candidates are the scripted player's currently
  playable abilities (`ScriptedPlayerController.collectPlayableAbilities`,
  canonical zone → card-id → ability-id order) plus their distinct host cards;
  the fed `BaselineAction` answers it (`SelectAbility`/`SelectCard` play,
  `Concede` concedes, `Ok`/`Cancel`/anything outside the candidates passes).
  Every other prompt family is synthesized non-interactively by the inherited
  `PlayerControllerAi` (audit A4); CMatchUI untouched.
- **29.1** — legal/illegal action handling, phase/step progression, state
  invariants over the adapter with a seeded `MyRandom`.
- **30.1/30.2** — golden traces and headless full matches through the
  adapter.
- **31.1** — serialization round-trips incl. version-mismatch rejection
  (`TrackerLifecycleShould` template).
- **32.1** — headless batch runner + state-observer hook exporting per-decision
  JSONL shards on the same snapshot/prompt/action types.
