# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 7 — 7.1 done, next 7.2)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.

## Completed items
- Part 3 (7.1): `GameRules.gamesPerMatch`/`gamesToWinMatch` + accessors, `FPref.UI_MATCHES_PER_GAME`,
  `HostedMatch.getDefaultRules` setGamesPerMatch, `VLobby` gamesInMatch combo/binder/frame (+getter,
  `START_ROW_*` consts, `SwingPrefBinders` import) + `CLobby` binder restore all gone. Interim
  single-game fixes (finalized in 7.2/7.4/7.5): `Match.isMatchOver()` = `!gameOutcomes.isEmpty()`,
  `isWonBy` threshold 1, `GameView` ctor `NumGamesInMatch`=1. `lblGamesInMatch` orphaned → 9.1. GATE green.
- Part 3 (6.1+6.2): `PlayerStatistics` deleted — `turnsPlayed`/`extraTurnsPlayed`/`outcome` now plain
  `Player` fields; `GameOutcome` rebased onto `PlayerOutcome` (`playerOutcomes` map, Iterable/iterator/
  `getWinSpellEffect` gone). GATE green (2708 tests).
- Part 3 (5.1+5.2+5.3): achievements system fully gone (36-file package, engine hooks, FModel, 6
  ForgeConstants, 4 res lists, settings screen + EDocID) + 90 orphaned i18n keys. GATE green.
- Part 2 (1.1–4.1): team/2HG/Archenemy engine + lobby + variant prefs/UI gone, `YourTeam`/`YourTeamCtrl`
  script names kept. **PART 2 COMPLETE: GATE (2708 tests, 0 fail) + PKG green.**

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 7.2** — `Match.java` single-outcome rework (`gameOutcomes`/`lastOutcome`/`playedMains`
  → one `GameOutcome outcome`; drop `isMatchOver`/`getGamesWon*`/`getOutcomes`; `prepareAllZones` sideboard
  block + else card-reset branch; `GameType.isSideboardingAllowed`).
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager.editDeck`/
  `getFilteredSetCodesInCatalog` variant branches + `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck`
  fields are dead (go with GameType entries, later part); `lblArchenemy`/`lbl{Brawl,...}{,Desc}` stay.
- `Player.getAllies()` always-empty (~20 forge-ai no-op sites); `GameStateEvaluator` ally branch dead
  but correct; both left untouched.
- Pre-existing orphan i18n keys (incl. new `lblGamesInMatch`) left for the 9.1 sweep.
