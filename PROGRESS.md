# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 7 — 7.5 done, next 7.6)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 7.5: `TrackableProperty` — `NumGamesInMatch`/`NumPlayedGamesInMatch` gone (`MatchOver` kept, still set in
  `GameView.updateGameOver`); `GameView` — ctor trackable sets + `getNumGamesInMatch`/`getNumPlayedGamesInMatch`/
  `isMatchWonBy`/`getOutcomesOfMatch`/`getGamesWonBy` deleted (`getOutcome` already `getMatch().getOutcome()`);
  `CPrompt.updateText` tooltip drops "G:x/y" + "Game #x of y" (sole external callers of the removed getters —
  this is the 7.6 CPrompt target state, done early to keep build green). GATE green (2708 tests).
- 7.4: `GameEventGameOutcome` dropped `matchSummary` (1-arg ctor already in use at `Game:616`);
  `GameLogFormatter.visit` keeps GAME_OUTCOME lines, now `return null` (safe: `AbstractGuiGame:861` re-feed
  only adds non-null); `MATCH_RESULTS` entry+verbosity, `ViewWinLose.showPlayerScores`/`lblStats`,
  `ReplaySession` `(game N)` title suffix gone. `GameLog.markMatchStartIfNeeded` kept (naming only).
- 7.3: `GameAction.startGame` single-game — `startGame(Runnable)` KEEP-seam; `determineFirstTurnPlayer()` no
  outcome param, always `Aggregates.random` starter, always fires `GameEventFlipCoin`; `lblYouLostTheLastGame`
  still referenced by `PlayerControllerHuman`.
- 7.2: `Match` single-outcome (`recordOutcome`/`getOutcome`), sideboard zone-prep machinery gone,
  `GameType.isSideboardingAllowed()` deleted. 7.1: games-per-match rules + lobby selector gone.
  6.x/5.x: `PlayerStatistics` + achievements removed. Part 2 (team/2HG/variants) complete.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 7.6** — `ViewWinLose` drop `btnContinue`/`getBtnContinue` + `IWinLoseView`/`ControlWinLose`
  listeners; `NextGameDecision.CONTINUE` + `HostedMatch.continueMatch` gone → single `QUIT` flow;
  `tallyNextGameDecisions` → `restartMatch()`; `CPrompt` tooltip already simplified in 7.5 (done early).
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant branches +
  `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with GameType entries, later part).
- Pre-existing orphan i18n keys (incl. `lblGamesInMatch`) left for the 9.1 sweep.
