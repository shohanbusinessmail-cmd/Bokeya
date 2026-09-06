package com.shohan.bokeya.ui.screens.reports

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohan.bokeya.R
import com.shohan.bokeya.core.datetime.BanglaDate
import com.shohan.bokeya.export.ExportFormat
import com.shohan.bokeya.export.ReportType
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.components.SectionHeader
import com.shohan.bokeya.ui.components.label
import com.shohan.bokeya.ui.screens.money.MonthPicker
import com.shohan.bokeya.ui.viewmodel.ReportEvent
import com.shohan.bokeya.ui.viewmodel.ReportsViewModel

/**
 * Generates CSV/PDF reports and hands them to the system share sheet.
 *
 * Files live in the app cache and are shared through the FileProvider, so the
 * app never needs a storage permission and nothing is left behind on disk.
 */
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportEvent.Ready -> {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(
                        share,
                        context.getString(R.string.report_share_title),
                    )
                    runCatching { context.startActivity(chooser) }
                }
                is ReportEvent.Failed ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.reports_title), onBack = onBack) },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
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
                SectionHeader(title = stringResource(R.string.reports_title))
                Spacer(Modifier.height(8.dp))
                ReportType.entries.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.selectedType == type,
                            onClick = { viewModel.selectType(type) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = type.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // A "full" report covers everything, so a month has no meaning there.
            if (state.selectedType != ReportType.FULL && state.selectedType != ReportType.DEBT) {
                BokeyaCard {
                    Text(
                        text = stringResource(R.string.report_period),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    MonthPicker(
                        label = BanglaDate.formatMonthYear(
                            state.month,
                            state.settings.useBengaliDigits,
                        ),
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.generate(ExportFormat.PDF) },
                    enabled = !state.isGenerating,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_export_pdf))
                }
                OutlinedButton(
                    onClick = { viewModel.generate(ExportFormat.CSV) },
                    enabled = !state.isGenerating,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Icon(Icons.Outlined.TableChart, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_export_csv))
                }
            }

            if (state.isGenerating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.report_generating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.backup_contents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
