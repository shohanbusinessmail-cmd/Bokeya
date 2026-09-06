package com.shohan.bokeya.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shohan.bokeya.core.money.Money
import com.shohan.bokeya.ui.theme.AmountTypography
import com.shohan.bokeya.ui.theme.BokeyaTheme

/**
 * The standard content card.
 *
 * Elevation stays very low (1dp) and separation comes from surface tone plus a
 * hairline outline instead — heavy drop shadows are what make Material apps
 * look dated.
 */
@Composable
fun BokeyaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    elevation: Dp = 1.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = border,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * The dashboard hero surface: a dark gradient panel that anchors the screen and
 * carries the single most important number ("মোট বকেয়া").
 *
 * The corner highlight is a cheap radial gradient rather than a real blur —
 * it reads as glass without costing a render pass on every frame.
 */
@Composable
fun GradientHeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(BokeyaTheme.colors.heroGradient)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        radius = 720f,
                    ),
                ),
        )
        content()
    }
}

/** Compact metric tile: icon + label on top, amount below. */
@Composable
fun StatTile(
    label: String,
    amount: Money,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    useBengaliDigits: Boolean = true,
    onClick: (() -> Unit)? = null,
    compactAmount: Boolean = false,
) {
    BokeyaCard(modifier = modifier, onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        AmountText(
            money = amount,
            style = AmountTypography.small,
            color = MaterialTheme.colorScheme.onSurface,
            useBengaliDigits = useBengaliDigits,
            compact = compactAmount,
        )
    }
}

/** Slim progress bar with an animated fill and a spoken percentage. */
@Composable
fun ProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    animate: Boolean = true,
    contentDescription: String? = null,
) {
    val target = percent.coerceIn(0, 100) / 100f
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (animate) 600 else 0),
        label = "progress",
    )
    val animatedColor by animateColorAsState(progressColor, tween(400), label = "progressColor")

    val base = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(50))
        .background(trackColor)

    Box(
        modifier = if (contentDescription != null) {
            base.semantics { this.contentDescription = contentDescription }
        } else {
            base
        },
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(animatedColor),
            )
        }
    }
}

/** Section header with an optional trailing text action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
