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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

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
    private BookmarksDBHelper dbHelper;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public BookmarksAdapter(Context context, OnBookmarkClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.bookmarks = new ArrayList<>();
        this.dbHelper = new BookmarksDBHelper(context.getApplicationContext());
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

        holder.bookmarkPosition.setText(formatPosition(item.position));
        holder.bookTitle.setText(getBookTitle(item.path));
        holder.bookmarkTimestamp.setText(formatTimestamp(item.timestamp));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookmarkClick(position);
            }
        });

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
            int idIdx = cursor.getColumnIndex("id");
            int pathIdx = cursor.getColumnIndex("book_path");
            int valueIdx = cursor.getColumnIndex("bookmark_value");
            int labelIdx = cursor.getColumnIndex("label");
            int timestampIdx = cursor.getColumnIndex("timestamp");

            while (cursor.moveToNext()) {
                BookmarkItem item = new BookmarkItem();
                if (idIdx >= 0) item.id = cursor.getLong(idIdx);
                if (pathIdx >= 0) item.path = cursor.getString(pathIdx);
                if (valueIdx >= 0) item.position = cursor.getString(valueIdx);
                if (labelIdx >= 0) item.label = cursor.getString(labelIdx);
                if (timestampIdx >= 0) item.timestamp = cursor.getLong(timestampIdx);
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
     * Get the book path for a bookmark.
     */
    public String getBookPath(int position) {
        if (position >= 0 && position < bookmarks.size()) {
            return bookmarks.get(position).path;
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

    private String formatPosition(String position) {
        if (position == null) return "";
        if (position.startsWith("page:")) {
            try {
                return context.getString(R.string.page_number,
                        Integer.parseInt(position.substring(5)) + 1);
            } catch (NumberFormatException ignored) {
                return position;
            }
        }
        if (position.startsWith("chapter:")) {
            try {
                return context.getString(R.string.chapter_number,
                        Integer.parseInt(position.substring(8)) + 1);
            } catch (NumberFormatException ignored) {
                return position;
            }
        }
        return position;
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "";
        return dateFormat.format(new Date(timestamp));
    }

    /**
     * Get the book title from the file name.
     */
    private String getBookTitle(String path) {
        if (path != null) {
            String name = new File(path).getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
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
        long timestamp;
    }

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
