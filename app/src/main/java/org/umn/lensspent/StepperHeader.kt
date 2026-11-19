package org.umn.lensspent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.umn.lensspent.ui.theme.Spacing

/**
 * Shared header chrome for the screens that step through time (analytics and expense review).
 *
 * Every header has the same minimum band height, so whatever follows it - the chart, the amount, the
 * form - starts in the same place whichever compartment is on screen. The minimum is a floor rather
 * than a cap: a long date at a doubled font scale adds a second line and pushes the content below it
 * down, instead of being clipped to fit a pixel height.
 */
internal val HeaderRowHeight = 48.dp

private val DatePartFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
private val DayNameFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

/** "18-09-2026 | Friday" - the single shape every stepped date label takes in the app. */
internal fun dayLabel(date: LocalDate): String =
    "${date.format(DatePartFormatter)} | ${date.format(DayNameFormatter)}"

/** "Friday" on its own, for prose that only needs the weekday. */
internal fun dayName(date: LocalDate): String = date.format(DayNameFormatter)

/** "14-09-2026 - 20-09-2026": the same date shape, for a range that has to stay on one line. */
internal fun dateRangeLabel(start: LocalDate, endInclusive: LocalDate): String =
    "${start.format(DatePartFormatter)} - ${endInclusive.format(DatePartFormatter)}"

/** The header band a compartment occupies. */
@Composable
internal fun HeaderSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HeaderRowHeight)
            .padding(vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Arrow stepper: "previous" is always available, "next" only while stepping back towards today.
 *
 * The date may wrap to two lines. That is deliberate: a date is the one thing here that must never
 * be abbreviated, and 48dp is enough for one line at normal scale while still leaving room for a
 * second when the user enlarges text.
 */
@Composable
internal fun StepperHeader(
    label: String,
    nextEnabled: Boolean,
    onStep: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HeaderRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onStep(-1) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous"
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onStep(1) },
            enabled = nextEnabled
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next"
            )
        }
    }
}
