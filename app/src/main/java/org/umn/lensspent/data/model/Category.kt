package org.umn.lensspent.data.model

/** A spending category as the UI sees it. */
data class Category(
    val id: Long,
    val name: String,
    /** Stable key the UI maps to an icon (see [org.umn.lensspent.categoryIcon]). */
    val iconKey: String,
    val colorHex: String
)
