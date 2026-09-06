package com.shohan.bokeya

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.ui.BokeyaAppRoot
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.AppViewModel
import com.shohan.bokeya.ui.viewmodel.appViewModelFactory

/**
 * The app's single activity.
 *
 * Compose owns all navigation; the activity only handles platform concerns —
 * the splash screen handoff, edge-to-edge insets, deep links arriving from a
 * notification, and re-arming the app lock when the app leaves the foreground.
 *
 * It extends [FragmentActivity] (not `ComponentActivity`) purely because
 * `BiometricPrompt` requires a fragment host for the system dialog.
 */
class MainActivity : FragmentActivity() {

    private val container: AppContainer
        get() = (application as BokeyaApp).container

    private val appViewModel: AppViewModel by viewModels { appViewModelFactory(container) }

    /** Set from the launching intent; consumed once by the nav graph. */
    private var pendingIntentRequest by mutableStateOf<NotificationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the system splash until the first settings read resolves, so the
        // app never flashes the wrong theme or start destination.
        splash.setKeepOnScreenCondition { !appViewModel.startDestinationReady.value }

        pendingIntentRequest = intent.toNotificationRequest()

        // Re-lock on background rather than on destroy: the user expects the PIN
        // again after switching apps, not only after a cold start.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) appViewModel.lock()
            },
        )

        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()

            BokeyaTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.useDynamicColor,
            ) {
                BokeyaAppRoot(
                    container = container,
                    appViewModel = appViewModel,
                    notificationRequest = pendingIntentRequest,
                    onNotificationRequestHandled = { pendingIntentRequest = null },
                )
            }
        }
    }

    /** singleTask: a notification tap while the app is alive lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toNotificationRequest()?.let { pendingIntentRequest = it }
    }

    private fun Intent.toNotificationRequest(): NotificationRequest? {
        val accountId = getLongExtra(EXTRA_ACCOUNT_ID, -1L)
        if (accountId <= 0L) return null
        return NotificationRequest(
            accountId = accountId,
            openPayment = getBooleanExtra(EXTRA_OPEN_PAYMENT, false),
        )
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
        const val EXTRA_OPEN_PAYMENT = "extra_open_payment"
    }
}

/** A "open this account (and maybe its payment sheet)" request from a notification. */
data class NotificationRequest(
    val accountId: Long,
    val openPayment: Boolean,
)
