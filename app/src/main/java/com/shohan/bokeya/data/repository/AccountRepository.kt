package com.shohan.bokeya.data.repository

import androidx.room.withTransaction
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.local.entity.AccountEntity
import com.shohan.bokeya.data.local.entity.DebtItemEntity
import com.shohan.bokeya.data.local.entity.InstallmentEntity
import com.shohan.bokeya.data.local.entity.PaymentEntity
import com.shohan.bokeya.domain.calc.BalanceCalculator
import com.shohan.bokeya.domain.calc.ScheduleGenerator
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountDetail
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.domain.model.PaymentMethod
import com.shohan.bokeya.domain.model.UpcomingPayment
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single write path for accounts, items, payments and schedules.
 *
 * Every operation that touches more than one table runs inside a Room
 * transaction, so a crash mid-save can never leave a payment recorded without
 * its installment updated (or vice versa).
 */
class AccountRepository(
    private val database: BokeyaDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val accountDao = database.accountDao()
    private val paymentDao = database.paymentDao()

    // ------------------------------------------------------------------ reads

    fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAllWithTotals()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(io)

    fun observeAccountsByType(type: AccountType): Flow<List<Account>> =
        accountDao.observeByTypeWithTotals(type)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(io)

    fun observeAccount(id: Long): Flow<Account?> =
        accountDao.observeWithTotals(id)
            .map { it?.toDomain() }
            .flowOn(io)

    fun observeAccountDetail(id: Long): Flow<AccountDetail?> = combine(
        accountDao.observeWithTotals(id),
        accountDao.observeItems(id),
        accountDao.observePayments(id),
        accountDao.observeInstallments(id),
        accountDao.observeById(id),
    ) { totals, items, payments, installments, entity ->
        if (totals == null || entity == null) return@combine null
        AccountDetail(
            account = totals.toDomain(),
            items = items.map { it.toDomain() },
            payments = payments.map { it.toDomain() },
            installments = installments.map { it.toDomain() },
            hasInterest = entity.hasInterest,
            interestRatePercent = entity.interestRateBps?.let { it / 100.0 },
            principal = entity.principalAmount?.let(::Money),
            downPayment = entity.downPayment?.let(::Money),
            frequency = entity.frequency,
            customIntervalDays = entity.customIntervalDays,
        )
    }.flowOn(io)

    fun searchAccounts(query: String): Flow<List<Account>> =
        accountDao.search(query.trim())
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(io)

    suspend fun getAccount(id: Long): Account? = withContext(io) {
        accountDao.observeWithTotals(id).let { _ ->
            val entity = accountDao.getById(id) ?: return@withContext null
            val paid = paymentDao.sumForAccount(id)
            val total = Money(entity.totalAmount)
            val paidMoney = Money(paid)
            val dueDate = entity.nextDueDate?.let(BanglaDate::fromEpochDay)
                ?: entity.endDate?.let(BanglaDate::fromEpochDay)
            Account(
                id = entity.id,
                type = entity.type,
                name = entity.name,
                secondaryName = entity.secondaryName,
                total = total,
                paid = paidMoney,
                remaining = BalanceCalculator.remaining(total, paidMoney),
                status = BalanceCalculator.status(
                    total, paidMoney, dueDate, BanglaDate.today(), entity.isClosed,
                ),
                progressPercent = BalanceCalculator.progressPercent(total, paidMoney),
                nextDueDate = dueDate,
                endDate = entity.endDate?.let(BanglaDate::fromEpochDay),
                startDate = BanglaDate.fromEpochDay(entity.startDate),
                isClosed = entity.isClosed,
                installmentAmount = entity.installmentAmount?.let(::Money),
                tenureCount = entity.tenureCount,
                paidInstallments = accountDao.getInstallments(id).count { it.isPaid },
                direction = entity.direction,
                note = entity.note,
                phone = entity.phone,
                daysUntilDue = dueDate?.let { BanglaDate.daysBetween(BanglaDate.today(), it) },
            )
        }
    }

    suspend fun getAccountEntity(id: Long): AccountEntity? = withContext(io) { accountDao.getById(id) }

    // ------------------------------------------------------------ shop / bazar

    data class ItemInput(
        val name: String,
        val quantity: Double?,
        val unit: ItemUnit?,
        val unitPrice: Money?,
        val total: Money,
        val note: String? = null,
    )

    /**
     * Creates a shop account. [items] may be a detailed list or a single
     * lump-sum row; the account total is always the sum of its items so the
     * two representations stay interchangeable.
     */
    suspend fun createShopAccount(
        name: String,
        items: List<ItemInput>,
        purchaseDate: LocalDate,
        initialPayment: Money = Money.ZERO,
        dueDate: LocalDate? = null,
        note: String? = null,
    ): Long = withContext(io) {
        val now = BanglaDate.nowMillis()
        val total = Money(items.sumOf { it.total.minor })
        database.withTransaction {
            val accountId = accountDao.insert(
                AccountEntity(
                    type = AccountType.SHOP,
                    name = name.trim(),
                    totalAmount = total.minor,
                    startDate = purchaseDate.toEpochDay(),
                    endDate = dueDate?.toEpochDay(),
                    nextDueDate = dueDate?.toEpochDay(),
                    note = note?.trim()?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            accountDao.insertItems(items.map { it.toEntity(accountId, purchaseDate, now) })
            if (initialPayment.isPositive) {
                paymentDao.insert(
                    PaymentEntity(
                        accountId = accountId,
                        amount = initialPayment.minor,
                        paymentDate = purchaseDate.toEpochDay(),
                        paymentTimeMillis = now,
                        method = PaymentMethod.CASH,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                refreshClosedState(accountId, total, initialPayment, now)
            }
            accountId
        }
    }

    /** Adds another day's purchases to an existing shop tab. */
    suspend fun addItemsToAccount(
        accountId: Long,
        items: List<ItemInput>,
        purchaseDate: LocalDate,
    ) = withContext(io) {
        if (items.isEmpty()) return@withContext
        val now = BanglaDate.nowMillis()
        database.withTransaction {
            accountDao.insertItems(items.map { it.toEntity(accountId, purchaseDate, now) })
            val newTotal = accountDao.sumItems(accountId)
            accountDao.updateTotal(accountId, newTotal, now)
            val paid = paymentDao.sumForAccount(accountId)
            refreshClosedState(accountId, Money(newTotal), Money(paid), now)
        }
    }

    suspend fun deleteItem(accountId: Long, itemId: Long) = withContext(io) {
        val now = BanglaDate.nowMillis()
        database.withTransaction {
            accountDao.deleteItem(itemId)
            val newTotal = accountDao.sumItems(accountId)
            accountDao.updateTotal(accountId, newTotal, now)
            val paid = paymentDao.sumForAccount(accountId)
            refreshClosedState(accountId, Money(newTotal), Money(paid), now)
        }
    }

    private fun ItemInput.toEntity(accountId: Long, date: LocalDate, now: Long) = DebtItemEntity(
        accountId = accountId,
        name = name.trim(),
        quantityMilli = quantity?.toQuantityMilli(),
        unit = unit,
        unitPrice = unitPrice?.minor,
        totalPrice = total.minor,
        purchaseDate = date.toEpochDay(),
        note = note?.trim()?.ifBlank { null },
        createdAt = now,
    )

    // ------------------------------------------------------------------- loan

    suspend fun createLoanAccount(
        loanName: String,
        organisation: String?,
        principal: Money,
        payable: Money,
        hasInterest: Boolean,
        interestRatePercent: Double?,
        installmentAmount: Money,
        frequency: InstallmentFrequency,
        customIntervalDays: Int?,
        startDate: LocalDate,
        firstDueDate: LocalDate,
        alreadyPaid: Money = Money.ZERO,
        note: String? = null,
    ): Long = withContext(io) {
        val now = BanglaDate.nowMillis()
        val schedule = ScheduleGenerator.generate(
            totalAmount = payable,
            perInstallment = installmentAmount,
            firstDueDate = firstDueDate,
            frequency = frequency,
            customIntervalDays = customIntervalDays,
        )
        database.withTransaction {
            val accountId = accountDao.insert(
                AccountEntity(
                    type = AccountType.LOAN,
                    name = loanName.trim(),
                    secondaryName = organisation?.trim()?.ifBlank { null },
                    totalAmount = payable.minor,
                    principalAmount = principal.minor,
                    hasInterest = hasInterest,
                    interestRateBps = interestRatePercent?.let { Math.round(it * 100).toInt() },
                    installmentAmount = installmentAmount.minor,
                    frequency = frequency,
                    customIntervalDays = customIntervalDays,
                    tenureCount = schedule.size,
                    startDate = startDate.toEpochDay(),
                    endDate = schedule.lastOrNull()?.dueDate?.toEpochDay(),
                    nextDueDate = firstDueDate.toEpochDay(),
                    note = note?.trim()?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            persistSchedule(accountId, schedule)
            if (alreadyPaid.isPositive) {
                recordPaymentInternal(
                    accountId = accountId,
                    amount = alreadyPaid,
                    date = startDate,
                    timeMillis = now,
                    method = PaymentMethod.CASH,
                    note = null,
                    total = payable,
                )
            }
            accountId
        }
    }

    // -------------------------------------------------------------------- EMI

    suspend fun createEmiAccount(
        productName: String,
        seller: String?,
        cashPrice: Money,
        downPayment: Money,
        tenureMonths: Int,
        installmentAmount: Money?,
        startDate: LocalDate,
        firstDueDate: LocalDate,
        note: String? = null,
    ): Long = withContext(io) {
        val now = BanglaDate.nowMillis()
        val financed = (cashPrice - downPayment).coerceAtLeastZero()
        val schedule = if (installmentAmount != null && installmentAmount.isPositive) {
            ScheduleGenerator.generate(
                totalAmount = financed,
                perInstallment = installmentAmount,
                firstDueDate = firstDueDate,
                frequency = InstallmentFrequency.MONTHLY,
                maxCount = tenureMonths,
            )
        } else {
            ScheduleGenerator.generateByCount(
                totalAmount = financed,
                count = tenureMonths,
                firstDueDate = firstDueDate,
                frequency = InstallmentFrequency.MONTHLY,
            )
        }
        val perMonth = schedule.firstOrNull()?.amount ?: Money.ZERO

        database.withTransaction {
            val accountId = accountDao.insert(
                AccountEntity(
                    type = AccountType.EMI,
                    name = productName.trim(),
                    secondaryName = seller?.trim()?.ifBlank { null },
                    totalAmount = financed.minor,
                    principalAmount = cashPrice.minor,
                    downPayment = downPayment.minor,
                    installmentAmount = perMonth.minor,
                    frequency = InstallmentFrequency.MONTHLY,
                    tenureCount = schedule.size,
                    startDate = startDate.toEpochDay(),
                    endDate = schedule.lastOrNull()?.dueDate?.toEpochDay(),
                    nextDueDate = firstDueDate.toEpochDay(),
                    note = note?.trim()?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            persistSchedule(accountId, schedule)
            accountId
        }
    }

    // ----------------------------------------------------------------- person

    suspend fun createPersonAccount(
        personName: String,
        relationship: String?,
        phone: String?,
        amount: Money,
        direction: DebtDirection,
        borrowDate: LocalDate,
        returnDate: LocalDate?,
        alreadyPaid: Money = Money.ZERO,
        note: String? = null,
    ): Long = withContext(io) {
        val now = BanglaDate.nowMillis()
        database.withTransaction {
            val accountId = accountDao.insert(
                AccountEntity(
                    type = AccountType.PERSON,
                    name = personName.trim(),
                    secondaryName = relationship?.trim()?.ifBlank { null },
                    phone = phone?.trim()?.ifBlank { null },
                    totalAmount = amount.minor,
                    direction = direction,
                    startDate = borrowDate.toEpochDay(),
                    endDate = returnDate?.toEpochDay(),
                    nextDueDate = returnDate?.toEpochDay(),
                    note = note?.trim()?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (alreadyPaid.isPositive) {
                recordPaymentInternal(
                    accountId = accountId,
                    amount = alreadyPaid,
                    date = borrowDate,
                    timeMillis = now,
                    method = PaymentMethod.CASH,
                    note = null,
                    total = amount,
                )
            }
            accountId
        }
    }

    // --------------------------------------------------------------- payments

    /**
     * Records a payment and keeps every derived value consistent: installment
     * allocation, the cached next due date, and the closed flag.
     */
    suspend fun recordPayment(
        accountId: Long,
        amount: Money,
        date: LocalDate,
        timeMillis: Long = BanglaDate.nowMillis(),
        method: PaymentMethod = PaymentMethod.CASH,
        note: String? = null,
    ): BalanceCalculator.PaymentOutcome = withContext(io) {
        database.withTransaction {
            val entity = accountDao.getById(accountId)
                ?: error("Account $accountId not found")
            recordPaymentInternal(
                accountId = accountId,
                amount = amount,
                date = date,
                timeMillis = timeMillis,
                method = method,
                note = note,
                total = Money(entity.totalAmount),
            )
        }
    }

    private suspend fun recordPaymentInternal(
        accountId: Long,
        amount: Money,
        date: LocalDate,
        timeMillis: Long,
        method: PaymentMethod,
        note: String?,
        total: Money,
    ): BalanceCalculator.PaymentOutcome {
        val now = BanglaDate.nowMillis()
        val paidBefore = Money(paymentDao.sumForAccount(accountId))

        paymentDao.insert(
            PaymentEntity(
                accountId = accountId,
                amount = amount.minor,
                paymentDate = date.toEpochDay(),
                paymentTimeMillis = timeMillis,
                method = method,
                note = note?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now,
            ),
        )

        allocatePaymentsToInstallments(accountId)
        val paidAfter = paidBefore + amount
        refreshClosedState(accountId, total, paidAfter, now)
        return BalanceCalculator.applyPayment(total, paidBefore, amount)
    }

    suspend fun deletePayment(paymentId: Long) = withContext(io) {
        database.withTransaction {
            val payment = paymentDao.getById(paymentId) ?: return@withTransaction
            paymentDao.deleteById(paymentId)
            val account = accountDao.getById(payment.accountId) ?: return@withTransaction
            allocatePaymentsToInstallments(payment.accountId)
            val paid = Money(paymentDao.sumForAccount(payment.accountId))
            refreshClosedState(
                payment.accountId,
                Money(account.totalAmount),
                paid,
                BanglaDate.nowMillis(),
            )
        }
    }

    /**
     * Re-applies every payment to the schedule from scratch.
     *
     * Recomputing rather than incrementally patching means deleting a payment
     * from the middle of the history still leaves the schedule exactly right.
     */
    private suspend fun allocatePaymentsToInstallments(accountId: Long) {
        val installments = accountDao.getInstallments(accountId)
        if (installments.isEmpty()) return

        var pool = Money(paymentDao.sumForAccount(accountId))
        val updated = installments.sortedBy { it.dueDate }.map { installment ->
            val due = Money(installment.amount)
            val applied = if (pool >= due) due else pool
            pool -= applied
            installment.copy(
                paidAmount = applied.minor,
                isPaid = applied >= due,
                paidDate = if (applied >= due) {
                    installment.paidDate ?: BanglaDate.today().toEpochDay()
                } else {
                    null
                },
            )
        }
        accountDao.updateInstallments(updated)
    }

    /** Updates the cached next due date and the closed flag after a balance change. */
    private suspend fun refreshClosedState(
        accountId: Long,
        total: Money,
        paid: Money,
        now: Long,
    ) {
        val remaining = BalanceCalculator.remaining(total, paid)
        val entity = accountDao.getById(accountId) ?: return

        if (remaining.isZero) {
            accountDao.updateClosed(accountId, closed = true, closedAt = now, now = now)
            accountDao.updateNextDueDate(accountId, null, now)
            return
        }

        if (entity.isClosed) {
            accountDao.updateClosed(accountId, closed = false, closedAt = null, now = now)
        }
        val nextDue = accountDao.getInstallments(accountId)
            .filter { !it.isPaid }
            .minByOrNull { it.dueDate }
            ?.dueDate
            ?: entity.endDate
        accountDao.updateNextDueDate(accountId, nextDue, now)
    }

    private suspend fun persistSchedule(
        accountId: Long,
        schedule: List<ScheduleGenerator.PlannedInstallment>,
    ) {
        if (schedule.isEmpty()) return
        accountDao.insertInstallments(
            schedule.map {
                InstallmentEntity(
                    accountId = accountId,
                    sequence = it.sequence,
                    dueDate = it.dueDate.toEpochDay(),
                    amount = it.amount.minor,
                )
            },
        )
    }

    // ---------------------------------------------------------------- updates

    suspend fun updateAccountBasics(
        accountId: Long,
        name: String,
        secondaryName: String?,
        phone: String?,
        note: String?,
        dueDate: LocalDate?,
    ) = withContext(io) {
        val now = BanglaDate.nowMillis()
        database.withTransaction {
            val entity = accountDao.getById(accountId) ?: return@withTransaction
            accountDao.update(
                entity.copy(
                    name = name.trim(),
                    secondaryName = secondaryName?.trim()?.ifBlank { null },
                    phone = phone?.trim()?.ifBlank { null },
                    note = note?.trim()?.ifBlank { null },
                    endDate = dueDate?.toEpochDay() ?: entity.endDate,
                    nextDueDate = if (entity.type == AccountType.SHOP || entity.type == AccountType.PERSON) {
                        dueDate?.toEpochDay()
                    } else {
                        entity.nextDueDate
                    },
                    updatedAt = now,
                ),
            )
        }
    }

    /** Adjusts the headline amount for accounts without an item list. */
    suspend fun updateAccountTotal(accountId: Long, total: Money) = withContext(io) {
        val now = BanglaDate.nowMillis()
        database.withTransaction {
            accountDao.updateTotal(accountId, total.minor, now)
            val paid = Money(paymentDao.sumForAccount(accountId))
            refreshClosedState(accountId, total, paid, now)
        }
    }

    /** Settles whatever is left on an account in a single payment. */
    suspend fun closeAccount(accountId: Long) = withContext(io) {
        val account = getAccount(accountId) ?: return@withContext
        if (account.remaining.isPositive) {
            recordPayment(
                accountId = accountId,
                amount = account.remaining,
                date = BanglaDate.today(),
                method = PaymentMethod.CASH,
            )
        }
    }

    suspend fun deleteAccount(accountId: Long) = withContext(io) {
        accountDao.deleteById(accountId)
    }

    // -------------------------------------------------------------- dashboard

    /**
     * Payments coming due within [horizonDays], plus anything already overdue.
     * Accounts without a schedule fall back to their single deadline.
     */
    suspend fun getUpcomingPayments(
        today: LocalDate = BanglaDate.today(),
        horizonDays: Long = 30,
        limit: Int = 50,
    ): List<UpcomingPayment> = withContext(io) {
        val horizon = today.plusDays(horizonDays)
        val result = mutableListOf<UpcomingPayment>()

        val accounts = accountDao.getOpenAccounts()
        for (account in accounts) {
            val paid = Money(paymentDao.sumForAccount(account.id))
            val remaining = BalanceCalculator.remaining(Money(account.totalAmount), paid)
            if (remaining.isZero) continue
            if (account.direction == DebtDirection.LENT) continue

            val installments = accountDao.getInstallments(account.id)
            if (installments.isNotEmpty()) {
                installments
                    .filter { !it.isPaid }
                    .sortedBy { it.dueDate }
                    .take(3)
                    .forEach { installment ->
                        val due = BanglaDate.fromEpochDay(installment.dueDate)
                        if (due.isAfter(horizon)) return@forEach
                        val amount = (Money(installment.amount) - Money(installment.paidAmount))
                            .coerceAtLeastZero()
                        if (amount.isZero) return@forEach
                        result += UpcomingPayment(
                            accountId = account.id,
                            accountName = account.name,
                            accountType = account.type,
                            amount = amount.coerceAtMost(remaining),
                            dueDate = due,
                            daysUntil = BanglaDate.daysBetween(today, due),
                            isOverdue = due.isBefore(today),
                            installmentId = installment.id,
                        )
                    }
            } else {
                val due = account.endDate?.let(BanglaDate::fromEpochDay) ?: continue
                if (due.isAfter(horizon)) continue
                result += UpcomingPayment(
                    accountId = account.id,
                    accountName = account.name,
                    accountType = account.type,
                    amount = remaining,
                    dueDate = due,
                    daysUntil = BanglaDate.daysBetween(today, due),
                    isOverdue = due.isBefore(today),
                    installmentId = null,
                )
            }
        }
        result.sortedWith(compareBy({ it.dueDate }, { -it.amount.minor })).take(limit)
    }

    suspend fun accountCount(): Int = withContext(io) { accountDao.count() }
}
