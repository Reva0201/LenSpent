package org.umn.lensspent.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing, elevation and fixed component sizes, named once.
 *
 * Before this file each screen carried a private `Dimens` object holding the same handful of numbers
 * under different names, which is how a 16dp gutter became a 20dp gutter on two screens and a 32dp
 * footer spacer became 24dp on a third. The steps below are a 4dp grid with two intentional gaps - 24
 * and 32 - because those are the two most common card and section paddings in this app, and forcing
 * them onto an 8dp grid with a multiplier expression reads worse than naming them.
 *
 * Only spacing belongs here. A size that has a Material token already (touch targets, icon sizes) is
 * listed anyway when a screen needs it in a `Modifier`, so the screens stop disagreeing about whether a
 * small icon is 16 or 18dp.
 */
object Spacing {
    /** Hairline separation, e.g. between a label and the value it names. */
    val xxs = 2.dp

    /** Inside a chip, or between an icon and its one-word label. */
    val xs = 4.dp

    /** Between tightly related lines in the same block. */
    val sm = 8.dp

    /** Between rows of a list where the rows are not cards. */
    val md = 12.dp

    /** The default gap inside a card. */
    val lg = 16.dp

    /** The default screen gutter. */
    val xl = 20.dp

    /** Between two cards, or between a card and the section that follows it. */
    val xxl = 24.dp

    /** Between major sections, and the bottom scroll spacer. */
    val xxxl = 32.dp

    /** Around a hero element: a big total, a chart, an empty state. */
    val huge = 48.dp
}

/**
 * Elevation is used sparingly: the app is drawn with surface colours rather than shadows, because a
 * shadow on a near-black background in dark mode does nothing but band. These four steps cover the
 * cases where a surface genuinely floats - a card, the nav bar, the FAB, a dialog.
 */
object Elevation {
    val flat = 0.dp
    val raised = 1.dp
    val card = 2.dp
    val floating = 6.dp
    val modal = 12.dp
}

/**
 * Fixed dimensions that are not spacing: the sizes a touch target, an icon or the FAB is allowed to be.
 */
object Sizes {
    /** Comfortably above the 48dp minimum touch target, used for anything tappable that is not a row. */
    val touchTarget = 48.dp

    val iconSmall = 18.dp
    val icon = 24.dp
    val iconLarge = 32.dp

    /** The floating action button: larger than Material's 56dp so it reads as the app's one main action. */
    val fab = 64.dp

    /**
     * Bottom padding that lets the last list row clear a docked FAB at [fab], including the part of it
     * that hangs below the navigation bar.
     */
    val fabClearance = 104.dp
}
