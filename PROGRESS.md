# PROGRESS.md — Pre-rewrite cleanup (Part 4 in progress, next: item 11.4)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 11.3: `RandomDeckGenerator` → user-deck-only: removed `getRandomDecks`, `Generated`/`Favorite`
  `RandomDeckType` entries (dropped the enum + `type`/`name` fields), `getFavoriteDeck`, `compareTo`+`Comparable`;
  `getDeck()` → `getUserDeck()`. Sole external caller `VLobby:600` (`getRandomUserDeck`, planar "Random") kept.
  GATE exit 0 (2708 tests).
- 11.2: removed now-dead `FDeckChooser.updateRandomCommander`/`updateRandomCardGenCommander`/`updateRandom`
  (switch cases already gone in 11.1) + deleted `forge-gui/.../deck/CommanderDeckGenerator.java` (its
  `getBrawlDecks` was internal-only). KEPT `DeckgenUtil.generateRandomCommanderDeck` (still called by
  `DeckgenUtil.generateCommanderDeck` L587 — the no-saved-deck fallback). GATE exit 0 (2708 tests).
- 11.1: removed `DeckType` entries `RANDOM_COMMANDER_DECK`/`RANDOM_CARDGEN_COMMANDER_DECK`/`RANDOM_DECK`
  from `CommanderOptions`/`ConstructedOptions`; collapsed `CommanderOptions` into one unconditional array
  (`FModel` import kept for the ConstructedOptions branch). Pulled forward (compile-required): `FDeckChooser`
  switch (3 cases), `VLobby.updateImpl` (2 cases), `VLobby.createDeckChooser` Commander case → always
  `DeckType.COMMANDER_DECK` (drops AI-slot `RANDOM_CARDGEN_COMMANDER_DECK` default). GATE exit 0 (2707 tests).

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 4 item 11.4** (PLAN.md L122) — i18n: remove `lblRandomCommanderDecks`,
  `lblRandomCommanderCard-basedDecks`, `lblRandomDecks` across `forge-gui/res/languages/*`; KEEP singular `lblRandomDeck`.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant
  branches + `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with
  GameType entries, later part).
