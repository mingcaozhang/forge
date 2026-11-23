# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE (per AGENTS.md/PLAN.md): `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Full final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- `TrackableProperty` ordinals are replay-stable — leave unused enum entries in place.
- `ApiType.smartValueOf` is a strict lookup at ability-BUILD time, but `CardScriptParser.isAbilityApiLegal` wraps it in try/catch (lenient for unknown `DB$`/`Mode$` names in card data). Cards without a `Set:` line are unassigned/illegal in Commander → unparseable PlanarConquest card files degrade safely at load.
- `RestartGameEffect` = Karn Liberated core effect (KEEP); only its The-Ring lines are PlanarConquest ripple.

## Recently completed
- **18.4** — removed Archenemy/ArchenemyRumble scheme assignment + planes/planeswalker/vanguardAvatars accessors from `RegisteredPlayer` (D1a). Ripple: `forVariants` reduced to `(appliedVariants, deck)`; `GameLobby` call site rebased; orphaned `DeckFormat.getSchemeSectionConformanceProblem` removed; SubgameEffect vanguard-avatar block pulled forward from 18.7. GATE green (2686 tests).
- **18.5** — removed `Game.activePlanes` field + `getActivePlanes`/`setActivePlanes` + `GameCopier` planechase copy block. Pulled forward from 18.6 (last Java users of `getActivePlanes`): `ChaosEnsuesEffect` + `ApiType.ChaosEnsues` + `SpellApiToAi` entry. GATE green (2686 tests, 0 failures).

## Active caveats / follow-ups
- 18.6 remaining: `PlanarDice`, `RunChaosEffect`, `Replace*`, `TriggerPlaneswalkedFrom`/`TriggerPlaneswalkedTo`/`TriggerPlanarDice`/`TriggerSetInMotion`/`TriggerRingTemptsYou` (+ `TriggerType.RingTemptsYou`), `PlayerControllerAi.choosePDRollToIgnore` (last Java user of `PlanarDice`), `AbilityKey.Scheme` (still used by `TriggerSetInMotion`), `ReplacementType.Planeswalk`/`SetInMotion`.
- 18.7 pulled forward: SubgameEffect vanguard-avatar block already removed in 18.4 (item now a no-op).
- Left orphaned (compile-safe, later items): `Card.ringBearer` flag + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName` / `GameView.updatePlanarPlayer`, `DeckFormat.getPlaneSectionConformanceProblem` (already orphaned → 25.2).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
