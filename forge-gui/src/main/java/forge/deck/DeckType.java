package forge.deck;

import forge.util.Localizer;

public enum DeckType {
    CUSTOM_DECK("lblCustomUserDecks"),
    CONSTRUCTED_DECK("lblConstructedDecks"),
    COMMANDER_DECK("lblCommanderDecks"),
    SCHEME_DECK("lblSchemeDecks"),
    PLANAR_DECK("lblPlanarDecks"),
    DRAFT_DECK("lblDraftDecks"),
    SEALED_DECK("lblSealedDecks"),
    PRECONSTRUCTED_DECK("lblPreconstructedDecks"),
    PRECON_COMMANDER_DECK("lblPreconCommanderDecks"),
    QUEST_OPPONENT_DECK("lblQuestOpponentDecks"),
    COLOR_DECK("lblRandomColorDecks"),
    STANDARD_CARDGEN_DECK("lblRandomStandardArchetypeDecks"),
    PIONEER_CARDGEN_DECK("lblRandomPioneerArchetypeDecks"),
    HISTORIC_CARDGEN_DECK("lblRandomHistoricArchetypeDecks"),
    MODERN_CARDGEN_DECK("lblRandomModernArchetypeDecks"),
    LEGACY_CARDGEN_DECK("lblRandomLegacyArchetypeDecks"),
    VINTAGE_CARDGEN_DECK("lblRandomVintageArchetypeDecks"),
    PAUPER_CARDGEN_DECK("lblRandomPauperArchetypeDecks"),
    STANDARD_COLOR_DECK("lblRandomStandardColorDecks"),
    MODERN_COLOR_DECK("lblRandomModernColorDecks"),
    PAUPER_COLOR_DECK("lblRandomPauperColorDecks"),
    THEME_DECK("lblRandomThemeDecks"),
    NET_DECK("lblNetDecks"),
    NET_ARCHIVE_STANDARD_DECK("lblNetArchiveStandardDecks"),
    NET_ARCHIVE_PIONEER_DECK("lblNetArchivePioneerDecks"),
    NET_ARCHIVE_MODERN_DECK("lblNetArchiveModernDecks"),
    NET_ARCHIVE_PAUPER_DECK("lblNetArchivePauperDecks"),
    NET_ARCHIVE_LEGACY_DECK("lblNetArchiveLegacyDecks"),
    NET_ARCHIVE_VINTAGE_DECK("lblNetArchiveVintageDecks"),
    NET_ARCHIVE_BLOCK_DECK("lblNetArchiveBlockDecks"),
    NET_EVENT_DECK("lblNetEventDecks"),
    PROVIDED_DECK_URL("lblProvideDeckUrl");

    public static DeckType[] ConstructedOptions;
    public static DeckType[] CommanderOptions;

    static {
        ConstructedOptions = new DeckType[]{
                DeckType.CUSTOM_DECK,
                DeckType.PRECONSTRUCTED_DECK,
                DeckType.QUEST_OPPONENT_DECK,
                DeckType.COLOR_DECK,
                DeckType.STANDARD_COLOR_DECK,
                DeckType.MODERN_COLOR_DECK,
                DeckType.PAUPER_COLOR_DECK,
                DeckType.THEME_DECK,
                DeckType.NET_DECK,
                DeckType.NET_EVENT_DECK,
                DeckType.NET_ARCHIVE_STANDARD_DECK,
                DeckType.NET_ARCHIVE_PIONEER_DECK,
                DeckType.NET_ARCHIVE_MODERN_DECK,
                DeckType.NET_ARCHIVE_PAUPER_DECK,
                DeckType.NET_ARCHIVE_LEGACY_DECK,
                DeckType.NET_ARCHIVE_VINTAGE_DECK,
                DeckType.NET_ARCHIVE_BLOCK_DECK,
                DeckType.PROVIDED_DECK_URL
        };
    }
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
