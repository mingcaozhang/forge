package forge.baseline.state;

import forge.baseline.TestMatch;
import forge.card.ColorSet;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pending-prompt state: builder normalization, the snapshot
 * prompt field, and the format version.
 */
public class BaselinePromptShould {

    @Test
    public void builderNormalizesMissingCandidatesToEmptyLists() {
        BaselinePrompt prompt = BaselinePrompt.builder(BaselinePrompt.Kind.CONFIRM, 3).build();

        assertThat(prompt.getKind()).isEqualTo(BaselinePrompt.Kind.CONFIRM);
        assertThat(prompt.getPlayerId()).isEqualTo(3);
        assertThat(prompt.getMessage()).isNull();
        assertThat(prompt.getCardCandidates()).isEmpty();
        assertThat(prompt.getAbilityCandidates()).isEmpty();
        assertThat(prompt.getPlayerCandidates()).isEmpty();
        assertThat(prompt.getStackItemCandidates()).isEmpty();
        assertThat(prompt.getStringCandidates()).isEmpty();
        assertThat(prompt.getNumberCandidates()).isEmpty();
        assertThat((Object) prompt.getColorCandidates()).isNull();
        assertThat(prompt.getMinCount()).isZero();
        assertThat(prompt.getMaxCount()).isZero();
        assertThat(prompt.isOptional()).isFalse();
    }

    @Test
    public void builderCarriesEveryFieldAndReturnsImmutableLists() {
        TestMatch match = TestMatch.create();
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Lightning Bolt").getView();
        PlayerView playerView = match.game.getView().getPlayers().get(1);

        BaselinePrompt prompt = BaselinePrompt
                .builder(BaselinePrompt.Kind.SELECT_CARD, playerView.getId())
                .message("Choose a target")
                .cardCandidates(java.util.List.of(cardView))
                .stringCandidates(java.util.List.of("a", "b"))
                .numberCandidates(java.util.List.of(1, 2, 3))
                .colorCandidates(ColorSet.WU)
                .minCount(1)
                .maxCount(2)
                .optional(true)
                .build();

        assertThat(prompt.getMessage()).isEqualTo("Choose a target");
        assertThat(prompt.getCardCandidates()).containsExactly(cardView);
        assertThat(prompt.getStringCandidates()).containsExactly("a", "b");
        assertThat(prompt.getNumberCandidates()).containsExactly(1, 2, 3);
        assertThat((Object) prompt.getColorCandidates()).isEqualTo(ColorSet.WU);
        assertThat(prompt.getMinCount()).isEqualTo(1);
        assertThat(prompt.getMaxCount()).isEqualTo(2);
        assertThat(prompt.isOptional()).isTrue();

        assertThatThrownBy(() -> prompt.getCardCandidates().add(cardView))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> prompt.getStringCandidates().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void snapshotCarriesThePendingPrompt() {
        TestMatch match = TestMatch.create();
        GameView view = match.game.getView();
        CardView cardView = match.addCard(match.player1, ZoneType.Graveyard, "Lightning Bolt").getView();
        BaselinePrompt prompt = BaselinePrompt
                .builder(BaselinePrompt.Kind.SELECT_CARD, match.player1.getView().getId())
                .cardCandidates(java.util.List.of(cardView))
                .minCount(1)
                .maxCount(1)
                .build();

        BaselineSnapshot withPrompt = new BaselineSnapshot(7L, view, prompt);
        assertThat(withPrompt.hasPrompt()).isTrue();
        assertThat(withPrompt.getPrompt()).isSameAs(prompt);

        BaselineSnapshot without = new BaselineSnapshot(8L, view);
        assertThat(without.hasPrompt()).isFalse();
        assertThat(without.getPrompt()).isNull();
    }

    @Test
    public void promptFieldDoesNotChangeTheEnvelopeVersioning() {
        TestMatch match = TestMatch.create();
        BaselinePrompt prompt = BaselinePrompt.builder(BaselinePrompt.Kind.CONFIRM, 1).build();
        BaselineSnapshot snapshot = new BaselineSnapshot(1L, match.game.getView(), prompt);

        assertThat(snapshot.getVersion()).isEqualTo(BaselineFormat.VERSION);
        assertThatCode(() -> BaselineFormat.checkVersion(snapshot.getVersion()))
                .doesNotThrowAnyException();
    }
}
