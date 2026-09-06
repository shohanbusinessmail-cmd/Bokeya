package com.shohan.bokeya.data.repository

import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.local.DefaultCategories
import com.shohan.bokeya.data.local.entity.CategoryEntity
import com.shohan.bokeya.data.local.entity.MoneyEntryEntity
import com.shohan.bokeya.domain.model.Category
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.CategorySpend
import com.shohan.bokeya.domain.model.MoneyEntry
import com.shohan.bokeya.domain.model.MonthlyTrend
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Income, expense and category storage. */
class MoneyRepository(
    private val database: BokeyaDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val dao = database.moneyEntryDao()

    // ---------------------------------------------------------------- entries

    fun observeEntries(isIncome: Boolean): Flow<List<MoneyEntry>> =
        dao.observeByKind(isIncome).map { rows -> rows.map { it.toDomain() } }

    fun observeEntriesBetween(isIncome: Boolean, from: LocalDate, to: LocalDate): Flow<List<MoneyEntry>> =
        dao.observeByKindBetween(isIncome, from.toEpochDay(), to.toEpochDay())
            .map { rows -> rows.map { it.toDomain() } }

    fun observeAllBetween(from: LocalDate, to: LocalDate): Flow<List<MoneyEntry>> =
        dao.observeBetween(from.toEpochDay(), to.toEpochDay())
            .map { rows -> rows.map { it.toDomain() } }

    fun observeEntry(id: Long): Flow<MoneyEntry?> = dao.observeById(id).map { it?.toDomain() }

    fun searchEntries(query: String): Flow<List<MoneyEntry>> =
        dao.search(query.trim()).map { rows -> rows.map { it.toDomain() } }

    fun observeSum(isIncome: Boolean, from: LocalDate, to: LocalDate): Flow<Money> =
        dao.observeSum(isIncome, from.toEpochDay(), to.toEpochDay()).map(::Money)

    suspend fun sum(isIncome: Boolean, from: LocalDate, to: LocalDate): Money =
        withContext(io) { Money(dao.sum(isIncome, from.toEpochDay(), to.toEpochDay())) }

    suspend fun addEntry(
        isIncome: Boolean,
        title: String,
        amount: Money,
        categoryId: Long?,
        date: LocalDate,
        timeMillis: Long = BanglaDate.nowMillis(),
        note: String? = null,
    ): Long = withContext(io) {
        val now = BanglaDate.nowMillis()
        dao.insert(
            MoneyEntryEntity(
                isIncome = isIncome,
                title = title.trim(),
                amount = amount.minor,
                categoryId = categoryId,
                entryDate = date.toEpochDay(),
                entryTimeMillis = timeMillis,
                note = note?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateEntry(
        id: Long,
        title: String,
        amount: Money,
        categoryId: Long?,
        date: LocalDate,
        timeMillis: Long,
        note: String?,
    ) = withContext(io) {
        val existing = dao.getById(id) ?: return@withContext
        dao.update(
            existing.copy(
                title = title.trim(),
                amount = amount.minor,
                categoryId = categoryId,
                entryDate = date.toEpochDay(),
                entryTimeMillis = timeMillis,
                note = note?.trim()?.ifBlank { null },
                updatedAt = BanglaDate.nowMillis(),
            ),
        )
    }

    suspend fun deleteEntry(id: Long) = withContext(io) { dao.deleteById(id) }

    suspend fun getEntry(id: Long): MoneyEntryEntity? = withContext(io) { dao.getById(id) }

    /** Re-inserts a deleted entry so Snackbar "ফিরিয়ে আনুন" can restore it verbatim. */
    suspend fun restoreEntry(entity: MoneyEntryEntity) = withContext(io) { dao.insertAll(listOf(entity)) }

    // ------------------------------------------------------------- categories

    fun observeCategories(kind: CategoryKind): Flow<List<Category>> =
        dao.observeCategories(kind).map { rows -> rows.map { it.toDomain() } }

    fun observeAllCategories(): Flow<List<Category>> =
        dao.observeAllCategories().map { rows -> rows.map { it.toDomain() } }

    suspend fun getCategories(kind: CategoryKind): List<Category> =
        withContext(io) { dao.getCategories(kind).map { it.toDomain() } }

    suspend fun addCategory(name: String, kind: CategoryKind, iconKey: String?, color: Int?): Long =
        withContext(io) {
            dao.insertCategory(
                CategoryEntity(
                    name = name.trim(),
                    kind = kind,
                    iconKey = iconKey ?: "label",
                    colorArgb = color,
                    sortOrder = 100,
                    isDefault = false,
                ),
            )
        }

    suspend fun renameCategory(id: Long, name: String) = withContext(io) {
        dao.getAllCategories().firstOrNull { it.id == id }?.let {
            dao.updateCategory(it.copy(name = name.trim()))
        }
    }

    /** Deletes a custom category; its entries fall back to the "অন্যান্য" bucket. */
    suspend fun deleteCategory(id: Long) = withContext(io) { dao.deleteCategory(id) }

    /** Seeds the built-in categories exactly once. */
    suspend fun ensureDefaultCategories() = withContext(io) {
        if (dao.categoryCount() == 0) {
            dao.insertCategories(DefaultCategories.asEntities())
        }
    }

    suspend fun defaultCategoryId(kind: CategoryKind): Long? = withContext(io) {
        val key = if (kind == CategoryKind.INCOME) {
            DefaultCategories.OTHER_INCOME_KEY
        } else {
            DefaultCategories.OTHER_EXPENSE_KEY
        }
        dao.getCategoryByKey(key)?.id
    }

    // --------------------------------------------------------------- analysis

    fun observeCategoryBreakdown(
        isIncome: Boolean,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CategorySpend>> =
        dao.observeCategoryTotals(isIncome, from.toEpochDay(), to.toEpochDay()).map { rows ->
            val total = rows.sumOf { it.total }
            rows.map { row ->
                CategorySpend(
                    categoryId = row.categoryId,
                    name = row.categoryName ?: "অন্যান্য",
                    color = row.categoryColor,
                    iconKey = row.categoryIconKey,
                    amount = Money(row.total),
                    entryCount = row.entryCount,
                    sharePercent = if (total > 0) {
                        (row.total.toDouble() / total * 100).roundToInt()
                    } else {
                        0
                    },
                )
            }
        }

    suspend fun getCategoryBreakdown(
        isIncome: Boolean,
        from: LocalDate,
        to: LocalDate,
    ): List<CategorySpend> = withContext(io) {
        val rows = dao.getCategoryTotals(isIncome, from.toEpochDay(), to.toEpochDay())
        val total = rows.sumOf { it.total }
        rows.map { row ->
            CategorySpend(
                categoryId = row.categoryId,
                name = row.categoryName ?: "অন্যান্য",
                color = row.categoryColor,
                iconKey = row.categoryIconKey,
                amount = Money(row.total),
                entryCount = row.entryCount,
                sharePercent = if (total > 0) (row.total.toDouble() / total * 100).roundToInt() else 0,
            )
        }
    }

    /** Income/expense totals for the last [months] calendar months, oldest first. */
    suspend fun getMonthlyTrend(months: Int = 6, endMonth: LocalDate = BanglaDate.today()): List<MonthlyTrend> =
        withContext(io) {
            val result = mutableListOf<MonthlyTrend>()
            for (offset in (months - 1) downTo 0) {
                val monthDate = endMonth.minusMonths(offset.toLong())
                val start = BanglaDate.startOfMonth(monthDate)
                val end = BanglaDate.endOfMonth(monthDate)
                result += MonthlyTrend(
                    year = monthDate.year,
                    month = monthDate.monthValue,
                    income = Money(dao.sum(true, start.toEpochDay(), end.toEpochDay())),
                    expense = Money(dao.sum(false, start.toEpochDay(), end.toEpochDay())),
                )
            }
            result
        }

    suspend fun entryCount(): Int = withContext(io) { dao.count() }
}
