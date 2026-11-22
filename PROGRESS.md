# PROGRESS.md — Pre-rewrite cleanup (Part 5: 16.1–17.1 done)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", WireClassFilter REJECTED ERROR lines in net tests. `AiReportScenarioTest` flaky under full parallel GATE (green in isolation/rerun).
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing. i18n keys are plain string literals via `Localizer.getMessage` (no I18N enum) — deletion = property lines only.

## Recently completed
- 17.1: Final sweep. Removed ALL remaining orphaned i18n keys from all 10 locales (13,881 key lines + 872 stale `#` section
  comments; ~300/locale were stale translations of keys already gone from en-US). Verified via full Java string-literal diff
  (2,058 .java files) + zero non-Java refs. Hard-KEEP protected: `lblPuzzle*`/`lblRestartPuzzle`/`lblQuitPuzzle` (puzzle
  follow-up) + 9 vanguard i18n keys. Also deleted orphaned `forge-gui/server.preferences.example`. GATE (2686 desktop tests) + PKG green.
- 16.6: Deleted `LobbySlotAuthorizationTest` + `HeadlessNetworkGuiGame` (last `forge.net` harness files).
  `HeadlessGuiDesktop`: dropped `getNewGuiGame()` override (inherits `GuiDesktop`'s `return new CMatchUI()`),
  dead `lastMatch`/`getLastMatch`/`clearLastMatch`; KEPT `hostMatch()` override (inherited one NPEs on null `Singletons.getControl()`).
  Verified 7 kept net tests reference only kept classes. GATE green.
- 16.5: Relocated test harness `forge.net/{HeadlessGuiDesktop,TestUtils}` → `forge.headless` (git mv); updated imports.

## 17.1 sweep — verified expected survivors
- Kept net transport subset (16.2): `FGameClient`/`GameClientHandler`/`NetGameController`/`ReplayClient`/`NetworkLogConfig`
  (+ its `ForgeConstants.NETWORK_LOGS_DIR` dependency), javadoc refs in `IGuiGame`/`YieldController`/`YieldUpdate`/`AbstractGuiGame`, kept net tests.
- Card names in card data/docs (brawler/oathbreaker cards; "Brawl"/"Historic-Brawl" in `tools/DeckConversionTools/mtgdecksnet_convert.py` are MTG format names).
- `res/quest/**`, `quest-opponent-icons.txt`, `formats/Archived/**` (card names) — quest mode, later part.
- `org.jupnp` imports in IGuiBase/GuiDesktop/IDeviceAdapter/ForgeMatrixWriter = device/display adapters, not multiplayer.

## Active caveats / follow-ups
- Deferred (later parts): Archenemy* + scheme engine; ante; Puzzle core remnants + `lblPuzzle*` keys (hard-KEEP);
  `LEGACY_*` prefs; quest/gauntlet/adventure remnants (`res/quest/`, `forge-gui/tools/adventure_i18n.py` + README).
