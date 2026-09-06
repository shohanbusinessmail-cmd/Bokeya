package com.shohan.bokeya.sample

import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.repository.AccountRepository
import com.shohan.bokeya.data.repository.MoneyRepository
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.domain.model.PaymentMethod

/**
 * Fills the database with a realistic-looking Bangladeshi household ledger.
 *
 * Debug builds only — [com.shohan.bokeya.ui.viewmodel.SettingsViewModel] refuses
 * to call this in release. It exists so screenshots, charts and the reminder
 * pipeline can be exercised without half an hour of manual data entry, never as
 * production content.
 */
class SampleDataSeeder(
    private val accountRepository: AccountRepository,
    private val moneyRepository: MoneyRepository,
) {

    suspend fun seed() {
        val today = BanglaDate.today()

        // ---------------------------------------------------------- shop dues
        val grocery = accountRepository.createShopAccount(
            name = "করিম স্টোর",
            items = listOf(
                AccountRepository.ItemInput(
                    name = "চাল",
                    quantity = 10.0,
                    unit = ItemUnit.KG,
                    unitPrice = Money.ofTaka(72),
                    total = Money.ofTaka(720),
                ),
                AccountRepository.ItemInput(
                    name = "সয়াবিন তেল",
                    quantity = 2.0,
                    unit = ItemUnit.LITRE,
                    unitPrice = Money.ofTaka(175),
                    total = Money.ofTaka(350),
                ),
                AccountRepository.ItemInput(
                    name = "ডাল",
                    quantity = 3.0,
                    unit = ItemUnit.KG,
                    unitPrice = Money.ofTaka(140),
                    total = Money.ofTaka(420),
                ),
            ),
            purchaseDate = today.minusDays(12),
            dueDate = today.plusDays(3),
            note = "মাসের বাজার",
        )
        accountRepository.recordPayment(
            accountId = grocery,
            amount = Money.ofTaka(500),
            date = today.minusDays(4),
            method = PaymentMethod.CASH,
            note = "আংশিক পরিশোধ",
        )

        accountRepository.createShopAccount(
            name = "রহিম ফার্মেসি",
            items = listOf(
                AccountRepository.ItemInput(
                    name = "ওষুধ",
                    quantity = null,
                    unit = null,
                    unitPrice = null,
                    total = Money.ofTaka(1_250),
                ),
            ),
            purchaseDate = today.minusDays(20),
            dueDate = today.minusDays(2),
            note = "আম্মার ওষুধ",
        )

        // ---------------------------------------------------------------- loan
        val loan = accountRepository.createLoanAccount(
            loanName = "ব্যবসার লোন",
            organisation = "ব্র্যাক ব্যাংক",
            principal = Money.ofTaka(100_000),
            payable = Money.ofTaka(112_000),
            hasInterest = true,
            interestRatePercent = 12.0,
            installmentAmount = Money.ofTaka(9_334),
            frequency = InstallmentFrequency.MONTHLY,
            customIntervalDays = null,
            startDate = today.minusMonths(3),
            firstDueDate = today.minusMonths(2).withDayOfMonth(10),
            note = "১২ মাসে পরিশোধ",
        )
        repeat(2) { index ->
            accountRepository.recordPayment(
                accountId = loan,
                amount = Money.ofTaka(9_334),
                date = today.minusMonths((2 - index).toLong()).withDayOfMonth(10),
                method = PaymentMethod.BANK,
            )
        }

        // ----------------------------------------------------------------- EMI
        val emi = accountRepository.createEmiAccount(
            productName = "স্যামসাং রেফ্রিজারেটর",
            seller = "ট্রান্সকম ডিজিটাল",
            cashPrice = Money.ofTaka(62_000),
            downPayment = Money.ofTaka(12_000),
            tenureMonths = 10,
            installmentAmount = null,
            startDate = today.minusMonths(2),
            firstDueDate = today.minusMonths(1).withDayOfMonth(5),
            note = "০% ইন্টারেস্ট অফার",
        )
        accountRepository.recordPayment(
            accountId = emi,
            amount = Money.ofTaka(5_000),
            date = today.minusMonths(1).withDayOfMonth(5),
            method = PaymentMethod.MOBILE_BANKING,
        )

        // ------------------------------------------------------------- person
        accountRepository.createPersonAccount(
            personName = "সাকিব",
            relationship = "বন্ধু",
            phone = "01712345678",
            amount = Money.ofTaka(5_000),
            direction = DebtDirection.BORROWED,
            borrowDate = today.minusDays(25),
            returnDate = today.plusDays(5),
            note = "জরুরি দরকারে নেওয়া",
        )
        accountRepository.createPersonAccount(
            personName = "মামুন ভাই",
            relationship = "কলিগ",
            phone = null,
            amount = Money.ofTaka(3_000),
            direction = DebtDirection.LENT,
            borrowDate = today.minusDays(10),
            returnDate = today.plusDays(20),
            alreadyPaid = Money.ofTaka(1_000),
            note = "ধার দিয়েছি",
        )

        // ------------------------------------------------------ income/expense
        moneyRepository.ensureDefaultCategories()
        val expenseCategories = moneyRepository.getCategories(CategoryKind.EXPENSE)
        val incomeCategories = moneyRepository.getCategories(CategoryKind.INCOME)

        fun expenseCategory(key: String) =
            expenseCategories.firstOrNull { it.builtInKey == key }?.id
                ?: expenseCategories.firstOrNull()?.id

        val salaryId = incomeCategories.firstOrNull { it.builtInKey == "income_salary" }?.id
            ?: incomeCategories.firstOrNull()?.id

        // Two months of activity so the trend chart and month-over-month
        // insight have something honest to compare.
        for (monthsAgo in 1 downTo 0) {
            val monthAnchor = today.minusMonths(monthsAgo.toLong())
            val payDay = minOf(1, monthAnchor.lengthOfMonth())
            moneyRepository.addEntry(
                isIncome = true,
                title = "মাসিক বেতন",
                amount = Money.ofTaka(45_000),
                categoryId = salaryId,
                date = monthAnchor.withDayOfMonth(payDay),
            )
            SAMPLE_EXPENSES.forEachIndexed { index, sample ->
                val day = minOf(sample.day, monthAnchor.lengthOfMonth())
                moneyRepository.addEntry(
                    isIncome = false,
                    title = sample.title,
                    amount = Money.ofTaka(sample.taka + monthsAgo * 100L * (index + 1)),
                    categoryId = expenseCategory(sample.categoryKey),
                    date = monthAnchor.withDayOfMonth(day),
                    note = sample.note,
                )
            }
        }
    }

    private data class SampleExpense(
        val title: String,
        val taka: Long,
        val categoryKey: String,
        val day: Int,
        val note: String? = null,
    )

    private companion object {
        val SAMPLE_EXPENSES = listOf(
            SampleExpense("বাসা ভাড়া", 12_000, "expense_home", 2),
            SampleExpense("কাঁচা বাজার", 3_400, "expense_bazar", 4, "সাপ্তাহিক বাজার"),
            SampleExpense("বিদ্যুৎ বিল", 1_850, "expense_bill", 8),
            SampleExpense("বাচ্চার স্কুল ফি", 2_500, "expense_education", 10),
            SampleExpense("রিকশা ও বাস", 900, "expense_transport", 12),
            SampleExpense("হোটেলে খাওয়া", 750, "expense_food", 15),
            SampleExpense("ডাক্তার দেখানো", 1_200, "expense_health", 18),
            SampleExpense("জামা কেনা", 2_200, "expense_shopping", 22),
            SampleExpense("সিনেমা", 600, "expense_entertainment", 25),
        )
    }
}
