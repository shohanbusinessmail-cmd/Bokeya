package com.shohan.bokeya.domain.calc

import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.domain.model.AccountStatus
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The single source of truth for every balance in the app.
 *
 * All four account types reduce to the same arithmetic — `remaining = total - paid`
 * clamped at zero — so it lives here once instead of being re-derived in each
 * ViewModel. Keeping it pure (no Android, no I/O) makes it exhaustively testable.
 */
object BalanceCalculator {

    /** Number of days before a due date at which an account starts warning. */
    const val DUE_SOON_WINDOW_DAYS = 3L

    /**
     * `remaining = total - paid`, never negative.
     *
     * Overpayment is allowed to be recorded (the user really did hand over the
     * money) but it can never push a balance below zero, which would otherwise
     * show up as a negative "due" on the dashboard.
     */
    fun remaining(total: Money, paid: Money): Money = (total - paid).coerceAtLeastZero()

    /** Amount paid beyond what was owed, or zero. */
    fun overpayment(total: Money, paid: Money): Money = (paid - total).coerceAtLeastZero()

    /**
     * Progress as a whole percentage, 0..100.
     *
     * Guards two edge cases: a zero total (an empty shop tab is 0% done, not a
     * divide-by-zero) and overpayment (capped at 100 rather than 137%).
     */
    fun progressPercent(total: Money, paid: Money): Int {
        if (total.minor <= 0L) return if (paid.isPositive) 100 else 0
        val ratio = paid.minor.toDouble() / total.minor.toDouble()
        return (ratio * 100).roundToInt().coerceIn(0, 100)
    }

    /**
     * Derives the display status.
     *
     * Order matters: a fully-paid account is PAID even if its date has passed,
     * and an explicitly closed account is never shown as overdue.
     */
    fun status(
        total: Money,
        paid: Money,
        dueDate: LocalDate?,
        today: LocalDate,
        isClosed: Boolean,
    ): AccountStatus {
        val left = remaining(total, paid)
        if (isClosed || left.isZero) return AccountStatus.PAID
        if (dueDate == null) return AccountStatus.ACTIVE

        val days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)
        return when {
            days < 0 -> AccountStatus.OVERDUE
            days == 0L -> AccountStatus.DUE_TODAY
            days <= DUE_SOON_WINDOW_DAYS -> AccountStatus.DUE_SOON
            else -> AccountStatus.ACTIVE
        }
    }

    /**
     * Applies a payment to a balance and reports what happened.
     * Used by the payment sheet to show "বাকি রইল …" and to decide whether the
     * account should be closed.
     */
    fun applyPayment(total: Money, alreadyPaid: Money, payment: Money): PaymentOutcome {
        val before = remaining(total, alreadyPaid)
        val newPaid = alreadyPaid + payment
        val after = remaining(total, newPaid)
        return PaymentOutcome(
            remainingBefore = before,
            remainingAfter = after,
            totalPaid = newPaid,
            isFullyPaid = after.isZero,
            overpaid = overpayment(total, newPaid),
        )
    }

    /**
     * How much of [payment] is still unallocated after settling [installments]
     * in due-date order. Returns the updated installments plus any leftover.
     */
    fun allocateToInstallments(
        payment: Money,
        installments: List<InstallmentBalance>,
    ): AllocationResult {
        var left = payment
        val updated = mutableListOf<InstallmentBalance>()
        for (installment in installments.sortedBy { it.dueDate }) {
            if (left.isZero || installment.isSettled) {
                updated += installment
                continue
            }
            val owed = installment.remaining
            val applied = if (left >= owed) owed else left
            left -= applied
            updated += installment.copy(paid = installment.paid + applied)
        }
        return AllocationResult(updated, left)
    }

    data class PaymentOutcome(
        val remainingBefore: Money,
        val remainingAfter: Money,
        val totalPaid: Money,
        val isFullyPaid: Boolean,
        val overpaid: Money,
    )

    data class InstallmentBalance(
        val id: Long,
        val dueDate: LocalDate,
        val amount: Money,
        val paid: Money,
    ) {
        val remaining: Money get() = (amount - paid).coerceAtLeastZero()
        val isSettled: Boolean get() = remaining.isZero
    }

    data class AllocationResult(
        val installments: List<InstallmentBalance>,
        val unallocated: Money,
    )
}
