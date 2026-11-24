package forge.baseline.engine;

import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselineSnapshot;
import forge.game.Game;
import forge.game.GameOutcome;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.GameLossReason;
import forge.game.player.Player;
import forge.game.player.PlayerOutcome;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 30.2: headless full-match tests through the adapter. Runs complete
 * seeded two-player Commander matches on the {@link BaselineEngine} — no UI
 * windows, no Xvfb — driven only by fed {@link BaselineAction}s at the prompt
 * boundary, until the game reaches its outcome. For every match the test
 * asserts:
 *
 * <ul>
 *   <li>the match runs to completion with a decisive, recorded outcome
 *       (exactly one winner, no draw, a real number of turns played);</li>
 *   <li>the final state is coherent (the winner is alive, per-player card
 *       conservation, the commander in a legal zone);</li>
 *   <li>no UI-only package was touched by any thread during the match
 *       ({@link UiClassTouchGuard}); a touched UI class would surface as the
 *       engine's failure.</li>
 * </ul>
 */
public class BaselineFullMatchShould {

    private static final long SEED = 20261126L;
    private static final int CARDS_PER_PLAYER = 20;
    /** A natural end (life, commander damage, or an empty library) comes long before this; a stall must fail loudly. */
    private static final int PROMPT_CAP = 2000;
    private static final long WALL_CLOCK_MILLIS = 240_000;

    private UiClassTouchGuard guard;

    @BeforeMethod
    public void installUiTouchGuard() {
        guard = UiClassTouchGuard.install();
    }

    @AfterMethod
    public void uninstallUiTouchGuard() {
        if (guard != null) {
            guard.uninstall();
        }
    }

    @Test
    public void runsAFullCommanderMatchToCompletionWithoutTouchingUiClasses() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        long startedAt = System.currentTimeMillis();
        engine.start();
        EngineRig.awaitInitialSnapshot(engine);

        int prompts = 0;
        while (engine.getOutcome() == null && engine.getFailure() == null) {
            long elapsed = System.currentTimeMillis() - startedAt;
            if (elapsed > WALL_CLOCK_MILLIS || prompts > PROMPT_CAP) {
                throw new AssertionError("the full match did not complete: " + prompts
                        + " prompts in " + elapsed + " ms");
            }
            if (engine.getPromptSnapshots().size() > prompts) {
                drivePlayLandCastCommanderOrPass(engine, engine.getPromptSnapshots().get(prompts));
                prompts++;
            } else {
                sleep(20);
            }
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        System.out.println("[30.2] full match finished: " + prompts + " prompts, " + elapsed + " ms, "
                + describeOutcome(engine, rig));

        assertCompletedDecisively(engine, rig, prompts, true, 20, 5);
    }

    @Test
    public void aConcededMatchIsARunToCompletionToo() throws Exception {
        EngineRig.Result rig = EngineRig.create(SEED);
        BaselineEngine engine = rig.engine();
        long startedAt = System.currentTimeMillis();
        engine.start();
        EngineRig.awaitInitialSnapshot(engine);

        // wait for the first prompt, play a few normal prompts (lands /
        // commander), then concede
        int prompts = 0;
        while (engine.getPromptSnapshots().size() <= prompts
                && engine.getFailure() == null
                && System.currentTimeMillis() - startedAt < 60_000) {
            sleep(20);
        }
        for (int i = 0; i < 3 && engine.getPromptSnapshots().size() > i; i++) {
            drivePlayLandCastCommanderOrPass(engine, engine.getPromptSnapshots().get(i));
            prompts++;
        }
        engine.feed(new BaselineAction.Concede());

        assertCompletedDecisively(engine, rig, prompts, false, 1, 1);
        // getPlayers() drops the loser after the match ends; the scripted player is registered first
        PlayerOutcome scriptedOutcome = engine.getGame().getRegisteredPlayers().get(0).getOutcome();
        assertThat(scriptedOutcome.hasWon()).as("the conceding scripted player lost").isFalse();
        assertThat(scriptedOutcome.lossState).as("the scripted player's loss is a concede")
                .isEqualTo(GameLossReason.Conceded);
    }

    /**
     * The full-match assertions: no game-thread failure (a touched UI class
     * would fail it), the game thread finished, and a decisive, naturally
     * played outcome with a coherent final state.
     */
    private static void assertCompletedDecisively(BaselineEngine engine, EngineRig.Result rig,
                                                  int prompts, boolean naturalEnd,
                                                  int minPrompts, int minTurns) throws Exception {
        assertThat(engine.getFailure())
                .as("the game thread finished without failure (a touched UI class would fail it here)")
                .isNull();
        assertThat(engine.awaitCompletion(60, TimeUnit.SECONDS)).as("the game thread finished").isTrue();

        GameOutcome outcome = engine.getOutcome();
        assertThat(outcome).as("an outcome was recorded").isNotNull();
        assertThat(outcome.isDraw()).as("the match ended decisively").isFalse();

        RegisteredPlayer winner = null;
        RegisteredPlayer loser = null;
        long winners = 0;
        for (RegisteredPlayer p : outcome.getPlayerNames().keySet()) {
            if (outcome.isWinner(p)) {
                winners++;
                winner = p;
            } else {
                loser = p;
            }
        }
        assertThat(winners).as("exactly one winner exists").isEqualTo(1);
        assertThat(loser).as("exactly one loser exists").isNotNull();
        assertThat(outcome.getLastTurnNumber())
                .as("a full match was played, not an early end").isGreaterThanOrEqualTo(minTurns);
        assertThat(prompts)
                .as("the match ran through the adapter's prompt boundary").isGreaterThanOrEqualTo(minPrompts);

        // the outcome is a real played end, not a voluntary one
        PlayerOutcome loserOutcome = loserPlayer(engine, loser).getOutcome();
        assertThat(loserOutcome.hasWon()).as("the loser did not win").isFalse();
        if (naturalEnd) {
            assertThat(loserOutcome.lossState)
                    .as("the loser lost by a natural game condition, not by concede")
                    .isIn(GameLossReason.LifeReachedZero, GameLossReason.CommanderDamage, GameLossReason.Milled);
        }

        // final-state coherence on the live view graph
        for (int i = 0; i < 2; i++) {
            PlayerView p = engine.getGame().getView().getPlayers().get(i);
            int total = p.getZoneSize(ZoneType.Hand) + p.getZoneSize(ZoneType.Library)
                    + p.getZoneSize(ZoneType.Battlefield) + p.getZoneSize(ZoneType.Command)
                    + p.getZoneSize(ZoneType.Graveyard) + p.getZoneSize(ZoneType.Exile);
            for (var item : engine.getGame().getView().getStack()) {
                PlayerView activator = item.getActivatingPlayer();
                if (!item.isAbility() && activator != null && activator.getId() == p.getId()) {
                    total++;
                }
            }
            assertThat(total).as("card conservation, player %d", i).isEqualTo(CARDS_PER_PLAYER);
        }
        // the commanders live in legal zones in the real game. (The CardView in the
        // view's commander collection may wrap a stale card instance left behind when
        // the cast copy resolved, so the live instance is located by zone scan.)
        Game game = engine.getGame();
        for (Player p : game.getRegisteredPlayers()) {
            Card live = liveCommander(p, game);
            assertThat(live).as("the commander is in a zone, %s", p.getName()).isNotNull();
            assertThat(live.getZone().getZoneType())
                    .as("the commander is in a legal zone, %s", p.getName())
                    .isIn(ZoneType.Command, ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Exile);
        }
        PlayerView winnerView = playerView(engine, winner);
        assertThat(winnerView.getLife()).as("the winner is alive").isGreaterThanOrEqualTo(1);
    }

    /** Finds the live commander instance across every zone the card can occupy. */
    private static Card liveCommander(Player p, Game game) {
        for (ZoneType zt : ZoneType.values()) {
            Zone z = p.getZone(zt);
            if (z != null) {
                for (Card c : z.getCards()) {
                    if (EngineRig.BEAR_NAME.equals(c.getName())) {
                        return c;
                    }
                }
            }
        }
        for (Card c : game.getStackZone().getCards()) {
            if (EngineRig.BEAR_NAME.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    /**
     * The scripted player's driving policy: play a land drop while one is a
     * legal candidate, otherwise cast the commander from the command zone
     * once three untapped lands can pay for it, otherwise pass priority.
     * (The match ends naturally — the policy never concedes.)
     */
    private static void drivePlayLandCastCommanderOrPass(BaselineEngine engine, BaselineSnapshot snap) {
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

    private static Player loserPlayer(BaselineEngine engine, RegisteredPlayer loser) {
        // getPlayers() drops the loser into getLostPlayers() once the match ends;
        // getRegisteredPlayers() keeps every participant
        for (Player p : engine.getGame().getRegisteredPlayers()) {
            if (p.getRegisteredPlayer().equals(loser)) {
                return p;
            }
        }
        throw new AssertionError("no ingame player for the losing registered player");
    }

    private static PlayerView playerView(BaselineEngine engine, RegisteredPlayer winner) {
        for (PlayerView p : engine.getGame().getView().getPlayers()) {
            if (p.isLobbyPlayer(winner.getPlayer())) {
                return p;
            }
        }
        throw new AssertionError("no view for the winning player");
    }

    private static String describeOutcome(BaselineEngine engine, EngineRig.Result rig) {
        GameOutcome outcome = engine.getOutcome();
        if (outcome == null) {
            return "failure=" + engine.getFailure();
        }
        return "winner=" + (outcome.isWinner(rig.scripted()) ? "scripted" : "ai")
                + " turns=" + outcome.getLastTurnNumber();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
