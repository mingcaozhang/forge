package forge.screens.home;

import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.*;

import javax.swing.*;

import com.google.common.collect.Lists;

import forge.deck.*;
import forge.deckchooser.FDeckChooser;
import forge.game.GameFormat;
import forge.game.GameType;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gui.CommanderChooser;
import forge.gui.FThreads;
import forge.gui.interfaces.ILobbyView;
import forge.gui.util.SOptionPane;
import forge.interfaces.IPlayerChangeListener;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.toolbox.*;
import forge.util.*;
import net.miginfocom.swing.MigLayout;

/**
 * Lobby view. View of a number of players at the deck selection stage.
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public class VLobby implements ILobbyView {

    static final int MAX_PLAYERS = 8;
    final Localizer localizer = Localizer.getInstance();
    private static final ForgePreferences prefs = FModel.getPreferences();

    // General variables
    private final GameLobby lobby;
    private CLobby controller;
    private IPlayerChangeListener playerChangeListener = null;
    private final LblHeader lblTitle = new LblHeader(localizer.getMessage("lblHeaderConstructedMode"));
    private int activePlayersNum = 0;
    private int playerWithFocus = 0; // index of the player that currently has focus

    private final StartButton btnStart  = new StartButton();
    private final JPanel pnlStart = new JPanel(new MigLayout("insets 0, gap 0, wrap 2"));
    private final JPanel constructedFrame = new JPanel(new MigLayout("insets 0, gap 0, wrap 2, hidemode 3")); // Main content frame

    // Player frame elements
    private final JPanel playersFrame = new JPanel(new MigLayout("insets 0, gap 0 5, wrap, hidemode 3"));
    private final FScrollPanel playersScroll = new FScrollPanel(new MigLayout("insets 0, gap 0, wrap, hidemode 3"), true);
    private final List<PlayerPanel> playerPanels = new ArrayList<>(MAX_PLAYERS);
    // Cache deck choosers so switching settings doesn't re-generate random decks.
    private final Map<FPref, FDeckChooser> cachedDeckChoosers = new HashMap<>();

    private final FLabel addPlayerBtn = new FLabel.ButtonBuilder().fontSize(14).text(localizer.getMessage("lblAddAPlayer")).build();

    // Deck frame elements
    private final JPanel decksFrame = new JPanel(new MigLayout("insets 0, gap 0, wrap, hidemode 3"));
    private final FCheckBox cbSingletons = new FCheckBox(localizer.getMessage("cbSingletons"));
    private final FCheckBox cbArtifacts = new FCheckBox(localizer.getMessage("cbRemoveArtifacts"));
    private final Deck[] decks = new Deck[MAX_PLAYERS];
    // Commander variant: each deck as picked in the deck chooser, who may lead it, and the
    // commander(s) the player picked instead of the default. Picks are never saved to the deck.
    private final Deck[] baseDecks = new Deck[MAX_PLAYERS];
    private final Map<Integer, List<CommanderOptions.Option>> commanderOptions = new HashMap<>();
    private final Map<Integer, List<PaperCard>> commanderPicks = new HashMap<>();

    private boolean refreshGeneratedDecks = false;

    public VLobby(final GameLobby lobby) {
        this.lobby = lobby;
        // Create controller first — VLobby.update() and render methods rely on a non-null
        // controller. External callers pick up the same instance via view.getController().
        new CLobby(this);

        lblTitle.setBackground(FSkin.getColor(FSkin.Colors.CLR_THEME2));

        playersFrame.setOpaque(false);
        playersFrame.add(playersScroll, "w 100%, h 100%-35px");

        if (lobby.hasControl()) {
            addPlayerBtn.setFocusable(true);
            addPlayerBtn.setCommand(lobby::addSlot);
            playersFrame.add(addPlayerBtn, "height 30px!, growx, pushx");
        }

        constructedFrame.add(playersFrame, "gapright 10px, w 50%-5px, growy, pushy");

        ////////////////////////////////////////////////////////
        ////////////////////// Deck Panel //////////////////////
        constructedFrame.add(decksFrame, "w 50%-5px, growy, pushy");
        constructedFrame.setOpaque(false);
        decksFrame.setOpaque(false);

        // Start Button
        if (lobby.hasControl()) {
            pnlStart.setOpaque(false);
            addConstructedStartControls();
            // Start button event handling
            btnStart.addActionListener(arg0 -> {
                if (refreshGeneratedDecks) {
                    refreshGeneratedDecks = false;
                    update(true);
                }
                Runnable startGame = lobby.startGame();
                if (startGame != null) {
                    startGame.run();
                }
            });
        }
    }

    private void addConstructedStartControls() {
        pnlStart.setLayout(new MigLayout("insets 0, gap 0, wrap 2"));
        pnlStart.add(btnStart, "align center");
    }

    public void updateDeckPanel() {
        for (final PlayerPanel playerPanel : playerPanels) {
            playerPanel.getDeckChooser().restoreSavedState();
        }
    }

    private PlayerPanel getPlayerPanel(int slot) {
        return playerPanels.get(slot);
    }

    @Override
    public void update(final int slot, final LobbySlotType type) {
        FThreads.invokeInEdtNowOrLater(() -> getDeckChooser(slot).setIsAi(type == LobbySlotType.AI));
    }

    // Lobby updates arrive on the Netty IO thread (network) and the EDT (local actions);
    // VLobby mutates non-thread-safe Swing state, so funnel every update through the EDT.
    @Override
    public void update(final boolean fullUpdate) {
        FThreads.invokeInEdtNowOrLater(() -> updateImpl(fullUpdate));
    }

    private void updateImpl(final boolean fullUpdate) {
        activePlayersNum = lobby.getNumberOfSlots();
        addPlayerBtn.setEnabled(activePlayersNum < lobby.getSlotLimit());

        for (int i = 0; i < MAX_PLAYERS; i++) {
            final boolean hasPanel = i < playerPanels.size();
            if (i < activePlayersNum) {
                // visible panels
                final LobbySlot slot = lobby.getSlot(i);
                final PlayerPanel panel;
                final boolean isNewPanel;
                if (hasPanel) {
                    panel = playerPanels.get(i);
                    isNewPanel = !panel.isVisible();
                } else {
                    panel = new PlayerPanel(this, i, slot, lobby.mayEdit(i), lobby.hasControl());
                    playerPanels.add(panel);
                    String constraints = "pushx, growx, wrap, hidemode 3";
                    if (i == 0) {
                        constraints += ", gaptop 5px";
                    }
                    playersScroll.add(panel, constraints);
                    isNewPanel = true;
                }

                final LobbySlotType type = slot.getType();
                panel.setType(type);
                panel.setPlayerName(slot.getName());
                final Deck slotDeck = slot.getDeck();
                panel.setSleeve(slot.getSleeveIndex(),
                        slotDeck == null ? "" : slotDeck.getSleeveArtKey(),
                        slotDeck == null ? Deck.DEFAULT_SLEEVE_OFFSET : slotDeck.getSleeveArtOffset());
                panel.setIsReady(slot.isReady());
                panel.setIsDevMode(slot.isDevMode());
                panel.setUseAiSimulation(slot.getAiOptions());
                panel.setMayEdit(lobby.mayEdit(i));
                panel.setMayControl(lobby.mayControl(i));
                panel.setMayRemove(lobby.mayRemove(i));
                panel.setAiProfile(slot.getAiProfile());
                panel.update();

                final boolean isSlotAI = slot.getType() == LobbySlotType.AI;
                if (isNewPanel || fullUpdate) {
                    final FDeckChooser deckChooser = createDeckChooser(i, isSlotAI);
                    deckChooser.populate();
                    panel.setDeckChooser(deckChooser);
                    if (i == 0) {
                        // TODO: This seems like the wrong place to do this:
                        slot.setIsDevMode(prefs.getPrefBoolean(FPref.DEV_MODE_ENABLED));
                    }
                    if (lobby.mayEdit(i)) {
                        changePlayerFocus(i);
                    }
                } else {
                    panel.getDeckChooser().setIsAi(isSlotAI);
                }
                if (fullUpdate && (type == LobbySlotType.LOCAL || isSlotAI)) {
                    // Deck section selection
                    panel.getDeckChooser().getLstDecks().getSelectCommand().run();
                }
                if (isNewPanel) {
                    panel.setVisible(true);
                }
            } else if (hasPanel) {
                playerPanels.get(i).setVisible(false);
            }
        }

        if (playerWithFocus >= activePlayersNum) {
            changePlayerFocus(activePlayersNum - 1);
        } else {
            updateRightPanelForMode();
        }
        refreshPanels(true, true);
    }

    public void setController(final CLobby controller) {
        this.controller = controller;
    }
    public CLobby getController() {
        return controller;
    }

    GameLobby getLobby() {
        return lobby;
    }

    public void setPlayerChangeListener(final IPlayerChangeListener listener) {
        this.playerChangeListener = listener;
    }

    void setReady(final int index, final boolean ready) {
        if (ready && decks[index] == null && !lobby.hasAutoGeneratedVariant()) {
            SOptionPane.showErrorDialog(localizer.getMessage("msgSelectAdeckBeforeReadying"));
            update(false);
            return;
        }

        if (playerChangeListener != null) {
            playerChangeListener.update(index, UpdateLobbyPlayerEvent.isReadyUpdate(ready));
        }
        changePlayerFocus(index);
    }
    void setDevMode(final int index) {
        // Push dev mode first: subsequent ready-clear broadcasts trigger view.update,
        // which re-syncs panel.isDevMode from slot.isDevMode. If the slot is stale
        // at that point the dev mode checkbox flips back visually.
        if (playerChangeListener != null) {
            playerChangeListener.update(index, UpdateLobbyPlayerEvent.devModeUpdate(getPlayerPanel(index).isDevMode()));
        }
        // clear ready for everyone
        for (int i = 0; i < activePlayersNum; i++) {
            final PlayerPanel panel = getPlayerPanel(i);
            final boolean wasReady = panel.isReady();
            panel.setIsReady(false);
            if (wasReady && playerChangeListener != null) {
                playerChangeListener.update(i, UpdateLobbyPlayerEvent.isReadyUpdate(false));
            }
        }
        changePlayerFocus(index);
    }
    void firePlayerChangeListener(final int index) {
        if (playerChangeListener != null) {
            playerChangeListener.update(index, getSlot(index));
        }
    }
    // Re-broadcasts a deck whose card-art sleeve changed, so networked opponents pick up the new sleeve
    void fireDeckSleeveChange(final int index, final Deck deck) {
        if (playerChangeListener != null && deck != null) {
            playerChangeListener.update(index, UpdateLobbyPlayerEvent.deckUpdate(deck));
        }
    }

    private void fireDeckChangeListener(final int index, final Deck deck) {
        decks[index] = deck;
        getPlayerPanel(index).refreshSleeveFromDeck(deck);
        getPlayerPanel(index).refreshPlayerImageFromDeck(deck);
        if (playerChangeListener != null) {
            playerChangeListener.update(index, UpdateLobbyPlayerEvent.deckUpdate(deck));
        }
    }
    private void fireDeckSectionChangeListener(final int index, final DeckSection section, final CardPool cards) {
        final Deck deck = decks[index];
        final Deck copy = deck == null ? new Deck() : new Deck(deck);
        copy.putSection(section, cards);
        decks[index] = copy;
        if (playerChangeListener != null) {
            playerChangeListener.update(index, UpdateLobbyPlayerEvent.deckUpdate(section, cards));
        }
    }

    void removePlayer(final int index) {
        lobby.removeSlot(index);
    }

    boolean hasVariant(final GameType variant) {
        return lobby.hasVariant(variant);
    }

    /** True when the selected game format builds the deck itself, as Momir Basic and MoJhoSto do. */
    boolean hasAutoGeneratedVariant() {
        return lobby.hasAutoGeneratedVariant();
    }

    private UpdateLobbyPlayerEvent getSlot(final int index) {
        final PlayerPanel panel = getPlayerPanel(index);
        return UpdateLobbyPlayerEvent.create(panel.getType(),
                panel.getPlayerName(),
                panel.getSleeveIndex(),
                panel.isDevMode(),
                panel.getAiOptions(),
                panel.getAiProfile());
    }

    private FDeckChooser getDeckChooser(final int iSlot) {
        return getPlayerPanel(iSlot).getDeckChooser();
    }

    private void selectMainDeck(final FDeckChooser mainChooser, final int playerIndex) {
        refreshGeneratedDecks = false;
        final DeckType type = mainChooser.getSelectedDeckType();
        final Deck deck = mainChooser.getDeck();
        // something went wrong, clear selection to prevent error loop
        if (deck == null) {
            mainChooser.getLstDecks().setSelectedIndex(0);
        }
        final Collection<DeckProxy> selectedDecks = mainChooser.getLstDecks().getSelectedItems();
        if (playerIndex < activePlayersNum && lobby.mayEdit(playerIndex)) {
            final String text = type.toString() + ": " + Lang.joinHomogenous(selectedDecks, DeckProxy::getName);
            getPlayerPanel(playerIndex).setCommanderDeckSelectorButtonText(text);
            fireDeckChangeListener(playerIndex, prepareCommanderPick(playerIndex, deck));
        }
        mainChooser.saveState();
    }

    /**
     * Records the newly selected deck and works out who may lead it. Returns the deck to use:
     * the deck itself, or a copy led by the player's earlier pick when a lobby refresh
     * reselects the same deck.
     */
    private Deck prepareCommanderPick(final int index, final Deck deck) {
        final Deck previousBase = baseDecks[index];
        final List<PaperCard> previousPick = commanderPicks.remove(index);
        baseDecks[index] = deck;
        commanderOptions.remove(index);
        if (deck == null || !hasVariant(GameType.Commander)) {
            updateCommanderPickButton(index);
            return deck;
        }
        commanderOptions.put(index, CommanderOptions.getOptions(deck, DeckFormat.Commander));

        Deck result = deck;
        if (previousPick != null && previousBase != null
                && deck.getName().equals(previousBase.getName())
                && CommanderPicks.isValidPick(deck, previousPick, DeckFormat.Commander)) {
            result = CommanderOptions.withCommanders(deck, previousPick);
            commanderPicks.put(index, previousPick);
        }
        updateCommanderPickButton(index);
        return result;
    }

    /** Opens the commander picker for a player and applies their choice to the lobby deck. */
    void chooseCommander(final int index) {
        final List<CommanderOptions.Option> options = commanderOptions.get(index);
        final Deck base = baseDecks[index];
        if (options == null || options.isEmpty() || base == null || decks[index] == null) {
            return;
        }

        final List<PaperCard> current = commanderPicks.getOrDefault(index, base.getCommanders());
        final String playerName = getPlayerPanel(index).getPlayerName();
        final CommanderOptions.Option option = CommanderChooser.choose(
                localizer.getMessage("lblChooseCommanderFor", playerName),
                localizer.getMessage("lblChooseCommanderHint"),
                options, CommanderPicks.indexOfCurrent(options, current), CommanderPicks::describe, o -> o.getCommanders().get(0));
        if (option == null) {
            return;
        }

        List<PaperCard> picked = option.getCommanders();
        if (picked.size() == 1) {
            final PaperCard commander = picked.get(0);
            final List<PaperCard> partners = CommanderOptions.getPartnerOptions(base, commander, DeckFormat.Commander);
            if (!partners.isEmpty()) {
                // Optional.empty() stands for "No partner"; null means the dialog was cancelled
                final List<Optional<PaperCard>> partnerChoices = new ArrayList<>();
                if (CommanderPicks.allowsNoPartner(base, option, DeckFormat.Commander)) {
                    partnerChoices.add(Optional.empty());
                }
                final int currentPartner = CommanderPicks.indexOfCurrentPartner(partners, commander, current);
                final int selectedPartner = currentPartner < 0 ? 0 : partnerChoices.size() + currentPartner;
                for (final PaperCard partner : partners) {
                    partnerChoices.add(Optional.of(partner));
                }
                final Optional<PaperCard> partner = CommanderChooser.choose(
                        localizer.getMessage("lblChoosePartnerFor", CardTranslation.getTranslatedName(commander.getName())),
                        localizer.getMessage("lblChooseCommanderHint"),
                        partnerChoices, selectedPartner,
                        p -> p.map(c -> CardTranslation.getTranslatedName(c.getName())).orElse(localizer.getMessage("lblNoPartner")),
                        p -> p.orElse(null));
                if (partner == null) {
                    return;
                }
                if (partner.isPresent()) {
                    picked = Arrays.asList(commander, partner.get());
                }
            }
        }

        if (CommanderPicks.isSame(picked, base.getCommanders())) {
            commanderPicks.remove(index);
        } else {
            commanderPicks.put(index, picked);
        }
        fireDeckChangeListener(index, CommanderOptions.withCommanders(decks[index], picked));
        updateCommanderPickButton(index);
    }

    private void updateCommanderPickButton(final int index) {
        if (index >= playerPanels.size()) {
            return;
        }
        final PlayerPanel panel = getPlayerPanel(index);
        final List<CommanderOptions.Option> options = commanderOptions.get(index);
        final Deck base = baseDecks[index];
        if (options == null || options.isEmpty() || base == null) {
            panel.setCommanderPick("", false);
            return;
        }
        panel.setCommanderPick(CommanderPicks.describeCurrent(base, commanderPicks.get(index)),
                CommanderPicks.hasChoices(base, options, DeckFormat.Commander));
    }

    /** Populates the deck panel with the focused player's deck choices. */
    private void populateDeckPanel(final GameType forGameType) {
        decksFrame.removeAll();

        if (!lobby.mayEdit(playerWithFocus)) {
            return;
        }

        switch (forGameType) {
        case Constructed:
            decksFrame.add(getDeckChooser(playerWithFocus), "grow, push");
            if (getDeckChooser(playerWithFocus).getSelectedDeckType().toString().contains(localizer.getMessage("lblRandom"))) {
                final String strCheckboxConstraints = "h 30px!, gap 0 20px 0 0";
                decksFrame.add(cbSingletons, strCheckboxConstraints);
                decksFrame.add(cbArtifacts, strCheckboxConstraints);
            }
            break;
        case Commander:
            decksFrame.add(getDeckChooser(playerWithFocus), "grow, push");
            break;
        default:
            break;
        }
        refreshPanels(false, true);
    }

    /** @return {@link javax.swing.JButton} */
    JButton getBtnStart() {
        return this.btnStart;
    }

    public LblHeader getLblTitle() { return lblTitle; }
    public JPanel getConstructedFrame() { return constructedFrame; }
    public JPanel getPanelStart() { return pnlStart; }
    public List<FDeckChooser> getDeckChoosers() {
        List<FDeckChooser> choosers = Lists.newArrayList();
        for (final PlayerPanel playerPanel : playerPanels) {
            choosers.add(playerPanel.getDeckChooser());
        }
        return choosers;
    }

    /** Gets the random deck checkbox for Singletons. */
    FCheckBox getCbSingletons() { return cbSingletons; }

    /** Gets the random deck checkbox for Artifacts. */
    FCheckBox getCbArtifacts() { return cbArtifacts; }

    public final List<PlayerPanel> getPlayerPanels() {
        return playerPanels;
    }
    private PlayerPanel getPlayerPanelWithFocus() {
        return getPlayerPanels().get(playerWithFocus);
    }
    boolean hasFocus(final int iPlayer) {
        return iPlayer == playerWithFocus;
    }

    void setCurrentGameMode(final GameType mode) {
        lobby.setGameType(mode);
        update(true);
    }

    private boolean isPlayerAI(final int playernum) {
        if (playernum < activePlayersNum) {
            return playerPanels.get(playernum).isAi();
        }
        return true;
    }

    /** Revalidates the player and deck sections. Necessary after adding or hiding any panels. */
    private void refreshPanels(final boolean refreshPlayerFrame, final boolean refreshDeckFrame) {
        if (refreshPlayerFrame) {
            playersScroll.validate();
            playersScroll.repaint();
        }
        if (refreshDeckFrame) {
            decksFrame.validate();
            decksFrame.repaint();
        }
    }

    public void changePlayerFocus(final int newFocusOwner) {
        changePlayerFocus(newFocusOwner, lobby.getGameType());
    }

    void changePlayerFocus(final int newFocusOwner, final GameType gType) {
        final PlayerPanel oldFocus = getPlayerPanelWithFocus();
        if (oldFocus != null) {
            oldFocus.setFocused(false);
        }
        playerWithFocus = newFocusOwner;
        final PlayerPanel newFocus = getPlayerPanelWithFocus();
        newFocus.setFocused(true);

        playersScroll.getViewport().scrollRectToVisible(newFocus.getBounds());
        updateRightPanelForMode();

        refreshPanels(true, true);
    }

    void updateRightPanelForMode() {
        decksFrame.removeAll();
        populateDeckPanel(lobby.getGameType());
        decksFrame.revalidate();
        decksFrame.repaint();
    }

    void updateActionButtons() {
        pnlStart.removeAll();
        pnlStart.setOpaque(false);
        if (lobby.hasControl()) {
            addConstructedStartControls();
        }
        // Non-host: nothing to show here — match controls are host-only.
        pnlStart.revalidate();
        pnlStart.repaint();
    }

    /** Saves sleeve prefs for players one and two. */
    void updateSleevePrefs() {
        final int pOneIndex = getPlayerPanel(0).getSleeveIndex();
        final int pTwoIndex = getPlayerPanel(1).getSleeveIndex();

        prefs.setPref(FPref.UI_SLEEVES, pOneIndex + "," + pTwoIndex);
        prefs.save();
    }

    /** Adds a pre-styled FLabel component with the specified title. */
    FLabel newLabel(final String title) {
        return new FLabel.Builder().text(title).fontSize(14).fontStyle(Font.ITALIC).build();
    }

    List<Integer> getUsedSleeves() {
        final List<Integer> usedSleeves = Lists.newArrayListWithCapacity(MAX_PLAYERS);
        for (final PlayerPanel pp : playerPanels) {
            usedSleeves.add(pp.getSleeveIndex());
        }
        return usedSleeves;
    }

    List<String> getPlayerNames() {
        final List<String> names = new ArrayList<>();
        for (final PlayerPanel pp : playerPanels) {
            names.add(pp.getPlayerName());
        }
        return names;
    }

    public void markDirty() {
        refreshGeneratedDecks = true;
    }

    /////////////////////////////////////////////

    /** The format the deck lists show. */
    private GameFormat deckListFormat() {
        return lobby.getFormat();
    }

    private FDeckChooser createDeckChooser(final int iSlot, final boolean ai) {
        return cachedDeckChoosers.computeIfAbsent(FPref.COMMANDER_DECK_STATES[iSlot], (key) -> {
            final FDeckChooser fdc = new FDeckChooser(null, ai, GameType.Commander);
            fdc.initialize(key, DeckType.COMMANDER_DECK);
            fdc.getLstDecks().setRequiredFormat(deckListFormat());
            fdc.setDeckSelectionCommand(() -> selectMainDeck(fdc, iSlot));
            return fdc;
        });
    }

    final ActionListener nameListener = e -> {
        final FTextField nField = (FTextField)e.getSource();
        nField.transferFocus();
    };

}
