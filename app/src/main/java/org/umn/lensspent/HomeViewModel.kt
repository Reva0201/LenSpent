package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.umn.lensspent.data.SpendingAggregator
import org.umn.lensspent.data.local.NoBudgetIdr
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.TransactionRepository

private const val RecentTransactionLimit = 6

/** Dashboard state. Amounts are whole Rupiah. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val recentTransactions: List<Transaction> = emptyList(),
    val weekSeries: List<Float> = List(SpendingAggregator.DaysInWeek) { 0f },
    val monthTotal: Long = 0L,
    /** The ceiling the user chose, or [NoBudgetIdr] when they have not chosen one. */
    val monthlyBudget: Long = NoBudgetIdr,
    /** Which of [weekSeries] is today, so the week's own card can point at the right bar. */
    val todayIndex: Int = 0,
    /** The month being totalled, spelled out for the hero card's heading. */
    val monthName: String = ""
) {

    /**
     * Whether there is a ceiling to measure the month against.
     *
     * The card asks this before drawing a bar: with no budget the fraction below is 0 by definition,
     * and a bar pinned at empty reads as "you have spent nothing" when it means "there is nothing to
     * compare against".
     */
    val hasBudget: Boolean get() = monthlyBudget > NoBudgetIdr

    val budgetUsedFraction: Float
        get() = if (monthlyBudget <= 0L) 0f else (monthTotal.toFloat() / monthlyBudget).coerceIn(0f, 1f)

    val budgetUsedPercent: Int get() = (budgetUsedFraction * 100f).roundToInt()

    /** What is left of the ceiling, floored at zero: overspending is a message, not a negative amount. */
    val budgetRemaining: Long get() = (monthlyBudget - monthTotal).coerceAtLeast(0L)

    /** This week's running total, derived from the same buckets the chart draws. */
    val weekTotal: Float get() = weekSeries.sum()

    /** Today's bucket, derived rather than queried a second time, so the card and the bar agree. */
    val todayTotal: Float get() = weekSeries.getOrElse(todayIndex) { 0f }

    /** What the week has averaged per day, including the days that have not happened yet. */
    val weekDailyAverage: Float
        get() = if (weekSeries.isEmpty()) 0f else weekTotal / weekSeries.size
}

/**
 * Backs the dashboard with real rows: the recent list, this week's weekday buckets, this month's
 * running total and the ceiling the user set in onboarding, all recomputed whenever anything changes.
 */
class HomeViewModel(
    repository: TransactionRepository,
    preferences: UserPreferencesRepository,
    clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val zone: ZoneId = clock.zone

    /**
     * The windows are resolved once per ViewModel. A dashboard left open across midnight keeps
     * yesterday's week and month until it is reopened - the same trade-off the mock data always made.
     */
    private val today: LocalDate = LocalDate.now(clock)
    private val weekStart: LocalDate = SpendingAggregator.weekStart(today)
    private val monthStart: LocalDate = today.withDayOfMonth(1)
    private val nextMonthStart: LocalDate = monthStart.plusMonths(1)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeTransactions(RecentTransactionLimit),
        repository.observeTransactionsBetween(
            SpendingAggregator.startOfDayMillis(weekStart, zone),
            SpendingAggregator.startOfDayMillis(
                weekStart.plusDays(SpendingAggregator.DaysInWeek.toLong()),
                zone
            )
        ),
        repository.observeSumBetween(
            SpendingAggregator.startOfDayMillis(monthStart, zone),
            SpendingAggregator.startOfDayMillis(nextMonthStart, zone)
        ),
        // The budget is a preference, not a row: it rides along with the ledger flows so that setting
        // one redraws the card - including on the frame where the user comes back from Settings.
        preferences.monthlyBudgetIdr
    ) { recent, weekTransactions, monthTotal, monthlyBudget ->
        HomeUiState(
            isLoading = false,
            recentTransactions = recent,
            weekSeries = SpendingAggregator.dailySeries(weekTransactions, weekStart, zone),
            monthTotal = monthTotal,
            monthlyBudget = monthlyBudget,
            // Clamped because a stored week start can only differ from today by less than a week, but
            // the index is used to index a list and a stray value must not crash the dashboard.
            todayIndex = ChronoUnit.DAYS.between(weekStart, today).toInt()
                .coerceIn(0, SpendingAggregator.DaysInWeek - 1),
            monthName = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionStopTimeoutMillis),
        initialValue = HomeUiState()
    )
}
