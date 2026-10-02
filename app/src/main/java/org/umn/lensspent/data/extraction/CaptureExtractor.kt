package org.umn.lensspent.data.extraction

import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.ExpenseDraft

/**
 * Turns a capture into an [ExpenseDraft].
 *
 * This is the seam the on-device pipeline plugs into: ML Kit OCR plus a fast-path structurer today
 * ([MlKitCaptureExtractor]), and the quantized local LLM fallback later. The confirmation screen
 * talks to this one interface, so Stage 2 can land without the UI changing at all.
 */
interface CaptureExtractor {

    /**
     * Turns [capture] into a draft, reporting each stage it passes through to [onStep].
     *
     * [onStep] is a debug tap and nothing more: it is called on whichever thread a stage happens to
     * run on, nothing above it may decide anything from what it hears, and a caller that passes no
     * tap gets exactly the extraction it would have got before. See [ExtractionStep].
     */
    suspend fun extract(
        capture: Capture,
        onStep: (ExtractionStep) -> Unit = {}
    ): ExpenseDraft
}
