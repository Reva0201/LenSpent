package org.umn.lensspent.data.extraction

import org.umn.lensspent.data.model.ExpenseDraft

/**
 * The pipeline's stored output: what a capture's `structuredJson` column holds.
 */
internal object StructuredJson {

    fun of(draft: ExpenseDraft): String =
        """{"merchant":"${escape(draft.merchant)}","total_amount":${draft.amountRupiah}}"""

    /**
     * Enough JSON escaping to keep the column parseable: the two characters that would end or break
     * the string, and the control characters a recognizer's text can still contain.
     */
    private fun escape(text: String): String = buildString(text.length) {
        text.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character == '\n' -> append("\\n")
                character == '\r' -> append("\\r")
                character == '\t' -> append("\\t")
                character < ' ' -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
    }
}
