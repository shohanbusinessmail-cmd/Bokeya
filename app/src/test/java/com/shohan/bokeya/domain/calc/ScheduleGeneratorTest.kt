package com.shohan.bokeya.domain.calc

import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.domain.model.InstallmentFrequency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleGeneratorTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 10)

    // ------------------------------------------------------------ generate

    @Test
    fun `schedule totals match the amount owed exactly`() {
        val schedule = ScheduleGenerator.generate(
            totalAmount = Money.ofTaka(10_000),
            perInstallment = Money.ofTaka(3_000),
            firstDueDate = start,
            frequency = InstallmentFrequency.MONTHLY,
        )

        assertEquals(4, schedule.size)
        assertEquals(Money.ofTaka(10_000), Money.sum(schedule.map { it.amount }))
        // The last instalment absorbs the shortfall rather than overshooting.
        assertEquals(Money.ofTaka(1_000), schedule.last().amount)
    }

    @Test
    fun `an even schedule has equal installments`() {
        val schedule = ScheduleGenerator.generate(
            totalAmount = Money.ofTaka(12_000),
            perInstallment = Money.ofTaka(1_000),
            firstDueDate = start,
            frequency = InstallmentFrequency.MONTHLY,
        )
        assertEquals(12, schedule.size)
        assertTrue(schedule.all { it.amount == Money.ofTaka(1_000) })
    }

    @Test
    fun `sequences are one-based and contiguous`() {
        val schedule = ScheduleGenerator.generate(
            totalAmount = Money.ofTaka(5_000),
            perInstallment = Money.ofTaka(1_000),
            firstDueDate = start,
            frequency = InstallmentFrequency.MONTHLY,
        )
        assertEquals(listOf(1, 2, 3, 4, 5), schedule.map { it.sequence })
    }

    @Test
    fun `zero or negative input produces no schedule`() {
        assertTrue(
            ScheduleGenerator.generate(
                Money.ZERO,
                Money.ofTaka(100),
                start,
                InstallmentFrequency.MONTHLY,
            ).isEmpty(),
        )
        assertTrue(
            ScheduleGenerator.generate(
                Money.ofTaka(100),
                Money.ZERO,
                start,
                InstallmentFrequency.MONTHLY,
            ).isEmpty(),
        )
    }

    @Test
    fun `absurd input is capped instead of exploding`() {
        val schedule = ScheduleGenerator.generate(
            totalAmount = Money.ofTaka(1_000_000),
            perInstallment = Money.ofMinor(1),
            firstDueDate = start,
            frequency = InstallmentFrequency.DAILY,
        )
        assertEquals(ScheduleGenerator.MAX_INSTALLMENTS, schedule.size)
    }

    // ----------------------------------------------------- generateByCount

    @Test
    fun `EMI schedule splits exactly across the tenure`() {
        val schedule = ScheduleGenerator.generateByCount(
            totalAmount = Money.ofTaka(24_000),
            count = 12,
            firstDueDate = start,
            frequency = InstallmentFrequency.MONTHLY,
        )

        assertEquals(12, schedule.size)
        assertTrue(schedule.all { it.amount == Money.ofTaka(2_000) })
        assertEquals(Money.ofTaka(24_000), Money.sum(schedule.map { it.amount }))
    }

    @Test
    fun `an indivisible EMI still sums to the financed amount`() {
        val schedule = ScheduleGenerator.generateByCount(
            totalAmount = Money.ofTaka(10_000),
            count = 3,
            firstDueDate = start,
            frequency = InstallmentFrequency.MONTHLY,
        )
        assertEquals(Money.ofTaka(10_000), Money.sum(schedule.map { it.amount }))
        // Difference between the largest and smallest share is at most 1 poisha.
        val minor = schedule.map { it.amount.minor }
        assertTrue(minor.max() - minor.min() <= 1L)
    }

    @Test
    fun `EMI due dates advance one month at a time`() {
        val schedule = ScheduleGenerator.generateByCount(
            totalAmount = Money.ofTaka(3_000),
            count = 3,
            firstDueDate = start,
            frequency = InstallmentFrequency.MONTHLY,
        )
        assertEquals(LocalDate.of(2026, 1, 10), schedule[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 10), schedule[1].dueDate)
        assertEquals(LocalDate.of(2026, 3, 10), schedule[2].dueDate)
    }

    // ----------------------------------------------------------- dueDateFor

    @Test
    fun `frequencies step by the right interval`() {
        assertEquals(
            start.plusDays(5),
            ScheduleGenerator.dueDateFor(start, 5, InstallmentFrequency.DAILY),
        )
        assertEquals(
            start.plusWeeks(3),
            ScheduleGenerator.dueDateFor(start, 3, InstallmentFrequency.WEEKLY),
        )
        assertEquals(
            start.plusMonths(2),
            ScheduleGenerator.dueDateFor(start, 2, InstallmentFrequency.MONTHLY),
        )
        assertEquals(
            start.plusDays(45),
            ScheduleGenerator.dueDateFor(start, 3, InstallmentFrequency.CUSTOM, customIntervalDays = 15),
        )
    }

    /** 31 January + 1 month has no 31st — it must clamp, not overflow into March. */
    @Test
    fun `month-end dates clamp to a valid day`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(
            LocalDate.of(2026, 2, 28),
            ScheduleGenerator.dueDateFor(jan31, 1, InstallmentFrequency.MONTHLY),
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            ScheduleGenerator.dueDateFor(jan31, 2, InstallmentFrequency.MONTHLY),
        )
    }

    @Test
    fun `leap year February is respected`() {
        val jan31 = LocalDate.of(2028, 1, 31)
        assertEquals(
            LocalDate.of(2028, 2, 29),
            ScheduleGenerator.dueDateFor(jan31, 1, InstallmentFrequency.MONTHLY),
        )
    }

    // ------------------------------------------------------------ nextDue

    @Test
    fun `next due date is strictly after the reference day`() {
        val next = ScheduleGenerator.nextDueDate(
            start = start,
            from = LocalDate.of(2026, 3, 10),
            frequency = InstallmentFrequency.MONTHLY,
        )
        assertEquals(LocalDate.of(2026, 4, 10), next)
    }

    @Test
    fun `next due date from before the start is the start itself`() {
        val next = ScheduleGenerator.nextDueDate(
            start = start,
            from = LocalDate.of(2025, 12, 1),
            frequency = InstallmentFrequency.MONTHLY,
        )
        assertEquals(start, next)
    }

    // ---------------------------------------------------- simple interest

    @Test
    fun `simple interest follows principal times rate times years`() {
        // 100000 at 10% flat for 12 months -> 110000
        assertEquals(
            Money.ofTaka(110_000),
            ScheduleGenerator.simpleInterestPayable(Money.ofTaka(100_000), 10.0, 12),
        )
        // Half a year is half the interest.
        assertEquals(
            Money.ofTaka(105_000),
            ScheduleGenerator.simpleInterestPayable(Money.ofTaka(100_000), 10.0, 6),
        )
    }

    @Test
    fun `no interest returns the principal untouched`() {
        assertEquals(
            Money.ofTaka(50_000),
            ScheduleGenerator.simpleInterestPayable(Money.ofTaka(50_000), 0.0, 24),
        )
        assertEquals(
            Money.ofTaka(50_000),
            ScheduleGenerator.simpleInterestPayable(Money.ofTaka(50_000), 12.0, 0),
        )
    }

    @Test
    fun `end date is the last installment not one past it`() {
        assertEquals(
            LocalDate.of(2026, 12, 10),
            ScheduleGenerator.endDate(start, 12, InstallmentFrequency.MONTHLY),
        )
        assertEquals(start, ScheduleGenerator.endDate(start, 1, InstallmentFrequency.MONTHLY))
    }
}
