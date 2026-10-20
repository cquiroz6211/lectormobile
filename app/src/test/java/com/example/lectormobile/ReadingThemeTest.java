package com.example.lectormobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ReadingTheme} (v1.5.0).
 * Verifies theme resolution, default behavior, backward compatibility with
 * legacy dark_mode, and dark_mode synchronization.
 */
public class ReadingThemeTest {

    @Test
    public void defaultThemeIsDarkWhenBothAreNull() {
        assertEquals(ReadingTheme.THEME_DARK, ReadingTheme.resolveTheme(null, null));
    }

    @Test
    public void resolvesValidThemesDirectly() {
        assertEquals("light", ReadingTheme.resolveTheme("light", true));
        assertEquals("sepia", ReadingTheme.resolveTheme("sepia", true));
        assertEquals("dark", ReadingTheme.resolveTheme("dark", false));
    }

    @Test
    public void trimsAndNormalizesCase() {
        assertEquals("sepia", ReadingTheme.resolveTheme("  SEPIA  ", null));
        assertEquals("light", ReadingTheme.resolveTheme("Light", null));
        assertEquals("dark", ReadingTheme.resolveTheme(" DARK ", null));
    }

    @Test
    public void fallsBackToLegacyDarkModeWhenThemeNotSet() {
        assertEquals(ReadingTheme.THEME_DARK, ReadingTheme.resolveTheme(null, true));
        assertEquals(ReadingTheme.THEME_LIGHT, ReadingTheme.resolveTheme(null, false));
    }

    @Test
    public void fallsBackToLegacyDarkModeWhenThemeIsInvalid() {
        assertEquals(ReadingTheme.THEME_DARK, ReadingTheme.resolveTheme("invalid_theme", true));
        assertEquals(ReadingTheme.THEME_LIGHT, ReadingTheme.resolveTheme("invalid_theme", false));
        assertEquals(ReadingTheme.THEME_DARK, ReadingTheme.resolveTheme("", null));
    }

    @Test
    public void isDarkModeOnlyTrueForDark() {
        assertTrue(ReadingTheme.isDarkMode(ReadingTheme.THEME_DARK));
        assertTrue(ReadingTheme.isDarkMode("DARK"));
        assertFalse(ReadingTheme.isDarkMode(ReadingTheme.THEME_LIGHT));
        assertFalse(ReadingTheme.isDarkMode(ReadingTheme.THEME_SEPIA));
        assertFalse(ReadingTheme.isDarkMode(null));
        assertFalse(ReadingTheme.isDarkMode("other"));
    }
}
