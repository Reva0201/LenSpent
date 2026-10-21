package org.umn.lensspent

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.umn.lensspent.data.DaySummary
import org.umn.lensspent.data.SpendingAggregator
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.ui.components.CategoryBadge
import org.umn.lensspent.ui.components.CategoryShareRow
import org.umn.lensspent.ui.components.SegmentedTabs
import org.umn.lensspent.ui.theme.Spacing

private val AnalyticsTabs = listOf("Daily", "Weekly", "Yearly", "Categories")

/**
 * The sheet lists the whole ledger, not the selected compartment, so it is titled for what it is
 * rather than for the period above it. "History Spending" was neither one thing nor the other.
 */
private const val HistoryTitle = "All transactions"

/** "11-09-2026 - 18-09-2026": the week the stepper is currently on, Monday through Sunday. */
private fun weekRangeLabel(weekStart: LocalDate): String =
    dateRangeLabel(weekStart, weekStart.plusDays(6))

/** Screen-level layout tokens; shapes, colours and typography come from Material 3. */
private object AnalyticsDimens {
    /**
     * The compartment height. It was 280dp, sized for the daily summary; the category breakdown needs a
     * little more room, and the daily tab has slack, so the whole pager grew rather than the sheet
     * shrinking on one tab only.
     */
    val PagerHeight = 336.dp
    /**
     * The plot area of the analytics chart. Sized so a compartment is fully used without clipping the
     * axis labels: header band (48) + gap (8) + plot + vertical padding (32) + labels (~24) has to fit
     * inside [PagerHeight], which leaves 224dp for the plot.
     */
    val ChartHeight = 214.dp
    val Gutter = 16.dp
    val VerticalGap = 8.dp
    val SheetCorner = 32.dp
    val SheetTopPadding = 24.dp
    val ListBottomPadding = 24.dp
    val ListSpacing = 12.dp
    val TxCardPadding = 12.dp
    val TxCardSpacing = 12.dp
    val TxChipSize = 40.dp
    val TxGlyphSize = 20.dp
    val InlineContextPadding = 4.dp

    /** Daily stat cards: tall enough to breathe, shallow enough to still fit inside [PagerHeight]. */
    val StatRowHeight = 112.dp
    val StatIconSize = 18.dp
    val StatInnerGap = 4.dp
}

/**
 * Analytics destination reachable from the dashboard chevron. It is split into two compartments:
 * a top area with swipeable daily, weekly and yearly charts, and a bottom, independently scrolling
 * transaction history.
 *
 * Every series here is bucketed by [SpendingAggregator] from the same Room rows the dashboard reads,
 * and the chart itself is the shared [SpendingBarChart] - no chart code is duplicated per screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    // The day/week offsets live in the ViewModel so they survive rotation; only pager state is here
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { AnalyticsTabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // "Trends" rather than "Spending Analytics": the screen answers "how is this
                        // going over time" and "where did it go", which is trends and breakdown, not
                        // statistics. It is also short enough to sit next to the back arrow without
                        // reading like a report heading.
                        text = "Trends",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                // Edge-to-edge is enabled, so the app bar keeps its status bar inset
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // A pill switcher instead of a tab row: the selection is an object on a recessed track,
            // and it sits in the gutter with the content rather than spanning the screen edge to edge.
            SegmentedTabs(
                options = AnalyticsTabs,
                selectedIndex = pagerState.currentPage,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.padding(
                    start = AnalyticsDimens.Gutter,
                    end = AnalyticsDimens.Gutter,
                    top = AnalyticsDimens.VerticalGap,
                    bottom = AnalyticsDimens.VerticalGap
                )
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AnalyticsDimens.PagerHeight)
            ) { page ->
                when (page) {
                    0 -> DailySummaryPage(
                        state = uiState,
                        onOffsetChange = viewModel::stepDay
                    )

                    1 -> ChartPage(
                        data = uiState.weekSeries,
                        labels = SpendingAggregator.dailyLabels
                    ) {
                        StepperHeader(
                            label = weekRangeLabel(uiState.weekStart),
                            nextEnabled = uiState.canStepForwardWeek,
                            onStep = viewModel::stepWeek
                        )
                    }

                    2 -> ChartPage(
                        data = uiState.yearSeries,
                        labels = SpendingAggregator.monthlyLabels
                    ) {
                        LabelHeader(text = "${uiState.year} Overview")
                    }

                    else -> CategoryPage(state = uiState)
                }
            }            
            TransactionHistorySheet(transactions = uiState.history)
        }
    }
}

/**
 * The fourth compartment: where the month's money actually went.
 *
 * The three tabs before this one answer "when"; none of them answer "what on", which is the question a
 * spending app exists for. Ranked bars rather than a donut: nine categories are easier to compare as
 * lengths than as arcs, and each row can carry its own amount without a legend.
 */
@Composable
private fun CategoryPage(state: AnalyticsUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = AnalyticsDimens.Gutter,
                vertical = AnalyticsDimens.VerticalGap
            ),
        verticalArrangement = Arrangement.spacedBy(AnalyticsDimens.VerticalGap)
    ) {
        Column {
            Text(
                text = "By category",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${state.monthName} Â· ${formatIDR(state.monthTotal)} spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.categoryTotals.isEmpty()) {
            Text(
                text = "No spending recorded this month yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(state.categoryTotals, key = { it.category.id }) { total ->
                    CategoryShareRow(
                        category = total.category,
                        amount = total.amount,
                        share = total.share,
                        formattedAmount = formatIDR(total.amount)
                    )
                }
            }
        }
    }
}

/** Chart compartment: an optional header above the shared [SpendingBarChart]. */
@Composable
private fun ChartPage(
    data: List<Float>,
    labels: List<String>,
    header: @Composable () -> Unit
) {    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = AnalyticsDimens.Gutter,
                vertical = AnalyticsDimens.VerticalGap
            ),
        verticalArrangement = Arrangement.spacedBy(AnalyticsDimens.VerticalGap)
    ) {
        // A fixed-height band, so the chart starts on the same line in every compartment. The plot
        // area itself is taller than the dashboard's strip: the compartment is 336dp and a 160dp chart
        // left a dead band between the chart and the history sheet.
        HeaderSlot { header() }
        SpendingBarChart(
            data = data,
            labels = labels,
            barAreaHeight = AnalyticsDimens.ChartHeight
        )
    }
}

/**
 * Daily compartment: the date stepper above the day's spend, styled as a small label followed by the
 * headline amount and its inline context ("so far today." / "on Thursday."). Amounts come from
 * the stored total for the selected date, so this tab can never disagree with the chart.
 */
@Composable
private fun DailySummaryPage(
    state: AnalyticsUiState,
    onOffsetChange: (Int) -> Unit
) {
    val date = state.selectedDate
    val spent = state.selectedDateTotal
    val context = if (date == state.today) {
        " so far today."
    } else {
        " on ${dayName(date)}."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = AnalyticsDimens.Gutter,
                vertical = AnalyticsDimens.VerticalGap
            ),
        verticalArrangement = Arrangement.spacedBy(AnalyticsDimens.VerticalGap)
    ) {
        // The same header band the chart tabs use, so swiping between tabs moves nothing above the fold
        StepperHeader(
            label = dayLabel(date),
            nextEnabled = state.canStepForwardDay,
            onStep = onOffsetChange
        )

        Text(
            text = "You have spent,",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatIDR(spent),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = context,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = AnalyticsDimens.InlineContextPadding)
            )
        }

        // The space under the headline is dead on this tab: the pager reserves a fixed height so the
        // three compartments stay put, and only the chart tabs fill it. The day's two numbers go here,
        // stretched to the bottom of the compartment so the tab is not half empty.
        if (state.hasPurchases) {
            // The row keeps its natural height and is centred in what is left of the compartment:
            // stretching the cards to fill it left each card with a hole in the middle, because their
            // icon sits at the top and their numbers at the bottom.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                DayStatRow(summary = state.selectedDay)
            }
        }
    }
}

/**
 * The selected day's two numbers, side by side: how many expenses were logged, and the largest.
 *
 * The biggest-expense card only exists when the day has one, which [DaySummary.purchaseCount] above
 * zero already guarantees - the check is here so the cards cannot render a placeholder amount.
 */
@Composable
private fun DayStatRow(summary: DaySummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AnalyticsDimens.StatRowHeight),
        horizontalArrangement = Arrangement.spacedBy(AnalyticsDimens.TxCardSpacing)
    ) {
        StatCard(
            icon = Icons.Filled.Receipt,
            value = summary.purchaseCount.toString(),
            label = if (summary.purchaseCount == 1) "purchase" else "purchases",
            supporting = null,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        summary.largest?.let { largest ->
            StatCard(
                // The same category icon the expense carries in the history list below.
                icon = categoryIcon(largest.category.iconKey),
                value = formatIDR(largest.amount),
                label = "biggest expense",
                supporting = largest.merchant,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

/** One daily stat card, drawn with the same surface recipe as a history row. */
@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    supporting: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(AnalyticsDimens.TxCardPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AnalyticsDimens.StatIconSize)
        )
        Column(verticalArrangement = Arrangement.spacedBy(AnalyticsDimens.StatInnerGap)) {
            // The number carries the scale's own weight rather than a bold override, so this card and
            // the dashboard's two stat cards read at the same emphasis.
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Rounded bottom sheet holding the transaction history, newest first. */
@Composable
private fun TransactionHistorySheet(transactions: List<Transaction>) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(
            topStart = AnalyticsDimens.SheetCorner,
            topEnd = AnalyticsDimens.SheetCorner
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(
                top = AnalyticsDimens.SheetTopPadding,
                start = AnalyticsDimens.Gutter,
                end = AnalyticsDimens.Gutter
            ),
            verticalArrangement = Arrangement.spacedBy(AnalyticsDimens.VerticalGap)
        ) {
            Text(
                text = HistoryTitle,
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AnalyticsDimens.ListBottomPadding),
                verticalArrangement = Arrangement.spacedBy(AnalyticsDimens.ListSpacing)
            ) {
                if (transactions.isEmpty()) {
                    item {
                        Text(
                            text = "No spending recorded for this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(transactions, key = { it.id }) { txn ->
                    TransactionRow(
                        txn = txn,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

/** Single transaction card: the same category badge the dashboard uses, then the Rupiah amount. */
@Composable
private fun TransactionRow(txn: Transaction, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .padding(AnalyticsDimens.TxCardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AnalyticsDimens.TxCardSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryBadge(category = txn.category)
            Column {
                Text(
                    text = txn.merchant,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${txn.category.name} â€¢ ${formatTransactionDate(txn.occurredAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = formatIDR(txn.amount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
