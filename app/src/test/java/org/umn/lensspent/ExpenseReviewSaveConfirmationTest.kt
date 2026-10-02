package org.umn.lensspent

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.umn.lensspent.data.model.Category

/**
 * What the sheet confirms once the row is written.
 *
 * The confirmation is built from the values the write is made with, so this pins the two things the
 * user reads: a draft that cannot be written has nothing to confirm - and so is never written at all -
 * and a draft that can be written is described exactly as it was stored, down to the trimmed merchant,
 * the parsed Rupiah amount and the day the ledger row lands on rather than the day a label happens to
 * show.
 */
class ExpenseReviewSaveConfirmationTest {

    /** Jakarta, because this app's money is Rupiah and a day boundary here is not UTC's. */
    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    private val food = Category(id = 7L, name = "Food", iconKey = "dining", colorHex = "#FF7043")

    private fun atJakarta(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun draft(
        merchant: String = "Warung Sederhana",
        amountText: String = "57500",
        categories: List<Category> = listOf(food),
        selectedCategoryId: Long? = food.id,
        occurredAt: Long = atJakarta(LocalDate.of(2026, 9, 18), 20, 30)
    ) = ExpenseReviewUiState.Editing(
        captureId = 1L,
        imageUri = "content://media/external/images/media/1",
        merchant = merchant,
        amountText = amountText,
        categories = categories,
        selectedCategoryId = selectedCategoryId,
        occurredAt = occurredAt,
        // Derived the way the sheet derives it, so the fixture cannot disagree with the screen
        selectedDate = localDateOf(occurredAt, zone),
        today = LocalDate.of(2026, 9, 19)
    )

    @Test
    fun `a complete draft confirms the merchant, the amount, the category and the day`() {
        val confirmation = requireNotNull(confirmationOf(draft(), zone)) { "a complete draft saves" }

        assertEquals("Warung Sederhana", confirmation.merchant)
        assertEquals(57_500L, confirmation.amountRupiah)
        assertEquals(7L, confirmation.categoryId)
        assertEquals("Food", confirmation.categoryName)
        assertEquals(LocalDate.of(2026, 9, 18), confirmation.date)
    }

    @Test
    fun `the merchant is trimmed, so the confirmation names exactly what is stored`() {
        val confirmation = requireNotNull(confirmationOf(draft(merchant = "  Warung Sederhana "), zone))

        assertEquals("Warung Sederhana", confirmation.merchant)
    }

    @Test
    fun `the amount confirmed is the parsed Rupiah, not the text that was typed`() {
        val confirmation = requireNotNull(confirmationOf(draft(amountText = "1.250.000"), zone))

        assertEquals(1_250_000L, confirmation.amountRupiah)
    }

    @Test
    fun `a draft with no merchant has nothing to confirm`() {
        assertNull(confirmationOf(draft(merchant = ""), zone))
        assertNull(confirmationOf(draft(merchant = "   "), zone))
    }

    @Test
    fun `a draft with no amount in it has nothing to confirm`() {
        assertNull(confirmationOf(draft(amountText = ""), zone))
        assertNull(confirmationOf(draft(amountText = "0"), zone))
        assertNull(confirmationOf(draft(amountText = "not a number"), zone))
    }

    @Test
    fun `a draft with no category has nothing to confirm`() {
        assertNull(confirmationOf(draft(selectedCategoryId = null), zone))
    }

    @Test
    fun `a category the sheet cannot name does not block the write, and is not made up`() {
        val confirmation = requireNotNull(confirmationOf(draft(selectedCategoryId = 99L), zone))

        // The row still goes in under the id it was given; only the name is claimed to be unknown
        assertEquals(99L, confirmation.categoryId)
        assertNull(confirmation.categoryName)
    }

    @Test
    fun `the day confirmed is the day the row lands on, not the day a label shows`() {
        // A draft whose label has drifted a day ahead of its timestamp still confirms the stored day
        val drifted = draft(occurredAt = atJakarta(LocalDate.of(2026, 9, 18), 23, 30))
            .copy(selectedDate = LocalDate.of(2026, 9, 19))

        assertEquals(LocalDate.of(2026, 9, 18), requireNotNull(confirmationOf(drifted, zone)).date)
    }

    @Test
    fun `the day is the sheet's own, so an early Jakarta receipt is not yesterday`() {
        // 06:00 in Jakarta is 23:00 the previous day in UTC: the row lands on the 18th, and so does
        // the confirmation - a receipt is dated where it was bought, not where the clocks are set
        val earlyJakarta = draft(occurredAt = atJakarta(LocalDate.of(2026, 9, 18), 6, 0))

        assertEquals(LocalDate.of(2026, 9, 18), requireNotNull(confirmationOf(earlyJakarta, zone)).date)
    }
}
