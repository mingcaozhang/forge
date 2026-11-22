# PROGRESS.md — Pre-rewrite cleanup (Part 5: 16.1–16.6 done)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests. `AiReportScenarioTest` flaky under full parallel GATE (green in isolation/rerun).
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing. i18n keys are plain string literals via `Localizer.getMessage` (no I18N enum) — deletion = property lines only.

## Recently completed
- 16.6: Deleted `LobbySlotAuthorizationTest` + `HeadlessNetworkGuiGame` (last `forge.net` harness files).
  `HeadlessGuiDesktop`: dropped `getNewGuiGame()` override (now inherits `GuiDesktop`'s `return new CMatchUI()`),
  dead `lastMatch`/`getLastMatch`/`clearLastMatch` + stale `NetworkLogConfig.setTestMode` comment; KEPT `hostMatch()`
  override (inherited `GuiDesktop.hostMatch()` would NPE on `Singletons.getControl()`, null in tests). Verified the 7 kept
  net tests reference only kept classes (`WireClassFilterTest`/`WireDeserializationTest` live in
  `forge-gui-desktop/src/test/.../gamemodes/net/`, not forge-gui as PLAN phrased). GATE green.
- 16.5: Relocated test harness `forge.net/{HeadlessGuiDesktop,TestUtils}` → `forge.headless` (git mv); updated imports.
- 16.4: Deleted 62 online i18n keys (531 lines) from all 10 locale files. KEPT `lblHost` (`CardType.Host`).
- 16.3/16.2: Deleted net prefs + server stack; KEPT `NetGameController`/`GameProtocolSender`/heartbeat events;
  delta engine moved NetworkGuiGame → AbstractGuiGame. Part 5 (O/B/TL: 14.1–15.4) fully complete. See git log.

## Active caveats / follow-ups
- 17.1: extra orphaned online keys (0 Java refs, not in 16.4 list): `lblReconnectedToast`, `lblReconnectRejoin`,
  `lblUPnP{Title,Question,Success,Failed}`, `lblNotNow`, `lblAsk`, `lblJustOnce`, `lblInterface`, `lblAddress`, `lblCopy{External,Local}URL`,
  `lblForgeUnableDetermineYourExternalIP`, `lblEnterMessageToSend`, `lblNetworkDeckFilter`, `lblNetworkNewEventButton`,
  `lblNetworkSetUpEventPrompt`, `lblDisconnect`, `lblShowDraftPanel` — rg-verify each before removing
  (`lblAlways`/`lblNever` stay — used by `DeclineScope`).
- `org.jupnp` imports in IGuiBase/GuiDesktop/IDeviceAdapter/ForgeMatrixWriter = device/display adapters, NOT multiplayer — expected 17.1-sweep survivors.
- Deferred (later parts): Archenemy* + scheme engine; ante; Puzzle remnants; `LEGACY_*` prefs; other non-Commander modes.
