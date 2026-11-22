# PROGRESS.md — Pre-rewrite cleanup (Part 4 in progress, next: item 12.2)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 12.1: `DeckType` removed `NET_COMMANDER_DECK`/`NET_ARCHIVE_COMMANDER_DECK` + `CommanderOptions` now
  COMMANDER_DECK/PRECON_COMMANDER_DECK/PROVIDED_DECK_URL only. `DecksComboBox.refresh` collapsed to single
  `refresh(DeckType, boolean)` (no more Commander special-case, `gameType` param, or zero-caller 2-arg
  overload). `FDeckChooser.refreshDecksList` → `decksComboBox.refresh(deckType, isForCommander)`.
  PULLED FORWARD (compile-required, 11.1 precedent): all of 12.2's FDeckChooser deletions — `NetDeckArchiveCommander`
  field, `updateNetArchiveCommanderDecks`, both `deckTypeSelected` branches, both switch cases, `getState` +
  `getDeckTypeFromSavedState` branches, `updateNetDecks` ternary → always `ItemManagerConfig.NET_DECKS`.
  GATE exit 0 (2708 tests).
- 11.4: removed i18n keys `lblRandomCommanderDecks`, `lblRandomCommanderCard-basedDecks`,
  `lblRandomDecks` (zero Java refs before removal; singular `lblRandomDeck` KEPT). GATE exit 0.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **12.2** — FDeckChooser work was already pulled forward in 12.1 (compile-required); verify with `rg`
  that no `NET_COMMANDER_DECK`/`NET_ARCHIVE_COMMANDER_DECK` refs remain in FDeckChooser, then close `[x]`.
  Then 12.3: DELETE `NetDeckArchiveCommander.java`, `DeckProxy.getNetArchiveCommanderDecks` (L627),
  `NetDeckCategory` commander field + `case Commander:`, `ForgeConstants.NET_DECKS_COMMANDER_LIST_FILE`
  (L61) + `NET_ARCHIVE_COMMANDER_DECKS_LIST_FILE` (L71), `ItemManagerConfig.NET_COMMANDER_DECKS` (L120) +
  `NET_ARCHIVE_COMMANDER_DECKS` (L136), res `net-decks-commander.txt` + `net-decks-archive-duel-commander.txt`.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant
  branches + `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with
  GameType entries, later part).
