package forge.baseline.transport;

import forge.baseline.TestMatch;
import forge.baseline.state.BaselineDelta;
import forge.baseline.state.BaselineFixtures;
import forge.baseline.state.BaselineFormat;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;
import forge.baseline.state.BaselineVersionException;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import forge.gamemodes.net.CompatibleObjectDecoder;
import forge.gamemodes.net.CompatibleObjectEncoder;
import forge.gamemodes.net.DeltaPacket;
import forge.gamemodes.net.ProtocolMethod;
import forge.gamemodes.net.event.GuiGameEvent;
import forge.gamemodes.net.server.DeltaSyncManager;
import forge.trackable.TrackableCollection;
import forge.trackable.TrackableObject;
import forge.trackable.TrackableProperty;
import forge.trackable.TrackableTypes;
import forge.trackable.Tracker;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the baseline envelopes travel through the existing Netty object codec the same
 * way the network protocol sends them: full snapshots wrapped in {@code setGameView}
 * (no reference replacement) and deltas wrapped in {@code applyDelta}.
 */
public class BaselineTransportShould {

    @Test
    public void keyEncodingMatchesTheNetProtocol() {
        TestMatch match = TestMatch.create();
        GameView view = match.game.getView();
        PlayerView playerView = view.getPlayers().get(0);
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Bolt").getView();
        CardStateView csv = cardView.getCurrentState();
        StackItemView stackItemView = new StackItemView(99, match.game.getTracker());

        TrackableObject[] objects = {view, playerView, cardView, stackItemView, csv};
        for (TrackableObject obj : objects) {
            assertThat(BaselineFormat.makeKey(obj))
                    .as("key for %s", obj.getClass().getSimpleName())
                    .isEqualTo(DeltaPacket.makeDeltaKey(obj));
            assertThat(BaselineFormat.typeTagFor(obj)).isEqualTo(DeltaPacket.typeTagFor(obj));
        }

        for (int type = 0; type <= BaselineFormat.TYPE_CSV; type++) {
            for (int id : new int[]{-1, 0, 1, 4096, 0x0FFFFFFF, -0x0FFFFFFF}) {
                assertThat(BaselineFormat.makeKey(type, id))
                        .as("composite key for type %d id %d", type, id)
                        .isEqualTo(DeltaPacket.makeDeltaKey(type, id));
            }
        }
    }

    @Test
    public void snapshotRoundTripsThroughTheKeptCodec() throws Exception {
        TestMatch match = TestMatch.create();
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Lightning Bolt").getView();
        BaselineSnapshot out = new BaselineSnapshot(42L, match.game.getView());

        BaselineSnapshot in = roundTrip(
                new GuiGameEvent(ProtocolMethod.setGameView, out), match.game.getTracker(), null);

        assertThat(in).isNotNull();
        BaselineFormat.checkVersion(in.getVersion());
        assertThat(in.getSequenceNumber()).isEqualTo(42L);

        GameView view = in.getGameView();
        assertThat(view).isNotSameAs(out.getGameView());
        if (view.getGameLog() == null) {
            view.initGameLog();
        }
        reattachTracker(view);

        Tracker tracker = view.getTracker();
        assertThat(view.getPlayers()).hasSize(2);
        CardView inCard = view.getPlayers().get(0).getGraveyard().get(0);
        assertThat(inCard.getId()).isEqualTo(cardView.getId());
        assertThat(inCard.getName()).isEqualTo("Lightning Bolt");
        assertThat(inCard.getZone()).isEqualTo(ZoneType.Graveyard);
        assertThat(tracker.getObj(TrackableTypes.CardViewType, cardView.getId())).isSameAs(inCard);
    }

    @Test
    public void snapshotWithPromptRoundTripsThroughTheKeptCodec() throws Exception {
        TestMatch match = TestMatch.create();
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Lightning Bolt").getView();
        BaselinePrompt prompt = BaselinePrompt
                .builder(BaselinePrompt.Kind.SELECT_CARD, match.player1.getView().getId())
                .message("Choose a target")
                .cardCandidates(List.of(cardView))
                .minCount(1)
                .maxCount(1)
                .build();
        BaselineSnapshot out = new BaselineSnapshot(11L, match.game.getView(), prompt);

        BaselineSnapshot in = roundTrip(
                new GuiGameEvent(ProtocolMethod.setGameView, out), match.game.getTracker(), null);

        assertThat(in).isNotNull();
        assertThat(in.hasPrompt()).isTrue();
        BaselinePrompt inPrompt = in.getPrompt();
        assertThat(inPrompt.getKind()).isEqualTo(BaselinePrompt.Kind.SELECT_CARD);
        assertThat(inPrompt.getPlayerId()).isEqualTo(match.player1.getView().getId());
        assertThat(inPrompt.getMessage()).isEqualTo("Choose a target");
        assertThat(inPrompt.getMinCount()).isEqualTo(1);
        assertThat(inPrompt.getMaxCount()).isEqualTo(1);
        // setGameView carries full state (no IdRef replacement), so candidates come back
        // as fresh instances with the same identity.
        assertThat(inPrompt.getCardCandidates()).hasSize(1);
        assertThat(inPrompt.getCardCandidates().get(0).getId()).isEqualTo(cardView.getId());
        assertThat(inPrompt.getCardCandidates().get(0)).isNotSameAs(cardView);
    }

    @Test
    public void deltaRoundTripsThroughTheKeptCodec() throws Exception {
        TestMatch match = TestMatch.create();
        match.addCard(match.player1, ZoneType.Graveyard, "Lightning Bolt");

        DeltaSyncManager sync = new DeltaSyncManager();
        DeltaPacket baseline = sync.collectDeltas(match.game.getView());
        assertThat(baseline.getNewObjects()).as("first walk registers every object").isNotEmpty();

        match.player1.setLife(19, null);
        match.addCard(match.player1, ZoneType.Graveyard, "Shock");

        DeltaPacket packet = sync.collectDeltas(match.game.getView());
        assertThat(packet.getObjectDeltas()).as("life + graveyard changes are dirty").isNotEmpty();
        assertThat(packet.getNewObjects()).as("the new card is registered").isNotEmpty();

        BaselineDelta out = new BaselineDelta(packet.getSequenceNumber(),
                packet.getObjectDeltas(), packet.getNewObjects(),
                packet.getChecksum(), packet.getChecksumProperties());

        BaselineDelta in = roundTrip(
                new GuiGameEvent(ProtocolMethod.applyDelta, out), match.game.getTracker(), new Tracker());

        assertThat(in).isNotNull();
        BaselineFormat.checkVersion(in.getVersion());
        assertThat(in.getSequenceNumber()).isEqualTo(packet.getSequenceNumber());
        assertThat(in.getObjectDeltas()).isNotEmpty();
        // Full-envelope equality: the wire-safe property maps and checksum fields come back intact
        assertWireMapEqual("objectDeltas", packet.getObjectDeltas(), in.getObjectDeltas());
        assertWireMapEqual("newObjects", packet.getNewObjects(), in.getNewObjects());
        assertThat(in.getChecksum()).isEqualTo(packet.getChecksum());
        assertThat(in.getChecksumProperties()).isEqualTo(packet.getChecksumProperties());

        int playerKey = BaselineFormat.makeKey(match.game.getView().getPlayers().get(0));
        Map<TrackableProperty, Object> playerDelta = in.getObjectDeltas().get(playerKey);
        assertThat(playerDelta)
                .as("life change for player key %s", Integer.toHexString(playerKey))
                .isNotNull()
                .containsEntry(TrackableProperty.Life, 19);
    }

    // Fails if the version stamp does not survive serialization, or if a deserialized
    // envelope stamped with another version is not rejected by the format gate
    @Test
    public void snapshotWithMismatchedVersionSurvivesTheWireAndIsRejected() throws Exception {
        for (int version : new int[]{BaselineFormat.VERSION - 1, BaselineFormat.VERSION + 1}) {
            TestMatch match = TestMatch.create();
            BaselineSnapshot out = BaselineFixtures.snapshotWithVersion(version, 42L, match.game.getView());

            BaselineSnapshot in = roundTrip(
                    new GuiGameEvent(ProtocolMethod.setGameView, out), match.game.getTracker(), null);

            assertThat(in)
                    .as("a snapshot stamped with version %d", version)
                    .isNotNull();
            assertThat(in.getVersion()).as("the version stamp is payload and must survive the wire").isEqualTo(version);
            assertThat(in.getSequenceNumber()).isEqualTo(42L);
            assertThatThrownBy(() -> BaselineFormat.checkVersion(in.getVersion()))
                    .as("the deserialized envelope must be rejected")
                    .isInstanceOf(BaselineVersionException.class)
                    .hasMessageContaining(String.valueOf(BaselineFormat.VERSION));
        }
    }

    @Test
    public void deltaWithMismatchedVersionSurvivesTheWireAndIsRejected() throws Exception {
        for (int version : new int[]{BaselineFormat.VERSION - 1, BaselineFormat.VERSION + 1}) {
            TestMatch match = TestMatch.create();
            BaselineDelta out = BaselineFixtures.deltaWithVersion(version, 7L, null, null, 0, null);

            BaselineDelta in = roundTrip(
                    new GuiGameEvent(ProtocolMethod.applyDelta, out), match.game.getTracker(), new Tracker());

            assertThat(in)
                    .as("a delta stamped with version %d", version)
                    .isNotNull();
            assertThat(in.getVersion()).as("the version stamp is payload and must survive the wire").isEqualTo(version);
            assertThat(in.getSequenceNumber()).isEqualTo(7L);
            assertThatThrownBy(() -> BaselineFormat.checkVersion(in.getVersion()))
                    .as("the deserialized envelope must be rejected")
                    .isInstanceOf(BaselineVersionException.class)
                    .hasMessageContaining(String.valueOf(BaselineFormat.VERSION));
        }
    }

    /**
     * Field-by-field equality over the wire-safe property maps. Values are
     * Integer/enum/String/List/primitive-array; int[] has no value-based equals,
     * so it is compared explicitly.
     */
    private static void assertWireMapEqual(String what, Map<Integer, Map<TrackableProperty, Object>> expected,
                                           Map<Integer, Map<TrackableProperty, Object>> actual) {
        assertThat(actual).as(what).hasSameSizeAs(expected);
        for (Map.Entry<Integer, Map<TrackableProperty, Object>> entry : expected.entrySet()) {
            Map<TrackableProperty, Object> expectedProps = entry.getValue();
            Map<TrackableProperty, Object> actualProps = actual.get(entry.getKey());
            assertThat(actualProps)
                    .as("%s entry 0x%08X", what, entry.getKey())
                    .isNotNull()
                    .hasSameSizeAs(expectedProps);
            for (Map.Entry<TrackableProperty, Object> prop : expectedProps.entrySet()) {
                assertWireValueEqual(what + " entry 0x" + Integer.toHexString(entry.getKey()) + " " + prop.getKey(),
                        prop.getValue(), actualProps.get(prop.getKey()));
            }
        }
    }

    private static void assertWireValueEqual(String what, Object expected, Object actual) {
        if (expected == null) {
            assertThat(actual).as(what).isNull();
        } else if (expected instanceof int[]) {
            assertThat(actual).as(what).isInstanceOf(int[].class);
            assertThat((int[]) actual).as(what).isEqualTo((int[]) expected);
        } else {
            // The wire format is Java serialization itself: Serializable values without
            // value-based equals (e.g. CardType) round-trip iff their serialized forms
            // are byte-identical
            assertThat(actual).as(what).isInstanceOf(expected.getClass());
            assertThat(canonicalBytes(actual)).as(what).isEqualTo(canonicalBytes(expected));
        }
    }

    /**
     * Canonical wire bytes of a value: serialize it, deserialize the result, and serialize
     * the fresh copy again. The extra hop normalizes the serialized form of collections
     * whose internal state depends on mutation history (e.g. a {@code CardType} whose
     * {@code LinkedHashSet} was resized mid-game), so values without value-based equals
     * compare by their wire state, not by the object's life on this side of the wire.
     */
    private static byte[] canonicalBytes(Object value) {
        try {
            return serialize(deserialize(serialize(value)));
        } catch (IOException | ClassNotFoundException e) {
            throw new UncheckedIOException(e instanceof IOException io ? io : new IOException(e));
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return bos.toByteArray();
        }
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    /** Encodes through a host encoder wired to the game's tracker and decodes into a fresh tracker. */
    private static <T> T roundTrip(GuiGameEvent event, Tracker hostTracker, Tracker freshTracker) throws Exception {
        CompatibleObjectEncoder host = new CompatibleObjectEncoder();
        host.setTracker(hostTracker);

        CompatibleObjectDecoder decoder = new CompatibleObjectDecoder(9766 * 1024, ClassResolvers.cacheDisabled(null));
        if (freshTracker != null) {
            decoder.setTracker(freshTracker);
        }
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        try {
            channel.writeInbound(host.encodeToBuf(event, ByteBufAllocator.DEFAULT));
            channel.checkException();
            GuiGameEvent in = (GuiGameEvent) channel.readInbound();
            assertThat(in).as("a decoded event").isNotNull();
            assertThat(in.getMethod()).isEqualTo(event.getMethod());
            return (T) in.getObjects()[0];
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Recursively point the transient tracker references of a deserialized view graph at a
     * fresh tracker, mirroring what the network client does after receiving a full view.
     */
    private static void reattachTracker(GameView view) {
        Tracker tracker = view.getTracker() != null ? view.getTracker() : new Tracker();
        updateTrackers(tracker, view);
        view.updateObjLookup();
    }

    private static void updateTrackers(Tracker tracker, Object obj) {
        if (obj instanceof TrackableObject trackableObject) {
            if (trackableObject.getTracker() == null) {
                trackableObject.setTracker(tracker);
                Map<?, ?> props = trackableObject.getProps();
                if (props != null) {
                    for (Object propObj : props.values()) {
                        updateTrackers(tracker, propObj);
                    }
                }
            }
        } else if (obj instanceof TrackableCollection<?> collection) {
            for (Object element : collection) {
                updateTrackers(tracker, element);
            }
        }
    }
}
