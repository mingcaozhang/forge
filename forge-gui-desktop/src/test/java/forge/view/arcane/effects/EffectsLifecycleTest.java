package forge.view.arcane.effects;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Rectangle;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import forge.view.arcane.util.UIAnimator;
import forge.view.arcane.util.UIAnimator.Mode;

/**
 * Guards the fire-and-forget lifecycle contract of {@link ArcaneEffect} and its
 * Phase 3 subclasses: scheduling an effect registers it and schedules exactly one
 * {@link UIAnimator} tween, and once the effect's full duration has elapsed both
 * the active effect list and the shared animator timer are drained/idle (zero
 * lingering cost). All effects are driven with a {@code null} painting target so
 * the checks stay headless-safe.
 */
public class EffectsLifecycleTest {

    private static final long EDT_TIMEOUT_MILLIS = 5_000L;
    private static final int EFFECT_DURATION_MILLIS = 150;
    private static final long POLL_INTERVAL_MILLIS = 20L;

    private static void runOnEdt(final Runnable action) throws Exception {
        if (EventQueue.isDispatchThread()) {
            action.run();
        } else {
            EventQueue.invokeAndWait(action);
        }
    }

    /** Point-in-time read of the shared active effect / tween / timer state. */
    private static final class IdleSnapshot {
        int effects;
        int tweens;
        boolean timerRunning;
    }

    private static IdleSnapshot readIdleState() throws Exception {
        final IdleSnapshot snapshot = new IdleSnapshot();
        runOnEdt(() -> {
            snapshot.effects = ArcaneEffect.activeCount();
            snapshot.tweens = UIAnimator.activeCount();
            snapshot.timerRunning = UIAnimator.isTimerRunning();
        });
        return snapshot;
    }

    private static IdleSnapshot awaitIdle(final long timeoutMillis) throws Exception {
        final long deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000L;
        IdleSnapshot snapshot = readIdleState();
        while (snapshot.effects != 0 || snapshot.tweens != 0 || snapshot.timerRunning) {
            if (System.nanoTime() >= deadlineNanos) {
                return snapshot;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
            snapshot = readIdleState();
        }
        return snapshot;
    }

    @BeforeMethod
    public void forceNormalModeAndDrain() throws Exception {
        UIAnimator.setForcedMode(Mode.NORMAL);
        // Completing every in-flight tween retires the owning effects too, so a
        // single animator drain empties both the effect list and the tween list.
        runOnEdt(UIAnimator::cancelAll);
        final IdleSnapshot drained = readIdleState();
        assertEquals(drained.effects, 0);
        assertEquals(drained.tweens, 0);
        assertFalse(drained.timerRunning);
    }

    @AfterMethod
    public void restoreModeAndDrain() throws Exception {
        runOnEdt(UIAnimator::cancelAll);
        UIAnimator.setForcedMode(null);
    }

    private static void assertSchedulesThenRetires(final ArcaneEffect effect) throws Exception {
        runOnEdt(effect::start);

        // Mid-flight: the effect is registered and drives exactly one animator tween.
        final IdleSnapshot active = readIdleState();
        assertTrue(effect.isActive(), "effect should be active right after start");
        assertEquals(active.effects, 1, "exactly one effect should be active");
        assertEquals(active.tweens, 1, "the effect should drive exactly one animator tween");
        assertTrue(active.timerRunning, "the shared timer should run while the effect animates");

        // After the full duration elapses: both lists drained and the timer stopped.
        final IdleSnapshot idle = awaitIdle(EFFECT_DURATION_MILLIS + EDT_TIMEOUT_MILLIS);
        assertEquals(idle.effects, 0, "active effect list must be empty after full duration");
        assertEquals(idle.tweens, 0, "animator active set must be empty after full duration");
        assertFalse(idle.timerRunning, "shared animator timer must be stopped once idle");
        assertFalse(effect.isActive(), "effect must report inactive after retiring");
    }

    @Test
    public void hitFlashOverlayRetiresAfterFullDuration() throws Exception {
        assertSchedulesThenRetires(new HitFlashOverlay(
                null, new Rectangle(0, 0, 20, 20), Color.WHITE, EFFECT_DURATION_MILLIS));
    }

    @Test
    public void damagePopupRetiresAfterFullDuration() throws Exception {
        assertSchedulesThenRetires(new DamagePopup(
                null, 10, 10, "-3", Color.RED, EFFECT_DURATION_MILLIS));
    }

    @Test
    public void castGlowRetiresAfterFullDuration() throws Exception {
        assertSchedulesThenRetires(new CastGlow(
                null, new Rectangle(0, 0, 20, 20), Color.CYAN, EFFECT_DURATION_MILLIS));
    }

    @Test
    public void multipleEffectsAllRetireAfterFullDuration() throws Exception {
        final ArcaneEffect hit = new HitFlashOverlay(
                null, new Rectangle(0, 0, 20, 20), Color.WHITE, EFFECT_DURATION_MILLIS);
        final ArcaneEffect popup = new DamagePopup(
                null, 10, 10, "-3", Color.RED, EFFECT_DURATION_MILLIS);
        final ArcaneEffect glow = new CastGlow(
                null, new Rectangle(0, 0, 20, 20), Color.CYAN, EFFECT_DURATION_MILLIS);

        runOnEdt(() -> {
            hit.start();
            popup.start();
            glow.start();
        });

        final IdleSnapshot active = readIdleState();
        assertEquals(active.effects, 3, "all three effects should be active");
        assertEquals(active.tweens, 3, "each effect should drive its own animator tween");
        assertTrue(active.timerRunning, "the shared timer should run while effects animate");

        final IdleSnapshot idle = awaitIdle(EFFECT_DURATION_MILLIS + EDT_TIMEOUT_MILLIS);
        assertEquals(idle.effects, 0, "every effect must retire after its full duration");
        assertEquals(idle.tweens, 0, "every tween must retire after its full duration");
        assertFalse(idle.timerRunning, "shared animator timer must be stopped once all effects idle");
        assertFalse(hit.isActive());
        assertFalse(popup.isActive());
        assertFalse(glow.isActive());
    }
}
