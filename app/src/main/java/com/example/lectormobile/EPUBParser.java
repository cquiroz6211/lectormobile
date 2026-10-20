package com.example.lectormobile;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUBParser v3.0 — EPUB 2/3 support with OPF spine ordering and TOC extraction.
 *
 * Reading order comes from the OPF spine (correct chapter order), with a
 * filesystem fallback for malformed EPUBs.
 */
public class EPUBParser {
    private static final String TAG = "EPUBParser";
    private static final String CONTAINER_XML = "META-INF/container.xml";

    /**
     * Extract an EPUB (ZIP) into a destination directory.
     */
    public static void unzip(File zipFile, File destDir) throws IOException {
        destDir.mkdirs();
        String destRoot = destDir.getCanonicalPath();
        long totalBytes = 0;
        final long maxBytes = 500L * 1024 * 1024; // 500 MB anti zip-bomb cap

        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File dest = new File(destDir, entry.getName());

                // Zip-slip protection: never write outside the extraction dir.
                String destPath = dest.getCanonicalPath();
                if (!destPath.equals(destRoot) &&
                        !destPath.startsWith(destRoot + File.separator)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    dest.mkdirs();
                } else {
                    File parent = dest.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (InputStream is = zip.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) > 0) {
                            totalBytes += len;
                            if (totalBytes > maxBytes) {
                                throw new IOException("EPUB demasiado grande");
                            }
                            fos.write(buf, 0, len);
                        }
                    }
                }
            }
        }
    }

    /**
     * Locate the OPF package file from META-INF/container.xml.
     * Returns an absolute File, or null if not found.
     */
    public static File findOpfFile(File extractDir) {
        File containerXml = new File(extractDir, CONTAINER_XML);
        if (!containerXml.exists()) return null;
        try {
            String xml = readFile(containerXml);
            Matcher m = Pattern.compile("full-path\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE).matcher(xml);
            if (m.find()) {
                File opf = new File(extractDir, m.group(1));
                if (opf.exists()) return opf;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing container.xml", e);
        }
        return null;
    }

    /**
     * Directory that contains the OPF (e.g. "OEBPS"), or empty string.
     */
    public static String findContentDir(File extractDir) {
        File opf = findOpfFile(extractDir);
        if (opf == null) return null;
        String dir = opf.getParentFile() != null ? opf.getParentFile().getAbsolutePath() : null;
        if (dir == null) return "";
        String root = extractDir.getAbsolutePath();
        if (dir.equals(root)) return "";
        if (dir.startsWith(root + File.separator)) {
            return dir.substring(root.length() + 1);
        }
        return dir;
    }

    /**
     * Reading order from the OPF spine, as absolute file paths.
     * Falls back to scanning the content directory when the OPF is unusable.
     */
    public static ArrayList<String> getChapterFiles(File extractDir, String contentDir) {
        ArrayList<String> chapters = new ArrayList<>();
        File opf = findOpfFile(extractDir);
        if (opf != null) {
            chapters = parseSpine(opf);
        }
        if (chapters.isEmpty()) {
            File dir = (contentDir != null && !contentDir.isEmpty())
                    ? new File(extractDir, contentDir) : extractDir;
            chapters = scanHtmlFiles(dir);
        }
        return chapters;
    }

    /**
     * Parse manifest + spine from the OPF and return the reading order.
     */
    private static ArrayList<String> parseSpine(File opf) {
        ArrayList<String> result = new ArrayList<>();
        try {
            String xml = readFile(opf);
            File base = opf.getParentFile();

            // manifest: id -> href
            java.util.HashMap<String, String> manifest = new java.util.HashMap<>();
            Matcher item = Pattern.compile("<item\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(xml);
            while (item.find()) {
                String tag = item.group();
                String id = attr(tag, "id");
                String href = attr(tag, "href");
                String mediaType = attr(tag, "media-type");
                if (id != null && href != null) {
                    if (mediaType == null || mediaType.contains("html") || mediaType.contains("xml")) {
                        manifest.put(id, href);
                    }
                }
            }

            // spine: ordered idrefs
            Matcher ref = Pattern.compile("<itemref\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(xml);
            while (ref.find()) {
                String idref = attr(ref.group(), "idref");
                if (idref == null) continue;
                String href = manifest.get(idref);
                if (href == null) continue;
                href = href.split("#")[0];
                File file = new File(base, href);
                if (file.exists() && file.isFile()) {
                    result.add(file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing OPF spine", e);
        }
        return result;
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extract the cover image declared in the OPF and copy it to destFile.
     * Resolution order (spec S1 of odd/tasks/library-covers.md):
     *   1. EPUB 3: manifest item with properties="cover-image"
     *   2. EPUB 2: meta[name=cover] content=id -> manifest item with that id
     *   3. any manifest image item whose href starts with "cover"
     * Returns true when a file was copied; false means "no cover" and the
     * caller falls back to the generic icon. Does not use android.util.Log
     * so it stays unit-testable on the JVM.
     */
    public static boolean extractCover(File extractDir, File destFile) {
        if (extractDir == null || destFile == null) return false;
        try {
            File opf = findOpfFile(extractDir);
            if (opf == null) return false;

            File baseDir = opf.getParentFile() != null ? opf.getParentFile() : extractDir;
            String xml = readFile(opf);

            String href = findCoverHref(xml);
            if (href == null) return false;

            File cover = new File(baseDir, href.split("#")[0]);
            if (!cover.exists() || !cover.isFile()) return false;

            copyFile(cover, destFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Find the cover href in an OPF document string, or null.
     */
    private static String findCoverHref(String opfXml) {
        if (opfXml == null) return null;

        // EPUB 2: <meta name="cover" content="id"/>
        String metaId = null;
        Matcher meta = Pattern.compile("<meta\\b[^>]*>",
                Pattern.CASE_INSENSITIVE).matcher(opfXml);
        while (meta.find()) {
            String tag = meta.group();
            String name = attr(tag, "name");
            if (name != null && name.equalsIgnoreCase("cover")) {
                metaId = attr(tag, "content");
                break;
            }
        }

        // EPUB 3 property wins over the EPUB 2 meta declaration.
        String propertyHref = null;
        String metaHref = null;
        String looseHref = null;

        Matcher item = Pattern.compile("<item\\b[^>]*>",
                Pattern.CASE_INSENSITIVE).matcher(opfXml);
        while (item.find()) {
            String tag = item.group();
            String href = attr(tag, "href");
            String id = attr(tag, "id");
            String props = attr(tag, "properties");
            String media = attr(tag, "media-type");

            if (href == null) continue;
            if (media != null && !media.startsWith("image/")) continue;

            if (props != null && props.contains("cover-image")) {
                propertyHref = href;
            }
            if (metaId != null && metaId.equals(id)) {
                metaHref = href;
            }
            if (looseHref == null && new File(href).getName().toLowerCase()
                    .startsWith("cover")) {
                looseHref = href;
            }
        }

        if (propertyHref != null) return propertyHref;
        if (metaHref != null) return metaHref;
        return looseHref;
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    /**
     * Fallback: all HTML/XHTML files under a directory, sorted by name.
     */
    private static ArrayList<String> scanHtmlFiles(File dir) {
        ArrayList<String> files = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return files;
        File[] children = dir.listFiles();
        if (children == null) return files;
        java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(scanHtmlFiles(child));
            } else if (isHtml(child.getName())) {
                files.add(child.getAbsolutePath());
            }
        }
        return files;
    }

    private static boolean isHtml(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm");
    }

    /**
     * Read a text file as a UTF-8 string.
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

    /**
     * Extract the table of contents (EPUB 3 nav.xhtml or EPUB 2 toc.ncx).
     */
    public static List<TocEntry> extractTOC(File extractDir, String contentDir) {
        File opf = findOpfFile(extractDir);
        File baseDir = (opf != null && opf.getParentFile() != null)
                ? opf.getParentFile()
                : (contentDir != null && !contentDir.isEmpty()
                    ? new File(extractDir, contentDir) : extractDir);

        // EPUB 3: nav document declared in the OPF manifest
        File nav = findNavDocument(opf, baseDir);
        if (nav != null && nav.exists()) {
            List<TocEntry> toc = parseNavXHTML(nav);
            if (!toc.isEmpty()) return toc;
        }

        // EPUB 2: NCX
        File ncx = findNcx(opf, baseDir);
        if (ncx != null && ncx.exists()) {
            return parseNCX(ncx);
        }

        return new ArrayList<>();
    }

    private static File findNavDocument(File opf, File baseDir) {
        if (opf == null) {
            File guess = new File(baseDir, "nav.xhtml");
            if (guess.exists()) return guess;
            guess = new File(baseDir, "nav.html");
            return guess.exists() ? guess : null;
        }
        try {
            String xml = readFile(opf);
            Matcher item = Pattern.compile("<item\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(xml);
            while (item.find()) {
                String tag = item.group();
                String props = attr(tag, "properties");
                String href = attr(tag, "href");
                if (href != null && props != null && props.contains("nav")) {
                    File f = new File(baseDir, href.split("#")[0]);
                    if (f.exists()) return f;
                }
            }
            File fallback = new File(baseDir, "nav.xhtml");
            if (fallback.exists()) return fallback;
        } catch (Exception e) {
            Log.e(TAG, "Error finding nav document", e);
        }
        return null;
    }

    private static File findNcx(File opf, File baseDir) {
        if (opf != null) {
            try {
                String xml = readFile(opf);
                Matcher item = Pattern.compile("<item\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(xml);
                while (item.find()) {
                    String tag = item.group();
                    String mediaType = attr(tag, "media-type");
                    String href = attr(tag, "href");
                    if (href != null && mediaType != null && mediaType.contains("dtbncx")) {
                        File f = new File(baseDir, href);
                        if (f.exists()) return f;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error finding NCX", e);
            }
        }
        File fallback = new File(baseDir, "toc.ncx");
        return fallback.exists() ? fallback : null;
    }

    private static List<TocEntry> parseNavXHTML(File navFile) {
        List<TocEntry> toc = new ArrayList<>();
        try {
            String html = readFile(navFile);
            Matcher m = Pattern.compile(
                    "<a\\s+[^>]*href=[\"']([^\"'>]+)[\"'][^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            while (m.find()) {
                String href = m.group(1);
                String title = m.group(2).replaceAll("<[^>]+>", "").trim();
                if (!title.isEmpty() && !href.startsWith("http")) {
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
            Matcher labels = Pattern.compile("<text>([^<]*)</text>", Pattern.DOTALL).matcher(xml);
            Matcher srcs = Pattern.compile("<content[^>]*src=[\"']([^\"'>]+)[\"']",
                    Pattern.CASE_INSENSITIVE).matcher(xml);
            // NCX lists navLabel/order then content; pair them in document order
            java.util.ArrayList<String> labelList = new java.util.ArrayList<>();
            java.util.ArrayList<String> srcList = new java.util.ArrayList<>();
            while (labels.find()) labelList.add(labels.group(1).trim());
            while (srcs.find()) srcList.add(srcs.group(1));
            int n = Math.min(labelList.size(), srcList.size());
            for (int i = 0; i < n; i++) {
                if (!labelList.get(i).isEmpty()) {
                    toc.add(new TocEntry(labelList.get(i), srcList.get(i), 0));
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

        @Override
        public String toString() {
            return title;
        }
    }
}