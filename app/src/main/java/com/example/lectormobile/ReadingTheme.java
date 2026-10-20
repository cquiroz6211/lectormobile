package com.example.lectormobile;

import android.content.SharedPreferences;

/**
 * Reading theme management for LectorMobile (v1.5.0).
 * Supports "light", "sepia", and "dark" themes while maintaining backward
 * compatibility with legacy "dark_mode" boolean preferences.
 */
public final class ReadingTheme {

    public static final String THEME_LIGHT = "light";
    public static final String THEME_SEPIA = "sepia";
    public static final String THEME_DARK = "dark";

    public static final String PREF_READING_THEME = "reading_theme";
    public static final String PREF_DARK_MODE = "dark_mode";

    private ReadingTheme() {}

    /**
     * Resolves the active theme string ("light", "sepia", "dark").
     * If savedTheme is valid, returns it.
     * Otherwise derives from legacyDarkMode (true -> "dark", false -> "light").
     * Defaults to "dark" if neither is provided.
     */
    public static String resolveTheme(String savedTheme, Boolean legacyDarkMode) {
        if (savedTheme != null) {
            String clean = savedTheme.trim().toLowerCase();
            if (THEME_LIGHT.equals(clean) || THEME_SEPIA.equals(clean) || THEME_DARK.equals(clean)) {
                return clean;
            }
        }
        if (legacyDarkMode != null) {
            return legacyDarkMode ? THEME_DARK : THEME_LIGHT;
        }
        return THEME_DARK;
    }

    /**
     * Legacy dark_mode sync boolean: true for dark, false for light and sepia.
     */
    public static boolean isDarkMode(String theme) {
        return THEME_DARK.equalsIgnoreCase(theme);
    }

    /**
     * Reads the current reading theme from SharedPreferences.
     */
    public static String getTheme(SharedPreferences prefs) {
        if (prefs == null) return THEME_DARK;
        String savedTheme = prefs.contains(PREF_READING_THEME)
                ? prefs.getString(PREF_READING_THEME, null) : null;
        Boolean legacy = prefs.contains(PREF_DARK_MODE)
                ? prefs.getBoolean(PREF_DARK_MODE, true) : null;
        return resolveTheme(savedTheme, legacy);
    }

    /**
     * Persists the reading theme to SharedPreferences and keeps legacy "dark_mode" in sync.
     */
    public static void setTheme(SharedPreferences prefs, String theme) {
        if (prefs == null) return;
        String resolved = resolveTheme(theme, null);
        prefs.edit()
                .putString(PREF_READING_THEME, resolved)
                .putBoolean(PREF_DARK_MODE, isDarkMode(resolved))
                .apply();
    }
}
