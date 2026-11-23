package forge.baseline.state;

import forge.card.ColorSet;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The pending prompt the engine is currently blocked on, exposed as state:
 * the prompt family, the player being asked, the engine's prompt text, and the
 * engine-computed legal candidates. This mirrors the information the UI would
 * render on the latch-blocking prompt path (the engine computes the candidates,
 * then parks the game thread on a {@code CountDownLatch} until the UI answers)
 * so a consumer can answer with a {@code BaselineAction} without that path.
 *
 * <p>Normalization: candidate lists are empty (never null) when the family does
 * not use them; {@link #getMinCount()} / {@link #getMaxCount()} are 0 when the
 * family has no count constraint; {@link #getColorCandidates()} is null unless
 * the family is a color choice.
 *
 * <p>This class is UI-agnostic: it only references the Swing-free game view model.
 */
public final class BaselinePrompt implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Prompt families, classified by the shape of the answer they expect.
     * Families marked "prompt-channel answer" are answered by the consuming UI
     * on the prompt channel (the {@code IGuiGame} request/response surface)
     * rather than by a {@code BaselineAction}.
     */
    public enum Kind {
        /** Choose one or more cards; answered with {@code SelectCard} (plus {@code Ok}/{@code Cancel} for multi-choose). */
        SELECT_CARD,
        /** Choose an ability; answered with {@code SelectAbility}. */
        SELECT_ABILITY,
        /** Choose a player; answered with {@code SelectPlayer}. */
        SELECT_PLAYER,
        /** Choose a stack item; answered with {@code SelectStackItem}. */
        SELECT_STACK_ITEM,
        /** Yes/no confirmation; answered with {@code Ok}/{@code Cancel}. */
        CONFIRM,
        /** Reorder a list (simultaneous abilities, attackers, blockers, costs); prompt-channel answer. */
        ORDER,
        /** Choose a number (X values, cost reductions); {@code numberCandidates} holds the discrete values, otherwise {@code minCount}/{@code maxCount} bound the range; prompt-channel answer. */
        CHOOSE_NUMBER,
        /** Choose one or more colors; prompt-channel answer. */
        CHOOSE_COLOR,
        /** Choose from string options (types, names, keywords); prompt-channel answer. */
        CHOOSE_OPTION,
        /** Choose an ability mode; mode names in {@code stringCandidates}; prompt-channel answer. */
        CHOOSE_MODE,
        /** Pay a mana cost; answered with {@code UseMana} (plus {@code Ok}/{@code Cancel}). */
        PAY_MANA,
        /** Mulligan / choose starting hand; answered with {@code Ok} (keep) or {@code Cancel} (mulligan). */
        MULLIGAN,
        /** Declare attackers; answered with {@code SelectCard} / {@code AlphaStrike} (plus {@code Ok}). */
        ATTACK,
        /** Declare blockers; answered with {@code SelectCard} (plus {@code Ok}/{@code Cancel}). */
        BLOCK,
        /** Draw-offer vote; answered with {@code DrawOffer}. */
        DRAW_OFFER,
        /** Next-game decision; answered with {@code NextGameDecision}. */
        NEXT_GAME_DECISION
    }

    private final Kind kind;
    private final int playerId;
    private final String message;
    private final List<CardView> cardCandidates;
    private final List<SpellAbilityView> abilityCandidates;
    private final List<PlayerView> playerCandidates;
    private final List<StackItemView> stackItemCandidates;
    private final List<String> stringCandidates;
    private final List<Integer> numberCandidates;
    private final ColorSet colorCandidates;
    private final int minCount;
    private final int maxCount;
    private final boolean optional;

    private BaselinePrompt(Builder b) {
        this.kind = b.kind;
        this.playerId = b.playerId;
        this.message = b.message;
        this.cardCandidates = normalize(b.cardCandidates);
        this.abilityCandidates = normalize(b.abilityCandidates);
        this.playerCandidates = normalize(b.playerCandidates);
        this.stackItemCandidates = normalize(b.stackItemCandidates);
        this.stringCandidates = normalize(b.stringCandidates);
        this.numberCandidates = normalize(b.numberCandidates);
        this.colorCandidates = b.colorCandidates;
        this.minCount = b.minCount;
        this.maxCount = b.maxCount;
        this.optional = b.optional;
    }

    private static <T> List<T> normalize(Iterable<T> candidates) {
        if (candidates == null) {
            return Collections.emptyList();
        }
        List<T> copy = new ArrayList<>();
        candidates.forEach(copy::add);
        return Collections.unmodifiableList(copy);
    }

    public Kind getKind() {
        return kind;
    }

    /** Trackable ID of the {@code PlayerView} being asked. */
    public int getPlayerId() {
        return playerId;
    }

    /** The engine's prompt text, or null when the family has none. */
    public String getMessage() {
        return message;
    }

    public List<CardView> getCardCandidates() {
        return cardCandidates;
    }

    public List<SpellAbilityView> getAbilityCandidates() {
        return abilityCandidates;
    }

    public List<PlayerView> getPlayerCandidates() {
        return playerCandidates;
    }

    public List<StackItemView> getStackItemCandidates() {
        return stackItemCandidates;
    }

    public List<String> getStringCandidates() {
        return stringCandidates;
    }

    public List<Integer> getNumberCandidates() {
        return numberCandidates;
    }

    /** Legal colors for a {@link Kind#CHOOSE_COLOR} prompt, null otherwise. */
    public ColorSet getColorCandidates() {
        return colorCandidates;
    }

    /** Minimum number of choices to make, 0 when the family has no count constraint. */
    public int getMinCount() {
        return minCount;
    }

    /** Maximum number of choices to make, 0 when the family has no count constraint. */
    public int getMaxCount() {
        return maxCount;
    }

    /** Whether the prompt can be answered with "none"/cancel. */
    public boolean isOptional() {
        return optional;
    }

    public static Builder builder(Kind kind, int playerId) {
        return new Builder(kind, playerId);
    }

    public static final class Builder {
        private final Kind kind;
        private final int playerId;
        private String message;
        private Iterable<CardView> cardCandidates;
        private Iterable<SpellAbilityView> abilityCandidates;
        private Iterable<PlayerView> playerCandidates;
        private Iterable<StackItemView> stackItemCandidates;
        private Iterable<String> stringCandidates;
        private Iterable<Integer> numberCandidates;
        private ColorSet colorCandidates;
        private int minCount;
        private int maxCount;
        private boolean optional;

        private Builder(Kind kind, int playerId) {
            this.kind = kind;
            this.playerId = playerId;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder cardCandidates(Iterable<CardView> candidates) {
            this.cardCandidates = candidates;
            return this;
        }

        public Builder abilityCandidates(Iterable<SpellAbilityView> candidates) {
            this.abilityCandidates = candidates;
            return this;
        }

        public Builder playerCandidates(Iterable<PlayerView> candidates) {
            this.playerCandidates = candidates;
            return this;
        }

        public Builder stackItemCandidates(Iterable<StackItemView> candidates) {
            this.stackItemCandidates = candidates;
            return this;
        }

        public Builder stringCandidates(Iterable<String> candidates) {
            this.stringCandidates = candidates;
            return this;
        }

        public Builder numberCandidates(Iterable<Integer> candidates) {
            this.numberCandidates = candidates;
            return this;
        }

        public Builder colorCandidates(ColorSet colors) {
            this.colorCandidates = colors;
            return this;
        }

        public Builder minCount(int minCount) {
            this.minCount = minCount;
            return this;
        }

        public Builder maxCount(int maxCount) {
            this.maxCount = maxCount;
            return this;
        }

        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public BaselinePrompt build() {
            return new BaselinePrompt(this);
        }
    }

    @Override
    public String toString() {
        return "BaselinePrompt[" + kind + ", player=" + playerId
                + ", cards=" + cardCandidates.size()
                + ", abilities=" + abilityCandidates.size()
                + ", players=" + playerCandidates.size()
                + ", stackItems=" + stackItemCandidates.size()
                + ", min=" + minCount + ", max=" + maxCount
                + (optional ? ", optional" : "") + "]";
    }
}
