package org.umn.lensspent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.remote.AiAccess
import org.umn.lensspent.data.remote.AiAvailabilityMessages
import org.umn.lensspent.data.remote.AiInsightsResult

/**
 * The chat's own rules: when a question may be sent, how an outcome is styled, and the disclaimer.
 *
 * None of this needs an Android device or a ViewModel - the state, the mapping and the wording are all
 * plain values - so they are pinned here rather than by reading the screen.
 */
class AiChatStateTest {

    private val available = AiChatAvailability(isKnown = true)

    @Test
    fun `a question can only be sent when there is one and none is in flight`() {
        assertTrue(
            AiChatUiState(draft = "Where did my money go?", availability = available).canSend
        )
        assertFalse(
            "whitespace is not a question",
            AiChatUiState(draft = "   ", availability = available).canSend
        )
        assertFalse(AiChatUiState(draft = "", availability = available).canSend)
        assertFalse(
            "the send button stays quiet while an answer is on its way",
            AiChatUiState(
                draft = "And last week?",
                isAwaitingReply = true,
                availability = available
            ).canSend
        )
    }

    /**
     * The gate and the button are the same rule. An unavailable feature must not offer a send the
     * repository would refuse, even with a perfectly good question typed.
     */
    @Test
    fun `an unavailable feature cannot send, whatever is typed`() {
        val notice = AiChatAvailability(
            notice = AiAvailabilityMessages.NotConfigured,
            isKnown = true
        )

        assertFalse(AiChatUiState(draft = "Where did my money go?", availability = notice).canSend)
    }

    /**
     * Consent is the one unavailable state a tap can resolve: it opens the disclosure rather than
     * leaving the user with a disabled button and no explanation.
     */
    @Test
    fun `a pending consent still offers the tap that opens the disclosure`() {
        val pendingConsent = AiChatAvailability(
            notice = AiAvailabilityMessages.ConsentRequired,
            needsConsent = true,
            isKnown = true
        )

        assertTrue(AiChatUiState(draft = "Where did my money go?", availability = pendingConsent).canSend)
        assertFalse(pendingConsent.isAvailable)
    }

    /** Before the first store read lands, the input is quiet rather than wrongly enabled. */
    @Test
    fun `an unknown access state is not sendable`() {
        assertFalse(AiChatUiState(draft = "Hello?").canSend)
        assertTrue(AiChatAvailability().notice == null)
        assertFalse(AiChatAvailability().isAvailable)
    }

    @Test
    fun `an answer is the assistant's turn, and a failure is the same turn in red`() {
        val answered = AiInsightsResult.Success("You spent **Rp 975.000**.").toChatMessage()
        val refused = AiInsightsResult.Failure("Could not reach the AI service.").toChatMessage()

        assertEquals(AiChatAuthor.Assistant, answered.author)
        assertEquals("You spent **Rp 975.000**.", answered.text)
        assertFalse(answered.isError)

        assertEquals("bad news is still the assistant talking", AiChatAuthor.Assistant, refused.author)
        assertEquals("Could not reach the AI service.", refused.text)
        assertTrue(refused.isError)
    }

    @Test
    fun `the disclaimer is the required sentence`() {
        assertEquals(
            "AI is a tool and can make mistakes. This information is for general insights, not " +
                "professional financial advice.",
            AiInsightsDisclaimer
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Access -> what the screen says.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an open gate leaves the input available and says nothing`() {
        val availability = AiAccess(
            configured = true,
            enabled = true,
            consentGranted = true
        ).toAvailability()

        assertTrue(availability.isAvailable)
        assertNull(availability.notice)
    }

    @Test
    fun `a missing backend is explained rather than left as a dead input`() {
        val availability = AiAccess(configured = false).toAvailability()

        assertFalse(availability.isAvailable)
        assertEquals(AiAvailabilityMessages.NotConfigured, availability.notice)
        assertFalse(availability.needsConsent)
    }

    @Test
    fun `a missing consent offers the disclosure instead of a request`() {
        val availability = AiAccess(
            configured = true,
            enabled = true,
            consentGranted = false
        ).toAvailability()

        assertFalse(availability.isAvailable)
        assertEquals(AiAvailabilityMessages.ConsentRequired, availability.notice)
        assertTrue("the screen must be able to open the dialog", availability.needsConsent)
    }

    @Test
    fun `the switch being off is reported as the user's own choice`() {
        val availability = AiAccess(
            configured = true,
            enabled = false,
            consentGranted = true
        ).toAvailability()

        assertEquals(AiAvailabilityMessages.DisabledByUser, availability.notice)
        assertFalse(availability.needsConsent)
    }

    /**
     * The disclosure is the contract with the user, so its claims are asserted: it must name the
     * third party, the aggregate-only payload, the exclusions, and the switch.
     */
    @Test
    fun `the disclosure names the third party, the payload and the exclusions`() {
        val text = AiPrivacyDisclosureLines.joinToString(" ")

        assertTrue(text.contains("DeepSeek"))
        assertTrue(text.contains("monthly spending totals"))
        // The user's own typed question is transmitted too, so the disclosure must say so - a claim
        // that only aggregates leave would be inaccurate.
        assertTrue("the disclosure must name the question: $text", text.contains("typed question"))
        listOf("Receipt photos", "OCR text", "notes", "merchant names", "database").forEach { excluded ->
            assertTrue("the disclosure must exclude $excluded: $text", text.contains(excluded))
        }
        assertTrue("the disclosure must mention the setting", text.contains("turn AI networking off"))
        assertTrue(AiPrivacyDisclosureLines.size >= 4)
    }

    /** The disclosure must not repeat the old absolute claim that nothing ever leaves the device. */
    @Test
    fun `the disclosure does not claim everything always stays on device`() {
        val text = AiPrivacyDisclosureLines.joinToString(" ").lowercase()
        assertFalse(text, text.contains("never leaves this device"))
        assertFalse(text, text.contains("nothing is ever sent"))
    }
}
