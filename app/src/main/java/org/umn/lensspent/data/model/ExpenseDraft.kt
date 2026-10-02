package org.umn.lensspent.data.model

/**
 * What extraction produces and the confirmation sheet edits.
 *
 * Kept separate from [Transaction] because a draft has no id yet, may be half-filled, and may come
 * from a model that was wrong - the user is the one who makes it true.
 */
data class ExpenseDraft(
    val merchant: String = "",
    /** Whole Rupiah. Zero means "nothing detected yet", never a real expense. */
    val amountRupiah: Long = 0L,
    val categoryId: Long? = null,
    /** Epoch millis of the purchase; defaults to now, which is right for a receipt being scanned. */
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String? = null
)
