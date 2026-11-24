# PLAN.md — Pre-Rewrite Cleanup, Iteration 1: Remove Unused Features

## Context & Goal
Per `REWRITE.md`, this cleanup trims the JVM baseline BEFORE the rewrite phases so less code is ported. End state: a **single-player Commander (EDH)** experience (no Brawl/Oathbreaker/Tiny Leaders/Commander variants.

**KEEP (do not touch):** core Commander rules/gameplay, AI players, game UI/navigation, preferences for surviving features, deck editor + saved decks, in-turn undo, "Provide Deck URL", Moxfield/Archidekt import, card art fetching, deck validation, audio.

## Confirmed Decisions
1. Remove BOTH the interactive tutorial and the static `res/howto.txt` doc (stale 1v1-vs-computer intro) + its Help-menu entry.
2. Keep `DialogDownloadCardImages` + the first-run bulk-CDN-sync prompt in `FView` (card-art UX); reword the prompt text that references the removed section.
3. Lobby slot panels show the slot's **selected deck's commander card image**, falling back to the generic player icon (`FSkinProp.ICO_PLAYERS`) when no deck is selected.
4. "Bracket/banlist validation" = the Commander **bracket** (EDHREC-style skill-tier) system. Per-format **banlist** validation (`GameFormat.bannedCardNames`, `DeckFormat.isLegalCard/isLegalCommander`, `DeckRecognizer.isBannedInFormat`, `res/formats/**`) is separate and STAYS (format *data files* for removed modes go with those modes).
5. `res/lists/commander-bracket-combos.txt` feeds AI two-card-combo knowledge (2,176 pairs via `FModel` → `AiCardLists`); AI is a KEEP feature → keep the data, rename to `commander-combos.txt` (+ constant `COMMANDER_COMBOS_FILE`).

## Hard KEEP Lists (card-art / shared infra)
- Card art: `ImageFetcher`, `SwingImageFetcher`, `CdnUuidCache`, `ScryfallSetSync`, `ScryfallBulkDataSync`, `ScryfallRateLimiter`, `GuiDownloadFilteredCardImages`, `GuiDownloadService`, `GuiDownloadZipService`, `GuiDownloader` (forge-gui-desktop), `DialogDownloadCardImages`, `FView` first-run prompt (L239-250).
- App update: `AutoUpdater`, `RSSReader`, `GITHUB_*`/`RELEASE_URL` constants, `FControl`/`FTitleBarBase` updater paths, `CSubmenuPreferences` auto-updater channel combo.
- Vanguard "avatar" (unrelated feature): `DeckSection.Avatar`, `GamePieceType.AVATAR`, `FSkinProp.IMG_ZONE_AVATAR`, `LobbySlot.AvatarVanguard`, vanguard i18n keys, `CEditorConstructed` avatar pool.
- Core deck validation (all of it, see decision 4), sleeves (`FPref.UI_SLEEVES` + sleeve UI), audio, `ImportDialog`, `BugReporter`, bundled `README.txt`/`CHANGES.txt` + `git-changelog-maven-plugin` packaging.
- `NameGenerator` class + auto-naming (`GameLobby.randomName`, `GuiDisplayUtil.getRandomAiName`, `PlayerPanel` auto-fill at L296/L695) + manual `PLAYER_NAME` pref (only the visible generator UI goes).
- `GameType.Puzzle` core remnants (`GameType`, `DeckFormat.Puzzle`, `GameAction`, `GameState` puzzle parsing, `PuzzleAchievements`, `TotalPuzzlesSolved`, `lblPuzzle*`/`lblRestartPuzzle`/`lblQuitPuzzle` keys) — separate mode, follow-up iteration.
- Core `Match.startGame(Game, Runnable)` / `GameAction.startGame(...)` / `PhaseHandler.setupFirstTurn/startFirstTurn(..., Runnable)` hook params — generic seam, keep.
- `IGuiBase.isMatchInProgress()` (used by `ReplayLauncher`).

## Git & Execution Rules
- Work locally on **`dev`**.
- **Atomic local commit per completed `- [ ]` item**: code changes + `PLAN.md` (`[ ]`→`[x]`) + `PROGRESS.md` (≤ 40 lines, next to `PLAN.md`) in ONE commit. Commit style: `chore(commander): <short summary>`.
- **NEVER** `git push`, **NEVER** `git reset --hard`, no destructive commands outside the workspace.
- Before deleting/renaming any symbol, file, i18n key, or menu entry, search all references first (`rg`); for i18n keys, `rg -n "<key>" --type java` must show zero remaining references before removal.
- All Maven commands run with a **600000 ms** timeout (see `forge-test-runner` skill).
- Scratch/test artifacts only under `/tmp/opencode/` (e.g. `/tmp/opencode/forge-ui-e2e`).

## Verification Commands
- `GATE` — `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms; builds+tests forge-core, forge-game, forge-ai, forge-gui, forge-gui-desktop)
- `PKG` — `mvn package -DskipTests -T 1C` (600000 ms)
- `FULL` — `mvn test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms)

## Completed Parts (all items `[x]`, committed on `dev`; every item verified `GATE` exit 0 with an atomic commit)
- Parts 1–4 — team/2HG logic + UI, achievements/stats/match logic, deckgen + Commander deck-selection options (items 1.1–13.1).
- Part 5 — Oathbreaker/Brawl/TinyLeaders + online multiplayer (items 14.1–17.5).
- Part 6 — comprehensive post-Commander-only dead-code sweep (items 18.1–25.2).
- Standalone fix — `DeckStorage` rootDir trailing-separator crash on precon load (e17b8904590).

### Part 5 — Oathbreaker/Brawl/TinyLeaders + Online Multiplayer
Scope: remove the 3 Commander variants (engine, formats, data, storage, editor, lobby, i18n) and online multiplayer (lobby UI, host/join, server, client/reconnect, chat, net prefs, tests). KEPT: replay built on the kept slim `forge.gamemodes.net` protocol subset ("Option A": `IRemote` → codec → `ProtocolGuiGame` + `ReplayClient` over netty `EmbeddedChannel`), generic lobby seams (`GameLobby`/`LocalLobby`/`LobbySlot`/`HostingServer`), local headless harness + pure-engine simulation tests. Card-script compat: `ActivationGameTypes` parsing is lenient (unknown names skipped) — removing the GameTypes does not break card parsing.

### Phase 14 — O/B/TL engine + formats + data
- [x] 14.1 Delete `GameType.Oathbreaker`/`TinyLeaders`/`Brawl` + rebase engine branches (GameRules/Player/RegisteredPlayer/Zone/CardView/MulliganService/GameAction).
- [x] 14.2 Delete O/B/TL `DeckFormat` entries + `CardRules`/`CardRulesPredicates`/`StaticData`/`Deck`/`DeckSection`/`FModel` plumbing; delete `res/formats/Casual/{Brawl,Oathbreaker}.txt`.
- [x] 14.3 Delete O/B/TL deck storage + net-deck list files + prefs (`CardCollections`/`ForgeConstants`/`DeckPreferences`/`ItemManagerConfig`).

### Phase 15 — O/B/TL lobby + deck editor + i18n + tests
- [x] 15.1 Delete lobby variant logic (`VLobby`/`GameLobby` applyVariant/removeVariant/startGame).
- [x] 15.2 Delete O/B/TL editor + deck-manager cases (`CEditorConstructed`/`DeckManager`).
- [x] 15.3 Delete O/B/TL i18n keys from all 10 locales.
- [x] 15.4 Delete the 4 mode-specific `DeckRecognizerTest` tests (TinyLeaders-constrained).

### Phase 16 — Online multiplayer
- [x] 16.1 Delete online-lobby UI + entry points (home/online package, `FNetOverlay`, `ReconnectModals`, EDocID/EMenuGroup); rebase `CMatchUI` onto `AbstractGuiGame`.
- [x] 16.2 Delete server + online transport (`FServerManager`/`RemoteClient`/`NetworkGuiGame`/etc.); kept Option A protocol subset; moved the delta engine (`applyDelta`) into `AbstractGuiGame` (fixes replay regression).
- [x] 16.3 Delete `ForgeNetPreferences` + net prefs UI/model/docs (UPnP/server-port/AFK-timeout; kept `UI_ENABLE_ONLINE_IMAGE_FETCHER`).
- [x] 16.4 Delete all online i18n keys from all 10 locales.
- [x] 16.5 Relocate headless harness (`HeadlessGuiDesktop`/`TestUtils`) to test package `forge.headless`.
- [x] 16.6 Delete net tests; kept protocol-core tests (DeltaSync/AbuseLimits/ProtocolGuiGame*/Wire*/ReplayRecording).

### Phase 17 — Final sweep + verification
- [x] 17.1 `rg` sweep for removed symbols; removed leftover orphaned i18n keys; final GATE+PKG green.
- [x] 17.2 Silently skip stale `ItemManagerConfig` item-view prefs from removed modes (+ test).
- [x] 17.3 Create the user `skins` dir on profile init; dropped the false `FSkin` "resource not found" error.
- [x] 17.4 Guard null `cellTarget` when dragging a deck-editor tab outside the cell area.
- [x] 17.5 Remove lobby Format selector + Variant checkboxes AND the Vanguard/Planechase game modes (empty `FormatChoices.VARIANTS`, engine blocks, dev cheats, planar/vanguard lobby plumbing).

### Part 6 — Comprehensive Dead-Code Sweep (post Commander-only)
Reachability model (verified — do not re-litigate): the only New Game flow is `VSubmenuConstructed` → Commander; base type always `GameType.Constructed`; `setLimitedMode` has zero callers; lobby deck selection is Commander-only. Ordering was dependency-safe: remove dead code *referencing* the enums (18–21) before pruning the enum values (22).

Decisions (binding):
- **D1 = (a):** keep the avatar *data* feature (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `DeckFormat.Vanguard`, `CEditorConstructed` avatar pool, vanguard i18n); remove the `MomirBasic`/`MoJhoSto` game types + auto-gen wiring + orphaned avatar engine-loading.
- **D2 = KEEP:** archived/Block legality formats + `FPref.LOAD_ARCHIVED_FORMATS` + `FormatChoices.ARCHIVED`.
- **D3 = KEEP:** the `Planes`/`Schemes`/`Dungeon` deck-editor sections (`Attractions`/`Contraptions`/`Stickers` always kept).

### Phase 18 — Engine dead branches (forge-game)
- [x] 18.1 `GameAction`: dropped Planeswalker-variant check, Puzzle mulligan guard/early-return, Archenemy first-player branch.
- [x] 18.2 Planes/PlanarDice/Archenemy-scheme machinery (`PhaseHandler`/`ExtraTurn`/`AddTurnEffect`) + `RollPlanarDiceEffect` ripple (ApiType/SpellApiToAi/AiProps/`.ai` profiles).
- [x] 18.3 `Player`: Archenemy schemes, planes engine, Planeswalker-as-commander, "The Ring" PlanarConquest, avatar loading block (D1a) + ripple (Planeswalk/SetInMotion/RingTemptsYou effects, GameState ring state, `Game.onPlayerLost`, ring display, `RestartGameEffect` ring resets).
- [x] 18.4 `RegisteredPlayer`: Archenemy scheme assignment + planes/schemes/vanguardAvatars accessors; `forVariants` reduced to `(appliedVariants, deck)`; SubgameEffect avatar block.
- [x] 18.5 `Game`: `activePlanes` + accessors; `GameCopier` ripple; `ChaosEnsuesEffect` + `ApiType.ChaosEnsues`.
- [x] 18.6 Delete dead mechanic classes (PlanarDice/RunChaos/Replace*Planar*/*Trigger* + TriggerType/ReplacementType entries); `GameEventRollDie` reduced to `(sides, result)`.
- [x] 18.7 SubgameEffect vanguard-avatar block — already pulled forward in 18.4, no remaining work.

### Phase 19 — Deck generation (forge-gui + forge-core)
- [x] 19.1 Delete dead generator classes (Random/CardThemed/Archetype/Color/LDA generators + limited builders + IO); rebased `DeckgenUtil`/`DeckProxy`/`FModel`/`FDeckChooser`/`DeckType`.
- [x] 19.2 Strip dead `DeckgenUtil` methods (kept `randomSelect`, `suggestBasicLandCount`).
- [x] 19.3 Remove FModel deckgen startup load + `FPref.DECKGEN_CARDBASED` + preferences checkbox.
- [x] 19.4 Remove `DeckFormat.getCardPool`/`adjustCMCLevels` + forge-core `deck/generation` package + tests.
- [x] 19.5 Delete `res/deckgendecks/` + EDHREC matrix toolchain + stale deckgen docs.

### Phase 20 — Deck storage, net decks, DeckType (forge-gui)
- [x] 20.1 Delete `NetDeck*` classes + `res/lists/net-decks*.txt` + `NET_*` constants; rebased `FDeckChooser`/`DeckProxy`/`CardCollections` (kept `ItemManagerConfig.NET_DECKS` for "Provide Deck URL").
- [x] 20.2 Delete 8 dead `CardCollections` getters (draft/sealed/winston/scheme/plane/geneticAI/customStarter/networkEvent) + dir constants; rebased `ImportSourceAnalyzer`/`ImportDialog`.
- [x] 20.3 Delete dead `DeckProxy` getAll* methods (preconstructed/quest/winston remainders).
- [x] 20.4 `DeckType`: only CUSTOM/CONSTRUCTED/COMMANDER/PRECON_COMMANDER/PROVIDED_DECK_URL; rebased `DecksComboBox`/`FDeckChooser`/`VLobby`; removed `FPref.CONSTRUCTED_*_DECK_STATE`.
- [x] 20.5 Delete `FDeckChooser` dead branches (colors/matrix/themes/precons/net* update + refresh cases).
- [x] 20.6 Delete `res/quest/precons/`, `res/quest/themes/`, `res/geneticaidecks/` + dir constants + `missingPreconFiles.py` (kept `commanderprecons/` + `precon-images.txt`).

### Phase 21 — Deck editor (forge-gui-desktop)
- [x] 21.1 Delete 39 orphaned `ItemManagerConfig` pool constants + `CEditorVariant` + orphaned `ColumnDef`/`SColumnUtil` members (17 live constants kept; Avatar/Planes/Schemes/Dungeon sections kept per D1/D3).
- [x] 21.2 Verified `CAllDecks` "All Decks" Constructed tab REACHABLE (editor default + default layout + import flow) → kept, no removal.

### Phase 22 — Enum pruning (GameType + DeckFormat)
- [x] 22.1 `GameType`: removed 20 dead values (kept `Constructed` + `Commander`); rebased FormatChoices/GameLobby/CLobby/PlayAi/AiProps/CEditorTokenViewer/ViewWinLose/PlayerPanel/VLobby/DeckManager.
- [x] 22.2 `DeckFormat`: removed QuestDeck/Limited/CommanderDraft/PlanarConquest/Adventure/Planechase/Archenemy/Puzzle (kept Constructed/Commander/Pauper/Vanguard).
- [x] 22.3 Verification only: compiler flagged nothing; full enum-name sweep clean.

### Phase 23 — Resource data (res/)
- [x] 23.1 Delete `res/defaults/gauntlet/` + `GAUNTLET_DIR` + gauntlet branches in ImportSourceAnalyzer/ImportDialog/FView migration.
- [x] 23.2 Delete zero-ref `res/lists/planes-images.txt` + `classicModuleCardtoCrop.txt` + constants.
- [x] 23.3 Delete quest/booster image lists + `CACHE_*_PICS_DIR` + booster branches (kept card/token pics, `commanderprecons/`, quest-saves migration).

### Phase 24 — i18n orphan sweep (all 10 locale files)
- [x] 24.1 Full fresh orphan scan: deleted 81 zero-ref keys across all 10 locales (8 vanguard keys kept per D1a; dynamic-key builders verified).

### Phase 25 — (D2) Archived formats + final verification
- [x] 25.1 No-op per D2: archived/Block legality formats + `FormatChoices.ARCHIVED` kept.
- [x] 25.2 Final sweep: deleted 14 orphaned classes (FDraftOverlay/DraftTimerRope/ConstructedGameMenu/DeckGroup*/ChaosBoosterSupplier/product classes) + blockdata txts + draft sprites; rebased CardEdition/StaticData/ItemPredicate/FModel/FSkin*/ForgeConstants/FScreen/FPref + lobby/prefs UI + dead engine methods; removed 31 i18n keys from all 10 locales; GATE+PKG green.

## Part 7 — REWRITE.md Phase 1: Decoupled JVM Baseline & State-Machine Testing

### Goal
Separate UI rendering from game/rules/deck/lobby state behind a clean, testable single-player state/action contract; the existing Swing app stays runnable as baseline.

### Verified starting point (audit — do not re-litigate)
- Module graph core→game→ai→gui→desktop; forge-game/forge-ai already Swing-free (zero `java.awt`/EDT imports) — the target is runtime seams, not imports.
- Engine→UI: `Game.fireEvent` (Guava EventBus) → `FControlGameEventHandler` (ad-hoc `@Subscribe` visitor, forge-gui) → one EDT hop → `IGuiGame.update*` → `CMatchUI`.
- State: `GameView`/`PlayerView`/`CardView` are `TrackableObject`s (forge-game) kept current by direct engine `update*` calls; per-game `Tracker` (`Game.java:164`).
- Delta: `DeltaPacket` + `DeltaSyncManager.collectDeltas` + `AbstractGuiGame.applyDelta` — already used by netplay AND replay playback; full state = serialized `GameView` graph (`setGameView`).
- Actions: `IGameController` + `ProtocolMethod` + `GameProtocolSender` — canonical action channel exists.
- Prompts: latch-blocking contract (`PlayerControllerHuman.choose*` → `Input*.showAndWait` → `IGuiGame.awaitInput(CountDownLatch)`); `Input*` live in forge-gui `gamemodes/match/input`; legal candidates computed engine-side before the controller call. Biggest seam to boundary.
- Determinism: `MyRandom` single RNG funnel (`setSeed`/`CountingRandom`, thread-local streams).
- Headless precedents: `forge.headless` harness, `forge/ai/simulation` (GameSimulator/SimAudit), `gamesimulationtests` (`PlayerControllerForTests`), `FlatMonteCarloCommanderTest` AI-vs-AI recipe (`Match` + `createGame` + `setNoGUIUser` + `startGame` — no GuiBase needed).
- Gaps: no engine-side observer interface, no versioned snapshot/delta/action contract, no golden-trace or round-trip boundary tests, no trajectory/ML export hook.

### Decisions
- Contract + adapter + headless runner live in a NEW Maven module **`forge-baseline`** (depends on forge-gui; added to the reactor after forge-gui). The adapter drives `Match`/`Game` directly (forge-game) with `PlayerControllerAi`/scripted controllers — no GuiBase, no Swing.
- Serialization reuses the kept net codec (`CompatibleObjectEncoder`/`Decoder`, `TrackableSerializer`, `WireClassFilter`, EmbeddedChannel round-trip pattern) rather than inventing a new one.
- From 27.1 on, GATE includes the new module: `mvn test -pl forge-gui-desktop,forge-baseline -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`

### Phase 26 — Coupling audit artifact
- [x] 26.1 Write `docs/baseline/Coupling-Audit.md`: all engine↔UI runtime seams (event path, delta engine, prompt/latch contract, action channel, match lifecycle GameLobby→HostedMatch→Match→Game, observer wiring) with file:line refs + the seam list the Phase 28 adapter must own. Doc-only commit.

### Phase 27 — State & action contract (forge-baseline)
- [x] 27.1 Scaffold `forge-baseline` module (root pom + module pom, depends on forge-gui); versioned state contract: `BaselineSnapshot` (full) + `BaselineDelta` envelopes over the existing TrackableProperty/DeltaPacket schema; explicit format `VERSION` constant; UI-agnostic (no forge-gui/Swing imports in the state package).
- [x] 27.2 Canonical action model: `BaselineAction` (select/ok/cancel, concede, yield, next-game decision) + prompt state (pending prompt + engine-computed legal candidates) as a snapshot field; document mapping to IGameController/ProtocolMethod.
- [x] 27.3 Design doc `docs/baseline/StateActionContract.md`: schemas, versioning rules, threading model (game thread vs consumers), Swing-baseline vs future Phase-2 backend consumption.

### Phase 28 — Engine adapter
- [x] 28.1 `BaselineEngine` adapter over the direct Match/Game path (FlatMonteCarloCommanderTest recipe): `start(seed, decks, players)`; `feed(BaselineAction)` → emits deterministic Snapshot/Delta (seeded MyRandom, canonical ordering); no UI, no GuiBase.
- [x] 28.2 Prompt boundary: pending prompts + legal candidates exposed as state; choices accepted as actions; adapter runs non-interactively (PlayerControllerAi / scripted test controller) off the latch-blocking awaitInput path; CMatchUI path untouched.

### Phase 29 — Transition & invariant tests
- [x] 29.1 Fast deterministic unit tests over BaselineEngine: legal/illegal action handling (illegal → rejected, state unchanged), phase/step progression (mulligan→draw/combat/end), state invariants (zone counts, life, stack, commander in command zone). Seeded MyRandom, no UI.

### Phase 30 — Golden replay & headless match tests
- [x] 30.1 Seeded golden traces: record N small AI-vs-AI Commander games (action sequence + per-step state hashes) as committed golden files; test replays actions on a fresh engine and asserts per-step equality. (Root-caused the Phase-30 blocker first: the headless baseline never loaded `TypeLists.txt`, so basic lands had no tap-for-mana ability and no spell could ever be cast — added `BaselineEngine.ensureTypeLists()`. Three committed traces (`golden-3000{1,2,3}.trace`, seed 30001/2/3, 57 steps each, scripted land-drop + command-zone commander cast vs `LobbyPlayerAi`); `GoldenTraceReplayShould` replays each on a fresh engine and asserts per-step SHA-256 state-hash equality. Updated the 28.2/29.1 invariants that had assumed the mana-broken behavior: conservation now counts a player's spells on the shared stack, the commander's legal zones include the stack (mid-cast), and the pass-priority candidate set now includes a battlefield land's mana ability.)
- [ ] 30.2 Headless full-match tests through the adapter to completion (no UI windows, no Xvfb); assert outcome + no UI classes touched.

### Phase 31 — Serialization & transport tests
- [ ] 31.1 Round-trip tests: snapshot/delta/action serialize→deserialize→equal via the kept CompatibleObjectEncoder/Decoder + EmbeddedChannel pattern (TrackerLifecycleShould template); include a version-mismatch rejection case.

### Phase 32 — Headless simulation & ML trajectory hook
- [ ] 32.1 Headless batch runner (N seeded AI-vs-AI matches, no UI, parallel-safe via MyRandom thread-locals) + state-observer hook on the adapter exporting per-decision shards (state snapshot, legal action candidates, chosen action, match outcome) as JSONL with a documented shard schema; ML tensor schema stays out of the contract.
- [ ] 32.2 Final gate: GATE + PKG (600000 ms) + UI e2e launch (home screen renders + one local Commander match starts against the untouched CMatchUI path); update PROGRESS.md.
