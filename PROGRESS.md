# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch: `dev` == `trim_code` == `aadb87daeaa`; working tree clean before PLAN.md was added.
- `PKG` (`mvn package -DskipTests -T 1C`) → BUILD SUCCESS (~14 s). Reactor modules resolve as
  Forge Parent, Forge Core, Forge Game, Forge AI, Forge Gui, Forge (artifactId `forge-gui-desktop`).
- `GATE` (`mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`)
  → BUILD SUCCESS, 2736 tests run, 0 failures/errors, 11 skipped (75 s TestSuite run).

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
