package com.shohan.bokeya.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.core.money.MoneyFormatter

/** Floor for the shrink-to-fit pass. Amounts stay legible; they never ellipsise. */
private val DEFAULT_MIN_AMOUNT_SIZE = 11.sp

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

    // The fit is measured against the settled value so a running count-up
    // animation cannot make the type size jitter frame by frame.
    val sizingText = remember(money, useBengaliDigits, withSymbol, showPoisha) {
        MoneyFormatter.format(money, useBengaliDigits, withSymbol, showPoisha)
    }

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
        sizingText = sizingText,
    )
}

/**
 * Single-line text that shrinks to fit the width it is given.
 *
 * Used for anything monetary. Unlike `TextOverflow.Ellipsis` it never drops
 * characters, so a figure is either shown in full or not at all — and the floor
 * at [minFontSize] keeps it readable.
 *
 * The text is measured once at its natural size and scaled by
 * [fitFontScale]; the type never grows past [style]'s own size, so the approved
 * visual hierarchy is untouched and only oversized values are affected.
 *
 * @param sizingText the string the fit is calculated from; pass the settled
 *   value when [text] is animating.
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = DEFAULT_MIN_AMOUNT_SIZE,
    sizingText: String = text,
) {
    val resolved = if (color == Color.Unspecified) LocalContentColor.current else color
    val baseSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val available = constraints.maxWidth

        val fitted = remember(sizingText, available, baseSize, minFontSize, style) {
            // Unbounded width (e.g. inside a horizontal scroller) means there is
            // nothing to shrink against, so the natural size is already correct.
            if (available == Constraints.Infinity || available <= 0) {
                style
            } else {
                val natural = measurer.measure(
                    text = sizingText,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                ).size.width

                val scale = fitFontScale(
                    naturalWidthPx = natural,
                    availableWidthPx = available,
                    baseFontSizeSp = baseSize.value,
                    minFontSizeSp = minFontSize.value,
                )

                if (scale >= 1f) {
                    style
                } else {
                    style.copy(
                        fontSize = baseSize * scale,
                        lineHeight = if (style.lineHeight.isSpecified) {
                            style.lineHeight * scale
                        } else {
                            style.lineHeight
                        },
                    )
                }
            }
        }

        Text(
            text = text,
            style = fitted,
            color = resolved,
            maxLines = 1,
            softWrap = false,
            // Never Ellipsis: an amount must not be rendered as "৳ ৫,০...".
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * Scale factor that makes text of [naturalWidthPx] fit [availableWidthPx].
 *
 * Pure so the sizing rule can be tested without a device. Returns `1f` when the
 * text already fits, never returns more than `1f` (text is only ever shrunk,
 * never inflated), and never goes below [minFontSizeSp] / [baseFontSizeSp] so
 * an extreme value stays legible rather than collapsing to nothing.
 */
fun fitFontScale(
    naturalWidthPx: Int,
    availableWidthPx: Int,
    baseFontSizeSp: Float,
    minFontSizeSp: Float,
): Float {
    if (naturalWidthPx <= 0 || availableWidthPx <= 0) return 1f
    if (naturalWidthPx <= availableWidthPx) return 1f

    val floor = if (baseFontSizeSp > 0f) {
        (minFontSizeSp / baseFontSizeSp).coerceIn(0f, 1f)
    } else {
        1f
    }
    // A hair under the exact ratio absorbs sub-pixel rounding in the shaper, so
    // the fitted line cannot end up one pixel too wide and get clipped.
    val exact = (availableWidthPx.toFloat() / naturalWidthPx.toFloat()) * FIT_SAFETY
    return exact.coerceIn(floor, 1f)
}

private const val FIT_SAFETY = 0.995f
