# PROGRESS.md — Pre-rewrite cleanup (Part 4 complete; next part per REWRITE.md)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 13.1: Part-4 final sweep — `rg` over all removed Part-4 symbols (`RANDOM_COMMANDER_DECK`,
  `RANDOM_CARDGEN_COMMANDER_DECK`, `RANDOM_DECK`, `NET_COMMANDER_DECK`, `NET_ARCHIVE_COMMANDER_DECK`,
  `CommanderDeckGenerator`, `NetDeckArchiveCommander`, `EDITOR_DECKGEN`, `CDeckgen`/`VDeckgen`,
  `getRandomDecks`, `getFavoriteDeck`, `getNetArchiveCommanderDecks`, both net-decks list constants)
  matches only PLAN/PROGRESS docs. No orphaned i18n keys: deckgen-tab keys already gone (10.2),
  `RandomDeckGeneration` still used by `VSubmenuPreferences` deckgen section, other `lblNetArchive*Decks`
  back surviving deferred `NET_ARCHIVE_*` deck types, `lblRandomDeck` in use. GATE exit 0 (2708 tests) + PKG exit 0.
- 12.4: Removed orphaned i18n keys `lblNetCommanderDecks` + `lblNetArchiveCommanderDecks` from all 9 locale
  files. GATE exit 0 (2707 tests).

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Part 4 (10.1–13.1) complete. Next per REWRITE.md: remaining non-Commander constructed deck options
  (`COLOR_DECK`, `*_COLOR_DECK`, `*_CARDGEN_DECK`, `THEME_DECK`, `NET_DECK`, `NET_EVENT_DECK`, non-Commander
  `NET_ARCHIVE_*`, `PRECONSTRUCTED_DECK`, `QUEST_OPPONENT_DECK`, `CONSTRUCTED_DECK`) + the Constructed lobby
  (`cbSingletons`/`cbArtifacts` checkboxes in `VLobby.populateDeckPanel`).
- Deferred (later parts): `GameType` Brawl/Oathbreaker/TinyLeaders/Archenemy* + rule code + `DeckFormat`;
  Archenemy scheme engine; ante mechanics; Puzzle remnants; `LEGACY_*` prefs; other non-Commander modes.
- Dead until GameType entries go: `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels,
  `DeckManager` variant branches, `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields.
