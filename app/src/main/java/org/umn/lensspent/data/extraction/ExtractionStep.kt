package org.umn.lensspent.data.extraction

/**
 * Which part of the pipeline is speaking.
 *
 * The stages are the recognizer, the fast path, the model and the guard - one per decision the
 * pipeline makes - so a step can be read without knowing where in the code it came from.
 */
enum class ExtractionStage(val label: String) {

    /** Reading the image: the only step that needs the camera roll and a recognizer. */
    OCR("OCR"),

    /** The printed totals, read line by line with no model involved. */
    FAST_PATH("Fast path"),

    /** The on-device model, which only ever runs when [FAST_PATH] could not finish. */
    MODEL("Model"),

    /** Checking what the model proposed against the text it was given. */
    GUARD("Guard")
}

/**
 * One thing the pipeline just did, and what it found out by doing it.
 *
 * This is a debug tap, not a contract. The pipeline reports to it and nothing reads it back into a
 * decision: the values in [detail] are for a human reading logcat after a slow extraction, and no
 * stage may behave differently because somebody is listening. That is what lets a receipt that read
 * badly be explained afterwards - or prove that the model was never needed at all.
 *
 * Kept free of Android and of clocks, so the pipeline stays testable on the JVM and every step's
 * timing is the listener's business.
 */
data class ExtractionStep(val stage: ExtractionStage, val detail: String)
