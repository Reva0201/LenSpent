package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.umn.lensspent.data.DaySummary
import org.umn.lensspent.data.SpendingAggregator
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * Analytics state: the selected day, the selected Monday-first week, the current year and the history
 * list, all derived from the same rows the dashboard shows.
 */
data class AnalyticsUiState(
    val isLoading: Boolean = true,
    /**
     * The clock's today. Required, not defaulted to LocalDate.now(): [AnalyticsViewModel] already
     * injects a Clock, and a default here would read the system clock a second time and could hand
     * back a state whose week starts after the day it contains.
     */
    val today: LocalDate,
    val selectedDate: LocalDate = today,
    val selectedDateTotal: Long = 0L,
    /**
     * The selected day's row count and its largest expense.
     *
     * One field rather than two, so a state that counts expenses it cannot name cannot be built.
     */
    val selectedDay: DaySummary = DaySummary(),
    val weekStart: LocalDate = SpendingAggregator.weekStart(today),
    val weekSeries: List<Float> = List(SpendingAggregator.DaysInWeek) { 0f },
    val year: Int = today.year,
    val yearSeries: List<Float> = List(SpendingAggregator.MonthsInYear) { 0f },
    val history: List<Transaction> = emptyList()
) {
    /** Whether the selected day has anything to summarise. */
    val hasPurchases: Boolean get() = selectedDay.purchaseCount > 0

    val canStepForwardDay: Boolean get() = selectedDate.isBefore(today)

    /** The week containing today is the newest one there is. */
    val canStepForwardWeek: Boolean
        get() = weekStart.isBefore(SpendingAggregator.weekStart(today))
}

/**
 * Owns the day/week offsets so they survive configuration changes, and rebuckets the ledger every
 * time a row changes. Aggregation happens in Kotlin over the observed rows: precise, testable and
 * cheap at these volumes, and it moves into SQL when the ledger grows.
 */
class AnalyticsViewModel(
    repository: TransactionRepository,
    clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val zone: ZoneId = clock.zone
    private val today: LocalDate = LocalDate.now(clock)
    private val todayWeekStart: LocalDate = SpendingAggregator.weekStart(today)

    private val dayOffset = MutableStateFlow(0)
    private val weekOffset = MutableStateFlow(0)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.observeTransactions(),
        dayOffset,
        weekOffset
    ) { transactions, dayOffsetValue, weekOffsetValue ->
        val selectedDate = today.plusDays(dayOffsetValue.toLong())
        val weekStart = todayWeekStart.plusWeeks(weekOffsetValue.toLong())
        AnalyticsUiState(
            isLoading = false,
            today = today,
            selectedDate = selectedDate,
            selectedDateTotal = SpendingAggregator.totalOn(transactions, selectedDate, zone),
            selectedDay = SpendingAggregator.daySummary(transactions, selectedDate, zone),
            weekStart = weekStart,
            weekSeries = SpendingAggregator.dailySeries(transactions, weekStart, zone),
            year = today.year,
            yearSeries = SpendingAggregator.monthlySeries(transactions, today.year, zone),
            history = transactions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionStopTimeoutMillis),
        initialValue = AnalyticsUiState(today = today, weekStart = todayWeekStart)
    )

    /** Steps the day stepper; never past today, since the future has no spending in it. */
    fun stepDay(delta: Int) {
        dayOffset.update { (it + delta).coerceAtMost(0) }
    }

    /** Steps the week stepper; the week containing today is the newest one there is. */
    fun stepWeek(delta: Int) {
        weekOffset.update { (it + delta).coerceAtMost(0) }
    }
}
