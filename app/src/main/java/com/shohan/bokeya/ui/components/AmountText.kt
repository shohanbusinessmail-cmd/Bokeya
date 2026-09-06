package com.shohan.bokeya.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text

/**
 * Renders a [Money] value with Bokeya's formatting rules.
 *
 * When [animate] is on, the number counts up to its new value instead of
 * snapping — the small touch that makes a balance update feel deliberate. The
 * animation is disabled in previews and when the value is hidden.
 */
@Composable
fun AmountText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = com.shohan.bokeya.ui.theme.AmountTypography.medium,
    color: Color = Color.Unspecified,
    useBengaliDigits: Boolean = true,
    withSymbol: Boolean = true,
    showPoisha: Boolean = false,
    compact: Boolean = false,
    hidden: Boolean = false,
    animate: Boolean = false,
    maxLines: Int = 1,
) {
    val inPreview = LocalInspectionMode.current
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color

    val displayMoney = if (animate && !inPreview && !hidden) {
        // animateIntAsState works in whole Taka: animating poisha would overflow
        // Int for large balances and the extra precision is invisible anyway.
        val target = money.wholeTaka.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val animated by animateIntAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 550),
            label = "amount",
        )
        Money.ofTaka(animated.toLong())
    } else {
        money
    }

    val text = when {
        hidden -> HIDDEN_PLACEHOLDER
        compact -> MoneyFormatter.formatCompact(displayMoney, useBengaliDigits, withSymbol)
        else -> MoneyFormatter.format(displayMoney, useBengaliDigits, withSymbol, showPoisha)
    }

    // Screen readers always announce the true, unabbreviated amount.
    val spoken = remember(money, hidden, useBengaliDigits) {
        if (hidden) HIDDEN_DESCRIPTION else MoneyFormatter.format(money, useBengaliDigits, true, showPoisha)
    }

    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = spoken },
        style = style,
        color = resolvedColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val HIDDEN_PLACEHOLDER = "৳ ••••"
private const val HIDDEN_DESCRIPTION = "টাকার অঙ্ক লুকানো আছে"
