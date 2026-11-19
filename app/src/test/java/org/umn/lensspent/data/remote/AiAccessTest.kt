package org.umn.lensspent.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three gates, as a pure data type.
 *
 * These are the rules the repository consults before every request, so their truth table is worth
 * pinning independently of the HTTP layer.
 */
class AiAccessTest {

    @Test
    fun `a fresh install is off even when the build has a backend`() {
        // The default `UserPreferences` values, as the app actually starts: consent false.
        val access = AiAccess(configured = true, enabled = true, consentGranted = false)

        assertFalse(access.isAllowed)
        assertEquals(AiUnavailableReason.ConsentRequired, access.unavailableReason)
    }

    @Test
    fun `a build with no backend is off regardless of the user's switches`() {
        val access = AiAccess(configured = false, enabled = true, consentGranted = true)

        assertFalse(access.isAllowed)
        assertEquals(AiUnavailableReason.NotConfigured, access.unavailableReason)
    }

    @Test
    fun `the settings switch closes the gate even after consent`() {
        val access = AiAccess(configured = true, enabled = false, consentGranted = true)

        assertFalse(access.isAllowed)
        assertEquals(AiUnavailableReason.DisabledByUser, access.unavailableReason)
    }

    @Test
    fun `only three open gates allow a request`() {
        val access = AiAccess(configured = true, enabled = true, consentGranted = true)

        assertTrue(access.isAllowed)
        assertNull(access.unavailableReason)
    }

    /** A missing backend outranks the user's switches: there is nothing they could turn on. */
    @Test
    fun `reason precedence puts the backend first`() {
        assertEquals(
            AiUnavailableReason.NotConfigured,
            AiAccess(configured = false, enabled = false, consentGranted = false).unavailableReason
        )
    }

    @Test
    fun `every reason has a non-empty sentence`() {
        AiUnavailableReason.entries.forEach { reason ->
            val message = AiAvailabilityMessages.forReason(reason)
            assertTrue("$reason", message.isNotBlank())
        }
        assertTrue(AiAvailabilityMessages.NotConfigured.contains("off"))
        assertTrue(AiAvailabilityMessages.DisabledByUser.contains("Settings"))
        assertTrue(AiAvailabilityMessages.ConsentRequired.contains("review"))
    }

    /**
     * The unprompted default is the important assertion: a data class with defaults must not allow a
     * request, because a default that does would be a transmission nobody chose.
     */
    @Test
    fun `the zero value of AiAccess is closed`() {
        assertFalse(AiAccess().isAllowed)
    }
}
