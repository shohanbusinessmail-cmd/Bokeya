package com.shohan.bokeya.backup

import kotlinx.serialization.Serializable

/**
 * The on-disk backup format.
 *
 * This is a **versioned public contract**, not a mirror of the Room entities:
 * a restore must keep working after the database schema changes. Every field is
 * optional-with-default so an older backup still deserialises into a newer app,
 * and [BackupFile.schemaVersion] guards the reverse case.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "",
    val createdAt: Long = 0L,
    val accounts: List<BackupAccount> = emptyList(),
    val items: List<BackupItem> = emptyList(),
    val payments: List<BackupPayment> = emptyList(),
    val installments: List<BackupInstallment> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val entries: List<BackupEntry> = emptyList(),
    val settings: BackupSettings? = null,
) {
    companion object {
        /** Bump when the format changes incompatibly. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupAccount(
    val id: Long,
    val type: String,
    val name: String,
    val secondaryName: String? = null,
    val phone: String? = null,
    val totalAmount: Long,
    val principalAmount: Long? = null,
    val hasInterest: Boolean = false,
    val interestRateBps: Int? = null,
    val downPayment: Long? = null,
    val installmentAmount: Long? = null,
    val frequency: String? = null,
    val customIntervalDays: Int? = null,
    val tenureCount: Int? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val nextDueDate: Long? = null,
    val direction: String? = null,
    val note: String? = null,
    val isClosed: Boolean = false,
    val closedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class BackupItem(
    val id: Long,
    val accountId: Long,
    val name: String,
    val quantityMilli: Long? = null,
    val unit: String? = null,
    val unitPrice: Long? = null,
    val totalPrice: Long,
    val purchaseDate: Long,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupPayment(
    val id: Long,
    val accountId: Long,
    val amount: Long,
    val paymentDate: Long,
    val paymentTimeMillis: Long,
    val method: String = "CASH",
    val installmentId: Long? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class BackupInstallment(
    val id: Long,
    val accountId: Long,
    val sequence: Int,
    val dueDate: Long,
    val amount: Long,
    val paidAmount: Long = 0,
    val isPaid: Boolean = false,
    val paidDate: Long? = null,
)

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val kind: String,
    val builtInKey: String? = null,
    val iconKey: String? = null,
    val colorArgb: Int? = null,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
)

@Serializable
data class BackupEntry(
    val id: Long,
    val isIncome: Boolean,
    val title: String,
    val amount: Long,
    val categoryId: Long? = null,
    val entryDate: Long,
    val entryTimeMillis: Long,
    val note: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Settings worth carrying across devices. The PIN is deliberately excluded. */
@Serializable
data class BackupSettings(
    val userName: String = "",
    val currencySymbol: String = "৳",
    val themeMode: String = "SYSTEM",
    val useDynamicColor: Boolean = false,
    val useBengaliDigits: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val reminderDaysBefore: Int = 1,
    val reminderSameDay: Boolean = true,
    val reminderOverdue: Boolean = true,
    val reminderTimeMinutes: Int = 540,
)
