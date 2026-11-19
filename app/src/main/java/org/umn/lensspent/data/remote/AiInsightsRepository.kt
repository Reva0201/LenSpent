package org.umn.lensspent.data.remote

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.TransactionRepository
import retrofit2.HttpException

/**
 * What one question came back as.
 *
 * A failure is a value rather than an exception because every one of them ends up as a sentence in the
 * chat, rendered exactly the way an answer is. The screen therefore needs no error plumbing of its own.
 */
sealed interface AiInsightsResult {

    data class Success(val answer: String) : AiInsightsResult

    /** A reason fit to show the user, never a raw exception. */
    data class Failure(val reason: String) : AiInsightsResult
}

/**
 * One category's total for a month.
 *
 * This is the whole of the per-record detail that leaves the phone: a category name the app itself
 * supplies, an aggregate amount, and how many expenses went into it. No merchant, no note, no date
 * finer than the month, and no identifier of any kind.
 */
@Serializable
internal data class InsightCategoryTotal(
    val category: String,
    val totalRp: Long,
    val count: Int
)

/**
 * One month of spending, aggregated.
 *
 * The payload used to be one JSON object per transaction, carrying the merchant and the exact day.
 * Aggregate totals and categories answer the same questions ("where does my money go?", "which
 * category is growing?") while exposing far less: a month with a category breakdown cannot be tied
 * to a visit, a shop, or a time of day.
 */
@Serializable
internal data class InsightMonth(
    val month: String,
    val totalRp: Long,
    val categories: List<InsightCategoryTotal>
)

/**
 * The assistant's identity and guardrails.
 *
 * The first message of every request, though never on its own: [AiInsightsRepository.systemMessage]
 * appends what today is, because a constant cannot know the date.
 */
internal const val FinancialAssistantSystemPrompt =
    "You are the financial assistant built into LensSpent, an app that tracks a user's expenses. " +
        "You are given the user's spending aggregated by month and category as JSON, newest month " +
        "first, where every amount is a whole number of Indonesian Rupiah (IDR). Individual " +
        "transactions and merchants are deliberately not provided. " +
        "Answer the user's question using only those totals: add up, compare and describe what is " +
        "there, call out patterns, and give practical, specific suggestions. " +
        "If the data does not answer the question, say so plainly instead of inventing a figure. " +
        "Never claim to be a licensed adviser: when a question needs real financial advice, say that " +
        "this is a general insight and not professional advice. " +
        "Keep answers short and scannable - a sentence or two of plain text, then markdown bullet " +
        "points, with any figure that matters in **bold**. Reply in the language the user wrote in."

/**
 * AI Insights: the one feature that may leave the phone on purpose.
 *
 * Everything else in the app is offline-first - a receipt is read, parsed and stored on-device. This
 * class is therefore written to be easy to audit: [ledgerJson] is the complete list of data that is
 * transmitted, [userMessage] is the complete text of the request, and [access] is consulted on every
 * call so a setting changed a moment ago takes effect immediately.
 *
 * Nothing is sent unless all three gates in [AiAccess] are open. When they are not, the answer is a
 * [AiInsightsResult.Failure] carrying the matching [AiAvailabilityMessages] sentence, and [ask]
 * returns before touching the ledger or the network.
 *
 * One question is one request. The thread is not replayed as history: the assistant only ever needs
 * the aggregates, so sending the whole conversation back would cost tokens and add nothing.
 */
class AiInsightsRepository(
    private val transactions: TransactionRepository,
    /** Null when this build has no secure backend; [ask] then never constructs a request. */
    private val api: DeepSeekApi?,
    /**
     * Read on every [ask], so the consent switch and the "disable AI" setting are enforced at the
     * point of use rather than captured once when the repository was built.
     */
    private val access: suspend () -> AiAccess = {
        AiAccess(configured = api != null, enabled = false, consentGranted = false)
    },
    private val model: String = DeepSeekApiClient.DefaultModel,
    private val json: Json = DeepSeekApiClient.Json,
    /**
     * Read for two things: the zone the ledger dates are grouped in, and the "today" the assistant is
     * told. Injected, the way every ViewModel here takes its clock, so a test can freeze both.
     */
    private val clock: Clock = Clock.systemDefaultZone(),
    private val systemPrompt: String = FinancialAssistantSystemPrompt
) {

    private val zone: ZoneId = clock.zone

    /**
     * Answers [question] from the user's recent spending, aggregated.
     *
     * Never throws at the caller: cancellation is rethrown so the caller's scope can still be torn
     * down, and every other failure comes back as [AiInsightsResult.Failure] carrying something worth
     * reading.
     */
    suspend fun ask(
        question: String,
        limit: Int = RecentTransactionsToSend
    ): AiInsightsResult {
        val prompt = question.trim()
        if (prompt.isEmpty()) {
            return AiInsightsResult.Failure("Type a question about your spending first.")
        }

        // The gate is checked before the ledger is read as well as before the network call: a refusal
        // must not even look at the spending it will not send.
        val permission = access()
        permission.unavailableReason?.let { reason ->
            return AiInsightsResult.Failure(AiAvailabilityMessages.forReason(reason))
        }
        val client = api ?: return AiInsightsResult.Failure(AiAvailabilityMessages.NotConfigured)

        return try {
            val request = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage.system(systemMessage()),
                    ChatMessage.user(userMessage(ledgerJson(limit), prompt))
                )
            )
            val answer = client.chatCompletions(request)
                .choices.firstOrNull()?.message?.content?.trim().orEmpty()

            if (answer.isEmpty()) {
                AiInsightsResult.Failure("The assistant came back with an empty answer. Try asking again.")
            } else {
                AiInsightsResult.Success(answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            AiInsightsResult.Failure(httpFailureReason(e))
        } catch (e: IOException) {
            AiInsightsResult.Failure("Could not reach the AI service. Check your connection and try again.")
        } catch (e: Exception) {
            // A malformed body, for instance: worth a sentence rather than a crash in a chat bubble.
            AiInsightsResult.Failure("The answer could not be read. Try asking again.")
        }
    }

    /**
     * The identity, plus what today is.
     *
     * The date cannot live in [systemPrompt], which is a constant. Without the line this method adds,
     * a question like "how about today?" gets "if today *is* 2026-09-22, thenâ€¦" - the model has the
     * totals but no calendar to hang them on. The zone is named too, so "this week" means the user's
     * week and not the API's.
     */
    internal fun systemMessage(): String = systemPrompt + "\n\n" + todayContext(clock)

    /**
     * The user's spending as monthly, category-level aggregates, newest month first.
     *
     * Read from [TransactionRepository], which is the same Room-backed source the dashboard and the
     * charts draw from. Rows are reduced before serialisation: [toInsightMonths] is the only place a
     * stored expense may influence the wire format, and it deliberately drops everything that could
     * identify a purchase.
     */
    internal suspend fun ledgerJson(limit: Int): String {
        val rows = transactions.observeTransactions(limit).first()
        return json.encodeToString(toInsightMonths(rows, zone))
    }

    /**
     * The single message the model reads: the data, then the question.
     *
     * Order matters. The array is labelled before it appears - that every amount is whole Rupiah and
     * that the list is newest first - because a bare array would leave the model guessing at the
     * currency, and the question comes last so the answer is anchored to it.
     */
    internal fun userMessage(ledgerJson: String, question: String): String = buildString {
        append(DataPreamble)
        append('\n')
        append(ledgerJson)
        append("\n\nMy question: ")
        append(question)
    }

    /** Turns the transport's failures into something the user can act on. */
    private fun httpFailureReason(e: HttpException): String = when (e.code()) {
        Unauthorized, Forbidden ->
            "The AI service refused this app's request (${e.code()})."

        PaymentRequired ->
            "The AI service is out of credit (${e.code()})."

        TooManyRequests ->
            "The AI service is rate limiting requests (${e.code()}). Wait a moment and ask again."

        in ServerErrorRange ->
            "The AI service is having trouble right now (${e.code()}). Try again shortly."

        else -> "The AI service refused the request (${e.code()})."
    }

    companion object {
        /** How many recent rows are read to build the aggregates: enough for current habits. */
        const val RecentTransactionsToSend = 200

        private const val DataPreamble =
            "My spending so far, aggregated by month and category as JSON, newest month first " +
                "(amounts are whole Indonesian Rupiah, with no minor unit):"

        private const val Unauthorized = 401
        private const val Forbidden = 403
        private const val PaymentRequired = 402
        private const val TooManyRequests = 429
        private val ServerErrorRange = 500..599
    }
}

/**
 * What today is, in the user's own zone, for the model to read.
 *
 * "Tuesday, 2026-09-22" is what a question about "this week" has to be answered against, and the time
 * comes along because a user asking about today may well be asking mid-evening. Day and month names
 * are forced to English to match the rest of the prompt.
 */
internal fun todayContext(at: Clock): String {
    val today = LocalDate.now(at)
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val time = LocalTime.now(at).format(ClockTimeFormatter)

    return "Today is $weekday, $today (local time $time, zone ${at.zone.id}). The months in the " +
        "spending you are given are in that same zone: read \"this month\" and \"this year\" " +
        "against that date, and never say you do not know the current date."
}

private val ClockTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * The only place a stored expense may influence what leaves the phone.
 *
 * Rows are folded into one entry per month, and within a month into one entry per category. The
 * merchant, the note, the exact timestamp and every database identifier are dropped here, before
 * serialisation, so widening the payload requires editing this function - which is exactly what the
 * payload-minimisation test asserts on.
 */
internal fun toInsightMonths(rows: List<Transaction>, zone: ZoneId): List<InsightMonth> =
    rows
        .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate().let(YearMonth::from) }
        .toSortedMap(compareByDescending { it })
        .map { (month, transactions) ->
            val byCategory = transactions
                .groupBy { it.category.name }
                .map { (category, inCategory) ->
                    InsightCategoryTotal(
                        category = category,
                        totalRp = inCategory.sumOf { it.amount },
                        count = inCategory.size
                    )
                }
                .sortedByDescending { it.totalRp }

            InsightMonth(
                month = month.toString(),
                totalRp = transactions.sumOf { it.amount },
                categories = byCategory
            )
        }

