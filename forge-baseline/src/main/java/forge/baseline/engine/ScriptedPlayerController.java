package forge.baseline.engine;

import forge.LobbyPlayer;
import forge.ai.PlayerControllerAi;
import forge.baseline.action.BaselineAction;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.List;

/**
 * Non-interactive controller for the scripted player. Every decision except
 * the pass-priority prompt is inherited from {@link PlayerControllerAi}
 * (deterministic given the seeded {@code MyRandom}); the pass-priority prompt
 * is answered with the next {@link BaselineAction} fed to the engine.
 *
 * <p>Supported action payloads:
 * <ul>
 *   <li>{@link BaselineAction.Concede} — the player concedes, ending the game.</li>
 *   <li>{@link BaselineAction.SelectCard} — plays the selected card's chosen
 *       ability (a land drop or a spell cast), resolved the same way the human
 *       flow resolves it ({@link #getAbilityToPlay} + the inherited
 *       {@code playChosenSpellAbility}).</li>
 *   <li>{@link BaselineAction.SelectAbility} — plays the ability with the
 *       matching ID, if currently playable.</li>
 *   <li>anything else ({@link BaselineAction.Ok}, {@link BaselineAction.Cancel},
 *       ...) — passes priority.</li>
 * </ul>
 */
public class ScriptedPlayerController extends PlayerControllerAi {

    private final BaselineEngine engine;

    public ScriptedPlayerController(Game game, Player player, LobbyPlayer lobbyPlayer, BaselineEngine engine) {
        super(game, player, lobbyPlayer);
        this.engine = engine;
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        engine.flushBeforeBlock();
        BaselineAction action = engine.takeAction();

        if (action instanceof BaselineAction.Concede) {
            getPlayer().concede();
            return null;
        }
        if (action instanceof BaselineAction.SelectCard select) {
            Card card = getGame().findByView(select.card());
            if (card == null) {
                return null;
            }
            List<SpellAbility> abilities = card.getAllPossibleAbilities(getPlayer(), true);
            SpellAbility sa = getAbilityToPlay(card, abilities, null);
            return sa == null ? null : List.of(sa);
        }
        if (action instanceof BaselineAction.SelectAbility select) {
            SpellAbility sa = findAbilityById(select.ability().getId());
            return sa == null ? null : List.of(sa);
        }
        return null; // Ok / Cancel / anything else: pass priority
    }

    /** Finds the currently-playable ability with the given ID among this player's zones. */
    private SpellAbility findAbilityById(int id) {
        for (ZoneType zone : List.of(ZoneType.Hand, ZoneType.Battlefield, ZoneType.Command,
                ZoneType.Graveyard, ZoneType.Exile)) {
            for (Card card : getPlayer().getCardsIn(zone)) {
                for (SpellAbility sa : card.getAllPossibleAbilities(getPlayer(), true)) {
                    if (sa.getId() == id) {
                        return sa;
                    }
                }
            }
        }
        return null;
    }
}
