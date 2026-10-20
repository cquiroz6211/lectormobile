package com.example.lectormobile;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Main library screen — shows books with bookmarks status and reading history.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PICK_BOOK_REQUEST = 1001;

    private RecyclerView recyclerView;
    private BooksAdapter booksAdapter;
    private ArrayList<BookItem> bookList;
    private BookmarksDBHelper dbHelper;
    private CoverStore coverStore;
    private Set<String> bookmarkedPaths;
    private SearchView searchView;
    private String currentSearchQuery = "";

    /**
     * Simple data class to hold book information.
     */
    public static class BookItem {
        String path;
        String name;
        boolean isBookmarked;
        long lastRead;
        int timesRead;
        /** Reading progress percent 0..100; -1 = never opened (spec S3). */
        int percent = -1;

        BookItem(String path, boolean bookmarked, long lastRead, int timesRead) {
            this.path = path;
            this.name = new File(path).getName();
            this.isBookmarked = bookmarked;
            this.lastRead = lastRead;
            this.timesRead = timesRead;
        }
    }

    /**
     * File picker for PDF/EPUB books.
     * OpenDocument allows filtering by several MIME types (GetContent only
     * accepts a single one), so EPUB files are selectable too.
     */
    private final ActivityResultLauncher<String[]> pickBookLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;

                String mime = null;
                try {
                    mime = getContentResolver().getType(uri);
                } catch (Exception ignored) { }

                if (!isBookUri(uri, mime)) {
                    Toast.makeText(MainActivity.this,
                            "Selecciona un archivo PDF o EPUB",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                File copied = UriFileCopier.copyFromUri(this, uri);
                if (copied == null) {
                    Toast.makeText(MainActivity.this,
                            "No se pudo importar el archivo",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                scanForBooksSafe();
                Toast.makeText(MainActivity.this,
                        "Libro agregado: " + copied.getName(),
                        Toast.LENGTH_SHORT).show();
                openBook(copied.getAbsolutePath());
            });

    private boolean isBookUri(Uri uri, String mime) {
        if (mime != null) {
            if (mime.contains("pdf") || mime.contains("epub")) return true;
        }
        String name = uri.getLastPathSegment();
        if (name == null) return false;
        name = name.toLowerCase();
        return name.endsWith(".pdf") || name.endsWith(".epub");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        bookList = new ArrayList<>();
        bookmarkedPaths = new HashSet<>();
        dbHelper = new BookmarksDBHelper(this);

        // Search View
        searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText.toLowerCase();
                filterBooks();
                return true;
            }
        });
        searchView.setIconifiedByDefault(false);

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Adapter FIRST (filterBooks() uses it)
        booksAdapter = new BooksAdapter(bookList,
                new BooksAdapter.OnBookClickListener() {
                    @Override
                    public void onBookClick(int position) {
                        BookItem item = booksAdapter.getItemAt(position);
                        if (item != null) {
                            openBook(item.path);
                        }
                    }
                    @Override
                    public void onBookLongClick(int position) {
                        BookItem item = booksAdapter.getItemAt(position);
                        if (item != null) {
                            showBookOptions(item.path);
                        }
                    }
                },
                dbHelper
        );
        coverStore = new CoverStore(getApplicationContext());
        booksAdapter.setCoverStore(coverStore);
        recyclerView.setAdapter(booksAdapter);

        // Load bookmarked paths (after adapter exists)
        loadBookmarksFromDB();

        // FAB to add books
        FloatingActionButton fab = findViewById(R.id.fabAddBook);
        fab.setOnClickListener(v -> pickBook());

        // Handle shared intent
        handleSharedIntent(getIntent());

        // Initial scan (best-effort, never crash)
        scanForBooksSafe();
    }

    /**
     * Safe version of scanForBooks that never crashes even without storage permission.
     * On Android 13+, listFiles() on root storage may throw SecurityException.
     */
    private void scanForBooksSafe() {
        new Thread(() -> {
            ArrayList<BookItem> books;
            try {
                books = buildBookList();
            } catch (Exception e) {
                android.util.Log.w(TAG, "Storage scan skipped: " + e.getMessage());
                books = new ArrayList<>();
            }
            final ArrayList<BookItem> result = books;
            runOnUiThread(() -> {
                bookList = result;
                filterBooks();
            });
        }, "book-scan").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarksFromDB();
        scanForBooksSafe();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedIntent(intent);
    }

    /**
     * Open a book in ReaderActivity and record reading history.
     */
    private void openBook(String path) {
        // Record reading history
        dbHelper.recordRead(path);

        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("bookPath", path);
        startActivity(intent);
    }

    /**
     * Pick a book file from storage.
     * Uses GetContent — no storage permission needed.
     */
    private void pickBook() {
        pickBookLauncher.launch(new String[]{
                "application/pdf",
                "application/epub+zip",
                "application/epub"
        });
    }

    /**
     * Handle incoming VIEW/SEND intents with PDF or EPUB files.
     */
    private void handleSharedIntent(Intent intent) {
        if (intent == null) return;

        Uri uri = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        }

        if (uri == null) {
            return;
        }

        String mime = null;
        try {
            mime = getContentResolver().getType(uri);
        } catch (Exception ignored) { }

        if (!isBookUri(uri, mime)) {
            return;
        }

        // Copy off the main thread: shared PDFs can be large.
        final Uri bookUri = uri;
        new Thread(() -> {
            File copied = UriFileCopier.copyFromUri(MainActivity.this, bookUri);
            runOnUiThread(() -> {
                if (copied == null) {
                    Toast.makeText(MainActivity.this,
                            "No se pudo importar el archivo",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(MainActivity.this,
                        "Libro agregado: " + copied.getName(),
                        Toast.LENGTH_SHORT).show();
                openBook(copied.getAbsolutePath());
            });
        }, "shared-import").start();
    }

    /**
     * Build the list of available books. Runs on a background thread.
     */
    private ArrayList<BookItem> buildBookList() {
        Set<String> currentPaths = new HashSet<>();

        // Books imported through the picker live in the app's private storage
        File booksDir = new File(getFilesDir(), "books");
        scanDirectory(booksDir, currentPaths, 0);

        // App-specific external storage (survives as long as the app is installed)
        File externalFiles = getExternalFilesDir(null);
        if (externalFiles != null) {
            scanDirectory(new File(externalFiles, "books"), currentPaths, 0);
        }

        // Legacy shared storage: only readable with permission (Android <= 12)
        File externalDir = Environment.getExternalStorageDirectory();
        if (externalDir != null && externalDir.exists() && externalDir.canRead()) {
            scanDirectory(externalDir, currentPaths, 0);
        }

        File dcimDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        if (dcimDir != null && dcimDir.exists() && dcimDir.canRead()) {
            scanDirectory(dcimDir, currentPaths, 0);
        }

        // Reading history, loaded once (avoids one query per book)
        HashMap<String, long[]> history = new HashMap<>();
        Cursor cursor = dbHelper.getRecentBooks(1000);
        if (cursor != null) {
            int pathIdx = cursor.getColumnIndex("book_path");
            int lastReadIdx = cursor.getColumnIndex("last_read_at");
            int timesReadIdx = cursor.getColumnIndex("times_read");
            while (cursor.moveToNext()) {
                if (pathIdx < 0) break;
                String path = cursor.getString(pathIdx);
                long lastRead = lastReadIdx >= 0 ? cursor.getLong(lastReadIdx) : 0;
                long timesRead = timesReadIdx >= 0 ? cursor.getInt(timesReadIdx) : 0;
                history.put(path, new long[]{lastRead, timesRead});
            }
            cursor.close();
        }

        // Reading progress percent per book (spec S3)
        HashMap<String, Integer> progress = dbHelper.getAllProgress();

        // Build book list
        ArrayList<BookItem> books = new ArrayList<>();
        for (String path : currentPaths) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".pdf") || lower.endsWith(".epub")) {
                boolean bookmarked = bookmarkedPaths.contains(path);
                long[] stats = history.get(path);
                long lastRead = stats != null ? stats[0] : 0;
                int timesRead = stats != null ? (int) stats[1] : 0;
                BookItem item = new BookItem(path, bookmarked, lastRead, timesRead);
                Integer percent = progress.get(path);
                item.percent = percent != null ? percent : -1;
                books.add(item);
            }
        }

        return books;
    }

    /**
     * Recursively scan a directory for book files (max 2 levels deep).
     */
    private void scanDirectory(File dir, Set<String> files, int depth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || depth > 2) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile()) {
                String name = child.getName().toLowerCase();
                if (name.endsWith(".pdf") || name.endsWith(".epub")) {
                    files.add(child.getAbsolutePath());
                }
            } else if (child.isDirectory()) {
                scanDirectory(child, files, depth + 1);
            }
        }
    }

    /**
     * Load bookmarked book paths from the database.
     */
    private void loadBookmarksFromDB() {
        bookmarkedPaths.clear();
        Cursor cursor = dbHelper.getAllBookmarks();
        if (cursor != null) {
            int pathIdx = cursor.getColumnIndex("book_path");
            if (pathIdx >= 0) {
                while (cursor.moveToNext()) {
                    bookmarkedPaths.add(cursor.getString(pathIdx));
                }
            }
            cursor.close();
        }
        filterBooks();
    }

    /**
     * Filter books based on search query.
     */
    private void filterBooks() {
        ArrayList<BookItem> filtered = new ArrayList<>();
        String query = currentSearchQuery.trim().toLowerCase();

        for (BookItem item : bookList) {
            if (query.isEmpty() || item.name.toLowerCase().contains(query)) {
                filtered.add(item);
            }
        }

        // "Último leído" order (spec S3): most recently read first; ties and
        // never-opened books fall back to case-insensitive alphabetical.
        java.util.Collections.sort(filtered, (a, b) -> {
            if (a.lastRead != b.lastRead) {
                return Long.compare(b.lastRead, a.lastRead);
            }
            return a.name.compareToIgnoreCase(b.name);
        });

        if (booksAdapter != null) {
            booksAdapter.setBooks(filtered);
        }
        TextView empty = findViewById(R.id.emptyLibrary);
        if (empty != null) {
            empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Show contextual options dialog for a book.
     */
    private void showBookOptions(String path) {
        String name = new File(path).getName();
        String[] options = {
                getString(R.string.action_open),
                getString(R.string.action_delete)
        };

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openBook(path);
                    } else if (which == 1) {
                        confirmDeleteBook(path);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeleteBook(String path) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_book_title)
                .setMessage(R.string.delete_book_message)
                .setPositiveButton(R.string.action_delete,
                        (dialog, which) -> deleteBook(path))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Remove a book, its database rows and (only for imported copies) the file.
     */
    private void deleteBook(String path) {
        File file = new File(path);

        // Delete only copies stored inside the app. Files from shared storage
        // are left untouched.
        File booksDir = new File(getFilesDir(), "books");
        try {
            String filePath = file.getCanonicalPath();
            String booksPath = booksDir.getCanonicalPath();
            if (filePath.startsWith(booksPath + File.separator)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (IOException ignored) { }

        dbHelper.deleteBookmarksForPath(path);
        dbHelper.deleteReadingPosition(path);
        dbHelper.deleteHistory(path);
        if (coverStore != null) {
            coverStore.invalidate(path);
        }

        for (int i = 0; i < bookList.size(); i++) {
            if (path.equals(bookList.get(i).path)) {
                bookList.remove(i);
                break;
            }
        }
        filterBooks();

        Toast.makeText(this, R.string.book_deleted, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_bookmarks) {
            startActivity(new Intent(this, BookmarksActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}