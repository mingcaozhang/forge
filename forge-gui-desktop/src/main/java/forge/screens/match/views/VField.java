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
package forge.screens.match.views;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

import forge.game.card.CardView;
import forge.game.card.CounterEnumType;
import forge.game.GameView;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.gui.framework.IVDoc;
import forge.localinstance.skin.FSkinProp;
import forge.screens.match.CMatchUI;
import forge.screens.match.controllers.CField;
import forge.toolbox.FLabel;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.toolbox.FSkin.SkinImage;
import forge.toolbox.FSkin.SkinnedPanel;
import forge.toolbox.special.PhaseIndicator;
import forge.toolbox.special.PlayerDetailsPanel;
import forge.util.AITimeoutTracker;
import forge.util.Localizer;
import forge.view.arcane.PlayArea;
import forge.view.arcane.CardPanel;
import forge.view.arcane.util.Easing;
import forge.view.arcane.util.UIAnimator;
import net.miginfocom.swing.MigLayout;

/** 
 * Assembles Swing components of a player field instance.
 * 
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public class VField implements IVDoc<CField> {
    private final static int LIFE_CRITICAL = 5;
    private final static int POISON_CRITICAL = 8;

    // Fields used with interface IVDoc
    private final CField control;
    private DragCell parentCell;
    private final EDocID docID;
    private final DragTab tab = new DragTab(Localizer.getInstance().getMessage("lblField"));

    // Other fields
    private final CMatchUI matchUI;
    private final PlayerView player;

    // Top-level containers
    private final FScrollPane scroller = new FScrollPane(false);
    private final PlayArea tabletop;
    private final SkinnedPanel playerImageArea = new SkinnedPanel() {
        @Override
        protected void paintComponent(final Graphics g) {
            super.paintComponent(g);
            paintPlayerPanelChrome(g);
            paintLifeChips(g);
        }

        @Override
        protected void paintChildren(final Graphics g) {
            super.paintChildren(g);
            paintAITimeoutBar(g);
        }
    };
    private boolean playerImagePanelHovered;

    // ---- AI "thinking" timer bar over the player image (shrinks to 0 at the AI timeout) ----
    private static final Color BAR_BG = new Color(0, 0, 0, 165);
    private static final Color BAR_AMBER = new Color(255, 165, 0);
    private static final Set<VField> LIVE_FIELDS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    static {
        // AITimeoutTracker calls this when a bar is due to appear and whenever an AI decision ends
        AITimeoutTracker.setRepaintHook(() -> SwingUtilities.invokeLater(() -> {
            final List<VField> fields;
            synchronized (LIVE_FIELDS) { fields = new ArrayList<>(LIVE_FIELDS); }
            for (final VField f : fields) { f.refreshAITimeoutBar(); }
        }));
    }
    private final Timer aiTimeoutTimer = new Timer(50, e -> refreshAITimeoutBar());
    private boolean aiTimeoutBarShown;

    /** (game, player) key of this field's player; the tracker is shared by every game in the JVM. */
    private long aiTimeoutKey() {
        final GameView gv = matchUI == null ? null : matchUI.getGameView();
        return gv == null ? Long.MIN_VALUE : AITimeoutTracker.key(gv.getId(), player.getId()); // MIN_VALUE: no session
    }

    private void refreshAITimeoutBar() {
        if (player == null) { return; }
        final boolean show = AITimeoutTracker.getRemainingFraction(aiTimeoutKey()) >= 0f;
        if (show) {
            if (!aiTimeoutTimer.isRunning()) { aiTimeoutTimer.start(); }
            playerImageArea.repaint();
        } else {
            aiTimeoutTimer.stop();
            if (aiTimeoutBarShown) { playerImageArea.repaint(); } // clear it
        }
        aiTimeoutBarShown = show;
    }

    private void paintAITimeoutBar(final Graphics g) {
        if (player == null) { return; }
        final float f = AITimeoutTracker.getRemainingFraction(aiTimeoutKey());
        if (f < 0f) { return; }
        lblPlayerImage.getBounds(playerImageBoundsScratch);
        final Rectangle r = playerImageBoundsScratch;
        final int barH = Math.max(4, r.height / 10);
        final int y = r.y + r.height - barH;
        g.setColor(BAR_BG);
        g.fillRect(r.x, y, r.width, barH);
        g.setColor(f > 0.5f ? Color.GREEN : f > 0.25f ? BAR_AMBER : Color.RED);
        g.fillRect(r.x, y, Math.round(r.width * f), barH);
    }

    // ---- Arena-style rounded player-panel backdrop + life/counter chips ----
    private static final int PANEL_ARC = 12;
    private static final int PANEL_PAD = 2;

    /** Paints the rounded, subtly gradient card backdrop (and hover fill) behind a player's image panel. */
    private void paintPlayerPanelChrome(final Graphics graphics) {
        final int w = playerImageArea.getWidth();
        final int h = playerImageArea.getHeight();
        if (w <= PANEL_PAD * 2 || h <= PANEL_PAD * 2) {
            return;
        }
        final Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final SkinColor theme = FSkin.getColor(FSkin.Colors.CLR_THEME);
            g2d.setPaint(new GradientPaint(0f, PANEL_PAD, theme.stepColor(-12).getColor(),
                    0f, h - PANEL_PAD, theme.stepColor(-20).getColor()));
            g2d.fillRoundRect(PANEL_PAD, PANEL_PAD, w - PANEL_PAD * 2, h - PANEL_PAD * 2, PANEL_ARC, PANEL_ARC);
            if (playerImagePanelHovered) {
                FSkin.setGraphicsColor(g2d, FSkin.getColor(FSkin.Colors.CLR_HOVER).alphaColor(110));
                g2d.fillRoundRect(PANEL_PAD, PANEL_PAD, w - PANEL_PAD * 2, h - PANEL_PAD * 2, PANEL_ARC, PANEL_ARC);
            }
            FSkin.setGraphicsColor(g2d,
                    FSkin.getColor(FSkin.Colors.CLR_BORDERS).getContrastColor(25).alphaColor(200));
            g2d.drawRoundRect(PANEL_PAD, PANEL_PAD, w - PANEL_PAD * 2, h - PANEL_PAD * 2, PANEL_ARC, PANEL_ARC);
        } finally {
            g2d.dispose();
        }
    }

    /** Paints a rounded pill behind the visible life/counter read-out row, giving it a modern chip look. */
    private void paintLifeChips(final Graphics graphics) {
        final Rectangle row = getLifeRowBounds();
        if (row == null || row.width <= 4 || row.height <= 4) {
            return;
        }
        final Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final int arc = row.height;
            final SkinColor theme2 = FSkin.getColor(FSkin.Colors.CLR_THEME2);
            g2d.setPaint(new GradientPaint(0f, row.y, theme2.stepColor(-6).getColor(),
                    0f, row.y + row.height, theme2.stepColor(-14).getColor()));
            g2d.fillRoundRect(row.x, row.y, row.width, row.height, arc, arc);
            FSkin.setGraphicsColor(g2d,
                    FSkin.getColor(FSkin.Colors.CLR_BORDERS).getContrastColor(30).alphaColor(170));
            g2d.drawRoundRect(row.x, row.y, row.width, row.height, arc, arc);
        } finally {
            g2d.dispose();
        }
    }

    /** Union of the visible life + counter label bounds, or {@code null} when nothing is shown.
     *  Accumulates into a shared scratch Rectangle (and the fixed label array field) so the
     *  per-frame chip repaint doesn't allocate a new array/union of Rectangles. The result is
     *  only consumed immediately by paintLifeChips, so returning the shared instance is safe. */
    private Rectangle getLifeRowBounds() {
        boolean found = false;
        for (final FLabel label : lifeRowLabels) {
            if (label.isShowing()) {
                if (found) {
                    lifeRowScratch.add(label.getBounds());
                } else {
                    lifeRowScratch.setBounds(label.getBounds());
                    found = true;
                }
            }
        }
        return found ? lifeRowScratch : null;
    }

    private final PlayerDetailsPanel detailsPanel;

    // Player image area
    private final FLabel lblPlayerImage     = new FLabel.Builder().fontAlign(SwingConstants.CENTER).iconScaleFactor(1.0f).build();
    private final FLabel lblLife       = new FLabel.Builder().fontAlign(SwingConstants.CENTER).fontStyle(Font.BOLD).build();
    private final FLabel lblPoison     = new FLabel.Builder().fontAlign(SwingConstants.CENTER).fontStyle(Font.BOLD).icon(FSkin.getImage(FSkinProp.IMG_POISON)).iconInBackground().build();
    private final FLabel lblEnergy     = new FLabel.Builder().fontAlign(SwingConstants.CENTER).fontStyle(Font.BOLD).icon(FSkin.getImage(FSkinProp.IMG_ENERGY)).iconInBackground().build();
    private final FLabel lblExperience = new FLabel.Builder().fontAlign(SwingConstants.CENTER).fontStyle(Font.BOLD).icon(FSkin.getImage(FSkinProp.IMG_EXPERIENCE)).iconInBackground().build();
    private final FLabel lblTicket     = new FLabel.Builder().fontAlign(SwingConstants.CENTER).fontStyle(Font.BOLD).icon(FSkin.getImage(FSkinProp.IMG_TICKET)).iconInBackground().build();
    private final FLabel lblRad        = new FLabel.Builder().fontAlign(SwingConstants.CENTER).fontStyle(Font.BOLD).icon(FSkin.getImage(FSkinProp.IMG_RAD)).iconInBackground().build();

    // Reused across the player image panel's repaint so getLifeRowBounds()/paintAITimeoutBar
    // don't allocate a new Rectangle (and array) every frame.
    private final FLabel[] lifeRowLabels = { lblLife, lblPoison, lblEnergy, lblExperience, lblTicket, lblRad };
    private final Rectangle lifeRowScratch = new Rectangle();
    private final Rectangle playerImageBoundsScratch = new Rectangle();

    private final PhaseIndicator phaseIndicator = new PhaseIndicator();

    private final Border borderPlayerImageSimple = new LineBorder(new Color(0, 0, 0, 0), 1);
    private final Border borderPlayerImageHighlighted = new LineBorder(Color.red, 2);
    // dashed while the player can be picked as a target, solid red once picked
    private final Border borderPlayerImageSelectable = BorderFactory.createDashedBorder(new Color(255, 215, 0), 2f, 5f, 3f, false);

    private final TabDiffTracker tabDiff = new TabDiffTracker();


    //========= Constructor
    /**
     * Assembles Swing components of a player field instance.
     * 
     * @param p &emsp; {@link forge.game.player.Player}
     * @param id0 &emsp; {@link forge.gui.framework.EDocID}
     */
    public VField(final CMatchUI matchUI, final EDocID id0, final PlayerView p, final boolean mirror) {
        this.docID = id0;

        this.matchUI = matchUI;
        this.player = p;
        LIVE_FIELDS.add(this);
        if (p != null) { tab.setText(Localizer.getInstance().getMessage("lblPlayField", p.getName())); }
        else { tab.setText(Localizer.getInstance().getMessage("lblNoPlayerForEDocID", docID.toString())); }

        detailsPanel = new PlayerDetailsPanel(player, CMatchUI.FLOATING_ZONE_TYPES);

        // TODO player is hard-coded into tabletop...should be dynamic
        // (haven't looked into it too deeply). Doublestrike 12-04-12
        tabletop = new PlayArea(matchUI, scroller, mirror, player, ZoneType.Battlefield);

        control = new CField(matchUI, player, this);

        lblPlayerImage.setFocusable(false);
        lblLife.setFocusable(false);
        lblPoison.setFocusable(false);
        lblEnergy.setFocusable(false);
        lblExperience.setFocusable(false);
        lblTicket.setFocusable(false);
        lblRad.setFocusable(false);

        playerImageArea.setOpaque(false);
        playerImageArea.setBackground(FSkin.getColor(FSkin.Colors.CLR_HOVER));
        playerImageArea.setLayout(new MigLayout("insets 0, gap 0"));
        playerImageArea.add(lblPlayerImage, "w 100%-6px!, h 100%-23px!, wrap, gap 3 3 3 0");
        playerImageArea.add(lblLife, "w 100%!, h 20px!, wrap");

        // Player area hover effect
        playerImageArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(final MouseEvent e) {
                playerImagePanelHovered = true;
                playerImageArea.repaint();
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                playerImagePanelHovered = false;
                playerImageArea.repaint();
            }
        });

        tabletop.setBorder(new FSkin.MatteSkinBorder(0, 1, 0, 0, FSkin.getColor(FSkin.Colors.CLR_BORDERS)));
        tabletop.setOpaque(false);

        scroller.setViewportView(this.tabletop);

        updateDetails();
    }

    @Override
    public void populate() {
        final JPanel pnl = parentCell.getBody();
        pnl.setLayout(new MigLayout("insets 0, gap 0"));

        pnl.add(playerImageArea, "w 10%!, h 35%!");
        pnl.add(phaseIndicator, "w 5%!, h 100%!, span 1 2");
        pnl.add(scroller, "w 85%!, h 100%!, span 1 2, wrap");
        pnl.add(detailsPanel, "w 10%!, h 64%!, gapleft 1px");
    }

    @Override
    public EDocID getDocumentID() {
        return docID;
    }

    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    @Override
    public CField getLayoutControl() {
        return control;
    }

    @Override
    public void setParentCell(final DragCell cell0) {
        this.parentCell = cell0;
    }

    @Override
    public DragCell getParentCell() {
        return this.parentCell;
    }

    public PlayArea getTabletop() {
        return this.tabletop;
    }

    /**
     * @return the {@link PlayerView} this field belongs to; never {@code null}
     * for fields wired through the constructor (only the EDocID placeholder can
     * pass {@code null}, which we treat as unowned and skip animations for).
     */
    public PlayerView getPlayer() {
        return player;
    }

    public JPanel getPlayerImageArea() {
        return this.playerImageArea;
    }

    public PhaseIndicator getPhaseIndicator() {
        return phaseIndicator;
    }

    public PlayerDetailsPanel getDetailsPanel() {
        return detailsPanel;
    }

    private boolean isHighlighted() {
        return control.getMatchUI().isHighlighted(player);
    }

    /** Solid red frame once the player is picked, dashed frame while the player can be picked, none otherwise. */
    public void updatePlayerImageBorder() {
        final boolean highlighted = isHighlighted();
        playerImageArea.setBorder(highlighted ? borderPlayerImageHighlighted
                : control.getMatchUI().isSelectable(player) ? borderPlayerImageSelectable : borderPlayerImageSimple);
        playerImageArea.repaint();
    }

    public void setPlayerImage(final SkinImage playerImage) {
        lblPlayerImage.setIcon(playerImage);
        lblPlayerImage.getResizeTimer().start();
    }

    public void updateManaPool() {
        detailsPanel.updateManaPool();
    }
    public void updateZones() {
        detailsPanel.updateZones();
    }

    public void updateTabLabel() {
        if (player == null) {
            return;
        }
        final int delta = tabDiff.update(tabletop.getCardPanels().size(), tab);
        String label = Localizer.getInstance().getMessage("lblPlayField", player.getName());
        if (delta != 0) {
            label += " (" + delta + " new)";
        }
        tab.setText(label);
        tab.setToolTipText(label);
    }

    // ---- Cast lunge + glow pulse on battlefield entry ----------------------------------------
    private static final Color COLOR_CAST_ENTRY = new Color(180, 140, 255);
    private static final int CAST_LUNGE_PX = 18;
    private static final int CAST_LUNGE_DURATION_MILLIS = 320;

    /**
     * Plays the transient "cast lunge + glow pulse" cue for a card that just
     * entered this field's battlefield zone. Cosmetic only: the card's logical
     * bounds, layout, hit testing and game state are untouched, and no-op
     * happens when animation is off, the card has no panel on this field, or
     * the panel hasn't been sized yet.
     *
     * <p>Opponent-field panels lunge toward the field centre (positive Y) and
     * local-field panels lunge toward their opponent (negative Y), matching the
     * tabletop's mirrored top/bottom layout.</p>
     *
     * @param card the {@link CardView} that just entered the battlefield
     */
    public void dispatchCastEntry(final CardView card) {
        if (card == null || player == null || matchUI == null
                || UIAnimator.currentMode() == UIAnimator.Mode.OFF) {
            return;
        }
        final CardPanel panel = tabletop.getCardPanel(card.getId());
        if (panel == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            return;
        }
        final boolean opponentField = !matchUI.getLocalPlayers().contains(player);
        tweenCastLunge(panel, opponentField ? CAST_LUNGE_PX : -CAST_LUNGE_PX);
        matchUI.dispatchCardGlowAt(panel, COLOR_CAST_ENTRY);
    }

    private static void tweenCastLunge(final CardPanel panel, final int peakOffsetPx) {
        UIAnimator.startRaw(CAST_LUNGE_DURATION_MILLIS, "cast-lunge-" + System.identityHashCode(panel),
                progress -> {
                    final float eased;
                    if (progress <= 0.5f) {
                        eased = Easing.cubicOut(progress / 0.5f);
                    } else {
                        eased = 1f - Easing.cubicOut((progress - 0.5f) / 0.5f);
                    }
                    panel.setLungeOffsetY(Math.round(peakOffsetPx * eased));
                });
    }

    private void addLblTicket() {
        if (lblTicket.isShowing() || lblExperience.isShowing() || lblEnergy.isShowing() || lblPoison.isShowing()) {
            return; // experience, energy, poison take precedence
        }
        playerImageArea.remove(lblLife);
        lblLife.setIcon(FSkin.getImage(FSkinProp.ICO_QUEST_LIFE));
        playerImageArea.add(lblLife, "w 50%!, h 20px!, split 2");
        playerImageArea.add(lblTicket, "w 50%!, h 20px!, wrap");
    }

    private void removeLblTicket() {
        if (!lblTicket.isShowing()) {
            return;
        }
        playerImageArea.remove(lblTicket);
        playerImageArea.remove(lblLife);
        playerImageArea.add(lblLife, "w 100%!, h 20px!, wrap");
    }

    private void addLblRad() {
        if (lblRad.isShowing() || lblExperience.isShowing() || lblEnergy.isShowing() || lblPoison.isShowing()) {
            return;
        }
        playerImageArea.remove(lblLife);
        lblLife.setIcon(FSkin.getImage(FSkinProp.ICO_QUEST_LIFE));
        playerImageArea.add(lblLife, "w 50%!, h 20px!, split 2");
        playerImageArea.add(lblRad, "w 50%!, h 20px!, wrap");
    }

    private void removeLblRad() {
        if (!lblRad.isShowing()) {
            return;
        }
        playerImageArea.remove(lblRad);
        playerImageArea.remove(lblLife);
        playerImageArea.add(lblLife, "w 100%!, h 20px!, wrap");
    }

    private void addLblExperience() {
        if (lblExperience.isShowing() || lblEnergy.isShowing() || lblPoison.isShowing()) {
            return; // energy and poison take precedence
        }
        playerImageArea.remove(lblLife);
        lblLife.setIcon(FSkin.getImage(FSkinProp.ICO_QUEST_LIFE));
        playerImageArea.add(lblLife, "w 50%!, h 20px!, split 2");
        playerImageArea.add(lblExperience, "w 50%!, h 20px!, wrap");
    }

    private void removeLblExperience() {
        if (!lblExperience.isShowing()) {
            return;
        }
        playerImageArea.remove(lblExperience);
        playerImageArea.remove(lblLife);
        playerImageArea.add(lblLife, "w 100%!, h 20px!, wrap");
    }

    private void addLblEnergy() {
        if (lblEnergy.isShowing() || lblPoison.isShowing()) {
            return; // poison takes precedence
        }
        playerImageArea.remove(lblLife);
        lblLife.setIcon(FSkin.getImage(FSkinProp.ICO_QUEST_LIFE));
        playerImageArea.add(lblLife, "w 50%!, h 20px!, split 2");
        playerImageArea.add(lblEnergy, "w 50%!, h 20px!, wrap");
    }
    
    private void removeLblEnergy() {
        if (!lblEnergy.isShowing()) {
            return;
        }
        playerImageArea.remove(lblEnergy);
        playerImageArea.remove(lblLife);
        playerImageArea.add(lblLife, "w 100%!, h 20px!, wrap");
    }

    private void addLblPoison() {
        if (lblPoison.isShowing()) {
            return;
        }
        playerImageArea.remove(lblLife);
        lblLife.setIcon(FSkin.getImage(FSkinProp.ICO_QUEST_LIFE));
        playerImageArea.add(lblLife, "w 50%!, h 20px!, split 2");
        playerImageArea.add(lblPoison, "w 50%!, h 20px!, wrap");
    }
    private void removeLblPoison() {
        if (!lblPoison.isShowing()) {
            return;
        }
        playerImageArea.remove(lblPoison);
        playerImageArea.remove(lblLife);
        playerImageArea.add(lblLife, "w 100%!, h 20px!, wrap");
    }

    public void updateDetails() {
        // Update life total
        final int life = player.getLife();
        lblLife.setText(String.valueOf(life));
        if (life > LIFE_CRITICAL) {
            lblLife.setForeground(FSkin.getColor(FSkin.Colors.CLR_TEXT));
        } else {
            lblLife.setForeground(Color.RED);
        }

        // Update poison and/or energy counters, poison counters take precedence
        final int poison = player.getCounters(CounterEnumType.POISON);
        final int energy = player.getCounters(CounterEnumType.ENERGY);
        final int experience = player.getCounters(CounterEnumType.EXPERIENCE);
        final int rad = player.getCounters(CounterEnumType.RAD);
        final int ticket = player.getCounters(CounterEnumType.TICKET);

        if (poison > 0) {
            removeLblEnergy();
            removeLblExperience();
            removeLblRad();
            removeLblTicket();
            addLblPoison();
            lblPoison.setText(String.valueOf(poison));
            if (poison < POISON_CRITICAL) {
                lblPoison.setForeground(FSkin.getColor(FSkin.Colors.CLR_TEXT));
            } else {
                lblPoison.setForeground(Color.RED);
            }
        } else {
            removeLblPoison();
        }

        if (energy > 0) {
            removeLblExperience();
            removeLblRad();
            removeLblTicket();
            if (poison == 0) {
                addLblEnergy();
                lblEnergy.setText(String.valueOf(energy));
            }
        } else {
            removeLblEnergy();
        }

        if (experience > 0) {
            removeLblRad();
            removeLblTicket();
            if (poison == 0 && energy == 0) {
                addLblExperience();
                lblExperience.setText(String.valueOf(experience));
            }
        } else {
            removeLblExperience();
        }

        if (rad > 0) {
            removeLblTicket();
            if (poison == 0 && energy == 0 && experience == 0) {
                addLblRad();
                lblRad.setText(String.valueOf(rad));
            }
        } else {
            removeLblRad();
        }

        if (ticket > 0) {
            if (poison == 0 && energy == 0 && experience == 0 && rad == 0) {
                addLblTicket();
                lblTicket.setText(String.valueOf(ticket));
            }
        } else {
            removeLblTicket();
        }

        updatePlayerImageBorder();
        this.playerImageArea.setToolTipText(player.getDetailsHtml());
    }

}
