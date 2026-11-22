# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 7 — 7.2 done, next 7.3)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 7.2: `Match` single-outcome — one `GameOutcome outcome` field; `recordOutcome(Game)` (called from `Game.gameOver`) + `getOutcome()`;
  dropped `getOutcomes`/`getLastOutcome`/`isMatchOver`/`getGamesWonBy`/`getGamesWon`/`isWonBy`/`getWinner`/`clearGamesPlayed`/
  `getPlayedMain`/1-arg `startGame`; `getTitle` plain; `getAnteResult` reads the single outcome (empty when null);
  `prepareAllZones` sideboard machinery + else card-reset branch gone; `GameType.isSideboardingAllowed()` deleted.
  Callers rebased: `GameEventGameOutcome` 1-arg ctor (`matchSummary=""` until 7.4 drops the component); `GameView` MatchOver ←
  `game.isGameOver()` + outcome getters read `match.getOutcome()`; `HostedMatch` sets `isMatchOver = true` after game end;
  `PlayerControllerAi` reads opponents' `getDeck().getMain()` + `match.getOutcome()`; `ReplaySession`/`UnifiedNetworkHarness`
  single-outcome; `StormGoldfishTest`/`FlatMonteCarloBenchmark` → 2-arg `startGame(game, null)`. GATE green (2708 tests).
- 7.1: games-per-match rules + lobby selector gone; interim single-game fixes finalized in 7.4/7.5. GATE green.
- 6.x/5.x: `PlayerStatistics` + achievements removed. Part 2 (team/2HG/variants) complete: GATE+PKG green.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 7.3** — `GameAction`: delete `startGame(GameOutcome)` overload; `startGame(GameOutcome, Runnable)` →
  `startGame(Runnable)` (KEEP-seam param); callers `Match.startGame` (now passes `this.outcome`) + `SubgameEffect:162`
  (`startGame(null, null)` → `startGame(null)`); `determineFirstTurnPlayer(GameOutcome)` → `determineFirstTurnPlayer()`
  (always `Aggregates.random`, keep hotseat `nextToStart` fallback).
- 7.4: drop `GameEventGameOutcome.matchSummary` component + `GameLogFormatter` MATCH_RESULTS entry + `ViewWinLose.showPlayerScores`
  / `lblStats`; 7.5: delete now-zero-ref `GameView.isMatchWonBy`/`getOutcomesOfMatch`/`getGamesWonBy` + multi-game trackables.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant branches +
  `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with GameType entries, later part).
- Pre-existing orphan i18n keys (incl. `lblGamesInMatch`) left for the 9.1 sweep.
