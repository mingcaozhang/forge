package forge.screens.home;

import javax.swing.SwingUtilities;

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
    }
}
