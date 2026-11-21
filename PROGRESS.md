# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (work started from plan commit `399acbbd29a`, clean tree).
- `PKG` → BUILD SUCCESS (~14 s). `GATE` baseline → 2736 tests, 0 failures/errors, 11 skipped (~75 s).

## Item 1.1 (Content Downloader) — done
- `GATE` after removal: 2736 tests, 0 failures/errors, 11 skipped; rg sweep for deleted classes → zero.
- `Localizer.getMessage` falls back locale → en-US bundle (forge-core .../util/Localizer.java:97-114),
  so deleting non-EN `lblFirstRunBulkCdnPrompt` lines is safe (en-US reworded in place).
- Also removed `lblImportPictures` (sole Java ref was in deleted VSubmenuDownloaders; would be a new orphan).
- Pre-existing i18n orphans left in place (zero Java refs even before 1.1): `lblDownloadCardImages`,
  `lblDownloadCJKFontPrompt` (all locales).
- `lblHowToPlay` removed from all 10 locales (Downloaders section AND the tutorial-block occurrence in the
  9 non-EN files) — item 3.1 will only find `lblHowToPlayTooltip`/`lblHowtoPlay` left.
- `res/defaults/home.xml` is legacy (many stale HOME_* ids); only the `HOME_UTILITIES` line removed per plan.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must always be paired with `-am`; `-Dtest=` needs
  `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false` (Surefire 3.1.2 fails early modules).
- Non-failing test-log noise to ignore: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/` (e.g. `/tmp/opencode/forge-ui-e2e`).

## Active caveats / follow-ups
- i18n key removal requires `rg -n "<key>" --type java` to show zero refs first; 10 locale files in
  `forge-gui/res/languages/` must be edited consistently (en-US uses different line numbers than each locale).
- `res/lists/commander-bracket-combos.txt` feeds AI combo knowledge → keep data, rename in 6.3.
- Per-format banlist validation (`DeckFormat`, `res/formats/**`) is NOT the Commander bracket system — keep.
- Vanguard "avatar" feature (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `AvatarVanguard`) is unrelated — keep.
- `GameType.Puzzle` core remnants and pre-existing quest/gauntlet/puzzle i18n orphans are out of scope.
- Networking files get minimal field-level edits only (networking removal is a later iteration).
