package org.umn.lensspent.data

/**
 * Parses Rupiah text into whole Rupiah.
 *
 * Handles what a person types into the amount field: "Rp 1.250.000", "1.250.000", "1250000", even
 * "1 250 000" or "1.250.000,-". Indonesian formatting uses "." as the thousands separator and IDR has
 * no minor unit in practice, so separators are simply discarded.
 *
 * Anything else returns null rather than a scraped number. That matters for money: "125rb" would
 * otherwise silently become Rp 125 instead of Rp 125.000, and an OCR line like "total bayar
 * 1.250.000" would be accepted as if the parser had understood it. The caller shows a validation error
 * so the user can say what they meant.
 */
fun parseRupiahAmount(text: String): Long? {
    val withoutCurrency = text.replace("Rp", "", ignoreCase = true)

    val understood = withoutCurrency.all { character ->
        character.isDigit() ||
            character == '.' ||
            character == ',' ||
            character == '-' ||
            character.isWhitespace()
    }
    if (!understood) return null

    val digits = withoutCurrency.filter { it.isDigit() }
    if (digits.isEmpty()) return null
    return digits.toLongOrNull()
}
