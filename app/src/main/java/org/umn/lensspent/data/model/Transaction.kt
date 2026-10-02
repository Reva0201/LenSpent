package org.umn.lensspent.data.model

/**
 * A confirmed expense.
 *
 * [amount] is whole Rupiah and is a Long on purpose: IDR has no minor unit and Float loses precision
 * past ~16 million, which is well inside the range of a monthly total.
 */
data class Transaction(
    val id: Long,
    val merchant: String,
    val amount: Long,
    val category: Category,
    /** Epoch millis of the purchase, not of the row being written. */
    val occurredAt: Long,
    val note: String? = null,
    /** The capture this expense came from, when it was created from a photo or screenshot. */
    val captureId: Long? = null
)
