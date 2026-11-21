# PROGRESS.md — Pre-rewrite cleanup (Part 2: team/2HG removal)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms).
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.

## Completed items
- Part 2 / 1.1–1.5 Game engine team/2HG mechanics fully removed + rebased single-player (each item GATE-green;
  `Player` team API gone, `YourTeam`/`YourTeamCtrl` script names kept, 2HG rule entry points gone).
- Part 2 / 2.1–2.2 GameLobby archenemy/team selection + lobby team/2HG/Archenemy/scheme UI removed (`lastArchenemy`,
  `applyVariantTeams`/`isEnoughTeams`, PlayerPanel team row + scheme controls, VLobby scheme panels +
  populateDeckPanel Archenemy/ArchenemyRumble/TwoHeadedGiant cases, `FScreen.DECK_EDITOR_ARCHENEMY`). `forVariants`
  archenemy/scheme params + `LobbySlot` team fields stay (2.4 / engine deferred).
- Part 2 / 2.3 `FormatChoices.VARIANTS` trimmed to Vanguard + Planechase (drives lobby Variants checkboxes +
  `restoreSetup` skip check; saved setups with dropped variants now skipped by design). GATE green: 2708 run / 11 skipped.
- Part 2 / 2.4 `LobbySlot` + `UpdateLobbyPlayerEvent` team/isArchenemy fields, ctor params, `teamUpdate`, getters,
  `apply()` lines, `toString` lines + `RegisteredPlayer.teamNumber`/getters all gone; 7 `new LobbySlot(` sites +
  `VLobby.getSlot` + `LobbySlotAuthorizationTest` updated to new signatures. `Player.isArchenemy()` (scheme-based,
  Archenemy engine) stays — deferred. GATE green: 2708 run / 11 skipped.
- Part 2 / 2.5 Removed 8 orphaned i18n keys (`lblTeam`, `lblHeroes`, `lblNotEnoughTeams`,
  `lblTwoHeadedGiantNeedsFourPlayers`, `lblSelectaSchemeDeck`, `lblSchemeDeckEditor`, `lblCommanderDealNDamageToTeam`,
  `lblTeamWon`) from all 10 locale files (62 lines; `lblAgainstTeam` already gone in 1.4, `lblSchemeDeck` kept per plan).
  GATE green: 2708 run / 11 skipped.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (one APPROX scenario failed once; green in isolation and on full rerun).

## Active caveats / follow-ups
- `Player.getAllies()` (Player.java:350) now always-empty; NOT in 1.4 scope per plan — ~20 forge-ai call sites degenerate to empty no-ops.
- `GameStateEvaluator.getScoreForGameStateImpl` ally branch (teammates/teamLife vars, "Ally" debug lines) now dead but functionally
  correct (scores `2*aiLife`); left untouched, out of 1.4 scope.
- `lblArchenemy` still referenced by the `GameType.Archenemy` entry (stays until a later part).
- GATE covers forge-core/game/ai/gui/gui-desktop tests; final phase 4.1 gate is GATE + PKG. Pre-existing orphan i18n keys
  (quest/gauntlet/puzzle/adv*/misc) left as follow-ups.
