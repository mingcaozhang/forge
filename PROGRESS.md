# PROGRESS.md — Pre-rewrite cleanup (Part 4 in progress, next: item 11.2)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 11.1: removed `DeckType` entries `RANDOM_COMMANDER_DECK`/`RANDOM_CARDGEN_COMMANDER_DECK`/`RANDOM_DECK`
  from `CommanderOptions`/`ConstructedOptions`; collapsed `CommanderOptions` into one unconditional array
  (`FModel` import kept for the ConstructedOptions branch). Pulled forward (compile-required): `FDeckChooser`
  switch (3 cases), `VLobby.updateImpl` (2 cases), `VLobby.createDeckChooser` Commander case → always
  `DeckType.COMMANDER_DECK` (drops AI-slot `RANDOM_CARDGEN_COMMANDER_DECK` default). `updateRandomCommander`/
  `updateRandomCardGenCommander`/`updateRandom` now dead → go in 11.2. GATE exit 0 (2707 tests).
- 10.2: removed deck-generation i18n keys + orphaned `#VDeckgen.java` headers from all 10 locales.
- 10.1: deleted deck-gen tab (`CDeckgen`/`VDeckgen`, `EDocID.EDITOR_DECKGEN`, `editor.xml`,
  `CEditorVariant`/`CEditorTokenViewer` tab handling); stale user layouts self-heal.
- 9.1: Part 3 final sweep (AI sideboarding props + orphaned i18n keys). GATE + PKG exit 0. Part 3 complete.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 4 item 11.2** (PLAN.md L120) — `FDeckChooser`: remove now-dead `updateRandomCommander`/
  `updateRandomCardGenCommander`/`updateRandom` + DELETE `CommanderDeckGenerator`. KEEP
  `DeckgenUtil.generateRandomCommanderDeck` (fallback for `generateCommanderDeck`).
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant
  branches + `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with
  GameType entries, later part).
