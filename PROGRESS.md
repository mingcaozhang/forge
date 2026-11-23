# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE: `mvn test -pl forge-gui-desktop,forge-baseline -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- Stale `target/` once caused 364 false `NoClassDefFoundError` failures; `mvn clean` fixes — if GATE fails with NoClassDefFound on untouched classes, clean before debugging.
- `Game`/`Match` live in `forge-game/src/main/java/forge/game/` (NOT `mage/core`); engine is Swing-free, forge-gui is Swing-free except `AutoUpdater`.

## Recently completed
- **28.1** — `BaselineEngine` (forge-baseline `forge.baseline.engine`): headless adapter over direct Match/Game path. `start(seed, decks, players)` builds Commander `Match`/`Game` on a daemon game thread; seeded `MyRandom`; `feed(BaselineAction)` FIFO-queued, consumed at the scripted player's pass-priority prompt via `ScriptedPlayerController` (extends `PlayerControllerAi`; Concede/SelectCard/SelectAbility, else pass). Emits `BaselineSnapshot` at turn-1 start (DeltaSyncManager primed, full graph discarded), then `BaselineDelta` at `GameEventForwarder` batching points (pre-priority-block, turn end). **Key gotchas:** (1) `StaticData.instance()` is null headless — engine bootstraps a minimal card-free `StaticData` from temp dirs when absent; (2) basic lands must NOT carry an `A:{T}: Add {B}.` script line (crashes `AbilityFactory`) — tap-mana is auto-generated from the basic-land subtype; (3) determinism across in-JVM runs is broken by JVM-wide static ID counters (`Game.nextId`, `SpellAbilityStackInstance.nextId`) — tests compare rank-normalized fingerprints (first-appearance rank per `(typeTag,id)` delta key), not raw bytes. Test: `BaselineEngineShould` — functional concede run + same-seed/same-actions envelope-stream equality. GATE+PKG exit 0.
- **27.3** — Wrote `docs/baseline/StateActionContract.md` (design doc, doc-only): state schemas (`BaselineFormat` VERSION=2 + key encoding, `BaselineSnapshot`, `BaselineDelta`, `BaselinePrompt` normalization), `BaselineAction` 15-variant mapping table to `IGameController`/`ProtocolMethod` + prompt-channel exclusion, versioning rules (single gate, strict equality, resync-not-migration, kept-protocol parity), threading model (single game thread produces immutable envelopes; consumers apply on fresh `Tracker`; no EDT, no latch on the contract path; `MyRandom` determinism), consumers (Swing baseline untouched; Phase-2 non-JVM parity surface; Phase 32 JSONL shards), test guards + Phase 28–32 hooks. GATE exit 0.
- **27.2** — Canonical action model in `forge.baseline.action`: sealed `BaselineAction` = 15 record variants, 1:1 with the `IGameController` client surface via `ProtocolMethod` (trailing `ITriggerEvent` param dropped — wire already sends null per `NetGameController`); mapping table + exclusions documented in Javadoc. `BaselinePrompt` (state pkg, builder, 16 `Kind`s) added to `BaselineSnapshot`; `BaselineFormat.VERSION` 1→2. Tests: machine-checked mapping, protocol-surface completeness, sealed-closure check, codec round-trips. GATE+PKG exit 0.

## Active caveats / follow-ups
- Part 7 (REWRITE.md Phase 1) in progress: next item is 28.2 (prompt boundary: pending prompts + legal candidates as state; choices as actions).
- 28.1 deliberately leaves `BaselineSnapshot.prompt` null (prompt boundary is 28.2); `ScriptedPlayerController` answers only at the pass-priority prompt.
- `BaselineEngine.ensureStaticData` bootstrap creates temp dirs per JVM run (leaks a few empty dirs; acceptable for headless/test use).
- Baseline key encoding: lower 28 bits are a SIGNED id (bit 27 = sign), same as `DeltaPacket.getIdFromDeltaKey`; ids above `0x07FFFFFF` do not round-trip (real ids are small).
- `DeltaPacket` itself still has NO schema VERSION constant (baseline carries `BaselineFormat.VERSION`); keep `ReplayRecorder`/`ReplayClient` wire-compatible (audit S8/A7).
- `docs/baseline/Coupling-Audit.md` line refs are point-in-time; re-verify before relying on them after engine refactors.
- `FDeckChooser.updateCustom` `default:` branch kept (reachable — constructed decks selectable in lobby via `CONSTRUCTED_DECK` type).
- i18n scan method: keys = `grep -E '^[A-Za-z0-9_]+=' en-US.properties | cut -d= -f1`; zero-ref = no `rg -F --glob '*.java'` hit AND no non-Java hit outside PLAN/PROGRESS; dynamic key builders: `CardTranslation` `lbl<TokenType>`, `InputBase` `lbl<Day>`.
