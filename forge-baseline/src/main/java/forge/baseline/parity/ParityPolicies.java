package forge.baseline.parity;

import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselineSnapshot;
import forge.baseline.trajectory.ActionPolicy;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;

import java.util.List;

/**
 * The named built-in deterministic policies the parity runner accepts via
 * {@code --policy}. A policy maps the pending prompt snapshot (state plus the
 * engine-computed legal candidates) to the {@link BaselineAction} fed to answer
 * it, and must be a pure function of the snapshot — the Rust parity runner
 * implements the same names with the same decisions, so both sides produce the
 * same report for the same input.
 *
 * <p>Known policies:
 * <ul>
 *   <li>{@code fixed} — the Phase 30 golden-trace policy: play a land from hand
 *       when one is a legal candidate, otherwise cast the commander from the
 *       command zone once three lands are untapped, otherwise pass priority;
 *       concede once the game reaches player turn {@link #FIXED_CONCEDE_TURN}.
 *       This is the policy that produced the committed golden traces.</li>
 *   <li>{@code pass} — always pass priority ({@code Ok}); the match ends by a
 *       natural condition (typically an empty library).</li>
 * </ul>
 */
public final class ParityPolicies {

    /** The player turn at which {@code fixed} concedes (bounds the game). */
    public static final int FIXED_CONCEDE_TURN = 7;

    private ParityPolicies() {
    }

    /** Looks up a policy by name; unknown names fail fast with the known set. */
    public static ActionPolicy byName(String name) {
        switch (name) {
            case "fixed":
                return ParityPolicies::fixed;
            case "pass":
                return snapshot -> new BaselineAction.Ok();
            default:
                throw new ParityException("unknown policy '" + name + "' (known: fixed, pass)");
        }
    }

    /** The {@code fixed} policy; see the class javadoc. */
    public static BaselineAction fixed(BaselineSnapshot snapshot) {
        GameView game = snapshot.getGameView();
        if (game.getTurn() >= FIXED_CONCEDE_TURN) {
            return new BaselineAction.Concede();
        }
        PlayerView me = game.getPlayers().get(0);
        for (SpellAbilityView ability : snapshot.getPrompt().getAbilityCandidates()) {
            CardView host = ability.getHostCard();
            if (host != null && host.getCurrentState().isLand() && me.getHand().contains(host)) {
                return new BaselineAction.SelectCard(host, null);
            }
        }
        List<CardView> commanders = me.getCommanders();
        if (!commanders.isEmpty() && commanders.get(0).getZone() == ZoneType.Command) {
            int untapped = 0;
            for (CardView card : me.getBattlefield()) {
                if (card.getCurrentState().isLand() && !card.isTapped()) {
                    untapped++;
                }
            }
            if (untapped >= 3) {
                return new BaselineAction.SelectCard(commanders.get(0), null);
            }
        }
        return new BaselineAction.Ok();
    }
}
