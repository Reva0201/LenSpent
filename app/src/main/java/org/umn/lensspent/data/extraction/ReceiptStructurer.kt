package org.umn.lensspent.data.extraction

import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.ExpenseDraft
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * What a recognizer read off an image: its text, and its lines in reading order.
 *
 * The lines are kept alongside [text] because the fast path reads a receipt line by line - a total
 * label and the figure beneath it, a merchant header above the footers.
 */
internal data class RecognizedText(
    val text: String,
    val lines: List<String>,
    val ocrLines: List<OcrLine> = emptyList()
)

/**
 * The middle of the pipeline: OCR text in, a recorded capture and a draft out.
 */
internal class ReceiptStructurer(
    private val repository: TransactionRepository,
    private val ocrModelVersion: String
) {

    suspend fun structure(
        capture: Capture,
        recognized: RecognizedText,
        onStep: (ExtractionStep) -> Unit = {}
    ): ExpenseDraft {
        repository.saveExtraction(
            captureId = capture.id,
            status = CaptureStatus.OCR_DONE,
            ocrText = recognized.text,
            modelVersion = ocrModelVersion
        )

        val draft = ReceiptParser.parseLines(recognized)
        onStep(
            ExtractionStep(
                ExtractionStage.FAST_PATH,
                "read merchant='${draft.merchant}' amount=${draft.amountRupiah}"
            )
        )
        return draft
    }
}
