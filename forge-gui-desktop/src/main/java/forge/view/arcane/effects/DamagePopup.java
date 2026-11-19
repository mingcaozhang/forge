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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import forge.view.arcane.util.Easing;

/**
 * <p>
 * DamagePopup class. A short-lived text callout (e.g. {@code -3}) that floats
 * upward from its anchor point and fades out. Used to surface damage, life
 * changes, or counter gains near a card or player panel.
 * </p>
 *
 * @author Forge
 */
public final class DamagePopup extends ArcaneEffect {

    /** Nominal duration in milliseconds. */
    public static final int DEFAULT_DURATION_MILLIS = 900;
    /** Fraction of the duration during which the popup is fully opaque. */
    private static final float HOLD_FRACTION = 0.55f;
    /** Vertical travel, in pixels, over the full duration. */
    private static final int RISE_PX = 26;
    /** Base glyph height, in pixels. */
    private static final int BASE_FONT_SIZE = 18;
    /** Extra glyph height at spawn, shrinking to the base over time. */
    private static final int PUNCH_FONT_SIZE = 6;

    private final int anchorX;
    private final int anchorY;
    private final String message;
    private final Color color;

    /**
     * Convenience constructor that formats an integer amount as a damage-style
     * label (a leading minus sign is prepended for positive amounts).
     *
     * @param target component whose repaints are requested while animating
     * @param x      anchor x coordinate (text is centered horizontally on it)
     * @param y      anchor y coordinate (baseline at spawn)
     * @param amount numeric amount to display
     * @param color  text color
     */
    public DamagePopup(final Component target, final int x, final int y, final int amount, final Color color) {
        this(target, x, y, amount > 0 ? "-" + amount : Integer.toString(amount), color);
    }

    /**
     * @param target component whose repaints are requested while animating
     * @param x      anchor x coordinate (text is centered horizontally on it)
     * @param y      anchor y coordinate (baseline at spawn)
     * @param message text to display
     * @param color  text color
     */
    public DamagePopup(final Component target, final int x, final int y, final String message, final Color color) {
        this(target, x, y, message, color, DEFAULT_DURATION_MILLIS);
    }

    /**
     * @param target         component whose repaints are requested while animating
     * @param x              anchor x coordinate (text is centered horizontally on it)
     * @param y              anchor y coordinate (baseline at spawn)
     * @param message        text to display
     * @param color          text color
     * @param durationMillis animation duration in milliseconds
     */
    public DamagePopup(final Component target, final int x, final int y, final String message, final Color color,
            final int durationMillis) {
        super(target, durationMillis);
        this.anchorX = x;
        this.anchorY = y;
        this.message = message == null ? "" : message;
        this.color = color == null ? Color.WHITE : color;
    }

    @Override
    public void paintOverlay(final Graphics2D g2d) {
        if (message.isEmpty()) {
            return;
        }
        final float p = getProgress();
        final float alpha;
        if (p <= HOLD_FRACTION) {
            alpha = 1f;
        } else {
            alpha = Math.max(0f, (1f - p) / (1f - HOLD_FRACTION));
        }
        if (alpha <= 0f) {
            return;
        }
        final int size = BASE_FONT_SIZE + Math.round(PUNCH_FONT_SIZE * (1f - p));
        final Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
        final float easedRise = Easing.cubicOut(p) * RISE_PX;
        final float baseline = anchorY - easedRise;

        final Graphics2D child = (Graphics2D) g2d.create();
        try {
            child.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            child.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            child.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            child.setFont(font);
            final FontMetrics fm = child.getFontMetrics(font);
            final int drawX = anchorX - fm.stringWidth(message) / 2;
            // Dark outline for readability against light and dark backgrounds.
            child.setColor(Color.BLACK);
            child.drawString(message, drawX - 1, baseline - 1);
            child.drawString(message, drawX + 1, baseline + 1);
            child.setColor(color);
            child.drawString(message, drawX, baseline);
        } finally {
            child.dispose();
        }
    }
}
