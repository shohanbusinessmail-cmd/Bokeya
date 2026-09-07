package com.shohan.bokeya.ui.screens.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.domain.model.MoneyEntry
import com.shohan.bokeya.ui.components.AmountText
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.CategoryBars
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.MoneyEntryRow
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.screens.accounts.ConfirmDialog
import com.shohan.bokeya.ui.theme.AmountTypography
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.MoneyEvent
import com.shohan.bokeya.ui.viewmodel.MoneyViewModel

/** আয়-ব্যয় tab: month picker, running totals, category split and the entry list. */
@Composable
fun MoneyScreen(
    viewModel: MoneyViewModel,
    onAddEntry: (Boolean) -> Unit,
    onEditEntry: (Boolean, Long) -> Unit,
    onManageCategories: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<MoneyEntry?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is MoneyEvent.Deleted) {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.money_entry_deleted),
                    actionLabel = context.getString(R.string.action_undo),
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            }
        }
    }

    Scaffold(
        topBar = {
            BokeyaTopBar(
                title = stringResource(R.string.money_title),
                actions = {
                    IconButton(onClick = onManageCategories) {
                        Icon(
                            Icons.Outlined.Category,
                            contentDescription = stringResource(R.string.money_manage_categories),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            PrimaryTabRow(
                selectedTabIndex = if (state.showIncome) 0 else 1,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = state.showIncome,
                    onClick = { viewModel.setShowIncome(true) },
                    text = { Text(stringResource(R.string.money_income)) },
                )
                Tab(
                    selected = !state.showIncome,
                    onClick = { viewModel.setShowIncome(false) },
                    text = { Text(stringResource(R.string.money_expense)) },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 14.dp,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("monthPicker") {
                    MonthPicker(
                        label = BanglaDate.formatMonthYear(state.month, state.settings.useBengaliDigits),
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                item("summary") {
                    BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            SummaryColumn(
                                label = stringResource(R.string.money_income),
                                money = state.income,
                                color = BokeyaTheme.colors.income,
                                bengali = state.settings.useBengaliDigits,
                                modifier = Modifier.weight(1f),
                            )
                            SummaryColumn(
                                label = stringResource(R.string.money_expense),
                                money = state.expense,
                                color = BokeyaTheme.colors.expense,
                                bengali = state.settings.useBengaliDigits,
                                modifier = Modifier.weight(1f),
                            )
                            SummaryColumn(
                                label = stringResource(R.string.money_balance),
                                money = state.net,
                                color = if (state.net.isNegative) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                bengali = state.settings.useBengaliDigits,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (state.breakdown.isNotEmpty()) {
                    item("breakdown") {
                        BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SectionHeader(title = stringResource(R.string.money_top_categories))
                            Spacer(Modifier.height(14.dp))
                            CategoryBars(
                                categories = state.breakdown,
                                useBengaliDigits = state.settings.useBengaliDigits,
                            )
                        }
                    }
                }

                if (state.isEmpty) {
                    item("empty") {
                        EmptyState(
                            icon = if (state.showIncome) {
                                Icons.AutoMirrored.Outlined.TrendingUp
                            } else {
                                Icons.AutoMirrored.Outlined.TrendingDown
                            },
                            title = stringResource(
                                if (state.showIncome) {
                                    R.string.money_empty_income
                                } else {
                                    R.string.money_empty_expense
                                },
                            ),
                            subtitle = stringResource(R.string.money_empty_sub),
                            actionLabel = stringResource(
                                if (state.showIncome) {
                                    R.string.money_add_income
                                } else {
                                    R.string.money_add_expense
                                },
                            ),
                            onAction = { onAddEntry(state.showIncome) },
                        )
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        MoneyEntryRow(
                            entry = entry,
                            onClick = { onEditEntry(entry.isIncome, entry.id) },
                            onLongClick = { pendingDelete = entry },
                            useBengaliDigits = state.settings.useBengaliDigits,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_entry_title),
            message = stringResource(R.string.confirm_delete_entry_msg),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteEntry(entry.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
fun MonthPicker(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = stringResource(R.string.cd_prev_month),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.cd_next_month),
            )
        }
    }
}

@Composable
private fun SummaryColumn(
    label: String,
    money: com.shohan.bokeya.core.money.Money,
    color: androidx.compose.ui.graphics.Color,
    bengali: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        AmountText(
            money = money,
            style = AmountTypography.small,
            color = color,
            useBengaliDigits = bengali,
        )
    }
}
