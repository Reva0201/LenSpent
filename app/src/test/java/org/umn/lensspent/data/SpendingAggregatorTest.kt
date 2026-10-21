package org.umn.lensspent.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.formatIDR

/**
 * The money arithmetic behind every chart. These are the assertions that protect the numbers the user
 * reads from a silent off-by-one in a bucket index or a half-open range.
 */
class SpendingAggregatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    private val category = Category(id = 1L, name = "Groceries", iconKey = "groceries", colorHex = "#4CAF50")

    private var nextId = 1L

    /** 18 September 2026 is a Friday, so its Monday-first week starts on the 14th. */
    private val friday = LocalDate.of(2026, 9, 18)

    private fun transactionOn(date: LocalDate, amount: Long): Transaction = Transaction(
        id = nextId++,
        merchant = "Test Merchant",
        amount = amount,
        category = category,
        occurredAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    )

    /** Pins the clock time as well, for the tests that sit on the edges of a day. */
    private fun transactionAt(date: LocalDate, hour: Int, minute: Int, amount: Long): Transaction =
        Transaction(
            id = nextId++,
            merchant = "Test Merchant",
            amount = amount,
            category = category,
            occurredAt = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        )

    @Test
    fun `weekStart returns the Monday of the containing week`() {
        assertEquals(LocalDate.of(2026, 9, 14), SpendingAggregator.weekStart(friday))
        assertEquals(LocalDate.of(2026, 9, 14), SpendingAggregator.weekStart(LocalDate.of(2026, 9, 14)))
        assertEquals(LocalDate.of(2026, 9, 14), SpendingAggregator.weekStart(LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun `dailySeries buckets each amount into its weekday`() {
        val weekStart = SpendingAggregator.weekStart(friday)
        val transactions = listOf(
            transactionOn(LocalDate.of(2026, 9, 14), 100_000L),   // Monday
            transactionOn(LocalDate.of(2026, 9, 16), 250_000L),   // Wednesday
            transactionOn(LocalDate.of(2026, 9, 20), 50_000L)     // Sunday
        )

        val series = SpendingAggregator.dailySeries(transactions, weekStart, zone)

        assertEquals(listOf(100_000f, 0f, 250_000f, 0f, 0f, 0f, 50_000f), series)
    }

    @Test
    fun `dailySeries ignores anything outside the selected week`() {
        val weekStart = SpendingAggregator.weekStart(friday)
        val transactions = listOf(
            transactionOn(LocalDate.of(2026, 9, 13), 999_999L),   // Sunday before
            transactionOn(LocalDate.of(2026, 9, 21), 999_999L)    // Monday after
        )

        assertEquals(List(SpendingAggregator.DaysInWeek) { 0f }, SpendingAggregator.dailySeries(transactions, weekStart, zone))
    }

    @Test
    fun `dailySeries adds several transactions on the same day`() {
        val weekStart = SpendingAggregator.weekStart(friday)
        val transactions = listOf(
            transactionOn(friday, 125_000L),
            transactionOn(friday, 75_000L)
        )

        val series = SpendingAggregator.dailySeries(transactions, weekStart, zone)

        assertEquals(200_000f, series[4])   // Friday is the fifth bucket
    }

    @Test
    fun `monthlySeries buckets by month and ignores other years`() {
        val transactions = listOf(
            transactionOn(LocalDate.of(2026, 1, 5), 1_000L),
            transactionOn(LocalDate.of(2026, 9, 18), 2_000L),
            transactionOn(LocalDate.of(2025, 9, 18), 5_000L)
        )

        val series = SpendingAggregator.monthlySeries(transactions, 2026, zone)

        assertEquals(SpendingAggregator.MonthsInYear, series.size)
        assertEquals(1_000f, series[0])
        assertEquals(2_000f, series[8])
        assertEquals(0f, series[11])
    }

    @Test
    fun `totalOn sums only the requested day`() {
        val transactions = listOf(
            transactionOn(friday, 125_000L),
            transactionOn(friday, 75_000L),
            transactionOn(LocalDate.of(2026, 9, 17), 500_000L)
        )

        assertEquals(200_000L, SpendingAggregator.totalOn(transactions, friday, zone))
        assertEquals(0L, SpendingAggregator.totalOn(transactions, LocalDate.of(2026, 9, 15), zone))
    }

    @Test
    fun `daySummary counts the selected day down to the minute`() {
        val transactions = listOf(
            transactionAt(friday.minusDays(1), 23, 59, 10_000L),   // the minute before
            transactionAt(friday, 0, 0, 20_000L),                  // local midnight
            transactionAt(friday, 12, 30, 30_000L),
            transactionAt(friday.plusDays(1), 0, 0, 40_000L)       // the minute after
        )

        val summary = SpendingAggregator.daySummary(transactions, friday, zone)

        assertEquals(2, summary.purchaseCount)
    }

    @Test
    fun `daySummary names the biggest expense of the day, not of the ledger`() {
        val large = transactionOn(friday, 250_000L)
        val transactions = listOf(
            transactionOn(friday, 30_000L),
            large,
            transactionOn(friday.minusDays(1), 9_000_000L)
        )

        val summary = SpendingAggregator.daySummary(transactions, friday, zone)

        assertEquals(2, summary.purchaseCount)
        assertEquals(large.id, summary.largest?.id)
    }

    /** Two identical amounts must not leave the card flipping between them on every recomposition. */
    @Test
    fun `daySummary sends an amount tie to the later expense, whatever order the rows arrive in`() {
        val earlier = transactionAt(friday, 9, 0, 100_000L)
        val later = transactionAt(friday, 18, 0, 100_000L)

        val forwards = SpendingAggregator.daySummary(listOf(earlier, later), friday, zone)
        val backwards = SpendingAggregator.daySummary(listOf(later, earlier), friday, zone)

        assertEquals(later.id, forwards.largest?.id)
        assertEquals(later.id, backwards.largest?.id)
    }

    @Test
    fun `daySummary of a day with nothing on it is empty`() {
        val summary = SpendingAggregator.daySummary(
            listOf(transactionOn(friday, 10_000L)),
            friday.minusDays(1),
            zone
        )

        assertEquals(0, summary.purchaseCount)
        assertNull(summary.largest)
    }

    @Test
    fun `startOfDayMillis opens the day at local midnight`() {
        val start = SpendingAggregator.startOfDayMillis(friday, zone)
        val next = SpendingAggregator.startOfDayMillis(friday.plusDays(1), zone)

        assertEquals(24 * 60 * 60 * 1000L, next - start)
        assertEquals(12 * 60 * 60 * 1000L, transactionOn(friday, 1L).occurredAt - start)
    }

    @Test
    fun `Rupiah amounts are formatted without decimals`() {
        assertEquals("Rp 1.340.000", formatIDR(1_340_000L))
        assertEquals("Rp 0", formatIDR(0L))
    }
}
