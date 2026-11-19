package forge.screens.deckeditor.controllers;

import forge.card.CardRulesPredicates;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.deck.DeckBase;
import forge.deck.DeckStatistics;
import forge.gui.framework.ICDoc;
import forge.item.InventoryItem;
import forge.item.PaperCard;
import forge.item.PaperCardPredicates;
import forge.screens.deckeditor.CDeckEditorUI;
import forge.screens.deckeditor.views.VStatistics;
import forge.util.ItemPool;

/**
 * Controls the "analysis" panel in the deck editor UI.
 *
 * <p>
 * The controller is responsible for all counting; it computes a small set of
 * numeric arrays (color identity, card types and a per-mana-value curve with
 * its colored-symbol breakdown) and hands them to {@link VStatistics}, which
 * paints them as modern charts. The displayed values are identical to those the
 * previous text-grid version produced.
 *
 * <br><br><i>(C at beginning of class name denotes a control class.)</i>
 */
public enum CStatistics implements ICDoc {
    /** */
    SINGLETON_INSTANCE;

    //========== Overridden methods

    @Override
    public void register() {
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.ICDoc#initialize()
     */
    @Override
    public void initialize() {
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.ICDoc#update()
     */
    @Override
    public void update() {
        analyze();
    }

    //========== Other methods
    @SuppressWarnings("unchecked")
    private <T extends InventoryItem, TModel extends DeckBase> void analyze() {
        final ACEditorBase<T, TModel> ed = (ACEditorBase<T, TModel>)
                CDeckEditorUI.SINGLETON_INSTANCE.getCurrentEditorController();

        if (ed == null) { return; }

        final ItemPool<PaperCard> deck = ItemPool.createFrom(ed.getDeckManager().getPool(), PaperCard.class);

        final int total = deck.countAll();
        // Avoid divide-by-zero for the percentage readouts while still showing real counts.
        final int percentTotal = total == 0 ? 1 : total;

        // Card types — order matches VStatistics.setCardTypeDistribution:
        // creature, land, enchantment, artifact, instant, sorcery, planeswalker
        final int[] typeCounts = {
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_CREATURE)),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_LAND)),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_ENCHANTMENT)),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_ARTIFACT)),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_INSTANT)),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_SORCERY)),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_PLANESWALKER)) };

        // Colors — order matches VStatistics.setColorDistribution:
        // white, blue, black, red, green, colorless, multicolor
        final int[] colorCounts = {
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.isMonoColor(MagicColor.WHITE))),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.isMonoColor(MagicColor.BLUE))),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.isMonoColor(MagicColor.BLACK))),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.isMonoColor(MagicColor.RED))),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.isMonoColor(MagicColor.GREEN))),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_COLORLESS)),
                deck.countAll(PaperCardPredicates.fromRules(CardRulesPredicates.IS_MULTICOLOR)) };

        // Mana curve — buckets 0,1,2,3,4,5,6+ with a per-bucket W U B R G colorless symbol total.
        final int[] curveCounts = new int[7];
        final int[][] curveShards = new int[7][6];
        int tmc = 0;
        for (final PaperCard c : deck.toFlatList()) {
            final int cmc = c.getRules().getManaCost().getCMC();
            tmc += cmc;
            final int bucket = Math.min(cmc, 6);
            curveCounts[bucket]++;
            final int[] cardShards = c.getRules().getManaCost().getColorShardCounts();
            for (int i = 0; i < 6; i++) {
                curveShards[bucket][i] += cardShards[i];
            }
        }
        final double amc = Math.round((double) tmc / (double) percentTotal * 100) / 100.0d;

        final VStatistics view = VStatistics.SINGLETON_INSTANCE;
        view.setSummary(total, tmc, amc);
        view.setColorDistribution(colorCounts, percentTotal);
        view.setCardTypeDistribution(typeCounts, percentTotal);
        view.setManaCurve(curveCounts, curveShards);

        // Commander points summary (unchanged behavior).
        final DeckController<TModel> controller = ed.getDeckController();
        final DeckBase model = controller == null ? null : controller.getModel();
        final Deck wholeDeck = model == null ? null : model.getHumanDeck();
        final String points = wholeDeck == null ? null : DeckStatistics.pointsSummary(wholeDeck);
        view.setPoints(points);
    }

    /**
     * Divides X by Y, multiplies by 100, rounds, returns.
     *
     * @param x0 &emsp; Numerator (int)
     * @param y0 &emsp; Denominator (int)
     * @return rounded result (int)
     */
    public static int calculatePercentage(final int x0, final int y0) {
        return (int) Math.round((double) (x0 * 100) / (double) y0);
    }

    public static int[] calculateShards(final ItemPool<PaperCard> deck) {
        final int[] counts = new int[6]; // in WUBRGC order
        for (final PaperCard c : deck.toFlatList()) {
            final int[] cShards = c.getRules().getManaCost().getColorShardCounts();
            for (int i = 0; i < 6; i++) {
                counts[i] += cShards[i];
            }
        }
        return counts;
    }

    public static int calculateTotalShards(final int[] counts) {
        int total = 0;
        for (final int count : counts) {
            total += count;
        }
        return total;
    }
}
