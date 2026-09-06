package com.shohan.bokeya.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shohan.bokeya.R
import com.shohan.bokeya.di.AppContainer
import com.shohan.bokeya.security.PinManager
import com.shohan.bokeya.ui.components.BokeyaCard
import kotlinx.coroutines.launch

/**
 * First-run setup: name, currency, notifications and optional App Lock.
 *
 * This talks to the container directly rather than through a ViewModel: it runs
 * exactly once, writes four preferences and then never exists again, so a
 * dedicated ViewModel would be ceremony without benefit.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var lockEnabled by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsEnabled = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(24.dp))

        BokeyaCard {
            Text(
                text = stringResource(R.string.setup_name_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setup_name_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))

        BokeyaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setup_currency),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "৳  বাংলাদেশি টাকা (BDT)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        BokeyaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setup_notification),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.setup_notification_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsEnabled = enabled
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        BokeyaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setup_lock),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.setup_lock_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = {
                        lockEnabled = it
                        if (!it) {
                            pin = ""
                            pinError = false
                        }
                    },
                )
            }

            if (lockEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(PinManager.MAX_PIN_LENGTH)
                        pinError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.setup_lock_setup)) },
                    singleLine = true,
                    isError = pinError,
                    supportingText = if (pinError) {
                        { Text(stringResource(R.string.lock_too_short)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                if (lockEnabled && pin.length < PinManager.MIN_PIN_LENGTH) {
                    pinError = true
                    return@Button
                }
                isSaving = true
                scope.launch {
                    with(container.preferences) {
                        setUserName(name)
                        setNotificationsEnabled(notificationsEnabled)
                        setSetupComplete(true)
                        setOnboardingComplete(true)
                    }
                    if (lockEnabled) {
                        container.pinManager.setPin(pin)
                        container.preferences.setAppLockEnabled(true)
                    }
                    container.reminderScheduler.schedule(container.preferences.current())
                    onFinished()
                }
            },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.setup_finish))
        }

        Spacer(Modifier.height(40.dp))
    }
}
