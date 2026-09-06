package com.shohan.bokeya.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.domain.model.TransactionType
import com.shohan.bokeya.ui.components.AccountCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.LedgerRow
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.viewmodel.SearchViewModel

/** Global search across accounts, people, products, notes and transactions. */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onEntryClick: (Boolean, Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    // Searching is the only reason to be here — open the keyboard immediately.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.search_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.hasQuery) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            )

            when {
                !state.hasQuery -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.search_start),
                    subtitle = stringResource(R.string.search_start_sub),
                )

                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.search_empty),
                    subtitle = stringResource(R.string.search_empty_sub),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 12.dp,
                        bottom = 104.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.results.accounts.isNotEmpty()) {
                        item("accountsHeader") {
                            SectionHeader(
                                title = stringResource(R.string.search_accounts),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(state.results.accounts, key = { "acc_${it.id}" }) { account ->
                            AccountCard(
                                account = account,
                                onClick = { onAccountClick(account.id) },
                                useBengaliDigits = state.settings.useBengaliDigits,
                                showProgress = false,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    if (state.results.transactions.isNotEmpty()) {
                        item("txHeader") {
                            SectionHeader(
                                title = stringResource(R.string.search_transactions),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(state.results.transactions, key = { it.id }) { entry ->
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
}
