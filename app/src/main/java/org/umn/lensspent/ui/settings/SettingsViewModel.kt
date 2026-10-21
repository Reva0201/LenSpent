package org.umn.lensspent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.umn.lensspent.SubscriptionStopTimeoutMillis
import org.umn.lensspent.data.local.NoBudgetIdr
import org.umn.lensspent.data.local.ThemePreference
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.TransactionRepository

/** Every column of the export, in order. This list is the file's shape. */
private val CsvColumns = listOf("Date", "Merchant", "Category", "Amount (IDR)", "Note")

/** What the settings screen draws. */
data class SettingsUiState(
    val username: String = "",
    val themePreference: ThemePreference = ThemePreference.Default,
    /** The ceiling chosen at onboarding or here, or [NoBudgetIdr] when there is none. */
    val monthlyBudgetIdr: Long = NoBudgetIdr
)

/**
 * Drives the settings screen.
 *
 * The screen has no state of its own beyond the exported file: [uiState] is the store's own snapshot,
 * so a value that reaches the store is on screen the moment it lands, and there is nowhere for the two
 * to drift apart.
 */
class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val ledger: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _pendingCsvExport = MutableStateFlow<String?>(null)

    /** The ledger as CSV, waiting for a share sheet; null when there is nothing to hand off. */
    val pendingCsvExport: StateFlow<String?> = _pendingCsvExport.asStateFlow()

    val uiState: StateFlow<SettingsUiState> =
        preferences.preferences.map { stored ->
            SettingsUiState(
                username = stored?.username.orEmpty(),
                themePreference = stored?.themePreference ?: ThemePreference.Default,
                monthlyBudgetIdr = stored?.monthlyBudgetIdr ?: NoBudgetIdr
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SubscriptionStopTimeoutMillis),
            initialValue = SettingsUiState()
        )

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch { preferences.setThemePreference(preference) }
    }

    /**
     * Stores the new ceiling, or clears it back to none when the dialog was saved empty.
     *
     * A whole number of Rupiah rather than text: a half-typed number lives in the dialog and dies with
     * it, and only something that parsed into an amount reaches this point. Zero is the same answer as
     * empty - "no ceiling" - because IDR has no minor unit and no one budgets Rp 0.
     */
    fun onBudgetConfirmed(amount: Long) {
        viewModelScope.launch { preferences.setMonthlyBudgetIdr(amount) }
    }

    /**
     * Reads the whole ledger once and publishes it as CSV for the screen to share.
     *
     * The read is a one-shot on purpose: the export should be the ledger as it is at the moment the
     * user asked, not a file that keeps changing under them while the share sheet is open.
     */
    fun exportTransactions() {
        if (_pendingCsvExport.value != null) return
        viewModelScope.launch {
            _pendingCsvExport.value = transactionsToCsv(ledger.observeTransactions().first(), zone)
        }
    }

    /** The share sheet is up, or the user dismissed it; either way the hand-off is over. */
    fun onCsvExportHandled() {
        _pendingCsvExport.value = null
    }
}

/**
 * The whole ledger as CSV: a header row, then one row per expense in the order it was passed in.
 *
 * [zone] is what turns the stored epoch millis into a date, and it is a parameter because the answer
 * depends on where the phone is: a purchase at 23:30 in Jakarta belongs to that day, not to the one
 * the UTC clock had already turned over to. The same rule the dashboard groups by.
 */
internal fun transactionsToCsv(transactions: List<Transaction>, zone: ZoneId): String = buildString {
    appendLine(CsvColumns.joinToString(","))
    transactions.forEach { transaction ->
        appendLine(
            listOf(
                // ISO dates: the one format every spreadsheet reads without being told what it is.
                Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate().toString(),
                csvField(transaction.merchant),
                csvField(transaction.category.name),
                transaction.amount.toString(),
                csvField(transaction.note.orEmpty())
            ).joinToString(",")
        )
    }
}

/**
 * One field, quoted if it has to be.
 *
 * A merchant name is whatever the receipt printed, so it can contain the separator itself. Quoting is
 * the difference between "Kopi, Susu" filling one column and that note spilling a shifted row into the
 * next - RFC 4180 rules: quote the field, and double every quote inside it.
 *
 * Rows end in "\n" rather than the RFC's "\r\n": every reader accepts it, and a file assembled in
 * memory has no reason to carry a line ending from another decade.
 */
private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
