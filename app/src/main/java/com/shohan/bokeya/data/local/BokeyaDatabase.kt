package com.shohan.bokeya.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shohan.bokeya.data.local.dao.AccountDao
import com.shohan.bokeya.data.local.dao.MoneyEntryDao
import com.shohan.bokeya.data.local.dao.PaymentDao
import com.shohan.bokeya.data.local.dao.ReminderDao
import com.shohan.bokeya.data.local.entity.AccountEntity
import com.shohan.bokeya.data.local.entity.BackupMetadataEntity
import com.shohan.bokeya.data.local.entity.CategoryEntity
import com.shohan.bokeya.data.local.entity.DebtItemEntity
import com.shohan.bokeya.data.local.entity.InstallmentEntity
import com.shohan.bokeya.data.local.entity.MoneyEntryEntity
import com.shohan.bokeya.data.local.entity.PaymentEntity
import com.shohan.bokeya.data.local.entity.ReminderLogEntity

@Database(
    entities = [
        AccountEntity::class,
        DebtItemEntity::class,
        PaymentEntity::class,
        InstallmentEntity::class,
        MoneyEntryEntity::class,
        CategoryEntity::class,
        ReminderLogEntity::class,
        BackupMetadataEntity::class,
    ],
    version = BokeyaDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BokeyaDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun paymentDao(): PaymentDao
    abstract fun moneyEntryDao(): MoneyEntryDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val VERSION = 1
        const val NAME = "bokeya.db"

        @Volatile
        private var instance: BokeyaDatabase? = null

        fun get(context: Context): BokeyaDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): BokeyaDatabase =
            Room.databaseBuilder(context, BokeyaDatabase::class.java, NAME)
                // Foreign keys are declared on the entities; SQLite still needs
                // them switched on per connection to enforce cascade deletes.
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                // Migrations are added here as the schema evolves. There is
                // deliberately NO fallbackToDestructiveMigration(): losing a
                // user's financial history on upgrade is never acceptable.
                .addMigrations(*MIGRATIONS)
                .build()

        /**
         * Registry of schema migrations. Empty at v1; each future version adds
         * one entry and a matching JSON schema under `app/schemas/`.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        /** Test hook — lets instrumentation swap in an in-memory database. */
        internal fun setInstanceForTesting(database: BokeyaDatabase?) {
            instance = database
        }
    }
}
