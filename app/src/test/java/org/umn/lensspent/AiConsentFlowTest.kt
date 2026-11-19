package org.umn.lensspent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.remote.AiAccess
import org.umn.lensspent.data.remote.AiAvailabilityMessages
import org.umn.lensspent.data.remote.AiInsightsRepository
import org.umn.lensspent.data.remote.AiInsightsResult
import org.umn.lensspent.data.remote.ChatChoice
import org.umn.lensspent.data.remote.ChatCompletionRequest
import org.umn.lensspent.data.remote.ChatCompletionResponse
import org.umn.lensspent.data.remote.ChatMessage
import org.umn.lensspent.data.remote.DeepSeekApi
import org.umn.lensspent.data.repository.StubTransactionRepository

/**
 * The consent flow, end to end at the ViewModel level.
 *
 * The repository tests already prove that no request leaves without all three gates open. What this
 * adds is the interaction the user actually performs: tap Send with consent pending, read the
 * disclosure, accept, and have the typed question sent - with the acceptance written to the store
 * before the request, never after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiConsentFlowTest {

    private val access = MutableStateFlow(
        AiAccess(configured = true, enabled = true, consentGranted = false)
    )

    private val api = RecordingApi()

    private lateinit var preferences: UserPreferencesRepository
    private lateinit var viewModel: AiChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        preferences = UserPreferencesRepository(
            org.umn.lensspent.ui.settings.TestPreferencesStore.create()
        )
        viewModel = AiChatViewModel(
            insights = AiInsightsRepository(
                transactions = StubTransactionRepository(),
                api = api,
                // Exactly what AppContainer does: the gate is read from the store at request time, so
                // accepting the disclosure takes effect for the very next question.
                access = {
                    AiAccess(
                        configured = access.value.configured,
                        enabled = access.value.enabled,
                        consentGranted = preferences.aiConsentGranted.first()
                    )
                }
            ),
            preferences = preferences,
            access = access
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing before consent does not send, but offers the disclosure`() {
        viewModel.onDraftChange("Where does my money go?")

        viewModel.send()

        assertTrue(
            "the tap must open the disclosure",
            viewModel.uiState.value.isShowingDisclosure
        )
        assertTrue("nothing may be sent yet", api.requests.isEmpty())
        assertEquals(
            "the typed question is kept, not cleared",
            "Where does my money go?",
            viewModel.uiState.value.draft
        )
    }

    @Test
    fun `accepting writes consent before the request goes out`() {
        viewModel.onDraftChange("Where does my money go?")
        viewModel.send()

        viewModel.onConsentAccepted()

        // Consent is a real DataStore write, so it completes asynchronously. Waiting for the store -
        // and only then for the request - is what proves the ordering: the request cannot exist until
        // the gate was actually opened.
        assertEquals(true, kotlinx.coroutines.runBlocking { awaitConsent(true) })
        assertEquals("exactly one request", 1, kotlinx.coroutines.runBlocking { awaitRequests(1) })
        assertFalse(viewModel.uiState.value.isShowingDisclosure)
        assertTrue(
            "the user's question became a bubble",
            viewModel.uiState.value.messages.any { it.text == "Where does my money go?" }
        )
        assertTrue(
            "the answer arrived",
            viewModel.uiState.value.messages.last().text.contains("Answer")
        )
    }

    /** Polls the store for the expected consent value, with a bound so a bug fails instead of hangs. */
    private suspend fun awaitConsent(expected: Boolean): Boolean {
        repeat(200) {
            if (preferences.aiConsentGranted.first() == expected) return true
            kotlinx.coroutines.delay(10)
        }
        return preferences.aiConsentGranted.first()
    }

    /** Polls the recorder, for the same reason: the request runs in the ViewModel's own coroutine. */
    private suspend fun awaitRequests(expected: Int): Int {
        repeat(200) {
            if (api.requests.size >= expected) return api.requests.size
            kotlinx.coroutines.delay(10)
        }
        return api.requests.size
    }

    @Test
    fun `the disclosure can be declined, leaving the feature off`() {
        viewModel.onDraftChange("Where does my money go?")
        viewModel.send()

        viewModel.onDismissDisclosure()

        assertFalse(viewModel.uiState.value.isShowingDisclosure)
        assertTrue("declining sends nothing", api.requests.isEmpty())
        assertEquals(
            false,
            kotlinx.coroutines.runBlocking { preferences.aiConsentGranted.first() }
        )
    }

    @Test
    fun `a disabled feature cannot open the disclosure at all`() {
        access.value = AiAccess(configured = true, enabled = false, consentGranted = true)
        viewModel.onDraftChange("Where does my money go?")

        viewModel.send()

        assertFalse(viewModel.uiState.value.isShowingDisclosure)
        assertTrue(api.requests.isEmpty())
        assertEquals(
            AiAvailabilityMessages.DisabledByUser,
            viewModel.uiState.value.availability.notice
        )
    }
}

/** Records what would have been sent and returns a fixed answer. */
private class RecordingApi : DeepSeekApi {

    val requests = mutableListOf<ChatCompletionRequest>()

    override suspend fun chatCompletions(request: ChatCompletionRequest): ChatCompletionResponse {
        requests += request
        return ChatCompletionResponse(
            choices = listOf(
                ChatChoice(message = ChatMessage(ChatMessage.RoleAssistant, "Answer"))
            )
        )
    }
}
