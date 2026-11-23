# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE (per AGENTS.md/PLAN.md): `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Full final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- `TrackableProperty` ordinals are replay-stable — leave unused enum entries in place.
- `ApiType.smartValueOf` is a strict lookup at ability-BUILD time, but `CardScriptParser.isAbilityApiLegal` wraps it in try/catch (lenient for unknown `DB$`/`Mode$` names in card data). Cards without a `Set:` line are unassigned/illegal in Commander → unparseable PlanarConquest card files degrade safely at load.

## Recently completed
- **18.6** — deleted 11 dead mechanic classes: `PlanarDice`, `RunChaosEffect`, `Replace{Planeswalk,RollPlanarDice,PlanarDiceResult,SetInMotion}`, `Trigger{PlaneswalkedFrom,PlaneswalkedTo,PlanarDice,SetInMotion,RingTemptsYou}` + `TriggerType` (5) / `ReplacementType` (4) / `ApiType.RunChaos` entries + `SpellApiToAi` mapping; stripped `CardTraitBase`/`ReplaceEffect` PlanarDice parsing branches; dropped `choosePDRollToIgnore` from `PlayerController`/`PlayerControllerAi`/`PlayerControllerHuman`/`PlayerControllerForTests`; rebased `GameEventRollDie` to `(int, int)` (generic dice-roll sound event kept). Kept `AbilityKey.Scheme` (live Abandon mechanic). GATE green (2686 tests, 0 failures).
- **18.7** — no-op: verified SubgameEffect vanguard-avatar subgame block already removed in 18.4 (commit 52193ec8f47; zero `vanguard` refs left in `SubgameEffect.java`). GATE green (2686 tests, 0 failures).

## Active caveats / follow-ups
- Left orphaned (compile-safe, later items): `Card.ringBearer` + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName` / `GameView.updatePlanarPlayer`, `DeckFormat.getPlaneSectionConformanceProblem` (→25.2), `TriggerChaosEnsues` + `TriggerType.ChaosEnsues` (orphaned by 18.5/18.6 → 25.2), i18n `lblPlaneswalkedFrom`/`lblPlaneswalkedTo`/`lblPlanarDiceResult` (→24.1), `docs/Card-scripting-API/Triggers.md` planar sections (→25.2).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
- Next: 19.1 — delete dead deck-gen classes in `forge-gui/.../deck/` (`RandomDeckGenerator`, `CardThemedDeckGenerator`, `ArchetypeDeckGenerator`, `ColorDeckGenerator`, `DeckGeneratorTheme`, `CardRelationMatrixGenerator`, `CardArchetypeLDAGenerator`, `io/CardThemedMatrixIO`, `io/CardThemedLDAIO`); `rg` all references first.
