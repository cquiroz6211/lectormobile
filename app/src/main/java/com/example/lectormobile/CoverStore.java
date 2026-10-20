package com.example.lectormobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Disk-backed cover cache for the library (spec S2 of
 * odd/tasks/library-covers.md).
 *
 * <p>Covers live in filesDir/covers/, one file per book, named after a
 * content key (path + lastModified + size) so a cover is regenerated only
 * when the book file actually changes. EPUB covers come from the OPF
 * declaration; PDF covers are a render of page 1 scaled to ~256px on the
 * long side. Everything is local: no network, no third-party image library.
 */
public class CoverStore {

    private static final String TAG = "CoverStore";
    private static final String DIR = "covers";
    /** Long-side size of a cached cover; enough for a 64dp list thumbnail. */
    private static final int TARGET_SIZE = 256;

    private final Context context;

    public CoverStore(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Return the cached cover file for a book, or null when none exists.
     * Synchronous disk cache hit only — generation happens on the caller's
     * background thread via {@link #getOrCreate}.
     */
    public File peek(String bookPath) {
        File cover = coverFile(bookPath);
        return cover.exists() ? cover : null;
    }

    /**
     * Return the cover for a book, generating and caching it when missing.
     * Meant for a background thread (EPUB unzip and PDF rendering block).
     * Returns null when no cover could be produced — the UI shows the
     * generic icon.
     */
    public File getOrCreate(String bookPath) {
        if (bookPath == null) return null;
        File cover = coverFile(bookPath);
        if (cover.exists()) return cover;

        File book = new File(bookPath);
        if (!book.exists()) return null;

        File tmp = null;
        try {
            File dir = coverDir();
            tmp = File.createTempFile("cover_", ".tmp", dir);
            boolean ok;
            if (bookPath.toLowerCase().endsWith(".pdf")) {
                ok = renderPdfCover(book, tmp);
            } else {
                ok = extractEpubCover(book, tmp);
            }
            if (!ok) {
                // Negative result is cached by leaving no file: peek keeps
                // returning null and the adapter retries only when the book
                // content key changes.
                return null;
            }
            if (!tmp.renameTo(cover)) {
                copy(tmp, cover);
                tmp.delete();
            }
            return cover;
        } catch (Exception e) {
            Log.w(TAG, "Cover generation failed for " + bookPath, e);
            return null;
        } finally {
            if (tmp != null && tmp.exists()) tmp.delete();
        }
    }

    /** Forget the cached cover for a deleted/changed book. */
    public void invalidate(String bookPath) {
        if (bookPath == null) return;
        File cover = coverFile(bookPath);
        if (cover.exists()) cover.delete();
    }

    private File coverDir() {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Content-keyed cover file: same book content -> same name, so an
     * updated file (new mtime/size) naturally regenerates its cover.
     */
    private File coverFile(String bookPath) {
        File book = new File(bookPath);
        String key = Math.abs(hash(bookPath + ":" + book.lastModified()
                + ":" + book.length())) + ".img";
        return new File(coverDir(), key);
    }

    private static long hash(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }

    /**
     * Extract the EPUB cover without unpacking the whole archive: locate the
     * OPF inside the zip, resolve the cover href, copy that entry out.
     */
    private boolean extractEpubCover(File epub, File dest) {
        ZipFile zip = null;
        File opfTmp = null;
        File extractTmp = null;
        try {
            zip = new ZipFile(epub);

            String opfPath = findOpfInZip(zip);
            if (opfPath == null) return false;
            String baseDir = parentPath(opfPath);

            // Materialize the OPF (and resolve the href) through the regular
            // parser: a tiny throwaway extraction keeps EPUBParser testable.
            extractTmp = File.createTempFile("epub_", ".dir",
                    context.getCacheDir());
            extractTmp.delete();
            extractTmp.mkdirs();
            opfTmp = new File(extractTmp, opfPath);
            opfTmp.getParentFile().mkdirs();
            copyZipEntry(zip, opfPath, opfTmp);

            String xml = readAsString(opfTmp);
            String href = coverHrefFromOpf(xml);
            if (href == null) return false;

            String entryPath = baseDir.isEmpty() ? href : baseDir + "/" + href;
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) {
                // Some EPUBs declare hrefs relative to the zip root.
                entry = zip.getEntry(href);
            }
            if (entry == null) return false;

            copyZipEntry(zip, entry.getName(), dest);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (zip != null) try { zip.close(); } catch (Exception ignored) { }
            if (opfTmp != null) opfTmp.delete();
            if (extractTmp != null) deleteRecursive(extractTmp);
        }
    }

    private static String findOpfInZip(ZipFile zip) {
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName().toLowerCase();
            if (name.endsWith(".opf") && !e.isDirectory()) {
                return e.getName();
            }
        }
        return null;
    }

    private static String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash) : "";
    }

    private static String coverHrefFromOpf(String xml) {
        if (xml == null) return null;
        // Mirrors EPUBParser.findCoverHref priorities.
        String metaId = null;
        java.util.regex.Matcher meta = java.util.regex.Pattern
                .compile("<meta\\b[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(xml);
        while (meta.find()) {
            String tag = meta.group();
            String name = attrOf(tag, "name");
            if (name != null && name.equalsIgnoreCase("cover")) {
                metaId = attrOf(tag, "content");
                break;
            }
        }
        String propertyHref = null, metaHref = null, looseHref = null;
        java.util.regex.Matcher item = java.util.regex.Pattern
                .compile("<item\\b[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(xml);
        while (item.find()) {
            String tag = item.group();
            String href = attrOf(tag, "href");
            String id = attrOf(tag, "id");
            String props = attrOf(tag, "properties");
            String media = attrOf(tag, "media-type");
            if (href == null) continue;
            if (media != null && !media.startsWith("image/")) continue;
            if (props != null && props.contains("cover-image")) propertyHref = href;
            if (metaId != null && metaId.equals(id)) metaHref = href;
            String file = href.substring(href.lastIndexOf('/') + 1);
            if (looseHref == null && file.toLowerCase().startsWith("cover")) {
                looseHref = href;
            }
        }
        if (propertyHref != null) return propertyHref;
        if (metaHref != null) return metaHref;
        return looseHref;
    }

    private static String attrOf(String tag, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(name + "\\s*=\\s*[\"']([^\"']*)[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private void copyZipEntry(ZipFile zip, String entryName, File dest)
            throws Exception {
        try (InputStream in = zip.getInputStream(zip.getEntry(entryName));
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /**
     * Render page 1 of the PDF at ~TARGET_SIZE on the long side.
     */
    private boolean renderPdfCover(File pdf, File dest) {
        ParcelFileDescriptor fd = null;
        try {
            fd = ParcelFileDescriptor.open(pdf,
                    ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(fd);
            try {
                if (renderer.getPageCount() <= 0) return false;
                PdfRenderer.Page page = renderer.openPage(0);
                try {
                    float scale = Math.min(
                            (float) TARGET_SIZE / page.getWidth(),
                            (float) TARGET_SIZE / page.getHeight());
                    int w = Math.max(1, Math.round(page.getWidth() * scale));
                    int h = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap bmp = Bitmap.createBitmap(w, h,
                            Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    try (FileOutputStream out = new FileOutputStream(dest)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
                    } finally {
                        bmp.recycle();
                    }
                    return true;
                } finally {
                    page.close();
                }
            } finally {
                renderer.close();
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (fd != null) try { fd.close(); } catch (Exception ignored) { }
        }
    }

    private static String readAsString(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        }
    }

    private static void copy(File src, File dest) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
