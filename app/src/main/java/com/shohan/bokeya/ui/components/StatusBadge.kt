package com.shohan.bokeya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.shohan.bokeya.R
import com.shohan.bokeya.domain.model.AccountStatus
import com.shohan.bokeya.ui.theme.BokeyaTheme

/**
 * Status pill for an account.
 *
 * Accessibility: status is conveyed by **icon + text**, never colour alone, so
 * it still reads correctly for colour-blind users and in greyscale.
 */
@Composable
fun StatusBadge(
    status: AccountStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = BokeyaTheme.colors
    val scheme = MaterialTheme.colorScheme

    val (label, icon, container, content) = when (status) {
        AccountStatus.PAID -> Quad(
            stringResource(R.string.status_paid),
            Icons.Filled.CheckCircle,
            colors.successContainer,
            colors.onSuccessContainer,
        )
        AccountStatus.OVERDUE -> Quad(
            stringResource(R.string.status_overdue),
            Icons.Outlined.ErrorOutline,
            scheme.errorContainer,
            scheme.onErrorContainer,
        )
        AccountStatus.DUE_TODAY -> Quad(
            stringResource(R.string.status_due_today),
            Icons.Outlined.Today,
            colors.warningContainer,
            colors.onWarningContainer,
        )
        AccountStatus.DUE_SOON -> Quad(
            stringResource(R.string.status_due_soon),
            Icons.Outlined.Schedule,
            colors.warningContainer,
            colors.onWarningContainer,
        )
        AccountStatus.ACTIVE -> Quad(
            stringResource(R.string.status_active),
            Icons.Outlined.TrendingUp,
            scheme.surfaceContainerHighest,
            scheme.onSurfaceVariant,
        )
    }

    val description = stringResource(R.string.cd_status_badge, label)

    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
        )
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

/** Small neutral pill used for account types and counts. */
@Composable
fun TypeChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d
