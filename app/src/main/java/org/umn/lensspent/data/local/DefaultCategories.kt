package org.umn.lensspent.data.local

import org.umn.lensspent.data.local.entity.CategoryEntity

/**
 * The starting taxonomy, seeded once on first launch.
 *
 * This list is the single source of truth for categories: the confirmation sheet offers exactly these
 * (plus whatever the user adds), the charts colour by [CategoryEntity.colorHex], and the on-device
 * model is later asked to choose from these names only.
 */
val DefaultCategories: List<CategoryEntity> = listOf(
    CategoryEntity(name = "Groceries", iconKey = "groceries", colorHex = "#4CAF50", sortOrder = 0),
    CategoryEntity(name = "Dining", iconKey = "dining", colorHex = "#FF7043", sortOrder = 1),
    CategoryEntity(name = "Transport", iconKey = "transport", colorHex = "#42A5F5", sortOrder = 2),
    CategoryEntity(name = "Shopping", iconKey = "shopping", colorHex = "#AB47BC", sortOrder = 3),
    CategoryEntity(name = "Bills", iconKey = "bills", colorHex = "#FFA726", sortOrder = 4),
    CategoryEntity(name = "Health", iconKey = "health", colorHex = "#EF5350", sortOrder = 5),
    CategoryEntity(name = "Entertainment", iconKey = "entertainment", colorHex = "#26C6DA", sortOrder = 6),
    CategoryEntity(name = "Education", iconKey = "education", colorHex = "#7E57C2", sortOrder = 7),
    CategoryEntity(name = "Other", iconKey = "other", colorHex = "#78909C", sortOrder = 8)
)
