package com.example.lectormobile;

import android.content.SharedPreferences;

/**
 * Navigation mode management for LectorMobile (v1.5.0, spec S4).
 * Supports "paginated" (horizontal page-by-page, default) and "continuous" (vertical scroll).
 */
public final class NavigationMode {

    public static final String MODE_PAGINATED = "paginated";
    public static final String MODE_CONTINUOUS = "continuous";

    public static final String PREF_NAVIGATION_MODE = "navigation_mode";

    private NavigationMode() {}

    /**
     * Resolves the navigation mode string ("paginated", "continuous").
     * Defaults to "paginated" if null or unrecognized.
     */
    public static String resolveMode(String mode) {
        if (mode != null && MODE_CONTINUOUS.equalsIgnoreCase(mode.trim())) {
            return MODE_CONTINUOUS;
        }
        return MODE_PAGINATED;
    }

    /**
     * Returns true if mode is paginated.
     */
    public static boolean isPaginated(String mode) {
        return MODE_PAGINATED.equalsIgnoreCase(resolveMode(mode));
    }

    /**
     * Reads the current navigation mode from SharedPreferences.
     */
    public static String getMode(SharedPreferences prefs) {
        if (prefs == null) return MODE_PAGINATED;
        String mode = prefs.getString(PREF_NAVIGATION_MODE, MODE_PAGINATED);
        return resolveMode(mode);
    }

    /**
     * Persists the navigation mode to SharedPreferences.
     */
    public static void setMode(SharedPreferences prefs, String mode) {
        if (prefs == null) return;
        prefs.edit().putString(PREF_NAVIGATION_MODE, resolveMode(mode)).apply();
    }
}
