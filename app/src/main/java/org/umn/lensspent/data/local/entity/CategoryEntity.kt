package org.umn.lensspent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A spending category.
 *
 * The taxonomy is seeded once from [org.umn.lensspent.data.local.DefaultCategories] and is the single
 * source of truth shared by the confirmation sheet, the charts and (later) the prompt handed to the
 * on-device model, so a detected category always maps onto a row that exists.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Stable key the UI maps to an icon: display names may be renamed or localised, this must not. */
    val iconKey: String,
    /** ARGB hex used by category chips and charts. */
    val colorHex: String,
    val sortOrder: Int,
    val isUserDefined: Boolean = false
)
