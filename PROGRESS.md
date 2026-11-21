# PROGRESS.md — Pre-rewrite cleanup (Part 2: team/2HG removal)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms).
- Prior iteration (avatar/bracket/tutorial/content-download/release-notes) complete; baseline PKG+FULL green (2735 run / 11 skipped, 0 F/E).
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.

## Completed items
- Part 2 / 1.1 Turn/phase/combat team mechanics removed, rebased to single-player: PhaseHandler (deleted teamLead/nextTurnLead → `nextTurn()`, `getActivePlayers()` singleton,
  shared-turn skip loops, combined team attack/block loops, CR 805.5/805.7/805.8/805.10 sites), Untap.doDayTime identity, Combat.isPlayerAttacked identity,
  CombatUtil (canBeBlocked identity, validateBlocks direct, validateTeamBlock deleted), AttackConstraints single attacker, MagicStack identity,
  TriggerAttackersDeclared + AbilityKey.OneOfAttackingTeam deleted, PlayerControllerAi dead team-block branch deleted.
- `TwoHeadedGiantTest.java` deleted in 1.1 (pulled forward from 1.4 — it asserted the removed mechanics; 1.4 keeps only the setTeam-line stripping).
- GATE green after 1.1: 2710 run / 12 skipped, 0 failures/errors, all 6 modules SUCCESS.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- Player team API (`sharesTeamWith`/`getSharedTeam`/`sameTeam`/`getTeamMates`/`getYourTeam`/`teamNumber`) still exists — removed in 1.4; `hasSharedTeamTurns` in 1.5.
- `Player.setTeam(...)` lines in AI tests (AIIntegrationTests, FlatMonteCarlo*) stay until 1.4.
- GATE covers forge-core/game/ai/gui/gui-desktop tests; final phase 4.1 gate is GATE + PKG.
- Pre-existing orphan i18n keys (quest/gauntlet/puzzle/adv*/misc) left as follow-ups.
