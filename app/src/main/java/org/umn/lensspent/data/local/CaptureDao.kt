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
}
