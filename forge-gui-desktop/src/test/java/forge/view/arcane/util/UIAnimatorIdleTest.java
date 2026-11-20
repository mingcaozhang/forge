package forge.view.arcane.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import forge.view.arcane.util.UIAnimator.Mode;
import forge.view.arcane.util.UIAnimator.Tween;

/**
 * Phase 6 guard for the {@link UIAnimator} idle invariant: whenever no tweens
 * are scheduled — before any animation ever runs, after every tween drains on
 * its own, after {@link UIAnimator#cancelAll()}, and after the single remaining
 * keyed tween is cancelled — the animator must report that it is not active and
 * the single shared Swing timer must report {@code isRunning() == false} (zero
 * idle CPU cost between animations).
 *
 * <p>
 * The PLAN's "isActive()" requirement maps onto the existing public
 * {@link UIAnimator#isAnimating()} accessor, so no redundant alias was added to
 * the production API. Every check drives the real event dispatch thread and
 * creates no {@code JFrame}, so the tests stay headless-safe. The "idle"
 * snapshot is always read on the EDT so the active-set and timer views are
 * mutually consistent (the completing {@code tick} that auto-stops the timer is
 * itself an EDT event that finishes before the queued snapshot event runs).
 * </p>
 */
public class UIAnimatorIdleTest {

    private static final long EDT_TIMEOUT_MILLIS = 5_000L;
    private static final int LONG_TWEEN_MILLIS = 1_500;
    private static final int SHORT_TWEEN_MILLIS = 120;

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
    }

    @AfterMethod
    public void restoreModeAndDrain() throws Exception {
        runOnEdt(UIAnimator::cancelAll);
        UIAnimator.setForcedMode(null);
    }

    /**
     * Reads the idle triple on the EDT: no active entries, not animating, and the
     * shared timer not running.
     *
     * @return {@code {noEntries, notAnimating, timerStopped}}
     */
    private static boolean[] idleSnapshot() throws Exception {
        final boolean[] snapshot = new boolean[3];
        runOnEdt(() -> {
            snapshot[0] = UIAnimator.activeCount() == 0;
            snapshot[1] = !UIAnimator.isAnimating();
            snapshot[2] = !UIAnimator.isTimerRunning();
        });
        return snapshot;
    }

    private static void assertIdle(final boolean[] snapshot) {
        assertTrue(snapshot[0], "the active set must be empty once idle");
        assertTrue(snapshot[1], "UIAnimator.isAnimating() must be false once idle");
        assertTrue(snapshot[2], "the shared Swing timer must report isRunning() == false once idle");
    }

    @Test
    public void idleBeforeAnyAnimationIsScheduled() throws Exception {
        assertIdle(idleSnapshot());
    }

    @Test
    public void returnsToIdleAfterEveryTweenCompletes() throws Exception {
        final int tweenCount = 5;
        final CountDownLatch ended = new CountDownLatch(tweenCount);
        runOnEdt(() -> {
            for (int i = 0; i < tweenCount; i++) {
                UIAnimator.startRaw(SHORT_TWEEN_MILLIS, "idle-drain-" + i, new Tween() {
                    @Override
                    public void onFrame(final float easedProgress) {
                        // visual only
                    }

                    @Override
                    public void onEnd() {
                        ended.countDown();
                    }
                });
            }
        });

        assertTrue(ended.await(LONG_TWEEN_MILLIS, TimeUnit.MILLISECONDS),
                "every tween should complete within a generous bound around its duration");
        assertIdle(idleSnapshot());
    }

    @Test
    public void cancelAllReturnsAnimatorToIdle() throws Exception {
        final AtomicInteger runningDuring = new AtomicInteger();
        runOnEdt(() -> {
            UIAnimator.startRaw(LONG_TWEEN_MILLIS, "cancel-all-tween", new Tween() {
                @Override
                public void onFrame(final float easedProgress) {
                    // visual only
                }
            });
            if (UIAnimator.isAnimating() && UIAnimator.isTimerRunning()) {
                runningDuring.incrementAndGet();
            }
        });
        assertEquals(runningDuring.get(), 1,
                "the shared timer should be running while a tween is in flight");

        runOnEdt(UIAnimator::cancelAll);
        assertIdle(idleSnapshot());
    }

    @Test
    public void cancellingTheOnlyTweenReturnsAnimatorToIdle() throws Exception {
        final Object key = "only-key";
        final boolean[] runningDuring = new boolean[2];
        runOnEdt(() -> {
            UIAnimator.startRaw(LONG_TWEEN_MILLIS, key, new Tween() {
                @Override
                public void onFrame(final float easedProgress) {
                    // visual only
                }
            });
            runningDuring[0] = UIAnimator.isAnimating();
            runningDuring[1] = UIAnimator.isTimerRunning();
        });
        assertTrue(runningDuring[0], "a tween should be in flight before it is cancelled");
        assertTrue(runningDuring[1], "the shared timer should be running before the tween is cancelled");

        runOnEdt(() -> UIAnimator.cancel(key));
        assertIdle(idleSnapshot());
    }
}
