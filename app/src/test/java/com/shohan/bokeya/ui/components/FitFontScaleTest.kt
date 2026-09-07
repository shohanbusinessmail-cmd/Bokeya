package com.shohan.bokeya.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shrink-to-fit rule behind [AutoSizeText].
 *
 * A hero amount that does not fit must be scaled down, never clipped and never
 * ellipsised — the failure that left "মোট বকেয়া ৳" with no number.
 */
class FitFontScaleTest {

    @Test
    fun `text that already fits is left alone`() {
        assertEquals(1f, fitFontScale(200, 400, 40f, 11f), 0f)
        assertEquals(1f, fitFontScale(400, 400, 40f, 11f), 0f)
    }

    @Test
    fun `text is never inflated to fill spare room`() {
        // Half the available width still renders at its designed size, so the
        // approved visual hierarchy does not drift.
        assertTrue(fitFontScale(100, 1_000, 40f, 11f) <= 1f)
    }

    @Test
    fun `oversized text is scaled down to fit`() {
        val scale = fitFontScale(naturalWidthPx = 800, availableWidthPx = 400, baseFontSizeSp = 40f, minFontSizeSp = 11f)
        assertTrue("expected shrink, got $scale", scale < 1f)
        // 800px at scale must land inside 400px.
        assertTrue(800 * scale <= 400f)
    }

    @Test
    fun `a very long amount stops at the legibility floor`() {
        // ৳ ১,০০,০০,০০০ in a tiny tile: clamped at 11sp of a 40sp style.
        val scale = fitFontScale(naturalWidthPx = 5_000, availableWidthPx = 100, baseFontSizeSp = 40f, minFontSizeSp = 11f)
        assertEquals(11f / 40f, scale, 0.0001f)
    }

    @Test
    fun `the fitted width never exceeds the space available`() {
        val widths = listOf(401, 500, 640, 1_000, 2_500)
        widths.forEach { natural ->
            val scale = fitFontScale(natural, 400, 40f, 1f)
            assertTrue(
                "natural=$natural scaled to ${natural * scale}, limit 400",
                natural * scale <= 400f,
            )
        }
    }

    @Test
    fun `degenerate measurements fall back to the natural size`() {
        assertEquals(1f, fitFontScale(0, 400, 40f, 11f), 0f)
        assertEquals(1f, fitFontScale(400, 0, 40f, 11f), 0f)
        assertEquals(1f, fitFontScale(-5, 400, 40f, 11f), 0f)
    }

    @Test
    fun `scale always stays within the floor and one`() {
        val cases = listOf(
            Triple(800, 400, 11f),
            Triple(10_000, 50, 11f),
            Triple(100, 400, 11f),
            Triple(1, 1, 11f),
        )
        cases.forEach { (natural, available, min) ->
            val scale = fitFontScale(natural, available, 40f, min)
            assertTrue("scale=$scale", scale <= 1f)
            assertTrue("scale=$scale", scale >= min / 40f - 0.0001f)
        }
    }
}
