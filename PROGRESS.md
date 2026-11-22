# PROGRESS.md — Pre-rewrite cleanup (Part 5 done; Part 6 in progress: 16.1 + 16.2 done)

## Verified baseline facts
- Branch `dev`, clean tree. Per-item verify: `GATE` = `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms). Final gates add `PKG` = `mvn package -DskipTests -T 1C`.
- Desktop surefire "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US.
- `rg --type java` here also matches `.properties` files; use `--glob '*.java'` for accurate Java-only ref checks.
- Checkstyle runs at `validate` with root `checkstyle.xml` — fails on unused imports; `mvn test-compile` catches it fast.

## Recently completed
- 16.2: Deleted the whole net server stack (FServerManager, ServerGameLobby, GameServerHandler, RemoteClient,
  RemoteClientGuiGame, IToClient, NetConnectUtil, ClientGameLobby, NetworkGuiGame, NetworkByteTracker, EventPhase,
  ChatMessage, IOnlineChatInterface, IOnlineLobby, ILobbyListener, OfflineLobby, event/{LobbyUpdateEvent,MessageEvent,
  LogoutEvent}, URLValidator) + dead test harness (9 files + analysis/, pulled forward from 16.6).
  Compiler-verified KEEP deviations from plan: `NetGameController` (replay path: FGameClient.setGameControllers →
  NetGameController per player; CMatchUI drives all replay UI input through it), `GameProtocolSender` (ProtocolGuiGame),
  `event/{HeartbeatEvent,SeatLostEvent,LoginEvent,ReplyEvent}` (kept transport refs them).
  Delta engine (`applyDelta` + ~570 lines of helpers) MOVED NetworkGuiGame → AbstractGuiGame — fixes silent 16.1
  regression (CMatchUI lost applyDelta inheritance; replay playback was broken, GATE stayed green because
  ReplayRecordingShould only tests file format). AbstractGuiGame now also `implements IHasForgeLog` (netLog).
  CompatibleObjectEncoder → no-arg ctor (all callers passed null); NetEvent.updateForClient removed.
  `sim` is a phantom console mode (in `Main.isCommandLineMode` but no switch case — falls to default, prints
  "Unknown mode", exits fast; pre-existing since limited-mode strip 7dca72218b9): HeadlessStartupTest now uses it
  as the fast-return mode in place of deleted `server`. GATE exit 0 (2687 tests = 2701 − 14).
- 16.1: Deleted `screens/home/online/` (5 files), `gui/FNetOverlay.java`, `screens/match/ReconnectModals.java`;
  `EDocID.HOME_NETWORK`/`HOME_NET_DECKS`; `EMenuGroup.ONLINE`. `CMatchUI` rebased `NetworkGuiGame` → `AbstractGuiGame`
  (reconnect dispatch + `ReconnectingHandle` + `FNetOverlay` focus check + 5 dead `isNetGame()` blocks gone).
  `pushSkipPhaseToControllers`/`seedYieldStateOnHost` MOVED NetworkGuiGame → AbstractGuiGame (generic yield wiring;
  only net touch is the `instanceof NetGameController` guard). GATE exit 0 (2701 tests).
- Part 5 (O/B/TL: 14.1–14.3, 15.1–15.4) fully complete: DeckFormat/GameType/Deck/CardRules/FModel/DeckManager/
  CEditorConstructed/GameLobby/CEditor* rebased, O/B/TL i18n keys + format/net-decks .txt files gone, mode-specific
  tests deleted. See git log for details.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`, WireClassFilter REJECTED ERROR lines in net tests.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.
- `AiReportScenarioTest` is flaky under full parallel GATE (APPROX scenarios fail occasionally; green in isolation and on full rerun).

## Active caveats / follow-ups
- Next: 16.3 (net prefs + model + docs). MUST rebase `TestUtils` (still imports `ForgeNetPreferences`, calls
  `FModel.getNetPreferences()` L50/L57-58 — both go in 16.3). `CSubmenuPreferences` netPrefs field + UPnP/server-port
  sections also 16.3 (FServerManager import + DEV_MODE guard already dropped in 16.2).
- 16.5: relocate HeadlessGuiDesktop + TestUtils → `forge.headless` (TestDeckLoader already gone).
- 16.6: delete `LobbySlotAuthorizationTest` + `HeadlessNetworkGuiGame` (currently rebased to extend AbstractGuiGame);
  drop `HeadlessGuiDesktop.getNewGuiGame()` override so it inherits GuiDesktop's `return new CMatchUI()`.
  `getNewGuiGame()` is called from LocalLobby/HostedMatch/ReplayLauncher (main) — never fully delete it.
- `HeadlessGuiDesktop` subclasses `GuiDesktop`; CMatchUI has a no-arg ctor (forge-gui-desktop/screens/match/CMatchUI).
- Deferred (later parts): Archenemy* + scheme engine; ante; Puzzle remnants; `LEGACY_*` prefs; other non-Commander modes.
