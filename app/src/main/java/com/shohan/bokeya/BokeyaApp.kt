package com.shohan.bokeya

import android.app.Application
import androidx.work.Configuration
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.notification.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BokeyaApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    /** Application-lifetime scope for fire-and-forget startup work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        NotificationChannels.ensureCreated(this)

        appScope.launch {
            // Seed built-in categories, then (re)schedule reminders. Both are
            // idempotent, so running them on every cold start is safe and keeps
            // the app correct after an update or a restore.
            runCatching { container.moneyRepository.ensureDefaultCategories() }
            runCatching {
                val settings = container.preferences.current()
                if (settings.notificationsEnabled) {
                    container.reminderScheduler.schedule(settings)
                } else {
                    container.reminderScheduler.cancel()
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()
}
