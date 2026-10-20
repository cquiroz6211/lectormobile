package com.example.lectormobile;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

/**
 * RecyclerView adapter for the bookmarks list (BookmarksActivity).
 */
public class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder> {

    public interface OnBookmarkClickListener {
        void onBookmarkClick(int position);
        void onDeleteClick(int position, View view);
    }

    private Context context;
    private ArrayList<BookmarkItem> bookmarks;
    private OnBookmarkClickListener listener;

    public BookmarksAdapter(Context context, OnBookmarkClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.bookmarks = new ArrayList<>();
    }

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark, parent, false);
        return new BookmarkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        BookmarkItem item = bookmarks.get(position);

        holder.bookmarkPosition.setText("Posición: " + item.position);
        holder.bookTitle.setText(getBookTitle(item.path));
        holder.bookmarkTimestamp.setText(item.timestamp);

        holder.bookmarkPosition.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(position);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(position, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return bookmarks == null ? 0 : bookmarks.size();
    }

    /**
     * Set bookmarks from a Cursor.
     */
    public void setBookmarks(Cursor cursor) {
        bookmarks.clear();
        if (cursor != null) {
            int pathIdx = cursor.getColumnIndex("book_path");
            int valueIdx = cursor.getColumnIndex("bookmark_value");
            int labelIdx = cursor.getColumnIndex("label");
            int timestampIdx = cursor.getColumnIndex("timestamp");

            while (cursor.moveToNext()) {
                BookmarkItem item = new BookmarkItem();
                if (pathIdx >= 0) item.path = cursor.getString(pathIdx);
                if (valueIdx >= 0) item.position = cursor.getString(valueIdx);
                if (labelIdx >= 0) item.label = cursor.getString(labelIdx);
                if (timestampIdx >= 0) {
                    long ts = cursor.getLong(timestampIdx);
                    item.timestamp = String.valueOf(ts);
                }
                bookmarks.add(item);
            }
            cursor.close();
        }
        notifyDataSetChanged();
    }

    /**
     * Get a bookmark value by position.
     */
    public String getBookmarkValue(int position) {
        if (position >= 0 && position < bookmarks.size()) {
            return bookmarks.get(position).position;
        }
        return null;
    }

    /**
     * Delete a bookmark by position.
     */
    public void deleteBookmark(int position) {
        if (position >= 0 && position < bookmarks.size()) {
            BookmarkItem item = bookmarks.get(position);
            if (item != null && item.id > 0) {
                dbHelper.deleteBookmark((int) item.id);
                bookmarks.remove(position);
                notifyItemRemoved(position);
            }
        }
    }

    /**
     * Get the book title from the file name.
     */
    private String getBookTitle(String path) {
        if (path != null) {
            File file = new File(path);
            return file.getName();
        }
        return "Libro";
    }

    /**
     * Simple data class for bookmark items.
     */
    static class BookmarkItem {
        long id;
        String path;
        String position;
        String label;
        String timestamp;
    }

    /**
     * Helper class for storing DB reference.
     */
    private BookmarksDBHelper dbHelper = new BookmarksDBHelper(context);

    public class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView bookmarkPosition;
        TextView bookTitle;
        TextView bookmarkTimestamp;
        ImageButton deleteButton;

        public BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            bookmarkPosition = itemView.findViewById(R.id.item_bookmark_position);
            bookTitle = itemView.findViewById(R.id.item_bookmark_book_title);
            bookmarkTimestamp = itemView.findViewById(R.id.item_bookmark_timestamp);
            deleteButton = itemView.findViewById(R.id.item_bookmark_delete);
        }
    }
}