# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE: `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- Stale `target/` once caused 364 false `NoClassDefFoundError` failures; `mvn clean` fixes — if GATE fails with NoClassDefFound on untouched classes, clean before debugging.

## Recently completed
- **25.2** — FINAL SWEEP done. Deleted 14 orphan classes (`FDraftOverlay`, `DraftTimerRope`, `ConstructedGameMenu`, `DeckGroup`(+Serializer), `ChaosBoosterSupplier`, `IUnOpenedProduct`/`UnOpenedProduct`/`UnOpenedMeta`, `BoxedProduct`/`FatPack`/`BoosterBox`/`TournamentPack`/`PreconDeck`) + 4 blockdata txts + 2 draft sprites; rebased `CardEdition`/`StaticData`/`ItemPredicate`/`MetaSet`/`CardBlock`/`FModel`/`FSkin`/`FSkinProp`/`ForgeConstants`/`FScreen`/`FPref` + lobby/prefs UI + dead engine methods (`GameState.puzzleCreatorState` block, plane methods, `ViewWinLose` switch, `PreconDeck` branches). KEPT-by-verification: `DraftEffect`+`ApiType.Draft` (live spellbook cards in `res/cardsfolder`), `TriggerChaosEnsues`/`TriggerType.ChaosEnsues` (10+ plane cards), `CardProperty.IsRingbearer` (LOTR cards), booster/print-sheet chain (`BoosterGenerator.tryGetStaticSheet`→`getPrintSheets` → `printsheets.txt` live; `SealedProduct`/`SealedTemplate*`/`PrintSheet` live via `MakeCardEffect`), `MetaSetType`/`MetaSet`/`CardBlock` (data-driven from blocks.txt), `FDeckChooser.getSelectedDeckType` instance method, `PACK_OR_DECK` UI, quest-saves migration. i18n: 31 keys removed from all 10 locales (13 orphaned by sweep + 8 vanguard UI + 7 `lblDraftOverlay*` + 3 deckgen); full re-scan clean. GATE (2685) + PKG exit 0.
- **25.1** — no-op: archived/block formats + `FPref.LOAD_ARCHIVED_FORMATS` + `FormatChoices.ARCHIVED` kept by decision D2.
- **24.1** — i18n orphan sweep: 81 keys deleted from all 10 locale files. GATE green (2685).
- **23.1–23.3** — gauntlet defaults, zero-ref image lists, quest/booster image lists + product image routing removed; token/card pics + `COMMANDER_PRECON_DIR` + quest-saves migration kept. GATE+PKG green.
- **22.1–22.2** — `GameType` pruned to `Constructed`+`Commander`; `DeckFormat` to `Constructed`/`Commander`/`Pauper`/`Vanguard`. GATE green.

## Active caveats / follow-ups
- `FDeckChooser.updateCustom` `default:` branch kept (reachable — constructed decks still selectable in lobby via `CONSTRUCTED_DECK` type).
- Product `getImageKey` methods + `ImageKeys` product prefixes kept: `InventoryItem.java:28` interface obligation (zero callers, compile-safe).
- `TrackableProperty.RingLevel`/`IsRingBearer` + `Card.ringBearer`/`CardProperty.IsRingbearer` kept: replay-ordinal stability + live LOTR card data.
- i18n scan method: keys = `grep -E '^[A-Za-z0-9_]+=' en-US.properties | cut -d= -f1`; zero-ref = no `rg -F --glob '*.java'` hit AND no non-Java hit outside PLAN/PROGRESS; dynamic key builders to watch: `CardTranslation` `lbl<TokenType>`, `InputBase` `lbl<Day>`.
- ALL PLAN.md ITEMS COMPLETE — refactor goal achieved.
