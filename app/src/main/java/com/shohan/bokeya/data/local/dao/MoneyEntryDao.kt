package com.shohan.bokeya.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shohan.bokeya.data.local.entity.CategoryEntity
import com.shohan.bokeya.data.local.entity.MoneyEntryEntity
import com.shohan.bokeya.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

data class MoneyEntryWithCategory(
    val id: Long,
    val isIncome: Boolean,
    val title: String,
    val amount: Long,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColor: Int?,
    val entryDate: Long,
    val entryTimeMillis: Long,
    val note: String?,
)

data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColor: Int?,
    val categoryIconKey: String?,
    val total: Long,
    val entryCount: Int,
)

/** One month's income/expense pair, used by the dashboard trend chart. */
data class MonthlyTotal(
    val yearMonth: Int, // year * 100 + month, e.g. 202609
    val income: Long,
    val expense: Long,
)

@Dao
interface MoneyEntryDao {

    @Query(
        """
        SELECT m.id, m.isIncome, m.title, m.amount, m.categoryId,
               c.name AS categoryName, c.iconKey AS categoryIconKey, c.colorArgb AS categoryColor,
               m.entryDate, m.entryTimeMillis, m.note
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.isIncome = :isIncome
        ORDER BY m.entryDate DESC, m.entryTimeMillis DESC, m.id DESC
        """,
    )
    fun observeByKind(isIncome: Boolean): Flow<List<MoneyEntryWithCategory>>

    @Query(
        """
        SELECT m.id, m.isIncome, m.title, m.amount, m.categoryId,
               c.name AS categoryName, c.iconKey AS categoryIconKey, c.colorArgb AS categoryColor,
               m.entryDate, m.entryTimeMillis, m.note
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.isIncome = :isIncome AND m.entryDate BETWEEN :from AND :to
        ORDER BY m.entryDate DESC, m.entryTimeMillis DESC, m.id DESC
        """,
    )
    fun observeByKindBetween(isIncome: Boolean, from: Long, to: Long): Flow<List<MoneyEntryWithCategory>>

    @Query(
        """
        SELECT m.id, m.isIncome, m.title, m.amount, m.categoryId,
               c.name AS categoryName, c.iconKey AS categoryIconKey, c.colorArgb AS categoryColor,
               m.entryDate, m.entryTimeMillis, m.note
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.entryDate BETWEEN :from AND :to
        ORDER BY m.entryDate DESC, m.entryTimeMillis DESC, m.id DESC
        """,
    )
    fun observeBetween(from: Long, to: Long): Flow<List<MoneyEntryWithCategory>>

    @Query(
        """
        SELECT m.id, m.isIncome, m.title, m.amount, m.categoryId,
               c.name AS categoryName, c.iconKey AS categoryIconKey, c.colorArgb AS categoryColor,
               m.entryDate, m.entryTimeMillis, m.note
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.entryDate BETWEEN :from AND :to
        ORDER BY m.entryDate DESC, m.id DESC
        """,
    )
    suspend fun getBetween(from: Long, to: Long): List<MoneyEntryWithCategory>

    @Query(
        """
        SELECT m.id, m.isIncome, m.title, m.amount, m.categoryId,
               c.name AS categoryName, c.iconKey AS categoryIconKey, c.colorArgb AS categoryColor,
               m.entryDate, m.entryTimeMillis, m.note
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.id = :id
        """,
    )
    fun observeById(id: Long): Flow<MoneyEntryWithCategory?>

    @Query("SELECT * FROM money_entries WHERE id = :id")
    suspend fun getById(id: Long): MoneyEntryEntity?

    @Query("SELECT * FROM money_entries ORDER BY id ASC")
    suspend fun getAll(): List<MoneyEntryEntity>

    @Query(
        """
        SELECT m.id, m.isIncome, m.title, m.amount, m.categoryId,
               c.name AS categoryName, c.iconKey AS categoryIconKey, c.colorArgb AS categoryColor,
               m.entryDate, m.entryTimeMillis, m.note
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.title LIKE '%' || :query || '%'
           OR IFNULL(m.note, '') LIKE '%' || :query || '%'
           OR IFNULL(c.name, '') LIKE '%' || :query || '%'
        ORDER BY m.entryDate DESC
        LIMIT 50
        """,
    )
    fun search(query: String): Flow<List<MoneyEntryWithCategory>>

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM money_entries
        WHERE isIncome = :isIncome AND entryDate BETWEEN :from AND :to
        """,
    )
    fun observeSum(isIncome: Boolean, from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM money_entries
        WHERE isIncome = :isIncome AND entryDate BETWEEN :from AND :to
        """,
    )
    suspend fun sum(isIncome: Boolean, from: Long, to: Long): Long

    @Query(
        """
        SELECT m.categoryId AS categoryId, c.name AS categoryName,
               c.colorArgb AS categoryColor, c.iconKey AS categoryIconKey,
               SUM(m.amount) AS total, COUNT(*) AS entryCount
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.isIncome = :isIncome AND m.entryDate BETWEEN :from AND :to
        GROUP BY m.categoryId
        ORDER BY total DESC
        """,
    )
    fun observeCategoryTotals(isIncome: Boolean, from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT m.categoryId AS categoryId, c.name AS categoryName,
               c.colorArgb AS categoryColor, c.iconKey AS categoryIconKey,
               SUM(m.amount) AS total, COUNT(*) AS entryCount
        FROM money_entries m
        LEFT JOIN categories c ON c.id = m.categoryId
        WHERE m.isIncome = :isIncome AND m.entryDate BETWEEN :from AND :to
        GROUP BY m.categoryId
        ORDER BY total DESC
        """,
    )
    suspend fun getCategoryTotals(isIncome: Boolean, from: Long, to: Long): List<CategoryTotal>

    @Query("SELECT COUNT(*) FROM money_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: MoneyEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MoneyEntryEntity>)

    @Update
    suspend fun update(entry: MoneyEntryEntity)

    @Delete
    suspend fun delete(entry: MoneyEntryEntity)

    @Query("DELETE FROM money_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM money_entries")
    suspend fun deleteAll()

    // ------------------------------------------------------------- categories

    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind ASC, sortOrder ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY sortOrder ASC")
    suspend fun getCategories(kind: CategoryKind): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE builtInKey = :key LIMIT 1")
    suspend fun getCategoryByKey(key: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}
