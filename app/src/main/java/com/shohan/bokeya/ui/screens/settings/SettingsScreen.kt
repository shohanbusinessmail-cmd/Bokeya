package com.shohan.bokeya.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.BuildConfig
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.domain.model.ThemeMode
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.components.label
import com.shohan.bokeya.ui.navigation.Routes
import com.shohan.bokeya.ui.screens.accounts.ConfirmDialog
import com.shohan.bokeya.ui.viewmodel.SettingsEvent
import com.shohan.bokeya.ui.viewmodel.SettingsViewModel
import java.time.LocalTime

/** Appearance, notifications, security, backup, data, currency, language, about. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showReminderDaysDialog by remember { mutableStateOf(false) }
    var showReminderTimeDialog by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.DataCleared -> context.getString(R.string.data_cleared)
                SettingsEvent.SampleDataAdded -> context.getString(R.string.sample_data_added)
                is SettingsEvent.Error -> context.getString(event.messageRes)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------------------------------------------------- profile
            BokeyaCard {
                SettingRow(
                    title = stringResource(R.string.settings_profile),
                    subtitle = settings.userName.ifBlank { stringResource(R.string.setup_name_hint) },
                    onClick = { showNameDialog = true },
                )
            }

            // ------------------------------------------------- appearance
            BokeyaCard {
                SectionHeader(title = stringResource(R.string.settings_appearance))
                Spacer(Modifier.height(4.dp))
                SettingRow(
                    title = stringResource(R.string.settings_theme),
                    subtitle = settings.themeMode.label(),
                    onClick = { showThemeDialog = true },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_sub),
                        checked = settings.useDynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
                SwitchRow(
                    title = stringResource(R.string.settings_bangla_digits),
                    subtitle = stringResource(R.string.settings_bangla_digits_sub),
                    checked = settings.useBengaliDigits,
                    onCheckedChange = viewModel::setBengaliDigits,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_hide_amounts),
                    subtitle = stringResource(R.string.settings_hide_amounts_sub),
                    checked = settings.hideAmounts,
                    onCheckedChange = viewModel::setHideAmounts,
                )
            }

            // ---------------------------------------------- notifications
            BokeyaCard {
                SectionHeader(title = stringResource(R.string.settings_notifications))
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_notifications_enable),
                    subtitle = stringResource(R.string.settings_notifications_sub),
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        // Android 13+ needs a runtime grant before anything can be posted.
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(enabled)
                        }
                    },
                )
                if (settings.notificationsEnabled) {
                    SettingRow(
                        title = stringResource(R.string.settings_reminder_days),
                        subtitle = stringResource(
                            R.string.settings_reminder_days_value,
                            MoneyFormatter.formatNumber(
                                settings.reminderDaysBefore,
                                settings.useBengaliDigits,
                            ),
                        ),
                        onClick = { showReminderDaysDialog = true },
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_reminder_time),
                        subtitle = BanglaDate.formatTime(
                            LocalTime.of(settings.reminderHour, settings.reminderMinute),
                            settings.useBengaliDigits,
                        ),
                        onClick = { showReminderTimeDialog = true },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_reminder_same_day),
                        checked = settings.reminderSameDay,
                        onCheckedChange = viewModel::setReminderSameDay,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_reminder_overdue),
                        checked = settings.reminderOverdue,
                        onCheckedChange = viewModel::setReminderOverdue,
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_test_notification),
                        onClick = viewModel::sendTestNotification,
                    )
                }
            }

            // -------------------------------------------------- security
            BokeyaCard {
                SectionHeader(title = stringResource(R.string.settings_security))
                Spacer(Modifier.height(4.dp))
                SettingRow(
                    title = stringResource(R.string.settings_app_lock),
                    subtitle = stringResource(R.string.settings_app_lock_sub),
                    onClick = { onNavigate(Routes.SECURITY) },
                )
            }

            // ---------------------------------------------------- backup
            BokeyaCard {
                SectionHeader(title = stringResource(R.string.settings_backup))
                Spacer(Modifier.height(4.dp))
                SettingRow(
                    title = stringResource(R.string.settings_backup),
                    subtitle = if (settings.lastBackupAt > 0) {
                        stringResource(
                            R.string.settings_last_backup,
                            BanglaDate.formatFull(
                                BanglaDate.dateOf(settings.lastBackupAt),
                                settings.useBengaliDigits,
                            ),
                        )
                    } else {
                        stringResource(R.string.settings_no_backup)
                    },
                    onClick = { onNavigate(Routes.BACKUP) },
                )
            }

            // ------------------------------------------------------ data
            BokeyaCard {
                SectionHeader(title = stringResource(R.string.settings_data))
                Spacer(Modifier.height(4.dp))
                SettingRow(
                    title = stringResource(R.string.money_manage_categories),
                    onClick = { onNavigate(Routes.CATEGORIES) },
                )
                // Demo data would be dishonest in a real install — debug only.
                if (BuildConfig.DEBUG) {
                    SettingRow(
                        title = stringResource(R.string.settings_sample_data),
                        subtitle = stringResource(R.string.settings_sample_data_sub),
                        onClick = viewModel::addSampleData,
                    )
                }
                SettingRow(
                    title = stringResource(R.string.settings_clear_data),
                    subtitle = stringResource(R.string.settings_clear_data_sub),
                    destructive = true,
                    onClick = { showClearDialog = true },
                )
            }

            // ------------------------------------------------- about etc.
            BokeyaCard {
                SettingRow(
                    title = stringResource(R.string.settings_currency),
                    subtitle = settings.currencySymbol + " (BDT)",
                    onClick = null,
                )
                SettingRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_sub),
                    onClick = null,
                )
                SettingRow(
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    onClick = { onNavigate(Routes.ABOUT) },
                )
            }

            Spacer(Modifier.height(60.dp))
        }
    }

    if (showThemeDialog) {
        OptionDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to it.label() },
            selected = settings.themeMode,
            onSelect = {
                viewModel.setTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showReminderDaysDialog) {
        OptionDialog(
            title = stringResource(R.string.settings_reminder_days),
            options = listOf(1, 2, 3, 5, 7).map { days ->
                days to stringResource(
                    R.string.settings_reminder_days_value,
                    MoneyFormatter.formatNumber(days, settings.useBengaliDigits),
                )
            },
            selected = settings.reminderDaysBefore,
            onSelect = {
                viewModel.setReminderDaysBefore(it)
                showReminderDaysDialog = false
            },
            onDismiss = { showReminderDaysDialog = false },
        )
    }

    if (showReminderTimeDialog) {
        OptionDialog(
            title = stringResource(R.string.settings_reminder_time),
            options = listOf(7, 8, 9, 10, 18, 20).map { hour ->
                hour * 60 to BanglaDate.formatTime(
                    LocalTime.of(hour, 0),
                    settings.useBengaliDigits,
                )
            },
            selected = settings.reminderTimeMinutes,
            onSelect = {
                viewModel.setReminderTime(it)
                showReminderTimeDialog = false
            },
            onDismiss = { showReminderTimeDialog = false },
        )
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf(settings.userName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.settings_profile)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.setup_name_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setUserName(name)
                        showNameDialog = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_clear_data_title),
            message = stringResource(R.string.confirm_clear_data_msg),
            confirmLabel = stringResource(R.string.confirm_clear_data_action),
            destructive = true,
            onConfirm = {
                showClearDialog = false
                viewModel.clearAllData()
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        shape = MaterialTheme.shapes.large,
    )
}
