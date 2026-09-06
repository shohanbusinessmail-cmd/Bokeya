package com.shohan.bokeya.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shohan.bokeya.data.local.entity.BackupMetadataEntity
import com.shohan.bokeya.data.local.entity.ReminderLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    /** IGNORE on conflict is what makes reminders idempotent per (account, date, kind). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun logReminder(log: ReminderLogEntity): Long

    @Query("SELECT COUNT(*) FROM reminder_log WHERE accountId = :accountId AND dueDate = :dueDate AND kind = :kind")
    suspend fun wasNotified(accountId: Long, dueDate: Long, kind: String): Int

    @Query("DELETE FROM reminder_log WHERE notifiedAt < :before")
    suspend fun pruneOlderThan(before: Long)

    @Query("DELETE FROM reminder_log")
    suspend fun deleteAll()

    @Query("SELECT * FROM backup_metadata WHERE id = 1")
    fun observeBackupMetadata(): Flow<BackupMetadataEntity?>

    @Query("SELECT * FROM backup_metadata WHERE id = 1")
    suspend fun getBackupMetadata(): BackupMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupMetadata(metadata: BackupMetadataEntity)
}
