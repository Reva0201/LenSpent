package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.umn.lensspent.data.SpendingAggregator
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.TransactionRepository

/** Budget is not user-configurable yet: one constant to replace when a settings screen exists. */
const val DefaultMonthlyBudgetIdr: Long = 37_500_000L

private const val RecentTransactionLimit = 6

/** Dashboard state. Amounts are whole Rupiah. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val recentTransactions: List<Transaction> = emptyList(),
    val weekSeries: List<Float> = List(SpendingAggregator.DaysInWeek) { 0f },
    val monthTotal: Long = 0L,
    val monthlyBudget: Long = DefaultMonthlyBudgetIdr
) {
    val budgetUsedFraction: Float
        get() = if (monthlyBudget <= 0L) 0f else (monthTotal.toFloat() / monthlyBudget).coerceIn(0f, 1f)

    val budgetUsedPercent: Int get() = (budgetUsedFraction * 100f).roundToInt()
}

/**
 * Backs the dashboard with real rows: the recent list, this week's weekday buckets and this month's
 * running total, all recomputed by Room whenever anything changes.
 */
class HomeViewModel(
    repository: TransactionRepository,
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
        )
    ) { recent, weekTransactions, monthTotal ->
        HomeUiState(
            isLoading = false,
            recentTransactions = recent,
            weekSeries = SpendingAggregator.dailySeries(weekTransactions, weekStart, zone),
            monthTotal = monthTotal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionStopTimeoutMillis),
        initialValue = HomeUiState()
    )
}
