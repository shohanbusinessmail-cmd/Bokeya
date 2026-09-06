package com.shohan.bokeya.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.shohan.bokeya.R

/**
 * Notification channels.
 *
 * Two channels rather than one: reminders and overdue alerts have genuinely
 * different urgency, and separating them lets a user silence gentle reminders
 * while still being told about a missed payment. Channels are created up-front
 * because their importance cannot be changed after the first creation.
 */
object NotificationChannels {

    const val REMINDERS = "bokeya_reminders"
    const val OVERDUE = "bokeya_overdue"

    /** Notifications are grouped so several dues don't flood the shade. */
    const val GROUP_KEY = "com.shohan.bokeya.REMINDERS"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notif_channel_reminder),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_reminder_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val overdue = NotificationChannel(
            OVERDUE,
            context.getString(R.string.notif_channel_overdue),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_overdue_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannels(listOf(reminders, overdue))
    }
}
