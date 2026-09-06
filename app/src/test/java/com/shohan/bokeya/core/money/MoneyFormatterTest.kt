package com.shohan.bokeya.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `amounts carry the taka symbol`() {
        assertEquals("৳ ১,২০০", MoneyFormatter.format(Money.ofTaka(1_200)))
        assertEquals("৳ 1,200", MoneyFormatter.format(Money.ofTaka(1_200), useBengaliDigits = false))
    }

    @Test
    fun `the symbol can be dropped`() {
        assertEquals("১,২০০", MoneyFormatter.format(Money.ofTaka(1_200), withSymbol = false))
    }

    /** South Asian grouping: 12,34,567 — not the western 1,234,567. */
    @Test
    fun `digits group the south asian way`() {
        assertEquals(
            "1,234",
            MoneyFormatter.format(Money.ofTaka(1_234), useBengaliDigits = false, withSymbol = false),
        )
        assertEquals(
            "12,345",
            MoneyFormatter.format(Money.ofTaka(12_345), useBengaliDigits = false, withSymbol = false),
        )
        assertEquals(
            "1,23,456",
            MoneyFormatter.format(Money.ofTaka(123_456), useBengaliDigits = false, withSymbol = false),
        )
        assertEquals(
            "12,34,567",
            MoneyFormatter.format(Money.ofTaka(1_234_567), useBengaliDigits = false, withSymbol = false),
        )
    }

    @Test
    fun `poisha appears only when asked for and non-zero`() {
        val amount = Money.ofMinor(120_050)
        assertEquals(
            "1,200",
            MoneyFormatter.format(amount, useBengaliDigits = false, withSymbol = false),
        )
        assertEquals(
            "1,200.50",
            MoneyFormatter.format(
                amount,
                useBengaliDigits = false,
                withSymbol = false,
                showPoisha = true,
            ),
        )
        // A round amount stays clean even with showPoisha on.
        assertEquals(
            "1,200",
            MoneyFormatter.format(
                Money.ofTaka(1_200),
                useBengaliDigits = false,
                withSymbol = false,
                showPoisha = true,
            ),
        )
    }

    @Test
    fun `negative amounts keep their sign in front`() {
        assertTrue(
            MoneyFormatter.format(Money.ofMinor(-50_000), useBengaliDigits = false).startsWith("-"),
        )
    }

    @Test
    fun `compact form uses bengali magnitudes`() {
        assertTrue(MoneyFormatter.formatCompact(Money.ofTaka(12_500)).contains("হাজার"))
        assertTrue(MoneyFormatter.formatCompact(Money.ofTaka(150_000)).contains("লাখ"))
        assertTrue(MoneyFormatter.formatCompact(Money.ofTaka(34_000_000)).contains("কোটি"))
        // Small amounts are shown as-is, with no magnitude word.
        assertEquals("৳ ৯৫০", MoneyFormatter.formatCompact(Money.ofTaka(950)))
    }

    @Test
    fun `plain form is machine readable for csv`() {
        assertEquals("1200", MoneyFormatter.formatPlain(Money.ofTaka(1_200), showPoisha = false))
        assertEquals("1200.50", MoneyFormatter.formatPlain(Money.ofMinor(120_050)))
    }

    @Test
    fun `digit conversion handles mixed text`() {
        assertEquals("০১২৩৪৫৬৭৮৯", MoneyFormatter.toBengaliDigits("0123456789"))
        assertEquals("১,২০০.৫০", MoneyFormatter.toBengaliDigits("1,200.50"))
        // Non-digits pass through untouched.
        assertEquals("৳ ১০০", MoneyFormatter.toBengaliDigits("৳ 100"))
    }

    @Test
    fun `numbers and percentages localise`() {
        assertEquals("৩", MoneyFormatter.formatNumber(3))
        assertEquals("3", MoneyFormatter.formatNumber(3, useBengaliDigits = false))
        assertEquals("৭৫%", MoneyFormatter.formatPercent(75))
        assertEquals("75%", MoneyFormatter.formatPercent(75, useBengaliDigits = false))
    }

    @Test
    fun `zero renders as zero not blank`() {
        assertEquals("৳ ০", MoneyFormatter.format(Money.ZERO))
    }
}
