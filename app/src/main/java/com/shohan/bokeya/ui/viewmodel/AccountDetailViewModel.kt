package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.data.prefs.UserSettings
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.domain.model.AccountDetail
import com.shohan.bokeya.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val detail: AccountDetail? = null,
    val settings: UserSettings = UserSettings(),
    val notFound: Boolean = false,
)

sealed interface DetailEvent {
    data class PaymentDeleted(val amountLabel: String) : DetailEvent
    data object AccountDeleted : DetailEvent
    data class Error(val messageRes: Int) : DetailEvent
}

class AccountDetailViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val accountId: Long = savedStateHandle.get<String>(Routes.ARG_ACCOUNT_ID)?.toLongOrNull()
        ?: savedStateHandle.get<Long>(Routes.ARG_ACCOUNT_ID)
        ?: -1L

    private val _events = MutableSharedFlow<DetailEvent>()
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    val uiState: StateFlow<AccountDetailUiState> = combine(
        container.accountRepository.observeAccountDetail(accountId),
        container.preferences.settings,
    ) { detail, settings ->
        AccountDetailUiState(
            isLoading = false,
            detail = detail,
            settings = settings,
            notFound = detail == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState())

    fun deletePayment(paymentId: Long, amountLabel: String) {
        viewModelScope.launch {
            runCatching { container.accountRepository.deletePayment(paymentId) }
                .onSuccess { _events.emit(DetailEvent.PaymentDeleted(amountLabel)) }
                .onFailure { _events.emit(DetailEvent.Error(com.shohan.bokeya.R.string.err_delete_failed)) }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            runCatching { container.accountRepository.deleteAccount(accountId) }
                .onSuccess { _events.emit(DetailEvent.AccountDeleted) }
                .onFailure { _events.emit(DetailEvent.Error(com.shohan.bokeya.R.string.err_delete_failed)) }
        }
    }

    /** Settles the whole outstanding balance in one payment. */
    fun markFullyPaid() {
        viewModelScope.launch {
            runCatching { container.accountRepository.closeAccount(accountId) }
                .onFailure { _events.emit(DetailEvent.Error(com.shohan.bokeya.R.string.err_save_failed)) }
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            runCatching { container.accountRepository.deleteItem(accountId, itemId) }
                .onFailure { _events.emit(DetailEvent.Error(com.shohan.bokeya.R.string.err_delete_failed)) }
        }
    }
}
