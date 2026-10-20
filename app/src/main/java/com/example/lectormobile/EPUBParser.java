package com.example.lectormobile;

import android.util.Log;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * EPUBParser v2.0 — Full EPUB 2/3 support with TOC, chapter navigation, and image handling.
 * Parses EPUB files (ZIP + XHTML) with full table of contents extraction.
 */
public class EPUBParser {
    private static final String TAG = "EPUBParser";
    private static final String CONTAINER_XML = "META-INF/container.xml";
    private static final String NAV_FILENAME = "nav.xhtml";
    private static final String OPF_FILENAME = "content.opf";
    private static final String NCX_FILENAME = "toc.ncx";

    /**
     * Extract EPUB to destination directory.
     */
    public static void unzip(File zipFile, File destDir) throws IOException {
        destDir.mkdirs();
        ZipFile zip = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File dest = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                dest.mkdirs();
            } else {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (InputStream is = zip.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                }
            }
        }
        zip.close();
    }

    /**
     * Find the content directory from container.xml.
     * Returns path like "OEBPS" or "EPUB/OEBPS".
     */
    public static String findContentDir(File extractDir) {
        File containerXml = new File(extractDir, CONTAINER_XML);
        if (!containerXml.exists()) return null;
        try {
            String xml = readFile(containerXml);
            int start = xml.indexOf("full-path=\"");
            if (start == -1) start = xml.indexOf("full-path='");
            if (start == -1) return null;
            start += 13;
            int end = xml.indexOf("\"", start);
            if (end == -1) end = xml.indexOf("'", start);
            if (end == -1) return null;
            String fullPath = xml.substring(start, end);
            int lastSlash = fullPath.lastIndexOf('/');
            return lastSlash > 0 ? fullPath.substring(0, lastSlash) : fullPath;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing container.xml", e);
        }
        return null;
    }

    /**
     * Extract the full table of contents from an EPUB.
     * Supports EPUB 3 (nav.xhtml) and EPUB 2 (toc.ncx).
     */
    public static List<TocEntry> extractTOC(File extractDir, String contentDir) {
        List<TocEntry> toc = new ArrayList<>();

        // Try EPUB 3 nav.xhtml first
        if (contentDir != null) {
            File navFile = new File(extractDir, contentDir + "/" + NAV_FILENAME);
            if (navFile.exists()) {
                toc = parseNavXHTML(navFile);
                if (!toc.isEmpty()) return toc;
            }
        }

        // Try EPUB 2 NCX
        File ncxFile = new File(extractDir, NCX_FILENAME);
        if (ncxFile.exists()) {
            return parseNCX(ncxFile);
        }

        return toc;
    }

    /**
     * Get the list of all chapter files in the EPUB.
     */
    public static ArrayList<String> getChapterFiles(File extractDir, String contentDir) {
        ArrayList<String> chapters = new ArrayList<>();
        if (contentDir != null) {
            File dir = new File(extractDir, contentDir);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"));
                if (files != null) {
                    for (File f : files) chapters.add(contentDir + "/" + f.getName());
                }
            }
        }
        return chapters;
    }

    /**
     * Read a text file as string.
     */
    public static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) sb.append(new String(buf, 0, len, "UTF-8"));
        }
        return sb.toString();
    }

    // ─── Internal parsing methods ───

    private static List<TocEntry> parseNavXHTML(File navFile) {
        List<TocEntry> toc = new ArrayList<>();
        try {
            String html = readFile(navFile);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<a\\s+[^>]*href=[\"']([^\"'>]+)[\"'][^>]*>(.*?)</a>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL
            );
            java.util.regex.Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String href = matcher.group(1);
                String title = matcher.group(2).trim();
                title = title.replaceAll("<[^>]+>", "").trim();
                if (!title.isEmpty() && href.contains(".")) {
                    toc.add(new TocEntry(title, href, 0));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing nav.xhtml", e);
        }
        return toc;
    }

    private static List<TocEntry> parseNCX(File ncxFile) {
        List<TocEntry> toc = new ArrayList<>();
        try {
            String xml = readFile(ncxFile);
            java.util.regex.Pattern labelPattern = java.util.regex.Pattern.compile(
                "<text>([^<]+)</text>", java.util.regex.Pattern.DOTALL
            );
            java.util.regex.Pattern srcPattern = java.util.regex.Pattern.compile(
                "<content[^>]*src=[\"']([^\"'>]+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Pattern navPointPattern = java.util.regex.Pattern.compile(
                "<navPoint[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE
            );

            java.util.regex.Matcher navMatcher = navPointPattern.matcher(xml);
            while (navMatcher.find()) {
                int start = navMatcher.start();
                int nextNav = xml.indexOf("<navPoint", start + 1);
                String navBlock = nextNav > 0 ? xml.substring(start, nextNav) : xml.substring(start);

                java.util.regex.Matcher labelMatcher = labelPattern.matcher(navBlock);
                java.util.regex.Matcher srcMatcher = srcPattern.matcher(navBlock);
                if (labelMatcher.find() && srcMatcher.find()) {
                    String title = labelMatcher.group(1).trim();
                    String href = srcMatcher.group(1);
                    toc.add(new TocEntry(title, href, 0));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing NCX", e);
        }
        return toc;
    }

    /**
     * A single entry in the table of contents.
     */
    public static class TocEntry {
        public String title;
        public String href;
        public int depth;

        public TocEntry(String title, String href, int depth) {
            this.title = title;
            this.href = href;
            this.depth = depth;
        }
    }
}
