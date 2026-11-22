# PROGRESS.md — Pre-rewrite cleanup (Part 5: 16.1–16.5 done)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests. `AiReportScenarioTest` flaky under full parallel GATE (green in isolation/rerun).
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing. i18n keys are plain string literals via `Localizer.getMessage` (no I18N enum) — deletion = property lines only.

## Recently completed
- 16.5: Relocated test harness `forge.net/{HeadlessGuiDesktop,TestUtils}` → `forge.headless` (git mv); updated imports in
  `ProtocolGuiGame{SingleThread,InProcess}Test` + javadoc link in `HeadlessNetworkGuiGame`. `HeadlessGuiDesktop` now imports
  `forge.net.HeadlessNetworkGuiGame` (still there until 16.6). GATE green.
- 16.4: Deleted 62 online i18n keys (531 lines) from all 10 locale files (60 PLAN-listed + orphaned `lblChat`/`lblLobby`). KEPT `lblHost` (`CardType.Host`).
- 16.3: Deleted `ForgeNetPreferences` + net prefs UI + FPref net entries + net constants + `docs/Network-Play.md`.
  Deviations: KEPT `NETWORK_LOGS_DIR` (kept `NetworkLogConfig` uses it); rebased `cleanupOldLogs`.
- 16.2: Deleted the net server stack + dead test harness. KEEP: `NetGameController`, `GameProtocolSender`, event/{Heartbeat,SeatLost,Login,Reply}Event.
  Delta engine MOVED NetworkGuiGame → AbstractGuiGame (fixes 16.1 CMatchUI applyDelta regression).
- Part 5 (O/B/TL: 14.1–15.4, 16.1) fully complete. See git log.

## Active caveats / follow-ups
- 16.6: delete `LobbySlotAuthorizationTest` + `HeadlessNetworkGuiGame`; drop `HeadlessGuiDesktop.getNewGuiGame()`
  override (inherits GuiDesktop's `return new CMatchUI()`). `getNewGuiGame()` called from LocalLobby/HostedMatch/
  ReplayLauncher (main) — never fully delete it. `forge.headless.HeadlessGuiDesktop` imports `HeadlessNetworkGuiGame` —
  remove that import + `hostMatch`/`getLastMatch`/`clearLastMatch` refs as needed once 16.6 lands.
- 17.1: extra orphaned online keys (0 Java refs, not in 16.4 list): `lblReconnectedToast`, `lblReconnectRejoin`,
  `lblUPnP{Title,Question,Success,Failed}`, `lblNotNow`, `lblAsk`, `lblJustOnce`, `lblInterface`, `lblAddress`, `lblCopy{External,Local}URL`,
  `lblForgeUnableDetermineYourExternalIP`, `lblEnterMessageToSend`, `lblNetworkDeckFilter`, `lblNetworkNewEventButton`,
  `lblNetworkSetUpEventPrompt`, `lblDisconnect`, `lblShowDraftPanel` — rg-verify each before removing
  (`lblAlways`/`lblNever` stay — used by `DeclineScope`).
- `org.jupnp` imports in IGuiBase/GuiDesktop/IDeviceAdapter/ForgeMatrixWriter = device/display adapters, NOT multiplayer — expected 17.1-sweep survivors.
- Deferred (later parts): Archenemy* + scheme engine; ante; Puzzle remnants; `LEGACY_*` prefs; other non-Commander modes.
