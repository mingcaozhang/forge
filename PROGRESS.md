# PROGRESS.md — Pre-rewrite cleanup (Part 3 in progress: Phase 8, 8.2 done, next 8.3)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 8.2: GUI sideboard dialog + order plumbing gone — `IGuiGame.sideboard` + `sideboardingMode` param from all
  `order(...)` overloads (abstract now 8-arg ending in `showRememberCheckbox`); `CMatchUI.sideboard` + 8-arg
  `order` override; `GuiChoose.sideboard` + param; `ProtocolGuiGame.order`/`sideboard` + `ProtocolMethod.order`
  (one `Boolean.TYPE` dropped) + `ProtocolMethod.sideboard` entry; `DualListBox` field/`setSideboardMode`/
  `_setButtonState` branch/`autoButton` cond. 9-arg `order` callers updated (`.ordered()` where return is List):
  `PlayerControllerHuman` L2173/L2346/L2350 + exert/enlist/orderCosts L834/845/858, `AbstractGuiGame.many` L822,
  `CMatchUI.chooseEntitiesForEffect`. Test `HeadlessNetworkGuiGame` order/sideboard updated. i18n
  `lblSideboardForPlayer` + `lblUpdateMainFromSideboard` removed (10 locales); `lblSideboard`/`lbltosideboard`/
  `lblfromsideboard` + `ttMain` KEPT (DeckSection/AdvancedSearch/ColumnDef/CEditorConstructed). GATE exit 0 (2707 tests).
- 8.1: Sideboard controller + AI API gone (`PlayerController`/`Human`/`Ai`, `SideboardAdvisor` DELETE, `GameRules`
  `sideboardForAI`/`AISideboardingEnabled`). `AiProps.SIDEBOARDING_*` consts + `.ai` profile data +
  `FPref.MATCH_AI_SIDEBOARDING_MODE` stay (8.3/9.1).
- 7.x/6.x/5.x: win/lose Continue, multi-game trackables, single-outcome `Match`, single-game `startGame`,
  `PlayerStatistics` + achievements removed. Part 2 complete.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 8.3** — AI-sideboarding pref: `FPref.MATCH_AI_SIDEBOARDING_MODE` (L214); `AiProfileUtil`
  `AISideboardingMode` enum + field + getter/setter; `FModel:271`; `CSubmenuPreferences` combo init; `VSubmenuPreferences`
  `cbpAiSideboardingMode`; i18n `cbpAiSideboardingMode` + `nlpAiSideboardingMode`.
- `VLobby.populateDeckPanel` Oathbreaker/TinyLeaders/Brawl case labels + `DeckManager` variant branches +
  `DeckPreferences.{oathbreaker,tinyLeaders,brawl}Deck` fields are dead (go with GameType entries, later part).
- Pre-existing orphan i18n keys (incl. `lblGamesInMatch`) left for the 9.1 sweep.
