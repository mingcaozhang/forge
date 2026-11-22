# PROGRESS.md — Pre-rewrite cleanup (Part 5: 16.1–16.4 done)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests. `AiReportScenarioTest` flaky under full parallel GATE (green in isolation/rerun).
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing. i18n keys are plain string literals via `Localizer.getMessage` (no I18N enum) — deletion = property lines only.

## Recently completed
- 16.4: Deleted 62 online i18n keys (531 lines) from all 10 locale files: the 60 PLAN-listed keys +
  orphaned `lblChat`/`lblLobby` (0 refs anywhere). KEPT `lblHost` (ref'd by `CardType.Host`).
  Reconnect keys existed only in en-US; `sOPServerPromptTitle` only in ko-KR/zh-CN. Section comments (e.g. `#FNetOverlay.java`) kept per 15.3 precedent.
- 16.3: Deleted `ForgeNetPreferences` + `FModel.getNetPreferences` + prefs-UI net section (UPnP combo, server-port/AFK buttons in
  C/VSubmenuPreferences) + `GamePlayerUtil` port/AFK prompts + FPref `{CHAT_WINDOW_LOC, PLAY_ONLINE_SCREEN, SUBMENU_ONLINE}` +
  ForgeConstants `{NETWORK_PLAY_WIKI_URL, SERVER_PREFS_FILE, CLOSE/INVALID_HOST/CONN_ERROR, getUPnPPreferenceMapping, localizer}` +
  LogExporter net-log export + TestUtils net-prefs lines + `docs/Network-Play.md` & doc cross-refs.
  Deviations: KEPT `NETWORK_LOGS_DIR` (kept `NetworkLogConfig` uses it); rebased `cleanupOldLogs` to drop the net-prefs gate.
- 16.2: Deleted the net server stack (FServerManager, ServerGameLobby, GameServerHandler, RemoteClient, RemoteClientGuiGame,
  IToClient, NetConnectUtil, ClientGameLobby, NetworkGuiGame, NetworkByteTracker, EventPhase, ChatMessage, IOnlineChatInterface,
  IOnlineLobby, ILobbyListener, OfflineLobby, event/{LobbyUpdateEvent,MessageEvent,LogoutEvent}, URLValidator) + dead test harness.
  KEEP deviations: `NetGameController` (replay), `GameProtocolSender`, event/{HeartbeatEvent,SeatLostEvent,LoginEvent,ReplyEvent}.
  Delta engine MOVED NetworkGuiGame → AbstractGuiGame (fixes 16.1 CMatchUI applyDelta regression); `CompatibleObjectEncoder` no-arg ctor; `NetEvent.updateForClient` gone.
- 16.1: Deleted `screens/home/online/` (5 files), `gui/FNetOverlay.java`, `screens/match/ReconnectModals.java`,
  `EDocID.HOME_NETWORK`/`HOME_NET_DECKS`, `EMenuGroup.ONLINE`. CMatchUI rebased to `AbstractGuiGame`.
- Part 5 (O/B/TL: 14.1–15.4) fully complete. See git log.

## Active caveats / follow-ups
- 16.5: relocate HeadlessGuiDesktop + TestUtils → `forge.headless` (TestDeckLoader already gone).
- 16.6: delete `LobbySlotAuthorizationTest` + `HeadlessNetworkGuiGame`; drop `HeadlessGuiDesktop.getNewGuiGame()`
  override (inherits GuiDesktop's `return new CMatchUI()`). `getNewGuiGame()` called from LocalLobby/HostedMatch/
  ReplayLauncher (main) — never fully delete it.
- 17.1: extra orphaned online keys (0 Java refs, not in 16.4 list): `lblReconnectedToast`, `lblReconnectRejoin`,
  `lblUPnP{Title,Question,Success,Failed}`, `lblNotNow`, `lblAsk`, `lblJustOnce`, `lblInterface`, `lblAddress`, `lblCopy{External,Local}URL`,
  `lblForgeUnableDetermineYourExternalIP`, `lblEnterMessageToSend`, `lblNetworkDeckFilter`, `lblNetworkNewEventButton`,
  `lblNetworkSetUpEventPrompt`, `lblDisconnect`, `lblShowDraftPanel` — rg-verify each before removing
  (`lblAlways`/`lblNever` stay — used by `DeclineScope`).
- `org.jupnp` imports in IGuiBase/GuiDesktop/IDeviceAdapter/ForgeMatrixWriter = device/display adapters, NOT multiplayer — expected 17.1-sweep survivors.
- Deferred (later parts): Archenemy* + scheme engine; ante; Puzzle remnants; `LEGACY_*` prefs; other non-Commander modes.
