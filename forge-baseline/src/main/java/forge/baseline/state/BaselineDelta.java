package forge.baseline.state;

import forge.trackable.TrackableProperty;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Incremental baseline envelope over the existing Trackable delta schema:
 * changed properties for already-known objects ({@link #getObjectDeltas()}) and
 * full property maps for newly-introduced objects ({@link #getNewObjects()}).
 *
 * <p>Property maps are {@code Map<TrackableProperty, Object>} with Integer ID
 * substitution for object references (the same wire-safe value shape the
 * network delta sync produces).</p>
 *
 * <p>This class is UI-agnostic: it only references the Swing-free trackable model.</p>
 */
public final class BaselineDelta implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int version;
    private final long sequenceNumber;
    private final Map<Integer, Map<TrackableProperty, Object>> objectDeltas;
    private final Map<Integer, Map<TrackableProperty, Object>> newObjects;
    private final int checksum;
    private final int[] checksumProperties;
    private final List<Object> events;

    public BaselineDelta(long sequenceNumber,
                         Map<Integer, Map<TrackableProperty, Object>> objectDeltas,
                         Map<Integer, Map<TrackableProperty, Object>> newObjects,
                         int checksum, int[] checksumProperties) {
        this(BaselineFormat.VERSION, sequenceNumber, objectDeltas, newObjects, checksum, checksumProperties, null);
    }

    BaselineDelta(int version, long sequenceNumber,
                  Map<Integer, Map<TrackableProperty, Object>> objectDeltas,
                  Map<Integer, Map<TrackableProperty, Object>> newObjects,
                  int checksum, int[] checksumProperties, List<Object> events) {
        this.version = version;
        this.sequenceNumber = sequenceNumber;
        this.objectDeltas = objectDeltas != null ? objectDeltas : Collections.emptyMap();
        this.newObjects = newObjects != null ? newObjects : Collections.emptyMap();
        this.checksum = checksum;
        this.checksumProperties = checksumProperties;
        this.events = events;
    }

    public int getVersion() {
        return version;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public Map<Integer, Map<TrackableProperty, Object>> getObjectDeltas() {
        return Collections.unmodifiableMap(objectDeltas);
    }

    public Map<Integer, Map<TrackableProperty, Object>> getNewObjects() {
        return Collections.unmodifiableMap(newObjects);
    }

    public int getChecksum() {
        return checksum;
    }

    public boolean hasChecksum() {
        return checksumProperties != null;
    }

    /** Property ordinals included in the sampled checksum, or null for legacy checksum. */
    public int[] getChecksumProperties() {
        return checksumProperties;
    }

    public List<Object> getEvents() {
        return events;
    }

    public boolean hasEvents() {
        return events != null && !events.isEmpty();
    }

    public boolean isEmpty() {
        return objectDeltas.isEmpty() && newObjects.isEmpty() && !hasEvents() && !hasChecksum();
    }

    @Override
    public String toString() {
        return "BaselineDelta[v" + version + ", seq=" + sequenceNumber
                + ", newObjects=" + newObjects.size() + ", deltas=" + objectDeltas.size() + "]";
    }
}
