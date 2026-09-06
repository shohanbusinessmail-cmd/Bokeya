package com.shohan.bokeya.ui.screens.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.ui.components.AccountCard
import com.shohan.bokeya.ui.components.AmountText
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.LoadingState
import com.shohan.bokeya.ui.components.accent
import com.shohan.bokeya.ui.components.shortLabel
import com.shohan.bokeya.ui.theme.AmountTypography
import com.shohan.bokeya.ui.viewmodel.AccountsViewModel

/** All accounts, filterable by type, with a running total at the top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onAccountClick: (Long) -> Unit,
    onAddAccount: (AccountType) -> Unit,
    onSearchClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            BokeyaTopBar(
                title = stringResource(R.string.accounts_title),
                subtitle = stringResource(
                    R.string.accounts_count,
                    MoneyFormatter.formatNumber(state.accounts.size, state.settings.useBengaliDigits),
                ),
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.cd_search),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("total") {
                BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_total_due),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    AmountText(
                        money = state.totalDue,
                        style = AmountTypography.large,
                        color = MaterialTheme.colorScheme.onSurface,
                        useBengaliDigits = state.settings.useBengaliDigits,
                        animate = true,
                    )
                }
            }

            item("filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedType == null,
                        onClick = { viewModel.selectType(null) },
                        label = { Text(stringResource(R.string.accounts_tab_all)) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    )
                    AccountType.entries.forEach { type ->
                        val accent = type.accent()
                        FilterChip(
                            selected = state.selectedType == type,
                            onClick = { viewModel.selectType(type) },
                            label = { Text(type.shortLabel()) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.16f),
                                selectedLabelColor = accent,
                            ),
                        )
                    }
                }
            }

            item("showPaid") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.accounts_show_paid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.showPaid,
                        onCheckedChange = { viewModel.toggleShowPaid() },
                    )
                }
            }

            if (state.isLoading) {
                item("loading") { LoadingState() }
            } else if (state.accounts.isEmpty()) {
                item("empty") {
                    EmptyState(
                        icon = Icons.Outlined.Wallet,
                        title = stringResource(R.string.accounts_empty_title),
                        subtitle = stringResource(R.string.accounts_empty_sub),
                        actionLabel = stringResource(R.string.accounts_empty_cta),
                        onAction = { onAddAccount(state.selectedType ?: AccountType.SHOP) },
                    )
                }
            } else {
                items(state.accounts, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        onClick = { onAccountClick(account.id) },
                        useBengaliDigits = state.settings.useBengaliDigits,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
