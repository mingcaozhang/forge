package forge.baseline.state;

import forge.game.GameView;

import java.io.Serializable;

/**
 * Full-state baseline envelope: the complete Trackable view graph rooted at a
 * {@link GameView}, plus the pending prompt (if any) the engine is blocked on.
 * Carries a format {@link #getVersion() version} and the
 * {@link #getSequenceNumber() sequence number} of the last applied delta, so a
 * receiver can reconcile against its own view.
 *
 * <p>This class is UI-agnostic: it only references the Swing-free game view model.
 */
public final class BaselineSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int version;
    private final long sequenceNumber;
    private final GameView gameView;
    private final BaselinePrompt prompt;

    public BaselineSnapshot(long sequenceNumber, GameView gameView) {
        this(BaselineFormat.VERSION, sequenceNumber, gameView, null);
    }

    public BaselineSnapshot(long sequenceNumber, GameView gameView, BaselinePrompt prompt) {
        this(BaselineFormat.VERSION, sequenceNumber, gameView, prompt);
    }

    BaselineSnapshot(int version, long sequenceNumber, GameView gameView, BaselinePrompt prompt) {
        this.version = version;
        this.sequenceNumber = sequenceNumber;
        this.gameView = gameView;
        this.prompt = prompt;
    }

    public int getVersion() {
        return version;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public GameView getGameView() {
        return gameView;
    }

    /** The prompt the engine is blocked on, or null when the engine is not waiting for input. */
    public BaselinePrompt getPrompt() {
        return prompt;
    }

    public boolean hasPrompt() {
        return prompt != null;
    }

    @Override
    public String toString() {
        return "BaselineSnapshot[v" + version + ", seq=" + sequenceNumber
                + ", " + gameView + (prompt != null ? ", " + prompt : "") + "]";
    }
}
