package com.shohan.bokeya.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.domain.model.Insight
import com.shohan.bokeya.domain.model.InsightTone
import com.shohan.bokeya.ui.components.AmountText
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.DonutChart
import com.shohan.bokeya.ui.components.DonutSlice
import com.shohan.bokeya.ui.components.GradientHeroCard
import com.shohan.bokeya.ui.components.IncomeExpenseChart
import com.shohan.bokeya.ui.components.LedgerRow
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.components.StatTile
import com.shohan.bokeya.ui.components.UpcomingPaymentRow
import com.shohan.bokeya.ui.components.accent
import com.shohan.bokeya.ui.components.shortLabel
import com.shohan.bokeya.ui.theme.AmountTypography
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.DashboardUiState
import com.shohan.bokeya.ui.viewmodel.DashboardViewModel

/**
 * The home screen: one glanceable answer to "where do I stand today?".
 *
 * Content is ordered by urgency — total বকেয়া, then overdue, then what is due
 * next, then today's cash flow, then the analytical extras.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAccountClick: (Long) -> Unit,
    onSeeAllAccounts: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onSearchClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onAddAccount: (AccountType) -> Unit,
    onAddMoney: (Boolean) -> Unit,
    onPayClick: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("greeting") {
            GreetingRow(
                state = state,
                onToggleVisibility = viewModel::toggleAmountVisibility,
                onSearchClick = onSearchClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item("hero") {
            HeroCard(
                state = state,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (state.summary.overdueCount > 0) {
            item("overdueBanner") {
                OverdueBanner(
                    count = state.summary.overdueCount,
                    amount = state.summary.overdueAmount,
                    useBengaliDigits = state.settings.useBengaliDigits,
                    hidden = state.settings.hideAmounts,
                    onClick = onSeeAllAccounts,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item("todayTiles") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    label = stringResource(R.string.dashboard_income_today),
                    amount = state.summary.incomeToday,
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    accent = BokeyaTheme.colors.income,
                    hidden = state.settings.hideAmounts,
                    useBengaliDigits = state.settings.useBengaliDigits,
                    onClick = { onAddMoney(true) },
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.dashboard_expense_today),
                    amount = state.summary.expenseToday,
                    icon = Icons.AutoMirrored.Outlined.TrendingDown,
                    accent = BokeyaTheme.colors.expense,
                    hidden = state.settings.hideAmounts,
                    useBengaliDigits = state.settings.useBengaliDigits,
                    onClick = { onAddMoney(false) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.overdue.isNotEmpty()) {
            item("overdueHeader") {
                SectionHeader(
                    title = stringResource(R.string.dashboard_overdue),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.overdue, key = { "overdue_${it.accountId}_${it.dueDate}" }) { payment ->
                UpcomingPaymentRow(
                    payment = payment,
                    onClick = { onPayClick(payment.accountId) },
                    useBengaliDigits = state.settings.useBengaliDigits,
                    hidden = state.settings.hideAmounts,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (state.upcoming.isNotEmpty()) {
            item("upcomingHeader") {
                SectionHeader(
                    title = stringResource(R.string.dashboard_upcoming),
                    actionLabel = stringResource(R.string.action_see_all),
                    onActionClick = onSeeAllAccounts,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.upcoming, key = { "up_${it.accountId}_${it.dueDate}" }) { payment ->
                UpcomingPaymentRow(
                    payment = payment,
                    onClick = { onAccountClick(payment.accountId) },
                    useBengaliDigits = state.settings.useBengaliDigits,
                    hidden = state.settings.hideAmounts,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (state.summary.dueByType.isNotEmpty()) {
            item("breakdown") {
                DebtBreakdownCard(
                    state = state,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (state.insights.isNotEmpty()) {
            item("insights") {
                InsightsCard(
                    insights = state.insights,
                    onClick = onInsightsClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (state.trend.size >= 2) {
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
                        trend = state.trend,
                        useBengaliDigits = state.settings.useBengaliDigits,
                        hidden = state.settings.hideAmounts,
                    )
                }
            }
        }

        if (state.recent.isNotEmpty()) {
            item("recentHeader") {
                SectionHeader(
                    title = stringResource(R.string.label_history),
                    actionLabel = stringResource(R.string.action_see_all),
                    onActionClick = onSeeAllHistory,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.recent, key = { it.id }) { entry ->
                LedgerRow(
                    entry = entry,
                    onClick = { entry.accountId?.let(onAccountClick) },
                    useBengaliDigits = state.settings.useBengaliDigits,
                    hidden = state.settings.hideAmounts,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (!state.isLoading && !state.hasAnyData) {
            item("empty") {
                com.shohan.bokeya.ui.components.EmptyState(
                    icon = Icons.Outlined.Wallet,
                    title = stringResource(R.string.accounts_empty_title),
                    subtitle = stringResource(R.string.accounts_empty_sub),
                    actionLabel = stringResource(R.string.accounts_empty_cta),
                    onAction = { onAddAccount(AccountType.SHOP) },
                )
            }
        }
    }
}

@Composable
private fun GreetingRow(
    state: DashboardUiState,
    onToggleVisibility: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greetingText = stringResource(
        when (state.greeting) {
            BanglaDate.GreetingType.MORNING -> R.string.greeting_morning
            BanglaDate.GreetingType.NOON -> R.string.greeting_noon
            BanglaDate.GreetingType.AFTERNOON -> R.string.greeting_afternoon
            BanglaDate.GreetingType.EVENING -> R.string.greeting_evening
            BanglaDate.GreetingType.NIGHT -> R.string.greeting_night
        },
    )

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.userName.isBlank()) {
                    greetingText
                } else {
                    stringResource(R.string.greeting_with_name, greetingText, state.userName)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = BanglaDate.formatFull(BanglaDate.today(), state.settings.useBengaliDigits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onToggleVisibility) {
            Icon(
                imageVector = if (state.settings.hideAmounts) {
                    Icons.Outlined.VisibilityOff
                } else {
                    Icons.Outlined.Visibility
                },
                contentDescription = stringResource(R.string.cd_toggle_amount_visibility),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val summary = state.summary
    val bengali = state.settings.useBengaliDigits

    GradientHeroCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dashboard_total_due),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(6.dp))
            AmountText(
                money = summary.totalDue,
                style = AmountTypography.hero,
                color = Color.White,
                useBengaliDigits = bengali,
                hidden = state.settings.hideAmounts,
                animate = true,
            )

            if (summary.totalReceivable.isPositive) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dashboard_total_receivable) + ": ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    AmountText(
                        money = summary.totalReceivable,
                        style = MaterialTheme.typography.titleSmall,
                        color = BokeyaTheme.colors.successContainer,
                        useBengaliDigits = bengali,
                        hidden = state.settings.hideAmounts,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroStat(
                    label = stringResource(R.string.dashboard_due_today),
                    amount = summary.dueToday,
                    bengali = bengali,
                    hidden = state.settings.hideAmounts,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = stringResource(R.string.dashboard_due_week),
                    amount = summary.dueThisWeek,
                    bengali = bengali,
                    hidden = state.settings.hideAmounts,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    label = stringResource(R.string.dashboard_due_month),
                    amount = summary.dueThisMonth,
                    bengali = bengali,
                    hidden = state.settings.hideAmounts,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    amount: Money,
    bengali: Boolean,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        AmountText(
            money = amount,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            useBengaliDigits = bengali,
            hidden = hidden,
            compact = true,
        )
    }
}

@Composable
private fun OverdueBanner(
    count: Int,
    amount: Money,
    useBengaliDigits: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BokeyaCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        border = null,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.insight_overdue_count,
                        MoneyFormatter.formatNumber(count, useBengaliDigits),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                AmountText(
                    money = amount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    useBengaliDigits = useBengaliDigits,
                    hidden = hidden,
                )
            }
        }
    }
}

@Composable
private fun DebtBreakdownCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val entries = state.summary.dueByType.entries.sortedByDescending { it.value.minor }
    val slices = entries.map { (type, money) ->
        DonutSlice(label = type.shortLabel(), value = money.minor, color = type.accent())
    }

    BokeyaCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.dashboard_breakdown))
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = slices,
                centerLabel = stringResource(R.string.label_total),
                centerValue = if (state.settings.hideAmounts) {
                    "৳ ••••"
                } else {
                    MoneyFormatter.formatCompact(state.summary.totalDue, state.settings.useBengaliDigits)
                },
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entries.forEach { (type, money) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(type.accent()),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = type.shortLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AmountText(
                            money = money,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            useBengaliDigits = state.settings.useBengaliDigits,
                            hidden = state.settings.hideAmounts,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(
    insights: List<Insight>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BokeyaCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        SectionHeader(
            title = stringResource(R.string.dashboard_insights),
            actionLabel = stringResource(R.string.action_see_all),
            onActionClick = onClick,
        )
        Spacer(Modifier.height(10.dp))
        insights.forEachIndexed { index, insight ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            InsightRow(insight)
        }
    }
}

@Composable
fun InsightRow(insight: Insight, modifier: Modifier = Modifier) {
    val tint = when (insight.tone) {
        InsightTone.POSITIVE -> BokeyaTheme.colors.success
        InsightTone.WARNING -> BokeyaTheme.colors.warning
        InsightTone.CRITICAL -> MaterialTheme.colorScheme.error
        InsightTone.NEUTRAL -> MaterialTheme.colorScheme.primary
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Insights,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = insight.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
