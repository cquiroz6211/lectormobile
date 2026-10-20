package com.example.lectormobile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Horizontal RecyclerView adapter for the "Recent Books" section.
 * Shows the last N books the user read.
 */
public class RecentBooksAdapter extends RecyclerView.Adapter<RecentBooksAdapter.ViewHolder> {

    private ArrayList<MainActivity.BookItem> items;
    private OnBookClickListener listener;

    public RecentBooksAdapter(ArrayList<MainActivity.BookItem> items, OnBookClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MainActivity.BookItem item = items.get(position);
        holder.bookName.setText(item.name);
        holder.bookCount.setText("Lecturas: " + item.timesRead);

        // Set bookmark status
        if (item.isBookmarked) {
            holder.bookmarkIcon.setImageResource(R.drawable.ic_bookmark);
        } else {
            holder.bookmarkIcon.setImageResource(R.drawable.ic_bookmark_border);
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public interface OnBookClickListener {
        void onBookClick(int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView bookName;
        TextView bookCount;
        ImageView bookmarkIcon;

        public ViewHolder(View itemView) {
            super(itemView);
            bookName = itemView.findViewById(R.id.recentBookTitle);
            bookCount = itemView.findViewById(R.id.recentBookCount);
            bookmarkIcon = itemView.findViewById(R.id.recentBookBookmark);
        }
    }
}