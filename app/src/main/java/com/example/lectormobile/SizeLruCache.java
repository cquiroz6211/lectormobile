package com.example.lectormobile;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU map with a fixed budget, where each value weighs a computed number of
 * bytes (optimization S2, v1.4.3).
 *
 * <p>Used by {@code BooksAdapter} to keep decoded cover bitmaps in memory so
 * scrolling the library does not re-decode the same file on every
 * {@code onBindViewHolder}. Pure Java (no Android types) so it is unit
 * testable locally; values are never touched or recycled by the cache, the
 * caller owns their lifecycle.
 *
 * <p>Not thread-safe: synchronize externally when accessing from several
 * threads.
 */
public class SizeLruCache<K, V> {

    /** Computes the weight in bytes of a cached value. */
    public interface Sizer<V> {
        int sizeOf(V value);
    }

    /** Access order: iteration starts at the least recently used entry. */
    private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxSize;
    private final Sizer<V> sizer;
    private int currentSize;

    /**
     * @param maxSize budget in bytes; must be positive
     * @param sizer   weight of each value, treated as non-negative
     */
    public SizeLruCache(int maxSize, Sizer<V> sizer) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0, got " + maxSize);
        }
        this.maxSize = maxSize;
        this.sizer = sizer;
    }

    /** The cached value for {@code key}, or {@code null}. Marks it as used. */
    public synchronized V get(K key) {
        return map.get(key);
    }

    /**
     * Inserts or replaces {@code key}. Evicts least recently used entries until
     * the budget holds. A value heavier than the whole budget is not cached.
     */
    public synchronized void put(K key, V value) {
        if (key == null || value == null) return;
        int valueSize = Math.max(0, sizer.sizeOf(value));
        if (valueSize > maxSize) return;

        V previous = map.put(key, value);
        if (previous != null) {
            currentSize -= Math.max(0, sizer.sizeOf(previous));
        }
        currentSize += valueSize;
        trimToSize();
    }

    /** Drops {@code key} if present. */
    public synchronized void remove(K key) {
        V previous = map.remove(key);
        if (previous != null) {
            currentSize -= Math.max(0, sizer.sizeOf(previous));
        }
    }

    /** Current total weight of the cached entries, in bytes. */
    public synchronized int size() {
        return currentSize;
    }

    /** Number of cached entries. */
    public synchronized int count() {
        return map.size();
    }

    private void trimToSize() {
        Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
        while (currentSize > maxSize && it.hasNext()) {
            Map.Entry<K, V> eldest = it.next();
            it.remove();
            currentSize -= Math.max(0, sizer.sizeOf(eldest.getValue()));
        }
    }
}
