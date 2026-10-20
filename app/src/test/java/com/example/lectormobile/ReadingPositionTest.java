package com.example.lectormobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ReadingPosition} — spec S1 (position formats) and
 * S2 (percent) of odd/tasks/resume-progress.md.
 *
 * S1: legacy formats {@code page:N} and {@code chapter:N} keep parsing;
 * EPUB gains {@code chapter:N:fraction}; formatting round-trips.
 * S2: percent = floor((index + fraction) * 100 / total), clamped to 0..100;
 * an unknown total (<= 0) returns -1 so callers never show or store a percent.
 */
public class ReadingPositionTest {

    // ── S1: parsing of legacy and new formats ──

    @Test
    public void parsePdfPageKeepsLegacyFormat() {
        ReadingPosition p = ReadingPosition.parse("page:5");
        assertNotNull(p);
        assertTrue(p.isPage);
        assertEquals(5, p.index);
        assertEquals(0f, p.fraction, 0.0001f);
    }

    @Test
    public void parseLegacyChapterWithoutFraction() {
        ReadingPosition p = ReadingPosition.parse("chapter:3");
        assertNotNull(p);
        assertTrue(!p.isPage);
        assertEquals(3, p.index);
        assertEquals(0f, p.fraction, 0.0001f);
    }

    @Test
    public void parseChapterWithFraction() {
        ReadingPosition p = ReadingPosition.parse("chapter:3:0.642");
        assertNotNull(p);
        assertEquals(3, p.index);
        assertEquals(0.642f, p.fraction, 0.0001f);
    }

    @Test
    public void formatRoundTrips() {
        assertEquals("page:5", ReadingPosition.parse("page:5").format());
        assertEquals("chapter:3", ReadingPosition.parse("chapter:3").format());
        assertEquals("chapter:3:0.642", ReadingPosition.parse("chapter:3:0.642").format());
        assertEquals("chapter:7:0.125", new ReadingPosition(false, 7, 0.125f).format());
    }

    @Test
    public void formatZeroFractionKeepsLegacyShape() {
        assertEquals("chapter:3", new ReadingPosition(false, 3, 0f).format());
        assertEquals("page:0", new ReadingPosition(true, 0, 0f).format());
    }

    @Test
    public void formatPreservesFractionValue() {
        ReadingPosition p = ReadingPosition.parse("chapter:3:0.64");
        ReadingPosition again = ReadingPosition.parse(p.format());
        assertNotNull(again);
        assertEquals(0.64f, again.fraction, 0.001f);
    }

    @Test
    public void parseRejectsMalformedInput() {
        assertNull(ReadingPosition.parse(null));
        assertNull(ReadingPosition.parse(""));
        assertNull(ReadingPosition.parse("   "));
        assertNull(ReadingPosition.parse("garbage"));
        assertNull(ReadingPosition.parse("chapter:abc"));
        assertNull(ReadingPosition.parse("chapter:-1"));
        assertNull(ReadingPosition.parse("page:-2"));
        assertNull(ReadingPosition.parse("chapter:1:2:3"));
    }

    @Test
    public void parseToleratesBrokenFraction() {
        ReadingPosition p = ReadingPosition.parse("chapter:3:xyz");
        assertNotNull(p);
        assertEquals(3, p.index);
        assertEquals(0f, p.fraction, 0.0001f);
    }

    @Test
    public void parseClampsFraction() {
        assertEquals(1f, ReadingPosition.parse("chapter:3:1.7").fraction, 0.0001f);
        assertEquals(0f, ReadingPosition.parse("chapter:3:-0.5").fraction, 0.0001f);
        assertEquals(0f, ReadingPosition.parse("chapter:3:NaN").fraction, 0.0001f);
        assertEquals(1f, ReadingPosition.parse("chapter:3:Infinity").fraction, 0.0001f);
    }

    // ── S2: percent ──

    @Test
    public void percentForPdfUsesReachedPage() {
        assertEquals(3, ReadingPosition.percent(2, 1f, 100));    // "Página 3 / 100 · 3%"
        assertEquals(1, ReadingPosition.percent(0, 1f, 100));    // first page
        assertEquals(100, ReadingPosition.percent(99, 1f, 100)); // last page
    }

    @Test
    public void percentForEpubUsesChapterPlusFraction() {
        assertEquals(0, ReadingPosition.percent(0, 0f, 40));
        assertEquals(6, ReadingPosition.percent(2, 0.6f, 40));   // "Capítulo 3 / 40 · 6%"
        assertEquals(100, ReadingPosition.percent(39, 1f, 40));  // end of last chapter
    }

    @Test
    public void percentUnknownTotalReturnsMinusOne() {
        assertEquals(-1, ReadingPosition.percent(0, 0f, 0));
        assertEquals(-1, ReadingPosition.percent(5, 0.5f, -3));
    }

    @Test
    public void percentFloorsFractionalValues() {
        assertEquals(33, ReadingPosition.percent(1, 0f, 3));
        assertEquals(16, ReadingPosition.percent(0, 0.5f, 3));
    }

    @Test
    public void percentClampsOutOfRange() {
        assertEquals(100, ReadingPosition.percent(200, 1f, 100));
        assertEquals(0, ReadingPosition.percent(-5, 0f, 100));
        assertEquals(1, ReadingPosition.percent(0, 5f, 100)); // fraction clamped to 1
    }
}
