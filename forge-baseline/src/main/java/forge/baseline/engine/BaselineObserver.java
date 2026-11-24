package forge.baseline.engine;

/**
 * The state-observer hook on the {@link BaselineEngine} adapter: per-decision
 * and terminal-match callbacks for trajectory consumers (the Phase 32.1
 * JSONL shard writer, invariant checkers, …).
 *
 * <p>Threading: callbacks fire on the game thread, at the moment of the event —
 * {@link #onDecision} right after the fed action has been consumed at a
 * pass-priority decision, {@link #onOutcome} exactly once per match after the
 * game thread has finished (so a consumer that observes {@code onOutcome} and
 * then returns from {@code BaselineEngine.awaitCompletion} sees every event).
 * Implementations must be non-blocking-safe on the game thread: a slow
 * callback stalls the match it observes.
 */
public interface BaselineObserver {

    /**
     * One pass-priority decision of the scripted player: the pending prompt
     * snapshot (state + engine-computed legal candidates) and the fed action
     * that answered it.
     */
    default void onDecision(BaselineDecision decision) {
    }

    /** The terminal match event: the recorded outcome and/or the game-thread failure. */
    default void onOutcome(BaselineMatchOutcome outcome) {
    }
}
