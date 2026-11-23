package forge.baseline;

import forge.ai.LobbyPlayerAi;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;

import java.util.List;

/**
 * An unstarted two-player AI game, built without the card database (no FModel, no asset
 * directory). Cards are created from inline script text, so each test states exactly the
 * card characteristics it relies on.
 */
public final class TestMatch {

    public final Game game;
    public final Player player1;
    public final Player player2;

    private TestMatch(Game game, Player player1, Player player2) {
        this.game = game;
        this.player1 = player1;
        this.player2 = player2;
    }

    public static TestMatch create() {
        // The engine reads localized strings and keyword descriptions from these singletons.
        Localizer.getInstance().initialize("en-US", langDir());
        Lang.createInstance("en-US");

        List<RegisteredPlayer> players = List.of(
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("P1", null)),
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("P2", null)));
        GameRules rules = new GameRules(GameType.Constructed);
        Game game = new Game(players, rules, new Match(rules, players, "Baseline Test"));
        return new TestMatch(game, game.getPlayers().get(0), game.getPlayers().get(1));
    }

    public Card addCard(Player owner, ZoneType zone, String name) {
        PaperCard paperCard = new PaperCard(
                CardRules.fromScript(List.of("Name:" + name, "ManaCost:1", "Types:Instant")),
                "", CardRarity.Common);
        Card card = CardFactory.getCard(paperCard, owner, game);
        owner.getZone(zone).add(card);
        return card;
    }

    private static String langDir() {
        String override = System.getProperty("forge.baseline.langDir");
        return override != null ? override : "../forge-gui/res/languages/";
    }
}
