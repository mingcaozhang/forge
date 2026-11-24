# Trajectory Shards (Phase 32.1)

Headless AI-vs-AI Commander matches run through `BaselineEngine` and exported
as per-decision JSONL shards: the pre-tensor training data for the ML side.
The **ML tensor schema is deliberately not part of this format** — shards are
a documented, deterministic, self-contained superset of it (see
`StateActionContract.md`, section 6, "Out of scope").

Components (all in `forge-baseline`, package `forge.baseline.trajectory`,
except the hook, which is on the engine):

| type | role |
|---|---|
| `BaselineBatchRunner` | runs N seeded matches headlessly (fixed thread pool, one game per thread) and writes one shard per match |
| `BaselineBatchRunner.MatchSpec` / `MatchSpecFactory` | one match = `(seed, decks, lobbyPlayers)`; the factory must be deterministic in the match index and return **fresh** `Deck`/`LobbyPlayer` objects (the game mutates them) |
| `ActionPolicy` | the policy side of the AI-vs-AI pair: `BaselineAction choose(BaselineSnapshot)` — pure in the snapshot |
| `BaselineObserver` (engine package) | the state-observer hook on `BaselineEngine`: `onDecision(BaselineDecision)` per pass-priority decision of the scripted player (prompt snapshot + fed action), `onOutcome(BaselineMatchOutcome)` exactly once, terminal |
| `JsonlShardWriter` | the observer that writes the shard file; `SCHEMA` = line-format version |

## Running a batch

```java
Path dir = ...; // any writable directory
BaselineBatchRunner.BatchResult result = BaselineBatchRunner.run(
        index -> new BaselineBatchRunner.MatchSpec(
                32001L + index,
                List.of(deckA(), deckB()),                    // one per player
                List.of(new ScriptedLobbyPlayer("Policy"),
                        new LobbyPlayerAi("AI", null))),
        10,            // matchCount
        4,             // parallelism (1 = sequential)
        myPolicy,      // ActionPolicy
        dir,           // shardDir
        240_000);      // per-match timeout, ms
```

Each spec needs exactly **one** `ScriptedLobbyPlayer` (the policy-driven side)
and no duplicated player names. The runner re-initializes the idempotent
singletons the engine reads (`Localizer`/`Lang`); `StaticData` bootstrap is
synchronized inside the engine. A stalled match (no outcome within the
timeout) fails the batch with an `IllegalStateException`.

Shards land in `shardDir` as `match-%04d-seed-%d.jsonl` (one file per match,
one JSON object per line, UTF-8, `null` serialized as `null`).

## Line schema

Every line carries the envelope:

```json
{"schema":1,"type":"header|decision|outcome","match":0,"seed":32001,...}
```

`schema` is the line-format version (`JsonlShardWriter.SCHEMA`, currently `1`);
bump it on any incompatible change to the line shapes.

### 1. `header` — first line

```json
{"schema":1,"type":"header","match":0,"seed":32001,"formatVersion":2,
 "players":["Scripted","AI"]}
```

`formatVersion` is the state/prompt/action contract version
(`BaselineFormat.VERSION`). `players` are the lobby names, in lobby order
(player 0 first).

### 2. `decision` — one line per pass-priority decision of the scripted player

```json
{"schema":1,"type":"decision","match":0,"seed":32001,"decision":11,"sequence":28,
 "state":{...},"prompt":{...},"action":{...}}
```

- `decision` — 0-based, sequential per match (== the shard's line index − 2).
- `sequence` — the engine's global prompt-sequence number for the same
  snapshot (cross-references the Phase 28–30 snapshot/delta stream).

**`state`** — the snapshot at the moment of the decision:

| field | meaning |
|---|---|
| `hash` | the canonical `BaselineStateHasher` fingerprint (64 hex) — the same value the golden traces (30.1) record; cross-checkable per decision |
| `turn`, `phase`, `activePlayer`, `mulligan`, `gameOver`, `stackSize` | scalar game state |
| `players[]` | per player: `id`, `name`, `life`, `landThisTurn`, `mana` (`W U B R G C`), `zones` |
| `players[].zones` | `hand`, `battlefield` (objects with `id` + `tapped`), `library`, `graveyard`, `exile`, `command` — card ids, sorted ascending |

**`prompt`** — the `SELECT_ABILITY` prompt the scripted player answered
(engine-computed legal candidates, canonical order):

| field | meaning |
|---|---|
| `kind`, `player`, `message`, `optional`, `minCount`, `maxCount` | prompt envelope |
| `cardCandidates[]` | `{id, name, zone}` — card-data ids |
| `abilityCandidates[]` | `{id, host, hostName, zone, description}` — `id` is the per-match re-based ability id (see Ids below), `host` the host card's card-data id |
| `playerCandidates[]` | `{id, name}` |
| `stackItemCandidates[]` | `{id, key}` — `id` re-based |
| `stringCandidates[]`, `numberCandidates[]` | literal values |
| `colorCandidates` | mana-symbol string, or `null` |

**`action`** — the fed `BaselineAction`, by variant name plus its payload ids:

| `type` | payload fields |
|---|---|
| `SelectCard` | `card`, `otherCards[]` (card-data ids) |
| `SelectAbility` | `ability` (re-based ability id), `host` (card-data id) |
| `SelectPlayer` | `player` |
| `SelectStackItem` | `stackItem` (re-based stack id) |
| `Yield` | `update` (delta-class simple name) |
| `NextGameDecision` | `decision` |
| `UseMana` | `color` |
| `ReorderHand` | `card` (card-data id), `index` |
| `DrawOffer` | `action` |
| `Ok`, `Cancel`, `Concede`, … | none |

An action's ids always resolve against the **same decision line's**
candidates: the policy may only choose from the prompt's legal candidates and
the engine rejects anything else as a pass.

### 3. `outcome` — last line

```json
{"schema":1,"type":"outcome","match":0,"seed":32001,"completed":true,
 "failure":null,"draw":false,"winCondition":"AllOpponentsLost","turns":30,
 "decisions":254,
 "players":[{"name":"Scripted","won":false,"loss":"Milled"},
            {"name":"AI","won":true,"loss":null}]}
```

`completed` is `false` and `failure` carries the game-thread exception text
when the match died before a `GameOutcome`; then `draw`/`winCondition`/
`players` are absent or `null` and `turns`/`decisions` reflect the partial
match. `players` is in lobby order; `loss` is a `LossState` name or `null`
(winners have none).

## Ids

- **Card ids** are the stable card-data ids — identical across runs and
  machines for the same deck; an action's `card` resolves against the shard's
  state zones and card candidates.
- **Spell-ability and stack-instance ids** are assigned by the game core from
  JVM-global counters (`SpellAbility`/`SpellAbilityStackInstance` `nextId()`),
  so their absolute values interleave when matches run concurrently. The
  writer re-bases them to **per-match dense ids** (starting at 1, assigned in
  first-seen order, per kind — abilities and stack items never collide). The
  re-based id is a pure function of the match's own decision stream, so the
  shard stays a function of `(seed, decks, players, policy)` alone.
- **Player ids** are the game's 0-based player indexes.

## Determinism

For a fixed `(seed, decks, players, policy)`, the shard is **byte-identical**
across runs and across sequential vs parallel batch execution — no
timestamps, no thread or machine identity, fixed field order
(`LinkedHashMap` + Gson), canonical candidate/zone ordering. The guards:

- `MyRandom` streams are thread-local; each engine's game thread seeds its
  own stream (`setRandom(new Random(seed))`) before any game logic, so
  concurrent matches never share a random stream.
- The only shared singletons (`StaticData` bootstrap, `Localizer`/`Lang`)
  are idempotent and guarded.
- `BaselineBatchShould` asserts all of it: the line schema end-to-end,
  byte-identical sequential-vs-parallel shards, and the observer hook itself
  (every decision observed exactly once, in order, with the terminal outcome
  exactly once).
