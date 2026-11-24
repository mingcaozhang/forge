package forge.baseline.parity;

import forge.LobbyPlayer;
import forge.ai.LobbyPlayerAi;
import forge.baseline.action.BaselineAction;
import forge.baseline.engine.BaselineEngine;
import forge.baseline.engine.ScriptedLobbyPlayer;
import forge.baseline.golden.BaselineStateHasher;
import forge.baseline.golden.GoldenActionCodec;
import forge.baseline.golden.GoldenTrace;
import forge.baseline.state.BaselineSnapshot;
import forge.baseline.trajectory.ActionPolicy;
import forge.deck.Deck;
import forge.game.GameOutcome;
import forge.game.player.PlayerOutcome;
import forge.game.player.RegisteredPlayer;
import forge.util.FileUtil;
import forge.util.Lang;
import forge.util.Localizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The Java half of the Phase 2 parity harness (34.1): a manual main class that
 * runs a seeded headless Commander match through the {@link BaselineEngine}
 * and emits a parity report in the {@link GoldenTrace} text format plus a
 * final outcome line. It is the reference implementation the Rust {@code
 * parity} runner is checked against: same inputs, same report.
 *
 * <p>Inputs: a seed, one deck spec file per player (see {@link
 * ParityDeckSpec}; player 0 is the scripted side), and either an action file
 * (one {@link GoldenActionCodec} token per line, fed in order at each
 * scripted decision) or a named built-in policy (see {@link
 * ParityPolicies}). The two are mutually exclusive.
 *
 * <p>Report format (one line per step, like the golden traces):
 * {@code <action-token> <64-hex sha256 of the canonical state string>}, plus
 * — with {@code --full-state} — the full canonical state string for
 * divergence diffs. The report ends with one {@code outcome=...} line: the
 * turns played, the end reason, and per player (lobby order) {@code Win} or
 * {@code Loss(<reason>)}. The step lines alone are byte-compatible with a
 * golden trace's steps for the same (seed, decks, policy).
 *
 * <p>Usage (from the {@code forge-baseline} directory, or with {@code
 * --res-dir}):
 * <pre>{@code
 * java forge.baseline.parity.ParityRunner --seed 30001 \
 *     --deck golden-rig-1.deckspec --deck golden-rig-2.deckspec \
 *     --policy fixed [--full-state] [--out report.txt]
 * }</pre>
 *
 * <p>This is a manual tool: it is not a test class, so the GATE does not run
 * it. Exit codes: 0 on success, 1 on a parity failure, 2 on usage errors.
 */
public final class ParityRunner {

    private static final Path DEFAULT_RES_DIR = Path.of("..", "forge-gui", "res");
    private static final long DEFAULT_TIMEOUT_MILLIS = 120_000;

    private ParityRunner() {
    }

    /** The harness inputs: seed, one deck spec per player, and the action source. */
    public record ParityOptions(
            long seed,
            List<Path> deckSpecs,
            Path actionsFile,
            String policyName,
            boolean fullState,
            Path resDir,
            long timeoutMillis) {

        public ParityOptions {
            deckSpecs = List.copyOf(deckSpecs);
        }

        /** The options with the defaults a CLI invocation would leave unset. */
        public static ParityOptions of(long seed, List<Path> deckSpecs, Path actionsFile,
                                       String policyName, boolean fullState) {
            return new ParityOptions(seed, deckSpecs, actionsFile, policyName,
                    fullState, null, DEFAULT_TIMEOUT_MILLIS);
        }
    }

    /** One recorded scripted decision, with the state fingerprint at the prompt. */
    public record ParityStep(String actionToken, String stateHash, String canonicalState) {
    }

    /** The parity report: header + one line per step + the final outcome line. */
    public record ParityReport(int version, long seed, List<ParityStep> steps, String outcome) {

        public ParityReport {
            steps = List.copyOf(steps);
        }

        /** Serialize to the committed line format; see the class javadoc. */
        public String serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append("# forge-baseline parity report\n");
            sb.append("version=").append(version).append('\n');
            sb.append("seed=").append(seed).append('\n');
            sb.append("steps=").append(steps.size()).append('\n');
            for (ParityStep step : steps) {
                sb.append(step.actionToken()).append(' ').append(step.stateHash());
                if (step.canonicalState() != null) {
                    sb.append(' ').append(step.canonicalState());
                }
                sb.append('\n');
            }
            sb.append("outcome=").append(outcome).append('\n');
            return sb.toString();
        }
    }

    /**
     * Runs the match and returns the report. Throws {@link ParityException} on
     * bad input, a game-thread failure, a stall, or an exhausted action file.
     */
    public static ParityReport run(ParityOptions options) {
        if (options == null) {
            throw new NullPointerException("options");
        }
        if (options.deckSpecs().size() < 2) {
            throw new ParityException("a Commander match needs at least two deck specs, got "
                    + options.deckSpecs().size());
        }
        if (options.actionsFile() == null == (options.policyName() == null)) {
            throw new ParityException("exactly one of actionsFile / policyName is required");
        }
        if (options.timeoutMillis() <= 0) {
            throw new ParityException("timeoutMillis must be > 0, was " + options.timeoutMillis());
        }

        Path resDir = resolveResDir(options);
        initLocalizer(resDir);

        List<Deck> decks = new ArrayList<>(options.deckSpecs().size());
        List<String> names = new ArrayList<>(options.deckSpecs().size());
        for (int i = 0; i < options.deckSpecs().size(); i++) {
            ParityDeckSpec.Parsed parsed =
                    ParityDeckSpec.load(options.deckSpecs().get(i), "Player" + (i + 1));
            decks.add(parsed.deck());
            names.add(parsed.name());
        }
        if (new java.util.HashSet<>(names).size() != names.size()) {
            throw new ParityException("duplicated player names in the deck specs: " + names);
        }

        ActionPolicy policy = null;
        List<String> actionTokens = null;
        if (options.policyName() != null) {
            policy = ParityPolicies.byName(options.policyName());
        } else {
            actionTokens = readActionTokens(options.actionsFile());
        }

        List<LobbyPlayer> players = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            // Player 0 is the scripted side fed from the file or the policy;
            // every other seat is driven by the non-interactive AI controller.
            players.add(i == 0
                    ? new ScriptedLobbyPlayer(names.get(0))
                    : new LobbyPlayerAi(names.get(i), null));
        }

        BaselineEngine engine = new BaselineEngine(options.seed(), decks, players);
        engine.start();
        long deadline = System.currentTimeMillis() + options.timeoutMillis();
        try {
            awaitInitialSnapshot(engine, deadline);

            List<ParityStep> steps = new ArrayList<>();
            int fed = 0;
            while (engine.getOutcome() == null && engine.getFailure() == null) {
                if (System.currentTimeMillis() > deadline) {
                    throw new ParityException("the match stalled after " + fed
                            + " decisions within " + options.timeoutMillis() + " ms");
                }
                List<BaselineSnapshot> prompts = engine.getPromptSnapshots();
                if (prompts.size() <= fed) {
                    sleep(10);
                    continue;
                }
                BaselineSnapshot prompt = prompts.get(fed);
                BaselineAction action;
                if (policy != null) {
                    action = policy.choose(prompt);
                } else {
                    if (fed >= actionTokens.size()) {
                        throw new ParityException("the action file ran out after " + fed
                                + " decisions; the match had not ended");
                    }
                    try {
                        action = GoldenActionCodec.decode(actionTokens.get(fed), prompt);
                    } catch (RuntimeException e) {
                        throw new ParityException("cannot feed action line " + (fed + 1)
                                + " (" + actionTokens.get(fed) + "): " + e.getMessage(), e);
                    }
                }
                steps.add(new ParityStep(
                        GoldenActionCodec.encode(action),
                        BaselineStateHasher.hash(prompt),
                        options.fullState() ? BaselineStateHasher.canonicalString(prompt) : null));
                engine.feed(action);
                fed++;
            }
            if (engine.getFailure() != null) {
                throw new ParityException("the game thread failed", engine.getFailure());
            }
            if (!engine.awaitCompletion(30, TimeUnit.SECONDS)) {
                throw new ParityException("the game thread did not finish after its outcome was recorded");
            }
            return new ParityReport(GoldenTrace.VERSION, options.seed(), steps,
                    outcomeLine(engine.getOutcome()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParityException("interrupted while driving the parity match", e);
        }
    }

    /** The final outcome line: stable, locale-free, one per player sorted by name. */
    static String outcomeLine(GameOutcome outcome) {
        StringBuilder sb = new StringBuilder("turns=").append(outcome.getLastTurnNumber())
                .append(";reason=")
                .append(outcome.getWinCondition() == null ? "None" : outcome.getWinCondition().name());
        List<RegisteredPlayer> players = new ArrayList<>(outcome.getPlayerNames().keySet());
        players.sort(Comparator.comparing(outcome.getPlayerNames()::get));
        for (RegisteredPlayer player : players) {
            PlayerOutcome playerOutcome = outcome.getPlayerOutcome(player);
            sb.append(';').append(outcome.getPlayerNames().get(player)).append('=');
            if (playerOutcome.hasWon()) {
                sb.append("Win");
            } else {
                sb.append("Loss(").append(playerOutcome.lossState.name()).append(')');
            }
        }
        return sb.toString();
    }

    /**
     * The CLI entry point: parses the arguments, runs the match, and prints
     * (or writes) the report. Exit codes: 0 success, 1 parity failure, 2 usage.
     */
    public static void main(String[] args) {
        Long seed = null;
        List<Path> deckSpecs = new ArrayList<>();
        Path actionsFile = null;
        String policyName = null;
        boolean fullState = false;
        Path resDir = null;
        long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        Path out = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value;
            switch (arg) {
                case "--seed" -> seed = parseLong(value(args, ++i, arg));
                case "--deck" -> deckSpecs.add(Path.of(value(args, ++i, arg)));
                case "--actions" -> actionsFile = Path.of(value(args, ++i, arg));
                case "--policy" -> policyName = value(args, ++i, arg);
                case "--full-state" -> fullState = true;
                case "--res-dir" -> resDir = Path.of(value(args, ++i, arg));
                case "--timeout-seconds" -> timeoutMillis = parseLong(value(args, ++i, arg)) * 1000;
                case "--out" -> out = Path.of(value(args, ++i, arg));
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> {
                    System.err.println("unknown option: " + arg);
                    printUsage();
                    System.exit(2);
                }
            }
        }

        try {
            if (seed == null) {
                throw new ParityException("--seed is required");
            }
            if (deckSpecs.size() < 2) {
                throw new ParityException("at least two --deck specs are required (one per player)");
            }
            if (actionsFile == null == (policyName == null)) {
                throw new ParityException("exactly one of --actions / --policy is required");
            }
            if (timeoutMillis <= 0) {
                throw new ParityException("--timeout-seconds must be > 0");
            }
        } catch (ParityException e) {
            System.err.println("usage error: " + e.getMessage());
            System.exit(2);
            return;
        }

        ParityReport report;
        try {
            report = run(new ParityOptions(seed, deckSpecs, actionsFile, policyName,
                    fullState, resDir, timeoutMillis));
        } catch (ParityException e) {
            System.err.println("parity run failed: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("caused by: " + e.getCause());
            }
            System.exit(1);
            return;
        }

        String text = report.serialize();
        try {
            if (out != null) {
                if (out.getParent() != null) {
                    Files.createDirectories(out.getParent());
                }
                Files.writeString(out, text);
                System.out.println("wrote " + out + " (" + report.steps().size() + " steps)");
            } else {
                System.out.print(text);
            }
        } catch (java.io.IOException e) {
            System.err.println("cannot write the report: " + e);
            System.exit(1);
        }
    }

    private static Path resolveResDir(ParityOptions options) {
        Path resDir;
        if (options.resDir() != null) {
            resDir = options.resDir();
        } else if (System.getProperty("forge.baseline.resDir") != null) {
            resDir = Path.of(System.getProperty("forge.baseline.resDir"));
        } else {
            resDir = DEFAULT_RES_DIR;
        }
        System.setProperty("forge.baseline.resDir", resDir.toString());
        return resDir;
    }

    private static void initLocalizer(Path resDir) {
        String langDir = System.getProperty("forge.baseline.langDir",
                resDir.resolve("languages").toString());
        Localizer.getInstance().initialize("en-US", langDir);
        Lang.createInstance("en-US");
    }

    private static List<String> readActionTokens(Path file) {
        List<String> lines;
        try {
            lines = FileUtil.readFile(file.toFile());
        } catch (Exception e) {
            throw new ParityException("cannot read action file " + file, e);
        }
        List<String> tokens = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.contains(" ")) {
                throw new ParityException("action file line has more than one field: " + line);
            }
            tokens.add(line);
        }
        if (tokens.isEmpty()) {
            throw new ParityException("action file " + file + " has no action tokens");
        }
        return tokens;
    }

    private static void awaitInitialSnapshot(BaselineEngine engine, long deadline) {
        while (engine.getSnapshot() == null) {
            if (engine.getFailure() != null) {
                throw new ParityException("the game thread failed before the initial snapshot",
                        engine.getFailure());
            }
            if (System.currentTimeMillis() > deadline) {
                throw new ParityException("the match never emitted its initial snapshot");
            }
            sleep(10);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParityException("interrupted while driving the parity match", e);
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new ParityException(option + " needs a value");
        }
        return args[index];
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ParityException("not a number: " + value);
        }
    }

    private static void printUsage() {
        System.out.println("""
                usage: ParityRunner --seed <long> --deck <file> --deck <file> ...
                              (--actions <file> | --policy <fixed|pass>)
                              [--full-state] [--res-dir <dir>] [--timeout-seconds <n>]
                              [--out <file>]

                Runs a seeded headless Commander match and prints a parity report:
                one "<action-token> <state-hash>" line per scripted decision, then
                an "outcome=..." line. With --full-state each step line also
                carries the full canonical state string.""");
    }
}
