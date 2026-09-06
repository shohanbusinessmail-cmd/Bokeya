package com.shohan.bokeya.ui.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.ui.components.AmountField
import com.shohan.bokeya.ui.components.BokeyaTextField
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.DateField
import com.shohan.bokeya.ui.components.FormSpacer
import com.shohan.bokeya.ui.components.TimeField
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.AddMoneyViewModel

/** Add or edit one income/expense entry. */
@Composable
fun AddMoneyScreen(
    viewModel: AddMoneyViewModel,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    val isEditing = state.editingId != null
    val title = stringResource(
        when {
            state.isIncome && isEditing -> R.string.money_edit_income
            state.isIncome -> R.string.money_add_income
            isEditing -> R.string.money_edit_expense
            else -> R.string.money_add_expense
        },
    )
    val accent = if (state.isIncome) BokeyaTheme.colors.income else BokeyaTheme.colors.expense

    Scaffold(
        topBar = { BokeyaTopBar(title = title, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            AmountField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                label = stringResource(R.string.label_amount),
                isError = state.amountError != null,
                errorText = state.amountError?.let { stringResource(it) },
            )

            FormSpacer()

            BokeyaTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = stringResource(
                    if (state.isIncome) R.string.money_source else R.string.money_spent_on,
                ),
                placeholder = stringResource(
                    if (state.isIncome) R.string.money_source_hint else R.string.money_spent_on_hint,
                ),
                isError = state.titleError != null,
                errorText = state.titleError?.let { stringResource(it) },
            )

            FormSpacer(18)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.money_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onManageCategories) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.money_new_category))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.categories.forEach { category ->
                    val color = category.color?.let { Color(it) } ?: accent
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { viewModel.setCategory(category.id) },
                        label = { Text(category.name) },
                        shape = RoundedCornerShape(50),
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(color),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.16f),
                            selectedLabelColor = color,
                        ),
                    )
                }
            }

            FormSpacer(18)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    DateField(
                        date = state.date,
                        onDateChange = viewModel::setDate,
                        label = stringResource(R.string.label_date),
                    )
                }
                Box(Modifier.weight(1f)) {
                    TimeField(
                        time = state.time,
                        onTimeChange = viewModel::setTime,
                        label = stringResource(R.string.label_time),
                    )
                }
            }

            FormSpacer()

            BokeyaTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = stringResource(R.string.label_note) + " " + stringResource(R.string.label_optional),
                singleLine = false,
                minLines = 2,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            )

            FormSpacer(22)

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
