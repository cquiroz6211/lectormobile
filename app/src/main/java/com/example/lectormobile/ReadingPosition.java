package com.example.lectormobile;

import java.util.Locale;

/**
 * Reading position value: which page/chapter plus the scroll fraction inside it.
 *
 * <p>Stored format in {@code reading_positions.position} (spec S1, S2 of
 * odd/tasks/resume-progress.md):
 * <ul>
 *   <li>{@code page:N} — PDF, page granularity (legacy, unchanged)</li>
 *   <li>{@code chapter:N} — EPUB chapter start, no scroll (legacy, unchanged)</li>
 *   <li>{@code chapter:N:0.642} — EPUB chapter + scroll fraction 0..1 (new)</li>
 * </ul>
 * Legacy rows keep parsing; a broken fraction falls back to 0 (chapter start).
 * Bookmarks intentionally keep the legacy format without fraction.
 */
public class ReadingPosition {

    /** True for PDF pages, false for EPUB chapters. */
    public final boolean isPage;
    /** Zero-based page or chapter index. */
    public final int index;
    /** Scroll fraction inside the chapter, clamped to 0..1 (always 0 for pages). */
    public final float fraction;

    public ReadingPosition(boolean isPage, int index, float fraction) {
        this.isPage = isPage;
        this.index = index;
        this.fraction = clampFraction(fraction);
    }

    /**
     * Parse a stored position string. Returns null when the input is not a
     * position at all (callers keep their default in that case).
     */
    public static ReadingPosition parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;

        boolean isPage;
        if (value.startsWith("page:")) {
            isPage = true;
            value = value.substring("page:".length());
        } else if (value.startsWith("chapter:")) {
            isPage = false;
            value = value.substring("chapter:".length());
        } else {
            return null;
        }

        String[] parts = value.split(":", -1);
        if (parts.length == 0 || parts.length > 2) return null;

        int index;
        try {
            index = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 0) return null;

        float fraction = 0f;
        if (parts.length == 2) {
            try {
                fraction = Float.parseFloat(parts[1].trim());
            } catch (NumberFormatException e) {
                fraction = 0f; // tolerate a broken fraction: chapter start
            }
            if (Float.isNaN(fraction)) fraction = 0f;
            if (Float.isInfinite(fraction)) fraction = fraction > 0 ? 1f : 0f;
        }

        return new ReadingPosition(isPage, index, fraction);
    }

    /**
     * Serialize back to the stored format. Zero fraction keeps the legacy
     * shape ({@code chapter:N}) so bookmark values stay byte-identical.
     */
    public String format() {
        StringBuilder sb = new StringBuilder(isPage ? "page:" : "chapter:");
        sb.append(index);
        if (fraction > 0f && !isPage) {
            sb.append(':').append(String.format(Locale.US, "%.3f", fraction));
        }
        return sb.toString();
    }

    /**
     * Percent of the book read so far: floor((index + fraction) * 100 / total),
     * clamped to 0..100. PDFs pass fraction = 1f (the reached page counts as
     * read up to its end). Returns -1 when the total is unknown (<= 0) so
     * callers neither display nor persist a percent (spec S2).
     */
    public static int percent(int index, float fraction, int total) {
        if (total <= 0) return -1;
        int safeIndex = Math.max(0, index);
        float progress = safeIndex + clampFraction(fraction);
        int value = (int) Math.floor(progress * 100.0 / total);
        return Math.max(0, Math.min(100, value));
    }

    private static float clampFraction(float fraction) {
        if (Float.isNaN(fraction)) return 0f;
        if (fraction < 0f) return 0f;
        if (fraction > 1f) return 1f;
        return fraction;
    }
}
