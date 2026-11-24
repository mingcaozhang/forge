package forge.baseline.golden;

import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;

import java.util.List;

/**
 * Encodes a {@link BaselineAction} to the compact text token stored in a golden
 * trace, and decodes a token back into a live action for the replay engine.
 *
 * <p>Actions reference live view objects ({@link CardView},
 * {@link SpellAbilityView}), which cannot be serialized directly; the token
 * therefore carries only the object's stable trackable id. On decode the id is
 * resolved against the replay engine's current prompt candidates (and, for
 * cards, the player zones), so the reconstituted action points at the live view
 * of the replay run.
 */
public final class GoldenActionCodec {

    private GoldenActionCodec() {
    }

    /** Encode an action to its golden-trace token. */
    public static String encode(BaselineAction action) {
        if (action instanceof BaselineAction.SelectCard select) {
            return "CARD:" + select.card().getId();
        }
        if (action instanceof BaselineAction.SelectAbility select) {
            return "ABILITY:" + select.ability().getId();
        }
        if (action instanceof BaselineAction.SelectPlayer select) {
            return "PLAYER:" + select.player().getId();
        }
        if (action instanceof BaselineAction.SelectStackItem select) {
            return "STACK:" + select.item().getId();
        }
        if (action instanceof BaselineAction.UseMana use) {
            return "MANA:" + use.color();
        }
        if (action instanceof BaselineAction.Ok) {
            return "OK";
        }
        if (action instanceof BaselineAction.Cancel) {
            return "CANCEL";
        }
        if (action instanceof BaselineAction.Concede) {
            return "CONCEDE";
        }
        throw new UnsupportedOperationException("action not supported by golden traces: " + action);
    }

    /**
     * Decode a golden-trace token into a live action for the given snapshot.
     * Card and ability ids are resolved against the snapshot's prompt candidates
     * (cards also fall back to the first player's zones).
     */
    public static BaselineAction decode(String token, BaselineSnapshot snapshot) {
        if ("OK".equals(token)) {
            return new BaselineAction.Ok();
        }
        if ("CANCEL".equals(token)) {
            return new BaselineAction.Cancel();
        }
        if ("CONCEDE".equals(token)) {
            return new BaselineAction.Concede();
        }
        if (token.startsWith("CARD:")) {
            int id = Integer.parseInt(token.substring("CARD:".length()));
            CardView card = findCard(snapshot, id);
            if (card == null) {
                throw new IllegalStateException("golden trace references unknown card id " + id);
            }
            return new BaselineAction.SelectCard(card, null);
        }
        if (token.startsWith("ABILITY:")) {
            int id = Integer.parseInt(token.substring("ABILITY:".length()));
            BaselinePrompt prompt = snapshot.getPrompt();
            if (prompt != null) {
                for (SpellAbilityView ability : prompt.getAbilityCandidates()) {
                    if (ability.getId() == id) {
                        return new BaselineAction.SelectAbility(ability);
                    }
                }
            }
            throw new IllegalStateException("golden trace references unknown ability id " + id);
        }
        if (token.startsWith("MANA:")) {
            return new BaselineAction.UseMana(Byte.parseByte(token.substring("MANA:".length())));
        }
        throw new UnsupportedOperationException("unknown golden-trace action token: " + token);
    }

    private static CardView findCard(BaselineSnapshot snapshot, int id) {
        BaselinePrompt prompt = snapshot.getPrompt();
        if (prompt != null) {
            for (CardView card : prompt.getCardCandidates()) {
                if (card.getId() == id) {
                    return card;
                }
            }
        }
        FCollectionView<PlayerView> players = snapshot.getGameView().getPlayers();
        if (!players.isEmpty()) {
            PlayerView player = players.get(0);
            for (ZoneType zone : List.of(
                    ZoneType.Hand, ZoneType.Battlefield, ZoneType.Command, ZoneType.Graveyard, ZoneType.Exile)) {
                for (CardView card : player.getCards(zone)) {
                    if (card.getId() == id) {
                        return card;
                    }
                }
            }
        }
        return null;
    }
}
