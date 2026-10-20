package com.example.lectormobile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ReaderActivity — PDF and EPUB reading screen with dark mode, bookmarks, and progress tracking.
 */
public class ReaderActivity extends AppCompatActivity {

    private static final String TAG = "ReaderActivity";

    private PDFView pdfView;
    private android.webkit.WebView webView;
    private SeekBar progressSeek;
    private TextView progressTextView;
    private TextView chapterTitleTextView;
    private ProgressBar progressBar;

    private String bookPath;
    private File bookFile;
    private boolean isPdf;
    private BookmarksDBHelper dbHelper;
    private SharedPreferences prefs;

    // EPUB state
    private ArrayList<String> epubChapters;
    private int currentChapterIndex = 0;
    private File epubExtractDir;
    private File epubContentDir;
    private String initialPosition;
    private List<EPUBParser.TocEntry> tocEntries;

    // PDF state
    private int currentPage = 0;
    private int totalPages = 0;
    private int pdfPagesForProgress = 0;

    // UI state
    private boolean isDarkMode = false;
    private int fontSize = 18;

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        pdfView = findViewById(R.id.pdfView);
        webView = findViewById(R.id.webView);
        progressSeek = findViewById(R.id.progressSeek);
        progressTextView = findViewById(R.id.tvProgress);
        chapterTitleTextView = findViewById(R.id.chapterTitle);
        progressBar = findViewById(R.id.progressBar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        dbHelper = new BookmarksDBHelper(this);

        // Restore settings
        isDarkMode = prefs.getBoolean("dark_mode", true);
        fontSize = prefs.getInt("font_size", 18);
        if (fontSize < 12 || fontSize > 28) fontSize = 18;

        // Get book path from intent
        bookPath = getIntent().getStringExtra("bookPath");
        initialPosition = getIntent().getStringExtra("position");
        Uri dataUri = getIntent().getData();

        if (bookPath != null) {
            bookFile = new File(bookPath);
        } else if (dataUri != null) {
            File copied = UriFileCopier.copyFromUri(this, dataUri);
            if (copied != null) {
                bookPath = copied.getAbsolutePath();
                bookFile = copied;
            }
        }

        if (bookFile == null || !bookFile.exists()) {
            Toast.makeText(this, "No se pudo abrir el archivo",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        isPdf = bookPath.toLowerCase().endsWith(".pdf");

        setupDarkMode();
        applyFontSize();

        if (isPdf) {
            setupPDF();
        } else {
            setupEPUB();
        }
    }

    /**
     * Setup PDF reading with the v2.0.2 API.
     * Uses setOnPageChangeListener (v2 API).
     * Verified with JitPack: com.github.barteksc:AndroidPdfViewer:2.0.2
     */
    private void setupPDF() {
        pdfView.setVisibility(View.VISIBLE);

        // Restore the last read page ("page:N"), or the bookmark position
        int startPage = 0;
        String saved = initialPosition != null ? initialPosition : dbHelper.getReadingPosition(bookPath);
        if (saved != null && saved.startsWith("page:")) {
            try {
                startPage = Math.max(0, Integer.parseInt(saved.substring(5)));
            } catch (NumberFormatException ignored) { }
        }
        currentPage = startPage;

        String title = bookFile.getName();
        if (title.toLowerCase().endsWith(".pdf")) {
            title = title.substring(0, title.length() - 4);
        }
        chapterTitleTextView.setText(title);

        // Seekbar navigates PDF pages
        progressSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && progress != currentPage) {
                    pdfView.jumpTo(progress, true);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // PDF viewer setup with v2.0.2 API
        pdfView.fromFile(bookFile)
                .defaultPage(startPage)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .onPageChange(new OnPageChangeListener() {
                    @Override
                    public void onPageChanged(int page, int pageCount) {
                        currentPage = page;
                        pdfPagesForProgress = pageCount;
                        totalPages = pageCount;
                        progressSeek.setMax(Math.max(0, pageCount - 1));
                        progressSeek.setProgress(page);
                        updateProgress();
                        invalidateOptionsMenu();
                    }
                })
                .load();
    }

    /**
     * Setup EPUB reading with chapter navigation and dark mode CSS.
     */
    private void setupEPUB() {
        webView.setVisibility(View.VISIBLE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);

        // Extract EPUB in background thread
        new Thread(() -> {
            try {
                // Remove extractions left over from previous sessions
                File[] oldDirs = getCacheDir().listFiles(
                        (dir, name) -> name.startsWith("epub_"));
                if (oldDirs != null) {
                    for (File old : oldDirs) {
                        deleteRecursive(old);
                    }
                }

                epubExtractDir = new File(getCacheDir(), "epub_" + System.currentTimeMillis());
                if (epubExtractDir.exists()) {
                    deleteRecursive(epubExtractDir);
                }
                epubExtractDir.mkdirs();

                EPUBParser.unzip(bookFile, epubExtractDir);

                // Directory that contains the OPF (e.g. "OEBPS"), used as base
                // for resolving relative links and images.
                String contentDirPath = EPUBParser.findContentDir(epubExtractDir);
                if (contentDirPath != null && !contentDirPath.isEmpty()) {
                    epubContentDir = new File(epubExtractDir, contentDirPath);
                } else {
                    epubContentDir = epubExtractDir;
                }

                // Get spine (reading order)
                epubChapters = EPUBParser.getChapterFiles(epubExtractDir, contentDirPath);

                if (epubChapters == null || epubChapters.isEmpty()) {
                    // Fallback: find all HTML files
                    epubChapters = findAllHTMLFiles(epubExtractDir);
                }

                // Table of contents (used for chapter titles and navigation)
                tocEntries = EPUBParser.extractTOC(epubExtractDir, contentDirPath);

                runOnUiThread(() -> {
                    if (epubChapters == null || epubChapters.isEmpty()) {
                        Toast.makeText(ReaderActivity.this,
                                "EPUB sin contenido válido",
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    currentChapterIndex = 0;
                    String saved = initialPosition != null
                            ? initialPosition
                            : dbHelper.getReadingPosition(bookPath);
                    if (saved != null && saved.startsWith("chapter:")) {
                        try {
                            int chapter = Integer.parseInt(saved.substring(8));
                            if (chapter >= 0 && chapter < epubChapters.size()) {
                                currentChapterIndex = chapter;
                            }
                        } catch (NumberFormatException ignored) { }
                    }

                    updateChapterTitle(currentChapterIndex);
                    renderChapter(currentChapterIndex);

                    progressSeek.setMax(epubChapters.size() - 1);
                    progressSeek.setProgress(currentChapterIndex);
                    progressSeek.setOnSeekBarChangeListener(
                            new SeekBar.OnSeekBarChangeListener() {
                                @Override
                                public void onProgressChanged(SeekBar seekBar,
                                        int progress, boolean fromUser) {
                                    if (fromUser && progress != currentChapterIndex) {
                                        currentChapterIndex = progress;
                                        updateChapterTitle(progress);
                                        renderChapter(progress);
                                    }
                                }

                                @Override
                                public void onStartTrackingTouch(SeekBar seekBar) {}

                                @Override
                                public void onStopTrackingTouch(SeekBar seekBar) {}
                            });

                    totalPages = epubChapters.size();
                    updateProgress();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error setting up EPUB", e);
                runOnUiThread(() -> {
                    Toast.makeText(ReaderActivity.this,
                            "Error al abrir EPUB: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }

    /**
     * Render an EPUB chapter in the WebView.
     */
    private void renderChapter(int index) {
        if (index < 0 || index >= epubChapters.size()) {
            return;
        }

        String chapterPath = epubChapters.get(index);
        String content;
        try {
            content = EPUBParser.readFile(new File(chapterPath));
        } catch (IOException e) {
            content = "<html><body><p>Error al leer el contenido</p></body></html>";
            Log.e(TAG, "Error reading chapter", e);
        }

        if (content == null || content.isEmpty()) {
            content = "<html><body><p>Contenido no disponible</p></body></html>";
        }

        // Clean up HTML content
        content = cleanHTMLContent(content);

        // Base URL must be the chapter's own directory so relative images
        // and internal links resolve correctly inside the WebView.
        File chapterFile = new File(chapterPath);
        String baseUrl = "file://" + chapterFile.getParentFile().getAbsolutePath() + "/";

        final String finalContent = content;
        final String finalBaseUrl = baseUrl;
        runOnUiThread(() -> {
            String css = getCSS();
            String fullHTML = "<html><head>"
                    + "<meta name='viewport' content='width=device-width, "
                    + "initial-scale=1.0'>" + css + "</head>"
                    + "<body>" + finalContent + "</body></html>";

            webView.loadDataWithBaseURL(finalBaseUrl,
                    fullHTML, "text/html", "UTF-8", null);

            // Scroll to top
            webView.scrollTo(0, 0);

            // Update progress
            updateProgress();
        });
    }

    /**
     * Get dark mode CSS for EPUB content.
     */
    private String getCSS() {
        String color;
        if (isDarkMode) {
            color = "#E8E6E3";
        } else {
            color = "#000000";
        }
        String bg = isDarkMode ? "#1a1a1a" : "#ffffff";

        return "<style>body { background: " + bg + "; color: " + color + "; "
                + "font-family: 'Segoe UI', sans-serif; line-height: 1.8; "
                + "padding: 16px; font-size: " + fontSize + "px; } "
                + "a { color: #64b5f6; } img { max-width: 100%; height: auto; } "
                + "h1, h2, h3 { color: " + color + "; } </style>";
    }

    /**
     * Clean up HTML content from EPUB before injecting it into the WebView.
     */
    private String cleanHTMLContent(String content) {
        // Keep only the body content (we provide our own html/head wrapper)
        java.util.regex.Matcher body = java.util.regex.Pattern.compile(
                "<body[^>]*>(.*)</body>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                .matcher(content);
        if (body.find()) {
            content = body.group(1);
        }

        // Remove scripts, stylesheets and inline <style> blocks: they would
        // override the reader theme (dark mode).
        content = content.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "");
        content = content.replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");
        content = content.replaceAll("(?i)<link\\b[^>]*>", "");

        // Drop inline style attributes but keep the tags themselves.
        content = content.replaceAll("(?i)\\sstyle\\s*=\\s*\"[^\"]*\"", "");
        content = content.replaceAll("(?i)\\sstyle\\s*=\\s*'[^']*'", "");

        return content;
    }

    /**
     * Update chapter title display.
     */
    private void updateChapterTitle(int index) {
        if (index >= 0 && index < epubChapters.size()) {
            String name = getChapterName();
            chapterTitleTextView.setText(name != null ? name : "Capítulo " + (index + 1));
        }
    }

    /**
     * Update progress display.
     */
    private void updateProgress() {
        if (isPdf) {
            progressTextView.setText("Página " + (currentPage + 1) + " / " + totalPages);
        } else {
            progressTextView.setText("Capítulo " + (currentChapterIndex + 1) + " / " + totalPages);
        }
        // Save position automatically
        saveReadingPosition(isPdf ? "page:" + currentPage : "chapter:" + currentChapterIndex);
    }

    /**
     * Setup dark mode for the reader.
     */
    private void setupDarkMode() {
        isDarkMode = prefs.getBoolean("dark_mode", true); // Default to dark
        if (isDarkMode) {
            webView.setBackgroundColor(0xFF1a1a1a);
            pdfView.setBackgroundColor(0xFF1a1a1a);
        } else {
            webView.setBackgroundColor(0xFFFFFFFF);
            pdfView.setBackgroundColor(0xFFFFFFFF);
        }
    }

    /**
     * Apply font size to EPUB content.
     */
    private void applyFontSize() {
        if (!isPdf) {
            WebSettings settings = webView.getSettings();
            settings.setDefaultFontSize(fontSize);
        }
    }

    /**
     * Toggle dark mode (made public for ReaderMenu access).
     */
    public void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        prefs.edit().putBoolean("dark_mode", isDarkMode).apply();

        if (isDarkMode) {
            webView.setBackgroundColor(0xFF1a1a1a);
            pdfView.setBackgroundColor(0xFF1a1a1a);
        } else {
            webView.setBackgroundColor(0xFFFFFFFF);
            pdfView.setBackgroundColor(0xFFFFFFFF);
        }

        if (!isPdf) {
            // Re-render with new theme
            renderChapter(currentChapterIndex);
        }
    }

    /**
     * Change font size by delta (made public for ReaderMenu access).
     */
    public void changeFontSize(int delta) {
        fontSize = Math.max(12, Math.min(28, fontSize + delta));
        applyFontSize();
        prefs.edit().putInt("font_size", fontSize).apply();
        if (!isPdf) {
            renderChapter(currentChapterIndex);
        }
    }

    /**
     * Increase font size.
     */
    private void increaseFontSize() {
        changeFontSize(2);
    }

    /**
     * Decrease font size.
     */
    private void decreaseFontSize() {
        changeFontSize(-2);
    }

    /**
     * Save reading position.
     */
    private void saveReadingPosition(String position) {
        if (bookPath != null) {
            dbHelper.saveReadingPosition(bookPath, position);
        }
    }

    /**
     * Get the current reading position.
     */
    private String getReadingPosition() {
        return bookPath != null ? dbHelper.getReadingPosition(bookPath) : null;
    }

    private String currentPositionValue() {
        return isPdf ? "page:" + currentPage : "chapter:" + currentChapterIndex;
    }

    /**
     * Add or remove a bookmark at the current position.
     */
    private void addBookmark() {
        String position = currentPositionValue();

        if (dbHelper.hasBookmark(bookPath, position)) {
            Cursor cursor = dbHelper.getBookmark(bookPath, position);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int idIndex = cursor.getColumnIndex("id");
                    if (idIndex >= 0) {
                        dbHelper.deleteBookmark(cursor.getInt(idIndex));
                    }
                }
                cursor.close();
            }
            Toast.makeText(this, R.string.bookmark_removed,
                    Toast.LENGTH_SHORT).show();
        } else {
            dbHelper.insertBookmark(bookPath, position, "bookmark");
            Toast.makeText(this, R.string.bookmark_added,
                    Toast.LENGTH_SHORT).show();
        }

        invalidateOptionsMenu();
    }

    /**
     * Get the name of the current chapter (used for bookmark labels).
     */
    private String getChapterName() {
        if (epubChapters != null && currentChapterIndex >= 0
                && currentChapterIndex < epubChapters.size()) {
            String chapterPath = epubChapters.get(currentChapterIndex);
            if (chapterPath != null) {
                String fileName = new File(chapterPath).getName();

                // Prefer the title from the table of contents
                if (tocEntries != null) {
                    for (EPUBParser.TocEntry entry : tocEntries) {
                        if (entry.href != null && entry.title != null &&
                                new File(entry.href.split("#")[0]).getName().equals(fileName)) {
                            return entry.title;
                        }
                    }
                }

                int dot = fileName.lastIndexOf('.');
                if (dot > 0) fileName = fileName.substring(0, dot);
                fileName = fileName.replace('_', ' ').replace('-', ' ').trim();
                if (!fileName.isEmpty()) {
                    return fileName.substring(0, 1).toUpperCase() + fileName.substring(1);
                }
            }
        }
        return null;
    }

    /**
     * Find all HTML files recursively in a directory.
     */
    private ArrayList<String> findAllHTMLFiles(File dir) {
        ArrayList<String> files = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return files;
        }

        File[] children = dir.listFiles();
        if (children == null) {
            return files;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(findAllHTMLFiles(child));
            } else if (child.getName().toLowerCase().endsWith(".xhtml") ||
                       child.getName().toLowerCase().endsWith(".html")) {
                files.add(child.getAbsolutePath());
            }
        }

        return files;
    }

    /**
     * Delete a file and all its contents recursively.
     */
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    @Override
    public void onBackPressed() {
        // Chapters are loaded as independent documents; leaving the reader
        // always exits instead of navigating stale WebView history.
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reader_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem bookmarkItem = menu.findItem(R.id.menuBookmark);
        if (bookmarkItem != null) {
            boolean hasBookmark = bookPath != null &&
                    dbHelper.hasBookmark(bookPath, currentPositionValue());
            bookmarkItem.setIcon(hasBookmark ?
                    R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menuBookmark) {
            addBookmark();
            return true;
        } else if (id == R.id.menuToc) {
            showTOC();
            return true;
        } else if (id == R.id.menuFontSizeUp) {
            increaseFontSize();
            return true;
        } else if (id == R.id.menuFontSizeDown) {
            decreaseFontSize();
            return true;
        } else if (id == R.id.menuDarkMode) {
            toggleDarkMode();
            return true;
        } else if (id == R.id.menuSettings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.pauseTimers();
        }
        // PDF v2.0.2 doesn't have onPause() - use invalidate() if needed
        if (isPdf) {
            // Save position on pause
            saveReadingPosition("page:" + currentPage);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.resumeTimers();
        }
        // PDF v2.0.2 doesn't have onResume()
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        if (pdfView != null) {
            pdfView.recycle();
            pdfView = null;
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }

    /**
     * Show the table of contents dialog.
     */
    public void showTOC() {
        if (isPdf) {
            Toast.makeText(this, "Índice no disponible para PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        if (epubExtractDir == null) {
            Toast.makeText(this, "EPUB no extraído aún", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract TOC from EPUB (cached during setup)
        List<EPUBParser.TocEntry> toc = tocEntries;
        if (toc == null || toc.isEmpty()) {
            toc = EPUBParser.extractTOC(
                    epubExtractDir, EPUBParser.findContentDir(epubExtractDir));
        }
        if (toc == null || toc.isEmpty()) {
            Toast.makeText(this, "Este libro no tiene índice", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert to ArrayList for TOCDialog
        ArrayList<TOCDialog.TocEntry> entries = new ArrayList<>();
        for (EPUBParser.TocEntry entry : toc) {
            entries.add(new TOCDialog.TocEntry(entry.title, entry.href, entry.depth));
        }

        // TOCDialog shows itself in its constructor
        new TOCDialog(this, entries);
    }

    /**
     * Navigate to the chapter referenced by a TOC href.
     */
    public void navigateToTocEntry(String href) {
        if (href == null || epubChapters == null || epubChapters.isEmpty()) {
            return;
        }

        String cleanHref = href.split("#")[0];
        while (cleanHref.startsWith("/")) {
            cleanHref = cleanHref.substring(1);
        }

        // Resolve the href against the OPF directory and compare canonical paths
        File target = null;
        try {
            File base = epubContentDir != null ? epubContentDir : epubExtractDir;
            target = new File(base, cleanHref).getCanonicalFile();
            for (int i = 0; i < epubChapters.size(); i++) {
                File chapter = new File(epubChapters.get(i)).getCanonicalFile();
                if (chapter.equals(target)) {
                    openChapter(i);
                    return;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error resolving TOC href: " + href, e);
        }

        // Fallback: match by file name
        String fileName = new File(cleanHref).getName();
        for (int i = 0; i < epubChapters.size(); i++) {
            if (new File(epubChapters.get(i)).getName().equals(fileName)) {
                openChapter(i);
                return;
            }
        }

        Toast.makeText(this, "No se encontró el capítulo", Toast.LENGTH_SHORT).show();
    }

    private void openChapter(int index) {
        currentChapterIndex = index;
        if (progressSeek != null) {
            progressSeek.setProgress(index);
        }
        updateChapterTitle(index);
        renderChapter(index);
    }
}