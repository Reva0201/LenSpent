package org.umn.lensspent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus

/**
 * A photo or screenshot on its way to becoming a [TransactionEntity].
 *
 * The raw OCR text and the structured JSON are kept next to the image on purpose: the pipeline is
 * entirely on-device, so re-extracting an old capture with a newer model is only possible while its
 * intermediate output is still here. [modelVersion] records what produced [structuredJson], which is
 * what makes "re-run everything older than model X" a query instead of a re-scan.
 */
@Entity(
    tableName = "captures",
    indices = [Index(value = ["sourceUri"], unique = true)]
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** content:// URI of the image, as handed back by the camera or the photo picker. */
    val sourceUri: String,
    val source: CaptureSource,
    val capturedAt: Long,
    val status: CaptureStatus = CaptureStatus.CAPTURED,
    val ocrText: String? = null,
    val structuredJson: String? = null,
    val modelVersion: String? = null
)
