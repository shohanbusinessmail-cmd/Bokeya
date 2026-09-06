package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.entity.MoneyEntryEntity
import com.shohan.bokeya.data.prefs.UserSettings
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.domain.model.Category
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.CategorySpend
import com.shohan.bokeya.domain.model.MoneyEntry
import com.shohan.bokeya.ui.navigation.Routes
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoneyUiState(
    val isLoading: Boolean = true,
    val showIncome: Boolean = false,
    val month: LocalDate = BanglaDate.today(),
    val entries: List<MoneyEntry> = emptyList(),
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val breakdown: List<CategorySpend> = emptyList(),
    val settings: UserSettings = UserSettings(),
) {
    val net: Money get() = income - expense
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}

class MoneyViewModel(private val container: AppContainer) : ViewModel() {

    private val showIncome = MutableStateFlow(false)
    private val month = MutableStateFlow(BanglaDate.today())

    private val _events = MutableSharedFlow<MoneyEvent>()
    val events: SharedFlow<MoneyEvent> = _events.asSharedFlow()

    /** Holds the last deleted row so the Snackbar can undo the delete. */
    private var pendingUndo: MoneyEntryEntity? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MoneyUiState> = combine(
        showIncome,
        month,
        container.preferences.settings,
    ) { isIncome, currentMonth, settings -> Triple(isIncome, currentMonth, settings) }
        .flatMapLatest { (isIncome, currentMonth, settings) ->
            val start = BanglaDate.startOfMonth(currentMonth)
            val end = BanglaDate.endOfMonth(currentMonth)
            combine(
                container.moneyRepository.observeEntriesBetween(isIncome, start, end),
                container.moneyRepository.observeSum(true, start, end),
                container.moneyRepository.observeSum(false, start, end),
                container.moneyRepository.observeCategoryBreakdown(isIncome, start, end),
            ) { entries, income, expense, breakdown ->
                MoneyUiState(
                    isLoading = false,
                    showIncome = isIncome,
                    month = currentMonth,
                    entries = entries,
                    income = income,
                    expense = expense,
                    breakdown = breakdown,
                    settings = settings,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyUiState())

    fun setShowIncome(value: Boolean) {
        showIncome.value = value
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        // Browsing into the future would only ever show an empty month.
        val candidate = month.value.plusMonths(1)
        if (!BanglaDate.startOfMonth(candidate).isAfter(BanglaDate.today())) {
            month.value = candidate
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            pendingUndo = container.moneyRepository.getEntry(id)
            container.moneyRepository.deleteEntry(id)
            _events.emit(MoneyEvent.Deleted)
        }
    }

    fun undoDelete() {
        val entity = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch { container.moneyRepository.restoreEntry(entity) }
    }
}

sealed interface MoneyEvent {
    data object Deleted : MoneyEvent
}

// ---------------------------------------------------------------------------

data class AddMoneyUiState(
    val isIncome: Boolean = false,
    val editingId: Long? = null,
    val title: String = "",
    val amount: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = BanglaDate.today(),
    val time: LocalTime = LocalTime.now(),
    val note: String = "",
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val titleError: Int? = null,
    val amountError: Int? = null,
    val saved: Boolean = false,
)

class AddMoneyViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val isIncome: Boolean =
        savedStateHandle.get<String>(Routes.ARG_IS_INCOME)?.toBooleanStrictOrNull()
            ?: savedStateHandle.get<Boolean>(Routes.ARG_IS_INCOME)
            ?: false

    private val editingId: Long? = savedStateHandle.get<String>(Routes.ARG_ENTRY_ID)
        ?.toLongOrNull()?.takeIf { it > 0 }

    private val _state = MutableStateFlow(AddMoneyUiState(isIncome = isIncome, editingId = editingId))
    val state: StateFlow<AddMoneyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val kind = if (isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE
            container.moneyRepository.ensureDefaultCategories()
            val categories = container.moneyRepository.getCategories(kind)

            val existing = editingId?.let { container.moneyRepository.getEntry(it) }
            _state.update { current ->
                current.copy(
                    categories = categories,
                    categoryId = existing?.categoryId ?: categories.firstOrNull()?.id,
                    title = existing?.title ?: current.title,
                    amount = existing?.let {
                        com.shohan.bokeya.core.money.MoneyFormatter.formatPlain(Money(it.amount))
                    } ?: current.amount,
                    date = existing?.let { BanglaDate.fromEpochDay(it.entryDate) } ?: current.date,
                    time = existing?.let {
                        BanglaDate.toLocalDateTime(it.entryTimeMillis).toLocalTime()
                    } ?: current.time,
                    note = existing?.note ?: current.note,
                )
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value, titleError = null) }
    fun setAmount(value: String) = _state.update { it.copy(amount = value, amountError = null) }
    fun setCategory(id: Long?) = _state.update { it.copy(categoryId = id) }
    fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }
    fun setTime(value: LocalTime) = _state.update { it.copy(time = value) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }

    fun save() {
        val current = _state.value
        val amount = Money.parseOrNull(current.amount)

        var titleError: Int? = null
        var amountError: Int? = null
        if (current.title.isBlank()) titleError = R.string.err_name_required
        if (amount == null) {
            amountError = R.string.err_amount_required
        } else if (!amount.isPositive) {
            amountError = R.string.err_amount_positive
        }

        if (titleError != null || amountError != null) {
            _state.update { it.copy(titleError = titleError, amountError = amountError) }
            return
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val millis = BanglaDate.toEpochMillis(current.date, current.time)
                if (current.editingId != null) {
                    container.moneyRepository.updateEntry(
                        id = current.editingId,
                        title = current.title,
                        amount = amount!!,
                        categoryId = current.categoryId,
                        date = current.date,
                        timeMillis = millis,
                        note = current.note,
                    )
                } else {
                    container.moneyRepository.addEntry(
                        isIncome = current.isIncome,
                        title = current.title,
                        amount = amount!!,
                        categoryId = current.categoryId,
                        date = current.date,
                        timeMillis = millis,
                        note = current.note,
                    )
                }
            }.onSuccess {
                _state.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _state.update { it.copy(isSaving = false, amountError = R.string.err_save_failed) }
            }
        }
    }
}
