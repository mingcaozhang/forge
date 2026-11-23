# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE (per AGENTS.md/PLAN.md): `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- `TrackableProperty` ordinals are replay-stable — leave unused enum entries in place.
- `ApiType.smartValueOf` is a strict lookup at ability-BUILD time, but `CardScriptParser.isAbilityApiLegal` wraps it in try/catch (lenient for unknown `DB$`/`Mode$` names in card data). Cards without a `Set:` line are unassigned/illegal in Commander → unparseable PlanarConquest card files degrade safely at load.

## Recently completed
- **19.1** — deleted 16 dead deck-gen files: the 9 plan-listed classes + `gamemodes/limited/{CardThemedDeckBuilder,CardThemedCommanderDeckBuilder}` (confirmed dead: only callers were dead `DeckgenUtil` methods) + orphans `io/Archetype`, `gamemodes/limited/{ArchetypeDeckBuilder,DeckColors,FullDeckColors}`, empty-package `package-info`. Rebased: `DeckgenUtil` (kept `randomSelect`, `suggestBasicLandCount`, `buildColorDeck`, precons + random-or-precon helpers for 19.2), `DeckProxy` (no `ThemeDeckGenerator`/`getAllThemeDecks`), `FModel` (deckgen-matrix load + `isdeckGenMatrixLoaded` gone — pulled fwd from 19.3), `FDeckChooser` (no color/matrix/theme branches — pulled fwd from 20.5), `DeckType.ConstructedOptions` (single list, no CARDGEN). GATE green (2686 tests, 0 failures).
- **18.7** — no-op: verified SubgameEffect vanguard-avatar block already removed in 18.4 (commit 52193ec8f47; zero `vanguard` refs left in `SubgameEffect.java`). GATE green (2686 tests, 0 failures).

## Active caveats / follow-ups
- Left orphaned (compile-safe, later items): `Card.ringBearer` + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName` / `GameView.updatePlanarPlayer`, `DeckFormat.getPlaneSectionConformanceProblem` (→25.2), `TriggerChaosEnsues` + `TriggerType.ChaosEnsues` (orphaned by 18.5/18.6 → 25.2), i18n `lblPlaneswalkedFrom`/`lblPlaneswalkedTo`/`lblPlanarDiceResult` (→24.1), `docs/Card-scripting-API/Triggers.md` planar sections (→25.2).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
- 19.2 targets (all now caller-less except noted): `DeckgenUtil` `buildColorDeck`, `getRandom*` (incl. `getRandomOrPreconOrThemeDeck`×2), `getCommanderDeck`, `randomSelectColors`, `colorCheck`, `generateSchemeDeck/Pool`, `generatePlanarDeck/Pool`; STILL LIVE: `randomSelect` (FDeckChooser), `suggestBasicLandCount` (AddBasicLandsDialog), `getPrecons` (FDeckChooser.updatePrecons → 20.5). `FPref.DECKGEN_CARDBASED` + `VSubmenuPreferences` cbCardBased checkbox → 19.3. `res/deckgendecks/` + `ForgeConstants.DECK_GEN_DIR` + `forge-gui/tools/` matrix tools/README → 19.5.
- Next: 19.2 — strip dead `DeckgenUtil` methods (KEEP `randomSelect`, `suggestBasicLandCount`).
