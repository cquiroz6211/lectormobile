package com.example.lectormobile;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
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

        BookItem(String path, boolean bookmarked, long lastRead, int timesRead) {
            this.path = path;
            this.name = new File(path).getName();
            this.isBookmarked = bookmarked;
            this.lastRead = lastRead;
            this.timesRead = timesRead;
        }
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

        // Load bookmarked paths
        loadBookmarksFromDB();

        // Adapter
        booksAdapter = new BooksAdapter(bookList,
                new BooksAdapter.OnBookClickListener() {
                    @Override
                    public void onBookClick(int position) {
                        BookItem item = bookList.get(position);
                        openBook(item.path);
                    }
                    @Override
                    public void onBookLongClick(int position) {
                        BookItem item = bookList.get(position);
                        showBookOptions(item.path, position);
                    }
                },
                dbHelper
        );
        recyclerView.setAdapter(booksAdapter);

        // FAB to add books
        FloatingActionButton fab = findViewById(R.id.fabAddBook);
        fab.setOnClickListener(v -> pickBook());

        // Handle shared intent
        handleSharedIntent(getIntent());

        // Initial scan
        scanForBooks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarksFromDB();
        scanForBooks();
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
     * Pick a book file from storage (PDF or EPUB).
     */
    private void pickBook() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/pdf",
                "application/epub+zip",
                "*/*"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_BOOK_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_BOOK_REQUEST) {
                Uri uri = data.getData();
                if (uri != null) {
                    File file = UriFileCopier.copyFromUri(this, uri);
                    if (file != null) {
                        scanForBooks();
                        Toast.makeText(this,
                                "Libro agregado: " + file.getName(),
                                Toast.LENGTH_SHORT).show();
                        openBook(file.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * Handle incoming shared PDF/EPUB files.
     */
    private void handleSharedIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (sharedUri != null) {
                File copied = UriFileCopier.copyFromUri(this, sharedUri);
                if (copied != null) {
                    scanForBooks();
                    Toast.makeText(this,
                            "Libro agregado: " + copied.getName(),
                            Toast.LENGTH_SHORT).show();
                    openBook(copied.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Scan external storage root and DCIM for PDF/EPUB files.
     */
    private void scanForBooks() {
        Set<String> currentPaths = new HashSet<>();

        File externalDir = Environment.getExternalStorageDirectory();
        if (externalDir != null && externalDir.exists() && externalDir.canRead()) {
            scanDirectory(externalDir, currentPaths, 0);
        }

        File dcimDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        if (dcimDir != null && dcimDir.exists() && dcimDir.canRead()) {
            scanDirectory(dcimDir, currentPaths, 0);
        }

        // Build book list
        ArrayList<BookItem> books = new ArrayList<>();
        for (String path : currentPaths) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".pdf") || lower.endsWith(".epub")) {
                boolean bookmarked = bookmarkedPaths.contains(path);
                long lastRead = 0;
                int timesRead = 0;

                // Check reading history
                Cursor cursor = dbHelper.getRecentBooks(100);
                if (cursor != null) {
                    int pathIdx = cursor.getColumnIndex("book_path");
                    int lastReadIdx = cursor.getColumnIndex("last_read_at");
                    int timesReadIdx = cursor.getColumnIndex("times_read");
                    while (cursor.moveToNext()) {
                        if (pathIdx >= 0 && path.equals(cursor.getString(pathIdx))) {
                            if (lastReadIdx >= 0) lastRead = cursor.getLong(lastReadIdx);
                            if (timesReadIdx >= 0) timesRead = cursor.getInt(timesReadIdx);
                            break;
                        }
                    }
                    cursor.close();
                }

                books.add(new BookItem(path, bookmarked, lastRead, timesRead));
            }
        }

        bookList = books;
        filterBooks();
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
        Cursor cursor = dbHelper.getBookmarks(null);
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

        booksAdapter.setBooks(filtered);
    }

    /**
     * Show contextual options dialog for a book.
     */
    private void showBookOptions(String path, int position) {
        String name = new File(path).getName();
        String[] options = {
                getString(R.string.action_bookmark),
                getString(R.string.action_delete)
        };

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openBook(path);
                    } else if (which == 1) {
                        deleteBook(path, position);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Remove a book from the list.
     */
    private void deleteBook(String path, int position) {
        bookList.remove(position);
        booksAdapter.notifyItemRemoved(position);
        dbHelper.deleteBookmarksForPath(path);
        dbHelper.deleteHistory(path);
        Toast.makeText(this, "Libro eliminado de la lista",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuSettings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menuBookmarks) {
            startActivity(new Intent(this, BookmarksActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}