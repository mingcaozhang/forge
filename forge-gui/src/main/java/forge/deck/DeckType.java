package forge.deck;

import forge.util.Localizer;

public enum DeckType {
    CUSTOM_DECK("lblCustomUserDecks"),
    CONSTRUCTED_DECK("lblConstructedDecks"),
    COMMANDER_DECK("lblCommanderDecks"),
    PRECON_COMMANDER_DECK("lblPreconCommanderDecks"),
    PROVIDED_DECK_URL("lblProvideDeckUrl");

    public static DeckType[] CommanderOptions;

    static {
        CommanderOptions = new DeckType[]{
                DeckType.COMMANDER_DECK,
                DeckType.PRECON_COMMANDER_DECK,
                DeckType.PROVIDED_DECK_URL
        };
    }

    private String value;
    DeckType(final String value) {
        final Localizer localizer = Localizer.getInstance();
        this.value = localizer.getMessage(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
