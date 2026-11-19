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

import java.awt.EventQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.Timer;

import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * <p>
 * UIAnimator class. A single shared, lazily-created Swing {@link Timer} drives
 * all arcane UI tweens. Subscribers are stored in a {@link CopyOnWriteArrayList}
 * and stepped with the default cubic ease-out curve from {@link Easing}. The
 * timer auto-stops whenever the active set becomes empty so there is zero idle
 * CPU cost between animations, and only ever one {@code Timer} is created for
 * the whole application.
 * </p>
 *
 * <p>
 * All entry points funnel onto the Swing event dispatch thread, so tweens may
 * safely mutate component state / request repaints from their callbacks. The
 * global {@link FPref#UI_ANIMATION_MODE} preference is honored: {@code Off}
 * snaps every tween straight to its end state, {@code Fast} halves each tween's
 * duration, and {@code Normal} (the default) uses the full duration.
 * </p>
 *
 * @author Forge
 */
public abstract class UIAnimator {

    /** Target frame interval in milliseconds (~60 FPS). */
    private static final int FRAME_MILLIS = 16;
    /** Lower bound for any per-call tween duration. */
    private static final int MIN_DURATION_MILLIS = 120;
    /** Upper bound for any per-call tween duration. */
    private static final int MAX_DURATION_MILLIS = 220;
    /** Duration used when a caller does not specify one. */
    private static final int DEFAULT_DURATION_MILLIS = 180;

    private UIAnimator() {
    }

    /**
     * Resolved animation level, derived from {@link FPref#UI_ANIMATION_MODE}.
     */
    public enum Mode {
        /** Full-duration tweens. */
        NORMAL,
        /** Half-duration tweens. */
        FAST,
        /** No animation: tweens snap to their end state. */
        OFF
    }

    /**
     * Callback contract for a single animation. Implementations apply the
     * eased progress each frame and optionally react to completion.
     */
    public interface Tween {
        /**
         * Called once per frame on the event dispatch thread.
         *
         * @param easedProgress eased value in {@code [0, 1]}
         */
        void onFrame(float easedProgress);

        /**
         * Called exactly once on the event dispatch thread when the tween
         * reaches its end state, including when it is snapped due to
         * {@link Mode#OFF} or cancellation.
         */
        default void onEnd() {
        }
    }

    private static final class Entry {
        final Tween tween;
        final long startNanos;
        final long durationNanos;
        final Object id;

        Entry(final Tween tween0, final long startNanos0, final long durationNanos0, final Object id0) {
            this.tween = tween0;
            this.startNanos = startNanos0;
            this.durationNanos = durationNanos0;
            this.id = id0;
        }
    }

    private static final CopyOnWriteArrayList<Entry> ACTIVE = new CopyOnWriteArrayList<>();
    private static Timer sharedTimer;
    private static volatile Mode forcedMode;

    /**
     * Starts a tween with the default duration.
     *
     * @param tween the tween to drive; ignored when {@code null}
     */
    public static void start(final Tween tween) {
        start(DEFAULT_DURATION_MILLIS, tween);
    }

    /**
     * Starts a tween with the requested duration (clamped to
     * {@value #MIN_DURATION_MILLIS}..{@value #MAX_DURATION_MILLIS} ms).
     *
     * @param durationMillis requested duration; clamped to the supported range
     * @param tween          the tween to drive; ignored when {@code null}
     */
    public static void start(final int durationMillis, final Tween tween) {
        startInternal(clampDuration(durationMillis), null, tween);
    }

    /**
     * Starts a keyed, clamped tween. When {@code id} is non-{@code null}, any
     * currently active tween with the same id is silently cancelled first, so
     * restarting e.g. a hover animation reverses smoothly instead of stacking.
     *
     * @param durationMillis requested duration; clamped to the supported range
     * @param id             identity used to coalesce/cancel this tween, or
     *                       {@code null} for an unkeyed tween
     * @param tween          the tween to drive; ignored when {@code null}
     */
    public static void start(final int durationMillis, final Object id, final Tween tween) {
        startInternal(clampDuration(durationMillis), id, tween);
    }

    /**
     * Starts a tween using the caller-supplied duration verbatim (only a
     * minimum of 1 ms is enforced), bypassing the {@value #MIN_DURATION_MILLIS}
     *..{@value #MAX_DURATION_MILLIS} clamp used by {@link #start(int, Tween)}.
     * Intended for legacy entry points whose durations are tuned per call.
     *
     * @param durationMillis caller-specified duration in milliseconds
     * @param tween          the tween to drive; ignored when {@code null}
     */
    public static void startRaw(final int durationMillis, final Tween tween) {
        startInternal(Math.max(1, durationMillis), null, tween);
    }

    /**
     * Keyed variant of {@link #startRaw(int, Tween)}: starting with a
     * non-{@code null} {@code id} silently cancels any active tween carrying the
     * same id before scheduling this one.
     *
     * @param durationMillis caller-specified duration in milliseconds
     * @param id             identity used to coalesce/cancel this tween, or
     *                       {@code null} for an unkeyed tween
     * @param tween          the tween to drive; ignored when {@code null}
     */
    public static void startRaw(final int durationMillis, final Object id, final Tween tween) {
        startInternal(Math.max(1, durationMillis), id, tween);
    }

    private static void startInternal(final int resolvedDurationMillis, final Object id, final Tween tween) {
        if (tween == null) {
            return;
        }
        final int scaled = scaledDurationMillis(resolvedDurationMillis, currentMode());
        if (scaled <= 0) {
            beginOnEdt(() -> {
                if (id != null) {
                    cancelSilently(id);
                }
                finishNow(tween);
            });
            return;
        }
        beginOnEdt(() -> {
            if (id != null) {
                cancelSilently(id);
            }
            ACTIVE.add(new Entry(tween, System.nanoTime(), scaled * 1_000_000L, id));
            ensureRunning();
        });
    }

    /**
     * @return number of currently active (in-flight) tweens
     */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /**
     * @return {@code true} when at least one tween is in flight
     */
    public static boolean isAnimating() {
        return !ACTIVE.isEmpty();
    }

    /**
     * Reports whether the single shared Swing {@link Timer} is currently
     * running. Observability hook for idle-state guards and tests; it must be
     * read on the event dispatch thread, which is the only thread that starts
     * and stops the timer.
     *
     * @return {@code true} when the shared timer is running
     */
    public static boolean isTimerRunning() {
        return sharedTimer != null && sharedTimer.isRunning();
    }

    /**
     * Snaps and completes every in-flight tween immediately.
     */
    public static void cancelAll() {
        beginOnEdt(() -> {
            for (final Entry entry : ACTIVE) {
                complete(entry);
            }
            ACTIVE.clear();
            stopIfIdle();
        });
    }

    /**
     * Silently removes every in-flight tween carrying the given id without
     * invoking their {@link Tween#onEnd()} callbacks (their owners are expected
     * to drive any follow-up state). No-op for a {@code null} id.
     *
     * @param id the tween identity to cancel
     */
    public static void cancel(final Object id) {
        if (id == null) {
            return;
        }
        beginOnEdt(() -> {
            cancelSilently(id);
            stopIfIdle();
        });
    }

    // Must run on the event dispatch thread.
    private static void cancelSilently(final Object id) {
        ACTIVE.removeIf(entry -> id.equals(entry.id));
    }

    /**
     * Overrides the animation level, bypassing the stored preference. Intended
     * for tests and future OS-level reduced-motion detection.
     *
     * @param mode mode to force, or {@code null} to read from preferences
     */
    public static void setForcedMode(final Mode mode) {
        forcedMode = mode;
    }

    /**
     * Maps a stored preference string to a {@link Mode}. Accepts the canonical
     * tokens {@code Normal}/{@code Fast}/{@code Off} plus legacy spellings
     * {@code Full}/{@code Reduced}/{@code None}.
     *
     * @param rawValue raw preference value, possibly {@code null}
     * @return the resolved mode, defaulting to {@link Mode#NORMAL}
     */
    public static Mode modeFromString(final String rawValue) {
        if (rawValue == null) {
            return Mode.NORMAL;
        }
        switch (rawValue.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "off":
            case "none":
                return Mode.OFF;
            case "fast":
            case "reduced":
                return Mode.FAST;
            default:
                return Mode.NORMAL;
        }
    }

    private static int clampDuration(final int durationMillis) {
        return Math.max(MIN_DURATION_MILLIS, Math.min(MAX_DURATION_MILLIS, durationMillis));
    }

    private static int scaledDurationMillis(final int resolvedDurationMillis, final Mode mode) {
        switch (mode) {
            case OFF:
                return 0;
            case FAST:
                return Math.max(1, resolvedDurationMillis / 2);
            default:
                return resolvedDurationMillis;
        }
    }

    private static Mode currentMode() {
        final Mode forced = forcedMode;
        if (forced != null) {
            return forced;
        }
        try {
            return modeFromString(FModel.getPreferences().getPref(FPref.UI_ANIMATION_MODE));
        } catch (Throwable ignored) {
            return Mode.NORMAL;
        }
    }

    private static void beginOnEdt(final Runnable action) {
        if (EventQueue.isDispatchThread()) {
            action.run();
        } else {
            EventQueue.invokeLater(action);
        }
    }

    // Must run on the event dispatch thread.
    private static void ensureRunning() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (sharedTimer == null) {
            sharedTimer = new Timer(FRAME_MILLIS, e -> tick());
            sharedTimer.setCoalesce(true);
            sharedTimer.setRepeats(true);
        }
        if (!sharedTimer.isRunning()) {
            sharedTimer.start();
        }
    }

    // Must run on the event dispatch thread.
    private static void tick() {
        final long now = System.nanoTime();
        for (final Entry entry : ACTIVE) {
            final float progress = (float) (now - entry.startNanos) / (float) entry.durationNanos;
            if (progress >= 1f) {
                entry.tween.onFrame(1f);
                entry.tween.onEnd();
                ACTIVE.remove(entry);
            } else {
                entry.tween.onFrame(Easing.cubicOut(progress));
            }
        }
        stopIfIdle();
    }

    // Must run on the event dispatch thread.
    private static void complete(final Entry entry) {
        entry.tween.onFrame(1f);
        entry.tween.onEnd();
    }

    // Must run on the event dispatch thread.
    private static void finishNow(final Tween tween) {
        tween.onFrame(1f);
        tween.onEnd();
    }

    // Must run on the event dispatch thread.
    private static void stopIfIdle() {
        if (ACTIVE.isEmpty() && sharedTimer != null && sharedTimer.isRunning()) {
            sharedTimer.stop();
        }
    }
}
