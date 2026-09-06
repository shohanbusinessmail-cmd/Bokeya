package com.shohan.bokeya.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.domain.model.PaymentMethod

/**
 * A single table backs all four account kinds (shop / loan / EMI / person).
 *
 * They share ~80% of their columns (name, totals, dates, notes, status) and the
 * dashboard constantly needs "all dues across every type" — one table keeps
 * those queries a single indexed scan instead of a four-way UNION. Type-specific
 * columns are nullable and validated in the domain layer.
 *
 * All money columns hold **poisha** (minor units); all dates hold **epoch days**;
 * all timestamps hold **epoch millis**.
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index("type"),
        Index("isClosed"),
        Index("nextDueDate"),
        Index("name"),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val type: AccountType,

    /** Shop name / loan name / product name / person name. */
    val name: String,

    /** Bank or NGO name, EMI seller, or the person's relationship. */
    val secondaryName: String? = null,

    val phone: String? = null,

    /**
     * The full amount owed.
     * SHOP: sum of all items. LOAN: total payable (principal + interest).
     * EMI: financed amount (cash price - down payment). PERSON: amount borrowed/lent.
     */
    val totalAmount: Long,

    /** LOAN: principal before interest. EMI: cash price. Null elsewhere. */
    val principalAmount: Long? = null,

    val hasInterest: Boolean = false,

    /** Stored x100 (e.g. 12.5% -> 1250) to keep it an exact integer. */
    val interestRateBps: Int? = null,

    /** EMI down payment, in poisha. */
    val downPayment: Long? = null,

    val installmentAmount: Long? = null,

    val frequency: InstallmentFrequency? = null,

    /** Interval in days when [frequency] is CUSTOM. */
    val customIntervalDays: Int? = null,

    /** Number of installments for EMI / loan schedules. */
    val tenureCount: Int? = null,

    val startDate: Long,

    /** Deadline for a person debt, or the expected final installment date. */
    val endDate: Long? = null,

    /** Cached next unpaid due date (epoch day) so dashboards need no recomputation. */
    val nextDueDate: Long? = null,

    val direction: DebtDirection? = null,

    val note: String? = null,

    /** Set once the balance reaches zero; keeps paid accounts out of active queries. */
    val isClosed: Boolean = false,

    val closedAt: Long? = null,

    val createdAt: Long,

    val updatedAt: Long,
)

/** One line of a shop purchase: চাল — ৫ কেজি — ৪০০ টাকা. */
@Entity(
    tableName = "debt_items",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("purchaseDate")],
)
data class DebtItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,

    /** Stored x1000 so ২.৫ কেজি is exact (2500). Null for lump-sum entries. */
    val quantityMilli: Long? = null,

    val unit: ItemUnit? = null,

    /** Price per unit in poisha. */
    val unitPrice: Long? = null,

    /** Line total in poisha — always authoritative, even for lump-sum rows. */
    val totalPrice: Long,

    val purchaseDate: Long,
    val note: String? = null,
    val createdAt: Long,
)

/** A payment against any account type. Partial payments are the norm. */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("paymentDate"), Index("installmentId")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Long,
    val paymentDate: Long,

    /** Wall-clock time of day, kept alongside the date for the timeline. */
    val paymentTimeMillis: Long,

    val method: PaymentMethod = PaymentMethod.CASH,

    /** Links the payment to a scheduled installment when one was settled. */
    val installmentId: Long? = null,

    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A generated row in a loan or EMI repayment schedule. */
@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("dueDate"), Index("isPaid")],
)
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,

    /** 1-based position in the schedule. */
    val sequence: Int,

    val dueDate: Long,
    val amount: Long,

    /** Running total actually paid against this installment. */
    val paidAmount: Long = 0,

    val isPaid: Boolean = false,
    val paidDate: Long? = null,
)

/** Income and expense entries share a table; [kind] on the category separates them. */
@Entity(
    tableName = "money_entries",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("entryDate"), Index("isIncome")],
)
data class MoneyEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isIncome: Boolean,
    val title: String,
    val amount: Long,
    val categoryId: Long? = null,
    val entryDate: Long,
    val entryTimeMillis: Long,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "kind"], unique = true), Index("kind")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,

    /** Stable key for the built-in categories, so they survive re-seeding. */
    val builtInKey: String? = null,

    /** Material icon name resolved by the UI layer. */
    val iconKey: String? = null,

    /** ARGB colour for charts and chips. */
    val colorArgb: Int? = null,

    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
)

/**
 * Records that a reminder notification was already delivered, so the worker
 * never posts the same reminder twice for the same day.
 */
@Entity(
    tableName = "reminder_log",
    indices = [Index(value = ["accountId", "dueDate", "kind"], unique = true)],
)
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val dueDate: Long,

    /** UPCOMING / TODAY / OVERDUE. */
    val kind: String,

    val notifiedAt: Long,
)

/** Bookkeeping for the local backup file, shown in Settings. */
@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "lastBackupAt") val lastBackupAt: Long? = null,
    @ColumnInfo(name = "lastBackupFile") val lastBackupFile: String? = null,
    @ColumnInfo(name = "lastRestoreAt") val lastRestoreAt: Long? = null,
    @ColumnInfo(name = "schemaVersion") val schemaVersion: Int = 1,
)
