package forge.view.arcane.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import forge.view.arcane.util.UIAnimator.Mode;
import forge.view.arcane.util.UIAnimator.Tween;

/**
 * Guards the {@link FPref}-driven duration contract of {@link UIAnimator} for
 * the three {@link Mode}s resolved from {@code UI_ANIMATION_MODE}:
 * <ul>
 *   <li>{@link Mode#OFF} snaps a tween straight to its end state (no tween is
 *       ever registered and the shared timer never starts),</li>
 *   <li>{@link Mode#FAST} runs for roughly half the declared duration,</li>
 *   <li>{@link Mode#NORMAL} runs for the full declared duration.</li>
 * </ul>
 *
 * <p>
 * The Fast-vs-Normal assertion is made by measuring both within the same test
 * so that any constant EDT/scheduling latency cancels out of the ratio, keeping
 * the check stable on slower CI machines while still proving the halving. All
 * checks drive the real event dispatch thread and create no {@code JFrame}, so
 * they stay headless-safe.
 * </p>
 */
public class AnimationModeTest {

    private static final long EDT_TIMEOUT_MILLIS = 5_000L;

    private static void runOnEdt(final Runnable action) throws Exception {
        if (EventQueue.isDispatchThread()) {
            action.run();
        } else {
            EventQueue.invokeAndWait(action);
        }
    }

    @BeforeMethod
    public void resetModeAndDrain() throws Exception {
        UIAnimator.setForcedMode(Mode.NORMAL);
        runOnEdt(UIAnimator::cancelAll);
        assertEquals(UIAnimator.activeCount(), 0);
        assertFalse(UIAnimator.isTimerRunning());
    }

    @AfterMethod
    public void restoreModeAndDrain() throws Exception {
        runOnEdt(UIAnimator::cancelAll);
        UIAnimator.setForcedMode(null);
    }

    /** Result of driving a single tween to completion under a forced mode. */
    private static final class TweenRun {
        long elapsedMillis;
        float lastProgress;
        int endCount;
        boolean sawIntermediate;
    }

    private static TweenRun runTween(final Mode mode, final int declaredMillis) throws Exception {
        UIAnimator.setForcedMode(mode);
        runOnEdt(UIAnimator::cancelAll);

        final TweenRun run = new TweenRun();
        final CountDownLatch ended = new CountDownLatch(1);
        // Capture the clock before scheduling so the measured window fully
        // contains the tween's real duration (the EDT hand-off overhead only
        // ever makes the measurement larger, never smaller).
        final long startNanos = System.nanoTime();
        runOnEdt(() -> UIAnimator.startRaw(declaredMillis, new Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                run.lastProgress = easedProgress;
                if (easedProgress < 1f) {
                    run.sawIntermediate = true;
                }
            }

            @Override
            public void onEnd() {
                run.endCount++;
                ended.countDown();
            }
        }));
        final boolean finished = ended.await(declaredMillis + EDT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        run.elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        assertTrue(finished, "tween should complete under mode " + mode);
        return run;
    }

    @Test
    public void offModeSnapsToEndWithoutEnteringActiveSet() throws Exception {
        UIAnimator.setForcedMode(Mode.OFF);
        final AtomicReference<Float> lastProgress = new AtomicReference<>();
        final AtomicInteger endCount = new AtomicInteger();
        final AtomicLong addedToActive = new AtomicLong(-1L);
        final AtomicBoolean timerStarted = new AtomicBoolean();
        final CountDownLatch ended = new CountDownLatch(1);

        runOnEdt(() -> UIAnimator.startRaw(200, new Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                lastProgress.set(easedProgress);
            }

            @Override
            public void onEnd() {
                // Snapshot the active-set size at the exact moment the tween ends:
                // OFF must never have added an entry, so it stays 0 here too.
                addedToActive.set(UIAnimator.activeCount());
                timerStarted.set(UIAnimator.isTimerRunning());
                endCount.incrementAndGet();
                ended.countDown();
            }
        }));

        assertTrue(ended.await(EDT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "OFF mode should snap the tween to its end immediately");
        assertEquals(endCount.get(), 1, "onEnd must fire exactly once");
        assertEquals(lastProgress.get(), Float.valueOf(1f), "OFF snap must land on 100%");
        assertEquals(addedToActive.get(), 0L, "OFF mode must never enter the active set");
        assertFalse(timerStarted.get(), "OFF mode must not start the shared timer");

        runOnEdt(() -> {
            assertEquals(UIAnimator.activeCount(), 0);
            assertFalse(UIAnimator.isTimerRunning());
        });
    }

    @Test
    public void fastHalvesAndNormalUsesFullDuration() throws Exception {
        final int declaredMillis = 400;

        final TweenRun normal = runTween(Mode.NORMAL, declaredMillis);
        final TweenRun fast = runTween(Mode.FAST, declaredMillis);

        // Both modes must animate through intermediate frames and land on 100%.
        assertTrue(normal.sawIntermediate, "NORMAL must animate through intermediate frames");
        assertTrue(fast.sawIntermediate, "FAST must animate through intermediate frames");
        assertEquals(normal.lastProgress, 1.0f, 0.0f, "NORMAL must land on 100%");
        assertEquals(fast.lastProgress, 1.0f, 0.0f, "FAST must land on 100%");
        assertEquals(normal.endCount, 1);
        assertEquals(fast.endCount, 1);

        // NORMAL cannot complete faster than its full declared duration.
        assertTrue(normal.elapsedMillis >= declaredMillis,
                "NORMAL must run the full declared duration; elapsed=" + normal.elapsedMillis
                        + "ms, declared=" + declaredMillis + "ms");

        // FAST is measurably faster than NORMAL but is clearly not a snap. The
        // ratio is computed from two measurements taken back-to-back so any
        // constant EDT/scheduling latency is shared and cancels out.
        assertTrue(fast.elapsedMillis >= declaredMillis * 0.25,
                "FAST must not snap to the end; elapsed=" + fast.elapsedMillis + "ms");
        final double fastToNormal = (double) fast.elapsedMillis / (double) normal.elapsedMillis;
        assertTrue(fastToNormal <= 0.8,
                "FAST should run for roughly half NORMAL's duration; ratio=" + fastToNormal
                        + " (fast=" + fast.elapsedMillis + "ms, normal=" + normal.elapsedMillis + "ms)");
    }
}
