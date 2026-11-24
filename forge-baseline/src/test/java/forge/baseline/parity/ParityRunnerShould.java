package forge.baseline.parity;

import forge.baseline.golden.GoldenTrace;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 34.1 parity runner test. Runs {@link ParityRunner} with the same
 * inputs that produced the committed golden trace for seed 30001 — the deck
 * spec files describing the {@code EngineRig} decks plus the {@code fixed}
 * policy — and asserts the report's step lines are byte-identical to the
 * trace's step lines, so a divergence in engine, policy, or deck parsing
 * fails here at the first differing step.
 */
public class ParityRunnerShould {

    @Test
    public void reproducesGoldenTrace30001StepLines() throws Exception {
        ParityRunner.ParityReport report = ParityRunner.run(new ParityRunner.ParityOptions(30001L,
                List.of(goldenDir().resolve("golden-rig-1.deckspec"),
                        goldenDir().resolve("golden-rig-2.deckspec")),
                null, "fixed", false, null, 120_000));

        GoldenTrace golden = loadGolden(30001L);
        assertThat(report.version()).isEqualTo(GoldenTrace.VERSION);
        assertThat(report.seed()).isEqualTo(30001L);
        assertThat(report.steps()).hasSize(golden.steps().size());
        for (int i = 0; i < golden.steps().size(); i++) {
            assertThat(report.steps().get(i).actionToken())
                    .as("action token at step %d", i)
                    .isEqualTo(golden.steps().get(i).actionToken());
            assertThat(report.steps().get(i).stateHash())
                    .as("state hash at step %d", i)
                    .isEqualTo(golden.steps().get(i).stateHash());
            assertThat(report.steps().get(i).canonicalState())
                    .as("canonical state at step %d", i)
                    .isNull();
        }
        assertThat(report.outcome())
                .isEqualTo("turns=7;reason=AllOpponentsLost;AI=Win;Scripted=Loss(Conceded)");
    }

    @Test
    public void rejectsInvalidDeckSpecsAndPolicies() {
        assertThatThrownBy(() -> ParityDeckSpec.parse("P", ""))
                .isInstanceOf(ParityException.class)
                .hasMessageContaining("version");
        assertThatThrownBy(() -> ParityDeckSpec.parse("P", "version=1\n"))
                .isInstanceOf(ParityException.class)
                .hasMessageContaining("Commander");
        assertThatThrownBy(() -> ParityDeckSpec.parse("P",
                "version=1\n[Nowhere]\ncount=1\nName:X\n"))
                .isInstanceOf(ParityException.class)
                .hasMessageContaining("unknown deck section");
        assertThatThrownBy(() -> ParityPolicies.byName("nope"))
                .isInstanceOf(ParityException.class)
                .hasMessageContaining("unknown policy");
    }

    private static Path goldenDir() {
        return Path.of("src", "test", "resources", "golden");
    }

    private static GoldenTrace loadGolden(long seed) throws IOException {
        String name = "golden/golden-" + seed + ".trace";
        try (InputStream in = ParityRunnerShould.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("golden trace not found on classpath: " + name);
            }
            return GoldenTrace.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
