package forge.view.arcane.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

/**
 * Guards the Phase 2 {@link CardTextureCache} contract: the cache is bounded
 * (access-ordered LRU evicts the eldest entry at the configured cap) and a
 * {@link CardTextureCache#clearCache()} call forces the next lookup to recompute
 * rather than serve a stale texture.
 */
public class CardTextureCacheTest {

    private static BufferedImage image(final int side) {
        return new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    public void evictsLeastRecentlyUsedBeyondConfiguredCap() {
        final int cap = 4;
        final CardTextureCache cache = new CardTextureCache(cap);
        assertEquals(cache.getMaxEntries(), cap);

        // Keep a strong reference to every image we insert so the SoftReferences
        // are never reclaimed by the GC and the only eviction is the LRU policy.
        final List<BufferedImage> keptAlive = new ArrayList<>();
        final int insertions = cap + 2;
        for (int i = 0; i < insertions; i++) {
            final BufferedImage img = image(8 + i);
            keptAlive.add(img);
            cache.put(CardTextureCache.textureKey("Card" + i, 8 + i, 8 + i, true, false, 0), img);
            assertTrue(cache.size() <= cap,
                    "cache size must never exceed the configured cap (" + cap + ")");
        }

        assertEquals(cache.size(), cap, "cache should sit exactly at its bound after over-filling");

        // The two eldest insertions must have been evicted.
        assertNull(cache.get(CardTextureCache.textureKey("Card0", 8, 8, true, false, 0)),
                "oldest entry should have been evicted");
        assertNull(cache.get(CardTextureCache.textureKey("Card1", 9, 9, true, false, 0)),
                "second-oldest entry should have been evicted");

        // The most recent `cap` entries must all still be present.
        for (int i = insertions - cap; i < insertions; i++) {
            assertNotNull(cache.get(CardTextureCache.textureKey("Card" + i, 8 + i, 8 + i, true, false, 0)),
                    "recently used entry " + i + " should be retained");
        }
    }

    @Test
    public void clearCacheForcesRecompute() {
        final CardTextureCache cache = new CardTextureCache(16);
        final AtomicInteger computes = new AtomicInteger();
        final String key = CardTextureCache.textureKey("Sol Ring", 64, 64, false, true, 90);

        final BufferedImage first = cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return image(64);
        });
        assertNotNull(first);
        assertEquals(computes.get(), 1, "first lookup should compute once");

        final BufferedImage second = cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return image(64);
        });
        assertSame(second, first, "second lookup should be served from the cache");
        assertEquals(computes.get(), 1, "cache hit must not recompute");

        cache.clearCache();
        assertEquals(cache.size(), 0, "clearCache should empty the cache");
        assertNull(cache.get(key), "cleared entry must not be retrievable");

        final BufferedImage third = cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return image(64);
        });
        assertEquals(computes.get(), 2, "clearCache should force exactly one recompute");
        assertNotNull(third);
    }

    @Test
    public void textureKeyIsStableAndParameterSensitive() {
        final String a = CardTextureCache.textureKey("Birds of Paradise", 100, 140, true, false, 0);
        final String b = CardTextureCache.textureKey("Birds of Paradise", 100, 140, true, false, 0);
        assertEquals(a, b, "the same visual parameters must produce the same key");
        assertFalse(a.equals(CardTextureCache.textureKey("Birds of Paradise", 100, 140, false, false, 0)),
                "changing the rounded flag must change the key");
        assertFalse(a.equals(CardTextureCache.textureKey("Birds of Paradise", 100, 140, true, true, 0)),
                "changing the glow flag must change the key");
        assertFalse(a.equals(CardTextureCache.textureKey("Birds of Paradise", 101, 140, true, false, 0)),
                "changing the width must change the key");
    }
}
