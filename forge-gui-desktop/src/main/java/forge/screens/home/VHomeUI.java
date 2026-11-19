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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import forge.gamemodes.tutorial.HowToPlay;
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
import forge.screens.home.settings.VSubmenuAvatars;
import forge.screens.home.settings.VSubmenuDownloaders;
import forge.screens.home.settings.VSubmenuPreferences;
import forge.screens.home.settings.VSubmenuReleaseNotes;
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
            "insets 0, gap 16, wrap " + HERO_TILE_COLUMNS, "repeat(" + HERO_TILE_COLUMNS + ", [grow 1])", "[]16[]"));
    private final Map<EDocID, HomeTile> heroTiles = new LinkedHashMap<>();
    private final RecentDecksCarousel recentDecksCarousel = new RecentDecksCarousel();
    private final LastMatchTile lastMatchTile = new LastMatchTile();

    private JLabel lblLogo = new FLabel.Builder()
        .icon(FSkin.getIcon(FSkinProp.ICO_LOGO))
        .iconAlignX(SwingConstants.CENTER)
        .iconInBackground(true).iconScaleFactor(1.0).build();

    /** Starts the guided practice game that teaches a newcomer the basics. */
    private final JLabel lblHowToPlay = new FLabel.Builder()
        .text(Localizer.getInstance().getMessage("lblHowToPlay")).fontAlign(SwingConstants.CENTER).fontSize(14)
        .tooltip(Localizer.getInstance().getMessage("lblHowToPlayTooltip"))
        .opaque().hoverable().cmdClick(() -> HowToPlay.chooseAndStart()).build();

    VHomeUI() {
        // Add main menu containing logo and menu buttons
        final JPanel pnlMainMenu = new JPanel(new MigLayout("w 200px!, ax center, insets 0, gap 0, wrap"));
        pnlMainMenu.setOpaque(false);

        final int logoSize = 170;
        final int logoBottomGap = 4;
        final int howToPlayHeight = 28;
        final int pnlMainMenuHeight = logoSize + logoBottomGap + howToPlayHeight + logoBottomGap;

        pnlMainMenu.add(lblLogo, "w " + logoSize + "px!, h " + logoSize +
                "px!, gap 0 4px 0 " + logoBottomGap + "px");
        pnlMainMenu.add(lblHowToPlay, "w " + logoSize + "px!, h " + howToPlayHeight +
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
        allSubmenus.add(VSubmenuAvatars.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuDownloaders.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuReleaseNotes.SINGLETON_INSTANCE);

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
        // CardLayout starts on the submenu display so existing navigation is unchanged. CHomeUI
        // wires every hero tile's click through CHomeUI.itemClick(...); surfacing the dashboard
        // (with the fade/slide transition) is deferred to a later phase.
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
            case HOME_AVATARS:
                return FSkinProp.ICO_QUEST_FOX;
            case HOME_RELEASE_NOTES:
                return FSkinProp.ICO_QUEST_NOTES;
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
     * Shows or hides the dashboard over the submenu display area.
     *
     * @param visible0 whether the dashboard should be visible
     */
    public void setDashboardVisible(final boolean visible0) {
        if (isDashboardVisible() == visible0) {
            return;
        }
        pnlDisplayArea.showDashboard(visible0);
        pnlDisplayArea.repaint();
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
        private static final String CARD_DISPLAY = "display";
        private static final String CARD_DASHBOARD = "dashboard";
        private final CardLayout cardLayout = new CardLayout();
        private Component dashboard;

        private PnlDisplayArea() {
            setLayout(cardLayout);
            setOpaque(false);
        }

        private void addCards(final Component display0, final Component dashboard0) {
            this.dashboard = dashboard0;
            add(display0, CARD_DISPLAY);
            add(dashboard0, CARD_DASHBOARD);
            cardLayout.show(this, CARD_DISPLAY);
        }

        private void showDashboard(final boolean show0) {
            cardLayout.show(this, show0 ? CARD_DASHBOARD : CARD_DISPLAY);
        }

        private boolean isDashboardShown() {
            return dashboard != null && dashboard.isVisible();
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
