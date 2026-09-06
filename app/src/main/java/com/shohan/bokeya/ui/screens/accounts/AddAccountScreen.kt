package com.shohan.bokeya.ui.screens.accounts

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.DebtDirection
import com.shohan.bokeya.domain.model.InstallmentFrequency
import com.shohan.bokeya.domain.model.ItemUnit
import com.shohan.bokeya.ui.components.AmountField
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTextField
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.ChipRow
import com.shohan.bokeya.ui.components.DateField
import com.shohan.bokeya.ui.components.FormSpacer
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.components.label
import com.shohan.bokeya.ui.viewmodel.AddAccountEvent
import com.shohan.bokeya.ui.viewmodel.AddAccountUiState
import com.shohan.bokeya.ui.viewmodel.AddAccountViewModel
import com.shohan.bokeya.ui.viewmodel.ItemDraft

/**
 * Create/edit form for all four account types.
 *
 * One screen with four field sets rather than four screens: the header, dates,
 * note, validation and save button are identical, and duplicating them would
 * guarantee they drift apart.
 */
@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is AddAccountEvent.Saved) onSaved(event.accountId)
        }
    }

    val isEditing = state.editingAccountId != null
    val title = stringResource(
        when (state.type) {
            AccountType.SHOP -> if (isEditing) R.string.edit_shop_title else R.string.add_shop_title
            AccountType.LOAN -> if (isEditing) R.string.edit_loan_title else R.string.add_loan_title
            AccountType.EMI -> if (isEditing) R.string.edit_emi_title else R.string.add_emi_title
            AccountType.PERSON -> if (isEditing) R.string.edit_person_title else R.string.add_person_title
        },
    )

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
            when (state.type) {
                AccountType.SHOP -> ShopForm(state, viewModel)
                AccountType.LOAN -> LoanForm(state, viewModel)
                AccountType.EMI -> EmiForm(state, viewModel)
                AccountType.PERSON -> PersonForm(state, viewModel)
            }

            FormSpacer(18)

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

// ------------------------------------------------------------------- shop

@Composable
private fun ShopForm(state: AddAccountUiState, viewModel: AddAccountViewModel) {
    val bengali = viewModel.useBengaliDigits

    BokeyaTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        label = stringResource(R.string.field_shop_name),
        placeholder = stringResource(R.string.field_shop_name_hint),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_NAME),
        errorText = state.errors[AddAccountViewModel.KEY_NAME]?.let { stringResource(it) },
    )

    FormSpacer()

    DateField(
        date = state.startDate,
        onDateChange = viewModel::setStartDate,
        label = stringResource(R.string.label_date),
        useBengaliDigits = bengali,
    )

    FormSpacer(18)

    // Two ways to record a tab: itemised (chal, tel, dal…) or one lump sum.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip(
            label = stringResource(R.string.item_detailed_mode),
            selected = state.detailedItems,
            onClick = { viewModel.setDetailedItems(true) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            label = stringResource(R.string.item_simple_mode),
            selected = !state.detailedItems,
            onClick = { viewModel.setDetailedItems(false) },
            modifier = Modifier.weight(1f),
        )
    }

    FormSpacer(16)

    if (state.detailedItems) {
        SectionHeader(title = stringResource(R.string.items_title))
        Spacer(Modifier.height(10.dp))

        state.items.forEach { draft ->
            ItemEditor(
                draft = draft,
                canRemove = state.items.size > 1,
                onChange = { transform -> viewModel.updateItem(draft.id) { transform(it) } },
                onRemove = { viewModel.removeItem(draft.id) },
            )
            Spacer(Modifier.height(10.dp))
        }

        val itemsError = state.errors[AddAccountViewModel.KEY_ITEMS]
        if (itemsError != null) {
            Text(
                text = stringResource(itemsError),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(onClick = viewModel::addItem, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.items_add))
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.items_total_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormatter.format(state.itemsTotal, bengali),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        AmountField(
            value = state.lumpAmount,
            onValueChange = viewModel::setLumpAmount,
            label = stringResource(R.string.item_lump_label),
            isError = state.errors.containsKey(AddAccountViewModel.KEY_AMOUNT),
            errorText = state.errors[AddAccountViewModel.KEY_AMOUNT]?.let { stringResource(it) },
        )
    }

    FormSpacer(18)

    AmountField(
        value = state.initialPayment,
        onValueChange = viewModel::setInitialPayment,
        label = stringResource(R.string.field_already_paid),
        supportingText = stringResource(R.string.field_already_paid_helper),
    )

    FormSpacer()

    OptionalDueDate(state, viewModel, labelRes = R.string.field_due_date)
}

@Composable
private fun ItemEditor(
    draft: ItemDraft,
    canRemove: Boolean,
    onChange: ((ItemDraft) -> ItemDraft) -> Unit,
    onRemove: () -> Unit,
) {
    BokeyaCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                BokeyaTextField(
                    value = draft.name,
                    onValueChange = { value -> onChange { it.copy(name = value) } },
                    label = stringResource(R.string.item_name),
                    placeholder = stringResource(R.string.item_name_hint),
                )
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BokeyaTextField(
                value = draft.quantity,
                onValueChange = { value -> onChange { it.copy(quantity = value) } },
                label = stringResource(R.string.item_qty),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AmountField(
                value = draft.unitPrice,
                onValueChange = { value -> onChange { it.copy(unitPrice = value) } },
                label = stringResource(R.string.item_unit_price),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        ChipRow(
            options = ItemUnit.entries.toList(),
            selected = draft.unit,
            onSelect = { unit -> onChange { it.copy(unit = unit) } },
            label = { it.label() },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollCompat(),
        )

        val computed = draft.computedTotal
        if (computed != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.item_total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = MoneyFormatter.format(computed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
            AmountField(
                value = draft.total,
                onValueChange = { value -> onChange { it.copy(total = value) } },
                label = stringResource(R.string.item_total),
            )
        }
    }
}

// ------------------------------------------------------------------- loan

@Composable
private fun LoanForm(state: AddAccountUiState, viewModel: AddAccountViewModel) {
    val bengali = viewModel.useBengaliDigits

    BokeyaTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        label = stringResource(R.string.field_loan_name),
        placeholder = stringResource(R.string.field_loan_name_hint),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_NAME),
        errorText = state.errors[AddAccountViewModel.KEY_NAME]?.let { stringResource(it) },
    )

    FormSpacer()

    BokeyaTextField(
        value = state.secondaryName,
        onValueChange = viewModel::setSecondaryName,
        label = stringResource(R.string.field_loan_org),
        placeholder = stringResource(R.string.field_loan_org_hint),
    )

    FormSpacer()

    AmountField(
        value = state.loanAmount,
        onValueChange = viewModel::setLoanAmount,
        label = stringResource(R.string.field_loan_amount),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_AMOUNT),
        errorText = state.errors[AddAccountViewModel.KEY_AMOUNT]?.let { stringResource(it) },
    )

    FormSpacer()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.field_has_interest),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = state.hasInterest, onCheckedChange = viewModel::setHasInterest)
    }

    if (state.hasInterest) {
        FormSpacer()
        BokeyaTextField(
            value = state.interestRate,
            onValueChange = viewModel::setInterestRate,
            label = stringResource(R.string.field_interest_rate),
            keyboardType = KeyboardType.Decimal,
            isError = state.errors.containsKey(AddAccountViewModel.KEY_INTEREST),
            errorText = state.errors[AddAccountViewModel.KEY_INTEREST]?.let { stringResource(it) },
        )
    }

    FormSpacer()

    AmountField(
        value = state.payableAmount,
        onValueChange = viewModel::setPayableAmount,
        label = stringResource(R.string.field_payable),
        supportingText = state.suggestedPayable?.let {
            stringResource(R.string.field_payable_helper) + " · " + MoneyFormatter.format(it, bengali)
        } ?: stringResource(R.string.field_payable_helper),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_PAYABLE),
        errorText = state.errors[AddAccountViewModel.KEY_PAYABLE]?.let { stringResource(it) },
    )

    FormSpacer()

    AmountField(
        value = state.installmentAmount,
        onValueChange = viewModel::setInstallmentAmount,
        label = stringResource(R.string.field_installment_amount),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_INSTALLMENT),
        errorText = state.errors[AddAccountViewModel.KEY_INSTALLMENT]?.let { stringResource(it) },
    )

    FormSpacer(16)

    Text(
        text = stringResource(R.string.field_frequency),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    ChipRow(
        options = InstallmentFrequency.entries.toList(),
        selected = state.frequency,
        onSelect = viewModel::setFrequency,
        label = { it.label() },
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScrollCompat(),
    )

    if (state.frequency == InstallmentFrequency.CUSTOM) {
        FormSpacer()
        BokeyaTextField(
            value = state.customDays,
            onValueChange = viewModel::setCustomDays,
            label = stringResource(R.string.field_custom_days),
            keyboardType = KeyboardType.Number,
            isError = state.errors.containsKey(AddAccountViewModel.KEY_DAYS),
            errorText = state.errors[AddAccountViewModel.KEY_DAYS]?.let { stringResource(it) },
        )
    }

    FormSpacer()

    DateField(
        date = state.startDate,
        onDateChange = viewModel::setStartDate,
        label = stringResource(R.string.field_start_date),
        useBengaliDigits = bengali,
    )

    FormSpacer()

    DateField(
        date = state.firstDueDate,
        onDateChange = viewModel::setFirstDueDate,
        label = stringResource(R.string.field_first_due),
        useBengaliDigits = bengali,
    )

    FormSpacer()

    AmountField(
        value = state.alreadyPaid,
        onValueChange = viewModel::setAlreadyPaid,
        label = stringResource(R.string.field_already_paid),
        supportingText = stringResource(R.string.field_already_paid_helper),
    )
}

// -------------------------------------------------------------------- EMI

@Composable
private fun EmiForm(state: AddAccountUiState, viewModel: AddAccountViewModel) {
    val bengali = viewModel.useBengaliDigits

    BokeyaTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        label = stringResource(R.string.field_product_name),
        placeholder = stringResource(R.string.field_product_name_hint),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_NAME),
        errorText = state.errors[AddAccountViewModel.KEY_NAME]?.let { stringResource(it) },
    )

    FormSpacer()

    BokeyaTextField(
        value = state.secondaryName,
        onValueChange = viewModel::setSecondaryName,
        label = stringResource(R.string.field_seller_name),
    )

    FormSpacer()

    AmountField(
        value = state.cashPrice,
        onValueChange = viewModel::setCashPrice,
        label = stringResource(R.string.field_cash_price),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_AMOUNT),
        errorText = state.errors[AddAccountViewModel.KEY_AMOUNT]?.let { stringResource(it) },
    )

    FormSpacer()

    AmountField(
        value = state.downPayment,
        onValueChange = viewModel::setDownPayment,
        label = stringResource(R.string.field_down_payment),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_DOWN),
        errorText = state.errors[AddAccountViewModel.KEY_DOWN]?.let { stringResource(it) },
    )

    FormSpacer()

    BokeyaTextField(
        value = state.tenureMonths,
        onValueChange = viewModel::setTenureMonths,
        label = stringResource(R.string.field_tenure),
        keyboardType = KeyboardType.Number,
        isError = state.errors.containsKey(AddAccountViewModel.KEY_TENURE),
        errorText = state.errors[AddAccountViewModel.KEY_TENURE]?.let { stringResource(it) },
    )

    // Live preview: users think in "how much per month", not "financed amount".
    if (state.financedAmount.isPositive) {
        FormSpacer(16)
        BokeyaCard(contentPadding = 14.dp) {
            PreviewRow(
                label = stringResource(R.string.field_financed),
                value = MoneyFormatter.format(state.financedAmount, bengali),
            )
            state.computedEmi?.let {
                Spacer(Modifier.height(8.dp))
                PreviewRow(
                    label = stringResource(R.string.field_installment_amount),
                    value = MoneyFormatter.format(it, bengali),
                    emphasise = true,
                )
            }
        }
    }

    FormSpacer()

    DateField(
        date = state.startDate,
        onDateChange = viewModel::setStartDate,
        label = stringResource(R.string.field_start_date),
        useBengaliDigits = bengali,
    )

    FormSpacer()

    DateField(
        date = state.firstDueDate,
        onDateChange = viewModel::setFirstDueDate,
        label = stringResource(R.string.field_first_due),
        useBengaliDigits = bengali,
    )
}

// ----------------------------------------------------------------- person

@Composable
private fun PersonForm(state: AddAccountUiState, viewModel: AddAccountViewModel) {
    val bengali = viewModel.useBengaliDigits

    Text(
        text = stringResource(R.string.field_direction),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    ChipRow(
        options = DebtDirection.entries.toList(),
        selected = state.direction,
        onSelect = viewModel::setDirection,
        label = { it.label() },
    )

    FormSpacer(16)

    BokeyaTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        label = stringResource(R.string.field_person_name),
        placeholder = stringResource(R.string.field_person_name_hint),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_NAME),
        errorText = state.errors[AddAccountViewModel.KEY_NAME]?.let { stringResource(it) },
    )

    FormSpacer()

    BokeyaTextField(
        value = state.secondaryName,
        onValueChange = viewModel::setSecondaryName,
        label = stringResource(R.string.field_relationship),
        placeholder = stringResource(R.string.field_relationship_hint),
    )

    FormSpacer()

    BokeyaTextField(
        value = state.phone,
        onValueChange = viewModel::setPhone,
        label = stringResource(R.string.label_phone) + " " + stringResource(R.string.label_optional),
        keyboardType = KeyboardType.Phone,
    )

    FormSpacer()

    AmountField(
        value = state.personAmount,
        onValueChange = viewModel::setPersonAmount,
        label = stringResource(
            if (state.direction == DebtDirection.BORROWED) {
                R.string.field_amount_borrowed
            } else {
                R.string.field_amount_lent
            },
        ),
        isError = state.errors.containsKey(AddAccountViewModel.KEY_AMOUNT),
        errorText = state.errors[AddAccountViewModel.KEY_AMOUNT]?.let { stringResource(it) },
    )

    FormSpacer()

    DateField(
        date = state.startDate,
        onDateChange = viewModel::setStartDate,
        label = stringResource(R.string.field_borrow_date),
        useBengaliDigits = bengali,
    )

    FormSpacer()

    OptionalDueDate(state, viewModel, labelRes = R.string.field_due_date)

    FormSpacer()

    AmountField(
        value = state.alreadyPaid,
        onValueChange = viewModel::setAlreadyPaid,
        label = stringResource(R.string.field_already_paid),
        supportingText = stringResource(R.string.field_already_paid_helper),
    )
}

// ----------------------------------------------------------------- shared

@Composable
private fun OptionalDueDate(
    state: AddAccountUiState,
    viewModel: AddAccountViewModel,
    labelRes: Int,
) {
    val dueDate = state.dueDate
    if (dueDate == null) {
        TextButton(onClick = { viewModel.setDueDate(state.startDate.plusDays(7)) }) {
            Text(stringResource(labelRes) + " " + stringResource(R.string.action_add))
        }
    } else {
        DateField(
            date = dueDate,
            onDateChange = viewModel::setDueDate,
            label = stringResource(labelRes),
            useBengaliDigits = viewModel.useBengaliDigits,
        )
        val error = state.errors[AddAccountViewModel.KEY_DUE_DATE]
        if (error != null) {
            Text(
                text = stringResource(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        TextButton(onClick = { viewModel.setDueDate(null) }) {
            Text(stringResource(R.string.action_clear))
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun PreviewRow(label: String, value: String, emphasise: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasise) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (emphasise) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Chip rows can overflow on small screens; make them scroll instead of clipping. */
@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(androidx.compose.foundation.horizontalScroll(rememberScrollState()))
