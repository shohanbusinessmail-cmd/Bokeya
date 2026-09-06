package com.shohan.bokeya.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.AccountDetail
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtItem
import com.shohan.bokeya.domain.model.Installment
import com.shohan.bokeya.ui.components.AmountText
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.LoadingState
import com.shohan.bokeya.ui.components.PaidProgress
import com.shohan.bokeya.ui.components.PaymentTimelineRow
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.components.StatusBadge
import com.shohan.bokeya.ui.components.accent
import com.shohan.bokeya.ui.components.label
import com.shohan.bokeya.ui.theme.AmountTypography
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.AccountDetailViewModel
import com.shohan.bokeya.ui.viewmodel.DetailEvent

/**
 * Everything about one account: balance, progress, the payment timeline, plus
 * the type-specific extras (items for a shop tab, the schedule for a loan/EMI).
 */
@Composable
fun AccountDetailScreen(
    viewModel: AccountDetailViewModel,
    onBack: () -> Unit,
    onPayClick: (Long) -> Unit,
    onEditClick: (AccountType, Long) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var pendingPaymentDelete by remember { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.AccountDeleted -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.details_deleted))
                    onBack()
                }
                is DetailEvent.PaymentDeleted ->
                    snackbarHostState.showSnackbar(context.getString(R.string.payment_deleted))
                is DetailEvent.Error ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    val detail = state.detail

    Scaffold(
        topBar = {
            BokeyaTopBar(
                title = detail?.account?.name ?: stringResource(R.string.label_details),
                subtitle = detail?.account?.secondaryName,
                onBack = onBack,
                actions = {
                    if (detail != null) {
                        IconButton(onClick = { onEditClick(detail.account.type, detail.account.id) }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.cd_edit))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.cd_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!detail.account.isPaid) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.details_mark_closed)) },
                                        onClick = { menuOpen = false; showCloseDialog = true },
                                        leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = { menuOpen = false; showDeleteDialog = true },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                )
                            }
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))

            detail == null -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = stringResource(R.string.err_load_failed),
                modifier = Modifier.padding(padding),
            )

            else -> DetailContent(
                detail = detail,
                useBengaliDigits = state.settings.useBengaliDigits,
                hidden = state.settings.hideAmounts,
                onPayClick = { onPayClick(detail.account.id) },
                onDeletePayment = { pendingPaymentDelete = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            )
        }
    }

    if (showDeleteDialog && detail != null) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_account_title),
            message = stringResource(R.string.confirm_delete_account_msg, detail.account.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { showDeleteDialog = false; viewModel.deleteAccount() },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showCloseDialog) {
        ConfirmDialog(
            title = stringResource(R.string.details_mark_closed),
            message = stringResource(R.string.status_paid),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { showCloseDialog = false; viewModel.markFullyPaid() },
            onDismiss = { showCloseDialog = false },
        )
    }

    pendingPaymentDelete?.let { paymentId ->
        val payment = detail?.payments?.firstOrNull { it.id == paymentId }
        val label = payment?.let {
            MoneyFormatter.format(it.amount, state.settings.useBengaliDigits)
        }.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_payment_title),
            message = stringResource(R.string.confirm_delete_payment_msg, label),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                pendingPaymentDelete = null
                viewModel.deletePayment(paymentId, label)
            },
            onDismiss = { pendingPaymentDelete = null },
        )
    }
}

@Composable
private fun DetailContent(
    detail: AccountDetail,
    useBengaliDigits: Boolean,
    hidden: Boolean,
    onPayClick: () -> Unit,
    onDeletePayment: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = detail.account
    val accent = account.type.accent()

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("summary") {
            BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_remaining),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        AmountText(
                            money = account.remaining,
                            style = AmountTypography.large,
                            color = if (account.isPaid) {
                                BokeyaTheme.colors.success
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            useBengaliDigits = useBengaliDigits,
                            hidden = hidden,
                            animate = true,
                        )
                    }
                    StatusBadge(status = account.status)
                }

                Spacer(Modifier.height(16.dp))

                PaidProgress(
                    paid = account.paid,
                    total = account.total,
                    percent = account.progressPercent,
                    useBengaliDigits = useBengaliDigits,
                    hidden = hidden,
                    color = accent,
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat(
                        label = stringResource(R.string.details_original),
                        value = MoneyFormatter.format(account.total, useBengaliDigits),
                        hidden = hidden,
                        modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        label = stringResource(R.string.label_paid),
                        value = MoneyFormatter.format(account.paid, useBengaliDigits),
                        hidden = hidden,
                        modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        label = stringResource(R.string.label_remaining),
                        value = MoneyFormatter.format(account.remaining, useBengaliDigits),
                        hidden = hidden,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!account.isPaid) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onPayClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Payments, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.details_add_payment))
                    }
                }
            }
        }

        item("info") {
            InfoCard(
                detail = detail,
                useBengaliDigits = useBengaliDigits,
                hidden = hidden,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (detail.items.isNotEmpty()) {
            item("itemsHeader") {
                SectionHeader(
                    title = stringResource(R.string.items_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item("items") {
                BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    detail.items.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        ItemRow(item, useBengaliDigits, hidden)
                    }
                }
            }
        }

        if (detail.installments.isNotEmpty()) {
            item("scheduleHeader") {
                SectionHeader(
                    title = stringResource(R.string.details_installment_plan),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            itemsIndexed(
                detail.installments,
                key = { _, installment -> "inst_${installment.id}" },
            ) { index, installment ->
                InstallmentRow(
                    installment = installment,
                    index = index,
                    useBengaliDigits = useBengaliDigits,
                    hidden = hidden,
                    accentColor = accent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item("paymentsHeader") {
            SectionHeader(
                title = stringResource(R.string.details_payment_history),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (detail.payments.isEmpty()) {
            item("noPayments") {
                EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.details_no_payments),
                    subtitle = stringResource(R.string.details_no_payments_sub),
                    compact = true,
                )
            }
        } else {
            item("payments") {
                BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    detail.payments.forEachIndexed { index, payment ->
                        PaymentTimelineRow(
                            payment = payment,
                            isFirst = index == 0,
                            isLast = index == detail.payments.lastIndex,
                            useBengaliDigits = useBengaliDigits,
                            hidden = hidden,
                            onLongClick = { onDeletePayment(payment.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (hidden) "৳ ••••" else value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoCard(
    detail: AccountDetail,
    useBengaliDigits: Boolean,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val account = detail.account
    val context = LocalContext.current

    val rows = buildList {
        add(stringResource(R.string.filter_type) to account.type.label())
        add(
            stringResource(R.string.field_start_date) to
                BanglaDate.formatFull(account.startDate, useBengaliDigits),
        )
        account.nextDueDate?.let {
            add(
                stringResource(R.string.details_next_installment) to
                    BanglaDate.formatFull(it, useBengaliDigits),
            )
        }
        account.endDate?.let {
            add(stringResource(R.string.field_end_date) to BanglaDate.formatFull(it, useBengaliDigits))
        }
        detail.principal?.let {
            add(
                stringResource(R.string.field_loan_amount) to
                    if (hidden) "৳ ••••" else MoneyFormatter.format(it, useBengaliDigits),
            )
        }
        detail.downPayment?.takeIf { it.isPositive }?.let {
            add(
                stringResource(R.string.field_down_payment) to
                    if (hidden) "৳ ••••" else MoneyFormatter.format(it, useBengaliDigits),
            )
        }
        detail.interestRatePercent?.let {
            add(
                stringResource(R.string.details_interest) to
                    stringResource(
                        R.string.percent_format,
                        MoneyFormatter.toBengaliDigits(it.toString()).takeIf { _ -> useBengaliDigits }
                            ?: it.toString(),
                    ),
            )
        }
        detail.frequency?.let {
            add(stringResource(R.string.field_frequency) to it.label())
        }
        account.installmentAmount?.takeIf { it.isPositive }?.let {
            add(
                stringResource(R.string.field_installment_amount) to
                    if (hidden) "৳ ••••" else MoneyFormatter.format(it, useBengaliDigits),
            )
        }
        account.tenureCount?.let { count ->
            add(
                stringResource(R.string.field_tenure) to
                    stringResource(
                        R.string.details_paid_installments,
                        MoneyFormatter.formatNumber(account.paidInstallments, useBengaliDigits),
                        MoneyFormatter.formatNumber(count, useBengaliDigits),
                    ),
            )
        }
        account.direction?.let { add(stringResource(R.string.field_direction) to it.label()) }
    }

    BokeyaCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.details_info))
        Spacer(Modifier.height(10.dp))
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        val phone = account.phone
        if (!phone.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:$phone"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            ) {
                Icon(Icons.Outlined.Call, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cd_call_person, account.name))
            }
        }

        val note = account.note
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.label_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ItemRow(item: DebtItem, useBengaliDigits: Boolean, hidden: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val qty = item.quantity
            val unitPrice = item.unitPrice
            if (qty != null && unitPrice != null) {
                Text(
                    text = buildString {
                        append(MoneyFormatter.formatNumber(qty.toLong(), useBengaliDigits))
                        item.unit?.let { append(" ${it.label()}") }
                        append(" × ")
                        append(MoneyFormatter.format(unitPrice, useBengaliDigits))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AmountText(
            money = item.total,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            useBengaliDigits = useBengaliDigits,
            hidden = hidden,
        )
    }
}

@Composable
private fun InstallmentRow(
    installment: Installment,
    index: Int,
    useBengaliDigits: Boolean,
    hidden: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val isOverdue = !installment.isPaid && installment.dueDate.isBefore(BanglaDate.today())
    val statusColor = when {
        installment.isPaid -> BokeyaTheme.colors.success
        isOverdue -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    BokeyaCard(modifier = modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (installment.isPaid) {
                            BokeyaTheme.colors.success.copy(alpha = 0.14f)
                        } else {
                            accentColor.copy(alpha = 0.10f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (installment.isPaid) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        tint = BokeyaTheme.colors.success,
                        modifier = Modifier.size(17.dp),
                    )
                } else {
                    Text(
                        text = MoneyFormatter.formatNumber(index + 1, useBengaliDigits),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.details_installment_no,
                        MoneyFormatter.formatNumber(installment.sequence, useBengaliDigits),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = BanglaDate.formatFull(installment.dueDate, useBengaliDigits),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
            AmountText(
                money = installment.amount,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                useBengaliDigits = useBengaliDigits,
                hidden = hidden,
            )
        }
    }
}

/** Shared confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.large,
    )
}
