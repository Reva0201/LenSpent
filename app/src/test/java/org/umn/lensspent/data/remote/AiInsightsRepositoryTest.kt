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
 * No socket is opened: the backend is replaced by a recorder, so what is asserted is exactly the
 * request the app would have sent. That is the part worth protecting - which fields of a stored
 * transaction are allowed to leave the phone, what the model is told, and what the user reads when a
 * request is refused or fails.
 */
class AiInsightsRepositoryTest {

    /**
     * The privacy line, spelled out as JSON.
     *
     * The expected string is written out by hand rather than built from the DTO, so a field added to
     * [InsightMonth] or [InsightCategoryTotal] fails here instead of quietly widening what is
     * transmitted. Monthly totals per category, and nothing else.
     */
    @Test
    fun `only monthly category totals leave the phone`() = runBlocking {
        val harness = Harness()

        harness.repository.ask("Where does my money go?")

        val sent = harness.ledgerJson()
        assertEquals(
            "[" +
                """{"month":"2026-09","totalRp":975000,"categories":[""" +
                """{"category":"Groceries","totalRp":850000,"count":1},""" +
                """{"category":"Dining","totalRp":125000,"count":1}""" +
                "]}," +
                """{"month":"2026-08","totalRp":50000,"categories":[""" +
                """{"category":"Transport","totalRp":50000,"count":1}""" +
                "]}" +
                "]",
            sent
        )
    }

    /**
     * The most important assertion in this file.
     *
     * A merchant name, a note, a full timestamp or an id reaching the wire would be a privacy
     * regression that still "works", so the absence is asserted directly rather than inferred from
     * the JSON shape above.
     */
    @Test
    fun `merchants, notes, ids and exact timestamps never appear in the request`() = runBlocking {
        val harness = Harness()

        harness.repository.ask("Where does my money go?")

        val body = harness.api.requests.single().messages.joinToString("\n") { it.content }
        listOf(
            "Supermarket Central",
            "Coffee Shop Artisanal",
            "Angkot to campus",
            "weekly groceries",
            "capture",
            "sourceUri",
            "transactionId",
            "2026-09-18T",
            "18:30"
        ).forEach { forbidden ->
            assertFalse("the request must not contain '$forbidden': $body", body.contains(forbidden))
        }
        // The month is present, the day is not: aggregation is what makes that true.
        assertTrue(body, body.contains("\"month\":\"2026-09\""))
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
            request.messages.last().content.startsWith(
                "My spending so far, aggregated by month and category as JSON"
            )
        )
        assertTrue(
            "the question is the last thing the model reads",
            request.messages.last().content.endsWith("My question: Where does my money go?")
        )
    }

    /**
     * The date line, which is what stops "how about this month?" coming back as a guess.
     *
     * The clock is frozen, so this is asserted as a literal: a Tuesday evening in Jakarta. All three
     * parts earn their place - the date for "today", the time for "so far today", and the zone for
     * "this month", which has to be the user's month and not the API's.
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
    fun `a caller can narrow how much history is read`() = runBlocking {
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

    @Test
    fun `each refusal reads like advice rather than a status code`() = runBlocking {
        val refusals = mapOf(
            401 to "refused this app's request",
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

        assertTrue(failure.reason, failure.reason.startsWith("Could not reach the AI service"))
    }

    // ---------------------------------------------------------------------------------------------
    // The gates. Every one of these asserts both halves: a readable sentence, and no request.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a build with no backend never makes a request`() = runBlocking {
        val harness = Harness(api = null, access = AiAccess(configured = false))

        val failure = failureFrom(harness.repository.ask("Where does my money go?"))

        assertEquals(AiAvailabilityMessages.NotConfigured, failure.reason)
        assertTrue("nothing may leave the phone", harness.api.requests.isEmpty())
        assertTrue("the ledger must not even be read", harness.transactions.requestedLimits.isEmpty())
    }

    @Test
    fun `turning AI off stops the next request even with consent on record`() = runBlocking {
        val harness = Harness(
            access = AiAccess(configured = true, enabled = false, consentGranted = true)
        )

        val failure = failureFrom(harness.repository.ask("Where does my money go?"))

        assertEquals(AiAvailabilityMessages.DisabledByUser, failure.reason)
        assertTrue(harness.api.requests.isEmpty())
        assertTrue(harness.transactions.requestedLimits.isEmpty())
    }

    @Test
    fun `no request is made before the disclosure is accepted`() = runBlocking {
        val harness = Harness(
            access = AiAccess(configured = true, enabled = true, consentGranted = false)
        )

        val failure = failureFrom(harness.repository.ask("Where does my money go?"))

        assertEquals(AiAvailabilityMessages.ConsentRequired, failure.reason)
        assertTrue(harness.api.requests.isEmpty())
        assertTrue(harness.transactions.requestedLimits.isEmpty())
    }

    /**
     * The gate is read per request, not captured once: a user who revokes consent mid-session must
     * have the very next question refused.
     */
    @Test
    fun `access is re-evaluated on every question`() = runBlocking {
        val harness = Harness()

        assertTrue(harness.repository.ask("First?") is AiInsightsResult.Success)
        harness.changeAccess(AiAccess(configured = true, enabled = true, consentGranted = false))
        val second = harness.repository.ask("Second?")

        assertEquals(AiAvailabilityMessages.ConsentRequired, failureFrom(second).reason)
        assertEquals("only the first question was sent", 1, harness.api.requests.size)
    }

    @Test
    fun `an empty question never calls the backend`() = runBlocking {
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
 * Both the zone and the moment are pinned: the ledger months must read back as the months that were
 * stored, and "today" in the system message has to be one specific day, whatever machine runs this.
 */
private class Harness(
    ledger: List<Transaction> = SampleLedger,
    response: () -> ChatCompletionResponse = { answer("You spent Rp 975.000 in total.") },
    api: RecordingDeepSeekApi? = RecordingDeepSeekApi(response),
    access: AiAccess = AiAccess(configured = true, enabled = true, consentGranted = true)
) {
    private val recorder: RecordingDeepSeekApi = api ?: RecordingDeepSeekApi(response)
    private var currentAccess: AiAccess = access

    val transactions = StubTransactionRepository(ledger)

    /** Never null in these tests, so assertions can read the recorded requests. */
    val api: RecordingDeepSeekApi get() = recorder

    val repository = AiInsightsRepository(
        transactions = transactions,
        // A real keyless build passes null; the harness always passes the recorder so `requested`
        // stays inspectable, and the "no backend" test closes the gate instead.
        api = if (access.configured) recorder else null,
        access = { currentAccess },
        clock = FixedClock
    )

    fun changeAccess(access: AiAccess) {
        currentAccess = access
    }

    /** The JSON the model would have been shown, read back out of the request that was sent. */
    fun ledgerJson(): String = recorder.requests.single()
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
 * Three rows across two months, carrying the things that must NOT be transmitted: merchant names,
 * a note, distinct times of day, and non-zero ids.
 *
 * Declared after the zone on purpose: top-level properties initialise in file order, so a ledger built
 * before [LedgerZone] was would read it while it is still null.
 */
private val SampleLedger = listOf(
    expense(
        merchant = "Supermarket Central",
        amountRp = 850_000L,
        category = "Groceries",
        date = "2026-09-18",
        hour = 18,
        minute = 30,
        note = "weekly groceries"
    ),
    expense(
        merchant = "Coffee Shop Artisanal",
        amountRp = 125_000L,
        category = "Dining",
        date = "2026-09-17",
        hour = 8,
        minute = 5
    ),
    expense(
        merchant = "Angkot to campus",
        amountRp = 50_000L,
        category = "Transport",
        date = "2026-08-30",
        hour = 7,
        minute = 15
    )
)

private var nextTransactionId = 101L

private fun expense(
    merchant: String,
    amountRp: Long,
    category: String,
    date: String,
    hour: Int = 12,
    minute: Int = 0,
    note: String? = null
): Transaction = Transaction(
    id = nextTransactionId++,
    merchant = merchant,
    amount = amountRp,
    category = Category(id = 1L, name = category, iconKey = "other", colorHex = "#78909C"),
    occurredAt = LocalDate.parse(date)
        .atTime(hour, minute)
        .atZone(LedgerZone)
        .toInstant()
        .toEpochMilli(),
    note = note,
    captureId = 42L
)
