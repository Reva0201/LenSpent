package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.umn.lensspent.data.extraction.CaptureExtractor
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.parseRupiahAmount
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * The confirmation sheet's state machine: the image is being read, then it is edited, then it is saved.
 */
sealed interface ExpenseReviewUiState {

    val captureId: Long

    /** Extraction is running; nothing is editable yet. */
    data class Processing(override val captureId: Long) : ExpenseReviewUiState

    /** The capture could not be loaded (deleted, or a stale navigation argument). */
    data class Unavailable(override val captureId: Long) : ExpenseReviewUiState

    /** The draft the user is editing. */
    data class Editing(
        override val captureId: Long,
        val imageUri: String,
        val merchant: String,
        val amountText: String,
        val categories: List<Category>,
        val selectedCategoryId: Long?,
        val occurredAt: Long,
        /** The day [occurredAt] falls on, in the ViewModel's zone - exactly what the stepper shows. */
        val selectedDate: LocalDate,
        /** Today, as the ViewModel's clock sees it: the newest day an expense can be dated. */
        val today: LocalDate,
        val isSaving: Boolean = false
    ) : ExpenseReviewUiState {

        /** Parsed from what is actually typed, every time, so the field and the truth cannot drift. */
        val amountRupiah: Long? get() = parseRupiahAmount(amountText)

        val amountError: Boolean get() = amountText.isNotBlank() && (amountRupiah ?: 0L) <= 0L

        /** The future has no expenses in it yet, so the forward arrow stops on today. */
        val canStepForward: Boolean get() = selectedDate.isBefore(today)

        val canSave: Boolean
            get() = !isSaving &&
                merchant.isNotBlank() &&
                (amountRupiah ?: 0L) > 0L &&
                selectedCategoryId != null
    }
}

/**
 * The day a draft lands on after stepping [days] through the calendar, given that [today] is the newest
 * day an expense can be dated.
 *
 * A forward step stops on today instead of walking into a future that has no spending in it. A draft
 * that already sits ahead of today - a receipt read as tomorrow's date - is left exactly where it is,
 * so it can be stepped back by hand rather than silently snapped. Backward steps are unrestricted,
 * because last year's receipts still have to be entered.
 */
internal fun steppedDate(current: LocalDate, days: Long, today: LocalDate): LocalDate = when {
    days <= 0L -> current.plusDays(days)
    current.isAfter(today) -> current
    else -> minOf(current.plusDays(days), today)
}

/** The calendar day an instant falls on, in [zone] - the same day the stepper's label shows. */
internal fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/**
 * The expense that was just written, held only until the sheet has shown the user that it landed.
 *
 * Built from the values the row is written from rather than read back out of storage, so what the
 * confirmation says and what the ledger holds cannot disagree.
 */
internal data class SavedExpense(
    val merchant: String,
    val amountRupiah: Long,
    /** The category the row was filed under, by the id the sheet's chips know. */
    val categoryId: Long,
    /** That category's name, or null when the sheet's own taxonomy cannot name the id. */
    val categoryName: String?,
    /** The day the row lands on, in the sheet's zone - the day the stepper's label showed. */
    val date: LocalDate
)

/**
 * The confirmation [state] would save as, or null when it could not be saved at all.
 *
 * The Save button is enabled on exactly these terms, so this is the same rule stated where the write
 * reads it: a draft with no merchant, no positive amount or no category never reaches the repository,
 * and so never has anything to confirm. The merchant is trimmed here rather than at the call site,
 * which is what makes the name the user is shown the name that was stored.
 *
 * The day comes from [ExpenseReviewUiState.Editing.occurredAt] - the timestamp the row is written
 * with - rather than from the label, so a confirmation can only ever name the day it actually wrote.
 *
 * Pure, and shared with its test, the way the stepper's rule is: what may be saved, and what the user
 * is told about it, is one decision and is worth pinning down without a database in the way.
 */
internal fun confirmationOf(state: ExpenseReviewUiState.Editing, zone: ZoneId): SavedExpense? {
    val merchant = state.merchant.trim()
    val amount = state.amountRupiah ?: return null
    val categoryId = state.selectedCategoryId ?: return null
    if (merchant.isEmpty() || amount <= 0L) return null

    return SavedExpense(
        merchant = merchant,
        amountRupiah = amount,
        categoryId = categoryId,
        // A category the chips cannot name is not a reason to refuse the write; it is only a reason
        // for the confirmation not to claim it knows the name.
        categoryName = state.categories.firstOrNull { it.id == categoryId }?.name,
        date = localDateOf(state.occurredAt, zone)
    )
}

/**
 * Owns the capture -> draft -> transaction flow.
 *
 * Everything the user confirms is written through the repository, and the capture is linked to the
 * transaction by id, so a saved expense can always be traced back to the photo that produced it.
 */
class ExpenseReviewViewModel(
    private val repository: TransactionRepository,
    private val extractor: CaptureExtractor,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val zone: ZoneId = clock.zone

    /** Today, as [clock] sees it: the newest day the draft may be dated. */
    private val today: LocalDate = LocalDate.now(clock)

    private val _uiState = MutableStateFlow<ExpenseReviewUiState?>(null)
    val uiState: StateFlow<ExpenseReviewUiState?> = _uiState.asStateFlow()

    private val _finished = MutableStateFlow(false)

    /**
     * True once the sheet is done - a saved expense the user has acknowledged, or a discard - so the
     * screen can navigate away.
     */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val _savedExpense = MutableStateFlow<SavedExpense?>(null)

    /**
     * The confirmation to show, non-null only between a successful save and [acknowledgeSaved].
     *
     * Saving deliberately does not set [finished]: the row is written, but the sheet does not leave
     * until the user has read that it was. What a photo became is therefore the last thing on screen
     * after a photo, instead of a dashboard that has already moved on.
     */
    internal val savedExpense: StateFlow<SavedExpense?> = _savedExpense.asStateFlow()

    private var startedCaptureId: Long? = null

    /** Idempotent on purpose: a recomposition or a rotation must not restart the extraction. */
    fun start(captureId: Long) {
        if (startedCaptureId == captureId) return
        startedCaptureId = captureId
        _uiState.value = ExpenseReviewUiState.Processing(captureId)
        _savedExpense.value = null

        viewModelScope.launch {
            val capture = repository.findCapture(captureId)
            if (capture == null) {
                _uiState.value = ExpenseReviewUiState.Unavailable(captureId)
                return@launch
            }

            // The extractor records its own output - the OCR text and the model version that produced
            // it - on the capture, so the sheet only has to turn the returned draft into edit state.
            // Its account of the stages it ran is written to logcat as it goes; nothing on this
            // screen shows it.
            val draft = extractor.extract(capture)

            val categories = repository.observeCategories().first()
            _uiState.value = ExpenseReviewUiState.Editing(
                captureId = captureId,
                imageUri = capture.sourceUri,
                merchant = draft.merchant,
                amountText = draft.amountRupiah.takeIf { it > 0L }?.toString().orEmpty(),
                categories = categories,
                // A detected category wins; otherwise start on "Other" rather than on an arbitrary one
                selectedCategoryId = draft.categoryId
                    ?: categories.firstOrNull { it.name == FallbackCategoryName }?.id,
                occurredAt = draft.occurredAt,
                selectedDate = localDateOf(draft.occurredAt, zone),
                today = today
            )
        }
    }

    fun onMerchantChange(value: String) = updateEditing { it.copy(merchant = value) }

    fun onAmountChange(value: String) = updateEditing { it.copy(amountText = value) }

    fun onCategorySelected(categoryId: Long) =
        updateEditing { it.copy(selectedCategoryId = categoryId) }

    /**
     * Steps the purchase date, keeping the time of day. Done in calendar terms rather than by adding
     * milliseconds, so a DST shift can never slide the date by one, and a forward step stops on today
     * rather than walking into a future that has no spending in it.
     */
    fun onDayStep(days: Long) = updateEditing { state ->
        val time = Instant.ofEpochMilli(state.occurredAt).atZone(zone).toLocalTime()
        val target = steppedDate(state.selectedDate, days, state.today)
        state.copy(
            selectedDate = target,
            occurredAt = target.atTime(time).atZone(zone).toInstant().toEpochMilli()
        )
    }

    /**
     * Writes the draft to the ledger, then stops - the confirmation is read before the sheet leaves.
     *
     * The write and the confirmation are both built from [confirmationOf]'s answer, so they cannot
     * describe different things, and a draft it refuses never reaches the repository at all.
     */
    fun save() {
        val state = _uiState.value as? ExpenseReviewUiState.Editing ?: return
        val confirmation = confirmationOf(state, zone) ?: return

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            try {
                repository.addTransaction(
                    merchant = confirmation.merchant,
                    amount = confirmation.amountRupiah,
                    categoryId = confirmation.categoryId,
                    occurredAt = state.occurredAt,
                    note = null,
                    captureId = state.captureId
                )
                repository.updateCaptureStatus(state.captureId, CaptureStatus.CONFIRMED)
                // The row exists; the sheet now says so. Nothing here sets [finished] - only the user
                // acknowledging the confirmation does, which is what keeps the dashboard from
                // appearing before the expense has been seen to land.
                _savedExpense.value = confirmation
            } catch (e: Exception) {
                // Leave the sheet open with the user's input intact, so saving can simply be retried
                e.printStackTrace()
                _uiState.value = state.copy(isSaving = false)
            }
        }
    }

    /**
     * The user has read the confirmation, so the sheet may go.
     *
     * Reached by the confirmation's button, by Back, or by a tap outside it - all three mean the same
     * thing, which is that the screen has been seen.
     */
    fun acknowledgeSaved() {
        _savedExpense.value = null
        _finished.value = true
    }

    fun discard() {
        val captureId = _uiState.value?.captureId ?: return
        viewModelScope.launch {
            repository.updateCaptureStatus(captureId, CaptureStatus.FAILED)
            _finished.value = true
        }
    }

    fun consumeFinished() {
        _finished.value = false
    }

    private fun updateEditing(
        transform: (ExpenseReviewUiState.Editing) -> ExpenseReviewUiState.Editing
    ) {
        val state = _uiState.value
        if (state is ExpenseReviewUiState.Editing) {
            _uiState.value = transform(state)
        }
    }

    private companion object {
        const val FallbackCategoryName = "Other"
    }
}
