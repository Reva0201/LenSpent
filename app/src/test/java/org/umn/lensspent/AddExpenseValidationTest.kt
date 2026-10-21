package org.umn.lensspent

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.model.Category

/**
 * The Add Expense form's rules, checked without a screen, a database or a dispatcher.
 *
 * All of it lives in [AddExpenseUiState], so these are plain assertions about a data class. That is the
 * point of keeping validation in the state rather than in the composable: the rule "Save is blocked
 * until the amount, merchant and category are all valid" is one line to test here, and it is the same
 * line the button and the write both read.
 */
class AddExpenseValidationTest {

    private val today = LocalDate.of(2026, 10, 22)

    private fun state(
        amountText: String = "",
        merchant: String = "",
        note: String = "",
        selectedCategoryId: Long? = null,
        amountTouched: Boolean = false,
        merchantTouched: Boolean = false,
        categoryTouched: Boolean = false,
        saveAttempted: Boolean = false
    ) = AddExpenseUiState(
        amountText = amountText,
        merchant = merchant,
        note = note,
        categories = listOf(Category(1L, "Groceries", "groceries", "#4CAF50")),
        selectedCategoryId = selectedCategoryId,
        selectedDate = today,
        today = today,
        amountTouched = amountTouched,
        merchantTouched = merchantTouched,
        categoryTouched = categoryTouched,
        saveAttempted = saveAttempted
    )

    @Test
    fun `an empty form cannot be saved`() {
        assertFalse(state().canSave)
    }

    @Test
    fun `a complete form can be saved`() {
        val form = state(amountText = "45000", merchant = "Warteg", selectedCategoryId = 1L)

        assertTrue(form.canSave)
    }

    @Test
    fun `an amount of zero is not a valid expense`() {
        val form = state(
            amountText = "0",
            merchant = "Warteg",
            selectedCategoryId = 1L,
            amountTouched = true
        )

        assertFalse(form.canSave)
        assertTrue(form.amountError)
    }

    @Test
    fun `letters in the amount are rejected rather than parsed`() {
        val form = state(amountText = "45rb", merchant = "Warteg", selectedCategoryId = 1L)

        assertFalse(form.canSave)
        assertEquals("Use digits only, for example 45000", form.amountSupportingText)
    }

    @Test
    fun `an untouched amount does not shout at the user`() {
        val form = state(merchant = "Warteg", selectedCategoryId = 1L)

        // Nothing typed into it and nothing attempted: the field is quiet.
        assertFalse(form.amountError)
    }

    @Test
    fun `pressing save reveals every outstanding error`() {
        val form = state(saveAttempted = true)

        assertTrue(form.amountError)
        assertTrue(form.merchantError)
        assertTrue(form.categoryError)
        assertFalse(form.canSave)
    }

    @Test
    fun `a category is required`() {
        val form = state(amountText = "45000", merchant = "Warteg", categoryTouched = true)

        assertTrue(form.categoryError)
        assertFalse(form.canSave)
    }

    @Test
    fun `a merchant of only spaces is not a merchant`() {
        val form = state(amountText = "45000", merchant = "   ", selectedCategoryId = 1L)

        assertFalse(form.canSave)
    }

    @Test
    fun `a note longer than the limit blocks the save`() {
        val form = state(
            amountText = "45000",
            merchant = "Warteg",
            selectedCategoryId = 1L,
            note = "x".repeat(NoteMaxLength + 1)
        )

        assertTrue(form.noteOverLimit)
        assertFalse(form.canSave)
    }

    @Test
    fun `a note exactly on the limit is allowed`() {
        val form = state(
            amountText = "45000",
            merchant = "Warteg",
            selectedCategoryId = 1L,
            note = "x".repeat(NoteMaxLength)
        )

        assertFalse(form.noteOverLimit)
        assertTrue(form.canSave)
    }

    @Test
    fun `thousands separators are read as the number the user meant`() {
        val form = state(amountText = "Rp 45.000", merchant = "Warteg", selectedCategoryId = 1L)

        assertEquals(45_000L, form.amountRupiah)
        assertTrue(form.canSave)
    }
}
