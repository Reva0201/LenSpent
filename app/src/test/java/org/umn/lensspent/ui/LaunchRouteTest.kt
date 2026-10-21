package org.umn.lensspent.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.umn.lensspent.data.local.UserPreferences

/**
 * The one decision the app root makes about where a launch begins.
 *
 * The store is an argument rather than a read, so the three states that matter can be checked without
 * a device - and the third one is the regression: before the first read lands the root knows nothing,
 * and "nothing" used to be answered as "onboarding was not finished", which is what a fresh install
 * looks like rather than what a returning user is. The welcome pages came back on every cold start
 * with the name and the theme still on disk.
 */
class LaunchRouteTest {

    @Test
    fun `a returning user opens at the dashboard`() {
        val stored = UserPreferences(hasCompletedOnboarding = true, username = "Mao Mao")
        assertEquals(AppRoutes.Home, launchRoute(stored))
    }

    @Test
    fun `a first run opens at onboarding`() {
        assertEquals(AppRoutes.Onboarding, launchRoute(UserPreferences()))
    }

    @Test
    fun `an unread store opens nowhere yet`() {
        assertNull(launchRoute(null))
    }

    /** The name is not part of this decision - onboarding is about the flag alone. */
    @Test
    fun `a half-written profile still counts as unfinished onboarding`() {
        assertEquals(AppRoutes.Onboarding, launchRoute(UserPreferences(username = "Sari")))
    }
}
