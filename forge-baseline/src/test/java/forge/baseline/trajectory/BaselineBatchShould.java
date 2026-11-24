package forge.baseline.trajectory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.ai.LobbyPlayerAi;
import forge.baseline.action.BaselineAction;
import forge.baseline.engine.BaselineDecision;
import forge.baseline.engine.BaselineEngine;
import forge.baseline.engine.BaselineMatchOutcome;
import forge.baseline.engine.BaselineObserver;
import forge.baseline.engine.EngineRig;
import forge.baseline.engine.ScriptedLobbyPlayer;
import forge.baseline.state.BaselineSnapshot;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 32.1: headless batch runner + per-decision JSONL trajectory shards.
 *
 * <ul>
 *   <li>a seeded batch of AI-vs-AI matches runs to completion headlessly and
 *       exports one shard per match with the documented line schema
 *       (header / decision* / outcome);</li>
 *   <li>a parallel batch produces byte-identical shards to a sequential one —
 *       the MyRandom thread-local streams keep concurrent matches independent;</li>
 *   <li>the engine's observer hook itself: every decision is observed exactly
 *       once, in order, paired with the fed action, and the terminal outcome
 *       exactly once.</li>
 * </ul>
 */
public class BaselineBatchShould {

    private static final long[] SEEDS = {32001L, 32002L, 32003L};
    private static final long TIMEOUT_MILLIS = 240_000;

    /**
     * The scripted side's policy (the "AI" of the AI-vs-AI pair): play a land
     * drop while one is a legal candidate, otherwise cast the commander from
     * the command zone once three untapped lands can pay for it, otherwise
     * pass priority. Deterministic in the prompt snapshot; never concedes, so
     * the match ends on a natural game condition.
     */
    private static final ActionPolicy POLICY = snap -> {
        PlayerView me = snap.getGameView().getPlayers().get(0);
        for (SpellAbilityView sa : snap.getPrompt().getAbilityCandidates()) {
            CardView host = sa.getHostCard();
            if (host != null && host.getCurrentState().isLand() && me.getHand().contains(host)) {
                return new BaselineAction.SelectCard(host, null);
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
            return new BaselineAction.SelectCard(commander, null);
        }
        return new BaselineAction.Ok();
    };

    private static BaselineBatchRunner.MatchSpecFactory specFactory() {
        return index -> new BaselineBatchRunner.MatchSpec(
                SEEDS[index],
                List.of(EngineRig.commanderDeck(), EngineRig.commanderDeck()),
                List.of(new ScriptedLobbyPlayer("Scripted"), new LobbyPlayerAi("AI", null)));
    }

    private static Path shardDir(String tag) throws IOException {
        // scratch artifacts stay under /tmp/opencode per the repo rules
        return Files.createTempDirectory(Path.of("/tmp/opencode"), "forge-baseline-" + tag);
    }

    private static Path shardFile(Path dir, int index) {
        return dir.resolve(String.format("match-%04d-seed-%d.jsonl", index, SEEDS[index]));
    }

    private static List<JsonObject> parseShard(Path shard) throws IOException {
        List<JsonObject> lines = new ArrayList<>();
        for (String line : Files.readAllLines(shard, StandardCharsets.UTF_8)) {
            lines.add(JsonParser.parseString(line).getAsJsonObject());
        }
        return lines;
    }

    @Test
    public void aSeededBatchExportsPerDecisionShards() throws IOException {
        Path dir = shardDir("batch-schema");
        BaselineBatchRunner.BatchResult result =
                BaselineBatchRunner.run(specFactory(), SEEDS.length, 1, POLICY, dir, TIMEOUT_MILLIS);

        assertThat(result.matches()).hasSize(SEEDS.length);
        for (int i = 0; i < SEEDS.length; i++) {
            BaselineBatchRunner.MatchResult match = result.matches().get(i);
            assertThat(match.matchIndex()).isEqualTo(i);
            assertThat(match.seed()).isEqualTo(SEEDS[i]);
            assertThat(match.failure()).as("match %d ran without a game-thread failure", i).isNull();
            assertThat(match.completed()).as("match %d ran to completion", i).isTrue();
            assertThat(match.decisions()).as("match %d produced decisions", i).isGreaterThan(0);

            Path shard = shardFile(dir, i);
            assertThat(shard).as("the shard for match %d exists", i).exists();
            List<JsonObject> lines = parseShard(shard);

            JsonObject header = lines.get(0);
            assertThat(header.get("type").getAsString()).isEqualTo("header");
            assertThat(header.get("schema").getAsInt()).isEqualTo(JsonlShardWriter.SCHEMA);
            assertThat(header.get("match").getAsInt()).isEqualTo(i);
            assertThat(header.get("seed").getAsLong()).isEqualTo(SEEDS[i]);
            JsonArray headerPlayers = header.getAsJsonArray("players");
            assertThat(headerPlayers).hasSize(2);
            assertThat(headerPlayers.get(0).getAsString()).isEqualTo("Scripted");
            assertThat(headerPlayers.get(1).getAsString()).isEqualTo("AI");

            JsonObject outcome = lines.get(lines.size() - 1);
            assertThat(outcome.get("type").getAsString()).isEqualTo("outcome");
            assertThat(outcome.get("completed").getAsBoolean()).isTrue();
            assertThat(outcome.get("failure").isJsonNull()).as("the outcome records no game-thread failure").isTrue();
            assertThat(outcome.get("draw").getAsBoolean()).as("the match ended decisively").isFalse();
            assertThat(outcome.get("turns").getAsInt()).isGreaterThanOrEqualTo(1);
            assertThat(outcome.get("decisions").getAsInt()).isEqualTo(lines.size() - 2);
            JsonArray outcomePlayers = outcome.getAsJsonArray("players");
            assertThat(outcomePlayers).hasSize(2);
            assertThat(outcomePlayers.get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("Scripted");
            long winners = 0;
            for (JsonElement p : outcomePlayers) {
                JsonObject po = p.getAsJsonObject();
                if (po.get("won").getAsBoolean()) {
                    winners++;
                    assertThat(po.get("loss").isJsonNull()).as("the winner has no loss reason").isTrue();
                } else {
                    assertThat(po.get("loss").isJsonNull()).as("the loser has a loss reason").isFalse();
                }
            }
            assertThat(winners).as("exactly one winner").isEqualTo(1);

            int decisions = 0;
            for (int d = 1; d < lines.size() - 1; d++) {
                JsonObject line = lines.get(d);
                assertThat(line.get("type").getAsString()).as("line %d is a decision", d).isEqualTo("decision");
                assertThat(line.get("match").getAsInt()).isEqualTo(i);
                assertThat(line.get("seed").getAsLong()).isEqualTo(SEEDS[i]);
                assertThat(line.get("decision").getAsInt()).as("decision numbering is sequential from 0")
                        .isEqualTo(decisions++);

                JsonObject state = line.getAsJsonObject("state");
                assertThat(state.get("hash").getAsString()).matches("[0-9a-f]{64}");
                assertThat(state.get("players").getAsJsonArray()).hasSize(2);
                JsonObject me = state.getAsJsonArray("players").get(0).getAsJsonObject();
                assertThat(me.getAsJsonObject("zones").keySet())
                        .containsExactly("hand", "battlefield", "library", "graveyard", "exile", "command");

                JsonObject prompt = line.getAsJsonObject("prompt");
                assertThat(prompt.get("kind").getAsString()).isEqualTo("SELECT_ABILITY");
                assertThat(prompt.get("player").getAsInt()).isZero();

                JsonObject action = line.getAsJsonObject("action");
                String type = action.get("type").getAsString();
                assertThat(type).isIn("SelectCard", "SelectAbility", "Ok", "Cancel");
                assertActionIsLegal(type, action, prompt);
            }
            assertThat(decisions).isEqualTo(lines.size() - 2);
        }
    }

    /**
     * The policy only picks from the prompt's legal candidates, so a
     * SelectCard/SelectAbility action must reference a candidate of the same
     * decision line (the engine rejects anything else as a pass).
     */
    private static void assertActionIsLegal(String type, JsonObject action, JsonObject prompt) {
        if ("SelectCard".equals(type)) {
            int cardId = action.get("card").getAsInt();
            JsonArray cards = prompt.getAsJsonArray("cardCandidates");
            boolean inCandidates = false;
            for (JsonElement c : cards) {
                if (c.getAsJsonObject().get("id").getAsInt() == cardId) {
                    inCandidates = true;
                    break;
                }
            }
            assertThat(inCandidates).as("the chosen card is a legal candidate (id %d)", cardId).isTrue();
        } else if ("SelectAbility".equals(type)) {
            int abilityId = action.get("ability").getAsInt();
            JsonArray abilities = prompt.getAsJsonArray("abilityCandidates");
            boolean inCandidates = false;
            for (JsonElement a : abilities) {
                if (a.getAsJsonObject().get("id").getAsInt() == abilityId) {
                    inCandidates = true;
                    break;
                }
            }
            assertThat(inCandidates).as("the chosen ability is a legal candidate (id %d)", abilityId).isTrue();
        }
    }

    @Test
    public void aParallelBatchProducesTheSameShardsAsASequentialOne() throws IOException {
        Path sequentialDir = shardDir("batch-seq");
        Path parallelDir = shardDir("batch-par");

        BaselineBatchRunner.BatchResult sequential =
                BaselineBatchRunner.run(specFactory(), SEEDS.length, 1, POLICY, sequentialDir, TIMEOUT_MILLIS);
        BaselineBatchRunner.BatchResult parallel =
                BaselineBatchRunner.run(specFactory(), SEEDS.length, SEEDS.length, POLICY, parallelDir, TIMEOUT_MILLIS);

        assertThat(parallel.matches()).hasSize(SEEDS.length);
        for (int i = 0; i < SEEDS.length; i++) {
            BaselineBatchRunner.MatchResult seq = sequential.matches().get(i);
            BaselineBatchRunner.MatchResult par = parallel.matches().get(i);
            assertThat(par.completed()).as("match %d (parallel) completed", i).isTrue();
            assertThat(par.failure()).as("match %d (parallel) had no failure", i).isNull();
            assertThat(par.decisions()).as("match %d: parallel and sequential decision counts agree", i)
                    .isEqualTo(seq.decisions());
            assertThat(par.outcome().getLastTurnNumber())
                    .as("match %d: parallel and sequential turn counts agree", i)
                    .isEqualTo(seq.outcome().getLastTurnNumber());
            // The shards carry no timestamps or thread identity: byte-identical
            // files are the strongest form of the determinism assertion.
            assertThat(Arrays.equals(
                            Files.readAllBytes(shardFile(sequentialDir, i)),
                            Files.readAllBytes(shardFile(parallelDir, i))))
                    .as("the shard of match %d is byte-identical sequential vs parallel", i)
                    .isTrue();
        }
    }

    @Test
    public void theObserverHookSeesEveryDecisionInOrderAndTheOutcomeOnce() throws Exception {
        EngineRig.Result rig = EngineRig.create(32004L);
        BaselineEngine engine = rig.engine();
        List<BaselineDecision> decisions = new ArrayList<>();
        List<BaselineMatchOutcome> outcomes = new ArrayList<>();
        engine.addObserver(new BaselineObserver() {
            @Override
            public void onDecision(BaselineDecision decision) {
                decisions.add(decision);
            }

            @Override
            public void onOutcome(BaselineMatchOutcome outcome) {
                outcomes.add(outcome);
            }
        });

        engine.start();
        EngineRig.awaitInitialSnapshot(engine);
        long startedAt = System.currentTimeMillis();
        int fed = 0;
        while (engine.getOutcome() == null && engine.getFailure() == null) {
            if (System.currentTimeMillis() - startedAt > TIMEOUT_MILLIS) {
                throw new AssertionError("the hook test match stalled after " + fed + " decisions");
            }
            List<BaselineSnapshot> prompts = engine.getPromptSnapshots();
            if (prompts.size() > fed) {
                engine.feed(POLICY.choose(prompts.get(fed)));
                fed++;
            } else {
                sleep(10);
            }
        }
        assertThat(engine.getFailure()).isNull();
        assertThat(engine.awaitCompletion(60, TimeUnit.SECONDS)).isTrue();

        assertThat(outcomes).as("the terminal outcome was observed exactly once").hasSize(1);
        assertThat(outcomes.get(0).seed()).isEqualTo(32004L);
        assertThat(outcomes.get(0).isCompleted()).isTrue();
        assertThat(outcomes.get(0).outcome()).isSameAs(engine.getOutcome());

        assertThat(decisions)
                .as("every pass-priority decision was observed")
                .hasSize(engine.getPromptSnapshots().size());
        for (int i = 0; i < decisions.size(); i++) {
            BaselineSnapshot promptSnapshot = engine.getPromptSnapshots().get(i);
            assertThat(decisions.get(i).snapshot())
                    .as("decision %d carries the prompt snapshot of the same decision", i)
                    .isSameAs(promptSnapshot);
            assertThat(decisions.get(i).prompt())
                    .as("decision %d's prompt is the snapshot's prompt", i)
                    .isSameAs(promptSnapshot.getPrompt());
            assertThat(decisions.get(i).action()).as("decision %d carries a fed action", i).isNotNull();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
