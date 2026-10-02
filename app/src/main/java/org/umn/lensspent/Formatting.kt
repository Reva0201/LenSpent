package org.umn.lensspent

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The strings the user reads money and dates in.
 *
 * They live here rather than at the bottom of the dashboard that first needed them: the dashboard, the
 * analytics tabs, the review sheet and the confirmation dialogs all print Rupiah, and a second copy is
 * how two screens end up disagreeing about what "Rp 1.340.000" looks like.
 */

private val IdrLocale = Locale.forLanguageTag("id-ID")

/**
 * NumberFormat is stateful and not thread-safe, so every thread keeps its own instance instead of
 * building a new formatter on each call - charts re-render far more often than they change data.
 */
private val IdrNumberFormat: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
    NumberFormat.getNumberInstance(IdrLocale)
}

/**
 * Formats a whole-Rupiah amount, e.g. 1340000L -> "Rp 1.340.000".
 *
 * Takes a Long rather than a Float: every amount in the ledger is a whole number of Rupiah, and a
 * Float overload invites a silent rounding step (or a precision loss) at each call site.
 */
fun formatIDR(amount: Long): String {
    val formatter = IdrNumberFormat.get() ?: NumberFormat.getNumberInstance(IdrLocale)
    return "Rp ${formatter.format(amount)}"
}

/** "Sep 18, 2026" in the device's zone, the way transaction dates read in the lists. */
fun formatTransactionDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(TransactionDateFormatter)

private val TransactionDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

/**
 * Compact Rupiah shorthand for the chart axis labels, e.g. 1700000f -> "Rp 1,7Jt", 561000f -> "Rp 561K".
 *
 * Callers should memoize the result (see [SpendingBarChart]): String.format allocates a Formatter per
 * call, which is fine for a handful of axis labels but not for every frame.
 */
fun formatCompactIDR(amount: Float): String {
    val (divisor, suffix) = when {
        amount >= 1_000_000f -> 1_000_000f to "Jt"
        amount >= 1_000f -> 1_000f to "K"
        else -> return "Rp ${amount.toInt()}"
    }
    val value = amount / divisor
    val formatted = if (value % 1 == 0f) {
        value.toInt().toString()
    } else {
        // The Indonesian locale renders the decimal separator as a comma (e.g. 1,7Jt)
        String.format(IdrLocale, "%.1f", value)
    }
    return "Rp $formatted$suffix"
}
