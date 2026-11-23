package forge.baseline.action;

import forge.baseline.TestMatch;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.DrawOfferMessage;
import forge.gamemodes.match.YieldUpdate;
import forge.gamemodes.net.CompatibleObjectDecoder;
import forge.gamemodes.net.CompatibleObjectEncoder;
import forge.gamemodes.net.ProtocolMethod;
import forge.interfaces.IGameController;
import forge.trackable.Tracker;
import forge.util.ITriggerEvent;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the canonical action model: the 1:1 mapping onto
 * {@code IGameController} / {@code ProtocolMethod} (machine-checked against the
 * live protocol enum) and round-trips through the kept Netty object codec.
 */
public class BaselineActionShould {

    private record Mapping(Class<? extends BaselineAction> type, String protocolName, Class<?>[] protocolArgTypes) {}

    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping(BaselineAction.SelectCard.class, "selectCard",
                    new Class<?>[]{CardView.class, List.class, ITriggerEvent.class}),
            new Mapping(BaselineAction.SelectAbility.class, "selectAbility",
                    new Class<?>[]{SpellAbilityView.class}),
            new Mapping(BaselineAction.SelectPlayer.class, "selectPlayer",
                    new Class<?>[]{PlayerView.class, ITriggerEvent.class}),
            new Mapping(BaselineAction.SelectStackItem.class, "selectStackItem",
                    new Class<?>[]{StackItemView.class}),
            new Mapping(BaselineAction.Ok.class, "selectButtonOk", new Class<?>[]{}),
            new Mapping(BaselineAction.Cancel.class, "selectButtonCancel", new Class<?>[]{}),
            new Mapping(BaselineAction.Concede.class, "concede", new Class<?>[]{}),
            new Mapping(BaselineAction.Yield.class, "sendYieldUpdate",
                    new Class<?>[]{YieldUpdate.class}),
            new Mapping(BaselineAction.NextGameDecision.class, "nextGameDecision",
                    new Class<?>[]{forge.gamemodes.match.NextGameDecision.class}),
            new Mapping(BaselineAction.UseMana.class, "useMana", new Class<?>[]{Byte.TYPE}),
            new Mapping(BaselineAction.AlphaStrike.class, "alphaStrike", new Class<?>[]{}),
            new Mapping(BaselineAction.ReorderHand.class, "reorderHand",
                    new Class<?>[]{CardView.class, Integer.TYPE}),
            new Mapping(BaselineAction.UndoLastAction.class, "undoLastAction", new Class<?>[]{}),
            new Mapping(BaselineAction.DrawOffer.class, "drawOfferAction",
                    new Class<?>[]{DrawOfferMessage.Action.class}),
            new Mapping(BaselineAction.RequestResync.class, "requestResync", new Class<?>[]{}));

    @Test
    public void everyVariantMapsOntoAnIGameControllerMethodAndProtocolMethod() {
        for (Mapping m : MAPPINGS) {
            ProtocolMethod pm = ProtocolMethod.valueOf(m.protocolName());
            Method controllerMethod = pm.getMethod();

            assertThat(controllerMethod)
                    .as("ProtocolMethod.%s must resolve on IGameController", m.protocolName())
                    .isNotNull();
            assertThat(controllerMethod.getDeclaringClass())
                    .as("ProtocolMethod.%s must be a client (IGameController) method", m.protocolName())
                    .isEqualTo(IGameController.class);
            assertThat(controllerMethod.getName())
                    .as("ProtocolMethod.%s must keep its name", m.protocolName())
                    .isEqualTo(m.protocolName());
            assertThat(controllerMethod.getParameterCount())
                    .as("ProtocolMethod.%s must have resolved with its full arg list (no no-arg fallback)", m.protocolName())
                    .isEqualTo(pm.getArgTypes().length);
            assertThat(pm.getArgTypes())
                    .as("ProtocolMethod.%s arg types", m.protocolName())
                    .containsExactly(m.protocolArgTypes());

            RecordComponent[] components = m.type().getRecordComponents();
            assertThat(components.length)
                    .as("%s must carry only decision-relevant args", m.type().getSimpleName())
                    .isLessThanOrEqualTo(pm.getArgTypes().length);
            for (int i = 0; i < components.length; i++) {
                assertThat(pm.getArgTypes()[i].isAssignableFrom(components[i].getType()))
                        .as("ProtocolMethod.%s arg %d must accept the %s component type %s",
                                m.protocolName(), i, m.type().getSimpleName(), components[i].getType())
                        .isTrue();
            }
        }
    }

    @Test
    public void theActionSetCoversTheWholeClientProtocolSurface() {
        Set<String> clientSurface = Arrays.stream(ProtocolMethod.values())
                .map(ProtocolMethod::getMethod)
                .filter(Objects::nonNull)
                .filter(m -> m.getDeclaringClass() == IGameController.class)
                .map(Method::getName)
                .collect(Collectors.toSet());

        // The 15 mapped actions plus getActivateDescription, which is a query (returns a
        // String) rather than a state transition and is therefore outside the action model.
        assertThat(clientSurface).containsExactlyInAnyOrder(
                "useMana", "undoLastAction", "selectPlayer", "selectCard", "selectButtonOk",
                "selectButtonCancel", "selectAbility", "selectStackItem", "nextGameDecision",
                "getActivateDescription", "concede", "drawOfferAction", "alphaStrike",
                "reorderHand", "requestResync", "sendYieldUpdate");
    }

    @Test
    public void theHierarchyIsClosedWithFifteenRecordVariants() {
        Class<?>[] permitted = BaselineAction.class.getPermittedSubclasses();
        assertThat(permitted).hasSize(15);
        for (Class<?> variant : permitted) {
            assertThat(variant.isRecord())
                    .as("%s must be a record", variant.getSimpleName())
                    .isTrue();
            assertThat(BaselineAction.class.isAssignableFrom(variant))
                    .isTrue();
        }
    }

    @Test
    public void everyConstructedActionBelongsToExactlyOneClosedVariant() {
        List<BaselineAction> all = List.of(
                new BaselineAction.SelectCard(null, List.of()),
                new BaselineAction.SelectAbility(null),
                new BaselineAction.SelectPlayer(null),
                new BaselineAction.SelectStackItem(null),
                new BaselineAction.Ok(),
                new BaselineAction.Cancel(),
                new BaselineAction.Concede(),
                new BaselineAction.Yield(new YieldUpdate.SetDisableYields(false)),
                new BaselineAction.NextGameDecision(forge.gamemodes.match.NextGameDecision.NEW),
                new BaselineAction.UseMana((byte) 1),
                new BaselineAction.AlphaStrike(),
                new BaselineAction.ReorderHand(null, 0),
                new BaselineAction.UndoLastAction(),
                new BaselineAction.DrawOffer(DrawOfferMessage.Action.ACCEPT),
                new BaselineAction.RequestResync());

        assertThat(all).hasSize(15);
        Set<String> variantNames = Arrays.stream(BaselineAction.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
        assertThat(all.stream().map(a -> a.getClass().getSimpleName()).collect(Collectors.toSet()))
                .as("each instance must belong to exactly one permitted variant")
                .containsExactlyInAnyOrderElementsOf(variantNames);
    }

    @Test
    public void actionsRoundTripThroughTheKeptCodec() throws Exception {
        TestMatch match = TestMatch.create();
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Lightning Bolt").getView();
        PlayerView playerView = match.game.getView().getPlayers().get(1);
        StackItemView stackItemView = new StackItemView(99, match.game.getTracker());

        List<BaselineAction> actions = List.of(
                new BaselineAction.Ok(),
                new BaselineAction.Cancel(),
                new BaselineAction.Concede(),
                new BaselineAction.AlphaStrike(),
                new BaselineAction.UndoLastAction(),
                new BaselineAction.RequestResync(),
                new BaselineAction.SelectCard(cardView, List.of()),
                new BaselineAction.SelectPlayer(playerView),
                new BaselineAction.SelectStackItem(stackItemView),
                new BaselineAction.ReorderHand(cardView, 2),
                new BaselineAction.UseMana((byte) 2),
                new BaselineAction.NextGameDecision(forge.gamemodes.match.NextGameDecision.NEW),
                new BaselineAction.DrawOffer(DrawOfferMessage.Action.DECLINE),
                new BaselineAction.Yield(new YieldUpdate.CardAutoYield("key", true)),
                new BaselineAction.Yield(new YieldUpdate.SetMarker(playerView, PhaseType.MAIN1, true)));

        for (BaselineAction action : actions) {
            BaselineAction in = roundTrip(action, match.game.getTracker());
            assertThat(in)
                    .as("round-trip of %s", action)
                    .isNotNull()
                    .isEqualTo(action);
        }

        // Tracker-resolved references must come back as the very same view instances.
        BaselineAction.SelectCard inSelect = (BaselineAction.SelectCard) roundTrip(
                new BaselineAction.SelectCard(cardView, List.of(cardView)), match.game.getTracker());
        assertThat(inSelect.card()).isSameAs(cardView);
        assertThat(inSelect.otherCards()).containsExactly(cardView);
    }

    /** Encodes the action with the game's tracker (IdRef substitution) and resolves it back from the same tracker. */
    private static BaselineAction roundTrip(BaselineAction action, Tracker tracker) throws Exception {
        CompatibleObjectEncoder host = new CompatibleObjectEncoder();
        host.setTracker(tracker);

        CompatibleObjectDecoder decoder = new CompatibleObjectDecoder(9766 * 1024, ClassResolvers.cacheDisabled(null));
        decoder.setTracker(tracker);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        try {
            channel.writeInbound(host.encodeToBuf(action, ByteBufAllocator.DEFAULT));
            channel.checkException();
            return (BaselineAction) channel.readInbound();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
