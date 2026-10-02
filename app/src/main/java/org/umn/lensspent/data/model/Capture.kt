package org.umn.lensspent.data.model

/** Where a capture came from: the in-app camera or the system photo picker. */
enum class CaptureSource { CAMERA, PICKER }

/** How far the on-device pipeline got with a capture. */
enum class CaptureStatus { CAPTURED, OCR_DONE, STRUCTURED, CONFIRMED, FAILED }

/**
 * An image on its way to becoming a [Transaction].
 *
 * [ocrText] and [structuredJson] are the pipeline's intermediate output. Because every stage runs
 * on-device, this is the only way to re-extract an old capture with a newer model: keep the raw text,
 * record which [modelVersion] produced the JSON, and "re-run everything older than X" becomes a query
 * rather than asking the user to photograph their receipt again.
 */
data class Capture(
    val id: Long,
    val sourceUri: String,
    val source: CaptureSource,
    val capturedAt: Long,
    val status: CaptureStatus,
    val ocrText: String? = null,
    val structuredJson: String? = null,
    val modelVersion: String? = null
)
