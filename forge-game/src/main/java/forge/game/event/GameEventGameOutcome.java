package forge.game.event;

import java.util.List;

import forge.game.GameOutcome;
import forge.LobbyPlayer;

public record GameEventGameOutcome(int lastTurnNumber, List<String> outcomeStrings, String winningPlayerName) implements GameEvent {

    public GameEventGameOutcome(GameOutcome result) {
        this(result.getLastTurnNumber(),
             result.getOutcomeStrings(),
             computeWinningPlayerName(result));
    }

    private static String computeWinningPlayerName(GameOutcome result) {
        LobbyPlayer winner = result.getWinningLobbyPlayer();
        return winner != null ? winner.getName() : null;
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Game Outcome: " + outcomeStrings;
    }
}
