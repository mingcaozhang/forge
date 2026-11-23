package forge.baseline.state;

import forge.baseline.TestMatch;
import forge.card.CardStateName;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the UI-agnostic baseline state contract, with no forge-gui (or Swing)
 * references.
 */
public class BaselineStateShould {

    @Test
    public void snapshotIsStampedWithTheCurrentFormatVersion() {
        GameView view = TestMatch.create().game.getView();

        BaselineSnapshot snapshot = new BaselineSnapshot(42L, view);

        assertThat(snapshot.getVersion()).isEqualTo(BaselineFormat.VERSION);
        assertThat(snapshot.getSequenceNumber()).isEqualTo(42L);
        assertThat(snapshot.getGameView()).isSameAs(view);
        assertThatCode(() -> BaselineFormat.checkVersion(snapshot.getVersion())).doesNotThrowAnyException();
    }

    @Test
    public void deltaIsStampedWithTheCurrentFormatVersion() {
        BaselineDelta delta = new BaselineDelta(7L, null, null, 0, null);

        assertThat(delta.getVersion()).isEqualTo(BaselineFormat.VERSION);
        assertThat(delta.getSequenceNumber()).isEqualTo(7L);
        assertThat(delta.getObjectDeltas()).isEmpty();
        assertThat(delta.getNewObjects()).isEmpty();
        assertThat(delta.hasChecksum()).isFalse();
        assertThat(delta.hasEvents()).isFalse();
        assertThat(delta.isEmpty()).isTrue();
        assertThatCode(() -> BaselineFormat.checkVersion(delta.getVersion())).doesNotThrowAnyException();
    }

    @Test
    public void versionMismatchIsRejected() {
        BaselineSnapshot future = new BaselineSnapshot(BaselineFormat.VERSION + 1, 1L, TestMatch.create().game.getView(), null);

        assertThatThrownBy(() -> BaselineFormat.checkVersion(future.getVersion()))
                .isInstanceOf(BaselineVersionException.class)
                .hasMessageContaining(String.valueOf(BaselineFormat.VERSION));
    }

    @Test
    public void typeTagRecognizesAllFiveViewKinds() {
        TestMatch match = TestMatch.create();
        GameView view = match.game.getView();
        PlayerView playerView = view.getPlayers().get(0);
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Bolt").getView();
        CardStateView csv = cardView.getCurrentState();
        StackItemView stackItemView = new StackItemView(99, match.game.getTracker());

        assertThat(BaselineFormat.typeTagFor(view)).isEqualTo(BaselineFormat.TYPE_GAME_VIEW);
        assertThat(BaselineFormat.typeTagFor(playerView)).isEqualTo(BaselineFormat.TYPE_PLAYER_VIEW);
        assertThat(BaselineFormat.typeTagFor(cardView)).isEqualTo(BaselineFormat.TYPE_CARD_VIEW);
        assertThat(BaselineFormat.typeTagFor(stackItemView)).isEqualTo(BaselineFormat.TYPE_STACK_ITEM_VIEW);
        assertThat(BaselineFormat.typeTagFor(csv)).isEqualTo(BaselineFormat.TYPE_CSV);
        assertThat(BaselineFormat.typeTagFor(null)).isEqualTo(-1);
    }

    @Test
    public void keyRoundTripsTypeAndIdIncludingNegativeIds() {
        // The lower 28 bits are a signed id (bit 27 is the sign), like the net protocol's
        for (int type = 0; type <= BaselineFormat.TYPE_CSV; type++) {
            for (int id : new int[]{-1, 0, 1, 4096, 0x07FFFFFF, -0x08000000}) {
                int key = BaselineFormat.makeKey(type, id);
                assertThat(BaselineFormat.typeFromKey(key))
                        .as("type for %d/%d", type, id)
                        .isEqualTo(type);
                assertThat(BaselineFormat.idFromKey(key))
                        .as("id for %d/%d", type, id)
                        .isEqualTo(id);
            }
        }
    }

    @Test
    public void csvKeyFoldsTheStateOrdinal() {
        TestMatch match = TestMatch.create();
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Bolt").getView();
        CardStateView csv = cardView.getCurrentState();
        CardStateName state = csv.getState();

        int key = BaselineFormat.makeKey(csv);

        assertThat(BaselineFormat.typeFromKey(key)).isEqualTo(BaselineFormat.TYPE_CSV);
        assertThat(key)
                .isEqualTo(BaselineFormat.makeKey(BaselineFormat.TYPE_CSV, cardView.getId() * 16 + state.ordinal()));
        assertThat(BaselineFormat.makeKey(csv)).isNotEqualTo(BaselineFormat.makeKey(cardView));
    }

    @Test
    public void csvKeySpaceRequiresAtMostSixteenStates() {
        assertThat(CardStateName.values().length).isLessThanOrEqualTo(16);
    }
}
