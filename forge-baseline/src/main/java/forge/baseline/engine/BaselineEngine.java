package forge.baseline.engine;

import com.google.common.eventbus.Subscribe;
import forge.CardStorageReader;
import forge.LobbyPlayer;
import forge.StaticData;
import forge.baseline.action.BaselineAction;
import forge.card.CardType;
import forge.baseline.state.BaselineDelta;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameOutcome;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.event.GameEvent;
import forge.game.event.GameEventPlayerPriority;
import forge.game.event.GameEventTurnEnded;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.net.DeltaPacket;
import forge.gamemodes.net.server.DeltaSyncManager;
import forge.util.FileSection;
import forge.util.FileUtil;
import forge.util.MyRandom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Headless adapter over the direct {@link Match}/{@link Game} path (the
 * {@code FlatMonteCarloCommanderTest} recipe): runs a real seeded Commander game
 * on a daemon game thread and exposes it as a stream of baseline envelopes.
 *
 * <p>Usage:
 * <pre>{@code
 * ScriptedLobbyPlayer p1 = new ScriptedLobbyPlayer("P1");
 * BaselineEngine engine = new BaselineEngine(123L, List.of(deckA, deckB),
 *         List.of(p1, new LobbyPlayerAi("P2", null)));
 * engine.onEnvelope(sink);
 * engine.start();
 * engine.feed(new BaselineAction.SelectCard(cardView, null));
 * engine.feed(new BaselineAction.Concede());
 * engine.awaitCompletion(120, TimeUnit.SECONDS);
 * }</pre>
 *
 * <p>Fed actions are consumed in FIFO order at the scripted player's
 * pass-priority decisions. The initial {@link BaselineSnapshot} is emitted at
 * the start of turn 1 (after mulligans and zone setup), and {@link BaselineDelta}
 * envelopes are emitted at the same batching points the network protocol uses
 * ({@code GameEventForwarder}): right before a player-priority block and at the
 * end of a turn.
 *
 * <p>Prompt boundary (28.2): before the game thread blocks waiting for the next
 * fed action, the engine emits a {@link BaselineSnapshot} carrying the pending
 * {@link BaselinePrompt} — the prompt family, the asked player, and the
 * engine-computed legal candidates ({@link #getPromptSnapshots()}). A consumer
 * reads the prompt from the stream and answers by feeding the matching
 * {@link BaselineAction}; actions outside the legal candidates are rejected and
 * treated as a pass. The consumer's current pending prompt is the prompt of the
 * most recent snapshot that carried one; deltas carry no prompt, so a following
 * delta means the game is no longer blocked.
 *
 * <p>No UI, no {@code GuiBase}: the game is driven directly on the game thread,
 * off the latch-blocking {@code awaitInput} path. The scripted player answers
 * with fed actions; every other player — and every prompt family the scripted
 * controller does not delegate (targeting, mulligans, colors, numbers, …) — is
 * driven non-interactively by whatever {@code IGameEntitiesFactory} its
 * {@link LobbyPlayer} implements (for example {@code LobbyPlayerAi}). The
 * CMatchUI path is untouched.
 */
public final class BaselineEngine {

    private final long seed;
    private final List<Deck> decks;
    private final List<LobbyPlayer> players;

    private final Object actionLock = new Object();
    private final Deque<BaselineAction> pendingActions = new ArrayDeque<>();

    private final CountDownLatch finished = new CountDownLatch(1);
    private final CopyOnWriteArrayList<Consumer<Object>> sinks = new CopyOnWriteArrayList<>();
    private final List<BaselineDelta> deltas = new ArrayList<>();
    private final List<BaselineSnapshot> promptSnapshots = new ArrayList<>();

    private Game game;
    private Match match;
    private DeltaSyncManager sync;
    private BaselineSnapshot snapshot;
    private volatile GameOutcome outcome;
    private volatile Throwable failure;
    private volatile boolean gameOver;
    private boolean started;

    private final Collector eventCollector = new Collector();

    public BaselineEngine(long seed, List<Deck> decks, List<LobbyPlayer> players) {
        this.seed = seed;
        this.decks = List.copyOf(decks);
        this.players = List.copyOf(players);
    }

    /**
     * Builds the match and runs the whole game on a daemon thread.
     * {@link #awaitCompletion(long, TimeUnit)} returns when the game ends.
     */
    public void start() {
        if (started) {
            throw new IllegalStateException("engine already started");
        }
        started = true;

        ensureTypeLists();

        for (LobbyPlayer player : players) {
            if (player instanceof ScriptedLobbyPlayer scripted) {
                scripted.bind(this);
            }
        }

        GameRules rules = new GameRules(GameType.Commander);
        List<RegisteredPlayer> registered = new ArrayList<>(players.size());
        for (int i = 0; i < players.size(); i++) {
            registered.add(RegisteredPlayer.forCommander(decks.get(i)).setPlayer(players.get(i)));
        }
        match = new Match(rules, registered, "Baseline " + seed);
        game = match.createGame();
        game.setNoGUIUser();
        game.subscribeToEvents(eventCollector);

        Thread gameThread = new Thread(this::runGame, "forge-baseline-engine");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    /**
     * The game requires a {@link StaticData} singleton (normally created by the
     * card-database model). In a headless context there is none, so bootstrap a
     * minimal, card-free instance if needed. Cards are supplied by the decks'
     * inline script data, not by a card database.
     */
    private static void ensureStaticData() {
        if (StaticData.instance() != null) {
            return;
        }
        try {
            Path base = Files.createTempDirectory("forge-baseline-staticdata");
            Files.createDirectories(base.resolve("cards"));
            Files.createDirectories(base.resolve("custom-cards"));
            new StaticData(
                    new CardStorageReader(base.resolve("cards").toString(), null, false),
                    new CardStorageReader(base.resolve("custom-cards").toString(), null, false),
                    base.resolve("editions").toString(),
                    base.resolve("custom-editions").toString(),
                    base.resolve("blockdata").toString(),
                    "auto",
                    false,
                    false);
        } catch (IOException e) {
            throw new IllegalStateException("failed to bootstrap a minimal StaticData", e);
        }
    }

    /**
     * Loads the card-type lists (the basic/land/creature/… subtype vocabulary)
     * that {@code CardType.sanisfySubtypes} consults to validate subtypes. In
     * the real game this happens while the card-database model loads; the
     * headless engine has no such model, so without it every non-core subtype
     * (for example the "Island" on a basic land) is stripped from card types,
     * which disables the auto-generated tap-for-mana on basic lands. The list
     * file is resolved from a res directory that defaults to the sibling
     * {@code forge-gui/res} (overridable via the {@code forge.baseline.resDir}
     * system property) and is loaded at most once per JVM.
     */
    private static synchronized void ensureTypeLists() {
        if (CardType.Constant.LOADED.isSet()) {
            return;
        }
        String resDir = System.getProperty("forge.baseline.resDir", "../forge-gui/res");
        Path typeLists = Path.of(resDir, "lists", "TypeLists.txt");
        if (!Files.exists(typeLists)) {
            return;
        }
        Map<String, List<String>> sections =
                FileSection.parseSections(FileUtil.readFile(typeLists.toString()));
        for (String sectionName : sections.keySet()) {
            CardType.Helper.parseTypes(sectionName, sections.get(sectionName));
        }
        CardType.Constant.LOADED.set();
    }

    private void runGame() {
        ensureStaticData();
        MyRandom.setRandom(new Random(seed));
        try {
            match.startGame(game, this::emitInitialSnapshot);
            outcome = match.getOutcome();
        } catch (Throwable t) {
            failure = t;
        } finally {
            flushPendingDeltas();
            gameOver = true;
            finished.countDown();
            synchronized (actionLock) {
                actionLock.notifyAll();
            }
        }
    }

    /**
     * Emits the full snapshot and primes the delta sync so subsequent envelopes
     * carry only changes. Runs on the game thread at the start of turn 1, after
     * zone setup.
     */
    private void emitInitialSnapshot() {
        sync = new DeltaSyncManager();
        // Prime walk: registers every object and clears its dirty bits. The
        // full-graph packet it returns is discarded — the snapshot below carries
        // the same state.
        sync.collectDeltas(game.getView());
        snapshot = new BaselineSnapshot(sync.getCurrentSequence(), game.getView());
        emit(snapshot);
    }

    /** Enqueues an action for the scripted player's next decision. Thread-safe. */
    public void feed(BaselineAction action) {
        if (action == null) {
            throw new NullPointerException("action");
        }
        synchronized (actionLock) {
            pendingActions.add(action);
            actionLock.notifyAll();
        }
    }

    /**
     * Blocks on the game thread until the next fed action arrives.
     * Used by {@link ScriptedPlayerController}.
     */
    BaselineAction takeAction() {
        synchronized (actionLock) {
            while (pendingActions.isEmpty()) {
                if (gameOver) {
                    throw new IllegalStateException("engine finished while waiting for an action");
                }
                try {
                    actionLock.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for an action", e);
                }
            }
            return pendingActions.poll();
        }
    }

    /**
     * Emits a {@link BaselineSnapshot} carrying the pending prompt, right before
     * the scripted player blocks waiting for the next fed action. Flushes any
     * pending deltas first, so the snapshot's state and sequence number are
     * current at the decision point. Game thread only.
     */
    void emitPromptSnapshot(BaselinePrompt prompt) {
        flushPendingDeltas();
        BaselineSnapshot snapshot = new BaselineSnapshot(sync.getCurrentSequence(), game.getView(), prompt);
        promptSnapshots.add(snapshot);
        emit(snapshot);
    }

    /**
     * Collects dirty properties into a {@link BaselineDelta} envelope and emits
     * it. Walks with nothing to report are skipped (they still advance the sync
     * manager's sequence, so emitted sequence numbers may have gaps).
     */
    private void flushPendingDeltas() {
        if (sync == null) {
            return;
        }
        DeltaPacket packet = sync.collectDeltas(game.getView());
        if (!packet.getObjectDeltas().isEmpty() || !packet.getNewObjects().isEmpty()) {
            BaselineDelta delta = new BaselineDelta(
                    packet.getSequenceNumber(),
                    packet.getObjectDeltas(),
                    packet.getNewObjects(),
                    0,
                    null);
            deltas.add(delta);
            emit(delta);
        }
        eventCollector.somethingHappened = false;
    }

    /**
     * Registers a sink that receives each envelope — the snapshot first, then
     * deltas in emission order — on the game thread, at the moment of emission.
     */
    public void onEnvelope(Consumer<Object> sink) {
        sinks.add(sink);
    }

    private void emit(Object envelope) {
        for (Consumer<Object> sink : sinks) {
            sink.accept(envelope);
        }
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return finished.await(timeout, unit);
    }

    public GameOutcome getOutcome() {
        return outcome;
    }

    public Throwable getFailure() {
        return failure;
    }

    public Game getGame() {
        return game;
    }

    public Match getMatch() {
        return match;
    }

    /** The initial snapshot envelope. The captured view keeps evolving with the game. */
    public BaselineSnapshot getSnapshot() {
        return snapshot;
    }

    /** The delta envelopes in emission order. */
    public List<BaselineDelta> getDeltas() {
        return Collections.unmodifiableList(deltas);
    }

    /**
     * The snapshots carrying a pending {@link BaselinePrompt}, in emission
     * order — one per pass-priority decision of the scripted player. Each
     * snapshot's {@link BaselineSnapshot#getPrompt() prompt} is the decision
     * the engine blocked on; its {@link BaselineSnapshot#getGameView() state}
     * is the live view graph at that moment.
     */
    public List<BaselineSnapshot> getPromptSnapshots() {
        return Collections.unmodifiableList(promptSnapshots);
    }

    /**
     * Event-bus collector that mirrors the network {@code GameEventForwarder}
     * batching: flush right before a player-priority block and at the end of a
     * turn, ignoring redraw-only events.
     */
    private final class Collector {
        private boolean somethingHappened;

        @Subscribe
        public void onGameEvent(GameEvent event) {
            if (event instanceof GameEventTurnEnded && somethingHappened) {
                flushPendingDeltas();
            }
            if (!(event instanceof GameEventPlayerPriority)) {
                somethingHappened |= !event.isRedrawHint();
            } else if (somethingHappened) {
                flushPendingDeltas();
            }
        }
    }
}
