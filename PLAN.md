# PLAN.md — Forge Rewrite: Cleanup to JVM Baseline to Rust Backend (REWRITE.md)

## Context
Per `REWRITE.md`: Phase 1 (decoupled JVM baseline) is **DONE** (Part 7); Phase 2 (direct non-JVM backend port + parity harness) is **ACTIVE** (Part 8). Pre-rewrite cleanup (Parts 1-6) trimmed the baseline to a **single-player Commander (EDH)** experience: no Brawl/Oathbreaker/Tiny Leaders/other variants, no online multiplayer.

## Confirmed Decisions (cleanup era, binding)
1. Removed BOTH the interactive tutorial and the static `howto.txt` doc + its Help-menu entry.
2. Kept `DialogDownloadCardImages` + first-run bulk-CDN-sync prompt in `FView` (card-art UX).
3. Lobby slot panels show the selected deck's commander card image, fallback `FSkinProp.ICO_PLAYERS`.
4. Commander **bracket** (EDHREC skill tiers) kept; per-format **banlist** validation (`GameFormat.bannedCardNames`, `DeckFormat.isLegalCard/isLegalCommander`, `DeckRecognizer.isBannedInFormat`, format data files) is separate and STAYS.
5. `commander-bracket-combos.txt` renamed to `commander-combos.txt` (AI two-card-combo knowledge; AI is KEEP).
Part 6 decisions also binding: **D1a** avatar *data* feature kept (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `FSkinProp.IMG_ZONE_AVATAR`, `LobbySlot.AvatarVanguard`, `CEditorConstructed` avatar pool; MomirBasic/MoJhoSto game types gone); **D2** archived/Block legality formats + `FPref.LOAD_ARCHIVED_FORMATS` kept; **D3** Planes/Schemes/Dungeon deck-editor sections kept.

## Hard KEEP Lists (card-art / shared infra — do not break)
- Card art: `ImageFetcher`, `SwingImageFetcher`, `CdnUuidCache`, `Scryfall*` sync/limiter, `GuiDownload*` services, `GuiDownloader`, `DialogDownloadCardImages`, `FView` first-run prompt.
- App update: `AutoUpdater`, `RSSReader`, `GITHUB_*`/`RELEASE_URL`, `FControl`/`FTitleBarBase` updater paths, `CSubmenuPreferences` auto-updater channel.
- Core deck validation (all of it), sleeves, audio, `ImportDialog`, `BugReporter`, bundled README/CHANGES + git-changelog packaging.
- `NameGenerator` + auto-naming + manual `PLAYER_NAME` pref (only the visible generator UI went).
- `GameType.Puzzle` core remnants (separate mode, follow-up iteration).
- Generic seams: `Match.startGame(Game, Runnable)` / `GameAction.startGame` / `PhaseHandler.setupFirstTurn/startFirstTurn(…, Runnable)`; `IGuiBase.isMatchInProgress()`.

## Git & Execution Rules
- **Two local repos**: `forge` (branch `dev`) and `forge-rs` (new, Part 8). NEVER `git push`, NEVER `git reset --hard`, no destructive commands outside the workspace; all commits stay local for review.
- **Atomic local commit per completed `- [ ]` item.** forge repo: code + `PLAN.md` (`[ ]` to `[x]`) + `PROGRESS.md` (40 lines max) in ONE commit, style `chore(commander): …`. forge-rs repo: code commit first (Conventional Commits, e.g. `feat(engine): …`), then the PLAN/PROGRESS commit in the forge repo (separate repos = two local commits per item).
- Before deleting/renaming any symbol, file, i18n key, or menu entry, search all references (`rg`).
- All Maven AND cargo commands: **600000 ms** timeout (see `forge-test-runner` skill).
- Scratch/test artifacts only under `/tmp/opencode/`.

## Verification Commands
forge repo:
- `GATE` — `mvn test -pl forge-gui-desktop,forge-baseline -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms)
- `PKG` — `mvn package -DskipTests -T 1C` (600000 ms)
- `FULL` — `mvn test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms)
forge-rs repo:
- `RUSTGATE` — `cargo fmt --all --check && cargo clippy --workspace --all-targets -- -D warnings && cargo test --workspace` (600000 ms)

## Completed Parts (all done, committed on `dev`)
### Parts 1-6 — Pre-Rewrite Cleanup: baseline trimmed to single-player Commander
- 1-4: team/2HG logic + UI, achievements/stats/match logic, deckgen + Commander deck-selection options (items 1.1-13.1).
- 5: Oathbreaker/Brawl/TinyLeaders (engine/formats/data/storage/editor/lobby/i18n) + online multiplayer (lobby UI, host/join, server, client/reconnect, chat, net prefs, tests). Kept: Option A replay protocol subset (`IRemote` to codec to `ProtocolGuiGame` + `ReplayClient` over Netty `EmbeddedChannel`), generic lobby seams, headless harness (items 14.1-17.5).
- 6: comprehensive post-Commander dead-code sweep — engine dead branches, deckgen, deck storage/DeckType, editor, enum pruning (`GameType` to Constructed+Commander; `DeckFormat` to Constructed/Commander/Pauper/Vanguard), res data, i18n orphans, final sweep (items 18.1-25.2).
- Standalone fix: `DeckStorage` rootDir trailing-separator crash (e17b8904590).

### Part 7 — REWRITE.md Phase 1: Decoupled JVM Baseline & State-Machine Testing (26.1-32.2)
- `forge-baseline` module (reactor after forge-gui): versioned `BaselineSnapshot`/`BaselineDelta` (VERSION=2; key encoding byte-for-byte compatible with `DeltaPacket`: `(type<<28)|(id&0x0FFFFFFF)`, type tags 0-4), `BaselinePrompt` (16 kinds, engine-computed legal candidates), `BaselineAction` (15 sealed variants, 1:1 with `IGameController`/`ProtocolMethod`).
- `BaselineEngine` headless adapter over the direct Match/Game path: seeded `MyRandom`, pending prompts exposed as snapshot state, choices accepted as actions; the CMatchUI path untouched.
- Tests: transition/invariant unit tests; 3 seeded golden traces (`golden-3000{1,2,3}.trace`, 57 steps, per-step SHA-256 state hashes); headless full match under a UI-class touch guard; codec round-trips + version-mismatch rejection; `BaselineBatchRunner` + per-decision JSONL trajectory shards.
- Docs: `docs/baseline/Coupling-Audit.md`, `docs/baseline/StateActionContract.md`, `docs/baseline/TrajectoryShards.md`.
- Final gate 32.2: GATE 2685 tests green + PKG + UI e2e through untouched CMatchUI. **REWRITE.md Phase 1 complete.**

## Part 8 — REWRITE.md Phase 2: Direct Non-JVM (Rust) Backend Port & Parity Harness (Unoptimized)

### Context & Goal
Port the single-player game/rules/AI engine into a **new standalone Rust repo** with 100% functional parity — no optimizations, no logic changes. **Server only**: no UI in Rust. End state of this phase: the forge backend runs on Rust, and the existing Swing UI (Java frontend) connects to it and plays single-player Commander (human vs AI). Out of scope: performance (Phase 3), new frontends (Phase 4), online/multiplayer, ML tensor schemas.

### Decisions (confirmed)
- **P1.** New repo `~/Projects/forge-rs` (own git repo, local commits only). Rust workspace crates: `forge-protocol` (state/action/wire types), `forge-data` (card/deck data), `forge-engine` (rules state machine), `forge-ai` (AI decisions), `forge-server` (TCP transport), `parity` (harness).
- **P2.** Wire format: **JSON lines over TCP** (one JSON object per line; serde in Rust, Gson in Java). The contract leaves transport/serialization to the backend's choice; the *schema* (`BaselineSnapshot`/`BaselineDelta`/`BaselinePrompt`/`BaselineAction`), `VERSION=2`, and key encoding are NOT the backend's choice. The Java binary codec is NOT reimplemented in this phase.
- **P3.** Parity surface: `BaselineStateHasher`'s canonical per-step state string (SHA-256), `GoldenActionCodec` action tokens, and the `GoldenTrace` text format are the shared fixture format; first corpus = the 3 committed golden traces.
- **P4.** Card data: the Rust engine reads forge data files directly from `forge-gui/res/` (34,481 card txt files in `cardsfolder/`, `lists/TypeLists.txt` + combo lists, `formats/`, `editions/`) via a `--data-dir` path — no data reformatting. (Confirmed for now; migrating the data into `forge-rs` is deferred to later.)
- **P5.** RNG: port `MyRandom` bit-for-bit — `java.util.Random` LCG (seed scramble `^ 0x5DEECE66D`, 64-bit LCG, `nextInt()` = top 32 bits) with the `nextInt() >>> (32-bits)` derivation and thread-local stream semantics; verify against committed RNG vectors (first N draws per seed) emitted by the Java side.

### Phase 33 — Rust Toolchain & Workspace Scaffold
- [x] 33.1 Verify Rust toolchain (rustup, stable) — verify `cargo --version` / `rustc --version`.
- [x] 33.2 Scaffold `~/Projects/forge-rs` per P1 (git init, README, workspace Cargo.toml, six empty crates, `docs/protocol.md` + `docs/parity.md` stubs); RUSTGATE green on the empty scaffold.

### Phase 34 — Parity Harness (both sides)
- [x] 34.1 Java: `forge.baseline.parity` main class (manual, NOT in GATE): given `(seed, decks, actions|policy)` run `BaselineEngine` and emit a parity report in `GoldenTrace` format (per-step `<action-token> <sha256-of-canonical-state-string>` + final outcome), reusing `BaselineStateHasher`/`GoldenActionCodec`; optional full canonical state strings per step for divergence diffs. GATE green.
- [ ] 34.2 Rust: `parity` crate — runner (consumes the same inputs, runs the Rust engine, emits the same report) + `parity-check` (compares a committed Java report vs a Rust run line-by-line; on mismatch prints the first divergent step + both canonical state strings). RUSTGATE green.
- [ ] 34.3 Fixtures + docs: copy the 3 golden traces into `forge-rs` (`fixtures/golden/`); finalize `docs/parity.md` (input spec, report format, how to add corpus entries); pointer note in forge `docs/baseline/StateActionContract.md` 6.2. GATE + RUSTGATE green.

### Phase 35 — Core Rules & Zones (golden-trace parity)
- [ ] 35.1 `forge-protocol`: port the schemas (`BaselineSnapshot`/`BaselineDelta`/`BaselinePrompt`/`BaselineAction`, VERSION=2, key encoding, type tags 0-4) + serde JSONL wire; unit tests with Java-emitted parity vectors (key encoding incl. negative ids, version gate).
- [ ] 35.2 `forge-data` golden slice: card loader for the traces' card subset + `TypeLists.txt` parsing (the 30.1 gotcha: basic lands need tap-for-mana) + Commander format rules + deck-file parser for harness inputs.
- [ ] 35.3 `forge-engine` core: mulligan, turn structure (beginning/main/combat/end), priority/pass, zones (hand, battlefield+tap, library, graveyard, exile, command), stack (cast/resolve), mana (pool, payment, `CostPartMana.canPay` stub semantics), the synthetic "Commander Effect" command-zone cast, life totals, win/loss (life zero, milling); bit-for-bit `MyRandom` port (P5) with the committed vector test.
- [ ] 35.4 Parity: Rust engine replays golden-3000{1,2,3} — per-step SHA-256 equality, 57 steps each; committed test in `parity` runs under RUSTGATE. **Acceptance gate for Phase 35.**

### Phase 36 — Cards, Triggers, Replacement Effects & AI (increments, parity-gated)
- [ ] 36.1 Java: corpus generator (main class in `forge.baseline.parity`, NOT in GATE): K seeded AI-vs-AI matches via `BaselineBatchRunner` (fixed policy mix) — emit committed corpus inputs + reports under `forge-rs/fixtures/corpus/`. GATE green.
- [ ] 36.2 `forge-data` full: parse the full card DB (34,481 cards) — fields, types/supertypes/subtypes, abilities (static/activated/instantaneous), triggers, replacement effects, formats, editions, combo lists; count + per-card field-hash spot-checks against a Java-emitted manifest.
- [ ] 36.3 `forge-engine` abilities: port card ability/script execution + continuous/replacement layers in frequency-ordered increments; each increment adds the corpus games that exercise it and keeps the full corpus green (RUSTGATE).
- [ ] 36.4 `forge-ai`: port the AI decision logic (`PlayerControllerAi` equivalent, combo knowledge from `commander-combos.txt` etc.) — parity on full AI-vs-AI matches (no scripted policy).
- [ ] 36.5 Expand corpus to real Commander decks (fixed deck list, fresh seeds) — full corpus green; parity status recorded in PROGRESS.md.

### Phase 37 — Server Transport & Swing UI Connection (drop-in backend)
- [ ] 37.1 `forge-server`: TCP JSONL server — handshake (protocol VERSION), start match `(seed, decks, players)`, stream snapshot/delta lines, accept action lines, terminal outcome line; finalize `docs/protocol.md` (framing, line types, error handling).
- [ ] 37.2 Java client: bidirectional adapter — `IGameController` actions to JSONL action lines; JSONL snapshot/delta lines to the kept `setGameView`/`applyDelta` `GuiGameEvent`s driving `ProtocolGuiGame`/`AbstractGuiGame.applyDelta` (view-graph reconstruction); `CMatchUI` untouched.
- [ ] 37.3 UI wiring: lobby option for backend (local JVM / Rust server) — Start Game with Rust selected connects to `localhost:<port>` and plays through the untouched `CMatchUI`. GATE + PKG green.
- [ ] 37.4 E2E (forge-ui-e2e skill): local Commander match (human vs AI) against the running Rust server — mulligan, land drop, prompts, turn passage, win/loss; screenshots; RUSTGATE + GATE + PKG green.

### Phase 38 — Phase 2 Final Gate
- [ ] 38.1 Final: full parity corpus green (RUSTGATE) + forge GATE + PKG + e2e vs the Rust backend; docs updated (forge-rs README, `docs/protocol.md`, `docs/parity.md`; forge `docs/baseline/` pointer); PLAN/PROGRESS updated.
