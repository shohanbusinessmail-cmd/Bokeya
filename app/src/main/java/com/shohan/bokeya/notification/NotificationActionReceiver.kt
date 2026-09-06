package com.shohan.bokeya.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Handles the "পরে দেখব" action, which simply dismisses the reminder. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val ACTION_DISMISS = "com.shohan.bokeya.DISMISS_REMINDER"
    }
}
