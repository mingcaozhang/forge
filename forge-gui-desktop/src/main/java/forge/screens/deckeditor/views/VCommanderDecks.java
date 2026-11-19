package forge.screens.deckeditor.views;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

import forge.ImageCache;
import forge.card.ColorSet;
import forge.deck.DeckProxy;
import forge.deck.io.DeckPreferences;
import forge.game.GameType;
import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.gui.framework.IVDoc;
import forge.item.PaperCard;
import forge.itemmanager.DeckManager;
import forge.itemmanager.ItemManagerContainer;
import forge.screens.deckeditor.controllers.CCommanderDecks;
import forge.screens.match.controllers.CDetailPicture;
import forge.toolbox.CardFaceSymbols;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.toolbox.FSkin.SkinFont;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/** 
 * Assembles Swing components of all deck viewer in deck editor.
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public enum VCommanderDecks implements IVDoc<CCommanderDecks> {
    /** */
    SINGLETON_INSTANCE;

    // Fields used with interface IVDoc
    private DragCell parentCell;
    final Localizer localizer = Localizer.getInstance();
    private final DragTab tab = new DragTab(localizer.getMessage("lblCommander"));

    private DeckManager lstDecks;
    private final CommanderShowcasePanel showcase = new CommanderShowcasePanel();

    //========== Overridden methods

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getDocumentID()
     */
    @Override
    public EDocID getDocumentID() {
        return EDocID.EDITOR_COMMANDER;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getTabLabel()
     */
    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getLayoutControl()
     */
    @Override
    public CCommanderDecks getLayoutControl() {
        return CCommanderDecks.SINGLETON_INSTANCE;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#setParentCell(forge.gui.framework.DragCell)
     */
    @Override
    public void setParentCell(DragCell cell0) {
        this.parentCell = cell0;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getParentCell()
     */
    @Override
    public DragCell getParentCell() {
        return this.parentCell;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#populate()
     */
    @Override
    public void populate() {
        CCommanderDecks.SINGLETON_INSTANCE.refresh(); //ensure decks refreshed in case any deleted or added since last loaded
        String preferredDeck = DeckPreferences.getCommanderDeck();
        JPanel parentBody = parentCell.getBody();
        parentBody.setLayout(new MigLayout("insets 5, gap 0, wrap, hidemode 3"));
        parentBody.add(showcase, "growx, wrap");
        parentBody.add(new ItemManagerContainer(lstDecks), "push, grow");
        VAllDecks.editPreferredDeck(lstDecks, preferredDeck);
        showcase.setDeck(lstDecks.getSelectedItem());
    }

    //========== Retrieval methods
    /** @return {@link JPanel} */
    public DeckManager getLstDecks() {
        return lstDecks;
    }

    /**
     * Updates the commander/partner showcase header for the given deck.
     *
     * @param deck selected commander deck, or null to show the placeholder
     */
    public void updateShowcase(final DeckProxy deck) {
        showcase.setDeck(deck);
    }

    public void setCDetailPicture(final CDetailPicture cDetailPicture) {
        this.lstDecks = new DeckManager(GameType.Commander, cDetailPicture);
        this.lstDecks.setCaption(localizer.getMessage("lblCommanderDecks"));
        this.lstDecks.setSelectCommand(() -> showcase.setDeck(this.lstDecks.getSelectedItem()));
    }

    /**
     * Rounded header painting a cover thumbnail, commander/partner name(s),
     * and color identity badges for the currently selected commander deck.
     * Purely visual: it never mutates deck state.
     */
    static final class CommanderShowcasePanel extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final int ARC = 12;
        private static final int PAD = 1;
        private static final int HEIGHT = 116;
        private static final int MARGIN = 12;
        private static final int ART_WIDTH = 66;
        private static final int ART_HEIGHT = 92;
        private static final int ART_RADIUS = 8;
        private static final int BADGE_SIZE = 18;

        private final Localizer localizer = Localizer.getInstance();
        private final SkinFont titleSkinFont = FSkin.getRelativeFont(16);
        private final SkinFont subtitleSkinFont = FSkin.getRelativeFont(11);

        private DeckProxy deck;

        void setDeck(final DeckProxy deck0) {
            this.deck = deck0;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, HEIGHT);
        }

        @Override
        protected void paintComponent(final Graphics graphics) {
            super.paintComponent(graphics);
            final int w = getWidth();
            final int h = getHeight();
            if (w <= PAD * 2) {
                return;
            }
            final Graphics2D g2d = (Graphics2D) graphics.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintChrome(g2d, w, h);

                String title = null;
                String subtitle = null;
                ColorSet colorIdentity = null;
                boolean showArt = false;
                if (deck != null) {
                    title = deck.getName();
                    if (!deck.isGeneratedDeck()) {
                        final List<PaperCard> commanders = deck.getDeck().getCommanders();
                        if (!commanders.isEmpty()) {
                            subtitle = deck.getName();
                            final StringBuilder names = new StringBuilder();
                            for (final PaperCard commander : commanders) {
                                if (names.length() > 0) {
                                    names.append(" & ");
                                }
                                names.append(commander.getName());
                            }
                            title = names.toString();
                            colorIdentity = deck.getColorIdentity();
                            showArt = true;
                            paintCover(g2d, commanders.get(0), h);
                        }
                    }
                }
                if (title == null) {
                    title = localizer.getMessageorUseDefault("lblSelectCommanderDeck", "Select a commander deck");
                }

                final int badgeRowWidth = colorIdentity == null ? 0 : colorIdentity.countColors() * BADGE_SIZE;
                final int textX = MARGIN + ART_WIDTH + MARGIN;
                final int maxTextWidth = Math.max(0, w - textX - MARGIN - badgeRowWidth - (badgeRowWidth > 0 ? MARGIN / 2 : 0));

                final Font titleFont = titleSkinFont.getBaseFont().deriveFont(Font.BOLD);
                final Font subtitleFont = subtitleSkinFont.getBaseFont();
                final FontMetrics tfm = g2d.getFontMetrics(titleFont);
                final FontMetrics sfm = g2d.getFontMetrics(subtitleFont);
                final int lineHeight = tfm.getHeight() + (subtitle != null ? sfm.getHeight() : 0);
                int textY = (h - lineHeight) / 2 + tfm.getAscent();

                FSkin.setGraphicsColor(g2d, FSkin.getColor(FSkin.Colors.CLR_TEXT));
                g2d.setFont(titleFont);
                g2d.drawString(truncate(tfm, title, maxTextWidth), textX, textY);
                if (subtitle != null) {
                    textY += tfm.getHeight();
                    FSkin.setGraphicsColor(g2d, FSkin.getColor(FSkin.Colors.CLR_TEXT).stepColor(-30));
                    g2d.setFont(subtitleFont);
                    g2d.drawString(truncate(sfm, subtitle, maxTextWidth), textX, textY);
                }

                if (colorIdentity != null) {
                    CardFaceSymbols.drawColorSet(g2d, colorIdentity,
                            w - MARGIN - badgeRowWidth, (h - BADGE_SIZE) / 2, BADGE_SIZE, false);
                }
            } finally {
                g2d.dispose();
            }
        }

        private void paintChrome(final Graphics2D g2d, final int w, final int h) {
            final SkinColor theme = FSkin.getColor(FSkin.Colors.CLR_THEME);
            g2d.setPaint(new GradientPaint(0f, PAD, theme.stepColor(-8).getColor(),
                    0f, h - PAD, theme.stepColor(-16).getColor()));
            g2d.fillRoundRect(PAD, PAD, w - PAD * 2, h - PAD * 2, ARC, ARC);
            FSkin.setGraphicsColor(g2d,
                    FSkin.getColor(FSkin.Colors.CLR_BORDERS).getContrastColor(20).alphaColor(180));
            g2d.drawRoundRect(PAD, PAD, w - PAD * 2, h - PAD * 2, ARC, ARC);
        }

        private void paintCover(final Graphics2D g2d, final PaperCard commander, final int h) {
            final int artX = MARGIN;
            final int artY = (h - ART_HEIGHT) / 2;
            g2d.setColor(FSkin.getColor(FSkin.Colors.CLR_THEME).stepColor(-24).getColor());
            g2d.fillRoundRect(artX, artY, ART_WIDTH, ART_HEIGHT, ART_RADIUS, ART_RADIUS);
            final BufferedImage art = ImageCache.scaleImage(commander.getImageKey(false),
                    ART_WIDTH, ART_HEIGHT, false, null);
            if (art != null) {
                g2d.drawImage(ImageCache.makeRoundedCorner(art, ART_RADIUS), artX, artY, null);
            }
        }

        private static String truncate(final FontMetrics fm, final String text, final int maxWidth) {
            if (text.isEmpty() || fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            int len = text.length();
            while (len > 0 && fm.stringWidth(text.substring(0, len) + "...") > maxWidth) {
                len--;
            }
            return len > 0 ? text.substring(0, len) + "..." : "";
        }
    }
}
