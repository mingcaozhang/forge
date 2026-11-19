package forge.screens.home.tiles;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JPanel;

import forge.ImageCache;
import forge.deck.Deck;
import forge.deck.DeckProxy;
import forge.item.PaperCard;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.toolbox.FSkin.SkinFont;
import forge.util.Localizer;
import forge.view.arcane.util.CardTextureCache;
import forge.view.arcane.util.UIAnimator;

/**
 * Horizontally scrolling strip of recently used decks for the home dashboard.
 *
 * <p>The carousel never queries deck storage itself: callers hand it a list (or
 * a supplier that can be re-read with {@link #refresh()}) and it renders cover
 * art for whichever decks it is given. This keeps it independent of how recent
 * decks end up being tracked.</p>
 */
@SuppressWarnings("serial")
public class RecentDecksCarousel extends JPanel {
    /** Width of a single deck cover in pixels. */
    public static final int COVER_WIDTH = 110;
    /** Height of a single deck cover in pixels. */
    public static final int COVER_HEIGHT = 154;
    /** Gap between neighbouring covers in pixels. */
    public static final int COVER_GAP = 12;

    private static final int PADDING = 8;
    private static final int CAPTION_HEIGHT = 20;
    private static final int ARC_SIZE = 10;
    private static final int HOVER_DURATION_MILLIS = 160;
    private static final float HOVER_LIFT = 4f;
    private static final int SCROLL_STEP = COVER_WIDTH + COVER_GAP;

    private final CardTextureCache textures = new CardTextureCache();
    private final SkinColor clrTheme = FSkin.getColor(FSkin.Colors.CLR_THEME);
    private final SkinColor coverBack = this.clrTheme.stepColor(-60);
    private final SkinColor baseBorder = this.clrTheme.stepColor(15);
    private final SkinColor hoverBorder = this.clrTheme.brighter();
    private final SkinColor textColor = FSkin.getColor(FSkin.Colors.CLR_TEXT);
    private final SkinFont captionFont = FSkin.getRelativeFont(11);

    private final List<DeckProxy> decks = new ArrayList<>();
    private final List<DeckProxy> snapshot = new ArrayList<>();
    private float[] hoverProgress = new float[0];

    private Supplier<List<DeckProxy>> deckSupplier = Collections::emptyList;
    private Consumer<? super DeckProxy> onDeckClicked;
    private int scrollOffset;
    private int hoveredIndex = -1;

    /**
     * Creates an empty carousel.
     */
    public RecentDecksCarousel() {
        setOpaque(false);
        setLayout(null);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseMoved(final MouseEvent e) {
                updateHoveredIndex(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                setHoveredIndex(-1);
            }

            @Override
            public void mouseClicked(final MouseEvent e) {
                if (!isEnabled()) {
                    return;
                }
                final DeckProxy clicked = deckAt(e.getX(), e.getY());
                if (clicked != null && RecentDecksCarousel.this.onDeckClicked != null) {
                    RecentDecksCarousel.this.onDeckClicked.accept(clicked);
                }
            }
        });
        addMouseWheelListener(this::onWheel);
    }

    //========== Configuration

    /**
     * Replaces the decks shown by the carousel. {@code null} clears it.
     *
     * @param recentDecks the decks to render, in most-recent-first order
     * @return this carousel, for chaining
     */
    public RecentDecksCarousel setDecks(final List<? extends DeckProxy> recentDecks) {
        this.decks.clear();
        if (recentDecks != null) {
            this.decks.addAll(recentDecks);
        }
        this.snapshot.clear();
        this.snapshot.addAll(this.decks);
        this.hoverProgress = new float[this.snapshot.size()];
        this.hoveredIndex = -1;
        clampScroll();
        updateAccessibleName();
        repaintCarousel();
        return this;
    }

    /**
     * @return an unmodifiable view of the decks currently rendered
     */
    public List<DeckProxy> getDecks() {
        return Collections.unmodifiableList(this.snapshot);
    }

    /**
     * @return {@code true} when there is nothing to show
     */
    public boolean isEmpty() {
        return this.snapshot.isEmpty();
    }

    /**
     * Installs the source re-read by {@link #refresh()}.
     *
     * @param deckSupplier0 the source of recent decks, or {@code null} to clear
     * @return this carousel, for chaining
     */
    public RecentDecksCarousel setDeckSupplier(final Supplier<List<DeckProxy>> deckSupplier0) {
        this.deckSupplier = deckSupplier0 == null ? () -> Collections.emptyList() : deckSupplier0;
        return this;
    }

    /**
     * Installs the handler invoked when a deck cover is clicked.
     *
     * @param onDeckClicked0 the handler, or {@code null} to make covers inert
     * @return this carousel, for chaining
     */
    public RecentDecksCarousel setOnDeckClicked(final Consumer<? super DeckProxy> onDeckClicked0) {
        this.onDeckClicked = onDeckClicked0;
        return this;
    }

    /**
     * Re-reads the installed supplier and updates the rendered covers.
     *
     * @return this carousel, for chaining
     */
    public RecentDecksCarousel refresh() {
        try {
            return setDecks(this.deckSupplier.get());
        } catch (final Exception e) {
            return setDecks(null);
        }
    }

    /**
     * @return the preferred height of the carousel, including its caption row
     */
    public static int getPreferredHeight() {
        return COVER_HEIGHT + CAPTION_HEIGHT + 2 * PADDING;
    }

    @Override
    public Dimension getPreferredSize() {
        final int contentWidth = getContentWidth();
        return new Dimension(Math.min(contentWidth, 4 * SCROLL_STEP), getPreferredHeight());
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(SCROLL_STEP, getPreferredHeight());
    }

    //========== Geometry

    private int getContentWidth() {
        return 2 * PADDING + this.snapshot.size() * SCROLL_STEP - COVER_GAP;
    }

    private Rectangle coverBounds(final int index) {
        final int x = PADDING + index * SCROLL_STEP - this.scrollOffset;
        final int y = PADDING + Math.round(HOVER_LIFT * hoverValue(index));
        return new Rectangle(x, y, COVER_WIDTH, COVER_HEIGHT);
    }

    private float hoverValue(final int index) {
        return index >= 0 && index < this.hoverProgress.length ? this.hoverProgress[index] : 0f;
    }

    /**
     * Finds the deck whose cover contains the given carousel-relative point.
     *
     * @param x carousel-relative x coordinate
     * @param y carousel-relative y coordinate
     * @return the deck under the point, or {@code null} when there is none
     */
    protected DeckProxy deckAt(final int x, final int y) {
        final Rectangle viewport = new Rectangle(0, 0, getWidth(), getHeight());
        for (int i = 0; i < this.snapshot.size(); i++) {
            final Rectangle bounds = coverBounds(i);
            if (bounds.intersects(viewport) && bounds.contains(x, y)) {
                return this.snapshot.get(i);
            }
        }
        return null;
    }

    private void updateHoveredIndex(final int x, final int y) {
        setHoveredIndex(deckAt(x, y) == null ? -1 : this.snapshot.indexOf(deckAt(x, y)));
    }

    /**
     * Highlights the cover at the given index.
     *
     * @param index the cover to highlight, or {@code -1} for none
     */
    public void setHoveredIndex(final int index) {
        if (this.hoveredIndex == index) {
            return;
        }
        final int previous = this.hoveredIndex;
        this.hoveredIndex = index;
        animateHover(previous, 0f);
        animateHover(index, 1f);
    }

    /**
     * @return the currently highlighted cover index, or {@code -1} when none is
     */
    public int getHoveredIndex() {
        return this.hoveredIndex;
    }

    private void animateHover(final int index, final float target) {
        if (index < 0 || index >= this.hoverProgress.length) {
            return;
        }
        final float from = this.hoverProgress[index];
        if (from == target) {
            return;
        }
        final String key = "recent-deck-hover-" + System.identityHashCode(this) + "-" + index;
        UIAnimator.start(HOVER_DURATION_MILLIS, key, new UIAnimator.Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                RecentDecksCarousel.this.hoverProgress[index] = from + (target - from) * easedProgress;
                repaintCarousel();
            }

            @Override
            public void onEnd() {
                if (index < RecentDecksCarousel.this.hoverProgress.length) {
                    RecentDecksCarousel.this.hoverProgress[index] = target;
                }
                repaintCarousel();
            }
        });
    }

    private void onWheel(final MouseWheelEvent e) {
        e.consume();
        final int direction = Integer.signum(e.getWheelRotation()) * SCROLL_STEP;
        setScrollOffset(this.scrollOffset + direction);
    }

    /**
     * Scrolls the strip by the given pixel amount, clamped to the content width.
     *
     * @param offset0 the new scroll offset in pixels
     */
    public void setScrollOffset(final int offset0) {
        final int maxOffset = Math.max(0, getContentWidth() - getWidth());
        final int clamped = Math.max(0, Math.min(maxOffset, offset0));
        if (this.scrollOffset == clamped) {
            return;
        }
        this.scrollOffset = clamped;
        repaintCarousel();
    }

    /**
     * @return the current scroll offset in pixels
     */
    public int getScrollOffset() {
        return this.scrollOffset;
    }

    private void clampScroll() {
        setScrollOffset(this.scrollOffset);
    }

    //========== Painting

    /**
     * Repaints just the carousel.
     */
    protected void repaintCarousel() {
        final Dimension size = getSize();
        repaint(0, 0, size.width, size.height);
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        final Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            if (this.snapshot.isEmpty()) {
                paintPlaceholder(g2d);
                return;
            }
            g2d.setClip(new Rectangle(0, 0, getWidth(), getHeight()));
            for (int i = 0; i < this.snapshot.size(); i++) {
                paintCover(g2d, i);
            }
        } finally {
            g2d.dispose();
        }
    }

    private void paintPlaceholder(final Graphics2D g2d) {
        final Font font = this.captionFont.getBaseFont();
        final FontMetrics fm = g2d.getFontMetrics(font);
        final String text = Localizer.getInstance()
                .getMessageorUseDefault("lblNoRecentDecks", "No recent decks yet");
        g2d.setFont(font);
        FSkin.setGraphicsColor(g2d, this.textColor.stepColor(-55));
        g2d.drawString(text, PADDING, (getHeight() + fm.getAscent()) / 2);
    }

    private void paintCover(final Graphics2D g2d, final int index) {
        final DeckProxy deck = this.snapshot.get(index);
        final Rectangle bounds = coverBounds(index);
        if (bounds.x > getWidth() || bounds.x + bounds.width < 0) {
            return;
        }
        final float hover = hoverValue(index);
        FSkin.setGraphicsColor(g2d, this.coverBack);
        g2d.fill(new RoundRectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height, ARC_SIZE, ARC_SIZE));

        final BufferedImage art = resolveCover(deck, bounds.width, bounds.height);
        if (art != null) {
            final Rectangle clip = g2d.getClipBounds();
            g2d.setClip(new RoundRectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height,
                    ARC_SIZE, ARC_SIZE));
            g2d.drawImage(art, bounds.x, bounds.y, bounds.width, bounds.height, null);
            g2d.setClip(clip);
        }

        g2d.setStroke(new BasicStroke(hover > 0f ? 2f : 1f));
        FSkin.setGraphicsColor(g2d, hover > 0f ? this.hoverBorder : this.baseBorder);
        g2d.draw(new RoundRectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height, ARC_SIZE, ARC_SIZE));

        final Color nameColor = HomeTile.blend(this.textColor.getColor(),
                this.hoverBorder.getColor(), hover);
        final Font font = this.captionFont.getBaseFont();
        final FontMetrics fm = g2d.getFontMetrics(font);
        g2d.setFont(font);
        g2d.setColor(nameColor);
        g2d.drawString(HomeTile.truncate(fm, deck.getName(), bounds.width),
                bounds.x, bounds.y + bounds.height + fm.getAscent());
    }

    /**
     * Resolves the cover art for a deck, caching the result by deck identity.
     *
     * @param deck   the deck to render
     * @param width  requested cover width in pixels
     * @param height requested cover height in pixels
     * @return the cached cover art, or {@code null} when none can be produced
     */
    protected BufferedImage resolveCover(final DeckProxy deck, final int width, final int height) {
        final String key = CardTextureCache.textureKey(deck.getUniqueKey(), width, height, true, false, 0);
        return this.textures.getOrCompute(key, () -> loadCover(deck, width, height));
    }

    private static BufferedImage loadCover(final DeckProxy deck, final int width, final int height) {
        try {
            if (deck.isGeneratedDeck()) {
                return null;
            }
            final Deck model = deck.getDeck();
            final List<PaperCard> commanders = model.getCommanders();
            final PaperCard cover = commanders.isEmpty() ? deck.getHighestCMCCard() : commanders.get(0);
            if (cover == null) {
                return null;
            }
            final BufferedImage cardImage = ImageCache.scaleImage(cover.getImageKey(false), width, height, false, null);
            if (cardImage == null) {
                return null;
            }
            return ImageCache.getCroppedArt(cardImage, 0, 0, width, height);
        } catch (final Exception e) {
            return null;
        }
    }

    private void updateAccessibleName() {
        getAccessibleContext().setAccessibleName(Localizer.getInstance()
                .getMessageorUseDefault("lblRecentDecks", "Recent decks"));
    }
}
