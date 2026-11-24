package forge.baseline.engine;

import forge.ai.LobbyPlayerAi;
import forge.baseline.state.BaselineSnapshot;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;

import java.util.List;

/**
 * Builds the standard two-player seeded Commander game used by the adapter
 * tests: one {@link ScriptedLobbyPlayer} plus one {@link LobbyPlayerAi}, each
 * with a 20-card deck (one scripted commander plus nineteen basic islands).
 * Cards are created from inline script text, so each test states exactly the
 * card characteristics it relies on.
 */
public final class EngineRig {

    public static final String BEAR_NAME = "Baseline Bear";
    public static final String ISLAND_NAME = "Baseline Island";

    private EngineRig() {
    }

    public record Result(BaselineEngine engine, ScriptedLobbyPlayer scripted, LobbyPlayerAi ai) {
    }

    /** Builds (but does not start) the game; register sinks before calling {@code engine.start()}. */
    public static Result create(long seed) {
        // The engine reads localized strings and keyword descriptions from these singletons.
        Localizer.getInstance().initialize("en-US", langDir());
        Lang.createInstance("en-US");

        ScriptedLobbyPlayer scripted = new ScriptedLobbyPlayer("Scripted");
        LobbyPlayerAi ai = new LobbyPlayerAi("AI", null);
        BaselineEngine engine = new BaselineEngine(seed,
                List.of(commanderDeck(), commanderDeck()),
                List.of(scripted, ai));
        return new Result(engine, scripted, ai);
    }

    /** Waits for the initial (turn-1) snapshot; fails if the game thread dies first. */
    public static BaselineSnapshot awaitInitialSnapshot(BaselineEngine engine) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (engine.getSnapshot() == null) {
            if (engine.getFailure() != null || System.currentTimeMillis() > deadline) {
                break;
            }
            sleep();
        }
        BaselineSnapshot snapshot = engine.getSnapshot();
        if (snapshot == null) {
            throw new AssertionError("initial snapshot was not emitted; game thread failure: "
                    + engine.getFailure(), engine.getFailure());
        }
        return snapshot;
    }

    /** Waits for the prompt snapshot at the given emission index. */
    static BaselineSnapshot awaitPrompt(BaselineEngine engine, int index) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (engine.getPromptSnapshots().size() <= index) {
            if (engine.getFailure() != null || System.currentTimeMillis() > deadline) {
                break;
            }
            sleep();
        }
        if (engine.getPromptSnapshots().size() <= index) {
            throw new AssertionError("prompt snapshot #" + index + " was not emitted; game thread failure: "
                    + engine.getFailure(), engine.getFailure());
        }
        return engine.getPromptSnapshots().get(index);
    }

    private static void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Two 20-card Commander decks: one scripted commander plus nineteen basic islands. */
    public static Deck commanderDeck() {
        Deck deck = new Deck();
        deck.getOrCreate(DeckSection.Commander).add(bearCard(), 1);
        deck.getOrCreate(DeckSection.Main).add(islandCard(), 19);
        return deck;
    }

    static PaperCard bearCard() {
        return new PaperCard(CardRules.fromScript(List.of(
                "Name:" + BEAR_NAME,
                "ManaCost:1",
                "Types:Creature Bear",
                "PT:2/2")), "", CardRarity.Common);
    }

    // Basic lands get their tap-for-mana ability generated from the subtype;
    // no A: line is needed (the real Island script has none either).
    static PaperCard islandCard() {
        return new PaperCard(CardRules.fromScript(List.of(
                "Name:" + ISLAND_NAME,
                "ManaCost:no cost",
                "Types:Basic Land Island")), "", CardRarity.Common);
    }

    private static String langDir() {
        String override = System.getProperty("forge.baseline.langDir");
        return override != null ? override : "../forge-gui/res/languages/";
    }
}
