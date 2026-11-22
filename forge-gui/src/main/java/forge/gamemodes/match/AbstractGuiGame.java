package forge.gamemodes.match;

import com.google.common.collect.*;

import forge.card.CardStateName;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameLog;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.spellability.StackItemView;
import forge.game.card.CardView.CardStateView;
import forge.game.event.GameEvent;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.gamemodes.net.DeltaPacket;
import forge.gamemodes.net.NetworkChecksumUtil;
import forge.gamemodes.net.TrackableSerializer;
import forge.gui.FThreads;
import forge.gui.GuiBase;
import forge.gui.control.FControlGameEventHandler;
import forge.gui.control.PlaybackSpeed;
import forge.gui.interfaces.IGuiGame;
import forge.gui.interfaces.IMayViewCards;
import forge.interfaces.IGameController;
import forge.interfaces.IMacroSystem;
import forge.player.PlayerControllerHuman;
import forge.player.PlayerZoneUpdate;
import forge.player.PlayerZoneUpdates;
import forge.trackable.Tracker;
import forge.trackable.TrackableCollection;
import forge.trackable.TrackableObject;
import forge.trackable.TrackableProperty;
import forge.trackable.TrackableTypes;
import forge.trackable.TrackableTypes.TrackableType;
import forge.util.FSerializableFunction;
import forge.util.IHasForgeLog;
import forge.util.Localizer;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;

public abstract class AbstractGuiGame implements IGuiGame, IMayViewCards, IHasForgeLog {
    private PlayerView currentPlayer = null;
    private IGameController spectator = null;
    private final Map<PlayerView, IGameController> gameControllers = Maps.newHashMap();
    private final Map<PlayerView, IGameController> originalGameControllers = Maps.newHashMap();
    private boolean gamePause = false;
    private PlaybackSpeed playbackSpeed = PlaybackSpeed.NORMAL;
    private String daytime = null;
    private boolean networkGame = false;

    private Timer waitingTimer;
    private long waitingStartTime;

    @Override
    public boolean isNetGame() {
        return networkGame;
    }
    @Override
    public void setNetGame() {
        networkGame = true;
    }

    public final boolean hasLocalPlayers() {
        return !gameControllers.isEmpty();
    }

    public final Set<PlayerView> getLocalPlayers() {
        return gameControllers.keySet();
    }

    public final int getLocalPlayerCount() {
        return gameControllers.size();
    }

    public final boolean isLocalPlayer(final PlayerView player) {
        return gameControllers.containsKey(player);
    }

    @Override
    public String getDayTime() {
        return daytime;
    }
    @Override
    public void updateDayTime(String daytime) {
        this.daytime = daytime;
    }

    public final PlayerView getCurrentPlayer() {
        return currentPlayer;
    }

    @Override
    public final void setCurrentPlayer(PlayerView player) {
        player = TrackableTypes.PlayerViewType.lookup(player); //ensure we use the correct player

        if (hasLocalPlayers() && !isLocalPlayer(player)) {
            throw new IllegalArgumentException();
        }

        currentPlayer = player;
        updateCurrentPlayer(player);
    }

    protected abstract void updateCurrentPlayer(PlayerView player);

    private GameView gameView = null;

    public final GameView getGameView() {
        return gameView;
    }

    // Network clients have no server-side match, so decks are only reachable through the lobby.
    // Null on the host and in local games, where getGameView().getDeck() works directly.
    private GameLobby clientLobby;

    public final void setClientLobby(final GameLobby lobby) {
        clientLobby = lobby;
    }

    public final Deck getDeckForPlayer(final PlayerView player) {
        if (player == null) {
            return null;
        }
        if (clientLobby != null) {
            for (int i = 0; i < clientLobby.getNumberOfSlots(); i++) {
                final LobbySlot slot = clientLobby.getSlot(i);
                if (slot != null && player.getLobbyPlayerName().equals(slot.getName())) {
                    return slot.getDeck();
                }
            }
            return null;
        }
        return gameView == null ? null : gameView.getDeck(player);
    }

    /**
     * Installs a {@link GameView} as the GUI's view of game state. A view belongs to one game, and
     * {@code openView} builds the screen from it.
     */
    @Override
    public void setGameView(final GameView gameView0) {
        if (gameView0 != null) {
            gameView0.updateObjLookup();
        }
        gameView = gameView0;
    }

    public final IGameController getGameController() {
        return getGameController(getCurrentPlayer());
    }
    public final IGameController getGameController(final PlayerView player) {
        if (player == null) {
            return spectator;
        }
        return gameControllers.get(player);
    }

    private boolean replay;

    /** True while this GUI is showing a recorded game rather than a live one. */
    public final boolean isReplay() {
        return replay;
    }
    public final void setReplay(final boolean replay0) {
        replay = replay0;
    }

    public final Collection<IGameController> getOriginalGameControllers() {
        return originalGameControllers.values();
    }

    @Override
    public void setOriginalGameController(PlayerView player, final IGameController gameController) {
        if (player == null || gameController == null) {
            throw new IllegalArgumentException();
        }

        player = TrackableTypes.PlayerViewType.lookup(player); //ensure we use the correct player

        // HashMap.put keeps the existing key on an id-equal put and PlayerView equality is by id, so without
        // removing first, re-registration across matches would retain the prior game's stale PlayerView.
        // currentPlayer is re-set for the same reason.
        final boolean doSetCurrentPlayer = originalGameControllers.isEmpty() || player.equals(getCurrentPlayer());
        originalGameControllers.remove(player);
        originalGameControllers.put(player, gameController);
        gameControllers.remove(player);
        gameControllers.put(player, gameController);
        if (doSetCurrentPlayer) {
            setCurrentPlayer(player);
        }
    }

    @Override
    public void setGameController(PlayerView player, final IGameController gameController) {
        if (player == null) {
            throw new IllegalArgumentException();
        }

        player = TrackableTypes.PlayerViewType.lookup(player); //ensure we use the correct player

        if (gameController == null) {
            if (originalGameControllers.containsKey(player)) {
                gameControllers.put(player, originalGameControllers.get(player));
            } else {
                gameControllers.remove(player);
                final PlayerView currentPlayer = getCurrentPlayer();
                if (player.equals(currentPlayer)) {
                    // set current player to a value known to be legal
                    setCurrentPlayer(Iterables.getFirst(gameControllers.keySet(), null));
                }
            }
        } else {
            gameControllers.put(player, gameController);
        }
    }

    @Override
    public void setSpectator(final IGameController spectator) {
        this.spectator = spectator;
    }

    /**
     * Discard the previous match's controller bookkeeping. The mobile port hands out a single
     * reused {@code MatchController} instance per match (see {@code GuiMobile.getNewGuiGame()}),
     * whereas desktop constructs a fresh {@code CMatchUI}; without this reset the prior match's
     * controllers and spectator leak into the next match and break {@link #setCurrentPlayer} and
     * {@link #mayView}.
     */
    public void resetForNewMatch() {
        replay = false;
        gameControllers.clear();
        originalGameControllers.clear();
        spectator = null;
        currentPlayer = null;
        selection = Selection.NONE;
    }

    @Override
    public boolean mayView(final CardView c) {
        if (!hasLocalPlayers()) {
            return true; //if not in game, card can be shown
        }
        if (GuiBase.getInterface().isLibgdxPort() && gameView != null && gameView.isGameOver()) {
            return true; //mobile: browse every zone from the minimized win/lose overlay after the match ends
        }
        if (getGameController().mayLookAtAllCards()) {
            return true;
        }
        return c.canBeShownToAny(getLocalPlayers());
    }

    @Override
    public boolean mayFlip(final CardView cv) {
        if (cv == null) {
            return false;
        }

        final CardStateView altState = cv.getAlternateState();
        if (altState == null) {
            return false;
        }

        switch (altState.getState()) {
            case Original:
                if (cv.isFaceDown()) {
                    return getCurrentPlayer() == null || cv.canFaceDownBeShownToAny(getLocalPlayers());
                }
                return true; //original can always be shown if not a face down that can't be shown
            case Flipped:
            case Meld:
            case Backside:
                return true;
            case Secondary:
                if (cv.isFaceDown()) {
                    return getCurrentPlayer() == null || cv.canFaceDownBeShownToAny(getLocalPlayers());
                }
                return false;
            case PreparedSpell:
                if (cv.isFaceDown()) {
                    return getCurrentPlayer() == null || cv.canFaceDownBeShownToAny(getLocalPlayers());
                }
                return cv.useCardArt();
            default:
                return false;
        }
    }

    private volatile Selection selection = Selection.NONE;

    @Override
    public void setSelection(final Selection next) {
        final Selection prev = selection;
        selection = next;
        // updateSingleCard reaches the widgets directly, and callers include the game thread
        FThreads.invokeInEdtNowOrLater(() -> {
            for (final TrackableObject object : Sets.symmetricDifference(new HashSet<>(prev.chosen()), new HashSet<>(next.chosen()))) {
                if (object instanceof PlayerView pv) {
                    updateLives(Collections.singleton(pv));
                } else if (object instanceof CardView cv) {
                    updateCard(cv);
                }
            }
        });
    }

    public Selection getSelection() {
        return selection;
    }

    public boolean isHighlighted(final TrackableObject object) {
        return selection.chosen().contains(object);
    }

    public boolean isSelectable(final PlayerView player) {
        return player != null && selection.targets().contains(player);
    }

    public boolean isSelectable(final StackItemView item) {
        return item != null && selection.targets().contains(item);
    }

    /** A stack entry was picked in this GUI's stack view while stack targets are being chosen. */
    public void pickStackItem(final StackItemView item) {
        final IGameController controller = getGameController();
        if (controller != null && item != null) {
            controller.selectStackItem(item);
        }
    }

    protected static PlayerZoneUpdates getZonesHolding(final Iterable<CardView> cards) {
        final PlayerZoneUpdates zones = new PlayerZoneUpdates();
        for (final CardView c : cards) {
            // an IdRef the tracker cannot resolve arrives as a null view, and PlayerZoneUpdate rejects a null player
            if (c == null || c.getOwner() == null) { continue; }
            final ZoneType zone = c.getZone();
            if (zone != null && zone != ZoneType.Battlefield) {
                zones.add(new PlayerZoneUpdate(c.getOwner(), zone));
            }
        }
        return zones;
    }

    public boolean isSelectable(final CardView card) {
        return selection.cards().contains(card);
    }

    /** Number of selectable cards currently highlighted — i.e. picked so far in the active selection prompt. */
    public int countPickedSelectables() {
        int n = 0;
        for (CardView cv : selection.cards()) {
            if (selection.chosen().contains(cv)) n++;
        }
        return n;
    }

    public boolean isSelecting() {
        return !selection.cards().isEmpty();
    }

    public int getSelectionMin() {
        return selection.min();
    }
    public int getSelectionMax() {
        return selection.max();
    }

    public boolean isActionable(final CardView card) {
        return selection.actionable().contains(card);
    }

    /** How strongly a card is marked: 1 = actionable, 2 = Auto would tap it. */
    public int getActionableStrength(final CardView card) {
        return Collections.frequency(selection.actionable(), card);
    }

    public boolean isGamePaused() {
        return gamePause;
    }
    public void setGamePause(boolean pause) {
        gamePause = pause;
    }

    public PlaybackSpeed getGameSpeed() {
        return playbackSpeed;
    }
    public void setGameSpeed(PlaybackSpeed speed) {
        playbackSpeed = speed;
    }

    public void pauseMatch() {
        IGameController controller = spectator;
        if (controller != null && !isGamePaused())
            controller.selectButtonOk();
    }

    public void resumeMatch() {
        IGameController controller = spectator;
        if (controller != null && isGamePaused())
            controller.selectButtonOk();
    }

    /**
     * Concedes the local players, or ends the game once they have all lost. Returns true only when the caller may
     * close the screen.
     */
    public boolean concede() {
        if (replay) {
            // nothing to concede in a recording: leaving is just closing it, and the caller closes the screen
            forge.gamemodes.net.client.ReplayPlayer.stopFor(this, false);
            return true;
        }
        if (gameView.isGameOver()) {
            return true;
        }
        if (hasLocalPlayers()) {
            if (!hasLocalPlayerStillPlaying()) {
                if (showConfirmDialog(Localizer.getInstance().getMessage("lblLeaveEndsGame"), Localizer.getInstance().getMessage("btnQuitMatch"), Localizer.getInstance().getMessage("btnQuitMatch"), Localizer.getInstance().getMessage("lblCancel"))) {
                    // Leaving after losing ends the game for everyone still in it
                    final Game game = gameView.getGame();
                    game.getAction().invoke(() -> {
                        game.setGameOver(GameEndReason.AllHumansLost);
                        DrawOfferCoordinator.releaseInputQueues(game);
                    });
                }
                return false;
            }
            if (gameView.isMulligan()) { //prevent UI freezing when conceding while the game is waiting for inputs/action
                showErrorDialog(Localizer.getInstance().getMessage("lblWaitingforActions"));
                return false;
            }
            if (!showConfirmDialog(Localizer.getInstance().getMessage("lblConcedeCurrentGame"), Localizer.getInstance().getMessage("lblConcedeTitle"), Localizer.getInstance().getMessage("lblConcede"), Localizer.getInstance().getMessage("lblCancel"))) {
                return false;
            }
            for (final IGameController c : getOriginalGameControllers()) {
                // Concede each player on this Gui (except mind-controlled players)
                c.concede();
            }
            // Never close here: a finished game shows its win/lose screen, and a running one stays open to watch
            return false;
        } else if (spectator == null) {
            return true; //if no local players or spectator, just quit
        } else if (showConfirmDialog(Localizer.getInstance().getMessage("lblCloseGameSpectator"), Localizer.getInstance().getMessage("lblCloseGame"), Localizer.getInstance().getMessage("lblClose"), Localizer.getInstance().getMessage("lblCancel"))) {
            IGameController controller = spectator;
            spectator = null; //ensure we don't prompt again, including when calling nextGameDecision below
            if (!isGamePaused())
                controller.selectButtonOk(); //pause
            controller.nextGameDecision(NextGameDecision.QUIT);
        }
        return false; //let logic above handle closing current screen
    }

    private boolean hasLocalPlayerStillPlaying() {
        for (final IGameController c : getOriginalGameControllers()) {
            // A network client has no Player outcome, so its players count as still in the game
            if (!(c instanceof PlayerControllerHuman pch) || pch.getPlayer().getOutcome() == null) {
                return true;
            }
        }
        return false;
    }

    public String getConcedeCaption() {
        if (hasLocalPlayers()) {
            if (!hasLocalPlayerStillPlaying()) {
                return Localizer.getInstance().getMessage("btnQuitMatch");
            }
            return Localizer.getInstance().getMessage("lblConcede");
        }
        return Localizer.getInstance().getMessage("lblStopWatching");
    }

    // Auto-yield and other input-related code

    private Timer awaitNextInputTimer;
    private TimerTask awaitNextInputTask;

    @Override
    public void awaitNextInput() {
        checkAwaitNextInputTimer();
        //delay updating prompt to await next input briefly so buttons don't flicker disabled then enabled
        awaitNextInputTask = new TimerTask() {
            @Override
            public void run() {
                FThreads.invokeInEdtLater(() -> {
                    checkAwaitNextInputTimer();
                    synchronized (awaitNextInputTimer) {
                        if (awaitNextInputTask != null) {
                            String waitingForName = updatePromptForAwait(getCurrentPlayer());
                            if (GuiBase.isNetPlay(AbstractGuiGame.this)) {
                                showWaitingTimer(getCurrentPlayer(), waitingForName);
                            }
                            awaitNextInputTask = null;
                        }
                    }
                });
            }
        };
        awaitNextInputTimer.schedule(awaitNextInputTask, 250);
    }

    private void checkAwaitNextInputTimer() {
        if (awaitNextInputTimer == null) {
            String name = "?";
            if (this.currentPlayer != null)
                name = this.currentPlayer.getLobbyPlayerName();
            awaitNextInputTimer = new Timer("awaitNextInputTimer Game:" + this.gameView.getId() + " Player:" + name, true);
        }
    }

    protected final String updatePromptForAwait(final PlayerView playerView) {
        // Append "Waiting for opponent..." below the yield prompt so the user keeps the
        // cancel-yield UI during opponent turns instead of losing it to the await prompt.
        String waitingForName = findWaitingForPlayerName(playerView);
        String waiting = waitingForName != null
                ? Localizer.getInstance().getMessage("lblWaitingForPlayer", waitingForName)
                : Localizer.getInstance().getMessage("lblWaitingForOpponent");
        String yieldMsg = currentYieldMessage();
        if (yieldMsg != null) {
            cancelAwaitNextInput();
            showPromptMessage(playerView, yieldMsg + "\n\n" + waiting);
            updateButtons(playerView, false, true, false);
        } else {
            showPromptMessage(playerView, waiting);
            updateButtons(playerView, false, false, false);
        }
        return waitingForName;
    }

    protected String currentYieldMessage() {
        YieldController yielding = null;
        for (IGameController c : gameControllers.values()) {
            YieldController yc = c.getYieldController();
            if (yc.shouldAutoYield()) {
                yielding = yc;
                break;
            }
        }
        if (yielding == null) return null;
        Localizer loc = Localizer.getInstance();
        if (yielding.getAutoPassUntilMarker() != null) {
            YieldMarker m = yielding.getAutoPassUntilMarker();
            return loc.getMessage("lblYieldingUntilPhaseFmt", m.getPhaseOwner().getName(), m.getPhase().nameForUi);
        }
        if (yielding.autoPassUntilStackEmpty()) {
            return loc.getMessage("lblYieldingUntilStackClears");
        }
        if (yielding.autoPassUntilEndOfTurn()) {
            return loc.getMessage("lblYieldingUntilEndOfTurn");
        }
        return null;
    }

    private void showWaitingTimer(final PlayerView forPlayer, final String waitingForPlayerName) {
        cancelWaitingTimer();
        if (waitingForPlayerName == null) {
            return;
        }
        this.waitingStartTime = System.currentTimeMillis();
        // Capture timer so stale EDT tick runnables detect cancel/restart and skip
        final Timer myTimer = new Timer("waitingTimer");
        waitingTimer = myTimer;
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                FThreads.invokeInEdtLater(() -> {
                    if (waitingTimer != myTimer) {
                        return; // canceled or replaced before the EDT got to us
                    }
                    updateWaitingDisplay(forPlayer, waitingForPlayerName);
                });
            }
        }, 1000, 1000);
    }

    private void updateWaitingDisplay(final PlayerView forPlayer, final String waitingForPlayerName) {
        long elapsedSec = (System.currentTimeMillis() - waitingStartTime) / 1000;
        if (elapsedSec < 2) {
            return;
        }
        String timeStr;
        if (elapsedSec < 60) {
            timeStr = elapsedSec + "s";
        } else {
            timeStr = String.format("%d:%02d", elapsedSec / 60, elapsedSec % 60);
        }
        String waiting = Localizer.getInstance().getMessage("lblWaitingForPlayer", waitingForPlayerName) + " (" + timeStr + ")";
        String yieldMsg = currentYieldMessage();
        showPromptMessageNoCancel(forPlayer, yieldMsg != null ? yieldMsg + "\n\n" + waiting : waiting);
    }

    protected void cancelWaitingTimer() {
        if (waitingTimer != null) {
            waitingTimer.cancel();
            waitingTimer = null;
        }
    }

    public void showPromptMessageNoCancel(final PlayerView playerView, final String message) {}

    private String findWaitingForPlayerName(final PlayerView forPlayer) {
        if (gameView.getPlayers() != null) {
            for (PlayerView pv : gameView.getPlayers()) {
                if (pv.getHasPriority() && (forPlayer == null || pv.getId() != forPlayer.getId())) {
                    return pv.getName();
                }
            }
        }
        // Fallback to turn player during mulligan/setup
        PlayerView turnPlayer = gameView.getPlayerTurn();
        if (turnPlayer != null && (forPlayer == null || turnPlayer.getId() != forPlayer.getId())) {
            return turnPlayer.getName();
        }
        // Fallback to any non-local player
        if (gameView.getPlayers() != null) {
            for (PlayerView pv : gameView.getPlayers()) {
                if (forPlayer != null && pv.getId() == forPlayer.getId()) {
                    continue;
                }
                if (!isLocalPlayer(pv)) {
                    return pv.getName();
                }
            }
        }
        return null;
    }

    @Override
    public final void cancelAwaitNextInput() {
        cancelWaitingTimer();
        if (awaitNextInputTimer == null) {
            return;
        }
        synchronized (awaitNextInputTimer) { //ensure task doesn't reset awaitNextInputTask during this block
            if (awaitNextInputTask != null) {
                try {
                    awaitNextInputTask.cancel(); //cancel timer once next input shown if needed
                } catch (final Exception ex) {
                } //suppress any exception thrown by cancel()
                awaitNextInputTask = null;
            }
        }
    }

    @Override
    public void updateAutoPassPrompt() {
        String message = currentYieldMessage();
        if (message == null) return;
        cancelAwaitNextInput();
        showPromptMessage(getCurrentPlayer(), message);
        updateButtons(getCurrentPlayer(), false, true, false);
        if (GuiBase.isNetPlay(this)) {
            showWaitingTimer(getCurrentPlayer(), findWaitingForPlayerName(getCurrentPlayer()));
        }
    }

    @Override
    public void applyYieldUpdate(YieldUpdate update) {
        PlayerView pv;
        if (update instanceof YieldUpdate.ClearMarker u) pv = u.player();
        else if (update instanceof YieldUpdate.StackYield u) pv = u.player();
        else if (update instanceof YieldUpdate.SetAutoPassUntilEndOfTurn u) pv = u.player();
        // SetMarker carries the phase owner, not who is yielding. It only reaches a client, for its own player
        else if (update instanceof YieldUpdate.SetMarker) pv = getCurrentPlayer();
        else return;
        IGameController c = getGameController(pv);
        if (c != null) c.applyYieldUpdate(update);
        refreshYieldUi(pv);
    }

    @Override
    public void applyMacroState(final IMacroSystem.State state) {
        final IGameController c = getGameController();
        if (c != null) c.macros().applyState(state);
    }

    /** Toggle the auto-pass marker for {@code phase} on {@code phaseOwner}'s turn. The
     *  caller's {@code markLabelStopsAtPhase} runs when setting a marker, to un-skip
     *  the cell on the platform-specific phase indicator so the marker can fire. */
    protected final void handleYieldMarkerToggle(PlayerView phaseOwner, PhaseType phase, Runnable markLabelStopsAtPhase) {
        PlayerView local = getCurrentPlayer();
        if (local == null) return;
        IGameController controller = getGameController(local);
        if (controller == null) return;
        YieldMarker existing = controller.getYieldController().getAutoPassUntilMarker();
        boolean clickedSameLabel = existing != null
                && phaseOwner.equals(existing.getPhaseOwner())
                && phase == existing.getPhase();
        if (clickedSameLabel) {
            controller.sendYieldUpdate(new YieldUpdate.ClearMarker(local));
        } else {
            // Read the phase before un-skipping: that push can pass priority and move the game on
            boolean atOrPast = YieldController.isPriorityAtOrPastMarker(getGameView(), phaseOwner, phase);
            markLabelStopsAtPhase.run();
            controller.sendYieldUpdate(new YieldUpdate.SetMarker(phaseOwner, phase, atOrPast));
        }
        refreshYieldUi(local);
    }

    protected final void pushSkipPhaseToControllers(final PlayerView player, final PhaseType phase) {
        // Mind-slave AND-combines master+controlled rows, so a master toggle invalidates dependents
        for (final PlayerView p : getGameView().getPlayers()) {
            if (!p.equals(player) && !player.equals(p.getMindSlaveMaster())) continue;
            final boolean shouldSkip = isUiSetToSkipPhase(p, phase);
            // Only the host's proxy for a remote player reads the value; a local controller answers from
            // the label. Sent to both so either re-checks the prompt it is sitting on
            for (final IGameController c : getOriginalGameControllers()) {
                c.sendYieldUpdate(new YieldUpdate.SkipPhase(p, phase, shouldSkip));
            }
        }
    }

    /**
     * Replace the host's persistent yield state for each controlled player
     * in one atomic message: auto-yields from the AutoYieldStore,
     * skip-phase prefs from PhaseLabel state. Per-key edits
     * during play flow as individual YieldUpdate deltas.
     */
    protected final void seedYieldStateOnHost() {
        Map<PlayerView, EnumSet<PhaseType>> skipPhases = new HashMap<>();
        for (PlayerView p : getGameView().getPlayers()) {
            EnumSet<PhaseType> set = EnumSet.noneOf(PhaseType.class);
            for (PhaseType ph : PhaseType.values()) {
                if (isUiSetToSkipPhase(p, ph)) {
                    set.add(ph);
                }
            }
            if (!set.isEmpty()) {
                skipPhases.put(p, set);
            }
        }
        for (final IGameController c : getOriginalGameControllers()) {
            if (c instanceof forge.gamemodes.net.client.NetGameController nc) {
                nc.seedYieldStateOnHost(skipPhases);
            }
        }
    }

    // End auto-yield/input code

    /**
     * Client-side delta application for the delta-sync protocol: replay playback
     * feeds the recorded delta packets through here.
     */
    @Override
    public void applyDelta(DeltaPacket packet) {
        if (packet == null || getGameView() == null) {
            return;
        }

        Tracker tracker = getGameView().getTracker();
        if (tracker == null) {
            netLog.error("[DeltaSync] Cannot apply delta: Tracker is null");
            return;
        }

        long startTime = System.currentTimeMillis();

        // Log with game context for easier correlation
        String activePlayerName = getGameView().getPlayerTurn() != null ? getGameView().getPlayerTurn().getName() : "?";
        String phaseName = getGameView().getPhase() != null ? getGameView().getPhase().name() : "?";
        int turn = getGameView().getTurn();

        boolean hasEvents = packet.hasEvents();

        int newObjectCount = 0;
        int appliedCount = 0;
        int skippedCount = 0;

        List<Map.Entry<TrackableObject, Map<TrackableProperty, Object>>> pendingPropertyApplications = new ArrayList<>();
        Map<Integer, CardStateView> csvRegistry = new HashMap<>();
        Map<Integer, Map<TrackableProperty, Object>> newObjects = packet.getNewObjects();
        if (!newObjects.isEmpty()) {
            // Sort by delta key so CardViews (type 0) are created before CSVs (type 4)
            List<Map.Entry<Integer, Map<TrackableProperty, Object>>> sortedNew = new ArrayList<>(newObjects.entrySet());
            sortedNew.sort(Comparator.comparingInt(Map.Entry::getKey));

            // STEP 1: Create new objects first
            for (Map.Entry<Integer, Map<TrackableProperty, Object>> newEntry : sortedNew) {
                int deltaKey = newEntry.getKey();
                int objectType = DeltaPacket.getTypeFromDeltaKey(deltaKey);
                int objectId = DeltaPacket.getIdFromDeltaKey(deltaKey);
                try {
                    if (objectType == DeltaPacket.TYPE_CSV) {
                        int cardId = getCardIdFromCsvEncodedId(objectId);
                        CardStateName state = getStateFromCsvEncodedId(objectId);
                        CardView parent = tracker.getObj(TrackableTypes.CardViewType, cardId);
                        if (parent == null) {
                            netLog.error("[DeltaSync] Parent CardView ID={} not found for CSV state={}",
                                    cardId, state);
                            continue;
                        }
                        CardStateView csv = findCsvByState(parent, state);
                        if (csv != null) {
                            csv.getProps().clear();
                        } else {
                            csv = parent.createAlternateState(state);
                        }
                        csvRegistry.put(deltaKey, csv);
                        pendingPropertyApplications.add(new AbstractMap.SimpleEntry<>(csv, newEntry.getValue()));
                        newObjectCount++;
                    } else {
                        TrackableObject created = createObjectOnly(objectType, objectId, tracker);
                        if (created != null) {
                            pendingPropertyApplications.add(new AbstractMap.SimpleEntry<>(created, newEntry.getValue()));
                            newObjectCount++;
                        }
                    }
                } catch (Exception e) {
                    netLog.error(e, "[DeltaSync] Error creating new object type={} id={}", objectType, objectId);
                }
            }

            // STEP 2: Populate new objects now that all exist for cross-references
            for (Map.Entry<TrackableObject, Map<TrackableProperty, Object>> pending : pendingPropertyApplications) {
                try {
                    applyPropertyMap(pending.getKey(), pending.getValue(), tracker, csvRegistry);
                } catch (Exception e) {
                    netLog.error("[DeltaSync] Error applying properties to object {}: {}",
                            pending.getKey().getId(), e.getMessage());
                }
            }
        }

        // STEP 3: Apply property deltas to existing objects
        for (Map.Entry<Integer, Map<TrackableProperty, Object>> entry : packet.getObjectDeltas().entrySet()) {
            int deltaKey = entry.getKey();
            Map<TrackableProperty, Object> deltaProps = entry.getValue();

            int objectType = DeltaPacket.getTypeFromDeltaKey(deltaKey);
            int actualObjectId = DeltaPacket.getIdFromDeltaKey(deltaKey);

            TrackableObject obj;
            if (objectType == DeltaPacket.TYPE_CSV) {
                obj = csvRegistry.get(deltaKey);
                if (obj == null) {
                    obj = findObjectByTypeAndId(tracker, objectType, actualObjectId);
                }
            } else {
                obj = findObjectByTypeAndId(tracker, objectType, actualObjectId);
            }

            if (obj != null) {
                try {
                    applyPropertyMap(obj, deltaProps, tracker, csvRegistry);
                    appliedCount++;
                } catch (Exception e) {
                    netLog.error("[DeltaSync] Error applying delta to object ID={} type={} (deltaKey={})",
                            actualObjectId, objectType, String.format("0x%08X", deltaKey));
                    netLog.error(e, "[DeltaSync] Exception details:");
                    skippedCount++;
                }
            } else {
                String typeName = getObjectTypeName(objectType);
                netLog.warn("[DeltaSync] {} ID={} (deltaKey={}) NOT FOUND for delta application",
                        typeName, actualObjectId, String.format("0x%08X", deltaKey));
                skippedCount++;
            }
        }

        netLog.info("[DeltaSync] Applied seq={} (Turn {}, {}, Active={}): {} new, {} deltas, {} skipped, {} events, {}ms",
                packet.getSequenceNumber(), turn, phaseName, activePlayerName, newObjectCount, appliedCount, skippedCount,
                hasEvents ? packet.getEvents().size() : 0, System.currentTimeMillis() - startTime);

        // Unwrap and dispatch events now that new objects are in the tracker.
        if (hasEvents) {
            List<GameEvent> resolvedEvents = TrackableSerializer.unwrapEvents(packet.getEvents(), tracker);
            if (!resolvedEvents.isEmpty()) {
                handleGameEvents(resolvedEvents);
            }
        }

        // TODO shouldn't be needed if hands are ordered correctly
        afterDeltaApplied();

        if (packet.hasChecksum()) {
            int serverChecksum = packet.getChecksum();
            int clientChecksum = NetworkChecksumUtil.computeSampledChecksum(getGameView(), packet.getChecksumProperties(), null);

            if (serverChecksum != clientChecksum) {
                netLog.error("[DeltaSync] CHECKSUM MISMATCH! Server={}, Client={} at seq={}",
                        serverChecksum, clientChecksum, packet.getSequenceNumber());
                if (packet.getChecksumProperties() != null) {
                    List<String> clientDivLog = new ArrayList<>();
                    NetworkChecksumUtil.computeSampledChecksum(getGameView(),
                            packet.getChecksumProperties(), clientDivLog);
                    netLog.error("[DeltaSync] Client checksum detail: {}", clientDivLog);
                }
                logChecksumDetails(getGameView(), packet);
                requestFullStateResync();
            } else {
                netLog.info("[DeltaSync] Checksum OK (seq={}, checksum={})",
                        packet.getSequenceNumber(), serverChecksum);
            }
        }

    }

    /**
     * Apply a property map to a TrackableObject.
     * Resolves network values (IDs) back to object references.
     */
    private void applyPropertyMap(TrackableObject obj, Map<TrackableProperty, Object> delta,
                                   Tracker tracker, Map<Integer, CardStateView> csvRegistry) {
        // Snapshot existing CSVs before processing — slot assignments processed in ordinal
        // order can displace a CSV from its slot before a later slot assignment looks for it.
        Map<CardStateName, CardStateView> existingCsvs = null;
        if (obj instanceof CardView cardView) {
            for (TrackableProperty p : delta.keySet()) {
                if (isCsvSlotProperty(p)) {
                    existingCsvs = snapshotExistingCsvs(cardView);
                    break;
                }
            }
        }

        for (Map.Entry<TrackableProperty, Object> entry : delta.entrySet()) {
            TrackableProperty prop = entry.getKey();
            Object value = entry.getValue();

            try {
                // CSV slot-assignment properties carry a CardStateName ordinal
                if (isCsvSlotProperty(prop) && obj instanceof CardView cardView) {
                    if (value == null) {
                        cardView.set(prop, null);
                    } else {
                        int ordinal = (Integer) value;
                        CardStateName state = CardStateName.values()[ordinal];
                        int csvKey = DeltaPacket.makeDeltaKey(DeltaPacket.TYPE_CSV, cardView.getId() * 16 + ordinal);
                        CardStateView csv = csvRegistry.get(csvKey);
                        if (csv == null) csv = findCsvByState(cardView, state);
                        if (csv == null && existingCsvs != null) csv = existingCsvs.get(state);
                        if (csv == null) csv = cardView.createAlternateState(state);
                        cardView.set(prop, csv);
                    }
                    continue;
                }

                Object resolved = resolveFromNetwork(prop, value, tracker);

                if (prop == TrackableProperty.Zone && obj instanceof CardView) {
                    netLog.debug("[DeltaSync] CardView id={} Zone: {} -> {}",
                            obj.getId(), ((CardView) obj).getZone(), resolved);
                }

                // Sync per-card Zone when zone collections change, otherwise
                // canBeShownTo() fails and cards render as hidden/empty.
                if (obj instanceof PlayerView) {
                    ZoneType changedZone = getZoneTypeForProperty(prop);
                    if (changedZone != null && resolved instanceof TrackableCollection<?> coll) {
                        for (Object item : coll) {
                            if (item instanceof CardView cv && cv.getZone() != changedZone) {
                                cv.set(TrackableProperty.Zone, changedZone);
                            }
                        }
                    }
                }

                obj.set(prop, resolved);
            } catch (Exception e) {
                netLog.error("[DeltaSync] Error setting property {} on object {}: {}",
                        prop, obj.getId(), e.getMessage());
            }
        }
    }

    /**
     * Resolve a network value back to its local form.
     * Integer IDs become object references via tracker lookup.
     */
    @SuppressWarnings("unchecked")
    static Object resolveFromNetwork(TrackableProperty prop, Object value, Tracker tracker) {
        if (value == null) return null;
        TrackableType<?> type = prop.getType();

        // Integer ID → CardView
        if (type == TrackableTypes.CardViewType) {
            return tracker.getObj(TrackableTypes.CardViewType, (Integer) value);
        }
        // Integer ID → PlayerView
        if (type == TrackableTypes.PlayerViewType) {
            return tracker.getObj(TrackableTypes.PlayerViewType, (Integer) value);
        }

        // int[]{typeMarker, id} → GameEntityView
        if (type == TrackableTypes.GameEntityViewType) {
            int[] arr = (int[]) value;
            return arr[0] == 0
                ? tracker.getObj(TrackableTypes.CardViewType, arr[1])
                : tracker.getObj(TrackableTypes.PlayerViewType, arr[1]);
        }

        // List<Integer> → TrackableCollection<CardView>
        if (type == TrackableTypes.CardViewCollectionType) {
            List<Integer> ids = (List<Integer>) value;
            TrackableCollection<CardView> coll = new TrackableCollection<>();
            for (int id : ids) {
                if (id != -1) {
                    CardView cv = tracker.getObj(TrackableTypes.CardViewType, id);
                    if (cv != null) {
                        coll.add(cv);
                    } else {
                        netLog.warn("[DeltaSync] CardView ID={} not found in tracker during collection resolve", id);
                    }
                }
            }
            return coll;
        }

        // List<Integer> → TrackableCollection<PlayerView>
        if (type == TrackableTypes.PlayerViewCollectionType) {
            List<Integer> ids = (List<Integer>) value;
            TrackableCollection<PlayerView> coll = new TrackableCollection<>();
            for (int id : ids) {
                if (id != -1) {
                    PlayerView pv = tracker.getObj(TrackableTypes.PlayerViewType, id);
                    if (pv != null) {
                        coll.add(pv);
                    } else {
                        netLog.warn("[DeltaSync] PlayerView ID={} not found in tracker during collection resolve", id);
                    }
                }
            }
            return coll;
        }

        if (type == TrackableTypes.CombatViewType) {
            if (value instanceof DeltaPacket.CombatData combatData) {
                return combatDataToCombatView(combatData, tracker);
            }
            netLog.warn("[DeltaSync] Expected CombatData for prop {}, got {}", prop, value.getClass().getSimpleName());
            return null;
        }

        if (type == TrackableTypes.StackItemViewType) {
            return tracker.getObj(TrackableTypes.StackItemViewType, (Integer) value);
        }

        if (type == TrackableTypes.StackItemViewListType) {
            List<Integer> ids = (List<Integer>) value;
            TrackableCollection<forge.game.spellability.StackItemView> coll = new TrackableCollection<>();
            for (int id : ids) {
                if (id != -1) {
                    forge.game.spellability.StackItemView siv = tracker.getObj(TrackableTypes.StackItemViewType, id);
                    if (siv != null) {
                        coll.add(siv);
                    }
                }
            }
            return coll;
        }

        return value;
    }

    /**
     * Convert a CombatData back into a CombatView by resolving IDs and calling addAttackingBand.
     */
    private static forge.game.combat.CombatView combatDataToCombatView(DeltaPacket.CombatData data, Tracker tracker) {
        forge.game.combat.CombatView combat = new forge.game.combat.CombatView(tracker);

        for (int i = 0; i < data.bandAttackerIds.size(); i++) {
            List<CardView> attackers = new ArrayList<>();
            for (int id : data.bandAttackerIds.get(i)) {
                CardView cv = tracker.getObj(TrackableTypes.CardViewType, id);
                if (cv != null) {
                    attackers.add(cv);
                } else {
                    netLog.warn("[DeltaSync] CombatView attacker CardView ID={} not found in tracker (band {})", id, i);
                }
            }

            int[] defRef = data.bandDefenderRefs.get(i);
            forge.game.GameEntityView defender = defRef[0] == 0
                    ? tracker.getObj(TrackableTypes.CardViewType, defRef[1])
                    : tracker.getObj(TrackableTypes.PlayerViewType, defRef[1]);
            if (defender == null) {
                if (defRef[1] == -1) {
                    // Rule 506.4c: when a planeswalker/battle leaves combat,
                    // the server replaces it with a fake Card(-1) placeholder
                    // that isn't in the object graph
                    defender = new CardView(defRef[1], tracker, "<Nothing>");
                } else {
                    netLog.warn("[DeltaSync] CombatView defender ID={} (type={}) not found in tracker (band {})",
                            defRef[1], defRef[0] == 0 ? "Card" : "Player", i);
                }
            }

            List<CardView> blockers = null;
            List<Integer> blockerIds = data.bandBlockerIds.get(i);
            if (blockerIds != null) {
                blockers = new ArrayList<>();
                for (int id : blockerIds) {
                    CardView cv = tracker.getObj(TrackableTypes.CardViewType, id);
                    if (cv != null) {
                        blockers.add(cv);
                    } else {
                        netLog.warn("[DeltaSync] CombatView blocker CardView ID={} not found in tracker (band {})", id, i);
                    }
                }
            }

            List<CardView> plannedBlockers = null;
            List<Integer> plannedIds = data.bandPlannedBlockerIds.get(i);
            if (plannedIds != null) {
                plannedBlockers = new ArrayList<>();
                for (int id : plannedIds) {
                    CardView cv = tracker.getObj(TrackableTypes.CardViewType, id);
                    if (cv != null) {
                        plannedBlockers.add(cv);
                    } else {
                        netLog.warn("[DeltaSync] CombatView planned blocker CardView ID={} not found in tracker (band {})", id, i);
                    }
                }
            }

            combat.addAttackingBand(attackers, defender, blockers, plannedBlockers);
        }

        return combat;
    }

    private TrackableObject createObjectOnly(int objectType, int objectId, Tracker tracker) {
        String typeName = getObjectTypeName(objectType);

        // Check if object of the SAME TYPE already exists
        TrackableObject existing = findObjectByTypeAndId(tracker, objectType, objectId);
        if (existing != null) {
            if (objectType == DeltaPacket.TYPE_CARD_VIEW) {
                // Create a NEW instance rather than clearing the existing one in-place.
                // This mirrors the game engine: when a card changes zones, a new Card
                // object is created while the old one persists for existing references
                // (e.g., StackItemView.SourceCard still shows the card's state from
                // when the ability went on the stack).
                CardView replacement = new CardView(objectId, tracker);
                tracker.putObj(TrackableTypes.CardViewType, objectId, replacement);
                return replacement;
            }
            // Non-CardView types: clear and reuse (PlayerView identity must be preserved
            // for GUI player matching; other types are rarely replaced).
            existing.getProps().clear();
            netLog.trace("[DeltaSync] {} ID={} already exists (replaced), cleared stale props",
                    typeName, objectId);
            return existing;
        }

        if (objectType == DeltaPacket.TYPE_PLAYER_VIEW) {
            netLog.warn("[DeltaSync] Creating NEW PlayerView ID={} - this may cause identity mismatch!", objectId);
        }

        TrackableObject obj = null;
        switch (objectType) {
            case DeltaPacket.TYPE_CARD_VIEW:
                obj = new CardView(objectId, tracker);
                tracker.putObj(TrackableTypes.CardViewType, objectId, (CardView) obj);
                break;
            case DeltaPacket.TYPE_PLAYER_VIEW:
                obj = new PlayerView(objectId, tracker);
                tracker.putObj(TrackableTypes.PlayerViewType, objectId, (PlayerView) obj);
                netLog.debug("[DeltaSync] Created PlayerView ID={} hash={}", objectId, System.identityHashCode(obj));
                break;
            case DeltaPacket.TYPE_STACK_ITEM_VIEW:
                obj = new forge.game.spellability.StackItemView(objectId, tracker);
                tracker.putObj(TrackableTypes.StackItemViewType, objectId, (forge.game.spellability.StackItemView) obj);
                break;
            case DeltaPacket.TYPE_GAME_VIEW:
                if (getGameView() != null) {
                    return getGameView();
                }
                break;
            default:
                netLog.error("[DeltaSync] Unknown object type: {}", objectType);
                return null;
        }

        return obj;
    }

    private TrackableObject findObjectByTypeAndId(Tracker tracker, int objectType, int objectId) {
        switch (objectType) {
            case DeltaPacket.TYPE_CARD_VIEW:
                return tracker.getObj(TrackableTypes.CardViewType, objectId);
            case DeltaPacket.TYPE_PLAYER_VIEW:
                return tracker.getObj(TrackableTypes.PlayerViewType, objectId);
            case DeltaPacket.TYPE_STACK_ITEM_VIEW:
                return tracker.getObj(TrackableTypes.StackItemViewType, objectId);
            case DeltaPacket.TYPE_GAME_VIEW:
                return getGameView();
            case DeltaPacket.TYPE_CSV:
                int cardId = getCardIdFromCsvEncodedId(objectId);
                CardStateName state = getStateFromCsvEncodedId(objectId);
                CardView parent = tracker.getObj(TrackableTypes.CardViewType, cardId);
                return parent != null ? findCsvByState(parent, state) : null;
            default:
                netLog.warn("[DeltaSync] Unknown object type {} for object {}", objectType, objectId);
                return null;
        }
    }

    private static String getObjectTypeName(int objectType) {
        switch (objectType) {
            case DeltaPacket.TYPE_CARD_VIEW: return "CardView";
            case DeltaPacket.TYPE_PLAYER_VIEW: return "PlayerView";
            case DeltaPacket.TYPE_STACK_ITEM_VIEW: return "StackItemView";
            case DeltaPacket.TYPE_GAME_VIEW: return "GameView";
            case DeltaPacket.TYPE_CSV: return "CardStateView";
            default: return "Unknown(type=" + objectType + ")";
        }
    }

    private static Map<CardStateName, CardStateView> snapshotExistingCsvs(CardView cardView) {
        Map<CardStateName, CardStateView> map = new HashMap<>();
        CardStateView csv;
        csv = cardView.getCurrentState();
        if (csv != null) map.put(csv.getState(), csv);
        csv = cardView.getAlternateState();
        if (csv != null) map.put(csv.getState(), csv);
        csv = cardView.getLeftSplitState();
        if (csv != null) map.put(csv.getState(), csv);
        csv = cardView.getRightSplitState();
        if (csv != null) map.put(csv.getState(), csv);
        return map;
    }

    private static CardStateView findCsvByState(CardView parent, CardStateName state) {
        CardStateView csv;
        csv = parent.getCurrentState();
        if (csv != null && csv.getState() == state) return csv;
        csv = parent.getAlternateState();
        if (csv != null && csv.getState() == state) return csv;
        csv = parent.getLeftSplitState();
        if (csv != null && csv.getState() == state) return csv;
        csv = parent.getRightSplitState();
        if (csv != null && csv.getState() == state) return csv;
        return null;
    }

    private static int getCardIdFromCsvEncodedId(int encodedId) {
        return encodedId / 16;
    }
    private static CardStateName getStateFromCsvEncodedId(int encodedId) {
        return CardStateName.values()[encodedId % 16];
    }

    private static boolean isCsvSlotProperty(TrackableProperty prop) {
        return prop == TrackableProperty.CurrentState
                || prop == TrackableProperty.AlternateState
                || prop == TrackableProperty.LeftSplitState
                || prop == TrackableProperty.RightSplitState;
    }

    private static ZoneType getZoneTypeForProperty(TrackableProperty prop) {
        switch (prop) {
            case Hand: return ZoneType.Hand;
            case Library: return ZoneType.Library;
            case Graveyard: return ZoneType.Graveyard;
            case Battlefield: return ZoneType.Battlefield;
            case Exile: return ZoneType.Exile;
            case Command: return ZoneType.Command;
            // Commander is a list of commander cards — they may be in any zone, not just Command
            case Commander: return null;
            // Flashback is a virtual zone — cards in it keep their real zone (Graveyard, Command, etc.)
            case Flashback: return null;
            case Ante: return ZoneType.Ante;
            case Sideboard: return ZoneType.Sideboard;
            default: return null;
        }
    }

    private void requestFullStateResync() {
        IGameController controller = getGameController();
        if (controller != null) {
            netLog.warn("[DeltaSync] Requesting full state resync from server");
            controller.requestResync();
        } else {
            netLog.error("[DeltaSync] Cannot request resync: No game controller available");
        }
    }

    protected void afterDeltaApplied() {
        // Override in GUI subclasses to refresh views after delta application.
    }

    private void logChecksumDetails(GameView gameView, DeltaPacket packet) {
        netLog.error("[DeltaSync] Checksum details (client state):");
        netLog.error("[DeltaSync]   GameView ID: {}", gameView.getId());
        netLog.error("[DeltaSync]   Turn: {}", gameView.getTurn());
        netLog.error("[DeltaSync]   Phase: {}", gameView.getPhase() != null ? gameView.getPhase().name() : "null");
        netLog.error("[DeltaSync]   Sampled properties: {}", NetworkChecksumUtil.sampledPropertyNames(packet.getChecksumProperties()));
        for (PlayerView player : NetworkChecksumUtil.getSortedPlayers(gameView)) {
            int handSize = player.getHand().size();
            int graveyardSize = player.getGraveyard().size();
            int battlefieldSize = player.getBattlefield().size();
            netLog.error("[DeltaSync]   Player {} ({}): Life={}, Hand={}, GY={}, BF={}",
                    player.getId(), player.getName(), player.getLife(),
                    handSize, graveyardSize, battlefieldSize);
        }
        netLog.error("[DeltaSync] Compare with server state in host log at seq={}", packet.getSequenceNumber());
        int phaseOrdinal = gameView.getPhase() != null ? gameView.getPhase().ordinal() : -1;
        String breakdown = NetworkChecksumUtil.computeChecksumBreakdown(gameView.getTurn(), phaseOrdinal, gameView);
        if (breakdown != null) {
            netLog.error("[DeltaSync] Client breakdown: {}", breakdown);
        }
    }

    /**
     * Convenience for getChoices(message, 0, 1, choices).
     *
     * @param <T>     is automatically inferred.
     * @param message a {@link java.lang.String} object.
     * @param choices a T object.
     * @return null if choices is missing, empty, or if the users' choices are
     * empty; otherwise, returns the first item in the List returned by
     * getChoices.
     * @see #getChoices(String, int, int, List)
     */
    @Override
    public <T> T oneOrNone(final String message, final List<T> choices) {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        final List<T> choice = getChoices(message, 0, 1, choices);
        return choice.isEmpty() ? null : choice.get(0);
    }

    @Override
    public <T> T one(final String message, final List<T> choices, FSerializableFunction<T, String> display) {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        if (choices.size() == 1) {
            return Iterables.getFirst(choices, null);
        }

        final List<T> choice = getChoices(message, 1, 1, choices, null, display);
        assert choice.size() == 1;
        return choice.get(0);
    }

    // Nothing to choose here. Code uses this to just reveal one or more items
    @Override
    public <T> void reveal(final String message, final List<T> items) {
        getChoices(message, -1, -1, items);
    }

    @Override
    public Integer getInteger(final String message, final int min, final int max, final boolean sortDesc) {
        if (max <= min) {
            return min;
        } //just return min if max <= min

        //force cutting off after 100 numbers at most
        if (max == Integer.MAX_VALUE) {
            return getInteger(message, min, max, min + 99);
        }
        final int count = max - min + 1;
        if (count > 100) {
            return getInteger(message, min, max, min + 99);
        }

        final Integer[] choices = new Integer[count];
        if (sortDesc) {
            for (int i = 0; i < count; i++) {
                choices[count - i - 1] = i + min;
            }
        } else {
            for (int i = 0; i < count; i++) {
                choices[i] = i + min;
            }
        }
        return oneOrNone(message, ImmutableList.copyOf(choices));
    }

    @Override
    public Integer getInteger(final String message, final int min, final int max, final int cutoff) {
        if (max <= min || cutoff < min) {
            return min; //just return min if max <= min or cutoff < min
        }

        if (cutoff >= max) { //fallback to regular integer prompt if cutoff at or after max
            return getInteger(message, min, max, false);
        }

        final ImmutableList.Builder<Serializable> choices = ImmutableList.builder();
        for (int i = min; i <= cutoff; i++) {
            choices.add(i);
        }
        choices.add(Localizer.getInstance().getMessage("lblOtherInteger"));

        final Object choice = oneOrNone(message, choices.build());
        if (choice instanceof Integer || choice == null) {
            return (Integer) choice;
        }

        //if Other option picked, prompt for number input
        Localizer localizer = Localizer.getInstance();
        String prompt = "";
        if (min != Integer.MIN_VALUE) {
            if (max != Integer.MAX_VALUE) {
                prompt = localizer.getMessage("lblEnterNumberBetweenMinAndMax", min, max);
            } else {
                prompt = localizer.getMessage("lblEnterNumberGreaterThanOrEqualsToMin", min);
            }
        } else if (max != Integer.MAX_VALUE) {
            prompt = localizer.getMessage("lblEnterNumberLessThanOrEqualsToMax", max);
        }

        while (true) {
            final String str = showInputDialog(prompt, message, null, "", null, true);
            if (str == null) {
                return null;
            } // that is 'cancel'

            if (StringUtils.isNumeric(str)) {
                final int val = Integer.parseInt(str);
                if (val >= min && val <= max) {
                    return val;
                }
            }
        }
    }

    @Override
    public <T> List<T> many(String title, String topCaption, int min, int max, List<T> sourceChoices, List<T> destChoices, CardView c) {
        if (max == 1) {
            return getChoices(title, min, max, sourceChoices);
        }
        final int m2 = min >= 0 ? sourceChoices.size() - min : -1;
        final int m1 = max >= 0 ? sourceChoices.size() - max : -1;
        return order(title, topCaption, m1, m2, sourceChoices, destChoices, c, false).ordered();
    }

    /**
     * Ask the user to insert an object into a list of other objects. The
     * current implementation requires the user to cancel in order to get the
     * new item to be the first item in the resulting list.
     *
     * @param title    the dialog title.
     * @param newItem  the object to insert.
     * @param oldItems the list of objects.
     * @return A shallow copy of the list of objects, with newItem inserted.
     */
    @Override
    public <T> List<T> insertInList(final String title, final T newItem, final List<T> oldItems) {
        final T placeAfter = oneOrNone(title, oldItems);
        final int indexAfter = (placeAfter == null ? 0 : oldItems.indexOf(placeAfter) + 1);
        final List<T> result = Lists.newArrayListWithCapacity(oldItems.size() + 1);
        result.addAll(oldItems);
        result.add(indexAfter, newItem);
        return result;
    }

    private FControlGameEventHandler localEventHandler;

    @Override
    public void handleGameEvent(GameEvent event) {
        if (localEventHandler == null) {
            localEventHandler = new FControlGameEventHandler(this);
        }
        localEventHandler.receiveGameEvent(event);

        // Feed forwarded events to the local GameLog so remote clients
        // build their own game log (host populates via EventBus instead).
        // gameLog is null for deserialized GameViews until openView calls ensureGameLog().
        GameView gv = getGameView();
        if (gv != null) {
            GameLog gameLog = gv.getGameLog();
            if (gameLog != null) {
                gameLog.getEventVisitor().recieve(event);
            }
        }
    }

    @Override
    public void afterGameEnd() {
        cancelWaitingTimer();
        if (awaitNextInputTimer != null) {
            awaitNextInputTimer.cancel();
            awaitNextInputTimer = null;
        }
        daytime = null;
    }

}
