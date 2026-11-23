package forge.game.player;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.item.IPaperCard;
import forge.item.PaperCard;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RegisteredPlayer {
    private final Deck originalDeck; // never return or modify this instance (it's a reference to game resources)
    private Deck currentDeck;

    private static final Iterable<PaperCard> EmptyList = Collections.emptyList();

    private LobbyPlayer player = null;

    private int startingLife = 20;
    private int startingHand = 7;
    private int manaShards = 0;
    private Iterable<IPaperCard> extraCardsOnBattlefield = null;
    private Iterable<IPaperCard> extraCardsInCommandZone = null;
    private Iterable<PaperCard> conspiracies = null;
    private Iterable<PaperCard> attractions = null;
    private Iterable<PaperCard> contraptions = null;
    private Iterable<PaperCard> stickerSheets = null;
    private List<PaperCard> commanders = Lists.newArrayList();
    private Integer id = null;
    private boolean randomFoil = false;
    private boolean enableETBCountersEffect = false;
    private final List<List<String>> startGameScripts = Lists.newArrayList();

    public RegisteredPlayer(Deck deck0) {
        originalDeck = deck0;
        restoreDeck();
    }

    public final Integer getId() {
        return id;
    }
    public final void setId(Integer id0) {
        id = id0;
    }

    public final Deck getDeck() {
        return currentDeck;
    }

    public final int getStartingLife() {
        return startingLife;
    }
    public final void setStartingLife(int startingLife) {
        this.startingLife = startingLife;
    }

    public final int getManaShards() {
        return manaShards;
    }
    public final void setManaShards(int manaShards) {
        this.manaShards = manaShards;
    }

    public boolean hasEnableETBCountersEffect() {
        return enableETBCountersEffect;
    }
    public void setEnableETBCountersEffect(boolean value) {
        enableETBCountersEffect = value;
    }

    public final Iterable<? extends IPaperCard> getCardsOnBattlefield() {
        return extraCardsOnBattlefield == null ? EmptyList : extraCardsOnBattlefield;
    }
    public final Iterable<? extends IPaperCard> getExtraCardsInCommandZone() {
        return extraCardsInCommandZone == null ? EmptyList : extraCardsInCommandZone;
    }

    public final void addExtraCardsOnBattlefield(Iterable<IPaperCard> extraCardsonTable) {
        if (this.extraCardsOnBattlefield == null)
            this.extraCardsOnBattlefield = extraCardsonTable;
        else
            this.extraCardsOnBattlefield = Iterables.concat(this.extraCardsOnBattlefield, extraCardsonTable);
    }
    public final void addExtraCardsInCommandZone(Iterable<IPaperCard> extraCardsInCommandZone) {
        if (this.extraCardsInCommandZone == null)
            this.extraCardsInCommandZone = extraCardsInCommandZone;
        else
            this.extraCardsInCommandZone = Iterables.concat(this.extraCardsInCommandZone, extraCardsInCommandZone);
    }
    public final List<List<String>> getStartGameScripts() {
        return startGameScripts;
    }
    /** Card-script lines run after mulligans. A line {@code SVar:Name:Value} sets an SVar the other lines can use, every other line is an ability. */
    public final void addStartGameScript(List<String> script) {
        startGameScripts.add(script);
    }

    public int getStartingHand() {
        return startingHand;
    }
    public void setStartingHand(int startingHand0) {
        this.startingHand = startingHand0;
    }

    public Iterable<PaperCard> getConspiracies() {
        return conspiracies == null ? EmptyList : conspiracies;
    }
    public void assignConspiracies() {
        if (currentDeck.has(DeckSection.Conspiracy)) {
            conspiracies = currentDeck.get(DeckSection.Conspiracy).toFlatList();
        }
    }

    public static RegisteredPlayer forCommander(final Deck deck) {
        RegisteredPlayer start = new RegisteredPlayer(deck);
        start.commanders = deck.getCommanders();
        start.setStartingLife(40);
        return start;
    }

    public static RegisteredPlayer forVariants(final Set<GameType> appliedVariants, final Deck deck) {
    	RegisteredPlayer start = new RegisteredPlayer(deck);
    	if (appliedVariants.contains(GameType.Commander)) {
            start.commanders = deck.getCommanders();
            start.setStartingLife(start.getStartingLife() + 20); // 903.7: ...each player sets his or her life total to 40
		                                                         // Modified for layering of variants to life +20
    	}
    	return start;
    }

    public LobbyPlayer getPlayer() {
        return player;
    }
    public RegisteredPlayer setPlayer(LobbyPlayer player0) {
        this.player = player0;
        return this;
    }

    public List<PaperCard> getCommanders() {
        return commanders;
    }
    public void assignCommander() {
    	commanders = currentDeck.getCommanders();
    }

    public Iterable<PaperCard> getAttractions() {
        return attractions;
    }
    private void assignAttractions() {
        attractions = currentDeck.has(DeckSection.Attractions)
                ? currentDeck.get(DeckSection.Attractions).toFlatList()
                : EmptyList;
    }

    public Iterable<PaperCard> getContraptions() {
        return contraptions;
    }
    private void assignContraptions() {
        contraptions = currentDeck.has(DeckSection.Contraptions)
                ? currentDeck.get(DeckSection.Contraptions).toFlatList()
                : EmptyList;
    }

    public Iterable<PaperCard> getStickerSheets() {
        return stickerSheets;
    }
    private void assignStickerSheets() {
        stickerSheets = currentDeck.has(DeckSection.Stickers)
                ? currentDeck.get(DeckSection.Stickers).toFlatList()
                : EmptyList;
    }

    public void restoreDeck() {
        currentDeck = (Deck) originalDeck.copyTo(originalDeck.getName());
        assignAttractions();
        assignContraptions();
        assignStickerSheets();
    }

    public boolean useRandomFoil() {
        return randomFoil;
    }
    public void setRandomFoil(boolean useRandomFoil) {
        randomFoil = useRandomFoil;
    }
}
