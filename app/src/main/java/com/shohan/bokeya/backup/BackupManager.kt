package com.shohan.bokeya.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.shohan.bokeya.BuildConfig
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.local.entity.AccountEntity
import com.shohan.bokeya.data.local.entity.BackupMetadataEntity
import com.shohan.bokeya.data.local.entity.CategoryEntity
import com.shohan.bokeya.data.local.entity.DebtItemEntity
import com.shohan.bokeya.data.local.entity.InstallmentEntity
import com.shohan.bokeya.data.local.entity.MoneyEntryEntity
import com.shohan.bokeya.data.local.entity.PaymentEntity
import com.shohan.bokeya.data.prefs.UserPreferencesRepository
import com.shohan.bokeya.data.prefs.UserSettings
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.domain.model.PaymentMethod
import com.shohan.bokeya.domain.model.ThemeMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Local JSON backup and restore.
 *
 * Backups are written to a user-chosen location through the Storage Access
 * Framework, so the app needs no storage permission and the file survives
 * uninstall. Restore is destructive but runs inside a single transaction: it
 * either fully replaces the data or leaves the existing database untouched.
 */
class BackupManager(
    private val context: Context,
    private val database: BokeyaDatabase,
    private val preferences: UserPreferencesRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    sealed interface RestoreResult {
        data class Success(val accounts: Int, val entries: Int) : RestoreResult
        data object InvalidFile : RestoreResult
        data object NewerSchema : RestoreResult
        data class Failed(val reason: String) : RestoreResult
    }

    fun suggestedFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "bokeya-backup-$stamp.json"
    }

    /** Serialises the whole database to [uri]. */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(io) {
        runCatching {
            val payload = buildBackup()
            val text = json.encodeToString(BackupFile.serializer(), payload)
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Cannot open output stream")

            val now = BanglaDate.nowMillis()
            database.reminderDao().upsertBackupMetadata(
                BackupMetadataEntity(
                    id = 1,
                    lastBackupAt = now,
                    lastBackupFile = uri.lastPathSegment,
                    schemaVersion = BackupFile.CURRENT_SCHEMA_VERSION,
                ),
            )
            preferences.setLastBackupAt(now)
            payload.accounts.size + payload.entries.size
        }
    }

    private suspend fun buildBackup(): BackupFile {
        val accountDao = database.accountDao()
        val paymentDao = database.paymentDao()
        val moneyDao = database.moneyEntryDao()
        val settings = preferences.current()

        return BackupFile(
            schemaVersion = BackupFile.CURRENT_SCHEMA_VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = BanglaDate.nowMillis(),
            accounts = accountDao.getAll().map { it.toBackup() },
            items = accountDao.getAllItems().map { it.toBackup() },
            payments = paymentDao.getAll().map { it.toBackup() },
            installments = accountDao.getAllInstallments().map { it.toBackup() },
            categories = moneyDao.getAllCategories().map { it.toBackup() },
            entries = moneyDao.getAll().map { it.toBackup() },
            settings = settings.toBackup(),
        )
    }

    /** Replaces all local data with the contents of [uri]. */
    suspend fun restoreFrom(uri: Uri): RestoreResult = withContext(io) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext RestoreResult.InvalidFile

        val payload = runCatching {
            json.decodeFromString(BackupFile.serializer(), text)
        }.getOrElse { return@withContext RestoreResult.InvalidFile }

        if (payload.schemaVersion > BackupFile.CURRENT_SCHEMA_VERSION) {
            return@withContext RestoreResult.NewerSchema
        }
        if (payload.accounts.isEmpty() && payload.entries.isEmpty() && payload.categories.isEmpty()) {
            return@withContext RestoreResult.InvalidFile
        }

        runCatching {
            val accountDao = database.accountDao()
            val paymentDao = database.paymentDao()
            val moneyDao = database.moneyEntryDao()

            database.withTransaction {
                // Order matters: children first, then parents, to respect FKs.
                moneyDao.deleteAll()
                paymentDao.deleteAll()
                accountDao.deleteAll() // cascades to items and installments
                moneyDao.deleteAllCategories()

                moneyDao.insertCategories(payload.categories.map { it.toEntity() })
                accountDao.insertAll(payload.accounts.map { it.toEntity() })
                accountDao.insertItems(payload.items.map { it.toEntity() })
                accountDao.insertInstallments(payload.installments.map { it.toEntity() })
                paymentDao.insertAll(payload.payments.map { it.toEntity() })
                moneyDao.insertAll(payload.entries.map { it.toEntity() })

                database.reminderDao().deleteAll()
                database.reminderDao().upsertBackupMetadata(
                    BackupMetadataEntity(
                        id = 1,
                        lastRestoreAt = BanglaDate.nowMillis(),
                        schemaVersion = payload.schemaVersion,
                    ),
                )
            }

            payload.settings?.let { preferences.applySettings(it.toSettings()) }
            RestoreResult.Success(payload.accounts.size, payload.entries.size)
        }.getOrElse { throwable ->
            RestoreResult.Failed(throwable.message ?: "unknown")
        }
    }

    /** Wipes every user row but keeps the seeded categories. */
    suspend fun clearAllData() = withContext(io) {
        database.withTransaction {
            database.moneyEntryDao().deleteAll()
            database.paymentDao().deleteAll()
            database.accountDao().deleteAll()
            database.reminderDao().deleteAll()
        }
    }
}

// ------------------------------------------------------------------ mapping

private fun AccountEntity.toBackup() = BackupAccount(
    id = id, type = type.name, name = name, secondaryName = secondaryName, phone = phone,
    totalAmount = totalAmount, principalAmount = principalAmount, hasInterest = hasInterest,
    interestRateBps = interestRateBps, downPayment = downPayment,
    installmentAmount = installmentAmount, frequency = frequency?.name,
    customIntervalDays = customIntervalDays, tenureCount = tenureCount,
    startDate = startDate, endDate = endDate, nextDueDate = nextDueDate,
    direction = direction?.name, note = note, isClosed = isClosed, closedAt = closedAt,
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun BackupAccount.toEntity() = AccountEntity(
    id = id,
    type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.SHOP),
    name = name, secondaryName = secondaryName, phone = phone,
    totalAmount = totalAmount, principalAmount = principalAmount, hasInterest = hasInterest,
    interestRateBps = interestRateBps, downPayment = downPayment,
    installmentAmount = installmentAmount,
    frequency = frequency?.let { runCatching { InstallmentFrequency.valueOf(it) }.getOrNull() },
    customIntervalDays = customIntervalDays, tenureCount = tenureCount,
    startDate = startDate, endDate = endDate, nextDueDate = nextDueDate,
    direction = direction?.let { runCatching { DebtDirection.valueOf(it) }.getOrNull() },
    note = note, isClosed = isClosed, closedAt = closedAt,
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun DebtItemEntity.toBackup() = BackupItem(
    id = id, accountId = accountId, name = name, quantityMilli = quantityMilli,
    unit = unit?.name, unitPrice = unitPrice, totalPrice = totalPrice,
    purchaseDate = purchaseDate, note = note, createdAt = createdAt,
)

private fun BackupItem.toEntity() = DebtItemEntity(
    id = id, accountId = accountId, name = name, quantityMilli = quantityMilli,
    unit = unit?.let { runCatching { ItemUnit.valueOf(it) }.getOrNull() },
    unitPrice = unitPrice, totalPrice = totalPrice, purchaseDate = purchaseDate,
    note = note, createdAt = createdAt,
)

private fun PaymentEntity.toBackup() = BackupPayment(
    id = id, accountId = accountId, amount = amount, paymentDate = paymentDate,
    paymentTimeMillis = paymentTimeMillis, method = method.name, installmentId = installmentId,
    note = note, createdAt = createdAt, updatedAt = updatedAt,
)

private fun BackupPayment.toEntity() = PaymentEntity(
    id = id, accountId = accountId, amount = amount, paymentDate = paymentDate,
    paymentTimeMillis = paymentTimeMillis,
    method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.CASH),
    installmentId = installmentId, note = note, createdAt = createdAt, updatedAt = updatedAt,
)

private fun InstallmentEntity.toBackup() = BackupInstallment(
    id = id, accountId = accountId, sequence = sequence, dueDate = dueDate,
    amount = amount, paidAmount = paidAmount, isPaid = isPaid, paidDate = paidDate,
)

private fun BackupInstallment.toEntity() = InstallmentEntity(
    id = id, accountId = accountId, sequence = sequence, dueDate = dueDate,
    amount = amount, paidAmount = paidAmount, isPaid = isPaid, paidDate = paidDate,
)

private fun CategoryEntity.toBackup() = BackupCategory(
    id = id, name = name, kind = kind.name, builtInKey = builtInKey,
    iconKey = iconKey, colorArgb = colorArgb, sortOrder = sortOrder, isDefault = isDefault,
)

private fun BackupCategory.toEntity() = CategoryEntity(
    id = id, name = name,
    kind = runCatching { CategoryKind.valueOf(kind) }.getOrDefault(CategoryKind.EXPENSE),
    builtInKey = builtInKey, iconKey = iconKey, colorArgb = colorArgb,
    sortOrder = sortOrder, isDefault = isDefault,
)

private fun MoneyEntryEntity.toBackup() = BackupEntry(
    id = id, isIncome = isIncome, title = title, amount = amount, categoryId = categoryId,
    entryDate = entryDate, entryTimeMillis = entryTimeMillis, note = note,
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun BackupEntry.toEntity() = MoneyEntryEntity(
    id = id, isIncome = isIncome, title = title, amount = amount, categoryId = categoryId,
    entryDate = entryDate, entryTimeMillis = entryTimeMillis, note = note,
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun UserSettings.toBackup() = BackupSettings(
    userName = userName, currencySymbol = currencySymbol, themeMode = themeMode.name,
    useDynamicColor = useDynamicColor, useBengaliDigits = useBengaliDigits,
    notificationsEnabled = notificationsEnabled, reminderDaysBefore = reminderDaysBefore,
    reminderSameDay = reminderSameDay, reminderOverdue = reminderOverdue,
    reminderTimeMinutes = reminderTimeMinutes,
)

private fun BackupSettings.toSettings() = UserSettings(
    userName = userName, currencySymbol = currencySymbol,
    themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
    useDynamicColor = useDynamicColor, useBengaliDigits = useBengaliDigits,
    notificationsEnabled = notificationsEnabled, reminderDaysBefore = reminderDaysBefore,
    reminderSameDay = reminderSameDay, reminderOverdue = reminderOverdue,
    reminderTimeMinutes = reminderTimeMinutes,
)
