package com.example.lectormobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * SizeLruCache: byte-budget LRU used to keep decoded cover bitmaps in memory
 * (optimization S2, v1.4.3) so scrolling the library does not re-decode
 * covers on every bind.
 */
public class SizeLruCacheTest {

    /** Weights entries by a fixed value stored alongside the key. */
    private SizeLruCache<String, int[]> newCache(int maxSize) {
        return new SizeLruCache<>(maxSize, v -> v[0]);
    }

    @Test
    public void putAndGetReturnsValue() {
        SizeLruCache<String, int[]> cache = newCache(100);
        cache.put("a", new int[]{10});
        assertEquals(10, cache.get("a")[0]);
        assertNull(cache.get("missing"));
    }

    @Test
    public void evictsLeastRecentlyUsedWhenOverBudget() {
        SizeLruCache<String, int[]> cache = newCache(100);
        cache.put("a", new int[]{40});
        cache.put("b", new int[]{40});
        cache.put("c", new int[]{40}); // 120 > 100: eldest ("a") goes

        assertNull(cache.get("a"));
        assertEquals(40, cache.get("b")[0]);
        assertEquals(40, cache.get("c")[0]);
        assertEquals(80, cache.size());
        assertEquals(2, cache.count());
    }

    @Test
    public void getRefreshesRecency() {
        SizeLruCache<String, int[]> cache = newCache(100);
        cache.put("a", new int[]{40});
        cache.put("b", new int[]{40});
        cache.get("a");               // "a" is now the most recently used

        cache.put("c", new int[]{40}); // evicts "b", the least recently used
        assertNull(cache.get("b"));
        assertEquals(40, cache.get("a")[0]);
        assertEquals(40, cache.get("c")[0]);
    }

    @Test
    public void valueLargerThanBudgetIsNeverCached() {
        SizeLruCache<String, int[]> cache = newCache(100);
        cache.put("huge", new int[]{500});
        assertNull(cache.get("huge"));
        assertEquals(0, cache.size());
        assertEquals(0, cache.count());
    }

    @Test
    public void replacingKeyKeepsSizeAccountingAccurate() {
        SizeLruCache<String, int[]> cache = newCache(100);
        cache.put("a", new int[]{60});
        cache.put("a", new int[]{30}); // same key, smaller value
        assertEquals(30, cache.size());
        assertEquals(1, cache.count());

        cache.put("b", new int[]{70}); // 100 total: fits, nothing evicted
        assertEquals(100, cache.size());
        assertEquals(2, cache.count());
    }

    @Test
    public void removeDeletesEntry() {
        SizeLruCache<String, int[]> cache = newCache(100);
        cache.put("a", new int[]{50});
        cache.remove("a");
        assertNull(cache.get("a"));
        assertEquals(0, cache.size());
        assertEquals(0, cache.count());
    }

    @Test
    public void sizeNeverExceedsBudgetAcrossManyEntries() {
        SizeLruCache<String, int[]> cache = newCache(64);
        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, new int[]{8});
            org.junit.Assert.assertTrue(cache.size() <= 64);
        }
        assertEquals(8, cache.count()); // 64 / 8
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveBudgetIsRejected() {
        new SizeLruCache<String, int[]>(0, v -> v[0]);
    }

    @Test
    public void evictAllClearsCache() {
        SizeLruCache<String, int[]> cache = newCache(100);
        cache.put("a", new int[]{20});
        cache.put("b", new int[]{30});
        assertEquals(50, cache.size());
        assertEquals(2, cache.count());

        cache.evictAll();
        assertEquals(0, cache.size());
        assertEquals(0, cache.count());
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
    }
}
