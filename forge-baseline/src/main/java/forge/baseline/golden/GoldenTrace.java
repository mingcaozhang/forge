package forge.baseline.golden;

import java.util.List;

/**
 * A recorded baseline game: the seed plus the ordered sequence of steps. Each
 * step pairs the state fingerprint {@link Step#stateHash()} of the engine at a
 * prompt boundary with the {@link Step#actionToken()} token fed to advance past
 * it (see {@link GoldenActionCodec}). The last step's action is the one that
 * ends the game (typically a concede).
 *
 * <p>Serialization is a small committed text format: a header (format version,
 * seed, step count) followed by one {@code <token> <stateHash>} line per step.
 * The format is stable and versioned so a future change to the fingerprint or
 * token grammar can be rejected by a replay test rather than silently
 * mismatching.
 */
public record GoldenTrace(int version, long seed, List<GoldenTrace.Step> steps) {

    /** The current golden-trace format version. */
    public static final int VERSION = 1;

    /** One recorded decision: the state fingerprint at the prompt, and the action token fed to advance. */
    public record Step(String actionToken, String stateHash) {
    }

    public GoldenTrace {
        steps = List.copyOf(steps);
    }

    /** Serialize to the committed text format. */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# forge-baseline golden trace\n");
        sb.append("version=").append(version).append('\n');
        sb.append("seed=").append(seed).append('\n');
        sb.append("steps=").append(steps.size()).append('\n');
        for (Step step : steps) {
            sb.append(step.actionToken()).append(' ').append(step.stateHash()).append('\n');
        }
        return sb.toString();
    }

    /** Parse the committed text format produced by {@link #serialize()}. */
    public static GoldenTrace parse(String text) {
        int version = -1;
        long seed = Long.MIN_VALUE;
        int declaredSteps = -1;
        List<Step> steps = new java.util.ArrayList<>();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("version=")) {
                version = Integer.parseInt(line.substring("version=".length()).strip());
            } else if (line.startsWith("seed=")) {
                seed = Long.parseLong(line.substring("seed=".length()).strip());
            } else if (line.startsWith("steps=")) {
                declaredSteps = Integer.parseInt(line.substring("steps=".length()).strip());
            } else {
                int space = line.indexOf(' ');
                if (space < 0) {
                    throw new IllegalArgumentException("malformed golden-trace step line: " + line);
                }
                steps.add(new Step(line.substring(0, space), line.substring(space + 1).strip()));
            }
        }
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported golden-trace version: " + version);
        }
        if (declaredSteps != steps.size()) {
            throw new IllegalArgumentException("golden-trace step count mismatch: declared "
                    + declaredSteps + " but found " + steps.size());
        }
        return new GoldenTrace(version, seed, steps);
    }
}
