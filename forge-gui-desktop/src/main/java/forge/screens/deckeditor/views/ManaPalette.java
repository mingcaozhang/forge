package forge.screens.deckeditor.views;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinImage;

/**
 * Resolves a representative fill {@link Color} for a skin icon so that the deck
 * editor statistics charts (mana curve, color and type distribution) can be
 * painted with colors that exactly match the currently loaded skin.
 *
 * <p>
 * The color is derived by rendering the skin image into a small off-screen
 * buffer and averaging the opaque pixels, so it tracks both mana symbols and
 * card-type glyphs without hard-coding palette values. Results are cached per
 * {@link FSkinProp}.
 *
 * <br><br><i>(Purely a view/painting helper — no game or deck logic is
 * involved.)</i>
 */
final class ManaPalette {
    private static final int SAMPLE_SIZE = 24;
    private static final int MIN_ALPHA = 190;
    private static final Map<FSkinProp, Color> CACHE = new HashMap<>();

    private ManaPalette() {
    }

    /**
     * @param prop0 the skin image property to sample
     * @return a solid color representing the icon, falling back to the theme
     *         text color if the image cannot be sampled
     */
    static Color colorFor(final FSkinProp prop0) {
        return CACHE.computeIfAbsent(prop0, ManaPalette::sampleColor);
    }

    private static Color sampleColor(final FSkinProp prop0) {
        final Color fallback = FSkin.getColor(FSkin.Colors.CLR_TEXT).getColor();
        try {
            final SkinImage image = FSkin.getImage(prop0);
            final BufferedImage buffer = new BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g2d = buffer.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                FSkin.drawImage(g2d, image, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE);
            } finally {
                g2d.dispose();
            }

            long red = 0;
            long green = 0;
            long blue = 0;
            long count = 0;
            for (int y = 0; y < SAMPLE_SIZE; y++) {
                for (int x = 0; x < SAMPLE_SIZE; x++) {
                    final int pixel = buffer.getRGB(x, y);
                    final int alpha = (pixel >>> 24) & 0xFF;
                    if (alpha < MIN_ALPHA) {
                        continue;
                    }
                    red += (pixel >> 16) & 0xFF;
                    green += (pixel >> 8) & 0xFF;
                    blue += pixel & 0xFF;
                    count++;
                }
            }

            if (count == 0) {
                return fallback;
            }
            return new Color((int) (red / count), (int) (green / count), (int) (blue / count));
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
