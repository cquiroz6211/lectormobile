package com.example.lectormobile;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * BookmarksActivity — Shows all bookmarks for the current book or all books.
 */
public class BookmarksActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BookmarksAdapter bookmarksAdapter;
    private BookmarksDBHelper dbHelper;
    private String currentBookPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        dbHelper = new BookmarksDBHelper(this);
        currentBookPath = getIntent().getStringExtra("bookPath");

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        bookmarksAdapter = new BookmarksAdapter(this, new BookmarksAdapter.OnBookmarkClickListener() {
            @Override
            public void onBookmarkClick(int position) {
                // Open the book at the bookmark position
                String bookmarkValue = bookmarksAdapter.getBookmarkValue(position);
                if (currentBookPath != null) {
                    Intent intent = new Intent(BookmarksActivity.this, ReaderActivity.class);
                    intent.putExtra("bookPath", currentBookPath);
                    intent.putExtra("position", bookmarkValue);
                    startActivity(intent);
                }
            }

            @Override
            public void onDeleteClick(int position, View view) {
                bookmarksAdapter.deleteBookmark(position);
                Toast.makeText(BookmarksActivity.this,
                        getString(R.string.bookmark_removed),
                        Toast.LENGTH_SHORT).show();
            }
        });

        recyclerView.setAdapter(bookmarksAdapter);

        // Load bookmarks
        loadBookmarks();
    }

    private void loadBookmarks() {
        Cursor cursor;
        if (currentBookPath != null) {
            cursor = dbHelper.getBookmarks(currentBookPath);
        } else {
            cursor = dbHelper.getAllBookmarks();
        }

        if (cursor != null && cursor.getCount() > 0) {
            bookmarksAdapter.setBookmarks(cursor);
        } else {
            bookmarksAdapter.setBookmarks(null);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarks();
    }
}