package forge.deck;

import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.item.PaperCard;
import forge.item.PaperCardPredicates;
import forge.itemmanager.IItemManager;
import forge.util.MyRandom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utility collection for deck-related helpers (random selection, basic land suggestions). */
public class DeckgenUtil {

    public static void randomSelect(final IItemManager<DeckProxy> deckManager) {
        final int size = deckManager.getItemCount();
        if (size == 0) { return; }

        deckManager.setSelectedIndex(MyRandom.getRandom().nextInt(size));
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
