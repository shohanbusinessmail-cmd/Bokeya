package com.shohan.bokeya.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.CategorySpend
import com.shohan.bokeya.domain.model.MonthlyTrend
import com.shohan.bokeya.ui.theme.BokeyaTheme

/**
 * Charts are hand-drawn on [Canvas] rather than pulled from a charting library.
 *
 * Three reasons: no extra third-party dependency in a privacy-focused app, full
 * control over Bangla labels and accessibility text, and each chart stays a few
 * dozen lines because it only draws what this app actually needs.
 */

private const val CHART_ANIMATION_MS = 700

/** Grouped income vs expense bars for the last N months. */
@Composable
fun IncomeExpenseChart(
    trend: List<MonthlyTrend>,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
) {
    if (trend.isEmpty()) return

    val incomeColor = BokeyaTheme.colors.income
    val expenseColor = BokeyaTheme.colors.expense
    val maxValue = trend.maxOf { maxOf(it.income.minor, it.expense.minor) }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            trend.forEach { month ->
                val description = stringResource(
                    R.string.cd_chart_bar,
                    BanglaDate.monthShort(month.month),
                    MoneyFormatter.format(month.income, useBengaliDigits),
                    MoneyFormatter.format(month.expense, useBengaliDigits),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Row(
                        modifier = Modifier.height(104.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Bar(
                            fraction = if (hidden) 0.35f else month.income.minor.toFloat() / maxValue,
                            color = incomeColor,
                        )
                        Bar(
                            fraction = if (hidden) 0.2f else month.expense.minor.toFloat() / maxValue,
                            color = expenseColor,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = BanglaDate.monthShort(month.month),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(incomeColor, stringResource(R.string.money_income))
            LegendDot(expenseColor, stringResource(R.string.money_expense))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Bar(fraction: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(CHART_ANIMATION_MS),
        label = "bar",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .height((104 * animated.coerceAtLeast(0.02f)).dp)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(color),
    )
}

/**
 * Donut showing how the total debt splits across categories.
 *
 * Slices below one degree are still drawn at a minimum width so a tiny debt
 * doesn't silently vanish from the picture.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val inPreview = LocalInspectionMode.current
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (inPreview) 0 else CHART_ANIMATION_MS),
        label = "donut",
    )
    val description = stringResource(R.string.cd_donut)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier.size(168.dp).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(168.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )

            var startAngle = -90f
            slices.forEach { slice ->
                // A 2° floor keeps rounding-small slices visible.
                val sweep = (slice.value.toFloat() / total * 360f).coerceAtLeast(2f) * progress
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class DonutSlice(val label: String, val value: Long, val color: Color)

/** Horizontal category bars — easier to read (and label) than a pie. */
@Composable
fun CategoryBars(
    categories: List<CategorySpend>,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
    maxRows: Int = 5,
    fallbackColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (categories.isEmpty()) return
    val rows = categories.sortedByDescending { it.amount.minor }.take(maxRows)
    val maxAmount = rows.maxOf { it.amount.minor }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { category ->
            val color = category.color?.let { Color(it) } ?: fallbackColor
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    AmountText(
                        money = category.amount,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        useBengaliDigits = useBengaliDigits,
                        hidden = hidden,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(
                    percent = (category.amount.minor * 100 / maxAmount).toInt(),
                    height = 6.dp,
                    progressColor = color,
                    contentDescription = null,
                )
            }
        }
    }
}

/** Small colour-dot + label pair used under charts. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Compact "paid of total" bar used on account cards and detail headers. */
@Composable
fun PaidProgress(
    paid: Money,
    total: Money,
    percent: Int,
    modifier: Modifier = Modifier,
    useBengaliDigits: Boolean = true,
    hidden: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ProgressBar(
            percent = percent,
            progressColor = color,
            contentDescription = stringResource(
                R.string.cd_progress,
                MoneyFormatter.formatNumber(percent, useBengaliDigits),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.of_total,
                    MoneyFormatter.format(total, useBengaliDigits).takeIf { !hidden } ?: "৳ ••••",
                    MoneyFormatter.format(paid, useBengaliDigits).takeIf { !hidden } ?: "৳ ••••",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.percent_format,
                    MoneyFormatter.formatNumber(percent, useBengaliDigits),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
