package org.umn.lensspent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import org.umn.lensspent.data.SpendingAggregator
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.ui.components.CategoryBadge
import org.umn.lensspent.ui.components.CountUpAmount
import org.umn.lensspent.ui.components.LedgerRow
import org.umn.lensspent.ui.components.LedgerRule
import org.umn.lensspent.ui.components.LensCard
import org.umn.lensspent.ui.components.SectionHeader
import org.umn.lensspent.ui.theme.Motion
import org.umn.lensspent.ui.theme.Sizes
import org.umn.lensspent.ui.theme.Spacing
import org.umn.lensspent.ui.theme.motionTween
import org.umn.lensspent.ui.theme.rememberAnimationsEnabled

private val GreetingDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/**
 * The dashboard: what the user has spent, and the ways out of it.
 *
 * The Monthly Compass direction puts the month's budget first and the purchases that explain it
 * directly underneath. The order is the order the questions are actually asked:
 *
 *  1. how is this month going, against the ceiling I set;
 *  2. what does the week look like;
 *  3. anything else - the optional AI feature;
 *  4. which recent purchases explain the total.
 *
 * The budget summary is flat paper with a rule under it, not a coloured hero. That is both an
 * identity decision and a functional one: a gradient panel at the top of a ledger makes the number
 * look like a banner, and it forced a second colour system into a screen whose real information is
 * already carried by type, alignment and the category tints.
 *
 * [username] arrives from the root rather than being collected here, so this screen keeps no settings
 * state of its own to hold in step with the store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    username: String,
    onNavigateToCamera: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAiInsights: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Two labelled actions rather than a floating button. They are the app's whole input
            // surface, and hiding one behind a "+" would make the other read as the only option.
            // At large font scales the pair stacks instead of truncating its labels.
            AddExpenseBar(
                onAddManually = onNavigateToAddExpense,
                onScanReceipt = onNavigateToCamera
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.lg,
                bottom = Spacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
        ) {
            item { GreetingHeader(username = username, onOpenSettings = onNavigateToSettings) }

            item {
                BudgetSummary(
                    state = uiState,
                    onSetBudget = onNavigateToSettings
                )
            }

            item {
                WeekGlance(
                    weekTotal = uiState.weekTotal,
                    todayTotal = uiState.todayTotal
                )
            }

            item {
                SpendingOverviewCard(
                    weekSeries = uiState.weekSeries,
                    weekDailyAverage = uiState.weekDailyAverage,
                    onOpenAnalytics = onNavigateToAnalytics
                )
            }

            item { AiInsightsCard(onClick = onNavigateToAiInsights) }

            // Recent purchases are the supporting explanation for the month, not the opening
            // headline. Keeping this block last lets the budget, weekly pattern and optional AI
            // action breathe before the ledger begins.
            item {
                SectionHeader(title = "Recent transactions") {
                    TextButton(onClick = onNavigateToAnalytics) {
                        Text(
                            text = "See all",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            item {
                if (uiState.recentTransactions.isEmpty()) {
                    EmptyLedger(onNavigateToCamera = onNavigateToCamera)
                } else {
                    RecentTransactionsCard(
                        transactions = uiState.recentTransactions,
                        onOpenTransaction = onOpenTransaction
                    )
                }
            }
        }
    }
}

/**
 * Who it is, and when it is.
 *
 * Sentence case throughout: the date is a fact, not a label, and tracked capitals at the top of every
 * screen is one of the fastest ways to make an app look generated. The initial is the only personal
 * mark the app makes; it is decorative and is hidden from TalkBack, which already reads the greeting.
 */
@Composable
private fun GreetingHeader(username: String, onOpenSettings: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateLabel = remember { LocalDate.now().format(GreetingDateFormatter) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel.replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                // "User" only when onboarding was left blank: a greeting is not worth a name the user
                // chose not to give.
                text = "$greeting, ${username.ifBlank { "User" }}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = username.trim().firstOrNull()?.uppercase() ?: "L",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * The month, as the one thing the app is for.
 *
 * Paper and ink, with one rule and a thin olive progress bar. The amount leads at display size; the
 * percentage and the remaining figure sit on the same baseline below it, where they are read as a
 * sentence about the number rather than as two competing badges.
 *
 * With no ceiling the card does not draw an empty bar - an empty bar reads as "you have spent nothing"
 * when it actually means "there is nothing to compare against". It offers the one action that fixes
 * that instead. Over budget it says so, rather than showing "Rp 0 left" under a full bar.
 */
@Composable
private fun BudgetSummary(
    state: HomeUiState,
    onSetBudget: () -> Unit
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val animatedFraction by animateFloatAsState(
        targetValue = state.budgetUsedFraction,
        // Matches the total above it: the bar and the figure are the same fact, so they settle
        // together instead of the bar finishing a third of a second after the number.
        animationSpec = motionTween(
            Motion.Deliberate,
            animationsEnabled,
            Motion.Decelerate
        ),
        label = "budget-used-fraction"
    )
    val overspent = state.hasBudget && state.monthTotal > state.monthlyBudget
    val overBy = (state.monthTotal - state.monthlyBudget).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "Spent in ${state.monthName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        CountUpAmount(
            amount = state.monthTotal,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (state.hasBudget) {
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                color = if (overspent) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Text(
                text = if (overspent) {
                    "${formatIDR(overBy)} over budget"
                } else {
                    "${formatIDR(state.budgetRemaining)} left of ${formatIDR(state.monthlyBudget)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (overspent) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Text(
                text = if (overspent) {
                    // The state caps its progress fraction at one so the bar cannot overflow, which
                    // makes its own percentage read "100%" while the amount above says "over". The
                    // actual share is what belongs here.
                    val percent = ((state.monthTotal.toDouble() / state.monthlyBudget) * 100).toInt()
                    "$percent% of the ${formatIDR(state.monthlyBudget)} budget used"
                } else {
                    "${state.budgetUsedPercent}% of the month's budget used"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onSetBudget)
                    .heightIn(min = Sizes.touchTarget)
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set a monthly budget",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/**
 * Two numbers worth knowing without opening Trends: the week so far, and today.
 *
 * They are one inline row on the page, separated by a rule, rather than two identical stat cards.
 * The cards gave a pair of supporting figures the same visual weight as the month's total, which is
 * how a dashboard ends up with four things that all look equally important.
 */
@Composable
private fun WeekGlance(weekTotal: Float, todayTotal: Float) {
    Row(modifier = Modifier.fillMaxWidth()) {
        GlanceValue(
            label = "This week",
            amount = formatIDR(weekTotal.toLong()),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(44.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        GlanceValue(
            label = "Today",
            amount = formatIDR(todayTotal.toLong()),
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.lg)
        )
    }
}

@Composable
private fun GlanceValue(label: String, amount: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SpendingOverviewCard(
    weekSeries: List<Float>,
    weekDailyAverage: Float,
    onOpenAnalytics: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "This week's pattern",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatIDR(weekDailyAverage.toLong())} a day on average",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenAnalytics) {
                Text("Open trends", style = MaterialTheme.typography.labelLarge)
            }
        }

        LensCard {
            Box(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                SpendingBarChart(
                    data = weekSeries,
                    labels = SpendingAggregator.dailyLabels
                )
            }
        }
    }
}

/**
 * The AI entry point.
 *
 * It sits after the ledger because it is optional and, on a build without a backend, cannot do
 * anything at all. A quiet outline and a sentence is the correct weight for that; a gradient disc
 * made it the loudest thing on a screen about money.
 */
@Composable
private fun AiInsightsCard(onClick: () -> Unit) {
    LensCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = "AI Insights",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ask about your spending, in plain language",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The ledger itself.
 *
 * One surface with inset rules rather than a stack of separate cards: the rows are one table, and
 * drawing them as six cards made the dashboard read as six unrelated things. Each row is its own tap
 * target and grows with its text.
 */
@Composable
private fun RecentTransactionsCard(
    transactions: List<Transaction>,
    onOpenTransaction: (Long) -> Unit
) {
    LensCard {
        Column {
            transactions.forEachIndexed { index, transaction ->
                if (index > 0) {
                    LedgerRule()
                }
                LedgerRow(
                    title = transaction.merchant,
                    subtitle = "${transaction.category.name} · ${
                        formatTransactionDate(transaction.occurredAt)
                    } · ${formatTransactionTime(transaction.occurredAt)}",
                    amount = formatIDR(transaction.amount),
                    leading = { CategoryBadge(category = transaction.category) },
                    onClick = { onOpenTransaction(transaction.id) }
                )
            }
        }
    }
}

@Composable
private fun EmptyLedger(onNavigateToCamera: () -> Unit) {
    LensCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }
            Text(
                text = "No spending yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                // Kept in full: it names both ways to add an expense, including the one that needs
                // no camera permission.
                text = "Capture a receipt, or add an expense by hand, and it will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onNavigateToCamera,
                contentPadding = PaddingValues(horizontal = Spacing.xs)
            ) {
                Text("Capture a receipt", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * The two ways to add an expense, above the system navigation bar.
 *
 * The bar uses the top edge as its only separation from the scrolling page. It stacks at large font
 * scales: two 52dp buttons side by side cannot hold "Add manually" and "Scan receipt" at 200% text
 * without either clipping or shrinking the labels, and shrinking them is not an option.
 */
@Composable
private fun AddExpenseBar(
    onAddManually: () -> Unit,
    onScanReceipt: () -> Unit
) {
        val fontScale = LocalDensity.current.fontScale

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            // Two labelled buttons need roughly 330dp once their icons and insets are counted. Below
            // that - a narrow phone, or any large font scale - they stack, because a two-line button
            // label is a worse outcome than a taller bar.
            val stacked = maxWidth < 320.dp || fontScale >= 1.15f

            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryActionButton(
                        text = "Scan receipt",
                        icon = Icons.Default.CameraAlt,
                        onClick = onScanReceipt,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryActionButton(
                        text = "Add manually",
                        icon = Icons.Default.Add,
                        onClick = onAddManually,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryActionButton(
                        text = "Add manually",
                        icon = Icons.Default.Add,
                        onClick = onAddManually,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryActionButton(
                        text = "Scan receipt",
                        icon = Icons.Default.CameraAlt,
                        onClick = onScanReceipt,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconSmall)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconSmall)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false
        )
    }
}
