# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 7 done, next 8.1)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 7.6: Win/lose + next-game flow — `ViewWinLose` `btnContinue` (field/ctor/text/setEnabled/`pnlButtons.add`/
  `show()` focus/`getBtnContinue`) gone; `IWinLoseView.getBtnContinue` + `ControlWinLose` listener +
  `actionOnContinue` gone (Restart=`NEW`, Quit=`QUIT` kept); `NextGameDecision.CONTINUE` + `HostedMatch.continueMatch`
  deleted — AI end-of-game now `isMatchOver=true` + Constructed→`delayInEDT(3000)`→`QUIT`, else immediate `QUIT`;
  `tallyNextGameDecisions` → `restartMatch()` once all still-playing humans decided (`FServerManager` caller kept);
  `CPrompt` tooltip done early in 7.5; orphan i18n `btnNextGame` + pre-existing `btnContinue` removed from 10 locales;
  `ConcedeShould` (QUIT-only) passes. GATE exit 0.
- 7.5: `TrackableProperty` `NumGamesInMatch`/`NumPlayedGamesInMatch` gone (`MatchOver` kept); `GameView` match-outcome
  accessors deleted; `CPrompt.updateText` tooltip drops "G:x/y" + "Game #x of y".
- 7.4: `GameEventGameOutcome` `matchSummary` gone; `GameLogFormatter.visit` returns null; `MATCH_RESULTS`,
  `ViewWinLose.showPlayerScores`/`lblStats`, `ReplaySession` `(game N)` suffix gone.
- 7.3: `GameAction.startGame` single-game (`startGame(Runnable)` KEEP-seam); random starter always.
- 7.2: `Match` single-outcome (`recordOutcome`/`getOutcome`), sideboard zone-prep gone. 7.1: games-per-match gone.
  6.x/5.x: `PlayerStatistics` + achievements removed. Part 2 (team/2HG/variants) complete.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 8.1** (Phase 8) — sideboard controller + AI API: `PlayerController.sideboard`/`sideboardsByItself`/
  `getSideboardingReport`, `PlayerControllerHuman.sideboard`, `PlayerControllerAi` sideboard block, DELETE
  `forge-ai/.../ability/SideboardAdvisor.java`, `GameRules` `AISideboardingEnabled`/`sideboardForAI`.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant branches +
  `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with GameType entries, later part).
- Pre-existing orphan i18n keys (incl. `lblGamesInMatch`) left for the 9.1 sweep.
