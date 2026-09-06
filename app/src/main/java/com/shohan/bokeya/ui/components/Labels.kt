package com.shohan.bokeya.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.shohan.bokeya.R
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.CategoryKind
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.HistoryFilter
import com.shohan.bokeya.domain.model.HistorySort
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.domain.model.PaymentMethod
import com.shohan.bokeya.domain.model.ThemeMode
import com.shohan.bokeya.domain.model.TransactionType
import com.shohan.bokeya.export.ReportType
import com.shohan.bokeya.ui.theme.BokeyaTheme

/**
 * Single source of truth for how domain enums are named and coloured in the UI.
 *
 * Keeping these here (rather than a `when` inside each screen) means a label or
 * accent colour is changed in exactly one place, and no screen can drift.
 */

@Composable
@ReadOnlyComposable
fun AccountType.label(): String = stringResource(
    when (this) {
        AccountType.SHOP -> R.string.type_shop
        AccountType.LOAN -> R.string.type_loan
        AccountType.EMI -> R.string.type_emi
        AccountType.PERSON -> R.string.type_person
    },
)

@Composable
@ReadOnlyComposable
fun AccountType.shortLabel(): String = stringResource(
    when (this) {
        AccountType.SHOP -> R.string.type_shop_short
        AccountType.LOAN -> R.string.type_loan_short
        AccountType.EMI -> R.string.type_emi_short
        AccountType.PERSON -> R.string.type_person_short
    },
)

val AccountType.icon: ImageVector
    get() = when (this) {
        AccountType.SHOP -> Icons.Outlined.Storefront
        AccountType.LOAN -> Icons.Outlined.AccountBalance
        AccountType.EMI -> Icons.Outlined.CreditCard
        AccountType.PERSON -> Icons.Outlined.Handshake
    }

@Composable
fun AccountType.accent(): Color = when (this) {
    AccountType.SHOP -> BokeyaTheme.colors.shop
    AccountType.LOAN -> BokeyaTheme.colors.loan
    AccountType.EMI -> BokeyaTheme.colors.emi
    AccountType.PERSON -> BokeyaTheme.colors.person
}

@Composable
@ReadOnlyComposable
fun PaymentMethod.label(): String = stringResource(
    when (this) {
        PaymentMethod.CASH -> R.string.method_cash
        PaymentMethod.BANK -> R.string.method_bank
        PaymentMethod.MOBILE_BANKING -> R.string.method_mobile
        PaymentMethod.OTHER -> R.string.method_other
    },
)

@Composable
@ReadOnlyComposable
fun InstallmentFrequency.label(): String = stringResource(
    when (this) {
        InstallmentFrequency.DAILY -> R.string.freq_daily
        InstallmentFrequency.WEEKLY -> R.string.freq_weekly
        InstallmentFrequency.MONTHLY -> R.string.freq_monthly
        InstallmentFrequency.CUSTOM -> R.string.freq_custom
    },
)

@Composable
@ReadOnlyComposable
fun ItemUnit.label(): String = stringResource(
    when (this) {
        ItemUnit.KG -> R.string.unit_kg
        ItemUnit.GRAM -> R.string.unit_gram
        ItemUnit.LITRE -> R.string.unit_litre
        ItemUnit.PIECE -> R.string.unit_piece
        ItemUnit.DOZEN -> R.string.unit_dozen
        ItemUnit.PACKET -> R.string.unit_packet
        ItemUnit.BAG -> R.string.unit_bag
        ItemUnit.OTHER -> R.string.unit_other
    },
)

@Composable
@ReadOnlyComposable
fun DebtDirection.label(): String = stringResource(
    when (this) {
        DebtDirection.BORROWED -> R.string.direction_borrowed
        DebtDirection.LENT -> R.string.direction_lent
    },
)

@Composable
@ReadOnlyComposable
fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    },
)

@Composable
@ReadOnlyComposable
fun HistoryFilter.label(): String = stringResource(
    when (this) {
        HistoryFilter.ALL -> R.string.label_all
        HistoryFilter.DEBT -> R.string.filter_debt
        HistoryFilter.LOAN -> R.string.type_loan_short
        HistoryFilter.EMI -> R.string.type_emi_short
        HistoryFilter.PERSONAL -> R.string.type_person_short
        HistoryFilter.INCOME -> R.string.money_income
        HistoryFilter.EXPENSE -> R.string.money_expense
        HistoryFilter.PAID -> R.string.status_paid
        HistoryFilter.UNPAID -> R.string.filter_unpaid
        HistoryFilter.OVERDUE -> R.string.status_overdue
    },
)

@Composable
@ReadOnlyComposable
fun HistorySort.label(): String = stringResource(
    when (this) {
        HistorySort.NEWEST -> R.string.sort_newest
        HistorySort.OLDEST -> R.string.sort_oldest
        HistorySort.AMOUNT_HIGH -> R.string.sort_amount_high
        HistorySort.AMOUNT_LOW -> R.string.sort_amount_low
    },
)

@Composable
@ReadOnlyComposable
fun ReportType.label(): String = stringResource(
    when (this) {
        ReportType.MONTHLY -> R.string.report_monthly
        ReportType.DEBT -> R.string.report_debt
        ReportType.LOAN -> R.string.report_loan
        ReportType.INCOME_EXPENSE -> R.string.report_income_expense
        ReportType.FULL -> R.string.report_full
    },
)

@Composable
@ReadOnlyComposable
fun TransactionType.label(): String = stringResource(
    when (this) {
        TransactionType.DEBT -> R.string.filter_debt
        TransactionType.PAYMENT -> R.string.filter_payment
        TransactionType.LOAN -> R.string.type_loan_short
        TransactionType.LOAN_PAYMENT -> R.string.filter_payment
        TransactionType.EMI -> R.string.type_emi_short
        TransactionType.EMI_PAYMENT -> R.string.filter_payment
        TransactionType.INCOME -> R.string.money_income
        TransactionType.EXPENSE -> R.string.money_expense
    },
)

@Composable
@ReadOnlyComposable
fun CategoryKind.label(): String = stringResource(
    when (this) {
        CategoryKind.INCOME -> R.string.money_income
        CategoryKind.EXPENSE -> R.string.money_expense
    },
)

/** Colour used for a ledger row's amount: green in, red out, neutral otherwise. */
@Composable
fun TransactionType.amountColor(): Color = when {
    isMoneyIn -> BokeyaTheme.colors.income
    isPayment -> BokeyaTheme.colors.success
    isMoneyOut -> BokeyaTheme.colors.expense
    else -> BokeyaTheme.colors.warning
}
