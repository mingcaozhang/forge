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

import java.awt.Point;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.gui.framework.IVDoc;
import forge.screens.match.CMatchUI;
import forge.screens.match.controllers.CHand;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import forge.view.arcane.CardPanel;
import forge.view.arcane.HandArea;
import forge.view.arcane.util.Easing;
import forge.view.arcane.util.UIAnimator;
import net.miginfocom.swing.MigLayout;

/**
 * Assembles Swing components of hand area.
 * 
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public class VHand implements IVDoc<CHand> {
    // Fields used with interface IVDoc
    private final CHand control;
    private DragCell parentCell;
    private final EDocID docID;
    private final DragTab tab = new DragTab(Localizer.getInstance().getMessage("lblYourHand"));
    private final PlayerView player;
    private final TabDiffTracker tabDiff = new TabDiffTracker();

    // Top-level containers
    private final FScrollPane scroller = new FScrollPane(false);
    private final HandArea hand;

    //========= Constructor
    /**
     * Assembles Swing components of a player hand instance.
     * 
     * @param id0 &emsp; {@link forge.gui.framework.EDocID}
     * @param p &emsp; {@link forge.game.player.Player}
     */
    public VHand(final CMatchUI matchUI, final EDocID id0, final PlayerView p) {
        this.hand = new HandArea(matchUI, scroller);
        this.player = p;
        docID = id0;
        id0.setDoc(this);

        if (p == null) {
            tab.setText("lblNoPlayerHand");
        } else {
            tab.setText(Localizer.getInstance().getMessage("lblPlayerHand", p.getName()));
        }

        scroller.setViewportView(VHand.this.hand);

        hand.setOpaque(false);

        control = new CHand(matchUI, p, this);
    }

    //========= Overridden methods

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#populate()
     */
    @Override
    public void populate() {
        final JPanel pnl = parentCell.getBody();
        pnl.setLayout(new MigLayout("insets 0, gap 0"));

        pnl.add(scroller, "w 100%, h 100%!");
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getDocumentID()
     */
    @Override
    public EDocID getDocumentID() {
        return docID;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#setParentCell()
     */
    @Override
    public void setParentCell(final DragCell cell0) {
        this.parentCell = cell0;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getParentCell()
     */
    @Override
    public DragCell getParentCell() {
        return this.parentCell;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getTabLabel()
     */
    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getLayoutControl()
     */
    @Override
    public CHand getLayoutControl() {
        return control;
    }

    //========= Retrieval methods
    /**
     * Gets the hand area.
     *
     * @return {@link forge.view.arcane.HandArea}
     */
    public HandArea getHandArea() {
        return VHand.this.hand;
    }

    public PlayerView getPlayer() {
        return player;
    }

    public void updateTabLabel(final int count) {
        if (player == null) return;
        final int delta = tabDiff.update(count, tab);
        String label = Localizer.getInstance().getMessage("lblPlayerHand", player.getName());
        if (delta != 0) {
            label += " (" + delta + " new)";
        }
        tab.setText(label);
        tab.setToolTipText(label);
    }

    private static final int DRAW_ARC_DURATION_MILLIS = 360;
    private static final float DRAW_ARC_START_SCALE = 0.78f;
    private static final float DRAW_ARC_BOW_RATIO = 0.35f;

    private static final int TAP_PULSE_DURATION_MILLIS = 220;
    private static final float TAP_PULSE_PEAK = 0.6f;

    /**
     * Plays an Arena-style draw arc on the hand panel for the given card: the
     * card sweeps in along a shallow curve from the hand's leading edge while
     * easing up to its full size. Purely paint-only — layout, hit-testing and
     * drag state for the panel are unaffected. No-op when animation is off, the
     * player is unknown, or the card isn't currently shown in this hand.
     *
     * @param card the {@link CardView} just drawn into this hand
     */
    public void dispatchDrawArc(final CardView card) {
        if (card == null || UIAnimator.currentMode() == UIAnimator.Mode.OFF) {
            return;
        }
        final CardPanel panel = hand.getCardPanel(card.getId());
        if (panel == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            return;
        }
        final Point start = computeDrawArcStart(panel);
        tweenDrawArc(panel, start);
    }

    /**
     * Plays a short scale "pop" over the given card when it is tapped or
     * untapped. Purely paint-only; no-op when animation is off or the card
     * isn't currently shown in this hand.
     *
     * @param card the {@link CardView} whose tapped state just changed
     */
    public void dispatchTapPulse(final CardView card) {
        if (card == null || UIAnimator.currentMode() == UIAnimator.Mode.OFF) {
            return;
        }
        final CardPanel panel = hand.getCardPanel(card.getId());
        if (panel == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            return;
        }
        tweenTapPulse(panel);
    }

    private Point computeDrawArcStart(final CardPanel panel) {
        final Point panelTopLeft = SwingUtilities.convertPoint(panel, 0, 0, hand);
        final int handWidth = hand.getWidth();
        final int handHeight = hand.getHeight();
        final int startX;
        final int startY;
        if (hand.isVertical()) {
            startX = panelTopLeft.x + handWidth / 4;
            startY = panelTopLeft.y + handHeight + panel.getHeight();
        } else {
            startX = panelTopLeft.x - handWidth / 4 - panel.getWidth();
            startY = panelTopLeft.y + handHeight + panel.getHeight() / 4;
        }
        return new Point(startX - panelTopLeft.x, startY - panelTopLeft.y);
    }

    private static void tweenDrawArc(final CardPanel panel, final Point startOffset) {
        final int w = panel.getWidth();
        final int h = panel.getHeight();
        final int peakX = startOffset.x;
        final int peakY = startOffset.y;
        final int bowX = (int) (peakY * DRAW_ARC_BOW_RATIO) + (peakY > 0 ? -1 : 1) * (w / 2);
        final int bowY = (int) (peakY / 2f);
        UIAnimator.startRaw(DRAW_ARC_DURATION_MILLIS, "hand-draw-arc-" + System.identityHashCode(panel),
                progress -> {
                    final float eased = Easing.cubicOut(progress);
                    final float inv = 1f - eased;
                    final float bx = inv * inv * peakX + 2f * inv * eased * bowX + eased * eased * 0f;
                    final float by = inv * inv * peakY + 2f * inv * eased * bowY + eased * eased * 0f;
                    panel.setDrawArcOffsetX(bx);
                    panel.setDrawArcOffsetY(by);
                    panel.setDrawArcScale(DRAW_ARC_START_SCALE + (1f - DRAW_ARC_START_SCALE) * eased);
                });
    }

    private static void tweenTapPulse(final CardPanel panel) {
        UIAnimator.startRaw(TAP_PULSE_DURATION_MILLIS, "hand-tap-pulse-" + System.identityHashCode(panel),
                progress -> {
                    final float eased;
                    if (progress <= 0.5f) {
                        eased = Easing.cubicOut(progress / 0.5f);
                    } else {
                        eased = 1f - Easing.cubicOut((progress - 0.5f) / 0.5f);
                    }
                    panel.setTapPulse(TAP_PULSE_PEAK * eased);
                });
    }

}
