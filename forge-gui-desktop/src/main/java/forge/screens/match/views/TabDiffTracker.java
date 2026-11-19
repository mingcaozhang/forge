package forge.screens.match.views;

import forge.gui.framework.DragTab;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.view.arcane.util.UIAnimator;

/**
 * Tracks the change in a tab's item count between updates and briefly "bumps"
 * the tab — a red flash plus a paint-only label scale pop — when the count
 * increases while the tab is hidden. The baseline resets when the tab becomes
 * visible (the user is watching it).
 *
 * <p>The bump is driven through the shared {@link UIAnimator}, so it honors the
 * {@link FPref#UI_ANIMATION_MODE} preference: {@code Off} leaves the tab at
 * rest, {@code Fast} halves the bump, {@code Normal} uses the full duration.
 *
 * <p>Returns the delta to display in the tab label, or 0 when the
 * "New Card Count in Tabs" preference is off.
 */
final class TabDiffTracker {
    private static final int BUMP_DURATION_MILLIS = 420;
    private static final float BUMP_PEAK_SCALE = 1.10f;

    private int baseCount = -1;
    private int lastDelta = 0;

    int update(final int count, final DragTab tab) {
        if (baseCount < 0 || tab.isSelected()) {
            baseCount = count;
            lastDelta = 0;
        }
        final int delta = count - baseCount;
        final boolean prefOn = FModel.getPreferences().getPrefBoolean(FPref.UI_ZONE_TAB_NEW_COUNT);
        if (prefOn && delta > lastDelta) {
            startBump(tab);
        }
        lastDelta = delta;
        return prefOn ? delta : 0;
    }

    private void startBump(final DragTab tab) {
        final String id = "tab-bump-" + System.identityHashCode(tab);
        UIAnimator.startRaw(BUMP_DURATION_MILLIS, id, progress -> {
            // Triangle envelope: rest at 0 and 1, peak mid-way. The final
            // onFrame(1f) UIAnimator always delivers (incl. Mode.OFF snap)
            // therefore lands on the rest state.
            final float flash = progress <= 0.5f ? progress * 2f : (1f - progress) * 2f;
            tab.setFlashIntensity(flash);
            final float bump = 1f + (BUMP_PEAK_SCALE - 1f) * (float) Math.sin(Math.PI * progress);
            tab.setBumpScale(bump);
        });
    }
}
