/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Nate
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.BasicStroke;
import java.awt.Stroke;
import java.util.HashSet;
import java.util.Set;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import forge.CachedCardImage;
import forge.game.GameView;
import forge.game.card.CardView.CardStateView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.gamemodes.match.YieldUpdate;
import forge.gui.card.CardDetailUtil;
import forge.gui.card.CardDetailUtil.DetailColors;
import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.gui.framework.IVDoc;
import forge.interfaces.IGameController;
import forge.player.AutoYieldStore.TriggerDecision;
import forge.screens.match.controllers.CDock.ArcState;
import forge.screens.match.controllers.CStack;
import forge.toolbox.FMouseAdapter;
import forge.toolbox.FScrollPanel;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinnedTextArea;
import forge.util.Localizer;
import forge.util.collect.FCollectionView;
import forge.view.arcane.util.Easing;
import forge.view.arcane.util.UIAnimator;
import net.miginfocom.swing.MigLayout;

/**
 * Assembles Swing components of stack report.
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */
public class VStack implements IVDoc<CStack> {

    // Fields used with interface IVDoc
    private DragCell parentCell;
    private final DragTab tab = new DragTab(Localizer.getInstance().getMessage("lblStack"));

    // Top-level containers
    private final FScrollPanel scroller = new FScrollPanel(new MigLayout("insets 0, gap 0, wrap"), true,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    // Other fields
    private final AbilityMenu abilityMenu = new AbilityMenu();

    // dashed outline of a stack entry that can be picked as a target; a picked entry gets a solid red one
    private static final Color SELECTABLE_COLOR = new Color(255, 215, 0);

    private StackInstanceTextArea hoveredItem;

    public StackInstanceTextArea getHoveredItem() {
        return hoveredItem;
    }

    // Stack "drawer" entry animation: items slide up and fade in as they are added.
    private static final int ENTRY_DURATION_MILLIS = 240;
    private static final int ENTRY_STAGGER_MILLIS = 40;
    private static final int ENTRY_SLIDE_PX = 22;
    private static final int MAX_STAGGER_ITEMS = 8;

    // Tracks whether the drawer is currently showing a non-empty stack, so the
    // first batch of items after an empty state is played as a group "open".
    private boolean drawerOpen;
    // Stack item ids that have already been animated this match, so items that
    // persist across repaints are not re-animated on every stack change.
    private final Set<Integer> entryAnimated = new HashSet<>();

    private final CStack controller;
    public VStack(final CStack controller) {
        this.controller = controller;
    }

    @Override
    public void populate() {
        parentCell.getBody().setLayout(new MigLayout("insets 3px, gap 0"));
        parentCell.getBody().add(scroller, "grow, push");
    }

    @Override
    public void setParentCell(final DragCell cell0) {
        parentCell = cell0;
    }

    @Override
    public DragCell getParentCell() {
        return parentCell;
    }

    @Override
    public EDocID getDocumentID() {
        return EDocID.REPORT_STACK;
    }

    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    @Override
    public CStack getLayoutControl() {
        return controller;
    }

    public void updateStack() {
        final GameView model = controller.getMatchUI().getGameView();

        if (model == null) {
            return;
        }

        final FCollectionView<StackItemView> items = model.getStack();
        tab.setText(Localizer.getInstance().getMessage("lblStack") + " : " + items.size());

        // No need to update the rest unless it's showing
        if (parentCell == null || !parentCell.getSelected().equals(this)) { return; }

        hoveredItem = null;
        scroller.removeAll();

        // The drawer slides open when it goes from empty to non-empty: the whole
        // batch of items is (re)played as a group. After that only newly cast
        // items animate, so entries that persist are not re-animated per repaint.
        final boolean opening = !drawerOpen && !items.isEmpty();
        if (opening || (drawerOpen && items.isEmpty())) {
            entryAnimated.clear();
        }
        final boolean animate = UIAnimator.currentMode() != UIAnimator.Mode.OFF;
        int newEntryCount = 0;

        final Iterable<StackItemView> safeItems = controller.getMatchUI().isNetGame()
                ? items.threadSafeIterable() : items;
        boolean isFirst = true;
        for (final StackItemView item : safeItems) {
            final StackInstanceTextArea tar = new StackInstanceTextArea(item);

            scroller.add(tar, "pushx, growx" + (isFirst ? "" : ", gaptop 1px"));

            final int id = item.getId();
            if (animate && (opening || !entryAnimated.contains(id))) {
                animateEntry(tar, newEntryCount++);
            } else {
                tar.setEntryState(1f, 0f);
            }
            entryAnimated.add(id);

            //update the Card Picture/Detail when the spell is added to the stack
            if (isFirst) {
                isFirst = false;
                controller.getMatchUI().setCard(item.getSourceCard());
            }
        }

        drawerOpen = !items.isEmpty();

        scroller.revalidate();
        scroller.repaint();

        SwingUtilities.invokeLater(scroller::scrollToTop);
    }

    /**
     * Plays the per-item drawer entry: the entry starts faded and offset below its
     * resting spot, then rises and fades in. The {@code staggerIndex} delays later
     * items slightly so a batch reads as a drawer opening. Purely paint-only — the
     * entry's layout bounds and hit-testing never change.
     *
     * @param tar          the entry to animate
     * @param staggerIndex zero-based order among the entries added in this batch
     */
    private static void animateEntry(final StackInstanceTextArea tar, final int staggerIndex) {
        tar.setEntryState(0f, ENTRY_SLIDE_PX);
        final int stagger = Math.min(staggerIndex, MAX_STAGGER_ITEMS) * ENTRY_STAGGER_MILLIS;
        final int total = ENTRY_DURATION_MILLIS + stagger;
        final Object key = "stack-entry-" + System.identityHashCode(tar);
        UIAnimator.startRaw(total, key, progress -> {
            float local = ((float) total * progress - stagger) / ENTRY_DURATION_MILLIS;
            if (local <= 0f) {
                return; // still in this item's stagger hold-out
            }
            if (local > 1f) {
                local = 1f;
            }
            final float eased = Easing.cubicOut(local);
            tar.setEntryState(eased, (1f - eased) * ENTRY_SLIDE_PX);
        });
    }

    /** Repaints the entries, for when a stack target was marked, picked or unmarked (the outline is painted per entry). */
    public void repaintSelection() {
        scroller.repaint();
    }

    @SuppressWarnings("serial")
    public class StackInstanceTextArea extends SkinnedTextArea {
        public static final int PADDING = 3;
        public static final int CARD_WIDTH = 50;
        public static final int CARD_HEIGHT = 70;//Math.round((float)CARD_WIDTH * CardPanel.ASPECT_RATIO);

        private final StackItemView item;
        private final CachedCardImage cachedImage;

        // Paint-only drawer entry state; layout bounds and hit-testing are untouched.
        private float entryAlpha = 1f;
        private float entryOffsetY;

        void setEntryState(final float alpha0, final float offsetY0) {
            if (this.entryAlpha == alpha0 && this.entryOffsetY == offsetY0) {
                return;
            }
            this.entryAlpha = alpha0;
            this.entryOffsetY = offsetY0;
            repaint();
        }

        public StackItemView getItem() {
            return item;
        }

        @Override
        public Point getLocationOnScreen() {
            try {
                return super.getLocationOnScreen();
            } catch (final Exception e) {
                //suppress exception that can occur if stack hidden while over an item
                if (hoveredItem == this) {
                    hoveredItem = null; //reset this if this happens
                }
                return null;
            }
        }

        public StackInstanceTextArea(final StackItemView item0) {
            item = item0;

            final String txt = (item.isOptionalTrigger() && controller.getMatchUI().isLocalPlayer(item.getActivatingPlayer())
                    ? "(OPTIONAL) " : "") + item.getText();

            setText(txt);
            setOpaque(true);
            setBorder(new EmptyBorder(PADDING, CARD_WIDTH + 2 * PADDING, PADDING, PADDING));
            setFocusable(false);
            setEditable(false);
            setLineWrap(true);
            setFont(FSkin.getFont());
            setWrapStyleWord(true);
            setMinimumSize(new Dimension(CARD_WIDTH + 2 * PADDING, CARD_HEIGHT + 2 * PADDING));
            
            // if the top of the stack is not assigned yet...
            if (hoveredItem == null)
            {
            	// set things up to draw an arc from it...
                hoveredItem = StackInstanceTextArea.this;
                controller.getMatchUI().setCard(item.getSourceCard());
            }

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(final MouseEvent e) {
                    if (controller.getMatchUI().getCDock().getArcState() == ArcState.MOUSEOVER)	{
                        hoveredItem = StackInstanceTextArea.this;
                    }
                    controller.getMatchUI().setCard(item.getSourceCard());

                }

                @Override
                public void mouseExited(final MouseEvent e) {
                    if (controller.getMatchUI().getCDock().getArcState() == ArcState.MOUSEOVER)	{
                        if (hoveredItem == StackInstanceTextArea.this) {
                            hoveredItem = null;
                        }
                    }
                }
                
                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (controller.getMatchUI().getCDock().getArcState() == ArcState.ON) {
                        if (hoveredItem == StackInstanceTextArea.this) {
                            hoveredItem = null;
                        }
                        else
                        {
                            hoveredItem = StackInstanceTextArea.this;
                            controller.getMatchUI().setCard(item.getSourceCard());
                        }
                    }
                }
            });

            addMouseListener(new FMouseAdapter() {
                @Override
                public void onLeftClick(final MouseEvent e) {
                    if (controller.getMatchUI().isSelectable(item)) { // a stack target is being chosen: a click picks (or unpicks) it
                        controller.getMatchUI().pickStackItem(item);
                        return;
                    }
                    onClick(e);
                }
                @Override
                public void onRightClick(final MouseEvent e) {
                    onClick(e);
                }
                private void onClick(final MouseEvent e) {
                    abilityMenu.setStackInstance(item);
                    boolean hasVisibleItem = false;
                    for (Component c : abilityMenu.getComponents()) {
                        if (c.isVisible()) {
                            hasVisibleItem = true;
                            break;
                        }
                    }
                    if (!hasVisibleItem) {
                        return;
                    }
                    abilityMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            });

            // TODO: A hacky workaround is currently used to make the game not leak the color information for Morph cards.
            final CardStateView curState = item.getSourceCard().getCurrentState();
            final boolean isFaceDown = item.getSourceCard().isFaceDown();
            final DetailColors color = isFaceDown ? CardDetailUtil.DetailColors.FACE_DOWN : CardDetailUtil.getBorderColor(curState, true); // otherwise doesn't work correctly for face down Morphs
            setBackground(new Color(color.r, color.g, color.b));
            setForeground(FSkin.getHighContrastColor(getBackground()));
            
            this.cachedImage = new CachedCardImage(item.getSourceCard(), controller.getMatchUI().getLocalPlayers(), CARD_WIDTH, CARD_HEIGHT) {
                @Override
                public void onImageFetched() {
                    repaint();
                }
            };
        }

        @Override
        public void paintComponent(final Graphics g) {
            final boolean animating = entryAlpha < 1f || entryOffsetY != 0f;
            final Graphics2D g2d = animating ? (Graphics2D) g.create() : (Graphics2D) g;
            if (animating) {
                if (entryAlpha < 1f) {
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, entryAlpha));
                }
                if (entryOffsetY != 0f) {
                    g2d.translate(0f, entryOffsetY);
                }
            }

            super.paintComponent(g2d);

            //draw image for source card
            final BufferedImage img = cachedImage.getImage();
            if (img != null) {
                g2d.drawImage(img, null, PADDING, PADDING);
            }

            //outline the entry while it can be picked as a target, and once it is picked
            final boolean picked = controller.getMatchUI().isHighlighted(item);
            if (picked || controller.getMatchUI().isSelectable(item)) {
                final Stroke oldStroke = g2d.getStroke();
                g2d.setColor(picked ? Color.RED : SELECTABLE_COLOR);
                g2d.setStroke(picked ? new BasicStroke(3f)
                        : new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {8f, 6f}, 0f));
                g2d.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                g2d.setStroke(oldStroke);
            }

            if (animating) {
                g2d.dispose();
            }
        }
    }

    //========= Custom class handling

    private final class AbilityMenu extends JPopupMenu {
        private static final long serialVersionUID = 1548494191627807962L;
        private final JCheckBoxMenuItem jmiAutoYield;
        private final JCheckBoxMenuItem jmiAlwaysYes;
        private final JCheckBoxMenuItem jmiAlwaysNo;
        private final JMenuItem jmiYieldToStack;
        private final JMenuItem jmiYieldToEntireStack;
        private String yieldKey = "";
        private boolean abilityScope;

        public AbilityMenu(){
            jmiAutoYield = new JCheckBoxMenuItem(Localizer.getInstance().getMessage("cbpAutoYieldMode"));
            jmiAutoYield.addActionListener(arg0 -> {
                final boolean autoYield = controller.getMatchUI().getGameController().shouldAutoYield(yieldKey);
                controller.getMatchUI().getGameController().setShouldAutoYield(yieldKey, !autoYield, abilityScope);
            });
            add(jmiAutoYield);

            jmiAlwaysYes = new JCheckBoxMenuItem(Localizer.getInstance().getMessage("lblAlwaysYes"));
            jmiAlwaysYes.addActionListener(arg0 -> {
                if (yieldKey.isEmpty()) return;
                IGameController gc = controller.getMatchUI().getGameController();
                TriggerDecision next = gc.getTriggerDecision(yieldKey) == TriggerDecision.ACCEPT ? TriggerDecision.ASK : TriggerDecision.ACCEPT;
                gc.setTriggerDecision(yieldKey, next, abilityScope);
            });
            add(jmiAlwaysYes);

            jmiAlwaysNo = new JCheckBoxMenuItem(Localizer.getInstance().getMessage("lblAlwaysNo"));
            jmiAlwaysNo.addActionListener(arg0 -> {
                if (yieldKey.isEmpty()) return;
                IGameController gc = controller.getMatchUI().getGameController();
                TriggerDecision next = gc.getTriggerDecision(yieldKey) == TriggerDecision.DECLINE ? TriggerDecision.ASK : TriggerDecision.DECLINE;
                gc.setTriggerDecision(yieldKey, next, abilityScope);
            });
            add(jmiAlwaysNo);

            jmiYieldToStack = new JMenuItem(Localizer.getInstance().getMessage("lblYieldToStack"));
            jmiYieldToStack.addActionListener(arg0 -> {
                final PlayerView local = controller.getMatchUI().getCurrentPlayer();
                if (local == null) return;
                controller.getMatchUI().getGameController().sendYieldUpdate(new YieldUpdate.StackYield(local, true, true));
            });
            add(jmiYieldToStack);

            jmiYieldToEntireStack = new JMenuItem(Localizer.getInstance().getMessage("lblYieldToEntireStack"));
            jmiYieldToEntireStack.addActionListener(arg0 -> {
                final PlayerView local = controller.getMatchUI().getCurrentPlayer();
                if (local == null) return;
                controller.getMatchUI().getGameController().sendYieldUpdate(new YieldUpdate.StackYield(local, true, false));
            });
            add(jmiYieldToEntireStack);
        }

        public void setStackInstance(final StackItemView item0) {
            yieldKey = item0.getKey();
            abilityScope = controller.getMatchUI().getGameController().getYieldController().isAbilityScope();

            jmiAutoYield.setVisible(item0.isAbility());
            jmiAutoYield.setSelected(item0.isAbility()
                    && controller.getMatchUI().getGameController().shouldAutoYield(yieldKey));

            if (item0.isOptionalTrigger() && controller.getMatchUI().isLocalPlayer(item0.getActivatingPlayer()) && !yieldKey.isEmpty()) {
                TriggerDecision decision = controller.getMatchUI().getGameController().getTriggerDecision(yieldKey);
                jmiAlwaysYes.setSelected(decision == TriggerDecision.ACCEPT);
                jmiAlwaysNo.setSelected(decision == TriggerDecision.DECLINE);
                jmiAlwaysYes.setVisible(true);
                jmiAlwaysNo.setVisible(true);
            } else {
                jmiAlwaysYes.setVisible(false);
                jmiAlwaysNo.setVisible(false);
            }
        }
    }
}
