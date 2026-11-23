# PROGRESS.md — Pre-rewrite cleanup (Part 6: 18.1 done)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", WireClassFilter REJECTED ERROR lines in net tests. `AiReportScenarioTest` flaky under full parallel GATE (green in isolation/rerun).
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing. i18n keys are plain string literals via `Localizer.getMessage` (no I18N enum) — deletion = property lines only.

## Recently completed
- 18.1: `GameAction` dead branches — commander-damage SBA keyed on `GameType.Commander` only (Planeswalker
  variant check dropped); mulligan unconditional (Puzzle guard dropped); `determineFirstTurnPlayer` no longer
  early-returns for Puzzle or Archenemy (empty `game != null` wrapper removed too — `game` is used
  unconditionally in the rest of the method). GATE (2686 tests) green.
- 17.5: Removed lobby Format selector + Vanguard/Planechase game modes. `GameType` lost `Vanguard`/`Planechase`;
  `FormatChoices.VARIANTS` is now empty. Stripped their engine blocks (`RegisteredPlayer` plane avatar,
  `GameAction`/`PhaseHandler` Planechase init, `GameLobby` startGame legality), dev cheats, planar-deck
  plumbing (`DeckProxy.getAllPlanarDecks`, `RandomDeckGenerator` case), and all lobby UI (format combo, variant
  checkboxes, planar+vanguard panels; `PlayerPanel` planar/vanguard buttons). Commander stays a variant; kept
  `Player.getPlanes` (read at 2940) + planes card data (`CardType.Vanguard`, planes cards, `DeckFormat.Vanguard`).
- 17.4: Fixed deck-editor tab-drag NPE (`SRearrangingUtil.cellTarget` null); drag over non-cell areas now
  resets the drag. GUI drag not unit-testable; verified by full GATE+PKG.
- 17.3: Create user skins dir on profile init (`ForgeProfileProperties.load()` ensures `cacheDir/skins`);
  `FSkin.getSkinDirectoryNames()` returns empty list instead of printing "can't find skins directory!".
- 17.2: `ItemManagerConfig.load()` silently skips stale item-view prefs entries from removed modes via new
  `parseEnum` helper (no more `No enum constant` stack traces). Regression test `ItemManagerConfigStalePrefsShould`.

## Active caveats / follow-ups
- Part 6 order: Phase 18 (engine dead branches, forge-game) → 19 (deckgen) → 20 (storage/net decks/DeckType) →
  21 (deck editor) → 22 (enum pruning) → 23 (res data) → 24 (i18n) → 25 (final). 18.3/18.4 still hold the
  `Player`/`RegisteredPlayer` Archenemy/Planeswalker/Puzzle refs.
- Deferred (later parts): ante; Puzzle core remnants + `lblPuzzle*` keys (hard-KEEP); `LEGACY_*` prefs;
  quest/gauntlet/adventure remnants (`res/quest/`, `forge-gui/tools/adventure_i18n.py` + README).
- 17.1 sweep expected survivors (check at 25.2): kept net transport subset (`FGameClient`/`GameClientHandler`/
  `NetGameController`/`ReplayClient`/`NetworkLogConfig` + `NETWORK_LOGS_DIR`), card names in card data/docs,
  `res/quest/**`, `formats/Archived/**`, `org.jupnp` imports (device/display adapters, not multiplayer).
