package forge.baseline.engine;

import forge.baseline.state.BaselineDelta;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;
import forge.game.GameOutcome;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;
import forge.gamemodes.net.DeltaPacket;
import forge.trackable.TrackableCollection;
import forge.trackable.TrackableObject;
import forge.trackable.TrackableProperty;
import forge.trackable.TrackableTypes;
import org.testng.annotations.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the 28.1/28.2 adapter contract: a seeded Commander game runs headless
 * over the direct Match/Game path, fed {@code BaselineAction}s drive the
 * scripted player, and the same seed plus the same actions emit an identical
 * stream of baseline envelopes — including the prompt snapshots the adapter
 * emits before each pass-priority block (28.2).
 */
public class BaselineEngineShould {

    private static final long SEED = 20261123L;

    @Test
    public void shouldRunASeededGameHeadlessAndConcedeOnTheFedAction() throws Exception {
        RunResult run = runOnce(SEED);

        assertThat(run.engine.getFailure()).as("game thread failure").isNull();
        assertThat(run.engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();

        BaselineSnapshot snapshot = run.engine.getSnapshot();
        assertThat(snapshot).as("initial snapshot").isNotNull();
        assertThat(snapshot.getPrompt()).as("28.1 carries no prompt").isNull();
        assertThat(snapshot.getGameView().getPlayers()).hasSize(2);

        List<BaselineDelta> deltas = run.engine.getDeltas();
        assertThat(deltas).as("deltas were emitted").isNotEmpty();
        long lastSeq = snapshot.getSequenceNumber();
        for (BaselineDelta delta : deltas) {
            assertThat(delta.getSequenceNumber()).as("sequences are monotonic").isGreaterThan(lastSeq);
            lastSeq = delta.getSequenceNumber();
        }

        GameOutcome outcome = run.engine.getOutcome();
        assertThat(outcome).as("outcome recorded").isNotNull();
        assertThat(outcome.isWinner(run.scriptedLobbyPlayer)).isFalse();
        assertThat(outcome.isWinner(run.aiLobbyPlayer)).isTrue();
    }

    @Test
    public void shouldEmitAnIdenticalEnvelopeStreamForTheSameSeedAndActions() throws Exception {
        List<String> first = runOnce(SEED).fingerprints;
        List<String> second = runOnce(SEED).fingerprints;
        assertThat(first).as("a stream was produced").isNotEmpty();
        assertThat(second).as("same seed + same actions -> identical envelope stream").isEqualTo(first);
    }

    private static RunResult runOnce(long seed) throws Exception {
        EngineRig.Result rig = EngineRig.create(seed);
        BaselineEngine engine = rig.engine();

        EnvelopeFingerprinter fingerprinter = new EnvelopeFingerprinter();
        List<String> fingerprints = new ArrayList<>();
        engine.onEnvelope(envelope -> fingerprints.add(fingerprinter.fingerprint(envelope)));

        engine.start();

        // The snapshot is emitted at the start of turn 1, before any decision,
        // so the hand read from it is exactly the hand at the first decision.
        BaselineSnapshot snapshot = EngineRig.awaitInitialSnapshot(engine);

        PlayerView scriptedView = snapshot.getGameView().getPlayers().get(0);
        CardView firstLand = null;
        for (CardView card : scriptedView.getHand()) {
            if (card.getCurrentState().isLand()) {
                firstLand = card;
                break;
            }
        }
        if (firstLand != null) {
            engine.feed(new forge.baseline.action.BaselineAction.SelectCard(firstLand, null));
        }
        engine.feed(new forge.baseline.action.BaselineAction.Concede());

        assertThat(engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();

        return new RunResult(engine, rig.scripted(), rig.ai(), fingerprints);
    }

    private static final class RunResult {
        final BaselineEngine engine;
        final ScriptedLobbyPlayer scriptedLobbyPlayer;
        final forge.ai.LobbyPlayerAi aiLobbyPlayer;
        final List<String> fingerprints;

        RunResult(BaselineEngine engine, ScriptedLobbyPlayer scriptedLobbyPlayer,
                  forge.ai.LobbyPlayerAi aiLobbyPlayer, List<String> fingerprints) {
            this.engine = engine;
            this.scriptedLobbyPlayer = scriptedLobbyPlayer;
            this.aiLobbyPlayer = aiLobbyPlayer;
            this.fingerprints = fingerprints;
        }
    }

    /**
     * Canonical fingerprint of a baseline envelope stream. Object references
     * are replaced by first-appearance ranks, so two runs of the same game
     * compare equal even though JVM-wide ID counters (game IDs,
     * spell-stack-item IDs) allocate different absolute IDs per run.
     */
    static final class EnvelopeFingerprinter {

        private final Map<Integer, Integer> ranks = new HashMap<>();
        private final Map<Integer, String> expanded = new HashMap<>();
        private final Deque<Integer> onPath = new ArrayDeque<>();
        private int nextRank;

        String fingerprint(Object envelope) {
            if (envelope instanceof BaselineSnapshot snap) {
                return "SNAP[v" + snap.getVersion() + ",seq=" + snap.getSequenceNumber()
                        + "," + canonObject(snap.getGameView())
                        + (snap.getPrompt() == null ? "" : ",P" + canonPrompt(snap.getPrompt()))
                        + "]";
            }
            if (envelope instanceof BaselineDelta delta) {
                StringBuilder sb = new StringBuilder("DELTA[seq=").append(delta.getSequenceNumber()).append(';');
                List<Integer> keys = new ArrayList<>(delta.getObjectDeltas().keySet());
                keys.sort(Integer::compare);
                for (int key : keys) {
                    sb.append('k').append('R').append(rankOf(key)).append('=');
                    canonDeltaProps(sb, delta.getObjectDeltas().get(key));
                }
                sb.append('|');
                // newObjects keeps parent-before-child insertion order
                for (Map.Entry<Integer, Map<TrackableProperty, Object>> entry : delta.getNewObjects().entrySet()) {
                    sb.append('n').append('R').append(rankOf(entry.getKey())).append('=');
                    canonDeltaProps(sb, entry.getValue());
                }
                return sb.append(']').toString();
            }
            throw new IllegalArgumentException("unknown envelope: " + envelope);
        }

        /**
         * Canonical form of a pending prompt: family, asked player, message,
         * candidates (wire-typed views rank-normalized, others structural),
         * counts, and the optional flag.
         */
        private String canonPrompt(BaselinePrompt prompt) {
            StringBuilder sb = new StringBuilder();
            sb.append(prompt.getKind().name()).append(';');
            sb.append("pid=R").append(rankOf(DeltaPacket.makeDeltaKey(DeltaPacket.TYPE_PLAYER_VIEW,
                    prompt.getPlayerId()))).append(';');
            sb.append("msg=").append(prompt.getMessage() == null ? "Z" : canonScalar(prompt.getMessage()))
                    .append(';');
            for (CardView card : prompt.getCardCandidates()) {
                sb.append('c').append(canonObject(card));
            }
            for (SpellAbilityView ability : prompt.getAbilityCandidates()) {
                sb.append('a').append(canonObject(ability));
            }
            for (PlayerView player : prompt.getPlayerCandidates()) {
                sb.append('p').append(canonObject(player));
            }
            for (StackItemView item : prompt.getStackItemCandidates()) {
                sb.append('s').append(canonObject(item));
            }
            sb.append("min=").append(prompt.getMinCount()).append(';');
            sb.append("max=").append(prompt.getMaxCount()).append(';');
            if (prompt.isOptional()) {
                sb.append("opt;");
            }
            return sb.toString();
        }

        /** Ranks an object by (wire type, id) in first-appearance order across the whole run. */
        private int rankOf(int deltaKey) {
            return ranks.computeIfAbsent(deltaKey, key -> nextRank++);
        }

        /**
         * Live graph walk for the snapshot: memoized expansion with cycle
         * cutting, properties in ordinal order, collections in list order,
         * maps sorted by canonical entry.
         */
        private String canonObject(TrackableObject obj) {
            if (DeltaPacket.typeTagFor(obj) < 0) {
                // Wire-less object (e.g. CombatView): canonicalize structurally.
                StringBuilder sb = new StringBuilder("C{");
                canonProps(sb, obj.getProps());
                return sb.append('}').toString();
            }
            int key = DeltaPacket.makeDeltaKey(obj);
            int rank = rankOf(key);
            if (onPath.contains(key)) {
                return "O" + rank;
            }
            String memo = expanded.get(key);
            if (memo != null) {
                return memo;
            }
            onPath.push(key);
            StringBuilder sb = new StringBuilder("O").append(rank).append('{');
            canonProps(sb, obj.getProps());
            String result = sb.append('}').toString();
            onPath.pop();
            expanded.put(key, result);
            return result;
        }

        private void canonProps(StringBuilder sb, Map<TrackableProperty, Object> props) {
            List<Map.Entry<TrackableProperty, Object>> entries = new ArrayList<>(props.entrySet());
            entries.sort(Comparator.comparingInt(e -> e.getKey().ordinal()));
            for (Map.Entry<TrackableProperty, Object> entry : entries) {
                sb.append(entry.getKey().ordinal()).append(':').append(canonValue(entry.getValue())).append(';');
            }
        }

        private void canonDeltaProps(StringBuilder sb, Map<TrackableProperty, Object> props) {
            List<Map.Entry<TrackableProperty, Object>> entries = new ArrayList<>(props.entrySet());
            entries.sort(Comparator.comparingInt(e -> e.getKey().ordinal()));
            for (Map.Entry<TrackableProperty, Object> entry : entries) {
                sb.append(entry.getKey().ordinal()).append(':')
                        .append(canonDeltaValue(entry.getKey(), entry.getValue())).append(';');
            }
        }

        /** Live graph value (snapshot): objects are walked, everything else canonicalized. */
        private String canonValue(Object value) {
            if (value == null) {
                return "Z";
            }
            if (value instanceof TrackableObject to) {
                return canonObject(to);
            }
            if (value instanceof TrackableCollection<?> collection) {
                StringBuilder sb = new StringBuilder("L").append(collection.size()).append('[');
                for (Object item : collection) {
                    sb.append(canonValue(item));
                }
                return sb.append(']').toString();
            }
            if (value instanceof Map<?, ?> map) {
                List<String> entries = new ArrayList<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    entries.add(canonValue(entry.getKey()) + "=>" + canonValue(entry.getValue()));
                }
                Collections.sort(entries);
                return "M" + map.size() + "{" + String.join(",", entries) + "}";
            }
            if (value instanceof Iterable<?> iterable) {
                int size = value instanceof List<?> list ? list.size() : -1;
                StringBuilder sb = new StringBuilder("L").append(size).append('[');
                for (Object item : iterable) {
                    sb.append(canonValue(item));
                }
                return sb.append(']').toString();
            }
            return canonScalar(value);
        }

        /**
         * Wire value (deltas): object references arrive as Integer IDs or
         * {@code {marker, id}} pairs and are rank-normalized; everything else
         * is canonicalized as a scalar.
         */
        private String canonDeltaValue(TrackableProperty prop, Object value) {
            if (value == null) {
                return "Z";
            }
            TrackableTypes.TrackableType<?> type = prop.getType();

            if (value instanceof Integer id) {
                if (type == TrackableTypes.CardViewType) {
                    return "R" + rankOf(DeltaPacket.makeDeltaKey(DeltaPacket.TYPE_CARD_VIEW, id));
                }
                if (type == TrackableTypes.PlayerViewType) {
                    return "R" + rankOf(DeltaPacket.makeDeltaKey(DeltaPacket.TYPE_PLAYER_VIEW, id));
                }
                if (type == TrackableTypes.StackItemViewType) {
                    return "R" + rankOf(DeltaPacket.makeDeltaKey(DeltaPacket.TYPE_STACK_ITEM_VIEW, id));
                }
                return "N" + id; // CardStateView ordinals and plain scalars
            }
            if (value instanceof int[] ref && ref.length == 2) {
                int t = ref[0] == 0 ? DeltaPacket.TYPE_CARD_VIEW : DeltaPacket.TYPE_PLAYER_VIEW;
                return "R" + rankOf(DeltaPacket.makeDeltaKey(t, ref[1]));
            }
            if (value instanceof List<?> list) {
                int t = -1;
                if (type == TrackableTypes.CardViewCollectionType) {
                    t = DeltaPacket.TYPE_CARD_VIEW;
                } else if (type == TrackableTypes.PlayerViewCollectionType) {
                    t = DeltaPacket.TYPE_PLAYER_VIEW;
                } else if (type == TrackableTypes.StackItemViewListType) {
                    t = DeltaPacket.TYPE_STACK_ITEM_VIEW;
                }
                StringBuilder sb = new StringBuilder("L").append(list.size()).append('[');
                for (Object item : list) {
                    if (t >= 0) {
                        sb.append(item == null ? "Z" : "R" + rankOf(DeltaPacket.makeDeltaKey(t, (Integer) item)));
                    } else {
                        sb.append(canonScalar(item));
                    }
                }
                return sb.append(']').toString();
            }
            if (value instanceof DeltaPacket.CombatData combat) {
                return canonCombatData(combat);
            }
            return canonScalar(value);
        }

        private String canonCombatData(DeltaPacket.CombatData combat) {
            StringBuilder sb = new StringBuilder("CD[");
            sb.append(canonIdBands(combat.bandAttackerIds));
            for (int[] ref : combat.bandDefenderRefs) {
                int t = ref[0] == 0 ? DeltaPacket.TYPE_CARD_VIEW : DeltaPacket.TYPE_PLAYER_VIEW;
                sb.append("R").append(rankOf(DeltaPacket.makeDeltaKey(t, ref[1]))).append(',');
            }
            sb.append(',').append(canonIdBands(combat.bandBlockerIds));
            sb.append(',').append(canonIdBands(combat.bandPlannedBlockerIds));
            return sb.append(']').toString();
        }

        private String canonIdBands(List<List<Integer>> bands) {
            StringBuilder sb = new StringBuilder('B');
            for (List<Integer> band : bands) {
                if (band == null) {
                    sb.append("Z,");
                    continue;
                }
                sb.append(band.size()).append('[');
                for (int id : band) {
                    sb.append("R").append(rankOf(DeltaPacket.makeDeltaKey(DeltaPacket.TYPE_CARD_VIEW, id)));
                }
                sb.append("],");
            }
            return sb.toString();
        }

        private String canonScalar(Object value) {
            if (value instanceof String s) {
                return "S" + s.length() + "(" + s + ")";
            }
            if (value instanceof Enum<?> e) {
                return "E" + e.name();
            }
            if (value instanceof Boolean b) {
                return "B" + b;
            }
            if (value instanceof Number n) {
                return "N" + n;
            }
            if (value instanceof Character c) {
                return "C" + (int) c.charValue();
            }
            return "V" + value;
        }
    }
}
