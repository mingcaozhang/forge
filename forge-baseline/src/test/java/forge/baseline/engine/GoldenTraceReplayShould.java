package forge.baseline.engine;

import forge.baseline.action.BaselineAction;
import forge.baseline.golden.BaselineStateHasher;
import forge.baseline.golden.GoldenActionCodec;
import forge.baseline.golden.GoldenTrace;
import forge.baseline.state.BaselineSnapshot;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 30.1 golden-replay test. Loads each committed golden trace, drives a
 * fresh {@link BaselineEngine} with the same seed and decks, and feeds the
 * recorded action sequence. At every step it asserts the live state fingerprint
 * equals the recorded one, so any divergence in a replayed game — from an engine
 * change, a dependency change, or a lost determinism guarantee — fails the test
 * at the first step it occurs.
 *
 * <p>The golden files are produced by {@link GoldenTraceGenerator} (a manual,
 * non-GATE class) and committed under the test resources.
 */
public class GoldenTraceReplayShould {

    @Test
    public void replaysRecordedActionsWithPerStepStateEquality() throws Exception {
        for (long seed : GoldenTraceGenerator.SEEDS) {
            GoldenTrace trace = load(seed);
            assertThat(trace.seed()).as("seed of %s", traceName(seed)).isEqualTo(seed);

            EngineRig.Result rig = EngineRig.create(seed);
            BaselineEngine engine = rig.engine();
            engine.start();
            EngineRig.awaitInitialSnapshot(engine);

            for (int i = 0; i < trace.steps().size(); i++) {
                if (engine.getFailure() != null) {
                    throw new AssertionError("game thread failed replaying " + traceName(seed),
                            engine.getFailure());
                }
                BaselineSnapshot snapshot = EngineRig.awaitPrompt(engine, i);
                String actualHash = BaselineStateHasher.hash(snapshot);
                assertThat(actualHash)
                        .as("state hash at step %d of %s", i, traceName(seed))
                        .isEqualTo(trace.steps().get(i).stateHash());

                BaselineAction action =
                        GoldenActionCodec.decode(trace.steps().get(i).actionToken(), snapshot);
                engine.feed(action);
            }

            engine.awaitCompletion(120, TimeUnit.SECONDS);
            assertThat(engine.getFailure())
                    .as("game thread failure replaying %s", traceName(seed))
                    .isNull();
        }
    }

    private static GoldenTrace load(long seed) throws IOException {
        String name = traceName(seed);
        try (InputStream in =
                GoldenTraceReplayShould.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("golden trace not found on classpath: " + name
                        + " (run GoldenTraceGenerator to produce it)");
            }
            return GoldenTrace.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String traceName(long seed) {
        return "golden/golden-" + seed + ".trace";
    }
}
