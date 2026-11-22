# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 8, 8.1 done, next 8.2)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 8.1: Sideboard controller + AI API gone — `PlayerController` abstract `sideboard` + `sideboardsByItself` +
  `getSideboardingReport`; `PlayerControllerHuman.sideboard` (+`CardPool`/`Deck`/`Range` imports); `PlayerControllerAi`
  whole sideboard block (report field, `sideboardsAgainstOpponentDeck`, `sideboardAgainstOpponents`, `sideboard` impl);
  DELETE `forge-ai/.../ai/SideboardAdvisor.java` (was in `forge.ai`, not `...ability/`); `GameRules` `AISideboardingEnabled`/
  `sideboardForAI` + accessors; `HostedMatch.getDefaultRules` `AiProfileUtil.getAISideboardingMode()` switch + import gone.
  Tests: `PlayerControllerForTests.sideboard` override (+`Deck` import) + `FlatMonteCarloComboTest`
  `sideboardingBringsInAnswersToWhatTheOpponentPlays` removed (rest of file kept). `AiProps.SIDEBOARDING_*` consts +
  `.ai` profile data + `FPref.MATCH_AI_SIDEBOARDING_MODE` stay (9.1 sweep / 8.3). GATE exit 0 (2707 tests).
- 7.6: Win/lose + next-game flow — `ViewWinLose` `btnContinue` gone; `IWinLoseView.getBtnContinue` +
  `ControlWinLose.actionOnContinue` gone (Restart=`NEW`, Quit=`QUIT` kept); `NextGameDecision.CONTINUE` +
  `HostedMatch.continueMatch` deleted; `tallyNextGameDecisions` → `restartMatch()` once all still-playing humans
  decided; orphan i18n `btnNextGame` + pre-existing `btnContinue` removed from 10 locales. GATE exit 0.
- 7.5: `TrackableProperty` `NumGamesInMatch`/`NumPlayedGamesInMatch` gone; `GameView` match-outcome accessors deleted.
- 7.4: `GameEventGameOutcome` `matchSummary` gone; `MATCH_RESULTS`, `showPlayerScores`/`lblStats`, `(game N)` suffix gone.
- 7.3: `GameAction.startGame` single-game. 7.2: `Match` single-outcome, sideboard zone-prep gone. 7.1: games-per-match
  gone. 6.x/5.x: `PlayerStatistics` + achievements removed. Part 2 complete.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 8.2** (Phase 8) — GUI sideboard dialog + order plumbing: `IGuiGame.sideboard` + `sideboardingMode`
  from `order(...)` overloads, `CMatchUI`/`GuiChoose.sideboard`, `ProtocolGuiGame.order` + `ProtocolMethod.order`
  (drop `Boolean.TYPE`), `DualListBox` sideboard-mode, i18n `lblSideboardForPlayer` + `lblUpdateMainFromSideboard`.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant branches +
  `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with GameType entries, later part).
- Pre-existing orphan i18n keys (incl. `lblGamesInMatch`) left for the 9.1 sweep.
