package org.umn.lensspent.data.extraction

import org.umn.lensspent.data.model.ExpenseDraft
import org.umn.lensspent.data.parseRupiahAmount
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Stage 1's structurer: turns ML Kit's spatially grouped OCR output into an [ExpenseDraft] with pure
 * Kotlin, no model and no network.
 *
 * Implements Spatial Layout Heuristics and Expanded Dictionaries for English and Indonesian receipts.
 */
object ReceiptParser {

    private val AmountToken = Regex("""\d{1,3}(?:[.,]\d{3})+|\d{3,}""")

    private val PoisonWords = listOf(
        "VOUCHER", "DISKON", "DISCOUNT", "CANCEL", "KEMBALI",
        "CHANGE", "CASH", "TUNAI", "NON TUNAI", "HARGA JUAL",
        "PPN", "TAX", "ADMIN"
    )

    private val MerchantExclusions = listOf(
        "NPWP", "Kasir", "Cashier", "Tanggal", "Date",
        "No. Order", "Jl.", "Jalan", "Telp"
    )

    private val TerminalIdRegex = Regex(".*:[A-Z0-9]{2,4}\\s*$", RegexOption.IGNORE_CASE)
    private val DateLine = Regex("""^\d{1,4}[/.\-]\d{1,2}[/.\-]\d{1,4}""")
    private val TimeLine = Regex("""^\d{1,2}:\d{2}(:\d{2})?""")

    /**
     * [clock] supplies "today" for the one-year sanity window and the zone the receipt's own date is
     * resolved in, so a test can freeze a receipt in time and get the same draft on every run.
     */
    internal fun parseLines(lines: List<String>, clock: Clock = Clock.systemDefaultZone()): ExpenseDraft {
        val ocrLines = lines.mapIndexed { index, text ->
            OcrLine(text = text, left = 0, top = index * 50, bottom = index * 50 + 40, right = 500)
        }
        return parseOcrLines(ocrLines, clock)
    }

    internal fun parseLines(recognized: RecognizedText, clock: Clock = Clock.systemDefaultZone()): ExpenseDraft {
        val ocrLines = recognized.ocrLines.ifEmpty {
            recognized.lines.mapIndexed { index, text ->
                OcrLine(text = text, left = 0, top = index * 50, bottom = index * 50 + 40, right = 500)
            }
        }
        return parseOcrLines(ocrLines, clock)
    }

    private fun parseOcrLines(ocrLines: List<OcrLine>, clock: Clock): ExpenseDraft {
        val processedRows = processedRowsOf(ocrLines)
        if (processedRows.isEmpty()) {
            return ExpenseDraft()
        }

        // 1. Merchant Extraction
        // A slip prints the store's name first, above its own date, till number, address and items, so
        // the whole receipt is scanned and the topmost row that can be a name - one with letters in it,
        // and none of the furniture a till adds - is the merchant. Ranking the candidates by text size
        // instead reads an item: [processedRowsOf] welds an item name to the price printed beside it, and
        // the welded row measures taller than the header above it. Reading order is what the paper
        // actually printed; the geometry is an artefact of how the photograph was taken.
        val merchantCandidates = processedRows.filter { row ->
            row.text.any { it.isLetter() } &&
            !DateLine.containsMatchIn(row.text) &&
            !TimeLine.containsMatchIn(row.text) &&
            MerchantExclusions.none { exclusion -> row.text.contains(exclusion, ignoreCase = true) } &&
            !TerminalIdRegex.containsMatchIn(row.text)
        }
        val merchant = merchantCandidates.firstOrNull()?.text?.trim() ?: ""

        // 2. Bottom-Up Total Extraction
        val explicitKeywords = listOf("TOTAL BELANJA", "TOTAL BAYAR", "GRAND TOTAL", "AMOUNT DUE", "JUMLAH", "TOTAL HARGA")
        
        var totalAmount = 0L
        val explicitCandidates = mutableListOf<Pair<ProcessedRow, Long>>()
        val nakedCandidates = mutableListOf<Pair<ProcessedRow, Long>>()

        processedRows.forEachIndexed { index, row ->
            val rowTextUpper = row.text.uppercase()
            
            // Exclusions (Poison Words)
            if (PoisonWords.any { poison -> rowTextUpper.contains(poison) }) {
                return@forEachIndexed
            }

            val nextRow = processedRows.getOrNull(index + 1)
            val amt = extractValidAmount(row.text, nextRow?.text)

            if (amt != null) {
                if (explicitKeywords.any { keyword -> rowTextUpper.contains(keyword) }) {
                    explicitCandidates.add(row to amt)
                } else if (rowTextUpper.contains("TOTAL")) {
                    nakedCandidates.add(row to amt)
                }
            }
        }

        if (explicitCandidates.isNotEmpty()) {
            totalAmount = explicitCandidates.maxByOrNull { it.first.bottom }?.second ?: 0L
        } else if (nakedCandidates.isNotEmpty()) {
            totalAmount = nakedCandidates.maxByOrNull { it.first.bottom }?.second ?: 0L
        }

        // 3. Date Sanity Check
        val rowTexts = processedRows.map { it.text }
        // The receipt's own date when it has a usable one, otherwise "now" from the same clock that
        // judged it, so a frozen clock freezes the whole draft.
        val occurredAt = extractAndValidateDate(rowTexts, clock) ?: clock.millis()

        return ExpenseDraft(
            merchant = merchant,
            amountRupiah = totalAmount,
            categoryId = null,
            occurredAt = occurredAt
        )
    }

    private fun extractValidAmount(currentLine: String, nextLine: String?): Long? {
        val amounts = getValidAmounts(currentLine)
        if (amounts.isNotEmpty()) {
            return amounts.last()
        }
        if (nextLine != null) {
            val nextAmounts = getValidAmounts(nextLine)
            if (nextAmounts.isNotEmpty()) {
                return nextAmounts.last()
            }
        }
        return null
    }

    private fun getValidAmounts(line: String): List<Long> {
        return AmountToken.findAll(line).mapNotNull { match ->
            val start = match.range.first
            val end = match.range.last

            var isNegativeOrParenthesized = false
            
            // Look backward for minus or opening parenthesis
            var p = start - 1
            while (p >= 0 && line[p].isWhitespace()) {
                p--
            }
            if (p >= 0 && (line[p] == '-' || line[p] == '(')) {
                isNegativeOrParenthesized = true
            }

            // Look forward for closing parenthesis
            var n = end + 1
            while (n < line.length && line[n].isWhitespace()) {
                n++
            }
            if (n < line.length && line[n] == ')') {
                if (p >= 0 && line[p] == '(') {
                    isNegativeOrParenthesized = true
                }
            }

            if (isNegativeOrParenthesized) null
            else {
                parseRupiahAmount(match.value)?.takeIf { it > 0L }
            }
        }.toList()
    }

    private fun extractAndValidateDate(lines: List<String>, clock: Clock): Long? {
        val regex1 = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
        val regex2 = Regex("""\b(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})\b""")
        val regex3 = Regex("""\b(\d{1,2})[\s/.\-]([A-Za-z]{3})[\s/.\-](\d{2,4})\b""")

        val now = LocalDate.now(clock)
        val zone = clock.zone
        val oneYearAgo = now.minusYears(1)

        for (line in lines) {
            regex1.find(line)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val yearStr = match.groupValues[3]
                val year = if (yearStr.length == 2) 2000 + yearStr.toInt() else yearStr.toInt()
                try {
                    val date = LocalDate.of(year, month, day)
                    if (!date.isAfter(now) && !date.isBefore(oneYearAgo)) {
                        return date.atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                } catch (e: Exception) {}
            }

            regex2.find(line)?.let { match ->
                val year = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val day = match.groupValues[3].toInt()
                try {
                    val date = LocalDate.of(year, month, day)
                    if (!date.isAfter(now) && !date.isBefore(oneYearAgo)) {
                        return date.atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                } catch (e: Exception) {}
            }

            regex3.find(line)?.let { match ->
                val day = match.groupValues[1].toInt()
                val monthStr = match.groupValues[2]
                val yearStr = match.groupValues[3]
                val year = if (yearStr.length == 2) 2000 + yearStr.toInt() else yearStr.toInt()
                try {
                    val formatter = DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("MMM")
                        .toFormatter(Locale.ENGLISH)
                    
                    val month = try {
                        formatter.parse(monthStr).get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
                    } catch (e: Exception) {
                        when (monthStr.uppercase()) {
                            "JAN" -> 1
                            "FEB" -> 2
                            "MAR" -> 3
                            "APR" -> 4
                            "MEI" -> 5
                            "JUN" -> 6
                            "JUL" -> 7
                            "AGU", "AGS" -> 8
                            "SEP" -> 9
                            "OKT" -> 10
                            "NOV" -> 11
                            "DES" -> 12
                            else -> null
                        }
                    }
                    
                    if (month != null) {
                        val date = LocalDate.of(year, month, day)
                        if (!date.isAfter(now) && !date.isBefore(oneYearAgo)) {
                            return date.atStartOfDay(zone).toInstant().toEpochMilli()
                        }
                    }
                } catch (e: Exception) {}
            }
        }
        return null
    }
}
