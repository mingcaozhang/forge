package forge.screens.deckeditor.controllers;

import forge.deck.DeckProxy;
import forge.gui.framework.ICDoc;
import forge.itemmanager.DeckManager;
import forge.screens.deckeditor.views.VCommanderDecks;

/**
 * Controls the "Commander Decks" panel in the deck editor UI.
 *
 * <br><br><i>(C at beginning of class name denotes a control class.)</i>
 *
 */
public enum CCommanderDecks implements ICDoc {
    SINGLETON_INSTANCE;

    private final VCommanderDecks view = VCommanderDecks.SINGLETON_INSTANCE;

    //========== Overridden methods

    @Override
    public void register() {
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.ICDoc#initialize()
     */
    @Override
    public void initialize() {
        refresh();
    }

    public void refresh() {
        DeckManager lstDecks = view.getLstDecks();
        CAllDecks.refreshDeckManager(lstDecks, DeckProxy.getAllCommanderDecks());
        view.updateShowcase(lstDecks.getSelectedItem());
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.ICDoc#update()
     */
    @Override
    public void update() {
        CAllDecks.updateDeckManager(view.getLstDecks());
    }
}
