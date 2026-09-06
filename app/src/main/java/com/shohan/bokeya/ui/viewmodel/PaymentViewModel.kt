package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.domain.calc.BalanceCalculator
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.PaymentMethod
import com.shohan.bokeya.ui.navigation.Routes
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentUiState(
    val isLoading: Boolean = true,
    val account: Account? = null,
    val amount: String = "",
    val date: LocalDate = BanglaDate.today(),
    val time: LocalTime = LocalTime.now(),
    val method: PaymentMethod = PaymentMethod.CASH,
    val note: String = "",
    val isSaving: Boolean = false,
    val amountError: Int? = null,
    val success: BalanceCalculator.PaymentOutcome? = null,
    val useBengaliDigits: Boolean = true,
) {
    val parsedAmount: Money? get() = Money.parseOrNull(amount)

    /** Warn (don't block) when the user pays more than the balance. */
    val isOverpaying: Boolean
        get() {
            val entered = parsedAmount ?: return false
            val remaining = account?.remaining ?: return false
            return entered > remaining
        }
}

/**
 * Backs the payment bottom sheet.
 *
 * Overpayment is allowed but flagged: users really do hand over a rounded
 * amount, and silently rejecting it would be worse than recording the truth.
 */
class PaymentViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<String>(Routes.ARG_ACCOUNT_ID)?.toLongOrNull()
        ?: savedStateHandle.get<Long>(Routes.ARG_ACCOUNT_ID)
        ?: -1L

    private val _state = MutableStateFlow(PaymentUiState())
    val state: StateFlow<PaymentUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val account = container.accountRepository.getAccount(accountId)
            val settings = container.preferences.current()
            _state.update {
                it.copy(
                    isLoading = false,
                    account = account,
                    useBengaliDigits = settings.useBengaliDigits,
                )
            }
        }
    }

    fun setAmount(value: String) = _state.update { it.copy(amount = value, amountError = null) }

    /** "পুরোটা" shortcut — fills in the exact remaining balance. */
    fun fillFullAmount() = _state.update { current ->
        val remaining = current.account?.remaining ?: return@update current
        current.copy(
            amount = com.shohan.bokeya.core.money.MoneyFormatter.formatPlain(remaining),
            amountError = null,
        )
    }

    fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }
    fun setTime(value: LocalTime) = _state.update { it.copy(time = value) }
    fun setMethod(value: PaymentMethod) = _state.update { it.copy(method = value) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }

    fun save() {
        val current = _state.value
        val amount = current.parsedAmount

        if (amount == null) {
            _state.update { it.copy(amountError = R.string.err_amount_required) }
            return
        }
        if (!amount.isPositive) {
            _state.update { it.copy(amountError = R.string.err_amount_positive) }
            return
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                container.accountRepository.recordPayment(
                    accountId = accountId,
                    amount = amount,
                    date = current.date,
                    timeMillis = BanglaDate.toEpochMillis(current.date, current.time),
                    method = current.method,
                    note = current.note,
                )
            }.onSuccess { outcome ->
                _state.update { it.copy(isSaving = false, success = outcome) }
                // Dues changed, so the reminder schedule may need re-arming.
                runCatching {
                    container.reminderScheduler.schedule(container.preferences.current())
                }
            }.onFailure {
                _state.update { it.copy(isSaving = false, amountError = R.string.err_save_failed) }
            }
        }
    }

    fun dismissSuccess() = _state.update { it.copy(success = null) }
}
