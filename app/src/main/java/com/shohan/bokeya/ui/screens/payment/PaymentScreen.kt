package com.shohan.bokeya.ui.screens.payment

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.PaymentMethod
import com.shohan.bokeya.ui.components.AmountField
import com.shohan.bokeya.ui.components.AmountText
import com.shohan.bokeya.ui.components.BokeyaTextField
import com.shohan.bokeya.ui.components.ChipRow
import com.shohan.bokeya.ui.components.DateField
import com.shohan.bokeya.ui.components.FormSpacer
import com.shohan.bokeya.ui.components.TimeField
import com.shohan.bokeya.ui.components.label
import com.shohan.bokeya.ui.theme.AmountTypography
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.PaymentViewModel

/**
 * Payment bottom sheet.
 *
 * A sheet rather than a full screen: recording a payment is a quick, focused
 * act and the user should keep seeing where they came from. On success the same
 * sheet flips to a confirmation showing what was paid and what is left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        val outcome = state.success
        if (outcome != null) {
            SuccessContent(
                justPaid = MoneyFormatter.format(
                    outcome.remainingBefore - outcome.remainingAfter,
                    state.useBengaliDigits,
                ),
                remaining = MoneyFormatter.format(outcome.remainingAfter, state.useBengaliDigits),
                isFullyPaid = outcome.isFullyPaid,
                onDone = onDismiss,
            )
        } else {
            FormContent(state = state, viewModel = viewModel, onCancel = onDismiss)
        }
    }
}

@Composable
private fun FormContent(
    state: com.shohan.bokeya.ui.viewmodel.PaymentUiState,
    viewModel: PaymentViewModel,
    onCancel: () -> Unit,
) {
    val account = state.account
    val bengali = state.useBengaliDigits

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.payment_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (account != null) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.label_remaining) + ": ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AmountText(
                    money = account.remaining,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    useBengaliDigits = bengali,
                )
            }
        }

        FormSpacer(18)

        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) {
                AmountField(
                    value = state.amount,
                    onValueChange = viewModel::setAmount,
                    label = stringResource(R.string.payment_how_much),
                    isError = state.amountError != null,
                    errorText = state.amountError?.let { stringResource(it) },
                    supportingText = if (state.isOverpaying && account != null) {
                        stringResource(
                            R.string.err_overpay_helper,
                            MoneyFormatter.format(account.remaining, bengali),
                        )
                    } else {
                        null
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = viewModel::fillFullAmount,
                modifier = Modifier.padding(bottom = 22.dp),
            ) {
                Text(stringResource(R.string.payment_full))
            }
        }

        FormSpacer(6)

        Text(
            text = stringResource(R.string.payment_method),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ChipRow(
            options = PaymentMethod.entries.toList(),
            selected = state.method,
            onSelect = viewModel::setMethod,
            label = { it.label() },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )

        FormSpacer(16)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                DateField(
                    date = state.date,
                    onDateChange = viewModel::setDate,
                    label = stringResource(R.string.label_date),
                    useBengaliDigits = bengali,
                )
            }
            Box(Modifier.weight(1f)) {
                TimeField(
                    time = state.time,
                    onTimeChange = viewModel::setTime,
                    label = stringResource(R.string.label_time),
                    useBengaliDigits = bengali,
                )
            }
        }

        FormSpacer()

        BokeyaTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            label = stringResource(R.string.label_note) + " " + stringResource(R.string.label_optional),
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        )

        FormSpacer(22)

        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !state.isSaving && !state.isLoading,
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.payment_save))
            }
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun SuccessContent(
    justPaid: String,
    remaining: String,
    isFullyPaid: Boolean,
    onDone: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "successScale",
    )
    val accent = BokeyaTheme.colors.success

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cd_success),
                tint = accent,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(
                if (isFullyPaid) R.string.payment_success_full else R.string.payment_success,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SuccessStat(
                label = stringResource(R.string.payment_success_paid),
                value = justPaid,
                color = accent,
            )
            SuccessStat(
                label = stringResource(R.string.payment_success_remaining),
                value = remaining,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(26.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text(stringResource(R.string.action_done))
        }
    }
}

@Composable
private fun SuccessStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = AmountTypography.small, color = color)
    }
}
