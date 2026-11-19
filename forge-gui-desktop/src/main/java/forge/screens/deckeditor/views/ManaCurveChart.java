package forge.screens.deckeditor.views;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

import forge.gui.GuiBase;
import forge.itemmanager.SItemManagerUtil.StatTypes;
import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinImage;

/**
 * Paints a deck's mana curve as a row of rounded vertical bars — one per mana
 * value bucket (0, 1, 2, 3, 4, 5, 6+) — where each bar's height is proportional
 * to the number of cards at that mana value and the bar is stacked/colored by
 * the colored mana symbols (W U B R G colorless) inside those cards' costs.
 *
 * <br><br><i>(Purely a view/painting component — it stores only display data and
 * performs no deck or game logic.)</i>
 */
final class ManaCurveChart extends JComponent {
    private static final long serialVersionUID = 1L;

    private static final int BUCKETS = 7;
    private static final int SHARDS = 6; // W U B R G colorless
    private static final int PAD = 10;
    private static final int NUM_H = 16;
    private static final int SYMBOL_H = 20;
    private static final int ARC = 10;
    private static final int MIN_BAR_H = 3;

    // Mana symbol order matches ManaCost.getColorShardCounts(): W U B R G C.
    private static final FSkinProp[] SHARD_PROPS = {
            StatTypes.WHITE.skinProp, StatTypes.BLUE.skinProp, StatTypes.BLACK.skinProp,
            StatTypes.RED.skinProp, StatTypes.GREEN.skinProp, StatTypes.COLORLESS.skinProp };
    private static final FSkinProp[] CMC_PROPS = {
            StatTypes.CMC_0.skinProp, StatTypes.CMC_1.skinProp, StatTypes.CMC_2.skinProp,
            StatTypes.CMC_3.skinProp, StatTypes.CMC_4.skinProp, StatTypes.CMC_5.skinProp,
            StatTypes.CMC_6.skinProp };

    private final SkinImage[] cmcIcons = new SkinImage[BUCKETS];
    private final Color[] shardColors = new Color[SHARDS];
    private final int[] counts = new int[BUCKETS];
    private final int[][] shards = new int[BUCKETS][SHARDS];
    private int max = 1;

    ManaCurveChart() {
        final int iconSize = Math.round(SYMBOL_H * GuiBase.getInterface().getScreenScale());
        for (int i = 0; i < BUCKETS; i++) {
            this.cmcIcons[i] = FSkin.getImage(CMC_PROPS[i], iconSize, iconSize);
        }
        for (int i = 0; i < SHARDS; i++) {
            this.shardColors[i] = ManaPalette.colorFor(SHARD_PROPS[i]);
        }
        setOpaque(false);
    }

    /**
     * Pushes new display data.
     *
     * @param counts0 per-bucket card counts, length {@value #BUCKETS}
     * @param shards0 per-bucket colored mana symbol totals, {@code [bucket][W U B R G C]}
     */
    void update(final int[] counts0, final int[][] shards0) {
        System.arraycopy(counts0, 0, this.counts, 0, BUCKETS);
        for (int i = 0; i < BUCKETS; i++) {
            System.arraycopy(shards0[i], 0, this.shards[i], 0, SHARDS);
        }
        int maxTmp = 1;
        for (final int c : this.counts) {
            if (c > maxTmp) {
                maxTmp = c;
            }
        }
        this.max = maxTmp;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(240, PAD * 2 + NUM_H + SYMBOL_H + 150);
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
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            final Color textColor = FSkin.getColor(FSkin.Colors.CLR_TEXT).getColor();
            final Color track = FSkin.getColor(FSkin.Colors.CLR_THEME).stepColor(-16).getColor();
            final Color axisColor = FSkin.getColor(FSkin.Colors.CLR_BORDERS).getContrastColor(20).getColor();

            final Font base = getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : getFont();
            final Font numFont = base.deriveFont(Font.BOLD, 11f);
            g2d.setFont(numFont);
            final FontMetrics fm = g2d.getFontMetrics();

            final int slotW = (w - PAD * 2) / BUCKETS;
            final int barW = Math.max(6, Math.min(slotW - 10, 34));
            final int top = PAD + NUM_H;
            final int baseline = h - PAD - SYMBOL_H;
            final int barAreaH = Math.max(1, baseline - top);

            // axis line
            g2d.setColor(axisColor);
            g2d.drawLine(PAD, baseline, w - PAD, baseline);

            for (int i = 0; i < BUCKETS; i++) {
                final int slotX = PAD + i * slotW;
                final int barX = slotX + (slotW - barW) / 2;

                // empty slot guide
                g2d.setColor(new Color(track.getRed(), track.getGreen(), track.getBlue(), 90));
                g2d.fillRoundRect(barX, baseline - barAreaH, barW, barAreaH, ARC, ARC);

                final int hgt = (int) Math.round((double) barAreaH * counts[i] / max);
                if (hgt > 0) {
                    paintStackedBar(g2d, i, barX, baseline - hgt, barW, hgt);
                } else {
                    g2d.setColor(new Color(axisColor.getRed(), axisColor.getGreen(), axisColor.getBlue(), 70));
                    g2d.drawRoundRect(barX, baseline - MIN_BAR_H, barW, MIN_BAR_H, ARC, ARC);
                }

                // count above the bar (or just above the baseline when empty)
                final String text = Integer.toString(counts[i]);
                final int textY = baseline - Math.max(hgt, MIN_BAR_H) - 4;
                g2d.setColor(textColor);
                g2d.drawString(text, barX + (barW - fm.stringWidth(text)) / 2,
                        Math.max(top + fm.getAscent(), textY));

                // mana value symbol below the bar
                FSkin.drawImage(g2d, cmcIcons[i],
                        barX + (barW - SYMBOL_H) / 2, baseline + 3, SYMBOL_H, SYMBOL_H);
            }
        } finally {
            g2d.dispose();
        }
    }

    private void paintStackedBar(final Graphics2D g2d, final int bucket, final int x, final int y,
            final int width, final int height) {
        final Shape clip = new RoundRectangle2D.Float(x, y, width, height, ARC, ARC);
        final Shape oldClip = g2d.getClip();
        g2d.clip(clip);

        int total = 0;
        for (int s = 0; s < SHARDS; s++) {
            total += shards[bucket][s];
        }
        if (total > 0) {
            int offset = height;
            for (int s = 0; s < SHARDS; s++) {
                final int amount = shards[bucket][s];
                if (amount <= 0) {
                    continue;
                }
                final int segH = (int) Math.round((double) amount / total * height);
                if (segH <= 0) {
                    continue;
                }
                final Color fill = shardColors[s];
                g2d.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 225));
                offset -= segH;
                g2d.fillRect(x, y + offset, width, segH);
            }
        } else {
            final Color fill = shardColors[SHARDS - 1]; // colorless for costless / zero-symbol cards
            g2d.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 150));
            g2d.fillRect(x, y, width, height);
        }

        g2d.setClip(oldClip);
        g2d.setColor(shardColors[SHARDS - 1].darker());
        g2d.draw(clip);
    }
}
