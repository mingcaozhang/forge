# PROGRESS.md — Pre-rewrite cleanup (Part 2: team/2HG removal)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms).
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.

## Completed items
- Part 2 / 1.1–1.5: game engine team/2HG mechanics removed + rebased single-player (Player team API gone,
  `YourTeam`/`YourTeamCtrl` script names kept, 2HG rule entry points gone).
- Part 2 / 2.1–2.5: GameLobby archenemy/team selection, lobby team/2HG/Archenemy/scheme UI,
  `LobbySlot`/`UpdateLobbyPlayerEvent` team fields, `FormatChoices.VARIANTS` → Vanguard + Planechase, and
  8 orphaned team i18n keys removed. GATE green: 2708 run / 11 skipped.
- Part 2 / 3.1: `FormatChoices.COMMANDER_GAME_TYPES` → Commander only; `VLobby.createDeckChooser`
  TinyLeaders/Oathbreaker/Brawl cases dropped; `PlayerPanel.formatUsesCommandZone` + `cmdDeckSelectorBtn`
  chain → Commander; `FDeckChooser.updateCustom` 3 DeckFormat cases dropped; `VSubmenuConstructed` comment
  updated. GATE green: 2708 run / 11 skipped.
- Part 2 / 3.2: removed 24 Oathbreaker/TinyLeaders/Brawl `*_P{1..8}_DECK_STATE` FPref entries + the 3
  `*_DECK_STATES` arrays from `ForgePreferences` (all refs gone after 3.1, verified with rg). GATE green:
  2708 run / 11 skipped.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (one APPROX scenario failed once; green in isolation and on full rerun).

## Active caveats / follow-ups
- `VLobby.populateDeckPanel` still has Oathbreaker/TinyLeaders/Brawl case labels (unreachable now; go with the
  GameType entries in a later part).
- `Player.getAllies()` (Player.java:350) now always-empty; NOT in 1.4 scope per plan — ~20 forge-ai call sites degenerate to empty no-ops.
- `GameStateEvaluator.getScoreForGameStateImpl` ally branch (teammates/teamLife vars, "Ally" debug lines) now dead but functionally
  correct (scores `2*aiLife`); left untouched, out of 1.4 scope.
- `lblArchenemy` still referenced by the `GameType.Archenemy` entry (stays until a later part).
- GATE covers forge-core/game/ai/gui/gui-desktop tests; final phase 4.1 gate is GATE + PKG. Pre-existing orphan i18n keys
  (quest/gauntlet/puzzle/adv*/misc) left as follow-ups.
