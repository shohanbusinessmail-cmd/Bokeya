package com.shohan.bokeya.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.domain.model.CategoryKind
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
class MoneyRepositoryTest {

    private lateinit var database: BokeyaDatabase
    private lateinit var repository: MoneyRepository

    private val march: LocalDate = LocalDate.of(2026, 3, 15)
    private val monthStart: LocalDate = LocalDate.of(2026, 3, 1)
    private val monthEnd: LocalDate = LocalDate.of(2026, 3, 31)

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
        repository = MoneyRepository(database, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `income and expense are summed separately`() = runTest {
        repository.addEntry(true, "বেতন", Money.ofTaka(30_000), null, march)
        repository.addEntry(false, "বাজার", Money.ofTaka(5_000), null, march)
        repository.addEntry(false, "যাতায়াত", Money.ofTaka(1_200), null, march)

        assertEquals(Money.ofTaka(30_000), repository.sum(true, monthStart, monthEnd))
        assertEquals(Money.ofTaka(6_200), repository.sum(false, monthStart, monthEnd))
    }

    @Test
    fun `sums are scoped to the requested range`() = runTest {
        repository.addEntry(false, "মার্চের খরচ", Money.ofTaka(1_000), null, march)
        repository.addEntry(false, "এপ্রিলের খরচ", Money.ofTaka(9_999), null, LocalDate.of(2026, 4, 2))

        assertEquals(Money.ofTaka(1_000), repository.sum(false, monthStart, monthEnd))
    }

    @Test
    fun `range boundaries are inclusive`() = runTest {
        repository.addEntry(false, "১ তারিখ", Money.ofTaka(100), null, monthStart)
        repository.addEntry(false, "৩১ তারিখ", Money.ofTaka(200), null, monthEnd)

        assertEquals(Money.ofTaka(300), repository.sum(false, monthStart, monthEnd))
    }

    @Test
    fun `an empty month sums to zero not null`() = runTest {
        assertEquals(Money.ZERO, repository.sum(false, monthStart, monthEnd))
        assertEquals(Money.ZERO, repository.sum(true, monthStart, monthEnd))
    }

    @Test
    fun `entries can be edited`() = runTest {
        val id = repository.addEntry(false, "চা", Money.ofTaka(20), null, march)

        repository.updateEntry(
            id = id,
            title = "চা-নাস্তা",
            amount = Money.ofTaka(75),
            categoryId = null,
            date = march,
            timeMillis = 0L,
            note = "অফিসে",
        )

        val entry = repository.observeEntry(id).first()!!
        assertEquals("চা-নাস্তা", entry.title)
        assertEquals(Money.ofTaka(75), entry.amount)
        assertEquals("অফিসে", entry.note)
    }

    @Test
    fun `a deleted entry can be restored for undo`() = runTest {
        val id = repository.addEntry(false, "ভুল এন্ট্রি", Money.ofTaka(500), null, march)
        val snapshot = repository.getEntry(id)
        assertNotNull(snapshot)

        repository.deleteEntry(id)
        assertNull(repository.getEntry(id))
        assertEquals(Money.ZERO, repository.sum(false, monthStart, monthEnd))

        repository.restoreEntry(snapshot!!)
        assertEquals(Money.ofTaka(500), repository.sum(false, monthStart, monthEnd))
    }

    // -------------------------------------------------------- categories

    @Test
    fun `default categories are seeded once`() = runTest {
        repository.ensureDefaultCategories()
        val firstCount = repository.observeAllCategories().first().size

        repository.ensureDefaultCategories()
        assertEquals(firstCount, repository.observeAllCategories().first().size)
        assertTrue(firstCount > 0)
    }

    @Test
    fun `expense categories cover the brief`() = runTest {
        repository.ensureDefaultCategories()
        val names = repository.getCategories(CategoryKind.EXPENSE).map { it.name }

        listOf("খাবার", "বাজার", "যাতায়াত", "বাসা", "চিকিৎসা", "শিক্ষা", "বিল", "কেনাকাটা", "বিনোদন", "অন্যান্য")
            .forEach { expected ->
                assertTrue("missing category: $expected", names.contains(expected))
            }
    }

    @Test
    fun `custom categories can be added renamed and deleted`() = runTest {
        val id = repository.addCategory("দান", CategoryKind.EXPENSE, "label", 0xFF10B981.toInt())
        assertTrue(repository.getCategories(CategoryKind.EXPENSE).any { it.id == id })

        repository.renameCategory(id, "দান-খয়রাত")
        assertEquals(
            "দান-খয়রাত",
            repository.getCategories(CategoryKind.EXPENSE).first { it.id == id }.name,
        )

        repository.deleteCategory(id)
        assertTrue(repository.getCategories(CategoryKind.EXPENSE).none { it.id == id })
    }

    /** Deleting a category must orphan its entries, never delete the money. */
    @Test
    fun `deleting a category keeps its entries`() = runTest {
        val categoryId = repository.addCategory("অস্থায়ী", CategoryKind.EXPENSE, null, null)
        repository.addEntry(false, "খরচ", Money.ofTaka(400), categoryId, march)

        repository.deleteCategory(categoryId)

        assertEquals(Money.ofTaka(400), repository.sum(false, monthStart, monthEnd))
        val entry = repository.observeEntries(false).first().single()
        assertNull(entry.categoryId)
    }

    @Test
    fun `category breakdown ranks spending`() = runTest {
        val food = repository.addCategory("খাবার", CategoryKind.EXPENSE, null, null)
        val transport = repository.addCategory("যাতায়াত", CategoryKind.EXPENSE, null, null)
        repository.addEntry(false, "দুপুরের খাবার", Money.ofTaka(3_000), food, march)
        repository.addEntry(false, "রিকশা", Money.ofTaka(800), transport, march)

        val breakdown = repository.getCategoryBreakdown(false, monthStart, monthEnd)

        assertEquals(2, breakdown.size)
        assertEquals("খাবার", breakdown.first().name)
        assertEquals(Money.ofTaka(3_000), breakdown.first().amount)
    }

    @Test
    fun `monthly trend returns a row per month`() = runTest {
        repository.addEntry(true, "বেতন", Money.ofTaka(20_000), null, march)
        repository.addEntry(false, "খরচ", Money.ofTaka(8_000), null, march)

        val trend = repository.getMonthlyTrend(months = 3, endMonth = march)

        assertEquals(3, trend.size)
        val current = trend.last()
        assertEquals(Money.ofTaka(20_000), current.income)
        assertEquals(Money.ofTaka(8_000), current.expense)
    }

    @Test
    fun `search finds entries by title`() = runTest {
        repository.addEntry(false, "ওষুধ কেনা", Money.ofTaka(650), null, march)
        repository.addEntry(false, "বাজার", Money.ofTaka(1_500), null, march)

        assertEquals(1, repository.searchEntries("ওষুধ").first().size)
        assertTrue(repository.searchEntries("নেই").first().isEmpty())
    }
}
