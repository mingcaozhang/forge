package forge.baseline.trajectory;

import forge.LobbyPlayer;
import forge.baseline.action.BaselineAction;
import forge.baseline.engine.BaselineEngine;
import forge.baseline.engine.ScriptedLobbyPlayer;
import forge.baseline.state.BaselineSnapshot;
import forge.deck.Deck;
import forge.game.GameOutcome;
import forge.util.Lang;
import forge.util.Localizer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Headless batch runner: N seeded AI-vs-AI Commander matches through the
 * {@link BaselineEngine} adapter, no UI, no Xvfb. Each match is one
 * {@link ScriptedLobbyPlayer} driven by an {@link ActionPolicy} (the
 * policy side of the AI-vs-AI pair) against the other lobby players of the
 * spec (typically a {@code LobbyPlayerAi}), and the match's per-decision
 * trajectory (state snapshot, legal candidates, chosen action, outcome) is
 * exported as a JSONL shard ({@link JsonlShardWriter}).
 *
 * <p>Parallel safety: matches run on a fixed pool of threads, each match on
 * its own thread, and each engine runs its game on its own game thread that
 * seeds its own {@code MyRandom} stream ({@code MyRandom.setRandom(new
 * Random(seed))} is the first game logic of the engine's game thread).
 * {@code MyRandom}'s streams are thread-local (inheritable per thread), so
 * concurrent matches never share a random stream: a fixed
 * {@code (seed, decks, policy)} produces the same shard whether run alone,
 * in a sequential batch, or in a parallel batch. The only shared singletons
 * (the {@code StaticData} bootstrap, the card-type lists, {@code Localizer}/
 * {@code Lang}) are idempotent and guarded.
 *
 * <p>The spec factory must return a fresh {@link Deck}/{@link LobbyPlayer}
 * per call — the game mutates decks and lobby players.
 */
public final class BaselineBatchRunner {

    /** One match: the seed, the decks (one per player, in order), the lobby players. */
    public record MatchSpec(long seed, List<Deck> decks, List<LobbyPlayer> players) {
    }

    /** Builds the spec for match {@code matchIndex}; must be deterministic in {@code matchIndex}. */
    @FunctionalInterface
    public interface MatchSpecFactory {
        MatchSpec create(int matchIndex);
    }

    /** The terminal result of one match. */
    public record MatchResult(int matchIndex, long seed, boolean completed, GameOutcome outcome,
                              Throwable failure, int decisions, long durationMillis) {
    }

    /** The results of a whole batch, in match-index order. */
    public record BatchResult(List<MatchResult> matches, long durationMillis) {
    }

    private BaselineBatchRunner() {
    }

    /**
     * Runs {@code matchCount} matches with up to {@code parallelism} in
     * flight, writing one shard per match into {@code shardDir}.
     *
     * @throws IllegalArgumentException on bad arguments, a spec without exactly
     *                                  one scripted player, or duplicated player names
     * @throws IllegalStateException    if a match stalls (no outcome within
     *                                  {@code timeoutMillis}) or is interrupted
     */
    public static BatchResult run(MatchSpecFactory specFactory, int matchCount, int parallelism,
                                  ActionPolicy policy, Path shardDir, long timeoutMillis) {
        if (specFactory == null) {
            throw new NullPointerException("specFactory");
        }
        if (policy == null) {
            throw new NullPointerException("policy");
        }
        if (shardDir == null) {
            throw new NullPointerException("shardDir");
        }
        if (matchCount < 1) {
            throw new IllegalArgumentException("matchCount must be >= 1, was " + matchCount);
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1, was " + parallelism);
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be > 0, was " + timeoutMillis);
        }

        // Idempotent singletons the engine reads localized strings from; the
        // same initialization EngineRig performs per test.
        Localizer.getInstance().initialize("en-US", langDir());
        Lang.createInstance("en-US");
        try {
            Files.createDirectories(shardDir);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create the shard dir " + shardDir, e);
        }

        long startedAt = System.currentTimeMillis();
        int poolSize = Math.min(parallelism, matchCount);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "forge-baseline-batch");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<MatchResult>> futures = new ArrayList<>(matchCount);
            for (int i = 0; i < matchCount; i++) {
                final int matchIndex = i;
                MatchSpec spec = specFactory.create(i);
                futures.add(pool.submit(() -> runMatch(matchIndex, spec, policy, shardDir, timeoutMillis)));
            }
            List<MatchResult> results = new ArrayList<>(matchCount);
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    throw new IllegalStateException("match " + i + " did not run: " + cause, cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for match " + i, e);
                }
            }
            return new BatchResult(List.copyOf(results), System.currentTimeMillis() - startedAt);
        } finally {
            pool.shutdownNow();
        }
    }

    private static MatchResult runMatch(int index, MatchSpec spec, ActionPolicy policy,
                                        Path shardDir, long timeoutMillis) throws Exception {
        List<LobbyPlayer> scripted = new ArrayList<>();
        for (LobbyPlayer player : spec.players()) {
            if (player instanceof ScriptedLobbyPlayer) {
                scripted.add(player);
            }
        }
        if (scripted.size() != 1) {
            throw new IllegalArgumentException("match " + index + " (seed " + spec.seed()
                    + ") needs exactly one ScriptedLobbyPlayer — the policy-driven side — "
                    + "found " + scripted.size());
        }
        if (spec.decks().size() != spec.players().size()) {
            throw new IllegalArgumentException("match " + index + " (seed " + spec.seed()
                    + ") has " + spec.decks().size() + " decks for " + spec.players().size() + " players");
        }
        Set<String> names = new HashSet<>();
        for (LobbyPlayer player : spec.players()) {
            if (!names.add(player.getName())) {
                throw new IllegalArgumentException("match " + index + " (seed " + spec.seed()
                        + ") has duplicated player name " + player.getName());
            }
        }

        long matchStart = System.currentTimeMillis();
        Path shardFile = shardDir.resolve(
                String.format("match-%04d-seed-%d.jsonl", index, spec.seed()));
        JsonlShardWriter writer = new JsonlShardWriter(shardFile, index, spec.seed(),
                spec.players().stream().map(LobbyPlayer::getName).toList());
        BaselineEngine engine = new BaselineEngine(spec.seed(), spec.decks(), spec.players());
        engine.addObserver(writer);

        try {
            engine.start();
            awaitInitialSnapshot(engine, matchStart + timeoutMillis, index, spec.seed());
            writer.writeHeader();

            int fed = 0;
            long deadline = matchStart + timeoutMillis;
            while (engine.getOutcome() == null && engine.getFailure() == null) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("match " + index + " (seed " + spec.seed()
                            + ") stalled after " + fed + " decisions within " + timeoutMillis + " ms");
                }
                List<BaselineSnapshot> prompts = engine.getPromptSnapshots();
                if (prompts.size() > fed) {
                    // One fed action per new prompt: the engine consumes the
                    // action synchronously at the decision before the next
                    // prompt is emitted, so the queue never runs ahead.
                    BaselineAction action = policy.choose(prompts.get(fed));
                    engine.feed(action);
                    fed++;
                } else {
                    sleep(10);
                }
            }
            if (!engine.awaitCompletion(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("match " + index + " (seed " + spec.seed()
                        + "): the game thread did not finish after its outcome was recorded");
            }
            return new MatchResult(index, spec.seed(),
                    engine.getFailure() == null,
                    engine.getOutcome(),
                    engine.getFailure(),
                    fed,
                    System.currentTimeMillis() - matchStart);
        } finally {
            writer.close();
        }
    }

    private static void awaitInitialSnapshot(BaselineEngine engine, long deadline, int index, long seed) {
        while (engine.getSnapshot() == null) {
            if (engine.getFailure() != null) {
                return; // the outcome event carries the failure; the loop below exits
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("match " + index + " (seed " + seed
                        + ") never emitted its initial snapshot");
            }
            sleep(10);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while driving a batch match", e);
        }
    }

    private static String langDir() {
        String override = System.getProperty("forge.baseline.langDir");
        return override != null ? override : "../forge-gui/res/languages/";
    }
}
