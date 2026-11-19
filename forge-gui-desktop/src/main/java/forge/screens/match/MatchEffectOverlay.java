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
package forge.screens.match;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.plaf.LayerUI;

import forge.Singletons;
import forge.view.FView;
import forge.view.arcane.effects.ArcaneEffect;
import forge.view.arcane.util.UIAnimator;

/**
 * <p>
 * MatchEffectOverlay class. The match window's shared, transparent effect layer:
 * a non-opaque {@link JLayer} installed over the match screen that paints every
 * currently active {@link ArcaneEffect} (hit flashes, damage popups, cast glows)
 * on top of the match content.
 * </p>
 *
 * <p>
 * The layer itself is {@code final} in the JDK, so it cannot override hit
 * testing to stay out of the way. Instead it sits inside a transparent,
 * click-through host panel whose {@code contains} is always {@code false}, which
 * makes every mouse hit fall through to the cards and buttons below while the
 * {@link JLayer} still fills the window and paints above them. Neither component
 * takes part in layout (both live in the document's {@link JLayeredPane}) and
 * neither takes focus.
 * </p>
 *
 * <p>
 * Painting is skipped entirely while the overlay is not showing and while
 * {@code FPref.UI_ANIMATION_MODE} is {@code Off}, so a disabled animation setting
 * leaves the layer painting nothing at all. Attaching and detaching are both
 * idempotent, so a screen re-populate never stacks a second copy and a close path
 * may call {@link #detach()} freely.
 * </p>
 *
 * @author Forge
 */
public final class MatchEffectOverlay {

    private final JLayer<JComponent> layer;
    private final ClickThroughPane host = new ClickThroughPane();
    private final LayerResizer resizer = new LayerResizer();

    private boolean attached;

    MatchEffectOverlay() {
        this.layer = new JLayer<>(new TransparentView(), new EffectLayerUI());
        this.layer.setOpaque(false);
        this.layer.setFocusable(false);
        this.layer.setRequestFocusEnabled(false);
        this.host.setOpaque(false);
        this.host.setLayout(null);
        this.host.add(this.layer);
    }

    /**
     * Places the overlay over the match window. A no-op if it is already
     * attached; safe to call on every screen populate.
     */
    public void attach() {
        if (attached) {
            return;
        }
        attached = true;
        final JLayeredPane lpn = Singletons.getView().getLpnDocument();
        // Top of the targeting layer, so effects paint over the match and its arcs.
        lpn.add(host, FView.TARGETING_LAYER, 0);
        lpn.addComponentListener(resizer);
        fitToWindow();
    }

    /**
     * Takes the overlay off the match window; safe to call more than once.
     */
    public void detach() {
        if (!attached) {
            return;
        }
        attached = false;
        final JLayeredPane lpn = Singletons.getView().getLpnDocument();
        lpn.removeComponentListener(resizer);
        lpn.remove(host);
        lpn.repaint();
    }

    /**
     * @return the component effects should request repaints on, i.e. the
     *         {@link JLayer} that paints them
     */
    public JComponent getPaintTarget() {
        return layer;
    }

    /** @return the overlay's {@link JLayer} */
    public JLayer<JComponent> getLayer() {
        return layer;
    }

    /** @return {@code true} while the overlay is installed in the match window */
    public boolean isAttached() {
        return attached;
    }

    private void fitToWindow() {
        final JLayeredPane lpn = Singletons.getView().getLpnDocument();
        host.setBounds(lpn.getBounds());
        layer.setBounds(host.getBounds());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Inner classes
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Keeps the overlay covering the whole window when it is resized.
     */
    private final class LayerResizer extends ComponentAdapter {
        @Override
        public void componentResized(final ComponentEvent e) {
            fitToWindow();
        }
    }

    /**
     * Host of the {@link JLayer}. It is fully transparent and never claims a
     * mouse hit, so clicks and hovers fall through to whatever sits below it in
     * the layered pane.
     */
    private static final class ClickThroughPane extends JPanel {
        private static final long serialVersionUID = 1L;

        ClickThroughPane() {
            setOpaque(false);
            setLayout(null);
        }

        @Override
        public boolean contains(final int x, final int y) {
            return false;
        }
    }

    /**
     * Empty, transparent view of the {@link JLayer}. It exists only so the layer
     * has something to paint beneath {@link EffectLayerUI}; it holds no children
     * and contributes nothing visually, which keeps the overlay fully transparent.
     */
    private static final class TransparentView extends JPanel {
        private static final long serialVersionUID = 1L;

        TransparentView() {
            setOpaque(false);
            setLayout(null);
        }
    }

    /**
     * Paints the active effects over the (transparent) view. {@link LayerUI#paint}
     * is the hook {@code JLayer} hands its own painting to, so calling
     * {@code super.paint} first keeps the view beneath the effects.
     */
    private static final class EffectLayerUI extends LayerUI<JComponent> {
        private static final long serialVersionUID = 1L;

        @Override
        public void paint(final Graphics g, final JComponent c) {
            super.paint(g, c);
            if (!c.isShowing() || UIAnimator.currentMode() == UIAnimator.Mode.OFF) {
                return;
            }
            if (ArcaneEffect.activeCount() == 0) {
                return;
            }
            final Graphics2D g2d = (Graphics2D) g.create();
            try {
                for (final ArcaneEffect effect : ArcaneEffect.activeEffects()) {
                    effect.paintOverlay(g2d);
                }
            } finally {
                g2d.dispose();
            }
        }
    }
}
