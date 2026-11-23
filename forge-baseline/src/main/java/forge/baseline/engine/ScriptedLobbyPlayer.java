package forge.baseline.engine;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;

/**
 * A {@link LobbyPlayer} whose in-game player is driven by a
 * {@link ScriptedPlayerController} that answers with the actions fed to the
 * {@link BaselineEngine}. The engine binds itself to the scripted lobby players
 * it is constructed with when {@link BaselineEngine#start()} runs.
 */
public class ScriptedLobbyPlayer extends LobbyPlayer implements IGameEntitiesFactory {

    private BaselineEngine engine;

    public ScriptedLobbyPlayer(String name) {
        super(name);
    }

    void bind(BaselineEngine engine) {
        this.engine = engine;
    }

    @Override
    public Player createIngamePlayer(Game game, int id) {
        Player player = new Player(getName(), game, id);
        player.setFirstController(new ScriptedPlayerController(game, player, this, engine));
        return player;
    }

    @Override
    public PlayerController createMindSlaveController(Player master, Player slave) {
        return new ScriptedPlayerController(master.getGame(), slave, this, engine);
    }

    @Override
    public void hear(LobbyPlayer player, String message) {
        // scripted players do not chat
    }
}
