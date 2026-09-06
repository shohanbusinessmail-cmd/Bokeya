package com.shohan.bokeya.ui.screens.settings

import androidx.biometric.BiometricManager
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.screens.accounts.ConfirmDialog
import com.shohan.bokeya.ui.viewmodel.SecurityViewModel
import kotlinx.coroutines.launch

/** App Lock: PIN set/change/remove plus the biometric toggle. */
@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPinDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.security_title), onBack = onBack) },
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
            BokeyaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.security_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            BokeyaCard {
                SectionHeader(title = stringResource(R.string.settings_app_lock))
                Spacer(Modifier.height(4.dp))

                SettingRow(
                    title = stringResource(
                        if (state.hasPin) R.string.security_change_pin else R.string.security_set_pin,
                    ),
                    subtitle = stringResource(
                        if (state.hasPin) R.string.security_pin_set else R.string.security_pin_not_set,
                    ),
                    onClick = { showPinDialog = true },
                )

                SwitchRow(
                    title = stringResource(R.string.security_biometric),
                    subtitle = stringResource(
                        if (biometricAvailable) {
                            R.string.security_biometric_sub
                        } else {
                            R.string.security_biometric_unavailable
                        },
                    ),
                    checked = state.biometricEnabled,
                    enabled = state.hasPin && biometricAvailable,
                    onCheckedChange = viewModel::setBiometricEnabled,
                )

                if (state.hasPin) {
                    SettingRow(
                        title = stringResource(R.string.security_remove_lock),
                        destructive = true,
                        onClick = { showDisableDialog = true },
                    )
                }
            }

            Text(
                text = stringResource(R.string.security_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showPinDialog) {
        PinDialog(
            requireCurrent = state.hasPin,
            minLength = viewModel.minPinLength,
            maxLength = viewModel.maxPinLength,
            verifyCurrent = viewModel::verifyCurrentPin,
            onConfirm = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.security_pin_saved))
                }
            },
            onDismiss = { showPinDialog = false },
        )
    }

    if (showDisableDialog) {
        ConfirmDialog(
            title = stringResource(R.string.security_remove_lock),
            message = stringResource(R.string.security_remove_lock_msg),
            confirmLabel = stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                viewModel.disableLock()
                showDisableDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.security_lock_removed))
                }
            },
            onDismiss = { showDisableDialog = false },
        )
    }
}

@Composable
private fun PinDialog(
    requireCurrent: Boolean,
    minLength: Int,
    maxLength: Int,
    verifyCurrent: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    val mismatchError = stringResource(R.string.security_pin_mismatch)
    val lengthError = stringResource(R.string.security_pin_length)
    val wrongError = stringResource(R.string.security_pin_wrong)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (requireCurrent) R.string.security_change_pin else R.string.security_set_pin),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (requireCurrent) {
                    PinField(
                        value = currentPin,
                        onValueChange = { currentPin = it.filter(Char::isDigit).take(maxLength) },
                        label = stringResource(R.string.security_current_pin),
                        maxLength = maxLength,
                    )
                }
                PinField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(maxLength) },
                    label = stringResource(R.string.security_new_pin),
                    maxLength = maxLength,
                )
                PinField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(maxLength) },
                    label = stringResource(R.string.security_confirm_pin),
                    maxLength = maxLength,
                )
                error?.let {
                    Text(
                        text = when (it) {
                            1 -> lengthError
                            2 -> mismatchError
                            else -> wrongError
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        newPin.length < minLength -> 1
                        newPin != confirmPin -> 2
                        requireCurrent && !verifyCurrent(currentPin) -> 3
                        else -> null
                    }
                    if (error == null) onConfirm(newPin)
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
        ),
        shape = MaterialTheme.shapes.medium,
        supportingText = null,
    )
}
