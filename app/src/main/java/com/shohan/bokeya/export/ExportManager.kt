package com.shohan.bokeya.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.data.local.BokeyaDatabase
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.CategorySpend
import com.shohan.bokeya.domain.model.MoneyEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The reports a user can generate. */
enum class ReportType {
    MONTHLY,
    DEBT,
    LOAN,
    INCOME_EXPENSE,
    FULL,
}

enum class ExportFormat { CSV, PDF }

/**
 * Builds CSV and PDF reports.
 *
 * Files are written to `cacheDir/exports` and shared through [FileProvider], so
 * nothing is written to shared storage and no storage permission is required.
 */
class ExportManager(
    private val context: Context,
    private val database: BokeyaDatabase,
    private val accountRepository: AccountRepository,
    private val moneyRepository: MoneyRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    data class ReportData(
        val type: ReportType,
        val periodLabel: String,
        val generatedAt: String,
        val accounts: List<Account>,
        val entries: List<MoneyEntry>,
        val incomeTotal: Money,
        val expenseTotal: Money,
        val totalDue: Money,
        val totalPaid: Money,
        val categoryBreakdown: List<CategorySpend>,
        val useBengaliDigits: Boolean,
    )

    suspend fun buildReportData(
        type: ReportType,
        month: LocalDate,
        useBengaliDigits: Boolean,
    ): ReportData = withContext(io) {
        val monthStart = BanglaDate.startOfMonth(month)
        val monthEnd = BanglaDate.endOfMonth(month)

        // The debt/loan reports describe current balances, so they are not
        // restricted to the selected month.
        val wholeHistory = type == ReportType.DEBT || type == ReportType.LOAN || type == ReportType.FULL
        val from = if (wholeHistory) LocalDate.of(2000, 1, 1) else monthStart
        val to = if (wholeHistory) LocalDate.of(2100, 1, 1) else monthEnd

        val accounts = accountRepository.observeAccounts().first().let { list ->
            when (type) {
                ReportType.DEBT -> list.filter { it.type == AccountType.SHOP || it.type == AccountType.PERSON }
                ReportType.LOAN -> list.filter { it.type == AccountType.LOAN || it.type == AccountType.EMI }
                ReportType.INCOME_EXPENSE -> emptyList()
                else -> list
            }
        }

        val entries = if (type == ReportType.DEBT || type == ReportType.LOAN) {
            emptyList()
        } else {
            moneyRepository.observeAllBetween(from, to).first()
        }

        ReportData(
            type = type,
            periodLabel = if (wholeHistory) {
                "সব সময়"
            } else {
                BanglaDate.formatMonthYear(month, useBengaliDigits)
            },
            generatedAt = BanglaDate.formatFull(BanglaDate.today(), useBengaliDigits),
            accounts = accounts,
            entries = entries,
            incomeTotal = Money(entries.filter { it.isIncome }.sumOf { it.amount.minor }),
            expenseTotal = Money(entries.filterNot { it.isIncome }.sumOf { it.amount.minor }),
            totalDue = Money(accounts.filter { !it.isClosed }.sumOf { it.remaining.minor }),
            totalPaid = Money(accounts.sumOf { it.paid.minor }),
            categoryBreakdown = if (type == ReportType.DEBT || type == ReportType.LOAN) {
                emptyList()
            } else {
                moneyRepository.getCategoryBreakdown(false, from, to)
            },
            useBengaliDigits = useBengaliDigits,
        )
    }

    suspend fun export(data: ReportData, format: ExportFormat): Result<Uri> = withContext(io) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            // Keep the cache tidy: drop anything older than a day.
            dir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > DAY_MILLIS) file.delete()
            }

            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            val name = "bokeya-${data.type.name.lowercase()}-$stamp"
            val file = when (format) {
                ExportFormat.CSV -> File(dir, "$name.csv").also { CsvWriter.write(it, data) }
                ExportFormat.PDF -> File(dir, "$name.pdf").also { PdfReportWriter(context).write(it, data) }
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    fun mimeType(format: ExportFormat): String = when (format) {
        ExportFormat.CSV -> "text/csv"
        ExportFormat.PDF -> "application/pdf"
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

/**
 * CSV writer.
 *
 * The file opens in Excel with correct Bangla text because of the UTF-8 BOM —
 * without it Excel assumes the system codepage and shows mojibake.
 */
internal object CsvWriter {

    fun write(file: File, data: ExportManager.ReportData) {
        val digits = data.useBengaliDigits
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF") // UTF-8 BOM

            writer.appendLine(row("বকেয়া — ${title(data)}"))
            writer.appendLine(row("সময়কাল", data.periodLabel))
            writer.appendLine(row("তৈরি", data.generatedAt))
            writer.appendLine()

            if (data.accounts.isNotEmpty()) {
                writer.appendLine(row("হিসাবসমূহ"))
                writer.appendLine(
                    row("ধরন", "নাম", "প্রতিষ্ঠান/সম্পর্ক", "মোট", "পরিশোধ", "বাকি", "অবস্থা", "তারিখ"),
                )
                data.accounts.forEach { account ->
                    writer.appendLine(
                        row(
                            typeLabel(account.type),
                            account.name,
                            account.secondaryName.orEmpty(),
                            MoneyFormatter.formatPlain(account.total),
                            MoneyFormatter.formatPlain(account.paid),
                            MoneyFormatter.formatPlain(account.remaining),
                            statusLabel(account),
                            account.nextDueDate?.let { BanglaDate.formatFull(it, digits) }.orEmpty(),
                        ),
                    )
                }
                writer.appendLine()
                writer.appendLine(row("মোট বাকি", MoneyFormatter.formatPlain(data.totalDue)))
                writer.appendLine(row("মোট পরিশোধ", MoneyFormatter.formatPlain(data.totalPaid)))
                writer.appendLine()
            }

            if (data.entries.isNotEmpty()) {
                writer.appendLine(row("আয়-ব্যয়"))
                writer.appendLine(row("ধরন", "বিবরণ", "শ্রেণি", "টাকা", "তারিখ", "নোট"))
                data.entries.forEach { entry ->
                    writer.appendLine(
                        row(
                            if (entry.isIncome) "আয়" else "খরচ",
                            entry.title,
                            entry.categoryName.orEmpty(),
                            MoneyFormatter.formatPlain(entry.amount),
                            BanglaDate.formatFull(entry.date, digits),
                            entry.note.orEmpty(),
                        ),
                    )
                }
                writer.appendLine()
                writer.appendLine(row("মোট আয়", MoneyFormatter.formatPlain(data.incomeTotal)))
                writer.appendLine(row("মোট খরচ", MoneyFormatter.formatPlain(data.expenseTotal)))
                writer.appendLine(
                    row("অবশিষ্ট", MoneyFormatter.formatPlain(data.incomeTotal - data.expenseTotal)),
                )
            }
        }
    }

    private fun row(vararg cells: String): String = cells.joinToString(",") { escape(it) }

    /** RFC 4180 escaping: wrap in quotes and double any embedded quote. */
    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    fun title(data: ExportManager.ReportData): String = when (data.type) {
        ReportType.MONTHLY -> "মাসিক রিপোর্ট"
        ReportType.DEBT -> "বকেয়া রিপোর্ট"
        ReportType.LOAN -> "Loan ও EMI রিপোর্ট"
        ReportType.INCOME_EXPENSE -> "আয়-ব্যয় রিপোর্ট"
        ReportType.FULL -> "সম্পূর্ণ রিপোর্ট"
    }

    fun typeLabel(type: AccountType): String = when (type) {
        AccountType.SHOP -> "দোকান"
        AccountType.LOAN -> "Loan"
        AccountType.EMI -> "EMI"
        AccountType.PERSON -> "ধার"
    }

    fun statusLabel(account: Account): String = when {
        account.isPaid -> "সম্পূর্ণ পরিশোধ"
        account.isOverdue -> "সময় পেরিয়ে গেছে"
        else -> "চলমান"
    }
}
