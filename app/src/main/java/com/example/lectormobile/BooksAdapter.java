package com.example.lectormobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private CoverStore coverStore;

    /** Cover decode/generation runs off the main thread (spec S3). */
    private final ExecutorService coverExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Books already tried this session: never re-attempt a failed cover. */
    private final Set<String> coverAttempted = new HashSet<>();
    /**
     * Decoded covers kept in memory (optimization S2): rebinds while
     * scrolling bind instantly instead of re-decoding the file every time.
     * 8 MB budget ≈ 60-70 covers at ~120 KB each.
     */
    private final SizeLruCache<String, Bitmap> coverCache =
            new SizeLruCache<>(8 * 1024 * 1024, Bitmap::getByteCount);

    public BooksAdapter(ArrayList<MainActivity.BookItem> bookItems,
                        OnBookClickListener listener,
                        BookmarksDBHelper dbHelper) {
        this.bookItems = bookItems;
        this.listener = listener;
        this.dbHelper = dbHelper;
    }

    /** Provide the cover store (app context) for async cover loading. */
    public void setCoverStore(CoverStore store) {
        this.coverStore = store;
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

        // Cover: fallback icon immediately, real cover when ready (spec S3).
        // The holder tag guards against recycled views getting a stale cover.
        holder.bookCover.setTag(item.path);
        holder.bookCover.setImageResource(R.drawable.ic_pdf);
        holder.bookCover.setColorFilter(ContextCompat.getColor(
                holder.itemView.getContext(), R.color.primary));
        loadCover(holder, item.path);

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

    /**
     * Load the cover for a book off the main thread. Binds only if the
     * holder still shows the same book path (spec S3). Generation is
     * attempted once per session per book; decoded bitmaps are reused from
     * the memory cache (optimization S2).
     */
    private void loadCover(BookViewHolder holder, String bookPath) {
        if (coverStore == null || bookPath == null) return;

        Bitmap cached = coverCache.get(bookPath);
        if (cached != null) {
            bindCover(holder, bookPath, cached);
            return;
        }

        synchronized (coverAttempted) {
            if (coverAttempted.contains(bookPath)
                    && coverStore.peek(bookPath) == null) {
                return; // already failed this session: keep the icon
            }
            coverAttempted.add(bookPath);
        }
        coverExecutor.execute(() -> {
            File cover = coverStore.peek(bookPath);
            if (cover == null) {
                cover = coverStore.getOrCreate(bookPath);
            }
            if (cover == null) return;
            Bitmap bmp = decodeCover(cover);
            if (bmp == null) return;
            coverCache.put(bookPath, bmp);
            mainHandler.post(() -> bindCover(holder, bookPath, bmp));
        });
    }

    /**
     * Binds only if the holder still shows the same book. Cached bitmaps are
     * never recycled: the cache owns them until they are evicted.
     */
    private void bindCover(BookViewHolder holder, String bookPath, Bitmap bmp) {
        if (bookPath.equals(holder.bookCover.getTag())) {
            holder.bookCover.setColorFilter(null);
            holder.bookCover.setImageTintList(null);
            holder.bookCover.setImageBitmap(bmp);
        }
    }

    /** Decode a cover file downsampled to ~2x the 48x64dp view. */
    private static Bitmap decodeCover(File file) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            opts.inSampleSize = Math.max(1, Math.min(opts.outWidth, opts.outHeight) / 128);
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }

    public class BookViewHolder extends RecyclerView.ViewHolder {
        TextView bookName;
        TextView bookInfo;
        ImageView bookStar;
        ImageView bookCover;
        View bookProgress;
        ProgressBar bookProgressBar;
        TextView bookPercent;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            bookName = itemView.findViewById(R.id.item_book_name);
            bookInfo = itemView.findViewById(R.id.item_book_info);
            bookStar = itemView.findViewById(R.id.item_book_star);
            bookCover = itemView.findViewById(R.id.item_book_cover);
            bookProgress = itemView.findViewById(R.id.item_book_progress);
            bookProgressBar = itemView.findViewById(R.id.item_book_progress_bar);
            bookPercent = itemView.findViewById(R.id.item_book_percent);
        }
    }
}