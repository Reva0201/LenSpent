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
    val isSaving: Boolean = false
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
 * This is the app's only path to a transaction that did not come from a photo, so it is also the only
 * place where every field starts empty and every rule in [AddExpenseUiState] is reachable by hand.
 */
class AddExpenseViewModel(
    private val repository: TransactionRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(selectedDate = today, today = today)
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private val _saved = MutableStateFlow(false)

    /** True once the row is written; the screen navigates away on it. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = repository.observeCategories().first()
            // No category is pre-selected: choosing one is a requirement of the form, and a default
            // would mean the user could submit a row they never filed anywhere.
            _uiState.update { it.copy(categories = categories) }
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
     * button says is ready is exactly a form this function will write.
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
        val note = state.note.trim().takeIf { it.isNotEmpty() }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.addTransaction(
                    merchant = merchant,
                    amount = amount,
                    categoryId = categoryId,
                    // Noon on the chosen day: the row has a date the user picked and no meaningful
                    // time, and noon is the one time that cannot slide across a DST boundary.
                    occurredAt = state.selectedDate
                        .atTime(LocalTime.NOON)
                        .atZone(clock.zone)
                        .toInstant()
                        .toEpochMilli(),
                    note = note
                )
                _saved.value = true
            } catch (e: Exception) {
                // Keep every field as the user left it so the save can simply be retried.
                e.printStackTrace()
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
