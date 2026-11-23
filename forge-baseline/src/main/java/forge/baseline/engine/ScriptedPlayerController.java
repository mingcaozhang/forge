package forge.baseline.engine;

import forge.LobbyPlayer;
import forge.ai.PlayerControllerAi;
import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselinePrompt;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Non-interactive controller for the scripted player. Every decision except
 * the pass-priority prompt is inherited from {@link PlayerControllerAi}
 * (deterministic given the seeded {@code MyRandom}); the pass-priority prompt
 * is the adapter's prompt boundary: before the game thread blocks, the engine
 * emits a snapshot carrying a {@link BaselinePrompt} with the legal
 * candidates, and the next {@link BaselineAction} fed to the engine answers it.
 *
 * <p>The pending prompt is a {@link BaselinePrompt.Kind#SELECT_ABILITY}
 * decision for the scripted player:
 * <ul>
 *   <li>{@code abilityCandidates} — every currently playable
 *       {@link SpellAbility} of the scripted player, in canonical order
 *       (zone, then card id, then ability id);</li>
 *   <li>{@code cardCandidates} — the distinct host cards of those abilities;
 *   <li>{@code optional} — the prompt can be answered "none" (pass priority).
 * </ul>
 *
 * <p>Accepted answers (anything else is rejected and treated as a pass):
 * <ul>
 *   <li>{@link BaselineAction.Concede} — the player concedes, ending the game.</li>
 *   <li>{@link BaselineAction.SelectAbility} — plays the candidate ability
 *       with the matching ID.</li>
 *   <li>{@link BaselineAction.SelectCard} — plays the selected card's chosen
 *       ability (a land drop or a spell cast), resolved the same way the human
 *       flow resolves it ({@link #getAbilityToPlay} + the inherited
 *       {@code playChosenSpellAbility}).</li>
 *   <li>{@link BaselineAction.Ok} / {@link BaselineAction.Cancel} — passes
 *       priority.</li>
 * </ul>
 *
 * <p>Other prompt families (targeting, mulligans, colors, numbers, modes,
 * ordering) are answered non-interactively by the inherited
 * {@link PlayerControllerAi} logic, per the contract's prompt-channel rule —
 * they never block on a fed action and never reach the CMatchUI latch path.
 */
public class ScriptedPlayerController extends PlayerControllerAi {

    /** The zones whose cards can be played at a pass-priority decision. */
    private static final List<ZoneType> PLAYABLE_ZONES = List.of(
            ZoneType.Hand, ZoneType.Battlefield, ZoneType.Command, ZoneType.Graveyard, ZoneType.Exile);

    private final BaselineEngine engine;

    public ScriptedPlayerController(Game game, Player player, LobbyPlayer lobbyPlayer, BaselineEngine engine) {
        super(game, player, lobbyPlayer);
        this.engine = engine;
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        List<SpellAbility> candidates = collectPlayableAbilities();
        engine.emitPromptSnapshot(buildPassPriorityPrompt(candidates));
        BaselineAction action = engine.takeAction();
        return answerPassPriority(action, candidates);
    }

    /**
     * The legal candidates of the pass-priority prompt: every currently
     * playable ability of the scripted player, in canonical order (zone, then
     * card id, then ability id) so the prompt state is deterministic for a
     * fixed game state.
     */
    private List<SpellAbility> collectPlayableAbilities() {
        List<SpellAbility> candidates = new ArrayList<>();
        for (ZoneType zone : PLAYABLE_ZONES) {
            List<Card> cards = new ArrayList<>(getPlayer().getCardsIn(zone));
            cards.sort(Comparator.comparingInt(Card::getId));
            for (Card card : cards) {
                List<SpellAbility> abilities = card.getAllPossibleAbilities(getPlayer(), true);
                abilities.sort(Comparator.comparingInt(SpellAbility::getId));
                candidates.addAll(abilities);
            }
        }
        return candidates;
    }

    private BaselinePrompt buildPassPriorityPrompt(List<SpellAbility> candidates) {
        List<SpellAbilityView> abilityCandidates = new ArrayList<>(candidates.size());
        TreeMap<Integer, CardView> cardCandidates = new TreeMap<>();
        for (SpellAbility sa : candidates) {
            abilityCandidates.add(SpellAbilityView.get(sa));
            Card host = sa.getHostCard();
            if (host != null) {
                cardCandidates.putIfAbsent(host.getId(), host.getView());
            }
        }
        return BaselinePrompt.builder(BaselinePrompt.Kind.SELECT_ABILITY, getPlayer().getView().getId())
                .abilityCandidates(abilityCandidates)
                .cardCandidates(cardCandidates.values())
                .optional(true)
                .build();
    }

    private List<SpellAbility> answerPassPriority(BaselineAction action, List<SpellAbility> candidates) {
        if (action instanceof BaselineAction.Concede) {
            getPlayer().concede();
            return null;
        }
        if (action instanceof BaselineAction.SelectAbility select) {
            for (SpellAbility sa : candidates) {
                if (sa.getId() == select.ability().getId()) {
                    return List.of(sa);
                }
            }
            return null; // not a legal candidate: rejected, pass priority
        }
        if (action instanceof BaselineAction.SelectCard select) {
            Card card = getGame().findByView(select.card());
            if (card == null) {
                return null; // unknown card: rejected, pass priority
            }
            List<SpellAbility> abilities = card.getAllPossibleAbilities(getPlayer(), true);
            SpellAbility sa = getAbilityToPlay(card, abilities, null);
            return sa == null ? null : List.of(sa); // no playable ability: rejected, pass
        }
        return null; // Ok / Cancel / anything else: pass priority
    }
}
