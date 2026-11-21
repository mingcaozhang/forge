/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.screens.home;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import forge.Singletons;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.gui.framework.ICDoc;
import forge.gui.framework.ILocalRepaint;
import forge.gui.framework.IVTopLevelUI;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.localinstance.skin.FSkinProp;
import forge.model.FModel;
import forge.screens.home.sanctioned.VSubmenuConstructed;
import forge.screens.home.settings.VSubmenuAchievements;
import forge.screens.home.settings.VSubmenuPreferences;
import forge.screens.home.tiles.HomeTile;
import forge.screens.home.tiles.LastMatchTile;
import forge.screens.home.tiles.RecentDecksCarousel;
import forge.toolbox.FLabel;
import forge.toolbox.FScrollPanel;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.toolbox.FSkin.SkinnedPanel;
import forge.util.Localizer;
import forge.view.FView;
import forge.view.arcane.util.UIAnimator;
import net.miginfocom.swing.MigLayout;

/**
 * Top level view class for home UI drag layout.<br>
 * Uses singleton pattern.<br>
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */

@SuppressWarnings("serial")
public enum VHomeUI implements IVTopLevelUI {
    /** */
    SINGLETON_INSTANCE;

    private final SkinColor clrTheme = FSkin.getColor(FSkin.Colors.CLR_THEME);
    private final SkinColor l00 = clrTheme.stepColor(0);

    private final List<IVSubmenu<? extends ICDoc>> allSubmenus = new ArrayList<>();
    private final Map<EDocID, LblMenuItem> allSubmenuLabels = new HashMap<>();
    private final Map<EMenuGroup, LblGroup> allGroupLabels = new HashMap<>();

    private final PnlMenu pnlMenu = new PnlMenu();
    private final PnlDisplay pnlDisplay = new PnlDisplay();
    private final FScrollPanel pnlSubmenus;

    /** Number of hero tiles drawn per row of the dashboard grid. */
    private static final int HERO_TILE_COLUMNS = 3;
    /** Preferred height of a hero tile in pixels. */
    private static final int HERO_TILE_HEIGHT = 118;
    /** Preferred width of the last-match tile in pixels. */
    private static final int LAST_MATCH_TILE_WIDTH = 320;
    /** Preferred height of the last-match tile in pixels. */
    private static final int LAST_MATCH_TILE_HEIGHT = 154;

    /** Holds {@link #pnlDisplay} and {@link #pnlDashboard} in the same screen area. */
    private final PnlDisplayArea pnlDisplayArea = new PnlDisplayArea();
    private final JPanel pnlDashboard = new JPanel(new MigLayout("insets 24, gap 16, wrap, hidemode 3"));
    private final JPanel pnlHeroTiles = new JPanel(new MigLayout(
            "insets 0, gap 16, wrap " + HERO_TILE_COLUMNS, heroTileColumnConstraints(), "[]16[]"));
    private final Map<EDocID, HomeTile> heroTiles = new LinkedHashMap<>();
    private final RecentDecksCarousel recentDecksCarousel = new RecentDecksCarousel();
    private final LastMatchTile lastMatchTile = new LastMatchTile();

    private JLabel lblLogo = new FLabel.Builder()
        .icon(FSkin.getIcon(FSkinProp.ICO_LOGO))
        .iconAlignX(SwingConstants.CENTER)
        .iconInBackground(true).iconScaleFactor(1.0).build();

    VHomeUI() {
        // Add main menu containing logo and menu buttons
        final JPanel pnlMainMenu = new JPanel(new MigLayout("w 200px!, ax center, insets 0, gap 0, wrap"));
        pnlMainMenu.setOpaque(false);

        final int logoSize = 170;
        final int logoBottomGap = 4;
        final int pnlMainMenuHeight = logoSize + logoBottomGap;

        pnlMainMenu.add(lblLogo, "w " + logoSize + "px!, h " + logoSize +
                "px!, gap 0 4px 0 " + logoBottomGap + "px");
        pnlMenu.add(pnlMainMenu);
        
        pnlSubmenus = new FScrollPanel(new MigLayout("insets 0, gap 0, wrap, hidemode 3"), true,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Add new menu items here (order doesn't matter).
        // Single-player Commander/EDH only: the multiplayer/online submenus (VSubmenuOnlineLobby,
        // VSubmenuOnlineDecks) are intentionally not surfaced here. Their classes remain because the
        // match screen's network reconnect path still references them.
        allSubmenus.add(VSubmenuConstructed.SINGLETON_INSTANCE);

        allSubmenus.add(VSubmenuPreferences.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuAchievements.SINGLETON_INSTANCE);

        // Only render groups that actually contain menu items, so removed sections leave no empty header.
        final Set<EMenuGroup> populatedGroups = new LinkedHashSet<>();
        for (final IVSubmenu<? extends ICDoc> item : allSubmenus) {
            populatedGroups.add(item.getGroupEnum());
        }

        // For each populated group: init its panel
        final SortedMap<EMenuGroup, JPanel> allGroupPanels = new TreeMap<>();
        for (final EMenuGroup e : populatedGroups) {
            allGroupPanels.put(e, new PnlGroup());
            allGroupPanels.get(e).setVisible(false);
            allGroupPanels.get(e).setLayout(new MigLayout("insets 0, gap 0, wrap"));
            allGroupPanels.get(e).setName(e.toString());
        }

        // For each item: Add to its group, and add to the card layout in right panel.
        for (final IVSubmenu<? extends ICDoc> item : allSubmenus) {
            allSubmenuLabels.put(item.getItemEnum(), new LblMenuItem(item));
            allGroupPanels.get(item.getGroupEnum()).add(
                    allSubmenuLabels.get(item.getItemEnum()), "w 100%!, h 30px!, gap 0 0 1px 1px");
        }

        // For each group: add its title, then its panel, then "click" if necessary.
        for (final EMenuGroup e : allGroupPanels.keySet()) {
            allGroupLabels.put(e, new LblGroup(e));
            pnlSubmenus.add(allGroupLabels.get(e), "w 100%!, h 30px!, gap 0 0 3px 3px");
            pnlSubmenus.add(allGroupPanels.get(e), "w 100%!, gap 0 0 0 0");

            // Expand groups expanded from previous session
            if (FModel.getPreferences().getPrefBoolean(FPref.valueOf("SUBMENU_" + e.toString()))) {
                allGroupLabels.get(e).groupClick(e);
            }
        }

        pnlMenu.add(pnlSubmenus, "w 100%!, h 100% - " + pnlMainMenuHeight + "px!");
        pnlDisplay.setBackground(l00.alphaColor(100));

        pnlDashboard.setOpaque(false);
        pnlHeroTiles.setOpaque(false);
        for (final IVSubmenu<? extends ICDoc> item : allSubmenus) {
            final HomeTile tile = new HomeTile(item.getMenuTitle())
                    .setDocId(item.getItemEnum())
                    .setIcon(heroIconFor(item.getItemEnum()))
                    .setTileSize(HERO_TILE_HEIGHT * 2, HERO_TILE_HEIGHT);
            heroTiles.put(item.getItemEnum(), tile);
            pnlHeroTiles.add(tile, "w 100%!, h " + HERO_TILE_HEIGHT + "px!");
        }
        pnlDashboard.add(pnlHeroTiles, "w 100%!");

        final JLabel lblRecentDecks = new FLabel.Builder()
                .text(Localizer.getInstance().getMessageorUseDefault("lblRecentDecks", "Recent Decks"))
                .fontAlign(SwingConstants.LEFT).fontSize(16).build();
        pnlDashboard.add(lblRecentDecks, "w 100%!");

        final JPanel pnlRecentDecks = new JPanel(new MigLayout("insets 0, gap 16", "[grow 1][]"));
        pnlRecentDecks.setOpaque(false);
        pnlRecentDecks.add(recentDecksCarousel, "h " + RecentDecksCarousel.getPreferredHeight() + "px!, pushx, growx");
        lastMatchTile.setTileSize(LAST_MATCH_TILE_WIDTH, LAST_MATCH_TILE_HEIGHT);
        pnlRecentDecks.add(lastMatchTile, "w " + LAST_MATCH_TILE_WIDTH + "px!, h " + LAST_MATCH_TILE_HEIGHT + "px!");
        pnlDashboard.add(pnlRecentDecks, "w 100%!, pushy, growy");

        pnlDisplayArea.addCards(pnlDisplay, pnlDashboard);
        // Both panels share one area; the submenu display stays live so existing navigation and
        // getPnlDisplay() callers are unchanged. CHomeUI toggles the dashboard with a fade+slide
        // tween (re-clicking the active menu item opens it; tile or menu clicks return to it).
    }

    /**
     * MigLayout 4.2 predates the {@code repeat()} column function, so the hero
     * columns are spelled out explicitly, one {@code [grow 1]} cell each.
     *
     * @return the MigLayout column constraint string for the hero tile grid
     */
    private static String heroTileColumnConstraints() {
        final StringBuilder sb = new StringBuilder(HERO_TILE_COLUMNS * 8);
        for (int i = 0; i < HERO_TILE_COLUMNS; i++) {
            sb.append("[grow 1]");
        }
        return sb.toString();
    }

    /**
     * Picks a skin icon for a dashboard hero tile. Unknown documents fall back to
     * the generic shortcuts icon so that new submenus still render a tile.
     *
     * @param docId0 the document the tile opens
     * @return a skin image property, never {@code null}
     */
    private static FSkinProp heroIconFor(final EDocID docId0) {
        switch (docId0) {
            case HOME_CONSTRUCTED:
                return FSkinProp.ICO_PLAYERS;
            case HOME_PREFERENCES:
                return FSkinProp.ICO_SETTINGS;
            case HOME_ACHIEVEMENTS:
                return FSkinProp.ICO_QUEST_GOLD;
            default:
                return FSkinProp.ICO_SHORTCUTS;
        }
    }

    /** @return {@link javax.swing.JPanel} */
    public JPanel getPnlMenu() {
        return pnlMenu;
    }

    /** @return {@link javax.swing.JPanel} */
    public PnlDisplay getPnlDisplay() {
        return pnlDisplay;
    }

    /** @return {@link forge.toolbox.FScrollPanel} */
    public FScrollPanel getPnlSubmenus() {
        return pnlSubmenus;
    }

    /**
     * 
     * @return Map<EMenuItem, FLabel>
     */
    public Map<EDocID, LblMenuItem> getAllSubmenuLabels() {
        return allSubmenuLabels;
    }

    /** @return the Arena-style dashboard panel, {@code null}-free but hidden by default */
    public JPanel getPnlDashboard() {
        return pnlDashboard;
    }

    /** @return {@code true} when the dashboard is currently shown instead of a submenu */
    public boolean isDashboardVisible() {
        return pnlDisplayArea.isDashboardShown();
    }

    /**
     * Shows or hides the dashboard over the submenu display area, animating the swap
     * with a fade+slide tween (snapped when animations are disabled).
     *
     * <p>When {@link FPref#UI_SCREENREADER_OPTIMIZE} is enabled the request is
     * always treated as {@code false}, so screen-reader users keep the classic
     * submenu list layout instead of the Arena-style tile dashboard.</p>
     *
     * @param visible0 whether the dashboard should be visible
     */
    public void setDashboardVisible(final boolean visible0) {
        pnlDisplayArea.showDashboard(visible0);
    }

    /**
     * @return {@code true} when screen-reader optimization is enabled, which
     *         makes the home screen fall back to the classic submenu list
     *         layout and never surface the tile dashboard
     */
    public static boolean isAccessibilityLayout() {
        try {
            return FModel.getPreferences().getPrefBoolean(FPref.UI_SCREENREADER_OPTIMIZE);
        } catch (final Throwable t) {
            // Preferences not ready or unreadable: default to the normal layout.
            return false;
        }
    }

    /**
     * @return the hero tiles of the dashboard, keyed by the document each tile opens
     */
    public Map<EDocID, HomeTile> getHeroTiles() {
        return Collections.unmodifiableMap(heroTiles);
    }

    /** @return the document IDs surfaced by the dashboard hero tiles */
    public Set<EDocID> getDashboardDocIds() {
        return Collections.unmodifiableSet(heroTiles.keySet());
    }

    /** @return the recent decks carousel shown on the dashboard */
    public RecentDecksCarousel getRecentDecksCarousel() {
        return recentDecksCarousel;
    }

    /** @return the last-match tile shown on the dashboard */
    public LastMatchTile getLastMatchTile() {
        return lastMatchTile;
    }

    /** Re-reads the data shown by the dashboard carousel and tiles. */
    public void refreshDashboard() {
        recentDecksCarousel.refresh();
        lastMatchTile.repaint();
    }

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
        JPanel pnl = FView.SINGLETON_INSTANCE.getPnlInsets();
        pnl.setBorder(null);
        pnl.setLayout(new MigLayout("insets 0, gap 0"));

        pnl.add(pnlMenu, "w 205px!, h 100%!");
        pnl.add(pnlDisplayArea, "w 100% - 205px!, h 100%!");
    }

    /**
     * Stacks the dashboard and the submenu display panel in the same screen area so
     * that existing submenu code can keep using {@link #getPnlDisplay()} unchanged.
     */
    private static final class PnlDisplayArea extends JPanel {
        /** Key used to coalesce/cancel the in-flight display transition tween. */
        private static final String TWEEN_ID = "home-display-transition";
        /** Duration of the fade+slide transition in milliseconds (clamped by {@link UIAnimator}). */
        private static final int TRANSITION_MILLIS = 220;
        /** Slide distance as a fraction of the area width (outgoing panel slides off). */
        private static final int SLIDE_DIVISOR = 8;
        /** Minimum slide distance in pixels so the motion reads on narrow layouts. */
        private static final int MIN_SLIDE_PX = 48;

        private Component display;
        private Component dashboard;
        private boolean dashboardVisible;

        // Live transition state, painted on top of the incoming panel each frame.
        private BufferedImage snapshot;
        private float snapshotAlpha;
        private int snapshotDx;

        private PnlDisplayArea() {
            setLayout(null);
            setOpaque(false);
        }

        private void addCards(final Component display0, final Component dashboard0) {
            this.display = display0;
            this.dashboard = dashboard0;
            add(display0);
            add(dashboard0);
            applyVisibility();
        }

        @Override
        public void doLayout() {
            final int w = getWidth();
            final int h = getHeight();
            if (display != null) {
                display.setBounds(0, 0, w, h);
            }
            if (dashboard != null) {
                dashboard.setBounds(0, 0, w, h);
            }
        }

        void showDashboard(final boolean show0) {
            // Accessibility fallback: with screen-reader optimization on, the tile
            // dashboard is never shown and navigation stays in the submenu list.
            final boolean show = show0 && !isAccessibilityLayout();
            if (dashboardVisible == show) {
                return;
            }
            dashboardVisible = show;

            final Component outgoing = show ? display : dashboard;
            final Component incoming = show ? dashboard : display;

            if (UIAnimator.currentMode() == UIAnimator.Mode.OFF) {
                clearSnapshot();
                applyVisibility();
                repaint();
                return;
            }

            // Capture the outgoing panel while it is still live, then switch the live
            // panel immediately so the incoming one is hit-testable during the tween.
            final BufferedImage outgoingImage = snapshotOf(outgoing);
            clearSnapshot();
            applyVisibility();
            if (outgoingImage == null) {
                repaint();
                return;
            }
            snapshot = outgoingImage;
            snapshotAlpha = 1f;

            final int maxSlide = Math.max(MIN_SLIDE_PX, getWidth() / SLIDE_DIVISOR) * (show ? -1 : 1);
            UIAnimator.start(TRANSITION_MILLIS, TWEEN_ID, new UIAnimator.Tween() {
                @Override
                public void onFrame(final float easedProgress) {
                    snapshotAlpha = 1f - easedProgress;
                    snapshotDx = Math.round(maxSlide * easedProgress);
                    PnlDisplayArea.this.repaint();
                }

                @Override
                public void onEnd() {
                    clearSnapshot();
                    PnlDisplayArea.this.repaint();
                }
            });
        }

        boolean isDashboardShown() {
            return dashboardVisible;
        }

        private void applyVisibility() {
            if (display != null) {
                display.setVisible(!dashboardVisible);
            }
            if (dashboard != null) {
                dashboard.setVisible(dashboardVisible);
            }
            revalidate();
        }

        private void clearSnapshot() {
            snapshot = null;
            snapshotAlpha = 0f;
            snapshotDx = 0;
        }

        private static BufferedImage snapshotOf(final Component c0) {
            final int w = c0.getWidth();
            final int h = c0.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g2d = image.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                c0.paint(g2d);
            }
            finally {
                g2d.dispose();
            }
            return image;
        }

        @Override
        public void paint(final Graphics g) {
            super.paint(g);
            final BufferedImage image = snapshot;
            if (image == null || snapshotAlpha <= 0f) {
                return;
            }
            final Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, snapshotAlpha));
                g2d.drawImage(image, snapshotDx, 0, null);
            }
            finally {
                g2d.dispose();
            }
        }
    }


    /** */
    public class PnlDisplay extends SkinnedPanel implements ILocalRepaint {
        /** Constructor. */
        public PnlDisplay() {
            this.setOpaque(false);
        }

        @Override
        public void repaintSelf() {
            final Dimension d = this.getSize();
            repaint(0, 0, d.width, d.height);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            final Graphics2D g2d = (Graphics2D) g.create();

            g2d.setColor(this.getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.dispose();
        }
    }

    private class PnlMenu extends JPanel {
        private final SkinColor d80 = clrTheme.stepColor(-80);

        public PnlMenu() {
            this.setLayout(new MigLayout("insets 0, gap 0, wrap, hidemode 3"));
            this.setOpaque(false);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            final LblMenuItem lblSelected = CHomeUI.SINGLETON_INSTANCE.getLblSelected();
            final FScrollPanel scrollPanel = VHomeUI.SINGLETON_INSTANCE.getPnlSubmenus();
            final Graphics2D g2d = (Graphics2D) g.create();
            final int w = getWidth();
            int y1 = 0;
            int y2 = 0;
            int h1 = getHeight();
            int h2 = 0;
            
            if (lblSelected.isShowing()) {
                int scrollPanelTop = scrollPanel.getY();
                int labelTop = lblSelected.getY() + lblSelected.getParent().getY() + scrollPanelTop - scrollPanel.getVerticalScrollBar().getValue();
                y2 = labelTop + lblSelected.getHeight();

                //ensure clipped to scroll panel
                if (y2 > scrollPanelTop) {
                    if (labelTop < scrollPanelTop) {
                        labelTop = scrollPanelTop;
                    }
                    h2 = h1 - y2;
                    h1 = labelTop - y1;
                }
            }

            FSkin.setGraphicsColor(g2d, l00);
            g2d.fillRect(0, y1, w, h1);
            if (h2 > 0) {
                g2d.fillRect(0, y2, w, h2);
            }
            
            int x = w - 8;
            FSkin.setGraphicsGradientPaint(g2d, x, 0, l00, w, 0, d80);
            g2d.fillRect(x, y1, w, h1);
            if (h2 > 0) {
                g2d.fillRect(x, y2, w, h2);
            }

            g2d.dispose();
        }
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#onSwitching(forge.gui.framework.FScreen)
     */
    @Override
    public boolean onSwitching(FScreen fromScreen, FScreen toScreen) {
        return true;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#onClosing(forge.gui.control.FControl.Screens)
     */
    @Override
    public boolean onClosing(FScreen screen) {
        Singletons.getControl().exitForge();
        return false; //don't allow closing Home tab
    }
}
