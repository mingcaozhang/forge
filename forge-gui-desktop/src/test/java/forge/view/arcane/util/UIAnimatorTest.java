package forge.view.arcane.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import forge.view.arcane.util.UIAnimator.Mode;
import forge.view.arcane.util.UIAnimator.Tween;

/**
 * Guards the Phase 2 {@link UIAnimator} contract: a submitted tween runs
 * through its declared duration and reaches {@code 100%}, the single shared
 * Swing timer is running while work is in flight, and that same timer reports
 * {@code isRunning() == false} once the active set drains to empty (zero idle
 * cost). All checks drive the real event dispatch thread; no {@code JFrame} is
 * created, so they stay headless-safe.
 */
public class UIAnimatorTest {

    private static final long EDT_TIMEOUT_MILLIS = 5_000L;

    private static void runOnEdt(final Runnable action) throws Exception {
        if (EventQueue.isDispatchThread()) {
            action.run();
        } else {
            EventQueue.invokeAndWait(action);
        }
    }

    @BeforeMethod
    public void forceNormalModeAndDrain() throws Exception {
        UIAnimator.setForcedMode(Mode.NORMAL);
        runOnEdt(UIAnimator::cancelAll);
        assertEquals(UIAnimator.activeCount(), 0);
    }

    @AfterMethod
    public void restoreModeAndDrain() throws Exception {
        runOnEdt(UIAnimator::cancelAll);
        UIAnimator.setForcedMode(null);
    }

    @Test
    public void tweenReachesHundredPercentInDeclaredDuration() throws Exception {
        final int durationMillis = 200;
        final AtomicReference<Float> lastProgress = new AtomicReference<>();
        final AtomicInteger endCount = new AtomicInteger();
        final AtomicBoolean sawIntermediateFrame = new AtomicBoolean();
        final CountDownLatch ended = new CountDownLatch(1);
        final long submittedNanos = System.nanoTime();

        runOnEdt(() -> UIAnimator.startRaw(durationMillis, new Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                lastProgress.set(easedProgress);
                if (easedProgress < 1f) {
                    sawIntermediateFrame.set(true);
                }
            }

            @Override
            public void onEnd() {
                endCount.incrementAndGet();
                ended.countDown();
            }
        }));

        assertTrue(ended.await(durationMillis + EDT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "tween should reach its end within a generous bound around its declared duration");

        final long elapsedMillis = (System.nanoTime() - submittedNanos) / 1_000_000L;

        assertEquals(endCount.get(), 1, "onEnd must fire exactly once");
        assertEquals(lastProgress.get(), Float.valueOf(1f),
                "the final frame must land exactly on 100% progress");
        assertTrue(sawIntermediateFrame.get(),
                "Normal mode must animate through intermediate progress, never snap to the end");
        assertTrue(elapsedMillis >= durationMillis,
                "tween cannot complete faster than its declared duration; elapsed=" + elapsedMillis
                        + "ms, duration=" + durationMillis + "ms");
    }

    @Test
    public void sharedTimerStopsOnceActiveSetDrains() throws Exception {
        final int durationMillis = 200;
        final CountDownLatch ended = new CountDownLatch(1);

        runOnEdt(() -> UIAnimator.startRaw(durationMillis, new Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                // visual only
            }

            @Override
            public void onEnd() {
                ended.countDown();
            }
        }));

        // While in flight: active set is non-empty and the shared timer is running.
        final boolean[] runningDuringAnimation = new boolean[2];
        runOnEdt(() -> {
            runningDuringAnimation[0] = UIAnimator.isAnimating();
            runningDuringAnimation[1] = UIAnimator.isTimerRunning();
        });
        assertTrue(runningDuringAnimation[0], "at least one tween should be in flight");
        assertTrue(runningDuringAnimation[1], "the shared timer should be running while tweens are active");

        assertTrue(ended.await(durationMillis + EDT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "tween should complete");

        // After drain: the queued drain-check runs on the EDT *after* the completing
        // tick (and its auto-stop) finished, so the drained view is consistent.
        final boolean[] drained = new boolean[3];
        runOnEdt(() -> {
            drained[0] = UIAnimator.activeCount() == 0;
            drained[1] = !UIAnimator.isAnimating();
            drained[2] = !UIAnimator.isTimerRunning();
        });
        assertTrue(drained[0], "active set should be empty after the tween completes");
        assertTrue(drained[1], "isAnimating() should be false after the tween completes");
        assertTrue(drained[2], "the shared Swing timer must report isRunning() == false once idle");
    }

    @Test
    public void offModeSnapsToEndWithoutStartingTimer() throws Exception {
        UIAnimator.setForcedMode(Mode.OFF);
        final AtomicReference<Float> lastProgress = new AtomicReference<>();
        final AtomicInteger endCount = new AtomicInteger();
        final CountDownLatch ended = new CountDownLatch(1);

        runOnEdt(() -> UIAnimator.startRaw(200, new Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                lastProgress.set(easedProgress);
            }

            @Override
            public void onEnd() {
                endCount.incrementAndGet();
                ended.countDown();
            }
        }));

        assertTrue(ended.await(EDT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "OFF mode should snap the tween to its end immediately");
        assertEquals(endCount.get(), 1);
        assertEquals(lastProgress.get(), Float.valueOf(1f));

        final boolean[] idle = new boolean[2];
        runOnEdt(() -> {
            idle[0] = UIAnimator.activeCount() == 0;
            idle[1] = !UIAnimator.isTimerRunning();
        });
        assertTrue(idle[0], "OFF-mode tween must never enter the active set");
        assertTrue(idle[1], "OFF mode must not start the shared timer");
    }
}
