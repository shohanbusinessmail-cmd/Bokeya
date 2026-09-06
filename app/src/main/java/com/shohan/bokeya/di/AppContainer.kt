package com.shohan.bokeya.di

import android.content.Context
import com.shohan.bokeya.backup.BackupManager
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.prefs.UserPreferencesRepository
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.LedgerRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.usecase.DashboardUseCase
import com.shohan.bokeya.domain.usecase.InsightsUseCase
import com.shohan.bokeya.export.ExportManager
import com.shohan.bokeya.notification.ReminderNotifier
import com.shohan.bokeya.notification.ReminderScheduler
import com.shohan.bokeya.security.PinManager

/**
 * Manual dependency container.
 *
 * A financial app this size doesn't need Hilt: there is exactly one graph, all
 * of it application-scoped, and everything is lazily constructed. Avoiding the
 * annotation processor keeps builds fast and the dependency list short — which
 * matters when the whole point is "no unnecessary third-party SDKs".
 */
class AppContainer(private val context: Context) {

    val database: BokeyaDatabase by lazy { BokeyaDatabase.get(context) }

    val preferences: UserPreferencesRepository by lazy { UserPreferencesRepository(context) }

    val accountRepository: AccountRepository by lazy { AccountRepository(database) }

    val moneyRepository: MoneyRepository by lazy { MoneyRepository(database) }

    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(database, accountRepository, moneyRepository)
    }

    val dashboardUseCase: DashboardUseCase by lazy {
        DashboardUseCase(accountRepository, moneyRepository)
    }

    val insightsUseCase: InsightsUseCase by lazy {
        InsightsUseCase(context, accountRepository, moneyRepository)
    }

    val pinManager: PinManager by lazy { PinManager(context) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }

    val reminderNotifier: ReminderNotifier by lazy { ReminderNotifier(context) }

    val backupManager: BackupManager by lazy {
        BackupManager(context, database, preferences)
    }

    val exportManager: ExportManager by lazy {
        ExportManager(context, database, accountRepository, moneyRepository)
    }
}
