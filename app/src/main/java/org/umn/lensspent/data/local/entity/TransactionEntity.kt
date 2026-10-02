package org.umn.lensspent.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recorded expense.
 *
 * [amount] is whole Rupiah and is stored as a Long: IDR has no minor unit, and Float silently loses
 * precision past ~16 million, which is well inside the range of a monthly total.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId"), Index("occurredAt")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val merchant: String,
    val amount: Long,
    val categoryId: Long,
    /** Epoch millis of the purchase, not of the row being written. */
    val occurredAt: Long,
    val note: String? = null,
    /** Set when the expense was created from a photo or screenshot; null for manual entries. */
    val captureId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
