package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.data.prefs.UserSettings
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.DashboardSummary
import com.shohan.bokeya.domain.model.Insight
import com.shohan.bokeya.domain.model.LedgerEntry
import com.shohan.bokeya.domain.model.MonthlyTrend
import com.shohan.bokeya.domain.model.UpcomingPayment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val greeting: BanglaDate.GreetingType = BanglaDate.GreetingType.MORNING,
    val userName: String = "",
    val summary: DashboardSummary = DashboardSummary.EMPTY,
    val upcoming: List<UpcomingPayment> = emptyList(),
    val overdue: List<UpcomingPayment> = emptyList(),
    val recent: List<LedgerEntry> = emptyList(),
    val trend: List<MonthlyTrend> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val hasAnyData: Boolean = false,
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    /**
     * Bumped whenever data changes in a way Room's Flows can't observe
     * (upcoming payments are computed, not queried), forcing a recompute.
     */
    private val refreshTrigger = MutableStateFlow(0)

    private val accountsFlow = container.accountRepository.observeAccounts()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val derivedFlow = combine(
        accountsFlow,
        refreshTrigger,
    ) { accounts, _ -> accounts }
        .flatMapLatest { accounts ->
            kotlinx.coroutines.flow.flow {
                val today = BanglaDate.today()
                val upcoming = container.dashboardUseCase.upcomingPayments(horizonDays = 45)
                val trend = container.dashboardUseCase.monthlyTrend(6)
                val insights = container.insightsUseCase.generate(accounts)
                emit(Derived(accounts, upcoming, trend, insights, today))
            }
        }

    private data class Derived(
        val accounts: List<Account>,
        val upcoming: List<UpcomingPayment>,
        val trend: List<MonthlyTrend>,
        val insights: List<Insight>,
        val today: java.time.LocalDate,
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        derivedFlow,
        container.ledgerRepository.observeRecent(6),
        container.preferences.settings,
        todayTotalsFlow(),
        monthTotalsFlow(),
    ) { derived, recent, settings, todayTotals, monthTotals ->
        val summary = container.dashboardUseCase.buildSummary(
            accounts = derived.accounts,
            upcoming = derived.upcoming,
            incomeToday = todayTotals.first,
            expenseToday = todayTotals.second,
            incomeMonth = monthTotals.first,
            expenseMonth = monthTotals.second,
            today = derived.today,
        )
        DashboardUiState(
            isLoading = false,
            greeting = BanglaDate.greeting(java.time.LocalTime.now().hour),
            userName = settings.userName,
            summary = summary,
            upcoming = derived.upcoming.filterNot { it.isOverdue }.take(5),
            overdue = derived.upcoming.filter { it.isOverdue }.take(5),
            recent = recent,
            trend = derived.trend,
            insights = derived.insights.take(3),
            settings = settings,
            hasAnyData = derived.accounts.isNotEmpty() || recent.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun todayTotalsFlow() = combine(
        container.moneyRepository.observeSum(true, BanglaDate.today(), BanglaDate.today()),
        container.moneyRepository.observeSum(false, BanglaDate.today(), BanglaDate.today()),
    ) { income, expense -> income to expense }

    private fun monthTotalsFlow(): kotlinx.coroutines.flow.Flow<Pair<com.shohan.bokeya.core.money.Money, com.shohan.bokeya.core.money.Money>> {
        val start = BanglaDate.startOfMonth(BanglaDate.today())
        val end = BanglaDate.endOfMonth(BanglaDate.today())
        return combine(
            container.moneyRepository.observeSum(true, start, end),
            container.moneyRepository.observeSum(false, start, end),
        ) { income, expense -> income to expense }
    }

    fun refresh() {
        refreshTrigger.value = refreshTrigger.value + 1
    }

    fun toggleAmountVisibility() {
        viewModelScope.launch {
            val current = container.preferences.current()
            container.preferences.setHideAmounts(!current.hideAmounts)
        }
    }
}
