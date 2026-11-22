# PROGRESS.md — Pre-rewrite cleanup (Part 2 done; next: Part 3)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.

## Completed items
- Part 2 (1.1–1.5): game engine team/2HG mechanics removed + rebased single-player (Player team API gone,
  `YourTeam`/`YourTeamCtrl` script names kept, 2HG rule entry points gone). GATE green.
- Part 2 (2.1–2.5): lobby team/2HG/Archenemy/scheme selection + UI, `LobbySlot`/`UpdateLobbyPlayerEvent`
  team fields, `FormatChoices.VARIANTS` → Vanguard + Planechase, 8 orphaned team i18n keys removed. GATE green.
- Part 2 (3.1–3.4): `FormatChoices.COMMANDER_GAME_TYPES` → Commander only; lobby chooser + format zone chains
  trimmed; 24 Oathbreaker/TinyLeaders/Brawl `*_P{1..8}_DECK_STATE` prefs + 3 arrays gone; deck-editor variant
  tabs/`DeckType`/`DeckProxy` methods gone; 6 orphaned variant i18n keys removed. GATE green.
- Part 2 (4.1): `GameType.TwoHeadedGiant` + `lblTwoHeadedGiant{,Desc}` (en-US only) removed.
  **PART 2 COMPLETE: GATE (2708 tests, 0 fail) + PKG green.**

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (one APPROX scenario failed once; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: **Part 3 item 5.1** — delete the 36-file achievements package + engine/storage/i18n per PLAN.
- `VLobby.populateDeckPanel` still has Oathbreaker/TinyLeaders/Brawl case labels (unreachable; go with GameType
  entries in a later part). `lblArchenemy`/`lbl{Brawl,Oathbreaker,TinyLeaders}{,Desc}` stay (GameType entries stay).
- `DeckManager.editDeck`/`getFilteredSetCodesInCatalog` variant branches + `DeckPreferences.{oathbreaker,
  tinyLeaders,brawl}Deck` preferred-deck fields are dead (views gone); go with GameType entries, later part.
- `Player.getAllies()` (Player.java:350) now always-empty; ~20 forge-ai call sites degenerate to empty no-ops.
- `GameStateEvaluator` ally branch (teammates/teamLife vars, "Ally" debug lines) now dead but functionally
  correct (scores `2*aiLife`); left untouched.
- Pre-existing orphan i18n keys (quest/gauntlet/puzzle/adv*/misc) left as follow-ups.
