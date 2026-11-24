package forge.baseline.engine;

import forge.baseline.action.BaselineAction;
import forge.baseline.golden.BaselineStateHasher;
import forge.baseline.golden.GoldenActionCodec;
import forge.baseline.golden.GoldenTrace;
import forge.baseline.state.BaselineSnapshot;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Records the committed Phase 30 golden traces. Drives the standard two-player
 * seeded rig (one scripted player playing a fixed deterministic policy against
 * one {@code LobbyPlayerAi}) and, at every scripted pass-priority decision,
 * records the state fingerprint and the action token fed to advance. Output is
 * written to the test resources so the replay test
 * ({@link GoldenTraceReplayShould}) can load it from the classpath.
 *
 * <p>This is a manual, one-shot generator: its class name intentionally does not
 * end in {@code Should}, so the GATE does not run it. Regenerate with
 * {@code mvn test -pl forge-baseline -am -Dtest=GoldenTraceGenerator}.
 */
public class GoldenTraceGenerator {

    /** The seeds of the committed golden traces (shared with the replay test). */
    public static final long[] SEEDS = {30001L, 30002L, 30003L};

    /**
     * The scripted player concedes once the game reaches this player-turn, bounding
     * each trace. The turn counter increments per player, so the scripted player
     * (player 0) acts on odd turns; turn 7 lets it reach its third main phase, drop
     * a third land, and cast the commander (a real mana payment) before conceding.
     */
    private static final int TARGET_TURN = 7;

    /** Hard cap on recorded steps per trace as a safety bound. */
    private static final int MAX_STEPS = 400;

    @Test
    public void generate() throws Exception {
        for (long seed : SEEDS) {
            GoldenTrace trace = record(seed);
            Path file = goldenDir().resolve("golden-" + seed + ".trace");
            Files.createDirectories(file.getParent());
            Files.writeString(file, trace.serialize());
            System.out.println("recorded " + file + " (" + trace.steps().size() + " steps)");
        }
    }

    private static GoldenTrace record(long seed) throws Exception {
        EngineRig.Result rig = EngineRig.create(seed);
        BaselineEngine engine = rig.engine();
        engine.start();
        EngineRig.awaitInitialSnapshot(engine);

        List<GoldenTrace.Step> steps = new ArrayList<>();
        int index = 0;
        while (true) {
            if (engine.getFailure() != null) {
                throw new AssertionError("game thread failed while recording", engine.getFailure());
            }
            BaselineSnapshot snapshot = EngineRig.awaitPrompt(engine, index);
            String stateHash = BaselineStateHasher.hash(snapshot);
            BaselineAction action = chooseAction(snapshot);
            steps.add(new GoldenTrace.Step(GoldenActionCodec.encode(action), stateHash));
            engine.feed(action);
            index++;
            if (action instanceof BaselineAction.Concede || index >= MAX_STEPS) {
                if (!(action instanceof BaselineAction.Concede)) {
                    engine.feed(new BaselineAction.Concede());
                }
                break;
            }
        }

        engine.awaitCompletion(120, TimeUnit.SECONDS);
        if (engine.getFailure() != null) {
            throw new AssertionError("game thread failed at end of recording", engine.getFailure());
        }
        return new GoldenTrace(GoldenTrace.VERSION, seed, steps);
    }

    /**
     * The fixed policy for the scripted player: play a land from hand when one
     * is available, otherwise cast the commander once enough lands are untapped,
     * otherwise pass. Concedes at {@link #TARGET_TURN} to bound the trace.
     */
    private static BaselineAction chooseAction(BaselineSnapshot snapshot) {
        GameView game = snapshot.getGameView();
        if (game.getTurn() >= TARGET_TURN) {
            return new BaselineAction.Concede();
        }
        PlayerView me = game.getPlayers().get(0);
        for (SpellAbilityView ability : snapshot.getPrompt().getAbilityCandidates()) {
            CardView host = ability.getHostCard();
            if (host != null && host.getCurrentState().isLand() && me.getHand().contains(host)) {
                return new BaselineAction.SelectCard(host, null);
            }
        }
        List<CardView> commanders = me.getCommanders();
        if (!commanders.isEmpty() && commanders.get(0).getZone() == ZoneType.Command) {
            int untapped = 0;
            for (CardView card : me.getBattlefield()) {
                if (card.getCurrentState().isLand() && !card.isTapped()) {
                    untapped++;
                }
            }
            if (untapped >= 3) {
                return new BaselineAction.SelectCard(commanders.get(0), null);
            }
        }
        return new BaselineAction.Ok();
    }

    private static Path goldenDir() {
        String override = System.getProperty("forge.baseline.goldenDir");
        return override != null ? Path.of(override) : Path.of("src", "test", "resources", "golden");
    }
}
