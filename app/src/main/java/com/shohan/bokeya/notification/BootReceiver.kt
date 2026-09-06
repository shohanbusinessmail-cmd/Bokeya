package com.shohan.bokeya.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shohan.bokeya.BokeyaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms reminders after events that invalidate the existing schedule:
 * a reboot, an app update, or the user changing the clock/timezone.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }

        val app = context.applicationContext as? BokeyaApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = app.container.preferences.current()
                if (settings.notificationsEnabled) {
                    app.container.reminderScheduler.schedule(settings)
                }
            } catch (_: Exception) {
                // Never crash from a broadcast; the next app launch reschedules.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
