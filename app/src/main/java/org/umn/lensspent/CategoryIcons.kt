package org.umn.lensspent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a category's stable `iconKey` onto a Material icon.
 *
 * Keys are matched, never display names, so renaming or localising a category cannot change its icon.
 * Unknown keys (a user-defined category, or one added by a future model) fall back to a receipt.
 */
fun categoryIcon(iconKey: String): ImageVector = when (iconKey) {
    "groceries" -> Icons.Default.ShoppingBag
    "dining" -> Icons.Default.Restaurant
    "transport" -> Icons.AutoMirrored.Filled.TrendingUp
    "shopping" -> Icons.Default.ShoppingCart
    "bills" -> Icons.AutoMirrored.Filled.ReceiptLong
    "health" -> Icons.Default.LocalHospital
    "entertainment" -> Icons.Default.Movie
    "education" -> Icons.Default.School
    else -> Icons.Default.Receipt
}
