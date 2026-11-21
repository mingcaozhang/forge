# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (work started from plan commit `399acbbd29a`, clean tree).
- `PKG` → BUILD SUCCESS. `GATE` baseline → 2736 tests, 0 failures/errors, 11 skipped (~75 s).
- `Localizer.getMessage` falls back locale → en-US bundle (forge-core .../util/Localizer.java:97-114),
  so deleting non-EN key lines is safe when en-US is edited in place.
- forge-core/forge-ai have no test sources; GATE reports only for forge-game/gui/gui-desktop.
  `HomeNavigationRegistryTest` parses sources (no skin stack needed) and enforces the surfaced-submenu set.

## Item 1.1 (Content Downloader) — done
- `GATE` green; rg sweep for deleted classes → zero. `lblHowToPlay` removed from all 10 locales
  (item 3.1 will only find `lblHowToPlayTooltip`/`lblHowtoPlay` left).

## Item 2.1 (Release Notes) — done
- Deleted CSubmenu/VSubmenuReleaseNotes; surgical edits in EDocID, VHomeUI (reg + hero-icon case),
  HelpMenu (menu entry + method), HomeNavigationRegistryTest (surfaced set + doc-id map).
- Removed orphaned `ForgeConstants.CHANGES_FILE`/`CHANGES_FILE_NO_RELEASE` (sole refs were deleted UI;
  bundled README.txt/CHANGES.txt + git-changelog-maven-plugin block untouched) and
  `TextUtil.getFormattedChangelog` (+ `changes` field + 6 orphaned imports; `getSimpleDate` kept).
- i18n: `ReleaseNotes` + `lblReleaseNotes` deleted from all 10 locales.
- `GATE` → BUILD SUCCESS, 0 failures/errors; `rg -i "releasenotes|release.notes" --type java` → zero.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must always be paired with `-am`; `-Dtest=` needs
  `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false` (Surefire 3.1.2 fails early modules).
- Non-failing test-log noise to ignore: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/` (e.g. `/tmp/opencode/forge-ui-e2e`).

## Active caveats / follow-ups
- i18n key removal requires `rg -n "<key>" --type java` to show zero refs first; 10 locale files in
  `forge-gui/res/languages/` must be edited consistently (en-US line numbers differ per locale).
- `res/lists/commander-bracket-combos.txt` feeds AI combo knowledge → keep data, rename in 6.3.
- Per-format banlist validation (`DeckFormat`, `res/formats/**`) is NOT the Commander bracket system — keep.
- Vanguard "avatar" feature (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `AvatarVanguard`) is unrelated — keep.
- `GameType.Puzzle` core remnants and pre-existing quest/gauntlet/puzzle i18n orphans are out of scope.
- Networking files get minimal field-level edits only (networking removal is a later iteration).
- `res/defaults/home.xml` is legacy (stale HOME_* ids); per plan only item-specific lines get removed.
