package org.umn.lensspent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's shape vocabulary: one radius per role, and no radius written at a call site.
 *
 * The scale is deliberately softer than Material's default 4/8/12/16/28. A receipt is a document, and
 * the app is mostly cards on a page, so the radii step up as the surface grows: a chip is barely
 * rounded, a card is clearly rounded, a sheet is almost a lozenge. Keeping the steps a fixed ratio apart
 * is what makes screens built at different times look like they belong to one app.
 *
 * `extraLarge` is above Material's 28dp because the analytics sheet is the widest surface in the app and
 * a 28dp corner on a full-width sheet reads as an accident rather than a decision.
 */
val LensSpentShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)
