package com.shohan.bokeya.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter

/** Floor for the shrink-to-fit pass. Amounts stay legible; they never ellipsise. */
private val DEFAULT_MIN_AMOUNT_SIZE = 11.sp

/** Granularity of the auto-size search — fine enough to look stepless. */
private val AUTO_SIZE_STEP = 0.5.sp

/**
 * Renders a [Money] value with Bokeya's formatting rules.
 *
 * A monetary amount is never abbreviated, masked or truncated: the full
 * lakh/crore-grouped figure is always shown. When the available width is too
 * narrow the type scales down to fit instead of ellipsising, so
 * `৳ ১,০০,০০,০০০` stays completely readable even inside a one-third-width tile.
 *
 * When [animate] is on, the number counts up to its new value instead of
 * snapping — the small touch that makes a balance update feel deliberate. The
 * animation is disabled in previews.
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
    animate: Boolean = false,
    minFontSize: TextUnit = DEFAULT_MIN_AMOUNT_SIZE,
) {
    val inPreview = LocalInspectionMode.current

    val displayMoney = if (animate && !inPreview) {
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

    val text = MoneyFormatter.format(displayMoney, useBengaliDigits, withSymbol, showPoisha)

    // Screen readers always announce the true, unabbreviated amount.
    val spoken = remember(money, useBengaliDigits, showPoisha) {
        MoneyFormatter.format(money, useBengaliDigits, true, showPoisha)
    }

    AutoSizeText(
        text = text,
        modifier = modifier.semantics { contentDescription = spoken },
        style = style,
        color = color,
        minFontSize = minFontSize,
    )
}

/**
 * Single-line text that shrinks to fit the width it is given.
 *
 * Used for anything monetary. Unlike `TextOverflow.Ellipsis` it never drops
 * characters, so a figure is either shown in full or not at all — and the floor
 * at [minFontSize] keeps it readable. Sizing happens inside the text layout
 * pass, so it costs no extra subcomposition and stays smooth in long lists.
 *
 * The type never grows past [style]'s own size, which keeps the approved visual
 * hierarchy intact; it only ever scales down when a value would not otherwise
 * fit.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = DEFAULT_MIN_AMOUNT_SIZE,
) {
    val resolved = if (color == Color.Unspecified) LocalContentColor.current else color
    val maxSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp

    // StepBased requires min < max; clamp for styles smaller than the floor.
    val minSize = if (minFontSize.value < maxSize.value) minFontSize else maxSize * 0.6f

    val autoSize = remember(minSize, maxSize) {
        TextAutoSize.StepBased(
            minFontSize = minSize,
            maxFontSize = maxSize,
            stepSize = AUTO_SIZE_STEP,
        )
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        color = { resolved },
        maxLines = 1,
        softWrap = false,
        // Never Ellipsis: an amount must not be rendered as "৳ ৫,০...".
        overflow = TextOverflow.Visible,
        autoSize = autoSize,
    )
}
