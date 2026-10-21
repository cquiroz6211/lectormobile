package com.example.lectormobile;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

/**
 * Popup menu for font size selection and theme toggle.
 */
public class ReaderMenu extends PopupMenu {

    private ReaderActivity activity;

    public ReaderMenu(ReaderActivity activity, View anchor) {
        super(anchor.getContext(), anchor);
        this.activity = activity;

        // Add custom menu items
        Menu menu = getMenu();
        menu.add("A-").setIcon(R.drawable.ic_page);
        menu.add("A+").setIcon(R.drawable.ic_page);
        menu.add("🎨 Tema de lectura");
        menu.add("📖 Modo de lectura");
        menu.add("📚 Índice");

        // Set item click listener
        setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String title = item.getTitle().toString();
                if (title.equals("A-")) {
                    activity.changeFontSize(-2);
                } else if (title.equals("A+")) {
                    activity.changeFontSize(2);
                } else if (title.contains("Tema")) {
                    activity.showThemeDialog();
                } else if (title.contains("Modo")) {
                    activity.showNavigationModeDialog();
                } else if (title.contains("Índice")) {
                    activity.showTOC();
                }
                return true;
            }
        });

        show();
    }
}