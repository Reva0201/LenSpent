package org.umn.lensspent.ui

import org.umn.lensspent.data.local.NoBudgetIdr
import org.umn.lensspent.data.parseRupiahAmount

/**
 * The pieces both places a budget can be typed share: onboarding and the settings sheet.
 *
 * They are one file because they are one rule. Two screens asking for the same number and disagreeing
 * about what "1.250.000" means, or about whether an empty field is allowed, is the kind of drift a
 * user only discovers after their ceiling has already been stored wrong.
 */

/**
 * The budget field's text as whole Rupiah, or null when it is not a number.
 *
 * Blank is [NoBudgetIdr] rather than null: the field is optional, so "I would rather not say" is a real
 * answer, and it has to be told apart from "I typed something the app cannot read" - the one case a
 * save waits on. Separators are [parseRupiahAmount]'s business, not this function's: "1.250.000" and
 * "1250000" are the same ceiling, read the same way the ledger's own amount field reads them.
 */
internal fun parseBudgetInput(text: String): Long? = when {
    text.isBlank() -> NoBudgetIdr
    else -> parseRupiahAmount(text)
}

/**
 * What the line under the budget field says: what the typed number means, or what is wrong with it.
 *
 * The typed amount is echoed back formatted because the field itself keeps the raw digits - "Rp
 * 3.750.000 a month" under "3750000" is how the user checks the ceiling they are about to commit to,
 * without the field reformatting itself under their cursor.
 *
 * [parsed] is passed in rather than read from [text] again so that the line can never describe a number
 * the caller is not about to store.
 */
internal fun budgetSupportingText(text: String, parsed: Long?): String = when {
    parsed == null -> "Numbers only, for example 3750000."
    text.isBlank() -> "What you plan to spend this month. Blank means no ceiling."
    parsed == NoBudgetIdr -> "Zero means no ceiling: spending is tracked without a target."
    else -> "${org.umn.lensspent.formatIDR(parsed)} a month."
}
