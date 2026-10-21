package org.umn.lensspent.ui.components

import android.graphics.Color as AndroidColor
import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.umn.lensspent.categoryIcon
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.formatIDR
import org.umn.lensspent.ui.theme.Spacing

/**
 * A Rupiah amount that counts up to its value.
 *
 * Two movements in one: the first frame after a screen opens counts up from zero, which gives the eye
 * something to follow and makes the number feel like it arrived rather than appeared; and a later change
 * - saving an expense, setting a budget - animates from the old value to the new one, so the difference
 * is visible instead of being a number that silently became another number.
 *
 * The animation is skipped outright when the system's animator scale is zero. That setting is an
 * accessibility choice ("remove animations"), not a preference, and a counting number is exactly the
 * kind of motion it exists to stop.
 */
@Composable
fun CountUpAmount(
    amount: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMillis: Int = 700
) {
    val animated = remember { Animatable(0f) }
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }

    LaunchedEffect(amount) {
        animated.animateTo(
            targetValue = amount.toFloat(),
            animationSpec = tween(
                durationMillis = if (animationsEnabled) durationMillis else 0,
                easing = FastOutSlowInEasing
            )
        )
    }

    Text(
        text = formatIDR(animated.value.toLong()),
        style = style,
        color = color,
        modifier = modifier
    )
}

/**
 * The app's card.
 *
 * A card here is a hairline and a surface colour, not a shadow. Shadows were doing the work that the
 * layout should do: five cards in a column all casting the same drop shadow reads as five competing
 * slabs, and on the near-black dark background a shadow is invisible anyway. One outlined surface per
 * block keeps the page quiet, and the only things that float are the two action bars at the bottom of
 * the screen.
 */
@Composable
fun LensCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )

    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            // One soft ambient layer under the hairline. The border defines the edge; the shadow only
            // suggests that the card is above the paper, which is the difference between "a rectangle
            // drawn on the page" and "a surface resting on it".
            shadowElevation = 2.dp
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            shadowElevation = 2.dp
        ) {
            Column(content = content)
        }
    }
}

/**
 * The category picker, as a row of tiles.
 *
 * Chips with a leading icon were the obvious choice and the wrong one: nine chips wrap into three
 * uneven rows of text, and choosing a category becomes scanning words. A tile row is scanned by shape
 * and colour - the icon and its tint identify the category before the label is read - and it scrolls
 * sideways, so adding categories later never reflows the form.
 *
 * Selection is a tinted fill plus a border in the category's own colour, which is the same colour the
 * category has everywhere else in the app.
 */
@Composable
fun CategoryTilePicker(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            val tint = categoryTint(category.colorHex)

            Column(
                modifier = Modifier
                    .width(78.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) {
                            tint.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .then(
                        if (selected) {
                            Modifier.border(1.5.dp, tint, MaterialTheme.shapes.medium)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(category.id) }
                    .padding(vertical = Spacing.md, horizontal = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = if (selected) 0.28f else 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(category.iconKey),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * A segmented switcher: one track, and the selected segment as a raised pill inside it.
 *
 * The Material tab row underneath this screen was a line of text with an underscore that moved - a
 * control that says "these are tabs" without saying anything about which one is chosen beyond 2dp of
 * colour. A pill on a recessed track shows the selection as an object, and the sliding background
 * animates so a swipe of the pager and a tap on the segment look like the same event.
 *
 * Four options is the practical maximum for this shape on a phone: past that, the labels stop fitting
 * on one line and the control becomes a scrollable list of buttons.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                // The selected segment is filled with the brand container rather than white-on-white:
                // a white pill inside a white track is only legible through its shadow, which is
                // exactly what made the first version of this control read as grey smudge.
                val container by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "segment-container"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "segment-content"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(container)
                        .clickable { onSelect(index) }
                        .padding(vertical = Spacing.sm + Spacing.xxs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * A category's share of a total, as a labelled bar.
 *
 * Ranking answers "where did my money go" faster than any chart: the eye reads the order and the
 * lengths, and the amount is right there when it wants the number.
 */
@Composable
fun CategoryShareRow(
    category: Category,
    amount: Long,
    share: Float,
    formattedAmount: String,
    modifier: Modifier = Modifier
) {
    val tint = categoryTint(category.colorHex)
    val animatedShare = remember { Animatable(0f) }

    // Grows from nothing on first show, so the ranking is read as lengths changing rather than as a
    // finished bar chart that was always there.
    LaunchedEffect(share) {
        animatedShare.animateTo(
            targetValue = share.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 600, delayMillis = 80, easing = FastOutSlowInEasing)
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category = category, size = 36.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedShare.value)
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(tint)
                )
            }

            Text(
                text = "${(share * 100).toInt()}% of this month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
