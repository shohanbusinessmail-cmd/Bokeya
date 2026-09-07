package com.shohan.bokeya.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.calc.BalanceCalculator
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.UpcomingPayment
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Locks down "মোট বকেয়া".
 *
 * The hero card and the বকেয়ার ভাগ donut both read
 * [com.shohan.bokeya.domain.model.DashboardSummary], so the numbers can only
 * ever agree if this one calculation is right. The invariant at the bottom of
 * this file is the guard rail: hero total == sum of the per-category totals.
 */
@RunWith(RobolectricTestRunner::class)
class DashboardTotalsTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private lateinit var database: BokeyaDatabase
    private lateinit var useCase: DashboardUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BokeyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // buildSummary is pure aggregation over its arguments; the repositories
        // are real but stay empty.
        useCase = DashboardUseCase(
            accountRepository = AccountRepository(database),
            moneyRepository = MoneyRepository(database),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ----------------------------------------------------------- one category

    @Test
    fun `shop only`() {
        val summary = summaryOf(account(AccountType.SHOP, total = 2_000, paid = 0))
        assertEquals(taka(2_000), summary.totalDue)
        assertEquals(taka(2_000), summary.dueByType[AccountType.SHOP])
    }

    @Test
    fun `personal debt only`() {
        val summary = summaryOf(
            account(AccountType.PERSONAL, total = 5_000, paid = 0, direction = DebtDirection.BORROWED),
        )
        assertEquals(taka(5_000), summary.totalDue)
        assertEquals(taka(5_000), summary.dueByType[AccountType.PERSONAL])
    }

    @Test
    fun `loan only`() {
        val summary = summaryOf(account(AccountType.LOAN, total = 20_000, paid = 0))
        assertEquals(taka(20_000), summary.totalDue)
    }

    @Test
    fun `emi only`() {
        val summary = summaryOf(account(AccountType.EMI, total = 8_000, paid = 0))
        assertEquals(taka(8_000), summary.totalDue)
    }

    // ------------------------------------------------- the brief's scenarios

    @Test
    fun `personal debt plus a small shop tab`() {
        // The exact case from the bug report: ৫,০০০ ধার + ১২ দোকান = ৫,০১২.
        val summary = summaryOf(
            account(AccountType.PERSONAL, total = 5_000, paid = 0, direction = DebtDirection.BORROWED),
            account(AccountType.SHOP, total = 12, paid = 0),
        )
        assertEquals(taka(5_012), summary.totalDue)
        assertEquals(taka(5_000), summary.dueByType[AccountType.PERSONAL])
        assertEquals(taka(12), summary.dueByType[AccountType.SHOP])
        assertInvariant(summary)
    }

    @Test
    fun `all four categories combined`() {
        val summary = summaryOf(
            account(AccountType.SHOP, total = 1_200, paid = 0),
            account(AccountType.SHOP, total = 800, paid = 0),
            account(AccountType.PERSONAL, total = 5_000, paid = 0, direction = DebtDirection.BORROWED),
            account(AccountType.LOAN, total = 20_000, paid = 0),
            account(AccountType.EMI, total = 8_000, paid = 0),
        )

        assertEquals(taka(2_000), summary.dueByType[AccountType.SHOP])
        assertEquals(taka(5_000), summary.dueByType[AccountType.PERSONAL])
        assertEquals(taka(20_000), summary.dueByType[AccountType.LOAN])
        assertEquals(taka(8_000), summary.dueByType[AccountType.EMI])
        assertEquals(taka(35_000), summary.totalDue)
        assertInvariant(summary)
    }

    @Test
    fun `partial payments walk the total down step by step`() {
        // ৩৫,০০০ → pay ৫০০ off a shop → ৩৪,৫০০
        var summary = summaryOf(
            account(AccountType.SHOP, total = 1_200, paid = 500),
            account(AccountType.SHOP, total = 800, paid = 0),
            account(AccountType.PERSONAL, total = 5_000, paid = 0, direction = DebtDirection.BORROWED),
            account(AccountType.LOAN, total = 20_000, paid = 0),
            account(AccountType.EMI, total = 8_000, paid = 0),
        )
        assertEquals(taka(1_500), summary.dueByType[AccountType.SHOP])
        assertEquals(taka(34_500), summary.totalDue)
        assertInvariant(summary)

        // …then clear the personal debt entirely → ২৯,৫০০
        summary = summaryOf(
            account(AccountType.SHOP, total = 1_200, paid = 500),
            account(AccountType.SHOP, total = 800, paid = 0),
            account(AccountType.PERSONAL, total = 5_000, paid = 5_000, direction = DebtDirection.BORROWED),
            account(AccountType.LOAN, total = 20_000, paid = 0),
            account(AccountType.EMI, total = 8_000, paid = 0),
        )
        assertEquals(taka(29_500), summary.totalDue)
        // A settled account drops out of the breakdown rather than showing ০.
        assertTrue(summary.dueByType[AccountType.PERSONAL] == null)
        assertInvariant(summary)

        // …then ১০,০০০ off the loan → ১৯,৫০০
        summary = summaryOf(
            account(AccountType.SHOP, total = 1_200, paid = 500),
            account(AccountType.SHOP, total = 800, paid = 0),
            account(AccountType.PERSONAL, total = 5_000, paid = 5_000, direction = DebtDirection.BORROWED),
            account(AccountType.LOAN, total = 20_000, paid = 10_000),
            account(AccountType.EMI, total = 8_000, paid = 0),
        )
        assertEquals(taka(10_000), summary.dueByType[AccountType.LOAN])
        assertEquals(taka(19_500), summary.totalDue)
        assertInvariant(summary)
    }

    @Test
    fun `paying a shop tab down to zero removes it from the total`() {
        val start = summaryOf(account(AccountType.SHOP, total = 5_000, paid = 0))
        assertEquals(taka(5_000), start.totalDue)

        val partial = summaryOf(account(AccountType.SHOP, total = 5_000, paid = 2_000))
        assertEquals(taka(3_000), partial.totalDue)

        val settled = summaryOf(account(AccountType.SHOP, total = 5_000, paid = 5_000))
        assertEquals(Money.ZERO, settled.totalDue)
        assertEquals(0, settled.activeAccountCount)
    }

    // -------------------------------------------------------- what is excluded

    @Test
    fun `money owed to the user never touches the payable total`() {
        val summary = summaryOf(
            account(AccountType.SHOP, total = 1_000, paid = 0),
            account(AccountType.PERSONAL, total = 5_000, paid = 0, direction = DebtDirection.BORROWED),
            account(AccountType.LOAN, total = 20_000, paid = 0),
            account(AccountType.EMI, total = 10_000, paid = 0),
            // Rahim owes the user ৮,০০০ — receivable, not payable.
            account(AccountType.PERSONAL, total = 8_000, paid = 0, direction = DebtDirection.LENT),
        )

        assertEquals(taka(36_000), summary.totalDue)
        assertEquals(taka(8_000), summary.totalReceivable)
        // The receivable must not have inflated the ধার bucket either.
        assertEquals(taka(5_000), summary.dueByType[AccountType.PERSONAL])
        assertInvariant(summary)
    }

    @Test
    fun `closed and fully paid accounts are excluded`() {
        val summary = summaryOf(
            account(AccountType.SHOP, total = 1_000, paid = 0),
            account(AccountType.SHOP, total = 4_000, paid = 4_000),
            account(AccountType.LOAN, total = 9_000, paid = 0, closed = true),
        )
        assertEquals(taka(1_000), summary.totalDue)
        assertEquals(1, summary.activeAccountCount)
        assertInvariant(summary)
    }

    @Test
    fun `an empty database totals zero rather than failing`() {
        val summary = summaryOf()
        assertEquals(Money.ZERO, summary.totalDue)
        assertEquals(Money.ZERO, summary.totalReceivable)
        assertTrue(summary.dueByType.isEmpty())
        assertInvariant(summary)
    }

    @Test
    fun `deleting an account removes exactly its remaining balance`() {
        val before = summaryOf(
            account(AccountType.SHOP, total = 1_200, paid = 200),
            account(AccountType.LOAN, total = 20_000, paid = 0),
        )
        val after = summaryOf(account(AccountType.LOAN, total = 20_000, paid = 0))
        assertEquals(taka(21_000), before.totalDue)
        assertEquals(taka(20_000), after.totalDue)
    }

    @Test
    fun `editing an account total is reflected immediately`() {
        val before = summaryOf(account(AccountType.SHOP, total = 1_000, paid = 250))
        val after = summaryOf(account(AccountType.SHOP, total = 3_000, paid = 250))
        assertEquals(taka(750), before.totalDue)
        assertEquals(taka(2_750), after.totalDue)
    }

    @Test
    fun `overpayment can never push a balance negative`() {
        // BalanceCalculator floors remaining at zero, so an over-applied
        // payment cannot subtract from other accounts' debt.
        val summary = summaryOf(
            account(AccountType.SHOP, total = 1_000, paid = 5_000),
            account(AccountType.LOAN, total = 20_000, paid = 0),
        )
        assertEquals(taka(20_000), summary.totalDue)
        assertTrue(summary.totalDue.minor >= 0)
    }

    // ------------------------------------------------- period buckets are real

    @Test
    fun `today week and month buckets track schedules not the whole debt`() {
        val accounts = listOf(
            account(AccountType.LOAN, total = 20_000, paid = 0),
            account(AccountType.EMI, total = 8_000, paid = 0),
        )
        val upcoming = listOf(
            upcoming(amount = 2_000, dueDate = today),
            upcoming(amount = 1_500, dueDate = today.plusDays(2)),
            upcoming(amount = 3_000, dueDate = today.plusDays(20)),
        )

        val summary = useCase.buildSummary(
            accounts = accounts,
            upcoming = upcoming,
            incomeToday = Money.ZERO,
            expenseToday = Money.ZERO,
            incomeMonth = Money.ZERO,
            expenseMonth = Money.ZERO,
            today = today,
        )

        // Instalments due, not the ২৮,০০০ of outstanding principal.
        assertEquals(taka(2_000), summary.dueToday)
        assertTrue(summary.dueToday.minor < summary.totalDue.minor)
        assertTrue(summary.dueThisWeek.minor >= summary.dueToday.minor)
        assertTrue(summary.dueThisMonth.minor >= summary.dueThisWeek.minor)
        assertEquals(taka(28_000), summary.totalDue)
    }

    // ------------------------------------------------------------- invariant

    @Test
    fun `hero total always equals the sum of the category totals`() {
        // Every shape of data we can think of, checked against the one rule the
        // dashboard must never break.
        val cases = listOf(
            emptyList(),
            listOf(account(AccountType.SHOP, total = 12, paid = 0)),
            listOf(
                account(AccountType.PERSONAL, total = 5_000, paid = 0, direction = DebtDirection.BORROWED),
                account(AccountType.SHOP, total = 12, paid = 0),
            ),
            listOf(
                account(AccountType.SHOP, total = 1_200, paid = 500),
                account(AccountType.SHOP, total = 800, paid = 800),
                account(AccountType.PERSONAL, total = 5_000, paid = 2_000, direction = DebtDirection.BORROWED),
                account(AccountType.PERSONAL, total = 8_000, paid = 0, direction = DebtDirection.LENT),
                account(AccountType.LOAN, total = 20_000, paid = 19_999),
                account(AccountType.EMI, total = 8_000, paid = 0, closed = true),
                account(AccountType.EMI, total = 1_00_00_000, paid = 0),
            ),
        )

        cases.forEach { accounts -> assertInvariant(summaryOf(*accounts.toTypedArray())) }
    }

    // ----------------------------------------------------------------- helpers

    private fun assertInvariant(summary: com.shohan.bokeya.domain.model.DashboardSummary) {
        val breakdown = Money(summary.dueByType.values.sumOf { it.minor })
        assertEquals(
            "hero total must equal the বকেয়ার ভাগ breakdown",
            summary.totalDue,
            breakdown,
        )
    }

    private fun summaryOf(vararg accounts: Account) = useCase.buildSummary(
        accounts = accounts.toList(),
        upcoming = emptyList(),
        incomeToday = Money.ZERO,
        expenseToday = Money.ZERO,
        incomeMonth = Money.ZERO,
        expenseMonth = Money.ZERO,
        today = today,
    )

    private fun taka(value: Long) = Money.ofTaka(value)

    private var nextId = 1L

    /** Builds an account the way the repository does: remaining is derived. */
    private fun account(
        type: AccountType,
        total: Long,
        paid: Long,
        direction: DebtDirection? = null,
        closed: Boolean = false,
        dueDate: LocalDate? = null,
    ): Account {
        val totalMoney = Money.ofTaka(total)
        val paidMoney = Money.ofTaka(paid)
        val remaining = BalanceCalculator.remaining(totalMoney, paidMoney)
        val status = BalanceCalculator.status(
            total = totalMoney,
            paid = paidMoney,
            dueDate = dueDate,
            today = today,
            isClosed = closed,
        )
        return Account(
            id = nextId++,
            type = type,
            name = "${type.name} $nextId",
            secondaryName = null,
            total = totalMoney,
            paid = paidMoney,
            remaining = remaining,
            status = status,
            progressPercent = BalanceCalculator.progressPercent(totalMoney, paidMoney),
            nextDueDate = dueDate,
            endDate = null,
            startDate = today.minusDays(30),
            isClosed = closed,
            installmentAmount = null,
            tenureCount = null,
            paidInstallments = 0,
            direction = direction,
            note = null,
            phone = null,
            daysUntilDue = dueDate?.toEpochDay()?.minus(today.toEpochDay()),
        )
    }

    private fun upcoming(amount: Long, dueDate: LocalDate) = UpcomingPayment(
        accountId = nextId++,
        accountName = "due",
        accountType = AccountType.LOAN,
        amount = Money.ofTaka(amount),
        dueDate = dueDate,
        daysUntil = dueDate.toEpochDay() - today.toEpochDay(),
        isOverdue = dueDate.isBefore(today),
        installmentId = null,
    )

}
