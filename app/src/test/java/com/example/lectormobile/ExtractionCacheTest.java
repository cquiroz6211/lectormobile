package com.example.lectormobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

/**
 * ExtractionCache: reusing extracted EPUB directories across opens so a book
 * is not unzipped again (optimization S1, v1.4.3).
 */
public class ExtractionCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;
    private File books;
    private ExtractionCache cache;

    @Before
    public void setUp() throws IOException {
        root = new File(tmp.getRoot(), "epub_cache");
        books = tmp.newFolder("books");
        cache = new ExtractionCache(root);
    }

    /** A fake book file with a unique size so keys never collide. */
    private File book(int uniqueSize) throws IOException {
        File f = new File(books, "book_" + uniqueSize + ".epub");
        try (FileWriter w = new FileWriter(f)) {
            for (int i = 0; i < uniqueSize; i++) w.write('x');
        }
        f.setLastModified(System.currentTimeMillis() - 100_000L);
        return f;
    }

    private int dirCount() {
        File[] dirs = root.listFiles(File::isDirectory);
        return dirs == null ? 0 : dirs.length;
    }

    @Test
    public void missWhenNothingCached() throws IOException {
        File book = book(16);
        assertNull(cache.getValidDir(book));
    }

    @Test
    public void hitAfterCompleteExtraction() throws IOException {
        File book = book(16);
        File dir = cache.newDirFor(book);
        writeChapter(dir, "ch1.xhtml", "<p>hola</p>");
        cache.markComplete(dir);

        File hit = cache.getValidDir(book);
        assertNotNull(hit);
        assertEquals(dir, hit);
        // the extracted content survived and is reused as-is
        assertEquals("<p>hola</p>", readChapter(hit, "ch1.xhtml"));
    }

    @Test
    public void incompleteExtractionIsDiscardedAndRecreated() throws IOException {
        File book = book(24);
        File first = cache.newDirFor(book);
        writeChapter(first, "partial.xhtml", "half");
        // no markComplete: simulates a crash mid-unzip

        assertNull(cache.getValidDir(book));
        assertFalse(first.exists()); // broken dir removed on next open

        File second = cache.newDirFor(book);
        assertTrue(second.exists());
        writeChapter(second, "ch.xhtml", "<p>fixed</p>");
        cache.markComplete(second);
        File hit = cache.getValidDir(book);
        assertNotNull(hit);
        assertEquals(second, hit);
    }

    @Test
    public void changedBookProducesDifferentKey() throws IOException {
        File book = book(32);
        File dir = cache.newDirFor(book);
        cache.markComplete(dir);
        assertNotNull(cache.getValidDir(book));

        // the book file changes (re-imported/updated) -> stale extraction must not be reused
        try (FileWriter w = new FileWriter(book)) {
            w.write("totally different content, longer than before");
        }
        book.setLastModified(System.currentTimeMillis() + 50_000L);

        assertNull(cache.getValidDir(book));
        assertNotEquals(dir, cache.newDirFor(book));
    }

    @Test
    public void evictsOldestBeyondSixEntries() throws IOException {
        for (int size = 10; size <= 70; size += 10) {
            File b = book(size);
            File d = cache.newDirFor(b);
            cache.markComplete(d);
            // distinct mtimes so the LRU order is deterministic
            assertTrue(d.setLastModified(System.currentTimeMillis() - (100 - size) * 1000L));
        }
        assertEquals(6, dirCount());
    }

    @Test
    public void reusedDirIsTouchedForLruOrder() throws IOException {
        long now = System.currentTimeMillis();

        // A cache already at capacity (6 entries).
        File oldest = book(50);
        File oldestDir = cache.newDirFor(oldest);
        cache.markComplete(oldestDir);
        assertTrue(oldestDir.setLastModified(now - 60_000L)); // least recently used

        File victim = book(60);
        File victimDir = cache.newDirFor(victim);
        cache.markComplete(victimDir);
        assertTrue(victimDir.setLastModified(now - 40_000L));

        for (int size = 70; size <= 100; size += 10) {
            File filler = book(size);
            File fillerDir = cache.newDirFor(filler);
            cache.markComplete(fillerDir);
            assertTrue(fillerDir.setLastModified(now - 30_000L + size * 10L));
        }
        assertEquals(6, dirCount());

        // Reusing `oldest` bumps it to the front of the LRU queue.
        long before = oldestDir.lastModified();
        assertNotNull(cache.getValidDir(oldest));
        assertTrue(oldestDir.lastModified() > before);

        // A seventh entry now evicts `victim` (the least recently used),
        // never the just-reused `oldest`.
        File extra = book(110);
        cache.markComplete(cache.newDirFor(extra));
        assertEquals(6, dirCount());
        assertTrue(oldestDir.exists());
        assertFalse(victimDir.exists());
    }

    @Test
    public void cacheStoresOnlyDerivedDataUnderRoot() throws IOException {
        File book = book(12);
        File dir = cache.newDirFor(book);
        cache.markComplete(dir);

        assertTrue(dir.getCanonicalPath().startsWith(root.getCanonicalPath()));
        // the book file itself is never moved or deleted by the cache
        assertTrue(book.exists());
        assertEquals(12, book.length());
    }

    private void writeChapter(File dir, String name, String content) throws IOException {
        assertTrue(dir.exists());
        try (FileWriter w = new FileWriter(new File(dir, name))) {
            w.write(content);
        }
    }

    private String readChapter(File dir, String name) throws IOException {
        return new String(Files.readAllBytes(new File(dir, name).toPath()));
    }
}
