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
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.google.android.material.appbar.AppBarLayout;

import java.io.File;
import org.json.JSONObject;

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

    // Exact position restore (spec S1): scroll fraction inside the chapter.
    // pendingRestoreFraction >= 0 until the saved chapter's WebView finished
    // loading; restoreInFlight covers the retry window until the scroll lands.
    private float pendingRestoreFraction = -1f;
    private int restoreChapterIndex = -1;
    private float currentScrollFraction = 0f;
    private boolean restoreInFlight = false;
    private int restoreGeneration = 0;
    private final Runnable savePositionRunnable = this::saveCurrentReadingPosition;

    // Annotations (spec S1, S3): pending navigation from the annotations list
    private long jumpAnnotationId = -1;
    private boolean annotationsJsReady = false;
    private static final String JS_ANNOTATIONS = "window.HL={"
        + "nodes:function(){var a=[],w=document.createTreeWalker(document.body,4/*SHOW_TEXT*/,null,false);"
        + "while(w.nextNode()){a.push(w.currentNode);}return a;},"
        + "total:function(){var n=this.nodes(),t=0;for(var i=0;i<n.length;i++)t+=n[i].nodeValue.length;return t;},"
        + "loc:function(off){var n=this.nodes(),acc=0;"
        + "for(var i=0;i<n.length;i++){var L=n[i].nodeValue.length;"
        + "if(off<acc+L)return{n:n[i],o:off-acc};acc+=L;}return null;},"
        + "wrap:function(s,e,id,note){try{if(e<=s)return false;this.buildMap();"
        + "var self=this,wrapped=0,n=this.nodes();"
        + "n.forEach(function(node){var p=node.parentElement;"
        + "if(p&&p.classList&&(p.classList.contains('hl')||p.classList.contains('hl-note')))return;"
        + "var so=self.off(node);if(so<0)return;var len=node.nodeValue.length;"
        + "var a=Math.max(so,s),b=Math.min(so+len,e);if(b<=a)return;"
        + "var r=document.createRange();r.setStart(node,a-so);r.setEnd(node,b-so);"
        + "var span=document.createElement('span');"
        + "span.className=note?'hl-note':'hl';span.setAttribute('data-id',id);"
        + "if(note){span.setAttribute('title',note);"
        + "span.setAttribute('data-note',note);"
        + "span.setAttribute('onclick','LectorAnnotations.showNote(this.getAttribute(\\\"data-note\\\"))');}"
        + "try{r.surroundContents(span);self._map=null;wrapped++;}catch(err){}});"
        + "return wrapped>0;}catch(e){return false;}},"
        + "_map:null,"
        + "buildMap:function(){var n=this.nodes(),map=new Map(),acc=0;"
        + "for(var i=0;i<n.length;i++){map.set(n[i],acc);acc+=n[i].nodeValue.length;}"
        + "this._map=map;},"
        + "off:function(node){if(!this._map)this.buildMap();"
        + "var v=this._map.get(node);return v===undefined?-1:v;},"
        + "sel:function(){try{var s=window.getSelection();"
        + "if(!s||s.rangeCount===0||s.isCollapsed)return null;"
        + "var r=s.getRangeAt(0);this.buildMap();"
        + "var A=this.off(r.startContainer),B=this.off(r.endContainer);"
        + "if(A<0||B<0)return null;"
        + "return JSON.stringify({s:A+r.startOffset,e:B+r.endOffset,text:s.toString()});"
        + "}catch(e){return null;}},"
        + "clear:function(){window.getSelection().removeAllRanges();},"
        + "jump:function(id){var el=document.querySelector('[data-id=\"'+id+'\"]');"
        + "if(el){el.scrollIntoView({block:'center'});return true;}return false;}"
        + "};'ok'";

    // PDF state
    private int currentPage = 0;
    private int totalPages = 0;
    private int pdfPagesForProgress = 0;

    // UI state
    private String currentTheme = ReadingTheme.THEME_DARK;
    private boolean isDarkMode = true;
    private int fontSize = 18;
    private boolean isControlsVisible = true;

    private View rootView;
    private AppBarLayout appBarLayout;
    private View bottomControls;
    private View sepiaOverlay;

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        rootView = findViewById(R.id.coordinatorLayout);
        appBarLayout = findViewById(R.id.appBarLayout);
        bottomControls = findViewById(R.id.bottomControls);
        sepiaOverlay = findViewById(R.id.sepiaOverlay);

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
        progressTextView.setOnClickListener(v -> showJumpToDialog());
        chapterTitleTextView = findViewById(R.id.chapterTitle);
        progressBar = findViewById(R.id.progressBar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        dbHelper = new BookmarksDBHelper(this);

        // Restore settings
        currentTheme = ReadingTheme.getTheme(prefs);
        isDarkMode = ReadingTheme.isDarkMode(currentTheme);
        fontSize = prefs.getInt("font_size", 18);
        if (fontSize < 12 || fontSize > 28) fontSize = 18;

        // Get book path from intent
        bookPath = getIntent().getStringExtra("bookPath");
        initialPosition = getIntent().getStringExtra("position");
        jumpAnnotationId = getIntent().getLongExtra("annotationId", -1);
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

        applyThemeColors();
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
        loadPdfDocument(startPage);
    }

    /**
     * Load (or reload) the PDF with the current theme. The viewer applies
     * nightMode() as an inverted-color ColorMatrix at load time, so toggling
     * theme for a PDF means reloading at the current page.
     */
    private void loadPdfDocument(int startPage) {
        boolean isDark = ReadingTheme.isDarkMode(currentTheme);
        pdfView.fromFile(bookFile)
                .defaultPage(startPage)
                .nightMode(isDark)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .onTap(e -> {
                    toggleControls();
                    return true;
                })
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

        // Exact position (spec S1): restore after the chapter finishes loading
        // and keep the saved fraction fresh while the user scrolls.
        webView.addJavascriptInterface(new AnnotationBridge(), "LectorAnnotations");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                applyPendingRestore();
                applyAnnotations();
            }
        });
        webView.setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    refreshProgressLabel();
                    scheduleSavePosition();
                });

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
                    currentScrollFraction = 0f;
                    String saved = initialPosition != null
                            ? initialPosition
                            : dbHelper.getReadingPosition(bookPath);
                    ReadingPosition savedPosition = ReadingPosition.parse(saved);
                    if (savedPosition != null && !savedPosition.isPage
                            && savedPosition.index < epubChapters.size()) {
                        currentChapterIndex = savedPosition.index;
                        currentScrollFraction = savedPosition.fraction;
                        if (savedPosition.fraction > 0f) {
                            pendingRestoreFraction = savedPosition.fraction;
                        }
                        restoreChapterIndex = savedPosition.index;
                    }

                    // Total known before the first render so the label and the
                    // percent never show "Capítulo 1 / 0" (spec S2).
                    totalPages = epubChapters.size();

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

        // Navigating to a new chapter supersedes any pending or running
        // restore so a stale retry can never scroll the wrong chapter.
        restoreGeneration++;
        restoreInFlight = false;
        if (pendingRestoreFraction >= 0 && index != restoreChapterIndex) {
            pendingRestoreFraction = -1f;
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
            String clickScript = "<script>"
                    + "document.addEventListener('click', function(e) {"
                    + "  var el = (e.target && e.target.nodeType === 3) ? e.target.parentElement : e.target;"
                    + "  if (el && el.closest && (el.closest('a') || el.closest('.hl-note'))) return;"
                    + "  var sel = window.getSelection ? window.getSelection() : null;"
                    + "  if (sel && !sel.isCollapsed && sel.toString().trim().length > 0) return;"
                    + "  if (window.LectorAnnotations && window.LectorAnnotations.onContentTapped) {"
                    + "    window.LectorAnnotations.onContentTapped();"
                    + "  }"
                    + "});"
                    + "</script>";

            String fullHTML = "<html><head>"
                    + "<meta name='viewport' content='width=device-width, "
                    + "initial-scale=1.0'>" + css + clickScript + "</head>"
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
     * Get CSS for EPUB content based on current reading theme and font size.
     */
    private String getCSS() {
        String bg;
        String color;
        String link;
        String headings;

        switch (currentTheme) {
            case ReadingTheme.THEME_LIGHT:
                bg = "#FFFFFF";
                color = "#1A1A1A";
                link = "#1976D2";
                headings = "#111111";
                break;
            case ReadingTheme.THEME_SEPIA:
                bg = "#F8F1E3";
                color = "#433422";
                link = "#795548";
                headings = "#2B1D0F";
                break;
            case ReadingTheme.THEME_DARK:
            default:
                bg = "#1A1A1A";
                color = "#E8E6E3";
                link = "#64B5F6";
                headings = "#E8E6E3";
                break;
        }

        return "<style>body { background: " + bg + "; color: " + color + "; "
                + "font-family: 'Segoe UI', sans-serif; line-height: 1.8; "
                + "padding: 16px; font-size: " + fontSize + "px; } "
                + "a { color: " + link + "; } img { max-width: 100%; height: auto; } "
                + "h1, h2, h3, h4, h5, h6 { color: " + headings + "; } "
                + ".hl { background-color: rgba(255,214,0,0.55); border-radius: 2px; } "
                + ".hl-note { background-color: rgba(255,214,0,0.55); "
                + "border-bottom: 2px dotted #c8a600; border-radius: 2px; } "
                + "</style>";
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
     * Refresh the progress label with the percent read so far (spec S2).
     */
    private void refreshProgressLabel() {
        float fraction = pendingRestoreFraction >= 0
                ? currentScrollFraction
                : computeScrollFraction();
        int percent = isPdf
                ? ReadingPosition.percent(currentPage, 1f, totalPages)
                : ReadingPosition.percent(currentChapterIndex, fraction, totalPages);

        String text;
        if (isPdf) {
            text = "Página " + (currentPage + 1) + " / " + totalPages;
        } else {
            text = "Capítulo " + (currentChapterIndex + 1) + " / " + totalPages;
        }
        if (percent >= 0) {
            text += " · " + percent + "%";
        }
        progressTextView.setText(text);
    }

    /**
     * Update progress display and persist the position.
     */
    private void updateProgress() {
        refreshProgressLabel();
        saveCurrentReadingPosition();
    }

    /**
     * Scroll fraction (0..1) of the WebView inside the current chapter
     * (spec S1). 0 when the chapter fits on screen or is not loaded yet.
     */
    private float computeScrollFraction() {
        if (isPdf || webView == null) return 0f;
        int scrollable = scrollableHeight();
        if (scrollable <= 0) return 0f;
        float fraction = (float) webView.getScrollY() / scrollable;
        return Math.max(0f, Math.min(1f, fraction));
    }

    private int scrollableHeight() {
        int contentHeight = (int) (webView.getContentHeight() * webView.getScale());
        return contentHeight - webView.getHeight();
    }

    /**
     * Persist the exact position (spec S1): page for PDF, chapter + fraction
     * for EPUB. Skipped while a restore is pending or in flight so the saved
     * spot is never clobbered by the chapter-start scroll.
     */
    private void saveCurrentReadingPosition() {
        if (bookPath == null || dbHelper == null) return;
        if (isPdf) {
            int percent = ReadingPosition.percent(currentPage, 1f, totalPages);
            dbHelper.saveReadingPosition(bookPath, "page:" + currentPage, percent);
            return;
        }
        if (pendingRestoreFraction >= 0 || restoreInFlight) return;
        currentScrollFraction = computeScrollFraction();
        String value = new ReadingPosition(
                false, currentChapterIndex, currentScrollFraction).format();
        int percent = ReadingPosition.percent(
                currentChapterIndex, currentScrollFraction, totalPages);
        dbHelper.saveReadingPosition(bookPath, value, percent);
    }

    /**
     * Debounced position save (spec S1): scroll events fire far more often
     * than the database write is worth.
     */
    private void scheduleSavePosition() {
        handler.removeCallbacks(savePositionRunnable);
        handler.postDelayed(savePositionRunnable, 700);
    }

    /**
     * Scroll to the saved fraction once the chapter finished loading.
     */
    private void applyPendingRestore() {
        if (pendingRestoreFraction < 0) return;
        float fraction = pendingRestoreFraction;
        pendingRestoreFraction = -1f;
        restoreInFlight = true;
        currentScrollFraction = fraction;
        scrollToFraction(fraction, 0, restoreGeneration);
    }

    /**
     * Scroll to a fraction of the chapter, retrying while the WebView has
     * not laid its content out yet (bounded to ~1s).
     */
    private void scrollToFraction(float fraction, int attempt, int generation) {
        if (webView == null || isFinishing() || generation != restoreGeneration) {
            restoreInFlight = false;
            return;
        }
        int scrollable = scrollableHeight();
        if (scrollable <= 0) {
            if (attempt < 20) {
                handler.postDelayed(
                        () -> scrollToFraction(fraction, attempt + 1, generation), 50);
            } else {
                restoreInFlight = false; // give up: content never became scrollable
            }
            return;
        }
        restoreInFlight = false;
        webView.scrollTo(0, Math.round(fraction * scrollable));
        refreshProgressLabel();
        scheduleSavePosition();
    }

    /**
     * Apply theme colors to content views, toolbars, and system bars.
     */
    private void applyThemeColors() {
        int bgContent;
        int bgBars;
        int primaryText;
        int secondaryText;

        switch (currentTheme) {
            case ReadingTheme.THEME_LIGHT:
                bgContent = 0xFFFFFFFF;
                bgBars = 0xFFF5F5F5;
                primaryText = 0xFF1A1A1A;
                secondaryText = 0xFF666666;
                break;
            case ReadingTheme.THEME_SEPIA:
                bgContent = 0xFFF8F1E3;
                bgBars = 0xFFEFE6D5;
                primaryText = 0xFF433422;
                secondaryText = 0xFF795548;
                break;
            case ReadingTheme.THEME_DARK:
            default:
                bgContent = 0xFF1A1A1A;
                bgBars = 0xFF212121;
                primaryText = 0xFFE8E6E3;
                secondaryText = 0xFFB0B0B0;
                break;
        }

        if (rootView != null) rootView.setBackgroundColor(bgContent);
        if (webView != null) webView.setBackgroundColor(bgContent);
        if (pdfView != null) pdfView.setBackgroundColor(bgContent);

        if (sepiaOverlay != null) {
            sepiaOverlay.setVisibility(isPdf && ReadingTheme.THEME_SEPIA.equals(currentTheme)
                    ? View.VISIBLE : View.GONE);
        }

        if (appBarLayout != null) appBarLayout.setBackgroundColor(bgBars);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(bgBars);
            toolbar.setTitleTextColor(primaryText);
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().setTint(primaryText);
            }
            if (toolbar.getOverflowIcon() != null) {
                toolbar.getOverflowIcon().setTint(primaryText);
            }
        }
        if (bottomControls != null) bottomControls.setBackgroundColor(bgBars);

        if (chapterTitleTextView != null) chapterTitleTextView.setTextColor(primaryText);
        if (progressTextView != null) progressTextView.setTextColor(secondaryText);

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!ReadingTheme.THEME_DARK.equals(currentTheme));
            insetsController.setAppearanceLightNavigationBars(!ReadingTheme.THEME_DARK.equals(currentTheme));
        }

        invalidateOptionsMenu();
    }

    /**
     * Apply a new reading theme and re-render the document.
     */
    public void applyReadingTheme(String theme) {
        currentTheme = ReadingTheme.resolveTheme(theme, null);
        isDarkMode = ReadingTheme.isDarkMode(currentTheme);
        ReadingTheme.setTheme(prefs, currentTheme);

        applyThemeColors();

        if (isPdf) {
            loadPdfDocument(currentPage);
        } else {
            renderChapter(currentChapterIndex);
        }
    }

    /**
     * Show theme selector dialog.
     */
    public void showThemeDialog() {
        CharSequence[] items = {
                getString(R.string.theme_light),
                getString(R.string.theme_sepia),
                getString(R.string.theme_dark)
        };
        int checkedItem = 2; // dark default
        if (ReadingTheme.THEME_LIGHT.equals(currentTheme)) {
            checkedItem = 0;
        } else if (ReadingTheme.THEME_SEPIA.equals(currentTheme)) {
            checkedItem = 1;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.reading_theme)
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    String selected;
                    if (which == 0) {
                        selected = ReadingTheme.THEME_LIGHT;
                    } else if (which == 1) {
                        selected = ReadingTheme.THEME_SEPIA;
                    } else {
                        selected = ReadingTheme.THEME_DARK;
                    }
                    applyReadingTheme(selected);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
     * Toggle dark mode (kept for compatibility with legacy callers).
     */
    public void toggleDarkMode() {
        if (ReadingTheme.THEME_DARK.equals(currentTheme)) {
            applyReadingTheme(ReadingTheme.THEME_LIGHT);
        } else {
            applyReadingTheme(ReadingTheme.THEME_DARK);
        }
    }

    /**
     * Toggle full-screen immersive reading controls.
     */
    public void toggleControls() {
        isControlsVisible = !isControlsVisible;

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (isControlsVisible) {
            if (insetsController != null) {
                insetsController.show(WindowInsetsCompat.Type.systemBars());
            }
            if (appBarLayout != null) {
                appBarLayout.animate().cancel();
                appBarLayout.setVisibility(View.VISIBLE);
                int h = appBarLayout.getHeight() > 0 ? appBarLayout.getHeight() : 150;
                appBarLayout.setTranslationY(-h);
                appBarLayout.animate().translationY(0).setDuration(250).start();
            }
            if (bottomControls != null) {
                bottomControls.animate().cancel();
                bottomControls.setVisibility(View.VISIBLE);
                int h = bottomControls.getHeight() > 0 ? bottomControls.getHeight() : 150;
                bottomControls.setTranslationY(h);
                bottomControls.animate().translationY(0).setDuration(250).start();
            }
        } else {
            if (insetsController != null) {
                insetsController.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                insetsController.hide(WindowInsetsCompat.Type.systemBars());
            }
            if (appBarLayout != null) {
                appBarLayout.animate().cancel();
                int h = appBarLayout.getHeight() > 0 ? appBarLayout.getHeight() : 150;
                appBarLayout.animate()
                        .translationY(-h)
                        .setDuration(250)
                        .withEndAction(() -> appBarLayout.setVisibility(View.GONE))
                        .start();
            }
            if (bottomControls != null) {
                bottomControls.animate().cancel();
                int h = bottomControls.getHeight() > 0 ? bottomControls.getHeight() : 150;
                bottomControls.animate()
                        .translationY(h)
                        .setDuration(250)
                        .withEndAction(() -> bottomControls.setVisibility(View.GONE))
                        .start();
            }
        }
    }

    /**
     * Show jump to page / chapter dialog.
     */
    public void showJumpToDialog() {
        if (isPdf) {
            if (totalPages <= 0) {
                Toast.makeText(this, R.string.reader_error_opening, Toast.LENGTH_SHORT).show();
                return;
            }
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setHint("1 - " + totalPages);
            input.setText(String.valueOf(currentPage + 1));
            input.setSelectAllOnFocus(true);
            input.setPadding(48, 32, 48, 32);

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.jump_to_page_title)
                    .setView(input)
                    .setPositiveButton(R.string.jump_action, (dialog, which) -> {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) return;
                        try {
                            int page = Integer.parseInt(text);
                            if (page < 1 || page > totalPages) {
                                Toast.makeText(this,
                                        getString(R.string.jump_to_invalid_page, totalPages),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                currentPage = page - 1;
                                pdfView.jumpTo(currentPage, true);
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this,
                                    getString(R.string.jump_to_invalid_page, totalPages),
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            if (epubChapters == null || epubChapters.isEmpty()) {
                Toast.makeText(this, "Capítulos no disponibles", Toast.LENGTH_SHORT).show();
                return;
            }
            int totalChapters = epubChapters.size();
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setHint("1 - " + totalChapters);
            input.setText(String.valueOf(currentChapterIndex + 1));
            input.setSelectAllOnFocus(true);
            input.setPadding(48, 32, 48, 32);

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.jump_to_chapter_title)
                    .setView(input)
                    .setPositiveButton(R.string.jump_action, (dialog, which) -> {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) return;
                        try {
                            int chapter = Integer.parseInt(text);
                            if (chapter < 1 || chapter > totalChapters) {
                                Toast.makeText(this,
                                        getString(R.string.jump_to_invalid_chapter, totalChapters),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                loadChapter(chapter - 1);
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this,
                                    getString(R.string.jump_to_invalid_chapter, totalChapters),
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    /**
     * Load an EPUB chapter by index.
     */
    public void loadChapter(int index) {
        openChapter(index);
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
        int iconColor;
        if (ReadingTheme.THEME_LIGHT.equals(currentTheme)) {
            iconColor = 0xFF1A1A1A;
        } else if (ReadingTheme.THEME_SEPIA.equals(currentTheme)) {
            iconColor = 0xFF433422;
        } else {
            iconColor = 0xFFE8E6E3;
        }
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getIcon() != null) {
                item.getIcon().setTint(iconColor);
            }
        }
        MenuItem bookmarkItem = menu.findItem(R.id.menuBookmark);
        if (bookmarkItem != null) {
            boolean hasBookmark = bookPath != null &&
                    dbHelper.hasBookmark(bookPath, currentPositionValue());
            bookmarkItem.setIcon(hasBookmark ?
                    R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            if (bookmarkItem.getIcon() != null) {
                bookmarkItem.getIcon().setTint(hasBookmark ? 0xFFFFA726 : iconColor);
            }
        }
        // Annotations only exist for EPUB (PDFs have no annotations, spec S4)
        MenuItem annotationsItem = menu.findItem(R.id.menuAnnotations);
        if (annotationsItem != null) {
            annotationsItem.setVisible(!isPdf);
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
        } else if (id == R.id.menuAnnotations) {
            Intent intent = new Intent(this, AnnotationsActivity.class);
            intent.putExtra("bookPath", bookPath);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuToc) {
            showTOC();
            return true;
        } else if (id == R.id.menuJumpTo) {
            showJumpToDialog();
            return true;
        } else if (id == R.id.menuFontSizeUp) {
            increaseFontSize();
            return true;
        } else if (id == R.id.menuFontSizeDown) {
            decreaseFontSize();
            return true;
        } else if (id == R.id.menuTheme) {
            showThemeDialog();
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
        // Flush the debounced position save (PDF and EPUB, spec S1).
        handler.removeCallbacks(savePositionRunnable);
        saveCurrentReadingPosition();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.resumeTimers();
        }
        // PDF v2.0.2 doesn't have onResume()
        String savedTheme = ReadingTheme.getTheme(prefs);
        int savedFontSize = prefs.getInt("font_size", 18);

        boolean themeChanged = !savedTheme.equals(currentTheme);
        boolean fontChanged = (savedFontSize != fontSize);

        if (themeChanged || fontChanged) {
            currentTheme = savedTheme;
            isDarkMode = ReadingTheme.isDarkMode(currentTheme);
            fontSize = savedFontSize;
            applyThemeColors();
            applyFontSize();

            if (isPdf) {
                if (themeChanged) {
                    loadPdfDocument(currentPage);
                }
            } else {
                renderChapter(currentChapterIndex);
            }
        }
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

    // ─── Annotations (spec S1, S3) ───

    /** Stable menu item ids for the selection actions. */
    private static final int MENU_ITEM_HIGHLIGHT = 0x00a00001;
    private static final int MENU_ITEM_NOTE = 0x00a00002;

    /**
     * Inject «Resaltar» / «Nota» into a selection menu exactly once (spec S1).
     * Used both by the callback wrapper (pre-build) and by the safety net in
     * onActionModeStarted, so any action-mode flavor ends up with the items.
     */
    private void addAnnotationItems(final android.view.ActionMode mode, Menu menu) {
        if (menu.findItem(MENU_ITEM_HIGHLIGHT) != null) return; // already added
        menu.add(Menu.NONE, MENU_ITEM_HIGHLIGHT, Menu.NONE, "Resaltar")
                .setOnMenuItemClickListener(item -> {
                    captureSelection(null);
                    mode.finish();
                    return true;
                });
        menu.add(Menu.NONE, MENU_ITEM_NOTE, Menu.NONE, "Nota")
                .setOnMenuItemClickListener(item -> {
                    captureSelectionPromptNote();
                    mode.finish();
                    return true;
                });
    }

    /**
     * Add «Resaltar» / «Nota» to the text-selection action mode (EPUB only).
     * Covers every action-mode type; the WebView may start either floating
     * or primary depending on the installed WebView version.
     */
    @Override
    public android.view.ActionMode onWindowStartingActionMode(
            android.view.ActionMode.Callback callback, int type) {
        if (!isPdf && webView != null) {
            return super.onWindowStartingActionMode(
                    new AnnotationActionModeCallback(callback), type);
        }
        return super.onWindowStartingActionMode(callback, type);
    }

    /**
     * Safety net: when the WebView started its action mode without going
     * through our wrapper (or ignored it), add the items here and invalidate
     * so the toolbar rebuilds with them.
     */
    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (!isPdf && webView != null && mode.getMenu() != null
                && mode.getMenu().findItem(MENU_ITEM_HIGHLIGHT) == null) {
            addAnnotationItems(mode, mode.getMenu());
            mode.invalidate();
        }
        super.onActionModeStarted(mode);
    }

    /**
     * Wrap the WebView's selection callback so «Resaltar» / «Nota» exist
     * before the toolbar builds its layout (pre-build injection).
     */
    private class AnnotationActionModeCallback implements android.view.ActionMode.Callback {
        private final android.view.ActionMode.Callback delegate;

        AnnotationActionModeCallback(android.view.ActionMode.Callback delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
            boolean result = delegate.onCreateActionMode(mode, menu);
            addAnnotationItems(mode, menu);
            return result;
        }

        @Override
        public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
            return delegate.onPrepareActionMode(mode, menu);
        }

        @Override
        public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
            return delegate.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(android.view.ActionMode mode) {
            delegate.onDestroyActionMode(mode);
        }
    }

    /**
     * Read the current WebView selection as {start,end,text} and insert it
     * as an annotation (spec S1). promptForNote opens the note dialog first.
     */
    private void captureSelection(String presetNote) {
        webView.evaluateJavascript("window.HL && window.HL.sel()", value -> {
            if (value == null || "null".equals(value)) {
                Toast.makeText(this, "No se pudo capturar la selección",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject sel = new JSONObject(value);
                int s = sel.getInt("s");
                int e = sel.getInt("e");
                String text = sel.optString("text", "");
                if (e <= s || text.trim().isEmpty()) return;
                String note = presetNote != null && !presetNote.trim().isEmpty()
                        ? presetNote.trim() : null;
                long id = dbHelper.insertAnnotation(
                        bookPath, currentChapterIndex, s, e, text, note);
                webView.evaluateJavascript("window.HL.clear()", null);
                applyAnnotations();
                Toast.makeText(this,
                        note != null ? R.string.annotation_note_saved
                                : R.string.annotation_highlighted,
                        Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Log.e(TAG, "Error saving annotation", ex);
            }
        });
    }

    /** Ask for the note text, then save the selection with it (spec S1). */
    private void captureSelectionPromptNote() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.annotation_note_hint);
        input.setSingleLine(false);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.annotation_note_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok,
                        (dlg, w) -> captureSelection(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Re-apply every annotation of the current chapter as visible marks,
     * then jump to the pending annotation if any (spec S3, S4).
     */
    private void applyAnnotations() {
        if (isPdf || webView == null || bookPath == null) return;
        ensureAnnotationsJs();
        android.database.Cursor c = null;
        try {
            c = dbHelper.getAnnotations(bookPath, currentChapterIndex);
            StringBuilder js = new StringBuilder("(function(){try{");
            boolean any = false;
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("id"));
                int s = c.getInt(c.getColumnIndexOrThrow("start_offset"));
                int e = c.getInt(c.getColumnIndexOrThrow("end_offset"));
                String note = c.getString(c.getColumnIndexOrThrow("note"));
                String text = c.getString(c.getColumnIndexOrThrow("text"));
                js.append("window.HL.wrap(").append(s).append(',').append(e)
                        .append(',').append(id).append(',')
                        .append(note == null ? "null" : quoteJs(note))
                        .append(");");
                any = true;
            }
            if (jumpAnnotationId >= 0) {
                js.append("window.HL.jump(").append(jumpAnnotationId).append(");");
                jumpAnnotationId = -1;
            }
            js.append("}catch(e){}})()");
            if (any || js.indexOf("jump(") >= 0) {
                webView.evaluateJavascript(js.toString(), null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying annotations", e);
        } finally {
            if (c != null) c.close();
        }
    }

    private void ensureAnnotationsJs() {
        if (annotationsJsReady) return;
        webView.evaluateJavascript(JS_ANNOTATIONS, null);
        annotationsJsReady = true;
    }

    /** JS string literal with single quotes escaped (note text). */
    private static String quoteJs(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", " ") + "'";
    }

    /**
     * Bridge called from JS when the user interacts with EPUB content.
     */
    private class AnnotationBridge {
        @android.webkit.JavascriptInterface
        public void onContentTapped() {
            runOnUiThread(() -> toggleControls());
        }

        @android.webkit.JavascriptInterface
        public void showNote(final String note) {
            runOnUiThread(() -> new androidx.appcompat.app.AlertDialog.Builder(
                    ReaderActivity.this)
                    .setTitle(R.string.annotation_note_title)
                    .setMessage(note)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        }
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