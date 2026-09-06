package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shohan.bokeya.di.AppContainer

/**
 * Builds every ViewModel from the manual [AppContainer].
 *
 * One shared factory keeps construction explicit and compile-time checked — a
 * missing dependency is a build error, not a runtime crash on some screen the
 * user opens three taps later. `createSavedStateHandle()` gives the detail
 * screens their navigation arguments and survives process death.
 */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { AppViewModel(container) }
    initializer { DashboardViewModel(container) }
    initializer { AccountsViewModel(container) }
    initializer { AccountDetailViewModel(container, createSavedStateHandle()) }
    initializer { AddAccountViewModel(container, createSavedStateHandle()) }
    initializer { PaymentViewModel(container, createSavedStateHandle()) }
    initializer { MoneyViewModel(container) }
    initializer { AddMoneyViewModel(container, createSavedStateHandle()) }
    initializer { CalendarViewModel(container) }
    initializer { HistoryViewModel(container) }
    initializer { SearchViewModel(container) }
    initializer { InsightsViewModel(container) }
    initializer { ReportsViewModel(container) }
    initializer { SettingsViewModel(container) }
    initializer { SecurityViewModel(container) }
    initializer { BackupViewModel(container) }
    initializer { RemindersViewModel(container) }
    initializer { CategoriesViewModel(container) }
}
