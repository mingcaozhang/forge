# PLAN.md — Pre-Rewrite Cleanup, Iteration 1: Remove Unused Features

## Context & Goal
Per `REWRITE.md`, this cleanup trims the JVM baseline BEFORE the rewrite phases so less code is ported. End state: a **single-player Commander (EDH)** experience (no Brawl/Oathbreaker/Tiny Leaders/Commander variants.

**KEEP (do not touch):** core Commander rules/gameplay, AI players, game UI/navigation, preferences for surviving features, deck editor + saved decks, in-turn undo, "Provide Deck URL", Moxfield/Archidekt import, card art fetching, deck validation, audio.

## Confirmed Decisions
1. Remove BOTH the interactive tutorial and the static `res/howto.txt` doc (stale 1v1-vs-computer intro) + its Help-menu entry.
2. Keep `DialogDownloadCardImages` + the first-run bulk-CDN-sync prompt in `FView` (card-art UX); reword the prompt text that references the removed section.
3. Lobby slot panels show the slot's **selected deck's commander card image**, falling back to the generic player icon (`FSkinProp.ICO_PLAYERS`) when no deck is selected.
4. "Bracket/banlist validation" = the Commander **bracket** (EDHREC-style skill-tier) system. Per-format **banlist** validation (`GameFormat.bannedCardNames`, `DeckFormat.isLegalCard/isLegalCommander`, `DeckRecognizer.isBannedInFormat`, `res/formats/**`) is separate and STAYS.
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
- Work locally on **`dev`** (currently on `trim_code`; both point at `aadb87daeaa` with a clean tree — check out `dev` first).
- **Atomic local commit per completed `- [ ]` item**: code changes + `PLAN.md` (`[ ]`→`[x]`) + `PROGRESS.md` (≤ 40 lines, next to `PLAN.md`) in ONE commit. Commit style: `chore(commander): <short summary>`.
- **NEVER** `git push`, **NEVER** `git reset --hard`, no destructive commands outside the workspace.
- Before deleting/renaming any symbol, file, i18n key, or menu entry, search all references first (`rg`); for i18n keys, `rg -n "<key>" --type java` must show zero remaining references before removal.
- All Maven commands run with a **600000 ms** timeout (see `forge-test-runner` skill).
- Scratch/test artifacts only under `/tmp/opencode/` (e.g. `/tmp/opencode/forge-ui-e2e`).

## Verification Commands
- `GATE` — `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms; builds+tests forge-core, forge-game, forge-ai, forge-gui, forge-gui-desktop)
- `PKG` — `mvn package -DskipTests -T 1C` (600000 ms)
- `FULL` — `mvn test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms)
