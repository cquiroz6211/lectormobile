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
 * RecyclerView adapter for the "Notas y subrayados" list (AnnotationsActivity).
 */
public class AnnotationsAdapter
        extends RecyclerView.Adapter<AnnotationsAdapter.AnnotationViewHolder> {

    public interface OnAnnotationClickListener {
        void onAnnotationClick(int position);
        void onDeleteClick(int position, View view);
    }

    private Context context;
    private ArrayList<AnnotationItem> annotations;
    private OnAnnotationClickListener listener;
    private BookmarksDBHelper dbHelper;

    public AnnotationsAdapter(Context context, OnAnnotationClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.annotations = new ArrayList<>();
        this.dbHelper = new BookmarksDBHelper(context.getApplicationContext());
    }

    @NonNull
    @Override
    public AnnotationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_annotation, parent, false);
        return new AnnotationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AnnotationViewHolder holder, int position) {
        AnnotationItem item = annotations.get(position);

        holder.annotationText.setText(item.text);

        if (item.note != null && !item.note.trim().isEmpty()) {
            holder.annotationNote.setVisibility(View.VISIBLE);
            holder.annotationNote.setText(item.note);
        } else {
            holder.annotationNote.setVisibility(View.GONE);
            holder.annotationNote.setText(null);
        }

        holder.annotationBook.setText(getBookFileName(item.path));
        holder.annotationChapter.setText(
                context.getString(R.string.annotation_chapter, item.chapterIndex + 1));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAnnotationClick(position);
            }
        });

        holder.annotationText.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAnnotationClick(position);
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
        return annotations == null ? 0 : annotations.size();
    }

    /**
     * Set annotations from a Cursor.
     */
    public void setAnnotations(Cursor cursor) {
        annotations.clear();
        if (cursor != null) {
            int idIdx = cursor.getColumnIndex("id");
            int pathIdx = cursor.getColumnIndex("book_path");
            int chapterIdx = cursor.getColumnIndex("chapter_index");
            int textIdx = cursor.getColumnIndex("text");
            int noteIdx = cursor.getColumnIndex("note");

            while (cursor.moveToNext()) {
                AnnotationItem item = new AnnotationItem();
                if (idIdx >= 0) item.id = cursor.getLong(idIdx);
                if (pathIdx >= 0) item.path = cursor.getString(pathIdx);
                if (chapterIdx >= 0) item.chapterIndex = cursor.getInt(chapterIdx);
                if (textIdx >= 0) item.text = cursor.getString(textIdx);
                if (noteIdx >= 0) item.note = cursor.getString(noteIdx);
                annotations.add(item);
            }
            cursor.close();
        }
        notifyDataSetChanged();
    }

    /**
     * Get the annotation id by position.
     */
    public long getAnnotationId(int position) {
        if (position >= 0 && position < annotations.size()) {
            return annotations.get(position).id;
        }
        return -1;
    }

    /**
     * Get the book path for an annotation.
     */
    public String getBookPath(int position) {
        if (position >= 0 && position < annotations.size()) {
            return annotations.get(position).path;
        }
        return null;
    }

    /**
     * Get the chapter index (0-based) for an annotation.
     */
    public int getChapterIndex(int position) {
        if (position >= 0 && position < annotations.size()) {
            return annotations.get(position).chapterIndex;
        }
        return 0;
    }

    /**
     * Delete an annotation by position (no confirmation, spec S4).
     */
    public void deleteAnnotation(int position) {
        if (position >= 0 && position < annotations.size()) {
            AnnotationItem item = annotations.get(position);
            if (item != null && item.id > 0) {
                dbHelper.deleteAnnotation(item.id);
                annotations.remove(position);
                notifyItemRemoved(position);
            }
        }
    }

    /**
     * Get the book file name (without extension) from the path.
     */
    private String getBookFileName(String path) {
        if (path != null) {
            String name = new File(path).getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
        return "Libro";
    }

    /**
     * Simple data class for annotation items.
     */
    static class AnnotationItem {
        long id;
        String path;
        int chapterIndex;
        String text;
        String note;
    }

    public class AnnotationViewHolder extends RecyclerView.ViewHolder {
        TextView annotationText;
        TextView annotationNote;
        TextView annotationBook;
        TextView annotationChapter;
        ImageButton deleteButton;

        public AnnotationViewHolder(@NonNull View itemView) {
            super(itemView);
            annotationText = itemView.findViewById(R.id.item_annotation_text);
            annotationNote = itemView.findViewById(R.id.item_annotation_note);
            annotationBook = itemView.findViewById(R.id.item_annotation_book);
            annotationChapter = itemView.findViewById(R.id.item_annotation_chapter);
            deleteButton = itemView.findViewById(R.id.item_annotation_delete);
        }
    }
}
