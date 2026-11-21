package forge.ai.simulation;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

public class GameStateEvaluatorTest extends SimulationTest {
    private final GameStateEvaluator evaluator = new GameStateEvaluator();

    @Test
    public void testPhasingSeparatesStrategicAndAvailableValue() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card mountain = addCard("Mountain", ai);
        addCardToZone("Shivan Dragon", ai, ZoneType.Hand);
        moveToMain2(game, ai);

        GameStateEvaluator.Score score = evaluator.getScoreForGameState(game, ai);
        mountain.phase(false);
        GameStateEvaluator.Score phasedScore = evaluator.getScoreForGameState(game, ai);

        AssertJUnit.assertEquals("Normal phasing should retain strategic value",
                score.value, phasedScore.value);
        AssertJUnit.assertTrue("A phased-out permanent should not be currently available",
                phasedScore.availableValue < score.availableValue);

        mountain.setWontPhaseInNormal(true);
        GameStateEvaluator.Score contingentScore =
                evaluator.getScoreForGameState(game, ai);
        AssertJUnit.assertTrue("A contingent return should not retain strategic value",
                contingentScore.value < score.value);
    }

    @Test
    public void testLostPlayerGetsTerminalScoreInMultiplayerGame() {
        Game game = initAndCreateThreePlayerGame();
        Player ai = game.getPlayers().get(1);
        moveToMain2(game, ai);
        ai.concede();

        AssertJUnit.assertFalse("The multiplayer game should continue after one player loses",
                game.isGameOver());
        AssertJUnit.assertTrue("A real loss should not use the failed-simulation sentinel",
                evaluate(game, ai) > Integer.MIN_VALUE);
    }

    @Test
    public void testFinishedWinnerGetsTerminalScoreInMultiplayerGame() {
        Game game = initAndCreateThreePlayerGame();
        Player ai = game.getPlayers().get(1);
        moveToMain2(game, ai);
        ai.onGameOver();

        AssertJUnit.assertFalse("The multiplayer game should still be open",
                game.isGameOver());
        AssertJUnit.assertFalse("The winner should have a final player outcome",
                ai.isInGame());
        AssertJUnit.assertTrue("A finished winner should receive a terminal win score",
                GameStateEvaluator.isWinning(evaluate(game, ai)));
    }

    private int evaluate(Game game, Player ai) {
        return evaluator.getScoreForGameState(game, ai).value;
    }
}
