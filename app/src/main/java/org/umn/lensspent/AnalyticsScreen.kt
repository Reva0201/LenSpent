package org.umn.lensspent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.umn.lensspent.data.DaySummary
import org.umn.lensspent.data.SpendingAggregator
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.ui.components.CategoryBadge
import org.umn.lensspent.ui.components.CategoryShareRow
import org.umn.lensspent.ui.components.LedgerRow
import org.umn.lensspent.ui.components.LedgerRule
import org.umn.lensspent.ui.components.SegmentedTabs
import org.umn.lensspent.ui.theme.Motion
import org.umn.lensspent.ui.theme.Spacing
import org.umn.lensspent.ui.theme.motionTween
import org.umn.lensspent.ui.theme.rememberAnimationsEnabled

private val AnalyticsTabs = listOf("Daily", "Weekly", "Yearly", "Categories")

/** Categories is a breakdown, not a period, so it is the one compartment without the ledger below. */
private const val CategoriesTabIndex = 3

/**
 * The sheet used to be titled for the whole ledger but sat outside the selected compartment. It is
 * now the final section of every compartment, so it keeps the title that describes it.
 */
private const val HistoryTitle = "All transactions"

private val HistoryDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

/** "11-09-2026 - 18-09-2026": the week the stepper is currently on, Monday through Sunday. */
private fun weekRangeLabel(weekStart: LocalDate): String =
    dateRangeLabel(weekStart, weekStart.plusDays(6))

/**
 * Trends: when the money went, what it went on, and every expense that makes up the figures.
 *
 * **What changed.** The screen used to be two fixed viewports: a 336dp pager on top and an
 * independently scrolling history sheet below. On a normal phone that left the ledger as a narrow
 * strip, and at larger text it clipped the compartment it was supposedly showing. The compartments
 * and the ledger are now one scrolling column per tab, so the chart can take the height it needs and
 * the history simply follows it. Nothing is hidden behind a nested scroller and no height is fixed
 * in pixels for text-bearing content.
 *
 * **What was preserved.** Four tabs, their order, the swipe between them, the day/week steppers, the
 * year heading, the category ranking, all eight days/months of labels, and the complete history.
 * History rows are now tappable, which the old sheet was not: every row opens the same detail screen
 * the dashboard opens, so "See all" is no longer a dead end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { AnalyticsTabs.size })
    val scope = rememberCoroutineScope()
    val animationsEnabled = rememberAnimationsEnabled()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // "Trends" rather than "Spending Analytics": the screen answers "how is this
                        // going over time" and "where did it go", which is trends and breakdown.
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
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // The tab strip stays put while the compartment below it scrolls: switching periods
            // should not require scrolling back to the top first.
            SegmentedTabs(
                options = AnalyticsTabs,
                selectedIndex = pagerState.currentPage,
                onSelect = { index ->
                    scope.launch {
                        // Same duration and curve as the pill, so the strip and the page it controls
                        // move as one object. The default pager spring overshoots slightly, which read
                        // as a wobble on a screen of otherwise still content.
                        pagerState.animateScrollToPage(
                            index,
                            animationSpec = motionTween(
                                Motion.Standard,
                                animationsEnabled,
                                Motion.StandardEasing
                            )
                        )
                    }
                },
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.sm
                )
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                key = { page -> AnalyticsTabs[page] }
            ) { page ->
                val contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.xxxl
                )
                when (page) {
                    0 -> DailyPage(
                        state = uiState,
                        onOffsetChange = viewModel::stepDay,
                        contentPadding = contentPadding,
                        onOpenTransaction = onOpenTransaction
                    )

                    1 -> ChartPage(
                        title = "This week",
                        data = uiState.weekSeries,
                        labels = SpendingAggregator.dailyLabels,
                        contentPadding = contentPadding
                    ) {
                        StepperHeader(
                            label = weekRangeLabel(uiState.weekStart),
                            nextEnabled = uiState.canStepForwardWeek,
                            onStep = viewModel::stepWeek
                        )
                    }

                    2 -> ChartPage(
                        title = "${uiState.year} overview",
                        data = uiState.yearSeries,
                        labels = SpendingAggregator.monthlyLabels,
                        contentPadding = contentPadding,
                        // The Yearly compartment has no stepper, and its title already says what the
                        // chart is. A second centred "Spending by month" heading above it was a
                        // repeat that pushed the month axis under the ledger on a 360dp screen.
                        header = null
                    )

                    else -> CategoryPage(
                        state = uiState,
                        contentPadding = contentPadding
                    )
                }
            }

            // This is intentionally outside the pager. Changing Daily/Weekly/Yearly changes only the
            // analysis above; the complete ledger remains the same, in the same place, with the same
            // scroll position and date groups.
            //
            // Categories does not get it. That compartment answers "where did the month go" and is a
            // ranked breakdown in its own right; repeating the whole ledger under it made the tab
            // feel like every other tab plus a list, and halved the room the ranking had to breathe.
            //
            // The fold is animated rather than conditional: an `if` removes the composable on the same
            // frame, which is the one motion on this screen that read as a hard cut. Expanding and
            // fading in step with the pager keeps the whole change continuous.
            AnimatedVisibility(
                visible = pagerState.currentPage != CategoriesTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                enter = expandVertically(
                    animationSpec = motionTween(
                        Motion.Standard,
                        animationsEnabled,
                        Motion.StandardEasing
                    ),
                    expandFrom = Alignment.Top
                ) + fadeIn(
                    animationSpec = motionTween(
                        Motion.Standard,
                        animationsEnabled,
                        Motion.Decelerate
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = motionTween(
                        Motion.Standard,
                        animationsEnabled,
                        Motion.StandardEasing
                    ),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(
                    animationSpec = motionTween(
                        Motion.Quick,
                        animationsEnabled,
                        Motion.Accelerate
                    )
                )
            ) {
                SharedHistory(
                    history = uiState.history,
                    onOpenTransaction = onOpenTransaction,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * The fourth compartment: where the month's money actually went.
 *
 * Ranked bars rather than a donut: nine categories are easier to compare as lengths than as arcs, and
 * each row can carry its own amount without a legend.
 */
@Composable
private fun CategoryPage(
    state: AnalyticsUiState,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            Column(
                modifier = Modifier.padding(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = "By category",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${state.monthName} · ${formatIDR(state.monthTotal)} spent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.categoryTotals.isEmpty()) {
            item {
                Text(
                    text = "No spending recorded this month yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
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

/** Chart compartment: an optional header, the shared bar chart, then the ledger that produced it. */
@Composable
private fun ChartPage(
    title: String,
    data: List<Float>,
    labels: List<String>,
    contentPadding: PaddingValues,
    header: (@Composable () -> Unit)? = null
) {
    // The chart is sized from the height the compartment actually has rather than a fixed number.
    // A fixed 160dp was correct on a 360dp phone and left a dead band under the axis on a wide one;
    // a fixed 208dp was correct when wide and pushed the month axis under the ledger. Measuring the
    // column keeps the plot as tall as the space allows on every device, with a floor so the bars
    // never collapse and a ceiling so a tablet does not turn one week into a skyscraper.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtMost(1.6f)
        val reserved = 120.dp * fontScale
        val chartHeight = (maxHeight - reserved).coerceIn(160.dp, 360.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (header != null) {
                header()
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatIDR(data.sum().toLong()) + " spent in this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SpendingBarChart(
                data = data,
                labels = labels,
                barAreaHeight = chartHeight,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm)
            )
        }
    }
}

/**
 * Daily compartment: the stepper, the day's spend, and how it is made up.
 *
 * The day's totals sit together at the top, then the ledger. The stat row is allowed to stack when
 * the font scale would otherwise squeeze two amounts onto one line.
 */
@Composable
private fun DailyPage(
    state: AnalyticsUiState,
    onOffsetChange: (Int) -> Unit,
    contentPadding: PaddingValues,
    onOpenTransaction: (Long) -> Unit
) {
    val date = state.selectedDate
    val spent = state.selectedDateTotal
    val context = if (date == state.today) {
        "on this day so far"
    } else {
        "on ${dayName(date)}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            StepperHeader(
                label = dayLabel(date),
                nextEnabled = state.canStepForwardDay,
                onStep = onOffsetChange
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = if (state.hasPurchases) "You have spent" else "Nothing recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatIDR(spent),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.hasPurchases) {
            item {
                DayStatRow(
                    summary = state.selectedDay,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.md)
                )
            }
        }

    }
}

/** The one ledger shared by all four analysis modes. It has its own scroll, but never pages sideways. */
@Composable
private fun SharedHistory(
    history: List<Transaction>,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HistorySectionHeader(
            count = history.size,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxl)
        ) {
            historyItems(history, onOpenTransaction)
        }
    }
}

/**
 * The selected day's two numbers: how many expenses were logged, and the largest.
 *
 * Flat groupings rather than two raised cards, and a column on a narrow or large-text phone so the
 * amount and its merchant never have to be ellipsized to fit.
 */
@Composable
private fun DayStatRow(summary: DaySummary, modifier: Modifier = Modifier) {
    val largest = summary.largest
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        DayStat(
            value = summary.purchaseCount.toString(),
            label = if (summary.purchaseCount == 1) "purchase" else "purchases",
            supporting = null,
            modifier = Modifier.weight(1f)
        )
        largest?.let {
            DayStat(
                value = formatIDR(it.amount),
                label = "biggest expense",
                supporting = it.merchant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayStat(
    value: String,
    label: String,
    supporting: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .heightIn(min = 96.dp)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The heading above the ledger section, with a count so the list's scope is explicit.
 *
 * The list is the whole ledger, not just the selected period - that is what the old sheet did too,
 * and it is the reason its title was changed to "All transactions". Saying the count here removes
 * the last ambiguity about what the list contains.
 */
@Composable
private fun HistorySectionHeader(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = HistoryTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (count == 1) "1 expense" else "$count expenses",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A LazyListScope helper for the shared, date-grouped ledger. */
private fun androidx.compose.foundation.lazy.LazyListScope.historyItems(
    history: List<Transaction>,
    onOpenTransaction: (Long) -> Unit
) {
    if (history.isEmpty()) {
        item {
            Text(
                text = "No spending recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.md)
            )
        }
        return
    }

    val grouped = history
        .groupBy {
            java.time.Instant.ofEpochMilli(it.occurredAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
        .toSortedMap(compareByDescending { it })

    grouped.forEach { (date, transactions) ->
        item(key = "date-$date") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = date.format(HistoryDateFormatter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
        items(transactions, key = { it.id }) { txn ->
            LedgerRow(
                title = txn.merchant,
                subtitle = "${txn.category.name} · ${formatTransactionTime(txn.occurredAt)}",
                amount = formatIDR(txn.amount),
                leading = { CategoryBadge(category = txn.category, size = 40.dp) },
                onClick = { onOpenTransaction(txn.id) },
                modifier = Modifier.animateItem()
            )
            LedgerRule()
        }
    }
}
