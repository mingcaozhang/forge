# Forge Commander Refactor — Progress Notes

## Baseline facts (verified)
- Working branch: `dev`. Never `git push`; keep commits local.
- `rg --type java` matches `.properties` too — use `--glob '*.java'` for Java-only ref checks.
- GATE: `mvn test -pl forge-gui-desktop,forge-baseline -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`. Final gates add PKG: `mvn package -DskipTests -T 1C`. 600000 ms timeout on Maven runs.
- `AiReportScenarioTest` is flaky under full-parallel GATE (green in isolation / rerun).
- Stale `target/` once caused 364 false `NoClassDefFoundError` failures; `mvn clean` fixes — if GATE fails with NoClassDefFound on untouched classes, clean before debugging.
- `Game`/`Match` live in `forge-game/src/main/java/forge/game/` (NOT `mage/core`); engine is Swing-free, forge-gui is Swing-free except `AutoUpdater`.

## Recently completed
- **27.2** — Canonical action model in `forge.baseline.action` (new pkg; may use Swing-free `forge.gamemodes.match` data types — guarded by `ActionPackageIsSwingFreeShould`): sealed `BaselineAction` = 15 record variants, 1:1 with the `IGameController` client surface via `ProtocolMethod` (trailing `ITriggerEvent` param dropped — wire already sends null per `NetGameController`); mapping table + exclusions (queries, macros, local yield setters, server→client pushes, IGuiGame prompt-channel families) documented in Javadoc. `BaselinePrompt` (state pkg, builder, 16 `Kind`s, candidate lists normalized null→empty immutable) added to `BaselineSnapshot` (`getPrompt()`/`hasPrompt()`, legacy ctor = no prompt); `BaselineFormat.VERSION` 1→2 (now gates snapshot+delta+action schema). Tests: machine-checked mapping (name/declaring class/full arg list + component assignability), protocol-surface completeness (15 + `getActivateDescription` query), sealed-closure check, codec round-trip w/ tracker-resolved identity, snapshot-with-prompt round-trip. Java 17: no pattern switches; `assertThat` on `Iterable` types (e.g. `ColorSet`) needs `(Object)` cast; generic `ArrayList` copy needs explicit loop. GATE+PKG exit 0.
- **27.1** — New `forge-baseline` module (reactor after `forge-gui`, deps: core/game/ai/gui + TestNG/AssertJ test). `forge.baseline.state` (UI-agnostic, guard-tested): `BaselineFormat` (`VERSION=1`, type tags, key encode/decode mirroring `DeltaPacket`), `BaselineSnapshot` (full GameView root), `BaselineDelta` (DeltaPacket-schema envelope), `BaselineVersionException`. Tests: 11/11 green — key parity vs `DeltaPacket.makeDeltaKey` for all 5 tags, full snapshot + delta round-trips through the kept `CompatibleObjectEncoder/Decoder` (EmbeddedChannel, `GuiGameEvent(setGameView/applyDelta)` wrap, tracker re-attach like `GameClientHandler`), no-FModel 2-player AI `Game` via `TestMatch` helper (Localizer/Lang from `../forge-gui/res/languages`, overridable via `-Dforge.baseline.langDir`). GATE+PKG exit 0.
- **26.1** — Wrote `docs/baseline/Coupling-Audit.md`: all engine↔UI runtime seams with file:line refs (S1 event path, S2 Trackable graph, S3 delta engine, S4 prompt/latch, S5 action channel, S6 lifecycle, S7 threading, S8 replay) + the A1–A8 seam list the Phase 28 adapter must own. Doc-only commit.

## Active caveats / follow-ups
- Part 7 (REWRITE.md Phase 1) in progress: next item is 27.3 (design doc `docs/baseline/StateActionContract.md`).
- Baseline key encoding: lower 28 bits are a SIGNED id (bit 27 = sign), same as `DeltaPacket.getIdFromDeltaKey`; ids above `0x07FFFFFF` do not round-trip (real ids are small).
- `DeltaPacket` itself still has NO schema VERSION constant (baseline carries `BaselineFormat.VERSION`); keep `ReplayRecorder`/`ReplayClient` wire-compatible (audit S8/A7).
- `docs/baseline/Coupling-Audit.md` line refs are point-in-time; re-verify before relying on them after engine refactors.
- `FDeckChooser.updateCustom` `default:` branch kept (reachable — constructed decks selectable in lobby via `CONSTRUCTED_DECK` type).
- i18n scan method: keys = `grep -E '^[A-Za-z0-9_]+=' en-US.properties | cut -d= -f1`; zero-ref = no `rg -F --glob '*.java'` hit AND no non-Java hit outside PLAN/PROGRESS; dynamic key builders: `CardTranslation` `lbl<TokenType>`, `InputBase` `lbl<Day>`.
