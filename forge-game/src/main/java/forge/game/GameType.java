package forge.game;

import java.util.EnumSet;
import java.util.Set;

import com.google.common.base.Enums;

import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.util.Localizer;

public enum GameType {

    Constructed         (DeckFormat.Constructed, false, true, true, "lblConstructed", ""),
    Commander           (DeckFormat.Commander, false, false, false, "lblCommander", "lblCommanderDesc");

    private final DeckFormat deckFormat;
    private final boolean isCardPoolLimited, canSideboard, addWonCardsMidGame;
    private final String name, englishName, description;

    GameType(DeckFormat deckFormat0, boolean isCardPoolLimited0, boolean canSideboard0, boolean addWonCardsMidgame0, String name0, String description0) {
        final Localizer localizer = forge.util.Localizer.getInstance();
        deckFormat = deckFormat0;
        isCardPoolLimited = isCardPoolLimited0;
        canSideboard = canSideboard0;
        addWonCardsMidGame = addWonCardsMidgame0;
        name = localizer.getMessage(name0);
        englishName = localizer.getEnglishMessage(name0);
        if (!description0.isEmpty()) {
            description0 = localizer.getMessage(description0);
        }
        description = description0;
    }

    /**
     * @return the decksFormat
     */
    public DeckFormat getDeckFormat() {
        return deckFormat;
    }

    /**
     * @return the isCardpoolLimited
     */
    public boolean isCardPoolLimited() {
        return isCardPoolLimited;
    }

    public boolean canAddWonCardsMidGame() {
        return addWonCardsMidGame;
    }

    public String toString() {
        return name;
    }
    public String getEnglishName() {
        return englishName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @return the deck sections used by most decks in this game type.
     */
    public EnumSet<DeckSection> getPrimaryDeckSections() {
        return deckFormat.getPrimaryDeckSections();
    }

    public static GameType smartValueOf(String name) {
        return Enums.getIfPresent(GameType.class, name).orNull();
    }

    public static Set<GameType> listValueOf(final String values) {
        final Set<GameType> result = EnumSet.noneOf(GameType.class);
        for (final String s : values.split(",")) {
            GameType g = GameType.smartValueOf(s);
            if (g != null) {
                result.add(g);
            }
        }
        return result;
    }
}
