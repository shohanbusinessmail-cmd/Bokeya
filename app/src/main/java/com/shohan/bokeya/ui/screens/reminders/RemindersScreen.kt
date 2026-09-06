package com.shohan.bokeya.ui.screens.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
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
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.EmptyState
import com.shohan.bokeya.ui.components.LoadingState
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.components.UpcomingPaymentRow
import com.shohan.bokeya.ui.screens.settings.SettingRow
import com.shohan.bokeya.ui.screens.settings.SwitchRow
import com.shohan.bokeya.ui.viewmodel.RemindersViewModel
import com.shohan.bokeya.ui.viewmodel.SettingsViewModel

/**
 * Everything the app will remind about, plus the switches that control it.
 *
 * Reminder settings also live in Settings; showing them here means the user can
 * see a due date and silence or retime the reminder without leaving the screen.
 */
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAccountClick: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.reminders_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("settings") {
                BokeyaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(title = stringResource(R.string.settings_notifications))
                    Spacer(Modifier.height(4.dp))
                    SwitchRow(
                        title = stringResource(R.string.settings_notifications_enable),
                        subtitle = stringResource(R.string.settings_notifications_sub),
                        checked = settings.notificationsEnabled,
                        onCheckedChange = settingsViewModel::setNotificationsEnabled,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_reminder_same_day),
                        checked = settings.reminderSameDay,
                        enabled = settings.notificationsEnabled,
                        onCheckedChange = settingsViewModel::setReminderSameDay,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_reminder_overdue),
                        checked = settings.reminderOverdue,
                        enabled = settings.notificationsEnabled,
                        onCheckedChange = settingsViewModel::setReminderOverdue,
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_reminder_days),
                        subtitle = stringResource(
                            R.string.settings_reminder_days_value,
                            MoneyFormatter.formatNumber(
                                settings.reminderDaysBefore,
                                settings.useBengaliDigits,
                            ),
                        ),
                    )
                }
            }

            item("upcomingHeader") {
                SectionHeader(
                    title = stringResource(R.string.reminders_scheduled),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            when {
                state.isLoading -> item("loading") { LoadingState() }

                state.upcoming.isEmpty() -> item("empty") {
                    EmptyState(
                        icon = Icons.Outlined.NotificationsOff,
                        title = stringResource(R.string.reminders_empty),
                        subtitle = stringResource(R.string.reminders_empty_sub),
                    )
                }

                else -> items(state.upcoming, key = { "${it.accountId}_${it.dueDate}" }) { payment ->
                    UpcomingPaymentRow(
                        payment = payment,
                        onClick = { onAccountClick(payment.accountId) },
                        useBengaliDigits = settings.useBengaliDigits,
                        hidden = settings.hideAmounts,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item("footer") {
                Text(
                    text = stringResource(R.string.reminders_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
