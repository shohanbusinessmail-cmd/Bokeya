package com.shohan.bokeya.core.money

import kotlin.math.abs

/**
 * Formats [Money] the way Bangladeshi users read it.
 *
 * Bangladesh uses the **lakh/crore** grouping (12,34,567) rather than the
 * western thousands grouping (1,234,567), so this is implemented directly
 * instead of relying on [java.text.NumberFormat].
 *
 * There is deliberately no compact/abbreviated form ("৫ হাজার", "1.2M").
 * Monetary values are always rendered in full; when space is tight the UI
 * scales the type down via `AmountText` rather than shortening the number.
 */
object MoneyFormatter {

    const val SYMBOL = "৳"

    private val BENGALI_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    /**
     * @param useBengaliDigits render digits as ০-৯ instead of 0-9
     * @param withSymbol prefix the amount with ৳
     * @param showPoisha keep the decimal part; when false the value is shown as whole Taka
     */
    fun format(
        money: Money,
        useBengaliDigits: Boolean = true,
        withSymbol: Boolean = true,
        showPoisha: Boolean = false,
    ): String {
        val negative = money.minor < 0
        val absMinor = abs(money.minor)
        val taka = absMinor / 100
        val poisha = (absMinor % 100).toInt()

        val grouped = groupIndianStyle(taka.toString())
        val body = if (showPoisha && poisha != 0) {
            "$grouped.${poisha.toString().padStart(2, '0')}"
        } else {
            grouped
        }

        val localised = if (useBengaliDigits) toBengaliDigits(body) else body
        return buildString {
            if (negative) append('-')
            if (withSymbol) {
                append(SYMBOL)
                append(' ')
            }
            append(localised)
        }
    }

    /** Amount without symbol, for text fields and CSV cells. */
    fun formatPlain(money: Money, showPoisha: Boolean = true): String {
        val absMinor = abs(money.minor)
        val taka = absMinor / 100
        val poisha = (absMinor % 100).toInt()
        val sign = if (money.minor < 0) "-" else ""
        return if (showPoisha && poisha != 0) {
            "$sign$taka.${poisha.toString().padStart(2, '0')}"
        } else {
            "$sign$taka"
        }
    }

    fun toBengaliDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(if (ch in '0'..'9') BENGALI_DIGITS[ch - '0'] else ch)
        }
    }

    fun formatNumber(value: Int, useBengaliDigits: Boolean = true): String =
        if (useBengaliDigits) toBengaliDigits(value.toString()) else value.toString()

    fun formatNumber(value: Long, useBengaliDigits: Boolean = true): String =
        if (useBengaliDigits) toBengaliDigits(value.toString()) else value.toString()

    fun formatPercent(value: Int, useBengaliDigits: Boolean = true): String =
        "${formatNumber(value, useBengaliDigits)}%"

    /**
     * Groups digits the South Asian way: the last three digits stay together,
     * then every two digits get a separator — 1234567 becomes 12,34,567.
     */
    private fun groupIndianStyle(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        val chunks = mutableListOf<String>()
        var remaining = head
        while (remaining.length > 2) {
            chunks.add(0, remaining.takeLast(2))
            remaining = remaining.dropLast(2)
        }
        if (remaining.isNotEmpty()) chunks.add(0, remaining)
        return chunks.joinToString(",") + "," + tail
    }
}
