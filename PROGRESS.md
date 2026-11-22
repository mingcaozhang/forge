# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 7 — 7.3 done, next 7.4)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 7.3: `GameAction.startGame` single-game — deleted 1-arg `startGame(GameOutcome)` overload (zero refs);
  `startGame(GameOutcome, Runnable)` → `startGame(Runnable)` (KEEP-seam param); callers rebased: `Match.startGame`
  (no longer passes `this.outcome`) + `SubgameEffect:162` (`startGame(null)`); `determineFirstTurnPlayer()` takes no
  outcome — always `Aggregates.random` starter, always fires `GameEventFlipCoin`, hotseat `get(0)` fallback kept,
  `chooseStartingPlayer(true)` (the `boolean` param on the `PlayerController` API stays; `lblYouLostTheLastGame`
  still referenced by `PlayerControllerHuman`). GATE green (2708 tests).
- 7.2: `Match` single-outcome — one `GameOutcome outcome` field; `recordOutcome(Game)` (called from `Game.gameOver`) +
  `getOutcome()`; dropped `getOutcomes`/`getLastOutcome`/`isMatchOver`/`getGamesWonBy`/`getGamesWon`/`isWonBy`/`getWinner`/
  `clearGamesPlayed`/`getPlayedMain`/1-arg `startGame`; `getTitle` plain; `getAnteResult` reads the single outcome (empty
  when null); `prepareAllZones` sideboard machinery + else card-reset branch gone; `GameType.isSideboardingAllowed()` deleted.
  GATE green.
- 7.1: games-per-match rules + lobby selector gone. 6.x/5.x: `PlayerStatistics` + achievements removed.
  Part 2 (team/2HG/variants) complete: GATE+PKG green.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 7.4** — `GameEventGameOutcome`: drop `matchSummary` component + 2-arg ctor `computeMatchSummary`;
  `Game:633` → 1-arg ctor; `GameLogFormatter.visit`: keep GAME_OUTCOME lines, `return null`; delete
  `GameLogEntryType.MATCH_RESULTS` + `GameLogVerbosity` entries; `ViewWinLose.showPlayerScores` + `lblStats` placeholder;
  `ReplaySession` title: drop `(game N)` suffix. `GameLog.markMatchStartIfNeeded`/`getMatchStartMillis` stay.
- 7.5: delete now-zero-ref `GameView.isMatchWonBy`/`getOutcomesOfMatch`/`getGamesWonBy` + `NumGamesInMatch`/
  `NumPlayedGamesInMatch` trackables.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant branches +
  `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with GameType entries, later part).
- Pre-existing orphan i18n keys (incl. `lblGamesInMatch`) left for the 9.1 sweep.
