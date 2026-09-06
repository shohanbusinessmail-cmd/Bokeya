package com.shohan.bokeya.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.shohan.bokeya.R
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.Account
import java.io.File

/**
 * Renders a report as a real PDF using Android's built-in [PdfDocument].
 *
 * Bangla text is drawn with the bundled Hind Siliguri typeface — the platform's
 * default PDF font has no Bengali glyphs, so without this every label would
 * come out as boxes. Layout is manual because there is no reflow engine here:
 * a running `y` cursor emits rows and starts a new page before overflowing.
 */
internal class PdfReportWriter(private val context: Context) {

    private companion object {
        // A4 at 72dpi, the unit PdfDocument works in.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

        const val NAVY = 0xFF0E1A2B.toInt()
        const val EMERALD = 0xFF059669.toInt()
        const val ROSE = 0xFFBE123C.toInt()
        const val SLATE = 0xFF64748B.toInt()
        const val SLATE_LIGHT = 0xFFE2E8F0.toInt()
        const val INK = 0xFF0F172A.toInt()
    }

    private val regular: Typeface =
        ResourcesCompat.getFont(context, R.font.hind_siliguri_regular) ?: Typeface.DEFAULT
    private val medium: Typeface =
        ResourcesCompat.getFont(context, R.font.hind_siliguri_medium) ?: Typeface.DEFAULT_BOLD
    private val bold: Typeface =
        ResourcesCompat.getFont(context, R.font.hind_siliguri_bold) ?: Typeface.DEFAULT_BOLD

    private fun paint(
        size: Float,
        color: Int = INK,
        face: Typeface = regular,
        align: Paint.Align = Paint.Align.LEFT,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
        textAlign = align
    }

    fun write(file: File, data: ExportManager.ReportData) {
        val document = PdfDocument()
        val state = PageState(document)
        state.newPage()

        drawHeader(state, data)

        if (data.accounts.isNotEmpty()) {
            drawSummaryCards(state, data)
            drawAccountsTable(state, data)
        }

        if (data.entries.isNotEmpty()) {
            drawMoneySummary(state, data)
            drawCategoryBreakdown(state, data)
            drawEntriesTable(state, data)
        }

        state.finishPage()

        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    // ------------------------------------------------------------- page state

    private inner class PageState(val document: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = MARGIN
        var pageNumber = 0

        fun newPage() {
            finishPage()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info)
            canvas = page?.canvas
            y = MARGIN
        }

        fun finishPage() {
            page?.let { current ->
                drawFooter(current.canvas, pageNumber)
                document.finishPage(current)
            }
            page = null
            canvas = null
        }

        /** Starts a new page when [needed] points would overflow the current one. */
        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN - 24f) newPage()
        }
    }

    // ---------------------------------------------------------------- drawing

    private fun drawHeader(state: PageState, data: ExportManager.ReportData) {
        val canvas = state.canvas ?: return

        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 96f, paint(0f).apply { color = NAVY })

        canvas.drawText("বকেয়া", MARGIN, 42f, paint(24f, Color.WHITE, bold))
        canvas.drawText(
            context.getString(R.string.app_tagline),
            MARGIN,
            62f,
            paint(9f, 0xFF94A3B8.toInt(), regular),
        )
        canvas.drawText(
            CsvWriter.title(data),
            PAGE_WIDTH - MARGIN,
            42f,
            paint(14f, 0xFF6EE7B7.toInt(), medium, Paint.Align.RIGHT),
        )
        canvas.drawText(
            data.periodLabel,
            PAGE_WIDTH - MARGIN,
            62f,
            paint(10f, Color.WHITE, regular, Paint.Align.RIGHT),
        )
        canvas.drawText(
            "তৈরি: ${data.generatedAt}",
            PAGE_WIDTH - MARGIN,
            78f,
            paint(8f, 0xFF94A3B8.toInt(), regular, Paint.Align.RIGHT),
        )

        state.y = 96f + 26f
    }

    private fun drawSummaryCards(state: PageState, data: ExportManager.ReportData) {
        val canvas = state.canvas ?: return
        state.ensureSpace(76f)

        val cards = listOf(
            Triple("মোট বাকি", data.totalDue, ROSE),
            Triple("মোট পরিশোধ", data.totalPaid, EMERALD),
            Triple("হিসাব সংখ্যা", null, NAVY),
        )
        val gap = 10f
        val cardWidth = (CONTENT_WIDTH - gap * (cards.size - 1)) / cards.size

        cards.forEachIndexed { index, (label, value, color) ->
            val left = MARGIN + index * (cardWidth + gap)
            val rect = RectF(left, state.y, left + cardWidth, state.y + 58f)

            canvas.drawRoundRect(rect, 8f, 8f, paint(0f).apply { this.color = 0xFFF8FAFC.toInt() })
            canvas.drawRoundRect(
                rect, 8f, 8f,
                paint(0f).apply {
                    this.color = SLATE_LIGHT
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                },
            )
            canvas.drawText(label, left + 10f, state.y + 20f, paint(9f, SLATE, regular))
            val text = value?.let { MoneyFormatter.format(it, data.useBengaliDigits) }
                ?: MoneyFormatter.formatNumber(data.accounts.size, data.useBengaliDigits)
            canvas.drawText(text, left + 10f, state.y + 42f, paint(15f, color, bold))
        }
        state.y += 58f + 22f
    }

    private fun drawAccountsTable(state: PageState, data: ExportManager.ReportData) {
        sectionTitle(state, "হিসাবসমূহ")

        // name | total | paid | remaining
        val columns = floatArrayOf(0.42f, 0.19f, 0.19f, 0.20f)
        drawTableHeader(state, listOf("নাম", "মোট", "পরিশোধ", "বাকি"), columns)

        data.accounts.forEach { account ->
            state.ensureSpace(30f)
            val canvas = state.canvas ?: return
            val rowTop = state.y

            canvas.drawText(
                ellipsize(account.name, 30),
                MARGIN + 4f,
                rowTop + 13f,
                paint(9.5f, INK, medium),
            )
            val subtitle = listOfNotNull(
                CsvWriter.typeLabel(account.type),
                account.secondaryName,
            ).joinToString(" · ")
            canvas.drawText(ellipsize(subtitle, 34), MARGIN + 4f, rowTop + 25f, paint(7.5f, SLATE, regular))

            drawCell(state, MoneyFormatter.format(account.total, data.useBengaliDigits), columns, 1, rowTop + 18f, INK)
            drawCell(state, MoneyFormatter.format(account.paid, data.useBengaliDigits), columns, 2, rowTop + 18f, EMERALD)
            drawCell(
                state,
                MoneyFormatter.format(account.remaining, data.useBengaliDigits),
                columns, 3, rowTop + 18f,
                if (account.remaining.isZero) EMERALD else ROSE,
                bold,
            )

            state.y += 32f
            divider(state)
        }

        state.ensureSpace(30f)
        totalsRow(
            state,
            "মোট",
            MoneyFormatter.format(data.totalDue, data.useBengaliDigits),
        )
    }

    private fun drawMoneySummary(state: PageState, data: ExportManager.ReportData) {
        state.ensureSpace(90f)
        sectionTitle(state, "আয়-ব্যয়ের সারসংক্ষেপ")

        val canvas = state.canvas ?: return
        val net = data.incomeTotal - data.expenseTotal
        val cards = listOf(
            Triple("মোট আয়", data.incomeTotal, EMERALD),
            Triple("মোট খরচ", data.expenseTotal, ROSE),
            Triple("অবশিষ্ট", net, if (net.isNegative) ROSE else NAVY),
        )
        val gap = 10f
        val cardWidth = (CONTENT_WIDTH - gap * (cards.size - 1)) / cards.size

        cards.forEachIndexed { index, (label, value, color) ->
            val left = MARGIN + index * (cardWidth + gap)
            val rect = RectF(left, state.y, left + cardWidth, state.y + 54f)
            canvas.drawRoundRect(rect, 8f, 8f, paint(0f).apply { this.color = 0xFFF8FAFC.toInt() })
            canvas.drawRoundRect(
                rect, 8f, 8f,
                paint(0f).apply {
                    this.color = SLATE_LIGHT
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                },
            )
            canvas.drawText(label, left + 10f, state.y + 19f, paint(9f, SLATE, regular))
            canvas.drawText(
                MoneyFormatter.format(value, data.useBengaliDigits),
                left + 10f, state.y + 40f,
                paint(14f, color, bold),
            )
        }
        state.y += 54f + 20f
    }

    private fun drawCategoryBreakdown(state: PageState, data: ExportManager.ReportData) {
        if (data.categoryBreakdown.isEmpty()) return
        state.ensureSpace(60f)
        sectionTitle(state, "খরচের ধরন")

        data.categoryBreakdown.take(8).forEach { category ->
            state.ensureSpace(24f)
            val canvas = state.canvas ?: return
            val top = state.y

            canvas.drawText(ellipsize(category.name, 24), MARGIN + 4f, top + 11f, paint(9f, INK, regular))
            canvas.drawText(
                MoneyFormatter.format(category.amount, data.useBengaliDigits),
                PAGE_WIDTH - MARGIN - 4f, top + 11f,
                paint(9f, INK, medium, Paint.Align.RIGHT),
            )

            // Share bar
            val barTop = top + 15f
            val barWidth = CONTENT_WIDTH - 8f
            canvas.drawRoundRect(
                RectF(MARGIN + 4f, barTop, MARGIN + 4f + barWidth, barTop + 5f),
                2.5f, 2.5f,
                paint(0f).apply { color = SLATE_LIGHT },
            )
            val fill = barWidth * (category.sharePercent.coerceIn(0, 100) / 100f)
            if (fill > 0f) {
                canvas.drawRoundRect(
                    RectF(MARGIN + 4f, barTop, MARGIN + 4f + fill, barTop + 5f),
                    2.5f, 2.5f,
                    paint(0f).apply { color = category.color ?: EMERALD },
                )
            }
            state.y += 26f
        }
        state.y += 10f
    }

    private fun drawEntriesTable(state: PageState, data: ExportManager.ReportData) {
        sectionTitle(state, "আয়-ব্যয়ের তালিকা")
        val columns = floatArrayOf(0.46f, 0.26f, 0.28f)
        drawTableHeader(state, listOf("বিবরণ", "তারিখ", "টাকা"), columns)

        data.entries.take(MAX_ENTRY_ROWS).forEach { entry ->
            state.ensureSpace(28f)
            val canvas = state.canvas ?: return
            val top = state.y

            canvas.drawText(ellipsize(entry.title, 28), MARGIN + 4f, top + 12f, paint(9.5f, INK, medium))
            entry.categoryName?.let {
                canvas.drawText(ellipsize(it, 24), MARGIN + 4f, top + 23f, paint(7.5f, SLATE, regular))
            }
            drawCell(
                state,
                com.shohan.bokeya.core.datetime.BanglaDate.formatShort(entry.date, data.useBengaliDigits),
                columns, 1, top + 16f, SLATE,
            )
            drawCell(
                state,
                (if (entry.isIncome) "+ " else "− ") +
                    MoneyFormatter.format(entry.amount, data.useBengaliDigits, withSymbol = false),
                columns, 2, top + 16f,
                if (entry.isIncome) EMERALD else ROSE,
                bold,
            )
            state.y += 30f
            divider(state)
        }

        if (data.entries.size > MAX_ENTRY_ROWS) {
            state.ensureSpace(20f)
            state.canvas?.drawText(
                "আরও ${MoneyFormatter.formatNumber(data.entries.size - MAX_ENTRY_ROWS, data.useBengaliDigits)} টি হিসাব রয়েছে",
                MARGIN + 4f, state.y + 12f,
                paint(8.5f, SLATE, regular),
            )
            state.y += 20f
        }
    }

    // ----------------------------------------------------------- table pieces

    private fun sectionTitle(state: PageState, title: String) {
        state.ensureSpace(34f)
        val canvas = state.canvas ?: return
        canvas.drawText(title, MARGIN, state.y + 12f, paint(12f, NAVY, bold))
        state.y += 22f
    }

    private fun drawTableHeader(state: PageState, labels: List<String>, columns: FloatArray) {
        state.ensureSpace(24f)
        val canvas = state.canvas ?: return
        canvas.drawRect(
            MARGIN, state.y, MARGIN + CONTENT_WIDTH, state.y + 18f,
            paint(0f).apply { color = 0xFFF1F5F9.toInt() },
        )
        labels.forEachIndexed { index, label ->
            if (index == 0) {
                canvas.drawText(label, MARGIN + 4f, state.y + 12.5f, paint(8f, SLATE, medium))
            } else {
                drawCell(state, label, columns, index, state.y + 12.5f, SLATE, medium, 8f)
            }
        }
        state.y += 22f
    }

    /** Right-aligns text at the end of column [index]. */
    private fun drawCell(
        state: PageState,
        text: String,
        columns: FloatArray,
        index: Int,
        baseline: Float,
        color: Int,
        face: Typeface = regular,
        size: Float = 9.5f,
    ) {
        val canvas = state.canvas ?: return
        var right = MARGIN
        for (i in 0..index) right += CONTENT_WIDTH * columns[i]
        canvas.drawText(text, right - 4f, baseline, paint(size, color, face, Paint.Align.RIGHT))
    }

    private fun divider(state: PageState) {
        state.canvas?.drawLine(
            MARGIN, state.y - 4f, MARGIN + CONTENT_WIDTH, state.y - 4f,
            paint(0f).apply {
                color = SLATE_LIGHT
                strokeWidth = 0.5f
            },
        )
    }

    private fun totalsRow(state: PageState, label: String, value: String) {
        val canvas = state.canvas ?: return
        canvas.drawRect(
            MARGIN, state.y, MARGIN + CONTENT_WIDTH, state.y + 24f,
            paint(0f).apply { color = 0xFFECFDF5.toInt() },
        )
        canvas.drawText(label, MARGIN + 6f, state.y + 16f, paint(10f, NAVY, bold))
        canvas.drawText(
            value, PAGE_WIDTH - MARGIN - 6f, state.y + 16f,
            paint(11f, EMERALD, bold, Paint.Align.RIGHT),
        )
        state.y += 32f
    }

    /**
     * Footer is drawn as each page is finished — [PdfDocument] gives no way to
     * reopen a finished page, so a "total pages" count is not available here.
     */
    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val baseline = PAGE_HEIGHT - MARGIN + 14f
        canvas.drawLine(
            MARGIN, baseline - 18f, PAGE_WIDTH - MARGIN, baseline - 18f,
            paint(0f).apply {
                color = SLATE_LIGHT
                strokeWidth = 0.5f
            },
        )
        canvas.drawText(
            "বকেয়া — Bokeya · আপনার হিসাব, আপনার ফোনেই",
            MARGIN, baseline,
            paint(7.5f, SLATE, regular),
        )
        canvas.drawText(
            MoneyFormatter.toBengaliDigits(pageNumber.toString()),
            PAGE_WIDTH - MARGIN, baseline,
            paint(7.5f, SLATE, medium, Paint.Align.RIGHT),
        )
    }

    private fun ellipsize(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"
}

private const val MAX_ENTRY_ROWS = 200
