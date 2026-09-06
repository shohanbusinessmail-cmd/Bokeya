package com.shohan.bokeya.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.BuildConfig
import com.shohan.bokeya.R
import com.shohan.bokeya.backup.BackupManager
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.prefs.UserSettings
import com.shohan.bokeya.data.repository.LedgerRepository
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.domain.model.Category
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.DayActivity
import com.shohan.bokeya.domain.model.DayDetail
import com.shohan.bokeya.domain.model.HistoryFilter
import com.shohan.bokeya.domain.model.HistorySort
import com.shohan.bokeya.domain.model.Insight
import com.shohan.bokeya.domain.model.LedgerEntry
import com.shohan.bokeya.domain.model.ThemeMode
import com.shohan.bokeya.domain.model.UpcomingPayment
import com.shohan.bokeya.export.ExportFormat
import com.shohan.bokeya.export.ReportType
import com.shohan.bokeya.sample.SampleDataSeeder
import com.shohan.bokeya.security.PinManager
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ calendar

data class CalendarUiState(
    val month: LocalDate = BanglaDate.today(),
    val selectedDate: LocalDate = BanglaDate.today(),
    val activity: Map<LocalDate, DayActivity> = emptyMap(),
    val dayDetail: DayDetail? = null,
    val monthDueTotal: Money = Money.ZERO,
    val isLoading: Boolean = true,
    val settings: UserSettings = UserSettings(),
)

class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.preferences.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        loadMonth(BanglaDate.today())
        selectDate(BanglaDate.today())
    }

    fun loadMonth(month: LocalDate) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, month = month) }
            val activity = container.ledgerRepository.getMonthActivity(month)
            _state.update {
                it.copy(
                    isLoading = false,
                    activity = activity,
                    monthDueTotal = Money(activity.values.sumOf { day -> day.dueAmount.minor }),
                )
            }
        }
    }

    fun selectDate(date: LocalDate) {
        viewModelScope.launch {
            _state.update { it.copy(selectedDate = date) }
            val detail = container.ledgerRepository.getDayDetail(date)
            _state.update { it.copy(dayDetail = detail) }
        }
    }

    fun previousMonth() = loadMonth(_state.value.month.minusMonths(1))

    fun nextMonth() = loadMonth(_state.value.month.plusMonths(1))

    fun refresh() {
        loadMonth(_state.value.month)
        selectDate(_state.value.selectedDate)
    }
}

// ------------------------------------------------------------------- history

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<LedgerEntry> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val sort: HistorySort = HistorySort.NEWEST,
    val settings: UserSettings = UserSettings(),
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val sort = MutableStateFlow(HistorySort.NEWEST)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(filter, sort) { f, s -> f to s }
        .flatMapLatest { (currentFilter, currentSort) ->
            combine(
                container.ledgerRepository.observeHistory(
                    filter = currentFilter,
                    sort = currentSort,
                    // Two years back covers realistic history without unbounded reads.
                    from = BanglaDate.today().minusYears(2),
                    to = BanglaDate.today().plusYears(1),
                ),
                container.preferences.settings,
            ) { entries, settings ->
                HistoryUiState(
                    isLoading = false,
                    entries = entries,
                    filter = currentFilter,
                    sort = currentSort,
                    settings = settings,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun setSort(value: HistorySort) {
        sort.value = value
    }
}

// -------------------------------------------------------------------- search

data class SearchUiState(
    val query: String = "",
    val results: LedgerRepository.SearchResults = LedgerRepository.SearchResults.EMPTY,
    val isSearching: Boolean = false,
    val settings: UserSettings = UserSettings(),
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val isEmpty: Boolean get() = hasQuery && !isSearching && results.isEmpty
}

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = query
        // Debounced so each keystroke doesn't fire four LIKE queries.
        .debounce { if (it.isBlank()) 0L else 220L }
        .flatMapLatest { text ->
            combine(
                container.ledgerRepository.search(text),
                container.preferences.settings,
            ) { results, settings ->
                SearchUiState(query = text, results = results, settings = settings)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun clear() {
        query.value = ""
    }
}

// ------------------------------------------------------------------ insights

data class InsightsUiState(
    val isLoading: Boolean = true,
    val insights: List<Insight> = emptyList(),
    val monthlyTrend: List<com.shohan.bokeya.domain.model.MonthlyTrend> = emptyList(),
    val expenseBreakdown: List<com.shohan.bokeya.domain.model.CategorySpend> = emptyList(),
    val settings: UserSettings = UserSettings(),
)

class InsightsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                container.accountRepository.observeAccounts(),
                container.preferences.settings,
            ) { accounts, settings -> accounts to settings }
                .collect { (accounts, settings) ->
                    val today = BanglaDate.today()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            insights = container.insightsUseCase.generate(
                                accounts = accounts,
                                useBengaliDigits = settings.useBengaliDigits,
                                limit = 10,
                            ),
                            monthlyTrend = container.moneyRepository.getMonthlyTrend(6),
                            expenseBreakdown = container.moneyRepository.getCategoryBreakdown(
                                isIncome = false,
                                from = BanglaDate.startOfMonth(today),
                                to = BanglaDate.endOfMonth(today),
                            ),
                            settings = settings,
                        )
                    }
                }
        }
    }
}

// ------------------------------------------------------------------- reports

data class ReportsUiState(
    val selectedType: ReportType = ReportType.MONTHLY,
    val month: LocalDate = BanglaDate.today(),
    val isGenerating: Boolean = false,
    val settings: UserSettings = UserSettings(),
)

sealed interface ReportEvent {
    data class Ready(val uri: Uri, val mimeType: String) : ReportEvent
    data class Failed(val messageRes: Int) : ReportEvent
}

class ReportsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReportEvent>()
    val events: SharedFlow<ReportEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            container.preferences.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    fun selectType(type: ReportType) = _state.update { it.copy(selectedType = type) }

    fun previousMonth() = _state.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = _state.update { current ->
        val candidate = current.month.plusMonths(1)
        if (BanglaDate.startOfMonth(candidate).isAfter(BanglaDate.today())) current
        else current.copy(month = candidate)
    }

    fun generate(format: ExportFormat) {
        val current = _state.value
        _state.update { it.copy(isGenerating = true) }
        viewModelScope.launch {
            val data = container.exportManager.buildReportData(
                type = current.selectedType,
                month = current.month,
                useBengaliDigits = current.settings.useBengaliDigits,
            )
            if (data.accounts.isEmpty() && data.entries.isEmpty()) {
                _state.update { it.copy(isGenerating = false) }
                _events.emit(ReportEvent.Failed(R.string.err_no_data_export))
                return@launch
            }
            container.exportManager.export(data, format)
                .onSuccess { uri ->
                    _state.update { it.copy(isGenerating = false) }
                    _events.emit(ReportEvent.Ready(uri, container.exportManager.mimeType(format)))
                }
                .onFailure {
                    _state.update { it.copy(isGenerating = false) }
                    _events.emit(ReportEvent.Failed(R.string.report_failed))
                }
        }
    }
}

// ------------------------------------------------------------------ settings

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<UserSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun setTheme(mode: ThemeMode) = launchPref { container.preferences.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launchPref { container.preferences.setDynamicColor(enabled) }

    fun setBengaliDigits(enabled: Boolean) = launchPref { container.preferences.setBengaliDigits(enabled) }

    fun setUserName(name: String) = launchPref { container.preferences.setUserName(name) }

    fun setNotificationsEnabled(enabled: Boolean) = launchPref {
        container.preferences.setNotificationsEnabled(enabled)
        val updated = container.preferences.current()
        if (enabled) container.reminderScheduler.schedule(updated) else container.reminderScheduler.cancel()
    }

    fun setReminderDaysBefore(days: Int) = launchPref {
        container.preferences.setReminderDaysBefore(days)
        container.reminderScheduler.schedule(container.preferences.current())
    }

    fun setReminderSameDay(enabled: Boolean) = launchPref {
        container.preferences.setReminderSameDay(enabled)
    }

    fun setReminderOverdue(enabled: Boolean) = launchPref {
        container.preferences.setReminderOverdue(enabled)
    }

    fun setReminderTime(minutes: Int) = launchPref {
        container.preferences.setReminderTime(minutes)
        container.reminderScheduler.schedule(container.preferences.current())
    }

    /** Fires one notification immediately so the user can confirm the setup works. */
    fun sendTestNotification() {
        container.reminderNotifier.notifyTest()
    }

    /**
     * Debug-only demo data. Guarded twice — the menu entry is hidden in release
     * builds and the call itself is a no-op there — so production installs can
     * never end up with fabricated financial records.
     */
    fun addSampleData() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            runCatching {
                SampleDataSeeder(container.accountRepository, container.moneyRepository).seed()
            }.onSuccess {
                _events.emit(SettingsEvent.SampleDataAdded)
                container.reminderScheduler.schedule(container.preferences.current())
            }.onFailure {
                _events.emit(SettingsEvent.Error(R.string.err_generic))
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            runCatching { container.backupManager.clearAllData() }
                .onSuccess { _events.emit(SettingsEvent.DataCleared) }
                .onFailure { _events.emit(SettingsEvent.Error(R.string.err_generic)) }
        }
    }

    private fun launchPref(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}

sealed interface SettingsEvent {
    data object DataCleared : SettingsEvent
    data object SampleDataAdded : SettingsEvent
    data class Error(val messageRes: Int) : SettingsEvent
}

// ------------------------------------------------------------------ security

data class SecurityUiState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasPin: Boolean = false,
    val settings: UserSettings = UserSettings(),
)

class SecurityViewModel(private val container: AppContainer) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    val state: StateFlow<SecurityUiState> = combine(
        container.preferences.settings,
        refresh,
    ) { settings, _ ->
        SecurityUiState(
            appLockEnabled = settings.appLockEnabled,
            biometricEnabled = settings.biometricEnabled,
            hasPin = container.pinManager.hasPin,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityUiState())

    val minPinLength = PinManager.MIN_PIN_LENGTH
    val maxPinLength = PinManager.MAX_PIN_LENGTH

    fun setPin(pin: String) {
        viewModelScope.launch {
            container.pinManager.setPin(pin)
            container.preferences.setAppLockEnabled(true)
            refresh.value++
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            container.pinManager.clearPin()
            container.preferences.setAppLockEnabled(false)
            container.preferences.setBiometricEnabled(false)
            refresh.value++
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setBiometricEnabled(enabled) }
    }

    fun verifyCurrentPin(pin: String): Boolean = container.pinManager.verify(pin)
}

// -------------------------------------------------------------------- backup

data class BackupUiState(
    val isWorking: Boolean = false,
    val lastBackupAt: Long = 0L,
    val settings: UserSettings = UserSettings(),
)

sealed interface BackupEvent {
    data object BackupDone : BackupEvent
    data class RestoreDone(val accounts: Int) : BackupEvent
    data class Failed(val messageRes: Int) : BackupEvent
}

class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>()
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            container.preferences.settings.collect { settings ->
                _state.update { it.copy(settings = settings, lastBackupAt = settings.lastBackupAt) }
            }
        }
    }

    fun suggestedFileName(): String = container.backupManager.suggestedFileName()

    fun backupTo(uri: Uri) {
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            container.backupManager.exportTo(uri)
                .onSuccess {
                    _state.update { current -> current.copy(isWorking = false) }
                    _events.emit(BackupEvent.BackupDone)
                }
                .onFailure {
                    _state.update { current -> current.copy(isWorking = false) }
                    _events.emit(BackupEvent.Failed(R.string.backup_failed))
                }
        }
    }

    fun restoreFrom(uri: Uri) {
        _state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            when (val result = container.backupManager.restoreFrom(uri)) {
                is BackupManager.RestoreResult.Success -> {
                    _state.update { it.copy(isWorking = false) }
                    container.reminderScheduler.schedule(container.preferences.current())
                    _events.emit(BackupEvent.RestoreDone(result.accounts))
                }
                BackupManager.RestoreResult.NewerSchema -> {
                    _state.update { it.copy(isWorking = false) }
                    _events.emit(BackupEvent.Failed(R.string.backup_invalid_version))
                }
                else -> {
                    _state.update { it.copy(isWorking = false) }
                    _events.emit(BackupEvent.Failed(R.string.backup_restore_failed))
                }
            }
        }
    }
}

// ----------------------------------------------------------------- reminders

data class RemindersUiState(
    val isLoading: Boolean = true,
    val upcoming: List<UpcomingPayment> = emptyList(),
    val settings: UserSettings = UserSettings(),
)

class RemindersViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(RemindersUiState())
    val state: StateFlow<RemindersUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                container.accountRepository.observeAccounts(),
                container.preferences.settings,
            ) { _, settings -> settings }
                .collect { settings ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            upcoming = container.accountRepository.getUpcomingPayments(horizonDays = 60),
                            settings = settings,
                        )
                    }
                }
        }
    }
}

// ---------------------------------------------------------------- categories

data class CategoriesUiState(
    val income: List<Category> = emptyList(),
    val expense: List<Category> = emptyList(),
    val error: Int? = null,
)

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<CategoriesUiState> = combine(
        container.moneyRepository.observeCategories(CategoryKind.INCOME),
        container.moneyRepository.observeCategories(CategoryKind.EXPENSE),
    ) { income, expense ->
        CategoriesUiState(income = income, expense = expense)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun addCategory(name: String, kind: CategoryKind) {
        if (name.isBlank()) return
        viewModelScope.launch {
            container.moneyRepository.addCategory(
                name = name,
                kind = kind,
                iconKey = "label",
                color = PALETTE.random(),
            )
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { container.moneyRepository.deleteCategory(id) }
    }

    fun renameCategory(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.moneyRepository.renameCategory(id, name) }
    }

    private companion object {
        val PALETTE = listOf(
            0xFF10B981.toInt(), 0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(),
            0xFFF59E0B.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(),
        )
    }
}
