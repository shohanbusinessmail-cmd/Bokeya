package com.shohan.bokeya.data.repository

import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DayActivity
import com.shohan.bokeya.domain.model.DayDetail
import com.shohan.bokeya.domain.model.HistoryFilter
import com.shohan.bokeya.domain.model.HistorySort
import com.shohan.bokeya.domain.model.LedgerEntry
import com.shohan.bokeya.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The unified transaction view.
 *
 * Payments, income and expenses live in separate tables (they have genuinely
 * different shapes), so the merged "everything that happened" list is assembled
 * here into [LedgerEntry] rather than forced into one denormalised table.
 */
class LedgerRepository(
    private val database: BokeyaDatabase,
    private val accountRepository: AccountRepository,
    private val moneyRepository: MoneyRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val paymentDao = database.paymentDao()
    private val moneyDao = database.moneyEntryDao()

    /** Merged, filtered, sorted history. */
    fun observeHistory(
        filter: HistoryFilter,
        sort: HistorySort,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<LedgerEntry>> = combine(
        paymentDao.observeBetween(from.toEpochDay(), to.toEpochDay()),
        moneyDao.observeBetween(from.toEpochDay(), to.toEpochDay()),
    ) { payments, entries ->
        val ledger = buildList {
            payments.forEach { payment ->
                val accountType = runCatching { AccountType.valueOf(payment.accountType) }
                    .getOrDefault(AccountType.SHOP)
                add(
                    LedgerEntry(
                        id = "payment_${payment.id}",
                        type = accountType.paymentTransactionType(),
                        title = payment.accountName,
                        subtitle = payment.note,
                        amount = Money(payment.amount),
                        date = BanglaDate.fromEpochDay(payment.paymentDate),
                        timeMillis = payment.paymentTimeMillis,
                        accountId = payment.accountId,
                        entryId = payment.id,
                        categoryColor = null,
                        note = payment.note,
                    ),
                )
            }
            entries.forEach { entry ->
                add(
                    LedgerEntry(
                        id = "money_${entry.id}",
                        type = if (entry.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                        title = entry.title,
                        subtitle = entry.categoryName,
                        amount = Money(entry.amount),
                        date = BanglaDate.fromEpochDay(entry.entryDate),
                        timeMillis = entry.entryTimeMillis,
                        accountId = null,
                        entryId = entry.id,
                        categoryColor = entry.categoryColor,
                        note = entry.note,
                    ),
                )
            }
        }
        ledger.applyFilter(filter).applySort(sort)
    }.flowOn(io)

    private fun List<LedgerEntry>.applyFilter(filter: HistoryFilter): List<LedgerEntry> = when (filter) {
        HistoryFilter.ALL -> this
        HistoryFilter.DEBT -> filter { it.type == TransactionType.DEBT || it.type == TransactionType.PAYMENT }
        HistoryFilter.LOAN -> filter { it.type == TransactionType.LOAN || it.type == TransactionType.LOAN_PAYMENT }
        HistoryFilter.EMI -> filter { it.type == TransactionType.EMI || it.type == TransactionType.EMI_PAYMENT }
        HistoryFilter.PERSONAL -> filter { it.type == TransactionType.DEBT || it.type == TransactionType.PAYMENT }
        HistoryFilter.INCOME -> filter { it.type == TransactionType.INCOME }
        HistoryFilter.EXPENSE -> filter { it.type == TransactionType.EXPENSE }
        HistoryFilter.PAID -> filter { it.type.isPayment }
        HistoryFilter.UNPAID, HistoryFilter.OVERDUE -> this // handled by the accounts list
    }

    private fun List<LedgerEntry>.applySort(sort: HistorySort): List<LedgerEntry> = when (sort) {
        HistorySort.NEWEST -> sortedWith(compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.timeMillis })
        HistorySort.OLDEST -> sortedWith(compareBy<LedgerEntry> { it.date }.thenBy { it.timeMillis })
        HistorySort.AMOUNT_HIGH -> sortedByDescending { it.amount.minor }
        HistorySort.AMOUNT_LOW -> sortedBy { it.amount.minor }
    }

    /** Recent activity for the dashboard. */
    fun observeRecent(limit: Int = 8): Flow<List<LedgerEntry>> = combine(
        paymentDao.observeRecent(limit),
        moneyDao.observeBetween(
            BanglaDate.today().minusDays(60).toEpochDay(),
            BanglaDate.today().toEpochDay(),
        ),
    ) { payments, entries ->
        val ledger = buildList {
            payments.forEach { payment ->
                val accountType = runCatching { AccountType.valueOf(payment.accountType) }
                    .getOrDefault(AccountType.SHOP)
                add(
                    LedgerEntry(
                        id = "payment_${payment.id}",
                        type = accountType.paymentTransactionType(),
                        title = payment.accountName,
                        subtitle = payment.note,
                        amount = Money(payment.amount),
                        date = BanglaDate.fromEpochDay(payment.paymentDate),
                        timeMillis = payment.paymentTimeMillis,
                        accountId = payment.accountId,
                        entryId = payment.id,
                        categoryColor = null,
                        note = payment.note,
                    ),
                )
            }
            entries.take(limit).forEach { entry ->
                add(
                    LedgerEntry(
                        id = "money_${entry.id}",
                        type = if (entry.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                        title = entry.title,
                        subtitle = entry.categoryName,
                        amount = Money(entry.amount),
                        date = BanglaDate.fromEpochDay(entry.entryDate),
                        timeMillis = entry.entryTimeMillis,
                        accountId = null,
                        entryId = entry.id,
                        categoryColor = entry.categoryColor,
                        note = entry.note,
                    ),
                )
            }
        }
        ledger.sortedWith(
            compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.timeMillis },
        ).take(limit)
    }.flowOn(io)

    /** Global search across accounts, payments and money entries. */
    fun search(query: String): Flow<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return kotlinx.coroutines.flow.flowOf(SearchResults.EMPTY)

        return combine(
            accountRepository.searchAccounts(trimmed),
            paymentDao.search(trimmed),
            moneyDao.search(trimmed),
        ) { accounts, payments, entries ->
            SearchResults(
                accounts = accounts,
                transactions = buildList {
                    payments.forEach { payment ->
                        val accountType = runCatching { AccountType.valueOf(payment.accountType) }
                            .getOrDefault(AccountType.SHOP)
                        add(
                            LedgerEntry(
                                id = "payment_${payment.id}",
                                type = accountType.paymentTransactionType(),
                                title = payment.accountName,
                                subtitle = payment.note,
                                amount = Money(payment.amount),
                                date = BanglaDate.fromEpochDay(payment.paymentDate),
                                timeMillis = payment.paymentTimeMillis,
                                accountId = payment.accountId,
                                entryId = payment.id,
                                categoryColor = null,
                                note = payment.note,
                            ),
                        )
                    }
                    entries.forEach { entry ->
                        add(
                            LedgerEntry(
                                id = "money_${entry.id}",
                                type = if (entry.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                                title = entry.title,
                                subtitle = entry.categoryName,
                                amount = Money(entry.amount),
                                date = BanglaDate.fromEpochDay(entry.entryDate),
                                timeMillis = entry.entryTimeMillis,
                                accountId = null,
                                entryId = entry.id,
                                categoryColor = entry.categoryColor,
                                note = entry.note,
                            ),
                        )
                    }
                }.sortedByDescending { it.date },
            )
        }.flowOn(io)
    }

    // --------------------------------------------------------------- calendar

    /** Per-day markers for one calendar month. */
    suspend fun getMonthActivity(month: LocalDate): Map<LocalDate, DayActivity> = withContext(io) {
        val start = BanglaDate.startOfMonth(month)
        val end = BanglaDate.endOfMonth(month)
        val today = BanglaDate.today()

        val payments = paymentDao.getBetween(start.toEpochDay(), end.toEpochDay())
        val entries = moneyDao.getBetween(start.toEpochDay(), end.toEpochDay())
        // Look a full year ahead so long EMI schedules still mark future months.
        val upcoming = accountRepository.getUpcomingPayments(horizonDays = 400, limit = 500)
            .filter { !it.dueDate.isBefore(start) && !it.dueDate.isAfter(end) }

        val map = mutableMapOf<LocalDate, DayActivity>()
        fun mutate(date: LocalDate, block: (DayActivity) -> DayActivity) {
            val current = map[date] ?: DayActivity(
                date = date,
                dueAmount = Money.ZERO,
                paidAmount = Money.ZERO,
                income = Money.ZERO,
                expense = Money.ZERO,
                dueCount = 0,
                hasOverdue = false,
            )
            map[date] = block(current)
        }

        payments.forEach { payment ->
            mutate(BanglaDate.fromEpochDay(payment.paymentDate)) {
                it.copy(paidAmount = it.paidAmount + Money(payment.amount))
            }
        }
        entries.forEach { entry ->
            mutate(BanglaDate.fromEpochDay(entry.entryDate)) {
                if (entry.isIncome) {
                    it.copy(income = it.income + Money(entry.amount))
                } else {
                    it.copy(expense = it.expense + Money(entry.amount))
                }
            }
        }
        upcoming.forEach { due ->
            mutate(due.dueDate) {
                it.copy(
                    dueAmount = it.dueAmount + due.amount,
                    dueCount = it.dueCount + 1,
                    hasOverdue = it.hasOverdue || due.dueDate.isBefore(today),
                )
            }
        }
        map
    }

    /** Everything that happened (or is due) on one day. */
    suspend fun getDayDetail(date: LocalDate): DayDetail = withContext(io) {
        val payments = paymentDao.getBetween(date.toEpochDay(), date.toEpochDay())
        val entries = moneyDao.getBetween(date.toEpochDay(), date.toEpochDay())
        val due = accountRepository.getUpcomingPayments(horizonDays = 400, limit = 500)
            .filter { it.dueDate == date }

        DayDetail(
            date = date,
            duePayments = due,
            payments = payments.map { payment ->
                val accountType = runCatching { AccountType.valueOf(payment.accountType) }
                    .getOrDefault(AccountType.SHOP)
                LedgerEntry(
                    id = "payment_${payment.id}",
                    type = accountType.paymentTransactionType(),
                    title = payment.accountName,
                    subtitle = payment.note,
                    amount = Money(payment.amount),
                    date = date,
                    timeMillis = payment.paymentTimeMillis,
                    accountId = payment.accountId,
                    entryId = payment.id,
                    categoryColor = null,
                    note = payment.note,
                )
            },
            incomes = entries.filter { it.isIncome }.map { it.toLedgerEntry(date) },
            expenses = entries.filterNot { it.isIncome }.map { it.toLedgerEntry(date) },
        )
    }

    private fun com.shohan.bokeya.data.local.dao.MoneyEntryWithCategory.toLedgerEntry(date: LocalDate) =
        LedgerEntry(
            id = "money_$id",
            type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
            title = title,
            subtitle = categoryName,
            amount = Money(amount),
            date = date,
            timeMillis = entryTimeMillis,
            accountId = null,
            entryId = id,
            categoryColor = categoryColor,
            note = note,
        )

    data class SearchResults(
        val accounts: List<com.shohan.bokeya.domain.model.Account>,
        val transactions: List<LedgerEntry>,
    ) {
        val isEmpty: Boolean get() = accounts.isEmpty() && transactions.isEmpty()

        companion object {
            val EMPTY = SearchResults(emptyList(), emptyList())
        }
    }
}

private fun AccountType.paymentTransactionType(): TransactionType = when (this) {
    AccountType.LOAN -> TransactionType.LOAN_PAYMENT
    AccountType.EMI -> TransactionType.EMI_PAYMENT
    AccountType.SHOP, AccountType.PERSON -> TransactionType.PAYMENT
}
