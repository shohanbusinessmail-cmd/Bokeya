package com.shohan.bokeya.core.money

import kotlin.math.abs

/**
 * Formats [Money] the way Bangladeshi users read it.
 *
 * Bangladesh uses the **lakh/crore** grouping (12,34,567) rather than the
 * western thousands grouping (1,234,567), so this is implemented directly
 * instead of relying on [java.text.NumberFormat].
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

    /** Compact form for dense surfaces: ১২.৫ হাজার, ১.২ লাখ, ৩.৪ কোটি. */
    fun formatCompact(money: Money, useBengaliDigits: Boolean = true, withSymbol: Boolean = true): String {
        val negative = money.minor < 0
        val taka = abs(money.minor) / 100

        val (value, suffix) = when {
            taka >= 10_000_000L -> taka / 10_000_000.0 to "কোটি"
            taka >= 100_000L -> taka / 100_000.0 to "লাখ"
            taka >= 1_000L -> taka / 1_000.0 to "হাজার"
            else -> taka.toDouble() to ""
        }

        val text = if (suffix.isEmpty()) {
            taka.toString()
        } else {
            val rounded = (value * 10).toLong() / 10.0
            if (rounded % 1.0 == 0.0) "${rounded.toLong()}" else String.format("%.1f", rounded)
        }

        val localised = if (useBengaliDigits) toBengaliDigits(text) else text
        return buildString {
            if (negative) append('-')
            if (withSymbol) {
                append(SYMBOL)
                append(' ')
            }
            append(localised)
            if (suffix.isNotEmpty()) {
                append(' ')
                append(suffix)
            }
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
