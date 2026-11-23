# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE (per AGENTS.md/PLAN.md): `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- `TrackableProperty` ordinals are replay-stable — leave unused enum entries in place.
- `ApiType.smartValueOf` is a strict lookup at ability-BUILD time, but `CardScriptParser.isAbilityApiLegal` wraps it in try/catch (lenient for unknown `DB$`/`Mode$` names in card data). Cards without a `Set:` line are unassigned/illegal in Commander → unparseable card files degrade safely at load.

## Recently completed
- **21.1** — removed all 39 rg-verified orphaned `ItemManagerConfig` constants (56→17; kept the live pool/section/deck-list configs incl. `NET_DECKS`, `CONSTRUCTED_DECKS`, `SIDEBOARD` was orphaned — Commander sideboard tab reuses `DECK_EDITOR` via `CEditorConstructed` L500-504). Ripple: 10 `SColumnUtil` column-default methods + collapsed 6-arg private `getCardColumns` (owned/price/new/decks flags dead), `ColumnDef.{NEW,PRICE,OWNED,DECKS,DECK_EVENT_TYPE,DECK_EVENT_PRODUCT,DECK_EVENT_DATE}` + `eventTag`/`eventFormatDisplay`, `DeckProxy.getEventTag`. Deleted `CEditorVariant` (only self-refs; its i18n keys still used by `CEditorConstructed`). i18n orphans →24.1: `lblNew`/`lblPrice`/`ttPrice`/`lblOwned`/`lblDecks`/`lblEventType`/`lblProduct`/`lblEventDate`. GATE green (2685).
- **20.6** — deleted data dirs `res/quest/precons/` (505), `res/quest/themes/` (68), `res/geneticaidecks/` (786) + orphaned `forge-gui/tools/missingPreconFiles.py`. KEPT `res/quest/commanderprecons/` + `res/lists/precon-images.txt` (live in `ImportSourceAnalyzer`; 23.3 territory). GATE+PKG green (2735).
- **20.5** — `FDeckChooser`: deleted `promptForDeck` + 1-arg `initialize(DeckType)` (rest of its stale list pulled forward in 19.1/20.1/20.4). GATE green (2685).

## Active caveats / follow-ups
- Left orphaned (compile-safe, later items): `Card.ringBearer` + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName`/`GameView.updatePlanarPlayer`, `DeckFormat.getPlaneSectionConformanceProblem` (→25.2), `TriggerChaosEnsues` + `TriggerType.ChaosEnsues` (→25.2), i18n `lblPlaneswalkedFrom`/`lblPlaneswalkedTo`/`lblPlanarDiceResult` (→24.1), `docs/Card-scripting-API/Triggers.md` planar sections (→25.2), `DeckGroup` + `DeckGroupSerializer`, `PlayerPanel.setDeckSelectorButtonText`, `DeckPreferences.getSelectedDeckType`/`setSelectedDeckType` statics (→25.2).
- `FModel.getArchenemyCards/getPlanechaseCards` are LIVE (`CEditorConstructed` L114-115 Schemes/Planes pools, D1a/D3) — earlier "orphan" suspicion was a case-sensitivity rg miss (getter is `getPlanechaseCards`, field `planechaseCards`).
- `DECKGEN_{NOSMALL,ARTIFACTS,SINGLETONS}` prefs + `ConstructedGameMenu` (Game menu, added by `CSubmenuConstructed` L53) + `CLobby` checkbox wiring: code is referenced (not orphaned) but the checkboxes no longer affect anything since the deck generators went in 19.x — inert live UI, no PLAN item targets it; leave for 25.2 judgment. i18n `cbCardBased`/`nlCardBased` still orphaned (→24.1).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
- Stale `target/` once caused 364 false `NoClassDefFoundError` failures (old inner-class files); `mvn clean` fixed — if GATE fails with NoClassDefFound on untouched classes, clean before debugging.
- Next: 21.2 — `CAllDecks` "All Decks" Constructed editor tab: verify reachability from the Commander flow; if unreachable, remove the constructed tab + `DeckProxy.getAllConstructedDecks` + `CardCollections.getConstructed`. GATE exit 0.
