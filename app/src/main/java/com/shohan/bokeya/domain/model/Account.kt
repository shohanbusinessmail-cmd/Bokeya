package com.shohan.bokeya.domain.model

import com.shohan.bokeya.core.money.Money
import java.time.LocalDate

/**
 * An account as the UI understands it: identity, balances and a derived status.
 *
 * [remaining] and [status] are computed by [com.shohan.bokeya.domain.calc.BalanceCalculator]
 * rather than read from the database, so they can never drift out of sync with
 * the payment rows.
 */
data class Account(
    val id: Long,
    val type: AccountType,
    val name: String,
    val secondaryName: String?,
    val total: Money,
    val paid: Money,
    val remaining: Money,
    val status: AccountStatus,
    val progressPercent: Int,
    val nextDueDate: LocalDate?,
    val endDate: LocalDate?,
    val startDate: LocalDate,
    val isClosed: Boolean,
    val installmentAmount: Money?,
    val tenureCount: Int?,
    val paidInstallments: Int,
    val direction: DebtDirection?,
    val note: String?,
    val phone: String?,
    val daysUntilDue: Long?,
) {
    val isOverdue: Boolean get() = status == AccountStatus.OVERDUE
    val isPaid: Boolean get() = status == AccountStatus.PAID

    /** True when the user owes money (as opposed to being owed). */
    val isPayable: Boolean get() = direction != DebtDirection.LENT
}

/** Full detail for a single account screen. */
data class AccountDetail(
    val account: Account,
    val items: List<DebtItem>,
    val payments: List<Payment>,
    val installments: List<Installment>,
    val hasInterest: Boolean,
    val interestRatePercent: Double?,
    val principal: Money?,
    val downPayment: Money?,
    val frequency: InstallmentFrequency?,
    val customIntervalDays: Int?,
)

data class DebtItem(
    val id: Long,
    val accountId: Long,
    val name: String,
    /** Quantity in whole units, e.g. 2.5 kg. Null for lump-sum entries. */
    val quantity: Double?,
    val unit: ItemUnit?,
    val unitPrice: Money?,
    val total: Money,
    val purchaseDate: LocalDate,
    val note: String?,
)

data class Payment(
    val id: Long,
    val accountId: Long,
    val amount: Money,
    val date: LocalDate,
    val timeMillis: Long,
    val method: PaymentMethod,
    val installmentId: Long?,
    val note: String?,
)

data class Installment(
    val id: Long,
    val accountId: Long,
    val sequence: Int,
    val dueDate: LocalDate,
    val amount: Money,
    val paidAmount: Money,
    val isPaid: Boolean,
    val paidDate: LocalDate?,
) {
    val remaining: Money get() = (amount - paidAmount).coerceAtLeastZero()
}

data class MoneyEntry(
    val id: Long,
    val isIncome: Boolean,
    val title: String,
    val amount: Money,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColor: Int?,
    val date: LocalDate,
    val timeMillis: Long,
    val note: String?,
)

data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val builtInKey: String?,
    val iconKey: String?,
    val color: Int?,
    val isDefault: Boolean,
)

/** A payment that is coming up (or already late), shown on the dashboard. */
data class UpcomingPayment(
    val accountId: Long,
    val accountName: String,
    val accountType: AccountType,
    val amount: Money,
    val dueDate: LocalDate,
    val daysUntil: Long,
    val isOverdue: Boolean,
    val installmentId: Long?,
)

/** One entry in the unified history/ledger view. */
data class LedgerEntry(
    val id: String,
    val type: TransactionType,
    val title: String,
    val subtitle: String?,
    val amount: Money,
    val date: LocalDate,
    val timeMillis: Long,
    val accountId: Long?,
    val entryId: Long?,
    val categoryColor: Int?,
    val note: String?,
)
