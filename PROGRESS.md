# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 8 done, next 9.1)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 8.3: AI-sideboarding pref gone — `FPref.MATCH_AI_SIDEBOARDING_MODE`, `AiProfileUtil.AISideboardingMode`
  enum + field + getter/setter, `FModel` load call, `CSubmenuPreferences.initializeAiSideboardingModeComboBox`
  + call, `VSubmenuPreferences` field/panel adds/getter; i18n `cbpAiSideboardingMode` + `nlpAiSideboardingMode`
  removed from all 10 locales (perl line-delete, no-trailing-newline preserved). GATE exit 0 (2707 tests).
- 8.1/8.2: sideboard controller + AI API + GUI dialog/order plumbing gone (Phase 8 complete).
- 7.x/6.x/5.x: win/lose Continue, multi-game trackables, single-outcome `Match`, single-game `startGame`,
  `PlayerStatistics` + achievements removed. Part 2 complete.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 9.1** — final `rg` sweep (PLAN.md L103), incl. `AiProps.SIDEBOARDING_*` consts + `.ai`
  profile data left from 8.1 (deliberately deferred), plus orphan i18n keys (incl. `lblGamesInMatch`); then `GATE` + `PKG`.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant branches +
  `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with GameType entries, later part).
