package com.shohan.bokeya.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.UpcomingPayment
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `buildSummary` is pure aggregation over already-loaded lists, so every test
 * here feeds it fixtures directly — the arithmetic is the whole point. The
 * repositories are real but empty; only the suspend helpers touch them, and
 * those are covered by the repository tests.
 */
@RunWith(RobolectricTestRunner::class)
class DashboardUseCaseTest {

    private lateinit var database: BokeyaDatabase
    private lateinit var useCase: DashboardUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BokeyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        useCase = DashboardUseCase(
            accountRepository = AccountRepository(database),
            moneyRepository = MoneyRepository(database),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Sunday 15 March 2026; its week runs Sat 14 -> Fri 20. */
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private fun account(
        id: Long,
        type: AccountType = AccountType.SHOP,
        remaining: Money,
        status: AccountStatus = AccountStatus.ACTIVE,
        direction: DebtDirection? = null,
        isClosed: Boolean = false,
    ) = Account(
        id = id,
        type = type,
        name = "হিসাব $id",
        secondaryName = null,
        total = remaining,
        paid = Money.ZERO,
        remaining = remaining,
        status = status,
        progressPercent = 0,
        nextDueDate = null,
        endDate = null,
        startDate = today,
        isClosed = isClosed,
        installmentAmount = null,
        tenureCount = null,
        paidInstallments = 0,
        direction = direction,
        note = null,
        phone = null,
        daysUntilDue = null,
    )

    private fun due(dueDate: LocalDate, amount: Money) = UpcomingPayment(
        accountId = 1L,
        accountName = "হিসাব",
        accountType = AccountType.LOAN,
        amount = amount,
        dueDate = dueDate,
        daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate),
        isOverdue = dueDate.isBefore(today),
        installmentId = null,
    )

    private fun summaryOf(
        accounts: List<Account> = emptyList(),
        upcoming: List<UpcomingPayment> = emptyList(),
        incomeToday: Money = Money.ZERO,
        expenseToday: Money = Money.ZERO,
        incomeMonth: Money = Money.ZERO,
        expenseMonth: Money = Money.ZERO,
    ) = useCase.buildSummary(
        accounts = accounts,
        upcoming = upcoming,
        incomeToday = incomeToday,
        expenseToday = expenseToday,
        incomeMonth = incomeMonth,
        expenseMonth = expenseMonth,
        today = today,
    )

    @Test
    fun `total due sums every open payable account`() {
        val summary = summaryOf(
            accounts = listOf(
                account(1, AccountType.SHOP, Money.ofTaka(3_000)),
                account(2, AccountType.LOAN, Money.ofTaka(50_000)),
                account(3, AccountType.EMI, Money.ofTaka(20_000)),
            ),
        )
        assertEquals(Money.ofTaka(73_000), summary.totalDue)
        assertEquals(3, summary.activeAccountCount)
    }

    /** Money lent out is an asset — it must never inflate the "you owe" figure. */
    @Test
    fun `lent money is tracked separately from what is owed`() {
        val summary = summaryOf(
            accounts = listOf(
                account(1, AccountType.PERSON, Money.ofTaka(4_000), direction = DebtDirection.BORROWED),
                account(2, AccountType.PERSON, Money.ofTaka(9_000), direction = DebtDirection.LENT),
            ),
        )
        assertEquals(Money.ofTaka(4_000), summary.totalDue)
        assertEquals(Money.ofTaka(9_000), summary.totalReceivable)
    }

    @Test
    fun `settled and closed accounts drop out of the totals`() {
        val summary = summaryOf(
            accounts = listOf(
                account(1, remaining = Money.ofTaka(1_000)),
                account(2, remaining = Money.ZERO, status = AccountStatus.PAID),
                account(3, remaining = Money.ofTaka(5_000), isClosed = true),
            ),
        )
        assertEquals(Money.ofTaka(1_000), summary.totalDue)
        assertEquals(1, summary.activeAccountCount)
    }

    @Test
    fun `dues are broken down per account type`() {
        val summary = summaryOf(
            accounts = listOf(
                account(1, AccountType.SHOP, Money.ofTaka(2_000)),
                account(2, AccountType.SHOP, Money.ofTaka(1_000)),
                account(3, AccountType.LOAN, Money.ofTaka(40_000)),
            ),
        )
        assertEquals(Money.ofTaka(3_000), summary.dueByType[AccountType.SHOP])
        assertEquals(Money.ofTaka(40_000), summary.dueByType[AccountType.LOAN])
        // Types with nothing outstanding are omitted rather than shown as zero.
        assertFalse(summary.dueByType.containsKey(AccountType.EMI))
    }

    @Test
    fun `due buckets nest today inside week inside month`() {
        val summary = summaryOf(
            upcoming = listOf(
                due(today, Money.ofTaka(1_000)),
                due(today.plusDays(3), Money.ofTaka(2_000)),   // still this week (Fri 20)
                due(today.plusDays(10), Money.ofTaka(4_000)),  // this month
                due(LocalDate.of(2026, 4, 5), Money.ofTaka(8_000)), // next month
            ),
        )
        assertEquals(Money.ofTaka(1_000), summary.dueToday)
        assertEquals(Money.ofTaka(3_000), summary.dueThisWeek)
        assertEquals(Money.ofTaka(7_000), summary.dueThisMonth)
    }

    /** An overdue payment is still owed today, so it belongs in every bucket. */
    @Test
    fun `overdue payments count towards today`() {
        val summary = summaryOf(
            upcoming = listOf(
                due(today.minusDays(5), Money.ofTaka(1_500)),
                due(today, Money.ofTaka(500)),
            ),
        )
        assertEquals(Money.ofTaka(2_000), summary.dueToday)
        assertEquals(Money.ofTaka(2_000), summary.dueThisMonth)
    }

    @Test
    fun `overdue accounts are counted and totalled`() {
        val summary = summaryOf(
            accounts = listOf(
                account(1, remaining = Money.ofTaka(1_200), status = AccountStatus.OVERDUE),
                account(2, remaining = Money.ofTaka(800), status = AccountStatus.OVERDUE),
                account(3, remaining = Money.ofTaka(5_000), status = AccountStatus.ACTIVE),
            ),
        )
        assertEquals(2, summary.overdueCount)
        assertEquals(Money.ofTaka(2_000), summary.overdueAmount)
    }

    @Test
    fun `net figures subtract expense from income`() {
        val summary = summaryOf(
            incomeToday = Money.ofTaka(2_000),
            expenseToday = Money.ofTaka(750),
            incomeMonth = Money.ofTaka(45_000),
            expenseMonth = Money.ofTaka(38_000),
        )
        assertEquals(Money.ofTaka(1_250), summary.netToday)
        assertEquals(Money.ofTaka(7_000), summary.netThisMonth)
    }

    @Test
    fun `net can be negative when spending outruns income`() {
        val summary = summaryOf(
            incomeMonth = Money.ofTaka(10_000),
            expenseMonth = Money.ofTaka(14_500),
        )
        assertTrue(summary.netThisMonth.isNegative)
        assertEquals(Money.ofTaka(-4_500).minor, summary.netThisMonth.minor)
    }

    @Test
    fun `an empty dashboard is all zeroes`() {
        val summary = summaryOf()
        assertEquals(Money.ZERO, summary.totalDue)
        assertEquals(Money.ZERO, summary.dueThisMonth)
        assertEquals(0, summary.activeAccountCount)
        assertFalse(summary.hasAnyDue)
    }

    @Test
    fun `hasAnyDue covers both directions`() {
        assertTrue(
            summaryOf(accounts = listOf(account(1, remaining = Money.ofTaka(10)))).hasAnyDue,
        )
        assertTrue(
            summaryOf(
                accounts = listOf(
                    account(1, remaining = Money.ofTaka(10), direction = DebtDirection.LENT),
                ),
            ).hasAnyDue,
        )
    }
}
