package com.shohan.bokeya.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shohan.bokeya.MainActivity
import com.shohan.bokeya.R
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.UpcomingPayment

/**
 * Builds and posts payment reminders.
 *
 * Wording is tailored per account type ("রহমান স্টোরে আপনার ৳৮০০ বাকি আছে" reads
 * naturally; a generic "Payment due" does not), and every notification deep
 * links straight to the account it is about.
 */
class ReminderNotifier(private val context: Context) {

    enum class Kind { UPCOMING, TODAY, OVERDUE }

    fun canPostNotifications(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyPayment(payment: UpcomingPayment, kind: Kind, useBengaliDigits: Boolean) {
        if (!canPostNotifications()) return

        val amount = MoneyFormatter.format(payment.amount, useBengaliDigits)
        val title = when (kind) {
            Kind.UPCOMING -> context.getString(R.string.notif_upcoming_title)
            Kind.TODAY -> context.getString(R.string.notif_today_title)
            Kind.OVERDUE -> context.getString(R.string.notif_overdue_title)
        }
        val text = buildText(payment, kind, amount, useBengaliDigits)

        val channel = if (kind == Kind.OVERDUE) NotificationChannels.OVERDUE else NotificationChannels.REMINDERS

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(
                if (kind == Kind.OVERDUE) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setGroup(NotificationChannels.GROUP_KEY)
            .setContentIntent(openAccountIntent(payment.accountId))
            .addAction(
                0,
                context.getString(R.string.notif_action_pay),
                payIntent(payment.accountId),
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(payment.notificationId(kind), notification)
        }
    }

    private fun buildText(
        payment: UpcomingPayment,
        kind: Kind,
        amount: String,
        useBengaliDigits: Boolean,
    ): String = when {
        kind == Kind.OVERDUE -> context.getString(R.string.notif_overdue_text, payment.accountName, amount)
        kind == Kind.TODAY -> context.getString(R.string.notif_today_text, payment.accountName, amount)
        payment.daysUntil == 1L -> context.getString(R.string.notif_upcoming_text, payment.accountName, amount)
        else -> context.getString(
            R.string.notif_upcoming_days_text,
            MoneyFormatter.formatNumber(payment.daysUntil, useBengaliDigits),
            payment.accountName,
            amount,
        )
    }

    /** Posts the group summary shown when several reminders stack up. */
    fun notifySummary(count: Int, total: Money, useBengaliDigits: Boolean) {
        if (!canPostNotifications() || count < 2) return

        val summary = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    R.string.notif_summary_title,
                    MoneyFormatter.formatNumber(count, useBengaliDigits),
                ),
            )
            .setContentText(MoneyFormatter.format(total, useBengaliDigits))
            .setGroup(NotificationChannels.GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openAccountIntent(null))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary)
        }
    }

    /** Used by Settings -> "একটি Notification দেখে নিন". */
    fun notifyTest() {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAccountIntent(null))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(TEST_ID, notification) }
    }

    private fun openAccountIntent(accountId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (accountId != null) putExtra(MainActivity.EXTRA_ACCOUNT_ID, accountId)
        }
        return PendingIntent.getActivity(
            context,
            accountId?.toInt() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun payIntent(accountId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ACCOUNT_ID, accountId)
            putExtra(MainActivity.EXTRA_OPEN_PAYMENT, true)
        }
        return PendingIntent.getActivity(
            context,
            (accountId + PAY_REQUEST_OFFSET).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun UpcomingPayment.notificationId(kind: Kind): Int =
        (accountId * 10 + kind.ordinal).toInt()

    companion object {
        private const val SUMMARY_ID = 999_000
        private const val TEST_ID = 999_001
        private const val PAY_REQUEST_OFFSET = 500_000L
    }
}

/** Bangla label for account types, used in notification text. */
internal fun AccountType.bnLabel(context: Context): String = when (this) {
    AccountType.SHOP -> context.getString(R.string.type_shop_short)
    AccountType.LOAN -> context.getString(R.string.type_loan_short)
    AccountType.EMI -> context.getString(R.string.type_emi_short)
    AccountType.PERSON -> context.getString(R.string.type_person_short)
}
