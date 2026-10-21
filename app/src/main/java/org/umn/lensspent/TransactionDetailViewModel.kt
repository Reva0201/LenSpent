package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * What the detail screen is showing: a row, a request for one, or the fact that the row is gone.
 *
 * The third state is not defensive padding. The screen is opened with a row id in its route, so a
 * deleted expense - or an id from a restored back stack, or a stale notification - is a real thing to
 * navigate to with nothing behind it. Collapsing that into "loading" is how an app sits on a spinner
 * forever.
 */
sealed interface TransactionDetailUiState {

    data object Loading : TransactionDetailUiState

    data class Found(val transaction: Transaction) : TransactionDetailUiState

    data object Missing : TransactionDetailUiState
}

/**
 * The detail screen's state holder.
 *
 * The row is observed rather than fetched once: saving an edit on another screen, or deleting this row
 * from a second window, repaints this screen without it having to know that either happened.
 *
 * Delete is a two-step flow on purpose - [requestDelete] raises the confirmation, [confirmDelete] writes
 * - because deleting money by a single mis-tap on a back-stack screen is not a recoverable mistake.
 */
class TransactionDetailViewModel(
    private val repository: TransactionRepository,
    transactionId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Loading)
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    private val _confirmingDelete = MutableStateFlow(false)

    /** True while the confirmation dialog is up; owned here so a rotation does not dismiss it. */
    val confirmingDelete: StateFlow<Boolean> = _confirmingDelete.asStateFlow()

    private val _finished = MutableStateFlow(false)

    /** True once the row is deleted, which is the screen's cue to leave. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTransaction(transactionId).collect { transaction ->
                _uiState.value = transaction?.let(TransactionDetailUiState::Found)
                    ?: TransactionDetailUiState.Missing
            }
        }
    }

    fun requestDelete() {
        _confirmingDelete.value = true
    }

    fun cancelDelete() {
        _confirmingDelete.value = false
    }

    fun confirmDelete() {
        val transaction = (_uiState.value as? TransactionDetailUiState.Found)?.transaction ?: return
        _confirmingDelete.value = false
        viewModelScope.launch {
            repository.deleteTransaction(transaction.id)
            _finished.value = true
        }
    }
}
