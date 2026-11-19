package org.umn.lensspent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.umn.lensspent.ui.theme.Motion
import org.umn.lensspent.ui.theme.motionTween
import org.umn.lensspent.ui.theme.rememberAnimationsEnabled

/**
 * Geometry tokens for the spending chart. Everything else (typography, colours, the tooltip pill
 * shape) comes straight from Material 3 instead of being re-declared here.
 */
private object ChartDimens {
    val Height = 160.dp
    val VerticalPadding = 8.dp
    val BarWidth = 24.dp
    val BarCornerRadius = 8.dp
    val TooltipHeight = 28.dp
    val TooltipHorizontalPadding = 4.dp
    val GridLineThickness = 1.dp
    val AxisLabelGap = 8.dp
}

/** Peak, 66 %, 33 % and the Rp 0 baseline. */
private const val GridLevelCount = 4

/**
 * IDR grid-line bar chart shared by the dashboard and the analytics screen.
 *
 * The grid lines, their axis labels and the bar fills are painted in a single [Canvas] pass, and the
 * per-bar animated values are only read from the draw scope: animating heights therefore invalidates
 * drawing alone and never triggers composition or layout. A transparent interaction layer on top of
 * the canvas provides the ripple, the tap targets and the floating Rupiah tooltip.
 *
 * @param data bar values, in Rupiah
 * @param labels one X-axis label per bar
 * @param barAreaHeight how tall the plot area is. The dashboard wants a compact 160dp strip; the
 *   analytics compartments are taller and would otherwise leave a dead band under the chart.
 */
@Composable
fun SpendingBarChart(
    data: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barAreaHeight: Dp = ChartDimens.Height
) {
    var selectedBarIndex by remember(data) { mutableStateOf<Int?>(null) }
    val animationsEnabled = rememberAnimationsEnabled()

    val maxVal = remember(data) { data.maxOrNull() ?: 1f }

    // Currency formatting is the only allocation-heavy work here, so it happens once per data change
    val axisLabels = remember(maxVal) {
        listOf(
            formatCompactIDR(maxVal),
            formatCompactIDR(maxVal * 0.66f),
            formatCompactIDR(maxVal * 0.33f),
            formatCompactIDR(0f)
        )
    }

    // One animated fraction per bar, read later from the draw phase and by the visible tooltip only.
    // A period with nothing in it has no peak to scale against - every bucket is zero - so the
    // fraction is pinned to zero rather than dividing by it: 0f/0f would be NaN, which no
    // animation can settle on and which would place the tooltip at an undefined offset.
    //
    // It used to be a spring with dampingRatio 0.6, which overshot every bar before settling. On a
    // chart that represents money, an overshoot momentarily shows a total that was never spent, so
    // the scale now eases out without ever passing its target.
    val barFractions = data.map { amount ->
        animateFloatAsState(
            targetValue = if (maxVal > 0f) amount / maxVal else 0f,
            animationSpec = motionTween(Motion.Emphasized, animationsEnabled, Motion.Decelerate),
            label = "BarFraction"
        )
    }

    // Twelve months need to share the plot at 360dp. A compact single-line axis is more useful than
    // letting Compose wrap "May", "Sep" and "Dec" into clipped two-line fragments.
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val textMeasurer = rememberTextMeasurer(cacheSize = axisLabels.size)
    val axisLabelLayouts = remember(axisLabels, axisLabelStyle, textMeasurer) {
        axisLabels.map { label -> textMeasurer.measure(text = label, style = axisLabelStyle) }
    }

    val density = LocalDensity.current
    // The gutter follows the widest measured label instead of a hardcoded axis width
    val axisGutter = remember(axisLabelLayouts, density) {
        with(density) {
            (axisLabelLayouts.maxOfOrNull { it.size.width } ?: 0).toDp() + ChartDimens.AxisLabelGap
        }
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val barColor = MaterialTheme.colorScheme.primary
    val selectedBarColor = MaterialTheme.colorScheme.tertiary

    // A canvas of bars is invisible to a screen reader unless the numbers are said out loud. The
    // summary gives the same information the chart conveys visually - each labelled amount - without
    // pretending to be an interactive control, which the per-bar tooltips are not.
    val chartDescription = remember(data, labels) {
        if (data.isEmpty()) {
            "No spending to chart."
        } else {
            labels.zip(data).joinToString(separator = ", ") { (label, amount) ->
                "$label ${formatIDR(amount.toLong())}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription }
    ) {
        // One gesture handler for the whole plot: a tap is mapped to the bar slot it lands on, so
        // selecting, toggling and clearing the selection all come from a single place. Keyed on the
        // data and the measured gutter so the closure always sees fresh geometry.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight + ChartDimens.VerticalPadding * 2)
                .pointerInput(data, axisGutter) {
                    detectTapGestures { offset ->
                        val gutter = axisGutter.toPx()
                        val plotWidth = size.width - gutter
                        if (plotWidth <= 0f || data.isEmpty() || offset.x < gutter) {
                            // Left of the plot area: the axis label gutter counts as empty space
                            selectedBarIndex = null
                            return@detectTapGestures
                        }
                        val slotWidth = plotWidth / data.size
                        val clickedIndex = ((offset.x - gutter) / slotWidth).toInt()
                        if (clickedIndex in data.indices) {
                            // Tapping the already selected bar clears it
                            selectedBarIndex = if (selectedBarIndex == clickedIndex) null else clickedIndex
                        } else {
                            // Right of the last slot: empty space
                            selectedBarIndex = null
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = ChartDimens.VerticalPadding)
            ) {
                val gutter = axisGutter.toPx()
                val slot = if (data.isEmpty()) 0f else ((size.width - gutter) / data.size).coerceAtLeast(0f)
                val barWidth = ChartDimens.BarWidth.toPx()
                val barRadius = ChartDimens.BarCornerRadius.toPx()
                val stroke = ChartDimens.GridLineThickness.toPx()

                repeat(GridLevelCount) { level ->
                    val y = size.height * level / (GridLevelCount - 1)
                    drawLine(
                        color = gridColor,
                        start = Offset(gutter, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                        // Dashed rather than solid: a solid rule competes with the bars for attention,
                        // while a dashed one is read as a measuring guide behind them.
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                    )
                    axisLabelLayouts.getOrNull(level)?.let { layout ->
                        drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
                    }
                }

                // Bars: proportional to the chart height, with only the top corners rounded.
                //
                // These used to be drawn inside a `clipRect` with a full-height rectangle beneath it,
                // which relied on the clip to square off the bottom corners. Nothing in that block
                // appeared on screen. Drawing the shape itself - a rounded-top rectangle that ends
                // exactly on the baseline - needs no clip at all, so the bar cannot be lost to one.
                data.forEachIndexed { index, _ ->
                    val barHeight = size.height * barFractions[index].value
                    if (barHeight <= 0f) return@forEachIndexed
                    val left = gutter + slot * index + (slot - barWidth) / 2f
                    val top = size.height - barHeight
                    val barRect = RoundRect(
                        rect = Rect(
                            left = left,
                            top = top,
                            right = left + barWidth,
                            bottom = size.height
                        ),
                        topLeft = CornerRadius(barRadius, barRadius),
                        topRight = CornerRadius(barRadius, barRadius),
                        bottomRight = CornerRadius.Zero,
                        bottomLeft = CornerRadius.Zero
                    )
                    drawPath(
                        path = Path().apply { addRoundRect(barRect) },
                        color = if (index == selectedBarIndex) selectedBarColor else barColor
                    )
                }
            }

            // Transparent interaction layer that mirrors the canvas geometry exactly
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = axisGutter,
                        top = ChartDimens.VerticalPadding,
                        bottom = ChartDimens.VerticalPadding
                    ),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEachIndexed { index, amount ->
                    Box(
                        // Unbounded width lets the tooltip pill outgrow the narrow bar column, and
                        // zIndex keeps whichever tooltip is active on the topmost layer
                        modifier = Modifier
                            .weight(1f)
                            .zIndex(if (selectedBarIndex == index) 1f else 0f)
                            .wrapContentWidth(unbounded = true),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (selectedBarIndex == index) {
                            BarTooltip(
                                fraction = barFractions[index],
                                amount = amount,
                                barAreaHeight = barAreaHeight
                            )
                        }
                    }
                }
            }
        }

        // X-axis labels aligned beneath their respective bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = axisGutter),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            labels.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                     Text(
                         text = label,
                         style = MaterialTheme.typography.labelSmall.copy(
                             fontSize = 10.sp,
                             lineHeight = 12.sp
                         ),
                         fontWeight = FontWeight.Medium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         maxLines = 1,
                         softWrap = false
                     )
                }
            }
        }
    }
}

/**
 * Floating Rupiah tooltip. It reads the animated bar fraction inside its own scope, so animating a
 * bar recomposes this pill only - never the chart or its neighbouring bars.
 */
@Composable
private fun BarTooltip(
    fraction: State<Float>,
    amount: Float,
    barAreaHeight: Dp
) {
    Surface(
        modifier = Modifier
            .offset(y = -(barAreaHeight * fraction.value))
            .height(ChartDimens.TooltipHeight)
            .wrapContentWidth(unbounded = true),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                // The bucket is a Float for the layout maths; the label is a whole amount of Rupiah.
                text = formatIDR(amount.toLong()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = ChartDimens.TooltipHorizontalPadding)
            )
        }
    }
}
