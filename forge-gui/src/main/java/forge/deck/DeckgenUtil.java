package forge.deck;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import forge.card.CardDb;
import forge.card.ColorSet;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.generation.*;
import forge.gui.util.SOptionPane;
import forge.item.PaperCard;
import forge.item.PaperCardPredicates;
import forge.item.PreconDeck;
import forge.itemmanager.IItemManager;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.util.*;
import forge.util.storage.IStorage;
import forge.util.storage.StorageBase;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 
 * Utility collection for various types of decks.
 * - Builders (builds or retrieves deck based on a selection)
 * - Randomizers (retrieves random deck of selected type)
 * - Color checker (see javadoc)
 * - Decklist display-er
 */
// TODO This class can be used for home menu constructed deck generation as well.
public class DeckgenUtil {
    private static List<DeckProxy> advPrecons = Lists.newArrayList(), advThemes = Lists.newArrayList(), geneticAI = Lists.newArrayList();

    private static final Supplier<IStorage<PreconDeck>> precons = Suppliers.memoize(
            () -> new StorageBase<>("Preconstructed decks", new PreconDeck.Reader(new File(ForgeConstants.QUEST_PRECON_DIR))))::get;

    public static IStorage<PreconDeck> getPrecons() {
        return precons.get();
    }

    /**
     * @param selection {@link java.lang.String} array
     * @return {@link forge.deck.Deck}
     */
    public static Deck buildColorDeck(List<String> selection, Predicate<PaperCard> formatFilter, boolean forAi) {
        try {
            final Deck deck;
            String deckName = null;

            DeckGeneratorBase gen = null;
            CardDb cardDb = FModel.getMagicDb().getCommonCards();
            if (formatFilter == null){
                if (selection.size() == 1) {
                    gen = new DeckGeneratorMonoColor(cardDb, DeckFormat.Constructed, selection.get(0));
                }
                else if (selection.size() == 2) {
                    gen = new DeckGenerator2Color(cardDb, DeckFormat.Constructed,selection.get(0), selection.get(1));
                }
                else if (selection.size() == 3) {
                    gen = new DeckGenerator3Color(cardDb, DeckFormat.Constructed,selection.get(0), selection.get(1), selection.get(2));
                }
                else {
                    gen = new DeckGenerator5Color(cardDb, DeckFormat.Constructed);
                    deckName = "5 colors";
                }
            }else {
                if (selection.size() == 1) {
                    gen = new DeckGeneratorMonoColor(cardDb, DeckFormat.Constructed, formatFilter, selection.get(0));
                } else if (selection.size() == 2) {
                    gen = new DeckGenerator2Color(cardDb, DeckFormat.Constructed, formatFilter, selection.get(0), selection.get(1));
                } else if (selection.size() == 3) {
                    gen = new DeckGenerator3Color(cardDb, DeckFormat.Constructed, formatFilter, selection.get(0), selection.get(1), selection.get(2));
                } else {
                    gen = new DeckGenerator5Color(cardDb, DeckFormat.Constructed, formatFilter);
                    deckName = "5 colors";
                }
            }
            gen.setSingleton(FModel.getPreferences().getPrefBoolean(FPref.DECKGEN_SINGLETONS));
            gen.setUseArtifacts(!FModel.getPreferences().getPrefBoolean(FPref.DECKGEN_ARTIFACTS));
            final CardPool cards = gen.getDeck(60, forAi);

            if (null == deckName) {
                deckName = Lang.joinHomogenous(List.of(selection));
            }

            // After generating card lists, build deck.
            deck = new Deck("Random deck : " + deckName);
            deck.setDirectory("generated/color");
            deck.getMain().addAll(cards);
            deck.optimiseCardArt(null);  // follow the Card Art Preference, coherent era and border
            return deck;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return buildColorDeck(selection, formatFilter, forAi); //try again if previous color deck couldn't be generated
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getRandomColorDeck(Predicate<PaperCard> formatFilter, boolean forAi) {
        final int[] colorCount = new int[] {1, 2, 3};
        final int count = colorCount[MyRandom.getRandom().nextInt(colorCount.length)];
        final List<String> selection = new ArrayList<>();

        // A simulated selection of "random 1" will trigger the AI selection process.
        for (int i = 0; i < count; i++) { selection.add("Random"); }
        return DeckgenUtil.buildColorDeck(selection, formatFilter, forAi);
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getRandomColorDeck(boolean forAi) {
        final int[] colorCount = new int[] {1, 2, 3, 5};
        final int count = colorCount[MyRandom.getRandom().nextInt(colorCount.length)];
        final List<String> selection = new ArrayList<>();

        // A simulated selection of "random 1" will trigger the AI selection process.
        for (int i = 0; i < count; i++) { selection.add("Random"); }
        return DeckgenUtil.buildColorDeck(selection, null, forAi);
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getRandomCustomDeck() {
        final IStorage<Deck> allDecks = FModel.getDecks().getConstructed();
        // in case user has not created any yet
        if (allDecks.size() == 0) { return null; }
        return Aggregates.random(allDecks);
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getCommanderDeck() {
        final IStorage<Deck> allDecks = FModel.getDecks().getCommander();
        // in case user has not created any yet
        if (allDecks.size() == 0) { return null; }
        return Aggregates.random(allDecks);
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getRandomOrPreconOrThemeDeck(String colors, boolean forAi, boolean isTheme, boolean useGeneticAI) {
        return getRandomOrPreconOrThemeDeck(colors, forAi, isTheme, useGeneticAI, null);
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getRandomOrPreconOrThemeDeck(String colors, boolean forAi, boolean isTheme, boolean useGeneticAI, String[] allowedEditions) {
        final List<String> selection = new ArrayList<>();
        Deck deck = null;
        if (advPrecons.isEmpty()) {
            advPrecons.addAll(DeckProxy.getAllPreconstructedDecks(getPrecons()));
        }
        if (advThemes.isEmpty()) {
            advThemes.addAll(DeckProxy.getAllPreconstructedDecks(getPrecons()));
        }
        if (geneticAI.isEmpty()) {
            geneticAI.addAll(DeckProxy.getAllGeneticAIDecks());
        }
        if (!colors.isEmpty()) {
            for (char c : colors.toLowerCase().toCharArray()) {
                switch (c) {
                    case 'b': selection.add("black");
                    case 'r': selection.add("red");
                    case 'g': selection.add("green");
                    case 'u': selection.add("blue");
                    case 'w': selection.add("white");
                }
            }
        }
        try {
            if (useGeneticAI) {
                if (!selection.isEmpty())
                    deck = geneticAI.stream()
                            .filter(deckProxy -> deckProxy.getColorIdentity().sharesColorWith(ColorSet.fromNames(colors.toCharArray())))
                            .collect(StreamUtil.random()).get().getDeck();
                else
                    deck = Aggregates.random(geneticAI).getDeck();

            } else {
                Predicate<DeckProxy> predicate = deckProxy -> deckProxy.getMainSize() <= 60;
                if (allowedEditions != null && allowedEditions.length > 0) {
                    Set<String> editionSet = new HashSet<>(Arrays.asList(allowedEditions));
                    predicate = predicate.and(dp -> dp.getEdition() != null && editionSet.contains(dp.getEdition().getCode()));
                }
                if (!selection.isEmpty() && selection.size() < 4)
                    predicate = predicate.and(deckProxy -> deckProxy.getColorIdentity().hasAllColors(ColorSet.fromNames(colors.toCharArray()).getColor()));
                List<DeckProxy> source = isTheme ? advThemes : advPrecons;
                deck = source.stream().filter(predicate).collect(StreamUtil.random()).get().getDeck();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (deck != null) {
            return deck;
        }
        return getRandomColorDeck(forAi);
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getRandomPreconDeck() {
        final List<DeckProxy> allDecks = DeckProxy.getAllPreconstructedDecks(getPrecons());
        final int rand = (int) (Math.floor(MyRandom.getRandom().nextDouble() * allDecks.size()));
        return allDecks.get(rand).getDeck();
    }

    /** @return {@link forge.deck.Deck} */
    public static Deck getRandomCommanderPreconDeck() {
        final Iterable<DeckProxy> allDecks = DeckProxy.getAllCommanderPreconDecks();
        return Aggregates.random(allDecks).getDeck();
    }

    public static void randomSelectColors(final IItemManager<DeckProxy> deckManager) {
        final int size = deckManager.getItemCount();
        if (size == 0) { return; }

        int nColors = MyRandom.getRandom().nextInt(3) + 1;
        Integer[] indices = new Integer[nColors];
        for (int i = 0; i < nColors; i++) {
            int next = MyRandom.getRandom().nextInt(size);

            boolean isUnique = true;
            for (int j = 0; j < i; j++) {
                if (indices[j] == next) {
                    isUnique = false;
                    break;
                }
            }
            if (isUnique) {
                indices[i] = next;
            }
            else {
                i--; // try over with this number
            }
        }
        deckManager.setSelectedIndices(indices);
    }

    public static void randomSelect(final IItemManager<DeckProxy> deckManager) {
        final int size = deckManager.getItemCount();
        if (size == 0) { return; }

        deckManager.setSelectedIndex(MyRandom.getRandom().nextInt(size));
    }

    /** 
     * Checks lengths of selected values for color lists
     * to see if a deck generator exists. Alert and visual reminder if fail.
     * 
     * @param colors0 String[]
     * @return boolean
     */
    public static boolean colorCheck(final List<String> colors0) {
        boolean result = true;

        if (colors0.size() == 4) {
            SOptionPane.showMessageDialog(
                    "Sorry, four color generated decks aren't supported yet."
                    + "\n\rPlease use 2, 3, or 5 colors for this deck.",
                    "Generate deck: 4 colors", SOptionPane.ERROR_ICON);
            result = false;
        }
        else if (colors0.size() > 5) {
            SOptionPane.showMessageDialog(
                    "Generate deck: maximum five colors!",
                    "Generate deck: too many colors", SOptionPane.ERROR_ICON);
            result = false;
        }
        return result;
    }

    public static Deck generateSchemeDeck() {
        Deck deck = new Deck("");
        deck.putSection(DeckSection.Schemes, generateSchemePool());
        return deck;
    }

    public static CardPool generateSchemePool() {
        CardPool schemes = new CardPool();
        List<PaperCard> allSchemes = FModel.getArchenemyCards().toFlatList();

        int schemesToAdd = 20;
        int attemptsLeft = 100; // to avoid endless loop
        while (schemesToAdd > 0 && attemptsLeft > 0) {
            PaperCard cp = Aggregates.random(allSchemes);
            int appearances = schemes.count(cp) + 1;
            if (appearances < 2) {
                schemes.add(cp);
                schemesToAdd--;
            }
            else {
                attemptsLeft--;
            }
        }

        return schemes;
    }

    public static Deck generatePlanarDeck() {
        Deck deck = new Deck("");
        deck.putSection(DeckSection.Planes, generatePlanarPool());
        return deck;
    }

    public static CardPool generatePlanarPool() {
        CardPool res = new CardPool();
        List<PaperCard> allPlanars = FModel.getPlanechaseCards().toFlatList();

        int phenoms = 0;
        int targetsize = MyRandom.getRandom().nextInt(allPlanars.size()-10)+10;
        while (true) {
            PaperCard rndPlane = Aggregates.random(allPlanars);
            allPlanars.remove(rndPlane);

            if (rndPlane.getRules().getType().isPhenomenon() && phenoms < 2) {
                res.add(rndPlane);
                phenoms++;
            }
            else if (rndPlane.getRules().getType().isPlane()) {
                res.add(rndPlane);
            }

            if (allPlanars.isEmpty() || res.countAll() == targetsize) {
                break;
            }
        }

        return res;
    }

    public static Map<ManaCostShard, Integer> suggestBasicLandCount(Deck d) {
        int W=0, U=0, R=0, B=0, G=0, total=0;
        List<PaperCard> cards = d.getOrCreate(DeckSection.Main).toFlatList();
        HashMap<ManaCostShard, Integer> suggestionMap = new HashMap<>();

        // determine how many additional lands we need, but don't take lands already in deck into consideration,
        // or we risk incorrectly determining the target deck size
        int numLands = (int) cards.stream().filter(PaperCardPredicates.IS_LAND).count();
        int sizeNoLands = cards.size() - numLands;

        // attempt to determine if building for sealed, constructed or EDH
        int targetDeckSize = sizeNoLands < 30 ? 40
                : sizeNoLands > 60 ? 100 : 60;

        int numLandsToAdd = targetDeckSize - cards.size();

        if (numLandsToAdd == 0) {
            // already at target deck size, do nothing
            suggestionMap.put(ManaCostShard.WHITE, 0);
            suggestionMap.put(ManaCostShard.BLUE, 0);
            suggestionMap.put(ManaCostShard.RED, 0);
            suggestionMap.put(ManaCostShard.BLACK, 0);
            suggestionMap.put(ManaCostShard.GREEN, 0);
            return suggestionMap;
        }

        for (PaperCard c : d.getMain().toFlatList()) {
            ManaCost m = c.getRules().getManaCost();
            W += m.getShardCount(ManaCostShard.WHITE);
            U += m.getShardCount(ManaCostShard.BLUE);
            R += m.getShardCount(ManaCostShard.RED);
            B += m.getShardCount(ManaCostShard.BLACK);
            G += m.getShardCount(ManaCostShard.GREEN);
        }
        total = W + U + R + B + G;

        int whiteSources = Math.max(0, Math.round(numLandsToAdd * ((float)W / (float)total)));
        if (W > 0) { whiteSources = Math.max(1, whiteSources); }
        numLandsToAdd -= whiteSources;
        total -= W;
        int blueSources = Math.max(0, Math.round(numLandsToAdd * ((float)U / (float)total)));
        if (U > 0) { blueSources = Math.max(1, blueSources); }
        numLandsToAdd -= blueSources;
        total -= U;
        int blackSources = Math.max(0, Math.round(numLandsToAdd * ((float)B / (float)total)));
        if (B > 0) { blackSources = Math.max(1, blackSources); }
        numLandsToAdd -= blackSources;
        total -= B;
        int redSources = Math.max(0, Math.round(numLandsToAdd * ((float)R / (float)total)));
        if (R > 0) { redSources = Math.max(1, redSources); }
        numLandsToAdd -= redSources;
        total -= R;
        int greenSources = Math.max(0, Math.round(numLandsToAdd * ((float)G / (float)total)));
        if (G > 0) { greenSources = Math.max(1, greenSources); }
        numLandsToAdd -= greenSources;
        total -= G;
        
        // in case of a rounding error, add the remaining lands to one of the relevant colors
        if (numLandsToAdd > 0) {
            if (whiteSources > 0) { whiteSources += numLandsToAdd; }
            else if (blueSources > 0) { blueSources += numLandsToAdd; }
            else if (blackSources > 0) { blackSources += numLandsToAdd; }
            else if (redSources > 0) { redSources += numLandsToAdd; }
            else if (greenSources > 0) { greenSources += numLandsToAdd; }
        }

        suggestionMap.put(ManaCostShard.WHITE, whiteSources);
        suggestionMap.put(ManaCostShard.BLUE, blueSources);
        suggestionMap.put(ManaCostShard.RED, redSources);
        suggestionMap.put(ManaCostShard.BLACK, blackSources);
        suggestionMap.put(ManaCostShard.GREEN, greenSources);

        return suggestionMap;
    }
}
