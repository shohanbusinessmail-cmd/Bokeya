package com.shohan.bokeya.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shohan.bokeya.data.local.entity.AccountEntity
import com.shohan.bokeya.data.local.entity.DebtItemEntity
import com.shohan.bokeya.data.local.entity.InstallmentEntity
import com.shohan.bokeya.data.local.entity.PaymentEntity
import com.shohan.bokeya.domain.model.AccountType
import kotlinx.coroutines.flow.Flow

/**
 * Aggregate row used by every list and dashboard surface.
 *
 * Totals are summed in SQLite rather than in Kotlin so the app never loads
 * thousands of payment rows just to show one balance.
 */
data class AccountWithTotals(
    val id: Long,
    val type: AccountType,
    val name: String,
    val secondaryName: String?,
    val totalAmount: Long,
    val paidAmount: Long,
    val nextDueDate: Long?,
    val endDate: Long?,
    val startDate: Long,
    val isClosed: Boolean,
    val installmentAmount: Long?,
    val tenureCount: Int?,
    val paidInstallments: Int,
    val direction: String?,
    val note: String?,
    val phone: String?,
    val updatedAt: Long,
)

@Dao
interface AccountDao {

    // ------------------------------------------------------------------ reads

    /**
     * Every account with its paid total. Kept as one query with correlated
     * sub-selects: LEFT JOIN + GROUP BY over two child tables would multiply
     * rows and double-count payments when an account also has installments.
     */
    @Query(
        """
        SELECT a.id, a.type, a.name, a.secondaryName, a.totalAmount,
               IFNULL((SELECT SUM(p.amount) FROM payments p WHERE p.accountId = a.id), 0) AS paidAmount,
               a.nextDueDate, a.endDate, a.startDate, a.isClosed,
               a.installmentAmount, a.tenureCount,
               IFNULL((SELECT COUNT(*) FROM installments i WHERE i.accountId = a.id AND i.isPaid = 1), 0) AS paidInstallments,
               a.direction, a.note, a.phone, a.updatedAt
        FROM accounts a
        ORDER BY a.isClosed ASC,
                 CASE WHEN a.nextDueDate IS NULL THEN 1 ELSE 0 END,
                 a.nextDueDate ASC,
                 a.updatedAt DESC
        """,
    )
    fun observeAllWithTotals(): Flow<List<AccountWithTotals>>

    @Query(
        """
        SELECT a.id, a.type, a.name, a.secondaryName, a.totalAmount,
               IFNULL((SELECT SUM(p.amount) FROM payments p WHERE p.accountId = a.id), 0) AS paidAmount,
               a.nextDueDate, a.endDate, a.startDate, a.isClosed,
               a.installmentAmount, a.tenureCount,
               IFNULL((SELECT COUNT(*) FROM installments i WHERE i.accountId = a.id AND i.isPaid = 1), 0) AS paidInstallments,
               a.direction, a.note, a.phone, a.updatedAt
        FROM accounts a
        WHERE a.type = :type
        ORDER BY a.isClosed ASC,
                 CASE WHEN a.nextDueDate IS NULL THEN 1 ELSE 0 END,
                 a.nextDueDate ASC,
                 a.updatedAt DESC
        """,
    )
    fun observeByTypeWithTotals(type: AccountType): Flow<List<AccountWithTotals>>

    @Query(
        """
        SELECT a.id, a.type, a.name, a.secondaryName, a.totalAmount,
               IFNULL((SELECT SUM(p.amount) FROM payments p WHERE p.accountId = a.id), 0) AS paidAmount,
               a.nextDueDate, a.endDate, a.startDate, a.isClosed,
               a.installmentAmount, a.tenureCount,
               IFNULL((SELECT COUNT(*) FROM installments i WHERE i.accountId = a.id AND i.isPaid = 1), 0) AS paidInstallments,
               a.direction, a.note, a.phone, a.updatedAt
        FROM accounts a
        WHERE a.id = :id
        """,
    )
    fun observeWithTotals(id: Long): Flow<AccountWithTotals?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isClosed = 0")
    suspend fun getOpenAccounts(): List<AccountEntity>

    /** Free-text search across names, the linked org/person and notes. */
    @Query(
        """
        SELECT a.id, a.type, a.name, a.secondaryName, a.totalAmount,
               IFNULL((SELECT SUM(p.amount) FROM payments p WHERE p.accountId = a.id), 0) AS paidAmount,
               a.nextDueDate, a.endDate, a.startDate, a.isClosed,
               a.installmentAmount, a.tenureCount,
               IFNULL((SELECT COUNT(*) FROM installments i WHERE i.accountId = a.id AND i.isPaid = 1), 0) AS paidInstallments,
               a.direction, a.note, a.phone, a.updatedAt
        FROM accounts a
        WHERE a.name LIKE '%' || :query || '%'
           OR IFNULL(a.secondaryName, '') LIKE '%' || :query || '%'
           OR IFNULL(a.note, '') LIKE '%' || :query || '%'
           OR EXISTS (
                SELECT 1 FROM debt_items di
                WHERE di.accountId = a.id AND di.name LIKE '%' || :query || '%'
           )
        ORDER BY a.isClosed ASC, a.updatedAt DESC
        LIMIT 50
        """,
    )
    fun search(query: String): Flow<List<AccountWithTotals>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    // ----------------------------------------------------------------- writes

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE accounts SET totalAmount = :total, updatedAt = :now WHERE id = :id")
    suspend fun updateTotal(id: Long, total: Long, now: Long)

    @Query("UPDATE accounts SET nextDueDate = :nextDueDate, updatedAt = :now WHERE id = :id")
    suspend fun updateNextDueDate(id: Long, nextDueDate: Long?, now: Long)

    @Query("UPDATE accounts SET isClosed = :closed, closedAt = :closedAt, updatedAt = :now WHERE id = :id")
    suspend fun updateClosed(id: Long, closed: Boolean, closedAt: Long?, now: Long)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    // --------------------------------------------------------------- children

    @Query("SELECT * FROM debt_items WHERE accountId = :accountId ORDER BY purchaseDate DESC, id DESC")
    fun observeItems(accountId: Long): Flow<List<DebtItemEntity>>

    @Query("SELECT * FROM debt_items WHERE accountId = :accountId")
    suspend fun getItems(accountId: Long): List<DebtItemEntity>

    @Query("SELECT * FROM debt_items ORDER BY id ASC")
    suspend fun getAllItems(): List<DebtItemEntity>

    @Insert
    suspend fun insertItem(item: DebtItemEntity): Long

    @Insert
    suspend fun insertItems(items: List<DebtItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: DebtItemEntity)

    @Query("DELETE FROM debt_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT IFNULL(SUM(totalPrice), 0) FROM debt_items WHERE accountId = :accountId")
    suspend fun sumItems(accountId: Long): Long

    @Query("SELECT * FROM installments WHERE accountId = :accountId ORDER BY sequence ASC")
    fun observeInstallments(accountId: Long): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE accountId = :accountId ORDER BY sequence ASC")
    suspend fun getInstallments(accountId: Long): List<InstallmentEntity>

    @Query("SELECT * FROM installments ORDER BY id ASC")
    suspend fun getAllInstallments(): List<InstallmentEntity>

    @Insert
    suspend fun insertInstallments(installments: List<InstallmentEntity>)

    @Update
    suspend fun updateInstallment(installment: InstallmentEntity)

    @Update
    suspend fun updateInstallments(installments: List<InstallmentEntity>)

    @Query("DELETE FROM installments WHERE accountId = :accountId")
    suspend fun deleteInstallments(accountId: Long)

    /** Upcoming schedule rows across all accounts — powers dashboard and reminders. */
    @Query(
        """
        SELECT i.* FROM installments i
        INNER JOIN accounts a ON a.id = i.accountId
        WHERE i.isPaid = 0 AND a.isClosed = 0 AND i.dueDate <= :until
        ORDER BY i.dueDate ASC
        """,
    )
    suspend fun getDueInstallmentsUntil(until: Long): List<InstallmentEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM payments
        WHERE accountId = :accountId
        ORDER BY paymentDate DESC, paymentTimeMillis DESC, id DESC
        """,
    )
    fun observePayments(accountId: Long): Flow<List<PaymentEntity>>
}
