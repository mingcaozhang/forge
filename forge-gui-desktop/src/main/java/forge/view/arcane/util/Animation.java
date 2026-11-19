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
package forge.view.arcane.util;

import java.awt.Container;
import java.awt.EventQueue;

import javax.swing.JLayeredPane;

import forge.view.arcane.CardPanel;

/**
 * <p>
 * Animation class. Provides useful static methods for animating movement of
 * cards.
 * </p>
 * 
 * @author Forge
 * @version $Id: Animation.java 24769 2014-02-09 13:56:04Z Hellfish $
 */
public abstract class Animation {
    private final long duration;

    /**
     * Constructor for Animation.
     * 
     * @param duration
     *            the duration, in milliseconds, for which the animation will
     *            run.
     */
    private Animation(final long duration) {
        this.duration = duration;
    }

    /**
     * Starts the animation by delegating to the shared {@link UIAnimator}
     * tween engine, preserving this animation's caller-specified duration.
     */
    protected final void run() {
        UIAnimator.startRaw((int) duration, new UIAnimator.Tween() {
            private boolean started;

            @Override
            public void onFrame(final float eased) {
                if (!started) {
                    started = true;
                    Animation.this.onStart();
                }
                Animation.this.update(eased);
            }

            @Override
            public void onEnd() {
                Animation.this.onEnd();
            }
        });
    }

    /**
     * Called each animation frame while the animation is running.
     * 
     * @param percentage
     *            the eased progress in {@code [0, 1]}.
     */
    protected abstract void update(float percentage);

    /**
     * Executed when the animation starts.
     */
    protected void onStart() {
    }

    /**
     * Executed when the animation ends.
     */
    protected void onEnd() {
    }

    /**
     * <p>
     * invokeLater.
     * </p>
     * 
     * @param runnable
     *            a {@link java.lang.Runnable} object.
     */
    private static void invokeLater(final Runnable runnable) {
        EventQueue.invokeLater(runnable);
    }

    /**
     * <p>
     * tapCardToggle.
     * </p>
     * 
     * @param panel
     *            a {@link forge.view.arcane.CardPanel} object.
     */
    public static void tapCardToggle(final CardPanel panel) {
        new Animation(200) {
            @Override
            protected void onStart() {
                panel.setTapped(!panel.isTapped());
            }

            @Override
            protected void update(final float percentage) {
                panel.setTappedAngle(CardPanel.TAPPED_ANGLE * percentage);
                if (!panel.isTapped()) {
                    panel.setTappedAngle(CardPanel.TAPPED_ANGLE - panel.getTappedAngle());
                }
                panel.repaint();
            }

            @Override
            protected void onEnd() {
                panel.setTappedAngle(panel.isTapped() ? CardPanel.TAPPED_ANGLE : 0);
            }
        }.run();
    }

    /**
     * Animate a {@link CardPanel} moving.
     * 
     * @param startX
     *            a int.
     * @param startY
     *            a int.
     * @param startWidth
     *            a int.
     * @param endX
     *            a int.
     * @param endY
     *            a int.
     * @param endWidth
     *            a int.
     * @param animationPanel
     *            a {@link forge.view.arcane.CardPanel} object.
     * @param placeholder
     *            a {@link forge.view.arcane.CardPanel} object.
     * @param layeredPane
     *            a {@link javax.swing.JLayeredPane} object.
     * @param speed
     *            a int.
     */
    public static void moveCard(final int startX, final int startY, final int startWidth, final int endX,
            final int endY, final int endWidth, final CardPanel animationPanel, final CardPanel placeholder,
            final JLayeredPane layeredPane, final int speed) {
        Animation.invokeLater(() -> {
            final int startHeight = Math.round(startWidth * CardPanel.ASPECT_RATIO);
            final int endHeight = Math.round(endWidth * CardPanel.ASPECT_RATIO);

            animationPanel.setCardBounds(startX, startY, startWidth, startHeight);
            animationPanel.setAnimationPanel(true);
            Container parent = animationPanel.getParent();
            if (parent != layeredPane) {
                layeredPane.add(animationPanel);
                layeredPane.setLayer(animationPanel, JLayeredPane.MODAL_LAYER);
            }

            new Animation(speed) {
                @Override
                protected void update(final float percentage) {
                    int currentX = startX + Math.round((endX - startX) * percentage);
                    int currentY = startY + Math.round((endY - startY) * percentage);
                    int currentWidth = startWidth + Math.round((endWidth - startWidth) * percentage);
                    int currentHeight = startHeight + Math.round((endHeight - startHeight) * percentage);
                    animationPanel.setCardBounds(currentX, currentY, currentWidth, currentHeight);
                }

                @Override
                protected void onEnd() {
                    EventQueue.invokeLater(() -> {
                        if (placeholder != null) {
                            placeholder.setDisplayEnabled(true);
                            placeholder.setCard(placeholder.getCard());
                        }
                        animationPanel.setVisible(false);
                        animationPanel.repaint();
                        layeredPane.remove(animationPanel);
                        if (animationPanel != CardPanel.getDragAnimationPanel()) {
                            animationPanel.dispose();
                        }
                    });
                }
            }.run();
        });
    }

    /**
     * Animate a {@link CardPanel} moving.
     * 
     * @param placeholder
     *            a {@link forge.view.arcane.CardPanel} object.
     */
    public static void moveCard(final CardPanel placeholder) {
        Animation.invokeLater(() -> {
            if (placeholder != null) {
                placeholder.setDisplayEnabled(true);
                // placeholder.setImage(imagePanel);
                placeholder.setCard(placeholder.getCard());
            }
        });
    }

}
