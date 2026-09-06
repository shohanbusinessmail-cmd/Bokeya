package com.shohan.bokeya.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shohan.bokeya.BokeyaApp
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.data.local.entity.ReminderLogEntity
import com.shohan.bokeya.domain.model.UpcomingPayment

/**
 * Daily worker that posts payment reminders.
 *
 * Reminders are **logged after posting** (unique on account+date+kind), so a
 * worker that runs twice — after a reboot, a time change, or WorkManager's own
 * retry — never notifies the user about the same payment twice.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BokeyaApp ?: return Result.success()
        val container = app.container

        return runCatching {
            val settings = container.preferences.current()
            if (!settings.notificationsEnabled) return Result.success()

            val notifier = ReminderNotifier(applicationContext)
            if (!notifier.canPostNotifications()) return Result.success()

            val today = BanglaDate.today()
            val reminderDao = container.database.reminderDao()

            // Look far enough ahead to honour the user's "days before" setting.
            val horizon = (settings.reminderDaysBefore.toLong() + 1).coerceAtLeast(1)
            val upcoming = container.accountRepository.getUpcomingPayments(
                today = today,
                horizonDays = horizon,
            )

            var posted = 0
            var total = Money.ZERO

            for (payment in upcoming) {
                val kind = classify(payment, today, settings.reminderDaysBefore) ?: continue

                if (kind == ReminderNotifier.Kind.TODAY && !settings.reminderSameDay) continue
                if (kind == ReminderNotifier.Kind.OVERDUE && !settings.reminderOverdue) continue

                val alreadySent = reminderDao.wasNotified(
                    accountId = payment.accountId,
                    dueDate = payment.dueDate.toEpochDay(),
                    kind = kind.name,
                ) > 0
                if (alreadySent) continue

                notifier.notifyPayment(payment, kind, settings.useBengaliDigits)
                reminderDao.logReminder(
                    ReminderLogEntity(
                        accountId = payment.accountId,
                        dueDate = payment.dueDate.toEpochDay(),
                        kind = kind.name,
                        notifiedAt = BanglaDate.nowMillis(),
                    ),
                )
                posted++
                total += payment.amount
            }

            if (posted >= 2) {
                notifier.notifySummary(posted, total, settings.useBengaliDigits)
            }

            // Keep the log from growing forever.
            reminderDao.pruneOlderThan(BanglaDate.nowMillis() - LOG_RETENTION_MILLIS)

            Result.success()
        }.getOrElse {
            // Transient database/notification failures are worth one retry;
            // the next daily run would otherwise be 24h away.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    private fun classify(
        payment: UpcomingPayment,
        today: java.time.LocalDate,
        daysBefore: Int,
    ): ReminderNotifier.Kind? {
        val days = BanglaDate.daysBetween(today, payment.dueDate)
        return when {
            days < 0 -> ReminderNotifier.Kind.OVERDUE
            days == 0L -> ReminderNotifier.Kind.TODAY
            days <= daysBefore -> ReminderNotifier.Kind.UPCOMING
            else -> null
        }
    }

    companion object {
        const val WORK_NAME = "bokeya_daily_reminders"
        private const val MAX_ATTEMPTS = 3
        private const val LOG_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
