package org.umn.lensspent.data.local

import org.umn.lensspent.data.local.entity.CategoryEntity
import org.umn.lensspent.ui.theme.CategoryPalette

/**
 * The starting taxonomy, seeded once on first launch.
 *
 * This list is the single source of truth for categories: the confirmation sheet offers exactly these
 * (plus whatever the user adds), the charts colour by [CategoryEntity.colorHex], and the on-device
 * model is later asked to choose from these names only.
 *
 * The colours come from `ui/theme/CategoryPalette` rather than hex literals here, so the taxonomy and
 * the theme cannot drift apart: a category colour is a design decision, and design decisions live in
 * one place.
 */
val DefaultCategories: List<CategoryEntity> = listOf(
    CategoryEntity(
        name = "Groceries",
        iconKey = "groceries",
        colorHex = CategoryPalette.Groceries.toHex(),
        sortOrder = 0
    ),
    CategoryEntity(
        name = "Dining",
        iconKey = "dining",
        colorHex = CategoryPalette.Dining.toHex(),
        sortOrder = 1
    ),
    CategoryEntity(
        name = "Transport",
        iconKey = "transport",
        colorHex = CategoryPalette.Transport.toHex(),
        sortOrder = 2
    ),
    CategoryEntity(
        name = "Shopping",
        iconKey = "shopping",
        colorHex = CategoryPalette.Shopping.toHex(),
        sortOrder = 3
    ),
    CategoryEntity(
        name = "Bills",
        iconKey = "bills",
        colorHex = CategoryPalette.Bills.toHex(),
        sortOrder = 4
    ),
    CategoryEntity(
        name = "Health",
        iconKey = "health",
        colorHex = CategoryPalette.Health.toHex(),
        sortOrder = 5
    ),
    CategoryEntity(
        name = "Entertainment",
        iconKey = "entertainment",
        colorHex = CategoryPalette.Entertainment.toHex(),
        sortOrder = 6
    ),
    CategoryEntity(
        name = "Education",
        iconKey = "education",
        colorHex = CategoryPalette.Education.toHex(),
        sortOrder = 7
    ),
    CategoryEntity(
        name = "Other",
        iconKey = "other",
        colorHex = CategoryPalette.Other.toHex(),
        sortOrder = 8
    )
)

/**
 * `#RRGGBB` in upper case, which is the shape `AndroidColor.parseColor` and the CSV export both expect.
 *
 * Written as one function rather than nine string literals because the palette is authored as `Color`
 * values (so it can be previewed and compared in one file) while storage wants text.
 */
internal fun androidx.compose.ui.graphics.Color.toHex(): String =
    String.format("#%02X%02X%02X", (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
