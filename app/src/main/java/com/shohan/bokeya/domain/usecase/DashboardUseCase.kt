package com.shohan.bokeya.domain.usecase

import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DashboardSummary
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.UpcomingPayment
import java.time.LocalDate

/**
 * Turns raw accounts and entries into the numbers the dashboard shows.
 *
 * Deliberately pure and synchronous: the caller supplies already-loaded lists,
 * which keeps this exhaustively unit-testable and free of dispatcher concerns.
 */
class DashboardUseCase(
    private val accountRepository: AccountRepository,
    private val moneyRepository: MoneyRepository,
) {

    /**
     * @param accounts every account (open and closed)
     * @param upcoming pre-computed due payments used for the today/week/month buckets
     */
    fun buildSummary(
        accounts: List<Account>,
        upcoming: List<UpcomingPayment>,
        incomeToday: Money,
        expenseToday: Money,
        incomeMonth: Money,
        expenseMonth: Money,
        today: LocalDate = BanglaDate.today(),
    ): DashboardSummary {
        val open = accounts.filter { !it.isClosed && it.remaining.isPositive }

        // Money the user owes vs money owed to the user are never mixed.
        val payable = open.filter { it.direction != DebtDirection.LENT }
        val receivable = open.filter { it.direction == DebtDirection.LENT }

        val dueByType = AccountType.entries.associateWith { type ->
            Money(payable.filter { it.type == type }.sumOf { it.remaining.minor })
        }.filterValues { it.isPositive }

        val weekEnd = BanglaDate.endOfWeek(today)
        val monthEnd = BanglaDate.endOfMonth(today)

        // Overdue amounts are included in every bucket: they are still owed now.
        val dueToday = upcoming.filter { !it.dueDate.isAfter(today) }
        val dueWeek = upcoming.filter { !it.dueDate.isAfter(weekEnd) }
        val dueMonth = upcoming.filter { !it.dueDate.isAfter(monthEnd) }

        val overdue = payable.filter { it.status == AccountStatus.OVERDUE }

        return DashboardSummary(
            totalDue = Money(payable.sumOf { it.remaining.minor }),
            totalReceivable = Money(receivable.sumOf { it.remaining.minor }),
            dueByType = dueByType,
            dueToday = Money(dueToday.sumOf { it.amount.minor }),
            dueThisWeek = Money(dueWeek.sumOf { it.amount.minor }),
            dueThisMonth = Money(dueMonth.sumOf { it.amount.minor }),
            incomeToday = incomeToday,
            expenseToday = expenseToday,
            incomeThisMonth = incomeMonth,
            expenseThisMonth = expenseMonth,
            overdueCount = overdue.size,
            overdueAmount = Money(overdue.sumOf { it.remaining.minor }),
            activeAccountCount = open.size,
        )
    }

    suspend fun upcomingPayments(horizonDays: Long = 30, limit: Int = 50): List<UpcomingPayment> =
        accountRepository.getUpcomingPayments(horizonDays = horizonDays, limit = limit)

    suspend fun monthlyTrend(months: Int = 6) = moneyRepository.getMonthlyTrend(months)
}
