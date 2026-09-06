package com.shohan.bokeya.ui.screens.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.domain.model.HistoryFilter
import com.shohan.bokeya.domain.model.HistorySort
import com.shohan.bokeya.domain.model.TransactionType
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.LedgerRow
import com.shohan.bokeya.ui.components.LoadingState
import com.shohan.bokeya.ui.components.label
import com.shohan.bokeya.ui.viewmodel.HistoryViewModel

/** Unified, filterable, sortable list of every transaction. */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onEntryClick: (Boolean, Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BokeyaTopBar(
                title = stringResource(R.string.history_title),
                onBack = onBack,
                actions = {
                    Column {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = stringResource(R.string.cd_sort),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            HistorySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label()) },
                                    onClick = {
                                        viewModel.setSort(sort)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label()) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }

            when {
                state.isLoading -> LoadingState()

                state.isEmpty -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = stringResource(R.string.history_empty),
                    subtitle = stringResource(R.string.history_empty_sub),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 8.dp,
                        bottom = 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        LedgerRow(
                            entry = entry,
                            onClick = {
                                val entryId = entry.entryId
                                when {
                                    entry.accountId != null -> onAccountClick(entry.accountId)
                                    entryId != null ->
                                        onEntryClick(entry.type == TransactionType.INCOME, entryId)
                                }
                            },
                            useBengaliDigits = state.settings.useBengaliDigits,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
