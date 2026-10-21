package com.example.lectormobile;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for BookPageEstimator (spec S1 of odd/tasks/paginated-reading.md).
 */
public class BookPageEstimatorTest {

    @Test
    public void testEmptyOrNullChaptersReturnsAtLeastOnePage() {
        BookPageEstimator estimator = BookPageEstimator.fromCharCounts(new int[0]);
        assertEquals(1, estimator.getTotalPages());
        assertEquals(1, estimator.getPageForPosition(0, 0f));

        float[] pos = estimator.getPositionForPage(1);
        assertEquals(0, (int) pos[0]);
        assertEquals(0f, pos[1], 0.001f);
    }

    @Test
    public void testSingleChapterPagination() {
        // 2048 chars -> exactly 2 pages (1024 chars/page)
        BookPageEstimator estimator = BookPageEstimator.fromCharCounts(new int[]{2048});
        assertEquals(2, estimator.getTotalPages());

        // Start of chapter (fraction 0.0) -> page 1
        assertEquals(1, estimator.getPageForPosition(0, 0.0f));

        // Halfway through (fraction 0.5) -> page 2 (at char 1024)
        assertEquals(2, estimator.getPageForPosition(0, 0.5f));

        // End of chapter (fraction 1.0) -> page 2
        assertEquals(2, estimator.getPageForPosition(0, 1.0f));

        // Inverting: page 1 starts at fraction 0.0
        float[] pos1 = estimator.getPositionForPage(1);
        assertEquals(0, (int) pos1[0]);
        assertEquals(0f, pos1[1], 0.001f);

        // Page 2 starts at fraction 0.5
        float[] pos2 = estimator.getPositionForPage(2);
        assertEquals(0, (int) pos2[0]);
        assertEquals(0.5f, pos2[1], 0.001f);
    }

    @Test
    public void testMultipleChaptersPagination() {
        // Ch0: 1024 chars (page 1)
        // Ch1: 2048 chars (pages 2, 3)
        // Ch2: 1024 chars (page 4)
        // Total 4096 chars -> 4 pages
        BookPageEstimator estimator = BookPageEstimator.fromCharCounts(new int[]{1024, 2048, 1024});
        assertEquals(4, estimator.getTotalPages());

        // Ch0 start -> page 1
        assertEquals(1, estimator.getPageForPosition(0, 0f));
        // Ch1 start -> page 2
        assertEquals(2, estimator.getPageForPosition(1, 0f));
        // Ch1 middle (fraction 0.5 = 1024 chars into Ch1) -> page 3
        assertEquals(3, estimator.getPageForPosition(1, 0.5f));
        // Ch2 start -> page 4
        assertEquals(4, estimator.getPageForPosition(2, 0f));

        // Reverse lookup: get position for page 3
        float[] posP3 = estimator.getPositionForPage(3);
        assertEquals(1, (int) posP3[0]); // Ch 1
        assertEquals(0.5f, posP3[1], 0.001f); // 50% into Ch 1

        // Reverse lookup: page 4
        float[] posP4 = estimator.getPositionForPage(4);
        assertEquals(2, (int) posP4[0]); // Ch 2
        assertEquals(0f, posP4[1], 0.001f); // 0% into Ch 2
    }

    @Test
    public void testClampingAndEdgeCases() {
        BookPageEstimator estimator = BookPageEstimator.fromCharCounts(new int[]{1024, 1024});
        assertEquals(2, estimator.getTotalPages());

        // Negative indices and fractions
        assertEquals(1, estimator.getPageForPosition(-1, -0.5f));
        // Beyond bounds
        assertEquals(2, estimator.getPageForPosition(5, 2.0f));

        // Target page out of range
        float[] posLow = estimator.getPositionForPage(-5);
        assertEquals(0, (int) posLow[0]);
        assertEquals(0f, posLow[1], 0.001f);

        float[] posHigh = estimator.getPositionForPage(99);
        assertEquals(1, (int) posHigh[0]);
    }

    @Test
    public void testFromChapterFiles() throws Exception {
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("estimator_test").toFile();
        tempDir.deleteOnExit();

        java.io.File ch1 = new java.io.File(tempDir, "ch1.html");
        java.io.File ch2 = new java.io.File(tempDir, "ch2.html");

        // Write ~1024 chars to ch1 and ~2048 chars to ch2
        StringBuilder sb1 = new StringBuilder("<html><body><p>");
        for (int i = 0; i < 100; i++) sb1.append("palabra ");
        sb1.append("</p></body></html>");
        java.nio.file.Files.write(ch1.toPath(), sb1.toString().getBytes("UTF-8"));

        StringBuilder sb2 = new StringBuilder("<html><body><p>");
        for (int i = 0; i < 260; i++) sb2.append("contenido ");
        sb2.append("</p></body></html>");
        java.nio.file.Files.write(ch2.toPath(), sb2.toString().getBytes("UTF-8"));

        BookPageEstimator estimator = BookPageEstimator.fromChapterFiles(
                Arrays.asList(ch1.getAbsolutePath(), ch2.getAbsolutePath()));

        assertTrue(estimator.getTotalPages() >= 2);
        assertTrue(estimator.getTotalChars() > 1000);
        assertEquals(1, estimator.getPageForPosition(0, 0f));
    }
}
