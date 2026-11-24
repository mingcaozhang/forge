package forge.baseline.parity;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.util.FileUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The parity harness's deck input format: one small text file per lobby
 * player, describing that player's deck as inline card scripts (the same
 * script grammar the card database files use), so a deck is fully specified
 * by the file without a card database. The format is deliberately trivial to
 * parse — line oriented, no nesting — because the Rust parity runner parses
 * the identical files.
 *
 * <p>Grammar (version 1):
 * <pre>{@code
 * # comments and blank lines are ignored
 * version=1
 * name=Scripted            # optional lobby player name (default: Player<n>)
 * [Commander]               # a DeckSection header
 * count=1                   # starts a card entry: N copies of the card below
 * Name:Baseline Bear        # the card's inline script (Key:Value lines)
 * ManaCost:1
 * Types:Creature Bear
 * PT:2/2
 * [Main]
 * count=19
 * Name:Baseline Island
 * ManaCost:no cost
 * Types:Basic Land Island
 * }</pre>
 *
 * <p>A valid spec has {@code version=1}, at least one card entry, and a
 * {@code [Commander]} section holding exactly one commander (the baseline
 * engine plays Commander). Multiple {@code count=} entries per section are
 * allowed; each is one card definition.
 */
public final class ParityDeckSpec {

    /** The only supported spec version. */
    public static final int VERSION = 1;

    private ParityDeckSpec() {
    }

    /** A parsed spec: the lobby player's name and the built deck. */
    public record Parsed(String name, Deck deck) {
    }

    /** Reads and parses the deck spec at {@code file}; {@code defaultName} is used when the spec has no {@code name=}. */
    public static Parsed load(Path file, String defaultName) {
        String text;
        try {
            text = FileUtil.readFileToString(file.toFile());
        } catch (Exception e) {
            throw new ParityException("cannot read deck spec " + file, e);
        }
        return parse(defaultName, text);
    }

    /** Parses the deck spec text; see the class javadoc for the grammar. */
    public static Parsed parse(String defaultName, String text) {
        Integer version = null;
        String name = null;
        DeckSection section = null;
        int count = 0;
        List<String> script = new ArrayList<>();
        Deck deck = new Deck();

        for (String rawLine : text.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                if (count > 0) {
                    flushCard(deck, section, count, script);
                }
                count = 0;
                DeckSection parsed = DeckSection.smartValueOf(line.substring(1, line.length() - 1));
                if (parsed == null) {
                    throw new ParityException("unknown deck section: " + line);
                }
                section = parsed;
            } else if (line.startsWith("version=")) {
                if (version != null) {
                    throw new ParityException("duplicate version= line");
                }
                version = Integer.parseInt(line.substring("version=".length()).strip());
            } else if (line.startsWith("name=")) {
                if (name != null) {
                    throw new ParityException("duplicate name= line");
                }
                name = line.substring("name=".length()).strip();
                if (name.isEmpty()) {
                    throw new ParityException("empty name= line");
                }
            } else if (line.startsWith("count=")) {
                flushCard(deck, section, count, script);
                if (section == null) {
                    throw new ParityException("count= line before any [section] header: " + line);
                }
                count = Integer.parseInt(line.substring("count=".length()).strip());
                if (count < 1) {
                    throw new ParityException("card count must be >= 1: " + line);
                }
                script = new ArrayList<>();
            } else {
                if (section == null || count < 1) {
                    throw new ParityException("card script line outside a card entry: " + line);
                }
                if (!line.contains(":")) {
                    throw new ParityException("malformed card script line (expected Key:Value): " + line);
                }
                script.add(line);
            }
        }
        flushCard(deck, section, count, script);

        if (version == null || version != VERSION) {
            throw new ParityException("deck spec requires version=" + VERSION);
        }
        List<PaperCard> commanders = deck.getCommanders();
        if (commanders.isEmpty()) {
            throw new ParityException("deck spec has no card in the [Commander] section");
        }
        if (commanders.size() > 1) {
            throw new ParityException("deck spec has " + commanders.size()
                    + " commanders; the parity harness plays Commander with exactly one");
        }
        deck.setName(name != null ? name : defaultName);
        return new Parsed(name != null ? name : defaultName, deck);
    }

    private static void flushCard(Deck deck, DeckSection section, int count, List<String> script) {
        if (count < 1) {
            return;
        }
        if (script.isEmpty()) {
            throw new ParityException("count= entry with no card script lines");
        }
        if (script.stream().noneMatch(l -> l.startsWith("Name:"))) {
            throw new ParityException("card entry without a Name: line: " + script);
        }
        PaperCard card = new PaperCard(CardRules.fromScript(script), "", CardRarity.Common);
        deck.getOrCreate(section).add(card, count);
    }
}
