package org.umn.lensspent.ui.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction

/**
 * The export, checked as text.
 *
 * The file is the whole product of this feature - it is opened in a spreadsheet, or mailed, with no
 * second chance to correct it later - so the two things that decide whether it opens as columns rather
 * than as one long line are pinned here: the date is the user's own day, and a field containing the
 * separator is quoted.
 */
class TransactionsCsvTest {

    @Test
    fun `the file is a header and one row per expense`() {
        val csv = transactionsToCsv(
            listOf(expense("Supermarket Central", 850_000L, "Groceries", "2026-09-18")),
            Jakarta
        )

        assertEquals(
            "Date,Merchant,Category,Amount (IDR),Note\n" +
                "2026-09-18,Supermarket Central,Groceries,850000,\n",
            csv
        )
    }

    @Test
    fun `an empty ledger is a header and nothing else`() {
        assertEquals(
            "Date,Merchant,Category,Amount (IDR),Note\n",
            transactionsToCsv(emptyList(), Jakarta)
        )
    }

    /**
     * Half past midnight in Jakarta is half past five in the afternoon in UTC, on the day before: the
     * row belongs to the 19th, because that is the day the person who bought it had.
     */
    @Test
    fun `the date is the user's own day, not the UTC one`() {
        val justAfterMidnight = Transaction(
            id = 1L,
            merchant = "Warung Malam",
            amount = 42_000L,
            category = expenseCategory("Dining"),
            // 2026-09-19T00:30+07:00, which is 2026-09-18T17:30Z.
            occurredAt = Instant.parse("2026-09-18T17:30:00Z").toEpochMilli()
        )

        val row = transactionsToCsv(listOf(justAfterMidnight), Jakarta).lineSequence().elementAt(1)

        assertEquals("2026-09-19,Warung Malam,Dining,42000,", row)
    }

    /** A comma in a merchant name is the one input that can turn one row into two. */
    @Test
    fun `a field containing the separator is quoted, quotes and all`() {
        val csv = transactionsToCsv(
            listOf(
                expense(
                    merchant = "Kopi, Susu",
                    amountRp = 25_000L,
                    category = "Dining",
                    date = "2026-09-18",
                    note = "Kopi, susu \"enak\""
                )
            ),
            Jakarta
        )

        assertEquals(
            "2026-09-18,\"Kopi, Susu\",Dining,25000,\"Kopi, susu \"\"enak\"\"\"",
            csv.lineSequence().elementAt(1)
        )
    }
}

private val Jakarta: ZoneId = ZoneId.of("Asia/Jakarta")

private fun expense(
    merchant: String,
    amountRp: Long,
    category: String,
    date: String,
    note: String? = null
): Transaction = Transaction(
    id = 1L,
    merchant = merchant,
    amount = amountRp,
    category = expenseCategory(category),
    occurredAt = LocalDate.parse(date).atStartOfDay(Jakarta).toInstant().toEpochMilli(),
    note = note
)

private fun expenseCategory(name: String): Category =
    Category(id = 1L, name = name, iconKey = "other", colorHex = "#78909C")
