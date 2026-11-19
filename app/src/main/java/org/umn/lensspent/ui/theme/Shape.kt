package org.umn.lensspent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's shape vocabulary: one radius per role, and no radius written at a call site.
 *
 * The scale is deliberately restrained. A receipt is a document, and most of this app is text on a
 * page rather than a tray of floating cards, so the radii stay small: fields are barely rounded,
 * controls are clearly rounded, and only a true surface - a grouped settings block, a dialog - gets a
 * larger corner. Keeping the steps fixed is what makes screens built a week apart look related.
 */
val LensSpentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)
