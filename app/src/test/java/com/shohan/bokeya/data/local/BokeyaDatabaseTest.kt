package com.shohan.bokeya.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.shohan.bokeya.data.local.entity.AccountEntity
import com.shohan.bokeya.data.local.entity.DebtItemEntity
import com.shohan.bokeya.data.local.entity.InstallmentEntity
import com.shohan.bokeya.data.local.entity.PaymentEntity
import com.shohan.bokeya.data.local.entity.ReminderLogEntity
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.PaymentMethod
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Schema-level guarantees: cascades, uniqueness and aggregate correctness.
 *
 * These sit below the repository tests deliberately — if a foreign key or a
 * SUM() is wrong, every layer above it is wrong in a way that is very hard to
 * spot from the UI.
 */
@RunWith(RobolectricTestRunner::class)
class BokeyaDatabaseTest {

    private lateinit var database: BokeyaDatabase

    private val today: LocalDate = LocalDate.of(2026, 3, 15)
    private val epochToday: Long = today.toEpochDay()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BokeyaDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertAccount(name: String = "দোকান", total: Long = 100_000L): Long =
        database.accountDao().insert(
            AccountEntity(
                type = AccountType.SHOP,
                name = name,
                totalAmount = total,
                startDate = epochToday,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )

    @Test
    fun `database opens at the declared version`() {
        assertEquals(1, BokeyaDatabase.VERSION)
        assertNotNull(database.openHelper.writableDatabase)
    }

    @Test
    fun `every dao is reachable`() {
        assertNotNull(database.accountDao())
        assertNotNull(database.paymentDao())
        assertNotNull(database.moneyEntryDao())
        assertNotNull(database.reminderDao())
    }

    // ------------------------------------------------------------ cascades

    @Test
    fun `deleting an account deletes its items`() = runTest {
        val accountId = insertAccount()
        database.accountDao().insertItem(
            DebtItemEntity(
                accountId = accountId,
                name = "চাল",
                totalPrice = 30_000L,
                purchaseDate = epochToday,
                createdAt = 0L,
            ),
        )
        assertEquals(1, database.accountDao().getItems(accountId).size)

        database.accountDao().deleteById(accountId)

        assertTrue(database.accountDao().getItems(accountId).isEmpty())
    }

    @Test
    fun `deleting an account deletes its payments`() = runTest {
        val accountId = insertAccount()
        database.paymentDao().insert(
            PaymentEntity(
                accountId = accountId,
                amount = 25_000L,
                paymentDate = epochToday,
                paymentTimeMillis = 0L,
                method = PaymentMethod.CASH,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        assertEquals(25_000L, database.paymentDao().sumForAccount(accountId))

        database.accountDao().deleteById(accountId)

        assertEquals(0L, database.paymentDao().sumForAccount(accountId))
    }

    @Test
    fun `deleting an account deletes its installments`() = runTest {
        val accountId = insertAccount()
        database.accountDao().insertInstallments(
            listOf(
                InstallmentEntity(
                    accountId = accountId,
                    sequence = 1,
                    dueDate = epochToday,
                    amount = 50_000L,
                ),
            ),
        )
        assertEquals(1, database.accountDao().getInstallments(accountId).size)

        database.accountDao().deleteById(accountId)

        assertTrue(database.accountDao().getInstallments(accountId).isEmpty())
    }

    // ----------------------------------------------------------- aggregates

    @Test
    fun `payment sum totals every row`() = runTest {
        val accountId = insertAccount()
        listOf(10_000L, 25_000L, 5_500L).forEach { amount ->
            database.paymentDao().insert(
                PaymentEntity(
                    accountId = accountId,
                    amount = amount,
                    paymentDate = epochToday,
                    paymentTimeMillis = 0L,
                    method = PaymentMethod.CASH,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
        }

        assertEquals(40_500L, database.paymentDao().sumForAccount(accountId))
    }

    /** SUM() over no rows is NULL in SQL — the DAO must coalesce it to 0. */
    @Test
    fun `sums with no rows return zero not null`() = runTest {
        val accountId = insertAccount()
        assertEquals(0L, database.paymentDao().sumForAccount(accountId))
        assertEquals(0L, database.accountDao().sumItems(accountId))
    }

    @Test
    fun `item sum totals every row`() = runTest {
        val accountId = insertAccount()
        listOf(30_000L, 12_500L).forEach { total ->
            database.accountDao().insertItem(
                DebtItemEntity(
                    accountId = accountId,
                    name = "পণ্য",
                    totalPrice = total,
                    purchaseDate = epochToday,
                    createdAt = 0L,
                ),
            )
        }

        assertEquals(42_500L, database.accountDao().sumItems(accountId))
    }

    // --------------------------------------------------------- reminder log

    /**
     * The reminder log's UNIQUE(accountId, dueDate, kind) index is what stops a
     * user being notified about the same instalment twice.
     */
    @Test
    fun `the same reminder cannot be logged twice`() = runTest {
        val accountId = insertAccount()
        val entry = ReminderLogEntity(
            accountId = accountId,
            dueDate = epochToday,
            kind = "due_today",
            notifiedAt = 0L,
        )

        database.reminderDao().logReminder(entry)
        database.reminderDao().logReminder(entry.copy(notifiedAt = 999L))

        assertEquals(1, database.reminderDao().wasNotified(accountId, epochToday, "due_today"))
    }

    @Test
    fun `different reminder kinds are logged separately`() = runTest {
        val accountId = insertAccount()
        database.reminderDao().logReminder(
            ReminderLogEntity(accountId = accountId, dueDate = epochToday, kind = "upcoming", notifiedAt = 0L),
        )
        database.reminderDao().logReminder(
            ReminderLogEntity(accountId = accountId, dueDate = epochToday, kind = "due_today", notifiedAt = 0L),
        )

        assertEquals(1, database.reminderDao().wasNotified(accountId, epochToday, "upcoming"))
        assertEquals(1, database.reminderDao().wasNotified(accountId, epochToday, "due_today"))
    }

    @Test
    fun `an unlogged reminder reads as not yet notified`() = runTest {
        val accountId = insertAccount()
        assertEquals(0, database.reminderDao().wasNotified(accountId, epochToday, "overdue"))
    }

    @Test
    fun `old reminder rows are prunable`() = runTest {
        val accountId = insertAccount()
        database.reminderDao().logReminder(
            ReminderLogEntity(accountId = accountId, dueDate = epochToday, kind = "overdue", notifiedAt = 100L),
        )

        database.reminderDao().pruneOlderThan(500L)

        assertEquals(0, database.reminderDao().wasNotified(accountId, epochToday, "overdue"))
    }

    // ---------------------------------------------------------------- reads

    @Test
    fun `accounts round-trip through the dao`() = runTest {
        val id = insertAccount(name = "রহিম স্টোর", total = 75_000L)

        val stored = database.accountDao().getById(id)

        assertNotNull(stored)
        assertEquals("রহিম স্টোর", stored!!.name)
        assertEquals(75_000L, stored.totalAmount)
        assertEquals(AccountType.SHOP, stored.type)
    }

    @Test
    fun `a missing account reads as null rather than throwing`() = runTest {
        assertNull(database.accountDao().getById(9_999L))
    }

    @Test
    fun `clearing all accounts empties the table`() = runTest {
        insertAccount(name = "এক")
        insertAccount(name = "দুই")
        assertEquals(2, database.accountDao().count())

        database.accountDao().deleteAll()

        assertEquals(0, database.accountDao().count())
    }
}
