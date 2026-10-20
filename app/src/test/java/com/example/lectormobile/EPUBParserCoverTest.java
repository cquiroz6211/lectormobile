package com.example.lectormobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link EPUBParser#extractCover(File, File)} — spec S1 of
 * odd/tasks/library-covers.md: locate the cover image declared in the OPF
 * (EPUB 3 properties="cover-image", EPUB 2 meta[name=cover], or a file
 * literally named cover) and copy it to a destination. Returns false when
 * there is no cover or the source is missing — the caller falls back to the
 * generic icon.
 */
public class EPUBParserCoverTest {

    private File tmp;
    private File extractDir;
    private File dest;

    /** Minimal valid 1x1 PNG. */
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00,
        0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00,
        0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
        (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54, 0x78,
        (byte) 0x9C, 0x62, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, 0x0D,
        0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        (byte) 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Before
    public void setUp() throws IOException {
        tmp = Files.createTempDirectory("epubtest").toFile();
        extractDir = new File(tmp, "extracted");
        extractDir.mkdirs();
        dest = new File(tmp, "cover.png");
    }

    @After
    public void tearDown() {
        deleteRecursive(tmp);
    }

    private void unzipFixture(String opf) throws IOException {
        File epub = new File(tmp, "book.epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
            put(zos, "META-INF/container.xml",
                "<?xml version=\"1.0\"?><container><rootfiles><rootfile "
                + "full-path=\"OEBPS/content.opf\" "
                + "media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            put(zos, "OEBPS/content.opf", opf);
            put(zos, "OEBPS/ch1.xhtml", "<html><body>chapter</body></html>");
            putBytes(zos, "OEBPS/images/cover.png", PNG);
            putBytes(zos, "OEBPS/images/other.png", PNG);
        }
        EPUBParser.unzip(epub, extractDir);
    }

    private static void put(ZipOutputStream zos, String name, String content) throws IOException {
        putBytes(zos, name, content.getBytes("UTF-8"));
    }

    private static void putBytes(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
    }

    @Test
    public void extractsEpub3CoverImageProperty() throws IOException {
        unzipFixture("<?xml version=\"1.0\"?><package version=\"3.0\" "
            + "xmlns=\"http://www.idpf.org/2007/opf\"><metadata/>"
            + "<manifest>"
            + "<item id=\"ch1\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            + "<item id=\"c1\" href=\"images/cover.png\" media-type=\"image/png\" "
            + "properties=\"cover-image\"/>"
            + "</manifest><spine><itemref idref=\"ch1\"/></spine></package>");

        boolean ok = EPUBParser.extractCover(extractDir, dest);

        assertTrue(ok);
        assertTrue(dest.exists());
        assertEquals(PNG.length, dest.length());
    }

    @Test
    public void extractsEpub2MetaCover() throws IOException {
        unzipFixture("<?xml version=\"1.0\"?><package version=\"2.0\" "
            + "xmlns=\"http://www.idpf.org/2007/opf\">"
            + "<metadata><meta name=\"cover\" content=\"cover-id\"/></metadata>"
            + "<manifest>"
            + "<item id=\"ch1\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            + "<item id=\"cover-id\" href=\"images/other.png\" media-type=\"image/png\"/>"
            + "</manifest><spine><itemref idref=\"ch1\"/></spine></package>");

        boolean ok = EPUBParser.extractCover(extractDir, dest);

        assertTrue(ok);
        assertTrue(dest.exists());
        assertEquals(PNG.length, dest.length());
    }

    @Test
    public void returnsFalseWhenNoCoverDeclared() throws IOException {
        unzipFixture("<?xml version=\"1.0\"?><package version=\"3.0\" "
            + "xmlns=\"http://www.idpf.org/2007/opf\"><metadata/>"
            + "<manifest>"
            + "<item id=\"ch1\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            + "</manifest><spine><itemref idref=\"ch1\"/></spine></package>");

        assertFalse(EPUBParser.extractCover(extractDir, dest));
        assertFalse(dest.exists());
    }

    @Test
    public void returnsFalseWhenCoverFileMissing() throws IOException {
        unzipFixture("<?xml version=\"1.0\"?><package version=\"3.0\" "
            + "xmlns=\"http://www.idpf.org/2007/opf\"><metadata/>"
            + "<manifest>"
            + "<item id=\"ch1\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/>"
            + "<item id=\"c1\" href=\"images/nope.png\" media-type=\"image/png\" "
            + "properties=\"cover-image\"/>"
            + "</manifest><spine><itemref idref=\"ch1\"/></spine></package>");

        assertFalse(EPUBParser.extractCover(extractDir, dest));
    }

    @Test
    public void returnsFalseWithoutOpf() {
        assertFalse(EPUBParser.extractCover(extractDir, dest));
    }

    @Test
    public void nullSafe() {
        assertFalse(EPUBParser.extractCover(null, dest));
        assertFalse(EPUBParser.extractCover(extractDir, null));
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
