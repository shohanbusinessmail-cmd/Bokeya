package com.shohan.bokeya.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.DayActivity
import com.shohan.bokeya.ui.components.AmountText
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.LedgerRow
import com.shohan.bokeya.ui.components.LegendDot
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.components.UpcomingPaymentRow
import com.shohan.bokeya.ui.screens.money.MonthPicker
import com.shohan.bokeya.ui.theme.BokeyaTheme
import com.shohan.bokeya.ui.viewmodel.CalendarViewModel
import java.time.LocalDate

/**
 * Month grid with per-day markers, plus a detail panel for the selected day.
 *
 * The grid is a plain `Column` of `Row`s rather than a `LazyVerticalGrid`: a
 * month is at most 42 cells, so laziness costs more than it saves and nesting
 * a lazy grid inside a lazy column is a known source of measurement bugs.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onAccountClick: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bengali = state.settings.useBengaliDigits

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.calendar_title)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("picker") {
                MonthPicker(
                    label = BanglaDate.formatMonthYear(state.month, bengali),
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item("grid") {
                BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = 12.dp) {
                    WeekdayHeader()
                    Spacer(Modifier.height(6.dp))
                    MonthGrid(
                        month = state.month,
                        activity = state.activity,
                        selected = state.selectedDate,
                        onSelect = viewModel::selectDate,
                        useBengaliDigits = bengali,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(
                            MaterialTheme.colorScheme.error,
                            stringResource(R.string.calendar_legend_due),
                        )
                        LegendDot(
                            BokeyaTheme.colors.income,
                            stringResource(R.string.calendar_legend_income),
                        )
                        LegendDot(
                            BokeyaTheme.colors.expense,
                            stringResource(R.string.calendar_legend_expense),
                        )
                    }
                }
            }

            if (state.monthDueTotal.isPositive) {
                item("monthTotal") {
                    BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.calendar_month_due),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AmountText(
                                money = state.monthDueTotal,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                useBengaliDigits = bengali,
                            )
                        }
                    }
                }
            }

            item("dayHeader") {
                SectionHeader(
                    title = stringResource(
                        R.string.calendar_day_activity,
                        BanglaDate.formatFull(state.selectedDate, bengali),
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            val detail = state.dayDetail
            if (detail == null || detail.isEmpty) {
                item("emptyDay") {
                    EmptyState(
                        icon = Icons.Outlined.EventBusy,
                        title = stringResource(R.string.calendar_empty_day),
                        compact = true,
                    )
                }
            } else {
                items(detail.duePayments.size) { index ->
                    val payment = detail.duePayments[index]
                    UpcomingPaymentRow(
                        payment = payment,
                        onClick = { onAccountClick(payment.accountId) },
                        useBengaliDigits = bengali,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                val ledger = detail.payments + detail.incomes + detail.expenses
                items(ledger.size) { index ->
                    val entry = ledger[index]
                    LedgerRow(
                        entry = entry,
                        onClick = { entry.accountId?.let(onAccountClick) },
                        useBengaliDigits = bengali,
                        showDate = false,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        BanglaDate.WEEKDAY_HEADERS_SAT_FIRST.forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    activity: Map<LocalDate, DayActivity>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    useBengaliDigits: Boolean,
) {
    val first = BanglaDate.startOfMonth(month)
    val daysInMonth = month.lengthOfMonth()
    // Bangladeshi weeks start on Saturday; DayOfWeek.SATURDAY.value == 6.
    val leadingBlanks = (first.dayOfWeek.value + 1) % 7
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7
    val today = BanglaDate.today()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = first.withDayOfMonth(dayNumber)
                        DayCell(
                            date = date,
                            activity = activity[date],
                            isSelected = date == selected,
                            isToday = date == today,
                            useBengaliDigits = useBengaliDigits,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    activity: DayActivity?,
    isSelected: Boolean,
    isToday: Boolean,
    useBengaliDigits: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        R.string.cd_calendar_day,
        BanglaDate.formatFull(date, useBengaliDigits),
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = MoneyFormatter.formatNumber(date.dayOfMonth, useBengaliDigits),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )

            // Markers, not amounts: the grid stays readable and the detail
            // panel below carries the numbers.
            if (activity != null && activity.hasAnything) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (activity.dueCount > 0) {
                        Marker(
                            if (activity.hasOverdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                BokeyaTheme.colors.warning
                            },
                            isSelected,
                        )
                    }
                    if (activity.income.isPositive) Marker(BokeyaTheme.colors.income, isSelected)
                    if (activity.expense.isPositive) Marker(BokeyaTheme.colors.expense, isSelected)
                }
            }
        }
    }
}

@Composable
private fun Marker(color: Color, isSelected: Boolean) {
    Box(
        Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else color),
    )
}
