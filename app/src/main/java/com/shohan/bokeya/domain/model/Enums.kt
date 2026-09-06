package com.shohan.bokeya.domain.model

/**
 * The four kinds of money the user tracks. Every account row carries one of
 * these, which keeps a single table queryable while still letting each type
 * own the extra fields it needs.
 */
enum class AccountType {
    /** Running tab at a shop / bazar — many items, occasional payments. */
    SHOP,

    /** Bank or NGO loan repaid in scheduled installments. */
    LOAN,

    /** Product bought on installment (EMI), usually with a down payment. */
    EMI,

    /** Money borrowed from (or lent to) a person. */
    PERSON,
}

/** Direction of a personal debt: did the user take the money, or give it? */
enum class DebtDirection {
    /** The user owes someone else. */
    BORROWED,

    /** Someone else owes the user. */
    LENT,
}

enum class InstallmentFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM,
    ;

    fun daysOrNull(customDays: Int?): Int? = when (this) {
        DAILY -> 1
        WEEKLY -> 7
        MONTHLY -> null // handled with calendar months, not a fixed day count
        CUSTOM -> customDays
    }
}

/** Lifecycle of an account, derived from balances and dates — never stored stale. */
enum class AccountStatus {
    ACTIVE,
    DUE_TODAY,
    DUE_SOON,
    OVERDUE,
    PAID,
}

enum class PaymentMethod {
    CASH,
    BANK,
    MOBILE_BANKING,
    OTHER,
}

/** Unified ledger classification used by history, search, reports and the calendar. */
enum class TransactionType {
    DEBT,
    PAYMENT,
    LOAN,
    LOAN_PAYMENT,
    EMI,
    EMI_PAYMENT,
    INCOME,
    EXPENSE,
    ;

    val isPayment: Boolean
        get() = this == PAYMENT || this == LOAN_PAYMENT || this == EMI_PAYMENT

    val isMoneyIn: Boolean
        get() = this == INCOME

    val isMoneyOut: Boolean
        get() = this == EXPENSE || isPayment
}

enum class CategoryKind {
    INCOME,
    EXPENSE,
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/** Filter chips on the history screen. */
enum class HistoryFilter {
    ALL,
    DEBT,
    LOAN,
    EMI,
    PERSONAL,
    INCOME,
    EXPENSE,
    PAID,
    UNPAID,
    OVERDUE,
}

enum class HistorySort {
    NEWEST,
    OLDEST,
    AMOUNT_HIGH,
    AMOUNT_LOW,
}

enum class ItemUnit {
    KG,
    GRAM,
    LITRE,
    PIECE,
    DOZEN,
    PACKET,
    BAG,
    OTHER,
}
