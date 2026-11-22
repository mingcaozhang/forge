package forge.deck;

import com.google.common.collect.Iterables;
import forge.game.GameFormat;
import forge.game.GameType;
import forge.game.IHasGameType;
import forge.localinstance.properties.ForgePreferences;
import forge.model.FModel;
import forge.util.Aggregates;
import forge.util.IterableUtil;

import java.util.ArrayList;
import java.util.List;

public class RandomDeckGenerator extends DeckProxy {
    private final IHasGameType lstDecks;
    private final boolean isAi;

    private RandomDeckGenerator(final IHasGameType lstDecks0, final boolean isAi0) {
        super();
        lstDecks = lstDecks0;
        isAi = isAi0;
    }

    public static Deck getRandomUserDeck(final IHasGameType lstDecks0, final boolean isAi0) {
        return new RandomDeckGenerator(lstDecks0, isAi0).getDeck();
    }

    @Override
    public String getName() {
        return "Random User Deck";
    }

    @Override
    public Deck getDeck() {
        return getUserDeck();
    }

    private Deck getGeneratedDeck() {
        switch (lstDecks.getGameType()) {
            case CommanderGauntlet:
            case Commander:
                return DeckgenUtil.generateCommanderDeck(isAi, GameType.Commander);
            case Archenemy:
                return DeckgenUtil.generateSchemeDeck();
            case Planechase:
                return DeckgenUtil.generatePlanarDeck();
            default:
                while (true) {
                    switch (Aggregates.random(DeckType.ConstructedOptions)) {
                        case PRECONSTRUCTED_DECK:
                            return Aggregates.random(DeckProxy.getAllPreconstructedDecks(DeckgenUtil.getPrecons())).getDeck();
                        case COLOR_DECK:
                            List<String> colors = new ArrayList<>();
                            int count = Aggregates.randomInt(1, 3);
                            for (int i = 1; i <= count; i++) {
                                colors.add("Random " + i);
                            }
                            return DeckgenUtil.buildColorDeck(colors, null, isAi);
                        case STANDARD_CARDGEN_DECK:
                            return DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().getStandard(), isAi);
                        case PIONEER_CARDGEN_DECK:
                            return DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().getPioneer(), isAi);
                        case HISTORIC_CARDGEN_DECK:
                            return DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().getHistoric(), isAi);
                        case MODERN_CARDGEN_DECK:
                            return DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().getModern(), isAi);
                        case LEGACY_CARDGEN_DECK:
                            return DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().get("Legacy"), isAi);
                        case VINTAGE_CARDGEN_DECK:
                            return DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().get("Vintage"), isAi);
                        case PAUPER_CARDGEN_DECK:
                            return DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().getPauper(), isAi);
                        case STANDARD_COLOR_DECK:
                            return generateRandomColorDeckOfFormat(FModel.getFormats().getStandard());
                        case MODERN_COLOR_DECK:
                            return generateRandomColorDeckOfFormat(FModel.getFormats().getModern());
                        case PAUPER_COLOR_DECK:
                            return generateRandomColorDeckOfFormat(FModel.getFormats().getPauper());
                        case THEME_DECK:
                            return Aggregates.random(DeckProxy.getAllThemeDecks()).getDeck();
                        default:
                            continue;
                    }
                }
        }
    }

    private Deck generateRandomColorDeckOfFormat(GameFormat format) {
        int count;
        List<String> colors;
        colors = new ArrayList<>();
        count = Aggregates.randomInt(1, 3);
        for (int i = 1; i <= count; i++) {
            colors.add("Random " + i);
        }
        return DeckgenUtil.buildColorDeck(colors, format.getFilterPrinted(), isAi);
    }

    private Deck getUserDeck() {
        Iterable<DeckProxy> decks;
        final GameType gameType = lstDecks.getGameType();
        decks = switch (gameType) {
            case CommanderGauntlet, Commander -> DeckProxy.getAllCommanderDecks(DeckFormat.Commander.isLegalDeckPredicate());
            case Archenemy -> DeckProxy.getAllSchemeDecks(DeckFormat.Archenemy.isLegalDeckPredicate());
            case Planechase -> DeckProxy.getAllPlanarDecks(DeckFormat.Planechase.isLegalDeckPredicate());
            default -> DeckProxy.getAllConstructedDecks(gameType.getDeckFormat().isLegalDeckPredicate());
        };
        if (Iterables.isEmpty(decks)) {
            return getGeneratedDeck(); //fall back to generated deck if no decks in filtered list
        }
        if (isAi && FModel.getPreferences().getPrefBoolean(ForgePreferences.FPref.UI_AUTO_AIDECK_SELECTION)) {
            Iterable<DeckProxy> AIDecks = IterableUtil.filter(decks, deckProxy -> deckProxy.getAI().inMainDeck == 0);
            if (Iterables.size(AIDecks) > 10)
                return Aggregates.random(AIDecks).getDeck();
        }
        return Aggregates.random(decks).getDeck();
    }

    @Override
    public boolean isGeneratedDeck() {
        return true;
    }
}
