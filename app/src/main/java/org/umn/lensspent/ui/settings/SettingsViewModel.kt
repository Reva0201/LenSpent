package org.umn.lensspent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.umn.lensspent.AppLog
import org.umn.lensspent.SubscriptionStopTimeoutMillis
import org.umn.lensspent.data.local.NoBudgetIdr
import org.umn.lensspent.data.local.ThemePreference
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.remote.AiAccess
import org.umn.lensspent.data.remote.AiBackendConfig
import org.umn.lensspent.data.repository.CaptureImageDeleter
import org.umn.lensspent.data.repository.TransactionRepository

/** Every column of the export, in order. This list is the file's shape. */
private val CsvColumns = listOf("Date", "Merchant", "Category", "Amount (IDR)", "Note")

/** What the settings screen draws. */
data class SettingsUiState(
    val username: String = "",
    val themePreference: ThemePreference = ThemePreference.Default,
    /** The ceiling chosen at onboarding or here, or [NoBudgetIdr] when there is none. */
    val monthlyBudgetIdr: Long = NoBudgetIdr,
    /** Whether this build has a secure AI backend at all. */
    val aiBackendConfigured: Boolean = false,
    /** The user's AI networking switch. */
    val aiNetworkEnabled: Boolean = true,
    /** Whether the AI disclosure has been accepted. */
    val aiConsentGranted: Boolean = false,
    /** Whether the gate is fully open right now. */
    val aiAvailable: Boolean = false,
    /** Size of the stored receipt images, for the "remove" affordance. */
    val storedReceiptBytes: Long = 0L
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
    private val images: CaptureImageDeleter = CaptureImageDeleter.None,
    /** Reads the current directory size; injected so tests need no filesystem. */
    private val receiptBytes: suspend () -> Long = { 0L },
    /** Deletes every stored receipt image. */
    private val deleteReceiptImages: suspend () -> Int = { 0 },
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _pendingCsvExport = MutableStateFlow<String?>(null)

    /**
     * The ledger as CSV, waiting for a share sheet.
     *
     * Non-null means the user has confirmed the share; [SettingsScreen] hands it to the chooser and
     * then calls [onCsvExportHandled], which clears it. The CSV itself is never logged, never
     * persisted, and never written to a shared folder.
     */
    val pendingCsvExport: StateFlow<String?> = _pendingCsvExport.asStateFlow()

    private val _isConfirmingExport = MutableStateFlow(false)

    /** True while the "the whole ledger will be shared" dialog is up. */
    val isConfirmingExport: StateFlow<Boolean> = _isConfirmingExport.asStateFlow()

    private val _isConfirmingReceiptRemoval = MutableStateFlow(false)

    /** True while the "remove stored receipt images" dialog is up. */
    val isConfirmingReceiptRemoval: StateFlow<Boolean> = _isConfirmingReceiptRemoval.asStateFlow()

    private val _receiptBytes = MutableStateFlow(0L)

    /**
     * Reads the receipt directory size, if the caller has one.
     *
     * Called by the screen when it appears rather than launched from `init`: a ViewModel constructor
     * that starts work makes every test of it depend on a dispatcher, and the size is cosmetic.
     */
    fun refreshReceiptSize() {
        viewModelScope.launch {
            _receiptBytes.value = runCatching { receiptBytes() }.getOrDefault(0L)
        }
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferences.preferences,
            _receiptBytes
        ) { stored, bytes ->
            val consent = stored?.aiConsentGranted ?: false
            val enabled = stored?.aiNetworkEnabled ?: true
            SettingsUiState(
                username = stored?.username.orEmpty(),
                themePreference = stored?.themePreference ?: ThemePreference.Default,
                monthlyBudgetIdr = stored?.monthlyBudgetIdr ?: NoBudgetIdr,
                aiBackendConfigured = AiBackendConfig.isConfigured,
                aiNetworkEnabled = enabled,
                aiConsentGranted = consent,
                aiAvailable = AiAccess(
                    configured = AiBackendConfig.isConfigured,
                    enabled = enabled,
                    consentGranted = consent
                ).isAllowed,
                storedReceiptBytes = bytes
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SubscriptionStopTimeoutMillis),
            initialValue = SettingsUiState(aiBackendConfigured = AiBackendConfig.isConfigured)
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

    /** The AI networking switch. Turning it off stops any further request immediately. */
    fun onAiNetworkEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { preferences.setAiNetworkEnabled(enabled) }
    }

    /** Opening the export flow shows the disclosure first; nothing is read until it is confirmed. */
    fun onExportRequested() {
        if (_pendingCsvExport.value != null) return
        _isConfirmingExport.value = true
    }

    fun onExportDismissed() {
        _isConfirmingExport.value = false
    }

    /**
     * Reads the whole ledger once and publishes it as CSV for the screen to share.
     *
     * The read is a one-shot on purpose: the export should be the ledger as it is at the moment the
     * user confirmed, not a file that keeps changing under them while the share sheet is open.
     */
    fun onExportConfirmed() {
        _isConfirmingExport.value = false
        if (_pendingCsvExport.value != null) return
        viewModelScope.launch {
            // The CSV is built in memory and handed straight to the chooser. It is never logged: the
            // file is the complete ledger, and a log line would be a second, unprotected copy.
            _pendingCsvExport.value = transactionsToCsv(ledger.observeTransactions().first(), zone)
        }
    }

    /** The share sheet is up, or the user dismissed it; either way the hand-off is over. */
    fun onCsvExportHandled() {
        _pendingCsvExport.value = null
    }

    /** The screen could not open a chooser (no activity, no target): drop the pending export. */
    fun onCsvExportFailed() {
        _pendingCsvExport.value = null
        AppLog.warn("CSV export could not be handed to a share target")
    }

    fun onReceiptRemovalRequested() {
        _isConfirmingReceiptRemoval.value = true
    }

    fun onReceiptRemovalDismissed() {
        _isConfirmingReceiptRemoval.value = false
    }

    /**
     * Removes receipt photos and capture rows, keeping the ledger.
     *
     * The user asked to remove pictures, not their spending history, so the transactions stay and
     * only their link to a now-deleted capture is cleared. Any image URI the repository returns is
     * deleted through [images]; the directory sweep then removes anything left untracked.
     */
    fun onReceiptRemovalConfirmed() {
        _isConfirmingReceiptRemoval.value = false
        viewModelScope.launch {
            ledger.forgetAllCaptures().forEach { uri -> images.delete(uri) }
            deleteReceiptImages()
            refreshReceiptSize()
        }
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
