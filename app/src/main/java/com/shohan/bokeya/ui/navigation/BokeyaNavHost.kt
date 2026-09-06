package com.shohan.bokeya.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.ui.screens.about.AboutScreen
import com.shohan.bokeya.ui.screens.accounts.AccountDetailScreen
import com.shohan.bokeya.ui.screens.accounts.AccountsScreen
import com.shohan.bokeya.ui.screens.accounts.AddAccountScreen
import com.shohan.bokeya.ui.screens.backup.BackupScreen
import com.shohan.bokeya.ui.screens.calendar.CalendarScreen
import com.shohan.bokeya.ui.screens.dashboard.DashboardScreen
import com.shohan.bokeya.ui.screens.history.HistoryScreen
import com.shohan.bokeya.ui.screens.insights.InsightsScreen
import com.shohan.bokeya.ui.screens.money.AddMoneyScreen
import com.shohan.bokeya.ui.screens.money.CategoriesScreen
import com.shohan.bokeya.ui.screens.money.MoneyScreen
import com.shohan.bokeya.ui.screens.more.MoreScreen
import com.shohan.bokeya.ui.screens.onboarding.OnboardingScreen
import com.shohan.bokeya.ui.screens.onboarding.SetupScreen
import com.shohan.bokeya.ui.screens.payment.PaymentScreen
import com.shohan.bokeya.ui.screens.reminders.RemindersScreen
import com.shohan.bokeya.ui.screens.reports.ReportsScreen
import com.shohan.bokeya.ui.screens.search.SearchScreen
import com.shohan.bokeya.ui.screens.settings.SecurityScreen
import com.shohan.bokeya.ui.screens.settings.SettingsScreen
import com.shohan.bokeya.ui.viewmodel.AppViewModel
import com.shohan.bokeya.ui.viewmodel.appViewModelFactory

private const val TRANSITION_MS = 260

/**
 * The whole navigation graph.
 *
 * Every ViewModel is created through one shared [appViewModelFactory], so a
 * screen's dependencies are resolved the same way everywhere and navigation
 * arguments arrive through `SavedStateHandle` rather than being threaded down
 * as composable parameters.
 */
@Composable
fun BokeyaNavHost(
    navController: NavHostController,
    container: AppContainer,
    appViewModel: AppViewModel,
    startDestination: String,
    snackbarHostState: SnackbarHostState,
) {
    val factory = appViewModelFactory(container)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { it / 12 } },
        exitTransition = { fadeOut(tween(TRANSITION_MS / 2)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = { fadeOut(tween(TRANSITION_MS / 2)) + slideOutHorizontally(tween(TRANSITION_MS)) { it / 12 } },
    ) {
        // ------------------------------------------------------- first launch
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    appViewModel.onOnboardingComplete()
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETUP) {
            SetupScreen(
                container = container,
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }

        // -------------------------------------------------------------- tabs
        composable(Routes.HOME) {
            DashboardScreen(
                viewModel = viewModel(factory = factory),
                onAccountClick = { navController.navigate(Routes.accountDetail(it)) },
                onSeeAllAccounts = { navController.navigateToTab(TopLevelDestination.ACCOUNTS) },
                onSeeAllHistory = { navController.navigate(Routes.HISTORY) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onInsightsClick = { navController.navigate(Routes.INSIGHTS) },
                onAddAccount = { navController.navigate(Routes.addAccount(it)) },
                onAddMoney = { navController.navigate(Routes.addMoney(it)) },
                onPayClick = { navController.navigate(Routes.payment(it)) },
            )
        }

        composable(Routes.ACCOUNTS) {
            AccountsScreen(
                viewModel = viewModel(factory = factory),
                onAccountClick = { navController.navigate(Routes.accountDetail(it)) },
                onAddAccount = { navController.navigate(Routes.addAccount(it)) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
            )
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(
                viewModel = viewModel(factory = factory),
                onAccountClick = { navController.navigate(Routes.accountDetail(it)) },
            )
        }

        composable(Routes.MONEY) {
            MoneyScreen(
                viewModel = viewModel(factory = factory),
                onAddEntry = { isIncome -> navController.navigate(Routes.addMoney(isIncome)) },
                onEditEntry = { isIncome, id -> navController.navigate(Routes.addMoney(isIncome, id)) },
                onManageCategories = { navController.navigate(Routes.CATEGORIES) },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(Routes.MORE) {
            MoreScreen(
                onNavigate = { navController.navigate(it) },
            )
        }

        // ------------------------------------------------------------ details
        composable(
            route = Routes.ACCOUNT_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_ACCOUNT_ID) { type = NavType.StringType }),
        ) {
            AccountDetailScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                onPayClick = { navController.navigate(Routes.payment(it)) },
                onEditClick = { type, id -> navController.navigate(Routes.addAccount(type, id)) },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(
            route = Routes.ADD_ACCOUNT,
            arguments = listOf(
                navArgument(Routes.ARG_TYPE) { type = NavType.StringType },
                navArgument(Routes.ARG_ACCOUNT_ID) {
                    type = NavType.StringType
                    defaultValue = "-1"
                },
            ),
        ) {
            AddAccountScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                onSaved = { accountId ->
                    navController.popBackStack()
                    navController.navigate(Routes.accountDetail(accountId))
                },
            )
        }

        composable(
            route = Routes.PAYMENT,
            arguments = listOf(navArgument(Routes.ARG_ACCOUNT_ID) { type = NavType.StringType }),
        ) {
            PaymentScreen(
                viewModel = viewModel(factory = factory),
                onDismiss = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ADD_MONEY,
            arguments = listOf(
                navArgument(Routes.ARG_IS_INCOME) { type = NavType.StringType },
                navArgument(Routes.ARG_ENTRY_ID) {
                    type = NavType.StringType
                    defaultValue = "-1"
                },
            ),
        ) {
            AddMoneyScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                onManageCategories = { navController.navigate(Routes.CATEGORIES) },
            )
        }

        // ------------------------------------------------------------- "আরও"
        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                onAccountClick = { navController.navigate(Routes.accountDetail(it)) },
                onEntryClick = { isIncome, id -> navController.navigate(Routes.addMoney(isIncome, id)) },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                onAccountClick = { navController.navigate(Routes.accountDetail(it)) },
                onEntryClick = { isIncome, id -> navController.navigate(Routes.addMoney(isIncome, id)) },
            )
        }

        composable(Routes.INSIGHTS) {
            InsightsScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.REMINDERS) {
            RemindersScreen(
                viewModel = viewModel(factory = factory),
                settingsViewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                onAccountClick = { navController.navigate(Routes.accountDetail(it)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                onNavigate = { navController.navigate(it) },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(Routes.SECURITY) {
            SecurityScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(Routes.BACKUP) {
            BackupScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(Routes.CATEGORIES) {
            CategoriesScreen(
                viewModel = viewModel(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
