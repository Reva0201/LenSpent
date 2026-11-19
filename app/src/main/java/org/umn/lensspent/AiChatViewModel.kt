package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.remote.AiAccess
import org.umn.lensspent.data.remote.AiAvailabilityMessages
import org.umn.lensspent.data.remote.AiInsightsRepository
import org.umn.lensspent.data.remote.AiInsightsResult
import org.umn.lensspent.data.remote.AiUnavailableReason

/**
 * The line the chat always ends with, whether or not an answer ever arrives.
 *
 * It is a constant rather than a string in the layout so the exact wording can be asserted in a test:
 * a disclaimer that quietly changes is worse than none at all.
 */
const val AiInsightsDisclaimer =
    "AI is a tool and can make mistakes. This information is for general insights, not " +
        "professional financial advice."

/**
 * The disclosure shown before the first question, and re-openable from Settings.
 *
 * It names the third party, the data that leaves, and the data that does not - in that order, because
 * the first question a person has is "who gets it". Asserted by a test so the promise cannot drift
 * from `toInsightMonths`, which is what actually decides the payload.
 */
const val AiPrivacyDisclosureTitle = "Before you use AI Insights"

val AiPrivacyDisclosureLines: List<String> = listOf(
    "AI Insights is optional. Everything else in LensSpent works offline.",
    "When you ask a question, the app sends your typed question and monthly spending totals broken " +
        "down by category to a secure server operated for this app, which forwards them to DeepSeek " +
        "to write an answer.",
    "Receipt photos, OCR text, notes, merchant names, individual transactions, your name and any " +
        "database or capture identifiers are never sent.",
    "No API key is stored in the app. Answers are general insights, not financial advice.",
    "You can turn AI networking off at any time in Settings; nothing is sent while it is off."
)

/** Who said it. There are only ever two turns in this conversation. */
enum class AiChatAuthor { User, Assistant }

/** One bubble. [isError] only changes how the assistant's turn is drawn, never where it sits. */
data class AiChatMessage(
    val author: AiChatAuthor,
    val text: String,
    val isError: Boolean = false
)

/**
 * Why the input is unavailable, if it is.
 *
 * [notice] is null when AI may be used. When it is not null the screen shows it above the input, so
 * an unavailable feature never looks like a broken one. [isKnown] is false until the first access
 * read lands: the input stays quiet and disabled rather than flashing a banner that may be wrong.
 * [needsConsent] is the one "unavailable" state where a tap still does something useful: it opens
 * the disclosure, so the user is never left with a dead button on an otherwise available build.
 */
data class AiChatAvailability(
    val notice: String? = null,
    val needsConsent: Boolean = false,
    val isKnown: Boolean = false
) {
    val isAvailable: Boolean get() = isKnown && notice == null
}

/** Everything the chat screen draws from. */
data class AiChatUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val draft: String = "",
    val isAwaitingReply: Boolean = false,
    val availability: AiChatAvailability = AiChatAvailability(),
    /** True while the disclosure dialog is up. */
    val isShowingDisclosure: Boolean = false
) {

    /**
     * Whether a tap on Send leads anywhere.
     *
     * True when the feature is available, and also when the only thing standing in the way is the
     * user's own consent - that tap opens the disclosure and then sends the question. A missing
     * backend or a switched-off feature stays false: there is nothing the tap could do.
     */
    val canSend: Boolean
        get() = !isAwaitingReply &&
            draft.isNotBlank() &&
            (availability.isAvailable || availability.needsConsent)
}

/**
 * Drives the AI Insights chat.
 *
 * Thin on purpose: it owns the transcript and the "waiting" flag, and hands every question to
 * [AiInsightsRepository], which is where the ledger is read and the request is made. Holding the
 * transcript here means a rotation or a step into another screen does not lose the answers.
 *
 * It also owns the two consent interactions, because "accepting the disclosure" and "sending the
 * first question" have to be one uninterrupted flow: the gate is opened by the dialog's button and
 * the question that was waiting behind it is sent immediately.
 */
class AiChatViewModel(
    private val insights: AiInsightsRepository,
    private val preferences: UserPreferencesRepository,
    private val access: kotlinx.coroutines.flow.Flow<AiAccess>
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiChatUiState(messages = listOf(AiChatMessage(AiChatAuthor.Assistant, GreetingMessage)))
    )
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    /** The live gate, so a switch flipped in Settings updates this screen without a restart. */
    private val liveAccess: StateFlow<AiAccess?> = access.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionStopTimeoutMillis),
        initialValue = null
    )

    init {
        viewModelScope.launch {
            liveAccess.collect { current ->
                _uiState.update { it.copy(availability = current.toAvailability()) }
            }
        }
    }

    fun onDraftChange(draft: String) = _uiState.update { it.copy(draft = draft) }

    /** Opens the disclosure. Also used by Settings to re-read what the feature sends. */
    fun onShowDisclosure() = _uiState.update { it.copy(isShowingDisclosure = true) }

    fun onDismissDisclosure() = _uiState.update { it.copy(isShowingDisclosure = false) }

    /**
     * Records the user's acceptance, then sends whatever was already typed.
     *
     * Consent is written first and the send happens only after the write returns: a request that left
     * before the acceptance landed would be exactly the transmission the dialog exists to prevent.
     * The local availability is updated optimistically because the DataStore re-emission is
     * asynchronous, and a question typed before the tap should not be silently dropped.
     */
    fun onConsentAccepted() {
        viewModelScope.launch {
            preferences.setAiConsentGranted(true)
            val current = liveAccess.value
            _uiState.update { state ->
                state.copy(
                    availability = current
                        ?.copy(consentGranted = true)
                        ?.toAvailability()
                        ?: state.availability,
                    isShowingDisclosure = false
                )
            }
            send()
        }
    }

    /**
     * Sends the draft.
     *
     * The user's bubble and the waiting state are published together, before the call starts, so the
     * question appears the instant it is asked and the loading indicator covers the whole wait.
     */
    fun send() {
        val state = _uiState.value
        if (!state.canSend) return

        // Consent is a precondition of the request, not of the button: a tap with consent still
        // pending opens the disclosure, and `onConsentAccepted` sends the typed question afterwards.
        if (state.availability.needsConsent) {
            _uiState.update { it.copy(isShowingDisclosure = true) }
            return
        }

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
                "or where you could cut back. I read monthly totals by category to answer."
    }
}

/**
 * Turns the three gates into what the screen should say.
 *
 * Extracted from the ViewModel so the mapping - and the fact that a missing backend outranks every
 * other reason - is a plain function a test can call.
 */
internal fun AiAccess?.toAvailability(): AiChatAvailability {
    if (this == null) {
        // The store has not answered yet. Treating that as available would flash an input that cannot
        // send; treating it as unavailable with a message would flash a banner that may be wrong.
        // Unknown means disabled and silent.
        return AiChatAvailability(isKnown = false)
    }
    return if (isAllowed) {
        AiChatAvailability(isKnown = true)
    } else {
        val reason = unavailableReason
        AiChatAvailability(
            notice = reason?.let(AiAvailabilityMessages::forReason),
            needsConsent = reason == AiUnavailableReason.ConsentRequired,
            isKnown = true
        )
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
