/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Nate
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
package forge.view.arcane;

import java.awt.event.MouseEvent;
import java.util.List;

import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.CMatchUI;
import forge.toolbox.FScrollPane;
import forge.toolbox.MouseTriggerEvent;
import forge.view.arcane.util.CardPanelMouseAdapter;
import forge.view.arcane.util.UIAnimator;

/**
 * <p>
 * HandArea class.
 * </p>
 * 
 * @author Forge
 * @version $Id: HandArea.java 24769 2014-02-09 13:56:04Z Hellfish $
 */
public class HandArea extends CardArea {
    /** Constant <code>serialVersionUID=7488132628637407745L</code>. */
    private static final long serialVersionUID = 7488132628637407745L;

    /** Falloff of the fan bump around the hovered card, in card-index units. */
    private static final double FAN_SIGMA = 1.8;
    /** Per-frame tween duration for a single card's lift, before mode scaling. */
    private static final int FAN_ANIM_MILLIS = 180;

    private CardPanel hoveredFanPanel;

    /**
     * <p>
     * Constructor for HandArea.
     * </p>
     * TODO Make compatible with WindowBuilder
     * 
     * @param scrollPane
     */
    public HandArea(final CMatchUI matchUI, final FScrollPane scrollPane) {
        super(matchUI, scrollPane);

        this.setDragEnabled(true);
        this.setVertical(true);
        this.setMaxCardsPerRow(FModel.getPreferences().getPrefInt(FPref.UI_HAND_MAX_CARDS_PER_ROW));
        this.setNoOverlap(FModel.getPreferences().getPrefBoolean(FPref.UI_HAND_NO_OVERLAP));

        // mouseOut is final on CardPanelContainer, so the lift reset is driven
        // through the listener list rather than an override.
        this.addCardPanelMouseListener(new CardPanelMouseAdapter() {
            @Override
            public void mouseOut(final CardPanel panel, final MouseEvent evt) {
                clearFan();
            }
        });
    }

    @Override
    protected boolean cardPanelDraggable(final CardPanel panel) {
        return panel.getCard() != null;
    }

    @Override
    public void doLayout() {
        // Every panel in a hand reserves headroom above the card so the hovered
        // lift/scale is never clipped; enabling before super guarantees setCardBounds
        // picks it up on this very pass.
        for (final CardPanel panel : this.getCardPanels()) {
            panel.setHandLiftEnabled(true);
        }
        // A layout means the hover state may have shifted (cards added/removed, a
        // drag reorder), so collapse the fan and cancel any in-flight lift tweens.
        this.resetFanLifts();
        super.doLayout();
        if (this.hoveredFanPanel != null && !this.isDragged()
                && this.getCardPanels().contains(this.hoveredFanPanel)) {
            this.setComponentZOrder(this.hoveredFanPanel, 0);
        }
        this.repaint();
    }

    /** {@inheritDoc} */
    @Override
    public final void mouseOver(final CardPanel panel, final MouseEvent evt) {
        getMatchUI().setCard(panel.getCard(), evt.isShiftDown());
        super.mouseOver(panel, evt);
        applyFan(panel);
    }

    /** {@inheritDoc} */
    @Override
    public final void mouseLeftClicked(final CardPanel panel, final MouseEvent evt) {
        getMatchUI().getGameController().selectCard(panel.getCard(), null, new MouseTriggerEvent(evt));
        super.mouseLeftClicked(panel, evt);
    }

    /** {@inheritDoc} */
    @Override
    public final void mouseRightClicked(final CardPanel panel, final MouseEvent evt) {
        getMatchUI().getGameController().selectCard(panel.getCard(), null, new MouseTriggerEvent(evt));
        super.mouseRightClicked(panel, evt);
    }

    private void applyFan(final CardPanel hovered) {
        final List<CardPanel> panels = this.getCardPanels();
        final int hoveredIndex = panels.indexOf(hovered);
        if (hoveredIndex < 0) {
            return;
        }
        this.hoveredFanPanel = hovered;
        for (int i = 0; i < panels.size(); i++) {
            final CardPanel panel = panels.get(i);
            final float distance = i - hoveredIndex;
            final float target = panel == hovered ? 1f
                    : (float) Math.exp(-(distance * distance) / (2.0 * FAN_SIGMA * FAN_SIGMA));
            startFanTween(panel, target);
        }
        // Bring the hovered card to the front so its lift/scale is not hidden by
        // the cards it overlaps.
        this.setComponentZOrder(hovered, 0);
        this.repaint();
    }

    private void clearFan() {
        this.hoveredFanPanel = null;
        for (final CardPanel panel : this.getCardPanels()) {
            startFanTween(panel, 0f);
        }
        this.restoreHandZOrder();
        this.repaint();
    }

    private void startFanTween(final CardPanel panel, final float target) {
        final float from = panel.getVisualLift();
        if (from == target) {
            return;
        }
        UIAnimator.start(FAN_ANIM_MILLIS, new FanKey(panel), new UIAnimator.Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                panel.setVisualLift(from + (target - from) * easedProgress);
                HandArea.this.repaint();
            }

            @Override
            public void onEnd() {
                panel.setVisualLift(target);
                HandArea.this.repaint();
            }
        });
    }

    private void resetFanLifts() {
        for (final CardPanel panel : this.getCardPanels()) {
            UIAnimator.cancel(new FanKey(panel));
            if (panel.getVisualLift() != 0f) {
                panel.setVisualLift(0f);
            }
        }
    }

    /**
     * Restores the vertical-hand z-order (later cards drawn over earlier ones) that
     * {@link CardArea#doLayout()} establishes, undoing the bring-to-front applied to
     * the hovered card.
     */
    private void restoreHandZOrder() {
        final List<CardPanel> panels = this.getCardPanels();
        int zOrder = panels.size() - 1;
        final int maxZOrder = this.getComponentCount() - 1;
        if (zOrder > maxZOrder) {
            zOrder = maxZOrder;
        }
        for (final CardPanel panel : panels) {
            this.setComponentZOrder(panel, zOrder);
            if (zOrder > 0) {
                zOrder--;
            }
        }
    }

    /**
     * Tween key distinct from the {@link CardPanel} object used by the hover-glow
     * tween in {@link CardPanelContainer}, so the fan/lift and the glow can animate
     * the same panel independently.
     */
    private static final class FanKey {
        private final CardPanel panel;

        FanKey(final CardPanel panel) {
            this.panel = panel;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof FanKey && ((FanKey) other).panel == this.panel;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.panel);
        }
    }
}
