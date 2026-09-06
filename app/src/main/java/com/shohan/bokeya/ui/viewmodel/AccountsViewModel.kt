package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.prefs.UserSettings
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.domain.model.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUiState(
    val isLoading: Boolean = true,
    val selectedType: AccountType? = null,
    val showPaid: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val totalsByType: Map<AccountType, Money> = emptyMap(),
    val totalDue: Money = Money.ZERO,
    val settings: UserSettings = UserSettings(),
) {
    val isEmpty: Boolean get() = !isLoading && accounts.isEmpty()
}

class AccountsViewModel(private val container: AppContainer) : ViewModel() {

    private val selectedType = MutableStateFlow<AccountType?>(null)
    private val showPaid = MutableStateFlow(false)

    val uiState: StateFlow<AccountsUiState> = combine(
        container.accountRepository.observeAccounts(),
        selectedType,
        showPaid,
        container.preferences.settings,
    ) { accounts, type, includePaid, settings ->
        // Totals always reflect every open account, not the current filter, so
        // the header doesn't jump around while the user browses tabs.
        val open = accounts.filter { !it.isClosed && it.remaining.isPositive }
        val totals = AccountType.entries.associateWith { accountType ->
            Money(open.filter { it.type == accountType }.sumOf { it.remaining.minor })
        }

        val visible = accounts
            .filter { type == null || it.type == type }
            .filter { includePaid || !(it.isClosed || it.status == AccountStatus.PAID) }
            .sortedWith(
                compareBy<Account> { it.isClosed }
                    .thenBy { statusRank(it.status) }
                    .thenBy { it.nextDueDate ?: java.time.LocalDate.MAX },
            )

        AccountsUiState(
            isLoading = false,
            selectedType = type,
            showPaid = includePaid,
            accounts = visible,
            totalsByType = totals,
            totalDue = Money(open.sumOf { it.remaining.minor }),
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    /** Overdue first, then today, then soon — the order a user cares about. */
    private fun statusRank(status: AccountStatus): Int = when (status) {
        AccountStatus.OVERDUE -> 0
        AccountStatus.DUE_TODAY -> 1
        AccountStatus.DUE_SOON -> 2
        AccountStatus.ACTIVE -> 3
        AccountStatus.PAID -> 4
    }

    fun selectType(type: AccountType?) {
        selectedType.value = type
    }

    fun toggleShowPaid() {
        showPaid.value = !showPaid.value
    }

    private var lastDeleted: Long? = null

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            lastDeleted = accountId
            container.accountRepository.deleteAccount(accountId)
        }
    }
}
