# PROGRESS.md — Pre-rewrite cleanup (Part 2: team/2HG removal)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms).
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.

## Completed items
- Part 2 / 1.1–1.5 Game engine team/2HG mechanics fully removed + rebased single-player (each item GATE-green;
  `Player` team API gone, `YourTeam`/`YourTeamCtrl` script names kept, 2HG rule entry points gone).
- Part 2 / 2.1 GameLobby archenemy/team selection removed: `lastArchenemy`, `applyVariantTeams`/`isEnoughTeams`, 2HG
  4-player check, `case TwoHeadedGiant` + `remove(TwoHeadedGiant)` lines, startGame isArchenemy/team capture.
  `forVariants` archenemy/scheme params stay (engine deferred).
- Part 2 / 2.2 Lobby UI team/2HG/Archenemy/scheme-deck UI removed: PlayerPanel team row (teamComboBox/aeTeamComboBox/
  populateTeamsComboBoxes/teamListener/getTeam/setTeam/getArchenemyTeam/setIsArchenemy/isArchenemy) + scheme controls
  (scm* buttons, CEditorVariant archenemy cmd); VLobby schemeDeck lists/panels/selectSchemeDeck/isPlayerArchenemy +
  populateDeckPanel Archenemy/ArchenemyRumble/TwoHeadedGiant cases; CLobby scheme deck list; `FScreen.DECK_EDITOR_ARCHENEMY`
  (orphaned; keeps `lblSchemeDeckEditor` at zero refs for 2.5). pch/vgd panel rows renumbered 5→4, 6→5. `VLobby.getSlot`
  now reads team/archenemy from `LobbySlot` (data model keeps them until 2.4). GATE green: 2708 run / 11 skipped.

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
- `RegisteredPlayer.teamNumber` + `getTeamNumber`/`setTeamNumber` now zero refs (last caller removed in 2.1) → drop in 2.4 or 9.1 sweep.
- `LobbySlot`/`UpdateLobbyPlayerEvent` still carry team/isArchenemy (2.4 scope); `LobbySlotAuthorizationTest` asserts them (2.4).
- Orphaned i18n keys (java refs gone): `lblTeamWon` (1.3), `lblCommanderDealNDamageToTeam` (1.2), `lblTwoHeadedGiantNeedsFourPlayers` +
  `lblNotEnoughTeams` (2.1), `lblTeam`/`lblHeroes`/`lblSelectaSchemeDeck`/`lblSchemeDeckEditor` (2.2) → 2.5. `lblArchenemy` still
  referenced by the `GameType.Archenemy` entry (stays until a later part).
- GATE covers forge-core/game/ai/gui/gui-desktop tests; final phase 4.1 gate is GATE + PKG. Pre-existing orphan i18n keys
  (quest/gauntlet/puzzle/adv*/misc) left as follow-ups.
