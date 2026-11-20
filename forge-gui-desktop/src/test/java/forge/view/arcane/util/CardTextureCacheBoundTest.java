package forge.view.arcane.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.testng.annotations.Test;

/**
 * Phase 6 guard for the {@link CardTextureCache} hard bound: under a large
 * stream of distinct keys (well over 1000), and under mixed read traffic that
 * re-orders the access-ordered LRU, the cache must never retain more entries
 * than its configured maximum and must settle exactly on that bound once
 * over-filled.
 *
 * <p>
 * A strong {@link List} keeps every stored image reachable so the
 * {@link java.lang.ref.SoftReference}-wrapped values are never reclaimed by the
 * GC; the only way the map can shrink is the LRU eviction policy, which makes
 * the bound assertions deterministic.
 * </p>
 */
public class CardTextureCacheBoundTest {

    private static BufferedImage image() {
        return new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    public void neverExceedsConfiguredBoundOverManyDistinctKeys() {
        final int cap = 64;
        final int insertions = 1_500; // comfortably over 1000 distinct keys
        final CardTextureCache cache = new CardTextureCache(cap);
        assertEquals(cache.getMaxEntries(), cap);

        final List<BufferedImage> keptAlive = new ArrayList<>(insertions);
        for (int i = 0; i < insertions; i++) {
            final BufferedImage img = image();
            keptAlive.add(img);
            cache.put(CardTextureCache.textureKey("Card" + i, 8, 8, true, false, 0), img);
            assertTrue(cache.size() <= cap,
                    "cache size must never exceed the configured bound " + cap + " (got " + cache.size() + ")");
        }
        assertEquals(cache.size(), cap, "cache should sit exactly at its bound after over-filling");
    }

    @Test
    public void boundHoldsUnderMixedReadChurn() {
        final int cap = 128;
        final int insertions = 1_200; // comfortably over 1000 distinct keys
        final CardTextureCache cache = new CardTextureCache(cap);

        final List<BufferedImage> keptAlive = new ArrayList<>(insertions);
        final List<String> keys = new ArrayList<>(insertions);
        final Random random = new Random(0x5EEDL);

        for (int i = 0; i < insertions; i++) {
            final String key = CardTextureCache.textureKey("Churn" + i, 4, 4, i % 2 == 0, false, 0);
            final BufferedImage img = image();
            keptAlive.add(img);
            cache.put(key, img);
            keys.add(key);

            // Occasionally re-read an earlier key to exercise access-order re-ordering.
            if (random.nextInt(4) == 0) {
                cache.get(keys.get(random.nextInt(keys.size())));
            }
            assertTrue(cache.size() <= cap,
                    "cache size must never exceed the configured bound " + cap + " under read churn");
        }
        assertEquals(cache.size(), cap, "cache should sit exactly at its bound after churn over-filling");
    }
}
