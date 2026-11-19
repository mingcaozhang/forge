package forge.screens.home.tiles;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;

import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.util.Localizer;

/**
 * Dashboard tile that summarises the most recent finished match.
 *
 * <p>The tile renders whatever {@link Summary} is handed to it and shows a
 * placeholder when no match has been recorded yet, so it can be constructed and
 * tested before any match-history persistence exists.</p>
 */
@SuppressWarnings("serial")
public class LastMatchTile extends HomeTile {
    /** Outcome of the referenced match. */
    public enum Result {
        /** The local player won. */
        WIN,
        /** The local player lost. */
        LOSS,
        /** The match ended without a winner. */
        DRAW,
        /** The match did not reach a conclusion. */
        INCOMPLETE
    }

    /**
     * Immutable description of a single finished match.
     */
    public static final class Summary {
        private final String deckName;
        private final String opponentName;
        private final Result result;
        private final int turns;
        private final String detail;

        private Summary(final Builder builder) {
            this.deckName = builder.deckName == null ? "" : builder.deckName;
            this.opponentName = builder.opponentName == null ? "" : builder.opponentName;
            this.result = builder.result == null ? Result.INCOMPLETE : builder.result;
            this.turns = Math.max(0, builder.turns);
            this.detail = builder.detail == null ? "" : builder.detail;
        }

        /**
         * @return the deck that was played, possibly empty
         */
        public String getDeckName() {
            return this.deckName;
        }

        /**
         * @return the opponent's name, possibly empty
         */
        public String getOpponentName() {
            return this.opponentName;
        }

        /**
         * @return the match outcome, never {@code null}
         */
        public Result getResult() {
            return this.result;
        }

        /**
         * @return the number of turns the match lasted
         */
        public int getTurns() {
            return this.turns;
        }

        /**
         * @return an optional pre-formatted timestamp or note, possibly empty
         */
        public String getDetail() {
            return this.detail;
        }

        /**
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Fluent builder for {@link Summary}.
         */
        public static final class Builder {
            private String deckName;
            private String opponentName;
            private Result result;
            private int turns;
            private String detail;

            private Builder() {
            }

            /**
             * @param deckName0 the deck that was played
             * @return this builder, for chaining
             */
            public Builder deckName(final String deckName0) {
                this.deckName = deckName0;
                return this;
            }

            /**
             * @param opponentName0 the opponent's name
             * @return this builder, for chaining
             */
            public Builder opponentName(final String opponentName0) {
                this.opponentName = opponentName0;
                return this;
            }

            /**
             * @param result0 the match outcome
             * @return this builder, for chaining
             */
            public Builder result(final Result result0) {
                this.result = result0;
                return this;
            }

            /**
             * @param turns0 the number of turns the match lasted
             * @return this builder, for chaining
             */
            public Builder turns(final int turns0) {
                this.turns = turns0;
                return this;
            }

            /**
             * @param detail0 an optional pre-formatted timestamp or note
             * @return this builder, for chaining
             */
            public Builder detail(final String detail0) {
                this.detail = detail0;
                return this;
            }

            /**
             * @return an immutable {@link Summary} from the current builder state
             */
            public Summary build() {
                return new Summary(this);
            }
        }
    }

    private static final int PILL_HEIGHT = 20;
    private static final int PILL_PAD = 10;

    private final SkinColor winColor = FSkin.getColor(FSkin.Colors.CLR_ACTIVE).stepColor(-10);
    private final SkinColor lossColor = FSkin.getColor(FSkin.Colors.CLR_INACTIVE).stepColor(-15);
    private final SkinColor neutralColor = FSkin.getColor(FSkin.Colors.CLR_THEME).stepColor(40);

    private Summary summary;

    /**
     * Creates an empty last-match tile.
     */
    public LastMatchTile() {
        super(Localizer.getInstance().getMessageorUseDefault("lblLastMatch", "Last Match"));
    }

    /**
     * @return the rendered summary, or {@code null} when none has been supplied
     */
    public Summary getSummary() {
        return this.summary;
    }

    /**
     * @return {@code true} when the tile still shows its placeholder text
     */
    public boolean isEmpty() {
        return this.summary == null;
    }

    /**
     * Replaces the rendered match summary.
     *
     * @param summary0 the summary to show, or {@code null} for the placeholder
     * @return this tile, for chaining
     */
    public LastMatchTile setSummary(final Summary summary0) {
        this.summary = summary0;
        repaintTile();
        return this;
    }

    /**
     * Clears the rendered match summary.
     *
     * @return this tile, for chaining
     */
    public LastMatchTile clearSummary() {
        return setSummary(null);
    }

    @Override
    protected void paintContent(final Graphics2D g2d, final Rectangle inner) {
        if (inner.width <= 0 || inner.height <= 0) {
            return;
        }
        final Font headFont = this.subtitleFont.getBaseFont();
        final Font bodyFont = this.titleFont.getBaseFont();
        final Font footFont = this.subtitleFont.getBaseFont();
        final FontMetrics headFm = g2d.getFontMetrics(headFont);
        final FontMetrics bodyFm = g2d.getFontMetrics(bodyFont);
        final FontMetrics footFm = g2d.getFontMetrics(footFont);
        final SkinColor textColor = FSkin.getColor(FSkin.Colors.CLR_TEXT);
        int y = inner.y;

        g2d.setFont(headFont);
        FSkin.setGraphicsColor(g2d, textColor.stepColor(-45));
        g2d.drawString(truncate(headFm, getTitle(), inner.width), inner.x, y + headFm.getAscent());
        y += headFm.getHeight() + 4;

        final Summary current = this.summary;
        if (current == null) {
            g2d.setFont(footFont);
            FSkin.setGraphicsColor(g2d, textColor.stepColor(-60));
            final String placeholder = Localizer.getInstance()
                    .getMessageorUseDefault("lblNoRecentMatch", "No matches played yet");
            g2d.drawString(truncate(footFm, placeholder, inner.width), inner.x, y + footFm.getAscent());
            return;
        }

        final int pillWidth = measurePill(g2d, current.getResult());
        final int textWidth = Math.max(0, inner.width - pillWidth - PILL_PAD);

        g2d.setFont(bodyFont);
        FSkin.setGraphicsColor(g2d, textColor);
        g2d.drawString(truncate(bodyFm, current.getDeckName(), textWidth), inner.x, y + bodyFm.getAscent());
        y += bodyFm.getHeight();

        g2d.setFont(footFont);
        FSkin.setGraphicsColor(g2d, textColor.stepColor(-35));
        final String footer = buildFooter(current);
        g2d.drawString(truncate(footFm, footer, textWidth), inner.x, y + footFm.getAscent());

        paintResultPill(g2d, inner, pillWidth);
    }

    private void paintResultPill(final Graphics2D g2d, final Rectangle inner, final int pillWidth) {
        final SkinColor fill = resultColor(this.summary.getResult());
        final RoundRectangle2D.Float pill = new RoundRectangle2D.Float(
                inner.x + inner.width - pillWidth, inner.y + (inner.height - PILL_HEIGHT) / 2,
                pillWidth, PILL_HEIGHT, PILL_HEIGHT, PILL_HEIGHT);
        g2d.setColor(fill.alphaColor(210).getColor());
        g2d.fill(pill);
        g2d.setStroke(new BasicStroke(1f));
        FSkin.setGraphicsColor(g2d, fill.brighter());
        g2d.draw(pill);

        final Font font = this.subtitleFont.getBaseFont();
        final FontMetrics fm = g2d.getFontMetrics(font);
        g2d.setFont(font);
        FSkin.setGraphicsColor(g2d, FSkin.getColor(FSkin.Colors.CLR_TEXT));
        final String label = resultLabel(this.summary.getResult());
        g2d.drawString(label, pill.x + (pillWidth - fm.stringWidth(label)) / 2,
                pill.y + (PILL_HEIGHT - fm.getHeight()) / 2 + fm.getAscent());
    }

    private String buildFooter(final Summary current) {
        final Localizer localizer = Localizer.getInstance();
        final StringBuilder sb = new StringBuilder();
        if (!current.getOpponentName().isEmpty()) {
            sb.append(current.getOpponentName());
        }
        if (current.getTurns() > 0) {
            if (sb.length() > 0) {
                sb.append("  \u2022  ");
            }
            sb.append(localizer.getMessageorUseDefault("lblTurnsFormat", "{0} turns", current.getTurns()));
        }
        if (!current.getDetail().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  \u2022  ");
            }
            sb.append(current.getDetail());
        }
        return sb.toString();
    }

    private int measurePill(final Graphics2D g2d, final Result result) {
        final FontMetrics fm = g2d.getFontMetrics(this.subtitleFont.getBaseFont());
        return Math.max(48, fm.stringWidth(resultLabel(result)) + 16);
    }

    private SkinColor resultColor(final Result result) {
        switch (result) {
            case WIN:
                return this.winColor;
            case LOSS:
                return this.lossColor;
            default:
                return this.neutralColor;
        }
    }

    private static String resultLabel(final Result result) {
        final Localizer localizer = Localizer.getInstance();
        switch (result) {
            case WIN:
                return localizer.getMessageorUseDefault("lblResultWin", "Victory");
            case LOSS:
                return localizer.getMessageorUseDefault("lblResultLoss", "Defeat");
            case DRAW:
                return localizer.getMessageorUseDefault("lblResultDraw", "Draw");
            default:
                return localizer.getMessageorUseDefault("lblResultIncomplete", "No Result");
        }
    }
}
