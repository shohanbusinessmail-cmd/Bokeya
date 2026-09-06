package com.shohan.bokeya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.Account
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.LedgerEntry
import com.shohan.bokeya.domain.model.MoneyEntry
import com.shohan.bokeya.domain.model.Payment
import com.shohan.bokeya.domain.model.UpcomingPayment
import com.shohan.bokeya.ui.theme.BokeyaTheme

/**
 * The account row used on the dashboard and the accounts list.
 *
 * Everything a user needs to triage a debt at a glance: who, how much is left,
 * how far along they are, and when the next payment is due.
 */
@Composable
fun AccountCard(
    account: Account,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
    showProgress: Boolean = true,
) {
    val accent = account.type.accent()
    val statusLabel = stringResource(
        when (account.status) {
            AccountStatus.PAID -> R.string.status_paid
            AccountStatus.OVERDUE -> R.string.status_overdue
            AccountStatus.DUE_TODAY -> R.string.status_due_today
            AccountStatus.DUE_SOON -> R.string.status_due_soon
            AccountStatus.ACTIVE -> R.string.status_active
        },
    )
    val cardDescription = stringResource(
        R.string.cd_account_card,
        account.name,
        MoneyFormatter.format(account.remaining, useBengaliDigits),
        statusLabel,
    )

    BokeyaCard(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = cardDescription },
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = account.type.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = account.secondaryName ?: account.type.label()
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    money = account.remaining,
                    style = com.shohan.bokeya.ui.theme.AmountTypography.small,
                    color = if (account.direction == DebtDirection.LENT) {
                        BokeyaTheme.colors.success
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    useBengaliDigits = useBengaliDigits,
                    hidden = hidden,
                )
                Spacer(Modifier.height(5.dp))
                StatusBadge(status = account.status, compact = true)
            }
        }

        if (showProgress && account.total.isPositive && !account.isPaid) {
            Spacer(Modifier.height(12.dp))
            ProgressBar(
                percent = account.progressPercent,
                height = 5.dp,
                progressColor = accent,
                contentDescription = null,
            )
        }

        val due = account.nextDueDate
        if (due != null && !account.isPaid) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.EventNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = BanglaDate.relativeDayLong(due, useBengaliDigits = useBengaliDigits),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (account.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Compact upcoming/overdue payment row for the dashboard and reminders. */
@Composable
fun UpcomingPaymentRow(
    payment: UpcomingPayment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
) {
    val accent = if (payment.isOverdue) MaterialTheme.colorScheme.error else payment.accountType.accent()

    BokeyaCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = 13.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = payment.accountType.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payment.accountName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = BanglaDate.relativeDayLong(payment.dueDate, useBengaliDigits = useBengaliDigits),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (payment.isOverdue) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            AmountText(
                money = payment.amount,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                useBengaliDigits = useBengaliDigits,
                hidden = hidden,
            )
        }
    }
}

/** One row in the unified history / recent-activity list. */
@Composable
fun LedgerRow(
    entry: LedgerEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
    showDate: Boolean = true,
) {
    val color = entry.type.amountColor()
    val icon: ImageVector = when {
        entry.type.isMoneyIn -> Icons.AutoMirrored.Outlined.TrendingUp
        entry.type.isPayment -> Icons.Outlined.CheckCircle
        else -> Icons.AutoMirrored.Outlined.TrendingDown
    }
    val sign = if (entry.type.isMoneyIn) "+" else "−"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                entry.subtitle?.takeIf { it.isNotBlank() },
                if (showDate) BanglaDate.relativeDay(entry.date, useBengaliDigits = useBengaliDigits) else null,
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = if (hidden) {
                "৳ ••••"
            } else {
                sign + MoneyFormatter.format(entry.amount, useBengaliDigits)
            },
            style = MaterialTheme.typography.titleSmall,
            color = color,
            maxLines = 1,
        )
    }
}

/** Row in the আয়-ব্যয় list, with the category dot as the leading element. */
@Composable
fun MoneyEntryRow(
    entry: MoneyEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val fallback = if (entry.isIncome) BokeyaTheme.colors.income else BokeyaTheme.colors.expense
    val color = entry.categoryColor?.let { Color(it) } ?: fallback

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            // Long-press to delete keeps the row clean: no trailing bin icon on
            // every line of a list the user scrolls through daily.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 11.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (entry.isIncome) {
                    Icons.AutoMirrored.Outlined.TrendingUp
                } else {
                    Icons.AutoMirrored.Outlined.TrendingDown
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    entry.categoryName,
                    BanglaDate.relativeDay(entry.date, useBengaliDigits = useBengaliDigits),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        AmountText(
            money = entry.amount,
            style = MaterialTheme.typography.titleSmall,
            color = if (entry.isIncome) BokeyaTheme.colors.income else MaterialTheme.colorScheme.onSurface,
            useBengaliDigits = useBengaliDigits,
            hidden = hidden,
        )
    }
}

/** One entry in an account's payment timeline. */
@Composable
fun PaymentTimelineRow(
    payment: Payment,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val accent = BokeyaTheme.colors.success

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Timeline rail: a dot per payment joined by a hairline.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp),
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(if (isFirst) 6.dp else 12.dp)
                    .background(
                        if (isFirst) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmountText(
                    money = payment.amount,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    useBengaliDigits = useBengaliDigits,
                    hidden = hidden,
                )
                Text(
                    text = payment.method.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = BanglaDate.formatFull(payment.date, useBengaliDigits) +
                    " · " + BanglaDate.formatTime(payment.timeMillis, useBengaliDigits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val note = payment.note
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onLongClick != null) {
            IconButtonDelete(onClick = onLongClick)
        }
    }
}

@Composable
private fun IconButtonDelete(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}
