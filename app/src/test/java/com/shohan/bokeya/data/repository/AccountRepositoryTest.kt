package com.shohan.bokeya.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.domain.model.PaymentMethod
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository tests against a real in-memory Room database.
 *
 * Mocking the DAOs would only prove the mocks agree with themselves; the parts
 * that actually break are the SQL aggregates and the transaction boundaries, so
 * these exercise real Room.
 */
@RunWith(RobolectricTestRunner::class)
class AccountRepositoryTest {

    private lateinit var database: BokeyaDatabase
    private lateinit var repository: AccountRepository

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // SQLite needs foreign keys switched on per connection, exactly as the
        // production builder does — otherwise cascade deletes silently no-op
        // and these tests would pass against a database that does not.
        database = Room.inMemoryDatabaseBuilder(context, BokeyaDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
        repository = AccountRepository(database, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -------------------------------------------------------------- shop

    @Test
    fun `shop account total is the sum of its items`() = runTest {
        val id = repository.createShopAccount(
            name = "রহিম স্টোর",
            items = listOf(
                AccountRepository.ItemInput("চাল", 5.0, ItemUnit.KG, Money.ofTaka(60), Money.ofTaka(300)),
                AccountRepository.ItemInput("তেল", 2.0, ItemUnit.LITRE, Money.ofTaka(180), Money.ofTaka(360)),
            ),
            purchaseDate = today,
        )

        val account = repository.getAccount(id)
        assertNotNull(account)
        assertEquals(Money.ofTaka(660), account!!.total)
        assertEquals(Money.ZERO, account.paid)
        assertEquals(Money.ofTaka(660), account.remaining)
        assertEquals(AccountType.SHOP, account.type)
    }

    @Test
    fun `adding items raises the total`() = runTest {
        val id = repository.createShopAccount(
            name = "করিম স্টোর",
            items = listOf(
                AccountRepository.ItemInput("ডাল", null, null, null, Money.ofTaka(200)),
            ),
            purchaseDate = today,
        )

        repository.addItemsToAccount(
            accountId = id,
            items = listOf(AccountRepository.ItemInput("চিনি", null, null, null, Money.ofTaka(150))),
            purchaseDate = today,
        )

        assertEquals(Money.ofTaka(350), repository.getAccount(id)!!.total)
    }

    @Test
    fun `deleting an item lowers the total`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(
                AccountRepository.ItemInput("ক", null, null, null, Money.ofTaka(100)),
                AccountRepository.ItemInput("খ", null, null, null, Money.ofTaka(250)),
            ),
            purchaseDate = today,
        )

        val detail = repository.observeAccountDetail(id).first()!!
        repository.deleteItem(id, detail.items.first().id)

        assertEquals(Money.ofTaka(250), repository.getAccount(id)!!.total)
    }

    @Test
    fun `an initial payment is recorded at creation`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাজার", null, null, null, Money.ofTaka(1_000))),
            purchaseDate = today,
            initialPayment = Money.ofTaka(400),
        )

        val account = repository.getAccount(id)!!
        assertEquals(Money.ofTaka(400), account.paid)
        assertEquals(Money.ofTaka(600), account.remaining)
    }

    // ----------------------------------------------------------- payments

    /** The headline requirement: 10000 - 2500 = 7500, then - 7500 = 0. */
    @Test
    fun `partial payments reduce the balance exactly and settle at zero`() = runTest {
        val id = repository.createShopAccount(
            name = "বড় বাজার",
            items = listOf(AccountRepository.ItemInput("মাসের বাজার", null, null, null, Money.ofTaka(10_000))),
            purchaseDate = today,
        )

        val first = repository.recordPayment(id, Money.ofTaka(2_500), today)
        assertEquals(Money.ofTaka(10_000), first.remainingBefore)
        assertEquals(Money.ofTaka(7_500), first.remainingAfter)
        assertFalse(first.isFullyPaid)
        assertEquals(Money.ofTaka(7_500), repository.getAccount(id)!!.remaining)

        val second = repository.recordPayment(id, Money.ofTaka(7_500), today)
        assertEquals(Money.ofTaka(7_500), second.remainingBefore)
        assertEquals(Money.ZERO, second.remainingAfter)
        assertTrue(second.isFullyPaid)

        val account = repository.getAccount(id)!!
        assertEquals(Money.ZERO, account.remaining)
        assertEquals(AccountStatus.PAID, account.status)
        assertEquals(100, account.progressPercent)
    }

    @Test
    fun `overpayment never produces a negative remaining`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাকি", null, null, null, Money.ofTaka(1_000))),
            purchaseDate = today,
        )

        val outcome = repository.recordPayment(id, Money.ofTaka(1_500), today)

        assertEquals(Money.ZERO, outcome.remainingAfter)
        assertEquals(Money.ofTaka(500), outcome.overpaid)
        val account = repository.getAccount(id)!!
        assertFalse(account.remaining.isNegative)
        assertEquals(Money.ZERO, account.remaining)
    }

    @Test
    fun `payment history is kept in full`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাকি", null, null, null, Money.ofTaka(3_000))),
            purchaseDate = today,
        )

        repository.recordPayment(id, Money.ofTaka(500), today, method = PaymentMethod.CASH)
        repository.recordPayment(id, Money.ofTaka(700), today.plusDays(1), method = PaymentMethod.BANK)
        repository.recordPayment(id, Money.ofTaka(300), today.plusDays(2), method = PaymentMethod.MOBILE_BANKING)

        val detail = repository.observeAccountDetail(id).first()!!
        assertEquals(3, detail.payments.size)
        assertEquals(Money.ofTaka(1_500), detail.account.paid)
        assertEquals(Money.ofTaka(1_500), detail.account.remaining)
    }

    @Test
    fun `deleting a payment restores the balance`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাকি", null, null, null, Money.ofTaka(2_000))),
            purchaseDate = today,
        )
        repository.recordPayment(id, Money.ofTaka(800), today)
        val payment = repository.observeAccountDetail(id).first()!!.payments.first()

        repository.deletePayment(payment.id)

        val account = repository.getAccount(id)!!
        assertEquals(Money.ZERO, account.paid)
        assertEquals(Money.ofTaka(2_000), account.remaining)
    }

    /** Deleting the final payment must re-open an account that was auto-closed. */
    @Test
    fun `deleting the settling payment reopens the account`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাকি", null, null, null, Money.ofTaka(1_000))),
            purchaseDate = today,
        )
        repository.recordPayment(id, Money.ofTaka(1_000), today)
        assertEquals(AccountStatus.PAID, repository.getAccount(id)!!.status)

        val payment = repository.observeAccountDetail(id).first()!!.payments.first()
        repository.deletePayment(payment.id)

        val account = repository.getAccount(id)!!
        assertFalse(account.isClosed)
        assertEquals(Money.ofTaka(1_000), account.remaining)
    }

    // --------------------------------------------------------------- loan

    @Test
    fun `loan generates a schedule that sums to the payable amount`() = runTest {
        val id = repository.createLoanAccount(
            loanName = "ব্র্যাক ব্যাংক Loan",
            organisation = "BRAC Bank",
            principal = Money.ofTaka(100_000),
            payable = Money.ofTaka(120_000),
            hasInterest = true,
            interestRatePercent = 10.0,
            installmentAmount = Money.ofTaka(10_000),
            frequency = InstallmentFrequency.MONTHLY,
            customIntervalDays = null,
            startDate = today,
            firstDueDate = today.plusMonths(1),
        )

        val detail = repository.observeAccountDetail(id).first()!!
        assertEquals(12, detail.installments.size)
        assertEquals(Money.ofTaka(120_000), Money.sum(detail.installments.map { it.amount }))
        assertEquals(Money.ofTaka(120_000), detail.account.total)
        assertEquals(Money.ofTaka(100_000), detail.principal)
        assertTrue(detail.hasInterest)
    }

    @Test
    fun `loan payments mark installments as paid in order`() = runTest {
        val id = repository.createLoanAccount(
            loanName = "Loan",
            organisation = null,
            principal = Money.ofTaka(12_000),
            payable = Money.ofTaka(12_000),
            hasInterest = false,
            interestRatePercent = null,
            installmentAmount = Money.ofTaka(1_000),
            frequency = InstallmentFrequency.MONTHLY,
            customIntervalDays = null,
            startDate = today,
            firstDueDate = today.plusMonths(1),
        )

        repository.recordPayment(id, Money.ofTaka(2_000), today)

        val detail = repository.observeAccountDetail(id).first()!!
        assertTrue(detail.installments[0].isPaid)
        assertTrue(detail.installments[1].isPaid)
        assertFalse(detail.installments[2].isPaid)
        assertEquals(2, detail.account.paidInstallments)
    }

    @Test
    fun `an already paid amount is carried into a new loan`() = runTest {
        val id = repository.createLoanAccount(
            loanName = "চলমান Loan",
            organisation = null,
            principal = Money.ofTaka(10_000),
            payable = Money.ofTaka(10_000),
            hasInterest = false,
            interestRatePercent = null,
            installmentAmount = Money.ofTaka(1_000),
            frequency = InstallmentFrequency.MONTHLY,
            customIntervalDays = null,
            startDate = today.minusMonths(3),
            firstDueDate = today.minusMonths(2),
            alreadyPaid = Money.ofTaka(3_000),
        )

        val account = repository.getAccount(id)!!
        assertEquals(Money.ofTaka(3_000), account.paid)
        assertEquals(Money.ofTaka(7_000), account.remaining)
    }

    // ---------------------------------------------------------------- EMI

    @Test
    fun `EMI finances only the amount left after the down payment`() = runTest {
        val id = repository.createEmiAccount(
            productName = "স্মার্টফোন",
            seller = "Star Tech",
            cashPrice = Money.ofTaka(60_000),
            downPayment = Money.ofTaka(12_000),
            tenureMonths = 12,
            installmentAmount = null,
            startDate = today,
            firstDueDate = today.plusMonths(1),
        )

        val detail = repository.observeAccountDetail(id).first()!!
        assertEquals(Money.ofTaka(48_000), detail.account.total)
        assertEquals(Money.ofTaka(12_000), detail.downPayment)
        assertEquals(12, detail.installments.size)
        assertTrue(detail.installments.all { it.amount == Money.ofTaka(4_000) })
        assertEquals(Money.ofTaka(48_000), Money.sum(detail.installments.map { it.amount }))
    }

    @Test
    fun `EMI due dates run monthly from the first due date`() = runTest {
        val id = repository.createEmiAccount(
            productName = "ল্যাপটপ",
            seller = null,
            cashPrice = Money.ofTaka(30_000),
            downPayment = Money.ZERO,
            tenureMonths = 3,
            installmentAmount = null,
            startDate = today,
            firstDueDate = LocalDate.of(2026, 4, 10),
        )

        val installments = repository.observeAccountDetail(id).first()!!.installments
        assertEquals(LocalDate.of(2026, 4, 10), installments[0].dueDate)
        assertEquals(LocalDate.of(2026, 5, 10), installments[1].dueDate)
        assertEquals(LocalDate.of(2026, 6, 10), installments[2].dueDate)
    }

    // ------------------------------------------------------------- person

    @Test
    fun `personal borrowing records the counterparty`() = runTest {
        val id = repository.createPersonAccount(
            personName = "সাকিব",
            relationship = "বন্ধু",
            phone = "01700000000",
            amount = Money.ofTaka(5_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = today.plusDays(30),
        )

        val account = repository.getAccount(id)!!
        assertEquals(AccountType.PERSON, account.type)
        assertEquals("সাকিব", account.name)
        assertEquals("01700000000", account.phone)
        assertEquals(DebtDirection.BORROWED, account.direction)
        assertEquals(Money.ofTaka(5_000), account.remaining)
    }

    /** Money lent out is an asset, not a due — it must not appear in reminders. */
    @Test
    fun `lent money is excluded from upcoming payments`() = runTest {
        repository.createPersonAccount(
            personName = "ধার দিয়েছি",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(2_000),
            direction = DebtDirection.LENT,
            borrowDate = today,
            returnDate = today.plusDays(5),
        )
        repository.createPersonAccount(
            personName = "ধার নিয়েছি",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(3_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = today.plusDays(5),
        )

        val upcoming = repository.getUpcomingPayments(today = today, horizonDays = 30)

        assertEquals(1, upcoming.size)
        assertEquals("ধার নিয়েছি", upcoming.single().accountName)
    }

    // ------------------------------------------------------ close & delete

    /** "Mark as paid" settles the remainder with one payment, so the ledger stays honest. */
    @Test
    fun `closing an account settles the remainder`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাকি", null, null, null, Money.ofTaka(900))),
            purchaseDate = today,
        )

        repository.closeAccount(id)

        val account = repository.getAccount(id)!!
        assertEquals(Money.ZERO, account.remaining)
        assertEquals(Money.ofTaka(900), account.paid)
        assertEquals(AccountStatus.PAID, account.status)
        // The settling payment is visible in the timeline rather than implicit.
        assertEquals(1, repository.observeAccountDetail(id).first()!!.payments.size)
    }

    @Test
    fun `deleting an account cascades to its children`() = runTest {
        val id = repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাকি", null, null, null, Money.ofTaka(500))),
            purchaseDate = today,
        )
        repository.recordPayment(id, Money.ofTaka(100), today)

        repository.deleteAccount(id)

        assertNull(repository.getAccount(id))
        assertTrue(database.accountDao().getItems(id).isEmpty())
        assertTrue(database.accountDao().getInstallments(id).isEmpty())
        assertEquals(0L, database.paymentDao().sumForAccount(id))
    }

    // -------------------------------------------------------------- search

    @Test
    fun `search matches names and notes`() = runTest {
        repository.createShopAccount(
            name = "রহিম স্টোর",
            items = listOf(AccountRepository.ItemInput("চাল", null, null, null, Money.ofTaka(300))),
            purchaseDate = today,
            note = "ঈদের বাজার",
        )
        repository.createPersonAccount(
            personName = "করিম",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(1_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = null,
        )

        assertEquals(1, repository.searchAccounts("রহিম").first().size)
        assertEquals(1, repository.searchAccounts("ঈদ").first().size)
        assertEquals(1, repository.searchAccounts("করিম").first().size)
        assertTrue(repository.searchAccounts("নেই এমন").first().isEmpty())
    }

    @Test
    fun `observeAccounts emits every type`() = runTest {
        repository.createShopAccount(
            name = "দোকান",
            items = listOf(AccountRepository.ItemInput("বাকি", null, null, null, Money.ofTaka(100))),
            purchaseDate = today,
        )
        repository.createPersonAccount(
            personName = "বন্ধু",
            relationship = null,
            phone = null,
            amount = Money.ofTaka(200),
            direction = DebtDirection.BORROWED,
            borrowDate = today,
            returnDate = null,
        )

        val accounts = repository.observeAccounts().first()
        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.type == AccountType.SHOP })
        assertTrue(accounts.any { it.type == AccountType.PERSON })
    }
}
