package org.umn.lensspent.data.remote

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
 * The ledger rows that leave the phone, and nothing else.
 *
 * Four fields, exactly as the feature asks: the date, the merchant, the category and the amount. The
 * note, the capture id and every database id stay behind, so what goes to DeepSeek is a summary of
 * spending and not a copy of the database. The keys are short because the whole point of the shape is
 * that it is cheap to send.
 */
@Serializable
internal data class InsightTransaction(
    val date: String,
    val merchant: String,
    val category: String,
    val amountRp: Long
)

/**
 * The assistant's identity and guardrails.
 *
 * The first message of every request, though never on its own: [AiInsightsRepository.systemMessage]
 * appends what today is, because a constant cannot know the date.
 */
internal const val FinancialAssistantSystemPrompt =
    "You are the financial assistant built into LensSpent, an app that tracks a user's expenses by " +
        "photographing receipts. You are given the user's most recent expenses as JSON, newest first, " +
        "where every amount is a whole number of Indonesian Rupiah (IDR). " +
        "Answer the user's question using only those expenses: add up, compare and describe what is " +
        "there, call out patterns, and give practical, specific suggestions. " +
        "If the data does not answer the question, say so plainly instead of inventing a figure. " +
        "An expense is in that list only because the user recorded it, so a day with no entry means " +
        "nothing was recorded that day - say that plainly rather than doubting the date. " +
        "Never claim to be a licensed adviser: when a question needs real financial advice, say that " +
        "this is a general insight and not professional advice. " +
        "Keep answers short and scannable - a sentence or two of plain text, then markdown bullet " +
        "points, with any figure that matters in **bold**. Reply in the language the user wrote in."

/**
 * Shown instead of an answer when this build was compiled without a key - which is also what a fresh
 * install of a keyless build sees.
 *
 * It names the key as the thing that is missing rather than pointing at a screen: no setting in the app
 * can supply one, because the key is fixed when the build is made.
 */
internal const val MissingApiKeyMessage =
    "Premium AI Insights is off. This build was compiled without a DeepSeek API key, so questions " +
        "cannot be answered."

/**
 * Premium AI Insights: the one feature that leaves the phone on purpose.
 *
 * Everything else in the app is offline-first - a receipt is read, parsed and stored on-device. This
 * class is therefore written to be easy to audit: [toInsightTransaction] is the complete list of what
 * is transmitted, and [userMessage] is the complete text of the request.
 *
 * One question is one request. The thread is not replayed as history: the assistant only ever needs the
 * ledger, so sending the whole conversation back would cost tokens and add nothing.
 */
class AiInsightsRepository(
    private val transactions: TransactionRepository,
    private val api: DeepSeekApi,
    /**
     * Whether this build carries a key to send with, which is [DeepSeekApiClient.isConfigured] in the
     * app. A parameter so a test can ask what a keyless build does without rebuilding one.
     */
    private val apiKeyConfigured: Boolean = true,
    private val model: String = DeepSeekApiClient.DefaultModel,
    private val json: Json = DeepSeekApiClient.Json,
    /**
     * Read for two things: the zone the ledger dates are rendered in, and the "today" the assistant is
     * told. Injected, the way every ViewModel here takes its clock, so a test can freeze both.
     */
    private val clock: Clock = Clock.systemDefaultZone(),
    private val systemPrompt: String = FinancialAssistantSystemPrompt
) {

    private val zone: ZoneId = clock.zone

    /**
     * Answers [question] from the user's recent spending.
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
        if (!apiKeyConfigured) return AiInsightsResult.Failure(MissingApiKeyMessage)

        return try {
            val request = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessage.system(systemMessage()),
                    ChatMessage.user(userMessage(ledgerJson(limit), prompt))
                )
            )
            val answer = api.chatCompletions(request)
                .choices.firstOrNull()?.message?.content?.trim().orEmpty()

            if (answer.isEmpty()) {
                AiInsightsResult.Failure("DeepSeek came back with an empty answer. Try asking again.")
            } else {
                AiInsightsResult.Success(answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            AiInsightsResult.Failure(httpFailureReason(e))
        } catch (e: IOException) {
            AiInsightsResult.Failure("Could not reach DeepSeek. Check your connection and try again.")
        } catch (e: Exception) {
            // A malformed body, for instance: worth a sentence rather than a crash in a chat bubble.
            AiInsightsResult.Failure("The answer could not be read. Try asking again.")
        }
    }

    /**
     * The identity, plus what today is.
     *
     * The date cannot live in [systemPrompt], which is a constant. Without the line this method adds,
     * a question like "how about today?" gets "if today *is* 2026-09-22, then…" - the model has the
     * ledger but no calendar to hang it on. The zone is named too, so "this week" means the user's
     * week and not the API's.
     */
    internal fun systemMessage(): String = systemPrompt + "\n\n" + todayContext(clock)

    /**
     * The user's recent expenses as JSON, newest first.
     *
     * Read from [TransactionRepository], which is the same Room-backed source the dashboard and the
     * charts draw from, so the assistant and the screen can never disagree about the ledger.
     */
    internal suspend fun ledgerJson(limit: Int): String {
        val rows = transactions.observeTransactions(limit).first().map { it.toInsightTransaction(zone) }
        return json.encodeToString(rows)
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
            "DeepSeek rejected the API key in this build (${e.code()}). Check the key and rebuild."

        PaymentRequired ->
            "The DeepSeek account behind this key is out of credit (${e.code()})."

        TooManyRequests ->
            "DeepSeek is rate limiting this key (${e.code()}). Wait a moment and ask again."

        in ServerErrorRange ->
            "DeepSeek is having trouble right now (${e.code()}). Try again shortly."

        else -> "DeepSeek refused the request (${e.code()})."
    }

    companion object {
        /** How much history goes out: recent enough to describe the user's current habits. */
        const val RecentTransactionsToSend = 50

        private const val DataPreamble =
            "My most recent expenses, newest first, as JSON (amounts are whole Indonesian Rupiah, " +
                "with no minor unit):"

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
 * "Tuesday, 2026-09-22" is what a question about "today" or "this week" has to be answered against,
 * and the time comes along because a user asking about today may well be asking mid-evening. Day and
 * month names are forced to English to match the rest of the prompt: the tables the answers are built
 * from read better with one language in them.
 */
internal fun todayContext(at: Clock): String {
    val today = LocalDate.now(at)
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val time = LocalTime.now(at).format(ClockTimeFormatter)

    return "Today is $weekday, $today (local time $time, zone ${at.zone.id}). The dates in the " +
        "expenses you are given are in that same zone: read \"today\", \"this week\", \"this month\" " +
        "and \"this year\" against that date, and never say you do not know the current date."
}

private val ClockTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * The only place a stored expense and the wire format meet.
 *
 * The date is rendered in the user's own zone as `yyyy-MM-dd`: unambiguous to read, and free of the
 * locale-specific month names that would otherwise ride along with every row.
 */
internal fun Transaction.toInsightTransaction(zone: ZoneId): InsightTransaction = InsightTransaction(
    date = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate().toString(),
    merchant = merchant,
    category = category.name,
    amountRp = amount
)

