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

import java.awt.Component;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import forge.view.arcane.util.UIAnimator;

/**
 * <p>
 * ArcaneEffect class. Base for the short-lived, purely-visual match effects
 * (hit flashes, damage popups, cast glows) rendered on the match overlay layer.
 * Each effect is driven by exactly one keyed {@link UIAnimator} tween: starting
 * the effect registers it in a shared active list and schedules the tween; when
 * the tween reaches its end the effect retires itself from the list. Because it
 * schedules a single tween and retires in that tween's {@code onEnd}, once the
 * full duration has elapsed both the active effect list and the shared animator
 * timer are empty/idle.
 * </p>
 *
 * <p>
 * Effects never mutate game state and never read it either; they are handed any
 * values they need at construction time and only request repaints of their
 * target component while in flight.
 * </p>
 *
 * @author Forge
 */
public abstract class ArcaneEffect {

    private static final List<ArcaneEffect> ACTIVE = new CopyOnWriteArrayList<>();
    private static final AtomicInteger ID_SEQUENCE = new AtomicInteger();

    private final Component target;
    private final int durationMillis;
    private final Object animatorId;

    private volatile boolean running;
    private float progress;

    /**
     * @param target         component whose repaints are requested while the
     *                       effect is in flight; may be {@code null} for a
     *                       non-painting (e.g. headless/test) effect
     * @param durationMillis nominal duration in milliseconds; enforced with a
     *                       minimum of 1 ms and passed verbatim to
     *                       {@link UIAnimator#startRaw(int, Object, UIAnimator.Tween)}
     *                       so effects may run longer than the standard tween clamp
     */
    protected ArcaneEffect(final Component target, final int durationMillis) {
        this.target = target;
        this.durationMillis = Math.max(1, durationMillis);
        this.animatorId = "effect-" + ID_SEQUENCE.incrementAndGet();
    }

    /**
     * Registers this effect and schedules its driving tween. A no-op if the
     * effect is already running.
     */
    public final void start() {
        if (running) {
            return;
        }
        running = true;
        progress = 0f;
        ACTIVE.add(this);
        UIAnimator.startRaw(durationMillis, animatorId, new UIAnimator.Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                setProgress(easedProgress);
                repaintTarget();
            }

            @Override
            public void onEnd() {
                setProgress(1f);
                retire();
                repaintTarget();
            }
        });
    }

    /**
     * Cancels any in-flight tween and removes this effect from the active list
     * without advancing its rendered appearance.
     */
    public final void cancel() {
        UIAnimator.cancel(animatorId);
        retire();
    }

    /**
     * @return {@code true} while this effect is registered and animating
     */
    public final boolean isActive() {
        return running;
    }

    /**
     * @return eased progress in {@code [0, 1]}; subclasses derive their visual
     *         appearance from this value
     */
    public final float getProgress() {
        return progress;
    }

    /**
     * @return the target component whose repaints are requested, possibly {@code null}
     */
    public final Component getTarget() {
        return target;
    }

    /**
     * Draws this effect into the supplied graphics, which is expected to be the
     * overlay layer's coordinate space. Called only while the effect is active.
     *
     * @param g2d the overlay graphics to paint onto
     */
    public abstract void paintOverlay(Graphics2D g2d);

    private void setProgress(final float value) {
        this.progress = Math.max(0f, Math.min(1f, value));
    }

    private void retire() {
        running = false;
        ACTIVE.remove(this);
    }

    private void repaintTarget() {
        final Component component = target;
        if (component == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            component.repaint();
        } else {
            SwingUtilities.invokeLater(component::repaint);
        }
    }

    /**
     * @return number of effects currently registered and animating
     */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /**
     * @return a point-in-time snapshot of the currently active effects, safe to
     *         iterate while the list mutates
     */
    public static List<ArcaneEffect> activeEffects() {
        return new ArrayList<>(ACTIVE);
    }
}
