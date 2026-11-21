# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (work started from plan commit `399acbbd29a`, clean tree).
- `PKG` → BUILD SUCCESS. `GATE` baseline → 2736 tests, 0 failures/errors, 11 skipped (~75 s).
- `Localizer.getMessage` falls back locale → en-US (forge-core .../util/Localizer.java:97-114):
  deleting non-EN key lines is safe when en-US is edited in place.
- forge-core/forge-ai have no test sources; GATE reports only for forge-game/gui/gui-desktop.
  `HomeNavigationRegistryTest` parses sources and enforces the surfaced-submenu set.

## Completed items
- 1.1 Content Downloader: deleted CSubmenu/VSubmenuDownloaders + 8 download services + 2 tests;
  surgical EDocID/VHomeUI/home.xml/test edits; i18n keys gone from all 10 locales. `GATE` green.
- 2.1 Release Notes: deleted CSubmenu/VSubmenuReleaseNotes; surgical EDocID/VHomeUI/HelpMenu/test edits;
  orphaned `CHANGES_FILE*` consts + `TextUtil.getFormattedChangelog`; i18n gone from all 10 locales. `GATE` green.
- 3.1 How To Play tutorial: deleted `forge.gamemodes.tutorial` (7 classes), `TutorialOverlayPanel`,
  `res/tutorial/HowToPlay.pzl`, `res/howto.txt`. Surgical: IGuiBase (coach/layout/highlight defaults),
  GuiDesktop (currentMatchUI + overrides), CMatchUI (overlay + doc accessor + detach), ViewWinLose
  (ternaries collapsed; `GameType.Puzzle` branch kept), VHomeUI (panel + height math), HelpMenu (file item),
  HostedMatch (start/endGameHook → `match.startGame(currentGame, null)`; `Match` Runnable seam kept),
  ForgeConstants (HOWTO_FILE/TUTORIAL_DIR/USER_PUZZLE_DIR). i18n: 195 en-US + 99/non-EN tutorial keys,
  `lblHowToPlayTooltip`/`lblHowtoPlay`, `#VSubmenuTutorial`/`lblTutorial`/`lblTutorialMode` removed;
  `lblRestartPuzzle`/`lblQuitPuzzle` + dev `lblTutor` kept. `GATE` → 2736 tests, 0 failures/errors.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false
  -Dsurefire.failIfNoSpecifiedTests=false` (Surefire 3.1.2 fails early modules).
- Non-failing test-log noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- i18n key removal requires `rg -n "<key>" --type java` zero refs first; 10 locale files must stay consistent.
- `res/lists/commander-bracket-combos.txt` feeds AI combo knowledge → keep data, rename in 6.3.
- Per-format banlist validation (`DeckFormat`, `res/formats/**`) is NOT the Commander bracket system — keep.
- Vanguard "avatar" feature (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `AvatarVanguard`) is unrelated — keep.
- `GameType.Puzzle` core remnants and pre-existing quest/gauntlet/puzzle i18n orphans are out of scope.
- Networking files get minimal field-level edits only (networking removal is a later iteration).
- `res/defaults/home.xml` is legacy (stale HOME_* ids); per plan only item-specific lines get removed.
