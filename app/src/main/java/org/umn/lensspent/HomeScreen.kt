package org.umn.lensspent

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import org.umn.lensspent.data.SpendingAggregator
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.ui.components.CategoryBadge
import org.umn.lensspent.ui.components.CountUpAmount
import org.umn.lensspent.ui.components.LensCard
import org.umn.lensspent.ui.components.SectionHeader
import org.umn.lensspent.ui.theme.Elevation
import org.umn.lensspent.ui.theme.LocalHeroColors
import org.umn.lensspent.ui.theme.Sizes
import org.umn.lensspent.ui.theme.Spacing

private val GreetingDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/**
 * The dashboard: what the user has spent, and the ways out of it.
 *
 * The layout is a deliberate stack of three ideas, in the order a person asks them: how am I doing
 * this month (the hero), what does this week look like (the chart), what did I actually buy (the
 * ledger). Everything else - the AI entry point, the actions - is secondary and sits below or in chrome.
 *
 * [username] arrives from the root rather than being collected here, so this screen keeps no settings
 * state of its own to hold in step with the store.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
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

    // Manage granular modern system platform runtime permissions
    val permissionsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        // Pre-scoped storage: saving a capture into the shared gallery also needs WRITE
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions = permissionsList)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // A real action bar rather than a floating button over the list: the two ways to add an
            // expense are the app's whole input surface, and labelling both of them - instead of
            // hiding one behind a "+" - is worth the strip of screen. Settings moved to the header,
            // which leaves this bar saying one thing.
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                ),
                // The dock lifts off the scrolling content instead of being a third white block on it.
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onNavigateToAddExpense,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Add manually",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = {
                            if (multiplePermissionsState.allPermissionsGranted) {
                                onNavigateToCamera()
                            } else {
                                multiplePermissionsState.launchMultiplePermissionRequest()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Scan receipt",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
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
                BudgetHero(
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
                    EmptyLedgerCard(onNavigateToCamera = onNavigateToCamera)
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
 * The date is spelled out rather than implied, because the whole dashboard is about a month and a week:
 * "Thursday, 22 October" is the anchor for the two amounts underneath it. The initial in the circle is
 * the only personal mark the app makes, and it stands in for an avatar it does not have.
 */
@Composable
private fun GreetingHeader(username: String, onOpenSettings: () -> Unit) {
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
                text = dateLabel.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
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
 * This is the only surface painted with the brand gradient and the only place display-size type is
 * used, which is what makes it the answer to "how am I doing" before anything else on the page is read.
 * With no budget it degrades to a single invitation rather than drawing an empty bar, because an empty
 * bar reads as "you have spent nothing" when it means "there is nothing to compare against".
 */
@Composable
private fun BudgetHero(
    state: HomeUiState,
    onSetBudget: () -> Unit
) {
    val hero = LocalHeroColors.current
    val animatedFraction by animateFloatAsState(
        targetValue = state.budgetUsedFraction,
        animationSpec = tween(durationMillis = 700),
        label = "budget-used-fraction"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(listOf(hero.start, hero.end)))
            // A 1dp white edge at 12%, drawn inside the shape. On a dark card this is what separates
            // "a filled rectangle" from "a pane of tinted glass": the eye reads a lit top edge and the
            // card lifts off the page without a drop shadow underneath it.
            .border(
                width = 1.dp,
                // The card's own content colour, not a literal white: in dark mode the hero's ink is a
                // pale blue-white, and a fixed white edge would be brighter than the text it frames.
                color = hero.content.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPENT IN ${state.monthName.uppercase(Locale.getDefault())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = hero.contentMuted
                )
                if (state.hasBudget) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = hero.content.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "${state.budgetUsedPercent}% of budget",
                            style = MaterialTheme.typography.labelSmall,
                            color = hero.content,
                            modifier = Modifier.padding(
                                horizontal = Spacing.sm,
                                vertical = Spacing.xs
                            )
                        )
                    }
                }
            }

            CountUpAmount(
                amount = state.monthTotal,
                style = MaterialTheme.typography.displaySmall,
                color = hero.content
            )

            if (state.hasBudget) {
                // White rather than the accent: the line is a measurement of the total above it, and on
                // a navy card a white line reads as part of the number while a coloured one reads as a
                // second piece of information.
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = hero.content,
                    trackColor = hero.content.copy(alpha = 0.24f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatIDR(state.budgetRemaining)} left",
                        style = MaterialTheme.typography.labelLarge,
                        color = hero.content
                    )
                    Text(
                        text = "of ${formatIDR(state.monthlyBudget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = hero.contentMuted
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onSetBudget)
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Set a monthly budget",
                        style = MaterialTheme.typography.labelLarge,
                        color = hero.content
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = hero.content
                    )
                }
            }
        }
    }
}

/**
 * Two numbers worth knowing without opening Analytics: the week so far, and today.
 *
 * Both are read out of the same seven buckets the chart draws, so they cannot disagree with it.
 */
@Composable
private fun WeekGlance(
    weekTotal: Float,
    todayTotal: Float
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatCard(
            label = "This week",
            amount = weekTotal,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Today",
            amount = todayTotal,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    amount: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatIDR(amount.toLong()),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SpendingOverviewCard(
    weekSeries: List<Float>,
    weekDailyAverage: Float,
    onOpenAnalytics: () -> Unit
) {
    LensCard {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Spending overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatIDR(weekDailyAverage.toLong())} a day on average",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenAnalytics) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open trends",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            SpendingBarChart(
                data = weekSeries,
                labels = SpendingAggregator.dailyLabels
            )
        }
    }
}

/**
 * The one paid-tier entry point on the dashboard.
 *
 * It earns a gradient disc rather than a plain circle because it is a different kind of thing from the
 * ledger underneath: the accent is the app saying "this one costs money".
 */
/**
 * The AI entry point on the dashboard.
 *
 * No "PREMIUM" pill: the app has no paid tier behind it, so the label was advertising a distinction
 * that does not exist. The card says what it does and lets the screen behind it say the rest.
 */
@Composable
private fun AiInsightsCard(onClick: () -> Unit) {
    val hero = LocalHeroColors.current
    LensCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(hero.end, hero.start))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = hero.content,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = "AI Insights",
                    style = MaterialTheme.typography.titleMedium,
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
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * The ledger itself.
 *
 * One surface with dividers rather than a stack of separate cards: the rows are one table, and drawing
 * them as six cards made the dashboard read as six unrelated things. Each row is still its own tap
 * target, and the divider is the only thing between them.
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
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 68.dp)
                    )
                }
                TransactionRow(
                    transaction = transaction,
                    onClick = { onOpenTransaction(transaction.id) }
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category = transaction.category)

        Spacer(modifier = Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${transaction.category.name} · ${formatTransactionDate(transaction.occurredAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Text(
            text = formatIDR(transaction.amount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(Sizes.iconSmall)
        )
    }
}

@Composable
private fun EmptyLedgerCard(onNavigateToCamera: () -> Unit) {
    LensCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.iconLarge)
            )
            Text(
                text = "No spending yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Capture a receipt, or add an expense by hand, and it will show up here.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onNavigateToCamera) {
                Text("Capture a receipt", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
