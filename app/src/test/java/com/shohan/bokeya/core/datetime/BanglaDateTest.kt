package com.shohan.bokeya.core.datetime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaDateTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    // ------------------------------------------------------------ relative

    @Test
    fun `today tomorrow and yesterday read naturally`() {
        assertEquals("আজ", BanglaDate.relativeDay(today, today))
        assertEquals("আগামীকাল", BanglaDate.relativeDay(today.plusDays(1), today))
        assertEquals("গতকাল", BanglaDate.relativeDay(today.minusDays(1), today))
    }

    @Test
    fun `near dates are counted in days`() {
        assertEquals("৩ দিন পর", BanglaDate.relativeDay(today.plusDays(3), today))
        assertEquals("৫ দিন আগে", BanglaDate.relativeDay(today.minusDays(5), today))
    }

    @Test
    fun `distant dates fall back to a written date`() {
        val far = today.plusDays(40)
        val text = BanglaDate.relativeDay(far, today)
        assertTrue(text.contains("এপ্রিল") || text.contains("মে"))
    }

    @Test
    fun `relativeDayLong spells out other years`() {
        val nextYear = LocalDate.of(2027, 6, 1)
        val text = BanglaDate.relativeDayLong(nextYear, today)
        assertTrue(text.contains("২০২৭"))
    }

    @Test
    fun `latin digits are used when bengali digits are off`() {
        assertEquals("3 দিন পর", BanglaDate.relativeDay(today.plusDays(3), today, useBengaliDigits = false))
    }

    // ------------------------------------------------------- month & week

    @Test
    fun `month names are bengali`() {
        assertEquals("জানুয়ারি", BanglaDate.monthName(1))
        assertEquals("ডিসেম্বর", BanglaDate.monthName(12))
        assertEquals("জানু", BanglaDate.monthShort(1))
    }

    @Test
    fun `month boundaries are inclusive`() {
        assertEquals(LocalDate.of(2026, 3, 1), BanglaDate.startOfMonth(today))
        assertEquals(LocalDate.of(2026, 3, 31), BanglaDate.endOfMonth(today))
    }

    @Test
    fun `february length follows the leap year`() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            BanglaDate.endOfMonth(LocalDate.of(2026, 2, 10)),
        )
        assertEquals(
            LocalDate.of(2028, 2, 29),
            BanglaDate.endOfMonth(LocalDate.of(2028, 2, 10)),
        )
    }

    /** The Bangladeshi week runs Saturday to Friday. */
    @Test
    fun `weeks start on saturday`() {
        // 2026-03-15 is a Sunday, so its week began the day before.
        assertEquals(DayOfWeek.SUNDAY, today.dayOfWeek)
        val weekStart = BanglaDate.startOfWeek(today)
        assertEquals(DayOfWeek.SATURDAY, weekStart.dayOfWeek)
        assertEquals(LocalDate.of(2026, 3, 14), weekStart)
        assertEquals(LocalDate.of(2026, 3, 20), BanglaDate.endOfWeek(today))
    }

    @Test
    fun `a saturday is its own week start`() {
        val saturday = LocalDate.of(2026, 3, 14)
        assertEquals(saturday, BanglaDate.startOfWeek(saturday))
    }

    @Test
    fun `weekday headers are saturday first and cover seven days`() {
        assertEquals(7, BanglaDate.WEEKDAY_HEADERS_SAT_FIRST.size)
        assertEquals("শনি", BanglaDate.WEEKDAY_HEADERS_SAT_FIRST.first())
        assertEquals("শুক্র", BanglaDate.WEEKDAY_HEADERS_SAT_FIRST.last())
    }

    // ----------------------------------------------------- epoch round-trip

    @Test
    fun `epoch day conversion round-trips`() {
        val epochDay = BanglaDate.toEpochDay(today)
        assertEquals(today, BanglaDate.fromEpochDay(epochDay))
    }

    @Test
    fun `epoch millis round-trips through the local zone`() {
        val millis = BanglaDate.toEpochMillis(today)
        assertEquals(today, BanglaDate.dateOf(millis))
    }

    @Test
    fun `daysBetween is signed`() {
        assertEquals(5L, BanglaDate.daysBetween(today, today.plusDays(5)))
        assertEquals(-5L, BanglaDate.daysBetween(today, today.minusDays(5)))
        assertEquals(0L, BanglaDate.daysBetween(today, today))
    }

    // -------------------------------------------------------------- time

    @Test
    fun `time is formatted as bengali 12-hour with a day part`() {
        assertEquals("সকাল ৯:০০", BanglaDate.formatTime(LocalTime.of(9, 0)))
        assertEquals("দুপুর ১২:৩০", BanglaDate.formatTime(LocalTime.of(12, 30)))
        assertEquals("রাত ১২:০৫", BanglaDate.formatTime(LocalTime.of(0, 5)))
        assertEquals("বিকেল ৪:৪৫", BanglaDate.formatTime(LocalTime.of(16, 45)))
    }

    @Test
    fun `minutes are zero padded`() {
        assertTrue(BanglaDate.formatTime(LocalTime.of(9, 5)).endsWith("০৫"))
    }

    @Test
    fun `greetings follow the clock`() {
        assertEquals(BanglaDate.GreetingType.MORNING, BanglaDate.greeting(8))
        assertEquals(BanglaDate.GreetingType.NOON, BanglaDate.greeting(13))
        assertEquals(BanglaDate.GreetingType.AFTERNOON, BanglaDate.greeting(16))
        assertEquals(BanglaDate.GreetingType.EVENING, BanglaDate.greeting(19))
        assertEquals(BanglaDate.GreetingType.NIGHT, BanglaDate.greeting(2))
    }

    @Test
    fun `full date includes day month and year`() {
        val text = BanglaDate.formatFull(today)
        assertTrue(text.contains("১৫"))
        assertTrue(text.contains("মার্চ"))
        assertTrue(text.contains("২০২৬"))
    }
}
