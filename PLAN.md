# PLAN.md — Forge → Single-Player Commander/EDH Strip-Down

Global rules (from AGENTS.md / forge-workflow skill):
- Work on `dev` only; never touch `master`, `sync_commit_registry.csv`, `scripts/sync-upstream.sh`.
- Keep `forge-core` / `forge-game` / `forge-ai` diffs at ZERO where possible: do NOT delete
  `GameType`/`GameFormat` enum values; only stop *referencing* them from GUI code.
- Always `grep` every reference before deleting a class/package; delete obsolete tests in the same
  commit that deletes the code they test (sync runs bare `mvn test`).
- Commit each phase: `refactor(commander): <phase summary>` (include PLAN.md checkbox flip).

Findings baseline (from exploration):
- Modules: forge-core, forge-game, forge-ai, forge-gui, forge-gui-desktop (`forge-gui-ios` in an
  inactive root-pom `ios` profile only — leave the pom alone for sync-friendliness).
- `forge.adventure` package already gone; only GUI hooks remain (GuiBase, achievements, prefs, VField).
- No quest/puzzle/gauntlet/tournament/adventure-mode tests exist. Draft/limited has 4 tests;
  `forge-gui-desktop/src/test/java/forge/planarconquestgenerate/*` (5 GA `main` files) depend on
  `forge.gamemodes.tournament.system.*`, `forge.gamemodes.limited.CardRanker`, `DeckFormat.PlanarConquest`.
- Mode resources: forge-gui/res/{adventure 157M, quest 40M, conquest 6.4M, puzzle 1.6M, draft 2.1M,
  sealed, cube, tutorial} (~207 MB), bundled by desktop OSX profiles from `../forge-gui/res`.

## Phase 1 — Adventure runtime leftovers (most isolated)
- [x] Remove adventure hooks in shared GUI code (no functional Commander behavior affected):
  - `forge-gui`: `localinstance/achievements/AdventureAchievements.java` (delete) +
    registration in `AchievementCollection.java`; `gui/GuiBase.java:12-13,36-39`
    (adventure dir/cache fields+accessors); `gui/interfaces/IGuiGame.java` adventure members;
    `localinstance/properties/ForgeConstants.java` & `ForgePreferences.java` `ADVENTURE_*` keys;
    `localinstance/skin/FSkinProp.java`; `util/JVMOptions.java`, `util/ZipUtil.java`,
    `sound/SoundSystem.java`, `haptic/HapticEngine.java`, `model/CardBlock.java`, `model/FModel.java`,
    `itemmanager/ItemManagerConfig.java`, `SColumnUtil.java`, `deck/DeckProxy.java`,
    `gamemodes/limited/LimitedPlayer.java`, `gamemodes/replay/ReplaySession.java`,
    `gui/card/CardDetailUtil.java` adventure branches.
  - `forge-gui-desktop`: `GuiDesktop.java` startup adventure-dir setup;
    `screens/match/views/VField.java`, `toolbox/imaging/FCardImageRenderer.java` adventure UI branches
    (keep card-side `CardType.Adventure` gameplay handling — that's rules, not mode).
  - Home/menu hooks: grep `CHomeUI`/`VHomeUI`/`EMenuGroup`/`LblMenuItem`/`ForgeMenu` for
    adventure entries (expect none/one; remove any).
  - Decoupling point: `GuiBase` static adventure directories (only set by `GuiDesktop`) +
    `AchievementCollection` wiring + `ForgePreferences` load/save keys.
  -     Verify: `mvn test-compile -T 1C` then
    `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C`  (keep the 3 test files that
    mention "Adventure" card names — they are card-keyword tests, not mode tests).
  - Deferred to Phase 5 (keep compile-safe until all consumers gone): `ForgePreferences.FPref`
    `UI_VIBRATE_ON_ADVENTURE_REWARD`, `ADV_COMMANDER_AUTOSELL_VARIANT`,
    `ADV_DISPLAY_PRICE_IN_REWARD_SCREEN`; `FSkinProp` `ADV_CLR_*`/`IMG_ADV_BTN_*`/`ICO_ADV*` entries
    and `PropType.ADV_BUTTONS`/`PropType.ADVENTURE` + their backing constants
    `ForgeConstants.SPRITE_ADV_BUTTONS_FILE`/`SPRITE_ADVENTURE_FILE`; `CardDetailUtil` and
    `FCardImageRenderer` adventure alternate-card-face rendering (card-side rules, not mode).
    Also deferred: `GuiDesktop` has no adventure startup code (no edit needed).

## Phase 2 — quest / puzzle / conquest / gauntlet modes + desktop tabs
- [x] Delete mode packages (all `src/main/java`):
  - `forge-gui`: `forge/gamemodes/quest/` (entire, ~31 files incl. QuestDraftUtils,
    QuestTournamentController), `forge/gamemodes/puzzle/`, `forge/gamemodes/planarconquest/`,
    `forge/gamemodes/gauntlet/`.
  - `forge-gui-desktop`: `screens/home/quest/`, `screens/home/puzzle/`, `screens/home/gauntlet/`,
    `screens/bazaar/` (quest shop), `screens/workshop/` (quest item forge) — grep refs first.
  - Decoupling points: `CHomeUI`/`VHomeUI` submenu instantiation + `EMenuGroup` entries;
    `GuiDesktop.java` imports/`openNewScreen` paths; `PlayerPanel`/`CLobby`/`VLobby` quest-lobby
    branches (keep classes if used by commander setup); `forge/player/HumanPlayer.java`
    `GameType.Quest` branches (~12 refs); `forge/itemmanager` quest managers/views;
    `deck/DeckProxy.java` quest branches; `localinstance/achievements/Quest*` +
    quest/puzzle/gauntlet/conquest keys in `ForgeConstants`/`ForgePreferences`; `ForgeMenu.java`
    quest/puzzle entries; `screens/deckeditor` quest/puzzle hooks.
  - Do NOT touch `GameType` (Quest/Puzzle/PlanarConquest/Gauntlet values stay in forge-game);
  - Note: the `planarconquestgenerate` test generators were deleted in Phase 2 (not Phase 3)
    since they depend on the removed `DeckgenUtil` conquest builders; `DeckFormat.PlanarConquest`
    and `forge/gamemodes/tournament/` remain untouched for Phase 3.
  - Verify: `mvn test-compile -T 1C` then
    `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C` (no mode tests to delete).

## Phase 3 — Limited modes (draft / sealed / booster / cube) + tournament engine + gauntlet tests
- [x] Delete limited/tournament code:
  - `forge-gui`: `forge/gamemodes/limited/` (BoosterDraft, DraftPack, LimitedPlayer, CardRanker, …),
    `forge/gamemodes/tournament/` (last main-source users removed in Phase 2).
  - `forge-gui-desktop`: `screens/home/DraftPicksSelector.java`; draft/sealed entries in home UI,
    `GuiDesktop.java` (`GameType.Draft/Sealed/Winston` paths), `ForgeMenu.java` (New > Draft/Sealed),
    deck chooser/editor draft-pick panels (keep editor core), `deck/DeckFormat` Draft/Sealed/Winston
    handling branches (keep enum values that deck import/serialization shares).
  - Delete obsolete tests (keep `mvn test` green for sync): `forge/CardRankerTest.java`,
    `forge/BoosterDraftTest.java`, `forge/BoosterDraft1Test.java`,
    `forge/gamemodes/limited/LimitedPlayerBrokerTest.java`,
    `forge-gui-desktop/src/test/java/forge/planarconquestgenerate/` (5 GA files — depend on
    `tournament.system`, `limited.CardRanker`, `DeckFormat.PlanarConquest`).
  - Decoupling points: `forge/deck/DeckProxy.java` + deck editor's limited pools; any
    `LimitedPlayerBroker`/`CardRanker` uses in AI-adjacent GUI code.
  - Verify: `mvn test-compile -T 1C` then
    `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C`.

## Phase 4 — Prune non-Commander constructed screens/tabs; streamline home screen
- [ ] Home screen / menu streamlining:
  - `forge-gui-desktop`: simplify `screens/home/CHomeUI.java`, `VHomeUI.java`, `EMenuGroup.java`
    to a Commander-first menu (Play Commander / New & Import Deck / Deck Editor / Settings);
    delete `screens/home/sanctioned/` and `screens/home/online/` tabs + `CLobby.java`/`VLobby.java`
    multiplayer lobby if unused after that (grep `StartButton`/`StopButton` usage);
    `ForgeMenu.java`: New Game submenu → Commander only; grep
    `GameFormat.values()`/`GameType.values()` list builders in GUI and restrict to Commander.
  - `forge-gui`: `forge/gamemodes/tutorial/` + menu/wizard entries; `forge/gamemodes/net` +
    `forge/gamemodes/net/client` trackers only if zero references remain after lobby removal —
    then delete tests `gamemodes/net/NetworkReferenceResolutionShould.java`,
    `gamemodes/net/client/TrackerLifecycleShould.java` and netty/jupnp deps in `forge-gui/pom.xml`
    (only if truly orphaned; otherwise keep).
  - Keep untouched: `screens/match/**`, `deckchooser/**`, deck editor core, `forge/deck/io` importers,
    `gamesimulationtests` helpers (`GameType.Constructed` test infra).
  - Decoupling point: home menu model (`EMenuGroup`/`IVSubmenu`/`LblMenuItem`) and `GuiDesktop`
    screen registry; New Game dialog format dropdown source list (filter in GUI layer, not in
    forge-game `GameFormat`).
  - Verify: `mvn test-compile -T 1C`, targeted tests, then full-package sanity
    `mvn package -DskipTests -T 1C`.

## Phase 5 — Orphaned resources/preferences + final verification
- [ ] Resource/prefs purge:
  - `grep` for res-path constants (`ForgeConstants` `*Dir`, skin `FSkinProp`) then delete
    `forge-gui/res/adventure/`, `res/quest/`, `res/conquest/`, `res/puzzle/`, `res/draft/`,
    `res/sealed/`, `res/cube/`, `res/tutorial/` (~207 MB). Keep `cardsfolder`, `languages`, `skins`,
    `formats`, `editions`, `geneticaidecks`, `lists`, `music`, `fonts`, `defaults`, `effects`.
  - Remove now-orphaned pref keys/skin props from `ForgePreferences.java`, `ForgeConstants.java`,
    `FSkinProp.java` in one sweep after code grep; leave root `pom.xml` `ios` profile and desktop
    `android-test-build` profile as-is (dead but sync-safe).
  - Final gates before marking `[x]`: `mvn package -DskipTests -T 1C` then `mvn test -T 1C`
    (both exit 0); manual smoke: launch desktop, import a `.dck`/Moxfield deck, start a Commander
    match vs AI, use deck editor.
