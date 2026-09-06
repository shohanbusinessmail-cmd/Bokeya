package com.shohan.bokeya.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.shohan.bokeya.R
import com.shohan.bokeya.domain.model.AccountType
import com.shohan.bokeya.ui.theme.BokeyaTheme

/**
 * Bottom navigation for the five top-level tabs.
 *
 * Uses Material 3's own pill indicator rather than a custom one — it handles
 * touch targets, ripples and accessibility correctly out of the box.
 */
@Composable
fun BottomBar(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * Expandable quick-action FAB: two taps to any new record.
 *
 * The six actions are the entire "add" surface of the app, so they live here
 * instead of being scattered as per-screen buttons.
 */
@Composable
fun QuickActionsFab(
    onAddAccount: (AccountType) -> Unit,
    onAddMoney: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(220),
        label = "fabRotation",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(),
            exit = fadeOut(tween(120)) + shrinkVertically(),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                QuickAction(
                    label = stringResource(R.string.quick_new_debt),
                    icon = Icons.Outlined.Storefront,
                    color = BokeyaTheme.colors.shop,
                ) { expanded = false; onAddAccount(AccountType.SHOP) }

                QuickAction(
                    label = stringResource(R.string.quick_add_loan),
                    icon = Icons.Outlined.AccountBalance,
                    color = BokeyaTheme.colors.loan,
                ) { expanded = false; onAddAccount(AccountType.LOAN) }

                QuickAction(
                    label = stringResource(R.string.quick_add_emi),
                    icon = Icons.Outlined.CreditCard,
                    color = BokeyaTheme.colors.emi,
                ) { expanded = false; onAddAccount(AccountType.EMI) }

                QuickAction(
                    label = stringResource(R.string.quick_add_person),
                    icon = Icons.Outlined.Handshake,
                    color = BokeyaTheme.colors.person,
                ) { expanded = false; onAddAccount(AccountType.PERSON) }

                QuickAction(
                    label = stringResource(R.string.quick_add_income),
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    color = BokeyaTheme.colors.income,
                ) { expanded = false; onAddMoney(true) }

                QuickAction(
                    label = stringResource(R.string.quick_add_expense),
                    icon = Icons.AutoMirrored.Outlined.TrendingDown,
                    color = BokeyaTheme.colors.expense,
                ) { expanded = false; onAddMoney(false) }
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(
                    if (expanded) R.string.fab_close else R.string.fab_add,
                ),
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Switches tabs without stacking duplicates: pops to the graph's start, saves
 * the outgoing tab's state and restores the incoming one.
 */
fun NavController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
