package forge.itemmanager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import javax.swing.JSpinner;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import forge.GuiDesktop;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.game.GameFormat;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.itemmanager.SItemManagerUtil.StatTypes;
import forge.itemmanager.filters.CardCMCRangeFilter;
import forge.itemmanager.filters.CardColorFilter;
import forge.itemmanager.filters.CardFormatFilter;
import forge.itemmanager.filters.ItemFilter;
import forge.itemmanager.filters.TextSearchFilter;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.toolbox.FLabel;
import forge.toolbox.FSkin;
import forge.toolbox.FTextField;
import forge.view.FView;

/**
 * Locks the predicate behavior of the deck-editor filters against independent
 * oracles. Each GUI-backed filter is built through its real {@code getWidget()}
 * path, its widget-held state is driven, and the predicate produced by
 * {@link ItemFilter#buildPredicate(Class)} is compared against a hand-written
 * expectation that does not reuse the filter's own code path.
 */
public class DeckEditorFilterParityTest {

    private static boolean initialized = false;

    private CardManager manager;
    private List<PaperCard> allSample;
    private List<PaperCard> nonLandSample;

    @BeforeClass(alwaysRun = true)
    public void setUp() throws Exception {
        if (!initialized) {
            GuiBase.setInterface(new GuiDesktop());
            FModel.initialize(null, preferences -> {
                preferences.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, false);
                preferences.setPref(FPref.UI_LANGUAGE, "en-US");
                return null;
            });
            // FSkin.loadFull() resolves the main frame via Singletons.getView().getFrame(),
            // which is only populated by Singletons.initializeOnce(true) in the real app.
            // Wire the view singleton directly so the skin/look-and-feel path can initialize.
            setSingletonsView(FView.SINGLETON_INSTANCE);
            FSkin.loadLight("default", true);
            FSkin.loadFull(true);
            initialized = true;
        }

        manager = new CardManager(null, false, false, false);

        allSample = new ArrayList<>();
        nonLandSample = new ArrayList<>();
        for (Entry<PaperCard, Integer> entry : FModel.getAllCards()) {
            PaperCard card = entry.getKey();
            if (allSample.size() >= 500) {
                break;
            }
            allSample.add(card);
            if (!card.getRules().getType().isLand()) {
                nonLandSample.add(card);
            }
        }
        Assert.assertFalse(allSample.isEmpty(), "expected at least one card in the card pool");
        Assert.assertFalse(nonLandSample.isEmpty(), "expected at least one nonland card in the card pool");
    }

    @Test
    public void textSearchFilterMatchesNameSubstringOracle() throws Exception {
        TextSearchFilter<PaperCard> filter = new TextSearchFilter<>(manager);
        filter.getWidget(); // build txtSearch

        checkTextFilter(filter, "bolt", false);
        checkTextFilter(filter, "zzqxvnonsense", false);
        checkTextFilter(filter, "Lightning", true);
    }

    private void checkTextFilter(TextSearchFilter<PaperCard> filter, String query, boolean expectSomePass) throws Exception {
        setText(filter, query);
        final String needle = query.toLowerCase();
        Predicate<PaperCard> predicate = filter.buildPredicate(PaperCard.class);

        int passed = 0;
        for (PaperCard card : allSample) {
            boolean expected = card.getName().toLowerCase().contains(needle);
            Assert.assertEquals(predicate.test(card), expected,
                    "text filter mismatch for query '" + query + "' on card " + card.getName());
            if (expected) {
                passed++;
            }
        }
        if (expectSomePass) {
            Assert.assertTrue(passed > 0, "expected at least one card to match query '" + query + "'");
        }
    }

    @Test
    public void cmcRangeFilterMatchesManaValueOracle() throws Exception {
        CardCMCRangeFilter filter = new CardCMCRangeFilter(manager);
        filter.getWidget(); // build lower/upper spinners

        setSpinnerValue(filter, "lowerBound", 2);
        setSpinnerValue(filter, "upperBound", 6);

        final int lower = readSpinnerValue(filter, "lowerBound");
        final int upper = readSpinnerValue(filter, "upperBound");
        final boolean hasMin = lower != 0; // ValueRangeFilter.minValue()
        final boolean hasMax = upper != 20; // ValueRangeFilter.maxValue()

        Predicate<PaperCard> predicate = filter.buildPredicate(PaperCard.class);
        for (PaperCard card : allSample) {
            double cmc = card.getRules().getManaCost().getCMC();
            boolean expected = (!hasMin || cmc >= lower) && (!hasMax || cmc <= upper);
            Assert.assertEquals(predicate.test(card), expected,
                    "cmc filter mismatch on card " + card.getName() + " (cmc=" + cmc + ")");
        }
    }

    @Test
    public void formatFilterMatchesFormatRuleOracle() {
        GameFormat commander = FModel.getFormats().get("Commander");
        Assert.assertNotNull(commander, "Commander format should be registered");

        CardFormatFilter filter = new CardFormatFilter(manager, commander);
        filter.getWidget();

        Predicate<PaperCard> predicate = filter.buildPredicate(PaperCard.class);
        Predicate<PaperCard> oracle = commander.getFilterRules();

        int accepted = 0;
        int rejected = 0;
        for (PaperCard card : allSample) {
            boolean expected = oracle.test(card);
            Assert.assertEquals(predicate.test(card), expected,
                    "format filter mismatch on card " + card.getName());
            if (expected) {
                accepted++;
            } else {
                rejected++;
            }
        }
        Assert.assertTrue(accepted > 0, "Commander format should accept at least one sample card");
        Assert.assertTrue(rejected > 0, "Commander format should reject at least one sample card");
    }

    @Test
    public void colorToggleFilterMatchesColorOracle() throws Exception {
        CardColorFilter filter = new CardColorFilter(manager);
        filter.getWidget(); // build the seven toggle buttons, all selected

        Map<StatTypes, FLabel> buttonMap = readButtonMap(filter);

        // Default: every toggle selected, so every nonland card passes.
        Predicate<PaperCard> allSelected = filter.buildPredicate(PaperCard.class);
        for (PaperCard card : nonLandSample) {
            Assert.assertTrue(allSelected.test(card),
                    "all-selected color filter should pass nonland card " + card.getName());
        }

        // Deselect COLORLESS only: pass iff not colorless.
        buttonMap.get(StatTypes.COLORLESS).setSelected(false);
        Predicate<PaperCard> noColorless = filter.buildPredicate(PaperCard.class);
        assertColorOracle(noColorless, card -> !card.getRules().getColor().isColorless(),
                "no-colorless color filter mismatch");

        // Re-select COLORLESS, then deselect MULTICOLOR only: pass iff not multicolor.
        buttonMap.get(StatTypes.COLORLESS).setSelected(true);
        buttonMap.get(StatTypes.MULTICOLOR).setSelected(false);
        Predicate<PaperCard> noMulticolor = filter.buildPredicate(PaperCard.class);
        assertColorOracle(noMulticolor, card -> !card.getRules().getColor().isMulticolor(),
                "no-multicolor color filter mismatch");

        // White only: pass iff the card can be cast with white mana and is neither
        // colorless nor multicolor.
        buttonMap.get(StatTypes.MULTICOLOR).setSelected(true);
        buttonMap.get(StatTypes.WHITE).setSelected(true);
        buttonMap.get(StatTypes.BLUE).setSelected(false);
        buttonMap.get(StatTypes.BLACK).setSelected(false);
        buttonMap.get(StatTypes.RED).setSelected(false);
        buttonMap.get(StatTypes.GREEN).setSelected(false);
        buttonMap.get(StatTypes.COLORLESS).setSelected(false);
        buttonMap.get(StatTypes.MULTICOLOR).setSelected(false);

        Predicate<PaperCard> whiteOnly = filter.buildPredicate(PaperCard.class);
        assertColorOracle(whiteOnly,
                card -> {
                    ColorSet color = card.getRules().getColor();
                    return !color.isColorless() && !color.isMulticolor()
                            && card.getRules().canCastWithAvailable(MagicColor.WHITE);
                },
                "white-only color filter mismatch");

        int whitePasses = 0;
        for (PaperCard card : nonLandSample) {
            if (whiteOnly.test(card)) {
                whitePasses++;
            }
        }
        Assert.assertTrue(whitePasses > 0, "white-only filter should pass at least one nonland card");
    }

    private void assertColorOracle(Predicate<PaperCard> predicate, Predicate<PaperCard> oracle, String message) {
        for (PaperCard card : nonLandSample) {
            Assert.assertEquals(predicate.test(card), oracle.test(card),
                    message + " on card " + card.getName() + " (" + card.getRules().getColor() + ")");
        }
    }

    private static void setText(TextSearchFilter<PaperCard> filter, String text) throws Exception {
        Object txt = readField(filter, "txtSearch");
        ((FTextField) txt).setText(text);
    }

    private static void setSpinnerValue(ItemFilter<?> filter, String fieldName, int value) throws Exception {
        Object spinner = readField(filter, fieldName);
        ((JSpinner) spinner).setValue(value);
    }

    private static int readSpinnerValue(ItemFilter<?> filter, String fieldName) throws Exception {
        Object spinner = readField(filter, fieldName);
        return ((Number) ((JSpinner) spinner).getValue()).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<StatTypes, FLabel> readButtonMap(CardColorFilter filter) throws Exception {
        return (Map<StatTypes, FLabel>) readField(filter, "buttonMap");
    }

    private static void setSingletonsView(FView view) throws Exception {
        Field f = forge.Singletons.class.getDeclaredField("view");
        f.setAccessible(true);
        f.set(null, view);
    }

    private static Object readField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found on " + target.getClass());
    }
}
