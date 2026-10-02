package org.umn.lensspent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.local.ThemePreference

/**
 * The one decision the app root makes before it can draw: which theme the stored preference asks for.
 *
 * The phone's own dark-mode setting is an argument rather than a read, so the three cases and the two
 * possible answers can be checked without a device.
 */
class ThemeSelectionTest {

    @Test
    fun `the stored preference decides, and the phone answers only for System`() {
        assertTrue(themeIsDark(ThemePreference.SYSTEM, systemInDarkTheme = true))
        assertFalse(themeIsDark(ThemePreference.SYSTEM, systemInDarkTheme = false))

        // The two explicit settings ignore the phone, which is the whole reason to offer them.
        assertFalse(themeIsDark(ThemePreference.LIGHT, systemInDarkTheme = true))
        assertTrue(themeIsDark(ThemePreference.DARK, systemInDarkTheme = false))
    }
}
