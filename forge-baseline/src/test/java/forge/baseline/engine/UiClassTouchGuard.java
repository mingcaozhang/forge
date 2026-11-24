package forge.baseline.engine;

import java.security.Permission;

/**
 * Runtime guard for the 30.2 headless full-match test: installs a
 * {@link SecurityManager} that fails the touching thread as soon as a class
 * from a UI-only package is referenced, proving the adapter drives a complete
 * match without touching the UI layer (no windows, no Swing, no Xvfb).
 *
 * <p>The hook is the JVM's cross-package access probe,
 * {@link #checkPackageAccess(String)}: it fires whenever code in one package
 * references a class in another, so any use of a UI class — instantiation,
 * method call, field access, subclassing — throws a
 * {@link SecurityException} on the game thread, which the engine captures as
 * its failure.
 *
 * <p>The blocklist is the set of packages that exist ONLY in the
 * {@code forge-gui} / {@code forge-gui-desktop} source trees, plus the
 * AWT/Swing/JFX packages. Kept non-UI packages that live in the UI trees are
 * deliberately NOT blocked:
 * <ul>
 *   <li>{@code forge.gamemodes.net} (and subpackages) — the kept
 *       delta/protocol subset the adapter's envelope path uses
 *       ({@code DeltaPacket}, {@code DeltaSyncManager}, …);</li>
 *   <li>{@code forge.gamemodes.match} (the root package only) — the kept
 *       pure data types the action contract references
 *       ({@code DrawOfferMessage}, {@code YieldUpdate},
 *       {@code NextGameDecision}) plus the generic lobby seam; the Swing
 *       subpackage {@code forge.gamemodes.match.input} is blocked.</li>
 * </ul>
 * Packages split across UI and non-UI modules ({@code forge.util},
 * {@code forge.error}, …) are not UI-only and stay allowed.
 *
 * <p>The JVM must be started with {@code -Djava.security.manager=allow}
 * (set in this module's surefire configuration); otherwise {@link #install()}
 * fails loudly instead of silently skipping the guard.
 */
public final class UiClassTouchGuard extends SecurityManager {

    /** UI-only packages; a package or any of its subpackages is blocked. */
    private static final String[] UI_PACKAGE_PREFIXES = {
            // the desktop toolkit
            "java.awt", "javax.swing", "sun.awt", "javafx",
            // forge-gui / forge-gui-desktop only
            "forge.control",
            "forge.deckchooser",
            "forge.download",
            "forge.gamemodes.match.input",
            "forge.gamemodes.replay",
            "forge.gui",
            "forge.haptic",
            "forge.itemmanager",
            "forge.localinstance",
            "forge.menus",
            "forge.screens",
            "forge.sound",
            "forge.toolbox",
            "forge.view",
    };

    private UiClassTouchGuard() {
    }

    /** Installs the guard as the JVM security manager; fails loudly if the JVM was started without the flag. */
    public static UiClassTouchGuard install() {
        UiClassTouchGuard guard = new UiClassTouchGuard();
        try {
            System.setSecurityManager(guard);
        } catch (UnsupportedOperationException e) {
            throw new AssertionError("cannot install the UI-touch guard; the test JVM must be started "
                    + "with -Djava.security.manager=allow (see the surefire configuration in "
                    + "forge-baseline/pom.xml)", e);
        }
        return guard;
    }

    public void uninstall() {
        System.setSecurityManager(null);
    }

    @Override
    public void checkPackageAccess(String packageName) {
        for (String prefix : UI_PACKAGE_PREFIXES) {
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) {
                throw new SecurityException("UI package touched during the headless match: " + packageName);
            }
        }
    }

    /**
     * Allows the guard to be uninstalled again: while a security manager is
     * active, {@code System.setSecurityManager} requires the
     * {@code setSecurityManager} runtime permission, which this guard grants.
     * Everything else keeps the default (allow) behaviour.
     */
    @Override
    public void checkPermission(Permission perm) {
        if (perm instanceof RuntimePermission rp && "setSecurityManager".equals(rp.getName())) {
            return;
        }
    }
}
