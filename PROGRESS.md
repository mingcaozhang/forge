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
- **18.2** — removed PlanarDice roll API + roll effects (432 lines, 3 files).
- **18.3** — removed Archenemy/planes/"The Ring" engine from `Player.java` (287 lines) + compile ripple: deleted `PlaneswalkEffect`/`SetInMotionEffect`/`RingTemptsYouEffect` + their `ApiType`/`SpellApiToAi` entries (pulled from 18.6), `AbilityUtils` RingTemptedYou/PlaneswalkedToThisTurn X-math, `GameState` ring state (field/save/load/ring-bearer/ring-level), `Game.onPlayerLost` planar loss blocks+locals (pulled from 18.5), `CardView`/`CardDetailUtil` ring display, `RestartGameEffect` 3 ring-reset lines. GATE green (2686 tests, 0 failures). 535 lines deleted, 0 added, 12 files.

## Active caveats / follow-ups
- 18.5 scope reduced: only `Game.activePlanes` field + `getActivePlanes`/`setActivePlanes` (L81, 1137-1140) + `GameCopier` (forge-ai/simulation L355-360) ripple remain.
- 18.6 list updated: `PlaneswalkEffect`/`SetInMotionEffect`/`RingTemptsYouEffect` + entries already gone; add `TriggerRingTemptsYou` (+ `TriggerType.RingTemptsYou` entry) and `PlayerControllerAi.choosePDRollToIgnore` (last Java user of `PlanarDice`).
- Left orphaned (compile-safe, later items): `Card.ringBearer` flag + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName` / `GameView.updatePlanarPlayer`, `AbilityKey.Scheme` (still used by `TriggerSetInMotion` → 18.6), `ReplacementType.Planeswalk`/`SetInMotion` + `Replace*` (→18.6), `PlanarDice` class / `ChaosEnsuesEffect` / `RunChaosEffect` (→18.6).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
