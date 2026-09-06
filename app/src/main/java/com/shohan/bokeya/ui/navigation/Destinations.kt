package com.shohan.bokeya.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector
import com.shohan.bokeya.R
import com.shohan.bokeya.domain.model.AccountType

/**
 * Every navigable destination.
 *
 * Routes are plain strings with typed builders ([Routes.accountDetail] etc.)
 * so callers never hand-concatenate a path and every argument stays URL-safe.
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SETUP = "setup"
    const val LOCK = "lock"

    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val CALENDAR = "calendar"
    const val MONEY = "money"
    const val MORE = "more"

    const val ACCOUNT_DETAIL = "account/{accountId}"
    const val ADD_ACCOUNT = "add_account/{type}?accountId={accountId}"
    const val ADD_MONEY = "add_money/{isIncome}?entryId={entryId}"
    const val PAYMENT = "payment/{accountId}"
    const val HISTORY = "history"
    const val SEARCH = "search"
    const val INSIGHTS = "insights"
    const val REPORTS = "reports"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val SECURITY = "security"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val CATEGORIES = "categories"

    fun accountDetail(accountId: Long) = "account/$accountId"

    fun addAccount(type: AccountType, accountId: Long? = null) =
        "add_account/${type.name}?accountId=${accountId ?: -1L}"

    fun addMoney(isIncome: Boolean, entryId: Long? = null) =
        "add_money/$isIncome?entryId=${entryId ?: -1L}"

    fun payment(accountId: Long) = "payment/$accountId"

    const val ARG_ACCOUNT_ID = "accountId"
    const val ARG_TYPE = "type"
    const val ARG_IS_INCOME = "isIncome"
    const val ARG_ENTRY_ID = "entryId"
}

/** The five bottom-navigation tabs. */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    ACCOUNTS(
        Routes.ACCOUNTS,
        R.string.nav_accounts,
        Icons.Filled.AccountBalanceWallet,
        Icons.Outlined.AccountBalanceWallet,
    ),
    CALENDAR(
        Routes.CALENDAR,
        R.string.nav_calendar,
        Icons.Filled.CalendarMonth,
        Icons.Outlined.CalendarMonth,
    ),
    MONEY(Routes.MONEY, R.string.nav_money, Icons.Filled.SwapVert, Icons.Outlined.SwapVert),
    MORE(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}
