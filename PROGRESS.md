# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE (per AGENTS.md/PLAN.md): `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- `TrackableProperty` ordinals are replay-stable — leave unused enum entries in place.
- `ApiType.smartValueOf` is a strict lookup at ability-BUILD time, but `CardScriptParser.isAbilityApiLegal` wraps it in try/catch (lenient for unknown `DB$`/`Mode$` names in card data). Cards without a `Set:` line are unassigned/illegal in Commander → unparseable card files degrade safely at load.

## Recently completed
- **20.1** — deleted 9 `NetDeck*` classes + 8 `res/lists/net-decks*.txt` + `ForgeConstants` `NET_DECKS_LIST_FILE`/7×`NET_ARCHIVE_*_DECKS_LIST_FILE`/`DECK_NET_DIR`/`DECK_NET_ARCHIVE_DIR`/`DECK_NET_EVENT_DIR` (+ PROFILE_DIRS entries). Rebased `FDeckChooser` (all net-deck UI: fields, `updateNet*`, combo load handlers, `refreshDecksList` NET_* cases, state save/restore — stale saved `NET_*` prefixes now hit the `DeckType.valueOf` catch → default), `DeckProxy` (`getNetDecks`/`getNetArchive*`/`getAllNetworkEventDecks`, pulled from 20.3), `CardCollections` (`networkEvent` storage, pulled from 20.2). KEPT `ItemManagerConfig.NET_DECKS` (used by kept Provide-Deck-Url path). GATE green (2685).
- **19.x** — deck-gen strip complete: 16 dead generator classes + `DeckgenUtil` trimmed to `randomSelect`/`suggestBasicLandCount`, `FPref.DECKGEN_CARDBASED` gone, `forge-core` `deck/generation` package + `DeckFormat.getCardPool`/`adjustCMCLevels` deleted, `res/deckgendecks/` + EDHREC matrix toolchain + stale deck-gen docs removed. GATE+PKG green.

## Active caveats / follow-ups
- Left orphaned (compile-safe, later items): `Card.ringBearer` + `CardProperty.IsRingbearer` (→25.2), `TrackableProperty.RingLevel`/`IsRingBearer` (replay stability), `PlayerView.updateCurrentPlaneName`/`GameView.updatePlanarPlayer`, `DeckFormat.getPlaneSectionConformanceProblem` (→25.2), `TriggerChaosEnsues` + `TriggerType.ChaosEnsues` (→25.2), i18n `lblPlaneswalkedFrom`/`lblPlaneswalkedTo`/`lblPlanarDiceResult` (→24.1), `docs/Card-scripting-API/Triggers.md` planar sections (→25.2).
- Orphaned by 19.x/20.1 (→20.x/21.1): `ItemManagerConfig.{PRECON_DECKS, NET_ARCHIVE_*_DECKS, NET_EVENT_DECKS}`, `DeckProxy.getAllPreconstructedDecks`, `VLobby.createDeckChooser` `default:` branch, `DECKGEN_{NOSMALL,ARTIFACTS,SINGLETONS}` prefs + `ConstructedGameMenu` (Game menu in `CSubmenuConstructed`)/`CLobby`/`CSubmenuPreferences` wiring — the prefs now have no consumer (generators gone since 19.1/19.2), i18n `cbCardBased`/`nlCardBased` (→24.1). `FModel.getArchenemyCards/getPlanechaseCards` stay (CEditorConstructed pools, D1a/D3).
- `Player.getPlaneswalkersInPlay` is real MTG (Planeswalker-card) code used by AI/combat — KEEP.
- Next: 20.2 — dead `CardCollections` getters (`getDraft`/`getSealed`/`getWinston`/`getScheme`/`getPlane`/`getGeneticAIDecks`/`getCustomStarterDecks`) + `ForgeConstants` dir consts + `USER_DIRS`/PROFILE_DIRS entries (GATE).
