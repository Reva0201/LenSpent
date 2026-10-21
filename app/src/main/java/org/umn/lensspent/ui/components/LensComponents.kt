package org.umn.lensspent.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.umn.lensspent.categoryIcon
import org.umn.lensspent.data.model.Category

/**
 * The category's own colour, from the hex the taxonomy stores.
 *
 * Categories carry a colour so the charts and the rows can be told apart at a glance. It is the one
 * place raw colour is allowed past the theme, and it is deliberately *data* rather than design: the
 * palette is the taxonomy's, seeded with the app and later editable by the user, not a set of tokens.
 *
 * An unparseable hex falls back to the theme's primary rather than throwing - a bad colour in one row
 * should not take a screen down.
 */
@Composable
fun categoryTint(colorHex: String): Color {
    val fallback = MaterialTheme.colorScheme.primary
    return remember(colorHex) {
        runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrNull()
    } ?: fallback
}

/**
 * A category, as a tinted disc with its icon.
 *
 * Tinting from the category's colour is what gives a list of expenses any visual variety at all: nine
 * identical blue circles would make every row look like the same transaction. The disc is a wash of the
 * colour rather than the colour itself, so the icon stays legible in both light and dark.
 */
@Composable
fun CategoryBadge(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val tint = categoryTint(category.colorHex)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIcon(category.iconKey),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * A section heading with an optional trailing action.
 *
 * Headings are the app's paragraph breaks: every screen with more than one block uses this, so the
 * vertical rhythm above a heading and the weight of the heading itself cannot drift screen to screen.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        action?.invoke()
    }
}
