package com.example.lectormobile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BookPageEstimator — Estimates standard book pages for reflowable content (spec S1).
 *
 * <p>Uses the industry standard (1024 clean characters per synthetic page, as defined
 * in Adobe Digital Editions / EPUB synthetic page number specification).
 * Allows bidirectional conversion between (chapterIndex, fraction) and absolute book page.
 */
public class BookPageEstimator {

    /** Standard characters per synthetic page (Adobe RMSDK / EPUB standard). */
    public static final int CHARS_PER_PAGE = 1024;

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE_PATTERN = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final int[] chapterCharCounts;
    private final long[] cumulativeChars;
    private final long totalChars;
    private final int totalPages;

    private BookPageEstimator(int[] chapterCharCounts) {
        if (chapterCharCounts == null || chapterCharCounts.length == 0) {
            this.chapterCharCounts = new int[]{0};
        } else {
            this.chapterCharCounts = chapterCharCounts.clone();
        }

        this.cumulativeChars = new long[this.chapterCharCounts.length + 1];
        long sum = 0;
        for (int i = 0; i < this.chapterCharCounts.length; i++) {
            this.cumulativeChars[i] = sum;
            sum += Math.max(0, this.chapterCharCounts[i]);
        }
        this.cumulativeChars[this.chapterCharCounts.length] = sum;
        this.totalChars = sum;
        this.totalPages = Math.max(1, (int) Math.ceil((double) this.totalChars / CHARS_PER_PAGE));
    }

    /**
     * Create an estimator from chapter files on disk. Reads each file and counts
     * clean readable characters. Runs off the main thread.
     */
    public static BookPageEstimator fromChapterFiles(java.util.List<String> chapterPaths) {
        if (chapterPaths == null || chapterPaths.isEmpty()) {
            return fromCharCounts(new int[0]);
        }
        int[] counts = new int[chapterPaths.size()];
        for (int i = 0; i < chapterPaths.size(); i++) {
            java.io.File f = new java.io.File(chapterPaths.get(i));
            if (f.exists() && f.isFile()) {
                try {
                    String html = EPUBParser.readFile(f);
                    counts[i] = countCleanCharacters(html);
                } catch (Exception e) {
                    counts[i] = (int) Math.max(0, f.length() / 2);
                }
            }
        }
        return fromCharCounts(counts);
    }

    /**
     * Create an estimator from character counts per chapter in spine order.
     */
    public static BookPageEstimator fromCharCounts(int[] counts) {
        return new BookPageEstimator(counts);
    }

    /**
     * Total synthetic pages estimated for the book. Always at least 1.
     */
    public int getTotalPages() {
        return totalPages;
    }

    /**
     * Total clean characters across all chapters.
     */
    public long getTotalChars() {
        return totalChars;
    }

    /**
     * Get clean character count for a specific chapter index.
     */
    public int getChapterCharCount(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= chapterCharCounts.length) {
            return 0;
        }
        return chapterCharCounts[chapterIndex];
    }

    /**
     * Compute 1-based book page for a given chapter and scroll fraction (0..1).
     */
    public int getPageForPosition(int chapterIndex, float fraction) {
        if (chapterCharCounts.length == 0 || totalChars <= 0) {
            return 1;
        }

        int safeChapter = Math.max(0, Math.min(chapterCharCounts.length - 1, chapterIndex));
        float safeFraction = clampFraction(fraction);

        long chapterBase = cumulativeChars[safeChapter];
        long inChapterOffset = (long) (safeFraction * chapterCharCounts[safeChapter]);
        long absoluteOffset = chapterBase + inChapterOffset;

        int page = (int) (absoluteOffset / CHARS_PER_PAGE) + 1;
        return Math.max(1, Math.min(totalPages, page));
    }

    /**
     * Given a 1-based target page (1..totalPages), returns [chapterIndex, fraction]
     * where that page begins in the spine.
     */
    public float[] getPositionForPage(int targetPage) {
        if (chapterCharCounts.length == 0 || totalChars <= 0 || targetPage <= 1) {
            return new float[]{0f, 0f};
        }

        int safePage = Math.min(targetPage, totalPages);
        long targetChar = (long) (safePage - 1) * CHARS_PER_PAGE;

        // Find the chapter containing targetChar
        int targetChapter = 0;
        for (int i = 0; i < chapterCharCounts.length; i++) {
            if (targetChar < cumulativeChars[i + 1]) {
                targetChapter = i;
                break;
            }
            targetChapter = i;
        }

        long chapterBase = cumulativeChars[targetChapter];
        long offsetInChapter = targetChar - chapterBase;
        int count = chapterCharCounts[targetChapter];
        float fraction = count > 0 ? (float) offsetInChapter / count : 0f;

        return new float[]{(float) targetChapter, clampFraction(fraction)};
    }

    /**
     * Fast extraction and counting of readable text characters from HTML/XHTML content.
     * Ignores scripts, styles, markup tags and collapses whitespace.
     */
    public static int countCleanCharacters(String html) {
        if (html == null || html.isEmpty()) return 0;

        String text = SCRIPT_PATTERN.matcher(html).replaceAll(" ");
        text = STYLE_PATTERN.matcher(text).replaceAll(" ");
        text = TAG_PATTERN.matcher(text).replaceAll(" ");

        // Unescape standard entities
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");

        // Numeric entities
        if (text.contains("&#")) {
            text = unescapeNumericEntities(text);
        }

        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
        return text.length();
    }

    private static String unescapeNumericEntities(String input) {
        Matcher m = Pattern.compile("&#(?:x([0-9a-fA-F]+)|(\\d+));").matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int codePoint = m.group(1) != null
                        ? Integer.parseInt(m.group(1), 16)
                        : Integer.parseInt(m.group(2), 10);
                if (Character.isValidCodePoint(codePoint)) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
                    continue;
                }
            } catch (Exception ignored) { }
            m.appendReplacement(sb, " ");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static float clampFraction(float fraction) {
        if (Float.isNaN(fraction)) return 0f;
        if (fraction < 0f) return 0f;
        if (fraction > 1f) return 1f;
        return fraction;
    }
}
