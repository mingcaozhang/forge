# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE: `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (from 27.1 on add `forge-baseline` to `-pl`). Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- Stale `target/` once caused 364 false `NoClassDefFoundError` failures; `mvn clean` fixes — if GATE fails with NoClassDefFound on untouched classes, clean before debugging.
- `Game`/`Match` live in `forge-game/src/main/java/forge/game/` (NOT `mage/core`); engine is Swing-free, forge-gui is Swing-free except `AutoUpdater`.

## Recently completed
- **26.1** — Wrote `docs/baseline/Coupling-Audit.md`: all engine↔UI runtime seams with file:line refs — S1 event path (`Game.fireEvent` EventBus → `FControlGameEventHandler` one-EDT-hop batching → `IGuiGame.update*` → `CMatchUI`), S2 Trackable state graph (`Tracker`/`GameView`/`PlayerView`/`CardView`), S3 delta engine (`DeltaPacket`/`DeltaSyncManager.collectDeltas`/`AbstractGuiGame.applyDelta`), S4 prompt/latch (`PlayerControllerHuman.choose*` → `InputSyncronizedBase.showAndWait` → `IGuiGame.awaitInput(CountDownLatch)`), S5 action channel (`IGameController`/`ProtocolMethod`/`GameProtocolSender`/`NetGameController`), S6 lifecycle (`VSubmenuConstructed`→`GameLobby.startGame`→`HostedMatch`→`Match`→`GameAction`/`PhaseHandler`), S7 threading/determinism, S8 replay (reuses S1+S3). Ends with the A1–A8 seam list the Phase 28 `BaselineEngine` adapter must own. Doc-only commit.
- **25.2** — FINAL SWEEP (Part 6): deleted 14 orphan classes + data + 31 i18n keys; KEPT-by-verification: `DraftEffect`/`ApiType.Draft`, `TriggerChaosEnsues`, `CardProperty.IsRingbearer`, booster/print-sheet chain, `MetaSetType`/`MetaSet`/`CardBlock`. GATE+PKG exit 0.

## Active caveats / follow-ups
- Part 7 (REWRITE.md Phase 1) in progress: next item is 27.1 (scaffold `forge-baseline` module + versioned `BaselineSnapshot`/`BaselineDelta`); from 27.1 on GATE must include `-pl forge-gui-desktop,forge-baseline`.
- `docs/baseline/Coupling-Audit.md` line refs are point-in-time; re-verify before relying on them after engine refactors.
- `DeltaPacket` has NO schema VERSION constant yet — 27.1 introduces one; keep `ReplayRecorder`/`ReplayClient` wire-compatible (audit S8/A7).
- `FDeckChooser.updateCustom` `default:` branch kept (reachable — constructed decks selectable in lobby via `CONSTRUCTED_DECK` type).
- i18n scan method: keys = `grep -E '^[A-Za-z0-9_]+=' en-US.properties | cut -d= -f1`; zero-ref = no `rg -F --glob '*.java'` hit AND no non-Java hit outside PLAN/PROGRESS; dynamic key builders: `CardTranslation` `lbl<TokenType>`, `InputBase` `lbl<Day>`.
