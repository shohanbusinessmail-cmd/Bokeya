package com.shohan.bokeya.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DashboardSummary
import com.shohan.bokeya.domain.model.DebtDirection
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Walks the exact scenario from the bug report through the real Room database,
 * the real repository and the real use case — no fixtures.
 *
 * This proves the whole pipeline agrees: what the database stores, what
 * `observeAccounts()` emits, and what the hero card and বকেয়ার ভাগ donut read.
 */
@RunWith(RobolectricTestRunner::class)
class DashboardEndToEndTest {

    private lateinit var database: BokeyaDatabase
    private lateinit var accounts: AccountRepository
    private lateinit var useCase: DashboardUseCase

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BokeyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = AccountRepository(database)
        useCase = DashboardUseCase(accounts, MoneyRepository(database))
    }

    @After
    fun tearDown() = database.close()

    /** Recomputes the summary the same way DashboardViewModel does. */
    private suspend fun summary(): DashboardSummary = useCase.buildSummary(
        accounts = accounts.observeAccounts().first(),
        upcoming = emptyList(),
        incomeToday = Money.ZERO,
        expenseToday = Money.ZERO,
        incomeMonth = Money.ZERO,
        expenseMonth = Money.ZERO,
        today = today,
    )

    private fun assertHeroMatchesBreakdown(summary: DashboardSummary) {
        assertEquals(
            "hero total must equal the বকেয়ার ভাগ breakdown",
            summary.totalDue,
            Money(summary.dueByType.values.sumOf { it.minor }),
        )
    }

    @Test
    fun `five thousand personal debt plus a twelve taka shop tab`() = runTest {
        val personId = accounts.createPersonAccount(
            personName = "করিম",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(5_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = null,
        )
        accounts.createShopAccount(
            name = "রহিমের দোকান",
            items = listOf(
                AccountRepository.ItemInput(
                    name = "চাল",
                    quantity = null,
                    unit = null,
                    unitPrice = null,
                    total = Money.ofTaka(12),
                ),
            ),
            purchaseDate = today,
        )

        // মোট বকেয়া = ৫,০১২, and the two buckets must add up to exactly that.
        summary().let {
            assertEquals(Money.ofTaka(5_012), it.totalDue)
            assertEquals(Money.ofTaka(5_000), it.dueByType[AccountType.PERSON])
            assertEquals(Money.ofTaka(12), it.dueByType[AccountType.SHOP])
            assertHeroMatchesBreakdown(it)
        }

        // Pay ২,০০০ towards the personal debt → ৩,০১২.
        accounts.recordPayment(personId, Money.ofTaka(2_000), today)
        summary().let {
            assertEquals(Money.ofTaka(3_000), it.dueByType[AccountType.PERSON])
            assertEquals(Money.ofTaka(12), it.dueByType[AccountType.SHOP])
            assertEquals(Money.ofTaka(3_012), it.totalDue)
            assertHeroMatchesBreakdown(it)
        }

        // Clear the remaining ৩,০০০ → only the shop's ১২ is left.
        accounts.recordPayment(personId, Money.ofTaka(3_000), today)
        summary().let {
            assertNull(it.dueByType[AccountType.PERSON])
            assertEquals(Money.ofTaka(12), it.dueByType[AccountType.SHOP])
            assertEquals(Money.ofTaka(12), it.totalDue)
            assertHeroMatchesBreakdown(it)
        }
    }

    @Test
    fun `receivables stay out of the payable total end to end`() = runTest {
        accounts.createPersonAccount(
            personName = "করিম",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(5_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = null,
        )
        accounts.createPersonAccount(
            personName = "রহিম",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(8_000),
            direction = DebtDirection.LENT,
            borrowDate = today,
            returnDate = null,
        )

        summary().let {
            assertEquals(Money.ofTaka(5_000), it.totalDue)
            assertEquals(Money.ofTaka(8_000), it.totalReceivable)
            assertEquals(Money.ofTaka(5_000), it.dueByType[AccountType.PERSON])
            assertHeroMatchesBreakdown(it)
        }
    }

    @Test
    fun `an empty database reports zero, not a blank amount`() = runTest {
        summary().let {
            assertEquals(Money.ZERO, it.totalDue)
            assertEquals("৳ ০", com.shohan.bokeya.core.money.MoneyFormatter.format(it.totalDue))
            assertHeroMatchesBreakdown(it)
        }
    }

    @Test
    fun `deleting an account drops its balance from the total`() = runTest {
        val shopId = accounts.createShopAccount(
            name = "দোকান",
            items = listOf(
                AccountRepository.ItemInput(
                    name = "তেল",
                    quantity = null,
                    unit = null,
                    unitPrice = null,
                    total = Money.ofTaka(1_200),
                ),
            ),
            purchaseDate = today,
        )
        accounts.createPersonAccount(
            personName = "করিম",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(5_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = null,
        )
        assertEquals(Money.ofTaka(6_200), summary().totalDue)

        accounts.deleteAccount(shopId)
        summary().let {
            assertEquals(Money.ofTaka(5_000), it.totalDue)
            assertNull(it.dueByType[AccountType.SHOP])
            assertHeroMatchesBreakdown(it)
        }
    }

    @Test
    fun `overpaying one account cannot reduce the overall total`() = runTest {
        val shopId = accounts.createShopAccount(
            name = "দোকান",
            items = listOf(
                AccountRepository.ItemInput(
                    name = "ডাল",
                    quantity = null,
                    unit = null,
                    unitPrice = null,
                    total = Money.ofTaka(1_000),
                ),
            ),
            purchaseDate = today,
        )
        accounts.createPersonAccount(
            personName = "করিম",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(5_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = null,
        )

        // Far more than the shop tab is worth.
        accounts.recordPayment(shopId, Money.ofTaka(9_999), today)

        summary().let {
            // The shop settles at zero; the personal debt is untouched.
            assertNull(it.dueByType[AccountType.SHOP])
            assertEquals(Money.ofTaka(5_000), it.totalDue)
            assertHeroMatchesBreakdown(it)
        }
    }
}
