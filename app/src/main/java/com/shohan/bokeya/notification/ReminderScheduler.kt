package com.shohan.bokeya.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shohan.bokeya.data.prefs.UserSettings
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily reminder check.
 *
 * A periodic [ReminderWorker] is used rather than exact alarms: reminders are
 * informational, and `SCHEDULE_EXACT_ALARM` is a restricted permission that
 * Play requires justification for. WorkManager also survives reboots and app
 * updates for free, which exact alarms do not.
 */
class ReminderScheduler(private val context: Context) {

    fun schedule(settings: UserSettings) {
        if (!settings.notificationsEnabled) {
            cancel()
            return
        }

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes(settings), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    // No network requirement: the app is entirely offline.
                    .setRequiresBatteryNotLow(false)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .addTag(ReminderWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME,
            // UPDATE keeps the existing schedule when nothing changed, but picks
            // up a new reminder time immediately when the user edits it.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ReminderWorker.WORK_NAME)
    }

    /** Minutes until the next occurrence of the user's chosen reminder time. */
    private fun initialDelayMinutes(settings: UserSettings): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate()
            .atTime(settings.reminderHour, settings.reminderMinute)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMinutes().coerceAtLeast(1)
    }
}
