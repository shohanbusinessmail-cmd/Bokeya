package com.shohan.bokeya.domain.usecase

import android.content.Context
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.Insight
import com.shohan.bokeya.domain.model.InsightTone
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Generates the "বিশ্লেষণ" observations.
 *
 * Everything is computed from locally stored rows — no external AI service, no
 * network. Insights are ordered by usefulness (things needing action first) and
 * capped, because a wall of statistics is noise rather than insight.
 */
class InsightsUseCase(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val moneyRepository: MoneyRepository,
) {

    suspend fun generate(
        accounts: List<Account>,
        useBengaliDigits: Boolean = true,
        today: LocalDate = BanglaDate.today(),
        limit: Int = 6,
    ): List<Insight> {
        val insights = mutableListOf<Insight>()

        val monthStart = BanglaDate.startOfMonth(today)
        val monthEnd = BanglaDate.endOfMonth(today)
        val prevMonth = today.minusMonths(1)
        val prevStart = BanglaDate.startOfMonth(prevMonth)
        val prevEnd = BanglaDate.endOfMonth(prevMonth)

        val expenseThis = moneyRepository.sum(false, monthStart, monthEnd)
        val expensePrev = moneyRepository.sum(false, prevStart, prevEnd)
        val incomeThis = moneyRepository.sum(true, monthStart, monthEnd)

        fun money(value: Money) = MoneyFormatter.format(value, useBengaliDigits)
        fun number(value: Int) = MoneyFormatter.formatNumber(value, useBengaliDigits)

        // 1. Overdue accounts — the most actionable thing on the screen.
        val overdue = accounts.filter {
            it.status == AccountStatus.OVERDUE && it.direction != DebtDirection.LENT
        }
        if (overdue.isNotEmpty()) {
            insights += Insight(
                id = "overdue",
                text = context.getString(R.string.insight_overdue_count, number(overdue.size)),
                tone = InsightTone.CRITICAL,
                iconKey = "warning",
            )
        }

        // 2. What is owed over the coming week.
        val upcoming = accountRepository.getUpcomingPayments(today = today, horizonDays = 7)
        val nextSeven = Money(upcoming.sumOf { it.amount.minor })
        if (nextSeven.isPositive) {
            insights += Insight(
                id = "next7",
                text = context.getString(R.string.insight_next_seven, money(nextSeven)),
                tone = InsightTone.NEUTRAL,
                iconKey = "calendar",
            )
        }

        // 3. Month-over-month spending change (needs a real baseline to be meaningful).
        if (expensePrev.isPositive && expenseThis.isPositive) {
            val delta = expenseThis.minor - expensePrev.minor
            val percent = (abs(delta).toDouble() / expensePrev.minor * 100).roundToInt()
            if (percent >= 5) {
                insights += Insight(
                    id = "expense_trend",
                    text = if (delta > 0) {
                        context.getString(R.string.insight_expense_up, number(percent))
                    } else {
                        context.getString(R.string.insight_expense_down, number(percent))
                    },
                    tone = if (delta > 0) InsightTone.WARNING else InsightTone.POSITIVE,
                    iconKey = if (delta > 0) "trending_up" else "trending_down",
                )
            }
        }

        // 4. Biggest expense category this month.
        val breakdown = moneyRepository.getCategoryBreakdown(false, monthStart, monthEnd)
        breakdown.firstOrNull()?.let { top ->
            if (top.amount.isPositive) {
                insights += Insight(
                    id = "top_category",
                    text = context.getString(R.string.insight_top_category, top.name, money(top.amount)),
                    tone = InsightTone.NEUTRAL,
                    iconKey = "pie_chart",
                )
            }
        }

        // 5. How much of this month's income is left.
        if (incomeThis.isPositive) {
            val left = incomeThis - expenseThis
            insights += Insight(
                id = "month_left",
                text = if (left.isNegative) {
                    context.getString(R.string.insight_month_over, money(left.abs()))
                } else {
                    context.getString(R.string.insight_month_left, money(left))
                },
                tone = if (left.isNegative) InsightTone.WARNING else InsightTone.POSITIVE,
                iconKey = "savings",
            )
        }

        // 6. Composition of total debt.
        val payable = accounts.filter {
            !it.isClosed && it.remaining.isPositive && it.direction != DebtDirection.LENT
        }
        val totalDue = Money(payable.sumOf { it.remaining.minor })
        if (totalDue.isPositive) {
            val byType = payable.groupBy { it.type }
                .mapValues { (_, list) -> list.sumOf { it.remaining.minor } }
            val (topType, topAmount) = byType.maxByOrNull { it.value }!!
            val share = (topAmount.toDouble() / totalDue.minor * 100).roundToInt()
            if (share >= 20) {
                insights += Insight(
                    id = "debt_share",
                    text = context.getString(
                        R.string.insight_debt_share,
                        number(share),
                        context.getString(topType.labelRes()),
                    ),
                    tone = InsightTone.NEUTRAL,
                    iconKey = "donut",
                )
            }

            payable.maxByOrNull { it.remaining.minor }?.let { biggest ->
                insights += Insight(
                    id = "biggest_due",
                    text = context.getString(
                        R.string.insight_biggest_due,
                        biggest.name,
                        money(biggest.remaining),
                    ),
                    tone = InsightTone.NEUTRAL,
                    iconKey = "account",
                )
            }
        } else if (accounts.isNotEmpty()) {
            insights += Insight(
                id = "debt_free",
                text = context.getString(R.string.insight_debt_free),
                tone = InsightTone.POSITIVE,
                iconKey = "celebration",
            )
        }

        // 7. Average daily spend so far this month.
        if (expenseThis.isPositive && today.dayOfMonth > 3) {
            val perDay = Money(expenseThis.minor / today.dayOfMonth)
            insights += Insight(
                id = "daily_avg",
                text = context.getString(R.string.insight_daily_avg, money(perDay)),
                tone = InsightTone.NEUTRAL,
                iconKey = "today",
            )
        }

        return insights.take(limit)
    }
}

/** Bangla label for an account type. */
fun AccountType.labelRes(): Int = when (this) {
    AccountType.SHOP -> R.string.type_shop_short
    AccountType.LOAN -> R.string.type_loan_short
    AccountType.EMI -> R.string.type_emi_short
    AccountType.PERSON -> R.string.type_person_short
}
