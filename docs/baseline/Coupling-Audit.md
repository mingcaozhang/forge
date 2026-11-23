# Engine ↔ UI Coupling Audit (JVM baseline)

Part 7, item 26.1. Inventory of every runtime seam between the rules engine
(`forge-game`, Swing-free) and the desktop UI (`forge-gui` / `forge-gui-desktop`),
with file:line references, plus the seam list the Phase 28 `BaselineEngine`
adapter must own. All line numbers verified against the tree at the commit
that lands this doc.

## 0. Module topology

| Module | Role | UI coupling |
|---|---|---|
| `forge-core` | `MyRandom`, `LobbyPlayer`, collections, i18n | none |
| `forge-game` | engine: `Game`, `Match`, `GameAction`, `PhaseHandler`, `Player`, views, `Tracker`, events | zero `java.awt`/`javax.swing` imports |
| `forge-ai` | `PlayerControllerAi`, simulation | zero `java.awt`/`javax.swing` imports |
| `forge-gui` | glue: `HostedMatch`, `GameLobby`, human controller + inputs, `IGuiGame`, net protocol subset, replay | Swing only via `IGuiBase` abstraction (sole literal Swing import: `forge/download/AutoUpdater.java`) |
| `forge-gui-desktop` | Swing screens (`CMatchUI`, lobby, home) + `GuiDesktop` (`IGuiBase` impl) | full Swing |

The coupling is therefore in **five runtime seams** (S1–S5) plus the match
lifecycle (S6) and the threading/determinism model (S7). Replay (S8) reuses
S1+S3 and is not a separate coupling.

---

## S1 — Event path (engine → UI, push)

Guava `EventBus` + visitor dispatch. The engine fires immutable
`GameEvent*` objects; each human GUI has one subscriber that batches them
into one EDT hop per frame.

**Engine side**
- `Game` owns the bus: `forge-game/src/main/java/forge/game/Game.java:96`
  (`new EventBus("game events")`); `fireEvent(Event)` posts at `Game.java:1080-1082`;
  `subscribeToEvents(Object)` registers at `Game.java:1083-1085`.
- Fire sites throughout the engine, e.g.
  `forge-game/src/main/java/forge/game/zone/Zone.java:146,160,171`
  (`GameEventZone` add/remove/complex),
  `forge-game/src/main/java/forge/game/zone/MagicStack.java:347,464,698`
  (cast/added/resolved),
  `forge-game/src/main/java/forge/game/player/Player.java:425,642,852-864`
  (life/damage/counters),
  `forge-game/src/main/java/forge/game/GameAction.java:2339`
  (`GameEventGameStarted`),
  `forge-game/src/main/java/forge/game/Match.java:83`
  (`GameEventGameFinished`).
- `Match` has a second, match-scoped bus: `forge-game/src/main/java/forge/game/Match.java:36`
  (`new EventBus("match events")`), `fireEvent` `Match.java:312-314`,
  `subscribeToEvents` `Match.java:315-317`. Carries `UiEvent*` (blocker/attacker
  declared, next-game decision) and `GameEventSubgame*`.
- Event model: `forge-game/src/main/java/forge/game/event/Event.java:3`
  (interface `Event`), visitor `forge-game/src/main/java/forge/game/event/IGameEventVisitor.java:3`
  (`IGameEventVisitor<T>` + `Base` default impl). UI-domain events:
  `forge-gui/src/main/java/forge/gui/events/UiEvent.java` + `IUiEventVisitor.java`.
- The `Game` constructor also subscribes its own log:
  `Game.java:392` (`subscribeToEvents(gameLog.getEventVisitor())`;
  `GameLog` at `forge-game/src/main/java/forge/game/GameLog.java:33`,
  visitor at `GameLog.java:151`).

**UI subscriber (one per human GUI)**
- `FControlGameEventHandler extends IGameEventVisitor.Base<Void>` —
  `forge-gui/src/main/java/forge/gui/control/FControlGameEventHandler.java:27`.
  Ctor resolves the target `IGuiGame` from the controller
  (`FControlGameEventHandler.java:42-50`).
- `@Subscribe receiveGameEvent` at `FControlGameEventHandler.java:149-153`
  (also feeds `SoundSystem.instance.receiveEvent`).
- Batching: events accumulate in dirty sets (`cardsUpdate`, `livesUpdate`,
  `manaPoolUpdate`, `zonesUpdate`, `turnUpdate`, `needPhaseUpdate`, …;
  fields at `FControlGameEventHandler.java:30-40`); `processEvent()`
  (`FControlGameEventHandler.java:155-160`) queues **exactly one** EDT hop via
  `GuiBase.getInterface().invokeInEdtLater(processEvents)`
  (`FControlGameEventHandler.java:158`).
- `processEvents` (`FControlGameEventHandler.java:52-147`) drains the batches
  into `IGuiGame.updateCards / updateLives / updateManaPool / updateTurn /
  updatePhase / updateStack / updatePlayerControl / updateZones / refreshField /
  openZones / showCombat / finishGame / …` calls, and on game over releases
  blocked input threads via
  `humanController.getInputQueue().onGameOver(true)`
  (`FControlGameEventHandler.java:130-133`).
- Registration points (all in
  `forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java`):
  human players `HostedMatch.java:175`, yield controllers `HostedMatch.java:177`,
  spectator `HostedMatch.java:299`, subgame start `HostedMatch.java:413`,
  subgame end `HostedMatch.java:441`.
- Match-bus subscribers: `HostedMatch.java:113-115` (`SoundSystem`,
  `HapticEngine`, and the inner `MatchUiEventVisitor` at
  `HostedMatch.java:375-476` — handles `UiEventBlockerAssigned`/
  `UiEventAttackerDeclared`/`UiEventNextGameDecision` and subgame view
  switching via `GuiBase.getInterface().invokeInEdtNow/AndWait`).

**Swing terminal**
- `IGuiGame` interface: `forge-gui/src/main/java/forge/gui/interfaces/IGuiGame.java`
  (state updates `:59-331`; `setGameView :62`, `openView :70`, `afterGameEnd :72`,
  `finishGame :110`, `chooseEntitiesForEffect :263`, `setSelection :276`,
  `applyDelta :322`, `awaitInput :329-331`, `isNetGame :334`).
- `CMatchUI` (Swing): `forge-gui-desktop/src/main/java/forge/screens/match/CMatchUI.java:147`;
  key impls: `setGameView :223`, `showCombat :471`, `updateZones :523`,
  `updateManaPool :612`, `updateLives :620`, `updateCards :627`,
  `showRevealedCards :701`, `updateButtons :853`, `updatePhase :904`,
  `updateTurn :925`, `updatePlayerControl :933`, `finishGame :957`,
  `updateStack :971`, `showPromptMessage :1111`, `openView :1176`,
  `afterGameEnd :1260`, `showErrorDialog :1403`.

**Net/replay variant of the same seam**
- `GameEventForwarder` (buffers `GameEvent`s, flushes them as protocol
  messages): `forge-gui/src/main/java/forge/gui/control/GameEventForwarder.java:31`
  (`@Subscribe :41-52`, `flush :54-56`). Registered for net clients at
  `HostedMatch.java:171-173`, for replay at `ReplaySession.attach` (S8).

## S2 — Trackable state graph (the observable "world")

The engine's views are `TrackableObject`s kept current by direct engine
`set(TrackableProperty.X, …)` calls (not by events). A per-game `Tracker`
assigns ids and tracks dirty props per consumer.

- `Tracker` (per game): `forge-game/src/main/java/forge/trackable/Tracker.java:33`
  (freeze `:43-45`, `getObj(type, id)` `:50-52`); instance at
  `forge-game/src/main/java/forge/game/Game.java:164`
  (`private final Tracker tracker = new Tracker();`), exposed via `getTracker()`.
- `TrackableObject`: `forge-game/src/main/java/forge/trackable/TrackableObject.java`
  (props `EnumMap` `:35`, `get :75` / `set :83-90` with freeze modes,
  `flagAsChanged :131`, `addConsumer :164`, `getAndClearDirtyProps :184`).
- Property vocabulary: `forge-game/src/main/java/forge/trackable/TrackableProperty.java:13`.
- Views:
  - `GameView extends TrackableObject` —
    `forge-game/src/main/java/forge/game/GameView.java:33`; created **before**
    players/controllers in the `Game` ctor (`Game.java:353`); initial props
    `:45-50` (Title, IsCommander, GameType, PoisonCountersToLose); live props
    `:85-138` (Turn `:88`, Phase `:95`, PlayerTurn `:102`, Stack/StormCount
    `:119-120`, Mulligan `:127`, GameOver/MatchOver/Winner `:131-138`);
    `view.updatePlayers(this)` at `Game.java:390`.
  - `PlayerView extends GameEntityView` —
    `forge-game/src/main/java/forge/game/player/PlayerView.java:33`
    (e.g. Life set at `:175-178`).
  - `CardView` — `forge-game/src/main/java/forge/game/card/CardView.java`.
- Full-state handle: `Game.getView()` at `Game.java:395`;
  `HostedMatch.getGameView()` at `HostedMatch.java:309-311`.
- Determinism note: `Match.preparePlayerZone`
  (`forge-game/src/main/java/forge/game/Match.java:116-141`) iterates deck
  sections in **sorted** order (`:124-125`) so zone prep is a pure function of
  (deck, seed); `Trigger.resetIDs()` at `Match.java:145` keeps ids stable across
  games.

## S3 — Delta engine (incremental state transfer)

Collects per-consumer dirty props from the Trackable graph into a `DeltaPacket`;
the consumer rebuilds the same objects on its own `Tracker`. Already used by
netplay AND replay playback.

- Envelope: `DeltaPacket` — `forge-gui/src/main/java/forge/gamemodes/net/DeltaPacket.java`
  (`sequenceNumber :27`, `checksum`/`checksumProperties :30-31`, type tags
  `TYPE_CARD_VIEW..TYPE_CSV :34-38`, `typeTagFor :47`, `makeDeltaKey :61-69`,
  ctor `:111-118`). **Has no schema `VERSION` constant** — the gap 27.1 closes.
- Collector: `DeltaSyncManager` —
  `forge-gui/src/main/java/forge/gamemodes/net/server/DeltaSyncManager.java:45`
  (`ZONE_COLLECTIONS :60-63`, `collectDeltas(GameView) :113-197` →
  `DeltaPacket` with checksum, dirty-prop collection `:259` via
  `getAndClearDirtyProps`, new objects serialized with all props `:284`,
  authoritative instance pins for zones/cards `:307-320`).
- Producer (net host): `ProtocolGuiGame` —
  `forge-gui/src/main/java/forge/gamemodes/net/ProtocolGuiGame.java:51`
  (sync manager `:57`/`:70`, `collectDeltas` calls `:216` and `:547`,
  standalone-applyDelta path `:580`).
- Consumer: `AbstractGuiGame.applyDelta(DeltaPacket)` —
  `forge-gui/src/main/java/forge/gamemodes/match/AbstractGuiGame.java:751`
  (object reconstruction `:1134-1211`, `combatDataToCombatView :1064`,
  checksum logging `:1289`).
- Wire codec (reused by 27.1/31.1): `CompatibleObjectEncoder`,
  `CompatibleObjectDecoder`, `TrackableSerializer`, `WireClassFilter` —
  `forge-gui/src/main/java/forge/gamemodes/net/{CompatibleObjectEncoder,CompatibleObjectDecoder,TrackableSerializer,WireClassFilter}.java`.
- Round-trip test template (EmbeddedChannel pipeline):
  `forge-gui/src/test/java/forge/gamemodes/net/client/TrackerLifecycleShould.java:55`
  (mock `IGuiBase` `:59-71`, channel pipeline `:83-93`).

## S4 — Prompt / latch contract (the blocking boundary)

The engine computes legal candidates, then asks the controller; the human
controller parks the **game thread** on a `CountDownLatch` until the UI
answers.

- `PlayerControllerHuman` (engine-adjacent, lives in forge-gui):
  `forge-gui/src/main/java/forge/player/PlayerControllerHuman.java:110`
  (ctor builds `InputProxy` + `InputQueue` `:124-134`; `getInputQueue :159-161`).
  Dozens of `choose*`/prompt methods end in `input.showAndWait()` — call sites
  e.g. `:383, :448, :515, :565, :605, :808, :815, :926, :1151, :1166, :1234`;
  some delegate to GUI prompt methods instead (`getGui().chooseSingleEntityFor
  Effect :571`, `getGui().chooseEntitiesForEffect :611`). The legal candidates
  are engine-computed lists passed into these methods (e.g.
  `chooseSingleEntityForEffect(optionList, …) :538`, `chooseEntitiesForEffect
  :582`).
- Latch plumbing: `InputSyncronizedBase` —
  `forge-gui/src/main/java/forge/gamemodes/match/input/InputSyncronizedBase.java`
  (`CountDownLatch cdlDone :14-18`; `showAndWait() :38-41` =
  `inputQueue.setInput(this)` + `awaitLatchRelease()`; `awaitLatchRelease()
  :22-31` → `getController().getGui().awaitInput(cdlDone)`; `stop() :43-56`
  delivers the choice to the GUI then `cdlDone.countDown()`).
  Contract interface: `forge-gui/src/main/java/forge/gamemodes/match/input/InputSynchronized.java`.
- The block itself: `IGuiGame.awaitInput(CountDownLatch)` default
  `forge-gui/src/main/java/forge/gui/interfaces/IGuiGame.java:329-331`
  (`done.await()` on the game thread; a single-threaded host overrides it).
- `InputQueue` (per human): `forge-gui/src/main/java/forge/gamemodes/match/input/InputQueue.java:36`
  (stack `:38`, `setInput :95`, `onGameOver(releaseAll) :114`; an
  `Observable` whose `updateObservers :46-49` flushes pending events to
  registered observers before the game thread blocks — net forwarders
  `HostedMatch.java:193-202`, replay forwarder `ReplaySession.attach`,
  `InputProxy` `InputQueue.java:43`).
- `InputProxy implements Observer` —
  `forge-gui/src/main/java/forge/gamemodes/match/input/InputProxy.java:43`.
- UI answer path: prompt dialogs (`InputPassPriority`, `InputConfirm`, … in
  `forge-gui/src/main/java/forge/gamemodes/match/input/`) render via
  `IGuiGame` prompt methods (e.g. `chooseEntitiesForEffect :263`,
  `setSelection :276`; net equivalents `ProtocolMethod.confirm/getChoices/
  order :58-60`); user clicks call `IGameController.select*`/`selectButton*`
  (S5), which `stop()`s the input and releases the latch.
- Game-over release: `FControlGameEventHandler.java:130-133`
  (`onGameOver(true)` unlocks any game threads waiting on inputs).
- Non-interactive counterpart (already exists, no latch):
  `PlayerControllerAi` — `forge-ai/src/main/java/forge/ai/PlayerControllerAi.java:64`.

## S5 — Action channel (UI → engine)

Canonical controller interface + a protocol enum that already names every
remote action.

- `IGameController`: `forge-gui/src/main/java/forge/interfaces/IGameController.java:17`
  (`concede :23`, `drawOfferAction :25`, `alphaStrike :27`, `useMana :29`,
  `selectButtonOk/Cancel :31-33`, `selectPlayer :35`, `selectCard :37`,
  `selectAbility :39`, `selectStackItem :42`, `undoLastAction :44`,
  `cheat :46`, `macros :48`, `nextGameDecision :50`, `reorderHand :54`,
  `requestResync :60`, yield prefs `:71-95`).
- Local human impl: `PlayerControllerHuman` (S4) — `nextGameDecision` impl at
  `PlayerControllerHuman.java:3692`.
- Net impl: `NetGameController implements IGameController` —
  `forge-gui/src/main/java/forge/gamemodes/net/client/NetGameController.java:26`;
  forwards through `GameProtocolSender` —
  `forge-gui/src/main/java/forge/gamemodes/net/GameProtocolSender.java:5`
  (`send :12`, `write :17`, `sendAndWait :23` over `IRemote`).
- Action vocabulary: `ProtocolMethod` enum —
  `forge-gui/src/main/java/forge/gamemodes/net/ProtocolMethod.java:33`
  (SERVER→CLIENT: `setGameView :35`, `openView :36`, `updateButtons :41`,
  `confirm :58`, `getChoices :59`, `order :60`, `chooseSingleEntityForEffect
  :61`, `applyDelta :69`, `applyYieldUpdate :71`, …; CLIENT→SERVER:
  `nextGameDecision :86`, select/ok/cancel/… entries below).
- UI wiring: `AbstractGuiGame` holds the "original" controllers
  (`forge-gui/src/main/java/forge/gamemodes/match/AbstractGuiGame.java:174`
  `getOriginalGameControllers`, `:179` `setOriginalGameController`) and its
  button/menu handlers dispatch into them (`:440`, `:459`, `:712`, `:737`).
- Next-game decision flow: `ControlWinLose`
  (`forge-gui-desktop/src/main/java/forge/screens/match/ControlWinLose.java:57`
  `controller.nextGameDecision(decision)`) → `PlayerControllerHuman
  :3692` → `UiEventNextGameDecision` on the Match bus →
  `MatchUiEventVisitor.visit` (`HostedMatch.java:393-396`) →
  `HostedMatch.addNextGameDecision :483-497` → `tallyNextGameDecisions
  :500-507` → `restartMatch` on the EDT (`:506`).
- Yield state: `YieldController` —
  `forge-gui/src/main/java/forge/gamemodes/match/YieldController.java:48`;
  engine→UI push via `ProtocolMethod.applyYieldUpdate`
  (`ProtocolMethod.java:71`) and `IGameController.applyYieldUpdate`
  (`IGameController.java:82-89`).

## S6 — Match lifecycle (GameLobby → HostedMatch → Match → Game)

```
VSubmenuConstructed (VSubmenuConstructed.java:32-33: new LocalLobby + VLobby)
  → GameLobby.startGame()            GameLobby.java:344-471
      validates slots/decks/legality, builds RegisteredPlayers + guis map,
      returns a Runnable (GameLobby.java:455-470):
        GuiBase.getInterface().hostMatch()          :456
        hostedMatch.startMatch(Constructed, variants, players, guis)  :458
  → HostedMatch.startMatch           HostedMatch.java:85-118
      new Match (HostedMatch.java:112), match-bus subscribers :113-115,
      startGame() :117
  → HostedMatch.startGame            HostedMatch.java:125-278
      game = match.createGame()      :129
      prefs → AI settings            :131-136
      per-human wiring: controller.setGui / gui.setGameView /
        gui.setOriginalGameController / game.subscribeToEvents(
        FControlGameEventHandler)   :164-187
      InputQueue forwarder observers :193-202
      ReplaySession.begin            :204
      gui.openView                   :206-208
      spectator path                 :210-214
      game thread: game.getAction().invoke(…)   :228
        → match.startGame(game, null)           :243
  → Match.startGame                  forge-game/.../Match.java:63-87
      prepareAllZones (sorted, deterministic)   :64 (impl :143-218)
      ante                            :65-73
      game.getAction().startGame(hook) :75
      ownership changes               :78
      fireEvent(GameEventGameFinished) :83
  → GameAction.startGame             forge-game/.../GameAction.java:2330-2387
      GameEventGameStarted            :2339
      mulligans (MulliganService)     :2358
      start scripts + NewGame trigger :2363-2374
      PhaseHandler.startFirstTurn     :2378
      RestartedByKarn loop            :2386
  → PhaseHandler                     forge-game/.../phase/PhaseHandler.java
      setupFirstTurn (runs startGameHook) :1054-1070 (hook :1066-1069)
      startFirstTurn                  :1075-1078
      mainGameLoop                    :1080-1085
  → (game over) HostedMatch.endCurrentGame  HostedMatch.java:313-351
      → gui.afterGameEnd(); GameLobby.onMatchOver → updateView  GameLobby.java:473-477
```

Supporting facts:
- `Game` ctor: `forge-game/src/main/java/forge/game/Game.java:342/346`
  (view first `:353`, players+controllers `:356-376`, action/stack/phase
  handler `:377-386`).
- Player/controller creation:
  `forge-gui/src/main/java/forge/player/LobbyPlayerHuman.java:29-34`
  (`new PlayerControllerHuman(game, player, this)`).
- GUI host seam: `GuiBase` static holder
  (`forge-gui/src/main/java/forge/gui/GuiBase.java:8`, `getInterface :17-18`);
  `IGuiBase.getNewGuiGame :72` / `hostMatch :73` / `isMatchInProgress :34`;
  desktop impl `forge-gui-desktop/src/main/java/forge/GuiDesktop.java`
  (`getNewGuiGame :364`, `hostMatch :369`).
- Headless mode (no `GuiBase` needed): `Game.setNoGUIUser()`
  (`Game.java:1496-1498`), used by
  `forge-gui-desktop/src/test/java/forge/ai/StormGoldfishTest.java:226`,
  `FlatMonteCarloBenchmark.java:136`, and
  `forge-ai/src/main/java/forge/ai/simulation/GameCopier.java:114`.
  Direct-Match recipe (no `HostedMatch` at all):
  `forge-gui-desktop/src/test/java/forge/ai/FlatMonteCarloCommanderTest.java:29-44`
  (`new Match(rules, players, "Test")` → `new Game(players, rules, match)` →
  `initVariantsZones` → `game.setAge(GameStage.Play)` → phase dev-mode).
- `Match.startGame(Game, Runnable)` / `GameAction.startGame(Runnable)` /
  `PhaseHandler.setupFirstTurn/startFirstTurn(…, Runnable)` hook params are a
  **kept generic seam** (PLAN Part 7 hard-keep) — the Phase 28 adapter may use
  them, must not remove them.

## S7 — Threading & determinism model

- **Game thread**: the whole engine (mulligans, main loop, prompts) runs on a
  thread started by `game.getAction().invoke(…)`
  (`HostedMatch.java:228`; rationale comment `:226-227`). Headless tests run
  it on the test thread directly.
- **EDT**: all rendering via `GuiBase.getInterface().invokeInEdtLater/
  invokeInEdtAndWait` — wrapped by `FThreads`
  (`forge-gui/src/main/java/forge/gui/FThreads.java:26-51`;
  `assertExecutedByEdt :15`).
- **The one blocking point**: game thread parks in
  `IGuiGame.awaitInput(CountDownLatch)` (`IGuiGame.java:329-331`) while the
  EDT renders the prompt and the answer travels back through
  `IGameController.select*` → `Input.stop()` → `countDown()` (S4/S5).
  Net/replay forwarders are `InputQueue` observers so pending events flush on
  the game thread **before** the block (S1/S4).
- **RNG**: single funnel `MyRandom` —
  `forge-core/src/main/java/forge/util/MyRandom.java`
  (`CountingRandom :41`, `setSeed :50-51`, `InheritableThreadLocal<CountingRandom>
  :64-67`, thread-local stream install `:121`). Parallel-safe per thread.
- **Determinism anchors**: sorted deck-section iteration
  (`Match.java:124-125`), `Trigger.resetIDs()` (`Match.java:145`),
  per-game card-id restart (see `TrackerLifecycleShould.java:50-53` doc).

## S8 — Replay (reuses S1 + S3, kept)

- `ReplaySession` — `forge-gui/src/main/java/forge/gamemodes/replay/ReplaySession.java:24`
  (`begin :43-64`, `attach :77` — "wires the hidden GUI the way
  `HostedMatch` wires a remote client's": forwarder on the game bus +
  InputQueue observers, `gui.openView`; `finish :92`, `abort :104`).
- `ReplayRecorder implements IRemote` —
  `forge-gui/src/main/java/forge/gamemodes/replay/ReplayRecorder.java:30`
  (records the same `GuiGameEvent`/delta stream a net client would receive).
- `ReplayClient extends FGameClient` —
  `forge-gui/src/main/java/forge/gamemodes/net/client/ReplayClient.java:14`
  (plays the stream back into an `IGuiGame` via the same protocol; uses
  `AbstractGuiGame.applyDelta`). `ReplayLauncher` gates on
  `IGuiBase.isMatchInProgress()` (`IGuiBase.java:34`).
- Consequence for the adapter: replay is a *consumer* of the same event+delta
  contract, so anything the Phase 28 adapter standardizes (S1/S3) must stay
  wire-compatible with `ReplayRecorder`/`ReplayClient`.

---

## Seams the Phase 28 `BaselineEngine` adapter must own

Per PLAN Phase 28, the adapter drives `Match`/`Game` directly (no `GuiBase`,
no `HostedMatch`, no Swing). Mapping of each current seam to adapter
responsibility:

| # | Current seam (file:line) | Adapter ownership |
|---|---|---|
| A1 | Lifecycle wiring: `GameLobby.startGame` runnable → `GuiBase.hostMatch` → `HostedMatch.startMatch/startGame` (`GameLobby.java:455-470`, `HostedMatch.java:85-278`) | `start(seed, decks, players)` builds `GameRules` + `RegisteredPlayer`s + `Match` + `Game` directly (recipe: `FlatMonteCarloCommanderTest.java:29-44` + `Game.setNoGUIUser()` `Game.java:1496`); owns the game thread (`game.getAction().invoke` or test thread); owns match termination (no `ControlWinLose`, no `tallyNextGameDecisions`) |
| A2 | Event push: `Game.fireEvent` → `FControlGameEventHandler` → EDT → `IGuiGame.update*` (`Game.java:1080`, `FControlGameEventHandler.java:149-160`) | Synchronous, UI-free observer callback (no EDT hop, no `FControlGameEventHandler`); must preserve event ordering the delta/replay path relies on |
| A3 | State: Trackable `GameView`/`PlayerView`/`CardView` + per-game `Tracker` (`GameView.java:33`, `Game.java:164`, `Game.java:353`) | `BaselineSnapshot` = full serialized view graph over the existing Trackable schema; `BaselineDelta` = `DeltaSyncManager.collectDeltas` semantics (`DeltaSyncManager.java:113-197`) minus net coupling; owns the consumer `Tracker` for reconstruction (as `AbstractGuiGame.applyDelta :751` does) |
| A4 | Prompt/latch: `PlayerControllerHuman.choose*` → `Input*.showAndWait` → `awaitInput(CountDownLatch)` (`PlayerControllerHuman.java:383…`, `InputSyncronizedBase.java:38-41`, `IGuiGame.java:329-331`) | Pending prompt + engine-computed legal candidates exposed **as state** in the snapshot; choices accepted as `BaselineAction`; adapter plays through `PlayerControllerAi` / scripted controller — never the latch path; `PlayerControllerHuman`/`Input*` untouched |
| A5 | Action channel: `IGameController` + `ProtocolMethod` + `GameProtocolSender` (`IGameController.java:17`, `ProtocolMethod.java:33`, `GameProtocolSender.java:5`) | `BaselineAction` (select/ok/cancel, concede, yield, next-game decision) as the canonical model; documented 1:1 mapping onto `IGameController` semantics (and `ProtocolMethod` names for compatibility) |
| A6 | Match bus + `MatchUiEventVisitor` (subgame switch, next-game decision) (`Match.java:312-317`, `HostedMatch.java:375-476, 483-507`) | Adapter surfaces subgame start/end and next-game decisions as state transitions + actions instead of `GuiBase.invokeInEdt*` view swaps |
| A7 | Replay wiring (`ReplaySession.attach :77`) | Untouched by the adapter, but the adapter's event/delta emission must stay wire-compatible with `ReplayRecorder`/`ReplayClient` |
| A8 | Threading/determinism (S7) | Single game thread owned by the adapter; seeded `MyRandom` per thread (`MyRandom.java:64-67`); canonical ordering inherited from the engine (`Match.java:124-125`); no EDT anywhere in the adapter path |

## Known gaps (confirm the PLAN audit)

1. No engine-side observer interface — UI couples to Guava `EventBus` +
   `IGameEventVisitor` directly (S1).
2. No versioned snapshot/delta/action contract — `DeltaPacket` has no
   `VERSION` constant (`DeltaPacket.java:27-38`); `BaselineSnapshot`/
   `BaselineDelta`/`BaselineAction` + `VERSION` are introduced in 27.1/27.2.
3. No golden-trace or round-trip boundary tests — closest template is
   `TrackerLifecycleShould` (`TrackerLifecycleShould.java:55`).
4. No trajectory/ML export hook — Phase 32.1.
