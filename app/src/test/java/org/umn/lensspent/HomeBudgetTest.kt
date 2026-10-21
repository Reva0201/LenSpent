package org.umn.lensspent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.local.NoBudgetIdr

/**
 * The budget card's arithmetic: what the dashboard can say about a month, with and without a ceiling.
 *
 * [HomeUiState] rather than the view model, because this is the part of the dashboard that decides what
 * the user reads - and the case worth pinning is the one a fresh install is in: no budget stored, and a
 * card that must not pretend the month is empty.
 */
class HomeBudgetTest {

    @Test
    fun `a fresh install has no ceiling and does not claim to be under one`() {
        val state = HomeUiState(isLoading = false, monthTotal = 500_000L)

        assertFalse(state.hasBudget)
        assertEquals(0f, state.budgetUsedFraction, 0f)
        assertEquals(0, state.budgetUsedPercent)
    }

    @Test
    fun `a month under the ceiling reports the share spent`() {
        val state = HomeUiState(
            isLoading = false,
            monthTotal = 1_000_000L,
            monthlyBudget = 4_000_000L
        )

        assertTrue(state.hasBudget)
        assertEquals(0.25f, state.budgetUsedFraction, 0.0001f)
        assertEquals(25, state.budgetUsedPercent)
    }

    /** Over budget is still a full bar: the card is a summary, not the place to confront anyone. */
    @Test
    fun `overspending fills the bar and goes no further`() {
        val state = HomeUiState(
            isLoading = false,
            monthTotal = 9_000_000L,
            monthlyBudget = 4_000_000L
        )

        assertEquals(1f, state.budgetUsedFraction, 0f)
        assertEquals(100, state.budgetUsedPercent)
    }
}
