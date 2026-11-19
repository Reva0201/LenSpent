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
        // The raw text is written down before anything interprets it: if the fast path below ever
        // fails, what the recognizer read is still on the capture.
        repository.saveExtraction(
            captureId = capture.id,
            status = CaptureStatus.OCR_DONE,
            ocrText = recognized.text,
            modelVersion = ocrModelVersion
        )

        val draft = ReceiptParser.parseLines(recognized)
        // A status, not a transcription: the step trace is reported to a listener and, in debug builds,
        // to logcat, so it says whether the fast path found anything without naming a merchant or an
        // amount.
        onStep(
            ExtractionStep(
                ExtractionStage.FAST_PATH,
                "merchant=${draft.merchant.isNotBlank()} amount=${draft.amountRupiah > 0L}"
            )
        )

        // The fast path's answer is the capture's structured output, which is what moves it from "text
        // only" to "interpreted". ocrText is passed again because this write covers all four columns:
        // omitting it would blank the text persisted a moment ago.
        repository.saveExtraction(
            captureId = capture.id,
            status = CaptureStatus.STRUCTURED,
            ocrText = recognized.text,
            structuredJson = StructuredJson.of(draft),
            modelVersion = ocrModelVersion
        )
        return draft
    }
}
