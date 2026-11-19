package org.umn.lensspent.ui.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.StubTransactionRepository

/**
 * The export, checked as text and as a confirmation flow.
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

    /** A newline inside a note must be quoted, or it splits the row it belongs to. */
    @Test
    fun `a newline in a note is quoted`() {
        val csv = transactionsToCsv(
            listOf(
                expense(
                    merchant = "Warung",
                    amountRp = 20_000L,
                    category = "Dining",
                    date = "2026-09-18",
                    note = "line one\nline two"
                )
            ),
            Jakarta
        )

        assertTrue(csv, csv.contains("\"line one\nline two\""))
    }

    @Test
    fun `the confirmation explains the whole ledger leaves the app`() {
        assertTrue(CsvExportDialogTitle.contains("complete ledger"))
        listOf("date, merchant, category, amount and note", "whichever app you pick", "unless")
            .forEach { phrase ->
                assertTrue(
                    "the confirmation must say '$phrase': $CsvExportDialogBody",
                    CsvExportDialogBody.contains(phrase)
                )
            }
    }
}

/**
 * The settings state machine around the export, with a real repository stub.
 *
 * The point of these tests is that export is a two-step act: nothing is read, built or handed off
 * until the user confirms, and the pending value is cleared on every exit path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CsvExportFlowTest {

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val rows = listOf(expense("Supermarket Central", 850_000L, "Groceries", "2026-09-18"))

    @Test
    fun `requesting an export only asks for confirmation`() {
        val viewModel = viewModel()

        viewModel.onExportRequested()

        assertTrue(viewModel.isConfirmingExport.value)
        assertNull("no CSV is built before the confirmation", viewModel.pendingCsvExport.value)
    }

    @Test
    fun `dismissing the confirmation leaves nothing to share`() {
        val viewModel = viewModel()

        viewModel.onExportRequested()
        viewModel.onExportDismissed()

        assertFalse(viewModel.isConfirmingExport.value)
        assertNull(viewModel.pendingCsvExport.value)
    }

    @Test
    fun `confirming builds the CSV and closes the dialog`() = kotlinx.coroutines.runBlocking {
        val viewModel = viewModel()

        viewModel.onExportRequested()
        viewModel.onExportConfirmed()

        assertFalse(viewModel.isConfirmingExport.value)
        val csv = requireNotNull(viewModel.pendingCsvExport.value)
        assertTrue(csv.startsWith("Date,Merchant,Category,Amount (IDR),Note"))
        assertTrue(csv.contains("Supermarket Central"))
    }

    /** Once the chooser has opened, or failed to, the ledger must not still be pending. */
    @Test
    fun `handling or failing the hand-off clears the pending export`() {
        val viewModel = viewModel()

        viewModel.onExportRequested()
        viewModel.onExportConfirmed()
        viewModel.onCsvExportHandled()
        assertNull(viewModel.pendingCsvExport.value)

        viewModel.onExportRequested()
        viewModel.onExportConfirmed()
        viewModel.onCsvExportFailed()
        assertNull(viewModel.pendingCsvExport.value)
    }

    private fun viewModel() = SettingsViewModel(
        preferences = preferences(),
        ledger = StubTransactionRepository(rows),
        zone = Jakarta
    )
}

/**
 * A real DataStore over a temp file.
 *
 * SettingsViewModel reads the store as a flow, so a fake would have to reimplement that behaviour;
 * a throwaway file is simpler and exercises the same code the app does.
 */
private fun preferences(): UserPreferencesRepository {
    val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )
    val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = {
            java.io.File.createTempFile("settings-prefs", ".preferences_pb").also {
                it.deleteOnExit()
            }
        }
    )
    return UserPreferencesRepository(store)
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


