package org.umn.lensspent.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.local.NoBudgetIdr

/**
 * What a budget field means - the one rule onboarding and the settings dialog share.
 *
 * Text in, text out, because that is the whole of it: the two screens around it own the field and the
 * store, and this is the last place the app can disagree with itself about what the user typed.
 */
class BudgetInputTest {

    /**
     * Blank is an answer, not a failure: the field is optional, and the dashboard has something honest
     * to draw with no ceiling at all.
     */
    @Test
    fun `a blank field is a decision rather than an error`() {
        assertEquals(NoBudgetIdr, parseBudgetInput(""))
        assertEquals(NoBudgetIdr, parseBudgetInput("   "))
    }

    @Test
    fun `separators are read the way the ledger reads them`() {
        assertEquals(3_750_000L, parseBudgetInput("Rp 3.750.000"))
        assertEquals(3_750_000L, parseBudgetInput("3750000"))
        assertEquals(3_750_000L, parseBudgetInput("3 750 000"))
    }

    /**
     * A ceiling is the one number here that nobody can afford to have guessed at: "125rb" means
     * Rp 125.000, and reading it as Rp 125 would be the app's mistake, not the user's.
     */
    @Test
    fun `shorthand is refused rather than guessed at`() {
        assertNull(parseBudgetInput("125rb"))
        assertNull(parseBudgetInput("."))
    }

    /** The line under the field has to tell the three states apart, or it says nothing at all. */
    @Test
    fun `each state of the field reads differently`() {
        val blank = budgetSupportingText("", NoBudgetIdr)
        val unreadable = budgetSupportingText("125rb", null)
        val typed = budgetSupportingText("3750000", 3_750_000L)

        assertEquals("Rp 3.750.000 a month.", typed)
        assertTrue(unreadable != blank)
        assertTrue(typed != blank)
        assertTrue(typed != unreadable)
    }
}
