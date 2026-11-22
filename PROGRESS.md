# PROGRESS.md — Pre-rewrite cleanup (Part 3 done, next: Part 4 item 10.1)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 9.1: Part 3 final sweep — removed `AiProps.SIDEBOARDING_*` consts (6) + their comment/property
  blocks in all 6 `res/ai/*.ai` profiles (must stay in sync: `AiProfileUtil.loadProfile` does
  `AiProps.valueOf(line)` — orphaned `.ai` lines would throw on load); removed orphaned i18n keys
  `lblGamesInMatch` (7.1), `lblMatchBestof`/`lblMatchBestOf1/3/5`/`btnStartMatchSmall` (gauntlet
  multi-game match keys) + 30 dead achievement/sideboard-dialog `#Class.java` section headers in
  all 10 locales. GATE exit 0 (2707 tests), PKG exit 0. Part 3 complete.
- 8.x/7.x/6.x/5.x: sideboard subsystem, multi-game match logic, `PlayerStatistics`, achievements
  all removed. Part 2 complete.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `perl -i -ne '... END { print @buf }'` is UNSAFE: END prints go to stdout and the file is
  truncated empty — buffer to a temp file and `cp` over, or use `-0777 -pe` to a redirect.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 4 item 10.1** (PLAN.md L115) — delete deck-gen tab `CDeckgen`/`VDeckgen` +
  `EDocID.EDITOR_DECKGEN` + editor.xml doc + `CEditorVariant`/`CEditorTokenViewer` tab re-adds.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant
  branches + `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with
  GameType entries, later part).
