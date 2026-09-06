package com.shohan.bokeya.data.repository

import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.dao.AccountWithTotals
import com.shohan.bokeya.data.local.dao.MoneyEntryWithCategory
import com.shohan.bokeya.data.local.entity.CategoryEntity
import com.shohan.bokeya.data.local.entity.DebtItemEntity
import com.shohan.bokeya.data.local.entity.InstallmentEntity
import com.shohan.bokeya.data.local.entity.PaymentEntity
import com.shohan.bokeya.domain.calc.BalanceCalculator
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.Category
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.DebtItem
import com.shohan.bokeya.domain.model.Installment
import com.shohan.bokeya.domain.model.MoneyEntry
import com.shohan.bokeya.domain.model.Payment
import java.time.LocalDate

/**
 * Entity -> domain conversions.
 *
 * Derived values (remaining, status, progress) are computed here through
 * [BalanceCalculator] so the rest of the app can only ever see consistent numbers.
 */

fun AccountWithTotals.toDomain(today: LocalDate = BanglaDate.today()): Account {
    val total = Money(totalAmount)
    val paid = Money(paidAmount)
    val remaining = BalanceCalculator.remaining(total, paid)
    val dueDate = nextDueDate?.let(BanglaDate::fromEpochDay)
        ?: endDate?.let(BanglaDate::fromEpochDay)
    val status = BalanceCalculator.status(total, paid, dueDate, today, isClosed)

    return Account(
        id = id,
        type = type,
        name = name,
        secondaryName = secondaryName,
        total = total,
        paid = paid,
        remaining = remaining,
        status = status,
        progressPercent = BalanceCalculator.progressPercent(total, paid),
        nextDueDate = dueDate,
        endDate = endDate?.let(BanglaDate::fromEpochDay),
        startDate = BanglaDate.fromEpochDay(startDate),
        isClosed = isClosed,
        installmentAmount = installmentAmount?.let(::Money),
        tenureCount = tenureCount,
        paidInstallments = paidInstallments,
        direction = direction?.let { name ->
            runCatching { DebtDirection.valueOf(name) }.getOrNull()
        },
        note = note,
        phone = phone,
        daysUntilDue = dueDate?.let { BanglaDate.daysBetween(today, it) },
    )
}

fun DebtItemEntity.toDomain() = DebtItem(
    id = id,
    accountId = accountId,
    name = name,
    quantity = quantityMilli?.let { it / 1000.0 },
    unit = unit,
    unitPrice = unitPrice?.let(::Money),
    total = Money(totalPrice),
    purchaseDate = BanglaDate.fromEpochDay(purchaseDate),
    note = note,
)

fun PaymentEntity.toDomain() = Payment(
    id = id,
    accountId = accountId,
    amount = Money(amount),
    date = BanglaDate.fromEpochDay(paymentDate),
    timeMillis = paymentTimeMillis,
    method = method,
    installmentId = installmentId,
    note = note,
)

fun InstallmentEntity.toDomain() = Installment(
    id = id,
    accountId = accountId,
    sequence = sequence,
    dueDate = BanglaDate.fromEpochDay(dueDate),
    amount = Money(amount),
    paidAmount = Money(paidAmount),
    isPaid = isPaid,
    paidDate = paidDate?.let(BanglaDate::fromEpochDay),
)

fun MoneyEntryWithCategory.toDomain() = MoneyEntry(
    id = id,
    isIncome = isIncome,
    title = title,
    amount = Money(amount),
    categoryId = categoryId,
    categoryName = categoryName,
    categoryIconKey = categoryIconKey,
    categoryColor = categoryColor,
    date = BanglaDate.fromEpochDay(entryDate),
    timeMillis = entryTimeMillis,
    note = note,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    kind = kind,
    builtInKey = builtInKey,
    iconKey = iconKey,
    color = colorArgb,
    isDefault = isDefault,
)

/** Quantities are stored x1000 to keep fractional amounts (২.৫ কেজি) exact. */
fun Double.toQuantityMilli(): Long = Math.round(this * 1000.0)
