# PROGRESS.md — Pre-rewrite cleanup (Part 5 in progress: 14.2 + 15.4 done)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 14.2: Deleted `DeckFormat.Oathbreaker/Brawl/TinyLeaders` entries (incl. TinyLeaders ban set + `adjustCMCLevels`);
  `hasCommander` → `Commander || CommanderDraft`; deleted `hasSignatureSpell`, Oathbreaker conformance block,
  O/B/TL branches in `isLegalCommander`/`isAllowedCommander`/`getPartnerableCommander`/`getIllegalCommanderWarning`,
  `Deck` signature-spell sort + `getOathbreaker`/`getSignatureSpell`, `CardRules` 4 `canBe*` methods,
  `CardRulesPredicates` 4 predicates, `StaticData` O/B predicate fields + accessors, `DeckSection.COMMANDER_VALIDATOR`
  O/B terms, `GameFormat` Brawl/Oathbreaker coreFormats, `FModel` O/B/TL suppliers + predicate init + getters.
  Pulled forward from 14.3 (compile-required): `DeckgenUtil` (Brawl branch, Oathbreaker partner switch/filter,
  `getRandomSignatureSpell`), `CardRelationMatrixGenerator` (Commander only), `CardThemedCommanderDeckBuilder`/
  `CardThemedDeckBuilder` (O/B branches), `ArchidektDeckUrlProvider` (Brawl id mapping), `CEditorConstructed`
  (`hasSignatureSpell` check). Pulled forward from 15.4: deleted the 4 mode-specific TinyLeaders `DeckRecognizerTest`
  tests (test `StaticData` has no format predicates → rework to another constrained format would NPE) + stripped 19
  inert O/B/TL comments. Deleted `res/formats/Casual/{Brawl,Oathbreaker}.txt`. GATE exit 0 (2703 tests = 2707 − 4).
- 14.1: Deleted `GameType.Oathbreaker/TinyLeaders/Brawl`; rebased engine — `GameRules.hasCommander` (Commander only),
  `Player` commander-damage check → `hasCommander()`, `createCommanderEffect` (Oathbreaker signature-spell stack→command +
  cast restriction, TinyLeaders/Oathbreaker return branches gone → 903.9b only), `RegisteredPlayer.forVariants` (3 blocks),
  `Zone` command-zone ordering, `CardView.updateCommander` (alt-type storage → "Commander" only; `getCommanderType()` removed,
  card text now hardcodes "Commander"), `MulliganService.firstMullFree`, `GameAction` commander SBA check.
  Because the enum constants went, the same commit also rebased their only other referrers (pre-work for 14.3/15.1/15.2):
  `RandomDeckGenerator` cases, `GameLobby` (applyVariant/removeVariant/startGame — `isOathbreakerMatch` etc. gone),
  `NetDeckCategory` (brawl/oathbreaker/tinyleaders maps+cases), `VLobby.populateDeckPanel`, `DeckManager`
  (Brawl set-codes override deleted as pure pass-through; editDeck/deleteDeck cases), `CEditorConstructed`
  (editor-setup/deck-controller/card-limit cases, Oathbreaker add/context-menu/slot-swap branches, `commanderSlotKey`).
  GATE exit 0 (2708 tests).
- 13.1: Part-4 final sweep — `rg` over all removed Part-4 symbols matches only PLAN/PROGRESS docs. No orphaned i18n keys.
  GATE exit 0 (2708 tests) + PKG exit 0.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: 14.3 (remainder — see PLAN.md; the DeckgenUtil/matrix/themed-builder/Archidekt pieces were pulled into 14.2).
- 15.1/15.2 are largely pre-done by 14.1 (see above); when their turn comes, verify the listed spots are already clean,
  then GATE + commit. 14.3 still owes: `CardCollections`, `ForgeConstants` net-deck list/deck-dir constants, `DeckPreferences`,
  `ItemManagerConfig.OATHBREAKER_SECTION`, `war_room.txt` tokens, `docs/AI.md`, net-decks txt files.
- 15.4 done in 14.2 (tests were mode-specific — deleted, not reworked).
- `RegisteredPlayer.forVariants` keeps its now-unused `playerCount` param (single caller: `GameLobby`) — trim in a sweep.
- `TrackableProperty.CommanderAltType` kept (replay/trackable-enum stability); now always "Commander".
- Orphaned i18n keys awaiting 15.3: `lblasoathbreaker`, `lblassignaturespell` (live refs removed in 14.1) + the
  already-orphaned O/B/TL `lbl*` list in 15.3.
- Deferred (later parts): Archenemy* + scheme engine; ante; Puzzle remnants; `LEGACY_*` prefs; other non-Commander modes.
