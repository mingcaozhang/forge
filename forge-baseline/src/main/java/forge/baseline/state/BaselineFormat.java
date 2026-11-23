package forge.baseline.state;

import forge.card.CardStateName;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.trackable.TrackableObject;

/**
 * Version and wire-key contract for the baseline state format.
 *
 * <p>The key encoding intentionally mirrors the network {@code DeltaPacket} schema so
 * baseline snapshots and deltas stay byte-for-byte compatible with the existing
 * Trackable property protocol: upper 4 bits = type tag, lower 28 bits = object ID
 * (for {@link CardStateView} the composite id is {@code id * 16 + state.ordinal()}).</p>
 */
public final class BaselineFormat {

    /** Current baseline format version. Bump when the snapshot/delta schema changes incompatibly. */
    public static final int VERSION = 1;

    public static final int TYPE_CARD_VIEW = 0;
    public static final int TYPE_PLAYER_VIEW = 1;
    public static final int TYPE_STACK_ITEM_VIEW = 2;
    public static final int TYPE_GAME_VIEW = 3;
    public static final int TYPE_CSV = 4;

    static {
        if (CardStateName.values().length > 16) {
            throw new AssertionError("CardStateName has " + CardStateName.values().length
                    + " values; baseline delta key encoding supports at most 16");
        }
    }

    private BaselineFormat() {
    }

    public static int typeTagFor(TrackableObject obj) {
        if (obj == null) return -1;
        if (obj instanceof CardStateView) return TYPE_CSV;
        if (obj instanceof CardView) return TYPE_CARD_VIEW;
        if (obj instanceof PlayerView) return TYPE_PLAYER_VIEW;
        if (obj instanceof StackItemView) return TYPE_STACK_ITEM_VIEW;
        if (obj instanceof GameView) return TYPE_GAME_VIEW;
        return -1;
    }

    /** Composite baseline key: upper 4 bits = type (0-15), lower 28 bits = ID. */
    public static int makeKey(TrackableObject obj) {
        int type = typeTagFor(obj);
        int id = obj.getId();
        if (obj instanceof CardStateView csv) {
            id = id * 16 + csv.getState().ordinal();
        }
        return makeKey(type, id);
    }

    public static int makeKey(int type, int id) {
        return (type << 28) | (id & 0x0FFFFFFF);
    }

    public static int typeFromKey(int key) {
        return (key >>> 28) & 0xF;
    }

    public static int idFromKey(int key) {
        int id = key & 0x0FFFFFFF;
        if ((id & 0x08000000) != 0) {
            id |= 0xF0000000;
        }
        return id;
    }

    /** Reject envelopes stamped with an unsupported format version. */
    public static void checkVersion(int version) {
        if (version != VERSION) {
            throw new BaselineVersionException(version);
        }
    }
}
