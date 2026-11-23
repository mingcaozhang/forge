package forge.baseline.engine;

import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselineSnapshot;
import forge.game.GameOutcome;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the 29.1 transition and invariants of the {@link BaselineEngine}
 * adapter, all fast and deterministic (fixed seed, headless, no UI):
 *
 * <ul>
 *   <li>phase/step progression: the post-mulligan start (turn 1, untap),
 *       the per-turn phase order (draw, main, the combat steps, end), and
 *       the game running to a recorded outcome;</li>
 *   <li>state invariants at every emitted envelope: per-player card
 *       conservation (hand + library + battlefield + command + graveyard +
 *       exile), life bounds and the bear-sized life deltas, the commander
 *       staying in a legal zone, and an empty stack at the start of a turn;</li>
 *   <li>action handling: actions outside the legal candidates are rejected
 *       and leave the scripted player's state unchanged, while a legal
 *       action is applied and visible in the next prompt snapshot.</li>
 * </ul>
 *
 * <p>State is captured on the game thread at the moment each envelope is
 * emitted (the envelopes carry the live view graph, which keeps evolving).
 */
public class BaselineTransitionsAndInvariantsShould {

    private static final long SEED = 20261125L;
    private static final int CARDS_PER_PLAYER = 20;

    @Test
    public void shouldProgressFromThePostMulliganStartThroughDrawAndCombatToEnd() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        Capture capture = new Capture();
        engine.onEnvelope(e -> capture.record(engine));
        engine.start();

        EngineRig.awaitInitialSnapshot(engine);
        assertPostMulliganStart(engine);

        int promptIndex = 0;
        int turn = 1;
        while (turn < 3 && promptIndex < 120) {
            if (engine.getFailure() != null) {
                throw new AssertionError("game thread failure", engine.getFailure());
            }
            BaselineSnapshot snap = EngineRig.awaitPrompt(engine, promptIndex);
            turn = snap.getGameView().getTurn();
            feedLandThenCommanderThenPass(engine, snap);
            promptIndex++;
        }
        assertThat(turn).as("reached turn 3").isGreaterThanOrEqualTo(3);

        engine.feed(new BaselineAction.Concede());
        assertThat(engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();
        assertThat(engine.getFailure()).as("game thread failure").isNull();
        GameOutcome outcome = engine.getOutcome();
        assertThat(outcome).as("outcome recorded").isNotNull();
        assertThat(outcome.isWinner(rig.scripted())).isFalse();
        assertThat(outcome.isWinner(rig.ai())).isTrue();

        assertPhaseProgression(capture);
        assertInvariants(capture);
    }

    @Test
    public void shouldKeepZoneLifeCommanderAndStackInvariantsAtEveryEnvelope() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        Capture capture = new Capture();
        engine.onEnvelope(e -> capture.record(engine));
        engine.start();

        EngineRig.awaitInitialSnapshot(engine);
        assertPostMulliganStart(engine);

        // play until global turn 3 has started, then concede
        int promptIndex = 0;
        int turn = 1;
        while (turn < 3 && promptIndex < 120) {
            if (engine.getFailure() != null) {
                throw new AssertionError("game thread failure", engine.getFailure());
            }
            BaselineSnapshot snap = EngineRig.awaitPrompt(engine, promptIndex);
            turn = snap.getGameView().getTurn();
            feedLandThenCommanderThenPass(engine, snap);
            promptIndex++;
        }
        assertThat(turn).as("reached turn 3").isGreaterThanOrEqualTo(3);

        engine.feed(new BaselineAction.Concede());
        assertThat(engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();
        assertThat(engine.getFailure()).as("game thread failure").isNull();
        assertThat(capture.turns).as("envelopes were captured").isNotEmpty();
        assertThat(capture.turns.get(capture.turns.size() - 1))
                .as("captured through multiple turns").isGreaterThanOrEqualTo(3);

        assertInvariants(capture);
    }

    @Test
    public void shouldRejectIllegalActionsAndLeaveTheScriptedPlayerUnchanged() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        engine.start();
        EngineRig.awaitInitialSnapshot(engine);

        // wait until a land drop is a legal candidate, so the control action
        // below is guaranteed legal (a prompt with candidates may be combat or
        // the opponent's turn, where playing a land is not)
        BaselineSnapshot snap = awaitLandDropPrompt(engine);
        int index = engine.getPromptSnapshots().size() - 1;
        PlayerView me = snap.getGameView().getPlayers().get(0);
        PlayerView aiPlayer = snap.getGameView().getPlayers().get(1);
        CardView aiCommander = aiPlayer.getCommanders().get(0);
        CardView land = firstLandInHand(me);
        assertThat(land).as("a playable land is in hand").isNotNull();
        assertThat(snap.getPrompt().getCardCandidates())
                .as("the opponent's commander is not a legal candidate").doesNotContain(aiCommander);

        // control: the legal land drop is applied and visible in the next prompt
        engine.feed(new BaselineAction.SelectCard(land, null));
        BaselineSnapshot afterLand = EngineRig.awaitPrompt(engine, index + 1);
        assertThat(afterLand.getPrompt()).as("the game did not stall").isNotNull();
        PlayerView meAfterLand = afterLand.getGameView().getPlayers().get(0);
        assertThat(meAfterLand.getBattlefield()).as("the chosen land was played").contains(land);
        assertThat(meAfterLand.getHand()).as("the land left the hand").doesNotContain(land);

        // the post-land state is the baseline for the illegal-action checks
        List<Integer> handBefore = cardIds(meAfterLand.getHand());
        List<Integer> battlefieldBefore = cardIds(meAfterLand.getBattlefield());
        int lifeBefore = meAfterLand.getLife();
        ZoneType commanderZoneBefore = meAfterLand.getCommanders().get(0).getZone();
        int index2 = engine.getPromptSnapshots().size() - 1;

        // selecting a card outside the legal candidates is rejected and
        // treated as a pass: the scripted player's own state is untouched
        engine.feed(new BaselineAction.SelectCard(aiCommander, null));
        BaselineSnapshot next = EngineRig.awaitPrompt(engine, index2 + 1);
        assertThat(next.getPrompt()).as("the game did not stall").isNotNull();
        PlayerView after = next.getGameView().getPlayers().get(0);
        assertThat(cardIds(after.getHand())).as("hand unchanged by the rejected action").isEqualTo(handBefore);
        assertThat(cardIds(after.getBattlefield())).as("battlefield unchanged").isEqualTo(battlefieldBefore);
        assertThat(after.getLife()).as("life unchanged").isEqualTo(lifeBefore);
        assertThat(after.getCommanders().get(0).getZone()).as("commander zone unchanged").isEqualTo(commanderZoneBefore);

        // a player selection (these prompts have no player candidates) is
        // rejected the same way
        int index3 = engine.getPromptSnapshots().size() - 1;
        engine.feed(new BaselineAction.SelectPlayer(aiPlayer));
        BaselineSnapshot next2 = EngineRig.awaitPrompt(engine, index3 + 1);
        PlayerView after2 = next2.getGameView().getPlayers().get(0);
        assertThat(cardIds(after2.getHand())).as("hand still unchanged").isEqualTo(handBefore);
        assertThat(cardIds(after2.getBattlefield())).as("battlefield still unchanged").isEqualTo(battlefieldBefore);
        assertThat(after2.getLife()).as("life still unchanged").isEqualTo(lifeBefore);

        engine.feed(new BaselineAction.Concede());
        assertThat(engine.awaitCompletion(120, TimeUnit.SECONDS)).as("game finished").isTrue();
        assertThat(engine.getFailure()).as("game thread failure").isNull();
        assertThat(engine.getOutcome().isWinner(rig.ai())).isTrue();
    }

    /** The post-mulligan start of turn 1: the state the mulligan phase left behind. */
    private static void assertPostMulliganStart(BaselineEngine engine) {
        BaselineSnapshot s0 = engine.getSnapshot();
        assertThat(s0).as("initial snapshot").isNotNull();
        assertThat(s0.getPrompt()).as("the game is not blocked at the start").isNull();
        GameView g = s0.getGameView();
        assertThat(g.getTurn()).as("turn 1").isEqualTo(1);
        assertThat(g.getPhase()).as("the first step").isEqualTo(PhaseType.UNTAP);
        assertThat(g.getStack()).as("the stack is empty").isEmpty();
        assertThat(g.getPlayers()).hasSize(2);
        for (int i = 0; i < 2; i++) {
            PlayerView p = g.getPlayers().get(i);
            assertThat(p.getLife()).as("commander starting life, player %d", i).isEqualTo(40);
            assertThat(p.getZoneSize(ZoneType.Hand)).as("hand after mulligans, player %d", i).isBetween(1, 7);
            assertThat(p.getZoneSize(ZoneType.Hand) + p.getZoneSize(ZoneType.Library))
                    .as("all non-commander cards are in hand or library, player %d", i).isEqualTo(19);
            assertThat(p.getZoneSize(ZoneType.Battlefield)).as("battlefield, player %d", i).isZero();
            assertThat(p.getZoneSize(ZoneType.Graveyard)).as("graveyard, player %d", i).isZero();
            assertThat(p.getZoneSize(ZoneType.Exile)).as("exile, player %d", i).isZero();
            assertThat(p.getCommanders()).as("one commander, player %d", i).hasSize(1);
            assertThat(p.getCommanders().get(0).getZone())
                    .as("commander starts in the command zone, player %d", i).isEqualTo(ZoneType.Command);
        }
    }

    /**
     * The captured (turn, phase) sequence: it starts at turn 1 untap, turns
     * advance by at most one, phases never move backwards within a turn, a
     * new turn starts at untap/upkeep, the draw step is reached (skipped on
     * turn 1 in two-player games), and the combat steps appear in order on
     * the turn the combat phase is entered.
     */
    private static void assertPhaseProgression(Capture c) {
        assertThat(c.turns).as("a phase sequence was captured").isNotEmpty();
        assertThat(c.turns.get(0)).as("the first envelope is turn 1").isEqualTo(1);
        assertThat(c.phases.get(0)).as("the first envelope is the untap step").isEqualTo(PhaseType.UNTAP);

        for (int i = 1; i < c.turns.size(); i++) {
            int turnDelta = c.turns.get(i) - c.turns.get(i - 1);
            assertThat(turnDelta).as("turn delta at envelope " + i).isBetween(0, 1);
            if (turnDelta == 0) {
                PhaseType previous = c.phases.get(i - 1);
                PhaseType current = c.phases.get(i);
                assertThat(current == previous || previous.isBefore(current))
                        .as("phase order at envelope " + i + ": " + previous + " -> " + current).isTrue();
            } else {
                assertThat(c.phases.get(i))
                        .as("the new turn starts at untap or upkeep, envelope " + i)
                        .isIn(PhaseType.UNTAP, PhaseType.UPKEEP);
            }
        }

        assertThat(c.phases).as("the draw step was reached").contains(PhaseType.DRAW);

        int combatBegin = -1;
        int declareAttackers = -1;
        for (int i = 0; i < c.phases.size(); i++) {
            if (combatBegin < 0 && c.phases.get(i) == PhaseType.COMBAT_BEGIN) {
                combatBegin = i;
            }
            if (combatBegin >= 0 && c.phases.get(i) == PhaseType.COMBAT_DECLARE_ATTACKERS
                    && c.turns.get(i) == c.turns.get(combatBegin)) {
                declareAttackers = i;
                break;
            }
        }
        assertThat(combatBegin).as("the combat phase was entered").isNotNegative();
        assertThat(declareAttackers)
                .as("declare attackers follows begin combat in the same turn").isGreaterThan(combatBegin);
    }

    /** The invariants that must hold at every captured state. */
    private static void assertInvariants(Capture c) {
        for (int i = 0; i < c.turns.size(); i++) {
            for (int p = 0; p < 2; p++) {
                assertThat(c.zoneTotals.get(i)[p])
                        .as("card conservation, player %d at envelope %d", p, i).isEqualTo(CARDS_PER_PLAYER);
                int life = c.lives.get(i)[p];
                assertThat(life).as("life in range, player %d at envelope %d", p, i).isBetween(0, 40);
                assertThat(c.commanderZones.get(i)[p])
                        .as("commander in a legal zone, player %d at envelope %d", p, i)
                        .isIn(ZoneType.Command, ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Exile);
                if (i > 0) {
                    // the only damage source in the rig is the 2/2 bear in
                    // combat, and at most one combat damage step fits
                    // between two consecutive envelopes
                    int delta = c.lives.get(i)[p] - c.lives.get(i - 1)[p];
                    assertThat(delta)
                            .as("life changes by 0 or one bear hit, player %d at envelope %d", p, i)
                            .isIn(0, -2);
                }
            }
            PhaseType phase = c.phases.get(i);
            if (phase == PhaseType.UNTAP || phase == PhaseType.UPKEEP || phase == PhaseType.DRAW) {
                assertThat(c.stackSizes.get(i))
                        .as("the stack is empty at the start of a turn, envelope %d", i).isZero();
            }
        }
    }

    /**
     * Drives the scripted player: play a land drop while one is a legal
     * candidate, otherwise cast the commander from the command zone once
     * three untapped lands can pay for it (the Commander format's command
     * zone cast), otherwise pass priority.
     */
    private static void feedLandThenCommanderThenPass(BaselineEngine engine, BaselineSnapshot snap) {
        PlayerView me = snap.getGameView().getPlayers().get(0);
        for (SpellAbilityView sa : snap.getPrompt().getAbilityCandidates()) {
            CardView host = sa.getHostCard();
            if (host != null && host.getCurrentState().isLand() && me.getHand().contains(host)) {
                engine.feed(new BaselineAction.SelectCard(host, null));
                return;
            }
        }
        CardView commander = me.getCommanders().get(0);
        int untappedLands = 0;
        for (CardView c : me.getBattlefield()) {
            if (c.getCurrentState().isLand() && !c.isTapped()) {
                untappedLands++;
            }
        }
        if (commander.getZone() == ZoneType.Command && untappedLands >= 3) {
            engine.feed(new BaselineAction.SelectCard(commander, null));
        } else {
            engine.feed(new BaselineAction.Ok());
        }
    }

    /**
     * Advances past prompts where playing a land is not legal (untap/end
     * steps, the opponent's turn, combat) by feeding passes, until a prompt
     * where a land drop is a legal candidate is emitted.
     */
    private static BaselineSnapshot awaitLandDropPrompt(BaselineEngine engine) {
        int index = 0;
        BaselineSnapshot snap = EngineRig.awaitPrompt(engine, index);
        while (!hasLegalLandDrop(snap) && index < 24) {
            engine.feed(new BaselineAction.Ok());
            index++;
            snap = EngineRig.awaitPrompt(engine, index);
        }
        assertThat(hasLegalLandDrop(snap)).as("reached a prompt with a legal land drop").isTrue();
        return snap;
    }

    private static boolean hasLegalLandDrop(BaselineSnapshot snap) {
        if (snap.getPrompt() == null) {
            return false;
        }
        PlayerView me = snap.getGameView().getPlayers().get(0);
        for (SpellAbilityView sa : snap.getPrompt().getAbilityCandidates()) {
            CardView host = sa.getHostCard();
            if (host != null && host.getCurrentState().isLand() && me.getHand().contains(host)) {
                return true;
            }
        }
        return false;
    }

    private static CardView firstLandInHand(PlayerView p) {
        for (CardView c : p.getHand()) {
            if (c.getCurrentState().isLand()) {
                return c;
            }
        }
        return null;
    }

    private static List<Integer> cardIds(Iterable<CardView> cards) {
        List<Integer> ids = new ArrayList<>();
        for (CardView c : cards) {
            ids.add(c.getId());
        }
        Collections.sort(ids);
        return ids;
    }

    /**
     * Captures the live game state on the game thread at the moment each
     * envelope is emitted: turn, phase, per-player life / zone total /
     * commander zone, and stack size.
     */
    private static final class Capture {
        final List<Integer> turns = new ArrayList<>();
        final List<PhaseType> phases = new ArrayList<>();
        final List<int[]> lives = new ArrayList<>();
        final List<int[]> zoneTotals = new ArrayList<>();
        final List<ZoneType[]> commanderZones = new ArrayList<>();
        final List<Integer> stackSizes = new ArrayList<>();

        void record(BaselineEngine engine) {
            GameView g = engine.getGame().getView();
            FCollectionView<PlayerView> players = g.getPlayers();
            turns.add(g.getTurn());
            phases.add(g.getPhase());
            int[] life = new int[2];
            int[] zones = new int[2];
            ZoneType[] commanders = new ZoneType[2];
            for (int i = 0; i < 2; i++) {
                PlayerView p = players.get(i);
                life[i] = p.getLife();
                zones[i] = p.getZoneSize(ZoneType.Hand) + p.getZoneSize(ZoneType.Library)
                        + p.getZoneSize(ZoneType.Battlefield) + p.getZoneSize(ZoneType.Command)
                        + p.getZoneSize(ZoneType.Graveyard) + p.getZoneSize(ZoneType.Exile);
                commanders[i] = p.getCommanders().get(0).getZone();
            }
            lives.add(life);
            zoneTotals.add(zones);
            commanderZones.add(commanders);
            stackSizes.add(g.getStack().size());
        }
    }
}
