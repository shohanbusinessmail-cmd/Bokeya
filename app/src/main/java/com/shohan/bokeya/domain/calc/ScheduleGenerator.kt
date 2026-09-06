package com.shohan.bokeya.domain.calc

import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.domain.model.InstallmentFrequency
import java.time.LocalDate

/**
 * Builds loan and EMI repayment schedules.
 *
 * Two rules keep the generated plan honest:
 *  1. **The instalments always sum to the financed amount.** Dividing ৳50,000
 *     into 3 gives ৳16,666.67 each, which cannot be represented in poisha, so
 *     the remainder is distributed over the earliest instalments instead of
 *     being silently dropped.
 *  2. **Monthly schedules use calendar months, not 30-day steps**, and clamp to
 *     the end of short months — a payment day of the 31st becomes the 28th in
 *     February rather than rolling into March.
 */
object ScheduleGenerator {

    /** Hard ceiling on generated rows; protects the DB from absurd input. */
    const val MAX_INSTALLMENTS = 600

    data class PlannedInstallment(
        val sequence: Int,
        val dueDate: LocalDate,
        val amount: Money,
    )

    /**
     * Generates [count] instalments of [perInstallment] starting at [firstDueDate].
     * The final instalment absorbs any rounding difference so the schedule total
     * matches [totalAmount] exactly.
     */
    fun generate(
        totalAmount: Money,
        perInstallment: Money,
        firstDueDate: LocalDate,
        frequency: InstallmentFrequency,
        customIntervalDays: Int? = null,
        maxCount: Int? = null,
    ): List<PlannedInstallment> {
        if (totalAmount.minor <= 0L || perInstallment.minor <= 0L) return emptyList()

        val estimated = ((totalAmount.minor + perInstallment.minor - 1) / perInstallment.minor).toInt()
        val count = (maxCount ?: estimated).coerceIn(1, MAX_INSTALLMENTS)

        val result = mutableListOf<PlannedInstallment>()
        var allocated = Money.ZERO
        for (index in 0 until count) {
            val isLast = index == count - 1
            val remaining = totalAmount - allocated
            if (remaining.minor <= 0L) break
            val amount = if (isLast || remaining <= perInstallment) remaining else perInstallment
            allocated += amount
            result += PlannedInstallment(
                sequence = index + 1,
                dueDate = dueDateFor(firstDueDate, index, frequency, customIntervalDays),
                amount = amount,
            )
        }
        return result
    }

    /**
     * Splits [totalAmount] into exactly [count] instalments (the EMI case, where
     * the tenure is fixed and the per-instalment amount is derived).
     */
    fun generateByCount(
        totalAmount: Money,
        count: Int,
        firstDueDate: LocalDate,
        frequency: InstallmentFrequency,
        customIntervalDays: Int? = null,
    ): List<PlannedInstallment> {
        if (totalAmount.minor <= 0L || count <= 0) return emptyList()
        val safeCount = count.coerceAtMost(MAX_INSTALLMENTS)
        return totalAmount.split(safeCount).mapIndexed { index, amount ->
            PlannedInstallment(
                sequence = index + 1,
                dueDate = dueDateFor(firstDueDate, index, frequency, customIntervalDays),
                amount = amount,
            )
        }
    }

    /** The due date of the instalment at [index] (0-based) after [start]. */
    fun dueDateFor(
        start: LocalDate,
        index: Int,
        frequency: InstallmentFrequency,
        customIntervalDays: Int? = null,
    ): LocalDate = when (frequency) {
        InstallmentFrequency.DAILY -> start.plusDays(index.toLong())
        InstallmentFrequency.WEEKLY -> start.plusWeeks(index.toLong())
        // plusMonths already clamps 31 Jan + 1 month -> 28/29 Feb.
        InstallmentFrequency.MONTHLY -> start.plusMonths(index.toLong())
        InstallmentFrequency.CUSTOM -> {
            val step = (customIntervalDays ?: 30).coerceIn(1, 365)
            start.plusDays(index.toLong() * step)
        }
    }

    /** The next due date strictly after [from], following [frequency]. */
    fun nextDueDate(
        start: LocalDate,
        from: LocalDate,
        frequency: InstallmentFrequency,
        customIntervalDays: Int? = null,
    ): LocalDate {
        var index = 0
        var candidate = start
        while (!candidate.isAfter(from) && index < MAX_INSTALLMENTS) {
            index++
            candidate = dueDateFor(start, index, frequency, customIntervalDays)
        }
        return candidate
    }

    /**
     * Total repayable for a simple-interest loan.
     *
     * Bank/NGO micro-loans in Bangladesh are usually quoted as flat (simple)
     * interest over the whole term, which is what this models:
     * `payable = principal * (1 + rate% * years)`.
     */
    fun simpleInterestPayable(
        principal: Money,
        annualRatePercent: Double,
        months: Int,
    ): Money {
        if (annualRatePercent <= 0.0 || months <= 0) return principal
        val years = months / 12.0
        val interest = principal.minor.toDouble() * (annualRatePercent / 100.0) * years
        return Money(principal.minor + Math.round(interest))
    }

    /** Expected final payment date for a schedule. */
    fun endDate(
        firstDueDate: LocalDate,
        count: Int,
        frequency: InstallmentFrequency,
        customIntervalDays: Int? = null,
    ): LocalDate = dueDateFor(firstDueDate, (count - 1).coerceAtLeast(0), frequency, customIntervalDays)
}
