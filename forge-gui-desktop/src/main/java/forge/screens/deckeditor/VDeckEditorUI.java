package forge.screens.deckeditor;

import javax.swing.SwingUtilities;

import forge.Singletons;
import forge.gui.framework.DragCell;
import forge.gui.framework.FScreen;
import forge.gui.framework.IVTopLevelUI;
import forge.screens.deckeditor.views.VCardCatalog;
import forge.view.FView;

/** 
/** 
 * Top level view class; instantiates and assembles
 * tabs used in deck editor UI drag layout.<br>
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 * 
 */
public enum VDeckEditorUI implements IVTopLevelUI {
    /** */
    SINGLETON_INSTANCE;

    //========== Overridden methods

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#instantiate()
     */
    @Override
    public void instantiate() {
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#populate()
     */
    @Override
    public void populate() {
        applyModernChrome();
        SwingUtilities.invokeLater(() -> VCardCatalog.SINGLETON_INSTANCE.getItemManager().focus());
    }

    /**
     * Applies the modern deck-editor chrome (modern tab strip + rounded panels)
     * to every drag cell currently hosting editor tabs. The editor layout file
     * ({@code editor.xml}) is untouched; this only toggles visual styling that
     * defaults off elsewhere, so the shared match-screen look is unchanged.
     */
    private void applyModernChrome() {
        for (final DragCell cell : FView.SINGLETON_INSTANCE.getDragCells()) {
            cell.setModernChrome(true);
        }
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#onSwitching(forge.gui.framework.FScreen)
     */
    @Override
    public boolean onSwitching(FScreen fromScreen, FScreen toScreen) {
        //ensure deck saved before switching
        return CDeckEditorUI.SINGLETON_INSTANCE.canSwitchAway(false);
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#onClosing()
     */
    @Override
    public boolean onClosing(FScreen screen) {
        if (screen == FScreen.DECK_EDITOR_CONSTRUCTED) {
            //don't close tab if Constructed editor, but return to home screen if this called
            Singletons.getControl().setCurrentScreen(FScreen.HOME_SCREEN);
            return false;
        }
        return CDeckEditorUI.SINGLETON_INSTANCE.canSwitchAway(true);
    }
}
