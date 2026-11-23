# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE (per AGENTS.md/PLAN.md): `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- `TrackableProperty` ordinals are replay-stable — leave unused enum entries in place.
- `ApiType.smartValueOf` is a strict lookup at ability-BUILD time, but `CardScriptParser.isAbilityApiLegal` wraps it in try/catch (lenient for unknown `DB$`/`Mode$` names in card data). Cards without a `Set:` line are unassigned/illegal in Commander → unparseable card files degrade safely at load.

## Recently completed
- **19.5** — deleted `res/deckgendecks/` (16 `.dat` files) + `ForgeConstants.DECK_GEN_DIR`; also deleted the dead EDHREC matrix toolchain whose sole consumer was removed in 19.1 (`CardThemedMatrixIO`/FModel matrix load): `forge-gui/tools/{build_forge_matrix.py, edhrec_to_forge.py, README-edhrec_to_forge.md, java/ForgeMatrixWriter.java}` (emptied `tools/java/`) + stale `docs/Development/Deck-Generation.md` (references nonexistent `LDAModelGenerator`). Kept `tools/DeckConversionTools/` (generic converters, not deckgen-data-specific). GATE (2685 tests) + PKG green.
- **19.4** — deleted `forge-core` `deck/generation` package (`DeckGenPool`, `IDeckGenPool`, `DeckGeneratorBase`, `DeckGenerator{Mono,2,3,4,5}Color`) + `DeckFormat.getCardPool`/`adjustCMCLevels`; `CardDb` no longer `implements IDeckGenPool`; deleted `Generate{2,3,5}ColorDeckTest` + `CardArtPreferenceTest.testGeneratedDeckPoolKeepsThePreferredPrintWhateverTheOrder` (live print selection is `CardDb.getCardFromEditions`). GATE green (2685).
- **19.3** — removed `FPref.DECKGEN_CARDBASED` (enum entry) + `CSubmenuPreferences` binding + `VSubmenuPreferences.cbCardBased`. i18n `cbCardBased`/`nlCardBased` → 24.1.
- **19.2** — `DeckgenUtil` stripped to KEEP `randomSelect` (FDeckChooser) + `suggestBasicLandCount` (AddBasicLandsDialog); all build/getRandom/generate methods + `randomSelectColors`/`colorCheck` gone; `FDeckChooser` rebased (no precons case).
- **19.1** — deleted 16 dead deck-gen files (9 plan-listed + limited-package builders + orphans); rebased `DeckgenUtil`, `DeckProxy` (no ThemeDeckGenerator), `FModel` (no matrix load), `FDeckChooser`, `DeckType.ConstructedOptions` (single list).

## Active caveats / follow-ups
- Left orphaned (compile-safe, later items): `Card.ringBearer` + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName`/`GameView.updatePlanarPlayer`, `DeckFormat.getPlaneSectionConformanceProblem` (→25.2), `TriggerChaosEnsues` + `TriggerType.ChaosEnsues` (→25.2), i18n `lblPlaneswalkedFrom`/`lblPlaneswalkedTo`/`lblPlanarDiceResult` (→24.1), `docs/Card-scripting-API/Triggers.md` planar sections (→25.2).
- Orphaned by 19.x (→20.x/21.1): `ItemManagerConfig.PRECON_DECKS`, `DeckProxy.getAllPreconstructedDecks`, `VLobby.createDeckChooser` `default:` branch, `DECKGEN_{NOSMALL,ARTIFACTS,SINGLETONS}` prefs + `ConstructedGameMenu` (Game menu in `CSubmenuConstructed`)/`CLobby`/`CSubmenuPreferences` wiring — the prefs now have no consumer (generators gone since 19.1/19.2), i18n `cbCardBased`/`nlCardBased` (→24.1). `FModel.getArchenemyCards/getPlanechaseCards` stay (CEditorConstructed pools, D1a/D3).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
- Next: 20.1 — delete `NetDeck*` classes + `res/lists/net-decks*.txt` (8) + `ForgeConstants` NET_* (GATE).
