# PROGRESS.md — Pre-rewrite cleanup (Part 2: team/2HG removal)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms).
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.

## Completed items
- Part 2 / 1.1 Turn/phase/combat team mechanics removed, rebased single-player (PhaseHandler, Untap, Combat, CombatUtil, AttackConstraints,
  MagicStack, TriggerAttackersDeclared, AbilityKey.OneOfAttackingTeam). `TwoHeadedGiantTest.java` deleted.
- Part 2 / 1.2 Spell/trigger/ability team mechanics removed (SpellAbilityEffect, AddTurn/SkipTurn/SkipPhase, CountersProliferateEffect,
  AbilityUtils team xMath branches, TriggerPhase CR 805.4d, CardView hand→self, PlayerView shared-team bits, playedByTeammate).
- Part 2 / 1.3 Team win/loss + game-level refs removed (GameAction checkGameOverCondition, GameEndReason.AllOpposingTeamsLost,
  GameOutcome.winningTeam, GameView/TrackableProperty WinningTeam+SharedTeam, ViewWinLose.composeTitle lblTeamWon, HostedMatch seat sort).
- Part 2 / 1.4 `Player` team API removed + last refs rebased + test cleanup: Player.java (teamNumber, getTeam/setTeam, sharesTeamWith,
  getSharedTeam, sameTeam, getTeamMates, getYourTeam, lifeGainedByTeamThisTurn, shared life/poison/commander-damage blocks;
  `isOpponentOf` → `other != null && other != this`; canGain/Lose/PayLife → self-only), Game.java ctor team auto-assign deleted,
  PlayerProperty "YourTeam" + CardProperty "YourTeamCtrl" → equals (names kept), PlayerPredicates.sameTeam deleted, 14 forge-ai files
  rebased to self/equals, AgainstAllOdds team tiers dropped (4-tier ctor: 3/7 individuals → common/uncommon; rare/mythic same desc,
  thresholds 3/4 unreachable), `lblAgainstTeam` removed from 10 locales, GameCopier setTeam line deleted, `setTeam(...)` stripped from
  22 AI test files, `GameStateEvaluatorTest.testAlliedResourcesImproveTeamScore` deleted (team-only premise). GATE green: 2708 run / 11 skipped.
- Part 2 / 1.5 2HG rule entry points removed: `GameRules.hasSharedTeamTurns` (zero refs), `getPoisonCountersToLose` → always returns
  `poisonCountersToLose` (2HG 15-branch gone), `RegisteredPlayer.forVariants` 2HG shared-life block deleted. GATE green: 2708 run / 11 skipped.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- `Player.getAllies()` (Player.java:350) now always-empty; NOT in 1.4 scope per plan — ~20 forge-ai call sites degenerate to empty no-ops
  (AiAttackController, SpellAbilityAi, TargetOnlyAi, PumpAi, ScryAi, DrawAi, ...). Candidate for later cleanup.
- `GameStateEvaluator.getScoreForGameStateImpl` ally branch (teammates/teamLife vars, "Ally" debug lines) now dead but functionally correct
  (scores `2*aiLife`); left untouched, out of 1.4 scope.
- `RegisteredPlayer.teamNumber` + `getTeamNumber` (zero refs) / `setTeamNumber` (only caller `GameLobby.startGame` L702) → 2.1/2.4.
- Orphaned i18n keys (java refs gone): `lblTeamWon` (1.3) + `lblCommanderDealNDamageToTeam` (1.2, en-US only) → 2.5.
- GATE covers forge-core/game/ai/gui/gui-desktop tests; final phase 4.1 gate is GATE + PKG. Pre-existing orphan i18n keys (quest/gauntlet/
  puzzle/adv*/misc) left as follow-ups.
