package org.umn.lensspent

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule behind the date stepper on the confirmation sheet.
 *
 * An expense can be dated in the past - that is the whole point of photographing an old receipt - but
 * never in the future, because the future has no spending in it yet. These assertions pin down that a
 * forward step stops on the current date, so the sheet can never open a draft dated tomorrow.
 */
class ExpenseReviewDateSteppingTest {

    /** The day these tests are written on: Saturday 19 September 2026. */
    private val today = LocalDate.of(2026, 9, 19)

    private fun stepForwardFrom(current: LocalDate, days: Long = 1L): LocalDate =
        steppedDate(current, days, today)

    private fun stepBackFrom(current: LocalDate, days: Long = 1L): LocalDate =
        steppedDate(current, -days, today)

    @Test
    fun `a forward step moves on by one day`() {
        assertEquals(LocalDate.of(2026, 9, 18), stepForwardFrom(LocalDate.of(2026, 9, 17)))
    }

    @Test
    fun `a forward step stops on today`() {
        assertEquals(today, stepForwardFrom(LocalDate.of(2026, 9, 18)))
        assertEquals(today, stepForwardFrom(today))
    }

    @Test
    fun `a forward step never walks past today, however far it is asked to go`() {
        assertEquals(today, stepForwardFrom(today, days = 5L))
        assertEquals(today, stepForwardFrom(LocalDate.of(2026, 9, 1), days = 30L))
        // A single step this large would land in a future year; the ceiling still holds
        assertEquals(today, stepForwardFrom(LocalDate.of(2026, 9, 1), days = 365L))
        assertEquals(today, stepForwardFrom(LocalDate.of(2020, 1, 1), days = 10_000L))
    }

    @Test
    fun `a draft already ahead of today is left where it is, not snapped back`() {
        val tomorrow = today.plusDays(1)
        val farAhead = tomorrow.plusDays(9)
        assertEquals(tomorrow, stepForwardFrom(tomorrow))
        assertEquals(farAhead, stepForwardFrom(farAhead, days = 3L))
    }

    @Test
    fun `a draft ahead of today can still be stepped back one day at a time`() {
        assertEquals(today.plusDays(1), stepBackFrom(today.plusDays(2)))
        assertEquals(today, stepBackFrom(today.plusDays(2), days = 2L))
    }

    @Test
    fun `a backward step is unrestricted, so last month's receipt can be entered`() {
        assertEquals(LocalDate.of(2026, 9, 18), stepBackFrom(today))
        assertEquals(LocalDate.of(2026, 8, 20), stepBackFrom(today, days = 30L))
    }
}
