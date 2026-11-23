package forge.baseline.transport;

import forge.baseline.TestMatch;
import forge.baseline.state.BaselineDelta;
import forge.baseline.state.BaselineFormat;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        DeltaPacket packet = sync.collectDeltas(match.game.getView());
        BaselineDelta out = new BaselineDelta(packet.getSequenceNumber(),
                packet.getObjectDeltas(), packet.getNewObjects(),
                packet.getChecksum(), packet.getChecksumProperties());

        BaselineDelta in = roundTrip(
                new GuiGameEvent(ProtocolMethod.applyDelta, out), match.game.getTracker(), new Tracker());

        assertThat(in).isNotNull();
        BaselineFormat.checkVersion(in.getVersion());
        assertThat(in.getSequenceNumber()).isEqualTo(packet.getSequenceNumber());
        assertThat(in.getObjectDeltas()).isNotEmpty();

        int playerKey = BaselineFormat.makeKey(match.game.getView().getPlayers().get(0));
        Map<TrackableProperty, Object> playerDelta = in.getObjectDeltas().get(playerKey);
        assertThat(playerDelta)
                .as("life change for player key %s", Integer.toHexString(playerKey))
                .isNotNull()
                .containsEntry(TrackableProperty.Life, 19);
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
