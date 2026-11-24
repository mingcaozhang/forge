package forge.baseline.engine;

import forge.game.GameOutcome;

import java.io.Serializable;

/**
 * The terminal event of one baseline match: the recorded
 * {@link GameOutcome} (null when the game thread died) and the
 * {@link #failure() failure} (null for a cleanly finished match).
 *
 * <p>UI-agnostic: it only references the Swing-free engine outcome model.
 */
public record BaselineMatchOutcome(long seed, GameOutcome outcome, Throwable failure) implements Serializable {
    private static final long serialVersionUID = 1L;

    /** True when the game ran to a recorded outcome without a game-thread failure. */
    public boolean isCompleted() {
        return failure == null && outcome != null;
    }
}
