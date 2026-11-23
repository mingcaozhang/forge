package forge.screens.home;

import forge.ai.AiProfileUtil;
import forge.deckchooser.FDeckChooser;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.*;

import org.apache.commons.lang3.StringUtils;

import forge.ImageCache;
import forge.ai.AIOption;
import forge.deck.Deck;
import forge.deck.DeckProxy;
import forge.game.GameType;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gui.UiCommand;
import forge.gui.util.SOptionPane;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.localinstance.skin.FSkinProp;
import forge.model.FModel;
import forge.screens.home.sanctioned.SleeveSelector;
import forge.toolbox.FCheckBox;
import forge.toolbox.FComboBox;
import forge.toolbox.FComboBoxWrapper;
import forge.toolbox.FLabel;
import forge.toolbox.FMouseAdapter;
import forge.toolbox.FPanel;
import forge.toolbox.FRadioButton;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.toolbox.FSkin.SkinImage;
import forge.toolbox.FTextField;
import forge.util.Localizer;
import forge.util.MyRandom;
import forge.util.NameGenerator;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class PlayerPanel extends FPanel {
    final Localizer localizer = Localizer.getInstance();
    private final static ForgePreferences prefs = FModel.getPreferences();
    private static final SkinColor unfocusedPlayerOverlay = FSkin.getColor(FSkin.Colors.CLR_OVERLAY).alphaColor(120);

    private LobbySlotType type;
    private final int index;
    private String playerName = StringUtils.EMPTY;
    private boolean mayEdit, mayControl, mayRemove;

    private final FLabel playerImageLabel = new FLabel.Builder().opaque(true).iconScaleFactor(0.99f).iconInBackground(true).build();
    private final FLabel sleeveLabel = new FLabel.Builder().opaque(true).hoverable(true).iconScaleFactor(0.99f).iconInBackground(true).build();
    private int sleeveIndex;
    private String sleeveArtKey = "";
    private int sleeveArtOffset = Deck.DEFAULT_SLEEVE_OFFSET;

    private final FTextField txtPlayerName = new FTextField.Builder().build();
    private FRadioButton radioHuman;
    private FRadioButton radioAi;
    private JPopupMenu radioAiUseSimulation;
    private FRadioButton radioOpen;
    private FCheckBox chkReady;

    // AI picker
    private String aiProfile;
    private final FLabel aiPickerLabel = new FLabel.Builder().text(localizer.getMessage("lblAiPickerPanel") + ":").build();
    private FComboBoxWrapper<Object> aiPickerComboBox = new FComboBoxWrapper<>();

    private final FLabel closeBtn;
    private final FLabel deckBtn = new FLabel.ButtonBuilder().text(localizer.getMessage("lblSelectaDeck")).build();
    private final FLabel deckLabel;

    private final String variantBtnConstraints = "height 30px, hidemode 3";

    private final FLabel cmdDeckSelectorBtn = new FLabel.ButtonBuilder().text(localizer.getMessage("lblSelectaCommanderDeck")).build();
    private final FLabel cmdLabel;

    // Picks which card of the chosen commander deck leads it in the next match
    private final FLabel cmdPickBtn = new FLabel.ButtonBuilder().text("").build();
    private final FLabel cmdPickLabel;
    private boolean hasCommanderChoices = false;

    private FCheckBox chkDevMode;

    private boolean allowNetworking;

    private FDeckChooser deckChooser;

    private final VLobby lobby;
    public PlayerPanel(final VLobby lobby, final int index, final LobbySlot slot, final boolean mayEdit, final boolean mayControl) {
        this.lobby = lobby;
        this.index = index;
        this.mayEdit = mayEdit;
        this.mayControl = mayControl;
        this.allowNetworking = lobby.getLobby().isAllowNetworking();

        this.deckLabel = lobby.newLabel(localizer.getMessage("lblDeck") + ":");
        this.cmdLabel = lobby.newLabel(localizer.getMessage("lblCommanderDeck") + ":");
        this.cmdPickLabel = lobby.newLabel(localizer.getMessage("lblCommanderPick") + ":");

        setLayout(new MigLayout("insets 10px, gap 5px"));

        // Add a button to players 3+ (or if server) to remove them from the setup
        closeBtn = createCloseButton();
        this.add(closeBtn, "w 20, h 20, pos (container.w-20) 0");

        this.add(playerImageLabel, "cell 0 0, spany 2, split 2, width 80px, height 80px");

        createSleeve();
        this.add(sleeveLabel, "width 58px, height 80px, gapleft 5px");

        createNameEditor();
        this.add(lobby.newLabel(localizer.getMessage("lblName") +":"), "w 40px, h 30px, gaptop 5px");
        this.add(txtPlayerName, "h 30px, pushx, growx");

        createPlayerTypeOptions();
        this.add(radioHuman, "gapright 5px");
        this.add(radioAi, "wrap");

        if (prefs.getPrefBoolean(FPref.UI_ENABLE_AI_PICKER)) {
            this.add(aiPickerLabel, "w 40px, h 30px");
            populateAiPickerComboBox();
            aiPickerComboBox.addTo(this, "h 30px, pushx, growx, wrap");
            aiPickerComboBox.addActionListener(aiPickerListener);
        }
        this.setAiProfile(slot.getAiProfile());

        createReadyButton();
        if (allowNetworking) {
            this.add(radioOpen, "cell 4 4, ax left, sx 2");
            this.add(chkReady, "cell 5 4, ax left, sx 2, wrap");
        }

        this.add(deckLabel, variantBtnConstraints + ", cell 0 3, sx 2, ax right");
        this.add(deckBtn, variantBtnConstraints + ", cell 2 3, pushx, growx, wmax 100%-153px, h 30px, spanx 4, wrap");

        addHandlersDeckSelector();

        this.add(cmdLabel, variantBtnConstraints + ", cell 0 2, sx 2, ax right");
        this.add(cmdDeckSelectorBtn, variantBtnConstraints + ", cell 2 2, pushx, growx, wmax 100%-153px, h 30px, spanx 4, wrap");

        // Shares row 3 with the deck button, which is always hidden while a commander deck is selected
        this.add(cmdPickLabel, variantBtnConstraints + ", cell 0 3, sx 2, ax right");
        this.add(cmdPickBtn, variantBtnConstraints + ", cell 2 3, pushx, growx, wmax 100%-153px, h 30px, spanx 4, wrap");
        cmdPickBtn.setToolTipText(localizer.getMessage("lblChooseCommanderHint"));

        addHandlersToVariantsControls();

        if (isNetworkHost()) {
            createDevModeButton();
            this.add(chkDevMode);
        }

        this.type = slot == null ? LobbySlotType.LOCAL : slot.getType();
        this.setPlayerName(slot == null ? "" : slot.getName());

        update();
    }

    boolean isNetworkHost() {
        return this.allowNetworking && this.index == 0;
    }

    void update() {
        playerImageLabel.setEnabled(mayEdit);
        final LobbySlot slot = lobby.getLobby().getSlot(index);
        refreshPlayerImageFromDeck(slot == null ? null : slot.getDeck());

        sleeveLabel.setEnabled(mayEdit);
        if (type != LobbySlotType.OPEN && sleeveArtKey != null && !sleeveArtKey.isEmpty()) {
            showCardArtOnSleeveLabel(sleeveArtKey);
        } else {
            sleeveLabel.setIcon(FSkin.getSleeves().get(type == LobbySlotType.OPEN ? -1 : sleeveIndex));
            sleeveLabel.repaintSelf();
        }

        txtPlayerName.setEnabled(mayEdit);
        txtPlayerName.setText(type == LobbySlotType.OPEN ? StringUtils.EMPTY : playerName);

        boolean enableAiPicker = mayEdit && type == LobbySlotType.AI && prefs.getPrefBoolean(FPref.UI_ENABLE_AI_PICKER);
        aiPickerLabel.setVisible(enableAiPicker);
        aiPickerComboBox.setVisible(enableAiPicker);
        aiPickerComboBox.setEnabled(enableAiPicker);

        deckLabel.setVisible(mayEdit);
        deckBtn.setVisible(mayEdit);
        chkReady.setVisible(type == LobbySlotType.LOCAL || type == LobbySlotType.REMOTE);
        chkReady.setEnabled(mayEdit);

        if (chkDevMode != null) {
            chkDevMode.setEnabled(mayEdit);
        }

        closeBtn.setVisible(mayRemove);

        if (mayRemove) {
            radioHuman.setEnabled(mayControl);
            radioAi.setEnabled(mayControl);
            radioOpen.setEnabled(mayControl);
        } else {
            radioHuman.setVisible(mayControl);
            radioAi.setVisible(mayControl);
            radioOpen.setVisible(mayControl);
        }

        radioHuman.setSelected(type == LobbySlotType.LOCAL);
        radioAi.setSelected(type == LobbySlotType.AI);
        radioOpen.setSelected(type == LobbySlotType.OPEN);

        updateVariantControlsVisibility();
    }

    private FMouseAdapter radioMouseAdapter(final FRadioButton source, final LobbySlotType type) {
        return new FMouseAdapter() {
            @Override public void onLeftClick(final MouseEvent e) {
                if (!source.isEnabled()) {
                    return;
                }
                setType(type);
                if (type != LobbySlotType.OPEN && getPlayerName().isEmpty()) {
                    final String newName = NameGenerator.getRandomName("Any", "Any", lobby.getPlayerNames());
                    setPlayerName(newName);
                }
                lobby.firePlayerChangeListener(index);
            }
        };
    }

    /**
     * Listens to name text fields and gives the appropriate player focus. Also
     * saves the name preference when leaving player one's text field.
     */
    private final FocusAdapter nameFocusListener = new FocusAdapter() {
        @Override
        public void focusGained(final FocusEvent e) {
            lobby.changePlayerFocus(index);
        }

        @Override
        public void focusLost(final FocusEvent e) {
            final Object source = e.getSource();
            if (source instanceof FTextField) { // the text box
                final FTextField nField = (FTextField)source;
                final String newName = nField.getText().trim();
                if (index == 0 && !StringUtils.isBlank(newName)
                        && StringUtils.isAlphanumericSpace(newName) && prefs.getPref(FPref.PLAYER_NAME) != newName) {
                    prefs.setPref(FPref.PLAYER_NAME, newName);
                    prefs.save();
                }
                lobby.firePlayerChangeListener(index);
            }
        }
    };

    /** Listens to sleeve buttons and gives the appropriate player focus. */
    private final FocusAdapter sleeveFocusListener = new FocusAdapter() {
        @Override
        public void focusGained(final FocusEvent e) {
            lobby.changePlayerFocus(index);
        }
    };

    /** The selected game format puts the deck's leader in the command zone. */
    private boolean formatUsesCommandZone() {
        return lobby.hasVariant(GameType.Commander);
    }

    private void updateVariantControlsVisibility() {
        // The game format decides where the deck comes from: chosen normally or chosen as a commander deck.
        final boolean isCommanderApplied = mayEdit && formatUsesCommandZone();

        // Deck building is always available for editable slots that have no commander format applied.
        final boolean isDeckBuildingAllowed = mayEdit && !isCommanderApplied;

        deckLabel.setVisible(isDeckBuildingAllowed);
        deckBtn.setVisible(isDeckBuildingAllowed);
        cmdDeckSelectorBtn.setVisible(isCommanderApplied);
        cmdLabel.setVisible(isCommanderApplied);

        // The commander can be picked for local humans and AI players alike
        final boolean isCommanderPickable = mayEdit && lobby.hasVariant(GameType.Commander)
                && (type == LobbySlotType.LOCAL || type == LobbySlotType.AI) && hasCommanderChoices;
        cmdPickBtn.setVisible(isCommanderPickable);
        cmdPickLabel.setVisible(isCommanderPickable);
    }

    @Override
    public void paintComponent(final Graphics g) {
        super.paintComponent(g);
        if (!hasFocusInLobby()) {
            FSkin.setGraphicsColor(g, unfocusedPlayerOverlay);
            g.fillRect(0, 0, this.getWidth(), this.getHeight());
        }
    }

    private boolean hasFocusInLobby() {
        return lobby.hasFocus(index);
    }

    LobbySlotType getType() {
        return type;
    }

    public boolean isAi() {
        return type == LobbySlotType.AI;
    }

    public Set<AIOption> getAiOptions() {
        if (radioAi.isSelected()) {
            for (int i = 0; i < radioAiUseSimulation.getComponentCount(); i++) {
                if (((JRadioButtonMenuItem) radioAiUseSimulation.getComponent(i)).isSelected()) {
                    if (i == 0) {
                        break;
                    }
                    if (i == 1) {
                        return Set.of(AIOption.USE_HYBRID_SIMULATION);
                    }
                    if (i == 2) {
                        return Set.of(AIOption.USE_FULL_SIMULATION);
                    }
                }
            }
        }
        return Collections.emptySet();
    }
    public void setUseAiSimulation(Set<AIOption> options) {
        ((JRadioButtonMenuItem) radioAiUseSimulation.getComponent(options.contains(AIOption.USE_FULL_SIMULATION) ? 2 : options.contains(AIOption.USE_HYBRID_SIMULATION) ? 1 : 0))
                .setSelected(true);
    }

    public void setType(final LobbySlotType type) {
        this.type = type;
        switch (type) {
        case LOCAL:
            radioHuman.setSelected(true);
            break;
        case AI:
            radioAi.setSelected(true);
            break;
        case OPEN:
            radioOpen.setSelected(true);
            break;
        case REMOTE:
            break;
        }
        update();
    }

    public void setRemote(final boolean remote) {
        if (remote) {
            setType(LobbySlotType.REMOTE);
            radioHuman.setSelected(false);
            radioAi.setSelected(false);
            radioOpen.setSelected(false);
        } else {
            setType(LobbySlotType.OPEN);
        }
    }

    public void setDeckSelectorButtonText(final String text) {
        deckBtn.setText(text);
    }

    public void setCommanderDeckSelectorButtonText(final String text) {
        cmdDeckSelectorBtn.setText(text);
    }

    /** Shows which commander leads the deck, and whether the player may pick another one. */
    public void setCommanderPick(final String text, final boolean hasChoices) {
        cmdPickBtn.setText(text);
        hasCommanderChoices = hasChoices;
        updateVariantControlsVisibility();
    }

    /**
     * Setup the AI Picker combo box with the known AI profiles.
     * Default the combo box selection to the default value of FPref.UI_CURRENT_AI_PROFILE.
     */
    private void populateAiPickerComboBox() {
        aiPickerComboBox.removeAllItems();
        final List<String> aiProfiles = AiProfileUtil.getAvailableProfiles();
        for (final String profile : aiProfiles) {
            aiPickerComboBox.addItem(profile);
        }
        aiPickerComboBox.setSelectedItem(FPref.UI_CURRENT_AI_PROFILE.getDefault());
        aiPickerComboBox.setEnabled(true);
    }

    private final ActionListener aiPickerListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            final FComboBox<Object> comboBox = (FComboBox<Object>) e.getSource();
            closeBtn.requestFocusInWindow();
            final Object selection = comboBox.getSelectedItem();

            if (selection != null) {
                setAiProfile(selection.toString());
                lobby.changePlayerFocus(index);
                lobby.firePlayerChangeListener(index);
            }
        }
    };

    private void addHandlersToVariantsControls() {
        // Commander buttons
        cmdDeckSelectorBtn.setCommand((Runnable) () -> {
            lobby.setCurrentGameMode(GameType.Commander);
            cmdDeckSelectorBtn.requestFocusInWindow();
            lobby.changePlayerFocus(index);
        });

        cmdPickBtn.setCommand((Runnable) () -> {
            lobby.changePlayerFocus(index);
            lobby.chooseCommander(index);
        });
    }

    private void createPlayerTypeOptions() {
        radioHuman = new FRadioButton(localizer.getMessage("lblHuman"));
        radioAi = new FRadioButton(localizer.getMessage("lblAI"));
        radioOpen = new FRadioButton(localizer.getMessage("lblOpen"));

        final ButtonGroup group = new ButtonGroup();
        radioAiUseSimulation = new JPopupMenu();
        JRadioButtonMenuItem item = new JRadioButtonMenuItem("Heuristics");
        item.addActionListener(e -> lobby.firePlayerChangeListener(index));
        group.add(item);
        radioAiUseSimulation.add(item);
        item = new JRadioButtonMenuItem(localizer.getMessage("lblUseSimulation") + " (Hybrid)");
        item.addActionListener(e -> lobby.firePlayerChangeListener(index));
        group.add(item);
        radioAiUseSimulation.add(item);
        item = new JRadioButtonMenuItem(localizer.getMessage("lblUseSimulation"));
        item.addActionListener(e -> lobby.firePlayerChangeListener(index));
        group.add(item);
        radioAiUseSimulation.add(item);
        radioAi.setComponentPopupMenu(radioAiUseSimulation);

        radioHuman.addMouseListener(radioMouseAdapter(radioHuman, LobbySlotType.LOCAL));
        radioAi.addMouseListener   (radioMouseAdapter(radioAi,    LobbySlotType.AI));
        radioOpen.addMouseListener (radioMouseAdapter(radioOpen,  LobbySlotType.OPEN));

        final ButtonGroup tempBtnGroup = new ButtonGroup();
        tempBtnGroup.add(radioHuman);
        tempBtnGroup.add(radioAi);
        tempBtnGroup.add(radioOpen);
    }

    private void createReadyButton() {
        chkReady = new FCheckBox(localizer.getMessage("lblReady"));
        chkReady.addActionListener(e -> lobby.setReady(index, chkReady.isSelected()));
    }

    private void createDevModeButton() {
        chkDevMode = new FCheckBox(localizer.getMessage("cbDevMode"));

        chkDevMode.addActionListener(e -> {
            final boolean toggle = chkDevMode.isSelected();
            prefs.setPref(FPref.DEV_MODE_ENABLED, String.valueOf(toggle));
            ForgePreferences.DEV_MODE = toggle;

            // ensure that preferences panel reflects the change
            prefs.save();

            lobby.setDevMode(index);
        });
    }

    private void addHandlersDeckSelector() {
        deckBtn.setCommand((Runnable) () -> {
            lobby.setCurrentGameMode(GameType.Constructed);
            deckBtn.requestFocusInWindow();
            lobby.changePlayerFocus(index, GameType.Constructed);
        });
    }

    private void createNameEditor() {
        String name;
        if (index == 0) {
            name = FModel.getPreferences().getPref(FPref.PLAYER_NAME);
            if (name.isEmpty()) {
                name = localizer.getMessage("lblHuman");
            }
        }
        else {
            name = NameGenerator.getRandomName("Any", "Any", lobby.getPlayerNames());
        }

        txtPlayerName.setText(name);
        txtPlayerName.setFocusable(true);
        txtPlayerName.setFont(FSkin.getRelativeFont(14));
        txtPlayerName.addActionListener(lobby.nameListener);
        txtPlayerName.addFocusListener(nameFocusListener);
    }

    private FLabel createCloseButton() {
        final FLabel closeBtn = new FLabel.Builder().tooltip(localizer.getMessage("lblRemove")).iconInBackground(false)
                .icon(FSkin.getIcon(FSkinProp.ICO_CLOSE)).hoverable(true).build();
        closeBtn.setCommand((Runnable) () -> {
            if (type == LobbySlotType.REMOTE && !SOptionPane.showConfirmDialog(String.format(localizer.getMessage("lblReallyKick"), playerName), localizer.getMessage("lblKick"), false)) {
                return;
            }
            lobby.removePlayer(index);
        });
        return closeBtn;
    }

    private void createSleeve() {
        final String[] currentPrefs = FModel.getPreferences().getPref(FPref.UI_SLEEVES).split(",");
        if (index < currentPrefs.length) {
            // card-art sleeve, if any, arrives via refreshSleeveFromDeck once a deck is selected
            setSleeve(Integer.parseInt(currentPrefs[index]), "", Deck.DEFAULT_SLEEVE_OFFSET);
        } else {
            setRandomSleeve(false);
        }

        sleeveLabel.setToolTipText("L-click: Select sleeve. R-click: Randomize sleeve.");
        sleeveLabel.addFocusListener(sleeveFocusListener);
        sleeveLabel.setCommand((UiCommand) () -> {
            lobby.changePlayerFocus(index);
            sleeveLabel.requestFocusInWindow();

            final SleeveSelector sSel = new SleeveSelector(playerName, sleeveIndex, sleeveArtKey, lobby.getUsedSleeves());
            sSel.setVisible(true);
            sSel.dispose();

            if (sSel.getResultArtKey() != null) {
                applyCardArtSleeve(sSel.getResultArtKey(), sSel.getResultOffset());
                persistSleeveToDeck(sSel.getResultArtKey(), sSel.getResultOffset());
            } else if (sSel.getResultIndex() >= 0) {
                setSleeveIndex(sSel.getResultIndex());
                persistSleeveToDeck("", Deck.DEFAULT_SLEEVE_OFFSET);
            }

            if (index < 2) {
                lobby.updateSleevePrefs();
            }

            lobby.firePlayerChangeListener(index);
        });
        sleeveLabel.setRightClickCommand((UiCommand) () -> {
            lobby.changePlayerFocus(index);
            sleeveLabel.requestFocusInWindow();

            setRandomSleeve();
            persistSleeveToDeck("", Deck.DEFAULT_SLEEVE_OFFSET);

            if (index < 2) {
                lobby.updateSleevePrefs();
            }
        });
    }

    private void applyCardArtSleeve(final String key, final int offset) {
        setSleeveArtKey(key);
        sleeveArtOffset = offset;
        showCardArtOnSleeveLabel(key);
    }

    /** Updates the sleeve display from a deck's stored card-art sleeve, or the built-in sleeve if it has none. */
    public void refreshSleeveFromDeck(final Deck deck) {
        final String artKey = deck == null ? "" : deck.getSleeveArtKey();
        if (artKey != null && !artKey.isEmpty()) {
            sleeveArtKey = artKey;
            sleeveArtOffset = deck.getSleeveArtOffset();
            showCardArtOnSleeveLabel(artKey);
        } else {
            sleeveArtKey = "";
            sleeveArtOffset = Deck.DEFAULT_SLEEVE_OFFSET;
            sleeveLabel.setIcon(FSkin.getSleeves().get(sleeveIndex));
            sleeveLabel.repaintSelf();
        }
    }

    /** Updates the player image from a deck's commander card, or the generic player icon if it has none. */
    public void refreshPlayerImageFromDeck(final Deck deck) {
        if (deck != null && !deck.getCommanders().isEmpty()) {
            playerImageLabel.setIcon(ImageCache.getIcon(deck.getCommanders().get(0).getImageKey(false)));
        } else {
            playerImageLabel.setIcon(FSkin.getIcon(FSkinProp.ICO_PLAYERS));
        }
        playerImageLabel.repaintSelf();
    }

    // Writes the chosen sleeve onto the currently selected deck and saves it (no-op for read-only decks)
    private void persistSleeveToDeck(final String key, final int offset) {
        final FDeckChooser chooser = getDeckChooser();
        if (chooser == null) {
            return;
        }
        final DeckProxy proxy = chooser.getLstDecks().getSelectedItem();
        if (proxy == null) {
            return;
        }
        final Deck deck = proxy.getDeck();
        if (deck == null) {
            return;
        }
        deck.setSleeveArtKey(key);
        deck.setSleeveArtOffset(offset);
        proxy.saveDeck();
        lobby.fireDeckSleeveChange(index, deck);
    }

    private void showCardArtOnSleeveLabel(final String key) {
        final BufferedImage art = ImageCache.getSleeveArtCropped(key, sleeveArtOffset);
        if (art != null) {
            sleeveLabel.setIcon(new FSkin.UnskinnedIcon(art));
            sleeveLabel.repaintSelf();
        } else {
            ImageCache.fetchSleeveArt(key, () -> showCardArtOnSleeveLabel(key));
        }
    }

    /** Applies a random sleeve, avoiding sleeve already used. */
    private void setRandomSleeve() {
        setRandomSleeve(true);
    }
    private void setRandomSleeve(final boolean fireListeners) {
        int random = 0;

        final List<Integer> usedSleeves = lobby.getUsedSleeves();
        do {
            random = MyRandom.getRandom().nextInt(FSkin.getSleeves().size());
        } while (usedSleeves.contains(random));
        setSleeveIndex(random);

        if (fireListeners) {
            lobby.firePlayerChangeListener(index);
        }
    }

    private final FSkin.LineSkinBorder focusedBorder = new FSkin.LineSkinBorder(FSkin.getColor(FSkin.Colors.CLR_BORDERS).alphaColor(255), 3);
    private final FSkin.LineSkinBorder defaultBorder = new FSkin.LineSkinBorder(FSkin.getColor(FSkin.Colors.CLR_THEME).alphaColor(200), 2);

    public void setFocused(final boolean focused) {
        playerImageLabel.setBorder(focused ? focusedBorder : defaultBorder);
    }

    String getPlayerName() {
        return txtPlayerName.getText();
    }
    public void setPlayerName(final String string) {
        playerName = string;
        txtPlayerName.setText(string);
    }

    public int getSleeveIndex() {
        return sleeveIndex;
    }
    public void setSleeveIndex(final int sleeveIndex0) {
        sleeveIndex = sleeveIndex0;
        sleeveArtKey = ""; // picking a built-in sleeve clears any card-art sleeve
        sleeveArtOffset = Deck.DEFAULT_SLEEVE_OFFSET;
        final SkinImage icon = FSkin.getSleeves().get(sleeveIndex);
        sleeveLabel.setIcon(icon);
        sleeveLabel.repaintSelf();
    }

    public void setSleeveArtKey(final String key) {
        sleeveArtKey = key == null ? "" : key;
    }

    /** Applies a sleeve from slot data: built-in index, then card-art key (with its icon) if present. */
    public void setSleeve(final int index, final String artKey, final int artOffset) {
        setSleeveIndex(index);
        if (artKey != null && !artKey.isEmpty()) {
            setSleeveArtKey(artKey);
            sleeveArtOffset = artOffset;
            showCardArtOnSleeveLabel(artKey);
        }
    }

    public boolean isReady() {
        return chkReady.isSelected();
    }
    public void setIsReady(final boolean isReady) {
        chkReady.setSelected(isReady);
    }

    public boolean isDevMode() {
        return chkDevMode != null && chkDevMode.isSelected();
    }
    public void setIsDevMode(final boolean isDevMode) {
        if (chkDevMode != null) {
            chkDevMode.setSelected(isDevMode);
        }
    }

    public void setMayEdit(final boolean mayEdit) {
        this.mayEdit = mayEdit;
    }

    public void setMayControl(final boolean mayControl) {
        this.mayControl = mayControl;
    }

    public void setMayRemove(final boolean mayRemove) {
        this.mayRemove = mayRemove;
    }

    FDeckChooser getDeckChooser() {
        return deckChooser;
    }

    void setDeckChooser(final FDeckChooser deckChooser) {
        this.deckChooser = deckChooser;
    }

    public void setAiProfile(String aiProfile) {
        this.aiProfile = aiProfile;
        if (aiProfile != null) {
            aiPickerComboBox.setSelectedItem(aiProfile);
        }
    }

    public String getAiProfile() {
        final Object selection = aiPickerComboBox.getSelectedItem();
        if (selection != null) {
            return selection.toString();
        }
        return aiProfile;
    }
}