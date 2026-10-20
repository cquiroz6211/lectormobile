package com.example.lectormobile;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;

/**
 * Table of contents dialog for EPUB books.
 * Shows the chapter list and jumps to the selected one.
 */
public class TOCDialog {

    private final ReaderActivity activity;
    private final ArrayList<TocEntry> tocEntries;

    TOCDialog(ReaderActivity activity, ArrayList<TocEntry> toc) {
        this.activity = activity;
        this.tocEntries = toc;
        show();
    }

    public void show() {
        if (tocEntries == null || tocEntries.isEmpty()) {
            return;
        }

        CharSequence[] titles = new CharSequence[tocEntries.size()];
        for (int i = 0; i < tocEntries.size(); i++) {
            titles[i] = tocEntries.get(i).title;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Índice del libro")
                .setItems(titles, (dialog, which) -> {
                    if (which >= 0 && which < tocEntries.size()) {
                        activity.navigateToTocEntry(tocEntries.get(which).href);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Simple wrapper class for TOC entries.
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
