package forge.screens.home;

import javax.swing.SwingUtilities;

import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

public class CLobby {

    private final VLobby view;

    public CLobby(final VLobby view) {
        this.view = view;
        view.setController(this);
    }

    public void update() {
        SwingUtilities.invokeLater(() -> {
            // General updates when switching back to this view
            view.getBtnStart().requestFocusInWindow();
        });
    }

    public void initialize() {
        final ForgePreferences prefs = FModel.getPreferences();
        // Checkbox event handling
        view.getCbSingletons().addActionListener(arg0 -> {
            prefs.setPref(FPref.DECKGEN_SINGLETONS, String.valueOf(view.getCbSingletons().isSelected()));
            prefs.save();
            view.markDirty();
        });

        view.getCbArtifacts().addActionListener(arg0 -> {
            prefs.setPref(FPref.DECKGEN_ARTIFACTS, String.valueOf(view.getCbArtifacts().isSelected()));
            prefs.save();
            view.markDirty();
        });

        // Pre-select checkboxes
        view.getCbSingletons().setSelected(prefs.getPrefBoolean(FPref.DECKGEN_SINGLETONS));
        view.getCbArtifacts().setSelected(prefs.getPrefBoolean(FPref.DECKGEN_ARTIFACTS));
    }
}
