package org.umn.lensspent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.umn.lensspent.data.local.entity.CaptureEntity
import org.umn.lensspent.data.model.CaptureStatus

/** Staging area for images that have not been confirmed into a transaction yet. */
@Dao
interface CaptureDao {

    @Query("SELECT * FROM captures ORDER BY capturedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun findById(id: Long): CaptureEntity?

    @Query("SELECT * FROM captures WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun findBySourceUri(sourceUri: String): CaptureEntity?

    /** Every image URI still referenced by a capture, for the orphan sweep. */
    @Query("SELECT sourceUri FROM captures")
    suspend fun allSourceUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CaptureEntity): Long

    /**
     * Status-only update. Separate from [updateExtraction] on purpose: that one writes all four
     * columns, so using it to confirm a capture would blank out the OCR text and JSON it produced.
     */
    @Query("UPDATE captures SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CaptureStatus)

    @Query(
        "UPDATE captures SET status = :status, ocrText = :ocrText, " +
            "structuredJson = :structuredJson, modelVersion = :modelVersion WHERE id = :id"
    )
    suspend fun updateExtraction(
        id: Long,
        status: CaptureStatus,
        ocrText: String?,
        structuredJson: String?,
        modelVersion: String?
    )

    /**
     * Drops the OCR text and structured JSON but keeps the row.
     *
     * Used when a capture is confirmed: the transaction is the durable record, so the receipt's raw
     * text and the model's JSON have no further purpose and should not sit on disk indefinitely.
     */
    @Query("UPDATE captures SET ocrText = NULL, structuredJson = NULL WHERE id = :id")
    suspend fun clearExtraction(id: Long)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Deletes every capture, so a "remove receipt data" action cannot leave rows pointing at nothing. */
    @Query("DELETE FROM captures")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM captures")
    suspend fun count(): Int
}
