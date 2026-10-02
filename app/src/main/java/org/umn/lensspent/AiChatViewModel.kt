package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.lensspent.data.remote.AiInsightsRepository
import org.umn.lensspent.data.remote.AiInsightsResult

/**
 * The line the chat always ends with, whether or not an answer ever arrives.
 *
 * It is a constant rather than a string in the layout so the exact wording can be asserted in a test:
 * a disclaimer that quietly changes is worse than none at all.
 */
const val AiInsightsDisclaimer =
    "AI is a tool and can make mistakes. This information is for general insights, not " +
        "professional financial advice."

/** Who said it. There are only ever two turns in this conversation. */
enum class AiChatAuthor { User, Assistant }

/** One bubble. [isError] only changes how the assistant's turn is drawn, never where it sits. */
data class AiChatMessage(
    val author: AiChatAuthor,
    val text: String,
    val isError: Boolean = false
)

/** Everything the chat screen draws from. */
data class AiChatUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val draft: String = "",
    val isAwaitingReply: Boolean = false
) {

    /** Blank input and a reply already in flight both mean there is nothing to send. */
    val canSend: Boolean get() = !isAwaitingReply && draft.isNotBlank()
}

/**
 * Drives the AI Insights chat.
 *
 * Thin on purpose: it owns the transcript and the "waiting" flag, and hands every question to
 * [AiInsightsRepository], which is where the ledger is read and DeepSeek is called. Holding the
 * transcript here means a rotation or a step into another screen does not lose the answers.
 */
class AiChatViewModel(private val insights: AiInsightsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiChatUiState(messages = listOf(AiChatMessage(AiChatAuthor.Assistant, GreetingMessage)))
    )
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    fun onDraftChange(draft: String) = _uiState.update { it.copy(draft = draft) }

    /**
     * Sends the draft.
     *
     * The user's bubble and the waiting state are published together, before the call starts, so the
     * question appears the instant it is asked and the loading indicator covers the whole wait.
     */
    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        val question = state.draft.trim()

        _uiState.update {
            it.copy(
                messages = it.messages + AiChatMessage(AiChatAuthor.User, question),
                draft = "",
                isAwaitingReply = true
            )
        }

        viewModelScope.launch {
            val reply = insights.ask(question)
            _uiState.update { current ->
                current.copy(
                    messages = current.messages + reply.toChatMessage(),
                    isAwaitingReply = false
                )
            }
        }
    }

    private companion object {
        const val GreetingMessage =
            "Ask me about your spending - what you have spent this month, which category is growing, " +
                "or where you could cut back. I read your recent transactions to answer."
    }
}

/**
 * Both outcomes are the assistant's turn; a failure is only styled differently.
 *
 * Left visible to the tests on purpose: it is the rule that decides whether bad news reads as the
 * assistant talking or as the app breaking, and it should not need a ViewModel to be checked.
 */
internal fun AiInsightsResult.toChatMessage(): AiChatMessage = when (this) {
    is AiInsightsResult.Success -> AiChatMessage(AiChatAuthor.Assistant, answer)
    is AiInsightsResult.Failure -> AiChatMessage(AiChatAuthor.Assistant, reason, isError = true)
}
