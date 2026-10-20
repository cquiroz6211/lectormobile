package com.example.lectormobile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

/**
 * Disk cache of extracted EPUB directories (optimization S1, v1.4.3).
 *
 * <p>Opening an EPUB used to unzip the whole file into {@code cacheDir} on
 * every single open. This cache keeps one extracted directory per book under
 * {@code filesDir/epub_cache} (so it survives the system clearing app cache),
 * keyed by the book's size + last-modified time, and reuses it on later opens.
 *
 * <p>Only derived data lives here: the directory is marked complete with a
 * {@code .ok} marker written after a successful unzip, incomplete extractions
 * are discarded and redone, and the cache never holds more than
 * {@link #MAX_ENTRIES} entries (least recently used first). It never touches
 * the book files themselves or any user data.
 *
 * <p>Not thread-safe: use one instance from one background thread at a time.
 */
public class ExtractionCache {

    /** Marker file written only after the unzip finished successfully. */
    public static final String MARKER = ".ok";

    /** Maximum number of extracted books kept on disk (LRU by last use). */
    public static final int MAX_ENTRIES = 6;

    private final File root;

    public ExtractionCache(File root) {
        this.root = root;
    }

    /**
     * Cache key for a book: changes whenever the file content changes
     * (size or modification time), so a re-imported book is never matched
     * against a stale extraction.
     */
    public String keyFor(File book) {
        return Long.toHexString(book.length()) + "_"
                + Long.toHexString(book.lastModified());
    }

    /**
     * The existing, complete extraction for this book, or {@code null}.
     * A hit refreshes the entry's last-use time for LRU ordering; a hit on an
     * incomplete directory (interrupted extraction) deletes it and returns
     * {@code null} so the caller extracts again.
     */
    public File getValidDir(File book) {
        File dir = new File(root, keyFor(book));
        if (dir.isDirectory() && isComplete(dir)) {
            // Touch for LRU; best effort, the hit itself is still valid.
            dir.setLastModified(System.currentTimeMillis());
            return dir;
        }
        if (dir.exists()) {
            deleteRecursive(dir);
        }
        return null;
    }

    /**
     * A fresh, empty directory to extract into. Evicts the least recently used
     * entries when the cache is at capacity so it never grows without bound.
     */
    public File newDirFor(File book) {
        root.mkdirs();
        File dir = new File(root, keyFor(book));
        if (dir.exists()) {
            deleteRecursive(dir);
        }
        dir.mkdirs();
        evict();
        return dir;
    }

    /** Marks an extraction as complete; call only after a successful unzip. */
    public void markComplete(File dir) throws IOException {
        File marker = new File(dir, MARKER);
        try (FileWriter w = new FileWriter(marker)) {
            w.write("ok");
        }
    }

    /** True when the directory holds a successfully finished extraction. */
    public boolean isComplete(File dir) {
        return new File(dir, MARKER).isFile();
    }

    /** Keeps the {@link #MAX_ENTRIES} most recently used directories. */
    private void evict() {
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null || dirs.length <= MAX_ENTRIES) return;
        Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_ENTRIES; i < dirs.length; i++) {
            deleteRecursive(dirs[i]);
        }
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        // Best effort: a file we cannot delete is retried on the next open.
        file.delete();
    }
}
