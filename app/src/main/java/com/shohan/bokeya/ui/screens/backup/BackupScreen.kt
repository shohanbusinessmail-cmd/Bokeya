package com.shohan.bokeya.ui.screens.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.screens.accounts.ConfirmDialog
import com.shohan.bokeya.ui.viewmodel.BackupEvent
import com.shohan.bokeya.ui.viewmodel.BackupViewModel

/**
 * Backup and restore through the Storage Access Framework.
 *
 * SAF means the user picks exactly where the file goes, so the app needs no
 * storage permission and the backup never leaves the device unless the user
 * chooses a cloud-backed folder themselves.
 */
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::backupTo) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestoreUri = uri }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                BackupEvent.BackupDone -> context.getString(R.string.backup_done)
                is BackupEvent.RestoreDone -> context.getString(
                    R.string.restore_done,
                    MoneyFormatter.formatNumber(event.accounts, true),
                )
                is BackupEvent.Failed -> context.getString(event.messageRes)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.backup_title), onBack = onBack) },
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
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.backup_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            BokeyaCard {
                SectionHeader(title = stringResource(R.string.backup_create))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.lastBackupAt > 0) {
                        stringResource(
                            R.string.settings_last_backup,
                            BanglaDate.formatFull(
                                BanglaDate.dateOf(state.lastBackupAt),
                                state.settings.useBengaliDigits,
                            ),
                        )
                    } else {
                        stringResource(R.string.settings_no_backup)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { createDocument.launch(viewModel.suggestedFileName()) },
                    enabled = !state.isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(Icons.Outlined.CloudUpload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_create_action))
                }
            }

            BokeyaCard {
                SectionHeader(title = stringResource(R.string.restore_title))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.restore_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        openDocument.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    enabled = !state.isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(Icons.Outlined.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.restore_action))
                }
            }

            if (state.isWorking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.backup_working),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.backup_contents),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    // Restore overwrites everything — always confirm first.
    pendingRestoreUri?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_restore_title),
            message = stringResource(R.string.confirm_restore_msg),
            confirmLabel = stringResource(R.string.restore_action),
            destructive = true,
            onConfirm = {
                viewModel.restoreFrom(uri)
                pendingRestoreUri = null
            },
            onDismiss = { pendingRestoreUri = null },
        )
    }
}
