package forge.headless;

import forge.StaticData;
import forge.util.IHasForgeLog;
import forge.gamemodes.net.NetworkChecksumUtil;
import forge.gamemodes.net.ProtocolGuiGame;
import forge.gui.GuiBase;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * Bootstrap and shared utilities for network test infrastructure.
 * {@link #ensureFModelInitialized()} is the entry point — all test classes call it
 * to set up HeadlessGuiDesktop, load card data, and configure test preferences.
 */
public final class TestUtils {

    private TestUtils() {} // Utility class

    /**
     * Format bytes in human-readable form (B, KB, MB, GB).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Ensure FModel is initialized with HeadlessGuiDesktop for testing.
     * Thread-safe. Always ensures HeadlessGuiDesktop is active, even if another
     * test class set a different GuiBase interface (e.g. GuiDesktop).
     */
    public static synchronized void ensureFModelInitialized() {
        if (!(GuiBase.getInterface() instanceof HeadlessGuiDesktop)) {
            GuiBase.setInterface(new HeadlessGuiDesktop());
        }
        if (StaticData.instance() == null) {
            FModel.initialize(null, preferences -> {
                preferences.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, false);
                preferences.setPref(FPref.UI_LANGUAGE, "en-US");
                preferences.setPref(FPref.ENFORCE_DECK_LEGALITY, false);
                return null;
            });
        }
        // Always ensure runtime test preferences regardless of initialization order —
        // another test class may have initialized FModel before us
        FModel.getPreferences().setPref(FPref.ENFORCE_DECK_LEGALITY, false);

        // Use -Dforge.checksum.mode=production to test with production (sampled) checksum
        boolean useStable = !"production".equalsIgnoreCase(System.getProperty("forge.checksum.mode"));
        NetworkChecksumUtil.setStableChecksum(useStable);

        // Delta sync enabled by default in tests; use -Dforge.deltasync=false to disable
        String deltaSyncProp = System.getProperty("forge.deltasync");
        ProtocolGuiGame.useDeltaSync = !"false".equalsIgnoreCase(deltaSyncProp);

        IHasForgeLog.netLog.info("[TestConfig] checksum={}, deltasync={}",
                useStable ? "stable" : "sampled",
                ProtocolGuiGame.useDeltaSync ? "on" : "off");
    }
}
