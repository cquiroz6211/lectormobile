package com.example.lectormobile;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Table of Contents popup for EPUB books.
 * Shows a list of chapters that the user can select to jump to.
 */
public class TOCDialog {

    private ReaderActivity activity;
    private ArrayList<TocEntry> tocEntries;

    TOCDialog(ReaderActivity activity, ArrayList<TocEntry> toc) {
        this.activity = activity;
        this.tocEntries = toc;
        show();
    }

    public void show() {
        final ArrayList<TocEntry> tocList = tocEntries;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("📚 Índice del libro");
        builder.setMessage("Selecciona un capítulo para navegar");

        // Create a simple list adapter
        ArrayAdapter<TocEntry> adapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_list_item_1,
                        tocList
                ) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        TextView textView = (TextView) super.getView(position, convertView, parent);
                        textView.setTextColor(activity.getResources().getColor(R.color.text_primary));
                        textView.setBackgroundColor(activity.getResources().getColor(R.color.surface));
                        return textView;
                    }
                };

        builder.setAdapter(adapter, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                if (which >= 0 && which < tocList.size()) {
                    TocEntry entry = tocList.get(which);
                    activity.navigateToTocEntry(entry.title);
                }
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    /**
     * Simple wrapper class to make TOC entries work with the ArrayAdapter.
     */
    static class TocEntry {
        String title;
        String href;
        int depth;

        TocEntry(String title, String href, int depth) {
            this.title = title;
            this.href = href;
            this.depth = depth;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}