package forge.baseline.engine;

import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;
import forge.game.GameOutcome;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the 28.2 prompt boundary: before the adapter blocks waiting for the
 * next fed action, it emits a snapshot carrying the pending prompt with the
 * engine-computed legal candidates; choices are accepted as actions, and
 * actions outside the legal candidates are rejected as passes. The game stays
 * headless (no UI, no latch path) from start to finish.
 *
 * <p>Rig: one scripted player plus one AI, each with a 20-card Commander deck
 * (one 1-cost creature commander plus nineteen basic islands). At a
 * main-phase priority the legal candidates are the island drops in hand plus
 * the commander's cast from the command zone (the Commander format grants it
 * through the engine's "Commander Effect" static ability; mana availability
 * is only checked at payment time, so it is a candidate even with an empty
 * mana pool — the same set the engine offers in the human flow).
 */
public class BaselinePromptBoundaryShould {

    private static final long SEED = 20261124L;

    @Test
    public void shouldExposeThePendingPromptWithItsLegalCandidates() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        engine.start();
        EngineRig.awaitInitialSnapshot(engine);

        BaselineSnapshot snap = awaitMainPhasePrompt(engine);
        BaselinePrompt prompt = snap.getPrompt();
        PlayerView scriptedView = snap.getGameView().getPlayers().get(0);

        assertThat(prompt.getKind()).isEqualTo(BaselinePrompt.Kind.SELECT_ABILITY);
        assertThat(prompt.getPlayerId()).as("the asked player").isEqualTo(scriptedView.getId());
        assertThat(prompt.getMessage()).as("this family has no engine prompt text").isNull();
        assertThat(prompt.isOptional()).as("the prompt can be passed").isTrue();
        assertThat(prompt.getMinCount()).isZero();
        assertThat(prompt.getMaxCount()).isZero();
        assertThat(prompt.getPlayerCandidates()).isEmpty();
        assertThat(prompt.getStackItemCandidates()).isEmpty();

        List<CardView> islandsInHand = landsInOrder(scriptedView.getHand());
        CardView commander = scriptedView.getCommanders().get(0);
        assertThat(commander.getCurrentState().isLand()).isFalse();

        // one drop ability per island in hand (card-id order), then the
        // commander's cast from the command zone
        List<SpellAbilityView> abilities = prompt.getAbilityCandidates();
        assertThat(abilities).as("legal candidates were computed").hasSize(islandsInHand.size() + 1);
        for (int i = 0; i < islandsInHand.size(); i++) {
            assertThat(abilities.get(i).getHostCard()).as("candidate " + i).isEqualTo(islandsInHand.get(i));
        }
        assertThat(abilities.get(islandsInHand.size()).getHostCard()).isEqualTo(commander);
        assertThat(abilities.get(islandsInHand.size()).getHostCard().getZone()).isEqualTo(ZoneType.Command);

        // card candidates are the distinct hosts, in card-id order
        List<CardView> expectedCards = new ArrayList<>(islandsInHand);
        expectedCards.add(commander);
        expectedCards.sort(Comparator.comparingInt(CardView::getId));
        assertThat(prompt.getCardCandidates()).containsExactlyElementsOf(expectedCards);

        engine.feed(new BaselineAction.Concede());
        assertThat(engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();
        assertThat(engine.getFailure()).as("game thread failure").isNull();
    }

    @Test
    public void shouldAcceptAChoiceAsAnActionAndReflectItInTheNextPrompt() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        engine.start();
        EngineRig.awaitInitialSnapshot(engine);

        BaselineSnapshot snap = awaitMainPhasePrompt(engine);
        int index = engine.getPromptSnapshots().size() - 1;
        PlayerView scriptedView = snap.getGameView().getPlayers().get(0);
        CardView landToPlay = landsInOrder(scriptedView.getHand()).get(0);
        assertThat(snap.getPrompt().getCardCandidates()).contains(landToPlay);

        engine.feed(new BaselineAction.SelectCard(landToPlay, null));
        BaselineSnapshot nextSnap = EngineRig.awaitPrompt(engine, index + 1);
        BaselinePrompt next = nextSnap.getPrompt();
        PlayerView afterView = nextSnap.getGameView().getPlayers().get(0);

        // the choice was applied: the land is on the battlefield...
        assertThat(afterView.getBattlefield()).as("the chosen land was played").contains(landToPlay);
        assertThat(afterView.getHand()).doesNotContain(landToPlay);
        // ...and no land drop is legal any more (one land per turn); the only
        // remaining candidate is the commander's cast from the command zone.
        assertThat(next.getAbilityCandidates()).hasSize(1);
        assertThat(next.getCardCandidates()).containsExactly(afterView.getCommanders().get(0));

        engine.feed(new BaselineAction.Concede());
        assertThat(engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();
        assertThat(engine.getFailure()).as("game thread failure").isNull();
    }

    @Test
    public void shouldRejectAnActionOutsideTheLegalCandidatesAsAPass() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        engine.start();
        EngineRig.awaitInitialSnapshot(engine);

        BaselineSnapshot snap = awaitMainPhasePrompt(engine);
        int index = engine.getPromptSnapshots().size() - 1;
        CardView aiCommander = snap.getGameView().getPlayers().get(1).getCommanders().get(0);
        assertThat(snap.getPrompt().getCardCandidates())
                .as("the opponent's commander is not a legal candidate").doesNotContain(aiCommander);

        // Selecting the opponent's commander is outside the legal candidates:
        // the action is rejected and treated as a pass, so the game keeps
        // running and nothing changes.
        engine.feed(new BaselineAction.SelectCard(aiCommander, null));
        BaselineSnapshot next = EngineRig.awaitPrompt(engine, index + 1);
        assertThat(next.getPrompt()).as("the game did not stall").isNotNull();
        assertThat(next.getGameView().getPlayers().get(1).getCommanders())
                .as("the rejected action changed nothing").contains(aiCommander);
        assertThat(engine.getFailure()).as("game thread failure").isNull();

        engine.feed(new BaselineAction.Concede());
        assertThat(engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();
        GameOutcome outcome = engine.getOutcome();
        assertThat(outcome.isWinner(rig.scripted())).isFalse();
        assertThat(outcome.isWinner(rig.ai())).isTrue();
    }

    /**
     * Advances past pass-priority prompts that have no legal candidates
     * (untap/end steps, the opponent's turn) by feeding passes, until a
     * main-phase prompt with candidates is emitted.
     */
    private static BaselineSnapshot awaitMainPhasePrompt(BaselineEngine engine) {
        int index = 0;
        BaselineSnapshot snap = EngineRig.awaitPrompt(engine, index);
        while ((snap.getPrompt() == null || snap.getPrompt().getAbilityCandidates().isEmpty())
                && index < 16) {
            engine.feed(new BaselineAction.Ok());
            index++;
            snap = EngineRig.awaitPrompt(engine, index);
        }
        assertThat(snap.getPrompt().getAbilityCandidates())
                .as("reached a prompt with legal candidates").isNotEmpty();
        return snap;
    }

    /** The land cards of the given view's zone contents, in card-id order. */
    private static List<CardView> landsInOrder(Collection<CardView> cards) {
        List<CardView> lands = new ArrayList<>();
        for (CardView card : cards) {
            if (card.getCurrentState().isLand()) {
                lands.add(card);
            }
        }
        lands.sort(Comparator.comparingInt(CardView::getId));
        return lands;
    }
}
