package forge.screens.deckeditor.views;

import java.awt.BorderLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.JPanel;

import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;

/**
 * Rounded, subtly gradient content card painted behind a single deck-editor
 * browser component (e.g. the constructed-decks or card-catalog manager).
 * It behaves exactly like the plain {@link JPanel} it wraps content in — same
 * fill placement, sizing and component hit-testing — and only adds a soft
 * sunken "well" with a rounded border so the browser panes read as modern
 * content cards. Purely visual: no layout, filter, or deck logic is involved
 * and the wrapped component keeps its exact placement.
 */
final class RoundedContentCard extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int ARC = 12;
    private static final int PAD = 1;

    /**
     * Wraps the given content so it fills the card (BorderLayout CENTER),
     * leaving the rounded gradient chrome visible around it.
     *
     * @param content0 the component to display inside the card
     */
    RoundedContentCard(final JComponent content0) {
        super(new BorderLayout());
        setOpaque(false);
        add(content0, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= PAD * 2 || h <= PAD * 2) {
            return;
        }
        final Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final SkinColor theme = FSkin.getColor(FSkin.Colors.CLR_THEME);
            g2d.setPaint(new GradientPaint(0f, PAD, theme.stepColor(-8).getColor(),
                    0f, h - PAD, theme.stepColor(-16).getColor()));
            g2d.fillRoundRect(PAD, PAD, w - PAD * 2, h - PAD * 2, ARC, ARC);
            FSkin.setGraphicsColor(g2d,
                    FSkin.getColor(FSkin.Colors.CLR_BORDERS).getContrastColor(20).alphaColor(180));
            g2d.drawRoundRect(PAD, PAD, w - PAD * 2, h - PAD * 2, ARC, ARC);
        } finally {
            g2d.dispose();
        }
    }
}
