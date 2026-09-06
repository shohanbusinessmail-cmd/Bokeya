package com.shohan.bokeya.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shohan.bokeya.NotificationRequest
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.ui.navigation.BokeyaNavHost
import com.shohan.bokeya.ui.navigation.BottomBar
import com.shohan.bokeya.ui.navigation.QuickActionsFab
import com.shohan.bokeya.ui.navigation.Routes
import com.shohan.bokeya.ui.navigation.TopLevelDestination
import com.shohan.bokeya.ui.navigation.navigateToTab
import com.shohan.bokeya.ui.screens.lock.LockScreen
import com.shohan.bokeya.ui.viewmodel.AppViewModel

/**
 * Root composable: decides between the lock screen and the app, and owns the
 * scaffold (bottom bar, FAB, snackbar host) shared by the five tabs.
 */
@Composable
fun BokeyaAppRoot(
    container: AppContainer,
    appViewModel: AppViewModel,
    notificationRequest: NotificationRequest?,
    onNotificationRequestHandled: () -> Unit,
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val isUnlocked by appViewModel.isUnlocked.collectAsStateWithLifecycle()
    val ready by appViewModel.startDestinationReady.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (!ready) return@Surface

        if (!isUnlocked) {
            LockScreen(
                biometricEnabled = settings.biometricEnabled,
                onPinEntered = appViewModel::verifyPin,
                onUnlocked = appViewModel::unlock,
            )
            return@Surface
        }

        MainScaffold(
            container = container,
            appViewModel = appViewModel,
            notificationRequest = notificationRequest,
            onNotificationRequestHandled = onNotificationRequestHandled,
        )
    }
}

@Composable
private fun MainScaffold(
    container: AppContainer,
    appViewModel: AppViewModel,
    notificationRequest: NotificationRequest?,
    onNotificationRequestHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by appViewModel.settings.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopLevelDestination.fromRoute(currentRoute)
    val showBars = topLevel != null

    // A notification tap jumps straight to the account it is about.
    LaunchedEffect(notificationRequest) {
        val request = notificationRequest ?: return@LaunchedEffect
        navController.navigate(Routes.accountDetail(request.accountId))
        if (request.openPayment) navController.navigate(Routes.payment(request.accountId))
        onNotificationRequestHandled()
    }

    val startDestination = when {
        !settings.onboardingComplete -> Routes.ONBOARDING
        !settings.setupComplete -> Routes.SETUP
        else -> Routes.HOME
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                BottomBar(
                    current = topLevel,
                    onSelect = { destination -> navController.navigateToTab(destination) },
                )
            }
        },
        floatingActionButton = {
            if (showBars) {
                QuickActionsFab(
                    onAddAccount = { type -> navController.navigate(Routes.addAccount(type)) },
                    onAddMoney = { isIncome -> navController.navigate(Routes.addMoney(isIncome)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Only the bottom inset is consumed here: screens draw their own
                // top bars edge-to-edge behind the status bar.
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            BokeyaNavHost(
                navController = navController,
                container = container,
                appViewModel = appViewModel,
                startDestination = startDestination,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}
