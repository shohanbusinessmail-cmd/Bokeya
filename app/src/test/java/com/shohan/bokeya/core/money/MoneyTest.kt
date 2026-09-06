package com.shohan.bokeya.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Money is the foundation every other calculation sits on, so these tests are
 * deliberately paranoid about exactness rather than "close enough".
 */
class MoneyTest {

    @Test
    fun `taka converts to poisha`() {
        assertEquals(100_000L, Money.ofTaka(1000).minor)
        assertEquals(0L, Money.ZERO.minor)
    }

    @Test
    fun `addition and subtraction are exact`() {
        val a = Money.ofTaka(10_000)
        val b = Money.ofTaka(2_500)
        assertEquals(Money.ofTaka(12_500), a + b)
        assertEquals(Money.ofTaka(7_500), a - b)
    }

    /**
     * The classic float trap: 0.1 + 0.2 != 0.3 in binary floating point.
     * Because Money is integer poisha this must be exact.
     */
    @Test
    fun `decimal amounts do not drift`() {
        val tenPoisha = Money.parseOrNull("0.10")!!
        val twentyPoisha = Money.parseOrNull("0.20")!!
        assertEquals(Money.parseOrNull("0.30"), tenPoisha + twentyPoisha)
        assertEquals(30L, (tenPoisha + twentyPoisha).minor)
    }

    @Test
    fun `a thousand small payments sum exactly`() {
        var total = Money.ZERO
        repeat(1000) { total += Money.ofMinor(1) }
        assertEquals(Money.ofTaka(10), total)
    }

    @Test
    fun `split distributes the remainder without losing poisha`() {
        val parts = Money.ofTaka(100).split(3)
        assertEquals(3, parts.size)
        // 10000 poisha / 3 = 3334 + 3333 + 3333
        assertEquals(3334L, parts[0].minor)
        assertEquals(3333L, parts[1].minor)
        assertEquals(3333L, parts[2].minor)
        assertEquals(Money.ofTaka(100), Money.sum(parts))
    }

    @Test
    fun `split of an exact multiple is even`() {
        val parts = Money.ofTaka(1200).split(12)
        assertTrue(parts.all { it == Money.ofTaka(100) })
        assertEquals(Money.ofTaka(1200), Money.sum(parts))
    }

    @Test
    fun `coerceAtLeastZero clamps negatives`() {
        assertEquals(Money.ZERO, (Money.ofTaka(100) - Money.ofTaka(250)).coerceAtLeastZero())
        assertEquals(Money.ofTaka(50), Money.ofTaka(50).coerceAtLeastZero())
    }

    @Test
    fun `parses plain grouped and bengali input`() {
        assertEquals(Money.ofTaka(1200), Money.parseOrNull("1200"))
        assertEquals(Money.ofTaka(1200), Money.parseOrNull("1,200"))
        assertEquals(Money.ofMinor(120_050), Money.parseOrNull("1,200.50"))
        assertEquals(Money.ofTaka(1200), Money.parseOrNull("১২০০"))
        assertEquals(Money.ofMinor(120_050), Money.parseOrNull("১২০০.৫০"))
    }

    @Test
    fun `rejects junk and negative input`() {
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("   "))
        assertNull(Money.parseOrNull("abc"))
        assertNull(Money.parseOrNull("-100"))
    }

    @Test
    fun `sign helpers agree`() {
        assertTrue(Money.ZERO.isZero)
        assertFalse(Money.ZERO.isPositive)
        assertTrue(Money.ofTaka(1).isPositive)
        assertTrue(Money.ofMinor(-1).isNegative)
    }

    @Test
    fun `whole taka and poisha parts split correctly`() {
        val amount = Money.ofMinor(123_456)
        assertEquals(1234L, amount.wholeTaka)
        assertEquals(56, amount.poishaPart)
    }

    @Test
    fun `comparison orders by minor units`() {
        assertTrue(Money.ofTaka(100) > Money.ofTaka(99))
        assertTrue(Money.ofMinor(1) > Money.ZERO)
        assertEquals(Money.ofTaka(100), Money.ofMinor(10_000))
    }
}
