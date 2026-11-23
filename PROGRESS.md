# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE (per AGENTS.md/PLAN.md): `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- `TrackableProperty` ordinals are replay-stable — leave unused enum entries in place.
- `ApiType.smartValueOf` is a strict lookup at ability-BUILD time, but `CardScriptParser.isAbilityApiLegal` wraps it in try/catch (lenient for unknown `DB$`/`Mode$` names in card data). Cards without a `Set:` line are unassigned/illegal in Commander → unparseable PlanarConquest card files degrade safely at load.

## Recently completed
- **19.2** — `DeckgenUtil` stripped to KEEP `randomSelect` (FDeckChooser) + `suggestBasicLandCount` (AddBasicLandsDialog); removed `buildColorDeck`, `getRandomColorDeck`×2, `getRandomCustomDeck`, `getCommanderDeck`, `getRandomOrPreconOrThemeDeck`×2, `getRandomPreconDeck`, `getRandomCommanderPreconDeck`, `getPrecons` + `advPrecons`/`advThemes`/`geneticAI` fields, `randomSelectColors`, `colorCheck`, `generateSchemeDeck/Pool`, `generatePlanarDeck/Pool`. Rebased `FDeckChooser`: dropped `updatePrecons()` + `refreshDecksList` `PRECONSTRUCTED_DECK` case (falls to default no-op); `FOptionPane` colorCheck comment reworded. GATE green (2686 tests, 0 failures).
- **19.1** — deleted 16 dead deck-gen files: the 9 plan-listed classes + `gamemodes/limited/{CardThemedDeckBuilder,CardThemedCommanderDeckBuilder}` (confirmed dead: only callers were dead `DeckgenUtil` methods) + orphans `io/Archetype`, `gamemodes/limited/{ArchetypeDeckBuilder,DeckColors,FullDeckColors}`, empty-package `package-info`. Rebased: `DeckgenUtil`, `DeckProxy` (no `ThemeDeckGenerator`/`getAllThemeDecks`), `FModel` (deckgen-matrix load + `isdeckGenMatrixLoaded` gone — pulled fwd from 19.3), `FDeckChooser` (no color/matrix/theme branches — pulled fwd from 20.5), `DeckType.ConstructedOptions` (single list, no CARDGEN). GATE green (2686 tests, 0 failures).

## Active caveats / follow-ups
- Left orphaned (compile-safe, later items): `Card.ringBearer` + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName` / `GameView.updatePlanarPlayer`, `DeckFormat.getPlaneSectionConformanceProblem` (→25.2), `TriggerChaosEnsues` + `TriggerType.ChaosEnsues` (orphaned by 18.5/18.6 → 25.2), i18n `lblPlaneswalkedFrom`/`lblPlaneswalkedTo`/`lblPlanarDiceResult` (→24.1), `docs/Card-scripting-API/Triggers.md` planar sections (→25.2).
- Orphaned by 19.2: `ItemManagerConfig.PRECON_DECKS` (→21.1), `DeckProxy.getAllPreconstructedDecks(IStorage<PreconDeck>)` (→20.3). `VLobby.createDeckChooser` `default:` branch (`PRECONSTRUCTED_DECK`/`COLOR_DECK`) is a dead non-Commander leftover (→20.x/21.x). `FModel.getArchenemyCards/getPlanechaseCards` stay (CEditorConstructed pools, D1a/D3).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
- 19.3 targets: `FPref.DECKGEN_CARDBASED` + `VSubmenuPreferences` cbCardBased checkbox (FModel matrix-load part already done in 19.1). `res/deckgendecks/` + `ForgeConstants.DECK_GEN_DIR` + `forge-gui/tools/` matrix tools/README → 19.5.
- Next: 19.3 — remove `FPref.DECKGEN_CARDBASED` + prefs checkbox.
