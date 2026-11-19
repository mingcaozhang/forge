package forge.screens.deckeditor.views;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinImage;

/**
 * Paints a small set of horizontal rounded "progress" bars — one per supplied
 * skin icon — where each bar length is proportional to its value and filled
 * with the sampled color of its icon. Used by the deck editor statistics tab
 * for the color-identity and card-type distributions.
 *
 * <br><br><i>(Purely a view/painting component — it stores only display data and
 * performs no deck or game logic.)</i>
 */
final class DistributionBarChart extends JComponent {
    private static final long serialVersionUID = 1L;

    private static final int PAD = 10;
    private static final int ICON = 18;
    private static final int ROW = 26;
    private static final int ROW_GAP = 8;
    private static final int VALUE_W = 64;
    private static final int ICON_GAP = 8;
    private static final int ARC = 10;

    private final FSkinProp[] props;
    private final SkinImage[] icons;
    private final Color[] fills;
    private final int[] values;
    private String[] valueTexts;
    private int max = 1;

    DistributionBarChart(final FSkinProp[] props0) {
        this.props = props0;
        this.icons = new SkinImage[props0.length];
        this.fills = new Color[props0.length];
        this.values = new int[props0.length];
        this.valueTexts = new String[props0.length];
        final int iconSize = Math.round(ICON * forge.gui.GuiBase.getInterface().getScreenScale());
        for (int i = 0; i < props0.length; i++) {
            this.icons[i] = FSkin.getImage(props0[i], iconSize, iconSize);
            this.fills[i] = ManaPalette.colorFor(props0[i]);
        }
        setOpaque(false);
    }

    /**
     * Pushes new display data.
     *
     * @param values0    count for each row (same length as the constructor props)
     * @param valueTexts0 per-row text drawn to the right of the bar, or {@code null}
     */
    void update(final int[] values0, final String[] valueTexts0) {
        System.arraycopy(values0, 0, this.values, 0, this.values.length);
        if (valueTexts0 != null) {
            System.arraycopy(valueTexts0, 0, this.valueTexts, 0, this.valueTexts.length);
        }
        int maxTmp = 1;
        for (final int v : this.values) {
            if (v > maxTmp) {
                maxTmp = v;
            }
        }
        this.max = maxTmp;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(240, PAD * 2 + props.length * ROW + (props.length - 1) * ROW_GAP);
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final int w = getWidth();
        if (w <= PAD * 2 || props.length == 0) {
            return;
        }
        final Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            final Color textColor = FSkin.getColor(FSkin.Colors.CLR_TEXT).getColor();
            final Font base = getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : getFont();
            final Font font = base.deriveFont(Font.BOLD, 11f);
            g2d.setFont(font);
            final FontMetrics fm = g2d.getFontMetrics();
            final int trackX = PAD + ICON + ICON_GAP;
            final int trackW = Math.max(1, w - PAD - trackX - VALUE_W);

            int y = PAD;
            for (int i = 0; i < props.length; i++) {
                final int barY = y + (ROW - ICON) / 2;
                // track (background well)
                FSkin.setGraphicsColor(g2d, FSkin.getColor(FSkin.Colors.CLR_THEME).stepColor(-18));
                g2d.fillRoundRect(trackX, barY, trackW, ICON, ARC, ARC);
                // filled portion scaled by value
                final int fillW = this.max > 0 ? (int) Math.round((double) trackW * values[i] / max) : 0;
                if (fillW > 0) {
                    final Color fill = fills[i];
                    g2d.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 215));
                    g2d.fillRoundRect(trackX, barY, fillW, ICON, ARC, ARC);
                    g2d.setColor(fill.darker());
                    g2d.drawRoundRect(trackX, barY, fillW, ICON, ARC, ARC);
                }
                // icon at the left of the bar
                FSkin.drawImage(g2d, icons[i], PAD, y + (ROW - ICON) / 2, ICON, ICON);
                // value text right-aligned in its own gutter
                if (valueTexts[i] != null) {
                    g2d.setColor(textColor);
                    final String text = valueTexts[i];
                    final int tw = fm.stringWidth(text);
                    g2d.drawString(text, w - PAD - tw, y + (ROW + fm.getAscent()) / 2 - 1);
                }
                y += ROW + ROW_GAP;
            }
        } finally {
            g2d.dispose();
        }
    }
}
