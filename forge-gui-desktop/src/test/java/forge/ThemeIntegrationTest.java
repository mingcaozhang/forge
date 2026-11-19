package forge;

import javax.swing.UIManager;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.formdev.flatlaf.FlatDarkLaf;

import forge.localinstance.properties.ForgePreferences.FPref;

/**
 * Guards the Phase 1 theming groundwork: FlatLaf availability on the desktop
 * classpath, the new theming FPref values and their defaults, and that
 * FlatLaf can be installed as the active look and feel. All checks are
 * headless-safe (no JFrame is created).
 */
public class ThemeIntegrationTest {

    @Test
    public void flatLafIsOnClasspath() {
        final Class<?> flatDarkLaf = FlatDarkLaf.class;
        Assert.assertNotNull(flatDarkLaf, "FlatDarkLaf should be resolvable on the test classpath");
        Assert.assertEquals(flatDarkLaf.getName(), "com.formdev.flatlaf.FlatDarkLaf");
    }

    @Test
    public void newThemingPrefsExistWithDefaults() {
        Assert.assertEquals(FPref.UI_UI_THEME.getDefault(), "ArenaDark");
        Assert.assertEquals(FPref.UI_ANIMATION_MODE.getDefault(), "Normal");
    }

    @Test
    public void flatDarkLafCanBecomeActiveLookAndFeel() throws Exception {
        final javax.swing.LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            Assert.assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
                    "FlatDarkLaf should be the active look and feel after install");
        } finally {
            if (previous != null) {
                try {
                    UIManager.setLookAndFeel(previous);
                } catch (Exception ignored) {
                    // Restoring the prior L&F is best-effort; nothing depends on it.
                }
            }
        }
    }
}
