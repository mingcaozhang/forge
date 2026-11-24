package forge.baseline.state;

import forge.game.GameView;
import forge.trackable.TrackableProperty;

import java.util.Map;

/**
 * Test-only access to the package-private version-stamped constructors, so round-trip
 * tests in other packages (e.g. {@code forge.baseline.transport}) can send envelopes
 * stamped with a version other than the current one.
 */
public final class BaselineFixtures {

    private BaselineFixtures() {
    }

    public static BaselineSnapshot snapshotWithVersion(int version, long sequenceNumber, GameView gameView) {
        return new BaselineSnapshot(version, sequenceNumber, gameView, null);
    }

    public static BaselineDelta deltaWithVersion(int version, long sequenceNumber,
                                                 Map<Integer, Map<TrackableProperty, Object>> objectDeltas,
                                                 Map<Integer, Map<TrackableProperty, Object>> newObjects,
                                                 int checksum, int[] checksumProperties) {
        return new BaselineDelta(version, sequenceNumber, objectDeltas, newObjects, checksum, checksumProperties, null);
    }
}
