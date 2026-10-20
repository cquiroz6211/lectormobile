package com.example.lectormobile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * RecyclerView adapter for the book list screen (MainActivity).
 * Uses BookItem objects for enhanced book information display.
 */
public class BooksAdapter extends RecyclerView.Adapter<BooksAdapter.BookViewHolder> {

    public interface OnBookClickListener {
        void onBookClick(int position);
        void onBookLongClick(int position);
    }

    private ArrayList<MainActivity.BookItem> bookItems;
    private OnBookClickListener listener;
    private BookmarksDBHelper dbHelper;

    public BooksAdapter(ArrayList<MainActivity.BookItem> bookItems,
                        OnBookClickListener listener,
                        BookmarksDBHelper dbHelper) {
        this.bookItems = bookItems;
        this.listener = listener;
        this.dbHelper = dbHelper;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        MainActivity.BookItem item = bookItems.get(position);
        String fileName = item.name;

        // Set book name
        holder.bookName.setText(fileName);

        // Set file info (type and read count)
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase();
        String info = ext;
        if (item.timesRead > 0) {
            info += " • " + item.timesRead + " " + (item.timesRead == 1 ? "vez" : "veces");
        }
        holder.bookInfo.setText(info);

        // Reading progress (spec S3): hidden when the book was never opened
        if (item.percent >= 0) {
            holder.bookProgress.setVisibility(View.VISIBLE);
            holder.bookProgressBar.setProgress(item.percent);
            holder.bookPercent.setText(holder.itemView.getContext()
                    .getString(R.string.progress_percent, item.percent));
        } else {
            holder.bookProgress.setVisibility(View.GONE);
        }

        // Set bookmark star status
        boolean hasBookmark = item.isBookmarked;
        if (hasBookmark) {
            holder.bookStar.setImageResource(R.drawable.ic_bookmark);
            holder.bookStar.setColorFilter(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.red));
            holder.bookStar.setVisibility(View.VISIBLE);
        } else {
            holder.bookStar.setImageResource(R.drawable.ic_bookmark_border);
            holder.bookStar.setColorFilter(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.text_hint));
            holder.bookStar.setVisibility(View.VISIBLE);
        }

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookClick(position);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onBookLongClick(position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return bookItems == null ? 0 : bookItems.size();
    }

    public void setBooks(ArrayList<MainActivity.BookItem> items) {
        this.bookItems = items;
        notifyDataSetChanged();
    }

    public MainActivity.BookItem getItemAt(int position) {
        if (bookItems != null && position >= 0 && position < bookItems.size()) {
            return bookItems.get(position);
        }
        return null;
    }

    public class BookViewHolder extends RecyclerView.ViewHolder {
        TextView bookName;
        TextView bookInfo;
        ImageView bookStar;
        View bookProgress;
        ProgressBar bookProgressBar;
        TextView bookPercent;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            bookName = itemView.findViewById(R.id.item_book_name);
            bookInfo = itemView.findViewById(R.id.item_book_info);
            bookStar = itemView.findViewById(R.id.item_book_star);
            bookProgress = itemView.findViewById(R.id.item_book_progress);
            bookProgressBar = itemView.findViewById(R.id.item_book_progress_bar);
            bookPercent = itemView.findViewById(R.id.item_book_percent);
        }
    }
}