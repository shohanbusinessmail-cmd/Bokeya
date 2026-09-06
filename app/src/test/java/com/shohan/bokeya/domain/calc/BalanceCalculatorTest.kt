package com.shohan.bokeya.domain.calc

import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.domain.model.AccountStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    // ----------------------------------------------------------- remaining

    /** The exact scenario named in the requirements: 10000 - 2500 = 7500. */
    @Test
    fun `partial payment leaves the exact remainder`() {
        val remaining = BalanceCalculator.remaining(
            total = Money.ofTaka(10_000),
            paid = Money.ofTaka(2_500),
        )
        assertEquals(Money.ofTaka(7_500), remaining)
    }

    /** And the follow-up: 7500 - 7500 = 0. */
    @Test
    fun `settling the remainder reaches exactly zero`() {
        val remaining = BalanceCalculator.remaining(
            total = Money.ofTaka(10_000),
            paid = Money.ofTaka(2_500) + Money.ofTaka(7_500),
        )
        assertEquals(Money.ZERO, remaining)
        assertTrue(remaining.isZero)
    }

    @Test
    fun `remaining is never negative on overpayment`() {
        val remaining = BalanceCalculator.remaining(
            total = Money.ofTaka(5_000),
            paid = Money.ofTaka(6_000),
        )
        assertEquals(Money.ZERO, remaining)
        assertFalse(remaining.isNegative)
    }

    @Test
    fun `overpayment reports the excess`() {
        assertEquals(
            Money.ofTaka(1_000),
            BalanceCalculator.overpayment(Money.ofTaka(5_000), Money.ofTaka(6_000)),
        )
        assertEquals(
            Money.ZERO,
            BalanceCalculator.overpayment(Money.ofTaka(5_000), Money.ofTaka(4_000)),
        )
    }

    @Test
    fun `many partial payments settle without residue`() {
        val total = Money.ofTaka(10_000)
        // Deliberately awkward: 3 parts of 10000/3 must still land on zero.
        var paid = Money.ZERO
        total.split(3).forEach { paid += it }
        assertEquals(Money.ZERO, BalanceCalculator.remaining(total, paid))
    }

    // ------------------------------------------------------------ progress

    @Test
    fun `progress percent is bounded and rounded`() {
        assertEquals(0, BalanceCalculator.progressPercent(Money.ofTaka(1_000), Money.ZERO))
        assertEquals(25, BalanceCalculator.progressPercent(Money.ofTaka(10_000), Money.ofTaka(2_500)))
        assertEquals(100, BalanceCalculator.progressPercent(Money.ofTaka(10_000), Money.ofTaka(10_000)))
        // Overpayment must not render as 120%.
        assertEquals(100, BalanceCalculator.progressPercent(Money.ofTaka(10_000), Money.ofTaka(12_000)))
    }

    @Test
    fun `progress on a zero total does not divide by zero`() {
        assertEquals(0, BalanceCalculator.progressPercent(Money.ZERO, Money.ZERO))
        assertEquals(100, BalanceCalculator.progressPercent(Money.ZERO, Money.ofTaka(10)))
    }

    // -------------------------------------------------------------- status

    @Test
    fun `zero remaining is paid regardless of date`() {
        val status = BalanceCalculator.status(
            total = Money.ofTaka(1_000),
            paid = Money.ofTaka(1_000),
            dueDate = today.minusDays(30),
            today = today,
            isClosed = false,
        )
        assertEquals(AccountStatus.PAID, status)
    }

    @Test
    fun `a closed account is never overdue`() {
        val status = BalanceCalculator.status(
            total = Money.ofTaka(1_000),
            paid = Money.ZERO,
            dueDate = today.minusDays(10),
            today = today,
            isClosed = true,
        )
        assertEquals(AccountStatus.PAID, status)
    }

    @Test
    fun `status follows the due date`() {
        fun statusFor(dueDate: LocalDate?) = BalanceCalculator.status(
            total = Money.ofTaka(1_000),
            paid = Money.ofTaka(100),
            dueDate = dueDate,
            today = today,
            isClosed = false,
        )

        assertEquals(AccountStatus.OVERDUE, statusFor(today.minusDays(1)))
        assertEquals(AccountStatus.DUE_TODAY, statusFor(today))
        assertEquals(AccountStatus.DUE_SOON, statusFor(today.plusDays(1)))
        assertEquals(AccountStatus.DUE_SOON, statusFor(today.plusDays(3)))
        assertEquals(AccountStatus.ACTIVE, statusFor(today.plusDays(4)))
        assertEquals(AccountStatus.ACTIVE, statusFor(null))
    }

    // ------------------------------------------------------- applyPayment

    @Test
    fun `applyPayment reports before after and completion`() {
        val outcome = BalanceCalculator.applyPayment(
            total = Money.ofTaka(10_000),
            alreadyPaid = Money.ofTaka(2_500),
            payment = Money.ofTaka(7_500),
        )
        assertEquals(Money.ofTaka(7_500), outcome.remainingBefore)
        assertEquals(Money.ZERO, outcome.remainingAfter)
        assertEquals(Money.ofTaka(10_000), outcome.totalPaid)
        assertTrue(outcome.isFullyPaid)
        assertEquals(Money.ZERO, outcome.overpaid)
    }

    @Test
    fun `applyPayment flags overpayment but clamps the balance`() {
        val outcome = BalanceCalculator.applyPayment(
            total = Money.ofTaka(1_000),
            alreadyPaid = Money.ZERO,
            payment = Money.ofTaka(1_500),
        )
        assertEquals(Money.ZERO, outcome.remainingAfter)
        assertTrue(outcome.isFullyPaid)
        assertEquals(Money.ofTaka(500), outcome.overpaid)
    }

    @Test
    fun `a partial payment does not complete the account`() {
        val outcome = BalanceCalculator.applyPayment(
            total = Money.ofTaka(10_000),
            alreadyPaid = Money.ZERO,
            payment = Money.ofTaka(2_500),
        )
        assertEquals(Money.ofTaka(7_500), outcome.remainingAfter)
        assertFalse(outcome.isFullyPaid)
    }

    // -------------------------------------------------- installment split

    @Test
    fun `payment fills installments in due order`() {
        val installments = listOf(
            BalanceCalculator.InstallmentBalance(1, today.plusDays(30), Money.ofTaka(1_000), Money.ZERO),
            BalanceCalculator.InstallmentBalance(2, today.plusDays(60), Money.ofTaka(1_000), Money.ZERO),
            BalanceCalculator.InstallmentBalance(3, today.plusDays(90), Money.ofTaka(1_000), Money.ZERO),
        )

        val result = BalanceCalculator.allocateToInstallments(Money.ofTaka(1_500), installments)

        assertEquals(Money.ofTaka(1_000), result.installments[0].paid)
        assertEquals(Money.ofTaka(500), result.installments[1].paid)
        assertEquals(Money.ZERO, result.installments[2].paid)
        assertEquals(Money.ZERO, result.unallocated)
    }

    @Test
    fun `excess beyond every installment is reported as unallocated`() {
        val installments = listOf(
            BalanceCalculator.InstallmentBalance(1, today, Money.ofTaka(1_000), Money.ZERO),
        )
        val result = BalanceCalculator.allocateToInstallments(Money.ofTaka(1_600), installments)

        assertTrue(result.installments.single().isSettled)
        assertEquals(Money.ofTaka(600), result.unallocated)
    }

    @Test
    fun `already settled installments are skipped`() {
        val installments = listOf(
            BalanceCalculator.InstallmentBalance(1, today, Money.ofTaka(1_000), Money.ofTaka(1_000)),
            BalanceCalculator.InstallmentBalance(2, today.plusDays(30), Money.ofTaka(1_000), Money.ZERO),
        )
        val result = BalanceCalculator.allocateToInstallments(Money.ofTaka(400), installments)

        assertEquals(Money.ofTaka(1_000), result.installments[0].paid)
        assertEquals(Money.ofTaka(400), result.installments[1].paid)
        assertEquals(Money.ZERO, result.unallocated)
    }
}
