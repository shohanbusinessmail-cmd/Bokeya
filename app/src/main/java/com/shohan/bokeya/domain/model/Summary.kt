package com.shohan.bokeya.domain.model

import com.shohan.bokeya.core.money.Money
import java.time.LocalDate

/** Everything the dashboard header needs, computed in one pass over the data. */
data class DashboardSummary(
    val totalDue: Money,
    val totalReceivable: Money,
    val dueByType: Map<AccountType, Money>,
    val dueToday: Money,
    val dueThisWeek: Money,
    val dueThisMonth: Money,
    val incomeToday: Money,
    val expenseToday: Money,
    val incomeThisMonth: Money,
    val expenseThisMonth: Money,
    val overdueCount: Int,
    val overdueAmount: Money,
    val activeAccountCount: Int,
) {
    val netToday: Money get() = incomeToday - expenseToday
    val netThisMonth: Money get() = incomeThisMonth - expenseThisMonth
    val hasAnyDue: Boolean get() = totalDue.isPositive || totalReceivable.isPositive

    companion object {
        val EMPTY = DashboardSummary(
            totalDue = Money.ZERO,
            totalReceivable = Money.ZERO,
            dueByType = emptyMap(),
            dueToday = Money.ZERO,
            dueThisWeek = Money.ZERO,
            dueThisMonth = Money.ZERO,
            incomeToday = Money.ZERO,
            expenseToday = Money.ZERO,
            incomeThisMonth = Money.ZERO,
            expenseThisMonth = Money.ZERO,
            overdueCount = 0,
            overdueAmount = Money.ZERO,
            activeAccountCount = 0,
        )
    }
}

/** One bar in the six-month income/expense chart. */
data class MonthlyTrend(
    val year: Int,
    val month: Int,
    val income: Money,
    val expense: Money,
) {
    val net: Money get() = income - expense
}

data class CategorySpend(
    val categoryId: Long?,
    val name: String,
    val color: Int?,
    val iconKey: String?,
    val amount: Money,
    val entryCount: Int,
    val sharePercent: Int,
)

/** A locally-computed observation shown in the Insights section. */
data class Insight(
    val id: String,
    val text: String,
    val tone: InsightTone,
    val iconKey: String,
)

enum class InsightTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
}

/** Financial activity on a single calendar day. */
data class DayActivity(
    val date: LocalDate,
    val dueAmount: Money,
    val paidAmount: Money,
    val income: Money,
    val expense: Money,
    val dueCount: Int,
    val hasOverdue: Boolean,
) {
    val hasAnything: Boolean
        get() = dueAmount.isPositive || paidAmount.isPositive || income.isPositive || expense.isPositive
}

/** Full detail for the selected calendar day. */
data class DayDetail(
    val date: LocalDate,
    val duePayments: List<UpcomingPayment>,
    val payments: List<LedgerEntry>,
    val incomes: List<LedgerEntry>,
    val expenses: List<LedgerEntry>,
) {
    val isEmpty: Boolean
        get() = duePayments.isEmpty() && payments.isEmpty() && incomes.isEmpty() && expenses.isEmpty()
}
