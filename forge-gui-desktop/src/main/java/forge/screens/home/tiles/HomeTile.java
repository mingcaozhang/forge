package forge.screens.home.tiles;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

import javax.swing.JPanel;

import forge.gui.framework.EDocID;
import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.toolbox.FSkin.SkinFont;
import forge.toolbox.FSkin.SkinImage;
import forge.util.Localizer;
import forge.view.arcane.util.UIAnimator;

/**
 * Rounded, hoverable dashboard tile used by the Arena-style home screen.
 *
 * <p>The tile is intentionally free of any screen/navigation knowledge: it only
 * carries an optional {@link EDocID} tag and invokes an injected click handler,
 * so the home controller can wire it through the existing
 * {@code CHomeUI.itemClick(itemEnum)} path without this class depending on it.
 * Hover and press feedback is driven by {@link UIAnimator}, which automatically
 * degrades to an instant snap when animations are disabled.</p>
 */
@SuppressWarnings("serial")
public class HomeTile extends JPanel {
    /** Corner radius shared by every dashboard tile. */
    public static final int ARC_SIZE = 16;
    /** Inner padding between the tile border and its content. */
    protected static final int PADDING = 14;

    private static final int HOVER_DURATION_MILLIS = 160;
    private static final float HOVER_LIFT = 3f;
    private static final float BASE_STROKE = 1f;
    private static final float HOVER_STROKE = 2f;

    private final SkinColor clrTheme = FSkin.getColor(FSkin.Colors.CLR_THEME);
    private final SkinColor baseFillTop = this.clrTheme.stepColor(-20);
    private final SkinColor baseFillBottom = this.clrTheme.stepColor(-45);
    private final SkinColor hoverFillTop = this.clrTheme.stepColor(-8);
    private final SkinColor hoverFillBottom = this.clrTheme.stepColor(-34);
    private final SkinColor baseBorder = this.clrTheme.stepColor(20);
    private final SkinColor hoverBorder = this.clrTheme.brighter();
    private final SkinColor textColor = FSkin.getColor(FSkin.Colors.CLR_TEXT);
    private final SkinColor disabledTextColor = FSkin.getColor(FSkin.Colors.CLR_TEXT).stepColor(-60);
    protected final SkinFont titleFont = FSkin.getRelativeBoldFont(16);
    protected final SkinFont subtitleFont = FSkin.getRelativeFont(11);

    private String title = "";
    private String subtitle = "";
    private FSkinProp iconProp;
    private SkinImage icon;
    private EDocID docId;
    private Consumer<? super HomeTile> onClick;
    private float hoverProgress;
    private boolean pressed;

    /**
     * Creates a tile with only a title.
     *
     * @param title0 the headline shown on the tile; {@code null} is treated as empty
     */
    public HomeTile(final String title0) {
        this(title0, null);
    }

    /**
     * Creates a tile with a title and a secondary line.
     *
     * @param title0    the headline shown on the tile; {@code null} is treated as empty
     * @param subtitle0 the secondary line; {@code null} is treated as empty
     */
    public HomeTile(final String title0, final String subtitle0) {
        setOpaque(false);
        setLayout(null);
        setFocusable(true);
        setRequestFocusEnabled(true);
        setTitle(title0);
        setSubtitle(subtitle0);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(final MouseEvent e) {
                setHovered(true);
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                setPressed(false);
                setHovered(false);
            }

            @Override
            public void mousePressed(final MouseEvent e) {
                if (isEnabled()) {
                    setPressed(true);
                }
            }

            @Override
            public void mouseReleased(final MouseEvent e) {
                setPressed(false);
            }

            @Override
            public void mouseClicked(final MouseEvent e) {
                if (isEnabled() && contains(e.getX(), e.getY())) {
                    triggerClick();
                }
            }
        });
    }

    //========== Retrieval methods

    /**
     * @return the headline text, never {@code null}
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * @return the secondary line, never {@code null}
     */
    public String getSubtitle() {
        return this.subtitle;
    }

    /**
     * @return the navigation tag for this tile, or {@code null} when unset
     */
    public EDocID getDocId() {
        return this.docId;
    }

    /**
     * @return the content rectangle inside the tile border and padding
     */
    protected Rectangle getContentBounds() {
        final int lift = Math.round(HOVER_LIFT * this.hoverProgress);
        return new Rectangle(PADDING, PADDING + lift,
                Math.max(0, getWidth() - 2 * PADDING), Math.max(0, getHeight() - 2 * PADDING));
    }

    //========== Fluent configuration

    /**
     * Sets the headline text.
     *
     * @param title0 the headline; {@code null} is treated as empty
     * @return this tile, for chaining
     */
    public HomeTile setTitle(final String title0) {
        this.title = title0 == null ? "" : title0;
        updateAccessibleName();
        repaintTile();
        return this;
    }

    /**
     * Sets the secondary line.
     *
     * @param subtitle0 the secondary line; {@code null} is treated as empty
     * @return this tile, for chaining
     */
    public HomeTile setSubtitle(final String subtitle0) {
        this.subtitle = subtitle0 == null ? "" : subtitle0;
        repaintTile();
        return this;
    }

    /**
     * Sets the tile icon from a skin image, resolved lazily at paint time.
     *
     * @param iconProp0 the skin image to draw beside the title, or {@code null}
     * @return this tile, for chaining
     */
    public HomeTile setIcon(final FSkinProp iconProp0) {
        this.iconProp = iconProp0;
        this.icon = null;
        repaintTile();
        return this;
    }

    /**
     * Sets the tile icon directly, bypassing the skin lookup.
     *
     * @param icon0 the image to draw beside the title, or {@code null}
     * @return this tile, for chaining
     */
    public HomeTile setIcon(final SkinImage icon0) {
        this.iconProp = null;
        this.icon = icon0;
        repaintTile();
        return this;
    }

    /**
     * Tags the tile with the document it opens. The tag is only read by the
     * home controller when it wires the click handler.
     *
     * @param docId0 the navigation tag, or {@code null}
     * @return this tile, for chaining
     */
    public HomeTile setDocId(final EDocID docId0) {
        this.docId = docId0;
        return this;
    }

    /**
     * Installs the handler invoked when the tile is clicked.
     *
     * @param onClick0 the handler, or {@code null} to make the tile inert
     * @return this tile, for chaining
     */
    public HomeTile setOnClick(final Consumer<? super HomeTile> onClick0) {
        this.onClick = onClick0;
        return this;
    }

    /**
     * Requests the default tile footprint.
     *
     * @param width  preferred width in pixels
     * @param height preferred height in pixels
     * @return this tile, for chaining
     */
    public HomeTile setTileSize(final int width, final int height) {
        setPreferredSize(new Dimension(Math.max(0, width), Math.max(0, height)));
        setMinimumSize(getPreferredSize());
        return this;
    }

    //========== Interaction

    /**
     * Drives the hover glow, tweening smoothly unless animations are disabled.
     *
     * @param hovered0 whether the pointer is over the tile
     */
    public void setHovered(final boolean hovered0) {
        final float target = hovered0 ? 1f : 0f;
        if (this.hoverProgress == target) {
            return;
        }
        final float from = this.hoverProgress;
        UIAnimator.start(HOVER_DURATION_MILLIS, this, new UIAnimator.Tween() {
            @Override
            public void onFrame(final float easedProgress) {
                HomeTile.this.hoverProgress = from + (target - from) * easedProgress;
                repaintTile();
            }

            @Override
            public void onEnd() {
                HomeTile.this.hoverProgress = target;
                repaintTile();
            }
        });
    }

    /**
     * Drives the pressed state, which deepens the tile shading.
     *
     * @param pressed0 whether the pointer button is held down
     */
    public void setPressed(final boolean pressed0) {
        if (this.pressed == pressed0) {
            return;
        }
        this.pressed = pressed0;
        repaintTile();
    }

    /**
     * Invokes the installed click handler, if any.
     */
    protected void triggerClick() {
        final Consumer<? super HomeTile> handler = this.onClick;
        if (handler != null) {
            handler.accept(this);
        }
    }

    //========== Painting

    /**
     * Repaints just this tile.
     */
    protected void repaintTile() {
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
            final int w = getWidth();
            final int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            final boolean enabled = isEnabled();
            final float hover = enabled ? this.hoverProgress : 0f;
            final float lift = HOVER_LIFT * hover;
            paintTileFrame(g2d, w, h, lift, hover, enabled);
            paintContent(g2d, getContentBounds());
        } finally {
            g2d.dispose();
        }
    }

    /**
     * Fills and outlines the rounded tile body.
     *
     * @param g2d     the graphics context to paint onto
     * @param w       tile width in pixels
     * @param h       tile height in pixels
     * @param lift    vertical offset applied by the hover animation
     * @param hover   eased hover progress in {@code [0, 1]}
     * @param enabled whether the tile is currently enabled
     */
    protected void paintTileFrame(final Graphics2D g2d, final int w, final int h, final float lift,
            final float hover, final boolean enabled) {
        final float pressDim = this.pressed ? 0.15f : 0f;
        final RoundRectangle2D.Float shape = new RoundRectangle2D.Float(
                1f, 1f + lift, w - 3f, h - 3f, ARC_SIZE, ARC_SIZE);
        final Color top = blend(this.baseFillTop.getColor(), this.hoverFillTop.getColor(), hover);
        final Color bottom = blend(this.baseFillBottom.getColor(), this.hoverFillBottom.getColor(), hover);
        final Color fillTop = dim(top, pressDim);
        final Color fillBottom = dim(bottom, pressDim);
        g2d.setPaint(new GradientPaint(0f, 1f + lift, fillTop, 0f, h - 2f, fillBottom));
        g2d.fill(shape);
        if (hover > 0f) {
            final Color glow = blend(new Color(0f, 0f, 0f, 0f), this.clrTheme.brighter().alphaColor(70).getColor(), hover);
            g2d.setColor(glow);
            g2d.draw(new RoundRectangle2D.Float(0f, 0f + lift, w - 1f, h - 1f, ARC_SIZE, ARC_SIZE));
        }
        g2d.setStroke(new BasicStroke(hover > 0f ? HOVER_STROKE : BASE_STROKE));
        final SkinColor border = !enabled ? this.baseBorder.stepColor(-40)
                : (hover > 0.5f ? this.hoverBorder : this.baseBorder);
        FSkin.setGraphicsColor(g2d, border);
        g2d.draw(shape);
    }

    /**
     * Draws the tile content. The base implementation renders the optional icon
     * followed by the vertically centred title and subtitle.
     *
     * @param g2d   the graphics context to paint onto
     * @param inner the content rectangle inside the tile border
     */
    protected void paintContent(final Graphics2D g2d, final Rectangle inner) {
        if (inner.width <= 0 || inner.height <= 0) {
            return;
        }
        final SkinImage resolvedIcon = resolveIcon();
        final int iconSize = resolvedIcon != null ? Math.min(inner.height, 44) : 0;
        int textX = inner.x;
        if (resolvedIcon != null) {
            FSkin.drawImage(g2d, resolvedIcon, inner.x, inner.y + (inner.height - iconSize) / 2, iconSize, iconSize);
            if (!this.title.isEmpty() || !this.subtitle.isEmpty()) {
                textX += iconSize + 10;
            }
        }
        final int available = Math.max(0, inner.x + inner.width - textX);
        final Font titleBase = this.titleFont.getBaseFont();
        final Font subtitleBase = this.subtitleFont.getBaseFont();
        final FontMetrics titleFm = g2d.getFontMetrics(titleBase);
        final FontMetrics subtitleFm = g2d.getFontMetrics(subtitleBase);
        final boolean hasTitle = !this.title.isEmpty();
        final boolean hasSubtitle = !this.subtitle.isEmpty();
        if (!hasTitle && !hasSubtitle) {
            return;
        }
        final int blockHeight = (hasTitle ? titleFm.getHeight() : 0) + (hasSubtitle ? subtitleFm.getHeight() : 0);
        int textY = inner.y + (inner.height - blockHeight) / 2;
        final SkinColor bodyColor = isEnabled() ? this.textColor : this.disabledTextColor;
        if (hasTitle) {
            g2d.setFont(titleBase);
            FSkin.setGraphicsColor(g2d, bodyColor);
            g2d.drawString(truncate(titleFm, this.title, available), textX, textY + titleFm.getAscent());
            textY += titleFm.getHeight();
        }
        if (hasSubtitle) {
            g2d.setFont(subtitleBase);
            FSkin.setGraphicsColor(g2d, bodyColor.stepColor(-35));
            g2d.drawString(truncate(subtitleFm, this.subtitle, available), textX, textY + subtitleFm.getAscent());
        }
    }

    /**
     * Lazily resolves the configured skin icon, tolerating an unloaded skin.
     *
     * @return the resolved icon, or {@code null} when none is available
     */
    protected SkinImage resolveIcon() {
        if (this.icon != null) {
            return this.icon;
        }
        if (this.iconProp == null) {
            return null;
        }
        try {
            this.icon = FSkin.getImage(this.iconProp);
        } catch (final Exception e) {
            this.iconProp = null;
            this.icon = null;
        }
        return this.icon;
    }

    /**
     * Clips a single line of text to the given width with an ellipsis.
     *
     * @param fm       metrics for the font the text will be drawn with
     * @param text     the candidate text
     * @param maxWidth maximum width in pixels
     * @return the text, shortened with a trailing {@code "..."} when needed
     */
    protected static String truncate(final FontMetrics fm, final String text, final int maxWidth) {
        if (maxWidth <= 0 || text.isEmpty()) {
            return "";
        }
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        final int ellipsisWidth = fm.stringWidth("...");
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : text.substring(0, end) + "...";
    }

    /**
     * Linearly blends two colours, including their alpha channel.
     *
     * @param from the colour at {@code t == 0}
     * @param to   the colour at {@code t == 1}
     * @param t    blend factor, clamped to {@code [0, 1]}
     * @return the blended colour
     */
    protected static Color blend(final Color from, final Color to, final float t) {
        final float factor = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * factor),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * factor),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * factor),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * factor));
    }

    /**
     * Multiplies the brightness of a colour without changing its alpha.
     *
     * @param color  the source colour
     * @param amount how much darker to make it, in {@code [0, 1]}
     * @return the darkened colour
     */
    protected static Color dim(final Color color, final float amount) {
        final float factor = 1f - Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(color.getRed() * factor),
                Math.round(color.getGreen() * factor),
                Math.round(color.getBlue() * factor),
                color.getAlpha());
    }

    private void updateAccessibleName() {
        final String name = this.title.isEmpty()
                ? Localizer.getInstance().getMessageorUseDefault("lblHomeTile", "Home tile")
                : this.title;
        getAccessibleContext().setAccessibleName(name);
    }
}
