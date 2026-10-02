package org.umn.lensspent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.remote.AiInsightsResult

/**
 * The chat's own rules: when a question may be sent, how an outcome is styled, and the disclaimer.
 *
 * None of this needs an Android device or a ViewModel - the state, the mapping and the wording are all
 * plain values - so they are pinned here rather than by reading the screen.
 */
class AiChatStateTest {

    @Test
    fun `a question can only be sent when there is one and none is in flight`() {
        assertTrue(AiChatUiState(draft = "Where did my money go?").canSend)
        assertFalse("whitespace is not a question", AiChatUiState(draft = "   ").canSend)
        assertFalse(AiChatUiState(draft = "").canSend)
        assertFalse(
            "the send button stays quiet while an answer is on its way",
            AiChatUiState(draft = "And last week?", isAwaitingReply = true).canSend
        )
    }

    @Test
    fun `an answer is the assistant's turn, and a failure is the same turn in red`() {
        val answered = AiInsightsResult.Success("You spent **Rp 975.000**.").toChatMessage()
        val refused = AiInsightsResult.Failure("Could not reach DeepSeek.").toChatMessage()

        assertEquals(AiChatAuthor.Assistant, answered.author)
        assertEquals("You spent **Rp 975.000**.", answered.text)
        assertFalse(answered.isError)

        assertEquals("bad news is still the assistant talking", AiChatAuthor.Assistant, refused.author)
        assertEquals("Could not reach DeepSeek.", refused.text)
        assertTrue(refused.isError)
    }

    /**
     * The wording is asserted, not just its presence: the feature is required to say this, and a
     * disclaimer that quietly changes is worse than none at all.
     */
    @Test
    fun `the disclaimer is the required sentence`() {
        assertEquals(
            "AI is a tool and can make mistakes. This information is for general insights, not " +
                "professional financial advice.",
            AiInsightsDisclaimer
        )
    }
}
