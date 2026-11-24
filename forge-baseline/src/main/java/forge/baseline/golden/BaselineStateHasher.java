package forge.baseline.golden;

import forge.baseline.state.BaselineSnapshot;
import forge.card.mana.ManaAtom;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes a deterministic fingerprint of a baseline game state for the Phase 30
 * golden traces. The canonical string captures the turn/phase, the active
 * player, and — for each player in stable order — life, lands played this turn,
 * the mana pool, and the card ids in the game zones (sorted by id so the
 * fingerprint is independent of collection iteration order). The magic stack is
 * captured in order by ability key plus source-card id. The fingerprint is the
 * SHA-256 of the canonical string, so a fixed game state always yields the same
 * 64-character hex digest, and any divergence in the replayed game surfaces as
 * a hash mismatch at the first step it occurs.
 *
 * <p>This class is UI-agnostic: it only reads the Swing-free game view model.
 */
public final class BaselineStateHasher {

    private BaselineStateHasher() {
    }

    /** The 64-char lowercase hex SHA-256 of {@link #canonicalString(BaselineSnapshot)}. */
    public static String hash(BaselineSnapshot snapshot) {
        return sha256(canonicalString(snapshot));
    }

    /**
     * The order-independent canonical description of the state. Exposed
     * separately from {@link #hash} so a test can assert on the exact diverging
     * content when a hash mismatch occurs.
     */
    public static String canonicalString(BaselineSnapshot snapshot) {
        GameView game = snapshot.getGameView();
        StringBuilder sb = new StringBuilder(2048);
        sb.append('T').append(game.getTurn())
                .append("|P").append(game.getPhase())
                .append("|A").append(game.getPlayerTurn() == null ? -1 : game.getPlayerTurn().getId())
                .append("|M").append(game.isMulligan() ? 1 : 0)
                .append("|G").append(game.isGameOver() ? 1 : 0);

        for (PlayerView player : game.getPlayers()) {
            appendPlayer(sb, player);
        }

        sb.append("|S");
        for (StackItemView item : game.getStack()) {
            sb.append(item.getKey())
                    .append(':')
                    .append(item.getSourceCard() == null ? -1 : item.getSourceCard().getId())
                    .append(',');
        }
        return sb.toString();
    }

    private static void appendPlayer(StringBuilder sb, PlayerView player) {
        sb.append("|L").append(player.getId())
                .append("#life").append(player.getLife())
                .append("#land").append(player.getNumLandThisTurn());
        for (byte color : ManaAtom.MANATYPES) {
            sb.append("#m").append(color).append(player.getMana(color));
        }
        appendZone(sb, player, 'H', ZoneType.Hand, false);
        appendZone(sb, player, 'B', ZoneType.Battlefield, true);
        appendZone(sb, player, 'D', ZoneType.Library, false);
        appendZone(sb, player, 'Y', ZoneType.Graveyard, false);
        appendZone(sb, player, 'X', ZoneType.Exile, false);
        appendZone(sb, player, 'C', ZoneType.Command, false);
    }

    private static void appendZone(StringBuilder sb, PlayerView player, char tag, ZoneType zone, boolean includeTapped) {
        List<CardView> cards = new ArrayList<>(player.getCards(zone));
        cards.sort(Comparator.comparingInt(CardView::getId));
        sb.append('#').append(tag);
        for (CardView card : cards) {
            sb.append(card.getId());
            if (includeTapped) {
                sb.append(card.isTapped() ? 't' : 'u');
            }
            sb.append(',');
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", e);
        }
    }
}
