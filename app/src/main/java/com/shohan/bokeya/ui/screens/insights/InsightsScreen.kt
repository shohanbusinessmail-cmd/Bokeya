package com.shohan.bokeya.ui.screens.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.CategoryBars
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.IncomeExpenseChart
import com.shohan.bokeya.ui.components.LoadingState
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.screens.dashboard.InsightRow
import com.shohan.bokeya.ui.viewmodel.InsightsViewModel

/**
 * Local analysis of the user's own data.
 *
 * Everything here is computed on-device from rows the user entered — no model,
 * no upload, no "smart" service. That is the whole point of the feature.
 */
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.insights_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))

            state.insights.isEmpty() && state.monthlyTrend.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Insights,
                title = stringResource(R.string.insights_empty),
                subtitle = stringResource(R.string.insights_empty_sub),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.insights.isNotEmpty()) {
                    item("insights") {
                        BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            state.insights.forEachIndexed { index, insight ->
                                if (index > 0) Spacer(Modifier.height(14.dp))
                                InsightRow(insight)
                            }
                        }
                    }
                }

                if (state.monthlyTrend.size >= 2) {
                    item("trend") {
                        BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SectionHeader(title = stringResource(R.string.dashboard_income_expense_chart))
                            Text(
                                text = stringResource(R.string.dashboard_last_six_months),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            IncomeExpenseChart(
                                trend = state.monthlyTrend,
                                useBengaliDigits = state.settings.useBengaliDigits,
                                hidden = state.settings.hideAmounts,
                            )
                        }
                    }
                }

                if (state.expenseBreakdown.isNotEmpty()) {
                    item("categories") {
                        BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SectionHeader(title = stringResource(R.string.money_top_categories))
                            Spacer(Modifier.height(14.dp))
                            CategoryBars(
                                categories = state.expenseBreakdown,
                                useBengaliDigits = state.settings.useBengaliDigits,
                                hidden = state.settings.hideAmounts,
                                maxRows = 8,
                            )
                        }
                    }
                }
            }
        }
    }
}
