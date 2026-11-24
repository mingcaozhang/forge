package forge.baseline.trajectory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import forge.baseline.action.BaselineAction;
import forge.baseline.engine.BaselineDecision;
import forge.baseline.engine.BaselineMatchOutcome;
import forge.baseline.engine.BaselineObserver;
import forge.baseline.golden.BaselineStateHasher;
import forge.baseline.state.BaselineFormat;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;
import forge.card.mana.ManaAtom;
import forge.game.GameOutcome;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerOutcome;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the per-decision trajectory shards of one baseline match as JSONL
 * (one JSON object per line, the Phase 32.1 shard format):
 *
 * <ol>
 *   <li>one {@code header} line (match index, seed, format version, player names);</li>
 *   <li>one {@code decision} line per pass-priority decision of the scripted
 *       player — the state snapshot (the canonical state hash plus a structured
 *       state summary), the prompt with its engine-computed legal candidates,
 *       and the fed action that answered it;</li>
 *   <li>one {@code outcome} line — the match outcome (winner(s), loss reasons,
 *       turns, win condition) or the game-thread failure.</li>
 * </ol>
 *
 * <p>Lines are deterministic for a fixed match: no timestamps, no thread or
 * machine identity, stable field order, canonical candidate/zone ordering — so
 * the shard of a match is byte-identical across runs and across sequential vs
 * parallel batch execution. The ML tensor schema is deliberately NOT part of
 * this format; the shards are the documented pre-tensor data (see
 * {@code docs/baseline/TrajectoryShards.md}).
 *
 * <p>Card ids are the stable card-data ids. Spell-ability and stack-instance
 * ids are JVM-global counters in the game core (they interleave when matches
 * run in parallel), so the writer re-bases them to per-match dense ids,
 * assigned in first-seen order; an action's ability/stack id always resolves
 * against the same shard's candidates.
 *
 * <p>Implements {@link BaselineObserver}: the engine calls {@link #onDecision}
 * and {@link #onOutcome} on the game thread; {@link #writeHeader()} is called
 * once by the batch runner after the initial snapshot. The writer is safe for
 * one producer thread (the game thread) plus {@link #close()} from the runner
 * thread after {@code awaitCompletion} returned.
 */
public final class JsonlShardWriter implements BaselineObserver, Closeable {

    /** Shard schema version. Bump on any incompatible change to the line shapes. */
    public static final int SCHEMA = 1;

    private final Path shardFile;
    private final int matchIndex;
    private final long seed;
    private final List<String> playerNames;
    private final Gson gson;
    private final BufferedWriter writer;
    private final Map<Long, Integer> instanceIds = new HashMap<>();
    private int decisions;
    private boolean closed;

    public JsonlShardWriter(Path shardFile, int matchIndex, long seed, List<String> playerNames) {
        this.shardFile = shardFile;
        this.matchIndex = matchIndex;
        this.seed = seed;
        this.playerNames = List.copyOf(playerNames);
        this.gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
        try {
            if (shardFile.getParent() != null) {
                Files.createDirectories(shardFile.getParent());
            }
            this.writer = Files.newBufferedWriter(shardFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open the shard file " + shardFile, e);
        }
    }

    /** The first line of the shard. Write it once, before the match produces decisions. */
    public void writeHeader() {
        Map<String, Object> line = baseLine("header");
        line.put("formatVersion", BaselineFormat.VERSION);
        line.put("players", new ArrayList<>(playerNames));
        write(line);
    }

    @Override
    public void onDecision(BaselineDecision decision) {
        Map<String, Object> line = baseLine("decision");
        line.put("decision", decisions++);
        line.put("sequence", decision.sequenceNumber());
        line.put("state", stateJson(decision.snapshot()));
        line.put("prompt", promptJson(decision.prompt()));
        line.put("action", actionJson(decision.action()));
        write(line);
    }

    @Override
    public void onOutcome(BaselineMatchOutcome outcome) {
        Map<String, Object> line = baseLine("outcome");
        GameOutcome result = outcome.outcome();
        line.put("completed", outcome.isCompleted());
        line.put("failure", outcome.failure() == null ? null : String.valueOf(outcome.failure()));
        line.put("draw", result == null ? null : result.isDraw());
        line.put("winCondition",
                result == null || result.getWinCondition() == null ? null : result.getWinCondition().name());
        line.put("turns", result == null ? 0 : result.getLastTurnNumber());
        line.put("decisions", decisions);
        if (result != null) {
            line.put("players", outcomePlayersJson(result));
        }
        write(line);
    }

    private List<Map<String, Object>> outcomePlayersJson(GameOutcome result) {
        Map<String, RegisteredPlayer> byName = new HashMap<>();
        for (RegisteredPlayer p : result.getPlayerNames().keySet()) {
            byName.put(result.getPlayerNames().get(p), p);
        }
        // Emit in the header's lobby order — the outcome's name map is a
        // HashMap and its iteration order is not stable across runs.
        List<Map<String, Object>> players = new ArrayList<>(playerNames.size());
        for (String name : playerNames) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            RegisteredPlayer registered = byName.get(name);
            PlayerOutcome po = registered == null ? null : result.getPlayerOutcome(registered);
            m.put("won", po != null && po.hasWon());
            m.put("loss", po == null || po.lossState == null ? null : po.lossState.name());
            players.add(m);
        }
        return players;
    }

    /**
     * The state snapshot of a decision: the exact state hash (the
     * {@link BaselineStateHasher} fingerprint, cross-checkable with the golden
     * traces) plus a structured summary of the same state.
     */
    private Map<String, Object> stateJson(BaselineSnapshot snapshot) {
        GameView view = snapshot.getGameView();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("hash", BaselineStateHasher.hash(snapshot));
        state.put("turn", view.getTurn());
        state.put("phase", view.getPhase() == null ? null : view.getPhase().name());
        state.put("activePlayer", view.getPlayerTurn() == null ? -1 : view.getPlayerTurn().getId());
        state.put("mulligan", view.isMulligan());
        state.put("gameOver", view.isGameOver());
        state.put("stackSize", view.getStack().size());

        List<Map<String, Object>> players = new ArrayList<>(view.getPlayers().size());
        for (PlayerView p : view.getPlayers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("life", p.getLife());
            m.put("landThisTurn", p.getNumLandThisTurn());

            Map<String, Integer> mana = new LinkedHashMap<>();
            for (byte color : ManaAtom.MANATYPES) {
                mana.put(manaColorName(color), p.getMana(color));
            }
            m.put("mana", mana);
            m.put("zones", zonesJson(p));
            players.add(m);
        }
        state.put("players", players);
        return state;
    }

    private Map<String, Object> zonesJson(PlayerView p) {
        Map<String, Object> zones = new LinkedHashMap<>();
        zones.put("hand", zoneIds(p, ZoneType.Hand));
        zones.put("battlefield", zoneBattlefield(p));
        zones.put("library", zoneIds(p, ZoneType.Library));
        zones.put("graveyard", zoneIds(p, ZoneType.Graveyard));
        zones.put("exile", zoneIds(p, ZoneType.Exile));
        zones.put("command", zoneIds(p, ZoneType.Command));
        return zones;
    }

    private List<Integer> zoneIds(PlayerView p, ZoneType zone) {
        List<CardView> cards = new ArrayList<>(p.getCards(zone));
        cards.sort(Comparator.comparingInt(CardView::getId));
        List<Integer> ids = new ArrayList<>(cards.size());
        for (CardView card : cards) {
            ids.add(card.getId());
        }
        return ids;
    }

    private List<Map<String, Object>> zoneBattlefield(PlayerView p) {
        List<CardView> cards = new ArrayList<>(p.getCards(ZoneType.Battlefield));
        cards.sort(Comparator.comparingInt(CardView::getId));
        List<Map<String, Object>> out = new ArrayList<>(cards.size());
        for (CardView card : cards) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", card.getId());
            m.put("tapped", card.isTapped());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> promptJson(BaselinePrompt prompt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", prompt.getKind().name());
        m.put("player", prompt.getPlayerId());
        m.put("message", prompt.getMessage());
        m.put("optional", prompt.isOptional());
        m.put("minCount", prompt.getMinCount());
        m.put("maxCount", prompt.getMaxCount());

        List<Map<String, Object>> cards = new ArrayList<>(prompt.getCardCandidates().size());
        for (CardView card : prompt.getCardCandidates()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", card.getId());
            cm.put("name", cardName(card));
            cm.put("zone", card.getZone() == null ? null : card.getZone().name());
            cards.add(cm);
        }
        m.put("cardCandidates", cards);

        List<Map<String, Object>> abilities = new ArrayList<>(prompt.getAbilityCandidates().size());
        for (SpellAbilityView ability : prompt.getAbilityCandidates()) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id", localId(ID_KIND_ABILITY, ability.getId()));
            CardView host = ability.getHostCard();
            am.put("host", host == null ? null : host.getId());
            am.put("hostName", host == null ? null : cardName(host));
            am.put("zone", host == null || host.getZone() == null ? null : host.getZone().name());
            am.put("description", ability.getDescription());
            abilities.add(am);
        }
        m.put("abilityCandidates", abilities);

        List<Map<String, Object>> players = new ArrayList<>(prompt.getPlayerCandidates().size());
        for (PlayerView player : prompt.getPlayerCandidates()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", player.getId());
            pm.put("name", player.getName());
            players.add(pm);
        }
        m.put("playerCandidates", players);

        List<Map<String, Object>> stackItems = new ArrayList<>(prompt.getStackItemCandidates().size());
        for (StackItemView item : prompt.getStackItemCandidates()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", localId(ID_KIND_STACK_ITEM, item.getId()));
            sm.put("key", item.getKey());
            stackItems.add(sm);
        }
        m.put("stackItemCandidates", stackItems);

        m.put("stringCandidates", new ArrayList<>(prompt.getStringCandidates()));
        m.put("numberCandidates", new ArrayList<>(prompt.getNumberCandidates()));
        m.put("colorCandidates",
                prompt.getColorCandidates() == null ? null : prompt.getColorCandidates().getSymbolsString());
        return m;
    }

    /**
     * The fed action, by variant name plus the trackable ids of its payload
     * (card ids are the stable card-data ids; spell-ability and stack-instance
     * ids are the shard's per-match re-based ids, so an action's ids always
     * resolve against the same shard's candidates).
     */
    private Map<String, Object> actionJson(BaselineAction action) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (action instanceof BaselineAction.SelectCard s) {
            m.put("type", "SelectCard");
            m.put("card", s.card() == null ? null : s.card().getId());
            List<Integer> others = new ArrayList<>();
            if (s.otherCards() != null) {
                s.otherCards().forEach(c -> others.add(c.getId()));
            }
            m.put("otherCards", others);
        } else if (action instanceof BaselineAction.SelectAbility s) {
            m.put("type", "SelectAbility");
            m.put("ability", s.ability() == null ? null : localId(ID_KIND_ABILITY, s.ability().getId()));
            m.put("host", s.ability() == null || s.ability().getHostCard() == null
                    ? null : s.ability().getHostCard().getId());
        } else if (action instanceof BaselineAction.SelectPlayer s) {
            m.put("type", "SelectPlayer");
            m.put("player", s.player() == null ? null : s.player().getId());
        } else if (action instanceof BaselineAction.SelectStackItem s) {
            m.put("type", "SelectStackItem");
            m.put("stackItem", s.item() == null ? null : localId(ID_KIND_STACK_ITEM, s.item().getId()));
        } else if (action instanceof BaselineAction.Yield y) {
            m.put("type", "Yield");
            m.put("update", y.update() == null ? null : y.update().getClass().getSimpleName());
        } else if (action instanceof BaselineAction.NextGameDecision n) {
            m.put("type", "NextGameDecision");
            m.put("decision", n.decision() == null ? null : n.decision().name());
        } else if (action instanceof BaselineAction.UseMana u) {
            m.put("type", "UseMana");
            m.put("color", u.color());
        } else if (action instanceof BaselineAction.ReorderHand r) {
            m.put("type", "ReorderHand");
            m.put("card", r.card() == null ? null : r.card().getId());
            m.put("index", r.index());
        } else if (action instanceof BaselineAction.DrawOffer d) {
            m.put("type", "DrawOffer");
            m.put("action", d.action() == null ? null : d.action().name());
        } else {
            m.put("type", action.getClass().getSimpleName());
        }
        return m;
    }

    private static final int ID_KIND_ABILITY = 1;
    private static final int ID_KIND_STACK_ITEM = 2;

    /**
     * Re-bases a JVM-global instance id (spell ability or stack instance) to a
     * per-match dense id, assigned in first-seen order. The game core assigns
     * those ids from static counters shared by every match in the process, so
     * their absolute values depend on which other matches run concurrently; the
     * re-based id is a pure function of this match's own decision stream.
     */
    private int localId(int kind, int globalId) {
        long key = (((long) kind) << 32) | (globalId & 0xffffffffL);
        Integer local = instanceIds.get(key);
        if (local == null) {
            local = instanceIds.size() + 1;
            instanceIds.put(key, local);
        }
        return local;
    }

    private Map<String, Object> baseLine(String type) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("schema", SCHEMA);
        line.put("type", type);
        line.put("match", matchIndex);
        line.put("seed", seed);
        return line;
    }

    private static String cardName(CardView card) {
        var state = card.getCurrentState();
        return state == null ? null : state.getName();
    }

    private static String manaColorName(byte color) {
        return switch (color) {
            case ManaAtom.WHITE -> "W";
            case ManaAtom.BLUE -> "U";
            case ManaAtom.BLACK -> "B";
            case ManaAtom.RED -> "R";
            case ManaAtom.GREEN -> "G";
            default -> "C";
        };
    }

    private synchronized void write(Map<String, Object> line) {
        try {
            writer.write(gson.toJson(line));
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write a shard line to " + shardFile, e);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to close the shard file " + shardFile, e);
        }
    }

    /** The shard file this writer appends to. */
    public Path getShardFile() {
        return shardFile;
    }

    @Override
    public String toString() {
        return "JsonlShardWriter[" + shardFile + "]";
    }
}
