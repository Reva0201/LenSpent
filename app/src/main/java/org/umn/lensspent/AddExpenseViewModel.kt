package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.parseRupiahAmount
import org.umn.lensspent.data.repository.TransactionRepository

/** Longest note the field accepts; the counter in the form reads off this. */
internal const val NoteMaxLength = 200

/**
 * The Add Expense form, as state.
 *
 * Every field carries both its text and whether the user has visited it. That pair is what lets the
 * form say "this is required" at the right moment: a field the user has not typed in yet stays quiet,
 * and the moment they leave it - or press Save - the error appears. Showing every error from the first
 * frame is how a form greets the user with a wall of red.
 *
 * [canSave] is the same rule the ViewModel enforces on write, expressed as one property, so the button's
 * enabled state can never be more permissive than the write. That is also why it is not a `var`: it is
 * derived from the fields, not something a screen has to remember to keep in step.
 */
data class AddExpenseUiState(
    val amountText: String = "",
    val merchant: String = "",
    val note: String = "",
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val amountTouched: Boolean = false,
    val merchantTouched: Boolean = false,
    val categoryTouched: Boolean = false,
    /** Set by a Save press, which is what reveals every error at once. */
    val saveAttempted: Boolean = false,
    val isSaving: Boolean = false,
    /**
     * Whether this form is correcting an existing row rather than creating one. It changes the title,
     * the button and which repository call is made; nothing else about the form differs, which is the
     * point - a correction should be the same job as an entry.
     */
    val isEditing: Boolean = false
) {

    /** Parsed from what is typed, every time, so the field and the truth cannot drift apart. */
    val amountRupiah: Long? get() = parseRupiahAmount(amountText)

    val amountError: Boolean
        get() = (amountTouched || saveAttempted) &&
            (amountText.isBlank() || (amountRupiah ?: 0L) <= 0L)

    val merchantError: Boolean
        get() = (merchantTouched || saveAttempted) && merchant.isBlank()

    val categoryError: Boolean
        get() = (categoryTouched || saveAttempted) && selectedCategoryId == null

    val noteOverLimit: Boolean get() = note.length > NoteMaxLength

    /** The future has no expenses in it yet, so the forward arrow stops on today. */
    val canStepForward: Boolean get() = selectedDate.isBefore(today)

    val canSave: Boolean
        get() = !isSaving &&
            merchant.isNotBlank() &&
            (amountRupiah ?: 0L) > 0L &&
            selectedCategoryId != null &&
            !noteOverLimit

    /** "Use digits only" when the field holds something unreadable, "required" when it holds nothing. */
    val amountSupportingText: String
        get() = when {
            amountText.isBlank() -> "Amount is required"
            amountRupiah == null -> "Use digits only, for example 45000"
            (amountRupiah ?: 0L) <= 0L -> "Amount must be more than Rp 0"
            else -> ""
        }
}

/**
 * Owns the manual-entry form: the categories it offers, the draft being typed, and the one write.
 *
 * The same class backs creation and correction. [transactionId] decides which: null means a new row is
 * inserted, a non-null id means that row is loaded into the form and updated in place. Sharing the form
 * is deliberate - an edited expense has exactly the same fields and rules as a new one, and two copies
 * of that logic would drift apart the first time a rule changed.
 */
class AddExpenseViewModel(
    private val repository: TransactionRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val transactionId: Long? = null
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(
            selectedDate = today,
            today = today,
            isEditing = transactionId != null
        )
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private val _saved = MutableStateFlow(false)

    /** True once the row is written; the screen navigates away on it. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** The row being edited, once it has been loaded. Null while creating, or before the load lands. */
    private var editing: Transaction? = null

    init {
        viewModelScope.launch {
            val categories = repository.observeCategories().first()
            _uiState.update { it.copy(categories = categories) }

            val id = transactionId ?: return@launch
            val existing = repository.observeTransaction(id).first() ?: return@launch
            editing = existing
            _uiState.update { state ->
                state.copy(
                    amountText = existing.amount.toString(),
                    merchant = existing.merchant,
                    note = existing.note.orEmpty(),
                    selectedCategoryId = existing.category.id,
                    selectedDate = localDateOf(existing.occurredAt, clock.zone),
                    // A row that already exists is already valid, so the form opens without a wall of
                    // "required" errors on fields the user did not touch.
                    amountTouched = true,
                    merchantTouched = true,
                    categoryTouched = true
                )
            }
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amountText = value, amountTouched = true) }
    }

    fun onMerchantChange(value: String) {
        _uiState.update { it.copy(merchant = value, merchantTouched = true) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    fun onCategorySelected(categoryId: Long) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, categoryTouched = true) }
    }

    /** Same rule as the review sheet's stepper: backward freely, forward only up to today. */
    fun onDayStep(days: Long) {
        _uiState.update { it.copy(selectedDate = steppedDate(it.selectedDate, days, it.today)) }
    }

    /**
     * Writes the expense, or reveals every outstanding error.
     *
     * The guard is [AddExpenseUiState.canSave] rather than a second copy of the rules, so a form the
     * button says is ready is exactly a form this function will write. Whether that write inserts or
     * updates is decided by [editing], which is set when the form was opened for an existing row.
     */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update {
                it.copy(
                    saveAttempted = true,
                    amountTouched = true,
                    merchantTouched = true,
                    categoryTouched = true
                )
            }
            return
        }

        val merchant = state.merchant.trim()
        val amount = state.amountRupiah ?: return
        val categoryId = state.selectedCategoryId ?: return
        val category = state.categories.firstOrNull { it.id == categoryId } ?: return
        val note = state.note.trim().takeIf { it.isNotEmpty() }
        // Rows are ordered by occurredAt, so a manual entry has to carry a time of its own. Noon was
        // the first choice for being DST-safe, but it sorted a new expense into the middle of the day:
        // demo receipts run 09:30-19:30, so "Valorant, just added" appeared under Indomaret. A new line
        // now takes the current time on the chosen day, which is also what the user means by "now".
        val now = clock.instant().atZone(clock.zone)
        val occurredAt = if (state.selectedDate == today) {
            now.toInstant().toEpochMilli()
        } else {
            // Backdating keeps the same clock time, so two entries made on the same past day stay in
            // the order they were typed.
            state.selectedDate
                .atTime(now.toLocalTime())
                .atZone(clock.zone)
                .toInstant()
                .toEpochMilli()
        }

        val existing = editing
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (existing != null) {
                    // Exactly the fields the form owns. Everything else on the row - its id, when it
                    // was recorded, the capture it came from - is left as it is, so a correction
                    // cannot quietly re-date the audit trail.
                    repository.updateTransaction(
                        existing.copy(
                            merchant = merchant,
                            amount = amount,
                            category = category,
                            occurredAt = occurredAt,
                            note = note
                        )
                    )
                } else {
                    repository.addTransaction(
                        merchant = merchant,
                        amount = amount,
                        categoryId = categoryId,
                        occurredAt = occurredAt,
                        note = note
                    )
                }
                _saved.value = true
            } catch (e: Exception) {
                // Keep every field as the user left it so the save can simply be retried. The
                // exception can quote a row's contents, so only the type is recorded, and only in
                // debug builds.
                AppLog.warn("saving an expense failed", e)
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
