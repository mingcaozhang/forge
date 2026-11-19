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

import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * <p>
 * CardTextureCache class. A bounded, thread-safe, access-ordered LRU cache of
 * expensive card-derived {@link BufferedImage}s (rounded frames, scaled art
 * crops, drop-shadow layers and hover-glow layers). Values are wrapped in
 * {@link SoftReference} so the garbage collector can reclaim them under memory
 * pressure; stale entries are pruned opportunistically. Keys encode only visual
 * parameters, never game state, so the card data path stays untouched.
 * </p>
 *
 * @author Forge
 */
public final class CardTextureCache {

    /** Default maximum number of retained texture entries. */
    public static final int DEFAULT_MAX_ENTRIES = 256;

    private final int maxEntries;
    private final LinkedHashMap<String, SoftReference<BufferedImage>> map;

    /**
     * Creates a cache with {@value #DEFAULT_MAX_ENTRIES} entries.
     */
    public CardTextureCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Creates a cache bounded to the given number of entries.
     *
     * @param maxEntries maximum number of entries retained before the
     *                   least-recently-used entry is evicted
     */
    public CardTextureCache(final int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.map = new LinkedHashMap<String, SoftReference<BufferedImage>>(Math.min(maxEntries, 32), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, SoftReference<BufferedImage>> eldest) {
                return size() > CardTextureCache.this.maxEntries;
            }
        };
    }

    /**
     * Builds a cache key from visual parameters only.
     *
     * @param cardName card identifier (never a game object)
     * @param width    texture width in pixels
     * @param height   texture height in pixels
     * @param rounded  whether the rounded-frame variant is requested
     * @param glow     whether the hover-glow variant is requested
     * @param rotation rotation in degrees baked into the texture
     * @return a stable cache key
     */
    public static String textureKey(final String cardName, final int width, final int height,
            final boolean rounded, final boolean glow, final int rotation) {
        return cardName + "|v" + width + "x" + height
                + '|' + (rounded ? 'r' : 'R')
                + '|' + (glow ? 'g' : 'G')
                + '|' + rotation;
    }

    /**
     * Looks up a cached texture.
     *
     * @param key cache key
     * @return the cached image, or {@code null} when absent or reclaimed
     */
    public BufferedImage get(final String key) {
        if (key == null) {
            return null;
        }
        synchronized (map) {
            final SoftReference<BufferedImage> ref = map.get(key);
            if (ref == null) {
                return null;
            }
            final BufferedImage image = ref.get();
            if (image == null) {
                map.remove(key);
                return null;
            }
            return image;
        }
    }

    /**
     * Stores a texture, replacing any previous entry for the key. {@code null}
     * images are ignored so they never poison the cache.
     *
     * @param key   cache key
     * @param image texture to cache
     */
    public void put(final String key, final BufferedImage image) {
        if (key == null || image == null) {
            return;
        }
        synchronized (map) {
            map.put(key, new SoftReference<BufferedImage>(image));
            if (map.size() > maxEntries / 2) {
                purgeCollected();
            }
        }
    }

    /**
     * Returns the cached texture for {@code key}, computing and caching it via
     * {@code loader} on a miss. Concurrent misses for the same key are allowed
     * to compute independently; the last writer wins and correctness is
     * unaffected because loaders are expected to be deterministic.
     *
     * @param key    cache key
     * @param loader factory invoked only on a cache miss
     * @return the cached or freshly computed image (possibly {@code null})
     */
    public BufferedImage getOrCompute(final String key, final Supplier<BufferedImage> loader) {
        final BufferedImage cached = get(key);
        if (cached != null) {
            return cached;
        }
        final BufferedImage computed = loader.get();
        if (computed != null) {
            put(key, computed);
        }
        return computed;
    }

    /**
     * Removes every entry, e.g. after a skin or theme change invalidates baked
     * colors.
     */
    public void clearCache() {
        synchronized (map) {
            map.clear();
        }
    }

    /**
     * @return number of live (non-reclaimed) entries currently in the cache
     */
    public int size() {
        synchronized (map) {
            purgeCollected();
            return map.size();
        }
    }

    /**
     * @return the configured maximum entry count
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    // Must be called while holding the map lock.
    private void purgeCollected() {
        map.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }
}
