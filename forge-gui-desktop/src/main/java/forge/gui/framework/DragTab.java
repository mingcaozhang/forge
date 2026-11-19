package forge.gui.framework;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

import javax.swing.border.EmptyBorder;

import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinnedLabel;

/**
 * The tab label object in drag layout.
 * No modification should be necessary to this object.
 * Simply call the constructor with a title string argument.
 */
@SuppressWarnings("serial")
public final class DragTab extends SkinnedLabel implements ILocalRepaint {
    private boolean selected = false;
    private boolean modernChrome = false;
    private int priority = 10;
    private float flashIntensity = 0f;
    private float bumpScale = 1f;

    /**
     * The tab label object in drag layout.
     * No modification should be necessary to this object.
     * Simply call the constructor with a title string argument.
     * 
     * @param title0 &emsp; {java.lang.String}
     */
    public DragTab(final String title0) {
        super(title0);
        setToolTipText(title0);
        setOpaque(false);
        setSelected(false);
        setBorder(new EmptyBorder(2, 5, 2, 5));
        this.setForeground(FSkin.getColor(FSkin.Colors.CLR_TEXT));

        this.addMouseListener(SRearrangingUtil.getRearrangeClickEvent());
        this.addMouseMotionListener(SRearrangingUtil.getRearrangeDragEvent());
    }

    /** @param isSelected0 &emsp; boolean */
    public void setSelected(final boolean isSelected0) {
        selected = isSelected0;
        repaintSelf();
    }

    /**
     * Enables the modern (deck editor) tab chrome. Opt-in only: when false the
     * tab paints exactly as before, so shared match-screen chrome is unaffected.
     *
     * @param modernChrome0 whether to render the modern flat/gradient tab look
     */
    public void setModernChrome(final boolean modernChrome0) {
        modernChrome = modernChrome0;
        repaintSelf();
    }

    /** True when this is the active tab in its DragCell. */
    public boolean isSelected() {
        return selected;
    }

    /** Decreases display priority of this tab in relation to its siblings in an overflow case. */
    public void priorityDecrease() {
        priority++;
    }

    /** Sets this tab as first to be displayed if siblings overflow. */
    public void priorityOne() {
        priority = 1;
    }

    /**
     * Returns display priority of this tab in relation to its siblings in an overflow case.
     * @return int
     */
    public int getPriority() {
        return priority;
    }

    // There should be no need for this method.
    @SuppressWarnings("unused")
    private void setPriority() {
        // Intentionally empty.
    }

    /** Sets the red flash overlay intensity (0..1) and repaints. */
    public void setFlashIntensity(final float intensity) {
        flashIntensity = Math.max(0f, Math.min(1f, intensity));
        repaintSelf();
    }

    /**
     * Sets the paint-only scale multiplier applied to the tab label when
     * repainting (1.0 renders at native size). Driven by the counter-bump
     * animation; does not affect layout, preferred size, or hit-testing.
     *
     * @param scale scale multiplier; values outside {@code [0.5, 2.0]} are clamped
     */
    public void setBumpScale(final float scale) {
        bumpScale = Math.max(0.5f, Math.min(2.0f, scale));
        repaintSelf();
    }

    @Override
    public void repaintSelf() {
        final Dimension d = DragTab.this.getSize();
        repaint(0, 0, d.width, d.height);
    }

    @Override
    public void paintComponent(final Graphics g) {
        final int w = getWidth() - 1;
        final int h = getHeight() * 2;

        if (modernChrome) {
            final Graphics2D g2 = (Graphics2D) g;
            if (selected) {
                final Color top = FSkin.getColor(FSkin.Colors.CLR_ACTIVE).brighter().getColor();
                final Color bottom = FSkin.getColor(FSkin.Colors.CLR_ACTIVE).getColor();
                g2.setPaint(new GradientPaint(0f, 0f, top, 0f, h * 0.6f, bottom));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(FSkin.getColor(FSkin.Colors.CLR_BORDERS).getContrastColor(30).getColor());
                g2.drawRoundRect(0, 0, w, h, 8, 8);
            }
            else {
                g2.setColor(FSkin.getColor(FSkin.Colors.CLR_ACTIVE).alphaColor(40).getColor());
                g2.fillRoundRect(0, 0, w, h, 8, 8);
            }
        }
        else if (!selected) {
            FSkin.setGraphicsColor(g, FSkin.getColor(FSkin.Colors.CLR_INACTIVE));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() * 2, 6, 6);
            FSkin.setGraphicsColor(g, FSkin.getColor(FSkin.Colors.CLR_BORDERS));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() * 2, 6, 6);
        }
        else {
            FSkin.setGraphicsColor(g, FSkin.getColor(FSkin.Colors.CLR_ACTIVE));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() * 2, 6, 6);
            FSkin.setGraphicsColor(g, FSkin.getColor(FSkin.Colors.CLR_BORDERS));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() * 2, 6, 6);
        }

        if (flashIntensity > 0f) {
            g.setColor(new Color(1f, 0f, 0f, flashIntensity));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() * 2, 6, 6);
        }

        if (bumpScale != 1f) {
            final Graphics2D g2 = (Graphics2D) g;
            final AffineTransform original = g2.getTransform();
            final Object originalHint =
                    g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final int cx = getWidth() / 2;
            final int cy = getHeight() / 2;
            g2.translate(cx, cy);
            g2.scale(bumpScale, bumpScale);
            g2.translate(-cx, -cy);
            super.paintComponent(g);
            g2.setTransform(original);
            if (originalHint != null) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, originalHint);
            }
        } else {
            super.paintComponent(g);
        }
    }
}
