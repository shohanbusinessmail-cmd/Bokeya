package com.shohan.bokeya.core.datetime

import com.shohan.bokeya.core.money.MoneyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Date and time helpers that respect the device timezone and read naturally in Bangla.
 *
 * Dates are persisted as **epoch days** and timestamps as **epoch millis**, both
 * timezone independent; conversion to a wall-clock date happens here using the
 * device's current [ZoneId] so the app stays correct when the user travels.
 */
object BanglaDate {

    private val MONTHS = arrayOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
    )

    private val MONTHS_SHORT = arrayOf(
        "জানু", "ফেব", "মার্চ", "এপ্রি", "মে", "জুন",
        "জুলা", "আগ", "সেপ্ট", "অক্টো", "নভে", "ডিসে",
    )

    /** Indexed by [java.time.DayOfWeek.getValue] - 1, i.e. Monday first. */
    private val WEEKDAYS = arrayOf(
        "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার",
    )

    private val WEEKDAYS_SHORT = arrayOf("সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি", "রবি")

    /** Calendar grids in Bangladesh start on Saturday. */
    val WEEKDAY_HEADERS_SAT_FIRST = arrayOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র")

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now(zone())

    fun nowMillis(): Long = System.currentTimeMillis()

    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    fun fromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDateTime()

    fun toEpochMillis(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone()).toInstant().toEpochMilli()

    fun dateOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()

    // ---------------------------------------------------------------- formatting

    /** `৬ সেপ্টেম্বর ২০২৬` */
    fun formatFull(date: LocalDate, useBengaliDigits: Boolean = true): String {
        val day = MoneyFormatter.formatNumber(date.dayOfMonth, useBengaliDigits)
        val year = MoneyFormatter.formatNumber(date.year, useBengaliDigits)
        return "$day ${MONTHS[date.monthValue - 1]} $year"
    }

    /** `৬ সেপ্টেম্বর` — used where the year is obvious from context. */
    fun formatDayMonth(date: LocalDate, useBengaliDigits: Boolean = true): String {
        val day = MoneyFormatter.formatNumber(date.dayOfMonth, useBengaliDigits)
        return "$day ${MONTHS[date.monthValue - 1]}"
    }

    fun formatShort(date: LocalDate, useBengaliDigits: Boolean = true): String {
        val day = MoneyFormatter.formatNumber(date.dayOfMonth, useBengaliDigits)
        return "$day ${MONTHS_SHORT[date.monthValue - 1]}"
    }

    fun monthName(month: Int): String = MONTHS[month - 1]

    fun monthShort(month: Int): String = MONTHS_SHORT[month - 1]

    /** `সেপ্টেম্বর ২০২৬` */
    fun formatMonthYear(date: LocalDate, useBengaliDigits: Boolean = true): String =
        "${MONTHS[date.monthValue - 1]} ${MoneyFormatter.formatNumber(date.year, useBengaliDigits)}"

    fun weekdayName(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    fun weekdayShort(date: LocalDate): String = WEEKDAYS_SHORT[date.dayOfWeek.value - 1]

    /**
     * Bengali 12-hour clock with the traditional day-part prefix:
     * রাত ৯:৩০, সকাল ৭:০৫, দুপুর ১:১৫, বিকেল ৪:৪০, সন্ধ্যা ৬:২০.
     */
    fun formatTime(time: LocalTime, useBengaliDigits: Boolean = true): String {
        val hour24 = time.hour
        val part = dayPart(hour24)
        val hour12 = when (val h = hour24 % 12) {
            0 -> 12
            else -> h
        }
        val hh = MoneyFormatter.formatNumber(hour12, useBengaliDigits)
        val mm = MoneyFormatter.formatNumber(time.minute, useBengaliDigits).padStartLocalised(2, useBengaliDigits)
        return "$part $hh:$mm"
    }

    fun formatTime(epochMillis: Long, useBengaliDigits: Boolean = true): String =
        formatTime(toLocalDateTime(epochMillis).toLocalTime(), useBengaliDigits)

    private fun String.padStartLocalised(length: Int, useBengaliDigits: Boolean): String {
        val zero = if (useBengaliDigits) '০' else '0'
        return padStart(length, zero)
    }

    fun dayPart(hour24: Int): String = when (hour24) {
        in 4..11 -> "সকাল"
        in 12..14 -> "দুপুর"
        in 15..17 -> "বিকেল"
        in 18..19 -> "সন্ধ্যা"
        else -> "রাত"
    }

    fun greeting(hour24: Int): GreetingType = when (hour24) {
        in 4..11 -> GreetingType.MORNING
        in 12..14 -> GreetingType.NOON
        in 15..17 -> GreetingType.AFTERNOON
        in 18..20 -> GreetingType.EVENING
        else -> GreetingType.NIGHT
    }

    /**
     * Human wording relative to today: আজ / আগামীকাল / গতকাল, then
     * `৩ দিন পর`, `৫ দিন আগে`, falling back to a full date beyond a week.
     */
    fun relativeDay(date: LocalDate, today: LocalDate = today(), useBengaliDigits: Boolean = true): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "আজ"
            days == 1L -> "আগামীকাল"
            days == -1L -> "গতকাল"
            days in 2..6 -> "${MoneyFormatter.formatNumber(days, useBengaliDigits)} দিন পর"
            days in -6..-2 -> "${MoneyFormatter.formatNumber(-days, useBengaliDigits)} দিন আগে"
            else -> formatDayMonth(date, useBengaliDigits)
        }
    }

    /** Same as [relativeDay] but always spells out distant dates with the year. */
    fun relativeDayLong(date: LocalDate, today: LocalDate = today(), useBengaliDigits: Boolean = true): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "আজ"
            days == 1L -> "আগামীকাল"
            days == -1L -> "গতকাল"
            days in 2..6 -> "${MoneyFormatter.formatNumber(days, useBengaliDigits)} দিন পর"
            days in -6..-2 -> "${MoneyFormatter.formatNumber(-days, useBengaliDigits)} দিন আগে"
            date.year == today.year -> formatDayMonth(date, useBengaliDigits)
            else -> formatFull(date, useBengaliDigits)
        }
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    fun startOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun endOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(date.lengthOfMonth())

    /** Week runs Saturday -> Friday, matching the Bangladeshi work week. */
    fun startOfWeek(date: LocalDate): LocalDate {
        // DayOfWeek: Mon=1 .. Sat=6, Sun=7. Days elapsed since the most recent Saturday.
        val shift = (date.dayOfWeek.value + 1) % 7
        return date.minusDays(shift.toLong())
    }

    fun endOfWeek(date: LocalDate): LocalDate = startOfWeek(date).plusDays(6)

    enum class GreetingType { MORNING, NOON, AFTERNOON, EVENING, NIGHT }
}
