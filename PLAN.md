# Forge Desktop UI & 2–4 Player Commander Runtime Stability Plan

Goal: drive the real Swing desktop UI (via the `forge-ui-e2e` in-process inspection
agent) across main navigation, deck/Commander setup, and full 2/3/4-player Commander
matches, fixing every reproducible runtime error with a clean-build + relaunch
verification loop until the whole surface is error-free.

## Hard Constraints (never violate)
- Always pass `timeout: 600000` (10 min) on `mvn test`, `mvn package`, and long UI-agent bash tool calls so commands are never killed at the 120000 ms default.
- Work only on `dev`. Confirm with `git branch --show-current` (must print `dev`) before starting any phase.
- NEVER run `git push`. All commits stay local on `dev` for user review.
- Keep `forge-core`, `forge-game`, and `forge-ai` at **zero diff**.
  - Before committing, `git diff --cached --name-only -- forge-core forge-game forge-ai` must print nothing.
  - Working tree: `git status --short -- forge-core forge-game forge-ai` must print nothing.
- Code changes are allowed **only** in `forge-gui` and `forge-gui-desktop`.
- NEVER modify `sync_commit_registry.csv` or `scripts/sync-upstream.sh`.

## Gate: Rules for Marking an Item `[x]`
A `- [ ]` item may be marked `[x]` **only** when ALL of the following are true in the same iteration:
1. **Clean build succeeded** (10-min timeout): `mvn clean package -pl forge-gui-desktop -am -DskipTests -T 1C`
   exits 0 AND `forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar` exists.
2. **Launched + exercised**: the shaded jar was launched with the UI inspection agent
   (per `forge-ui-e2e`: isolated scratch dir such as `/tmp/forge-ui-e2e`, `res` symlink,
   `-javaagent:.../agent.jar=<dir>`, stdout/stderr → `app.log`), the flow was driven end-to-end
   through the UI, and the UI reached the expected terminal state (confirmed by a `ui.txt` dump).
3. **Logs/UI inspected**: `app.log` + `agent.log` were reviewed and show **zero reproducible runtime
   errors** (no exceptions, stack traces, or stalled transitions). Benign environment messages
   (e.g. missing audio `SourceDataLine` on a headless box) are noted, not counted as errors.
4. **Zero-diff guard passed** (see Hard Constraints).

On any runtime error: fix strictly within `forge-gui` / `forge-gui-desktop` → **clean build** →
**relaunch + re-exercise** to confirm the error no longer reproduces → update `PROGRESS.md`
(concise, high-signal; prune resolved items) → make a small **atomic local commit** on `dev`.
**If the error still reproduces, the item stays `- [ ]`.**

Anti-premature-completion rules:
- A clean build **alone** never justifies checking an item — the UI must be launched and exercised.
- A fix is not verified until it survives a **clean build + relaunch** (not just an incremental rebuild).
- Do not batch-check a phase: each item needs its own exercised evidence before it is `[x]`.
- Before finishing an iteration: stop any test Java process, delete the scratch dir, and confirm
  `git status --short` shows no untracked test/debug artifacts.

Common commands (used in every phase):
- Clean build: `mvn clean package -pl forge-gui-desktop -am -DskipTests -T 1C`
- Targeted tests (sync-compat): `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`

---

## Phase 1 — Clean Build, Launch, Main UI Navigation / Menus / Settings
Targets: `forge.view.Main`, `forge.GuiDesktop`, `forge.view.{FView,FFrame,FNavigationBar,SplashFrame}`,
`forge.control.{FControl,KeyboardShortcuts}`, `forge.menus.*`, `forge.screens.home.*`,
`forge.screens.home.{tiles,settings}.*`, `forge.gui.SwingPrefBinders`.

- [x] Clean-build the desktop jar (`mvn clean package -pl forge-gui-desktop -am -DskipTests -T 1C`) and confirm the shaded jar exists under `forge-gui-desktop/target/`.
- [x] Launch the jar with the UI agent from an isolated scratch dir; confirm the splash clears and the main window (`FView`/`FFrame`) is visible with the `FNavigationBar` present (verify via `dump` → `ui.txt`). No exceptions in `app.log`/`agent.log`.
- [ ] Exercise main navigation: open each home tab/tile (`HomeTile`, `LastMatchTile`, `RecentDecksCarousel`) and confirm the screen switches and renders without exceptions.
- [ ] Open and exercise the main menu items (`ForgeMenu`, `HelpMenu`, `AudioMenu`, `LayoutMenu`) and the `KeyboardShortcutsDialog`; confirm menus open, items enable/disable correctly, and no NPE/stack trace.
- [ ] Open Settings/Preferences (`screens.home.settings.VSubmenuPreferences` via `CSubmenuPreferences`); toggle representative preference controls (bound through `SwingPrefBinders`) and confirm values apply and persist without errors.
- [ ] Open the other settings submenus (`CSubmenuAvatars`, `CSubmenuDownloaders`, `CSubmenuReleaseNotes`, `CSubmenuAchievements`) and `DialogDownloadCardImages`; confirm open/close and any bulk-sync entry points do not throw.
- [ ] **Phase 1 gate:** after a single clean build + relaunch that exercises all items above with zero reproducible runtime errors, mark the phase items `[x]`, update `PROGRESS.md`, and make an atomic local commit on `dev` (verify zero-diff guard first).

## Phase 2 — Deck Selection / Editing / Importing & Commander Setup
Targets: `forge.screens.home.{VLobby,CLobby,PlayerPanel}`, `forge.screens.home.sanctioned.VSubmenuConstructed`,
`forge.gamemodes.match.{GameLobby,LocalLobby}`, `forge.deckchooser.{FDeckChooser,FDeckViewer,DecksComboBox}`,
`forge.gui.{CommanderChooser,ImportDialog,ImportSourceAnalyzer,CardListChooser}`,
`forge.screens.deckeditor.*` (`CDeckEditorUI`, `VDeckEditorUI`, `controllers.*`, `views.*`, `menus.*`,
`DeckImport`, `AddBasicLandsDialog`, `ChangePrintingDialog`), `forge.deck.{DeckProxy,CommanderPicks,CommanderOptions}`.

- [ ] Open the Commander lobby (`VSubmenuConstructed`): confirm the format is locked to Commander (`lobby.setFormatChoice(GameType.Commander)`), the **Maximum Bracket** combo and **Games-in-Match** combo render, and each per-player `FDeckChooser` is populated with Commander decks.
- [ ] Select/switch decks per player via `FDeckChooser` / `DecksComboBox`; confirm the deck list and deck viewer (`FDeckViewer`) update to the correct cards and the commander-pick button reflects the deck's commander(s) (`CommanderPicks`). No errors.
- [ ] Open the Commander/partner picker for a player (`VLobby.chooseCommander` → `gui.CommanderChooser`); choose a different commander and an optional partner; confirm the lobby deck updates (`CommanderOptions.withCommanders`) and the pick is recorded. No errors.
- [ ] Open the deck editor (`CDeckEditorUI`/`VDeckEditorUI`) and the deck-list tabs (`VAllDecks`, `VCommanderDecks`); confirm decks load and the current deck (`VCurrentDeck`) renders its card list.
- [ ] Edit a deck: add/remove cards, change a printing (`ChangePrintingDialog`), and adjust basic lands (`AddBasicLandsDialog`); confirm the deck updates and stats/curves recompute (`VStatistics`, `ManaCurveChart`, `VProbabilities`, `ManaPalette`) without errors.
- [ ] Import a deck via the import dialog (`gui.ImportDialog` / `ImportSourceAnalyzer`) from a supported source (`.dck` / text / Moxfield / Archidekt); confirm the deck parses and appears in the deck list. No errors.
- [ ] **Phase 2 gate:** after a clean build + relaunch exercising all items above with zero reproducible runtime errors, mark items `[x]`, update `PROGRESS.md`, and make an atomic local commit on `dev` (zero-diff guard first).

## Phase 3 — 2-Player Commander Match Play & Monitoring
Targets: `forge.screens.match.{CMatchUI,VMatchUI}` + `match/{views,controllers,menus}.*`
(`GameLogPanel`, `TargetingOverlay`, `MatchEffectOverlay`, `ZoneAction`, `VAssignCombatDamage`,
`VAssignGenericAmount`, `VAutoYieldsAndTriggers`, `VYieldSettings`, `WinLoseCardViewer`, `ControlWinLose`,
`ViewWinLose`), `forge.view.arcane.*` (`CardPanel`, `HandArea`, `PlayArea`, `CardArea`, `FloatingZone`,
`FloatingCardArea`, `ListCardArea`, `effects.*`, `util.*`), `forge.gamemodes.match.HostedMatch`,
`forge.control.{FControl,KeyboardShortcuts}`.

- [ ] From the Commander lobby configure a **2-player** match (1 human + 1 AI) and start it via `StartButton` → `lobby.startGame()`; confirm the match screen (`CMatchUI`/`VMatchUI`) appears, both players' zones (`HandArea`, `PlayArea`, `FloatingZone`) render, and the game begins. No exceptions.
- [ ] Advance play: take the opening turn, cast a spell/land, and let the AI respond; confirm turn/phase progression, the prompt bar, `TargetingOverlay` targeting, and combat (`VAssignCombatDamage`) all work without errors or stalled transitions.
- [ ] Monitor for several turns: inspect `app.log`/`agent.log` and repeated `ui.txt` dumps for exceptions, stuck prompts, or layout breakage; confirm the Commander zone, commander damage/tax state, and `GameLogPanel` render correctly. No reproducible errors.
- [ ] **Phase 3 gate:** after a clean build + relaunch re-playing a 2-player match with zero reproducible runtime errors, mark items `[x]`, update `PROGRESS.md`, and make an atomic local commit on `dev` (zero-diff guard first).

## Phase 4 — 3-Player Commander Match Play & Monitoring
Targets: same as Phase 3 (3-seat layout/seat handling), plus `forge.screens.match.*` multi-seat targeting and state.

- [ ] From the Commander lobby configure a **3-player** match (1 human + 2 AI) and start it; confirm the match screen appears, all three seats' zones render, and the 3-player layout does not overlap/clip. No exceptions.
- [ ] Advance play across seats: cast spells, resolve multi-target/multi-opponent prompts and `TargetingOverlay` for a 3-way table, and confirm combat and priority pass correctly between all three players. No errors/stalls.
- [ ] Monitor for several turns: inspect logs + `ui.txt` for exceptions, stuck prompts, or per-seat layout corruption; confirm all three players' life/commander state and `GameLogPanel` stay consistent. No reproducible errors.
- [ ] **Phase 4 gate:** after a clean build + relaunch re-playing a 3-player match with zero reproducible runtime errors, mark items `[x]`, update `PROGRESS.md`, and make an atomic local commit on `dev` (zero-diff guard first).

## Phase 5 — 4-Player Commander Match Play & Monitoring
Targets: same as Phase 3/4 (4-seat layout/seat handling), plus `forge.screens.match.*` multi-seat targeting and state.

- [ ] From the Commander lobby configure a **4-player** match (1 human + 3 AI) and start it; confirm the match screen appears, all four seats' zones render, and the 4-player layout does not overlap/clip. No exceptions.
- [ ] Advance play across seats: cast spells, resolve multi-target/multi-opponent prompts and `TargetingOverlay` for a 4-way table, and confirm combat and priority pass correctly between all four players. No errors/stalls.
- [ ] Monitor for several turns: inspect logs + `ui.txt` for exceptions, stuck prompts, or per-seat layout corruption; confirm all four players' life/commander state and `GameLogPanel` stay consistent. No reproducible errors.
- [ ] **Phase 5 gate:** after a clean build + relaunch re-playing a 4-player match with zero reproducible runtime errors, mark items `[x]`, update `PROGRESS.md`, and make an atomic local commit on `dev` (zero-diff guard first).

## Phase 6 — Final Clean Build, Relaunch & End-to-End Zero-Runtime-Error Verification
Targets: whole `forge-gui` + `forge-gui-desktop` surface exercised end-to-end.

- [ ] Full clean build: `mvn clean package -pl forge-gui-desktop -am -DskipTests -T 1C` exits 0 and the shaded jar exists.
- [ ] Sync-compat tests green: `mvn test -T 1C` and `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` pass.
- [ ] Relaunch with the UI agent and re-run the full pass: Phase 1 (navigation/menus/settings), Phase 2 (deck select/edit/import + Commander setup), and one each of a 2p, 3p, and 4p Commander match.
- [ ] Confirm **zero reproducible runtime errors** across the entire pass; if any error appears, fix within `forge-gui`/`forge-gui-desktop` only → clean build → relaunch → re-verify → commit, then re-run the pass until clean.
- [ ] Zero-diff guard: `git status --short -- forge-core forge-game forge-ai` is empty and the Phase 6 commit touches only `forge-gui`/`forge-gui-desktop` (+ `PLAN.md`/`PROGRESS.md`).
- [ ] Cleanup: stop all test Java processes, remove the scratch dir (e.g. `/tmp/forge-ui-e2e`), and confirm `git status --short` has no untracked test/debug artifacts.
- [ ] **Final gate:** update `PROGRESS.md`, mark all Phase 6 items `[x]`, and make the final atomic local commit on `dev`. Do **not** `git push`.
