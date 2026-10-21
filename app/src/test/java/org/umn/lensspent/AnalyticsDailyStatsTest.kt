package org.umn.lensspent

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.DaySummary
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction

/**
 * What the Daily tab is allowed to claim about the selected day.
 *
 * [AnalyticsUiState] rather than the view model, matching how the rest of the screen's arithmetic is
 * pinned: the case worth protecting is the empty day, where the "Rp 0" headline must not sprout a row
 * of zeroes underneath it.
 */
class AnalyticsDailyStatsTest {

    /** Friday 18 September 2026, the day these tests are written about. */
    private val day = LocalDate.of(2026, 9, 18)

    private fun expenseOn(date: LocalDate, amount: Long): Transaction = Transaction(
        id = 1L,
        merchant = "Alfamart",
        amount = amount,
        category = Category(id = 1L, name = "Groceries", iconKey = "groceries", colorHex = "#4CAF50"),
        occurredAt = date.atTime(18, 0).atZone(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
    )

    @Test
    fun `a day that holds nothing has no summary row`() {
        val state = AnalyticsUiState(today = day)

        assertFalse(state.hasPurchases)
        assertEquals(0, state.selectedDay.purchaseCount)
        assertNull(state.selectedDay.largest)
    }

    @Test
    fun `a day that holds expenses says how many and names the biggest`() {
        val state = AnalyticsUiState(
            today = day,
            selectedDay = DaySummary(purchaseCount = 2, largest = expenseOn(day, 38_000L))
        )

        assertTrue(state.hasPurchases)
        assertEquals(2, state.selectedDay.purchaseCount)
        val largest = requireNotNull(state.selectedDay.largest) { "a day with expenses names one" }
        assertEquals("Alfamart", largest.merchant)
        assertEquals(38_000L, largest.amount)
    }
}
