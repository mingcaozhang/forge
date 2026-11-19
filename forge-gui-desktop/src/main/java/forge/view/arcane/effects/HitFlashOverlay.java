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
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * <p>
 * HitFlashOverlay class. A brief solid-color flash over a rectangular card
 * region that ramps up to full intensity at the midpoint of its duration and
 * then fades back to transparent. Used to give a tactile "hit" punch when a
 * creature is dealt damage or blocks.
 * </p>
 *
 * @author Forge
 */
public final class HitFlashOverlay extends ArcaneEffect {

    /** Nominal duration in milliseconds. */
    public static final int DEFAULT_DURATION_MILLIS = 220;
    /** Peak opacity of the flash at the midpoint of the animation. */
    private static final float PEAK_OPACITY = 0.85f;
    /** Corner radius, in pixels, of the flashed region. */
    private static final int ARC_PX = 10;

    private final Rectangle bounds;
    private final Color flashColor;

    /**
     * @param target  component whose repaints are requested while flashing
     * @param bounds  region (in overlay coordinates) to flash over
     * @param color   flash color
     */
    public HitFlashOverlay(final Component target, final Rectangle bounds, final Color color) {
        this(target, bounds, color, DEFAULT_DURATION_MILLIS);
    }

    /**
     * @param target         component whose repaints are requested while flashing
     * @param bounds         region (in overlay coordinates) to flash over
     * @param color          flash color
     * @param durationMillis animation duration in milliseconds
     */
    public HitFlashOverlay(final Component target, final Rectangle bounds, final Color color,
            final int durationMillis) {
        super(target, durationMillis);
        this.bounds = bounds == null ? new Rectangle() : new Rectangle(bounds);
        this.flashColor = color == null ? Color.WHITE : color;
    }

    @Override
    public void paintOverlay(final Graphics2D g2d) {
        if (bounds.isEmpty()) {
            return;
        }
        final float p = getProgress();
        // Triangular intensity: ramp up over the first half, fade out over the second.
        final float intensity = p <= 0.5f ? p / 0.5f : (1f - p) / 0.5f;
        final float alpha = Math.max(0f, Math.min(1f, intensity)) * PEAK_OPACITY;
        if (alpha <= 0f) {
            return;
        }
        final Graphics2D child = (Graphics2D) g2d.create();
        try {
            child.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            child.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            child.setColor(flashColor);
            child.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, ARC_PX, ARC_PX);
        } finally {
            child.dispose();
        }
    }
}
