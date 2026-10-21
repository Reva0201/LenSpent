package org.umn.lensspent.data

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.umn.lensspent.data.model.Transaction

/**
 * Turns a list of transactions into the buckets the charts draw.
 *
 * Kept as pure functions over plain lists: no database, no Compose, so the arithmetic that decides
 * what the user sees can be unit tested. Screens never add money up themselves.
 */
object SpendingAggregator {

    const val DaysInWeek = 7
    const val MonthsInYear = 12

    /** Monday-first weekday initials, aligned with [dailySeries]. */
    val dailyLabels: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

    /** Short English month names, aligned with [monthlySeries]. */
    val monthlyLabels: List<String> = (1..MonthsInYear).map {
        Month.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }

    /** Start (inclusive) of the Monday-first week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    /** Whole-Rupiah total spent on [date]. */
    fun totalOn(
        transactions: List<Transaction>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long = transactions.sumOf { if (it.localDate(zone) == date) it.amount else 0L }

    /**
     * Orders expenses so the largest wins, and an amount tie goes to the later expense.
     *
     * The tie-break is what keeps the daily "biggest expense" card from flipping between two rows of
     * equal amount - the queries happen to return newest first, but nothing here should depend on it.
     */
    private val LargestFirst: Comparator<Transaction> =
        compareBy<Transaction> { it.amount }.thenBy { it.occurredAt }.thenBy { it.id }

    /**
     * What [date] adds up to: how many expenses it holds and the largest of them, in a single pass.
     *
     * [DaySummary.largest] is non-null whenever [DaySummary.purchaseCount] is above zero: both are
     * written as each row of the date is seen, so the summary can never count rows it cannot name.
     */
    fun daySummary(
        transactions: List<Transaction>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): DaySummary {
        var purchaseCount = 0
        var largest: Transaction? = null
        transactions.forEach { transaction ->
            if (transaction.localDate(zone) != date) return@forEach
            purchaseCount++
            val current = largest
            if (current == null || LargestFirst.compare(transaction, current) > 0) {
                largest = transaction
            }
        }
        return DaySummary(purchaseCount = purchaseCount, largest = largest)
    }

    /** Seven Monday-first buckets covering the week that starts at [start]. */
    fun dailySeries(
        transactions: List<Transaction>,
        start: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Float> {
        val buckets = LongArray(DaysInWeek)
        transactions.forEach { transaction ->
            val index = ChronoUnit.DAYS.between(start, transaction.localDate(zone)).toInt()
            if (index in 0 until DaysInWeek) {
                buckets[index] += transaction.amount
            }
        }
        return buckets.map { it.toFloat() }
    }

    /** Twelve month buckets covering [year]. */
    fun monthlySeries(
        transactions: List<Transaction>,
        year: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Float> {
        val buckets = LongArray(MonthsInYear)
        transactions.forEach { transaction ->
            val date = transaction.localDate(zone)
            if (date.year == year) {
                buckets[date.monthValue - 1] += transaction.amount
            }
        }
        return buckets.map { it.toFloat() }
    }

    /** Epoch millis of local midnight opening [date]; range queries are half-open [from, to). */
    fun startOfDayMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * What a single day holds: the number of expenses logged on it, and the largest of them.
 *
 * Kept as one type because the daily summary reads both from the same pass, and because a count with
 * no largest row is a state the aggregator never produces.
 */
data class DaySummary(
    val purchaseCount: Int = 0,
    val largest: Transaction? = null
)

/** Local calendar date the expense happened on, in [zone]. */
fun Transaction.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
