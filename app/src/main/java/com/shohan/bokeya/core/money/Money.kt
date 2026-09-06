package com.shohan.bokeya.core.money

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money is stored as an integer number of **poisha** (1 Taka = 100 poisha).
 *
 * Financial arithmetic must never run through [Double]: `0.1 + 0.2 != 0.3` in
 * binary floating point, and those errors accumulate across thousands of
 * partial payments until a "fully paid" account still shows ৳0.01 remaining.
 * Keeping the canonical value in minor units makes addition and subtraction
 * exact, and confines rounding to the single, explicit place where a user
 * types a decimal amount.
 *
 * The class is a [JvmInline] value class, so it costs nothing at runtime.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    operator fun plus(other: Money) = Money(minor + other.minor)

    operator fun minus(other: Money) = Money(minor - other.minor)

    operator fun times(factor: Int) = Money(minor * factor)

    operator fun times(factor: Long) = Money(minor * factor)

    operator fun unaryMinus() = Money(-minor)

    /** Divides into [parts] equal shares, distributing the remainder over the first shares. */
    fun split(parts: Int): List<Money> {
        require(parts > 0) { "parts must be > 0" }
        val base = minor / parts
        val remainder = (minor % parts).toInt()
        val sign = if (minor < 0) -1 else 1
        return List(parts) { index ->
            val extra = if (index < abs(remainder)) sign else 0
            Money(base + extra)
        }
    }

    /** Never lets a balance fall below zero — used for "remaining" calculations. */
    fun coerceAtLeastZero(): Money = if (minor < 0) ZERO else this

    fun coerceAtMost(other: Money): Money = if (minor > other.minor) other else this

    fun coerceAtLeast(other: Money): Money = if (minor < other.minor) other else this

    fun abs(): Money = Money(abs(minor))

    /** Only for presentation (charts, percentages) — never for arithmetic. */
    fun toDouble(): Double = minor / 100.0

    /** Whole Taka part, discarding poisha. */
    val wholeTaka: Long get() = minor / 100

    val poishaPart: Int get() = (abs(minor) % 100).toInt()

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    override fun toString(): String = "Money($minor poisha)"

    companion object {
        val ZERO = Money(0)

        fun ofTaka(taka: Long) = Money(taka * 100)

        fun ofTaka(taka: Int) = Money(taka.toLong() * 100)

        /** Rounds half-up at the poisha boundary — the only rounding point in the app. */
        fun ofTaka(taka: Double): Money = Money((taka * 100.0).roundToLong())

        fun ofMinor(minor: Long) = Money(minor)

        /**
         * Parses user input such as `"1200"`, `"1,200.50"` or `"১২০০.৫০"`.
         * Returns `null` when the text is not a valid non-negative amount.
         */
        fun parseOrNull(input: String): Money? {
            val normalised = input.trim()
                .map { ch ->
                    when (ch) {
                        in '০'..'৯' -> '0' + (ch - '০')   // Bengali digits
                        else -> ch
                    }
                }
                .joinToString("")
                .replace(",", "")
                .replace("৳", "")
                .replace(" ", "")
                .trim()

            if (normalised.isEmpty()) return null
            if (normalised.count { it == '.' } > 1) return null
            if (!normalised.all { it.isDigit() || it == '.' }) return null

            val parts = normalised.split('.')
            val takaPart = parts[0].ifEmpty { "0" }
            val poishaPart = parts.getOrNull(1).orEmpty()

            val taka = takaPart.toLongOrNull() ?: return null
            if (taka > MAX_TAKA) return null

            val poisha = when {
                poishaPart.isEmpty() -> 0L
                poishaPart.length == 1 -> poishaPart.toLongOrNull()?.times(10) ?: return null
                else -> poishaPart.take(2).toLongOrNull() ?: return null
            }
            return Money(taka * 100 + poisha)
        }

        /** Guards against absurd input that would overflow later multiplications. */
        const val MAX_TAKA = 9_999_999_999L

        fun sum(values: Iterable<Money>): Money = Money(values.sumOf { it.minor })
    }
}

fun Iterable<Money>.sumOfMoney(): Money = Money(sumOf { it.minor })
