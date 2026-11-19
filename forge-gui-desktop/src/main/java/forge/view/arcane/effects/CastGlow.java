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
package forge.view.arcane.effects;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * <p>
 * CastGlow class. A colored glow that pulses outward from a rectangular card
 * region and fades back away — the "lunge" halo shown when a spell or creature
 * is cast/enters the battlefield. Intensity peaks at the midpoint of the
 * animation, so the effect begins and ends fully transparent.
 * </p>
 *
 * @author Forge
 */
public final class CastGlow extends ArcaneEffect {

    /** Nominal duration in milliseconds. */
    public static final int DEFAULT_DURATION_MILLIS = 600;
    /** Number of concentric rings drawn to fake a soft glow. */
    private static final int RING_COUNT = 4;
    /** Outward travel of the outermost ring, in pixels, at peak intensity. */
    private static final int MAX_EXPAND_PX = 12;
    /** Peak opacity of the innermost ring. */
    private static final float PEAK_OPACITY = 0.7f;

    private final Rectangle bounds;
    private final Color glowColor;

    /**
     * @param target component whose repaints are requested while animating
     * @param bounds region (in overlay coordinates) to glow around
     * @param color  glow color
     */
    public CastGlow(final Component target, final Rectangle bounds, final Color color) {
        this(target, bounds, color, DEFAULT_DURATION_MILLIS);
    }

    /**
     * @param target         component whose repaints are requested while animating
     * @param bounds         region (in overlay coordinates) to glow around
     * @param color          glow color
     * @param durationMillis animation duration in milliseconds
     */
    public CastGlow(final Component target, final Rectangle bounds, final Color color, final int durationMillis) {
        super(target, durationMillis);
        this.bounds = bounds == null ? new Rectangle() : new Rectangle(bounds);
        this.glowColor = color == null ? Color.WHITE : color;
    }

    @Override
    public void paintOverlay(final Graphics2D g2d) {
        if (bounds.isEmpty()) {
            return;
        }
        // Pulse envelope: 0 at both ends, 1 at the midpoint.
        final float pulse = (float) Math.sin(Math.PI * getProgress());
        if (pulse <= 0.001f) {
            return;
        }
        final int arcWidth = Math.max(bounds.width, bounds.height) / 8;
        final Graphics2D child = (Graphics2D) g2d.create();
        try {
            child.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int ring = 0; ring < RING_COUNT; ring++) {
                final float ringFraction = (ring + 1) / (float) RING_COUNT;
                final float alpha = PEAK_OPACITY * (1f - ringFraction) * pulse;
                if (alpha <= 0f) {
                    continue;
                }
                final int expand = Math.round(MAX_EXPAND_PX * pulse * ringFraction);
                final int x = bounds.x - expand;
                final int y = bounds.y - expand;
                final int width = bounds.width + 2 * expand;
                final int height = bounds.height + 2 * expand;
                final float strokeWidth = Math.max(1f, (RING_COUNT - ring) * 1.5f);
                child.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                child.setColor(glowColor);
                child.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                child.drawRoundRect(x, y, width, height, arcWidth, arcWidth);
            }
        } finally {
            child.dispose();
        }
    }
}
