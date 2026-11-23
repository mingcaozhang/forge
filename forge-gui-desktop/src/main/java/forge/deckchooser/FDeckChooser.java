package forge.deckchooser;

import com.google.common.collect.ImmutableList;
import forge.deck.*;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gui.FThreads;
import forge.gui.UiCommand;
import forge.itemmanager.DeckManager;
import forge.itemmanager.ItemManagerConfig;
import forge.itemmanager.ItemManagerContainer;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.controllers.CDetailPicture;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FTextField;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("serial")
public class FDeckChooser extends JPanel implements IDecksComboBoxListener {
    private DecksComboBox decksComboBox;
    private DeckType selectedDeckType;
    private ItemManagerContainer lstDecksContainer;

    private boolean refreshingDeckType;

    private final DeckManager lstDecks;
    final Localizer localizer = Localizer.getInstance();

    private final FLabel btnViewDeck = new FLabel.ButtonBuilder().text(localizer.getMessage("lblViewDeck")).fontSize(14).build();
    private final FLabel btnRandom = new FLabel.ButtonBuilder().fontSize(14).build();
    private JPanel pnlDeckUrl;
    private FTextField txtDeckUrl;
    private FLabel btnReloadUrl;
    private String lastLoadedUrlDeckName;
    private UiCommand deckSelectionCommand;
    private boolean updatingDeckPool;

    private boolean isAi;

    private final ForgePreferences prefs = FModel.getPreferences();
    private FPref stateSetting = null;

    public FDeckChooser(final CDetailPicture cDetailPicture, final boolean forAi, GameType gameType) {
        lstDecks = new DeckManager(gameType, cDetailPicture);
        setOpaque(false);
        isAi = forAi;
        final UiCommand cmdViewDeck = () -> FDeckViewer.show(getDeck());
        lstDecks.setItemActivateCommand(cmdViewDeck);
        lstDecks.setSelectCommand(this::handleDeckSelection);
        btnViewDeck.setCommand(cmdViewDeck);
    }

    public void initialize(final FPref savedStateSetting, final DeckType defaultDeckType) {
        stateSetting = savedStateSetting;
        selectedDeckType = defaultDeckType;
    }

    public DeckType getSelectedDeckType() { return selectedDeckType; }
    public void setSelectedDeckType(final DeckType selectedDeckType0) {
        refreshDecksList(selectedDeckType0, false, null);
    }

    public DeckManager getLstDecks() { return lstDecks; }

    public void setDeckSelectionCommand(final UiCommand command) {
        deckSelectionCommand = command;
    }

    private void updateDecks(final Iterable<DeckProxy> decks, final ItemManagerConfig config) {
        lstDecks.setAllowMultipleSelections(false);

        setDeckPoolWithConfig(decks, config);

        btnRandom.setText(localizer.getMessage("lblRandomDeck"));
        btnRandom.setCommand((UiCommand) () -> DeckgenUtil.randomSelect(lstDecks));

        lstDecks.setSelectedIndex(0);
    }

    private void updateCustom() {
        DeckFormat deckFormat = lstDecks.getGameType().getDeckFormat();
        switch (deckFormat) {
        case Commander:
            updateDecks(DeckProxy.getAllCommanderDecks(), ItemManagerConfig.COMMANDER_DECKS);
            break;
        default:
            updateDecks(DeckProxy.getAllConstructedDecks(), ItemManagerConfig.CONSTRUCTED_DECKS);
            break;
        }
    }

    private void updateCommanderPrecons() {
        updateDecks(DeckProxy.getAllCommanderPreconDecks(), ItemManagerConfig.COMMANDER_DECKS);
    }

    private void updateProvidedDeckUrl() {
        lstDecks.setAllowMultipleSelections(false);
        setDeckPoolWithConfig(DeckUrlLoader.getUrlDecks(), ItemManagerConfig.NET_DECKS);

        btnRandom.setText(localizer.getMessage("lblRandomDeck"));
        btnRandom.setCommand((UiCommand) () -> DeckgenUtil.randomSelect(lstDecks));

        if (lastLoadedUrlDeckName != null) {
            lstDecks.setSelectedString(lastLoadedUrlDeckName);
        }
        if (lstDecks.getSelectedIndex() < 0) {
            lstDecks.setSelectedIndex(0);
        }
        syncUrlFieldWithSelectedDeck();
    }

    private void setDeckPoolWithConfig(final Iterable<DeckProxy> decks, final ItemManagerConfig config) {
        updatingDeckPool = true;
        try {
            // Clear the old source before applying a new ItemManagerConfig; otherwise stale items can
            // be sorted/rendered with columns from the next deck browser during source transitions.
            lstDecks.setPool(ImmutableList.of());
            lstDecks.setup(config);
            lstDecks.setPool(decks);
        } finally {
            updatingDeckPool = false;
        }
    }

    public Deck getDeck() {
        final DeckProxy proxy = lstDecks.getSelectedItem();
        if (proxy == null) {
            return null;
        }
        return proxy.getDeck();
    }

    /** Generates deck from current list selection(s). */
    public RegisteredPlayer getPlayer() {
        if (lstDecks.getSelectedIndex() < 0) { return null; }

        return new RegisteredPlayer(getDeck());
    }

    public void populate() {
        if (decksComboBox == null) { //initialize components with delayed initialization the first time this is populated
            decksComboBox = new DecksComboBox();
            lstDecksContainer = new ItemManagerContainer(lstDecks);
            initializeDeckUrlPanel();
            decksComboBox.addListener(this);
            restoreSavedState();
        } else {
            removeAll();
        }
        this.setLayout(new MigLayout("insets 0, gap 0, hidemode 3"));
        decksComboBox.addTo(this, "w 100%, h 30px!, gapbottom 5px, spanx 2, wrap");
        this.add(pnlDeckUrl, "w 100%, h 30px!, gapbottom 5px, spanx 2, wrap");
        this.add(lstDecksContainer, "w 100%, growy, pushy, spanx 2, wrap");
        this.add(btnViewDeck, "w 50%-3px, h 30px!, gaptop 5px, gapright 6px");
        this.add(btnRandom, "w 50%-3px, h 30px!, gaptop 5px");
        updateDeckUrlPanelVisibility();
        if (isShowing()) {
            revalidate();
            repaint();
        }
    }

    private void initializeDeckUrlPanel() {
        pnlDeckUrl = new JPanel(new MigLayout("insets 0, gap 0"));
        pnlDeckUrl.setOpaque(false);
        pnlDeckUrl.add(new FLabel.Builder().text(localizer.getMessage("lblDeckUrlLabel")).fontSize(12).fontStyle(Font.BOLD).build(),
                "h " + FTextField.HEIGHT + "px!, gapright 6px");
        txtDeckUrl = new FTextField.Builder().build();
        txtDeckUrl.addActionListener(e -> loadDeckFromUrl());
        pnlDeckUrl.add(txtDeckUrl, "growx, pushx, h " + FTextField.HEIGHT + "px!, gapright 6px");
        btnReloadUrl = new FLabel.ButtonBuilder().text(localizer.getMessage("lblReload")).fontSize(14).build();
        btnReloadUrl.setCommand(this::loadDeckFromUrl);
        pnlDeckUrl.add(btnReloadUrl, "h " + FTextField.HEIGHT + "px!, w pref!");
    }

    private void updateDeckUrlPanelVisibility() {
        final boolean isProvidedDeckUrl = selectedDeckType == DeckType.PROVIDED_DECK_URL;
        if (pnlDeckUrl != null) {
            pnlDeckUrl.setVisible(isProvidedDeckUrl);
        }
    }

    private void syncUrlFieldWithSelectedDeck() {
        if (txtDeckUrl == null || selectedDeckType != DeckType.PROVIDED_DECK_URL) {
            return;
        }
        final DeckProxy selected = lstDecks.getSelectedItem();
        if (selected != null && selected.getSourceUrl() != null) {
            txtDeckUrl.setText(selected.getSourceUrl());
        }
    }

    private void handleDeckSelection() {
        if (updatingDeckPool) {
            return;
        }
        syncUrlFieldWithSelectedDeck();
        if (deckSelectionCommand != null) {
            deckSelectionCommand.run();
        }
    }

    private void loadDeckFromUrl() {
        if (txtDeckUrl == null) {
            return;
        }
        final String deckUrl = txtDeckUrl.getText().trim();
        if (deckUrl.isBlank()) {
            return;
        }

        setDeckUrlLoading(true);
        FThreads.invokeInBackgroundThread(() -> {
            try {
                final DeckProxy deck = DeckUrlLoader.load(deckUrl);
                FThreads.invokeInEdtLater(() -> {
                    lastLoadedUrlDeckName = deck.toString();
                    if (selectedDeckType == DeckType.PROVIDED_DECK_URL) {
                        refreshDecksList(DeckType.PROVIDED_DECK_URL, true, null);
                    }
                    setDeckUrlLoading(false);
                });
            } catch (final IOException ex) {
                FThreads.invokeInEdtLater(() -> {
                    setDeckUrlLoading(false);
                    FOptionPane.showErrorDialog(ex.getMessage(), localizer.getMessage("lblUnableToLoadDeckUrl"));
                });
            }
        });
    }

    private void setDeckUrlLoading(final boolean loading) {
        txtDeckUrl.setEnabled(!loading);
        btnReloadUrl.setEnabled(!loading);
        btnRandom.setEnabled(!loading);
        if (loading) {
            btnReloadUrl.setText(localizer.getMessage("lblLoadingEllipsis"));
        } else {
            btnReloadUrl.setText(localizer.getMessage("lblReload"));
        }
    }

    public final boolean isAi() {
        return isAi;
    }
    public void setIsAi(final boolean isAiDeck) {
        isAi = isAiDeck;
    }

    @Override
    public void deckTypeSelected(final DecksComboBoxEvent ev) {
        refreshDecksList(ev.getDeckType(), false, ev);
    }

    private void refreshDecksList(final DeckType deckType, final boolean forceRefresh, final DecksComboBoxEvent ev) {
        if (decksComboBox == null) { return; } // Not yet populated
        if (selectedDeckType == deckType && !forceRefresh) { return; }
        selectedDeckType = deckType;

        if (ev == null) {
            refreshingDeckType = true;
            decksComboBox.refresh(deckType);
            refreshingDeckType = false;
        }
        lstDecks.setCaption(deckType.toString());

        switch (deckType) {
            case CUSTOM_DECK:
                updateCustom();
                break;
            case COMMANDER_DECK:
                updateCustom();
                break;
            case PRECON_COMMANDER_DECK:
                updateCommanderPrecons();
                break;
            case PROVIDED_DECK_URL:
                updateProvidedDeckUrl();
                break;
            default:
                break; //other deck types not currently supported here
        }
        updateDeckUrlPanelVisibility();
    }

    private final String SELECTED_DECK_DELIMITER = "::";

    public void saveState() {
        if (stateSetting == null) {
            throw new NullPointerException("State setting missing. Specify first using the initialize() method.");
        }
        prefs.setPref(stateSetting, getState());
        prefs.save();
    }

    private String getState() {
        final StringBuilder state = new StringBuilder();
        DeckType selectedDeckType = this.selectedDeckType;   // decksComboBox.getDeckType()
        if (selectedDeckType == null) {
            return "";
        }
        state.append(selectedDeckType.name());
        state.append(";");
        joinSelectedDecks(state, SELECTED_DECK_DELIMITER);
        return state.toString();
    }

    private void joinSelectedDecks(final StringBuilder state, final String delimiter) {
        final Iterable<DeckProxy> selectedDecks = lstDecks.getSelectedItems();
        boolean isFirst = true;
        if (selectedDecks != null) {
            for (final DeckProxy deck : selectedDecks) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    state.append(delimiter);
                }
                state.append(deck.toString());
            }
        }
    }

    public void restoreSavedState() {
        final DeckType oldDeckType = selectedDeckType;
        if (stateSetting == null) {
            //if can't restore saved state, just refresh deck list
            refreshDecksList(oldDeckType, true, null);
            return;
        }

        final String savedState = prefs.getPref(stateSetting);
        refreshDecksList(getDeckTypeFromSavedState(savedState), true, null);
        if (!lstDecks.setSelectedStrings(getSelectedDecksFromSavedState(savedState))) {
            //if can't select old decks, just refresh deck list
            refreshDecksList(oldDeckType, true, null);
        }
    }

    private DeckType getDeckTypeFromSavedState(final String savedState) {
        try {
            if (StringUtils.isBlank(savedState)) {
                return selectedDeckType;
            } else {
                final String deckType = savedState.split(";")[0];
                return DeckType.valueOf(deckType);
            }
        } catch (final IllegalArgumentException ex) {
            System.err.println(ex.getMessage() + ". Using default : " + selectedDeckType);
            return selectedDeckType;
        }
    }

    private List<String> getSelectedDecksFromSavedState(final String savedState) {
        try {
            if (StringUtils.isBlank(savedState)) {
                return new ArrayList<>();
            }
            final String[] parts = savedState.split(";", -1);
            return Arrays.asList(parts[1].split(SELECTED_DECK_DELIMITER));
        } catch (final Exception ex) {
            System.err.println(ex + " [savedState=" + savedState + "]");
            return new ArrayList<>();
        }
    }

    public DecksComboBox getDecksComboBox() {
        return decksComboBox;
    }
}
