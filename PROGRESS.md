# PROGRESS.md — Pre-rewrite cleanup (Part 2: team/2HG removal)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms).
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.

## Completed items
- Part 2 / 1.1 Turn/phase/combat team mechanics removed, rebased to single-player: PhaseHandler (deleted teamLead/nextTurnLead → `nextTurn()`,
  `getActivePlayers()` singleton, shared-turn skip loops, combined team attack/block loops, CR 805.5/805.7/805.8/805.10 sites), Untap.doDayTime identity,
  Combat.isPlayerAttacked identity, CombatUtil (validateTeamBlock deleted), AttackConstraints single attacker, MagicStack identity,
  TriggerAttackersDeclared + AbilityKey.OneOfAttackingTeam deleted. `TwoHeadedGiantTest.java` deleted in 1.1.
- Part 2 / 1.2 Spell/trigger/ability team mechanics removed: SpellAbilityEffect (`getTargetPlayersOnePerTeam` deleted; `addToCombat`
  sharesTeamWith→equals), AddTurn/SkipTurn/SkipPhase use `getTargetPlayers`, CountersProliferateEffect poison-team dedupe deleted (dead code),
  AbilityUtils (PlayerCountYourTeam + LifeYourTeamGainedThisTurn + LifeGainedByTeamThisTurn xMath branches deleted),
  TriggerPhase CR 805.4d team-step block + Trigger.`refersToTriggering` deleted, CardView hand case → self,
  PlayerView (SharedTeam trackable usage + `sharesTeamWith` + `getCommanderInfo` 2HG branch deleted; `updateOpponentsAndTeam`→`updateOpponents`),
  `PlayerController.playedByTeammate` deleted + 3 call sites (AI/Human/test controllers). GATE green: 2709 run / 11 skipped, 0 F/E.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- Player team API (`Player.sharesTeamWith`/`getSharedTeam`/`sameTeam`/`getTeamMates`/`getYourTeam`/`teamNumber`) still exists — removed in 1.4;
  `hasSharedTeamTurns` in 1.5 (remaining refs: Player L3835/L3841, TriggerPhase gone, HostedMatch L120 in 1.3).
- PULLED FORWARD into 1.2 (plan listed under 1.3): PlayerView-based `sharesTeamWith` call sites rebased to `equals` — InputPassPriority:222,
  YieldController L167/L188, CMatchUI.updatePhase, PlayerControllerHuman.isUiSetToSkipPhase. 1.3 only has GameAction/HostedMatch left.
- `lblCommanderDealNDamageToTeam` i18n key now orphaned (en-US only) — goes in 2.5.
- `Player.setTeam(...)` lines in AI tests (AIIntegrationTests, FlatMonteCarlo*) stay until 1.4.
- GATE covers forge-core/game/ai/gui/gui-desktop tests; final phase 4.1 gate is GATE + PKG.
- Pre-existing orphan i18n keys (quest/gauntlet/puzzle/adv*/misc) left as follow-ups.
