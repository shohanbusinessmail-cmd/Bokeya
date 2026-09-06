package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.domain.calc.ScheduleGenerator
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.ui.navigation.Routes
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable line in the shop item list. */
data class ItemDraft(
    val id: Long = System.nanoTime(),
    val name: String = "",
    val quantity: String = "",
    val unit: ItemUnit = ItemUnit.KG,
    val unitPrice: String = "",
    val total: String = "",
) {
    /** Quantity x unit price, when both are present. */
    val computedTotal: Money?
        get() {
            val qty = quantity.toDoubleOrNull()
            val price = Money.parseOrNull(unitPrice)
            return if (qty != null && qty > 0 && price != null) {
                Money(Math.round(price.minor * qty))
            } else {
                null
            }
        }

    val effectiveTotal: Money?
        get() = computedTotal ?: Money.parseOrNull(total)
}

data class AddAccountUiState(
    val type: AccountType = AccountType.SHOP,
    val editingAccountId: Long? = null,
    val isSaving: Boolean = false,

    // Shared
    val name: String = "",
    val secondaryName: String = "",
    val phone: String = "",
    val note: String = "",
    val startDate: LocalDate = BanglaDate.today(),
    val dueDate: LocalDate? = null,

    // Shop
    val detailedItems: Boolean = true,
    val items: List<ItemDraft> = listOf(ItemDraft()),
    val lumpAmount: String = "",
    val initialPayment: String = "",

    // Loan
    val loanAmount: String = "",
    val hasInterest: Boolean = false,
    val interestRate: String = "",
    val payableAmount: String = "",
    val installmentAmount: String = "",
    val frequency: InstallmentFrequency = InstallmentFrequency.MONTHLY,
    val customDays: String = "30",
    val firstDueDate: LocalDate = BanglaDate.today().plusMonths(1),
    val alreadyPaid: String = "",

    // EMI
    val cashPrice: String = "",
    val downPayment: String = "",
    val tenureMonths: String = "",
    val emiAmount: String = "",

    // Person
    val direction: DebtDirection = DebtDirection.BORROWED,
    val personAmount: String = "",

    val errors: Map<String, Int> = emptyMap(),
) {
    val itemsTotal: Money
        get() = if (detailedItems) {
            Money(items.mapNotNull { it.effectiveTotal }.sumOf { it.minor })
        } else {
            Money.parseOrNull(lumpAmount) ?: Money.ZERO
        }

    /** Live preview of the EMI/loan schedule shown under the form. */
    val financedAmount: Money
        get() = ((Money.parseOrNull(cashPrice) ?: Money.ZERO) - (Money.parseOrNull(downPayment) ?: Money.ZERO))
            .coerceAtLeastZero()

    val computedEmi: Money?
        get() {
            val months = tenureMonths.toIntOrNull() ?: return null
            if (months <= 0) return null
            val financed = financedAmount
            if (financed.isZero) return null
            return financed.split(months).firstOrNull()
        }

    val suggestedPayable: Money?
        get() {
            if (!hasInterest) return Money.parseOrNull(loanAmount)
            val principal = Money.parseOrNull(loanAmount) ?: return null
            val rate = interestRate.toDoubleOrNull() ?: return null
            val months = estimatedMonths() ?: return null
            return ScheduleGenerator.simpleInterestPayable(principal, rate, months)
        }

    private fun estimatedMonths(): Int? {
        val installment = Money.parseOrNull(installmentAmount) ?: return null
        val principal = Money.parseOrNull(loanAmount) ?: return null
        if (installment.isZero) return null
        val count = ((principal.minor + installment.minor - 1) / installment.minor).toInt()
        return when (frequency) {
            InstallmentFrequency.MONTHLY -> count
            InstallmentFrequency.WEEKLY -> (count / 4.34).toInt().coerceAtLeast(1)
            InstallmentFrequency.DAILY -> (count / 30.0).toInt().coerceAtLeast(1)
            InstallmentFrequency.CUSTOM -> {
                val days = customDays.toIntOrNull() ?: 30
                ((count * days) / 30.0).toInt().coerceAtLeast(1)
            }
        }
    }
}

sealed interface AddAccountEvent {
    data class Saved(val accountId: Long, val type: AccountType) : AddAccountEvent
    data class Error(val messageRes: Int) : AddAccountEvent
}

/**
 * Drives every "add account" form.
 *
 * All four account types share this ViewModel because the flow is identical —
 * validate, build a domain object, delegate to the repository — and only the
 * field set differs. Validation returns *field-keyed* errors so the UI can put
 * the Bangla message under the exact input that needs fixing.
 */
class AddAccountViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val typeArg: AccountType = savedStateHandle.get<String>(Routes.ARG_TYPE)
        ?.let { runCatching { AccountType.valueOf(it) }.getOrNull() }
        ?: AccountType.SHOP

    private val editingId: Long? = savedStateHandle.get<String>(Routes.ARG_ACCOUNT_ID)
        ?.toLongOrNull()
        ?.takeIf { it > 0 }

    private val _state = MutableStateFlow(
        AddAccountUiState(type = typeArg, editingAccountId = editingId),
    )
    val state: StateFlow<AddAccountUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddAccountEvent>()
    val events: SharedFlow<AddAccountEvent> = _events.asSharedFlow()

    val useBengaliDigits: Boolean
        get() = true

    init {
        if (editingId != null) loadExisting(editingId)
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val entity = container.accountRepository.getAccountEntity(id) ?: return@launch
            _state.update { current ->
                current.copy(
                    type = entity.type,
                    name = entity.name,
                    secondaryName = entity.secondaryName.orEmpty(),
                    phone = entity.phone.orEmpty(),
                    note = entity.note.orEmpty(),
                    startDate = BanglaDate.fromEpochDay(entity.startDate),
                    dueDate = entity.endDate?.let(BanglaDate::fromEpochDay),
                    direction = entity.direction ?: DebtDirection.BORROWED,
                    personAmount = plain(entity.totalAmount),
                    lumpAmount = plain(entity.totalAmount),
                    detailedItems = false,
                )
            }
        }
    }

    private fun plain(minor: Long) = com.shohan.bokeya.core.money.MoneyFormatter.formatPlain(Money(minor))

    // ------------------------------------------------------------- field sets

    fun setName(value: String) = _state.update { it.copy(name = value, errors = it.errors - KEY_NAME) }
    fun setSecondaryName(value: String) = _state.update { it.copy(secondaryName = value) }
    fun setPhone(value: String) = _state.update { it.copy(phone = value) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setStartDate(value: LocalDate) = _state.update { it.copy(startDate = value) }
    fun setDueDate(value: LocalDate?) = _state.update { it.copy(dueDate = value) }
    fun setDetailedItems(value: Boolean) = _state.update { it.copy(detailedItems = value, errors = emptyMap()) }
    fun setLumpAmount(value: String) = _state.update { it.copy(lumpAmount = value, errors = it.errors - KEY_AMOUNT) }
    fun setInitialPayment(value: String) = _state.update { it.copy(initialPayment = value) }

    fun setLoanAmount(value: String) = _state.update { it.copy(loanAmount = value, errors = it.errors - KEY_AMOUNT) }
    fun setHasInterest(value: Boolean) = _state.update { it.copy(hasInterest = value) }
    fun setInterestRate(value: String) = _state.update { it.copy(interestRate = value, errors = it.errors - KEY_INTEREST) }
    fun setPayableAmount(value: String) = _state.update { it.copy(payableAmount = value, errors = it.errors - KEY_PAYABLE) }
    fun setInstallmentAmount(value: String) =
        _state.update { it.copy(installmentAmount = value, errors = it.errors - KEY_INSTALLMENT) }
    fun setFrequency(value: InstallmentFrequency) = _state.update { it.copy(frequency = value) }
    fun setCustomDays(value: String) = _state.update { it.copy(customDays = value, errors = it.errors - KEY_DAYS) }
    fun setFirstDueDate(value: LocalDate) = _state.update { it.copy(firstDueDate = value) }
    fun setAlreadyPaid(value: String) = _state.update { it.copy(alreadyPaid = value) }

    fun setCashPrice(value: String) = _state.update { it.copy(cashPrice = value, errors = it.errors - KEY_AMOUNT) }
    fun setDownPayment(value: String) = _state.update { it.copy(downPayment = value, errors = it.errors - KEY_DOWN) }
    fun setTenureMonths(value: String) = _state.update { it.copy(tenureMonths = value, errors = it.errors - KEY_TENURE) }
    fun setEmiAmount(value: String) = _state.update { it.copy(emiAmount = value) }

    fun setDirection(value: DebtDirection) = _state.update { it.copy(direction = value) }
    fun setPersonAmount(value: String) = _state.update { it.copy(personAmount = value, errors = it.errors - KEY_AMOUNT) }

    // ------------------------------------------------------------------ items

    fun addItem() = _state.update { it.copy(items = it.items + ItemDraft()) }

    fun removeItem(id: Long) = _state.update { current ->
        val remaining = current.items.filterNot { it.id == id }
        current.copy(items = remaining.ifEmpty { listOf(ItemDraft()) })
    }

    fun updateItem(id: Long, transform: (ItemDraft) -> ItemDraft) = _state.update { current ->
        current.copy(items = current.items.map { if (it.id == id) transform(it) else it })
    }

    // ----------------------------------------------------------------- saving

    fun save() {
        val current = _state.value
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                if (current.editingAccountId != null) {
                    updateExisting(current)
                    current.editingAccountId
                } else {
                    when (current.type) {
                        AccountType.SHOP -> saveShop(current)
                        AccountType.LOAN -> saveLoan(current)
                        AccountType.EMI -> saveEmi(current)
                        AccountType.PERSON -> savePerson(current)
                    }
                }
            }.onSuccess { id ->
                _state.update { it.copy(isSaving = false) }
                container.reminderScheduler.schedule(container.preferences.current())
                _events.emit(AddAccountEvent.Saved(id, current.type))
            }.onFailure {
                _state.update { it.copy(isSaving = false) }
                _events.emit(AddAccountEvent.Error(R.string.err_save_failed))
            }
        }
    }

    private suspend fun updateExisting(state: AddAccountUiState): Long {
        val id = state.editingAccountId!!
        container.accountRepository.updateAccountBasics(
            accountId = id,
            name = state.name,
            secondaryName = state.secondaryName,
            phone = state.phone,
            note = state.note,
            dueDate = state.dueDate,
        )
        // Types without an item list keep an editable headline amount.
        val newTotal = when (state.type) {
            AccountType.PERSON -> Money.parseOrNull(state.personAmount)
            AccountType.SHOP -> if (!state.detailedItems) Money.parseOrNull(state.lumpAmount) else null
            else -> null
        }
        if (newTotal != null && newTotal.isPositive) {
            container.accountRepository.updateAccountTotal(id, newTotal)
        }
        return id
    }

    private suspend fun saveShop(state: AddAccountUiState): Long {
        val items = if (state.detailedItems) {
            state.items
                .filter { it.name.isNotBlank() && (it.effectiveTotal?.isPositive == true) }
                .map { draft ->
                    AccountRepository.ItemInput(
                        name = draft.name,
                        quantity = draft.quantity.toDoubleOrNull(),
                        unit = draft.unit,
                        unitPrice = Money.parseOrNull(draft.unitPrice),
                        total = draft.effectiveTotal ?: Money.ZERO,
                    )
                }
        } else {
            listOf(
                AccountRepository.ItemInput(
                    name = state.name.trim().ifBlank { "বাকি" },
                    quantity = null,
                    unit = null,
                    unitPrice = null,
                    total = Money.parseOrNull(state.lumpAmount) ?: Money.ZERO,
                ),
            )
        }

        return container.accountRepository.createShopAccount(
            name = state.name,
            items = items,
            purchaseDate = state.startDate,
            initialPayment = Money.parseOrNull(state.initialPayment) ?: Money.ZERO,
            dueDate = state.dueDate,
            note = state.note,
        )
    }

    private suspend fun saveLoan(state: AddAccountUiState): Long {
        val principal = Money.parseOrNull(state.loanAmount) ?: Money.ZERO
        val payable = Money.parseOrNull(state.payableAmount)
            ?: state.suggestedPayable
            ?: principal
        return container.accountRepository.createLoanAccount(
            loanName = state.name,
            organisation = state.secondaryName,
            principal = principal,
            payable = payable,
            hasInterest = state.hasInterest,
            interestRatePercent = state.interestRate.toDoubleOrNull(),
            installmentAmount = Money.parseOrNull(state.installmentAmount) ?: Money.ZERO,
            frequency = state.frequency,
            customIntervalDays = state.customDays.toIntOrNull(),
            startDate = state.startDate,
            firstDueDate = state.firstDueDate,
            alreadyPaid = Money.parseOrNull(state.alreadyPaid) ?: Money.ZERO,
            note = state.note,
        )
    }

    private suspend fun saveEmi(state: AddAccountUiState): Long =
        container.accountRepository.createEmiAccount(
            productName = state.name,
            seller = state.secondaryName,
            cashPrice = Money.parseOrNull(state.cashPrice) ?: Money.ZERO,
            downPayment = Money.parseOrNull(state.downPayment) ?: Money.ZERO,
            tenureMonths = state.tenureMonths.toIntOrNull() ?: 1,
            installmentAmount = Money.parseOrNull(state.emiAmount),
            startDate = state.startDate,
            firstDueDate = state.firstDueDate,
            note = state.note,
        )

    private suspend fun savePerson(state: AddAccountUiState): Long =
        container.accountRepository.createPersonAccount(
            personName = state.name,
            relationship = state.secondaryName,
            phone = state.phone,
            amount = Money.parseOrNull(state.personAmount) ?: Money.ZERO,
            direction = state.direction,
            borrowDate = state.startDate,
            returnDate = state.dueDate,
            alreadyPaid = Money.parseOrNull(state.alreadyPaid) ?: Money.ZERO,
            note = state.note,
        )

    // ------------------------------------------------------------- validation

    private fun validate(state: AddAccountUiState): Map<String, Int> {
        val errors = mutableMapOf<String, Int>()

        if (state.name.isBlank()) errors[KEY_NAME] = R.string.err_name_required

        when (state.type) {
            AccountType.SHOP -> {
                if (state.detailedItems) {
                    val valid = state.items.any {
                        it.name.isNotBlank() && (it.effectiveTotal?.isPositive == true)
                    }
                    if (!valid) errors[KEY_ITEMS] = R.string.err_items_required
                } else {
                    val amount = Money.parseOrNull(state.lumpAmount)
                    if (amount == null) {
                        errors[KEY_AMOUNT] = R.string.err_amount_required
                    } else if (!amount.isPositive) {
                        errors[KEY_AMOUNT] = R.string.err_amount_positive
                    }
                }
            }

            AccountType.LOAN -> {
                val principal = Money.parseOrNull(state.loanAmount)
                if (principal == null) {
                    errors[KEY_AMOUNT] = R.string.err_amount_required
                } else if (!principal.isPositive) {
                    errors[KEY_AMOUNT] = R.string.err_amount_positive
                }

                val installment = Money.parseOrNull(state.installmentAmount)
                if (installment == null || !installment.isPositive) {
                    errors[KEY_INSTALLMENT] = R.string.err_installment_required
                }

                if (state.hasInterest) {
                    val rate = state.interestRate.toDoubleOrNull()
                    if (rate == null || rate < 0 || rate > 500) {
                        errors[KEY_INTEREST] = R.string.err_interest_invalid
                    }
                }

                // A payable smaller than the principal would make the schedule nonsense.
                val payable = Money.parseOrNull(state.payableAmount)
                if (payable != null && principal != null && payable < principal) {
                    errors[KEY_PAYABLE] = R.string.err_payable_less
                }

                if (state.frequency == InstallmentFrequency.CUSTOM) {
                    val days = state.customDays.toIntOrNull()
                    if (days == null || days !in 1..365) errors[KEY_DAYS] = R.string.err_days_invalid
                }
            }

            AccountType.EMI -> {
                val price = Money.parseOrNull(state.cashPrice)
                if (price == null) {
                    errors[KEY_AMOUNT] = R.string.err_amount_required
                } else if (!price.isPositive) {
                    errors[KEY_AMOUNT] = R.string.err_amount_positive
                }

                val down = Money.parseOrNull(state.downPayment) ?: Money.ZERO
                if (price != null && down > price) errors[KEY_DOWN] = R.string.err_down_payment_high

                val months = state.tenureMonths.toIntOrNull()
                if (months == null) {
                    errors[KEY_TENURE] = R.string.err_tenure_required
                } else if (months !in 1..ScheduleGenerator.MAX_INSTALLMENTS) {
                    errors[KEY_TENURE] = R.string.err_tenure_invalid
                }
            }

            AccountType.PERSON -> {
                val amount = Money.parseOrNull(state.personAmount)
                if (amount == null) {
                    errors[KEY_AMOUNT] = R.string.err_amount_required
                } else if (!amount.isPositive) {
                    errors[KEY_AMOUNT] = R.string.err_amount_positive
                }
            }
        }

        if (state.dueDate != null && state.dueDate.isBefore(state.startDate)) {
            errors[KEY_DUE_DATE] = R.string.err_date_before_start
        }

        return errors
    }

    companion object {
        const val KEY_NAME = "name"
        const val KEY_AMOUNT = "amount"
        const val KEY_ITEMS = "items"
        const val KEY_INSTALLMENT = "installment"
        const val KEY_INTEREST = "interest"
        const val KEY_PAYABLE = "payable"
        const val KEY_TENURE = "tenure"
        const val KEY_DOWN = "down"
        const val KEY_DAYS = "days"
        const val KEY_DUE_DATE = "dueDate"
    }
}
