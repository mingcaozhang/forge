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
package forge.view.arcane.util;

/**
 * <p>
 * Easing class. Provides the small set of static easing curves used by the
 * arcane UI animation subsystem (see {@code UIAnimator}). All functions map a
 * normalized time {@code t} in the range {@code [0, 1]} onto an eased value in
 * {@code [0, 1]}; inputs outside the range are clamped.
 * </p>
 *
 * @author Forge
 */
public abstract class Easing {
    private Easing() {
    }

    private static float clamp01(final float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return t;
    }

    /**
     * Accelerating cubic curve (starts slow, ends fast).
     *
     * @param t normalized time in {@code [0, 1]}
     * @return eased value in {@code [0, 1]}
     */
    public static float cubicIn(final float t) {
        final float x = clamp01(t);
        return x * x * x;
    }

    /**
     * Decelerating cubic curve (starts fast, ends slow). This is the default
     * curve used by {@code UIAnimator} tweens.
     *
     * @param t normalized time in {@code [0, 1]}
     * @return eased value in {@code [0, 1]}
     */
    public static float cubicOut(final float t) {
        final float x = clamp01(t);
        final float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    /**
     * Smooth sinusoidal curve (slow at both ends, fast in the middle).
     *
     * @param t normalized time in {@code [0, 1]}
     * @return eased value in {@code [0, 1]}
     */
    public static float sineInOut(final float t) {
        final float x = clamp01(t);
        return (float) (-(Math.cos(Math.PI * x) - 1.0) / 2.0);
    }
}
