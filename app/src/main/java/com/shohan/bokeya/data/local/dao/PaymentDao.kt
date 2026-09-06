package com.shohan.bokeya.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shohan.bokeya.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

/** A payment joined with its account name, for timelines that span accounts. */
data class PaymentWithAccount(
    val id: Long,
    val accountId: Long,
    val accountName: String,
    val accountType: String,
    val amount: Long,
    val paymentDate: Long,
    val paymentTimeMillis: Long,
    val method: String,
    val note: String?,
)

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, id DESC")
    suspend fun getAll(): List<PaymentEntity>

    @Query(
        """
        SELECT p.id, p.accountId, a.name AS accountName, a.type AS accountType,
               p.amount, p.paymentDate, p.paymentTimeMillis, p.method, p.note
        FROM payments p
        INNER JOIN accounts a ON a.id = p.accountId
        ORDER BY p.paymentDate DESC, p.paymentTimeMillis DESC, p.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<PaymentWithAccount>>

    @Query(
        """
        SELECT p.id, p.accountId, a.name AS accountName, a.type AS accountType,
               p.amount, p.paymentDate, p.paymentTimeMillis, p.method, p.note
        FROM payments p
        INNER JOIN accounts a ON a.id = p.accountId
        WHERE p.paymentDate BETWEEN :from AND :to
        ORDER BY p.paymentDate DESC, p.paymentTimeMillis DESC, p.id DESC
        """,
    )
    fun observeBetween(from: Long, to: Long): Flow<List<PaymentWithAccount>>

    @Query(
        """
        SELECT p.id, p.accountId, a.name AS accountName, a.type AS accountType,
               p.amount, p.paymentDate, p.paymentTimeMillis, p.method, p.note
        FROM payments p
        INNER JOIN accounts a ON a.id = p.accountId
        WHERE p.paymentDate BETWEEN :from AND :to
        ORDER BY p.paymentDate DESC, p.id DESC
        """,
    )
    suspend fun getBetween(from: Long, to: Long): List<PaymentWithAccount>

    @Query(
        """
        SELECT p.id, p.accountId, a.name AS accountName, a.type AS accountType,
               p.amount, p.paymentDate, p.paymentTimeMillis, p.method, p.note
        FROM payments p
        INNER JOIN accounts a ON a.id = p.accountId
        WHERE IFNULL(p.note, '') LIKE '%' || :query || '%'
           OR a.name LIKE '%' || :query || '%'
        ORDER BY p.paymentDate DESC
        LIMIT 50
        """,
    )
    fun search(query: String): Flow<List<PaymentWithAccount>>

    @Query("SELECT IFNULL(SUM(amount), 0) FROM payments WHERE accountId = :accountId")
    suspend fun sumForAccount(accountId: Long): Long

    @Query("SELECT IFNULL(SUM(amount), 0) FROM payments WHERE paymentDate BETWEEN :from AND :to")
    fun observeSumBetween(from: Long, to: Long): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}
