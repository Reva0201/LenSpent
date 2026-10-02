package org.umn.lensspent.data.remote

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.StubTransactionRepository
import retrofit2.HttpException
import retrofit2.Response

/**
 * The wire contract, pinned.
 *
 * No socket is opened: the DeepSeek endpoint is replaced by a recorder, so what is asserted is exactly
 * the request the app would have sent. That is the part worth protecting - which fields of a stored
 * transaction leave the phone, what the model is told, and what the user reads when the call fails.
 */
class AiInsightsRepositoryTest {

    /**
     * The privacy line, spelled out as JSON: four fields per row, no note, no ids, no capture.
     *
     * The expected string is written out by hand rather than built from the DTO, so a field added to
     * [InsightTransaction] fails here instead of quietly widening what is transmitted.
     */
    @Test
    fun `the ledger leaves as JSON holding only date, merchant, category and amount`() = runBlocking {
        val harness = Harness()

        harness.repository.ask("Where does my money go?")

        assertEquals(
            "[" +
                """{"date":"2026-09-18","merchant":"Supermarket Central","category":"Groceries","amountRp":850000},""" +
                """{"date":"2026-09-17","merchant":"Coffee Shop Artisanal","category":"Dining","amountRp":125000}""" +
                "]",
            harness.ledgerJson()
        )
    }

    @Test
    fun `the model is told who it is, then handed the data, then asked`() = runBlocking {
        val harness = Harness()

        harness.repository.ask("  Where does my money go?  ")

        val request = harness.api.requests.single()
        assertTrue(
            "the identity comes first, the calendar after it",
            request.messages.first().content.startsWith(FinancialAssistantSystemPrompt)
        )
        assertTrue(
            "the data is labelled before it appears: ${request.messages.last().content}",
            request.messages.last().content.startsWith("My most recent expenses, newest first, as JSON")
        )
        assertTrue(
            "the question is the last thing the model reads",
            request.messages.last().content.endsWith("My question: Where does my money go?")
        )
    }

    /**
     * The date line, which is what stops "how about today?" coming back as "if today *is* 2026-09-22…".
     *
     * The clock is frozen, so this is asserted as a literal: a Tuesday evening in Jakarta. All three
     * parts earn their place - the date for "today", the time for "so far today", and the zone for
     * "this week", which has to be the user's week and not the API's.
     */
    @Test
    fun `the model is told today's date, time and zone`() = runBlocking {
        val harness = Harness()

        harness.repository.ask("How about today?")

        val system = harness.api.requests.single().messages.first()
        assertEquals(ChatMessage.RoleSystem, system.role)
        assertTrue(system.content, system.content.contains("Today is Tuesday, 2026-09-22"))
        assertTrue(system.content, system.content.contains("local time 18:04"))
        assertTrue(system.content, system.content.contains("zone Asia/Jakarta"))
        assertTrue(
            system.content,
            system.content.contains("never say you do not know the current date")
        )
    }

    @Test
    fun `the request is a single non-streamed completion of the chat model`() = runBlocking {
        val harness = Harness()

        harness.repository.ask("Where does my money go?")

        val request = harness.api.requests.single()
        assertEquals(DeepSeekApiClient.DefaultModel, request.model)
        assertFalse(request.stream)
        assertEquals(ChatCompletionRequest.DefaultTemperature, request.temperature, 0.0)
    }

    @Test
    fun `only the recent rows are sent, and a caller can narrow that`() = runBlocking {
        val harness = Harness()

        harness.repository.ask("Where does my money go?")
        harness.repository.ask("And the week before?", limit = 5)

        assertEquals(
            listOf(AiInsightsRepository.RecentTransactionsToSend, 5),
            harness.transactions.requestedLimits
        )
    }

    @Test
    fun `the answer is the model's first choice, trimmed`() = runBlocking {
        val harness = Harness(response = { answer("  You spent **Rp 975.000**.  ") })

        val result = harness.repository.ask("Where does my money go?")

        assertEquals(AiInsightsResult.Success("You spent **Rp 975.000**."), result)
    }

    /**
     * The one place the user is told what to do about a refusal.
     *
     * Every code here is a real DeepSeek answer, including the out-of-credit one: a key that works can
     * still stop working, and "402" on screen would tell the user nothing.
     */
    @Test
    fun `each refusal reads like advice rather than a status code`() = runBlocking {
        val refusals = mapOf(
            401 to "rejected the API key",
            402 to "out of credit",
            429 to "rate limiting",
            503 to "having trouble"
        )

        refusals.forEach { (code, expected) ->
            val harness = Harness(response = { throw httpError(code) })

            val failure = failureFrom(harness.repository.ask("Where does my money go?"))

            assertTrue("$code: ${failure.reason}", failure.reason.contains(expected))
        }
    }

    @Test
    fun `a dropped connection is a sentence, not an exception`() = runBlocking {
        val harness = Harness(response = { throw IOException("offline") })

        val failure = failureFrom(harness.repository.ask("Where does my money go?"))

        assertTrue(failure.reason, failure.reason.startsWith("Could not reach DeepSeek"))
    }

    @Test
    fun `a build with no key never calls DeepSeek`() = runBlocking {
        val harness = Harness(apiKeyConfigured = false)

        val failure = failureFrom(harness.repository.ask("Where does my money go?"))

        assertEquals(MissingApiKeyMessage, failure.reason)
        assertTrue("nothing may leave the phone without a key", harness.api.requests.isEmpty())
    }

    /**
     * The key is a build constant now, so there is no mid-session state to consult: a repository wired
     * for a keyless build refuses every question, and one wired with a key answers. Both halves are
     * asserted together because the point is that the flag is decided when the repository is built and
     * never asked again.
     */
    @Test
    fun `whether there is a key is settled when the repository is built`() = runBlocking {
        val keyless = Harness(apiKeyConfigured = false)
        val keyed = Harness(apiKeyConfigured = true)

        assertEquals(
            MissingApiKeyMessage,
            failureFrom(keyless.repository.ask("Where does my money go?")).reason
        )
        assertTrue("nothing may leave the phone without a key", keyless.api.requests.isEmpty())

        val result = keyed.repository.ask("Where does my money go?")

        assertTrue("expected an answer, got $result", result is AiInsightsResult.Success)
        assertEquals(1, keyed.api.requests.size)
    }

    @Test
    fun `an empty question never calls DeepSeek`() = runBlocking {
        val harness = Harness()

        val failure = failureFrom(harness.repository.ask("   "))

        assertTrue(failure.reason, failure.reason.contains("Type a question"))
        assertTrue(harness.api.requests.isEmpty())
    }

    @Test
    fun `an answer with no content is not shown as an answer`() = runBlocking {
        val harness = Harness(response = { ChatCompletionResponse(choices = emptyList()) })

        val failure = failureFrom(harness.repository.ask("Where does my money go?"))

        assertTrue(failure.reason, failure.reason.contains("empty answer"))
    }
}

/**
 * A repository over [SampleLedger], wired to a recorder, on a frozen Jakarta clock.
 *
 * Both the zone and the moment are pinned: the ledger dates must read back as the dates that were
 * stored, and "today" in the system message has to be one specific day, whatever machine runs this.
 */
private class Harness(
    ledger: List<Transaction> = SampleLedger,
    response: () -> ChatCompletionResponse = { answer("You spent Rp 975.000 in total.") },
    /** Whether the build this harness stands in for was given a DeepSeek key. */
    apiKeyConfigured: Boolean = true
) {
    val transactions = StubTransactionRepository(ledger)
    val api = RecordingDeepSeekApi(response)
    val repository = AiInsightsRepository(
        transactions = transactions,
        api = api,
        apiKeyConfigured = apiKeyConfigured,
        clock = FixedClock
    )

    /** The JSON the model would have been shown, read back out of the request that was sent. */
    fun ledgerJson(): String = api.requests.single()
        .messages.last()
        .content
        .substringAfter('\n')
        .substringBefore("\n\nMy question:")
}

/** Records every request instead of sending it, and answers with [response] - or throws out of it. */
private class RecordingDeepSeekApi(
    private val response: () -> ChatCompletionResponse
) : DeepSeekApi {

    val requests = mutableListOf<ChatCompletionRequest>()

    override suspend fun chatCompletions(request: ChatCompletionRequest): ChatCompletionResponse {
        requests += request
        return response()
    }
}

private fun answer(text: String): ChatCompletionResponse = ChatCompletionResponse(
    model = DeepSeekApiClient.DefaultModel,
    choices = listOf(ChatChoice(message = ChatMessage(ChatMessage.RoleAssistant, text)))
)

/** An HTTP failure of the shape Retrofit throws when the API answers with a status, not a body. */
private fun httpError(code: Int): HttpException = HttpException(
    Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType()))
)

private fun failureFrom(result: AiInsightsResult): AiInsightsResult.Failure {
    assertTrue("expected a failure, got $result", result is AiInsightsResult.Failure)
    return result as AiInsightsResult.Failure
}

/** The user's zone, and so the zone every expectation below is written in. */
private val LedgerZone: ZoneId = ZoneId.of("Asia/Jakarta")

/** Tuesday 2026-09-22, 18:04 in Jakarta - the "now" the system message has to report. */
private val FixedClock: Clock = Clock.fixed(Instant.parse("2026-09-22T11:04:00Z"), LedgerZone)

/**
 * Two rows, newest first, exactly as the dashboard's own query returns them.
 *
 * Declared after the zone on purpose: top-level properties initialise in file order, so a ledger built
 * before [LedgerZone] was would read it while it is still null.
 */
private val SampleLedger = listOf(
    expense("Supermarket Central", 850_000L, "Groceries", "2026-09-18"),
    expense("Coffee Shop Artisanal", 125_000L, "Dining", "2026-09-17")
)

private var nextTransactionId = 1L

private fun expense(merchant: String, amountRp: Long, category: String, date: String): Transaction =
    Transaction(
        id = nextTransactionId++,
        merchant = merchant,
        amount = amountRp,
        category = Category(id = 1L, name = category, iconKey = "other", colorHex = "#78909C"),
        occurredAt = LocalDate.parse(date).atStartOfDay(LedgerZone).toInstant().toEpochMilli()
    )
