package com.example.lectormobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for NavigationMode (spec S4).
 */
public class NavigationModeTest {

    @Test
    public void testDefaultIsPaginated() {
        assertEquals(NavigationMode.MODE_PAGINATED, NavigationMode.resolveMode(null));
        assertEquals(NavigationMode.MODE_PAGINATED, NavigationMode.resolveMode(""));
        assertEquals(NavigationMode.MODE_PAGINATED, NavigationMode.resolveMode("unknown"));
        assertTrue(NavigationMode.isPaginated(null));
        assertTrue(NavigationMode.isPaginated("paginated"));
    }

    @Test
    public void testContinuousMode() {
        assertEquals(NavigationMode.MODE_CONTINUOUS, NavigationMode.resolveMode("continuous"));
        assertEquals(NavigationMode.MODE_CONTINUOUS, NavigationMode.resolveMode("CONTINUOUS"));
        assertFalse(NavigationMode.isPaginated("continuous"));
    }
}
