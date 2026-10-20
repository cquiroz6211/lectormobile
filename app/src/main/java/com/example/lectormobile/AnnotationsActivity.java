package com.example.lectormobile;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * AnnotationsActivity — "Notas y subrayados" list (spec S4).
 * Shows all highlights/notes for the current book or all books when
 * no "bookPath" extra is provided. Tap opens the reader at the chapter
 * and jumps to the annotation.
 */
public class AnnotationsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyState;
    private AnnotationsAdapter annotationsAdapter;
    private BookmarksDBHelper dbHelper;
    private String currentBookPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_annotations);

        Toolbar toolbar = findViewById(R.id.annotations_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        dbHelper = new BookmarksDBHelper(this);
        currentBookPath = getIntent().getStringExtra("bookPath");

        emptyState = findViewById(R.id.annotations_empty_state);
        recyclerView = findViewById(R.id.annotations_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        annotationsAdapter = new AnnotationsAdapter(this,
                new AnnotationsAdapter.OnAnnotationClickListener() {
            @Override
            public void onAnnotationClick(int position) {
                // Open the reader at the annotation's chapter and jump to the mark
                String path = annotationsAdapter.getBookPath(position);
                if (path == null) path = currentBookPath;
                if (path != null) {
                    Intent intent = new Intent(AnnotationsActivity.this, ReaderActivity.class);
                    intent.putExtra("bookPath", path);
                    intent.putExtra("position",
                            "chapter:" + annotationsAdapter.getChapterIndex(position));
                    intent.putExtra("annotationId",
                            annotationsAdapter.getAnnotationId(position));
                    startActivity(intent);
                }
            }

            @Override
            public void onDeleteClick(int position, View view) {
                // Delete immediately, no confirmation dialog (spec S4, keep simple)
                annotationsAdapter.deleteAnnotation(position);
                updateEmptyState();
            }
        });

        recyclerView.setAdapter(annotationsAdapter);

        loadAnnotations();
    }

    private void loadAnnotations() {
        Cursor cursor = dbHelper.getAllAnnotations(currentBookPath);

        if (cursor != null && cursor.getCount() > 0) {
            annotationsAdapter.setAnnotations(cursor);
        } else {
            annotationsAdapter.setAnnotations(null);
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (emptyState != null) {
            emptyState.setVisibility(
                    annotationsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
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
        loadAnnotations();
    }
}
