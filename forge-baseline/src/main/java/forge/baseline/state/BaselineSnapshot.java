package forge.baseline.state;

import forge.game.GameView;

import java.io.Serializable;

/**
 * Full-state baseline envelope: the complete Trackable view graph rooted at a
 * {@link GameView}. Carries a format {@link #getVersion() version} and the
 * {@link #getSequenceNumber() sequence number} of the last applied delta, so a
 * receiver can reconcile against its own view.
 *
 * <p>This class is UI-agnostic: it only references the Swing-free game view model.</p>
 */
public final class BaselineSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int version;
    private final long sequenceNumber;
    private final GameView gameView;

    public BaselineSnapshot(long sequenceNumber, GameView gameView) {
        this(BaselineFormat.VERSION, sequenceNumber, gameView);
    }

    BaselineSnapshot(int version, long sequenceNumber, GameView gameView) {
        this.version = version;
        this.sequenceNumber = sequenceNumber;
        this.gameView = gameView;
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

    @Override
    public String toString() {
        return "BaselineSnapshot[v" + version + ", seq=" + sequenceNumber + ", " + gameView + "]";
    }
}
