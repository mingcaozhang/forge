package forge.screens.home;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.util.*;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import forge.deck.*;
import forge.deckchooser.FDeckChooser;
import forge.game.GameFormat;
import forge.game.GameType;
import forge.game.card.CardView;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gui.CardDetailPanel;
import forge.gui.CommanderChooser;
import forge.gui.FThreads;
import forge.gui.GuiChoose;
import forge.gui.SwingPrefBinders;
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
    private static final int START_ROW_LABEL_WIDTH = 150;
    private static final int START_ROW_COMBO_WIDTH = 50;
    private static final int START_ROW_GAMES_WIDTH = START_ROW_LABEL_WIDTH + START_ROW_COMBO_WIDTH;
    private static final int COMMANDER_BRACKET_SIDE_WIDTH = START_ROW_LABEL_WIDTH * 2 + START_ROW_COMBO_WIDTH;
    private static final int COMMANDER_GAMES_SIDE_WIDTH = COMMANDER_BRACKET_SIDE_WIDTH + START_ROW_GAMES_WIDTH;
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
    private final JPanel pnlStart = new JPanel(new MigLayout("insets 0, gap 0, wrap 3"));
    private final JComboBox<String> maximumCommanderBracket = new JComboBox<>(new String[]{"1", "2", "3", "4", "5"});
    private final SwingPrefBinders.ComboBox maximumCommanderBracketBinder = new SwingPrefBinders.ComboBox(FPref.DECKGEN_MAXIMUM_COMMANDER_BRACKET, maximumCommanderBracket);
    private final JPanel maximumCommanderBracketFrame = new JPanel(new MigLayout("insets 0, gap 0, wrap 2"));
    private final JComboBox<String> gamesInMatch = new JComboBox<>(new String[]{"1", "3", "5"});
    private final SwingPrefBinders.ComboBox gamesInMatchBinder = new SwingPrefBinders.ComboBox(FPref.UI_MATCHES_PER_GAME, gamesInMatch);
    private final JPanel gamesInMatchFrame = new JPanel(new MigLayout("insets 0, gap 0, wrap 2"));
    private final JPanel constructedFrame = new JPanel(new MigLayout("insets 0, gap 0, wrap 2, hidemode 3")); // Main content frame

    // Variants frame and variables
    private final FPanel variantsPanel = new FPanel(new MigLayout("insets 10, gapx 10"));
    private final List<VariantCheckBox> vntBoxes =
            FormatChoices.VARIANTS.stream().map(VariantCheckBox::new).toList();
    private final VariantCheckBox vntVanguard = Iterables.find(vntBoxes, vcb -> vcb.variant == GameType.Vanguard);

    private final FComboBoxPanel<Object> cboFormatPanel =
            new FComboBoxPanel<>(Localizer.getInstance().getMessage("lblFormat") + ":", FormatChoices.getCommanderOnly());
    private boolean suppressFormatListener = false;

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

    // Variants
    private final List<FList<Object>> schemeDeckLists = new ArrayList<>();
    private final List<FPanel> schemeDeckPanels = new ArrayList<>(MAX_PLAYERS);

    private final List<FList<Object>> planarDeckLists = new ArrayList<>();
    private final List<FPanel> planarDeckPanels = new ArrayList<>(MAX_PLAYERS);

    private final List<FList<Object>> vgdAvatarLists = new ArrayList<>();
    private final List<FPanel> vgdPanels = new ArrayList<>(MAX_PLAYERS);
    private final List<CardDetailPanel> vgdAvatarDetails = new ArrayList<>();
    private final List<PaperCard> vgdAllAvatars = new ArrayList<>();
    private final List<PaperCard> nonRandomHumanAvatars = new ArrayList<>();
    private final List<PaperCard> nonRandomAiAvatars = new ArrayList<>();
    private final Vector<Object> humanListData = new Vector<>();
    private final Vector<Object> aiListData = new Vector<>();

    private boolean refreshGeneratedDecks = false;

    public VLobby(final GameLobby lobby) {
        this.lobby = lobby;
        // Create controller first — VLobby.update() and render methods rely on a non-null
        // controller. External callers (e.g. CSubmenuOnlineLobby) pick up the same instance
        // via view.getController().
        new CLobby(this);

        lblTitle.setBackground(FSkin.getColor(FSkin.Colors.CLR_THEME2));

        ////////////////////////////////////////////////////////
        //////////////////// Game Format ///////////////////////
        cboFormatPanel.addActionListener(e -> onGameFormatChanged());
        for (final Component c : cboFormatPanel.getComponents()) {
            c.setFont(FSkin.getBoldFont(14).getBaseFont());
        }
        constructedFrame.add(cboFormatPanel, "w 100%, h 28px!, gapbottom 10px, spanx 2, wrap");

        ////////////////////////////////////////////////////////
        //////////////////// Variants Panel ////////////////////
        variantsPanel.setOpaque(false);
        variantsPanel.add(newLabel(localizer.getMessage("lblVariants")));
        for (final VariantCheckBox vcb : vntBoxes) {
            variantsPanel.add(vcb);
        }

        constructedFrame.add(new FScrollPane(variantsPanel, false, true,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED),
                "w 100%, h 45px!, gapbottom 10px, spanx 2, wrap");

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
        populateVanguardLists();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            buildDeckPanels(i);
        }
        constructedFrame.add(decksFrame, "w 50%-5px, growy, pushy");
        constructedFrame.setOpaque(false);
        decksFrame.setOpaque(false);

        // Start Button
        if (lobby.hasControl()) {
            pnlStart.setOpaque(false);
            maximumCommanderBracketFrame.add(newLabel("Maximum Bracket:"), "w " + START_ROW_LABEL_WIDTH + "px!, h 30px!");
            maximumCommanderBracketFrame.add(maximumCommanderBracket, "w " + START_ROW_COMBO_WIDTH + "px!, h 30px!");
            maximumCommanderBracketFrame.setOpaque(false);
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

        gamesInMatchFrame.add(newLabel(localizer.getMessage("lblGamesInMatch")), "w " + START_ROW_LABEL_WIDTH + "px!, h 30px!");
        gamesInMatchFrame.add(gamesInMatch, "w " + START_ROW_COMBO_WIDTH + "px!, h 30px!");
        gamesInMatchFrame.setOpaque(false);
        gamesInMatchBinder.bind();                          // loads stored pref, then listens
    }

    private void addConstructedStartControls() {
        if (lobby.getGameType() == GameType.Commander || hasVariant(GameType.Commander)) {
            maximumCommanderBracketFrame.setVisible(true);
            pnlStart.setLayout(new MigLayout("insets 0, gap 0, wrap 3"));
            pnlStart.add(maximumCommanderBracketFrame, "w " + COMMANDER_BRACKET_SIDE_WIDTH + "px!, align left");
            pnlStart.add(btnStart, "align center");
            pnlStart.add(gamesInMatchFrame, "w " + COMMANDER_GAMES_SIDE_WIDTH + "px!, align left");
        } else {
            maximumCommanderBracketFrame.setVisible(false);
            pnlStart.setLayout(new MigLayout("insets 0, gap 0, wrap 2"));
            pnlStart.add(btnStart, "align center");
            pnlStart.add(gamesInMatchFrame, "align center");
        }
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
        FThreads.invokeInEdtNowOrLater(() -> updateImpl(slot, type));
    }

    private void updateImpl(final int slot, final LobbySlotType type) {
        final FDeckChooser deckChooser = getDeckChooser(slot);
        deckChooser.setIsAi(type==LobbySlotType.AI);
        DeckType selectedDeckType = deckChooser.getSelectedDeckType();
        switch (selectedDeckType){
            case STANDARD_CARDGEN_DECK:
            case PIONEER_CARDGEN_DECK:
            case HISTORIC_CARDGEN_DECK:
            case MODERN_CARDGEN_DECK:
            case LEGACY_CARDGEN_DECK:
            case VINTAGE_CARDGEN_DECK:
            case PAUPER_CARDGEN_DECK:
            case COLOR_DECK:
            case STANDARD_COLOR_DECK:
            case MODERN_COLOR_DECK:
            case RANDOM_CARDGEN_COMMANDER_DECK:
            case RANDOM_COMMANDER_DECK:
                deckChooser.refreshDeckListForAI();
                break;
            default:
                break;
        }
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

        syncGameFormatCombo();

        for (final VariantCheckBox vcb : vntBoxes) {
            vcb.setSelected(hasVariant(vcb.variant));
            vcb.setEnabled(lobby.hasControl());
        }

        // Momir Basic and MoJhoSto write the Avatar section themselves, so Vanguard
        // cannot be layered on top. applyVariant already unticks it; say why.
        final boolean avatarSetByFormat = hasVariant(GameType.MomirBasic) || hasVariant(GameType.MoJhoSto);
        if (avatarSetByFormat) {
            vntVanguard.setEnabled(false);
            vntVanguard.setToolTipText(localizer.getMessage("ttVanguardSetByFormat"));
        } else {
            vntVanguard.setToolTipText(GameType.Vanguard.getDescription());
        }

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
                panel.setTeam(slot.getTeam());
                panel.setIsReady(slot.isReady());
                panel.setIsDevMode(slot.isDevMode());
                panel.setIsArchenemy(slot.isArchenemy());
                panel.setUseAiSimulation(slot.getAiOptions());
                panel.setMayEdit(lobby.mayEdit(i));
                panel.setMayControl(lobby.mayControl(i));
                panel.setMayRemove(lobby.mayRemove(i));
                panel.setAiProfile(slot.getAiProfile());
                panel.update();

                final boolean isSlotAI = slot.getType() == LobbySlotType.AI;
                if (isNewPanel || fullUpdate) {
                    final FDeckChooser deckChooser = createDeckChooser(lobby.getGameType(), i, isSlotAI);
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
                    selectSchemeDeck(i);
                    selectPlanarDeck(i);
                    selectVanguardAvatar(i);
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
                panel.getTeam(), panel.isArchenemy(),
                panel.isDevMode(),
                panel.getAiOptions(),
                panel.getAiProfile());
    }

    /** Builds the actual deck panel layouts for each player.
     * These are added to a list which can be referenced to populate the deck panel appropriately. */
    @SuppressWarnings("serial")
    private void buildDeckPanels(final int playerIndex) {
        // Scheme deck list
        buildDeckPanel(localizer.getMessage("lblSchemeDeck"), playerIndex, schemeDeckLists, schemeDeckPanels, e -> selectSchemeDeck(playerIndex));

        // Planar deck list
        buildDeckPanel(localizer.getMessage("lblPlanarDeck"), playerIndex, planarDeckLists, planarDeckPanels, e -> selectPlanarDeck(playerIndex));

        // Vanguard avatar list
        buildDeckPanel(localizer.getMessage("lblVanguardAvatar"), playerIndex, vgdAvatarLists, vgdPanels, e -> selectVanguardAvatar(playerIndex));
        Iterables.getLast(vgdAvatarLists).setListData(isPlayerAI(playerIndex) ? aiListData : humanListData);
        Iterables.getLast(vgdAvatarLists).setSelectedIndex(0);
        final CardDetailPanel vgdDetail = new CardDetailPanel();
        vgdAvatarDetails.add(vgdDetail);
        Iterables.getLast(vgdPanels).add(vgdDetail, "h 200px, pushx, growx, hidemode 3");
    }

    private void buildDeckPanel(final String formatName, final int playerIndex,
            final List<FList<Object>> deckLists, final List<FPanel> deckPanels,
            final ListSelectionListener selectionListener) {
        final FPanel deckPanel = new FPanel();
        deckPanel.setBorderToggle(false);
        deckPanel.setLayout(new MigLayout("insets 0, gap 0, wrap"));
        deckPanel.add(new FLabel.Builder().text("Select " + formatName)
                .fontStyle(Font.BOLD).fontSize(14).fontAlign(SwingConstants.CENTER)
                .build(), "gaptop 10px, gapbottom 5px, growx, pushx");
        final FList<Object> deckList = new FList<>();
        deckList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deckList.addListSelectionListener(selectionListener);

        final FScrollPane scrollPane = new FScrollPane(deckList, true,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        deckPanel.add(scrollPane, "grow, push");

        deckLists.add(deckList);
        deckPanels.add(deckPanel);
    }

    private FDeckChooser getDeckChooser(final int iSlot) {
        return getPlayerPanel(iSlot).getDeckChooser();
    }

    private void selectMainDeck(final FDeckChooser mainChooser, final int playerIndex, final boolean isCommanderDeck) {
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
            if (isCommanderDeck) {
                getPlayerPanel(playerIndex).setCommanderDeckSelectorButtonText(text);
            } else {
                getPlayerPanel(playerIndex).setDeckSelectorButtonText(text);
            }
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

    private void selectSchemeDeck(final int playerIndex) {
        if (playerIndex >= activePlayersNum || !(hasVariant(GameType.Archenemy) || hasVariant(GameType.ArchenemyRumble))) {
            return;
        }

        final Object selected = getSchemeDeckLists().get(playerIndex).getSelectedValue();
        final Deck deck = decks[playerIndex];
        CardPool schemePool = null;
        if (selected instanceof String) {
            String sel = (String) selected;
            if (sel.contains("Use deck's scheme section")) {
                // deck is null until the player has a deck for the current game format,
                // so a format with no decks yet falls through to Random rather than NPE.
                if (deck != null && deck.has(DeckSection.Schemes)) {
                    schemePool = deck.get(DeckSection.Schemes);
                } else {
                    sel = "Random";
                }
            }
            if (sel.equals("Random")) {
                final Deck randomDeck = RandomDeckGenerator.getRandomUserDeck(lobby, isPlayerAI(playerIndex));
                schemePool = randomDeck.get(DeckSection.Schemes);
            }
        } else if (selected instanceof Deck) {
            schemePool = ((Deck) selected).get(DeckSection.Schemes);
        }
        if (schemePool == null) { //Can be null if player deselects the list selection or chose Generate
            schemePool = DeckgenUtil.generateSchemePool();
        }
        fireDeckSectionChangeListener(playerIndex, DeckSection.Schemes, schemePool);
        getDeckChooser(playerIndex).saveState();
    }

    private void selectPlanarDeck(final int playerIndex) {
        if (playerIndex >= activePlayersNum || !hasVariant(GameType.Planechase)) {
            return;
        }

        final Object selected = getPlanarDeckLists().get(playerIndex).getSelectedValue();
        final Deck deck = decks[playerIndex];
        CardPool planePool = null;
        if (selected instanceof String) {
            String sel = (String) selected;
            if (sel.contains("Use deck's planes section")) {
                // Same null case as selectSchemeDeck: no deck yet for this game format.
                if (deck != null && deck.has(DeckSection.Planes)) {
                    planePool = deck.get(DeckSection.Planes);
                } else {
                    sel = "Random";
                }
            }
            if (sel.equals("Random")) {
                final Deck randomDeck = RandomDeckGenerator.getRandomUserDeck(lobby, isPlayerAI(playerIndex));
                planePool = randomDeck.get(DeckSection.Planes);
            }
        } else if (selected instanceof Deck) {
            planePool = ((Deck) selected).get(DeckSection.Planes);
        }
        if (planePool == null) { //Can be null if player deselects the list selection or chose Generate
            planePool = DeckgenUtil.generatePlanarPool();
        }
        fireDeckSectionChangeListener(playerIndex, DeckSection.Planes, planePool);
        getDeckChooser(playerIndex).saveState();
    }

    private void selectVanguardAvatar(final int playerIndex) {
        if (playerIndex >= activePlayersNum || !hasVariant(GameType.Vanguard)) {
            return;
        }

        final Object selected = vgdAvatarLists.get(playerIndex).getSelectedValue();
        final PlayerPanel pp = getPlayerPanel(playerIndex);
        final CardDetailPanel cdp = vgdAvatarDetails.get(playerIndex);

        PaperCard vanguardAvatar = null;
        final Deck deck = decks[playerIndex];
        if (selected instanceof PaperCard) {
            pp.setVanguardButtonText(((PaperCard) selected).getDisplayName());
            cdp.setCard(CardView.getCardForUi((PaperCard) selected));
            cdp.setVisible(true);
            refreshPanels(false, true);

            vanguardAvatar = (PaperCard)selected;
        } else {
            final String sel = (String) selected;
            pp.setVanguardButtonText(sel);
            cdp.setVisible(false);

            if (sel == null) {
                return;
            }
            if (sel.contains("Use deck's default avatar") && deck != null && deck.has(DeckSection.Avatar)) {
                vanguardAvatar = deck.get(DeckSection.Avatar).get(0);
            } else { //Only other string is "Random"
                if (isPlayerAI(playerIndex)) {
                    vanguardAvatar = Aggregates.random(getNonRandomAiAvatars());
                } else {
                    vanguardAvatar = Aggregates.random(getNonRandomHumanAvatars());
                }
            }
        }

        final CardPool avatarOnce = new CardPool();
        avatarOnce.add(vanguardAvatar);
        fireDeckSectionChangeListener(playerIndex, DeckSection.Avatar, avatarOnce);
        getDeckChooser(playerIndex).saveState();
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
        case Archenemy:
        case ArchenemyRumble:
            if (isPlayerArchenemy(playerWithFocus)) {
                decksFrame.add(schemeDeckPanels.get(playerWithFocus), "grow, push");
            } else {
                populateDeckPanel(GameType.Constructed);
            }
            break;
        case TwoHeadedGiant:
            populateDeckPanel(GameType.Constructed);
            break;
        case Commander:
        case Oathbreaker:
        case TinyLeaders:
        case Brawl:
            decksFrame.add(getDeckChooser(playerWithFocus), "grow, push");
            break;
        case Planechase:
            decksFrame.add(planarDeckPanels.get(playerWithFocus), "grow, push");
            break;
        case Vanguard:
            updateVanguardList(playerWithFocus);
            decksFrame.add(vgdPanels.get(playerWithFocus), "grow, push");
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

    void setVariantsVisible(boolean visible) {
        cboFormatPanel.setVisible(visible);
        applyDeckListFormat();
        Container scrollPane = variantsPanel.getParent();
        while (scrollPane != null && !(scrollPane instanceof JScrollPane)) {
            scrollPane = scrollPane.getParent();
        }
        if (scrollPane != null) {
            scrollPane.setVisible(visible);
        }
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

    private void onGameFormatChanged() {
        if (suppressFormatListener) { return; }

        final Object selected = cboFormatPanel.getSelectedItem();
        if (selected == null) { return; }

        if (!lobby.hasControl()) { // not ours to change; put the combo back
            syncGameFormatCombo();
            return;
        }
        if (selected == FormatChoices.ARCHIVED) {
            syncGameFormatCombo();
            final Object archived = GuiChoose.oneOrNone(Localizer.getInstance().getMessage("lblArchivedFormats"), FormatChoices.getArchivedChoices());
            if (archived != null) {
                applyFormatChoice(archived);
            }
            return;
        }
        if (selected == lobby.getFormatChoice()) { return; }

        applyFormatChoice(selected);
    }

    private void applyFormatChoice(final Object choice) {
        lobby.setFormatChoice(choice);
        lobby.saveSetup();
        update(false);
        updateActionButtons();
    }

    private void syncGameFormatCombo() {
        final Object applied = lobby.getFormatChoice();
        if (cboFormatPanel.getSelectedItem() != applied) {
            suppressFormatListener = true;
            try {
                // The model takes an archived format, which the list does not hold
                cboFormatPanel.getComboBox().getModel().setSelectedItem(applied);
            } finally {
                suppressFormatListener = false;
            }
        }
        cboFormatPanel.setEnabled(lobby.hasControl());
        applyDeckListFormat();
    }

    /** The format the deck lists show. */
    private GameFormat deckListFormat() {
        return lobby.getFormat();
    }

    private void applyDeckListFormat() {
        final GameFormat format = deckListFormat();
        for (final FDeckChooser chooser : cachedDeckChoosers.values()) {
            chooser.getLstDecks().setRequiredFormat(format);
        }
    }

    //========== Various listeners in build order

    @SuppressWarnings("serial") private class VariantCheckBox extends FCheckBox {
        private final GameType variant;
        private VariantCheckBox(final GameType variantType) {
            super(variantType.toString());
            this.variant = variantType;

            setToolTipText(variantType.getDescription());
            addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    lobby.applyVariant(variantType);
                } else {
                    lobby.removeVariant(variantType);
                }
                lobby.saveSetup();
                VLobby.this.update(false);
                VLobby.this.updateActionButtons();
            });
        }
    }

    private FDeckChooser createDeckChooser(final GameType type, final int iSlot, final boolean ai) {
        boolean forCommander;
        DeckType deckType;
        FPref prefKey;
        switch (type) {
            case Commander:
                forCommander = true;
                deckType = iSlot == 0 ? DeckType.COMMANDER_DECK : DeckType.RANDOM_CARDGEN_COMMANDER_DECK;
                prefKey = FPref.COMMANDER_DECK_STATES[iSlot];
                break;
            case TinyLeaders:
                forCommander = true;
                deckType = iSlot == 0 ? DeckType.TINY_LEADERS_DECK : DeckType.RANDOM_CARDGEN_COMMANDER_DECK;
                prefKey = FPref.TINY_LEADER_DECK_STATES[iSlot];
                break;
            case Oathbreaker:
                forCommander = true;
                deckType = iSlot == 0 ? DeckType.OATHBREAKER_DECK : DeckType.RANDOM_CARDGEN_COMMANDER_DECK;
                prefKey = FPref.OATHBREAKER_DECK_STATES[iSlot];
                break;
            case Brawl:
                forCommander = true;
                deckType = iSlot == 0 ? DeckType.BRAWL_DECK : DeckType.CUSTOM_DECK;
                prefKey = FPref.BRAWL_DECK_STATES[iSlot];
                break;
            default:
                forCommander = false;
                deckType = iSlot == 0 ? DeckType.PRECONSTRUCTED_DECK : DeckType.COLOR_DECK;
                prefKey = FPref.CONSTRUCTED_DECK_STATES[iSlot];
                break;
        }
        return cachedDeckChoosers.computeIfAbsent(prefKey, (key) -> {
            final GameType gameType = forCommander ? type : GameType.Constructed;
            final FDeckChooser fdc = new FDeckChooser(null, ai, gameType, forCommander);
            fdc.initialize(prefKey, deckType);
            fdc.getLstDecks().setRequiredFormat(deckListFormat());
            fdc.setDeckSelectionCommand(() -> selectMainDeck(fdc, iSlot, forCommander));
            return fdc;
        });
    }

    final ActionListener nameListener = e -> {
        final FTextField nField = (FTextField)e.getSource();
        nField.transferFocus();
    };

    /////////////////////////////////////
    //========== METHODS FOR VARIANTS

    /** Gets the list of planar deck lists. */
    public List<FList<Object>> getPlanarDeckLists() {
        return planarDeckLists;
    }

    /** Gets the list of scheme deck lists. */
    public List<FList<Object>> getSchemeDeckLists() {
        return schemeDeckLists;
    }

    public boolean isPlayerArchenemy(final int playernum) {
        return getPlayerPanel(playernum).isArchenemy();
    }

    /** Gets the list of Vanguard avatar lists. */
    public List<FList<Object>> getVanguardLists() {
        return vgdAvatarLists;
    }

    /** Return all the Vanguard avatars. */
    public Iterable<PaperCard> getAllAvatars() {
        if (vgdAllAvatars.isEmpty()) {
            for (final PaperCard c : FModel.getMagicDb().getVariantCards().getAllCards()) {
                if (c.getRules().getType().isVanguard()) {
                    vgdAllAvatars.add(c);
                }
            }
        }
        return vgdAllAvatars;
    }

    /** Return the Vanguard avatars not flagged RemoveDeck:Random. */
    public List<PaperCard> getNonRandomHumanAvatars() {
        return nonRandomHumanAvatars;
    }

    /** Return the Vanguard avatars not flagged RemoveDeck:All or RemoveDeck:Random. */
    public List<PaperCard> getNonRandomAiAvatars() {
        return nonRandomAiAvatars;
    }

    /** Return the gamesInMatchBinder */
    public SwingPrefBinders.ComboBox getGamesInMatchBinder() {
      return gamesInMatchBinder;
    }

    /** Return the maximumCommanderBracketBinder. */
    public SwingPrefBinders.ComboBox getMaximumCommanderBracketBinder() {
      return maximumCommanderBracketBinder;
    }

    /** Populate vanguard lists. */
    private void populateVanguardLists() {
        humanListData.add("Use deck's default avatar (random if unavailable)");
        humanListData.add("Random");
        aiListData.add("Use deck's default avatar (random if unavailable)");
        aiListData.add("Random");
        for (final PaperCard cp : getAllAvatars()) {
            humanListData.add(cp);
            if (!cp.getRules().getAiHints().getRemRandomDecks()) {
                nonRandomHumanAvatars.add(cp);
            }
            if (!cp.getRules().getAiHints().getRemAIDecks()) {
                aiListData.add(cp);
                if (!cp.getRules().getAiHints().getRemRandomDecks()) {
                    nonRandomAiAvatars.add(cp);
                }
            }
        }
    }

    /** update vanguard list. */
    public void updateVanguardList(final int playerIndex) {
        final FList<Object> vgdList = getVanguardLists().get(playerIndex);
        final Object lastSelection = vgdList.getSelectedValue();
        vgdList.setListData(isPlayerAI(playerIndex) ? aiListData : humanListData);
        if (null != lastSelection) {
            vgdList.setSelectedValue(lastSelection, true);
        }

        if (-1 == vgdList.getSelectedIndex()) {
            vgdList.setSelectedIndex(0);
        }
    }

}
