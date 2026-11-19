package org.umn.lensspent.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.umn.lensspent.categoryIcon
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.formatIDR
import org.umn.lensspent.ui.theme.CategoryPalette
import org.umn.lensspent.ui.theme.LocalLensDarkTheme
import org.umn.lensspent.ui.theme.Motion
import org.umn.lensspent.ui.theme.Spacing
import org.umn.lensspent.ui.theme.motionTween
import org.umn.lensspent.ui.theme.rememberAnimationsEnabled

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
    durationMillis: Int = Motion.Deliberate
) {
    val animated = remember { Animatable(0f) }
    val animationsEnabled = rememberAnimationsEnabled()

    LaunchedEffect(amount, animationsEnabled) {
        animated.animateTo(
            targetValue = amount.toFloat(),
            animationSpec = motionTween(
                durationMillis = durationMillis,
                enabled = animationsEnabled,
                easing = Motion.Decelerate
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
 * The app's grouped surface.
 *
 * A card here is a hairline and a flat fill, not a shadow. Shadows were doing work the layout should
 * do: several cards in a column all casting the same drop shadow read as competing slabs, and on a
 * dark background a shadow is invisible anyway. One outlined block per subject keeps the page quiet.
 *
 * The corner is the theme's `large` (16dp), and the fill is the page's own surface rather than a
 * raised tint, so a group reads as a bounded region of the page instead of a floating panel.
 */
@Composable
fun LensCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )

    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = border
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = border
        ) {
            Column(content = content)
        }
    }
}

/**
 * A horizontal rule between rows of one ledger.
 *
 * Inset to the text column rather than the container edge, so the rule lines up with the merchant
 * name and the category badge keeps the row's left edge to itself.
 */
@Composable
fun LedgerRule(modifier: Modifier = Modifier, startInset: Dp = 56.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
    )
}

/**
 * The category picker, as wrapping selectable tiles.
 *
 * Chips with a leading icon were the obvious choice and the wrong one: nine chips wrap into three
 * uneven rows of text, and choosing a category becomes scanning words. A tile grid is scanned by
 * shape and colour - the icon and its tint identify the category before the label is read.
 *
 * It wraps rather than scrolling sideways. A horizontal row hid five of nine categories behind a
 * gesture and left a clipped word at the edge, which reads as a layout bug. Nine is a small enough
 * taxonomy to show in full, and showing it in full is what makes the tiles worth their space. Each
 * tile is a real `selectable` control with `Role.RadioButton`, so TalkBack announces the category and
 * whether it is chosen.
 */
@Composable
fun CategoryTilePicker(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Three columns at normal scale on a 360dp phone, two once text is enlarged enough that a
    // three-column tile would squeeze its label. The grid is built from rows rather than a Lazy
    // grid: the taxonomy is nine items, and a nested lazy scroller inside a form is a scroll trap.
    val fontScale = LocalDensity.current.fontScale
    val columns = if (fontScale >= 1.3f) 2 else 3

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        categories.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        selected = category.id == selectedId,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep the last row's tiles the same width as every other row's instead of letting
                // two stretch across the whole form.
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = categoryTint(category.colorHex)
    val animationsEnabled = rememberAnimationsEnabled()

    // Selection is a state change, not a new surface: the fill and the border cross-fade on the
    // quick step so tapping a tile settles instead of blinking between two hard colours.
    val fill by animateColorAsState(
        targetValue = if (selected) {
            tint.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motionTween(Motion.Quick, animationsEnabled),
        label = "category-tile-fill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) tint else Color.Transparent,
        animationSpec = motionTween(Motion.Quick, animationsEnabled),
        label = "category-tile-border"
    )
    val discFill by animateColorAsState(
        targetValue = tint.copy(alpha = if (selected) 0.28f else 0.18f),
        animationSpec = motionTween(Motion.Quick, animationsEnabled),
        label = "category-tile-disc"
    )

    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(fill)
            .border(1.5.dp, borderColor, MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(category.id) }
            )
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(discFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon(category.iconKey),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            textAlign = TextAlign.Center
        )    }
}

/**
 * A segmented switcher: one track, and the selected segment as a pill.
 *
 * The selection is an object rather than an underline: a filled pill sits on the recessed track and
 * travels to the segment you chose, so a tap and a pager swipe look like the same event.
 *
 * **Adaptation.** The control keeps four equal segments while the labels fit. When the user enlarges
 * text past the point where "Categories" fits in a quarter of the width, the track becomes
 * horizontally scrollable instead of truncating labels or shrinking type. Nothing is hidden from
 * TalkBack, and the selected segment is scrolled into view when it changes.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = Spacing.xs
    val inset = Spacing.xs
    val segmentHeight = 48.dp
    // One step below body label size. Four labels share a 360dp phone, and at 15sp "Categories"
    // cannot fit a quarter of the strip without clipping or shrinking the type. 14sp is still
    // comfortably above the platform minimum, and it scales with the user's font setting.
    val textStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = 13.sp,
        letterSpacing = 0.sp
    )
    val horizontalTextPadding = 2.dp
    val animationsEnabled = rememberAnimationsEnabled()

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidths = remember(options, textStyle, measurer, density) {
        options.map { label ->
            with(density) {
                measurer.measure(text = label, style = textStyle).size.width.toDp()
            } + horizontalTextPadding * 2
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(modifier = Modifier.padding(inset)) {
            val equalWidth = (maxWidth - gap * (options.size - 1)) / options.size
            // Scroll only when a label genuinely cannot fit its share of the track. Checking the
            // widest label against the segment width is the honest test; summing all labels and
            // comparing with the whole strip overestimated, because the gaps are counted twice.
            val scrolls = labelWidths.any { it > equalWidth }
            val scrollState = rememberScrollState()

            // Keep the current selection visible when the track scrolls. It is a no-op in the
            // equal-width case, where the whole control fits without scrolling.
            LaunchedEffect(selectedIndex, scrolls) {
                if (scrolls && selectedIndex in options.indices) {
                    val target = labelWidths.take(selectedIndex).fold(0.dp) { total, width ->
                        total + width
                    } + gap * selectedIndex
                    scrollState.animateScrollTo(with(density) { target.roundToPx() })
                }
            }
            val pillOffset by animateDpAsState(
                targetValue = if (scrolls) {
                    labelWidths.take(selectedIndex).fold(0.dp) { total, width -> total + width } +
                        gap * selectedIndex
                } else {
                    (equalWidth + gap) * selectedIndex
                },
                // One curve for the pill and the pager it drives: the two are the same gesture, so
                // they have to arrive together rather than one snapping ahead of the other.
                animationSpec = motionTween(Motion.Standard, animationsEnabled, Motion.StandardEasing),
                label = "segment-pill-offset"
            )

            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(if (scrolls) labelWidths.getOrElse(selectedIndex) { 0.dp } else equalWidth)
                    .height(segmentHeight)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            Row(
                modifier = Modifier
                    .then(if (scrolls) Modifier.horizontalScroll(scrollState) else Modifier)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = motionTween(Motion.Quick, animationsEnabled),
                        label = "segment-content"
                    )

                    Box(
                        modifier = Modifier
                            .width(
                                if (scrolls) {
                                    labelWidths.getOrElse(index) { 0.dp }
                                } else {
                                    equalWidth
                                }
                            )
                            .heightIn(min = segmentHeight)
                            .clip(MaterialTheme.shapes.medium)
                            // Tabs use a deliberate selected pill, not the default gray pressed
                            // indication. The gray flash lingered over the previous tab after a
                            // pager change and made two tabs look selected.
                            .semantics {
                                this.selected = selected
                                this.role = Role.Tab
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = textStyle,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = horizontalTextPadding)
                        )
                    }
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
    val animationsEnabled = rememberAnimationsEnabled()

    LaunchedEffect(share, animationsEnabled) {
        animatedShare.animateTo(
            targetValue = share.coerceIn(0f, 1f),
            // No stagger delay. Bars below the first were visibly arriving late, which made a
            // ranked list look like it was still loading. They still grow; they just grow together.
            animationSpec = motionTween(
                durationMillis = Motion.Emphasized,
                enabled = animationsEnabled,
                easing = Motion.Decelerate
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
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
 * The category's own colour, from the hex the taxonomy stores, adjusted for the scheme on screen.
 *
 * Categories carry a colour so the charts and the rows can be told apart at a glance. It is the one
 * place raw colour is allowed past the theme, and it is deliberately *data* rather than design: the
 * palette is the taxonomy's, seeded from [org.umn.lensspent.ui.theme.CategoryPalette] and later editable
 * by the user, not a set of tokens.
 *
 * The scheme comes from [LocalLensDarkTheme], not the system setting: a user who forces Light in an
 * otherwise dark phone must still get the tints that belong to the light page in front of them.
 */
@Composable
fun categoryTint(colorHex: String): Color {
    val dark = LocalLensDarkTheme.current
    val fallback = MaterialTheme.colorScheme.primary
    val parsed = remember(colorHex) {
        runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrNull()
    } ?: fallback
    return if (dark) CategoryPalette.forScheme(parsed, dark = true) else parsed
}

/**
 * A category, as a tinted disc with its icon.
 *
 * Tinting from the category's colour is what gives a list of expenses any visual variety at all: nine
 * identical discs would make every row look like the same transaction. The disc is a wash of the
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
            .background(tint.copy(alpha = 0.16f)),
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

/**
 * A full-row tap target for a ledger entry.
 *
 * Kept as a vertical stack rather than a fixed-height row: a long merchant name, a large amount or a
 * doubled font scale must be allowed to add a line instead of being clipped. The minimum height is the
 * platform's 48dp floor, and the row merges its text into one TalkBack announcement with an explicit
 * action label.
 */
@Composable
fun LedgerRow(
    title: String,
    subtitle: String,
    amount: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String = "Open expense"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        leading()

        // Two lines, one measure: the amount shares the first line with the merchant and the
        // category/date line then gets the row's full width. The earlier single text column with a
        // fixed amount beside it made "Groceries · Nov 12, 2026" wrap on a 360dp phone for no reason.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 2
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
