/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.screens.match.views;

import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import forge.game.card.CardView;
import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.gui.framework.IVDoc;
import forge.gui.framework.SDisplayUtil;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.match.controllers.CPrompt;
import forge.toolbox.FButton;
import forge.toolbox.FHtmlViewer;
import forge.toolbox.FLabel;
import forge.toolbox.FPanel;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.Colors;
import forge.toolbox.FSkin.SkinColor;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * Assembles Swing components of message report.
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public class VPrompt implements IVDoc<CPrompt> {

    /** Corner diameter of the rounded prompt bar chrome, in px. */
    private static final int BAR_CORNER = 12;
    /** Corner diameter of the rounded prompt buttons, in px. */
    private static final int BUTTON_CORNER = 8;

    // Fields used with interface IVDoc
    private DragCell parentCell;
    final Localizer localizer = Localizer.getInstance();
    private final DragTab tab = new DragTab(localizer.getMessage("lblPrompt"));

    // Various components
    private final PromptButton btnOK = new PromptButton(localizer.getMessage("lblOK"), true);
    private final PromptButton btnCancel = new PromptButton(localizer.getMessage("lblCancel"), false);
    private final FLabel btnStack = new FLabel.ButtonBuilder()
            .text(localizer.getMessage("lblStack"))
            .fontSize(11)
            .modernToggle()
            .tooltip(localizer.getMessage("lblSHORTCUT_SHOWSTACK"))
            .cmdClick(() -> SDisplayUtil.showTab(EDocID.REPORT_STACK.getDoc()))
            .build();
    private final FHtmlViewer tarMessage = new FHtmlViewer();
    private final FScrollPane messageScroller = new FScrollPane(tarMessage, false,
    		ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JLabel lblGames;
    /** Rounded, gradient-filled chrome that hosts every prompt component. */
    private final JPanel promptBar = new PromptBar();
    /** Header band: game-info pill on the left, "show stack" affordance on the right. */
    private final JPanel header = new JPanel(new MigLayout("insets 0, gap 4px"));
    /** Rounded chrome around {@link #lblGames} so the game info reads as a pill. */
    private final FPanel gamesPill = new FPanel(new MigLayout("insets 1px 7px 1px 7px, gap 0px"));
    private CardView card = null;

    public void setCardView(final CardView card) {
        this.card = card;
    }

    private KeyAdapter buttonKeyAdapter = new KeyAdapter() {
        @Override
        public void keyPressed(final KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE
                    && btnCancel.isEnabled()
                    && (FModel.getPreferences().getPrefBoolean(FPref.UI_ALLOW_ESC_TO_END_TURN)
                            || !btnCancel.getText().equals(Localizer.getInstance().getMessage("lblEndTurn")))) {
                btnCancel.doClick();
                e.consume();
            }
        }
    };

    private final CPrompt controller;

    public VPrompt(final CPrompt controller) {
        this.controller = controller;

        lblGames = new FLabel.Builder()
                .fontSize(12)
                .fontStyle(Font.PLAIN)
                .fontAlign(SwingConstants.CENTER)
                .build();
        gamesPill.setCornerDiameter(14);
        gamesPill.add(lblGames);
        header.add(gamesPill, "h min:min");
        header.add(btnStack, "h 18px!, alignx right");

        btnOK.addKeyListener(buttonKeyAdapter);
        btnCancel.addKeyListener(buttonKeyAdapter);

        tarMessage.setForeground(FSkin.getColor(FSkin.Colors.CLR_TEXT));
        tarMessage.setMargin(new Insets(3, 3, 3, 3));
        tarMessage.getAccessibleContext().setAccessibleName("Prompt");
        tarMessage.setFocusable(true); // Allow tab to navigate to the prompt.
        messageScroller.getViewport().getView().addMouseListener(new MouseAdapter() {
        	@Override 
        	public void mouseEntered(final MouseEvent e) {
        		if (card != null) {
                    controller.getMatchUI().setCard(card);
        		}
        	}
        });
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#populate()
     */
    @Override
    public void populate() {
    	ForgePreferences prefs = FModel.getPreferences();
        JPanel container = parentCell.getBody();

        // The body keeps a thin margin so SDisplayUtil.remind()'s flash stays visible
        // behind the rounded prompt bar chrome.
        container.setLayout(new MigLayout("wrap 1, insets 2px 2px 2px 2px, gap 0px!"));
        promptBar.removeAll();
        // wrap 2 : two columns required for btnOK and btnCancel (order honors UI_REVERSE_PROMPT_BUTTON).
        promptBar.setLayout(new MigLayout("wrap 2, gap 4px!, insets 4px 6px 5px 6px"));

        if (prefs.getPrefBoolean(FPref.UI_COMPACT_PROMPT)) { //hide header and use smaller font if compact prompt
            tarMessage.setFont(FSkin.getFont());
        }
        else {
            promptBar.add(header, "span 2, w 10:100%, h min:min");
            tarMessage.setFont(FSkin.getRelativeFont(14));
        }
        lblGames.setText(localizer.getMessage("lblGameSetup"));

        promptBar.add(messageScroller, "span 2, w 10:100%, h 0:100%");

        boolean largerButtons = prefs.getPrefBoolean(FPref.UI_TOUCHSCREEN_OPTIMIZE);
        String constraints = largerButtons ? "w 10:50%, h 40%:40%:60px" : "w 10:50%, hmin 24px";

        // UI_REVERSE_PROMPT_BUTTON: primary action on the right instead of the left.
        final boolean reverse = prefs.getPrefBoolean(FPref.UI_REVERSE_PROMPT_BUTTON);
        final FButton primary = reverse ? btnCancel : btnOK;
        final FButton secondary = reverse ? btnOK : btnCancel;
        promptBar.add(primary, constraints + ", gaptop 4px!");
        promptBar.add(secondary, constraints + ", gaptop 4px!");

        container.add(promptBar, "w 10:100%, h 0:100%");
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#setParentCell()
     */
    @Override
    public void setParentCell(final DragCell cell0) {
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
     * @see forge.gui.framework.IVDoc#getDocumentID()
     */
    @Override
    public EDocID getDocumentID() {
        return EDocID.REPORT_MESSAGE;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getLayoutControl()
     */
    @Override
    public CPrompt getLayoutControl() {
        return controller;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVDoc#getTabLabel()
     */
    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    //========= Retrieval methods
    /** @return {@link javax.swing.JButton} */
    public FButton getBtnOK() {
        return this.btnOK;
    }

    /** @return {@link javax.swing.JButton} */
    public FButton getBtnCancel() {
        return this.btnCancel;
    }

    /** @return {@link javax.swing.JTextArea} */
    public FHtmlViewer getTarMessage() {
        return this.tarMessage;
    }

    /** @return {@link javax.swing.JLabel} */
    public JLabel getLblGames() {
        return this.lblGames;
    }

    /**
     * Rounded, gradient-filled chrome for the prompt bar. Non-opaque so the
     * parent cell's body (and its "incorrect action" flash) stays behind it.
     * The gradient is cached per size to keep {@code paintComponent} allocation-free.
     */
    private static final class PromptBar extends JPanel {
        private static final long serialVersionUID = 1L;

        private GradientPaint cachedFill;
        private SkinColor cachedBorder;
        private int cachedW = -1;
        private int cachedH = -1;

        private PromptBar() {
            setOpaque(false);
            setLayout(new MigLayout("wrap 2, gap 4px!, insets 4px 6px 5px 6px"));
        }

        @Override
        protected void paintComponent(final Graphics g) {
            super.paintComponent(g);

            final int w = getWidth();
            final int h = getHeight();
            if (cachedFill == null || cachedW != w || cachedH != h) {
                final SkinColor base = FSkin.getColor(Colors.CLR_THEME);
                cachedFill = new GradientPaint(0, 0, base.stepColor(-8).getColor(),
                        0, h, base.stepColor(-26).getColor());
                cachedBorder = FSkin.getColor(Colors.CLR_BORDERS).alphaColor(220);
                cachedW = w;
                cachedH = h;
            }

            final Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setPaint(cachedFill);
                g2d.fillRoundRect(0, 0, w, h, BAR_CORNER, BAR_CORNER);
                FSkin.setGraphicsColor(g2d, cachedBorder);
                g2d.drawRoundRect(0, 0, w - 1, h - 1, BAR_CORNER, BAR_CORNER);
            } finally {
                g2d.dispose();
            }
        }
    }

    /**
     * Arena-style prompt button: the primary action gets the theme's accent fill,
     * the secondary action a quiet outlined fill. Replaces the skin's button sprite
     * painting; sizing, focus and keyboard behavior are inherited from {@link FButton}.
     */
    private static final class PromptButton extends FButton {
        private static final long serialVersionUID = 1L;

        private static final int MODE_DISABLED = 0;
        private static final int MODE_NORMAL = 1;
        private static final int MODE_HOVER = 2;
        private static final int MODE_PRESSED = 3;

        private static final AlphaComposite FADED = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f);

        private final boolean primary;
        private boolean hovered;
        private boolean pressed;

        private GradientPaint cachedFill;
        private SkinColor cachedBorder;
        private int cachedMode = -1;
        private int cachedW = -1;
        private int cachedH = -1;

        private PromptButton(final String label, final boolean primary0) {
            super(label);
            this.primary = primary0;
            setForeground(primary
                    ? FSkin.getColor(Colors.CLR_ACTIVE).getHighContrastColor().getColor()
                    : FSkin.getColor(Colors.CLR_TEXT).getColor());
            if (!primary) {
                setFont(FSkin.getRelativeFont(13));
            }
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(final MouseEvent e) {
                    hovered = true;
                    repaintSelf();
                }

                @Override
                public void mouseExited(final MouseEvent e) {
                    hovered = false;
                    pressed = false;
                    repaintSelf();
                }

                @Override
                public void mousePressed(final MouseEvent e) {
                    pressed = true;
                    repaintSelf();
                }

                @Override
                public void mouseReleased(final MouseEvent e) {
                    pressed = false;
                    repaintSelf();
                }
            });
        }

        private int mode() {
            if (!isEnabled()) { return MODE_DISABLED; }
            if (pressed) { return MODE_PRESSED; }
            if (hovered) { return MODE_HOVER; }
            return MODE_NORMAL;
        }

        private void rebuild(final int mode, final int w, final int h) {
            final SkinColor accent = FSkin.getColor(Colors.CLR_ACTIVE);
            final SkinColor base;
            final int top;
            final int bottom;
            if (primary) {
                switch (mode) {
                    case MODE_DISABLED: base = accent; top = -26; bottom = -40; break;
                    case MODE_HOVER:    base = accent; top = 12;  bottom = -10; break;
                    case MODE_PRESSED:  base = accent; top = -12; bottom = -34; break;
                    default:            base = accent; top = 4;   bottom = -22; break;
                }
                cachedBorder = mode == MODE_DISABLED ? accent.stepColor(-40) : accent.stepColor(38);
            } else {
                base = FSkin.getColor(Colors.CLR_THEME);
                switch (mode) {
                    case MODE_DISABLED: top = -14; bottom = -20; break;
                    case MODE_HOVER:    top = -12; bottom = -30; break;
                    case MODE_PRESSED:  top = -30; bottom = -44; break;
                    default:            top = -18; bottom = -34; break;
                }
                cachedBorder = mode == MODE_DISABLED
                        ? FSkin.getColor(Colors.CLR_BORDERS).alphaColor(70)
                        : FSkin.getColor(Colors.CLR_BORDERS);
            }
            cachedFill = new GradientPaint(0, 0, base.stepColor(top).getColor(), 0, h, base.stepColor(bottom).getColor());
        }

        @Override
        protected void paintComponent(final Graphics g) {
            final Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            final int w = getWidth();
            final int h = getHeight();
            final int mode = mode();
            if (cachedFill == null || cachedMode != mode || cachedW != w || cachedH != h) {
                rebuild(mode, w, h);
                cachedMode = mode;
                cachedW = w;
                cachedH = h;
            }

            final Graphics2D fill = (Graphics2D) g2d.create();
            try {
                if (mode == MODE_DISABLED) {
                    fill.setComposite(FADED);
                }
                fill.setPaint(cachedFill);
                fill.fillRoundRect(0, 0, w, h, BUTTON_CORNER, BUTTON_CORNER);
                FSkin.setGraphicsColor(fill, cachedBorder);
                fill.drawRoundRect(0, 0, w - 1, h - 1, BUTTON_CORNER, BUTTON_CORNER);
                if (mode != MODE_DISABLED && isFocusOwner()) {
                    FSkin.setGraphicsColor(fill, FSkin.getColor(Colors.CLR_TEXT).alphaColor(110));
                    fill.drawRoundRect(1, 1, w - 3, h - 3, BUTTON_CORNER, BUTTON_CORNER);
                }
            } finally {
                fill.dispose();
            }

            // Render the button's contents (text, icon, focus ring) via the current
            // L&F. We bypass FButton.paintComponent because it draws the skin's
            // sprite-based chrome, which would sit on top of our flat fill.
            getUI().paint(g2d, this);
        }
    }
}
